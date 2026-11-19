package exp.ftxt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import exp.ftxt.R;
import exp.ftxt.core.appmanager.AppActions;
import exp.ftxt.core.appmanager.AppInfo;
import exp.ftxt.core.appmanager.InstalledApps;
import exp.ftxt.features.gamefps.reader.ShizukuHelper;

public class AppManagerActivity extends AppCompatActivity {
    private static final String[] SORTS = {
            "Waktu instal", "Nama", "Ukuran APK", "Kali dibuka",
            "Terakhir dipakai", "Screen time", "Data use"
    };

    private static final int F_ALL = 0;
    private static final int F_USER = 1;
    private static final int F_RUNNING = 2;
    private static final int F_SYSTEM = 3;
    private static final int F_32 = 4;
    private static final int F_64 = 5;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private InstalledApps apps;
    private AppListAdapter adapter;
    private EditText search;
    private Spinner categorySpinner;
    private TextView countText;
    private TextView countBadge;
    private TextView emptyView;
    private View clearButton;
    private LinearLayout actionBar;
    private ProgressBar loading;
    private long filterVersion;
    private boolean resumedOnce;
    private String selectedCategory = "Semua";
    private int currentFilterId = R.id.appMgrChipAll;
    private int currentSort = 0;

    private BottomNavigationView bottomNav;
    private View tabOverview;
    private View tabApps;
    private TextView overviewStats;
    private ChipGroup tagChips;
    private TextView shizukuStatus;
    private int currentTabId = R.id.appMgrNavOverview;

    public static void start(android.content.Context context) {
        InstalledApps.init(context);
        context.startActivity(new Intent(context, AppManagerActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manager);

        InstalledApps.init(getApplicationContext());
        apps = InstalledApps.get();

        countBadge = findViewById(R.id.appMgrCount);
        overviewStats = findViewById(R.id.appMgrOverviewStats);
        tagChips = findViewById(R.id.appMgrTagChips);
        shizukuStatus = findViewById(R.id.appMgrShizukuStatus);

        bottomNav = findViewById(R.id.appMgrBottomNav);
        tabOverview = findViewById(R.id.appMgrTabOverview);
        tabApps = findViewById(R.id.appMgrTabApps);

        search = findViewById(R.id.appMgrSearch);
        countText = findViewById(R.id.appMgrSelectedCount);
        emptyView = findViewById(R.id.appMgrEmpty);
        clearButton = findViewById(R.id.appMgrClearSearch);
        actionBar = findViewById(R.id.appMgrActionBar);
        loading = findViewById(R.id.appMgrLoading);

        findViewById(R.id.appMgrBack).setOnClickListener(v -> finish());
        clearButton.setOnClickListener(v -> search.setText(""));
        findViewById(R.id.appMgrFilterButton).setOnClickListener(v -> showFilterSheet());
        findViewById(R.id.appMgrOpenOverviewApps).setOnClickListener(v ->
                bottomNav.setSelectedItemId(R.id.appMgrNavApps));
        findViewById(R.id.appMgrActionCancel).setOnClickListener(v -> adapter.clearSelection());
        findViewById(R.id.appMgrActionUninstall).setOnClickListener(v -> confirmUninstall());
        findViewById(R.id.appMgrActionShare).setOnClickListener(v -> shareSelected());

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
        setupTagChips();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce) loadApps();
        resumedOnce = true;
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.appMgrNavOverview) {
                showTab(tabOverview);
                updateOverview();
            } else {
                showTab(tabApps);
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

    private void updateOverview() {
        if (apps == null) return;
        int total = apps.snapshot().size();
        int user = 0;
        int system = 0;
        int recent = 0;
        Set<String> active = apps.getRecentlyActive();
        for (AppInfo app : apps.snapshot()) {
            if (app.system) system++;
            else user++;
            if (active.contains(app.packageName)) recent++;
        }
        overviewStats.setText("Total aplikasi: " + total
                + "\nAplikasi pengguna: " + user
                + "\nAplikasi sistem: " + system
                + "\nAktif baru saja: " + recent
                + "\nKategori: " + Math.max(0, apps.allCategories().size() - 1));
        boolean alive = ShizukuHelper.isBinderAlive();
        boolean granted = ShizukuHelper.hasPermission();
        shizukuStatus.setText(!alive ? "Shizuku: tidak aktif"
                : granted ? "Shizuku: aktif dan diizinkan" : "Shizuku: aktif, izin belum diberikan");
    }

    private void setupTagChips() {
        if (apps == null) return;
        tagChips.removeAllViews();
        for (final String category : apps.allCategories()) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(true);
            chip.setChecked(category.equals(selectedCategory));
            chip.setOnClickListener(v -> {
                selectedCategory = category;
                if (categorySpinner != null) {
                    List<String> categories = apps == null
                            ? Collections.singletonList("Semua") : apps.allCategories();
                    int position = categories.indexOf(category);
                    if (position >= 0) categorySpinner.setSelection(position);
                }
                applyFilters();
            });
            tagChips.addView(chip);
        }
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
                setupTagChips();
                applyFilters();
                refreshCurrentTab();
            });
        });
    }

    private void applyFilters() {
        final long version = ++filterVersion;
        final String query = search.getText().toString().trim().toLowerCase(Locale.US);
        final int filter = filterOf(currentFilterId);
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
                emptyView.setVisibility(finalList.isEmpty() ? View.VISIBLE : View.GONE);
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
            applyFilters();
        });
        sortGroup.setOnCheckedChangeListener((group, checkedId) -> {
            currentSort = sortIndexOf(checkedId);
            if (currentSort == SORTS.length - 1) {
                Toast.makeText(this, "Data use belum tersedia di perangkat ini", Toast.LENGTH_SHORT).show();
            }
            applyFilters();
        });

        final List<String> categories = apps == null
                ? Collections.singletonList("Semua") : apps.allCategories();
        categorySpinner = sheet.findViewById(R.id.appMgrCategory);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        int categoryPosition = categories.indexOf(selectedCategory);
        if (categoryPosition >= 0) categorySpinner.setSelection(categoryPosition);
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = categories.get(position);
                applyFilters();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        dialog.show();
    }

    private int filterOf(int checkedId) {
        if (checkedId == R.id.appMgrChipUser) return F_USER;
        if (checkedId == R.id.appMgrChipRunning) return F_RUNNING;
        if (checkedId == R.id.appMgrChipSystem) return F_SYSTEM;
        if (checkedId == R.id.appMgrChip32) return F_32;
        if (checkedId == R.id.appMgrChip64) return F_64;
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
            case 6: return R.id.appMgrSortData;
            default: return R.id.appMgrSortInstall;
        }
    }

    private int sortIndexOf(int chipId) {
        if (chipId == R.id.appMgrSortName) return 1;
        if (chipId == R.id.appMgrSortSize) return 2;
        if (chipId == R.id.appMgrSortOpened) return 3;
        if (chipId == R.id.appMgrSortUsed) return 4;
        if (chipId == R.id.appMgrSortScreen) return 5;
        if (chipId == R.id.appMgrSortData) return 6;
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
