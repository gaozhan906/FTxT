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
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.core.FloatingService;
import exp.ftxt.features.fps_display.FpsConfig;
import exp.ftxt.features.gamefps.GameFpsService;
import exp.ftxt.features.gamefps.reader.GameFpsLog;
import exp.ftxt.features.gamefps.reader.GameFpsReader;
import exp.ftxt.features.gamefps.reader.ShizukuHelper;
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
    private Button btnFpsGameRefreshApps, btnFpsGameApplyManual, btnFpsGameLog;
    private AutoCompleteTextView edtFpsGamePkg;
    private TextView txtFpsGameShizuku, txtFpsGameTarget, txtFpsGameModeState;
    private RecyclerView rvFpsGameApps;
    private CheckBox chkFpsGameEnable;
    private RadioGroup rgFpsGameMode;
    private RadioButton rbFpsGameModeAuto, rbFpsGameModeManual;
    private LinearLayout grpFpsGameManual;
    private View fpsGameColorPreview;
    private GameAppListAdapter gameAppAdapter;
    private GameSearchAdapter gameSearchAdapter;
    private ExecutorService gameExec = Executors.newSingleThreadExecutor();
    private Handler gameHandler = new Handler(Looper.getMainLooper());
    private boolean syncingGameEnable = false;
    private boolean syncingGameMode = false;
    private static final String PREF_GAME_OVERLAY_ON = GameFpsReader.PREF_ENABLED;

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
        syncGameEnable();
        ensureGameFpsService();
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
            if (gameExec != null && !gameExec.isShutdown()) {
                gameExec.shutdownNow();
            }
        } catch (Throwable ignored) {}
    }

    private void bindViews(View rootView) {
        fpsSwitch = rootView.findViewById(R.id.fpsSwitch);
        fpsSizeSeekBar = rootView.findViewById(R.id.fpsSizeSeekBar);
        fpsColorPreview = rootView.findViewById(R.id.fpsColorPreview);
        fpsLabelColorPreview = rootView.findViewById(R.id.fpsLabelColorPreview);
        fpsGameColorPreview = rootView.findViewById(R.id.fpsGameColorPreview);
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
        btnFpsGameLog = rootView.findViewById(R.id.btnFpsGameLog);
        edtFpsGamePkg = rootView.findViewById(R.id.edtFpsGamePkg);
        txtFpsGameShizuku = rootView.findViewById(R.id.txtFpsGameShizuku);
        txtFpsGameTarget = rootView.findViewById(R.id.txtFpsGameTarget);
        txtFpsGameModeState = rootView.findViewById(R.id.txtFpsGameModeState);
        rvFpsGameApps = rootView.findViewById(R.id.rvFpsGameApps);
        rgFpsGameMode = rootView.findViewById(R.id.rgFpsGameMode);
        rbFpsGameModeAuto = rootView.findViewById(R.id.rbFpsGameModeAuto);
        rbFpsGameModeManual = rootView.findViewById(R.id.rbFpsGameModeManual);
        grpFpsGameManual = rootView.findViewById(R.id.grpFpsGameManual);

        chkFpsGameEnable = rootView.findViewById(R.id.chkFpsGameEnable);
        if (chkFpsGameEnable != null) {
            chkFpsGameEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (syncingGameEnable) return;
                activity.applyCheckboxTint(chkFpsGameEnable, isChecked);
                if (isChecked) {
                    if (!PermissionHelper.hasOverlayPermission(activity)) {
                        setGameEnabled(false);
                        Toast.makeText(activity, "Izin overlay belum diberikan",
                                Toast.LENGTH_SHORT).show();
                        refreshGameFpsStatus();
                        return;
                    }
                    if (!ShizukuHelper.isBinderAlive()) {
                        setGameEnabled(false);
                        Toast.makeText(activity, "Shizuku belum aktif",
                                Toast.LENGTH_SHORT).show();
                        refreshGameFpsStatus();
                        return;
                    }
                    if (!ShizukuHelper.hasPermission()) {
                        setGameEnabled(false);
                        Toast.makeText(activity,
                                "Atur izin Shizuku di Konfigurasi > Akses Izin",
                                Toast.LENGTH_LONG).show();
                        refreshGameFpsStatus();
                        return;
                    }
                    if (!GameFpsReader.isAutoMode(activity)
                            && !GameFpsReader.isValidPackage(activity, savedGameTarget())) {
                        setGameEnabled(false);
                        Toast.makeText(activity, "Pilih game dulu", Toast.LENGTH_SHORT).show();
                        refreshGameFpsStatus();
                        return;
                    }
                    saveGameEnabled(true);
                    ensureGameFpsService();
                } else {
                    saveGameEnabled(false);
                    GameFpsService.stop(activity);
                    FloatingService.setGameActive(false, null);
                    if (!FpsConfig.enabled) FloatingService.stopGameFpsModuleIfIdle();
                }
                refreshGameFpsStatus();
            });
        }
        if (fpsBottomNav != null) {
            fpsBottomNav.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.fpsTabGame) {
                    selectFpsTab(1);
                    refreshGameFpsStatus();
                    refreshGameFpsApps();
                    syncGameEnable();
                    updateGameModeUi();
                    ensureGameFpsService();
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
                String name = String.valueOf(activity.getPackageManager().getApplicationLabel(appInfo));
                selectGameApp(appInfo.packageName, name);
            });
            rvFpsGameApps.setAdapter(gameAppAdapter);
        }

        if (edtFpsGamePkg != null) {
            gameSearchAdapter = new GameSearchAdapter(activity);
            edtFpsGamePkg.setAdapter(gameSearchAdapter);
            edtFpsGamePkg.setThreshold(1);
            edtFpsGamePkg.setOnItemClickListener((parent, view, position, id) -> {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof GameSearchAdapter.Entry) {
                    GameSearchAdapter.Entry entry = (GameSearchAdapter.Entry) item;
                    selectGameApp(entry.pkg, entry.label);
                }
            });
        }

        if (rgFpsGameMode != null) {
            setGameModeControls(GameFpsReader.isAutoMode(activity));
            rgFpsGameMode.setOnCheckedChangeListener((group, checkedId) -> {
                if (syncingGameMode) return;
                boolean auto = checkedId == R.id.rbFpsGameModeAuto;
                saveGameMode(auto);
                updateGameModeUi();
                if (isGameEnabled()) GameFpsService.start(activity);
                refreshGameFpsStatus();
            });
        }
        updateGameModeUi();

        if (btnFpsGameRefreshApps != null) btnFpsGameRefreshApps.setOnClickListener(v -> refreshGameFpsApps());
        if (btnFpsGameApplyManual != null) btnFpsGameApplyManual.setOnClickListener(v -> {
            String input = edtFpsGamePkg != null ? edtFpsGamePkg.getText().toString().trim() : "";
            if (input.isEmpty()) return;
            GameSearchAdapter.Entry found = gameSearchAdapter != null
                    ? gameSearchAdapter.findSelected(input) : null;
            if (found != null) {
                selectGameApp(found.pkg, found.label);
                return;
            }
            String pkg = input;
            int open = input.lastIndexOf('(');
            int close = input.lastIndexOf(')');
            if (open >= 0 && close > open) {
                String inside = input.substring(open + 1, close).trim();
                if (!inside.isEmpty() && !inside.contains(" ")) pkg = inside;
            }
            if (!GameFpsReader.isValidPackage(activity, pkg)) {
                Toast.makeText(activity, "Package tidak ditemukan", Toast.LENGTH_SHORT).show();
                return;
            }
            selectGameApp(pkg, GameFpsReader.getAppName(activity, pkg));
        });

        if (btnFpsGameLog != null) btnFpsGameLog.setOnClickListener(v -> showGameFpsLogDialog());

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
                    .setPositiveButton("Simpan", (dialog, which) -> {
                        String path = GameFpsLog.save(activity);
                        Toast.makeText(activity, "Log disimpan: " + path, Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Tutup", null)
                    .show();
        } catch (Throwable ignored) {}
    }

    private void selectFpsTab(int idx) {
        if (fpsSubDisplay != null) fpsSubDisplay.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        if (fpsSubGame != null) fpsSubGame.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
    }

    private void refreshGameFpsStatus() {
        if (txtFpsGameShizuku == null) return;
        boolean enabled = isGameEnabled();
        boolean binder = ShizukuHelper.isBinderAlive();
        boolean permission = ShizukuHelper.hasPermission();
        boolean overlay = PermissionHelper.hasOverlayPermission(activity);
        boolean usage = PermissionHelper.hasUsageAccess(activity);

        String text = "Shizuku: " + (binder ? "● Aktif" : "● Nonaktif")
                + "   •   Izin: " + (permission ? "● Diberikan" : "● Belum")
                + "\nOverlay: " + (overlay ? "● Siap" : "● Belum")
                + "   •   Akses penggunaan: " + (usage ? "● Siap" : "○ Opsional")
                + "\nAtur izin di Konfigurasi > Akses Izin";
        txtFpsGameShizuku.setText(text);

        if (txtFpsGameModeState != null) {
            txtFpsGameModeState.setText(enabled ? "● Aktif" : "● Nonaktif");
            txtFpsGameModeState.setTextColor(enabled ? 0xFF4CAF50 : 0xFF9E9E9E);
        }
        updateGameTargetText();
    }

    private void ensureGameFpsService() {
        try {
            boolean armed = isGameEnabled();
            boolean shouldStart = GameFpsReader.isAutoMode(activity)
                    || GameFpsReader.isValidPackage(activity, savedGameTarget());
            if (armed && shouldStart) {
                if (FloatingService.instance == null) {
                    Intent intent = new Intent(activity, FloatingService.class);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        activity.startForegroundService(intent);
                    } else {
                        activity.startService(intent);
                    }
                }
                if (!GameFpsService.isRunning()) GameFpsService.start(activity);
            } else if (!armed && GameFpsService.isRunning()) {
                GameFpsService.stop(activity);
            }
        } catch (Throwable ignored) {}
    }

    private void selectGameApp(String pkg, String name) {
        if (!GameFpsReader.isValidPackage(activity, pkg)) {
            Toast.makeText(activity, "Package tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }
        saveGameFpsTarget(pkg);
        GameFpsLog.add("PILIH " + name + " (" + pkg + ")");
        if (GameFpsReader.isAutoMode(activity)) {
            saveGameMode(false);
            updateGameModeUi();
        }
        if (edtFpsGamePkg != null) {
            edtFpsGamePkg.setText(name + " (" + pkg + ")");
            edtFpsGamePkg.setSelection(edtFpsGamePkg.getText().length());
            edtFpsGamePkg.clearFocus();
            edtFpsGamePkg.dismissDropDown();
        }
        armGameFps();
    }

    private void armGameFps() {
        if (chkFpsGameEnable != null && !chkFpsGameEnable.isChecked()) {
            chkFpsGameEnable.setChecked(true);
        } else {
            ensureGameFpsService();
        }
        refreshGameFpsStatus();
    }

    private void syncGameEnable() {
        if (chkFpsGameEnable == null) return;
        boolean armed = isGameEnabled();
        if (chkFpsGameEnable.isChecked() != armed) {
            setGameEnabled(armed);
        } else {
            activity.applyCheckboxTint(chkFpsGameEnable, armed);
        }
    }

    private void setGameEnabled(boolean checked) {
        if (chkFpsGameEnable == null) return;
        syncingGameEnable = true;
        chkFpsGameEnable.setChecked(checked);
        syncingGameEnable = false;
        activity.applyCheckboxTint(chkFpsGameEnable, checked);
    }

    private void saveGameEnabled(boolean enabled) {
        try {
            activity.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_GAME_OVERLAY_ON, enabled).apply();
        } catch (Throwable ignored) {}
    }

    private boolean isGameEnabled() {
        try {
            return activity.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE)
                    .getBoolean(PREF_GAME_OVERLAY_ON, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private void saveGameMode(boolean auto) {
        try {
            activity.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE)
                    .edit().putString(GameFpsReader.PREF_MODE,
                            auto ? GameFpsReader.MODE_AUTO : GameFpsReader.MODE_MANUAL).apply();
        } catch (Throwable ignored) {}
    }

    private void setGameModeControls(boolean auto) {
        if (rgFpsGameMode == null) return;
        syncingGameMode = true;
        if (auto && rbFpsGameModeAuto != null) {
            rbFpsGameModeAuto.setChecked(true);
        } else if (!auto && rbFpsGameModeManual != null) {
            rbFpsGameModeManual.setChecked(true);
        }
        syncingGameMode = false;
    }

    private void updateGameModeUi() {
        boolean auto = GameFpsReader.isAutoMode(activity);
        setGameModeControls(auto);
        if (grpFpsGameManual != null) {
            grpFpsGameManual.setVisibility(auto ? View.GONE : View.VISIBLE);
        }
        updateGameTargetText();
    }

    private void updateGameTargetText() {
        if (txtFpsGameTarget == null) return;
        if (GameFpsReader.isAutoMode(activity)) {
            String active = GameFpsService.getLastTargetPackage();
            if (active != null && !active.isEmpty()) {
                txtFpsGameTarget.setText("Target: Otomatis • "
                        + GameFpsReader.getAppName(activity, active));
            } else {
                txtFpsGameTarget.setText("Target: Otomatis (aplikasi depan)");
            }
        } else {
            String saved = savedGameTarget();
            if (saved != null && !saved.isEmpty()
                    && GameFpsReader.isValidPackage(activity, saved)) {
                txtFpsGameTarget.setText("Target: "
                        + GameFpsReader.getAppName(activity, saved) + " (" + saved + ")");
            } else {
                txtFpsGameTarget.setText("Target: Belum dipilih");
            }
        }
    }

    private String savedGameTarget() {
        try {
            return activity.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE)
                    .getString(GameFpsReader.PREF_TARGET, "");
        } catch (Throwable t) {
            return "";
        }
    }

    private void saveGameFpsTarget(String pkg) {
        try {
            activity.getSharedPreferences("ftxt_prefs", Context.MODE_PRIVATE)
                    .edit().putString(GameFpsReader.PREF_TARGET, pkg).apply();
        } catch (Throwable ignored) {}
    }

    private void refreshGameFpsApps() {
        if (gameExec == null || gameExec.isShutdown()) return;
        gameExec.execute(() -> {
            PackageManager pm = activity.getPackageManager();
            List<ApplicationInfo> list = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            java.util.HashMap<String, String> labels = new java.util.HashMap<>();
            for (ApplicationInfo ai : list) {
                try {
                    labels.put(ai.packageName, String.valueOf(pm.getApplicationLabel(ai)));
                } catch (Throwable t) {
                    labels.put(ai.packageName, ai.packageName);
                }
            }
            List<ApplicationInfo> userApps = new ArrayList<>();
            for (ApplicationInfo ai : list) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    userApps.add(ai);
                }
            }
            if (userApps.isEmpty()) userApps.addAll(list);
            Collections.sort(userApps, (a, b) -> {
                String la = labels.get(a.packageName);
                String lb = labels.get(b.packageName);
                return la.compareToIgnoreCase(lb);
            });
            List<GameSearchAdapter.Entry> entries = new ArrayList<>();
            for (ApplicationInfo ai : list) {
                entries.add(new GameSearchAdapter.Entry(ai, labels.get(ai.packageName)));
            }
            Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
            gameHandler.post(() -> {
                if (gameAppAdapter != null) gameAppAdapter.setApps(userApps);
                if (gameSearchAdapter != null) gameSearchAdapter.setEntries(entries);
            });
        });
    }
    private void loadConfig() {
        fpsSwitch.setChecked(FpsConfig.enabled);
        activity.applyCheckboxTint(fpsSwitch, FpsConfig.enabled);
        fpsSizeSeekBar.setProgress((int) FpsConfig.size);
        fpsColorPreview.setBackgroundColor(FpsConfig.color);
        fpsLabelColorPreview.setBackgroundColor(FpsConfig.labelColor);
        if (fpsGameColorPreview != null) {
            fpsGameColorPreview.setBackgroundColor(FpsConfig.gameColor);
        }
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
                if (!isGameEnabled()) FloatingService.stopModule(FloatingService.fpsModule());
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

        if (fpsGameColorPreview != null) {
            fpsGameColorPreview.setOnClickListener(v -> {
                ColorPickerDialog.show(activity, "Warna FPS Game", FpsConfig.gameColor, color -> {
                    FpsConfig.gameColor = color;
                    fpsGameColorPreview.setBackgroundColor(color);
                    activity.getSharedPreferences("ftxt_prefs", MainActivity.MODE_PRIVATE)
                            .edit().putInt("fps_game_color", color).apply();
                    FloatingService.refreshFpsDisplay();
                });
            });
        }

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
