package exp.ftxt.ui;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.core.FloatingService;
import exp.ftxt.features.network_stats.app_usage.AppUsageConfig;
import exp.ftxt.features.network_stats.app_usage.ConnectingAppSource;
import exp.ftxt.shared.color.ColorPickerDialog;
import exp.ftxt.shared.ui.SectionHelper;
import exp.ftxt.shared.ui.SliderLabelEditor;
import exp.ftxt.utils.PermissionHelper;

public class NetworkAppDataController {

    private final MainActivity activity;

    private CheckBox appdataSwitch;
    private CheckBox appdataLockSwitch;
    private LinearLayout appdataPermissionCard;
    private TextView appdataPermissionText;
    private CheckBox appdataShowIconCheck;
    private CheckBox appdataShowIdleCheck;
    private RadioGroup appdataUnitGroup;
    private RadioButton appdataUnitKb;
    private RadioButton appdataUnitBps;
    private CheckBox appdataUnitTrimCheck;
    private CheckBox appdataDecimalCheck;
    private SeekBar appdataSizeSeekBar;
    private CheckBox appdataBoldCheck;
    private CheckBox appdataItalicCheck;
    private View appdataColorPreview;
    private View appdataLabelColorPreview;
    private View appdataShadowColorPreview;
    private View appdataBgColorPreview;
    private RadioGroup appdataModeGroup;
    private RadioButton appdataModeTop;
    private RadioButton appdataModeForeground;
    private SeekBar appdataRowsSeekBar;
    private SeekBar appdataHoldSeekBar;
    private CheckBox appdataSafeArea;
    private CheckBox appdataShadowSwitch;
    private LinearLayout appdataShadowConfigContainer;
    private SeekBar appdataShadowBlurSeekBar;
    private SeekBar appdataShadowOffsetXSeekBar;
    private SeekBar appdataShadowOffsetYSeekBar;
    private CheckBox appdataBgSwitch;
    private LinearLayout appdataBgConfigContainer;
    private SeekBar appdataBgPaddingSeekBar;
    private SeekBar appdataBgOffsetXSeekBar;
    private SeekBar appdataBgOffsetYSeekBar;
    private SeekBar appdataBgMarginSeekBar;
    private SeekBar appdataBgRadiusSeekBar;
    private TextView appdataSizeLabel;
    private TextView appdataRowsLabel;
    private TextView appdataHoldLabel;
    private TextView appdataShadowBlurLabel;
    private TextView appdataShadowOffsetXLabel;
    private TextView appdataShadowOffsetYLabel;
    private TextView appdataBgPaddingLabel;
    private TextView appdataBgOffsetXLabel;
    private TextView appdataBgOffsetYLabel;
    private TextView appdataBgMarginLabel;
    private TextView appdataBgRadiusLabel;
    private TextView appdataIntervalValue;
    private TextView appdataThresholdValue;

    private NetworkAppDataPositionController positionController;
    private PopupWindow intervalPopup;
    private PopupWindow thresholdPopup;

    private static final float[] INTERVAL_STEPS = {1f, 2f, 3f, 4f, 5f};
    private static final long[] THRESHOLD_STEPS = {0L, 1024L, 5120L, 10240L, 51200L};
    private static final String[] THRESHOLD_LABELS =
            {"0", "1 KB/s", "5 KB/s", "10 KB/s", "50 KB/s"};

    public NetworkAppDataController(MainActivity activity, View rootView) {
        this.activity = activity;
        bindViews(rootView);
        loadConfig();
        setupListeners();
        positionController = new NetworkAppDataPositionController(activity, rootView);
    }

    public void onPanelShown() {
        updatePermissionState(false);
        if (positionController != null) positionController.refresh();
    }

    public void onPanelHidden() {
        dismissPopups();
    }

    public void showLoadPresetDialog() {
        if (positionController != null) positionController.showLoadPresetDialog();
    }

    public void cleanup() {
        dismissPopups();
        if (positionController != null) {
            positionController.cleanup();
            positionController = null;
        }
    }

    private void bindViews(View rootView) {
        appdataSwitch = rootView.findViewById(R.id.appdataSwitch);
        appdataLockSwitch = rootView.findViewById(R.id.appdataLockSwitch);
        appdataPermissionCard = rootView.findViewById(R.id.appdataPermissionCard);
        appdataPermissionText = rootView.findViewById(R.id.appdataPermissionText);
        appdataShowIconCheck = rootView.findViewById(R.id.appdataShowIconCheck);
        appdataShowIdleCheck = rootView.findViewById(R.id.appdataShowIdleCheck);
        appdataUnitGroup = rootView.findViewById(R.id.appdataUnitGroup);
        appdataUnitKb = rootView.findViewById(R.id.appdataUnitKb);
        appdataUnitBps = rootView.findViewById(R.id.appdataUnitBps);
        appdataUnitTrimCheck = rootView.findViewById(R.id.appdataUnitTrimCheck);
        appdataDecimalCheck = rootView.findViewById(R.id.appdataDecimalCheck);
        appdataSizeSeekBar = rootView.findViewById(R.id.appdataSizeSeekBar);
        appdataBoldCheck = rootView.findViewById(R.id.appdataBoldCheck);
        appdataItalicCheck = rootView.findViewById(R.id.appdataItalicCheck);
        appdataColorPreview = rootView.findViewById(R.id.appdataColorPreview);
        appdataLabelColorPreview = rootView.findViewById(R.id.appdataLabelColorPreview);
        appdataShadowColorPreview = rootView.findViewById(R.id.appdataShadowColorPreview);
        appdataBgColorPreview = rootView.findViewById(R.id.appdataBgColorPreview);
        appdataModeGroup = rootView.findViewById(R.id.appdataModeGroup);
        appdataModeTop = rootView.findViewById(R.id.appdataModeTop);
        appdataModeForeground = rootView.findViewById(R.id.appdataModeForeground);
        appdataRowsSeekBar = rootView.findViewById(R.id.appdataRowsSeekBar);
        appdataHoldSeekBar = rootView.findViewById(R.id.appdataHoldSeekBar);
        appdataSafeArea = rootView.findViewById(R.id.appdataSafeArea);
        appdataShadowSwitch = rootView.findViewById(R.id.appdataShadowSwitch);
        appdataShadowConfigContainer = rootView.findViewById(R.id.shadowConfigAppData);
        appdataShadowBlurSeekBar = rootView.findViewById(R.id.appdataShadowBlurSeekBar);
        appdataShadowOffsetXSeekBar = rootView.findViewById(R.id.appdataShadowOffsetXSeekBar);
        appdataShadowOffsetYSeekBar = rootView.findViewById(R.id.appdataShadowOffsetYSeekBar);
        appdataBgSwitch = rootView.findViewById(R.id.appdataBgSwitch);
        appdataBgConfigContainer = rootView.findViewById(R.id.bgConfigAppData);
        appdataBgPaddingSeekBar = rootView.findViewById(R.id.appdataBgPaddingSeekBar);
        appdataBgOffsetXSeekBar = rootView.findViewById(R.id.appdataBgOffsetXSeekBar);
        appdataBgOffsetYSeekBar = rootView.findViewById(R.id.appdataBgOffsetYSeekBar);
        appdataBgMarginSeekBar = rootView.findViewById(R.id.appdataBgMarginSeekBar);
        appdataBgRadiusSeekBar = rootView.findViewById(R.id.appdataBgRadiusSeekBar);
        appdataSizeLabel = rootView.findViewById(R.id.appdataSizeLabel);
        appdataRowsLabel = rootView.findViewById(R.id.appdataRowsLabel);
        appdataHoldLabel = rootView.findViewById(R.id.appdataHoldLabel);
        appdataShadowBlurLabel = rootView.findViewById(R.id.appdataShadowBlurLabel);
        appdataShadowOffsetXLabel = rootView.findViewById(R.id.appdataShadowOffsetXLabel);
        appdataShadowOffsetYLabel = rootView.findViewById(R.id.appdataShadowOffsetYLabel);
        appdataBgPaddingLabel = rootView.findViewById(R.id.appdataBgPaddingLabel);
        appdataBgOffsetXLabel = rootView.findViewById(R.id.appdataBgOffsetXLabel);
        appdataBgOffsetYLabel = rootView.findViewById(R.id.appdataBgOffsetYLabel);
        appdataBgMarginLabel = rootView.findViewById(R.id.appdataBgMarginLabel);
        appdataBgRadiusLabel = rootView.findViewById(R.id.appdataBgRadiusLabel);
        appdataIntervalValue = rootView.findViewById(R.id.appdataIntervalValue);
        appdataThresholdValue = rootView.findViewById(R.id.appdataThresholdValue);

        SectionHelper.setupCollapsible(
                rootView.findViewById(R.id.appdata_sectionDisplayHeader),
                rootView.findViewById(R.id.appdata_sectionDisplay));
        SectionHelper.setupCollapsible(
                rootView.findViewById(R.id.appdata_sectionBehaviorHeader),
                rootView.findViewById(R.id.appdata_sectionBehavior));
        SectionHelper.setupCollapsible(
                rootView.findViewById(R.id.appdata_sectionPositionHeader),
                rootView.findViewById(R.id.appdata_sectionPosition));
        SectionHelper.setupCollapsible(
                rootView.findViewById(R.id.appdata_sectionShadowHeader),
                rootView.findViewById(R.id.appdata_sectionShadow));
        SectionHelper.setupCollapsible(
                rootView.findViewById(R.id.appdata_sectionBackgroundHeader),
                rootView.findViewById(R.id.appdata_sectionBackground));
    }

    private void loadConfig() {
        appdataSwitch.setChecked(AppUsageConfig.enabled);
        activity.applyCheckboxTint(appdataSwitch, AppUsageConfig.enabled);
        appdataLockSwitch.setChecked(AppUsageConfig.touchPassthrough);
        activity.applyCheckboxTint(appdataLockSwitch, AppUsageConfig.touchPassthrough);

        appdataShowIconCheck.setChecked(AppUsageConfig.showIcon);
        activity.applyCheckboxTint(appdataShowIconCheck, AppUsageConfig.showIcon);
        appdataShowIdleCheck.setChecked(AppUsageConfig.showWhenIdle);
        activity.applyCheckboxTint(appdataShowIdleCheck, AppUsageConfig.showWhenIdle);

        appdataSizeSeekBar.setProgress((int) AppUsageConfig.size);
        appdataBoldCheck.setChecked(AppUsageConfig.textBold);
        activity.applyCheckboxTint(appdataBoldCheck, AppUsageConfig.textBold);
        appdataItalicCheck.setChecked(AppUsageConfig.textItalic);
        activity.applyCheckboxTint(appdataItalicCheck, AppUsageConfig.textItalic);
        appdataDecimalCheck.setChecked(AppUsageConfig.showDecimal);
        activity.applyCheckboxTint(appdataDecimalCheck, AppUsageConfig.showDecimal);

        if (AppUsageConfig.unitMode == AppUsageConfig.UNIT_BPS) {
            appdataUnitBps.setChecked(true);
        } else {
            appdataUnitKb.setChecked(true);
        }
        appdataUnitTrimCheck.setChecked(AppUsageConfig.unitTrimmed);
        activity.applyCheckboxTint(appdataUnitTrimCheck, AppUsageConfig.unitTrimmed);
        updateUnitRadioLabels(AppUsageConfig.unitTrimmed);

        appdataColorPreview.setBackgroundColor(AppUsageConfig.color);
        appdataLabelColorPreview.setBackgroundColor(AppUsageConfig.labelColor);
        appdataShadowColorPreview.setBackgroundColor(AppUsageConfig.shadow.color);
        appdataBgColorPreview.setBackgroundColor(AppUsageConfig.bg.color);

        if (AppUsageConfig.mode == ConnectingAppSource.MODE_FOREGROUND) {
            appdataModeForeground.setChecked(true);
        } else {
            appdataModeTop.setChecked(true);
        }

        appdataRowsSeekBar.setProgress(clampRows(AppUsageConfig.maxRows) - 1);
        appdataRowsLabel.setText("Jumlah Aplikasi: " + clampRows(AppUsageConfig.maxRows));
        appdataHoldSeekBar.setProgress(Math.max(0, Math.min(10,
                AppUsageConfig.holdMs / 1000)));
        appdataHoldLabel.setText("Tahan setelah berhenti: "
                + Math.max(0, Math.min(10, AppUsageConfig.holdMs / 1000)) + " detik");
        appdataIntervalValue.setText(formatInterval(AppUsageConfig.updateInterval));
        appdataThresholdValue.setText(formatThreshold(AppUsageConfig.thresholdBps));

        appdataShadowSwitch.setChecked(AppUsageConfig.shadow.enabled);
        activity.applyCheckboxTint(appdataShadowSwitch, AppUsageConfig.shadow.enabled);
        appdataShadowConfigContainer.setVisibility(
                AppUsageConfig.shadow.enabled ? View.VISIBLE : View.GONE);
        appdataShadowBlurSeekBar.setProgress((int) AppUsageConfig.shadow.blur);
        appdataShadowOffsetXSeekBar.setProgress((int) AppUsageConfig.shadow.offsetX + 60);
        appdataShadowOffsetYSeekBar.setProgress((int) AppUsageConfig.shadow.offsetY + 60);
        appdataShadowBlurLabel.setText("Blur Shadow: " + (int) AppUsageConfig.shadow.blur);
        appdataShadowOffsetXLabel.setText("Shadow X: " + (int) AppUsageConfig.shadow.offsetX);
        appdataShadowOffsetYLabel.setText("Shadow Y: " + (int) AppUsageConfig.shadow.offsetY);

        appdataBgSwitch.setChecked(AppUsageConfig.bg.enabled);
        activity.applyCheckboxTint(appdataBgSwitch, AppUsageConfig.bg.enabled);
        appdataBgConfigContainer.setVisibility(
                AppUsageConfig.bg.enabled ? View.VISIBLE : View.GONE);
        appdataBgPaddingSeekBar.setProgress(AppUsageConfig.bg.padding);
        appdataBgOffsetXSeekBar.setProgress(AppUsageConfig.bg.offsetX + 60);
        appdataBgOffsetYSeekBar.setProgress(AppUsageConfig.bg.offsetY + 60);
        appdataBgMarginSeekBar.setProgress(AppUsageConfig.bg.margin);
        appdataBgRadiusSeekBar.setProgress(AppUsageConfig.bg.radius);
        appdataBgPaddingLabel.setText("Ukuran Background: " + AppUsageConfig.bg.padding);
        appdataBgOffsetXLabel.setText("Offset X: " + AppUsageConfig.bg.offsetX);
        appdataBgOffsetYLabel.setText("Offset Y: " + AppUsageConfig.bg.offsetY);
        appdataBgMarginLabel.setText("Margin: " + AppUsageConfig.bg.margin);
        appdataBgRadiusLabel.setText("Radius: " + AppUsageConfig.bg.radius);

        appdataSafeArea.setChecked(AppUsageConfig.safeArea);
        activity.applyCheckboxTint(appdataSafeArea, AppUsageConfig.safeArea);
        appdataSizeLabel.setText("Ukuran Teks: " + (int) AppUsageConfig.size);

        updatePermissionState(false);
    }

    private int clampRows(int rows) {
        return Math.max(1, Math.min(AppUsageConfig.MAX_ROWS, rows));
    }

    private void updateUnitRadioLabels(boolean trimmed) {
        appdataUnitKb.setText(trimmed ? "KB" : "KB/s");
        appdataUnitBps.setText(trimmed ? "b" : "bps");
    }

    private String formatInterval(float value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format("%.2f", value).replaceAll("0$", "").replaceAll("\\.$", "");
    }

    private String formatThreshold(long bytesPerSec) {
        if (bytesPerSec <= 0L) return "0";
        if (bytesPerSec % 1024L == 0L) {
            return (bytesPerSec / 1024L) + " KB/s";
        }
        return bytesPerSec + " B/s";
    }

    private void persist(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    private void persist(String key, int value) {
        prefs().edit().putInt(key, value).apply();
    }

    private void persist(String key, float value) {
        prefs().edit().putFloat(key, value).apply();
    }

    private void persist(String key, long value) {
        prefs().edit().putLong(key, value).apply();
    }

    private android.content.SharedPreferences prefs() {
        return activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE);
    }

    private void updatePermissionState(boolean requestIfMissing) {
        boolean granted = PermissionHelper.hasUsageAccess(activity);
        appdataPermissionCard.setVisibility(granted ? View.GONE : View.VISIBLE);
        appdataModeForeground.setEnabled(granted);
        appdataModeForeground.setAlpha(granted ? 1f : 0.5f);
        if (!granted) {
            if (AppUsageConfig.mode == ConnectingAppSource.MODE_FOREGROUND) {
                AppUsageConfig.mode = ConnectingAppSource.MODE_TOP_TALKERS;
                appdataModeTop.setChecked(true);
                persist("appdata_mode", AppUsageConfig.mode);
            }
            if (requestIfMissing) PermissionHelper.requestUsageAccess(activity);
        }
    }

    private void setupListeners() {
        appdataSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !PermissionHelper.hasOverlayPermission(activity)) {
                appdataSwitch.setChecked(false);
                activity.applyCheckboxTint(appdataSwitch, false);
                persist("appdata_enabled", false);
                return;
            }

            AppUsageConfig.enabled = isChecked;
            activity.applyCheckboxTint(appdataSwitch, isChecked);
            persist("appdata_enabled", isChecked);

            if (isChecked) {
                updatePermissionState(true);
                if (FloatingService.instance != null) {
                    FloatingService.startModule(FloatingService.appUsageModule());
                } else {
                    activity.startService(new Intent(activity, FloatingService.class));
                }
            } else {
                FloatingService.stopModule(FloatingService.appUsageModule());
            }
        });

        appdataLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.touchPassthrough = isChecked;
            activity.applyCheckboxTint(appdataLockSwitch, isChecked);
            persist("appdata_lock", isChecked);
            FloatingService.updateTouchFlagsForModule(FloatingService.appUsageModule());
        });

        appdataPermissionCard.setOnClickListener(v -> {
            if (!PermissionHelper.hasUsageAccess(activity)) {
                PermissionHelper.requestUsageAccess(activity);
            }
        });
        View permissionButton =
                appdataPermissionCard.findViewById(R.id.appdataPermissionBtn);
        if (permissionButton != null) {
            permissionButton.setOnClickListener(v ->
                    PermissionHelper.requestUsageAccess(activity));
        }

        appdataShowIconCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.showIcon = isChecked;
            activity.applyCheckboxTint(appdataShowIconCheck, isChecked);
            persist("appdata_show_icon", isChecked);
            FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
        });

        appdataShowIdleCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.showWhenIdle = isChecked;
            activity.applyCheckboxTint(appdataShowIdleCheck, isChecked);
            persist("appdata_show_when_idle", isChecked);
            FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
        });

        appdataUnitGroup.setOnCheckedChangeListener((group, checkedId) -> {
            AppUsageConfig.unitMode = checkedId == R.id.appdataUnitBps
                    ? AppUsageConfig.UNIT_BPS : AppUsageConfig.UNIT_KB;
            persist("appdata_unit_mode", AppUsageConfig.unitMode);
            FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
        });

        appdataUnitTrimCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.unitTrimmed = isChecked;
            activity.applyCheckboxTint(appdataUnitTrimCheck, isChecked);
            updateUnitRadioLabels(isChecked);
            persist("appdata_unit_trimmed", isChecked);
            FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
        });

        appdataDecimalCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.showDecimal = isChecked;
            activity.applyCheckboxTint(appdataDecimalCheck, isChecked);
            persist("appdata_show_decimal", isChecked);
            FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
        });

        appdataSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 8) {
                    progress = 8;
                    seekBar.setProgress(progress);
                }
                AppUsageConfig.size = progress;
                appdataSizeLabel.setText("Ukuran Teks: " + progress);
                persist("appdata_size", (float) progress);
                FloatingService.updateSizeForModule(FloatingService.appUsageModule(),
                        AppUsageConfig.size);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        appdataBoldCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.textBold = isChecked;
            activity.applyCheckboxTint(appdataBoldCheck, isChecked);
            persist("appdata_text_bold", isChecked);
            FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
        });

        appdataItalicCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.textItalic = isChecked;
            activity.applyCheckboxTint(appdataItalicCheck, isChecked);
            persist("appdata_text_italic", isChecked);
            FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
        });

        appdataColorPreview.setOnClickListener(v ->
                ColorPickerDialog.show(activity, "Warna Nilai", AppUsageConfig.color, color -> {
                    AppUsageConfig.color = color;
                    appdataColorPreview.setBackgroundColor(color);
                    persist("appdata_color", color);
                    FloatingService.updateColorForModule(
                            FloatingService.appUsageModule(), color);
                }));

        appdataLabelColorPreview.setOnClickListener(v ->
                ColorPickerDialog.show(activity, "Warna Nama Aplikasi",
                        AppUsageConfig.labelColor, color -> {
                            AppUsageConfig.labelColor = color;
                            appdataLabelColorPreview.setBackgroundColor(color);
                            persist("appdata_label_color", color);
                            FloatingService.updateLabelColorForModule(
                                    FloatingService.appUsageModule(), color);
                        }));

        appdataShadowColorPreview.setOnClickListener(v ->
                ColorPickerDialog.show(activity, "Warna Shadow",
                        AppUsageConfig.shadow.color, color -> {
                            AppUsageConfig.shadow.color = color;
                            appdataShadowColorPreview.setBackgroundColor(color);
                            persist("appdata_shadow_color", color);
                            FloatingService.updateShadowForModule(
                                    FloatingService.appUsageModule());
                        }));

        appdataBgColorPreview.setOnClickListener(v ->
                ColorPickerDialog.show(activity, "Warna Background",
                        AppUsageConfig.bg.color, color -> {
                            AppUsageConfig.bg.color = color;
                            appdataBgColorPreview.setBackgroundColor(color);
                            persist("appdata_bg_color", color);
                            FloatingService.updateBackgroundForModule(
                                    FloatingService.appUsageModule());
                        }));

        appdataModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.appdataModeForeground
                    && !PermissionHelper.hasUsageAccess(activity)) {
                appdataModeTop.setChecked(true);
                PermissionHelper.requestUsageAccess(activity);
                return;
            }
            AppUsageConfig.mode = checkedId == R.id.appdataModeForeground
                    ? ConnectingAppSource.MODE_FOREGROUND
                    : ConnectingAppSource.MODE_TOP_TALKERS;
            persist("appdata_mode", AppUsageConfig.mode);
        });

        appdataRowsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int rows = clampRows(progress + 1);
                AppUsageConfig.maxRows = rows;
                appdataRowsLabel.setText("Jumlah Aplikasi: " + rows);
                persist("appdata_max_rows", rows);
                FloatingService.updateLayoutForModule(FloatingService.appUsageModule());
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        appdataHoldSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppUsageConfig.holdMs = progress * 1000;
                appdataHoldLabel.setText("Tahan setelah berhenti: " + progress + " detik");
                persist("appdata_hold_ms", AppUsageConfig.holdMs);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        appdataSafeArea.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.safeArea = isChecked;
            activity.applyCheckboxTint(appdataSafeArea, isChecked);
            persist("appdata_safe_area", isChecked);
            FloatingService.updatePositionForModule(FloatingService.appUsageModule());
        });

        appdataShadowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.shadow.enabled = isChecked;
            activity.applyCheckboxTint(appdataShadowSwitch, isChecked);
            appdataShadowConfigContainer.setVisibility(
                    isChecked ? View.VISIBLE : View.GONE);
            persist("appdata_shadow_enabled", isChecked);
            saveShadowPrefs();
            FloatingService.updateShadowForModule(FloatingService.appUsageModule());
        });

        appdataShadowBlurSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        AppUsageConfig.shadow.blur = progress;
                        appdataShadowBlurLabel.setText("Blur Shadow: " + progress);
                        persist("appdata_shadow_blur", (float) progress);
                        FloatingService.updateShadowForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataShadowOffsetXSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int offset = progress - 60;
                        AppUsageConfig.shadow.offsetX = offset;
                        appdataShadowOffsetXLabel.setText("Shadow X: " + offset);
                        persist("appdata_shadow_offset_x", (float) offset);
                        FloatingService.updateShadowForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataShadowOffsetYSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int offset = progress - 60;
                        AppUsageConfig.shadow.offsetY = offset;
                        appdataShadowOffsetYLabel.setText("Shadow Y: " + offset);
                        persist("appdata_shadow_offset_y", (float) offset);
                        FloatingService.updateShadowForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataBgSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUsageConfig.bg.enabled = isChecked;
            activity.applyCheckboxTint(appdataBgSwitch, isChecked);
            appdataBgConfigContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            persist("appdata_bg_enabled", isChecked);
            FloatingService.updateBackgroundForModule(FloatingService.appUsageModule());
        });

        appdataBgPaddingSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        AppUsageConfig.bg.padding = progress;
                        appdataBgPaddingLabel.setText("Ukuran Background: " + progress);
                        persist("appdata_bg_padding", progress);
                        FloatingService.updateBackgroundForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataBgOffsetXSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int offset = progress - 60;
                        AppUsageConfig.bg.offsetX = offset;
                        appdataBgOffsetXLabel.setText("Offset X: " + offset);
                        persist("appdata_bg_offset_x", offset);
                        FloatingService.updateBackgroundForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataBgOffsetYSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        int offset = progress - 60;
                        AppUsageConfig.bg.offsetY = offset;
                        appdataBgOffsetYLabel.setText("Offset Y: " + offset);
                        persist("appdata_bg_offset_y", offset);
                        FloatingService.updateBackgroundForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataBgMarginSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        AppUsageConfig.bg.margin = progress;
                        appdataBgMarginLabel.setText("Margin: " + progress);
                        persist("appdata_bg_margin", progress);
                        FloatingService.updateBackgroundForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataBgRadiusSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        AppUsageConfig.bg.radius = progress;
                        appdataBgRadiusLabel.setText("Radius: " + progress);
                        persist("appdata_bg_radius", progress);
                        FloatingService.updateBackgroundForModule(
                                FloatingService.appUsageModule());
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        appdataSizeLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Ukuran Teks",
                        appdataSizeSeekBar, 100, appdataSizeLabel, "Ukuran Teks: "));
        appdataShadowBlurLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Blur Shadow",
                        appdataShadowBlurSeekBar, 50, appdataShadowBlurLabel,
                        "Blur Shadow: "));
        appdataShadowOffsetXLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Shadow X",
                        appdataShadowOffsetXSeekBar, appdataShadowOffsetXLabel,
                        "Shadow X: "));
        appdataShadowOffsetYLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Shadow Y",
                        appdataShadowOffsetYSeekBar, appdataShadowOffsetYLabel,
                        "Shadow Y: "));
        appdataBgPaddingLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Ukuran Background",
                        appdataBgPaddingSeekBar, 80, appdataBgPaddingLabel,
                        "Ukuran Background: "));
        appdataBgOffsetXLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Offset X",
                        appdataBgOffsetXSeekBar, appdataBgOffsetXLabel, "Offset X: "));
        appdataBgOffsetYLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Offset Y",
                        appdataBgOffsetYSeekBar, appdataBgOffsetYLabel, "Offset Y: "));
        appdataBgMarginLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Margin Background",
                        appdataBgMarginSeekBar, 30, appdataBgMarginLabel, "Margin: "));
        appdataBgRadiusLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Radius Background",
                        appdataBgRadiusSeekBar, 50, appdataBgRadiusLabel, "Radius: "));

        appdataIntervalValue.setOnClickListener(this::showIntervalPopup);
        appdataThresholdValue.setOnClickListener(this::showThresholdPopup);
    }

    private void saveShadowPrefs() {
        prefs().edit()
                .putInt("appdata_shadow_color", AppUsageConfig.shadow.color)
                .putFloat("appdata_shadow_blur", AppUsageConfig.shadow.blur)
                .putFloat("appdata_shadow_offset_x", AppUsageConfig.shadow.offsetX)
                .putFloat("appdata_shadow_offset_y", AppUsageConfig.shadow.offsetY)
                .apply();
    }

    private void showIntervalPopup(View anchor) {
        if (intervalPopup != null && intervalPopup.isShowing()) {
            intervalPopup.dismiss();
            return;
        }
        dismissPopups();

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFFFFFFF);

        for (float step : INTERVAL_STEPS) {
            TextView item = popupItem(formatInterval(step) + "s",
                    step == AppUsageConfig.updateInterval);
            item.setOnClickListener(v -> {
                AppUsageConfig.updateInterval = step;
                appdataIntervalValue.setText(formatInterval(step));
                persist("appdata_update_interval", step);
                FloatingService.restartModule(FloatingService.appUsageModule());
                dismissPopups();
            });
            content.addView(item);
        }

        intervalPopup = buildPopup(content, anchor);
    }

    private void showThresholdPopup(View anchor) {
        if (thresholdPopup != null && thresholdPopup.isShowing()) {
            thresholdPopup.dismiss();
            return;
        }
        dismissPopups();

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFFFFFFF);

        for (int i = 0; i < THRESHOLD_STEPS.length; i++) {
            long value = THRESHOLD_STEPS[i];
            TextView item = popupItem(THRESHOLD_LABELS[i],
                    value == AppUsageConfig.thresholdBps);
            item.setOnClickListener(v -> {
                AppUsageConfig.thresholdBps = value;
                appdataThresholdValue.setText(formatThreshold(value));
                persist("appdata_threshold_bps", value);
                dismissPopups();
            });
            content.addView(item);
        }

        thresholdPopup = buildPopup(content, anchor);
    }

    private TextView popupItem(String text, boolean selected) {
        TextView item = new TextView(activity);
        item.setText(text);
        item.setPadding(dp(16), dp(10), dp(16), dp(10));
        item.setTextSize(14);
        item.setTextColor(selected ? 0xFFFFFFFF : 0xFF222222);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (selected) item.setBackgroundColor(0xFF4A90D9);
        return item;
    }

    private PopupWindow buildPopup(LinearLayout content, View anchor) {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(content);

        PopupWindow popup = new PopupWindow(scrollView,
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(220), true);
        popup.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(4));
        popup.showAsDropDown(anchor, 0, dp(2));
        return popup;
    }

    private void dismissPopups() {
        if (intervalPopup != null) {
            intervalPopup.dismiss();
            intervalPopup = null;
        }
        if (thresholdPopup != null) {
            thresholdPopup.dismiss();
            thresholdPopup = null;
        }
    }

    private int dp(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density);
    }
}
