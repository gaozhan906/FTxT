package exp.ftxt.core.appmanager;

import android.graphics.drawable.Drawable;

import java.util.Locale;

public class AppInfo {
    public final String packageName;
    public String label;
    public Drawable icon;
    public boolean system;
    public boolean updatedSystem;
    public boolean launcher;
    public boolean enabled;
    public String versionName;
    public long versionCode;
    public long firstInstallTime;
    public long lastUpdateTime;
    public int targetSdk;
    public int minSdk;
    public int uid;
    public String processName;
    public String sourceDir;
    public String dataDir;
    public String installer;
    public String abi;
    public String category;
    public long lastTimeUsed;
    public long foregroundTimeMs;
    public int launchCount;

    public AppInfo(String packageName) {
        this.packageName = packageName;
    }

    public boolean is64Bit() {
        return abi != null && abi.contains("64");
    }

    public boolean isRecentlyActive(long now, long windowMs) {
        return lastTimeUsed > 0 && now - lastTimeUsed <= windowMs;
    }

    public String freshBadge(long now) {
        long day = 24L * 60L * 60L * 1000L;
        if (lastUpdateTime > firstInstallTime && now - lastUpdateTime <= day) {
            return "UPDATE";
        }
        if (now - firstInstallTime <= day) {
            return "BARU";
        }
        return null;
    }

    public String timeText(long ms) {
        if (ms <= 0) return "—";
        long min = (ms + 59_000L) / 60_000L;
        if (min < 60) return min + " mnt";
        long jam = min / 60;
        long sisa = min % 60;
        return sisa == 0 ? jam + " jam" : String.format(Locale.US, "%dj %dm", jam, sisa);
    }
}
