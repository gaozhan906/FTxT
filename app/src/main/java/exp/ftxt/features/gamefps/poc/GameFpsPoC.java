package exp.ftxt.features.gamefps.poc;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameFpsPoC {
    private static final String TAG = "GameFpsPoC";
    private static final long DEFAULT_INTERVAL = 1000L;
    private static final long IDLE_INTERVAL = 2000L;
    private static final long DEFAULT_TIMEOUT = 4000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String targetPackage = null;
    private long intervalMs = DEFAULT_INTERVAL;
    private long timeoutMs = DEFAULT_TIMEOUT;

    private static final Pattern TOTAL_FRAMES_PATTERN =
            Pattern.compile("Total frames rendered[:\\s]+([0-9]+)", Pattern.CASE_INSENSITIVE);

    private long lastTotalFrames = -1;
    private long lastTotalFramesTime = 0;

    private static void logOutputHead(String output) {
        if (output == null || output.isEmpty()) {
            GameFpsLog.add("STDOUT (kosong)");
            return;
        }
        String head = output.length() > 500 ? output.substring(0, 500) : output;
        GameFpsLog.add("STDOUT " + head.replace('\n', '|'));
    }

    private static long extractTotalFrames(String output) {
        if (output == null) return -1;
        Matcher m = TOTAL_FRAMES_PATTERN.matcher(output);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {}
        }
        return -1;
    }

    public interface Listener {
        void onFps(float fps, String source);
        void onStatus(String message);
    }

    private volatile Listener listener;

    public void setListener(Listener l) {
        this.listener = l;
    }

    private void notifyStatus(String msg) {
        Listener l = listener;
        if (l != null) handler.post(() -> l.onStatus(msg));
    }

    private void notifyFps(float fps, String source) {
        Listener l = listener;
        if (l != null) handler.post(() -> l.onFps(fps, source));
    }

    public GameFpsPoC(Context ctx) {
        this.context = ctx.getApplicationContext();
        try {
            android.content.SharedPreferences sp = this.context.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE);
            String saved = sp.getString("game_fps_poc_target", null);
            if (saved != null && !saved.isEmpty()) {
                this.targetPackage = saved;
            }
        } catch (Throwable ignored) {}
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String pkg) {
        this.targetPackage = pkg;
        this.lastTotalFrames = -1;
        this.lastTotalFramesTime = 0;
        this.sfLayers = null;
        this.sfLayerIndex = 0;
        this.sfLayerCheckedAt = 0;
        this.lastStatus = null;
        try {
            android.content.SharedPreferences sp = this.context.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE);
            sp.edit().putString("game_fps_poc_target", pkg).apply();
        } catch (Throwable ignored) {}
    }

    public void setInterval(long ms) {
        if (ms < 250) ms = 250; // safety
        this.intervalMs = ms;
    }

    private volatile java.util.List<String> sfLayers = null;
    private int sfLayerIndex = 0;
    private long sfLayerCheckedAt = 0;
    private volatile String lastStatus = null;

    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (running.get()) return;
        if (!ShizukuHelper.isBinderAlive()) {
            Log.w(TAG, "Shizuku binder not alive");
            GameFpsLog.add("START gagal: Shizuku binder tidak aktif");
            setStatus("Shizuku tidak aktif");
            return;
        }
        if (!ShizukuHelper.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted");
            GameFpsLog.add("START gagal: izin Shizuku belum diberikan");
            setStatus("Izin Shizuku belum diberikan");
            return;
        }
        running.set(true);
        handler.post(pollRunnable);
        Log.i(TAG, "PoC started, target=" + targetPackage);
        GameFpsLog.add("START target=" + (targetPackage == null ? "-" : targetPackage));
        if (targetPackage == null || targetPackage.isEmpty()) {
            setStatus("Pilih game dulu");
        } else {
            setStatus("Menunggu " + getAppName(context, targetPackage) + " dibuka...");
        }
    }

    public void stop() {
        if (!running.get()) return;
        running.set(false);
        handler.removeCallbacks(pollRunnable);
        sfLayers = null;
        sfLayerIndex = 0;
        sfLayerCheckedAt = 0;
        lastStatus = null;
        Log.i(TAG, "PoC stopped");
    }

    public void shutdown() {
        stop();
        executor.shutdownNow();
    }

    private void setStatus(String msg) {
        if (msg == null) return;
        if (msg.equals(lastStatus)) return;
        lastStatus = msg;
        GameFpsLog.add("STATUS " + msg);
        notifyStatus(msg);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running.get()) return;
            executor.execute(() -> {
                boolean flowing = false;
                try {
                    flowing = pollOnce();
                } catch (Throwable t) {
                    Log.e(TAG, "poll error", t);
                    GameFpsLog.add("ERROR " + t);
                } finally {
                    if (running.get()) {
                        handler.postDelayed(pollRunnable, flowing ? intervalMs : IDLE_INTERVAL);
                    }
                }
            });
        }
    };

    private boolean pollOnce() {
        String pkg = targetPackage;
        if (pkg == null || pkg.isEmpty()) {
            setStatus("Pilih game dulu");
            return false;
        }
        String appName = getAppName(context, pkg);
        long now = System.currentTimeMillis();

        if (!isTargetForeground(pkg)) {
            sfLayers = null;
            sfLayerIndex = 0;
            sfLayerCheckedAt = 0;
            lastTotalFrames = -1;
            lastTotalFramesTime = 0;
            setStatus("Menunggu " + appName + " dibuka...");
            return false;
        }

        if (sfLayers == null || (sfLayers.isEmpty() && now - sfLayerCheckedAt > 3000)) {
            sfLayers = SurfaceFlingerFps.findLayers(pkg, timeoutMs);
            sfLayerIndex = 0;
            sfLayerCheckedAt = now;
            if (!sfLayers.isEmpty()) {
                GameFpsLog.add("LAYER " + sfLayers.get(0));
            }
        }

        while (sfLayers != null && sfLayerIndex < sfLayers.size()) {
            String layer = sfLayers.get(sfLayerIndex);
            float sfFps = SurfaceFlingerFps.readFps(layer, timeoutMs);
            if (sfFps > 0) {
                setStatus("Memantau " + appName);
                notifyFps(sfFps, "surfaceflinger");
                return true;
            }
            if (sfFps == 0f) {
                setStatus("Game terdeteksi, menunggu frame...");
                return false;
            }
            sfLayerIndex++;
        }
        if (sfLayers != null) {
            sfLayers = null;
            sfLayerIndex = 0;
            lastTotalFrames = -1;
            lastTotalFramesTime = 0;
        }

        String cmd = "dumpsys gfxinfo " + pkg;
        ShizukuShell.Result res = ShizukuShell.exec(cmd, timeoutMs);
        if (res.error != null && !res.error.trim().isEmpty()) {
            GameFpsLog.add("STDERR " + res.error.trim());
        }
        String err = res.error == null ? "-" : res.error.trim();
        String combined = (res.output == null ? "" : res.output) + " " + err;
        boolean noProcess = combined.contains("No process found")
                || combined.contains("Unknown package")
                || combined.contains("Unable to find package");
        if (noProcess) {
            lastTotalFrames = -1;
            lastTotalFramesTime = 0;
            setStatus("Menunggu " + appName + " dibuka...");
            return false;
        }
        if (!res.ok) {
            if (err.length() > 160) err = err.substring(0, 160);
            Log.w(TAG, "dumpsys failed: " + err);
            logOutputHead(res.output);
            setStatus("Gagal baca: " + err);
            return false;
        }
        long totalFrames = extractTotalFrames(res.output);
        if (totalFrames < 0) {
            GfxInfoParser.FpsResult fpsRes = GfxInfoParser.parse(res.output);
            if (fpsRes.parsed && fpsRes.fps > 0) {
                setStatus("Memantau " + appName);
                notifyFps(fpsRes.fps, fpsRes.source);
                return true;
            }
            GameFpsLog.add("PARSE gagal: Total frames rendered tidak ditemukan");
            logOutputHead(res.output);
            setStatus("FPS belum terbaca, menunggu frame...");
            return false;
        }

        boolean flowing = false;
        if (lastTotalFrames >= 0 && now > lastTotalFramesTime) {
            long deltaFrames = totalFrames - lastTotalFrames;
            long deltaMs = now - lastTotalFramesTime;
            if (deltaFrames > 0 && deltaMs > 0) {
                float fps = (float) (deltaFrames * 1000.0 / deltaMs);
                setStatus("Memantau " + appName);
                notifyFps(fps, "gfxinfo");
                flowing = true;
            } else {
                setStatus("Game terdeteksi, menunggu frame...");
            }
        } else {
            setStatus("Game terdeteksi, menunggu frame...");
        }
        lastTotalFrames = totalFrames;
        lastTotalFramesTime = now;
        return flowing;
    }

    private Boolean usageAccess;

    private boolean hasUsageAccess() {
        if (usageAccess == null) {
            try {
                AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                int mode = ops == null ? AppOpsManager.MODE_DEFAULT
                        : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), context.getPackageName());
                usageAccess = mode == AppOpsManager.MODE_ALLOWED;
            } catch (Throwable t) {
                usageAccess = false;
            }
        }
        return usageAccess;
    }

    private boolean isTargetForeground(String pkg) {
        if (!hasUsageAccess()) return true;
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return true;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 60_000L, now);
            if (events == null) return true;
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
            if (lastPkg == null) return true;
            return pkg.equals(lastPkg);
        } catch (Throwable t) {
            return true;
        }
    }

    public static String getAppName(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
