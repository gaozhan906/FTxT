package exp.ftxt.features.network_stats.usage;

public enum UsageNetwork {
    ALL,
    WIFI,
    MOBILE;

    public boolean includesWifi() {
        return this == ALL || this == WIFI;
    }

    public boolean includesMobile() {
        return this == ALL || this == MOBILE;
    }
}
