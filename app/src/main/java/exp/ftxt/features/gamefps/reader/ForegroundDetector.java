package exp.ftxt.features.gamefps.reader;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import exp.ftxt.utils.PermissionHelper;

public final class ForegroundDetector {
    private static final long CACHE_MS = 1000L;
    private static final long EVENT_WINDOW_MS = 10L * 60L * 1000L;
    private static final long SHIZUKU_TIMEOUT_MS = 3000L;
    private static final Pattern ACTIVITY_PATTERN =
            Pattern.compile("ACTIVITY\\s+([A-Za-z0-9._]+)/");

    private static String cachedPackage;
    private static long cachedAt;
    private static String cachedHome;
    private static String[] cachedInputMethods;

    private ForegroundDetector() {}

    public static synchronized String getForegroundPackage(Context context) {
        long now = System.currentTimeMillis();
        if (cachedAt > 0 && now - cachedAt < CACHE_MS) return cachedPackage;
        String pkg = queryUsage(context, now);
        if (pkg == null) pkg = queryShizuku();
        cachedPackage = pkg;
        cachedAt = now;
        return pkg;
    }

    public static boolean isForeground(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return pkg.equals(getForegroundPackage(context));
    }

    public static boolean isExcluded(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (pkg.equals(context.getPackageName())) return true;
        if ("com.android.systemui".equals(pkg) || "android".equals(pkg)) return true;
        if (pkg.equals(homePackage(context))) return true;
        for (String ime : inputMethodPackages(context)) {
            if (pkg.equals(ime)) return true;
        }
        return false;
    }

    private static String queryUsage(Context context, long now) {
        if (!PermissionHelper.hasUsageAccess(context)) return null;
        try {
            UsageStatsManager usm =
                    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            UsageEvents events = usm.queryEvents(now - EVENT_WINDOW_MS, now);
            if (events == null) return null;
            UsageEvents.Event event = new UsageEvents.Event();
            String lastPkg = null;
            long lastTime = 0;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (event.getTimeStamp() >= lastTime) {
                        lastTime = event.getTimeStamp();
                        lastPkg = event.getPackageName();
                    }
                }
            }
            return lastPkg;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String queryShizuku() {
        try {
            if (!ShizukuHelper.isBinderAlive() || !ShizukuHelper.hasPermission()) return null;
            ShizukuShell.Result res = ShizukuShell.execArgs(
                    new String[]{"dumpsys", "activity", "top"}, SHIZUKU_TIMEOUT_MS);
            if (res.output == null || res.output.isEmpty()) return null;
            Matcher matcher = ACTIVITY_PATTERN.matcher(res.output);
            if (matcher.find()) return matcher.group(1);
        } catch (Throwable ignored) {}
        return null;
    }

    private static synchronized String homePackage(Context context) {
        if (cachedHome != null) return cachedHome;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = context.getPackageManager()
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            cachedHome = info != null && info.activityInfo != null
                    ? info.activityInfo.packageName : "";
        } catch (Throwable ignored) {
            cachedHome = "";
        }
        return cachedHome;
    }

    private static synchronized String[] inputMethodPackages(Context context) {
        if (cachedInputMethods != null) return cachedInputMethods;
        try {
            InputMethodManager imm =
                    (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            List<InputMethodInfo> methods = imm == null ? null : imm.getEnabledInputMethodList();
            if (methods == null) methods = imm == null ? null : imm.getInputMethodList();
            if (methods == null || methods.isEmpty()) {
                cachedInputMethods = new String[0];
            } else {
                cachedInputMethods = new String[methods.size()];
                for (int i = 0; i < methods.size(); i++) {
                    cachedInputMethods[i] = methods.get(i).getPackageName();
                }
            }
        } catch (Throwable ignored) {
            cachedInputMethods = new String[0];
        }
        return cachedInputMethods;
    }
}
