package exp.ftxt.features.network_stats.app_usage;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import exp.ftxt.shared.ui.OverlayDragHandler;
import exp.ftxt.shared.ui.OverlayModule;
import exp.ftxt.shared.ui.OverlayShadow;
import exp.ftxt.shared.ui.SmartPanelTarget;

public class AppUsageModule implements OverlayModule, SmartPanelTarget {

    private AppUsageOverlayView view;
    private WindowManager.LayoutParams params;
    private WindowManager wm;
    private Context context;
    private SharedPreferences prefs;
    private ConnectingAppSource source;
    private ExecutorService executor;
    private boolean running;
    private boolean userHidden;
    private boolean hasContent;
    private final AtomicBoolean querying = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<ConnectingAppSource.AppTraffic> lastApps = Collections.emptyList();
    private long lastActivityAt;

    private String orientationSuffix;

    public static Runnable onPositionUpdate;

    @Override
    public void setOrientationSuffix(String suffix) {
        this.orientationSuffix = suffix;
    }

    @Override
    public void init(WindowManager windowManager, Context ctx, SharedPreferences sp) {
        wm = windowManager;
        context = ctx;
        prefs = sp;
        orientationSuffix = null;
        loadPosition();
    }

    @Override
    public void start(WindowManager windowManager, Context ctx) {
        if (running) return;
        wm = windowManager;
        context = ctx;
        prefs = ctx.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE);
        source = new ConnectingAppSource(ctx);
        executor = Executors.newSingleThreadExecutor();

        lastApps = Collections.emptyList();
        lastActivityAt = System.currentTimeMillis();
        hasContent = false;
        userHidden = false;
        view = new AppUsageOverlayView(ctx);
        applyViewConfig();

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = (int) (AppUsageConfig.posX * getScreenWidth());
        params.y = (int) (AppUsageConfig.posY * getScreenHeight());

        OverlayShadow.apply(view, params, wm, AppUsageConfig.shadow, 4f);
        updateTouchFlags();
        applyContentVisibility();

        try {
            wm.addView(view, params);
        } catch (Exception e) {
            e.printStackTrace();
            view = null;
            params = null;
            source = null;
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            return;
        }

        view.post(this::updatePosition);
        running = true;
        handler.post(tickRunnable);
    }

    @Override
    public void stop() {
        running = false;
        handler.removeCallbacks(tickRunnable);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        source = null;
        querying.set(false);
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (Exception e) {
                e.printStackTrace();
            }
            view = null;
        }
        params = null;
        hasContent = false;
        userHidden = false;
        lastApps = Collections.emptyList();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void updateSize(float size) {
        AppUsageConfig.size = size;
        if (view != null) {
            view.setOverlayTextSize(size);
            view.post(this::updatePosition);
        }
    }

    @Override
    public void updateColor(int color) {
        AppUsageConfig.color = color;
        if (view != null) view.setTextColors(color, AppUsageConfig.labelColor);
    }

    @Override
    public void updateLabelColor(int color) {
        AppUsageConfig.labelColor = color;
        if (view != null) view.setTextColors(AppUsageConfig.color, color);
    }

    @Override
    public void updateShadow() {
        if (view != null) view.setShadowConfig(AppUsageConfig.shadow);
        OverlayShadow.apply(view, params, wm, AppUsageConfig.shadow, 4f);
    }

    @Override
    public void updateBackground() {
        if (view != null) {
            view.setBackgroundConfig(AppUsageConfig.bg);
            view.post(this::updatePosition);
        }
    }

    @Override
    public void updateLayout() {
        if (view == null) return;
        applyViewConfig();
        view.post(this::updatePosition);
    }

    @Override
    public void updatePosition() {
        if (view != null && params != null && wm != null) {
            params.x = (int) (AppUsageConfig.posX * getScreenWidth());
            params.y = (int) (AppUsageConfig.posY * getScreenHeight());
            if (AppUsageConfig.safeArea && view.getWidth() > 0 && view.getHeight() > 0) {
                int maxX = Math.max(0, getScreenWidth() - view.getWidth());
                int maxY = Math.max(0, getScreenHeight() - view.getHeight());
                params.x = Math.max(0, Math.min(params.x, maxX));
                params.y = Math.max(0, Math.min(params.y, maxY));
            }
            try {
                wm.updateViewLayout(view, params);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (onPositionUpdate != null) onPositionUpdate.run();
        }
    }

    @Override
    public void updateTouchFlags() {
        if (params == null || view == null || wm == null) return;

        if (AppUsageConfig.touchPassthrough) {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            view.setOnTouchListener(null);
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            view.setOnTouchListener(new OverlayDragHandler(params, wm,
                    null,
                    () -> {
                        if (AppUsageConfig.safeArea && view != null
                                && view.getWidth() > 0 && view.getHeight() > 0) {
                            params.x = Math.max(0, Math.min(params.x,
                                    getScreenWidth() - view.getWidth()));
                            params.y = Math.max(0, Math.min(params.y,
                                    getScreenHeight() - view.getHeight()));
                        }
                        if (params != null) {
                            AppUsageConfig.posX = Math.max(0, Math.min(1,
                                    (float) params.x / getScreenWidth()));
                            AppUsageConfig.posY = Math.max(0, Math.min(1,
                                    (float) params.y / getScreenHeight()));
                        }
                        if (onPositionUpdate != null) onPositionUpdate.run();
                    }));
        }

        try {
            wm.updateViewLayout(view, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reloadPosition() {
        orientationSuffix = null;
        loadPosition();
    }

    private String posSuffix() {
        if (orientationSuffix != null) return "_" + orientationSuffix;
        return (context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) ? "_land" : "_port";
    }

    private void loadPosition() {
        if (prefs == null) return;
        String sfx = posSuffix();
        AppUsageConfig.posX = prefs.getFloat("appdata_pos_x" + sfx, 0.5f);
        AppUsageConfig.posY = prefs.getFloat("appdata_pos_y" + sfx, 0.12f);
        updatePosition();
    }

    @Override
    public int[] getCurrentPosition() {
        if (params != null) return new int[]{params.x, params.y};
        return null;
    }

    @Override
    public void hide() {
        userHidden = true;
        resetContent();
        if (view != null) view.setVisibility(View.GONE);
    }

    @Override
    public void show() {
        userHidden = false;
        resetContent();
        lastActivityAt = System.currentTimeMillis();
        applyContentVisibility();
    }

    @Override
    public boolean isHidden() {
        return userHidden;
    }

    private void applyViewConfig() {
        if (view == null) return;
        view.setOverlayTextSize(AppUsageConfig.size);
        view.setTextColors(AppUsageConfig.color, AppUsageConfig.labelColor);
        view.setTextStyleConfig(AppUsageConfig.textBold, AppUsageConfig.textItalic);
        view.setUnitConfig(AppUsageConfig.unitMode, AppUsageConfig.unitTrimmed);
        view.setShowDecimal(AppUsageConfig.showDecimal);
        view.setDecimalPlaces(AppUsageConfig.decimalPlaces);
        view.setShowIcon(AppUsageConfig.showIcon);
        view.setShowName(AppUsageConfig.showName);
        view.setShadowConfig(AppUsageConfig.shadow);
        view.setBackgroundConfig(AppUsageConfig.bg);
        renderContent();
    }

    private void renderContent() {
        if (view == null) return;
        if (hasContent && !lastApps.isEmpty()) {
            view.render(lastApps);
        } else {
            view.showIdle();
        }
        applyContentVisibility();
    }

    private void applyContentVisibility() {
        if (view == null) return;
        long now = System.currentTimeMillis();
        boolean expired = !withinDisplay(now);
        boolean visible = !userHidden && !expired
                && (hasContent || AppUsageConfig.showWhenIdle);
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void resetContent() {
        lastApps = Collections.emptyList();
        hasContent = false;
        lastActivityAt = 0L;
        if (view != null) view.showIdle();
    }

    private boolean withinDisplay(long now) {
        int displayMs = Math.max(0, AppUsageConfig.holdMs);
        return displayMs == 0 || now - lastActivityAt <= displayMs;
    }

    private void renderResult(List<ConnectingAppSource.AppTraffic> apps) {
        if (!running || view == null || userHidden) return;
        long now = System.currentTimeMillis();

        List<ConnectingAppSource.AppTraffic> result =
                apps != null ? apps : Collections.emptyList();
        if (!result.isEmpty()) {
            lastApps = result;
            lastActivityAt = now;
            hasContent = true;
        } else if (!lastApps.isEmpty() && withinDisplay(now)) {
            result = lastApps;
            hasContent = true;
        } else {
            lastApps = Collections.emptyList();
            hasContent = false;
        }

        if (result.isEmpty()) {
            view.showIdle();
        } else {
            view.render(result);
        }
        applyContentVisibility();
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            final ConnectingAppSource currentSource = source;
            final ExecutorService currentExecutor = executor;
            if (currentSource != null && currentExecutor != null
                    && querying.compareAndSet(false, true)) {
                final long now = System.currentTimeMillis();
                final long intervalMs =
                        Math.max(500L, (long) (AppUsageConfig.updateInterval * 1000f));
                try {
                    currentExecutor.execute(() -> {
                        List<ConnectingAppSource.AppTraffic> result = null;
                        try {
                            result = currentSource.query(now, AppUsageConfig.mode,
                                    AppUsageConfig.maxRows, AppUsageConfig.thresholdBps,
                                    intervalMs);
                        } catch (Exception ignored) {
                        }
                        final List<ConnectingAppSource.AppTraffic> finalResult = result;
                        handler.post(() -> {
                            querying.set(false);
                            renderResult(finalResult);
                        });
                    });
                } catch (RejectedExecutionException e) {
                    querying.set(false);
                }
            }

            long delay = Math.max(500L,
                    (long) (AppUsageConfig.updateInterval * 1000f));
            handler.postDelayed(this, delay);
        }
    };

    // ── SmartPanelTarget ──

    @Override
    public String getTitle() {
        return "App Data";
    }

    @Override
    public boolean isEnabled() {
        return running;
    }

    @Override
    public float getPosX() {
        return AppUsageConfig.posX;
    }

    @Override
    public float getPosY() {
        return AppUsageConfig.posY;
    }

    @Override
    public void moveBy(float dxNorm, float dyNorm) {
        AppUsageConfig.posX = Math.max(0, Math.min(1, AppUsageConfig.posX + dxNorm));
        AppUsageConfig.posY = Math.max(0, Math.min(1, AppUsageConfig.posY + dyNorm));
        updatePosition();
    }

    @Override
    public void resetPosition() {
        AppUsageConfig.posX = 0.5f;
        AppUsageConfig.posY = 0.12f;
        updatePosition();
    }

    @Override
    public boolean isPositionLocked() {
        return AppUsageConfig.touchPassthrough;
    }

    @Override
    public void setPositionLocked(boolean locked) {
        AppUsageConfig.touchPassthrough = locked;
        updateTouchFlags();
    }

    @Override
    public boolean canSetSize() {
        return true;
    }

    @Override
    public float getSize() {
        return AppUsageConfig.size;
    }

    @Override
    public void setSize(float size) {
        updateSize(size);
    }

    private int getScreenWidth() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.widthPixels;
    }

    private int getScreenHeight() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.heightPixels;
    }
}
