package com.dig.service.impl;

import com.dig.common.DnsConstants;
import com.dig.common.utils.CacheUtils;
import com.dig.common.utils.EthUtils;
import com.dig.common.redis.RedisCache;
import com.dig.common.utils.IPUtils;
import com.dig.entity.DnsCountry;
import com.dig.event.IPChangeEvent;
import com.dig.init.InitApplicationData;
import com.dig.service.IRouteService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class RouteServiceImpl implements IRouteService {

    @Autowired
    private IpServiceImpl ipService;
    @Autowired
    private RedisCache redisCache;

    @Autowired
    private Route53Client route53Client;

    @Resource
    private RedissonClient redissonClient;

    @Value("${aws.dns.baseDomain}")
    private String baseDomain;

    @Value("${aws.dns.hostedZoneId}")
    private String hostedZoneId;

    @Value("${aws.dns.healthcheck.port}")
    private int healthCheckPort;

    @Value("${aws.dns.healthcheck.page}")
    private String healthCheckPage;

    @Value("${aws.dns.continent.ipNum}")
    private int ipNum;

    public AtomicInteger atomicInteger = new AtomicInteger(0);

    /**
     * String country = IPUtils.getCountryByIp(ip);
     * String key = "check_id_" + country;
     * String healthId = dataStore.get(key);
     *
     * @param healthCheckId
     */
    public void deleteHealthCheck(String ip, DnsCountry dnsCountry, String healthCheckId) {
        if (healthCheckId != null && !healthCheckId.trim().equals("")) {
            try {
                GetHealthCheckRequest getHealthCheckRequest = GetHealthCheckRequest.builder()
                        .healthCheckId(healthCheckId)
                        .build();
                // first whether it still exist.
                GetHealthCheckResponse healthCheck = route53Client.getHealthCheck(getHealthCheckRequest);
                if (healthCheck.healthCheck() != null) {
                    boolean hasChildHealthChecks = healthCheck.healthCheck().healthCheckConfig().hasChildHealthChecks();
                    // 删除
                    DeleteHealthCheckRequest delRequest = DeleteHealthCheckRequest.builder()
                            .healthCheckId(healthCheckId)
                            .build();
                    // Delete the Health Check
                    route53Client.deleteHealthCheck(delRequest);

                    if (hasChildHealthChecks) {
                        // group check
                        for (String areaType : DnsConstants.AREA_TYPES) {
                            String area = null;
                            if (DnsConstants.COUNTRY.equals(areaType)) {
                                area = dnsCountry.getCountry();
                            } else if (DnsConstants.CONTINENT.equals(areaType)) {
                                area = dnsCountry.getContinent();
                            }
                            String groupKey = CacheUtils.buildGroupHealthCheckIdKey(area);
                            String value = redisCache.getCacheObject(groupKey);
                            if (!StringUtils.isEmpty(value) && healthCheckId.equals(value)) {
                                redisCache.deleteObject(groupKey);
                                log.warn("delete cache key: {} where healthCheckId : {} ", groupKey, healthCheckId);
                                break;
                            }
                        }

                    } else {
                        if (!StringUtils.isEmpty(ip)) {
                            // clear cache key.
                            String ipKey = CacheUtils.buildHealthCheckIdKey(ip);
                            redisCache.deleteObject(ipKey);
                        }
                    }
                }

            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * create health check first. inlude ip check and group(country/continent) check.
     *
     * @param ip
     * @return
     */
    public String[] createHealthCheck(String ip, String country) {
        String[] ret = new String[2];
        try {
            ret[0] = createIpHealthCheck(ip, country);
            ret[1] = createGroupHealthCheck(country, ret[0]);
            log.info("healthCheckId:{} groupHealthCheckId:{}", ret[0], ret[1]);
            return ret;
        } catch (Route53Exception e) {
            log.error(e.getMessage(), e);
        }
        return ret;
    }

    /**
     * create health check for one ip.
     *
     * @param ip
     * @param country
     * @return
     * @throws Route53Exception
     */
    private String createIpHealthCheck(String ip, String country) throws Route53Exception {
        String oldId = redisCache.getCacheObject(CacheUtils.buildHealthCheckIdKey(ip));
        if (!StringUtils.isEmpty(oldId)) {
            return oldId;
        }
        // You must use a unique CallerReference string every time you submit a CreateHostedZone request
        String callerReference = UUID.randomUUID().toString();
        HealthCheckConfig config = HealthCheckConfig.builder()
                .ipAddress(ip)
                .port(healthCheckPort)
                .resourcePath(healthCheckPage)
                .type("HTTP")
                .build();
        CreateHealthCheckRequest healthCheckRequest = CreateHealthCheckRequest.builder()
                .callerReference(callerReference)
                .healthCheckConfig(config)
                .build();
        // Create the Health Check and return the id value
        CreateHealthCheckResponse healthResponse = route53Client.createHealthCheck(healthCheckRequest);
        String healthCheckId = healthResponse.healthCheck().id();
        redisCache.setCacheObject(CacheUtils.buildHealthCheckIdKey(ip), healthCheckId);
        return healthCheckId;
    }

    /**
     * create group health check,which inclues some ip checks.
     *
     * @param area
     * @param childIds
     * @return
     * @throws Route53Exception
     */
    private String createGroupHealthCheck(String area, String... childIds) throws Route53Exception {
        String groupHealthCheckId = redisCache.getCacheObject(CacheUtils.buildGroupHealthCheckIdKey(area));
        if (!StringUtils.isEmpty(groupHealthCheckId)) {
            // add child check id to this group.
            addHealthCheckToGroup(groupHealthCheckId, childIds);
            return groupHealthCheckId;
        }
        // You must use a unique CallerReference string every time you submit a CreateHostedZone request
        String callerReference = UUID.randomUUID().toString();
        HealthCheckConfig config = HealthCheckConfig.builder()
                .type(HealthCheckType.CALCULATED)
                .childHealthChecks(childIds)
                .healthThreshold(1)
                .build();
        CreateHealthCheckRequest healthCheckRequest = CreateHealthCheckRequest.builder()
                .callerReference(callerReference)
                .healthCheckConfig(config)
                .build();
        // Create the Health Check and return the id value
        CreateHealthCheckResponse healthResponse = route53Client.createHealthCheck(healthCheckRequest);
        String healthCheckId = healthResponse.healthCheck().id();
        redisCache.setCacheObject(CacheUtils.buildGroupHealthCheckIdKey(area), healthCheckId);
        addHealthCheckToGroup(healthCheckId, childIds);
        return healthCheckId;
    }

    private String addHealthCheckToGroup(String groupHealthCheckId, String... childIds) throws Route53Exception {
        GetHealthCheckResponse response = getHealthCheck(groupHealthCheckId);
        if (response == null) {
            return "";
        }
        final List<String> strings = response.healthCheck().healthCheckConfig().childHealthChecks();
        List newList = new ArrayList();
        for (String s : strings) {
            newList.add(s);
        }
        for (String s : childIds) {
            if (!newList.contains(s)) {
                newList.add(s);
            }
        }
        UpdateHealthCheckRequest checkRequest = UpdateHealthCheckRequest.builder()
                .healthCheckId(groupHealthCheckId)
                .childHealthChecks(newList)
                .healthThreshold(1)
                .build();
        // Update the Health Check
        UpdateHealthCheckResponse healthResponse = route53Client.updateHealthCheck(checkRequest);
        for (String healthId : childIds) {
            // save to cache.
            String key = CacheUtils.buildHealthCheckIdParentsKey(healthId);
            redisCache.addIntoCacheSet(key, groupHealthCheckId);
        }
        return healthResponse.healthCheck().id();
    }

    private GetHealthCheckResponse getHealthCheck(String healthCheckId) {
        try {
            GetHealthCheckRequest request = GetHealthCheckRequest.builder().healthCheckId(healthCheckId).build();
            return route53Client.getHealthCheck(request);
        } catch (NoSuchHealthCheckException nhce) {
            log.warn("getHealthCheck id:{} not found!", healthCheckId);
            return null;
        } catch (RuntimeException re) {
            throw re;
        }
    }

    /**
     * create healthcheck and dns record
     *
     * @param ipChangeEvent
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000L, multiplier = 1))
    public void dnsRecord(IPChangeEvent ipChangeEvent) {
        // Check whether the IP address of the node is changed
        String nodeIpKey = CacheUtils.buildNodeIpKey(ipChangeEvent.getNodeId());
        String existIp = redisCache.getCacheObject(nodeIpKey);
        if (!StringUtils.isEmpty(existIp)) {
            // same ip, do nothing.
            if (existIp.equals(ipChangeEvent.getIp())) {
                log.warn("dnsRecord ====> IP[{}] has been processed. Do nothing!", existIp);
                return;
            }
            // when ip change,delete old ip's relational config info.
            DnsCountry dnsCountryOfOld = ipService.getCountryByIp(existIp);
            // delete old
            log.info("[step1.0] Proxy's ip changes({}->{}), Now delete delete Old HeathCheck And DnsRecord ... ", existIp,ipChangeEvent.getIp());
            deleteHeathCheckAndRecord(existIp, dnsCountryOfOld);
        }
        // create health check first. inlude ip check and group(country/continent) check.
        String[] checkIds = createHealthCheck(ipChangeEvent.getIp(), ipChangeEvent.getCountry());
        log.info("[step1.1]createHealthCheck(country) ===> event:{}", ipChangeEvent);
        String healthCheckId = checkIds[0];
        String countryHealthCheckId = checkIds[1];
        String continentHealthCheckId4Continent = null;
        boolean isNeedAddToContinet = isNeedAddToContinet(ipChangeEvent.getCountry());
        if (isNeedAddToContinet) {
            continentHealthCheckId4Continent = createGroupHealthCheck(ipChangeEvent.getContinent(), checkIds[0]);
            log.info("[step2.1]createGroupHealthCheck(continent) ===> event:{}", ipChangeEvent);
        }
        List<Change> changeList = buildDnsChangesByIp(ipChangeEvent, healthCheckId, countryHealthCheckId, continentHealthCheckId4Continent, isNeedAddToContinet);
        log.info("[step 3] start upseert dnsRecords: {}", changeList);
        ChangeBatch changeBatch = ChangeBatch.builder().changes(changeList).build();
        ChangeResourceRecordSetsRequest crrsr = ChangeResourceRecordSetsRequest.builder().hostedZoneId(hostedZoneId).changeBatch(changeBatch).build();
        ChangeResourceRecordSetsResponse response = route53Client.changeResourceRecordSets(crrsr);
        String changeId = response.changeInfo().id();
        // save to cache.
        redisCache.setCacheObject(nodeIpKey, ipChangeEvent.getIp());
        if (isNeedAddToContinet) {
            // add into continent ip set.
            String ipsKey = CacheUtils.buildContinentIpsKey(ipChangeEvent.getContinent());
            redisCache.addIntoCacheSet(ipsKey, ipChangeEvent.getIp());
        }
        log.info("[step 3] finish dnsRecord ===> event:{} changeId:{} ", ipChangeEvent, changeId);
        // set new blockHeight
        redisCache.setCacheObject(EthUtils.INIT_SET_MINER_EVT, ipChangeEvent.getLastesBlockNumber());
        log.info("[step 4] update the lastest blockNumber to {} , ip: {}", ipChangeEvent.getLastesBlockNumber(), ipChangeEvent.getIp());
    }

    @Recover
    public void recover4DnsRecord(Exception e, IPChangeEvent ipChangeEvent) {
        log.error("DNS_ERROR==>[IP:{}] after try 3 time ,updateDnsRecord fail:{}", ipChangeEvent.getIp(), e.getMessage(), e);
    }

    /**
     * if the country is the Continent's Represent
     *
     * @param country
     * @return
     */
    private boolean isContinentRepresent(String country) {
        String belongContinent = InitApplicationData.countryAndContinentMap.get(country);
        if (!StringUtils.isEmpty(belongContinent)) {
            return true;
        }
        return false;
    }

    private boolean isNeedAddToContinet(String country) {
        if (isContinentRepresent(country)) {
            // this country should be continet's represent.
            // check if ip num exceeds the config number.
            String belongContinent = InitApplicationData.countryAndContinentMap.get(country);
            String ipsKey = CacheUtils.buildContinentIpsKey(belongContinent);
            Set<Object> cacheSet = redisCache.getCacheSet(ipsKey);
            int num = cacheSet == null ? 0 : cacheSet.size();
            if (num < ipNum) {
                return true;
            }
        }
        return false;
    }

    /**
     * create a CName Record
     *
     * @param ipChangeEvent
     * @param areaType
     * @return
     */
    private Change createCNameRecord(IPChangeEvent ipChangeEvent, String areaType, String groupHealthCheckId) {
        GeoLocation geoLocation = null;
        String area = "";
        if (DnsConstants.COUNTRY.equals(areaType)) {
            area = ipChangeEvent.getCountry();
            geoLocation = GeoLocation.builder().countryCode(area).build();
        } else {
            area = ipChangeEvent.getContinent();
            geoLocation = GeoLocation.builder().continentCode(area).build();
        }
        List<ResourceRecord> resourceRecords = new ArrayList<>();
        String cnameDomain = DnsConstants.buildDomainHost(area) + baseDomain;
        resourceRecords.add(ResourceRecord.builder().value(cnameDomain).build());
        ResourceRecordSet rrs = ResourceRecordSet.builder()
                .name(DnsConstants.buildUsedDomainHost() + baseDomain)
                .type(RRType.CNAME)
                .resourceRecords(resourceRecords)
                .ttl(60L) // seconds
                .geoLocation(geoLocation)
                .healthCheckId(groupHealthCheckId)
                .setIdentifier(area).build(); // set country/continent
        return Change.builder().action(ChangeAction.UPSERT).resourceRecordSet(rrs).build();
    }

    /**
     * buildDnsChangesByIp
     *
     * @param ipChangeEvent
     * @return
     */
    private List<Change> buildDnsChangesByIp(IPChangeEvent ipChangeEvent, String healthCheckId
            , String countryHealthCheckId, String continentHealthCheckId, boolean isNeedAddToContinet) {
        List<Change> changeList = new ArrayList<>();
        // add CNAME for COUNTRY
        Change a1 = createCNameRecord(ipChangeEvent, DnsConstants.COUNTRY, countryHealthCheckId);
        changeList.add(a1);
        // add A for COUNTRY'cname
        Change a2 = createARecord(ipChangeEvent, DnsConstants.COUNTRY, healthCheckId);
        changeList.add(a2);
        if (isNeedAddToContinet) {
            // add CNAME for CONTINENT
            Change c21 = createCNameRecord(ipChangeEvent, DnsConstants.CONTINENT, continentHealthCheckId);
            changeList.add(c21);
            // add A for CONTINENT'cname
            Change c22 = createARecord(ipChangeEvent, DnsConstants.CONTINENT, healthCheckId);
            changeList.add(c22);
        }
        return changeList;
    }

    /**
     * create A DnsRecord.
     *
     * @param ipChangeEvent
     * @param areaType
     * @return
     */
    private Change createARecord(IPChangeEvent ipChangeEvent, String areaType, String healthCheckId) {
        String area = "";
        if (DnsConstants.COUNTRY.equals(areaType)) {
            area = ipChangeEvent.getCountry();
        } else {
            area = ipChangeEvent.getContinent();
        }
        List<ResourceRecord> resourceRecords = new ArrayList<>();
        // value is ip
        resourceRecords.add(ResourceRecord.builder().value(ipChangeEvent.getIp()).build());
        ResourceRecordSet rrs = ResourceRecordSet.builder()
                .name(DnsConstants.buildDomainHost(area) + baseDomain)
                .type(ipChangeEvent.isIp4()?RRType.A:RRType.AAAA) // ipv4 or ipv6
                .resourceRecords(resourceRecords)
                .ttl(60L) // seconds
                .multiValueAnswer(true)
                .healthCheckId(healthCheckId)
                .setIdentifier(ipChangeEvent.getIp()).build(); // set ip as Identifier
        return Change.builder().action(ChangeAction.UPSERT).resourceRecordSet(rrs).build();
    }

    /**
     * @param ip
     * @param dnsCountry
     */
    private void deleteHeathCheckAndRecord(String ip, DnsCountry dnsCountry) {
        String ipKey = CacheUtils.buildHealthCheckIdKey(ip);
        String healthCheckId = redisCache.getCacheObject(ipKey);
        if (StringUtils.isEmpty(healthCheckId)) {
            log.warn("[0-delBoth][IP:{}] ===> healthCheckId is null,so exit.", ip);
            return;
        }
        log.warn("[0-delBoth][IP:{}] ===> will delete healthCheck and dnsRecord of current ip!!!", ip);
        // find out healthCheckId 's parents.
        String key = CacheUtils.buildHealthCheckIdParentsKey(healthCheckId);
        Set<String> parentCheckIdSet = redisCache.getCacheSet(key);
        if (parentCheckIdSet != null && !parentCheckIdSet.isEmpty()) {
            for (String groupCheckid : parentCheckIdSet) {
                removeHealthCheckFromGroup(dnsCountry, groupCheckid, healthCheckId);
                log.info("[1-delHealthCheck][IP:{}]  ===> remove healthId:{} from group:{}, include cache.", ip, healthCheckId, groupCheckid);
            }
            // clear cache, all parents reset to empty.
            redisCache.deleteObject(key);
        }
        // now delete myself
        log.info("[1-delHealthCheck][IP:{}]  ===> start delete my healthId: {}", ip, healthCheckId);
        this.deleteHealthCheck(ip, dnsCountry, healthCheckId);
        log.info("[1-delHealthCheck][IP:{}]  ===> end   delete my healthId: {}", ip, healthCheckId);

        // only delete dns a record. no need to delete cname record.
        List<Change> deleteChangeList = new ArrayList<>();
        Change delete1 = buildDnsARecordDeleteChange(ip, dnsCountry, DnsConstants.COUNTRY, healthCheckId);
        if (delete1 != null) {
            deleteChangeList.add(delete1);
        }
        boolean isContinentRepresent = isContinentRepresent(dnsCountry.getCountry());
        if (isContinentRepresent) {
            // the country is the reprent of its continent.
            Change delete2 = buildDnsARecordDeleteChange(ip, dnsCountry, DnsConstants.CONTINENT, healthCheckId);
            if (delete2 != null) {
                deleteChangeList.add(delete2);
            }
        }
        if (deleteChangeList.isEmpty()) {
            // no changes
            return;
        }
        log.info("[2-delDnsRecord][IP:{}]  ===> start delete DnsRecords: {}", ip, deleteChangeList);
        ChangeBatch changeBatch = ChangeBatch.builder().changes(deleteChangeList).build();
        ChangeResourceRecordSetsRequest request = ChangeResourceRecordSetsRequest.builder().hostedZoneId(hostedZoneId).changeBatch(changeBatch).build();
        String changeId = "";
        try {
            ChangeResourceRecordSetsResponse response = route53Client.changeResourceRecordSets(request);
            changeId = response.changeInfo().id();
        } catch (Exception e) {
            log.error("[2-delDnsRecord][IP:{}]  error: {}", ip, e.getMessage(), e);
        }
        String ipsKey = CacheUtils.buildContinentIpsKey(dnsCountry.getContinent());
        log.info("[2-delDnsRecord][IP:{}]  ===> finish delete changeId: {}", ip, changeId);
        redisCache.removeIntoCacheSet(ipsKey, ip);
    }

    private String removeHealthCheckFromGroup(DnsCountry dnsCountry, String groupHealthCheckId, String... childIds) throws Route53Exception {
        GetHealthCheckResponse response = getHealthCheck(groupHealthCheckId);
        if (response == null) {
            return "";
        }
        final List<String> strings = response.healthCheck().healthCheckConfig().childHealthChecks();
        List newList = new ArrayList();
        for (String s : strings) {
            newList.add(s);
        }
        for (String id : childIds) {
            if (newList.contains(id)) {
                newList.remove(id);
            }
        }
        if (newList.size() == 0) {
            // delete group check.
            this.deleteHealthCheck(null, dnsCountry, groupHealthCheckId);
            log.info("[{}] group is empty,delete it!", groupHealthCheckId);
        } else {
            UpdateHealthCheckRequest checkRequest = UpdateHealthCheckRequest.builder()
                    .healthCheckId(groupHealthCheckId)
                    .childHealthChecks(newList)
                    .healthThreshold(1)
                    .build();
            // Update the Health Check
            UpdateHealthCheckResponse healthResponse = route53Client.updateHealthCheck(checkRequest);
            log.info("[{}] group child checks num: {},update it!", groupHealthCheckId, newList.size());
            return healthResponse.toString();
        }
        return "";
    }

    /**
     * buildDnsARecordDeleteChange
     *
     * @param ip
     * @param dnsCountry
     * @param areaType
     * @param healthCheckId
     * @return
     */
    private Change buildDnsARecordDeleteChange(String ip, DnsCountry dnsCountry, String areaType, String healthCheckId) {
        String area = "";
        if (DnsConstants.COUNTRY.equals(areaType)) {
            area = dnsCountry.getCountry();
        } else {
            area = dnsCountry.getContinent();
        }
        try {
            String ipType = IPUtils.ipType(ip);
            if (StringUtils.isEmpty(ipType)) {
                log.warn("{} 's type={} is null,exit!", ip, ipType);
                return null;
            }
            List<ResourceRecord> resourceRecords = new ArrayList<>();
            // value is ip
            resourceRecords.add(ResourceRecord.builder().value(ip).build());
            ResourceRecordSet rrs = ResourceRecordSet.builder()
                    .name(DnsConstants.buildDomainHost(area) + baseDomain)
                    .type("ip4".equals(ipType)?RRType.A:RRType.AAAA)
                    .ttl(60L)
                    .resourceRecords(resourceRecords)
                    .multiValueAnswer(true)
                    .healthCheckId(healthCheckId)
                    .setIdentifier(ip).build(); // set ip as Identifier
            return Change.builder().action(ChangeAction.DELETE).resourceRecordSet(rrs).build();
        } catch (Exception e) {
            log.error("", e);
        }
        return null;
    }
}
