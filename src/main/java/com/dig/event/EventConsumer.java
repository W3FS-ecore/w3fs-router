package com.dig.event;

import com.dig.common.utils.SpringUtils;
import com.dig.service.IRouteService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class EventConsumer implements Runnable{

    private LinkedBlockingQueue eventQueue;

    public EventConsumer(LinkedBlockingQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    public LinkedBlockingQueue getEventQueue() {
        return eventQueue;
    }

    public void setEventQueue(LinkedBlockingQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    @Override
    public void run() {
        while(true) {
            try {
                BaseEvent event = (BaseEvent)eventQueue.take();
                if (event instanceof IPChangeEvent) {
                    IPChangeEvent e = (IPChangeEvent) event;
                    IRouteService routeService = SpringUtils.getBean(IRouteService.class);
                    RedissonClient redissonClient  = SpringUtils.getBean(RedissonClient.class);
                    long startTime = System.currentTimeMillis();
                    RLock lock = redissonClient.getLock("lock:ip:" + e.getIp());
                    try {
                        // first need gain one lock.
                        if (!lock.tryLock()) {
                            log.warn("[EC->{}]: {} cannot get lock! Return.", e.getIp(), Thread.currentThread().getName());
                            return;
                        }
                        log.info("[EC->{}]: Gain the lock,Now start create/update dnsRecord.....", e.getIp());
                        routeService.dnsRecord(e);
                    } finally {
                        if(lock.isLocked() && lock.isHeldByCurrentThread()) {
                            lock.unlock();
                            log.info("[EC->{}]: The method call of dnsRecord is completed (cost {}ms) and the lock is released at the same time!", e.getIp(), (System.currentTimeMillis() - startTime));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[EC->ERROR] Process event in eventQueue occur error: "+e.getMessage(), e);
            }
        }
    }
}
