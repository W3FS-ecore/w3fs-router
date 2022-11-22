package com.dig.controller;

import cn.hutool.core.thread.ThreadUtil;
import com.dig.common.redis.RedisCache;
import com.dig.service.IRouteService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.route53.Route53Client;

import javax.annotation.Resource;

/**
 * @author lp
 * @date 2022-06-21 10:25
 */
@RestController
@Slf4j
public class TestController {

    @Autowired
    private IRouteService routeService;

    @Autowired
    private Route53Client route53Client;

    @Value("${aws.dns.baseDomain}")
    private String domain;

    @Value("${aws.dns.hostedZoneId}")
    private String hostZoneId;

    @Value("${aws.dns.healthcheck.port}")
    private int healthCheckPort;

    @Value("${aws.dns.healthcheck.page}")
    private String healthCheckPage;

    @Resource
    private RedisCache redisCache;
    @Resource
    private RedissonClient redissonClient;

    @GetMapping("/health")
    public String health(){
        return "ok";
    }

    @GetMapping("/testRedis")
    public Object testRedis(String str){
        redisCache.setCacheObject(str,str);
        Object cacheObject = redisCache.getCacheObject(str);
        return cacheObject;
    }

    @GetMapping("/redisLock/{str}")
    public Object redisLock(@PathVariable String str){
        RLock lock = redissonClient.getLock(str);
        try {
            boolean b = lock.tryLock();
            if(!b){
                return "cannot get lock,return.";
            }
            // sleep 2s
            log.info(str+"get a lock,sleep 2s....");
            ThreadUtil.sleep(2000);
            log.info(str+"release a lock.");
        } finally {
            if(lock.isLocked() && lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
        return str+":unlock.  OK!";
    }

//    @GetMapping("/aws/event/{ip}/{nodeId}")
//    public String event(@PathVariable String ip,@PathVariable String nodeId) {
//        log.info("ip:{}", ip);
//        DnsCountry country = IPUtils.getInstance().getCountryByIp(ip);
//        QueueManager.addEvent(new IPChangeEvent(ip, nodeId, country.getCountry(), country.getContinent()));
//        return "ok";
//    }
}
