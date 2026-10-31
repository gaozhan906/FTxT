package exp.ftxt.features.network_stats.usage;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.RemoteException;
import android.telephony.TelephonyManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NetworkUsageSource {

    private static final String WIFI = "WIFI";
    private static final String MOBILE = "MOBILE";
    private static final int FULL_SYNC_DAYS = 30;
    private static final int HISTORY_DAYS = 90;

    private NetworkUsageSource() {
    }

    public static UsageSnapshot query(Context context, UsageQuery query) throws Exception {
        if (context == null || query == null) {
            throw new IllegalArgumentException("context/query null");
        }

        String cacheKey = query.cacheKey();
        UsageSnapshot cached = UsageCache.get(cacheKey);
        if (cached != null) return cached;

        Context app = context.getApplicationContext();
        UsageHistoryStore store = UsageHistoryStore.get(app);

        boolean fullSync;
        try {
            fullSync = store.shouldFullSync(query.nowMs, query.today().toString());
        } catch (Exception e) {
            fullSync = true;
        }

        long liveStart = fullSync
                ? query.today().minusDays(FULL_SYNC_DAYS - 1L)
                .atStartOfDay(query.zone).toInstant().toEpochMilli()
                : query.startMs();
        UsageNetwork liveNetwork = fullSync ? UsageNetwork.ALL : query.network;

        LiveData live;
        boolean liveUnavailable = false;
        try {
            live = queryLive(app, liveNetwork, liveStart, query.endMs(), query.zone);
        } catch (Exception e) {
            live = new LiveData();
            liveUnavailable = true;
        }

        try {
            String replaceStartDay = fullSync
                    ? query.today().minusDays(FULL_SYNC_DAYS - 1L).toString()
                    : null;
            store.sync(live.daily, live.packages, live.availableNetworks,
                    query.today().toString(), replaceStartDay);
            if (fullSync && !liveUnavailable && !live.availableNetworks.isEmpty()) {
                store.trimBefore(query.today().minusDays(HISTORY_DAYS - 1L).toString());
                store.setLastSync(query.nowMs, query.today().toString());
            }
        } catch (Exception ignored) {
        }

        List<UsageHistoryStore.Entry> entries;
        try {
            entries = store.loadRange(query.startDate().toString(), query.today().toString());
        } catch (Exception e) {
            entries = Collections.emptyList();
        }

        UsageSnapshot snapshot = buildSnapshot(query, live, entries, liveUnavailable);
        UsageCache.put(cacheKey, snapshot);
        return snapshot;
    }

    private static LiveData queryLive(Context context, UsageNetwork network,
                                      long startMs, long endMs, ZoneId zone) throws RemoteException {
        NetworkStatsManager manager =
                (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("NetworkStatsManager tidak tersedia");
        }

        LiveData data = new LiveData();

        if (network.includesWifi()) {
            collect(manager, ConnectivityManager.TYPE_WIFI, null, WIFI, startMs, endMs, zone, data);
            data.availableNetworks.add(WIFI);
        }

        if (network.includesMobile()) {
            String subscriberId = subscriberId(context);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && subscriberId == null) {
                data.mobileUnavailable = true;
            } else {
                try {
                    collect(manager, ConnectivityManager.TYPE_MOBILE, subscriberId, MOBILE,
                            startMs, endMs, zone, data);
                    data.availableNetworks.add(MOBILE);
                } catch (Exception e) {
                    data.mobileUnavailable = true;
                }
            }
        }

        PackageManager packageManager = context.getPackageManager();
        for (Integer uid : data.uids()) {
            data.packages.put(uid, resolvePackage(packageManager, uid));
        }
        return data;
    }

    private static void collect(NetworkStatsManager manager, int networkType, String subscriberId,
                                String network, long startMs, long endMs, ZoneId zone,
                                LiveData data) throws RemoteException {
        NetworkStats stats = manager.queryDetails(networkType, subscriberId, startMs, endMs);
        if (stats == null) return;

        try {
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket);
                int uid = bucket.getUid();
                if (uid < 0) continue;

                long rx = Math.max(0L, bucket.getRxBytes());
                long tx = Math.max(0L, bucket.getTxBytes());
                if (rx + tx <= 0) continue;

                long bucketStart = bucket.getStartTimeStamp();
                long bucketEnd = bucket.getEndTimeStamp();
                if (bucketStart <= 0) bucketStart = startMs;
                if (bucketEnd <= bucketStart) {
                    bucketEnd = Math.min(endMs, bucketStart + 1L);
                }

                long from = Math.max(startMs, bucketStart);
                long to = Math.min(endMs, bucketEnd);
                if (to <= from) continue;

                data.add(network, uid, from, to, rx, tx, zone);
            }
        } finally {
            stats.close();
        }
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

    private static String resolvePackage(PackageManager packageManager, int uid) {
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages != null && packages.length > 0) return packages[0];
        try {
            return packageManager.getNameForUid(uid);
        } catch (Exception e) {
            return null;
        }
    }

    private static UsageSnapshot buildSnapshot(UsageQuery query, LiveData live,
                                               List<UsageHistoryStore.Entry> entries,
                                               boolean liveUnavailable) {
        Map<String, Map<String, Map<Integer, long[]>>> dbDaily = new HashMap<>();
        Map<String, Map<String, Map<Integer, String>>> dbPackages = new HashMap<>();

        for (UsageHistoryStore.Entry entry : entries) {
            Map<String, Map<Integer, long[]>> day = dbDaily
                    .computeIfAbsent(entry.day, k -> new HashMap<>());
            Map<Integer, long[]> network = day
                    .computeIfAbsent(entry.network, k -> new HashMap<>());
            long[] bytes = network.get(entry.uid);
            if (bytes == null) {
                bytes = new long[2];
                network.put(entry.uid, bytes);
            }
            bytes[0] += Math.max(0L, entry.rxBytes);
            bytes[1] += Math.max(0L, entry.txBytes);

            if (entry.packageName != null) {
                dbPackages.computeIfAbsent(entry.day, k -> new HashMap<>())
                        .computeIfAbsent(entry.network, k -> new HashMap<>())
                        .put(entry.uid, entry.packageName);
            }
        }

        List<String> networks = selectedNetworks(query.network);
        String today = query.today().toString();
        List<UsageDay> days = new ArrayList<>();
        int liveDays = 0;
        int cacheDays = 0;

        for (LocalDate date = query.startDate(); !date.isAfter(query.today()); date = date.plusDays(1)) {
            String dayKey = date.toString();
            Map<String, Map<Integer, long[]>> selected = new HashMap<>();
            Map<Integer, String> packages = new HashMap<>();
            boolean usedLive = false;
            boolean usedCache = false;

            for (String network : networks) {
                Map<Integer, long[]> liveMap = null;
                Map<String, Map<Integer, long[]>> liveDay = live.daily.get(dayKey);
                if (liveDay != null) liveMap = liveDay.get(network);

                Map<Integer, long[]> dbMap = null;
                Map<String, Map<Integer, long[]>> dbDay = dbDaily.get(dayKey);
                if (dbDay != null) dbMap = dbDay.get(network);

                if (live.availableNetworks.contains(network) && liveMap != null) {
                    selected.put(network, liveMap);
                    mergePackages(packages, live.packages);
                    usedLive = true;
                } else if (live.availableNetworks.contains(network) && dayKey.equals(today)) {
                    usedLive = true;
                } else if (dbMap != null) {
                    selected.put(network, dbMap);
                    Map<Integer, String> dbPkg = packageMap(dbPackages, dayKey, network);
                    if (dbPkg != null) packages.putAll(dbPkg);
                    usedCache = true;
                }
            }

            long wifiRx = sum(selected, WIFI, 0);
            long wifiTx = sum(selected, WIFI, 1);
            long mobileRx = sum(selected, MOBILE, 0);
            long mobileTx = sum(selected, MOBILE, 1);
            long rx = wifiRx + mobileRx;
            long tx = wifiTx + mobileTx;
            boolean hasData = rx + tx > 0;

            UsageSource source;
            if (!hasData) source = UsageSource.NONE;
            else if (usedLive && usedCache) source = UsageSource.MIXED;
            else if (usedLive) source = UsageSource.LIVE;
            else if (usedCache) source = UsageSource.CACHE;
            else source = UsageSource.NONE;

            if (source == UsageSource.LIVE || source == UsageSource.MIXED) liveDays++;
            if (source == UsageSource.CACHE || source == UsageSource.MIXED) cacheDays++;

            List<UsageApp> dayApps = buildAppsFromNetworks(selected, packages);
            days.add(new UsageDay(dayKey, rx, tx, wifiRx, wifiTx, mobileRx, mobileTx,
                    hasData, source, dayApps));
        }

        UsageSource source;
        if (liveDays > 0 && cacheDays > 0) source = UsageSource.MIXED;
        else if (liveDays > 0) source = UsageSource.LIVE;
        else if (cacheDays > 0) source = UsageSource.CACHE;
        else source = UsageSource.NONE;

        long totalRx = 0;
        long totalTx = 0;
        Map<Integer, long[]> appTotals = new HashMap<>();
        Map<Integer, String> appPackages = new HashMap<>();

        for (UsageDay day : days) {
            totalRx += day.rxBytes;
            totalTx += day.txBytes;
            for (UsageApp app : day.apps) {
                long[] bytes = appTotals.computeIfAbsent(app.uid, k -> new long[2]);
                bytes[0] += app.rxBytes;
                bytes[1] += app.txBytes;
                if (app.packageName != null) appPackages.putIfAbsent(app.uid, app.packageName);
            }
        }

        List<UsageApp> apps = buildApps(appTotals, appPackages);
        List<UsageBucket> buckets = buildBuckets(query, live, days);

        UsageBucket peak = null;
        for (UsageBucket bucket : buckets) {
            if (peak == null || bucket.totalBytes() > peak.totalBytes()) peak = bucket;
        }
        if (peak != null && peak.totalBytes() <= 0) peak = null;

        long averageRx = 0;
        long averageTx = 0;
        boolean averageAvailable;
        long elapsed = Math.max(0L, query.endMs() - query.startMs());
        if (query.period == UsagePeriod.TODAY) {
            averageAvailable = elapsed >= 3600000L;
            if (averageAvailable) {
                double hours = elapsed / 3600000.0;
                averageRx = Math.round(totalRx / hours);
                averageTx = Math.round(totalTx / hours);
            }
        } else {
            averageAvailable = true;
            averageRx = totalRx / query.period.days;
            averageTx = totalTx / query.period.days;
        }

        return new UsageSnapshot(query, query.startMs(), query.endMs(), totalRx, totalTx,
                averageRx, averageTx, averageAvailable, peak, buckets, days, apps,
                liveUnavailable || live.mobileUnavailable, live.mobileUnavailable, source);
    }

    private static List<UsageBucket> buildBuckets(UsageQuery query, LiveData live,
                                                  List<UsageDay> days) {
        List<UsageBucket> buckets = new ArrayList<>();

        if (query.period == UsagePeriod.TODAY) {
            ZonedDateTime dayStart = query.startDate().atStartOfDay(query.zone);
            boolean hasHourly = true;
            for (String network : selectedNetworks(query.network)) {
                if (!live.availableNetworks.contains(network)) {
                    hasHourly = false;
                    break;
                }
            }

            if (hasHourly) {
                for (int hour = 0; hour < 24; hour++) {
                    long start = dayStart.plusHours(hour).toInstant().toEpochMilli();
                    long end = dayStart.plusHours(hour + 1L).toInstant().toEpochMilli();
                    if (start > query.endMs()) end = start;
                    else end = Math.min(end, query.endMs());

                    long rx = 0;
                    long tx = 0;
                    Map<String, long[]> hourly = live.hourly.get(start);
                    if (hourly != null) {
                        for (String network : selectedNetworks(query.network)) {
                            long[] bytes = hourly.get(network);
                            if (bytes != null) {
                                rx += bytes[0];
                                tx += bytes[1];
                            }
                        }
                    }
                    buckets.add(new UsageBucket(start, end, rx, tx));
                }
            } else {
                UsageDay day = days.isEmpty() ? null : days.get(0);
                buckets.add(new UsageBucket(dayStart.toInstant().toEpochMilli(), query.endMs(),
                        day != null ? day.rxBytes : 0,
                        day != null ? day.txBytes : 0));
            }
        } else {
            for (UsageDay day : days) {
                ZonedDateTime start = LocalDate.parse(day.date).atStartOfDay(query.zone);
                long startMs = start.toInstant().toEpochMilli();
                long endMs = Math.min(start.plusDays(1L).toInstant().toEpochMilli(), query.endMs());
                buckets.add(new UsageBucket(startMs, endMs, day.rxBytes, day.txBytes));
            }
        }

        return buckets;
    }

    private static List<UsageApp> buildAppsFromNetworks(Map<String, Map<Integer, long[]>> selected,
                                                        Map<Integer, String> packages) {
        Map<Integer, long[]> totals = new HashMap<>();
        for (Map<Integer, long[]> network : selected.values()) {
            for (Map.Entry<Integer, long[]> entry : network.entrySet()) {
                long[] bytes = totals.computeIfAbsent(entry.getKey(), k -> new long[2]);
                bytes[0] += Math.max(0L, entry.getValue()[0]);
                bytes[1] += Math.max(0L, entry.getValue()[1]);
            }
        }
        return buildApps(totals, packages);
    }

    private static List<UsageApp> buildApps(Map<Integer, long[]> totals,
                                            Map<Integer, String> packages) {
        List<UsageApp> apps = new ArrayList<>();
        for (Map.Entry<Integer, long[]> entry : totals.entrySet()) {
            long rx = Math.max(0L, entry.getValue()[0]);
            long tx = Math.max(0L, entry.getValue()[1]);
            if (rx + tx <= 0) continue;
            apps.add(new UsageApp(entry.getKey(), packages.get(entry.getKey()), rx, tx));
        }
        apps.sort((a, b) -> Long.compare(b.totalBytes(), a.totalBytes()));
        return apps;
    }

    private static long sum(Map<String, Map<Integer, long[]>> selected, String network, int index) {
        Map<Integer, long[]> map = selected.get(network);
        if (map == null) return 0;
        long total = 0;
        for (long[] bytes : map.values()) {
            total += Math.max(0L, bytes[index]);
        }
        return total;
    }

    private static void mergePackages(Map<Integer, String> target, Map<Integer, String> source) {
        for (Map.Entry<Integer, String> entry : source.entrySet()) {
            if (entry.getValue() != null) {
                target.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Map<Integer, String> packageMap(
            Map<String, Map<String, Map<Integer, String>>> packages,
            String day, String network) {
        Map<String, Map<Integer, String>> dayMap = packages.get(day);
        return dayMap != null ? dayMap.get(network) : null;
    }

    private static List<String> selectedNetworks(UsageNetwork network) {
        if (network == UsageNetwork.ALL) return Arrays.asList(WIFI, MOBILE);
        return Collections.singletonList(network == UsageNetwork.WIFI ? WIFI : MOBILE);
    }

    private static final class LiveData {
        final Map<String, Map<String, Map<Integer, long[]>>> daily = new HashMap<>();
        final Map<Long, Map<String, long[]>> hourly = new HashMap<>();
        final Map<Integer, String> packages = new HashMap<>();
        final Set<String> availableNetworks = new HashSet<>();
        boolean mobileUnavailable;

        void add(String network, int uid, long startMs, long endMs, long rx, long tx, ZoneId zone) {
            long totalDuration = Math.max(1L, endMs - startMs);
            long rxLeft = rx;
            long txLeft = tx;
            long cursor = startMs;

            while (cursor < endMs) {
                ZonedDateTime time = Instant.ofEpochMilli(cursor).atZone(zone);
                long nextHour = time.truncatedTo(ChronoUnit.HOURS)
                        .plusHours(1L).toInstant().toEpochMilli();
                long segmentEnd = Math.min(endMs, nextHour);
                long segmentDuration = Math.max(1L, segmentEnd - cursor);

                long rxPart;
                long txPart;
                if (segmentEnd >= endMs) {
                    rxPart = rxLeft;
                    txPart = txLeft;
                } else {
                    rxPart = Math.min(rxLeft,
                            Math.round(rx * (double) segmentDuration / totalDuration));
                    txPart = Math.min(txLeft,
                            Math.round(tx * (double) segmentDuration / totalDuration));
                }

                addToMaps(network, uid, cursor, rxPart, txPart, zone);
                rxLeft -= rxPart;
                txLeft -= txPart;
                cursor = segmentEnd;
            }

            if (rxLeft > 0 || txLeft > 0) {
                addToMaps(network, uid, endMs - 1L, rxLeft, txLeft, zone);
            }
        }

        private void addToMaps(String network, int uid, long timeMs,
                               long rx, long tx, ZoneId zone) {
            if (rx <= 0 && tx <= 0) return;

            ZonedDateTime time = Instant.ofEpochMilli(timeMs).atZone(zone);
            String day = time.toLocalDate().toString();
            long hour = time.truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli();

            Map<Integer, long[]> dayNetwork = daily
                    .computeIfAbsent(day, k -> new HashMap<>())
                    .computeIfAbsent(network, k -> new HashMap<>());
            add(dayNetwork, uid, rx, tx);

            Map<String, long[]> hourlyNetwork = hourly
                    .computeIfAbsent(hour, k -> new HashMap<>());
            long[] hourlyBytes = hourlyNetwork.get(network);
            if (hourlyBytes == null) {
                hourlyBytes = new long[2];
                hourlyNetwork.put(network, hourlyBytes);
            }
            hourlyBytes[0] += rx;
            hourlyBytes[1] += tx;
        }

        private static void add(Map<Integer, long[]> map, int uid, long rx, long tx) {
            long[] bytes = map.get(uid);
            if (bytes == null) {
                bytes = new long[2];
                map.put(uid, bytes);
            }
            bytes[0] += rx;
            bytes[1] += tx;
        }

        Set<Integer> uids() {
            Set<Integer> result = new HashSet<>();
            for (Map<String, Map<Integer, long[]>> day : daily.values()) {
                for (Map<Integer, long[]> network : day.values()) {
                    result.addAll(network.keySet());
                }
            }
            return result;
        }
    }
}
