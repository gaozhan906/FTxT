package exp.ftxt.features.network_stats.app_usage;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.TrafficStats;
import android.os.SystemClock;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final long UID_REFRESH_MS = 30000L;

    private final Context context;
    private final PackageManager packageManager;
    private final UsageStatsManager usageStatsManager;
    private final String selfPackage;
    private final int selfUid;

    private final LruCache<String, Drawable> iconCache = new LruCache<>(48);
    private final Map<String, String> labelCache = new HashMap<>();
    private String cachedForeground;
    private long cachedForegroundAt;

    private int[] cachedUids;
    private long cachedUidsAt;
    private Map<Integer, long[]> lastTotals;
    private long lastSampleAtMs;

    public ConnectingAppSource(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.usageStatsManager =
                (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.selfPackage = this.context.getPackageName();
        this.selfUid = android.os.Process.myUid();
    }

    public List<AppTraffic> query(long nowMs, int mode, int maxRows,
                                  long thresholdBps, long intervalMs) {
        Map<Integer, long[]> current = sample();
        Map<Integer, long[]> previous = lastTotals;
        long elapsedMs = lastSampleAtMs > 0 ? nowMs - lastSampleAtMs : 0L;

        lastTotals = current;
        lastSampleAtMs = nowMs;

        if (previous == null || elapsedMs <= 0L) return Collections.emptyList();

        int foregroundUid = -1;
        String foregroundPackage = mode == MODE_FOREGROUND ? foregroundPackage(nowMs) : null;
        if (foregroundPackage != null) {
            try {
                foregroundUid = packageManager.getPackageUid(foregroundPackage, 0);
            } catch (Exception ignored) {
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<Integer, long[]> entry : current.entrySet()) {
            int uid = entry.getKey();
            if (uid < 0 || uid == selfUid) continue;

            long[] prev = previous.get(uid);
            if (prev == null) continue;

            long[] values = entry.getValue();
            long rx = Math.max(0L, values[0] - prev[0]);
            long tx = Math.max(0L, values[1] - prev[1]);
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

    private Map<Integer, long[]> sample() {
        Map<Integer, long[]> totals = new HashMap<>();
        for (int uid : uids()) {
            if (uid < 0 || uid == selfUid) continue;
            long rx = TrafficStats.getUidRxBytes(uid);
            long tx = TrafficStats.getUidTxBytes(uid);
            if (rx < 0L || tx < 0L) continue;
            totals.put(uid, new long[]{rx, tx});
        }
        return totals;
    }

    private int[] uids() {
        long now = SystemClock.elapsedRealtime();
        if (cachedUids != null && now - cachedUidsAt < UID_REFRESH_MS) {
            return cachedUids;
        }
        Set<Integer> unique = new HashSet<>();
        try {
            for (ApplicationInfo info : packageManager.getInstalledApplications(0)) {
                unique.add(info.uid);
            }
        } catch (Exception ignored) {
        }
        int[] result = new int[unique.size()];
        int index = 0;
        for (int uid : unique) {
            result[index++] = uid;
        }
        cachedUids = result;
        cachedUidsAt = now;
        return result;
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
