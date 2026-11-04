package exp.ftxt.features.gamefps.poc;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
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

    public void setTargetPackage(String pkg) {
        this.targetPackage = pkg;
        this.lastTotalFrames = -1;
        this.lastTotalFramesTime = 0;
        try {
            android.content.SharedPreferences sp = this.context.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE);
            sp.edit().putString("game_fps_poc_target", pkg).apply();
        } catch (Throwable ignored) {}
    }

    public void setInterval(long ms) {
        if (ms < 250) ms = 250; // safety
        this.intervalMs = ms;
    }

    public void start() {
        if (running.get()) return;
        if (!ShizukuHelper.isBinderAlive()) {
            Log.w(TAG, "Shizuku binder not alive");
            GameFpsLog.add("START gagal: Shizuku binder tidak aktif");
            notifyStatus("Shizuku tidak aktif");
            return;
        }
        if (!ShizukuHelper.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted");
            GameFpsLog.add("START gagal: izin Shizuku belum diberikan");
            notifyStatus("Izin Shizuku belum diberikan");
            return;
        }
        running.set(true);
        handler.post(pollRunnable);
        Log.i(TAG, "PoC started, target=" + targetPackage);
        GameFpsLog.add("START target=" + (targetPackage == null ? "-" : targetPackage));
        notifyStatus("Memantau: " + (targetPackage == null ? "-" : targetPackage));
    }

    public void stop() {
        if (!running.get()) return;
        running.set(false);
        handler.removeCallbacks(pollRunnable);
        Log.i(TAG, "PoC stopped");
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running.get()) return;
            executor.execute(() -> {
                try {
                    String pkg = targetPackage;
                    if (pkg == null || pkg.isEmpty()) {
                        Log.d(TAG, "No target package set, skipping poll");
                        notifyStatus("Pilih aplikasi dulu");
                        return;
                    }
                    String cmd = "dumpsys gfxinfo " + pkg;
                    GameFpsLog.add("RUN " + cmd);
                    ShizukuShell.Result res = ShizukuShell.exec(cmd, timeoutMs);
                    GameFpsLog.add("EXIT " + res.exitCode + " ok=" + res.ok
                            + " outLen=" + (res.output == null ? 0 : res.output.length()));
                    if (res.error != null && !res.error.trim().isEmpty()) {
                        GameFpsLog.add("STDERR " + res.error.trim());
                    }
                    if (!res.ok) {
                        String err = res.error == null ? "-" : res.error.trim();
                        if (err.length() > 160) err = err.substring(0, 160);
                        Log.w(TAG, "dumpsys failed: " + err);
                        logOutputHead(res.output);
                        String combined = (res.output == null ? "" : res.output) + " " + err;
                        if (combined.contains("No process found")
                                || combined.contains("Unknown package")
                                || combined.contains("Unable to find package")) {
                            notifyStatus("Aplikasi belum berjalan: " + pkg);
                        } else {
                            notifyStatus("dumpsys gagal: " + err);
                        }
                        return;
                    }
                    long totalFrames = extractTotalFrames(res.output);
                    long now = System.currentTimeMillis();
                    if (totalFrames >= 0 && lastTotalFrames >= 0 && now > lastTotalFramesTime) {
                        long deltaFrames = totalFrames - lastTotalFrames;
                        long deltaMs = now - lastTotalFramesTime;
                        if (deltaFrames > 0 && deltaMs > 0) {
                            float fps = (float) (deltaFrames * 1000.0 / deltaMs);
                            Log.i(TAG, String.format("FPS=%.1f source=delta pkg=%s", fps, pkg));
                            GameFpsLog.add(String.format(Locale.US,
                                    "FPS %.1f (delta frames=%d ms=%d)", fps, deltaFrames, deltaMs));
                            notifyFps(fps, "delta");
                        }
                    }
                    if (totalFrames >= 0) {
                        GameFpsLog.add("TOTAL frames=" + totalFrames);
                        lastTotalFrames = totalFrames;
                        lastTotalFramesTime = now;
                    } else {
                        GfxInfoParser.FpsResult fpsRes = GfxInfoParser.parse(res.output);
                        if (fpsRes.parsed && fpsRes.fps > 0) {
                            Log.i(TAG, String.format("FPS=%.1f source=%s pkg=%s", fpsRes.fps, fpsRes.source, pkg));
                            GameFpsLog.add(String.format(Locale.US,
                                    "FPS %.1f (parse source=%s)", fpsRes.fps, fpsRes.source));
                            notifyFps(fpsRes.fps, fpsRes.source);
                        } else {
                            Log.d(TAG, "No FPS parsed pkg=" + pkg + " snippet=" + (fpsRes.rawSnippet == null ? "-" : fpsRes.rawSnippet));
                            GameFpsLog.add("PARSE gagal: Total frames rendered tidak ditemukan");
                            logOutputHead(res.output);
                            notifyStatus("FPS belum terbaca dari output gfxinfo");
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "poll error", t);
                    GameFpsLog.add("ERROR " + t);
                } finally {
                    if (running.get()) {
                        handler.postDelayed(pollRunnable, intervalMs);
                    }
                }
            });
        }
    };

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
