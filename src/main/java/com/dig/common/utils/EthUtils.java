package com.dig.common.utils;

import cn.hutool.core.util.HexUtil;
import com.dig.common.dto.MinerInfo;
import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lp
 * @date 2021-10-12 10:02
 */
@Slf4j
public class EthUtils {

    /**
     * store blockNumber for setMinerEvt listen
     */
    public static final String INIT_SET_MINER_EVT = "init:setMinerEvt:blockNumber";

    public static List<Type> extractEventValue(Log ethLog, Event event) {
        List<Type> results = new ArrayList<>();
        List<TypeReference<Type>> indexedParameters = event.getIndexedParameters();
        for (int i = 0; i < indexedParameters.size(); i++) {
            Type type = FunctionReturnDecoder.decodeIndexedValue(ethLog.getTopics().get(i + 1), indexedParameters.get(i));
            results.add(type);
        }
        List<Type> notIndexTypeList = FunctionReturnDecoder.decode(ethLog.getData(), event.getNonIndexedParameters());
        results.addAll(notIndexTypeList);
        return results;
    }

    public static void main(String[] args) {
//        String test="0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000014000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000280000000000000000000000000000000000000000000000000000000000000009c656e6f64653a2f2f3139366436363336643561356536636364366565353433393564373436633365666164623666633966396539323464633065646465363538333033663363626435653335613735373637613836356235306531346466633964613731333537626266363766356135303232633031313937353361666263333565313466656238403139322e3136382e35332e34323a333033303300000000000000000000000000000000000000000000000000000000000000000000009c3330346330333032303730303032303132303032323130303838316538363163663435383861613330346463636533346230636565623464313335333339363764663063353638366330313334343733333035633934373330323230323839363762383531666161393763653532373833396132623061396634663062326562363131646264326539653836383266303834333034653032613061620000000000000000000000000000000000000000000000000000000000000000000000416261667a61616a616961656a6362626d336967776266326d357571743779717064686437377679647476766d69646266686c3734356475376662633536686e683300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001c2f6970342f3139322e3136382e35332e34322f7463702f333033303800000000";
        String test = "0x0000000000000000000000000000000000000000000000000000000000000020" +
                    "0000000000000000000000000000000000000000000000000000000000000080" +
                    "0000000000000000000000000000000000000000000000000000000000000140" +
                    "0000000000000000000000000000000000000000000000000000000000000200" +
                    "0000000000000000000000000000000000000000000000000000000000000280" +
                    "000000000000000000000000000000000000000000000000000000000000009c" +
                    "656e6f64653a2f2f6438353131623837643062613364613337306665323066356263613166323361376662333534333832366136383433386136386365373564663433616432343738646234363761633330633863373634396361656261613866326131653836616233666231323666613630346335386164623237323965343236623232336562403139322e3136382e35332e34313a3330333033" +
                    "00000000000000000000000000000000000000000000000000000000000000000000009c" +
                    "333034633033303230373030303230313230303232303430313535643565303137303236616666623337643436376465373931633463663836666163646635646634303336383364313136616561356637343930386230323231303062386566326630386232326336633939393566303266363837353164623565646331366463653334633038663833323961396561623434353633613164313939" +
                    "000000000000000000000000000000000000000000000000000000000000000000000041" +
                    "6261667a61616a616961656a63617172366e676c7767706e35346a79733673703467766f7a76746435666c73636c376c62753569753779766a796871686d327677" +
                    "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001c" +
                    "2f6970342f3139322e3136382e35332e34312f7463702f333033303800000000";
        MinerInfo minerInfo = minerInfo(test);
        System.out.println(minerInfo);
    }

    /**
     *
     * @param value
     * @return
     */
    public static MinerInfo minerInfo(String value) {
        //剔除0x
        String s = Numeric.cleanHexPrefix(value);
        //1.enode解析
        String substring = s.substring(320, 384);
        int a = HexUtil.toBigInteger(substring).intValue() * 2;
        String encodeString = s.substring(384, 384 + a);
        String enco = toStringHex2(encodeString);
        //2.pubKey解析
        String substring1 = s.substring(384 + a, (384 + a) + 72);
        int b = HexUtil.toBigInteger(substring1).intValue() * 2;
        String b1 = s.substring((384 + a) + 72, ((384 + a) + 72) + b);
        String pubKey = toStringHex2(b1);
        //3.peerId
        String substring2 = s.substring(((384 + a) + 72) + b, (((384 + a) + 72) + b) + 72);
        int c = HexUtil.toBigInteger(substring2).intValue() * 2;
        int beginIndex = ((((384 + a) + 72) + b) + 72) + c;
        String c1 = s.substring((((384 + a) + 72) + b) + 72, beginIndex);
        String peerId = toStringHex2(c1);
        //4.peerAddr
        int beginIndex2 = ((((384 + a) + 72) + b) + 72) + c + 126;
        String substring3 = s.substring(beginIndex, beginIndex2);
        int d = HexUtil.toBigInteger(substring3).intValue() * 2;
        String d1 = s.substring(beginIndex2, ((((384 + a) + 72) + b) + 72) + c + 126 + d);
        String peerAddr = toStringHex2(d1);
        return MinerInfo.builder()
                .enode(enco)
                .pubKey(pubKey)
                .peerId(peerId)
                .peerAddr(peerAddr)
                .build();
    }

    /**
     * toStringHex2
     * @param s
     * @return string
     */
    public static String toStringHex2(String s) {
        byte[] baKeyword = new byte[s.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            try {
                baKeyword[i] = (byte) (0xff & Integer.parseInt(s.substring(
                        i * 2, i * 2 + 2), 16));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            // UTF-16le:Not
            s = new String(baKeyword, StandardCharsets.UTF_8);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return s;
    }

}
