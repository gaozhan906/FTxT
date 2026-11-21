package exp.ftxt.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.core.appmanager.AppActions;
import exp.ftxt.core.appmanager.AppInfo;
import exp.ftxt.core.appmanager.InstalledApps;
import exp.ftxt.features.gamefps.reader.ShizukuHelper;
import exp.ftxt.utils.PermissionHelper;

public class AppManagerActivity extends AppCompatActivity {
    private static final String[] SORTS = {
            "Waktu instal", "Nama", "Ukuran APK", "Kali dibuka",
            "Terakhir dipakai", "Screen time", "Baru diperbarui", "Jarang dipakai"
    };

    private static final int F_ALL = 0;
    private static final int F_USER = 1;
    private static final int F_RUNNING = 2;
    private static final int F_SYSTEM = 3;
    private static final int F_32 = 4;
    private static final int F_64 = 5;
    private static final int F_DISABLED = 6;
    private static final int REQUEST_INSTALL = 20004;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private InstalledApps apps;
    private AppListAdapter adapter;
    private EditText search;
    private TextView countText;
    private TextView countBadge;
    private TextView emptyView;
    private TextView filterSummary;
    private View emptyReset;
    private View clearButton;
    private View actionBar;
    private ProgressBar loading;
    private long filterVersion;
    private boolean resumedOnce;
    private String selectedCategory = "Semua";
    private int currentFilterId = R.id.appMgrChipAll;
    private int currentFilter = F_ALL;
    private int currentSort = 0;

    private BottomNavigationView bottomNav;
    private View tabOverview;
    private View tabApps;
    private TextView tileInstalled;
    private TextView tileSystem;
    private TextView tileRecent;
    private TextView tileDisabled;
    private TextView storageUsed;
    private TextView storagePercent;
    private TextView storageApps;
    private TextView storageOther;
    private TextView storageFree;
    private ProgressBar storageProgress;
    private TextView ramSummary;
    private TextView ramUsed;
    private TextView ramFree;
    private ProgressBar ramProgress;
    private TextView screenTimeSummary;
    private TextView shizukuStatus;
    private TextView usageStatus;
    private LinearLayout recentAppsRow;
    private LinearLayout screenTimeRow;
    private LinearLayout permissionRow;
    private LinearLayout largeAppsRow;
    private LinearLayout unusedAppsRow;
    private int currentTabId = R.id.appMgrNavOverview;

    public static void start(android.content.Context context) {
        InstalledApps.init(context);
        context.startActivity(new Intent(context, AppManagerActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_app_manager);

        InstalledApps.init(getApplicationContext());
        apps = InstalledApps.get();

        setupWindowInsets();
        MaterialToolbar toolbar = findViewById(R.id.appMgrToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        countBadge = findViewById(R.id.appMgrCount);
        tileInstalled = findViewById(R.id.appMgrTileInstalledValue);
        tileSystem = findViewById(R.id.appMgrTileSystemValue);
        tileRecent = findViewById(R.id.appMgrTileRecentValue);
        tileDisabled = findViewById(R.id.appMgrTileDisabledValue);
        storageUsed = findViewById(R.id.appMgrStorageUsed);
        storagePercent = findViewById(R.id.appMgrStoragePercent);
        storageApps = findViewById(R.id.appMgrStorageApps);
        storageOther = findViewById(R.id.appMgrStorageOther);
        storageFree = findViewById(R.id.appMgrStorageFree);
        storageProgress = findViewById(R.id.appMgrStorageProgress);
        ramSummary = findViewById(R.id.appMgrRamSummary);
        ramUsed = findViewById(R.id.appMgrRamUsed);
        ramFree = findViewById(R.id.appMgrRamFree);
        ramProgress = findViewById(R.id.appMgrRamProgress);
        screenTimeSummary = findViewById(R.id.appMgrScreenTimeSummary);
        shizukuStatus = findViewById(R.id.appMgrShizukuStatus);
        usageStatus = findViewById(R.id.appMgrUsageStatus);
        recentAppsRow = findViewById(R.id.appMgrRecentAppsRow);
        screenTimeRow = findViewById(R.id.appMgrScreenTimeRow);
        permissionRow = findViewById(R.id.appMgrPermissionRow);
        largeAppsRow = findViewById(R.id.appMgrLargeAppsRow);
        unusedAppsRow = findViewById(R.id.appMgrUnusedAppsRow);

        bottomNav = findViewById(R.id.appMgrBottomNav);
        tabOverview = findViewById(R.id.appMgrTabOverview);
        tabApps = findViewById(R.id.appMgrTabApps);

        search = findViewById(R.id.appMgrSearch);
        countText = findViewById(R.id.appMgrSelectedCount);
        emptyView = findViewById(R.id.appMgrEmpty);
        filterSummary = findViewById(R.id.appMgrFilterSummary);
        emptyReset = findViewById(R.id.appMgrEmptyReset);
        clearButton = findViewById(R.id.appMgrClearSearch);
        actionBar = findViewById(R.id.appMgrActionBar);
        loading = findViewById(R.id.appMgrLoading);

        clearButton.setOnClickListener(v -> search.setText(""));
        emptyReset.setOnClickListener(v -> resetFilters());
        findViewById(R.id.appMgrFilterButton).setOnClickListener(v -> showFilterSheet());
        findViewById(R.id.appMgrOpenOverviewApps).setOnClickListener(v ->
                bottomNav.setSelectedItemId(R.id.appMgrNavApps));
        findViewById(R.id.appMgrActionCancel).setOnClickListener(v -> adapter.clearSelection());
        findViewById(R.id.appMgrActionUninstall).setOnClickListener(v -> confirmUninstall());
        findViewById(R.id.appMgrActionShare).setOnClickListener(v -> shareSelected());

        findViewById(R.id.appMgrTileInstalledCard).setOnClickListener(v ->
                showAppsTabWithFilter(F_USER, 0));
        findViewById(R.id.appMgrTileSystemCard).setOnClickListener(v ->
                showAppsTabWithFilter(F_SYSTEM, 0));
        findViewById(R.id.appMgrTileRecentCard).setOnClickListener(v ->
                showAppsTabWithFilter(F_RUNNING, 0));
        findViewById(R.id.appMgrTileDisabledCard).setOnClickListener(v ->
                showAppsTabWithFilter(F_DISABLED, 0));
        findViewById(R.id.appMgrStorageAction).setOnClickListener(v -> openStorageSettings());
        findViewById(R.id.appMgrRamAction).setOnClickListener(v ->
                showAppsTabWithFilter(F_RUNNING, 0));
        findViewById(R.id.appMgrHistoryAction).setOnClickListener(v -> showAppsTabWithSort(6));
        findViewById(R.id.appMgrScreenTimeAction).setOnClickListener(v -> showAppsTabWithSort(5));
        findViewById(R.id.appMgrPermissionAction).setOnClickListener(v -> showPermissionAnalysis());
        findViewById(R.id.appMgrLargeAction).setOnClickListener(v -> showAppsTabWithSort(2));
        findViewById(R.id.appMgrUnusedAction).setOnClickListener(v ->
                showAppsTabWithFilter(F_USER, 7));
        findViewById(R.id.appMgrNetworkAction).setOnClickListener(v -> openNetworkPanel());
        findViewById(R.id.appMgrInstallAction).setOnClickListener(v -> pickInstallFile());
        findViewById(R.id.appMgrUsageAction).setOnClickListener(v ->
                PermissionHelper.requestUsageAccess(this));

        adapter = new AppListAdapter(new AppListAdapter.Listener() {
            @Override
            public void onOpen(AppInfo app) {
                AppDetailActivity.start(AppManagerActivity.this, app.packageName);
            }

            @Override
            public void onSelectionChanged(int count) {
                actionBar.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
                countText.setText(count + " dipilih");
            }
        });
        RecyclerView recycler = findViewById(R.id.appMgrRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        setupBottomNav();
        setupSearch();
        loadApps();
    }

    private void setupWindowInsets() {
        final View root = findViewById(R.id.appMgrRoot);
        final View bottom = findViewById(R.id.appMgrBottomNav);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, 0);
            bottom.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce) loadApps();
        resumedOnce = true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Intent install = new Intent(Intent.ACTION_VIEW, data.getData());
        install.setType("application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(install);
        } catch (Throwable ignored) {
            Toast.makeText(this, "Berkas tidak dapat dipasang", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.appMgrNavOverview) {
                showTab(tabOverview);
                updateOverview();
            } else {
                showTab(tabApps);
                updateFilterSummary();
            }
            currentTabId = id;
            return true;
        });
        showTab(tabOverview);
        updateOverview();
    }

    private void showTab(View tab) {
        tabOverview.setVisibility(tab == tabOverview ? View.VISIBLE : View.GONE);
        tabApps.setVisibility(tab == tabApps ? View.VISIBLE : View.GONE);
    }

    private void refreshCurrentTab() {
        if (currentTabId == R.id.appMgrNavOverview) updateOverview();
    }

    private void showAppsTabWithFilter(int filter, int sort) {
        currentFilter = filter;
        switch (filter) {
            case F_USER:
                currentFilterId = R.id.appMgrChipUser;
                break;
            case F_RUNNING:
                currentFilterId = R.id.appMgrChipRunning;
                break;
            case F_SYSTEM:
                currentFilterId = R.id.appMgrChipSystem;
                break;
            case F_32:
                currentFilterId = R.id.appMgrChip32;
                break;
            case F_64:
                currentFilterId = R.id.appMgrChip64;
                break;
            case F_DISABLED:
                currentFilterId = R.id.appMgrChipDisabled;
                break;
            default:
                currentFilterId = R.id.appMgrChipAll;
                break;
        }
        currentSort = sort;
        selectedCategory = "Semua";
        search.setText("");
        bottomNav.setSelectedItemId(R.id.appMgrNavApps);
        updateFilterSummary();
        applyFilters();
    }

    private void showAppsTabWithSort(int sort) {
        showAppsTabWithFilter(F_ALL, sort);
    }

    private void openStorageSettings() {
        Intent intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        try {
            startActivity(intent);
        } catch (Throwable ignored) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openNetworkPanel() {
        getSharedPreferences("ftxt_prefs", MODE_PRIVATE).edit()
                .putInt("nav_selected_item", R.id.navNetwork).apply();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void pickInstallFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQUEST_INSTALL);
        } catch (Throwable ignored) {
            Toast.makeText(this, "Tidak ada aplikasi pembuka berkas", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPermissionAnalysis() {
        List<OverviewApp> items = collectPermissionApps(apps.snapshot());
        StringBuilder message = new StringBuilder("Aplikasi dengan izin terbanyak:");
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            OverviewApp item = items.get(i);
            message.append("\n").append(i + 1).append(". ")
                    .append(item.app.label).append(" — ").append(item.caption);
        }
        if (items.isEmpty()) message.append("\nTidak ada data izin.");
        new AlertDialog.Builder(this)
                .setTitle("Analisis izin")
                .setMessage(message.toString())
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void updateOverview() {
        if (apps == null) return;
        List<AppInfo> snapshot = apps.snapshot();
        Set<String> active = apps.getRecentlyActive();
        long now = System.currentTimeMillis();
        int user = 0;
        int system = 0;
        int disabled = 0;
        int recent = 0;
        int usageTracked = 0;
        long appBytes = 0;
        long foreground = 0;
        for (AppInfo app : snapshot) {
            if (app.system) system++;
            else user++;
            if (!app.enabled) disabled++;
            if (active.contains(app.packageName) || app.isRecentlyActive(now, 10L * 60L * 1000L)) recent++;
            if (app.lastTimeUsed > 0) usageTracked++;
            appBytes += apkBytes(app);
            foreground += Math.max(0, app.foregroundTimeMs);
        }
        tileInstalled.setText(String.valueOf(user));
        tileSystem.setText(String.valueOf(system));
        tileRecent.setText(String.valueOf(recent));
        tileDisabled.setText(String.valueOf(disabled));

        updateStorageOverview(appBytes);
        updateRamOverview();

        screenTimeSummary.setText("Total foreground " + durationText(foreground));
        populateRecentApps(snapshot, now);
        populateScreenTimeApps(snapshot);
        populatePermissionApps(snapshot);
        populateLargeApps(snapshot);
        populateUnusedApps(snapshot, now, active);

        boolean alive = ShizukuHelper.isBinderAlive();
        boolean granted = ShizukuHelper.hasPermission();
        shizukuStatus.setText(!alive ? "Shizuku: tidak aktif"
                : granted ? "Shizuku: aktif dan diizinkan" : "Shizuku: aktif, izin belum diberikan");
        usageStatus.setText(usageTracked == 0
                ? "Akses penggunaan: belum tersedia"
                : "Akses penggunaan: " + usageTracked + " aplikasi tercatat");
    }

    private void updateStorageOverview(long appBytes) {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long total = stat.getTotalBytes();
        long free = stat.getAvailableBytes();
        long used = Math.max(0, total - free);
        long other = Math.max(0, used - appBytes);
        int percent = total == 0 ? 0 : (int) ((used * 100) / total);
        storageUsed.setText("Terpakai " + formatBytes(used) + " dari " + formatBytes(total));
        storagePercent.setText(percent + "%");
        storageProgress.setProgress(Math.min(100, Math.max(0, percent)));
        storageApps.setText("Aplikasi (APK): " + formatBytes(Math.min(appBytes, used)));
        storageOther.setText("Lainnya: " + formatBytes(other));
        storageFree.setText("Bebas: " + formatBytes(free));
    }

    private void updateRamOverview() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        long total = memory.totalMem;
        long available = memory.availMem;
        long used = Math.max(0, total - available);
        int percent = total == 0 ? 0 : (int) ((used * 100) / total);
        ramSummary.setText("Total " + formatBytes(total));
        ramUsed.setText("Terpakai: " + formatBytes(used));
        ramFree.setText("Tersedia: " + formatBytes(available));
        ramProgress.setProgress(Math.min(100, Math.max(0, percent)));
    }

    private void populateRecentApps(List<AppInfo> snapshot, long now) {
        List<OverviewApp> items = new ArrayList<>();
        for (AppInfo app : snapshot) {
            long time = Math.max(app.firstInstallTime, app.lastUpdateTime);
            if (time <= 0) continue;
            String relative = DateUtils.getRelativeTimeSpanString(
                    time, now, DateUtils.DAY_IN_MILLIS).toString();
            String caption = (app.lastUpdateTime > app.firstInstallTime ? "Diperbarui " : "Dipasang ")
                    + relative;
            items.add(new OverviewApp(app, caption, time));
        }
        Collections.sort(items, (a, b) -> Long.compare(b.weight, a.weight));
        populateAppRow(recentAppsRow, items);
    }

    private void populateScreenTimeApps(List<AppInfo> snapshot) {
        List<OverviewApp> items = new ArrayList<>();
        for (AppInfo app : snapshot) {
            if (app.foregroundTimeMs <= 0) continue;
            items.add(new OverviewApp(app, durationText(app.foregroundTimeMs), app.foregroundTimeMs));
        }
        Collections.sort(items, (a, b) -> Long.compare(b.weight, a.weight));
        populateAppRow(screenTimeRow, items);
    }

    private void populatePermissionApps(List<AppInfo> snapshot) {
        populateAppRow(permissionRow, collectPermissionApps(snapshot));
    }

    private List<OverviewApp> collectPermissionApps(List<AppInfo> snapshot) {
        List<OverviewApp> items = new ArrayList<>();
        PackageManager packageManager = getPackageManager();
        for (AppInfo app : snapshot) {
            try {
                PackageInfo info = packageManager.getPackageInfo(
                        app.packageName, PackageManager.GET_PERMISSIONS);
                int count = info.requestedPermissions == null ? 0 : info.requestedPermissions.length;
                if (count <= 0) continue;
                items.add(new OverviewApp(app, count + " izin", count));
            } catch (Throwable ignored) {
            }
        }
        Collections.sort(items, (a, b) -> Long.compare(b.weight, a.weight));
        return items;
    }

    private void populateLargeApps(List<AppInfo> snapshot) {
        List<OverviewApp> items = new ArrayList<>();
        for (AppInfo app : snapshot) {
            long bytes = apkBytes(app);
            if (bytes <= 0) continue;
            items.add(new OverviewApp(app, formatBytes(bytes), bytes));
        }
        Collections.sort(items, (a, b) -> Long.compare(b.weight, a.weight));
        populateAppRow(largeAppsRow, items);
    }

    private void populateUnusedApps(
            List<AppInfo> snapshot, long now, Set<String> active) {
        List<OverviewApp> items = new ArrayList<>();
        long month = 30L * 24L * 60L * 60L * 1000L;
        for (AppInfo app : snapshot) {
            if (app.system || active.contains(app.packageName)) continue;
            long idle = app.lastTimeUsed <= 0
                    ? Math.max(0, now - app.firstInstallTime) : now - app.lastTimeUsed;
            if (idle < month) continue;
            String caption = app.lastTimeUsed <= 0
                    ? "Belum dipakai" : (idle / (24L * 60L * 60L * 1000L)) + " hari lalu";
            items.add(new OverviewApp(app, caption, idle));
        }
        Collections.sort(items, (a, b) -> Long.compare(b.weight, a.weight));
        populateAppRow(unusedAppsRow, items);
    }

    private void populateAppRow(LinearLayout row, List<OverviewApp> items) {
        row.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Tidak ada data");
            empty.setTextSize(12);
            empty.setTextColor(getResources().getColor(R.color.appmgr_label));
            row.addView(empty);
            return;
        }
        int limit = Math.min(4, items.size());
        for (int i = 0; i < limit; i++) {
            OverviewApp item = items.get(i);
            row.addView(overviewAppTile(item.app, item.caption));
        }
    }

    private View overviewAppTile(AppInfo app, String caption) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackgroundResource(R.drawable.appmgr_item_bg);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(100), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(10), 0);
        tile.setLayoutParams(params);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        tile.addView(icon);

        TextView name = new TextView(this);
        name.setText(app.label);
        name.setTextSize(12);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextColor(getResources().getColor(R.color.appmgr_on_surface));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(6);
        name.setLayoutParams(nameParams);
        tile.addView(name);

        TextView detail = new TextView(this);
        detail.setText(caption);
        detail.setTextSize(11);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setTextColor(getResources().getColor(R.color.appmgr_label));
        tile.addView(detail);

        tile.setOnClickListener(v -> AppDetailActivity.start(this, app.packageName));
        return tile;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        if (unit == 0) return Math.round(value) + " B";
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    private String durationText(long ms) {
        if (ms <= 0) return "—";
        long minutes = (ms + 59_000L) / 60_000L;
        if (minutes < 60) return minutes + " mnt";
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " jam" : String.format(Locale.US, "%dj %dm", hours, rest);
    }

    private static class OverviewApp {
        final AppInfo app;
        final String caption;
        final long weight;

        OverviewApp(AppInfo app, String caption, long weight) {
            this.app = app;
            this.caption = caption;
            this.weight = weight;
        }
    }

    private void updateFilterSummary() {
        if (filterSummary == null) return;
        String filter = filterName(currentFilter);
        String category = "Semua".equals(selectedCategory) ? "" : " · " + selectedCategory;
        filterSummary.setText(filter + category + " · " + SORTS[currentSort]);
    }

    private String filterName(int filter) {
        switch (filter) {
            case F_USER: return "Aplikasi user";
            case F_RUNNING: return "Aplikasi berjalan";
            case F_SYSTEM: return "Aplikasi sistem";
            case F_32: return "Aplikasi 32-bit";
            case F_64: return "Aplikasi 64-bit";
            case F_DISABLED: return "Aplikasi dinonaktifkan";
            default: return "Semua aplikasi";
        }
    }

    private void resetFilters() {
        currentFilter = F_ALL;
        currentFilterId = R.id.appMgrChipAll;
        currentSort = 0;
        selectedCategory = "Semua";
        search.setText("");
        updateFilterSummary();
        applyFilters();
    }

    private void setupSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                clearButton.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void loadApps() {
        loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            final List<AppInfo> refreshed = apps.refresh();
            uiHandler.post(() -> {
                loading.setVisibility(View.GONE);
                countBadge.setText(refreshed.size() + " aplikasi");
                adapter.setApps(refreshed);
                applyFilters();
                refreshCurrentTab();
            });
        });
    }

    private void applyFilters() {
        final long version = ++filterVersion;
        updateFilterSummary();
        final String query = search.getText().toString().trim().toLowerCase(Locale.US);
        final int filter = currentFilter;
        final int sort = currentSort;
        final String category = selectedCategory;

        executor.execute(() -> {
            List<AppInfo> result = new ArrayList<>();
            long now = System.currentTimeMillis();
            Set<String> active = apps.getRecentlyActive();
            for (AppInfo app : apps.snapshot()) {
                if (!matchesFilter(app, filter, active, now)) continue;
                if (!"Semua".equals(category) && !category.equals(app.category)) continue;
                if (!query.isEmpty()
                        && !(app.label.toLowerCase(Locale.US).contains(query)
                        || app.packageName.toLowerCase(Locale.US).contains(query))) continue;
                result.add(app);
            }
            sortApps(result, sort);
            if (version != filterVersion) return;
            final List<AppInfo> finalList = result;
            uiHandler.post(() -> {
                adapter.setQuery(query);
                adapter.setApps(finalList);
                boolean empty = finalList.isEmpty();
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                emptyReset.setVisibility(empty ? View.VISIBLE : View.GONE);
                countText.setText(adapter.getSelected().size() + " dipilih");
            });
        });
    }

    private void showFilterSheet() {
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_appmgr_filter, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);

        ChipGroup filterGroup = sheet.findViewById(R.id.appMgrFilters);
        ChipGroup sortGroup = sheet.findViewById(R.id.appMgrSortChips);
        filterGroup.check(currentFilterId);
        sortGroup.check(sortChipId(currentSort));

        sheet.findViewById(R.id.appMgrFilterClose).setOnClickListener(v -> dialog.dismiss());
        filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) return;
            currentFilterId = checkedId;
            currentFilter = filterOf(checkedId);
            applyFilters();
        });
        sortGroup.setOnCheckedChangeListener((group, checkedId) -> {
            currentSort = sortIndexOf(checkedId);
            applyFilters();
        });

        final List<String> categories = apps == null
                ? Collections.singletonList("Semua") : apps.allCategories();
        ChipGroup categoryGroup = sheet.findViewById(R.id.appMgrCategories);
        categoryGroup.setSingleSelection(true);
        categoryGroup.removeAllViews();
        Chip checkedCategory = null;
        for (final String category : categories) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setText(category);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> {
                selectedCategory = category;
                applyFilters();
            });
            categoryGroup.addView(chip);
            if (category.equals(selectedCategory)) checkedCategory = chip;
        }
        if (checkedCategory != null) categoryGroup.check(checkedCategory.getId());
        dialog.show();
    }

    private int filterOf(int checkedId) {
        if (checkedId == R.id.appMgrChipUser) return F_USER;
        if (checkedId == R.id.appMgrChipRunning) return F_RUNNING;
        if (checkedId == R.id.appMgrChipSystem) return F_SYSTEM;
        if (checkedId == R.id.appMgrChip32) return F_32;
        if (checkedId == R.id.appMgrChip64) return F_64;
        if (checkedId == R.id.appMgrChipDisabled) return F_DISABLED;
        return F_ALL;
    }

    private boolean matchesFilter(AppInfo app, int filter, Set<String> active, long now) {
        switch (filter) {
            case F_USER:
                return !app.system;
            case F_RUNNING:
                return active.contains(app.packageName) || app.isRecentlyActive(now, 10L * 60L * 1000L);
            case F_SYSTEM:
                return app.system;
            case F_32:
                return !"—".equals(app.abi) && !app.is64Bit();
            case F_64:
                return app.is64Bit();
            case F_DISABLED:
                return !app.enabled;
            default:
                return true;
        }
    }

    private int sortChipId(int index) {
        switch (index) {
            case 1: return R.id.appMgrSortName;
            case 2: return R.id.appMgrSortSize;
            case 3: return R.id.appMgrSortOpened;
            case 4: return R.id.appMgrSortUsed;
            case 5: return R.id.appMgrSortScreen;
            case 6: return R.id.appMgrSortUpdated;
            case 7: return R.id.appMgrSortUnused;
            default: return R.id.appMgrSortInstall;
        }
    }

    private int sortIndexOf(int chipId) {
        if (chipId == R.id.appMgrSortName) return 1;
        if (chipId == R.id.appMgrSortSize) return 2;
        if (chipId == R.id.appMgrSortOpened) return 3;
        if (chipId == R.id.appMgrSortUsed) return 4;
        if (chipId == R.id.appMgrSortScreen) return 5;
        if (chipId == R.id.appMgrSortUpdated) return 6;
        if (chipId == R.id.appMgrSortUnused) return 7;
        return 0;
    }

    private void sortApps(List<AppInfo> list, int sort) {
        switch (sort) {
            case 1:
                Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
                break;
            case 2:
                Collections.sort(list, (a, b) -> Long.compare(apkBytes(b), apkBytes(a)));
                break;
            case 3:
                Collections.sort(list, (a, b) -> Integer.compare(b.launchCount, a.launchCount));
                break;
            case 4:
                Collections.sort(list, (a, b) -> Long.compare(b.lastTimeUsed, a.lastTimeUsed));
                break;
            case 5:
                Collections.sort(list, (a, b) -> Long.compare(b.foregroundTimeMs, a.foregroundTimeMs));
                break;
            case 6:
                Collections.sort(list, (a, b) -> Long.compare(b.lastUpdateTime, a.lastUpdateTime));
                break;
            case 7:
                Collections.sort(list, (a, b) -> Long.compare(a.lastTimeUsed, b.lastTimeUsed));
                break;
            case 0:
            default:
                Collections.sort(list, (a, b) -> Long.compare(b.firstInstallTime, a.firstInstallTime));
                break;
        }
    }

    private long apkBytes(AppInfo app) {
        if (app.sourceDir == null) return 0;
        try {
            return new File(app.sourceDir).length();
        } catch (Throwable t) {
            return 0;
        }
    }

    private void confirmUninstall() {
        final Set<String> selected = adapter.getSelected();
        if (selected.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Hapus aplikasi")
                .setMessage("Hapus " + selected.size() + " aplikasi yang dipilih?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> {
                    for (AppInfo app : apps.snapshot()) {
                        if (selected.contains(app.packageName)) {
                            startActivity(AppActions.uninstall(app));
                        }
                    }
                    adapter.clearSelection();
                })
                .show();
    }

    private void shareSelected() {
        final Set<String> selected = adapter.getSelected();
        if (selected.isEmpty()) return;
        final List<AppInfo> chosen = new ArrayList<>();
        for (AppInfo app : apps.snapshot()) {
            if (selected.contains(app.packageName)) chosen.add(app);
        }
        executor.execute(() -> {
            final List<android.net.Uri> uris = AppShareHelper.prepare(
                    getApplicationContext(), chosen);
            uiHandler.post(() -> {
                if (uris.isEmpty()) {
                    Toast.makeText(this, "APK tidak dapat disiapkan", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(AppShareHelper.sendIntent(this, uris));
            });
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
