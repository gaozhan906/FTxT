package exp.ftxt.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.core.FloatingService;
import exp.ftxt.features.battery_bar.BatteryBarConfig;
import exp.ftxt.features.battery_bar.DischargeAlertPlayer;
import exp.ftxt.shared.color.ColorPickerDialog;
import exp.ftxt.shared.ui.SectionHelper;
import exp.ftxt.shared.ui.SliderLabelEditor;
import exp.ftxt.utils.PermissionHelper;

public class BatteryBarPanelController {

    private final MainActivity activity;

    private CheckBox barSwitch;
    private RadioGroup barModeGroup;
    private RadioButton barModeQuick;
    private RadioButton barModeManual;
    private View quickSideRow;
    private TextView barQuickSideValue;
    private TextView batbarSubTabDisplay;
    private TextView batbarSubTabAnimation;
    private TextView batbarSubTabSound;
    private View batbarPageDisplay;
    private View batbarPageAnimation;
    private View batbarPageSound;
    private RadioGroup barOrientationGroup;
    private RadioButton barOrientationH;
    private RadioButton barOrientationV;
    private CheckBox barInvertCheck;
    private SeekBar barThicknessSeekBar;
    private TextView barThicknessLabel;
    private SeekBar barLengthSeekBar;
    private TextView barLengthLabel;
    private View barColorPreview;
    private TextView barSchemeSelector;
    private View barLowColorPreview;
    private TextView barLowColorLabel;
    private SeekBar barLowThresholdSeekBar;
    private TextView barLowThresholdLabel;
    private CheckBox barShowEmptyStripCheck;
    private View barEmptyColorPreview;
    private SeekBar barRadiusSeekBar;
    private TextView barRadiusLabel;
    private CheckBox barFadeCheck;
    private SeekBar barFadeSpeedSeekBar;
    private TextView barFadeSpeedLabel;
    private CheckBox barShineCheck;
    private SeekBar barShineSpeedSeekBar;
    private TextView barShineSpeedLabel;
    private SeekBar barShineWidthSeekBar;
    private TextView barShineWidthLabel;
    private CheckBox barWaveCheck;
    private SeekBar barWaveSpeedSeekBar;
    private TextView barWaveSpeedLabel;
    private SeekBar barWaveAmplitudeSeekBar;
    private TextView barWaveAmplitudeLabel;
    private CheckBox barChargeWaveCheck;
    private SeekBar barChargeWaveSpeedSeekBar;
    private TextView barChargeWaveSpeedLabel;
    private SeekBar barChargeWaveAmplitudeSeekBar;
    private TextView barChargeWaveAmplitudeLabel;
    private CheckBox barEdgeGlowCheck;
    private SeekBar barEdgeGlowSpeedSeekBar;
    private TextView barEdgeGlowSpeedLabel;
    private SeekBar barEdgeGlowIntensitySeekBar;
    private TextView barEdgeGlowIntensityLabel;
    private CheckBox barStartupSweepCheck;
    private SeekBar barStartupSweepSpeedSeekBar;
    private TextView barStartupSweepSpeedLabel;
    private CheckBox barDrainTickCheck;
    private CheckBox barDischargeAlertCheck;
    private RadioGroup barDischargeAlertModeGroup;
    private RadioButton barDischargeAlertModeEvery;
    private RadioButton barDischargeAlertModeManual;
    private TextView barDischargeAlertTargetsLabel;
    private TextView barDischargePitchLabel;
    private SeekBar barDischargePitchSeekBar;
    private TextView barDischargeDurationLabel;
    private SeekBar barDischargeDurationSeekBar;
    private TextView barDischargeRepeatLabel;
    private SeekBar barDischargeRepeatSeekBar;
    private TextView barDischargeVolumeLabel;
    private SeekBar barDischargeVolumeSeekBar;
    private CheckBox barDischargeVibrateCheck;
    private TextView barDischargeVibrateDurationLabel;
    private SeekBar barDischargeVibrateDurationSeekBar;
    private Button barDischargeTestButton;
    private CheckBox barLockSwitch;
    private CheckBox barSafeAreaCheck;
    private boolean savedSafeArea = true;

    private View manualSection;
    private TextView manualSectionHeader;

    private BatteryBarPositionController positionController;
    private PopupWindow sidePopup;

    public BatteryBarPanelController(MainActivity activity, View rootView) {
        this.activity = activity;
        bindViews(rootView);
        loadConfig();
        setupListeners();
        positionController = new BatteryBarPositionController(activity, rootView);
        updateManualVisibility();
    }

    public void onPanelShown() {
        if (positionController != null) {
            positionController.refresh();
        }
    }

    public void showLoadPresetDialog() {
        if (positionController != null) {
            positionController.showLoadPresetDialog();
        }
    }

    public void cleanup() {
        if (positionController != null) {
            positionController.cleanup();
            positionController = null;
        }
    }

    private void bindViews(View rootView) {
        barSwitch = rootView.findViewById(R.id.batbarSwitch);
        barModeGroup = rootView.findViewById(R.id.batbar_modeGroup);
        barModeQuick = rootView.findViewById(R.id.batbar_modeQuick);
        barModeManual = rootView.findViewById(R.id.batbar_modeManual);
        quickSideRow = rootView.findViewById(R.id.batbar_quickSideRow);
        barQuickSideValue = rootView.findViewById(R.id.batbarQuickSideValue);
        batbarSubTabDisplay = rootView.findViewById(R.id.batbarSubTabDisplay);
        batbarSubTabAnimation = rootView.findViewById(R.id.batbarSubTabAnimation);
        batbarSubTabSound = rootView.findViewById(R.id.batbarSubTabSound);
        batbarPageDisplay = rootView.findViewById(R.id.batbarPageDisplay);
        batbarPageAnimation = rootView.findViewById(R.id.batbarPageAnimation);
        batbarPageSound = rootView.findViewById(R.id.batbarPageSound);
        barOrientationGroup = rootView.findViewById(R.id.batbar_orientationGroup);
        barOrientationH = rootView.findViewById(R.id.batbar_orientationH);
        barOrientationV = rootView.findViewById(R.id.batbar_orientationV);
        barInvertCheck = rootView.findViewById(R.id.batbarInvertCheck);
        barThicknessSeekBar = rootView.findViewById(R.id.batbarThicknessSeekBar);
        barThicknessLabel = rootView.findViewById(R.id.batbarThicknessLabel);
        barLengthSeekBar = rootView.findViewById(R.id.batbarLengthSeekBar);
        barLengthLabel = rootView.findViewById(R.id.batbarLengthLabel);
        barColorPreview = rootView.findViewById(R.id.batbarColorPreview);
        barSchemeSelector = rootView.findViewById(R.id.batbarSchemeSelector);
        barLowColorPreview = rootView.findViewById(R.id.batbarLowColorPreview);
        barLowColorLabel = rootView.findViewById(R.id.batbarLowColorLabel);
        barLowThresholdSeekBar = rootView.findViewById(R.id.batbarLowThresholdSeekBar);
        barLowThresholdLabel = rootView.findViewById(R.id.batbarLowThresholdLabel);
        barShowEmptyStripCheck = rootView.findViewById(R.id.batbarShowEmptyStripCheck);
        barEmptyColorPreview = rootView.findViewById(R.id.batbarEmptyColorPreview);
        barRadiusSeekBar = rootView.findViewById(R.id.batbarRadiusSeekBar);
        barRadiusLabel = rootView.findViewById(R.id.batbarRadiusLabel);
        barFadeCheck = rootView.findViewById(R.id.batbarFadeCheck);
        barFadeSpeedSeekBar = rootView.findViewById(R.id.batbarFadeSpeedSeekBar);
        barFadeSpeedLabel = rootView.findViewById(R.id.batbarFadeSpeedLabel);
        barShineCheck = rootView.findViewById(R.id.batbarShineCheck);
        barShineSpeedSeekBar = rootView.findViewById(R.id.batbarShineSpeedSeekBar);
        barShineSpeedLabel = rootView.findViewById(R.id.batbarShineSpeedLabel);
        barShineWidthSeekBar = rootView.findViewById(R.id.batbarShineWidthSeekBar);
        barShineWidthLabel = rootView.findViewById(R.id.batbarShineWidthLabel);
        barWaveCheck = rootView.findViewById(R.id.batbarWaveCheck);
        barWaveSpeedSeekBar = rootView.findViewById(R.id.batbarWaveSpeedSeekBar);
        barWaveSpeedLabel = rootView.findViewById(R.id.batbarWaveSpeedLabel);
        barWaveAmplitudeSeekBar = rootView.findViewById(R.id.batbarWaveAmplitudeSeekBar);
        barWaveAmplitudeLabel = rootView.findViewById(R.id.batbarWaveAmplitudeLabel);
        barChargeWaveCheck = rootView.findViewById(R.id.batbarChargeWaveCheck);
        barChargeWaveSpeedSeekBar = rootView.findViewById(R.id.batbarChargeWaveSpeedSeekBar);
        barChargeWaveSpeedLabel = rootView.findViewById(R.id.batbarChargeWaveSpeedLabel);
        barChargeWaveAmplitudeSeekBar = rootView.findViewById(R.id.batbarChargeWaveAmplitudeSeekBar);
        barChargeWaveAmplitudeLabel = rootView.findViewById(R.id.batbarChargeWaveAmplitudeLabel);
        barEdgeGlowCheck = rootView.findViewById(R.id.batbarEdgeGlowCheck);
        barEdgeGlowSpeedSeekBar = rootView.findViewById(R.id.batbarEdgeGlowSpeedSeekBar);
        barEdgeGlowSpeedLabel = rootView.findViewById(R.id.batbarEdgeGlowSpeedLabel);
        barEdgeGlowIntensitySeekBar = rootView.findViewById(R.id.batbarEdgeGlowIntensitySeekBar);
        barEdgeGlowIntensityLabel = rootView.findViewById(R.id.batbarEdgeGlowIntensityLabel);
        barStartupSweepCheck = rootView.findViewById(R.id.batbarStartupSweepCheck);
        barStartupSweepSpeedSeekBar = rootView.findViewById(R.id.batbarStartupSweepSpeedSeekBar);
        barStartupSweepSpeedLabel = rootView.findViewById(R.id.batbarStartupSweepSpeedLabel);
        barDrainTickCheck = rootView.findViewById(R.id.batbarDrainTickCheck);
        barDischargeAlertCheck = rootView.findViewById(R.id.batbarDischargeAlertCheck);
        barDischargeAlertModeGroup = rootView.findViewById(R.id.batbarDischargeAlertModeGroup);
        barDischargeAlertModeEvery = rootView.findViewById(R.id.batbarDischargeAlertModeEvery);
        barDischargeAlertModeManual = rootView.findViewById(R.id.batbarDischargeAlertModeManual);
        barDischargeAlertTargetsLabel = rootView.findViewById(R.id.batbarDischargeAlertTargetsLabel);
        barDischargePitchLabel = rootView.findViewById(R.id.batbarDischargePitchLabel);
        barDischargePitchSeekBar = rootView.findViewById(R.id.batbarDischargePitchSeekBar);
        barDischargeDurationLabel = rootView.findViewById(R.id.batbarDischargeDurationLabel);
        barDischargeDurationSeekBar = rootView.findViewById(R.id.batbarDischargeDurationSeekBar);
        barDischargeRepeatLabel = rootView.findViewById(R.id.batbarDischargeRepeatLabel);
        barDischargeRepeatSeekBar = rootView.findViewById(R.id.batbarDischargeRepeatSeekBar);
        barDischargeVolumeLabel = rootView.findViewById(R.id.batbarDischargeVolumeLabel);
        barDischargeVolumeSeekBar = rootView.findViewById(R.id.batbarDischargeVolumeSeekBar);
        barDischargeVibrateCheck = rootView.findViewById(R.id.batbarDischargeVibrateCheck);
        barDischargeVibrateDurationLabel = rootView.findViewById(R.id.batbarDischargeVibrateDurationLabel);
        barDischargeVibrateDurationSeekBar = rootView.findViewById(R.id.batbarDischargeVibrateDurationSeekBar);
        barDischargeTestButton = rootView.findViewById(R.id.batbarDischargeTestButton);
        barLockSwitch = rootView.findViewById(R.id.batbarLockSwitch);
        barSafeAreaCheck = rootView.findViewById(R.id.batbarSafeAreaCheck);

        manualSection = rootView.findViewById(R.id.batbar_sectionManual);
        manualSectionHeader = rootView.findViewById(R.id.batbar_sectionManualHeader);
        SectionHelper.setupCollapsible(manualSectionHeader, manualSection);

        SectionHelper.setupCollapsible(rootView.findViewById(R.id.batbar_sectionAppearanceHeader),
                rootView.findViewById(R.id.batbar_sectionAppearance));
        SectionHelper.setupCollapsible(rootView.findViewById(R.id.batbar_sectionTransitionHeader),
                rootView.findViewById(R.id.batbar_sectionTransition));
        SectionHelper.setupCollapsible(rootView.findViewById(R.id.batbar_sectionChargeHeader),
                rootView.findViewById(R.id.batbar_sectionCharge));
        SectionHelper.setupCollapsible(rootView.findViewById(R.id.batbar_sectionDischargeHeader),
                rootView.findViewById(R.id.batbar_sectionDischarge));
        SectionHelper.setupCollapsible(rootView.findViewById(R.id.batbar_sectionLowAnimHeader),
                rootView.findViewById(R.id.batbar_sectionLowAnim));
    }

    private void loadConfig() {
        barSwitch.setChecked(BatteryBarConfig.enabled);
        activity.applyCheckboxTint(barSwitch, BatteryBarConfig.enabled);
        updateModeGroup();
        barQuickSideValue.setText(sideLabel(BatteryBarConfig.quickSide));
        barOrientationH.setChecked(BatteryBarConfig.horizontal);
        barOrientationV.setChecked(!BatteryBarConfig.horizontal);
        barInvertCheck.setChecked(BatteryBarConfig.invert);
        activity.applyCheckboxTint(barInvertCheck, BatteryBarConfig.invert);
        barThicknessSeekBar.setProgress(BatteryBarConfig.thickness);
        barLengthSeekBar.setProgress((int) (BatteryBarConfig.length * 100));
        barColorPreview.setBackgroundColor(BatteryBarConfig.color);
        barSchemeSelector.setText(schemeLabel(BatteryBarConfig.colorScheme));
        barLowColorPreview.setBackgroundColor(BatteryBarConfig.lowColor);
        barLowThresholdSeekBar.setProgress(BatteryBarConfig.lowThreshold);
        barShowEmptyStripCheck.setChecked(BatteryBarConfig.showEmptyStrip);
        activity.applyCheckboxTint(barShowEmptyStripCheck, BatteryBarConfig.showEmptyStrip);
        barEmptyColorPreview.setBackgroundColor(BatteryBarConfig.emptyColor);
        barRadiusSeekBar.setProgress(BatteryBarConfig.radius);
        barFadeSpeedSeekBar.setProgress((BatteryBarConfig.fadeSpeed - 200) / 100);
        barFadeCheck.setChecked(BatteryBarConfig.fadeEnabled);
        activity.applyCheckboxTint(barFadeCheck, BatteryBarConfig.fadeEnabled);
        barShineCheck.setChecked(BatteryBarConfig.shineEnabled);
        activity.applyCheckboxTint(barShineCheck, BatteryBarConfig.shineEnabled);
        barShineSpeedSeekBar.setProgress((BatteryBarConfig.shineSpeed - 200) / 100);
        barShineWidthSeekBar.setProgress(BatteryBarConfig.shineWidth - 2);
        barWaveCheck.setChecked(BatteryBarConfig.waveEnabled);
        activity.applyCheckboxTint(barWaveCheck, BatteryBarConfig.waveEnabled);
        barWaveSpeedSeekBar.setProgress((BatteryBarConfig.waveSpeed - 200) / 100);
        barWaveAmplitudeSeekBar.setProgress(BatteryBarConfig.waveAmplitude - 10);
        barChargeWaveCheck.setChecked(BatteryBarConfig.chargeWaveEnabled);
        activity.applyCheckboxTint(barChargeWaveCheck, BatteryBarConfig.chargeWaveEnabled);
        barChargeWaveSpeedSeekBar.setProgress((BatteryBarConfig.chargeWaveSpeed - 200) / 100);
        barChargeWaveAmplitudeSeekBar.setProgress(BatteryBarConfig.chargeWaveAmplitude - 10);
        barEdgeGlowCheck.setChecked(BatteryBarConfig.edgeGlowEnabled);
        activity.applyCheckboxTint(barEdgeGlowCheck, BatteryBarConfig.edgeGlowEnabled);
        barEdgeGlowSpeedSeekBar.setProgress((BatteryBarConfig.edgeGlowSpeed - 200) / 100);
        barEdgeGlowIntensitySeekBar.setProgress(BatteryBarConfig.edgeGlowIntensity - 10);
        barStartupSweepCheck.setChecked(BatteryBarConfig.startupSweepEnabled);
        activity.applyCheckboxTint(barStartupSweepCheck, BatteryBarConfig.startupSweepEnabled);
        barStartupSweepSpeedSeekBar.setProgress((BatteryBarConfig.startupSweepSpeed - 200) / 100);
        barDrainTickCheck.setChecked(BatteryBarConfig.drainTickEnabled);
        activity.applyCheckboxTint(barDrainTickCheck, BatteryBarConfig.drainTickEnabled);
        barDischargeAlertCheck.setChecked(BatteryBarConfig.dischargeAlertEnabled);
        activity.applyCheckboxTint(barDischargeAlertCheck, BatteryBarConfig.dischargeAlertEnabled);
        barDischargeAlertModeEvery.setChecked(BatteryBarConfig.dischargeAlertMode == 0);
        barDischargeAlertModeManual.setChecked(BatteryBarConfig.dischargeAlertMode == 1);
        barDischargeAlertTargetsLabel.setText("Persen Manual: " + BatteryBarConfig.dischargeAlertTargets);
        barDischargePitchSeekBar.setProgress(clampInt(BatteryBarConfig.dischargeAlertPitch, 200, 6000) - 200);
        barDischargeDurationSeekBar.setProgress(clampInt(BatteryBarConfig.dischargeAlertDuration, 20, 500) - 20);
        barDischargeRepeatSeekBar.setProgress(clampInt(BatteryBarConfig.dischargeAlertRepeat, 1, 10) - 1);
        barDischargeVolumeSeekBar.setProgress(clampInt(BatteryBarConfig.dischargeAlertVolume, 0, 100));
        barDischargeVibrateCheck.setChecked(BatteryBarConfig.dischargeAlertVibrate);
        activity.applyCheckboxTint(barDischargeVibrateCheck, BatteryBarConfig.dischargeAlertVibrate);
        barDischargeVibrateDurationSeekBar.setProgress(clampInt(BatteryBarConfig.dischargeAlertVibrateDuration, 10, 500) - 10);
        barLockSwitch.setChecked(BatteryBarConfig.touchPassthrough);
        barLockSwitch.setEnabled(false);
        barLockSwitch.setAlpha(0.3f);
        barSafeAreaCheck.setChecked(BatteryBarConfig.safeArea);
        activity.applyCheckboxTint(barSafeAreaCheck, BatteryBarConfig.safeArea);

        barThicknessLabel.setText("Ketebalan: " + BatteryBarConfig.thickness + "px");
        barLengthLabel.setText("Panjang: " + Math.round(BatteryBarConfig.length * 100) + "%");
        barLowThresholdLabel.setText("Ambang Low: " + BatteryBarConfig.lowThreshold + "%");
        barRadiusLabel.setText("Radius Sudut: " + BatteryBarConfig.radius + "px");
        barFadeSpeedLabel.setText("Kecepatan Fade: " + formatSec(BatteryBarConfig.fadeSpeed));
        barShineSpeedLabel.setText("Kecepatan Shine: " + formatSec(BatteryBarConfig.shineSpeed));
        barShineWidthLabel.setText("Lebar Band: " + BatteryBarConfig.shineWidth + "%");
        barWaveSpeedLabel.setText("Kecepatan Wave: " + formatSec(BatteryBarConfig.waveSpeed));
        barWaveAmplitudeLabel.setText("Intensitas Wave: " + BatteryBarConfig.waveAmplitude + "%");
        barChargeWaveSpeedLabel.setText("Kecepatan Wave: " + formatSec(BatteryBarConfig.chargeWaveSpeed));
        barChargeWaveAmplitudeLabel.setText("Intensitas Wave: " + BatteryBarConfig.chargeWaveAmplitude + "%");
        barEdgeGlowSpeedLabel.setText("Kecepatan Glow: " + formatSec(BatteryBarConfig.edgeGlowSpeed));
        barEdgeGlowIntensityLabel.setText("Intensitas Glow: " + BatteryBarConfig.edgeGlowIntensity + "%");
        barStartupSweepSpeedLabel.setText("Kecepatan Sweep: " + formatSec(BatteryBarConfig.startupSweepSpeed));
        barDischargePitchLabel.setText("Pitch: " + BatteryBarConfig.dischargeAlertPitch + " Hz");
        barDischargeDurationLabel.setText("Durasi Bunyi: " + BatteryBarConfig.dischargeAlertDuration + " ms");
        barDischargeRepeatLabel.setText("Repeat: " + BatteryBarConfig.dischargeAlertRepeat + "x");
        barDischargeVolumeLabel.setText("Volume: " + BatteryBarConfig.dischargeAlertVolume + "%");
        barDischargeVibrateDurationLabel.setText("Durasi Getar: " + BatteryBarConfig.dischargeAlertVibrateDuration + " ms");
        updateDischargeAlertControls();
        updateLowColorEnabled();
    }

    private String formatSec(int ms) {
        return String.format(java.util.Locale.US, "%.1fs", ms / 1000.0);
    }

    private void updateLowColorEnabled() {
        boolean schemeActive = BatteryBarConfig.colorScheme != BatteryBarConfig.SCHEME_NONE;
        float alpha = schemeActive ? 0.3f : 1f;
        barLowColorPreview.setAlpha(alpha);
        barLowColorPreview.setEnabled(!schemeActive);
        barLowColorLabel.setAlpha(alpha);
        barLowColorLabel.setText(schemeActive ? "Warna Low (tidak aktif saat skema)" : "Warna Low");
    }

    private void setupListeners() {
        batbarSubTabDisplay.setOnClickListener(v -> selectBatteryStripTab(0));
        batbarSubTabAnimation.setOnClickListener(v -> selectBatteryStripTab(1));
        batbarSubTabSound.setOnClickListener(v -> selectBatteryStripTab(2));
        selectBatteryStripTab(0);

        barSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !PermissionHelper.hasOverlayPermission(activity)) {
                barSwitch.setChecked(false);
                activity.applyCheckboxTint(barSwitch, false);
                prefs().edit().putBoolean("batbar_enabled", false).apply();
                return;
            }
            BatteryBarConfig.enabled = isChecked;
            activity.applyCheckboxTint(barSwitch, isChecked);
            prefs().edit().putBoolean("batbar_enabled", isChecked).apply();
            if (isChecked) {
                if (FloatingService.instance != null) {
                    FloatingService.startModule(FloatingService.batteryBarModule());
                } else {
                    activity.startService(new Intent(activity, FloatingService.class));
                }
            } else {
                FloatingService.stopModule(FloatingService.batteryBarModule());
            }
        });

        barModeGroup.setOnCheckedChangeListener((group, checkedId) ->
                setQuickMode(checkedId == R.id.batbar_modeQuick));

        barQuickSideValue.setOnClickListener(v -> showSidePopup(v));

        barOrientationGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean horizontal = checkedId == R.id.batbar_orientationH;
            BatteryBarConfig.horizontal = horizontal;
            prefs().edit().putBoolean("batbar_horizontal", horizontal).apply();
            restart();
        });

        barInvertCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.invert = isChecked;
            activity.applyCheckboxTint(barInvertCheck, isChecked);
            prefs().edit().putBoolean("batbar_invert", isChecked).apply();
            restart();
        });

        barThicknessSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyThickness));

        barLengthSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyLength));

        barColorPreview.setOnClickListener(v -> {
            ColorPickerDialog.show(activity, "Pilih Warna Bar", BatteryBarConfig.color, color -> {
                BatteryBarConfig.color = color;
                barColorPreview.setBackgroundColor(color);
                prefs().edit().putInt("batbar_color", color).apply();
                restart();
            });
        });

        barSchemeSelector.setOnClickListener(v -> showSchemePopup(v));

        barLowColorPreview.setOnClickListener(v -> {
            ColorPickerDialog.show(activity, "Warna Saat Low", BatteryBarConfig.lowColor, color -> {
                BatteryBarConfig.lowColor = color;
                barLowColorPreview.setBackgroundColor(color);
                prefs().edit().putInt("batbar_low_color", color).apply();
                restart();
            });
        });

        barLowThresholdSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyLowThreshold));

        barShowEmptyStripCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.showEmptyStrip = isChecked;
            activity.applyCheckboxTint(barShowEmptyStripCheck, isChecked);
            prefs().edit().putBoolean("batbar_show_empty_strip", isChecked).apply();
            restart();
        });

        barEmptyColorPreview.setOnClickListener(v -> {
            ColorPickerDialog.show(activity, "Warna Strip Kosong", BatteryBarConfig.emptyColor, color -> {
                BatteryBarConfig.emptyColor = color;
                barEmptyColorPreview.setBackgroundColor(color);
                prefs().edit().putInt("batbar_empty_color", color).apply();
                restart();
            });
        });

        barRadiusSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyRadius));

        barFadeSpeedSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyFadeSpeed));

        barFadeCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.fadeEnabled = isChecked;
            activity.applyCheckboxTint(barFadeCheck, isChecked);
            prefs().edit().putBoolean("batbar_fade_enabled", isChecked).apply();
            restart();
        });

        barShineCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.shineEnabled = isChecked;
            activity.applyCheckboxTint(barShineCheck, isChecked);
            prefs().edit().putBoolean("batbar_shine_enabled", isChecked).apply();
            restart();
        });

        barShineSpeedSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyShineSpeed));

        barShineWidthSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyShineWidth));

        barWaveCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.waveEnabled = isChecked;
            activity.applyCheckboxTint(barWaveCheck, isChecked);
            prefs().edit().putBoolean("batbar_wave_enabled", isChecked).apply();
            restart();
        });

        barWaveSpeedSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyWaveSpeed));

        barWaveAmplitudeSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyWaveAmplitude));

        barChargeWaveCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.chargeWaveEnabled = isChecked;
            activity.applyCheckboxTint(barChargeWaveCheck, isChecked);
            prefs().edit().putBoolean("batbar_charge_wave_enabled", isChecked).apply();
            restart();
        });

        barChargeWaveSpeedSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyChargeWaveSpeed));

        barChargeWaveAmplitudeSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyChargeWaveAmplitude));

        barEdgeGlowCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.edgeGlowEnabled = isChecked;
            activity.applyCheckboxTint(barEdgeGlowCheck, isChecked);
            prefs().edit().putBoolean("batbar_edge_glow_enabled", isChecked).apply();
            restart();
        });

        barEdgeGlowSpeedSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyEdgeGlowSpeed));

        barEdgeGlowIntensitySeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyEdgeGlowIntensity));

        barStartupSweepCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.startupSweepEnabled = isChecked;
            activity.applyCheckboxTint(barStartupSweepCheck, isChecked);
            prefs().edit().putBoolean("batbar_startup_sweep_enabled", isChecked).apply();
            restart();
        });

        barStartupSweepSpeedSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyStartupSweepSpeed));

        barDrainTickCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.drainTickEnabled = isChecked;
            activity.applyCheckboxTint(barDrainTickCheck, isChecked);
            prefs().edit().putBoolean("batbar_drain_tick_enabled", isChecked).apply();
            restart();
        });

        barDischargeAlertCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.dischargeAlertEnabled = isChecked;
            activity.applyCheckboxTint(barDischargeAlertCheck, isChecked);
            prefs().edit().putBoolean("batbar_discharge_alert_enabled", isChecked).apply();
            updateDischargeAlertControls();
        });

        barDischargeAlertModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            BatteryBarConfig.dischargeAlertMode =
                    checkedId == R.id.batbarDischargeAlertModeManual ? 1 : 0;
            prefs().edit().putInt("batbar_discharge_alert_mode", BatteryBarConfig.dischargeAlertMode).apply();
            updateDischargeAlertControls();
        });

        barDischargeAlertTargetsLabel.setOnClickListener(v -> showDischargeTargetsDialog());

        barDischargePitchSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyDischargePitch));

        barDischargeDurationSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyDischargeDuration));

        barDischargeRepeatSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyDischargeRepeat));

        barDischargeVolumeSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyDischargeVolume));

        barDischargeVibrateCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.dischargeAlertVibrate = isChecked;
            activity.applyCheckboxTint(barDischargeVibrateCheck, isChecked);
            prefs().edit().putBoolean("batbar_discharge_alert_vibrate", isChecked).apply();
            updateDischargeAlertControls();
        });

        barDischargeVibrateDurationSeekBar.setOnSeekBarChangeListener(simpleSeekBar(this::applyDischargeVibrateDuration));

        barDischargeTestButton.setOnClickListener(v -> DischargeAlertPlayer.preview(activity));

        barSafeAreaCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            BatteryBarConfig.safeArea = isChecked;
            activity.applyCheckboxTint(barSafeAreaCheck, isChecked);
            prefs().edit().putBoolean("batbar_safe_area", isChecked).apply();
            restart();
        });

        barThicknessLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Ketebalan", barThicknessSeekBar, 0, 50, 0, barThicknessLabel, "Ketebalan: ", "px", this::applyThickness));
        barLengthLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Panjang", barLengthSeekBar, 0, 100, 0, barLengthLabel, "Panjang: ", "%", this::applyLength));
        barRadiusLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Radius Sudut", barRadiusSeekBar, 0, 50, 0, barRadiusLabel, "Radius Sudut: ", "px", this::applyRadius));
        barLowThresholdLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Ambang Low", barLowThresholdSeekBar, 0, 100, 0, barLowThresholdLabel, "Ambang Low: ", "%", this::applyLowThreshold));
        barShineWidthLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Lebar Band", barShineWidthSeekBar, 2, 98, 2, barShineWidthLabel, "Lebar Band: ", "%", this::applyShineWidth));
        barWaveAmplitudeLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Intensitas Wave", barWaveAmplitudeSeekBar, 10, 100, 10, barWaveAmplitudeLabel, "Intensitas Wave: ", "%", this::applyWaveAmplitude));
        barChargeWaveAmplitudeLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Intensitas Wave", barChargeWaveAmplitudeSeekBar, 10, 100, 10, barChargeWaveAmplitudeLabel, "Intensitas Wave: ", "%", this::applyChargeWaveAmplitude));
        barEdgeGlowIntensityLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Intensitas Glow", barEdgeGlowIntensitySeekBar, 10, 100, 10, barEdgeGlowIntensityLabel, "Intensitas Glow: ", "%", this::applyEdgeGlowIntensity));
        barShineSpeedLabel.setOnClickListener(v ->
                SliderLabelEditor.showSecEditor(activity, "Kecepatan Shine", barShineSpeedSeekBar, 0.2f, 5.0f, barShineSpeedLabel, "Kecepatan Shine: ", this::applyShineSpeed));
        barFadeSpeedLabel.setOnClickListener(v ->
                SliderLabelEditor.showSecEditor(activity, "Kecepatan Fade", barFadeSpeedSeekBar, 0.2f, 5.0f, barFadeSpeedLabel, "Kecepatan Fade: ", this::applyFadeSpeed));
        barWaveSpeedLabel.setOnClickListener(v ->
                SliderLabelEditor.showSecEditor(activity, "Kecepatan Wave", barWaveSpeedSeekBar, 0.2f, 5.0f, barWaveSpeedLabel, "Kecepatan Wave: ", this::applyWaveSpeed));
        barChargeWaveSpeedLabel.setOnClickListener(v ->
                SliderLabelEditor.showSecEditor(activity, "Kecepatan Wave", barChargeWaveSpeedSeekBar, 0.2f, 5.0f, barChargeWaveSpeedLabel, "Kecepatan Wave: ", this::applyChargeWaveSpeed));
        barEdgeGlowSpeedLabel.setOnClickListener(v ->
                SliderLabelEditor.showSecEditor(activity, "Kecepatan Glow", barEdgeGlowSpeedSeekBar, 0.2f, 5.0f, barEdgeGlowSpeedLabel, "Kecepatan Glow: ", this::applyEdgeGlowSpeed));
        barStartupSweepSpeedLabel.setOnClickListener(v ->
                SliderLabelEditor.showSecEditor(activity, "Kecepatan Sweep", barStartupSweepSpeedSeekBar, 0.2f, 5.0f, barStartupSweepSpeedLabel, "Kecepatan Sweep: ", this::applyStartupSweepSpeed));
        barDischargePitchLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Pitch", barDischargePitchSeekBar, 200, 6000, 200, barDischargePitchLabel, "Pitch: ", " Hz", this::applyDischargePitch));
        barDischargeDurationLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Durasi Bunyi", barDischargeDurationSeekBar, 20, 500, 20, barDischargeDurationLabel, "Durasi Bunyi: ", " ms", this::applyDischargeDuration));
        barDischargeRepeatLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Repeat", barDischargeRepeatSeekBar, 1, 10, 1, barDischargeRepeatLabel, "Repeat: ", "x", this::applyDischargeRepeat));
        barDischargeVolumeLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Volume", barDischargeVolumeSeekBar, 0, 100, 0, barDischargeVolumeLabel, "Volume: ", "%", this::applyDischargeVolume));
        barDischargeVibrateDurationLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Durasi Getar", barDischargeVibrateDurationSeekBar, 10, 500, 10, barDischargeVibrateDurationLabel, "Durasi Getar: ", " ms", this::applyDischargeVibrateDuration));
    }

    private void selectBatteryStripTab(int index) {
        batbarPageDisplay.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        batbarPageAnimation.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        batbarPageSound.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        applyBatteryStripTabState(batbarSubTabDisplay, index == 0);
        applyBatteryStripTabState(batbarSubTabAnimation, index == 1);
        applyBatteryStripTabState(batbarSubTabSound, index == 2);
    }

    private void applyBatteryStripTabState(TextView tab, boolean active) {
        tab.setTextColor(active ? activity.getColor(R.color.bat_monitor_header) : activity.getColor(R.color.bat_monitor_label));
        tab.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void setQuickMode(boolean quick) {
        BatteryBarConfig.quickMode = quick;
        prefs().edit().putBoolean("batbar_quick_mode", quick).apply();
        updateModeGroup();
        updateManualVisibility();
        restart();
    }

    private void updateModeGroup() {
        if (barModeQuick == null || barModeManual == null) return;
        boolean quick = BatteryBarConfig.quickMode;
        barModeQuick.setChecked(quick);
        barModeManual.setChecked(!quick);
    }

    private void showSidePopup(View anchor) {
        if (sidePopup != null && sidePopup.isShowing()) {
            sidePopup.dismiss();
            return;
        }
        String[] sides = {"top", "bottom", "left", "right"};
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFFFFFFF);
        for (String side : sides) {
            TextView item = new TextView(activity);
            item.setText(sideLabel(side));
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            item.setTextSize(14);
            item.setTextColor(0xFF222222);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (side.equals(BatteryBarConfig.quickSide)) {
                item.setBackgroundColor(0xFF4A90D9);
                item.setTextColor(0xFFFFFFFF);
            }
            item.setOnClickListener(v -> {
                BatteryBarConfig.quickSide = side;
                barQuickSideValue.setText(sideLabel(side));
                prefs().edit().putString("batbar_quick_side", side).apply();
                if (positionController != null) positionController.syncAll();
                restart();
                if (sidePopup != null) sidePopup.dismiss();
            });
            content.addView(item);
        }
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(content);
        sidePopup = new PopupWindow(scrollView,
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(160), true);
        sidePopup.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
        sidePopup.setOutsideTouchable(true);
        sidePopup.setElevation(dp(4));
        sidePopup.showAsDropDown(anchor, 0, dp(2));
    }

    private void showSchemePopup(View anchor) {
        if (sidePopup != null && sidePopup.isShowing()) {
            sidePopup.dismiss();
            return;
        }
        int[] schemes = {BatteryBarConfig.SCHEME_NONE, BatteryBarConfig.SCHEME_CLASSIC, BatteryBarConfig.SCHEME_HUE};
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFFFFFFF);
        for (int scheme : schemes) {
            TextView item = new TextView(activity);
            item.setText(schemeLabel(scheme));
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            item.setTextSize(14);
            item.setTextColor(0xFF222222);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (scheme == BatteryBarConfig.colorScheme) {
                item.setBackgroundColor(0xFF4A90D9);
                item.setTextColor(0xFFFFFFFF);
            }
            item.setOnClickListener(v -> {
                BatteryBarConfig.colorScheme = scheme;
                barSchemeSelector.setText(schemeLabel(scheme));
                prefs().edit().putInt("batbar_color_scheme", scheme).apply();
                updateLowColorEnabled();
                restart();
                if (sidePopup != null) sidePopup.dismiss();
            });
            content.addView(item);
        }
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(content);
        sidePopup = new PopupWindow(scrollView,
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(160), true);
        sidePopup.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
        sidePopup.setOutsideTouchable(true);
        sidePopup.setElevation(dp(4));
        sidePopup.showAsDropDown(anchor, 0, dp(2));
    }

    private void updateManualVisibility() {
        boolean manual = !BatteryBarConfig.quickMode;
        quickSideRow.setVisibility(manual ? View.GONE : View.VISIBLE);
        manualSectionHeader.setVisibility(manual ? View.VISIBLE : View.GONE);
        manualSection.setAlpha(manual ? 1f : 0.3f);
        setManualSectionExpanded(manual);
        if (positionController != null) {
            positionController.setPositionControlsEnabled(manual);
        }
        updateSafeAreaLock(manual);
    }

    private void updateSafeAreaLock(boolean manual) {
        BatteryBarConfig.safeArea = true;
        barSafeAreaCheck.setChecked(true);
        barSafeAreaCheck.setEnabled(false);
        barSafeAreaCheck.setAlpha(0.3f);
        prefs().edit().putBoolean("batbar_safe_area", true).apply();
    }

    private void setManualSectionExpanded(boolean expanded) {
        if (manualSectionHeader == null) return;
        String clean = manualSectionHeader.getText().toString().replaceAll("^[▸▾]\\s*", "");
        manualSectionHeader.setTag(expanded);
        manualSection.setVisibility(expanded ? View.VISIBLE : View.GONE);
        manualSectionHeader.setText((expanded ? "▾ " : "▸ ") + clean);
    }

    private SeekBar.OnSeekBarChangeListener simpleSeekBar(OnProgress handler) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) handler.run(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        };
    }

    private void restart() {
        FloatingService.restartModule(FloatingService.batteryBarModule());
    }

    private void applyThickness(int progress) {
        BatteryBarConfig.thickness = progress;
        barThicknessLabel.setText("Ketebalan: " + progress + "px");
        prefs().edit().putInt("batbar_thickness", progress).apply();
        restart();
    }

    private void applyLength(int progress) {
        BatteryBarConfig.length = progress / 100f;
        barLengthLabel.setText("Panjang: " + progress + "%");
        prefs().edit().putFloat("batbar_length", BatteryBarConfig.length).apply();
        restart();
    }

    private void applyRadius(int progress) {
        BatteryBarConfig.radius = progress;
        barRadiusLabel.setText("Radius Sudut: " + progress + "px");
        prefs().edit().putInt("batbar_radius", progress).apply();
        restart();
    }

    private void applyLowThreshold(int progress) {
        BatteryBarConfig.lowThreshold = progress;
        barLowThresholdLabel.setText("Ambang Low: " + progress + "%");
        prefs().edit().putInt("batbar_low_threshold", progress).apply();
        restart();
    }

    private void applyShineWidth(int progress) {
        BatteryBarConfig.shineWidth = 2 + progress;
        barShineWidthLabel.setText("Lebar Band: " + BatteryBarConfig.shineWidth + "%");
        prefs().edit().putInt("batbar_shine_width", BatteryBarConfig.shineWidth).apply();
        restart();
    }

    private void applyWaveAmplitude(int progress) {
        BatteryBarConfig.waveAmplitude = 10 + progress;
        barWaveAmplitudeLabel.setText("Intensitas Wave: " + BatteryBarConfig.waveAmplitude + "%");
        prefs().edit().putInt("batbar_wave_amplitude", BatteryBarConfig.waveAmplitude).apply();
        restart();
    }

    private void applyChargeWaveAmplitude(int progress) {
        BatteryBarConfig.chargeWaveAmplitude = 10 + progress;
        barChargeWaveAmplitudeLabel.setText("Intensitas Wave: " + BatteryBarConfig.chargeWaveAmplitude + "%");
        prefs().edit().putInt("batbar_charge_wave_amplitude", BatteryBarConfig.chargeWaveAmplitude).apply();
        restart();
    }

    private void applyShineSpeed(int progress) {
        BatteryBarConfig.shineSpeed = 200 + progress * 100;
        barShineSpeedLabel.setText("Kecepatan Shine: " + formatSec(BatteryBarConfig.shineSpeed));
        prefs().edit().putInt("batbar_shine_speed", BatteryBarConfig.shineSpeed).apply();
        restart();
    }

    private void applyFadeSpeed(int progress) {
        BatteryBarConfig.fadeSpeed = 200 + progress * 100;
        barFadeSpeedLabel.setText("Kecepatan Fade: " + formatSec(BatteryBarConfig.fadeSpeed));
        prefs().edit().putInt("batbar_fade_speed", BatteryBarConfig.fadeSpeed).apply();
        restart();
    }

    private void applyWaveSpeed(int progress) {
        BatteryBarConfig.waveSpeed = 200 + progress * 100;
        barWaveSpeedLabel.setText("Kecepatan Wave: " + formatSec(BatteryBarConfig.waveSpeed));
        prefs().edit().putInt("batbar_wave_speed", BatteryBarConfig.waveSpeed).apply();
        FloatingService.updateBatteryBarInPlace();
    }

    private void applyChargeWaveSpeed(int progress) {
        BatteryBarConfig.chargeWaveSpeed = 200 + progress * 100;
        barChargeWaveSpeedLabel.setText("Kecepatan Wave: " + formatSec(BatteryBarConfig.chargeWaveSpeed));
        prefs().edit().putInt("batbar_charge_wave_speed", BatteryBarConfig.chargeWaveSpeed).apply();
        FloatingService.updateBatteryBarInPlace();
    }

    private void applyEdgeGlowSpeed(int progress) {
        BatteryBarConfig.edgeGlowSpeed = 200 + progress * 100;
        barEdgeGlowSpeedLabel.setText("Kecepatan Glow: " + formatSec(BatteryBarConfig.edgeGlowSpeed));
        prefs().edit().putInt("batbar_edge_glow_speed", BatteryBarConfig.edgeGlowSpeed).apply();
        restart();
    }

    private void applyEdgeGlowIntensity(int progress) {
        BatteryBarConfig.edgeGlowIntensity = 10 + progress;
        barEdgeGlowIntensityLabel.setText("Intensitas Glow: " + BatteryBarConfig.edgeGlowIntensity + "%");
        prefs().edit().putInt("batbar_edge_glow_intensity", BatteryBarConfig.edgeGlowIntensity).apply();
        restart();
    }

    private void applyStartupSweepSpeed(int progress) {
        BatteryBarConfig.startupSweepSpeed = 200 + progress * 100;
        barStartupSweepSpeedLabel.setText("Kecepatan Sweep: " + formatSec(BatteryBarConfig.startupSweepSpeed));
        prefs().edit().putInt("batbar_startup_sweep_speed", BatteryBarConfig.startupSweepSpeed).apply();
        restart();
    }

    private void applyDischargePitch(int progress) {
        BatteryBarConfig.dischargeAlertPitch = 200 + progress;
        barDischargePitchLabel.setText("Pitch: " + BatteryBarConfig.dischargeAlertPitch + " Hz");
        prefs().edit().putInt("batbar_discharge_alert_pitch", BatteryBarConfig.dischargeAlertPitch).apply();
    }

    private void applyDischargeDuration(int progress) {
        BatteryBarConfig.dischargeAlertDuration = 20 + progress;
        barDischargeDurationLabel.setText("Durasi Bunyi: " + BatteryBarConfig.dischargeAlertDuration + " ms");
        prefs().edit().putInt("batbar_discharge_alert_duration", BatteryBarConfig.dischargeAlertDuration).apply();
    }

    private void applyDischargeRepeat(int progress) {
        BatteryBarConfig.dischargeAlertRepeat = 1 + progress;
        barDischargeRepeatLabel.setText("Repeat: " + BatteryBarConfig.dischargeAlertRepeat + "x");
        prefs().edit().putInt("batbar_discharge_alert_repeat", BatteryBarConfig.dischargeAlertRepeat).apply();
    }

    private void applyDischargeVolume(int progress) {
        BatteryBarConfig.dischargeAlertVolume = progress;
        barDischargeVolumeLabel.setText("Volume: " + BatteryBarConfig.dischargeAlertVolume + "%");
        prefs().edit().putInt("batbar_discharge_alert_volume", BatteryBarConfig.dischargeAlertVolume).apply();
    }

    private void applyDischargeVibrateDuration(int progress) {
        BatteryBarConfig.dischargeAlertVibrateDuration = 10 + progress;
        barDischargeVibrateDurationLabel.setText("Durasi Getar: " + BatteryBarConfig.dischargeAlertVibrateDuration + " ms");
        prefs().edit().putInt("batbar_discharge_alert_vibrate_duration", BatteryBarConfig.dischargeAlertVibrateDuration).apply();
    }

    private void updateDischargeAlertControls() {
        boolean enabled = BatteryBarConfig.dischargeAlertEnabled;
        boolean manual = BatteryBarConfig.dischargeAlertMode == 1;
        boolean vibrate = BatteryBarConfig.dischargeAlertVibrate;
        float on = 1f;
        float off = 0.3f;

        setControlEnabled(barDischargeAlertModeEvery, enabled, enabled ? on : off);
        setControlEnabled(barDischargeAlertModeManual, enabled, enabled ? on : off);
        setControlEnabled(barDischargeAlertTargetsLabel, enabled && manual, enabled && manual ? on : off);
        setControlEnabled(barDischargePitchLabel, enabled, enabled ? on : off);
        setControlEnabled(barDischargePitchSeekBar, enabled, enabled ? on : off);
        setControlEnabled(barDischargeDurationLabel, enabled, enabled ? on : off);
        setControlEnabled(barDischargeDurationSeekBar, enabled, enabled ? on : off);
        setControlEnabled(barDischargeRepeatLabel, enabled, enabled ? on : off);
        setControlEnabled(barDischargeRepeatSeekBar, enabled, enabled ? on : off);
        setControlEnabled(barDischargeVolumeLabel, enabled, enabled ? on : off);
        setControlEnabled(barDischargeVolumeSeekBar, enabled, enabled ? on : off);
        setControlEnabled(barDischargeVibrateCheck, enabled, enabled ? on : off);
        setControlEnabled(barDischargeVibrateDurationLabel, enabled && vibrate, enabled && vibrate ? on : off);
        setControlEnabled(barDischargeVibrateDurationSeekBar, enabled && vibrate, enabled && vibrate ? on : off);
        setControlEnabled(barDischargeTestButton, enabled, enabled ? on : off);
    }

    private void showDischargeTargetsDialog() {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(BatteryBarConfig.dischargeAlertTargets);
        input.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Persen Manual")
                .setMessage("Masukkan persentase dipisah koma, mis. 90,75,50,20,10,5")
                .setView(input)
                .setPositiveButton("Simpan", null)
                .setNegativeButton("Batal", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String normalized = normalizeTargets(input.getText().toString());
            if (normalized == null) {
                Toast.makeText(activity, "Isi angka 1–100 dipisah koma", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            BatteryBarConfig.dischargeAlertTargets = normalized;
            barDischargeAlertTargetsLabel.setText("Persen Manual: " + normalized);
            prefs().edit().putString("batbar_discharge_alert_targets", normalized).apply();
        });
    }

    private String normalizeTargets(String raw) {
        java.util.TreeSet<Integer> values = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        if (raw != null) {
            for (String part : raw.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    int value = Integer.parseInt(trimmed);
                    if (value < 1 || value > 100) return null;
                    values.add(value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        if (values.isEmpty()) return null;
        StringBuilder builder = new StringBuilder();
        for (int value : values) {
            if (builder.length() > 0) builder.append(",");
            builder.append(value);
        }
        return builder.toString();
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void setControlEnabled(View view, boolean enabled, float alpha) {
        view.setEnabled(enabled);
        view.setAlpha(alpha);
    }

    private String schemeLabel(int scheme) {
        switch (scheme) {
            case BatteryBarConfig.SCHEME_CLASSIC: return "Klasik 3-warna";
            case BatteryBarConfig.SCHEME_HUE: return "Hue Gradien";
            default: return "Tanpa Skema";
        }
    }

    private String sideLabel(String side) {
        switch (side) {
            case "top": return "Atas (Top)";
            case "bottom": return "Bawah (Bottom)";
            case "left": return "Kiri (Left)";
            default: return "Kanan (Right)";
        }
    }

    private android.content.SharedPreferences prefs() {
        return activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE);
    }

    private int dp(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density);
    }

    private interface OnProgress {
        void run(int progress);
    }
}
