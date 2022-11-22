package com.dig.event;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class QueueManager {

    public static final int QUEUE_SIZE = 1000000;

    public static final LinkedBlockingQueue eventQueue = new LinkedBlockingQueue(QUEUE_SIZE);

    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10, 50, 120,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(10000));

    public static String getKey(IPChangeEvent event) {
        return event.getContinent().toLowerCase();
    }

    public static void addEvent(BaseEvent event) {
        if (event instanceof IPChangeEvent) {
            IPChangeEvent e = (IPChangeEvent) event;
            String ip = e.getIp();
            try {
                log.info("add event: {}", event);
                eventQueue.put(event);
            } catch (InterruptedException ex) {
                log.error("put ip:"+ip+"error:"+ex.getMessage(),ex);
            }
        }
    }

    public static void initConsumeQueue() {
        threadPoolExecutor.submit(new EventConsumer(eventQueue));
    }
}
