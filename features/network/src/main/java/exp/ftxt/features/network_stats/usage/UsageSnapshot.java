package exp.ftxt.features.network_stats.usage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UsageSnapshot {
    public final UsageQuery query;
    public final long startMs;
    public final long endMs;
    public final long totalRxBytes;
    public final long totalTxBytes;
    public final long averageRxBytes;
    public final long averageTxBytes;
    public final boolean averageAvailable;
    public final UsageBucket peak;
    public final List<UsageBucket> buckets;
    public final List<UsageDay> days;
    public final List<UsageApp> apps;
    public final boolean partial;
    public final boolean mobileUnavailable;
    public final UsageSource source;

    public UsageSnapshot(UsageQuery query, long startMs, long endMs,
                         long totalRxBytes, long totalTxBytes,
                         long averageRxBytes, long averageTxBytes, boolean averageAvailable,
                         UsageBucket peak, List<UsageBucket> buckets,
                         List<UsageDay> days, List<UsageApp> apps,
                         boolean partial, boolean mobileUnavailable, UsageSource source) {
        this.query = query;
        this.startMs = startMs;
        this.endMs = endMs;
        this.totalRxBytes = totalRxBytes;
        this.totalTxBytes = totalTxBytes;
        this.averageRxBytes = averageRxBytes;
        this.averageTxBytes = averageTxBytes;
        this.averageAvailable = averageAvailable;
        this.peak = peak;
        this.buckets = Collections.unmodifiableList(new ArrayList<>(buckets));
        this.days = Collections.unmodifiableList(new ArrayList<>(days));
        this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
        this.partial = partial;
        this.mobileUnavailable = mobileUnavailable;
        this.source = source;
    }

    public long totalBytes() {
        return totalRxBytes + totalTxBytes;
    }
}
