package exp.ftxt.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.core.FloatingService;
import exp.ftxt.features.fps_display.FpsConfig;
import exp.ftxt.features.gamefps.poc.GameFpsLog;
import exp.ftxt.features.gamefps.poc.GameFpsPoC;
import exp.ftxt.features.gamefps.poc.ShizukuHelper;
import exp.ftxt.shared.color.ColorPickerDialog;
import exp.ftxt.shared.ui.SliderLabelEditor;
import exp.ftxt.shared.ui.SectionHelper;
import exp.ftxt.utils.PermissionHelper;

public class FpsPanelController {

    private final MainActivity activity;

    private CheckBox fpsSwitch;
    private SeekBar fpsSizeSeekBar;
    private View fpsColorPreview;
    private View fpsLabelColorPreview;
    private CheckBox fpsShadowSwitch;
    private LinearLayout fpsShadowConfigContainer;
    private View fpsShadowColorPreview;
    private SeekBar fpsShadowBlurSeekBar;
    private SeekBar fpsShadowOffsetXSeekBar;
    private SeekBar fpsShadowOffsetYSeekBar;
    private CheckBox fpsLockSwitch;
    private CheckBox fpsValueOnlyCheck;
    private CheckBox fpsSafeArea;
    private CheckBox fpsBgSwitch;
    private LinearLayout fpsBgConfigContainer;
    private View fpsBgColorPreview;
    private SeekBar fpsBgPaddingSeekBar;
    private SeekBar fpsBgOffsetXSeekBar;
    private SeekBar fpsBgOffsetYSeekBar;
    private SeekBar fpsBgMarginSeekBar;
    private SeekBar fpsBgRadiusSeekBar;
    private TextView fpsSizeLabel, fpsBgPaddingLabel, fpsBgOffsetXLabel, fpsBgOffsetYLabel;
    private TextView fpsBgMarginLabel, fpsBgRadiusLabel;
    private TextView fpsShadowBlurLabel, fpsShadowOffsetXLabel, fpsShadowOffsetYLabel;
    private FpsPositionController fpsPositionController;
    private TextView fpsIntervalValue;
    private PopupWindow intervalPopup;

    // Game FPS subtab
    private BottomNavigationView fpsBottomNav;
    private View fpsSubDisplay, fpsSubGame;
    private Button btnFpsGameRefreshApps, btnFpsGameApplyManual;
    private Button btnFpsGameViewLog, btnFpsGameSaveLog;
    private EditText edtFpsGamePkg;
    private TextView txtFpsGameShizuku, txtFpsGameTarget, txtFpsGameValue, txtFpsGameStatus;
    private RecyclerView rvFpsGameApps;
    private GameAppListAdapter gameAppAdapter;
    private ExecutorService gameExec = Executors.newSingleThreadExecutor();
    private Handler gameHandler = new Handler(Looper.getMainLooper());
    private GameFpsPoC gameFpsPoc;
    private static final int GAME_FPS_SHIZUKU_REQ = 10001;
    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener gameFpsPermListener =
            (requestCode, grantResult) -> {
                if (requestCode == GAME_FPS_SHIZUKU_REQ) {
                    boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                    GameFpsLog.add("IZIN Shizuku " + (granted ? "GRANTED" : "DITOLAK"));
                    if (granted) ensureGameFpsPoc();
                }
            };

    public FpsPanelController(MainActivity activity, View rootView) {
        this.activity = activity;
        bindViews(rootView);
        loadConfig();
        setupListeners();
        fpsPositionController = new FpsPositionController(activity, rootView);
        setupGameFpsSubtab(rootView);
    }

    public void onPanelShown() {
        if (fpsPositionController != null) {
            fpsPositionController.refresh();
        }
        refreshGameFpsStatus();
        ensureGameFpsPoc();
    }

    public void showLoadPresetDialog() {
        if (fpsPositionController != null) {
            fpsPositionController.showLoadPresetDialog();
        }
    }

    public void cleanup() {
        if (fpsPositionController != null) {
            fpsPositionController.cleanup();
            fpsPositionController = null;
        }
        try {
            if (gameFpsPoc != null) {
                gameFpsPoc.setListener(null);
                gameFpsPoc.stop();
            }
        } catch (Throwable ignored) {}
        try {
            if (gameExec != null && !gameExec.isShutdown()) {
                gameExec.shutdownNow();
            }
        } catch (Throwable ignored) {}
        try {
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(gameFpsPermListener);
        } catch (Throwable ignored) {}
    }

    private void bindViews(View rootView) {
        fpsSwitch = rootView.findViewById(R.id.fpsSwitch);
        fpsSizeSeekBar = rootView.findViewById(R.id.fpsSizeSeekBar);
        fpsColorPreview = rootView.findViewById(R.id.fpsColorPreview);
        fpsLabelColorPreview = rootView.findViewById(R.id.fpsLabelColorPreview);
        fpsShadowSwitch = rootView.findViewById(R.id.fpsShadowSwitch);
        fpsShadowConfigContainer = rootView.findViewById(R.id.shadowConfigFps);
        fpsShadowColorPreview = rootView.findViewById(R.id.fpsShadowColorPreview);
        fpsShadowBlurSeekBar = rootView.findViewById(R.id.fpsShadowBlurSeekBar);
        fpsShadowOffsetXSeekBar = rootView.findViewById(R.id.fpsShadowOffsetXSeekBar);
        fpsShadowOffsetYSeekBar = rootView.findViewById(R.id.fpsShadowOffsetYSeekBar);
        fpsLockSwitch = rootView.findViewById(R.id.fpsLockSwitch);
        fpsValueOnlyCheck = rootView.findViewById(R.id.fpsValueOnlyCheck);
        fpsSafeArea = rootView.findViewById(R.id.fpsSafeArea);
        fpsBgSwitch = rootView.findViewById(R.id.fpsBgSwitch);
        fpsBgConfigContainer = rootView.findViewById(R.id.bgConfigFps);
        fpsBgColorPreview = rootView.findViewById(R.id.fpsBgColorPreview);
        fpsBgPaddingSeekBar = rootView.findViewById(R.id.fpsBgPaddingSeekBar);
        fpsBgOffsetXSeekBar = rootView.findViewById(R.id.fpsBgOffsetXSeekBar);
        fpsBgOffsetYSeekBar = rootView.findViewById(R.id.fpsBgOffsetYSeekBar);
        fpsBgMarginSeekBar = rootView.findViewById(R.id.fpsBgMarginSeekBar);
        fpsBgRadiusSeekBar = rootView.findViewById(R.id.fpsBgRadiusSeekBar);
        fpsSizeLabel = rootView.findViewById(R.id.fpsSizeLabel);
        fpsBgPaddingLabel = rootView.findViewById(R.id.fpsBgPaddingLabel);
        fpsBgOffsetXLabel = rootView.findViewById(R.id.fpsBgOffsetXLabel);
        fpsBgOffsetYLabel = rootView.findViewById(R.id.fpsBgOffsetYLabel);
        fpsBgMarginLabel = rootView.findViewById(R.id.fpsBgMarginLabel);
        fpsBgRadiusLabel = rootView.findViewById(R.id.fpsBgRadiusLabel);
        fpsShadowBlurLabel = rootView.findViewById(R.id.fpsShadowBlurLabel);
        fpsShadowOffsetXLabel = rootView.findViewById(R.id.fpsShadowOffsetXLabel);
        fpsShadowOffsetYLabel = rootView.findViewById(R.id.fpsShadowOffsetYLabel);
        fpsIntervalValue = rootView.findViewById(R.id.fpsIntervalValue);

        View sectionDisplay = rootView.findViewById(R.id.fps_sectionDisplay);
        TextView sectionDisplayHeader = rootView.findViewById(R.id.fps_sectionDisplayHeader);
        SectionHelper.setupCollapsible(sectionDisplayHeader, sectionDisplay);

        View sectionPosition = rootView.findViewById(R.id.fps_sectionPosition);
        TextView sectionPositionHeader = rootView.findViewById(R.id.fps_sectionPositionHeader);
        SectionHelper.setupCollapsible(sectionPositionHeader, sectionPosition);

        View sectionShadow = rootView.findViewById(R.id.fps_sectionShadow);
        TextView sectionShadowHeader = rootView.findViewById(R.id.fps_sectionShadowHeader);
        SectionHelper.setupCollapsible(sectionShadowHeader, sectionShadow);

        View sectionBackground = rootView.findViewById(R.id.fps_sectionBackground);
        TextView sectionBackgroundHeader = rootView.findViewById(R.id.fps_sectionBackgroundHeader);
        SectionHelper.setupCollapsible(sectionBackgroundHeader, sectionBackground);
    }

    private void setupGameFpsSubtab(View rootView) {
        fpsBottomNav = rootView.findViewById(R.id.fpsBottomNav);
        fpsSubDisplay = rootView.findViewById(R.id.fpsSubDisplay);
        fpsSubGame = rootView.findViewById(R.id.fpsSubGame);
        btnFpsGameRefreshApps = rootView.findViewById(R.id.btnFpsGameRefreshApps);
        btnFpsGameApplyManual = rootView.findViewById(R.id.btnFpsGameApplyManual);
        btnFpsGameViewLog = rootView.findViewById(R.id.btnFpsGameViewLog);
        btnFpsGameSaveLog = rootView.findViewById(R.id.btnFpsGameSaveLog);
        edtFpsGamePkg = rootView.findViewById(R.id.edtFpsGamePkg);
        txtFpsGameShizuku = rootView.findViewById(R.id.txtFpsGameShizuku);
        txtFpsGameTarget = rootView.findViewById(R.id.txtFpsGameTarget);
        txtFpsGameValue = rootView.findViewById(R.id.txtFpsGameValue);
        txtFpsGameStatus = rootView.findViewById(R.id.txtFpsGameStatus);
        rvFpsGameApps = rootView.findViewById(R.id.rvFpsGameApps);

        try {
            gameFpsPoc = new GameFpsPoC(activity);
            gameFpsPoc.setListener(new GameFpsPoC.Listener() {
                @Override
                public void onFps(float fps, String source) {
                    if (txtFpsGameValue != null) {
                        txtFpsGameValue.setText(String.format(java.util.Locale.US, "FPS: %.1f", fps));
                    }
                    if (txtFpsGameStatus != null) {
                        txtFpsGameStatus.setText("source: " + source);
                    }
                }

                @Override
                public void onStatus(String message) {
                    if (txtFpsGameStatus != null) {
                        txtFpsGameStatus.setText(message);
                    }
                }
            });
        } catch (Throwable ignored) {}
        try {
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(gameFpsPermListener);
        } catch (Throwable ignored) {}

        if (fpsBottomNav != null) {
            fpsBottomNav.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.fpsTabGame) {
                    selectFpsTab(1);
                    refreshGameFpsStatus();
                    refreshGameFpsApps();
                    ensureGameFpsPoc();
                } else {
                    selectFpsTab(0);
                }
                return true;
            });
            fpsBottomNav.setSelectedItemId(R.id.fpsTabDisplay);
        }
        selectFpsTab(0);

        if (rvFpsGameApps != null) {
            rvFpsGameApps.setLayoutManager(new LinearLayoutManager(activity));
            gameAppAdapter = new GameAppListAdapter(activity.getPackageManager(), appInfo -> {
                String pkg = appInfo.packageName;
                String name = String.valueOf(activity.getPackageManager().getApplicationLabel(appInfo));
                saveGameFpsTarget(pkg);
                GameFpsLog.add("PILIH " + name + " (" + pkg + ")");
                if (gameFpsPoc != null) {
                    gameFpsPoc.setTargetPackage(pkg);
                    gameFpsPoc.start();
                }
                if (txtFpsGameTarget != null) {
                    txtFpsGameTarget.setText("Target: " + name + " (" + pkg + ")");
                }
            });
            rvFpsGameApps.setAdapter(gameAppAdapter);
        }

        if (btnFpsGameRefreshApps != null) btnFpsGameRefreshApps.setOnClickListener(v -> refreshGameFpsApps());
        if (btnFpsGameApplyManual != null) btnFpsGameApplyManual.setOnClickListener(v -> {
            String pkg = edtFpsGamePkg != null ? edtFpsGamePkg.getText().toString().trim() : "";
            if (pkg.isEmpty()) return;
            saveGameFpsTarget(pkg);
            GameFpsLog.add("PILIH manual " + pkg);
            if (gameFpsPoc != null) {
                gameFpsPoc.setTargetPackage(pkg);
                gameFpsPoc.start();
            }
            if (txtFpsGameTarget != null) txtFpsGameTarget.setText("Target: " + pkg);
        });

        if (btnFpsGameViewLog != null) btnFpsGameViewLog.setOnClickListener(v -> showGameFpsLogDialog());
        if (btnFpsGameSaveLog != null) btnFpsGameSaveLog.setOnClickListener(v -> {
            String path = GameFpsLog.save(activity);
            if (txtFpsGameStatus != null) {
                txtFpsGameStatus.setText("Log disimpan: " + path);
            }
        });

        refreshGameFpsStatus();
    }

    private void showGameFpsLogDialog() {
        try {
            String content = GameFpsLog.snapshot();
            TextView tv = new TextView(activity);
            tv.setText(content);
            tv.setTextSize(11f);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setTextIsSelectable(true);
            int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            ScrollView sv = new ScrollView(activity);
            sv.addView(tv);
            new AlertDialog.Builder(activity)
                    .setTitle("Log Game FPS")
                    .setView(sv)
                    .setPositiveButton("Tutup", null)
                    .show();
        } catch (Throwable ignored) {}
    }

    private void selectFpsTab(int idx) {
        if (fpsSubDisplay != null) fpsSubDisplay.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        if (fpsSubGame != null) fpsSubGame.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
    }

    private void refreshGameFpsStatus() {
        if (txtFpsGameShizuku == null) return;
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("Shizuku binder: ").append(ShizukuHelper.isBinderAlive() ? "OK" : "TIDAK AKTIF").append('\n');
            sb.append("Izin Shizuku: ").append(ShizukuHelper.hasPermission() ? "GRANTED" : "BELUM/DITOLAK").append('\n');
            if (ShizukuHelper.isBinderAlive() && !ShizukuHelper.hasPermission()) {
                GameFpsLog.add("MINTA izin Shizuku");
                try { ShizukuHelper.requestPermission(GAME_FPS_SHIZUKU_REQ); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            sb.append("Error: ").append(t.getMessage());
        }
        txtFpsGameShizuku.setText(sb.toString());
        try {
            android.content.SharedPreferences sp = activity.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE);
            String saved = sp.getString("game_fps_poc_target", null);
            if (saved != null && !saved.isEmpty() && txtFpsGameTarget != null) {
                txtFpsGameTarget.setText("Target: " + saved);
                if (gameFpsPoc != null) gameFpsPoc.setTargetPackage(saved);
            }
        } catch (Throwable ignored) {}
    }

    private void ensureGameFpsPoc() {
        try {
            if (gameFpsPoc != null && ShizukuHelper.isBinderAlive() && ShizukuHelper.hasPermission()) {
                gameFpsPoc.start();
            }
        } catch (Throwable ignored) {}
    }

    private void saveGameFpsTarget(String pkg) {
        try {
            android.content.SharedPreferences sp = activity.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE);
            sp.edit().putString("game_fps_poc_target", pkg).apply();
        } catch (Throwable ignored) {}
    }

    private void refreshGameFpsApps() {
        if (gameExec == null || gameExec.isShutdown()) return;
        gameExec.execute(() -> {
            PackageManager pm = activity.getPackageManager();
            List<ApplicationInfo> list = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<ApplicationInfo> res = new ArrayList<>();
            for (ApplicationInfo ai : list) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    res.add(ai);
                }
            }
            if (res.isEmpty()) res.addAll(list);
            gameHandler.post(() -> {
                if (gameAppAdapter != null) gameAppAdapter.setApps(res);
            });
        });
    }

    private void loadConfig() {
        fpsSwitch.setChecked(FpsConfig.enabled);
        activity.applyCheckboxTint(fpsSwitch, FpsConfig.enabled);
        fpsSizeSeekBar.setProgress((int) FpsConfig.size);
        fpsColorPreview.setBackgroundColor(FpsConfig.color);
        fpsLabelColorPreview.setBackgroundColor(FpsConfig.labelColor);
        fpsBgSwitch.setChecked(FpsConfig.bg.enabled);
        activity.applyCheckboxTint(fpsBgSwitch, FpsConfig.bg.enabled);
        fpsBgConfigContainer.setVisibility(FpsConfig.bg.enabled ? View.VISIBLE : View.GONE);
        fpsBgPaddingSeekBar.setProgress(FpsConfig.bg.padding);
        fpsBgOffsetXSeekBar.setProgress(FpsConfig.bg.offsetX + 60);
        fpsBgOffsetYSeekBar.setProgress(FpsConfig.bg.offsetY + 60);
        fpsBgMarginSeekBar.setProgress(FpsConfig.bg.margin);
        fpsBgRadiusSeekBar.setProgress(FpsConfig.bg.radius);
        fpsShadowSwitch.setChecked(FpsConfig.shadow.enabled);
        activity.applyCheckboxTint(fpsShadowSwitch, FpsConfig.shadow.enabled);
        fpsShadowConfigContainer.setVisibility(FpsConfig.shadow.enabled ? View.VISIBLE : View.GONE);
        fpsShadowBlurSeekBar.setProgress((int) FpsConfig.shadow.blur);
        fpsShadowOffsetXSeekBar.setProgress((int) FpsConfig.shadow.offsetX + 60);
        fpsShadowOffsetYSeekBar.setProgress((int) FpsConfig.shadow.offsetY + 60);
        fpsLockSwitch.setChecked(FpsConfig.touchPassthrough);
        activity.applyCheckboxTint(fpsLockSwitch, FpsConfig.touchPassthrough);
        fpsValueOnlyCheck.setChecked(FpsConfig.showOnlyValue);
        fpsSafeArea.setChecked(FpsConfig.safeArea);
        fpsSizeLabel.setText("Ukuran Teks: " + (int) FpsConfig.size);
        fpsBgPaddingLabel.setText("Ukuran Background: " + FpsConfig.bg.padding);
        fpsBgOffsetXLabel.setText("Offset X: " + FpsConfig.bg.offsetX);
        fpsBgOffsetYLabel.setText("Offset Y: " + FpsConfig.bg.offsetY);
        fpsBgColorPreview.setBackgroundColor(FpsConfig.bg.color);
        fpsBgMarginLabel.setText("Margin: " + FpsConfig.bg.margin);
        fpsBgRadiusLabel.setText("Radius: " + FpsConfig.bg.radius);
        fpsShadowBlurLabel.setText("Blur Shadow: " + (int) FpsConfig.shadow.blur);
        fpsShadowOffsetXLabel.setText("Shadow X: " + (int) FpsConfig.shadow.offsetX);
        fpsShadowOffsetYLabel.setText("Shadow Y: " + (int) FpsConfig.shadow.offsetY);
        fpsShadowColorPreview.setBackgroundColor(FpsConfig.shadow.color);
        fpsIntervalValue.setText(formatIntervalValue(FpsConfig.updateInterval));
    }

    private void setupListeners() {
        fpsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !PermissionHelper.hasOverlayPermission(activity)) {
                fpsSwitch.setChecked(false);
                activity.applyCheckboxTint(fpsSwitch, false);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putBoolean("fps_enabled", false).apply();
                return;
            }

            FpsConfig.enabled = isChecked;
            activity.applyCheckboxTint(fpsSwitch, isChecked);
            activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                    .edit().putBoolean("fps_enabled", isChecked).apply();

            if (isChecked) {
                if (FloatingService.instance != null) {
                    FloatingService.startModule(FloatingService.fpsModule());
                } else {
                    activity.startService(new Intent(activity, FloatingService.class));
                }
            } else {
                FloatingService.stopModule(FloatingService.fpsModule());
            }
        });

        fpsSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (progress < 5) { progress = 5; sb.setProgress(progress); }
                if (progress > 140) { progress = 140; sb.setProgress(progress); }
                FpsConfig.size = progress;
                fpsSizeLabel.setText("Ukuran Teks: " + progress);
                FloatingService.updateSizeForModule(FloatingService.fpsModule(), FpsConfig.size);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsColorPreview.setOnClickListener(v -> {
            ColorPickerDialog.show(activity, "Pilih Warna FPS", FpsConfig.color, color -> {
                FpsConfig.color = color;
                fpsColorPreview.setBackgroundColor(color);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_color", color).apply();
                FloatingService.updateColorForModule(FloatingService.fpsModule(), FpsConfig.color);
            });
        });

        fpsLabelColorPreview.setOnClickListener(v -> {
            ColorPickerDialog.show(activity, "Pilih Warna Label", FpsConfig.labelColor, color -> {
                FpsConfig.labelColor = color;
                fpsLabelColorPreview.setBackgroundColor(color);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_label_color", color).apply();
                FloatingService.updateLabelColorForModule(FloatingService.fpsModule(), FpsConfig.labelColor);
            });
        });

        fpsBgSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FpsConfig.bg.enabled = isChecked;
            activity.applyCheckboxTint(fpsBgSwitch, isChecked);
            fpsBgConfigContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                    .edit().putBoolean("fps_bg_enabled", isChecked).apply();
            FloatingService.updateBackgroundForModule(FloatingService.fpsModule());
        });

        fpsBgColorPreview.setOnClickListener(v -> {
            ColorPickerDialog.show(activity, "Warna Background FPS", FpsConfig.bg.color, color -> {
                FpsConfig.bg.color = color;
                fpsBgColorPreview.setBackgroundColor(color);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_bg_color", color).apply();
                FloatingService.updateBackgroundForModule(FloatingService.fpsModule());
            });
        });

        fpsBgPaddingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (progress < 0) progress = 0;
                FpsConfig.bg.padding = progress;
                fpsBgPaddingLabel.setText("Ukuran Background: " + progress);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_bg_padding", progress).apply();
                FloatingService.updateBackgroundForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsBgOffsetXSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int offset = progress - 60;
                FpsConfig.bg.offsetX = offset;
                fpsBgOffsetXLabel.setText("Offset X: " + offset);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_bg_offset_x", offset).apply();
                FloatingService.updateBackgroundForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsBgOffsetYSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int offset = progress - 60;
                FpsConfig.bg.offsetY = offset;
                fpsBgOffsetYLabel.setText("Offset Y: " + offset);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_bg_offset_y", offset).apply();
                FloatingService.updateBackgroundForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsBgMarginSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                FpsConfig.bg.margin = progress;
                fpsBgMarginLabel.setText("Margin: " + progress);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_bg_margin", progress).apply();
                FloatingService.updateBackgroundForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsBgRadiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                FpsConfig.bg.radius = progress;
                fpsBgRadiusLabel.setText("Radius: " + progress);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_bg_radius", progress).apply();
                FloatingService.updateBackgroundForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsShadowSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FpsConfig.shadow.enabled = isChecked;
            activity.applyCheckboxTint(fpsShadowSwitch, isChecked);
            fpsShadowConfigContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                    .edit().putBoolean("fps_shadow_enabled", isChecked).apply();
            saveFpsShadowPrefs();
            FloatingService.updateShadowForModule(FloatingService.fpsModule());
        });

        fpsShadowColorPreview.setOnClickListener(v -> {
            ColorPickerDialog.show(activity, "Warna Shadow FPS", FpsConfig.shadow.color, color -> {
                FpsConfig.shadow.color = color;
                fpsShadowColorPreview.setBackgroundColor(color);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putInt("fps_shadow_color", color).apply();
                FloatingService.updateShadowForModule(FloatingService.fpsModule());
            });
        });

        fpsShadowBlurSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                FpsConfig.shadow.blur = progress;
                fpsShadowBlurLabel.setText("Blur Shadow: " + progress);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putFloat("fps_shadow_blur", (float) progress).apply();
                FloatingService.updateShadowForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsShadowOffsetXSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int offset = progress - 60;
                FpsConfig.shadow.offsetX = offset;
                fpsShadowOffsetXLabel.setText("Shadow X: " + offset);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putFloat("fps_shadow_offset_x", (float) offset).apply();
                FloatingService.updateShadowForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsShadowOffsetYSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int offset = progress - 60;
                FpsConfig.shadow.offsetY = offset;
                fpsShadowOffsetYLabel.setText("Shadow Y: " + offset);
                activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                        .edit().putFloat("fps_shadow_offset_y", (float) offset).apply();
                FloatingService.updateShadowForModule(FloatingService.fpsModule());
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        fpsLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FpsConfig.touchPassthrough = isChecked;
            activity.applyCheckboxTint(fpsLockSwitch, isChecked);
            activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                    .edit().putBoolean("fps_lock", isChecked).apply();
            FloatingService.updateTouchFlagsForModule(FloatingService.fpsModule());
        });

        fpsValueOnlyCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FpsConfig.showOnlyValue = isChecked;
            activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                    .edit().putBoolean("fps_show_only_value", isChecked).apply();
            FloatingService.fpsModule().updateDisplay();
        });

        fpsSafeArea.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FpsConfig.safeArea = isChecked;
            activity.applyCheckboxTint(fpsSafeArea, isChecked);
            activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                    .edit().putBoolean("fps_safe_area", isChecked).apply();
        });

        fpsSizeLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Ukuran Teks", fpsSizeSeekBar, 140, fpsSizeLabel, "Ukuran Teks: "));
        fpsBgPaddingLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Ukuran Background", fpsBgPaddingSeekBar, 80, fpsBgPaddingLabel, "Ukuran Background: "));
        fpsBgOffsetXLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Offset X", fpsBgOffsetXSeekBar, fpsBgOffsetXLabel, "Offset X: "));
        fpsBgOffsetYLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Offset Y", fpsBgOffsetYSeekBar, fpsBgOffsetYLabel, "Offset Y: "));
        fpsBgMarginLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Margin Background", fpsBgMarginSeekBar, 30, fpsBgMarginLabel, "Margin: "));
        fpsBgRadiusLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Radius Background", fpsBgRadiusSeekBar, 50, fpsBgRadiusLabel, "Radius: "));
        fpsShadowBlurLabel.setOnClickListener(v ->
                SliderLabelEditor.showSliderEditor(activity, "Blur Shadow", fpsShadowBlurSeekBar, 50, fpsShadowBlurLabel, "Blur Shadow: "));
        fpsShadowOffsetXLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Shadow X", fpsShadowOffsetXSeekBar, fpsShadowOffsetXLabel, "Shadow X: "));
        fpsShadowOffsetYLabel.setOnClickListener(v ->
                SliderLabelEditor.showOffsetEditor(activity, "Shadow Y", fpsShadowOffsetYSeekBar, fpsShadowOffsetYLabel, "Shadow Y: "));

        setupIntervalListeners();
    }

    private void saveFpsShadowPrefs() {
        activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE).edit()
                .putInt("fps_shadow_color", FpsConfig.shadow.color)
                .putFloat("fps_shadow_blur", FpsConfig.shadow.blur)
                .putFloat("fps_shadow_offset_x", FpsConfig.shadow.offsetX)
                .putFloat("fps_shadow_offset_y", FpsConfig.shadow.offsetY)
                .apply();
    }

    private static final float[] INTERVAL_STEPS = {0.2f, 0.5f, 0.75f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f};

    private void setupIntervalListeners() {
        fpsIntervalValue.setOnClickListener(v -> showIntervalPopup(v));
    }

    private void showIntervalPopup(View anchor) {
        if (intervalPopup != null && intervalPopup.isShowing()) {
            intervalPopup.dismiss();
            return;
        }

        int currentIdx = -1;
        for (int i = 0; i < INTERVAL_STEPS.length; i++) {
            if (INTERVAL_STEPS[i] == FpsConfig.updateInterval) {
                currentIdx = i;
                break;
            }
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFFFFFFF);

        for (int i = 0; i < INTERVAL_STEPS.length; i++) {
            TextView item = new TextView(activity);
            item.setText(formatIntervalValue(INTERVAL_STEPS[i]) + "s");
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            item.setTextSize(14);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setTextColor(0xFF222222);
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (i == currentIdx) {
                item.setBackgroundColor(0xFF4A90D9);
                item.setTextColor(0xFFFFFFFF);
            }

            final int idx = i;
            item.setOnClickListener(v -> {
                FpsConfig.updateInterval = INTERVAL_STEPS[idx];
                updateIntervalDisplay();
                FloatingService.restartModule(FloatingService.fpsModule());
                if (intervalPopup != null) intervalPopup.dismiss();
            });
            content.addView(item);
        }

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(content);

        intervalPopup = new PopupWindow(scrollView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(200), true);
        intervalPopup.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
        intervalPopup.setOutsideTouchable(true);
        intervalPopup.setElevation(dp(4));
        intervalPopup.showAsDropDown(anchor, 0, dp(2));
    }

    private void updateIntervalDisplay() {
        fpsIntervalValue.setText(formatIntervalValue(FpsConfig.updateInterval));
        activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                .edit().putFloat("fps_update_interval", FpsConfig.updateInterval).apply();
    }

    private String formatIntervalValue(float v) {
        if (v == (long) v) return String.valueOf((long) v);
        String s = String.format("%.2f", v).replaceAll("0$", "").replaceAll("\\.$", "");
        return s;
    }

    private int dp(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density);
    }
}
