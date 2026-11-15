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

    private static final long FOREGROUND_WINDOW_MS = 60000L;
    private static final long MIN_QUERY_WINDOW_MS = 2000L;

    private final Context context;
    private final PackageManager packageManager;
    private final NetworkStatsManager networkStatsManager;
    private final UsageStatsManager usageStatsManager;
    private final String selfPackage;
    private final int selfUid;

    private final LruCache<String, Drawable> iconCache = new LruCache<>(48);
    private final Map<String, String> labelCache = new HashMap<>();
    private String cachedForeground;
    private long cachedForegroundAt;

    public ConnectingAppSource(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.networkStatsManager =
                (NetworkStatsManager) this.context.getSystemService(Context.NETWORK_STATS_SERVICE);
        this.usageStatsManager =
                (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.selfPackage = this.context.getPackageName();
        this.selfUid = android.os.Process.myUid();
    }

    public List<AppTraffic> query(long nowMs, int mode, int maxRows,
                                  long thresholdBps, long intervalMs) {
        if (networkStatsManager == null) return Collections.emptyList();

        long windowMs = Math.max(MIN_QUERY_WINDOW_MS, intervalMs + 1000L);
        long startMs = nowMs - windowMs;
        long elapsedMs = Math.max(1L, nowMs - startMs);

        Map<Integer, long[]> totals = new HashMap<>();
        collect(ConnectivityManager.TYPE_WIFI, null, startMs, nowMs, totals);

        String subscriberId = subscriberId();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || subscriberId != null) {
            collect(ConnectivityManager.TYPE_MOBILE, subscriberId, startMs, nowMs, totals);
        }

        if (totals.isEmpty()) return Collections.emptyList();

        int foregroundUid = -1;
        String foregroundPackage = foregroundPackage(nowMs);
        if (foregroundPackage != null) {
            try {
                foregroundUid = packageManager.getPackageUid(foregroundPackage, 0);
            } catch (Exception ignored) {
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Integer, long[]> entry : totals.entrySet()) {
            int uid = entry.getKey();
            if (uid < 0 || uid == selfUid) continue;

            long[] values = entry.getValue();
            long rx = Math.max(0L, values[0]);
            long tx = Math.max(0L, values[1]);
            if (rx + tx <= 0L) continue;

            long rxBps = rx * 1000L / elapsedMs;
            long txBps = tx * 1000L / elapsedMs;
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

    private void collect(int networkType, String subscriberId,
                         long startMs, long endMs, Map<Integer, long[]> totals) {
        NetworkStats stats = null;
        try {
            stats = networkStatsManager.querySummary(networkType, subscriberId, startMs, endMs);
            if (stats == null) return;

            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket);
                int uid = bucket.getUid();
                if (uid < 0) continue;

                long[] values = totals.get(uid);
                if (values == null) {
                    values = new long[2];
                    totals.put(uid, values);
                }
                values[0] += Math.max(0L, bucket.getRxBytes());
                values[1] += Math.max(0L, bucket.getTxBytes());
            }
        } catch (Exception ignored) {
        } finally {
            if (stats != null) {
                try {
                    stats.close();
                } catch (Exception ignored) {
                }
            }
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

    private String subscriberId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null;
        try {
            TelephonyManager telephony =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return telephony != null ? telephony.getSubscriberId() : null;
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
