package exp.ftxt.features.gamefps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.core.FloatingService;
import exp.ftxt.features.fps_display.FpsConfig;
import exp.ftxt.features.gamefps.reader.GameFpsLog;
import exp.ftxt.features.gamefps.reader.GameFpsReader;
import exp.ftxt.features.gamefps.reader.ShizukuHelper;

public class GameFpsService extends Service {
    private static final String TAG = "GameFpsService";
    private static final String CHANNEL_ID = "game_fps_channel";
    private static final int NOTIF_ID = 2;

    public interface Listener {
        void onFps(float fps);
        void onStatus(String status);
    }

    private static volatile GameFpsService instance;
    private static volatile Listener listener;
    private static volatile float lastFps = -1f;
    private static volatile String lastStatus = "";
    private static volatile String lastTargetPackage = null;

    private GameFpsReader poc;
    private Handler handler;
    private String targetName = "";
    private String lastLabelPackage = null;
    private boolean gameActive = false;

    public static boolean isRunning() {
        return instance != null;
    }

    public static float getLastFps() {
        return lastFps;
    }

    public static String getLastStatus() {
        return lastStatus;
    }

    public static String getLastTargetPackage() {
        return lastTargetPackage;
    }

    public static void setListener(Listener l) {
        listener = l;
        if (l != null) {
            String status = lastStatus;
            if (status != null && !status.isEmpty()) l.onStatus(status);
            if (lastFps >= 0) l.onFps(lastFps);
        }
    }

    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, GameFpsService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, GameFpsService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        try {
            startForeground(NOTIF_ID, buildNotification("Menyiapkan..."));
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed", t);
        }

        if (!shouldRun()) {
            GameFpsLog.add("SERVICE skip: fitur nonaktif atau target tidak valid");
            stopSelf();
            return;
        }

        poc = new GameFpsReader(this);
        targetName = GameFpsReader.getAppName(this, currentTarget());
        poc.setListener(new GameFpsReader.Listener() {
            @Override
            public void onFps(float fps, String source, String packageName) {
                publishFps(fps, packageName);
            }

            @Override
            public void onStatus(String message) {
                publishStatus(message);
            }

            @Override
            public void onUnavailable(boolean holdLast) {
                if (holdLast) {
                    scheduleFallback();
                } else {
                    runFallback();
                }
            }
        });

        ensureGameModule();
        handler.post(retryRunnable);
        GameFpsLog.add("SERVICE start target=" + currentTarget()
                + " mode=" + (GameFpsReader.isAutoMode(this) ? "auto" : "manual"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!shouldRun()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (poc != null && !GameFpsReader.isAutoMode(this)) {
            String target = currentTarget();
            if (target != null && !target.isEmpty()
                    && !target.equals(poc.getTargetPackage())) {
                poc.setTargetPackage(target);
                targetName = GameFpsReader.getAppName(this, target);
            }
        }
        if (poc != null && !poc.isRunning()
                && ShizukuHelper.isBinderAlive() && ShizukuHelper.hasPermission()) {
            poc.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        GameFpsLog.add("SERVICE stop");
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (poc != null) {
            poc.shutdown();
            poc = null;
        }
        exp.ftxt.features.gamefps.reader.ShizukuShell.closeSession();
        FloatingService.setGameActive(false, null);
        FloatingService.stopGameFpsModuleIfIdle();
        if (listener != null) listener.onStatus("Dihentikan");
        instance = null;
        gameActive = false;
        lastFps = -1f;
        lastStatus = "";
        lastTargetPackage = null;
        try {
            stopForeground(true);
        } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!shouldRun()) {
                stopSelf();
                return;
            }
            if (poc != null && !poc.isRunning()) {
                if (ShizukuHelper.isBinderAlive() && ShizukuHelper.hasPermission()) {
                    poc.start();
                } else {
                    publishStatus(ShizukuHelper.isBinderAlive()
                            ? "Menunggu izin Shizuku..."
                            : "Shizuku tidak aktif");
                }
            }
            if (handler != null) handler.postDelayed(this, 2000);
        }
    };

    private final Runnable fallbackRunnable = () -> runFallback();

    private boolean shouldRun() {
        SharedPreferences sp = getSharedPreferences(GameFpsReader.PREFS, MODE_PRIVATE);
        if (!sp.getBoolean(GameFpsReader.PREF_ENABLED, false)) return false;
        if (GameFpsReader.isAutoMode(this)) return true;
        String target = sp.getString(GameFpsReader.PREF_TARGET, "");
        return GameFpsReader.isValidPackage(this, target);
    }

    private String currentTarget() {
        try {
            return getSharedPreferences(GameFpsReader.PREFS, MODE_PRIVATE)
                    .getString(GameFpsReader.PREF_TARGET, "");
        } catch (Throwable t) {
            return "";
        }
    }

    private void ensureGameModule() {
        try {
            if (FloatingService.instance == null) {
                Intent intent = new Intent(this, FloatingService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            } else {
                FloatingService.startModule(FloatingService.fpsModule());
            }
        } catch (Throwable t) {
            GameFpsLog.add("SERVICE gagal menyiapkan overlay: " + t);
        }
    }

    private void scheduleFallback() {
        if (handler == null) return;
        handler.removeCallbacks(fallbackRunnable);
        handler.postDelayed(fallbackRunnable, 2000);
    }

    private void cancelFallback() {
        if (handler != null) handler.removeCallbacks(fallbackRunnable);
    }

    private void runFallback() {
        if (!gameActive) return;
        gameActive = false;
        lastFps = -1f;
        lastTargetPackage = null;
        FloatingService.setGameActive(false, null);
        updateNotification(lastStatus == null || lastStatus.isEmpty()
                ? "Menunggu aplikasi depan..." : lastStatus);
    }

    private void publishFps(float fps, String pkg) {
        cancelFallback();
        gameActive = true;
        lastFps = fps;
        lastTargetPackage = pkg;
        if (pkg != null && !pkg.equals(lastLabelPackage)) {
            lastLabelPackage = pkg;
            targetName = GameFpsReader.getAppName(this, pkg);
        }
        FloatingService.setGameFps(fps, pkg);
        String label = targetName == null || targetName.isEmpty() ? "" : " • " + targetName;
        updateNotification(String.format(Locale.US, "FPS %.1f", fps) + label);
        if (listener != null) listener.onFps(fps);
    }

    private void publishStatus(String message) {
        lastStatus = message;
        updateNotification(message);
        if (listener != null) listener.onStatus(message);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "FTxT Game FPS", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Monitor FPS game via Shizuku");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_apps)
                .setContentTitle("FPS Game")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
        } catch (Throwable ignored) {}
    }
}
