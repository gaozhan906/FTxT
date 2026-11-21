package exp.ftxt.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import rikka.shizuku.Shizuku;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import exp.ftxt.R;
import exp.ftxt.core.appmanager.AppActions;
import exp.ftxt.core.appmanager.AppDetails;
import exp.ftxt.core.appmanager.AppInfo;
import exp.ftxt.core.appmanager.InstalledApps;
import exp.ftxt.features.gamefps.reader.ShizukuHelper;
import exp.ftxt.features.gamefps.reader.ShizukuShell;

public class AppDetailActivity extends AppCompatActivity {
    private static final String EXTRA_PACKAGE = "appManagerPackage";
    private static final int SHIZUKU_REQUEST = 20002;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private DetailPagerAdapter pagerAdapter;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_REQUEST && pagerAdapter != null) {
                    pagerAdapter.notifyDataSetChanged();
                }
            };

    private String packageName;
    private AppInfo app;
    private AppDetails.Result details;
    private int primaryColor;

    public static void start(android.content.Context context, String packageName) {
        Intent intent = new Intent(context, AppDetailActivity.class);
        intent.putExtra(EXTRA_PACKAGE, packageName);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_app_detail);
        setupWindowInsets();

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (packageName == null) {
            finish();
            return;
        }

        android.content.res.TypedArray ta = obtainStyledAttributes(
                new int[]{android.R.attr.textColorPrimary});
        primaryColor = ta.getColor(0, android.graphics.Color.BLACK);
        ta.recycle();

        findViewById(R.id.appDetailBack).setOnClickListener(v -> finish());

        pagerAdapter = new DetailPagerAdapter();
        androidx.viewpager2.widget.ViewPager2 pager = findViewById(R.id.appDetailPager);
        pager.setAdapter(pagerAdapter);
        TabLayout tabs = findViewById(R.id.appDetailTabs);
        new TabLayoutMediator(tabs, pager, (tab, position) ->
                tab.setText(pagerAdapter.getPageTitle(position))).attach();

        bindActions();
        load();
    }

    private void setupWindowInsets() {
        final View root = findViewById(R.id.appDetailRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
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
        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onPause() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener);
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    private void load() {
        executor.execute(() -> {
            InstalledApps.init(getApplicationContext());
            InstalledApps registry = InstalledApps.get();
            AppInfo found = registry.find(packageName);
            if (found == null) {
                registry.refresh();
                found = registry.find(packageName);
            }
            final AppInfo resolved = found;
            final AppDetails.Result result =
                    AppDetails.load(getApplicationContext(), packageName);
            uiHandler.post(() -> {
                app = resolved;
                details = result;
                bindHeader();
                pagerAdapter.notifyDataSetChanged();
            });
        });
    }

    private void bindHeader() {
        if (app == null) return;
        ((android.widget.ImageView) findViewById(R.id.appDetailIcon)).setImageDrawable(app.icon);
        ((TextView) findViewById(R.id.appDetailName)).setText(app.label);
        ((TextView) findViewById(R.id.appDetailPkg)).setText(app.packageName);
    }

    private void bindActions() {
        findViewById(R.id.appDetailOpen).setOnClickListener(v -> {
            if (app == null) return;
            Intent intent = AppActions.open(this, app);
            if (intent == null) {
                Toast.makeText(this, "Aplikasi tidak memiliki launcher", Toast.LENGTH_SHORT).show();
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        findViewById(R.id.appDetailInfo).setOnClickListener(v -> {
            if (app == null) return;
            startActivity(AppActions.settings(app));
        });
    }

    private void confirmUninstallApp() {
        if (app == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Hapus aplikasi")
                .setMessage("Hapus " + app.label + "?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) ->
                        startActivity(AppActions.uninstall(app)))
                .show();
    }

    private void shareApp() {
        if (app == null) return;
        executor.execute(() -> {
            java.util.List<AppInfo> list = new java.util.ArrayList<>();
            list.add(app);
            final List<android.net.Uri> uris =
                    AppShareHelper.prepare(getApplicationContext(), list);
            uiHandler.post(() -> {
                if (uris.isEmpty()) {
                    Toast.makeText(this, "APK tidak dapat disiapkan", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(AppShareHelper.sendIntent(this, uris));
            });
        });
    }

    private void forceStopWithShizuku() {
        if (!ShizukuHelper.isBinderAlive()) {
            Toast.makeText(this, "Shizuku belum aktif", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ShizukuHelper.hasPermission()) {
            ShizukuHelper.requestPermission(SHIZUKU_REQUEST);
            return;
        }
        final String pkg = packageName;
        executor.execute(() -> {
            final ShizukuShell.Result result = ShizukuShell.execArgs(
                    new String[]{"am", "force-stop", pkg}, 5000L);
            uiHandler.post(() -> Toast.makeText(this,
                    result != null && result.ok ? "Force stop berhasil" : "Force stop gagal",
                    Toast.LENGTH_SHORT).show());
        });
    }

    private class DetailPagerAdapter extends RecyclerView.Adapter<DetailPagerAdapter.PageHolder> {
        private final String[] tabs = {
                "Info", "Ukuran", "Izin", "Akses Khusus", "Lanjutan", "Pemakaian"
        };

        String getPageTitle(int position) {
            return tabs[position];
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_detail_page, parent, false);
            return new PageHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.container.removeAllViews();
            renderPage(holder.container, position);
        }

        @Override
        public int getItemCount() {
            return tabs.length;
        }

        class PageHolder extends RecyclerView.ViewHolder {
            final LinearLayout container;

            PageHolder(@NonNull View itemView) {
                super(itemView);
                container = itemView.findViewById(R.id.detailPageContainer);
            }
        }
    }

    private void renderPage(LinearLayout container, int page) {
        switch (page) {
            case 0:
                renderInfo(container);
                break;
            case 1:
                renderSize(container);
                break;
            case 2:
                renderPermissions(container);
                break;
            case 3:
                renderSpecialAccess(container);
                break;
            case 4:
                renderAdvanced(container);
                break;
            default:
                renderUsage(container);
                break;
        }
    }

    private void renderInfo(LinearLayout container) {
        if (app == null) return;
        addHeader(container, "Identitas");
        addLine(container, "Nama: " + app.label);
        addLine(container, "Package: " + app.packageName);
        addLine(container, "Versi: " + app.versionName + " (" + app.versionCode + ")");
        addLine(container, "Jenis: " + (app.system ? "Sistem" : "User")
                + (app.updatedSystem ? " (system ter-update)" : ""));
        addLine(container, "Status: " + (app.enabled ? "Aktif" : "Nonaktif"));
        addLine(container, "Launcher: " + (app.launcher ? "ya" : "tidak"));
        addLine(container, "Kategori: " + app.category);
        addLine(container, "Sumber: " + app.installer);

        addHeader(container, "Waktu & SDK");
        addLine(container, "Dipasang: " + date(app.firstInstallTime));
        addLine(container, "Diperbarui: " + date(app.lastUpdateTime));
        addLine(container, "Target SDK: " + app.targetSdk);
        addLine(container, "Min SDK: " + app.minSdk);
        addLine(container, "ABI: " + app.abi);

        addHeader(container, "Proses");
        addLine(container, "UID: " + app.uid);
        addLine(container, "Process: " + app.processName);
        addLine(container, "APK: " + app.sourceDir);
        addLine(container, "Data: " + app.dataDir);

        addHeader(container, "Aksi");
        addActionRow(container, "Bagikan APK", v -> shareApp());
        addActionRow(container, "Hapus aplikasi", v -> confirmUninstallApp());
        addActionRow(container, shizukuActionLabel(), v -> forceStopWithShizuku());
    }

    private void renderSize(LinearLayout container) {
        if (details == null) return;
        if (!details.sizeAvailable) {
            addLine(container, "Ukuran tidak tersedia untuk aplikasi ini.");
            return;
        }
        addHeader(container, "Penyimpanan");
        addLine(container, "APK: " + size(details.codeBytes));
        addLine(container, "Data: " + size(details.dataBytes));
        addLine(container, "Cache: " + size(details.cacheBytes));
        addLine(container, "Total: " + size(details.totalBytes));
    }

    private void renderPermissions(LinearLayout container) {
        if (details == null) return;
        addHeader(container, "Izin (" + details.permissions.size() + ")");
        if (details.permissions.isEmpty()) {
            addLine(container, "Tidak ada izin yang diminta.");
            return;
        }
        for (AppDetails.Perm perm : details.permissions) {
            addLine(container, (perm.granted ? "✓ " : "✗ ") + perm.label
                    + " · " + perm.protection + " · " + perm.name);
        }
    }

    private void renderSpecialAccess(LinearLayout container) {
        if (details == null) return;
        addHeader(container, "Akses khusus");
        for (final AppDetails.Access access : details.specialAccess) {
            TextView row = new TextView(this);
            row.setText("› " + access.label);
            row.setTextSize(14);
            row.setTextColor(getColor(R.color.appmgr_accent));
            row.setPadding(0, dp(8), 0, dp(8));
            row.setOnClickListener(v -> {
                try {
                    startActivity(AppActions.specialAccess(access.action, packageName));
                } catch (Throwable t) {
                    Toast.makeText(this, "Halaman Settings tidak tersedia", Toast.LENGTH_SHORT).show();
                }
            });
            container.addView(row);
        }
    }

    private void renderAdvanced(LinearLayout container) {
        if (details == null) return;
        addHeader(container, "Komponen");
        addLine(container, "Activity: " + details.activities.size());
        addNames(container, details.activities);
        addLine(container, "Service: " + details.services.size());
        addNames(container, details.services);
        addLine(container, "Receiver: " + details.receivers.size());
        addNames(container, details.receivers);
        addLine(container, "Provider: " + details.providers.size());
        addNames(container, details.providers);

        addHeader(container, "Sertifikat & grafis");
        addLine(container, "SHA-256: " + (details.certificateSha256 == null
                ? "—" : details.certificateSha256));
        addLine(container, details.graphics);
    }

    private void renderUsage(LinearLayout container) {
        if (details == null) return;
        addHeader(container, "Pemakaian");
        addLine(container, "Berjalan akhir-akhir ini: " + (details.running ? "ya" : "tidak"));
        addLine(container, "Terakhir dipakai: " + date(details.lastTimeUsed));
        addLine(container, "Total foreground: " + appTime(details.foregroundTimeMs));
        addLine(container, "Kali diluncurkan: " + details.launchCount);
    }

    private void addNames(LinearLayout container, List<String> names) {
        int limit = Math.min(12, names.size());
        for (int i = 0; i < limit; i++) {
            addLine(container, "  · " + names.get(i));
        }
        if (names.size() > limit) {
            addLine(container, "  · … dan " + (names.size() - limit) + " lainnya");
        }
    }

    private void addHeader(LinearLayout container, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(getColor(R.color.appmgr_on_surface));
        view.setPadding(0, dp(16), 0, dp(6));
        container.addView(view);
    }

    private void addLine(LinearLayout container, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(primaryColor);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(0, dp(3), 0, dp(3));
        container.addView(view);
    }

    private void addActionRow(LinearLayout container, String text, View.OnClickListener listener) {
        TextView view = new TextView(this);
        view.setText("› " + text);
        view.setTextSize(14);
        view.setTextColor(getColor(R.color.appmgr_accent));
        view.setPadding(0, dp(8), 0, dp(8));
        view.setOnClickListener(listener);
        container.addView(view);
    }

    private String shizukuActionLabel() {
        if (!ShizukuHelper.isBinderAlive()) return "Shizuku tidak aktif";
        return ShizukuHelper.hasPermission()
                ? "Paksa berhenti (Shizuku)" : "Izin Shizuku";
    }

    private String date(long millis) {
        if (millis <= 0) return "—";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date(millis));
    }

    private String size(long bytes) {
        return Formatter.formatFileSize(this, bytes);
    }

    private String appTime(long millis) {
        return app == null ? "—" : app.timeText(millis);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
