package exp.ftxt.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import exp.ftxt.R;
import exp.ftxt.core.FloatingService;
import exp.ftxt.features.network_stats.app_usage.AppUsageConfig;
import exp.ftxt.features.network_stats.app_usage.AppUsageModule;
import exp.ftxt.shared.preset.OverlayPreset;
import exp.ftxt.shared.preset.PresetHandler;
import exp.ftxt.shared.ui.DpadController;
import exp.ftxt.shared.ui.SliderPositionController;

public class NetworkAppDataPositionController {

    private final Activity activity;
    private final SharedPreferences prefs;

    private View btnUp, btnDown, btnLeft, btnRight, btnMinus, btnPlus, dpadCenter;
    private String currentOrientation;

    private DpadController dpad;
    private SliderPositionController sliderController;
    private TextView coordDisplay;
    private TextView activePresetLabel;
    private int displayWidth, displayHeight;

    private static final String PREFS_NAME = "ftxt_prefs";
    private final PresetHandler.StringHolder activePresetName = new PresetHandler.StringHolder();

    private final PresetHandler.Delegate delegate = new PresetHandler.Delegate() {
        @Override
        public String moduleLabel() { return "App Data"; }
        @Override
        public String moduleType() { return "appdata"; }
        @Override
        public String touchPassthroughPrefKey() { return "appdata_lock"; }
        @Override
        public String safeAreaPrefKey() { return "appdata_safe_area"; }
        @Override
        public String posXPrefKey() { return "appdata_pos_x"; }
        @Override
        public String posYPrefKey() { return "appdata_pos_y"; }

        @Override
        public void saveToPreset(OverlayPreset p) {
            p.moduleType = moduleType();
            p.posX = AppUsageConfig.posX;
            p.posY = AppUsageConfig.posY;
            p.size = AppUsageConfig.size;
            p.color = AppUsageConfig.color;
            p.shadow = PresetHandler.copyShadow(AppUsageConfig.shadow);
            p.bgEnabled = AppUsageConfig.bg.enabled;
            p.bgColor = AppUsageConfig.bg.color;
            p.bgPadding = AppUsageConfig.bg.padding;
            p.bgOffsetX = AppUsageConfig.bg.offsetX;
            p.bgOffsetY = AppUsageConfig.bg.offsetY;
            p.bgMargin = AppUsageConfig.bg.margin;
            p.bgRadius = AppUsageConfig.bg.radius;
            p.labelColor = AppUsageConfig.labelColor;
            p.touchPassthrough = AppUsageConfig.touchPassthrough;
            p.safeArea = AppUsageConfig.safeArea;
            p.appMaxRows = AppUsageConfig.maxRows;
            p.appMode = AppUsageConfig.mode;
            p.appInterval = AppUsageConfig.updateInterval;
            p.appThresholdBps = (int) AppUsageConfig.thresholdBps;
            p.appHoldMs = AppUsageConfig.holdMs;
            p.appShowIcon = AppUsageConfig.showIcon;
            p.appShowIdle = AppUsageConfig.showWhenIdle;
        }

        @Override
        public void applyFromPreset(Activity activity, OverlayPreset p, SharedPreferences prefs) {
            AppUsageConfig.posX = p.posX;
            AppUsageConfig.posY = p.posY;
            AppUsageConfig.size = p.size;
            AppUsageConfig.color = p.color;
            if (p.shadow != null) {
                AppUsageConfig.shadow.enabled = p.shadow.enabled;
                AppUsageConfig.shadow.color = p.shadow.color;
                AppUsageConfig.shadow.blur = p.shadow.blur;
                AppUsageConfig.shadow.offsetX = p.shadow.offsetX;
                AppUsageConfig.shadow.offsetY = p.shadow.offsetY;
            }
            AppUsageConfig.bg.enabled = p.bgEnabled;
            AppUsageConfig.bg.color = p.bgColor;
            AppUsageConfig.bg.padding = p.bgPadding;
            AppUsageConfig.bg.offsetX = p.bgOffsetX;
            AppUsageConfig.bg.offsetY = p.bgOffsetY;
            AppUsageConfig.bg.margin = p.bgMargin;
            AppUsageConfig.bg.radius = p.bgRadius;
            if (p.labelColor != null) AppUsageConfig.labelColor = p.labelColor;
            if (p.touchPassthrough != null) AppUsageConfig.touchPassthrough = p.touchPassthrough;
            if (p.safeArea != null) AppUsageConfig.safeArea = p.safeArea;
            if (p.appMaxRows != null) AppUsageConfig.maxRows = p.appMaxRows;
            if (p.appMode != null) AppUsageConfig.mode = p.appMode;
            if (p.appInterval != null) AppUsageConfig.updateInterval = p.appInterval;
            if (p.appThresholdBps != null) AppUsageConfig.thresholdBps = p.appThresholdBps;
            if (p.appHoldMs != null) AppUsageConfig.holdMs = p.appHoldMs;
            if (p.appShowIcon != null) AppUsageConfig.showIcon = p.appShowIcon;
            if (p.appShowIdle != null) AppUsageConfig.showWhenIdle = p.appShowIdle;

            prefs.edit()
                    .putFloat("appdata_size", AppUsageConfig.size)
                    .putInt("appdata_color", AppUsageConfig.color)
                    .putInt("appdata_label_color", AppUsageConfig.labelColor)
                    .putBoolean("appdata_shadow_enabled", AppUsageConfig.shadow.enabled)
                    .putInt("appdata_shadow_color", AppUsageConfig.shadow.color)
                    .putFloat("appdata_shadow_blur", AppUsageConfig.shadow.blur)
                    .putFloat("appdata_shadow_offset_x", AppUsageConfig.shadow.offsetX)
                    .putFloat("appdata_shadow_offset_y", AppUsageConfig.shadow.offsetY)
                    .putBoolean("appdata_safe_area", AppUsageConfig.safeArea)
                    .putBoolean("appdata_lock", AppUsageConfig.touchPassthrough)
                    .putBoolean("appdata_bg_enabled", AppUsageConfig.bg.enabled)
                    .putInt("appdata_bg_color", AppUsageConfig.bg.color)
                    .putInt("appdata_bg_padding", AppUsageConfig.bg.padding)
                    .putInt("appdata_bg_offset_x", AppUsageConfig.bg.offsetX)
                    .putInt("appdata_bg_offset_y", AppUsageConfig.bg.offsetY)
                    .putInt("appdata_bg_margin", AppUsageConfig.bg.margin)
                    .putInt("appdata_bg_radius", AppUsageConfig.bg.radius)
                    .putInt("appdata_max_rows", AppUsageConfig.maxRows)
                    .putInt("appdata_mode", AppUsageConfig.mode)
                    .putFloat("appdata_update_interval", AppUsageConfig.updateInterval)
                    .putLong("appdata_threshold_bps", AppUsageConfig.thresholdBps)
                    .putInt("appdata_hold_ms", AppUsageConfig.holdMs)
                    .putBoolean("appdata_show_icon", AppUsageConfig.showIcon)
                    .putBoolean("appdata_show_when_idle", AppUsageConfig.showWhenIdle)
                    .apply();
        }

        @Override
        public void syncToService() {
            FloatingService.updatePositionForModule(FloatingService.appUsageModule());
            FloatingService.updateSizeForModule(FloatingService.appUsageModule(), AppUsageConfig.size);
            FloatingService.updateColorForModule(FloatingService.appUsageModule(), AppUsageConfig.color);
            FloatingService.updateLabelColorForModule(FloatingService.appUsageModule(), AppUsageConfig.labelColor);
            FloatingService.updateShadowForModule(FloatingService.appUsageModule());
            FloatingService.updateBackgroundForModule(FloatingService.appUsageModule());
            FloatingService.restartModule(FloatingService.appUsageModule());
        }
    };

    public NetworkAppDataPositionController(Activity activity, View rootView) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int orientation = activity.getResources().getConfiguration().orientation;
        currentOrientation = (orientation == Configuration.ORIENTATION_LANDSCAPE) ? "land" : "port";
        loadPositionFromPrefs(currentOrientation);

        FloatingService.setOrientationSuffixForModule(
                FloatingService.appUsageModule(), currentOrientation);

        bindViews(rootView);

        WindowManager wm = activity.getWindowManager();
        DisplayMetrics realMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(realMetrics);
        displayWidth = realMetrics.widthPixels;
        displayHeight = realMetrics.heightPixels;

        AppUsageModule.onPositionUpdate = this::syncAll;

        sliderController = new SliderPositionController(
                rootView.findViewById(R.id.appdata_posXSeekBar),
                rootView.findViewById(R.id.appdata_posYSeekBar),
                rootView.findViewById(R.id.appdata_posXLabel),
                rootView.findViewById(R.id.appdata_posYLabel),
                (x, y) -> onPositionChanged(x, y)
        );
        setupListeners();
        syncAll();
        FloatingService.updatePositionForModule(FloatingService.appUsageModule());
    }

    private void bindViews(View rootView) {
        btnUp = rootView.findViewById(R.id.appdata_btnUp);
        btnDown = rootView.findViewById(R.id.appdata_btnDown);
        btnLeft = rootView.findViewById(R.id.appdata_btnLeft);
        btnRight = rootView.findViewById(R.id.appdata_btnRight);
        btnMinus = rootView.findViewById(R.id.appdata_btnMinus);
        btnPlus = rootView.findViewById(R.id.appdata_btnPlus);
        coordDisplay = rootView.findViewById(R.id.appdata_posCoordDisplay);
        activePresetLabel = rootView.findViewById(R.id.appdata_active_preset_label);
        dpadCenter = rootView.findViewById(R.id.appdata_dpadInterval);
    }

    private void setupListeners() {
        dpad = new DpadController(btnUp, btnDown, btnLeft, btnRight,
                btnMinus, btnPlus, dpadCenter, displayWidth, displayHeight,
                (dx, dy) -> onPositionChanged(
                        clamp(AppUsageConfig.posX + dx),
                        clamp(AppUsageConfig.posY + dy)));
    }

    public void showLoadPresetDialog() {
        PresetHandler.showLoadPresetDialog(activity, delegate, activePresetName, this::syncAll,
                (onSaved) -> PresetHandler.showSavePresetDialog(activity, delegate, onSaved));
    }

    private static float clamp(float val) {
        return Math.max(0, Math.min(1, val));
    }

    private void onPositionChanged(float x, float y) {
        AppUsageConfig.posX = x;
        AppUsageConfig.posY = y;
        syncAll();
        PresetHandler.savePositionToPrefs(prefs, delegate, currentOrientation, x, y);
        FloatingService.updatePositionForModule(FloatingService.appUsageModule());
    }

    private void loadPositionFromPrefs(String orient) {
        String sfx = "_" + orient;
        AppUsageConfig.posX = prefs.getFloat("appdata_pos_x" + sfx, 0.5f);
        AppUsageConfig.posY = prefs.getFloat("appdata_pos_y" + sfx, 0.12f);
    }

    public void cleanup() {
        AppUsageModule.onPositionUpdate = null;
        FloatingService.setOrientationSuffixForModule(FloatingService.appUsageModule(), null);
        if (dpad != null) dpad.cleanup();
    }

    public void refresh() {
        syncAll();
    }

    public void syncAll() {
        sliderController.sync(AppUsageConfig.posX, AppUsageConfig.posY);
        updateCoordDisplay();
        updateActivePresetLabel();
    }

    private void updateActivePresetLabel() {
        if (activePresetLabel == null) return;
        String name = activePresetName.value;
        if (name != null && !name.isEmpty()) {
            activePresetLabel.setText("Aktif: " + name);
            activePresetLabel.setVisibility(View.VISIBLE);
        } else {
            activePresetLabel.setVisibility(View.GONE);
        }
    }

    private void updateCoordDisplay() {
        if (coordDisplay == null) return;
        int px, py;
        int[] pos = FloatingService.getCurrentPositionForModule(FloatingService.appUsageModule());
        if (pos != null) {
            px = pos[0];
            py = pos[1];
        } else {
            px = Math.round(AppUsageConfig.posX * displayWidth);
            py = Math.round(AppUsageConfig.posY * displayHeight);
        }
        coordDisplay.setText(px + "X" + py);
    }
}
