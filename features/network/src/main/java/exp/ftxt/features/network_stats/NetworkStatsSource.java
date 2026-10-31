package exp.ftxt.features.network_stats;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NetworkStatsSource {

    public static final int MODE_TOTAL = 0;
    public static final int MODE_WIFI = 1;
    public static final int MODE_MOBILE = 2;

    public static class AppUsage {
        public final int uid;
        public final long rxBytes;
        public final long txBytes;

        public AppUsage(int uid, long rxBytes, long txBytes) {
            this.uid = uid;
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
        }

        public long totalBytes() {
            return rxBytes + txBytes;
        }
    }

    public static class Result {
        public final List<AppUsage> apps;
        public final boolean mobileUnavailable;

        public Result(List<AppUsage> apps, boolean mobileUnavailable) {
            this.apps = apps;
            this.mobileUnavailable = mobileUnavailable;
        }
    }

    private NetworkStatsSource() {
    }

    public static Result queryToday(Context context, int mode) throws Exception {
        long start = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return query(context, mode, start, System.currentTimeMillis());
    }

    public static Result query(Context context, int mode, long startMs, long endMs) throws Exception {
        NetworkStatsManager manager =
                (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("NetworkStatsManager tidak tersedia");
        }

        Map<Integer, long[]> totals = new HashMap<>();
        boolean mobileUnavailable = false;

        if (mode == MODE_TOTAL || mode == MODE_WIFI) {
            collect(manager.queryDetails(ConnectivityManager.TYPE_WIFI, null, startMs, endMs), totals);
        }

        if (mode == MODE_TOTAL || mode == MODE_MOBILE) {
            String subscriberId = subscriberId(context);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && subscriberId == null) {
                mobileUnavailable = true;
            } else {
                try {
                    collect(manager.queryDetails(ConnectivityManager.TYPE_MOBILE,
                            subscriberId, startMs, endMs), totals);
                } catch (Exception e) {
                    mobileUnavailable = true;
                }
            }
        }

        List<AppUsage> apps = new ArrayList<>(totals.size());
        for (Map.Entry<Integer, long[]> entry : totals.entrySet()) {
            long rx = entry.getValue()[0];
            long tx = entry.getValue()[1];
            if (rx + tx <= 0) continue;
            apps.add(new AppUsage(entry.getKey(), rx, tx));
        }
        apps.sort((a, b) -> Long.compare(b.totalBytes(), a.totalBytes()));

        return new Result(apps, mobileUnavailable);
    }

    private static String subscriberId(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null;
        try {
            TelephonyManager telephony =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return telephony != null ? telephony.getSubscriberId() : null;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static void collect(NetworkStats stats, Map<Integer, long[]> totals) {
        if (stats == null) return;
        try {
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
        } finally {
            stats.close();
        }
    }
}
