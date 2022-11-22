package com.dig.common.utils;

import com.dig.entity.DnsCountry;
import com.dig.init.InitApplicationData;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IPUtils {

    public static final DnsCountry defaultCountry = new DnsCountry("US", "NA");

    /**
     *
     * @param addr  multiAddr or ip address
     * @return
     */
    public static String ipType(String addr) {
        if (StringUtils.isEmpty(addr)) {
            return "";
        }
        if (!addr.contains("/")) {
            return ipTypeByIpAddress(addr);
        }
        String[] parts = addr.substring(1).split("/");
        if (parts[0].equals("ip4")) {
            return "ip4";
        } if (parts[0].equals("ip6")) {
            return "ip6";
        }
        return "";
    }

    private static String ipTypeByIpAddress(String address) {
        try {
            final InetAddress inetAddress = InetAddress.getByName(address);
            if (inetAddress instanceof Inet6Address) {
                return "ip6";
            } else if(inetAddress instanceof Inet4Address) {
                return "ip4";
            }
        } catch (UnknownHostException e) {
            return "";
        }
        return "";
    }
}
