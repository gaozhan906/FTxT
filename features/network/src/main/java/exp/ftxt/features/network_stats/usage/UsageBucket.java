package exp.ftxt.features.network_stats.usage;

public final class UsageBucket {
    public final long startMs;
    public final long endMs;
    public final long rxBytes;
    public final long txBytes;

    public UsageBucket(long startMs, long endMs, long rxBytes, long txBytes) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
    }

    public long totalBytes() {
        return rxBytes + txBytes;
    }
}
