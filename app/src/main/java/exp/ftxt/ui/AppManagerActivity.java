package exp.ftxt.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
    private ChipGroup filters;
    private Spinner sortSpinner;
    private Spinner categorySpinner;
    private TextView countText;
    private LinearLayout actionBar;
    private ProgressBar loading;
    private long filterVersion;
    private boolean resumedOnce;

    public static void start(android.content.Context context) {
        context.startActivity(new Intent(context, AppManagerActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_manager);

        InstalledApps.init(getApplicationContext());
        apps = InstalledApps.get();

        search = findViewById(R.id.appMgrSearch);
        filters = findViewById(R.id.appMgrFilters);
        sortSpinner = findViewById(R.id.appMgrSort);
        categorySpinner = findViewById(R.id.appMgrCategory);
        countText = findViewById(R.id.appMgrSelectedCount);
        actionBar = findViewById(R.id.appMgrActionBar);
        loading = findViewById(R.id.appMgrLoading);

        findViewById(R.id.appMgrBack).setOnClickListener(v -> finish());

        setupSpinners();
        setupSearch();
        setupFilters();

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

        findViewById(R.id.appMgrActionCancel).setOnClickListener(v -> adapter.clearSelection());
        findViewById(R.id.appMgrActionUninstall).setOnClickListener(v -> confirmUninstall());
        findViewById(R.id.appMgrActionShare).setOnClickListener(v -> shareSelected());

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce) loadApps();
        resumedOnce = true;
    }

    private void setupSpinners() {
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SORTS);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                if (position == SORTS.length - 1) {
                    Toast.makeText(AppManagerActivity.this,
                            "Data use belum tersedia di perangkat ini", Toast.LENGTH_SHORT).show();
                }
                applyFilters();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        setCategoryAdapter();
    }

    private void setCategoryAdapter() {
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                apps == null ? new String[]{"Semua"} : apps.allCategories());
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void setupSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilters();
            }
        });
    }

    private void setupFilters() {
        filters.setOnCheckedChangeListener((group, checkedId) -> applyFilters());
    }

    private void loadApps() {
        loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            final List<AppInfo> refreshed = apps.refresh();
            uiHandler.post(() -> {
                loading.setVisibility(View.GONE);
                setCategoryAdapter();
                adapter.setApps(refreshed);
                applyFilters();
            });
        });
    }

    private void applyFilters() {
        final long version = ++filterVersion;
        final String query = search.getText().toString().trim().toLowerCase(Locale.US);
        final int filter = filterOf(filters.getCheckedChipId());
        final int sort = sortSpinner.getSelectedItemPosition();
        final Object categoryObj = categorySpinner.getSelectedItem();
        final String category = categoryObj == null ? "Semua" : categoryObj.toString();

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
                countText.setText(adapter.getSelected().size() + " dipilih");
            });
        });
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
