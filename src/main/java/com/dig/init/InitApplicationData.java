package com.dig.init;

import com.dig.common.redis.RedisCache;
import com.dig.common.utils.CountryMapUtils;
import com.dig.common.utils.EthUtils;
import com.dig.common.utils.IPUtils;
import com.dig.entity.DnsCountry;
import com.dig.event.IPChangeEvent;
import com.dig.event.QueueManager;
import com.dig.service.impl.IpServiceImpl;
import io.ipfs.multiaddr.MultiAddress;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.websocket.WebSocketClient;
import org.web3j.protocol.websocket.WebSocketService;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author lp
 * @date 2022-06-20 13:55
 */
@Slf4j
@Component
public class InitApplicationData implements CommandLineRunner {

    /**
     * country and continent mapping
     */
    public static ConcurrentHashMap<String, String> countryAndContinentMap = new ConcurrentHashMap();

    /**
     * country from maxmind, mapping to aws's country and continent
     */
    public static ConcurrentHashMap<String, String> maxMind2AwsMap = new ConcurrentHashMap();

    @Value("${aws.dns.continent.countries}")
    private String countries;

    @Value("${file.ip-db}")
    private String ipFile;

    @Value("${file.country-map}")
    private String countryMapFile;

    @Value("${w3fs.enable}")
    private boolean enableW3fs;

    @Value("${w3fs.ip}")
    private String w3fsIp;

    @Value("${w3fs.wsPort}")
    private String wsPort;

    @Value("${w3fs.address}")
    private String address;

    @Autowired
    private IpServiceImpl ipService;

    @Resource
    private RedisCache redisCache;

    @Resource
    private RedissonClient redissonClient;

    // websocket client
    public static WebSocketClient webSocketClient;
    private WebSocketService webSocketService;
    private Web3j web3j;

    /**
     * connect end flag
     */
    private AtomicBoolean end;

    @Override
    public void run(String... args) throws Exception {

        parseMapfile();
        parseContinent();
        QueueManager.initConsumeQueue();
        //监听setMinerEvt事件
        if (enableW3fs) {
            createWebSocketClient();
        }
        log.info("init finish.");
    }

    private void createWebSocketClient() {
        while (true) {
            try {
                URI serverUri = new URI("ws://" + w3fsIp + ":" + wsPort);
                webSocketClient = new WebSocketClient(serverUri);
                if (web3j != null) {
                    try {
                        web3j.shutdown();
                    } catch (Exception e) {
                        log.error("web3j.shutdown error:{}", e.getMessage());
                    }
                }
                if (webSocketClient != null) {
                    try {
                        webSocketClient.close();
                    } catch (Exception e) {
                        log.error("webSocketClient.close error:{}", e.getMessage());
                    }
                }
                minerEventListen(webSocketClient);
                break;
            } catch (Exception e) {
                log.error("createWebSocketClient error:", e);
                try {
                    long sleepTime = 8l;
                    log.info("Will retry to Connect to WsServer after {}s....", sleepTime);
                    Thread.sleep(sleepTime * 1000);
                } catch (Exception e2) {
                }
            }
        }
    }

    public void parseContinent() {
        if (countries != null) {
            String[] arr = countries.split(",");
            for (String str : arr) {
                String[] cc = str.split(":");
                countryAndContinentMap.put(cc[1], cc[0]);
            }
        }
    }

    public void parseMapfile() {
        try {
            maxMind2AwsMap = CountryMapUtils.readMapFile(countryMapFile);
        } catch (IOException e) {
            log.error("parseMapfile error", e);
        }
    }

    /**
     * listen setMinerEvt from chain.
     *
     * @param mWebSocketClient
     */
    public void minerEventListen(WebSocketClient mWebSocketClient) throws ConnectException {
        webSocketService = new WebSocketService(mWebSocketClient, true);
        Runnable wsClosedHandle = new Runnable() {
            @Override
            public void run() {
                log.info("Error: Cannot connect to the Websocket Server!");
                createWebSocketClient();
            }
        };
        webSocketService.connect((s) -> {
                },
                (t) -> {
                },
                wsClosedHandle);
        web3j = Web3j.build(webSocketService);
        DefaultBlockParameter number = getStartBlockNumber(EthUtils.INIT_SET_MINER_EVT);
        EthFilter filter = getFilter(number, address);
        Event event = setMinerEvt();
        filter.addSingleTopic(EventEncoder.encode(event));
        log.info("start listen the event:setMinerEvt.......");
        web3j.ethLogFlowable(filter).subscribe(ethLog -> {
            try {
                if (checkLog(web3j, ethLog, "setMinerEvt")) {
                    log.info("[1] start parse event. txHash:{}", ethLog.getTransactionHash());
                    List<Type> results = EthUtils.extractEventValue(ethLog, event);
                    byte[] value = (byte[]) results.get(0).getValue();
                    String minerId = "0x" + Hex.toHexString(value);
                    String minerAddr = "0x" + Keys.getAddress((String) results.get(1).getValue());
                    log.info("txHash:{}, minerId:{}, minerAddr:{} ", ethLog.getTransactionHash(), minerId, minerAddr);
                    List<TypeReference<?>> resultList = new ArrayList<>();
                    resultList.add(new TypeReference<Utf8String>() {
                    });
                    List<Type> inputParameters = new ArrayList<>();
                    inputParameters.add(new Bytes32(value));
                    Function function = new Function("getProxyAddrByMinerId", inputParameters, resultList);
                    String functionEncode = FunctionEncoder.encode(function);
                    org.web3j.protocol.core.methods.response.EthCall response = web3j.ethCall(
                            Transaction.createEthCallTransaction(null, address, functionEncode),
                            DefaultBlockParameterName.LATEST).sendAsync().get();
                    List<Type> decode = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
                    if (decode != null && decode.size() > 0) {
                        String proxyAddr = (String) decode.get(0).getValue();
                        boolean isIp = false;
                        MultiAddress multiAddress = new MultiAddress(proxyAddr);
                        String currIp = multiAddress.getHost();
                        boolean ip4 = false;
                        if (!StringUtils.isEmpty(currIp)) {
                            String ipType = IPUtils.ipType(proxyAddr);
                            ip4 = "ip4".equals(ipType);
                            isIp = true;
                        }
                        if (isIp) {
                            DnsCountry dnsCountry = ipService.getCountryByIp(currIp);
                            String continentAndCountry = maxMind2AwsMap.get(dnsCountry.getCountry());
                            log.info("[ip:{}]====> before trans:{}, after trans:{}", currIp, dnsCountry.getCountry(), continentAndCountry);
                            if (StringUtils.isEmpty(continentAndCountry)) {
                                dnsCountry = IPUtils.defaultCountry;
                            } else {
                                String[] arr = continentAndCountry.split("-");
                                if (arr == null || arr.length < 2) {
                                    dnsCountry = IPUtils.defaultCountry;
                                } else {
                                    dnsCountry = new DnsCountry(arr[1], arr[0]);
                                }
                            }
                            IPChangeEvent ipChangeEvent = new IPChangeEvent(currIp, ip4, minerId, dnsCountry.getCountry(), dnsCountry.getContinent());
                            ipChangeEvent.setLastesBlockNumber(ethLog.getBlockNumber());
                            QueueManager.addEvent(ipChangeEvent);
                            log.info("[2] Publish event to queue. txHash:{}, proxyAddr:{}", ethLog.getTransactionHash(), proxyAddr);

                        } else {
                            log.error("[2] Ip's format is error! txHash:{}, proxyAddr:{}", ethLog.getTransactionHash(), proxyAddr);
                        }
                    } else {
                        log.error("[2] cannot get the proxyIp, txHash:{}", ethLog.getTransactionHash());
                    }
                } else {
                    log.info("Not setMinerEvt, ignore it!");
                }
            } catch (Exception e) {
                log.error("listen setMinerEvent error:" + e.getMessage(), e);
            }
        });
    }


    /**
     * 校验查询是否成功
     *
     * @param web3j  web3j
     * @param ethLog 监听到数据
     * @param event  事件名称
     * @return 返回
     */
    private boolean checkLog(Web3j web3j, Log ethLog, String event) throws IOException {
        EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(ethLog.getTransactionHash()).send();
        String status = receipt.getResult().getStatus();
        String s = Integer.parseInt(status.substring(2), 16) + "";
        int size = receipt.getResult().getLogs().size();
        log.info("receive event[" + event + "] blockHeight:{}, txStatus:{}, size:{}", ethLog.getBlockNumber(), s, size);
        return "1".equals(s) && size > 1;
    }

    private EthFilter getFilter(DefaultBlockParameter startBlock, String address) {
        return new EthFilter(
                startBlock, DefaultBlockParameterName.LATEST, address);
    }

    public static Event setMinerEvt() {
        return new Event(
                "setMinerEvt",
                Arrays.asList(
                        new TypeReference<Bytes32>(true) {
                        },
                        new TypeReference<Address>(true) {
                        },
                        new TypeReference<Utf8String>(true) {
                        }
                )
        );
    }

    private DefaultBlockParameter getStartBlockNumber(String key) {
        Integer block = redisCache.getCacheObject(key);
        if (block == null) {
            return DefaultBlockParameterName.EARLIEST;
        }
        return DefaultBlockParameter.valueOf(BigInteger.valueOf(block));
    }

}
