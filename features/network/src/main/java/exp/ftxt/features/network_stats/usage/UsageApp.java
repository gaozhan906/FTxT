package exp.ftxt.features.network_stats.usage;

public final class UsageApp {
    public final int uid;
    public final String packageName;
    public final long rxBytes;
    public final long txBytes;

    public UsageApp(int uid, String packageName, long rxBytes, long txBytes) {
        this.uid = uid;
        this.packageName = packageName;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
    }

    public long totalBytes() {
        return rxBytes + txBytes;
    }
}
