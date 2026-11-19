package exp.ftxt.core.appmanager;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.Signature;
import android.os.Process;
import android.os.storage.StorageManager;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppDetails {
    private AppDetails() {
    }

    public static class Perm {
        public String name;
        public String label;
        public String description;
        public String protection;
        public boolean granted;
    }

    public static class Access {
        public String label;
        public String action;
    }

    public static class Result {
        public boolean sizeAvailable;
        public long codeBytes;
        public long dataBytes;
        public long cacheBytes;
        public long totalBytes;
        public final List<Perm> permissions = new ArrayList<Perm>();
        public final List<Access> specialAccess = new ArrayList<Access>();
        public final List<String> activities = new ArrayList<String>();
        public final List<String> services = new ArrayList<String>();
        public final List<String> receivers = new ArrayList<String>();
        public final List<String> providers = new ArrayList<String>();
        public String certificateSha256;
        public String graphics;
        public boolean running;
        public long lastTimeUsed;
        public long foregroundTimeMs;
        public int launchCount;
    }

    public static Result load(Context context, String packageName) {
        Result result = new Result();
        PackageManager pm = context.getPackageManager();

        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_SERVICES
                    | PackageManager.GET_RECEIVERS
                    | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SIGNATURES);
        } catch (Throwable t) {
            return result;
        }

        loadSize(context, packageName, result);
        loadPermissions(pm, pi, result);
        loadComponents(pi, result);
        loadCertificate(pi, result);
        result.graphics = loadGraphics(pi.applicationInfo);
        loadUsage(context, packageName, result);

        InstalledApps apps = InstalledApps.get();
        result.running = apps != null && apps.getRecentlyActive().contains(packageName);
        result.specialAccess.addAll(specialAccess());
        return result;
    }

    private static void loadSize(Context context, String packageName, Result result) {
        try {
            StorageStatsManager ssm = (StorageStatsManager) context
                    .getSystemService(Context.STORAGE_STATS_SERVICE);
            if (ssm == null) return;
            StorageStats stats = ssm.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle());
            result.codeBytes = stats.getAppBytes();
            result.dataBytes = stats.getDataBytes();
            result.cacheBytes = stats.getCacheBytes();
            result.totalBytes = result.codeBytes + result.dataBytes + result.cacheBytes;
            result.sizeAvailable = true;
        } catch (Throwable t) {
            result.sizeAvailable = false;
        }
    }

    private static void loadPermissions(PackageManager pm, PackageInfo pi, Result result) {
        if (pi.requestedPermissions == null) return;
        int count = pi.requestedPermissions.length;
        for (int i = 0; i < count; i++) {
            Perm perm = new Perm();
            perm.name = pi.requestedPermissions[i];
            perm.granted = pi.requestedPermissionsFlags != null
                    && i < pi.requestedPermissionsFlags.length
                    && (pi.requestedPermissionsFlags[i]
                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
            try {
                PermissionInfo info = pm.getPermissionInfo(perm.name, 0);
                perm.label = info.loadLabel(pm).toString();
                perm.description = info.loadDescription(pm) == null
                        ? "" : info.loadDescription(pm).toString();
                perm.protection = protectionOf(info.protectionLevel);
            } catch (Throwable t) {
                perm.label = shortName(perm.name);
                perm.protection = "Tidak diketahui";
            }
            result.permissions.add(perm);
        }
    }

    private static String protectionOf(int level) {
        int base = level & PermissionInfo.PROTECTION_MASK_BASE;
        if (base == PermissionInfo.PROTECTION_DANGEROUS) return "Dangerous";
        if (base == PermissionInfo.PROTECTION_NORMAL) return "Normal";
        if (base == PermissionInfo.PROTECTION_SIGNATURE) return "Signature";
        if (base == PermissionInfo.PROTECTION_SIGNATURE_OR_SIGNATURE) {
            return "Signature/Privileged";
        }
        return "Lainnya";
    }

    private static String shortName(String permission) {
        int dot = permission.lastIndexOf('.');
        return dot >= 0 ? permission.substring(dot + 1) : permission;
    }

    private static void loadComponents(PackageInfo pi, Result result) {
        addNames(pi.activities, result.activities);
        addNames(pi.services, result.services);
        addNames(pi.receivers, result.receivers);
        addNames(pi.providers, result.providers);
    }

    private static void addNames(ComponentInfo[] components, List<String> target) {
        if (components == null) return;
        for (ComponentInfo component : components) {
            if (component.name != null) target.add(component.name);
        }
    }

    private static void loadCertificate(PackageInfo pi, Result result) {
        try {
            Signature[] signatures = pi.signatures;
            if (signatures == null || signatures.length == 0) return;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signatures[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02X", b));
            }
            result.certificateSha256 = sb.toString();
        } catch (Throwable t) {
            result.certificateSha256 = null;
        }
    }

    private static String loadGraphics(ApplicationInfo ai) {
        if (ai == null) return "Tidak tersedia";
        StringBuilder sb = new StringBuilder();
        sb.append("Large heap: ").append(flag(ai.flags, ApplicationInfo.FLAG_LARGE_HEAP));
        sb.append("\nDebuggable: ").append(flag(ai.flags, ApplicationInfo.FLAG_DEBUGGABLE));
        sb.append("\nTarget SDK: ").append(ai.targetSdkVersion);
        sb.append("\nABI: ").append(ai.nativeLibraryDir != null
                ? (ai.nativeLibraryDir.contains("lib64") ? "64-bit" : "32-bit") : "—");
        return sb.toString();
    }

    private static String flag(int flags, int value) {
        return (flags & value) != 0 ? "ya" : "tidak";
    }

    private static void loadUsage(Context context, String packageName, Result result) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context
                    .getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;
            long now = System.currentTimeMillis();
            long start = now - 365L * 24L * 60L * 60L * 1000L;
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_YEARLY,
                    start, now);
            if (stats != null) {
                for (UsageStats stat : stats) {
                    if (!packageName.equals(stat.getPackageName())) continue;
                    result.lastTimeUsed = Math.max(result.lastTimeUsed, stat.getLastTimeUsed());
                    result.foregroundTimeMs += stat.getTotalTimeInForeground();
                }
            }
            Integer launches = InstalledApps.countLaunches(context, start, now).get(packageName);
            if (launches != null) result.launchCount = launches;
        } catch (Throwable ignored) {
        }
    }

    private static List<Access> specialAccess() {
        List<Access> list = new ArrayList<Access>();
        list.add(access("Akses notifikasi", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        list.add(access("Aksesibilitas", "android.settings.ACCESSIBILITY_SETTINGS"));
        list.add(access("Tampil di atas aplikasi lain", "android.settings.action.MANAGE_OVERLAY_PERMISSION"));
        list.add(access("Ubah pengaturan sistem", "android.settings.action.MANAGE_WRITE_SETTINGS"));
        list.add(access("Abaikan optimasi baterai", "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
        list.add(access("Akses data penggunaan", "android.settings.USAGE_ACCESS_SETTINGS"));
        list.add(access("Instal aplikasi dari sumber tidak dikenal", "android.settings.MANAGE_UNKNOWN_APP_SOURCES"));
        list.add(access("Lokasi", "android.settings.LOCATION_SOURCE_SETTINGS"));
        list.add(access("VPN", "android.settings.VPN_SETTINGS"));
        list.add(access("Administrator perangkat", "android.settings.DEVICE_ADMIN_SETTINGS"));
        list.add(access("Aplikasi default", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"));
        list.add(access("Pemutar teks default", "android.settings.ACTION_MANAGE_DEFAULT_APP"));
        return list;
    }

    private static Access access(String label, String action) {
        Access item = new Access();
        item.label = label;
        item.action = action;
        return item;
    }
}
