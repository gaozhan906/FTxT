package exp.ftxt.features.network_stats.app_usage;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConnectingAppSource {

    public static final int MODE_TOP_TALKERS = 0;
    public static final int MODE_FOREGROUND = 1;

    public static final class AppTraffic {
        public final int uid;
        public final String packageName;
        public final String label;
        public final long rxBytesPerSec;
        public final long txBytesPerSec;
        public final boolean foreground;
        public final Drawable icon;

        public AppTraffic(int uid, String packageName, String label,
                          long rxBytesPerSec, long txBytesPerSec,
                          boolean foreground, Drawable icon) {
            this.uid = uid;
            this.packageName = packageName;
            this.label = label;
            this.rxBytesPerSec = rxBytesPerSec;
            this.txBytesPerSec = txBytesPerSec;
            this.foreground = foreground;
            this.icon = icon;
        }

        public long totalBytesPerSec() {
            return rxBytesPerSec + txBytesPerSec;
        }
    }

    private static final long FOREGROUND_WINDOW_MS = 10000L;

    private final Context context;
    private final PackageManager packageManager;
    private final UsageStatsManager usageStatsManager;
    private final NetworkStatsManager networkStatsManager;
    private final TelephonyManager telephonyManager;
    private final String selfPackage;
    private final int selfUid;

    private final LruCache<String, Drawable> iconCache = new LruCache<>(48);
    private final Map<String, String> labelCache = new HashMap<>();
    private String cachedForeground;
    private long cachedForegroundAt;

    private long lastQueryWallTimeMs;
    private long lastQueryElapsedMs;

    public ConnectingAppSource(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.usageStatsManager =
                (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.networkStatsManager =
                (NetworkStatsManager) this.context.getSystemService(Context.NETWORK_STATS_SERVICE);
        this.telephonyManager =
                (TelephonyManager) this.context.getSystemService(Context.TELEPHONY_SERVICE);
        this.selfPackage = this.context.getPackageName();
        this.selfUid = android.os.Process.myUid();
    }

    public List<AppTraffic> query(int mode, int maxRows, long thresholdBps, long intervalMs)
            throws Exception {
        // NetworkStatsManager expects wall-clock timestamps, while the rate denominator
        // must use a monotonic clock so clock/time-zone changes cannot skew the result.
        long queryEndWallMs = System.currentTimeMillis();
        long queryEndElapsedMs = SystemClock.elapsedRealtime();
        long queryStartWallMs = lastQueryWallTimeMs > 0L
                && lastQueryWallTimeMs < queryEndWallMs
                ? lastQueryWallTimeMs
                : queryEndWallMs - Math.max(500L, intervalMs);

        Map<Integer, long[]> traffic = sample(queryStartWallMs, queryEndWallMs);
        long elapsedMs = lastQueryElapsedMs > 0L
                ? queryEndElapsedMs - lastQueryElapsedMs
                : queryEndWallMs - queryStartWallMs;

        // Advance the window only after a successful sample. If both network queries fail,
        // the exception is propagated and the next tick retries the same interval.
        lastQueryWallTimeMs = queryEndWallMs;
        lastQueryElapsedMs = queryEndElapsedMs;

        if (elapsedMs <= 0L) return Collections.emptyList();

        int foregroundUid = -1;
        String foregroundPackage = mode == MODE_FOREGROUND
                ? foregroundPackage(queryEndWallMs) : null;
        if (foregroundPackage != null) {
            try {
                foregroundUid = packageManager.getPackageUid(foregroundPackage, 0);
            } catch (Exception ignored) {
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Integer, long[]> entry : traffic.entrySet()) {
            int uid = entry.getKey();
            if (uid < 0 || uid == selfUid) continue;

            long[] values = entry.getValue();
            long rxBps = values[0] * 1000L / elapsedMs;
            long txBps = values[1] * 1000L / elapsedMs;
            if (rxBps + txBps < Math.max(0L, thresholdBps)) continue;

            boolean foreground = uid == foregroundUid;
            if (mode == MODE_FOREGROUND && !foreground) continue;

            candidates.add(new Candidate(uid, rxBps, txBps, foreground));
        }

        if (candidates.isEmpty()) return Collections.emptyList();

        Collections.sort(candidates, (a, b) -> Long.compare(
                b.rxBps + b.txBps, a.rxBps + a.txBps));

        int limit = Math.min(Math.max(1, maxRows), candidates.size());
        List<AppTraffic> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Candidate candidate = candidates.get(i);
            String[] identity = resolveIdentity(candidate.uid);
            result.add(new AppTraffic(
                    candidate.uid,
                    identity[0],
                    identity[1],
                    candidate.rxBps,
                    candidate.txBps,
                    candidate.foreground,
                    resolveIcon(identity[0])
            ));
        }
        return result;
    }

    private Map<Integer, long[]> sample(long startMs, long endMs) throws Exception {
        if (networkStatsManager == null) {
            throw new IllegalStateException("NetworkStatsManager tidak tersedia");
        }

        Map<Integer, long[]> totals = new HashMap<>();
        boolean queried = false;
        Exception lastError = null;

        try {
            queried |= collect(networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_WIFI, null, startMs, endMs), totals);
        } catch (Exception e) {
            lastError = e;
        }

        String subscriberId = subscriberId();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || subscriberId != null) {
            try {
                queried |= collect(networkStatsManager.querySummary(
                        ConnectivityManager.TYPE_MOBILE, subscriberId, startMs, endMs), totals);
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (!queried) {
            if (lastError != null) throw lastError;
            throw new IllegalStateException("Statistik trafik per UID tidak tersedia");
        }
        return totals;
    }

    private String subscriberId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || telephonyManager == null) {
            return null;
        }
        try {
            return telephonyManager.getSubscriberId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean collect(NetworkStats stats, Map<Integer, long[]> totals) {
        if (stats == null) return false;
        try {
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.getNextBucket(bucket)) {
                int uid = bucket.getUid();
                if (uid < 0) continue;

                long[] values = totals.get(uid);
                if (values == null) {
                    values = new long[2];
                    totals.put(uid, values);
                }
                // querySummary can return separate foreground/background buckets.
                // Sum both states so the UID total is not under-reported.
                values[0] += Math.max(0L, bucket.getRxBytes());
                values[1] += Math.max(0L, bucket.getTxBytes());
            }
            return true;
        } finally {
            stats.close();
        }
    }

    private String foregroundPackage(long nowMs) {
        if (usageStatsManager == null) return cachedForeground;
        UsageEvents events = null;
        try {
            events = usageStatsManager.queryEvents(
                    nowMs - FOREGROUND_WINDOW_MS, nowMs);
            if (events == null) return cachedForeground;

            UsageEvents.Event event = new UsageEvents.Event();
            String latestForegroundPackage = null;
            long latestForegroundAt = -1L;
            boolean cachedBackgrounded = false;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                String packageName = event.getPackageName();
                if (packageName == null) continue;
                long timestamp = event.getTimeStamp();

                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (timestamp >= latestForegroundAt) {
                        latestForegroundAt = timestamp;
                        latestForegroundPackage = packageName;
                    }
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND
                        && packageName.equals(cachedForeground)
                        && timestamp >= cachedForegroundAt) {
                    cachedBackgrounded = true;
                }
            }

            if (latestForegroundPackage != null && latestForegroundAt >= cachedForegroundAt) {
                cachedForeground = latestForegroundPackage.equals(selfPackage)
                        ? null : latestForegroundPackage;
                cachedForegroundAt = latestForegroundAt;
            } else if (cachedBackgrounded) {
                cachedForeground = null;
                cachedForegroundAt = 0L;
            }
        } catch (Exception ignored) {
        }
        return cachedForeground;
    }

    private String[] resolveIdentity(int uid) {
        String packageName = null;
        try {
            String[] packages = packageManager.getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                packageName = packages[0];
                for (String candidate : packages) {
                    if (!candidate.equals(selfPackage)) {
                        packageName = candidate;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (packageName == null) {
            return new String[]{null, "UID " + uid};
        }

        String label = labelCache.get(packageName);
        if (label == null) {
            try {
                ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
                label = packageManager.getApplicationLabel(info).toString();
            } catch (Exception ignored) {
                label = packageName;
            }
            labelCache.put(packageName, label);
        }
        return new String[]{packageName, label};
    }

    private Drawable resolveIcon(String packageName) {
        if (packageName == null) return null;
        Drawable cached = iconCache.get(packageName);
        if (cached != null) return cached;
        try {
            Drawable icon = packageManager.getApplicationIcon(packageName);
            if (icon != null) iconCache.put(packageName, icon);
            return icon;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class Candidate {
        final int uid;
        final long rxBps;
        final long txBps;
        final boolean foreground;

        Candidate(int uid, long rxBps, long txBps, boolean foreground) {
            this.uid = uid;
            this.rxBps = rxBps;
            this.txBps = txBps;
            this.foreground = foreground;
        }
    }
}
