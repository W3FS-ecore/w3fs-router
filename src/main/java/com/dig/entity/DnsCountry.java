package com.dig.entity;

import java.io.Serializable;

public class DnsCountry implements Serializable {
    private String country;
    private String continent;

    public DnsCountry(String country, String continent) {
        this.country = country;
        this.continent = continent;
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
}
