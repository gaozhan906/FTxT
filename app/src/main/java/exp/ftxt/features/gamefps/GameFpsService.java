package exp.ftxt.features.gamefps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.features.gamefps.poc.GameFpsLog;
import exp.ftxt.features.gamefps.poc.GameFpsPoC;
import exp.ftxt.features.gamefps.poc.ShizukuHelper;

public class GameFpsService extends Service {
    private static final String TAG = "GameFpsPoC";
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

    private GameFpsPoC poc;
    private Handler handler;
    private WindowManager windowManager;
    private TextView overlayView;
    private WindowManager.LayoutParams overlayParams;
    private String targetName = "";

    public static boolean isRunning() {
        return instance != null;
    }

    public static float getLastFps() {
        return lastFps;
    }

    public static String getLastStatus() {
        return lastStatus;
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

        poc = new GameFpsPoC(this);
        targetName = GameFpsPoC.getAppName(this, currentTarget());
        poc.setListener(new GameFpsPoC.Listener() {
            @Override
            public void onFps(float fps, String source) {
                publishFps(fps);
            }

            @Override
            public void onStatus(String message) {
                publishStatus(message);
            }
        });

        ensureOverlay();
        handler.post(retryRunnable);
        GameFpsLog.add("SERVICE start target=" + currentTarget());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String target = currentTarget();
        if (poc != null && target != null && !target.isEmpty()
                && !target.equals(poc.getTargetPackage())) {
            poc.setTargetPackage(target);
            targetName = GameFpsPoC.getAppName(this, target);
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
        exp.ftxt.features.gamefps.poc.ShizukuShell.closeSession();
        removeOverlay();
        if (listener != null) listener.onStatus("Dihentikan");
        instance = null;
        lastFps = -1f;
        lastStatus = "";
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
            if (poc == null) return;
            if (!poc.isRunning()) {
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

    private String currentTarget() {
        try {
            return getSharedPreferences("ftxt_prefs", MODE_PRIVATE)
                    .getString("game_fps_poc_target", "");
        } catch (Throwable t) {
            return "";
        }
    }

    private void publishFps(float fps) {
        lastFps = fps;
        String text = String.format(Locale.US, "FPS %.1f", fps);
        updateOverlay(targetName.isEmpty() ? text : text + "\n" + targetName);
        if (listener != null) listener.onFps(fps);
    }

    private void publishStatus(String message) {
        lastStatus = message;
        updateOverlay(message);
        updateNotification(message);
        if (listener != null) listener.onStatus(message);
    }

    private void ensureOverlay() {
        if (overlayView != null) return;
        if (!Settings.canDrawOverlays(this)) {
            publishStatus("Izin overlay belum diberikan");
            return;
        }
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            overlayView = new TextView(this);
            overlayView.setTextColor(Color.WHITE);
            overlayView.setTextSize(14f);
            overlayView.setText("FPS --");
            overlayView.setBackgroundColor(0xB3000000);
            int pad = dp(8);
            overlayView.setPadding(pad, dp(4), pad, dp(4));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            overlayParams.gravity = Gravity.TOP | Gravity.START;
            overlayParams.x = dp(16);
            overlayParams.y = dp(96);

            overlayView.setOnTouchListener(new View.OnTouchListener() {
                private float downX, downY;
                private int startX, startY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getRawX();
                            downY = event.getRawY();
                            startX = overlayParams.x;
                            startY = overlayParams.y;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            overlayParams.x = startX + (int) (event.getRawX() - downX);
                            overlayParams.y = startY + (int) (event.getRawY() - downY);
                            try {
                                windowManager.updateViewLayout(overlayView, overlayParams);
                            } catch (Throwable ignored) {}
                            return true;
                        default:
                            return false;
                    }
                }
            });

            windowManager.addView(overlayView, overlayParams);
        } catch (Throwable t) {
            Log.e(TAG, "add overlay failed", t);
            overlayView = null;
            publishStatus("Gagal menampilkan overlay: " + t.getMessage());
        }
    }

    private void updateOverlay(String text) {
        if (overlayView == null) return;
        try {
            overlayView.setText(text);
        } catch (Throwable ignored) {}
    }

    private void removeOverlay() {
        if (overlayView == null || windowManager == null) return;
        try {
            windowManager.removeView(overlayView);
        } catch (Throwable ignored) {}
        overlayView = null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
