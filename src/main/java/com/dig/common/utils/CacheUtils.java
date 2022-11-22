package com.dig.common.utils;

public class CacheUtils {

    public static String buildHealthCheckIdKey(String ip) {
        return "dns:health:one:" + ip;
    }

    public static String buildGroupHealthCheckIdKey(String area) {
        return "dns:health:group:" + area;
    }

    public static String buildHealthCheckIdParentsKey(String healthCheckId) {
        return "dns:health:parents:" + healthCheckId;
    }

    public static String buildContinentIpsKey(String continent) {
        return "dns:continent:ips:" + continent;
    }

    public static String buildNodeIpKey(String nodeId) {
        return "dns:node:ip:" + nodeId;
    }
}
