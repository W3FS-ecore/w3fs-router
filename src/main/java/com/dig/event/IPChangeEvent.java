package com.dig.event;

public class IPChangeEvent extends BaseEvent {
    private String ip;
    private boolean isIp4;
    private String nodeId;
    private String country;
    private String continent;


    public IPChangeEvent(String ip, boolean isIp4, String nodeId, String country, String continent) {
        this.ip = ip;
        this.isIp4 = isIp4;
        this.nodeId = nodeId;
        this.country = country;
        this.continent = continent;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public boolean isIp4() {
        return isIp4;
    }

    public void setIp4(boolean ip4) {
        isIp4 = ip4;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getContinent() {
        return continent;
    }

    public void setContinent(String continent) {
        this.continent = continent;
    }

    @Override
    public String toString() {
        return "IPChangeEvent{" +
                "ip='" + ip + '\'' +
                ", isIp4=" + isIp4 +
                ", nodeId='" + nodeId + '\'' +
                ", country='" + country + '\'' +
                ", continent='" + continent + '\'' +
                '}';
    }
}
