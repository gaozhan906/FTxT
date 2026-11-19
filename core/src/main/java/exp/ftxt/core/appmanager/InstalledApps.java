package exp.ftxt.core.appmanager;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InstalledApps {
    private static InstalledApps instance;

    private final Context appContext;
    private final PackageManager pm;
    private final SharedPreferences prefs;
    private final List<AppInfo> cache = Collections.synchronizedList(new ArrayList<AppInfo>());
    private final Set<String> recentlyActive = Collections.synchronizedSet(new HashSet<String>());
    private final java.util.Map<String, UsageData> usageData =
            new java.util.HashMap<String, UsageData>();

    private InstalledApps(Context context) {
        this.appContext = context.getApplicationContext();
        this.pm = this.appContext.getPackageManager();
        this.prefs = this.appContext.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new InstalledApps(context);
        }
    }

    public static synchronized InstalledApps get() {
        return instance;
    }

    public static void invalidate(Context context) {
        init(context);
        InstalledApps current = instance;
        if (current != null) {
            synchronized (current.cache) {
                current.cache.clear();
            }
        }
    }

    public List<AppInfo> refresh() {
        Set<String> launchers = launcherPackages();
        loadUsage();

        List<AppInfo> result = new ArrayList<>();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        for (PackageInfo pi : packages) {
            if (pi == null || pi.packageName == null) continue;
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null) continue;

            AppInfo info = new AppInfo(pi.packageName);
            info.label = loadLabel(ai);
            info.icon = loadIcon(ai);
            info.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            info.updatedSystem = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            info.launcher = launchers.contains(pi.packageName);
            info.enabled = ai.enabled;
            info.versionName = pi.versionName;
            info.versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? pi.getLongVersionCode() : pi.versionCode;
            info.firstInstallTime = pi.firstInstallTime;
            info.lastUpdateTime = pi.lastUpdateTime;
            info.targetSdk = ai.targetSdkVersion;
            info.minSdk = ai.minSdkVersion;
            info.uid = ai.uid;
            info.processName = ai.processName;
            info.sourceDir = ai.sourceDir;
            info.dataDir = ai.dataDir;
            info.installer = installerOf(pi.packageName);
            info.abi = ai.nativeLibraryDir != null
                    ? (ai.nativeLibraryDir.contains("lib64") ? "64-bit" : "32-bit") : "—";
            info.category = getCategory(pi.packageName, info.label);
            applyUsage(info);
            result.add(info);
        }

        synchronized (cache) {
            cache.clear();
            cache.addAll(result);
        }
        return new ArrayList<>(result);
    }

    public List<AppInfo> snapshot() {
        synchronized (cache) {
            return new ArrayList<>(cache);
        }
    }

    public AppInfo find(String packageName) {
        synchronized (cache) {
            for (AppInfo info : cache) {
                if (info.packageName.equals(packageName)) return info;
            }
        }
        return null;
    }

    public Set<String> getRecentlyActive() {
        synchronized (recentlyActive) {
            return new HashSet<>(recentlyActive);
        }
    }

    public List<String> allCategories() {
        Set<String> categories = new LinkedHashSet<>();
        categories.add("Semua");
        synchronized (cache) {
            for (AppInfo info : cache) {
                if (info.category != null) categories.add(info.category);
            }
        }
        List<String> result = new ArrayList<>(categories);
        Collections.sort(result, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                if ("Semua".equals(a)) return -1;
                if ("Semua".equals(b)) return 1;
                return a.compareTo(b);
            }
        });
        return result;
    }

    public String getCategory(String packageName, String label) {
        String saved = prefs.getString("appmgr.tag." + packageName, null);
        return saved != null ? saved : guessCategory(label, packageName);
    }

    public void setCategory(String packageName, String category) {
        prefs.edit().putString("appmgr.tag." + packageName, category).apply();
        AppInfo info = find(packageName);
        if (info != null) info.category = category;
    }

    private void loadUsage() {
        synchronized (recentlyActive) {
            recentlyActive.clear();
        }
        synchronized (usageData) {
            usageData.clear();
        }
        try {
            UsageStatsManager usm = (UsageStatsManager) appContext
                    .getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;
            long now = System.currentTimeMillis();
            long start = now - 7L * 24L * 60L * 60L * 1000L;
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_WEEKLY,
                    start, now);
            if (stats != null) {
                for (UsageStats stat : stats) {
                    String pkg = stat.getPackageName();
                    if (pkg == null) continue;
                    UsageData data = usageData.get(pkg);
                    if (data == null) {
                        data = new UsageData();
                        usageData.put(pkg, data);
                    }
                    data.lastTimeUsed = Math.max(data.lastTimeUsed, stat.getLastTimeUsed());
                    data.foregroundMs += stat.getTotalTimeInForeground();
                    if (now - data.lastTimeUsed <= 10L * 60L * 1000L) {
                        recentlyActive.add(pkg);
                    }
                }
            }
            java.util.Map<String, Integer> launches = countLaunches(appContext, start, now);
            for (java.util.Map.Entry<String, Integer> entry : launches.entrySet()) {
                UsageData data = usageData.get(entry.getKey());
                if (data == null) {
                    data = new UsageData();
                    usageData.put(entry.getKey(), data);
                }
                data.launchCount = entry.getValue();
            }
        } catch (Throwable ignored) {
        }
    }

    public static java.util.Map<String, Integer> countLaunches(
            Context context, long from, long to) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<String, Integer>();
        try {
            UsageStatsManager usm = (UsageStatsManager) context
                    .getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return counts;
            UsageEvents events = usm.queryEvents(from, to);
            if (events == null) return counts;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) continue;
                String pkg = event.getPackageName();
                if (pkg == null) continue;
                Integer count = counts.get(pkg);
                counts.put(pkg, count == null ? 1 : count + 1);
            }
        } catch (Throwable ignored) {
        }
        return counts;
    }

    private static final class UsageData {
        long lastTimeUsed;
        long foregroundMs;
        int launchCount;
    }

    private void applyUsage(AppInfo info) {
        UsageData data;
        synchronized (usageData) {
            data = usageData.get(info.packageName);
        }
        if (data == null) return;
        info.lastTimeUsed = data.lastTimeUsed;
        info.foregroundTimeMs = data.foregroundMs;
        info.launchCount = data.launchCount;
    }

    private Set<String> launcherPackages() {
        Set<String> result = new HashSet<String>();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolves = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo resolve : resolves) {
            if (resolve.activityInfo != null) result.add(resolve.activityInfo.packageName);
        }
        return result;
    }

    private String loadLabel(ApplicationInfo ai) {
        CharSequence label = pm.getApplicationLabel(ai);
        return label != null ? label.toString() : ai.packageName;
    }

    private Drawable loadIcon(ApplicationInfo ai) {
        try {
            return pm.getApplicationIcon(ai);
        } catch (Throwable t) {
            return null;
        }
    }

    private String installerOf(String packageName) {
        String source = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                source = pm.getInstallSourceInfo(packageName).getInstallingPackageName();
            } else {
                source = pm.getInstallerPackageName(packageName);
            }
        } catch (Throwable ignored) {
        }
        if (source == null) return "Tidak diketahui";
        if ("com.android.vending".equals(source)) return "Google Play";
        if ("com.amazon.venezia".equals(source)) return "Amazon";
        if ("com.sec.android.app.samsungapps".equals(source)) return "Samsung";
        if ("com.facebook.katana".equals(source)) return "Facebook";
        return source;
    }

    private static String guessCategory(String label, String packageName) {
        String text = ((label == null ? "" : label) + " " + packageName).toLowerCase(Locale.US);
        if (text.contains("game") || text.contains("mlbb") || text.contains("pubg")
                || text.contains("codm") || text.contains("free fire") || text.contains("genshin")) {
            return "Games";
        }
        if (text.contains("chat") || text.contains("whatsapp") || text.contains("messenger")
                || text.contains("telegram") || text.contains("instagram") || text.contains("social")) {
            return "Sosial";
        }
        if (text.contains("news") || text.contains("berita") || text.contains("detik")
                || text.contains("kompas")) {
            return "Berita";
        }
        if (text.contains("camera") || text.contains("foto") || text.contains("photo")
                || text.contains("gallery")) {
            return "Foto";
        }
        if (text.contains("music") || text.contains("spotify") || text.contains("youtube")) {
            return "Hiburan";
        }
        if (text.contains("bank") || text.contains(" wallet") || text.contains("pay")) {
            return "Keuangan";
        }
        return "Lainnya";
    }
}
