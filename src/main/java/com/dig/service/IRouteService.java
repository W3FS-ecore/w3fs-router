package com.dig.service;

import com.dig.event.IPChangeEvent;

import java.util.Set;

public interface IRouteService {

    public String[] createHealthCheck(String ip, String country);

    public void dnsRecord(IPChangeEvent ipChangeEvent);
}
