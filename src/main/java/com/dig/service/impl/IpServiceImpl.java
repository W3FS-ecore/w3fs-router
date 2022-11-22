package com.dig.service.impl;

import com.dig.common.utils.IPUtils;
import com.dig.entity.DnsCountry;
import com.dig.entity.IPEntity;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetAddress;

@Service
public class IpServiceImpl {

    private static Logger logger = LoggerFactory.getLogger(IpServiceImpl.class);

    // geo ip reader
    private static DatabaseReader reader;

    IpServiceImpl(@Value("${file.ip-db}") String ipDbFile) {
        initDatabase(ipDbFile);
    }

    /**
     * init database by file
     * @param file
     */
    private void initDatabase(String file) {
        File database = null;
        try {
            // absolute file path
            database = new File(file);
            logger.info("load file {} sucessful.", file);
            reader = new DatabaseReader.Builder(database).build();
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        }
    }

    /**
     * get country from
     *
     * @param ip
     * @return
     */
    public DnsCountry getCountryByIp(String ip) {
        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = reader.city(ipAddress);
            Country country = response.getCountry();
            Continent continent = response.getContinent();
            if (country==null || continent==null
                    || StringUtils.isEmpty(country.getIsoCode()) || StringUtils.isEmpty(continent.getCode())) {
                return IPUtils.defaultCountry;
            }
            return new DnsCountry(country.getIsoCode(), continent.getCode());
        }catch (Exception e) {
            logger.error("getCountryByIp error:{}",e.getMessage());
        }
        return IPUtils.defaultCountry;
    }

    /**
     * @param ip
     * @return
     */
    public IPEntity getIPMsg(String ip) {
        IPEntity msg = new IPEntity();
        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = reader.city(ipAddress);
            Continent continent = response.getContinent();
            logger.info(continent.getName()+"  "+continent.getCode());
            Country country = response.getCountry();
            Subdivision subdivision = response.getMostSpecificSubdivision();
            City city = response.getCity();
            Postal postal = response.getPostal();
            Location location = response.getLocation();
            msg.setCountryName(country.getNames().get("zh-CN"));
            msg.setCountryCode(country.getIsoCode());
            msg.setProvinceName(subdivision.getNames().get("zh-CN"));
            msg.setProvinceCode(subdivision.getIsoCode());
            msg.setCityName(city.getNames().get("zh-CN"));
            msg.setPostalCode(postal.getCode());
            msg.setLongitude(location.getLongitude());
            msg.setLatitude(location.getLatitude());
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
            return null;
        } catch (GeoIp2Exception e) {
            logger.error("GeoIp2Exception error",e);
            return null;
        }
        return msg;
    }


    public static void main(String[] args) {
        IpServiceImpl ipService = new IpServiceImpl("D:/GeoLite2-City.mmdb");

        String[] ips = new String[] {
                "119.123.225.83", "44.202.6.139","222.164.138.13",
                "2001:df7:7c00::","2001:4860:4860::8888",
                "2409:8b43:311b:b6e0:211:32ff:fe12:3456",
                "2000:0000:0000:0000:0001:2345:6789:abcd"
        };
        for (String ip:ips) {
            System.out.println("==>ip:"+ip);
            IPEntity ipMsg = ipService.getIPMsg(ip);
            System.out.println(ipMsg.toString());
            System.out.println("#################################################");
            System.out.println("");
        }
    }

}
