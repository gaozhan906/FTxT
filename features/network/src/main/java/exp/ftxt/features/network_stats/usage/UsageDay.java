package exp.ftxt.features.network_stats.usage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UsageDay {
    public final String date;
    public final long rxBytes;
    public final long txBytes;
    public final long wifiRxBytes;
    public final long wifiTxBytes;
    public final long mobileRxBytes;
    public final long mobileTxBytes;
    public final boolean hasData;
    public final UsageSource source;
    public final List<UsageApp> apps;

    public UsageDay(String date, long rxBytes, long txBytes,
                    long wifiRxBytes, long wifiTxBytes,
                    long mobileRxBytes, long mobileTxBytes,
                    boolean hasData, UsageSource source, List<UsageApp> apps) {
        this.date = date;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
        this.wifiRxBytes = wifiRxBytes;
        this.wifiTxBytes = wifiTxBytes;
        this.mobileRxBytes = mobileRxBytes;
        this.mobileTxBytes = mobileTxBytes;
        this.hasData = hasData;
        this.source = source;
        this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
    }

    public long totalBytes() {
        return rxBytes + txBytes;
    }

    public long wifiTotalBytes() {
        return wifiRxBytes + wifiTxBytes;
    }

    public long mobileTotalBytes() {
        return mobileRxBytes + mobileTxBytes;
    }
}
