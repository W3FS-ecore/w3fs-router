package com.dig.common;

import java.util.Locale;

public class DnsConstants {

    public static final String DOMAIN_USED = "service";

    public static final String DOMAIN_DEFAULT = "dft0";

    public static final String DOMAIN_INNER = "inner-";

    public static final String HEALTH_CHECK_GROUP = "group-";

    public static String buildUsedDomainHost() {
        return DOMAIN_USED + ".";
    }

    public static String buildDomainHost(String area) {
        return DOMAIN_INNER + area.toLowerCase() + ".";
    }

    public static String buildGroupName(String area) {
        return HEALTH_CHECK_GROUP + area + ".";
    }

    public static final String COUNTRY = "country";

    public static final String CONTINENT = "continent";

    public static final String[] AREA_TYPES = new String[] {DnsConstants.COUNTRY,DnsConstants.CONTINENT};

    public static final String[] CONTINENTS = new String[] {"AF","AN","AS","EU","NA","OC","SA"};
}
