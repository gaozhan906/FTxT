package exp.ftxt.features.gamefps.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameFpsReader {
    private static final String TAG = "GameFpsReader";
    public static final String PREFS = "ftxt_prefs";
    public static final String PREF_TARGET = "game_fps_poc_target";
    public static final String PREF_ENABLED = "game_fps_overlay_on";
    public static final String PREF_MODE = "game_fps_target_mode";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";

    private static final long DEFAULT_INTERVAL = 1000L;
    private static final long IDLE_INTERVAL = 2000L;
    private static final long MAX_IDLE_INTERVAL = 6000L;
    private static final long DEFAULT_TIMEOUT = 4000L;

    private static final Pattern TOTAL_FRAMES_PATTERN =
            Pattern.compile("Total frames rendered[:\\s]+([0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("[A-Za-z0-9._]+");

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String targetPackage = null;
    private volatile String activePackage = null;
    private long intervalMs = DEFAULT_INTERVAL;
    private long timeoutMs = DEFAULT_TIMEOUT;

    private long lastTotalFrames = -1;
    private long lastTotalFramesTime = 0;
    private volatile long forcedDelay = -1;
    private int idleCount = 0;

    private volatile java.util.List<String> sfLayers = null;
    private int sfLayerIndex = 0;
    private long sfLayerCheckedAt = 0;
    private volatile String lastStatus = null;
    private volatile Listener listener;

    public interface Listener {
        void onFps(float fps, String source, String packageName);
        void onStatus(String message);
        void onUnavailable(boolean holdLast);
    }

    public static boolean isAutoMode(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return MODE_AUTO.equals(sp.getString(PREF_MODE, MODE_MANUAL));
    }

    public static boolean isValidPackage(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.length() > 255) return false;
        if (!PACKAGE_PATTERN.matcher(pkg).matches()) return false;
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
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

    public GameFpsReader(Context ctx) {
        this.context = ctx.getApplicationContext();
        try {
            SharedPreferences sp = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String saved = sp.getString(PREF_TARGET, null);
            if (saved != null && !saved.isEmpty() && isValidPackage(this.context, saved)) {
                this.targetPackage = saved;
            } else if (saved != null && !saved.isEmpty()) {
                sp.edit().remove(PREF_TARGET).apply();
            }
        } catch (Throwable ignored) {}
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public String getActivePackage() {
        return activePackage;
    }

    public void setTargetPackage(String pkg) {
        if (pkg != null && !pkg.isEmpty() && !isValidPackage(context, pkg)) return;
        this.targetPackage = pkg;
        resetReadState();
        this.lastStatus = null;
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (pkg == null || pkg.isEmpty()) {
                sp.edit().remove(PREF_TARGET).apply();
            } else {
                sp.edit().putString(PREF_TARGET, pkg).apply();
            }
        } catch (Throwable ignored) {}
    }

    public void setInterval(long ms) {
        if (ms < 250) ms = 250;
        this.intervalMs = ms;
    }

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
        idleCount = 0;
        handler.post(pollRunnable);
        Log.i(TAG, "Reader started, target=" + targetPackage
                + " mode=" + (isAutoMode(context) ? "auto" : "manual"));
        GameFpsLog.add("START target=" + (targetPackage == null ? "-" : targetPackage)
                + " mode=" + (isAutoMode(context) ? "auto" : "manual"));
        if (isAutoMode(context)) {
            setStatus("Menunggu aplikasi depan...");
        } else if (targetPackage == null || targetPackage.isEmpty()) {
            setStatus("Pilih game dulu");
        } else {
            setStatus("Menunggu " + getAppName(context, targetPackage) + " dibuka...");
        }
    }

    public void stop() {
        if (!running.get()) return;
        running.set(false);
        handler.removeCallbacks(pollRunnable);
        resetReadState();
        activePackage = null;
        lastStatus = null;
        Log.i(TAG, "Reader stopped");
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
        Listener l = listener;
        if (l != null) handler.post(() -> l.onStatus(msg));
    }

    private void notifyFps(float fps, String source, String pkg) {
        Listener l = listener;
        if (l != null) handler.post(() -> l.onFps(fps, source, pkg));
    }

    private void notifyUnavailable(boolean holdLast) {
        Listener l = listener;
        if (l != null) handler.post(() -> l.onUnavailable(holdLast));
    }

    private void resetReadState() {
        sfLayers = null;
        sfLayerIndex = 0;
        sfLayerCheckedAt = 0;
        lastTotalFrames = -1;
        lastTotalFramesTime = 0;
    }

    private long idleDelay() {
        idleCount = Math.min(idleCount + 1, 3);
        return Math.min(IDLE_INTERVAL * idleCount, MAX_IDLE_INTERVAL);
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
                        long delay;
                        if (forcedDelay > 0) {
                            delay = forcedDelay;
                            forcedDelay = -1;
                        } else {
                            delay = flowing ? intervalMs : idleDelay();
                        }
                        handler.postDelayed(pollRunnable, delay);
                    }
                }
            });
        }
    };

    private boolean pollOnce() {
        boolean auto = isAutoMode(context);
        String pkg;

        if (auto) {
            pkg = ForegroundDetector.getForegroundPackage(context);
            if (pkg == null || pkg.isEmpty()) {
                resetReadState();
                setStatus("Aplikasi depan tidak terdeteksi");
                notifyUnavailable(false);
                return false;
            }
            if (ForegroundDetector.isExcluded(context, pkg)) {
                resetReadState();
                setStatus("Menunggu aplikasi depan...");
                notifyUnavailable(true);
                return false;
            }
            if (!pkg.equals(activePackage)) {
                activePackage = pkg;
                resetReadState();
                setStatus("Beralih ke " + getAppName(context, pkg));
                notifyUnavailable(false);
                forcedDelay = 500;
                return false;
            }
        } else {
            pkg = targetPackage;
            if (pkg == null || pkg.isEmpty() || !isValidPackage(context, pkg)) {
                if (pkg != null && !pkg.isEmpty()) {
                    try {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit().remove(PREF_TARGET).apply();
                    } catch (Throwable ignored) {}
                }
                targetPackage = null;
                resetReadState();
                setStatus("Target tidak terpasang");
                notifyUnavailable(false);
                return false;
            }
            if (!pkg.equals(activePackage)) {
                activePackage = pkg;
                resetReadState();
            }
            if (!ForegroundDetector.isForeground(context, pkg)) {
                resetReadState();
                setStatus("Menunggu " + getAppName(context, pkg) + " dibuka...");
                notifyUnavailable(false);
                return false;
            }
        }

        String appName = getAppName(context, pkg);
        long now = System.currentTimeMillis();

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
                idleCount = 0;
                setStatus("Memantau " + appName);
                notifyFps(sfFps, "surfaceflinger", pkg);
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

        ShizukuShell.Result res = ShizukuShell.execArgs(
                new String[]{"dumpsys", "gfxinfo", pkg}, timeoutMs);
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
                idleCount = 0;
                setStatus("Memantau " + appName);
                notifyFps(fpsRes.fps, fpsRes.source, pkg);
                return true;
            }
            GameFpsLog.add("PARSE gagal: Total frames rendered tidak ditemukan"
                    + " (Android " + android.os.Build.VERSION.RELEASE
                    + ", " + android.os.Build.MANUFACTURER + ")");
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
                idleCount = 0;
                setStatus("Memantau " + appName);
                notifyFps(fps, "gfxinfo", pkg);
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
}
