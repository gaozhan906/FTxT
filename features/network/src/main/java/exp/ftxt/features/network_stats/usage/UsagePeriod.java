package exp.ftxt.features.network_stats.usage;

public enum UsagePeriod {
    TODAY(1),
    DAYS_7(7),
    DAYS_30(30);

    public final int days;

    UsagePeriod(int days) {
        this.days = days;
    }
}
