package exp.ftxt.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import exp.ftxt.MainActivity;
import exp.ftxt.R;
import exp.ftxt.features.network_stats.usage.NetworkUsageSource;
import exp.ftxt.features.network_stats.usage.UsageApp;
import exp.ftxt.features.network_stats.usage.UsageBucket;
import exp.ftxt.features.network_stats.usage.UsageChartView;
import exp.ftxt.features.network_stats.usage.UsageDay;
import exp.ftxt.features.network_stats.usage.UsageNetwork;
import exp.ftxt.features.network_stats.usage.UsagePeriod;
import exp.ftxt.features.network_stats.usage.UsageQuery;
import exp.ftxt.features.network_stats.usage.UsageSnapshot;
import exp.ftxt.features.network_stats.usage.UsageSource;
import exp.ftxt.ui.widget.UsageDonutView;
import exp.ftxt.utils.PermissionHelper;

public class NetworkUsageController {

    private static final String TAG = "NetworkUsage";
    private static final DateTimeFormatter RANGE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("id", "ID"));
    private static final DateTimeFormatter SHORT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM", new Locale("id", "ID"));
    private static final DateTimeFormatter FULL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("id", "ID"));

    private final MainActivity activity;
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();
    private final int usageTabActiveColor;
    private final int usageTabInactiveColor;
    private final int donutOtherColor;
    private final int[] donutColors = new int[7];

    private TextView networkUsageRange;
    private TextView networkUsageTotal;
    private TextView networkUsageRx;
    private TextView networkUsageTx;
    private TextView networkUsageAverage;
    private TextView networkUsagePeak;
    private TextView networkUsagePartialNote;
    private TextView networkUsageChartTitle;
    private TextView networkUsageErrorText;
    private TextView networkUsagePeriodFilter;
    private TextView networkUsageNetworkFilter;
    private TextView networkSubTabUsage;
    private TextView networkSubTabTools;
    private TextView networkUsageHistoryToggle;
    private TextView networkUsageAppsTitle;
    private TextView networkUsageAppsEmpty;
    private UsageChartView networkUsageChart;
    private RecyclerView networkUsageAppsList;
    private UsageAppAdapter networkUsageAppsAdapter;
    private View networkUsageContent;
    private View networkUsageLoading;
    private View networkUsagePermission;
    private View networkUsageError;
    private View networkUsageTabUsage;
    private View networkUsageTabTools;
    private View networkUsageDonutCard;

    private final List<UsageHistoryAdapter.Item> allHistoryItems = new ArrayList<>();
    private Map<String, AppInfo> currentAppInfos = Collections.emptyMap();

    private UsagePeriod selectedPeriod = UsagePeriod.TODAY;
    private UsageNetwork selectedNetwork = UsageNetwork.ALL;
    private int requestId;
    private boolean destroyed;
    private boolean panelShown;
    private BottomSheetDialog daySheetDialog;
    private BottomSheetDialog historySheetDialog;
    private PopupMenu filterPopup;

    public NetworkUsageController(MainActivity activity, View rootView) {
        this.activity = activity;
        usageTabActiveColor = activity.getColor(R.color.usage_accent);
        usageTabInactiveColor = activity.getColor(R.color.usage_filter_unselected_text);
        donutOtherColor = activity.getColor(R.color.usage_donut_other);
        donutColors[0] = activity.getColor(R.color.usage_donut_1);
        donutColors[1] = activity.getColor(R.color.usage_donut_2);
        donutColors[2] = activity.getColor(R.color.usage_donut_3);
        donutColors[3] = activity.getColor(R.color.usage_donut_4);
        donutColors[4] = activity.getColor(R.color.usage_donut_5);
        donutColors[5] = activity.getColor(R.color.usage_donut_6);
        donutColors[6] = activity.getColor(R.color.usage_donut_7);
        bindViews(rootView);
        setupLists();
        setupUsageTabs();
        setupFilters();
        updateFilterState();
    }

    public void onPanelShown() {
        panelShown = true;
        reloadUsage();
    }

    public void onPanelHidden() {
        panelShown = false;
        requestId++;
        dismissSheets();
    }

    public void refresh() {
        if (panelShown) reloadUsage();
    }

    public void cleanup() {
        destroyed = true;
        requestId++;
        dismissSheets();
        queryExecutor.shutdownNow();
    }

    private void bindViews(View rootView) {
        networkUsageRange = rootView.findViewById(R.id.networkUsageRange);
        networkUsageTotal = rootView.findViewById(R.id.networkUsageTotal);
        networkUsageRx = rootView.findViewById(R.id.networkUsageRx);
        networkUsageTx = rootView.findViewById(R.id.networkUsageTx);
        networkUsageAverage = rootView.findViewById(R.id.networkUsageAverage);
        networkUsagePeak = rootView.findViewById(R.id.networkUsagePeak);
        networkUsagePartialNote = rootView.findViewById(R.id.networkUsagePartialNote);
        networkUsageChartTitle = rootView.findViewById(R.id.networkUsageChartTitle);
        networkUsageErrorText = rootView.findViewById(R.id.networkUsageErrorText);
        networkUsagePeriodFilter = rootView.findViewById(R.id.networkUsagePeriodFilter);
        networkUsageNetworkFilter = rootView.findViewById(R.id.networkUsageNetworkFilter);
        networkSubTabUsage = rootView.findViewById(R.id.networkSubTabUsage);
        networkSubTabTools = rootView.findViewById(R.id.networkSubTabTools);
        networkUsageHistoryToggle = rootView.findViewById(R.id.networkUsageHistoryToggle);
        networkUsageAppsTitle = rootView.findViewById(R.id.networkUsageAppsTitle);
        networkUsageAppsEmpty = rootView.findViewById(R.id.networkUsageAppsEmpty);
        networkUsageChart = rootView.findViewById(R.id.networkUsageChart);
        networkUsageAppsList = rootView.findViewById(R.id.networkUsageAppsList);
        networkUsageContent = rootView.findViewById(R.id.networkUsageContent);
        networkUsageLoading = rootView.findViewById(R.id.networkUsageLoading);
        networkUsagePermission = rootView.findViewById(R.id.networkUsagePermission);
        networkUsageError = rootView.findViewById(R.id.networkUsageError);
        networkUsageTabUsage = rootView.findViewById(R.id.networkUsageTabUsage);
        networkUsageTabTools = rootView.findViewById(R.id.networkUsageTabTools);
        networkUsageDonutCard = rootView.findViewById(R.id.networkUsageDonutCard);

        View permissionButton = rootView.findViewById(R.id.networkUsagePermissionButton);
        permissionButton.setOnClickListener(v -> PermissionHelper.requestUsageAccess(activity));

        View retryButton = rootView.findViewById(R.id.networkUsageRetry);
        retryButton.setOnClickListener(v -> reloadUsage());
    }

    private void setupLists() {
        networkUsageAppsAdapter = new UsageAppAdapter();
        networkUsageAppsList.setLayoutManager(new LinearLayoutManager(activity));
        networkUsageAppsList.setAdapter(networkUsageAppsAdapter);

        networkUsageHistoryToggle.setOnClickListener(v -> showHistorySheet());
    }

    private void setupUsageTabs() {
        networkSubTabUsage.setOnClickListener(v -> selectUsageTab(0));
        networkSubTabTools.setOnClickListener(v -> selectUsageTab(1));
        selectUsageTab(0);
    }

    private void selectUsageTab(int index) {
        networkUsageTabUsage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        networkUsageTabTools.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        applyUsageTabState(networkSubTabUsage, index == 0);
        applyUsageTabState(networkSubTabTools, index == 1);
        if (index == 0 && panelShown) reloadUsage();
    }

    private void applyUsageTabState(TextView tv, boolean active) {
        tv.setTextColor(active ? usageTabActiveColor : usageTabInactiveColor);
        tv.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void setupFilters() {
        networkUsagePeriodFilter.setOnClickListener(v -> showFilterPopup(true));
        networkUsageNetworkFilter.setOnClickListener(v -> showFilterPopup(false));
    }

    private void selectPeriod(UsagePeriod period) {
        if (selectedPeriod == period) return;
        selectedPeriod = period;
        updateFilterState();
        reloadUsage();
    }

    private void selectNetwork(UsageNetwork network) {
        if (selectedNetwork == network) return;
        selectedNetwork = network;
        updateFilterState();
        reloadUsage();
    }

    private void updateFilterState() {
        networkUsagePeriodFilter.setText(periodFilterLabel(selectedPeriod));
        networkUsageNetworkFilter.setText(formatNetworkLabel(selectedNetwork));
    }

    private String periodFilterLabel(UsagePeriod period) {
        if (period == UsagePeriod.DAYS_7) return "7 hari";
        if (period == UsagePeriod.DAYS_30) return "30 hari";
        return "Hari ini";
    }

    private void showFilterPopup(boolean period) {
        if (destroyed || activity.isFinishing()) return;

        dismissFilterPopup();
        TextView anchor = period ? networkUsagePeriodFilter : networkUsageNetworkFilter;
        PopupMenu popup = new PopupMenu(activity, anchor);
        filterPopup = popup;
        popup.setOnDismissListener(p -> {
            if (filterPopup == popup) filterPopup = null;
        });
        int group = period ? 0 : 1;
        if (period) {
            addPopupItem(popup, group, 0, "Hari ini", selectedPeriod == UsagePeriod.TODAY);
            addPopupItem(popup, group, 1, "7 hari", selectedPeriod == UsagePeriod.DAYS_7);
            addPopupItem(popup, group, 2, "30 hari", selectedPeriod == UsagePeriod.DAYS_30);
        } else {
            addPopupItem(popup, group, 0, "Semua", selectedNetwork == UsageNetwork.ALL);
            addPopupItem(popup, group, 1, "WiFi", selectedNetwork == UsageNetwork.WIFI);
            addPopupItem(popup, group, 2, "Seluler", selectedNetwork == UsageNetwork.MOBILE);
        }
        popup.getMenu().setGroupCheckable(group, true, true);
        popup.setOnMenuItemClickListener(item -> {
            if (period) {
                if (item.getItemId() == 0) selectPeriod(UsagePeriod.TODAY);
                else if (item.getItemId() == 1) selectPeriod(UsagePeriod.DAYS_7);
                else selectPeriod(UsagePeriod.DAYS_30);
            } else {
                if (item.getItemId() == 0) selectNetwork(UsageNetwork.ALL);
                else if (item.getItemId() == 1) selectNetwork(UsageNetwork.WIFI);
                else selectNetwork(UsageNetwork.MOBILE);
            }
            return true;
        });
        popup.show();
    }

    private void addPopupItem(PopupMenu popup, int group, int id, String label, boolean checked) {
        MenuItem item = popup.getMenu().add(group, id, id, label);
        item.setCheckable(true);
        item.setChecked(checked);
    }

    private void reloadUsage() {
        if (destroyed) return;

        dismissSheets();
        final int request = ++requestId;
        if (!PermissionHelper.hasUsageAccess(activity)) {
            showPermission();
            return;
        }

        showLoading();
        final UsageQuery query = new UsageQuery(selectedPeriod, selectedNetwork);
        try {
            queryExecutor.execute(() -> {
                UsageSnapshot snapshot;
                PreparedSnapshot prepared;
                try {
                    snapshot = NetworkUsageSource.query(activity.getApplicationContext(), query);
                    prepared = prepareSnapshot(snapshot);
                } catch (Exception e) {
                    Log.e(TAG, "Gagal query pemakaian", e);
                    activity.runOnUiThread(() -> {
                        if (!destroyed && request == requestId) showError();
                    });
                    return;
                }

                activity.runOnUiThread(() -> {
                    if (!destroyed && request == requestId) renderSnapshot(prepared);
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "Gagal menjalankan query pemakaian", e);
            showError();
        }
    }

    private void showState(View target) {
        networkUsageContent.setVisibility(target == networkUsageContent ? View.VISIBLE : View.GONE);
        networkUsageLoading.setVisibility(target == networkUsageLoading ? View.VISIBLE : View.GONE);
        networkUsagePermission.setVisibility(target == networkUsagePermission ? View.VISIBLE : View.GONE);
        networkUsageError.setVisibility(target == networkUsageError ? View.VISIBLE : View.GONE);
    }

    private void showLoading() {
        networkUsageHistoryToggle.setVisibility(View.GONE);
        showState(networkUsageLoading);
    }

    private void showPermission() {
        networkUsageHistoryToggle.setVisibility(View.GONE);
        showState(networkUsagePermission);
    }

    private void showError() {
        networkUsageHistoryToggle.setVisibility(View.GONE);
        networkUsageErrorText.setText("Gagal memuat data pemakaian. Coba lagi.");
        showState(networkUsageError);
    }

    private void renderSnapshot(PreparedSnapshot prepared) {
        UsageSnapshot snapshot = prepared.snapshot;
        currentAppInfos = prepared.appInfos;
        networkUsageRange.setText(formatRange(snapshot));
        showState(networkUsageContent);
        networkUsageTotal.setText(formatBytes(snapshot.totalBytes()));
        networkUsageRx.setText("↓ " + formatBytes(snapshot.totalRxBytes));
        networkUsageTx.setText("↑ " + formatBytes(snapshot.totalTxBytes));

        String averageLabel = snapshot.query.period == UsagePeriod.TODAY
                ? "Rata-rata/jam" : "Rata-rata/hari";
        if (!snapshot.averageAvailable) {
            networkUsageAverage.setText(averageLabel + " —");
        } else {
            networkUsageAverage.setText(averageLabel + " "
                    + formatBytes(snapshot.averageRxBytes + snapshot.averageTxBytes));
        }

        networkUsagePeak.setText(formatPeak(snapshot));
        updatePartialNote(snapshot);
        networkUsageChartTitle.setText("Tren pemakaian · " + formatPeriodLabel(snapshot.query.period)
                + " · " + formatNetworkLabel(snapshot.query.network));
        networkUsageChart.setData(snapshot.buckets, snapshot.query.period, snapshot.query.zone);
        networkUsageChart.setContentDescription(buildChartDescription(snapshot));
        renderHistory(snapshot);
        renderDonut(prepared);
        renderApps(prepared);
    }

    private void updatePartialNote(UsageSnapshot snapshot) {
        String note = null;
        if (snapshot.mobileUnavailable) {
            note = "Data seluler tidak tersedia di perangkat ini.";
        }
        if (snapshot.source == UsageSource.CACHE || snapshot.source == UsageSource.MIXED) {
            String cacheNote = "Sebagian data berasal dari riwayat lokal.";
            note = note == null ? cacheNote : note + " " + cacheNote;
        } else if (snapshot.partial && note == null) {
            note = "Sebagian data tidak tersedia.";
        }

        if (note == null) {
            networkUsagePartialNote.setVisibility(View.GONE);
        } else {
            networkUsagePartialNote.setText(note);
            networkUsagePartialNote.setVisibility(View.VISIBLE);
        }
    }

    private void renderHistory(UsageSnapshot snapshot) {
        boolean show = snapshot.query.period != UsagePeriod.TODAY;
        networkUsageHistoryToggle.setVisibility(show ? View.VISIBLE : View.GONE);
        allHistoryItems.clear();

        if (!show) return;

        long periodTotal = snapshot.totalBytes();
        List<UsageDay> days = new ArrayList<>(snapshot.days);
        days.sort((a, b) -> b.date.compareTo(a.date));
        for (UsageDay day : days) {
            allHistoryItems.add(buildHistoryItem(day, periodTotal));
        }
    }

    private UsageHistoryAdapter.Item buildHistoryItem(UsageDay day, long periodTotal) {
        String date = formatDayLabel(day.date);
        String networks;
        String total;
        String percent;
        if (!day.hasData) {
            networks = "WiFi — · Seluler —";
            total = "Tidak ada data";
            percent = "—";
        } else {
            networks = "WiFi " + formatBytes(day.wifiTotalBytes())
                    + " · Seluler " + formatBytes(day.mobileTotalBytes());
            total = formatBytes(day.totalBytes());
            percent = formatPercent(day.totalBytes(), periodTotal);
        }
        return new UsageHistoryAdapter.Item(
                day, date, networks, total, percent, historySourceLabel(day.source));
    }

    private void showHistorySheet() {
        if (destroyed || activity.isFinishing()) return;
        dismissSheets();

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View sheet = LayoutInflater.from(activity).inflate(R.layout.sheet_usage_history, null);
        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> {
            if (historySheetDialog == dialog) historySheetDialog = null;
        });
        historySheetDialog = dialog;

        TextView title = sheet.findViewById(R.id.usageHistorySheetTitle);
        title.setText("Riwayat harian · " + formatPeriodLabel(selectedPeriod));

        RecyclerView list = sheet.findViewById(R.id.usageHistorySheetList);
        TextView empty = sheet.findViewById(R.id.usageHistorySheetEmpty);
        UsageHistoryAdapter adapter = new UsageHistoryAdapter(day -> {
            dismissHistorySheet();
            showDaySheet(day);
        });
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(adapter);
        adapter.submit(new ArrayList<>(allHistoryItems));

        boolean emptyList = allHistoryItems.isEmpty();
        list.setVisibility(emptyList ? View.GONE : View.VISIBLE);
        empty.setVisibility(emptyList ? View.VISIBLE : View.GONE);

        dialog.show();
    }

    private void renderDonut(PreparedSnapshot prepared) {
        UsageSnapshot snapshot = prepared.snapshot;
        List<DonutEntry> entries = buildDonutEntries(
                snapshot.apps, snapshot.totalBytes(), prepared.appInfos);
        String title = "Komposisi · " + formatPeriodLabel(snapshot.query.period)
                + " · " + formatNetworkLabel(snapshot.query.network);
        bindDonut(networkUsageDonutCard, entries, snapshot.totalBytes(), title);
    }

    private List<DonutEntry> buildDonutEntries(List<UsageApp> source, long total,
                                               Map<String, AppInfo> infos) {
        List<DonutEntry> entries = new ArrayList<>();
        if (source == null || source.isEmpty() || total <= 0) return entries;

        List<UsageApp> apps = new ArrayList<>(source);
        apps.sort((a, b) -> Long.compare(b.totalBytes(), a.totalBytes()));
        int shown = Math.min(7, apps.size());
        long topTotal = 0L;
        for (int i = 0; i < shown; i++) {
            UsageApp app = apps.get(i);
            long appTotal = app.totalBytes();
            AppInfo info = appInfo(infos, app);
            entries.add(new DonutEntry(info.label, info.icon, appTotal,
                    donutColors[i % donutColors.length]));
            topTotal += appTotal;
        }

        long others = 0L;
        for (int i = shown; i < apps.size(); i++) {
            others += apps.get(i).totalBytes();
        }
        long accounted = topTotal + others;
        if (total > accounted) others += total - accounted;
        if (others > 0) {
            entries.add(new DonutEntry("Lainnya", null, others, donutOtherColor));
        }
        return entries;
    }

    private void bindDonut(View card, List<DonutEntry> entries, long total, String title) {
        if (card == null) return;
        card.setVisibility(View.VISIBLE);

        TextView titleView = card.findViewById(R.id.usageDonutTitle);
        titleView.setText(title);

        UsageDonutView donut = card.findViewById(R.id.usageDonut);
        List<UsageDonutView.Slice> slices = new ArrayList<>();
        for (DonutEntry entry : entries) {
            float fraction = Math.min(1f, entry.totalBytes / (float) total);
            slices.add(new UsageDonutView.Slice(fraction, entry.color));
        }
        donut.setData(slices, formatBytes(total), "total");
        donut.setContentDescription(buildDonutDescription(title, entries, total));

        LinearLayout legend = card.findViewById(R.id.usageDonutLegend);
        legend.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (DonutEntry entry : entries) {
            View row = inflater.inflate(R.layout.item_usage_legend, legend, false);
            View dot = row.findViewById(R.id.itemUsageLegendDot);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(entry.color);
            dot.setBackground(oval);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            ImageView icon = row.findViewById(R.id.itemUsageLegendIcon);
            if (entry.icon == null) {
                icon.setVisibility(View.GONE);
            } else {
                icon.setImageDrawable(entry.icon);
            }
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            TextView name = row.findViewById(R.id.itemUsageLegendName);
            TextView percent = row.findViewById(R.id.itemUsageLegendPercent);
            TextView size = row.findViewById(R.id.itemUsageLegendSize);
            String percentText = formatPercent(entry.totalBytes, total);
            String sizeText = formatBytes(entry.totalBytes);
            name.setText(entry.label);
            percent.setText(percentText);
            size.setText(sizeText);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            percent.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            size.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.setFocusable(true);
            row.setContentDescription(entry.label + ", " + percentText + ", " + sizeText);
            legend.addView(row);
        }
    }

    private String buildChartDescription(UsageSnapshot snapshot) {
        return "Grafik pemakaian " + formatPeriodLabel(snapshot.query.period)
                + ", jaringan " + formatNetworkLabel(snapshot.query.network)
                + ". Total " + formatBytes(snapshot.totalBytes())
                + ". " + formatPeak(snapshot) + ".";
    }

    private String buildDonutDescription(String title, List<DonutEntry> entries, long total) {
        StringBuilder builder = new StringBuilder(title)
                .append(". Total ").append(formatBytes(total)).append(". ");
        int shown = Math.min(4, entries.size());
        for (int i = 0; i < shown; i++) {
            DonutEntry entry = entries.get(i);
            builder.append(entry.label).append(' ')
                    .append(formatPercent(entry.totalBytes, total)).append(", ")
                    .append(formatBytes(entry.totalBytes)).append(". ");
        }
        if (entries.size() > shown) {
            builder.append("Dan ").append(entries.size() - shown).append(" bagian lain.");
        }
        return builder.toString();
    }

    private void renderApps(PreparedSnapshot prepared) {
        UsageSnapshot snapshot = prepared.snapshot;
        long total = snapshot.totalBytes();
        List<UsageAppAdapter.Item> items = new ArrayList<>();
        List<UsageApp> apps = new ArrayList<>(snapshot.apps);
        apps.sort((a, b) -> Long.compare(b.totalBytes(), a.totalBytes()));
        for (UsageApp app : apps) {
            AppInfo info = appInfo(prepared.appInfos, app);
            String detail = "↓ " + formatBytes(app.rxBytes) + " · ↑ " + formatBytes(app.txBytes);
            float fraction = total > 0 ? (float) (app.totalBytes() / (double) total) : 0f;
            items.add(new UsageAppAdapter.Item(
                    info.label,
                    info.icon,
                    detail,
                    formatBytes(app.totalBytes()),
                    formatPercent(app.totalBytes(), total),
                    fraction));
        }

        boolean empty = items.isEmpty();
        networkUsageAppsAdapter.submit(items);
        networkUsageAppsTitle.setVisibility(View.VISIBLE);
        networkUsageAppsTitle.setText(empty ? "Aplikasi" : "Aplikasi (" + items.size() + ")");
        networkUsageAppsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        networkUsageAppsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showDaySheet(UsageDay day) {
        if (destroyed || activity.isFinishing()) return;
        dismissSheets();

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View sheet = LayoutInflater.from(activity).inflate(R.layout.sheet_usage_day, null);
        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> {
            if (daySheetDialog == dialog) daySheetDialog = null;
        });
        daySheetDialog = dialog;

        TextView date = sheet.findViewById(R.id.usageDaySheetDate);
        TextView source = sheet.findViewById(R.id.usageDaySheetSource);
        TextView total = sheet.findViewById(R.id.usageDaySheetTotal);
        TextView wifi = sheet.findViewById(R.id.usageDaySheetWifi);
        TextView mobile = sheet.findViewById(R.id.usageDaySheetMobile);
        TextView empty = sheet.findViewById(R.id.usageDaySheetEmpty);
        TextView appsTitle = sheet.findViewById(R.id.usageDaySheetAppsTitle);
        TextView appsEmpty = sheet.findViewById(R.id.usageDaySheetAppsEmpty);
        RecyclerView appsList = sheet.findViewById(R.id.usageDaySheetApps);
        View donutCard = sheet.findViewById(R.id.usageDayDonutCard);

        String dateLabel = formatFullDayLabel(day.date);
        date.setText(dateLabel);
        bindSourceNote(source, day.source);

        appsList.setLayoutManager(new LinearLayoutManager(activity));
        appsList.setAdapter(new UsageAppAdapter());
        appsList.setNestedScrollingEnabled(false);

        if (!day.hasData || day.totalBytes() <= 0) {
            total.setText("Tidak ada data");
            wifi.setText("WiFi —");
            mobile.setText("Seluler —");
            empty.setVisibility(View.VISIBLE);
            donutCard.setVisibility(View.GONE);
            appsTitle.setVisibility(View.GONE);
            appsList.setVisibility(View.GONE);
            appsEmpty.setVisibility(View.GONE);
        } else {
            total.setText(formatBytes(day.totalBytes()));
            wifi.setText("WiFi " + formatBytes(day.wifiTotalBytes()));
            mobile.setText("Seluler " + formatBytes(day.mobileTotalBytes()));
            empty.setVisibility(View.GONE);

            List<DonutEntry> entries = buildDonutEntries(
                    day.apps, day.totalBytes(), currentAppInfos);
            String donutTitle = "Komposisi · " + dateLabel
                    + " · " + formatNetworkLabel(selectedNetwork);
            bindDonut(donutCard, entries, day.totalBytes(), donutTitle);

            List<UsageAppAdapter.Item> items = new ArrayList<>();
            List<UsageApp> apps = new ArrayList<>(day.apps);
            apps.sort((a, b) -> Long.compare(b.totalBytes(), a.totalBytes()));
            for (UsageApp app : apps) {
                AppInfo info = appInfo(currentAppInfos, app);
                String detail = "↓ " + formatBytes(app.rxBytes)
                        + " · ↑ " + formatBytes(app.txBytes);
                float fraction = (float) (app.totalBytes() / (double) day.totalBytes());
                items.add(new UsageAppAdapter.Item(
                        info.label,
                        info.icon,
                        detail,
                        formatBytes(app.totalBytes()),
                        formatPercent(app.totalBytes(), day.totalBytes()),
                        fraction));
            }
            ((UsageAppAdapter) appsList.getAdapter()).submit(items);
            boolean emptyApps = items.isEmpty();
            appsTitle.setVisibility(View.VISIBLE);
            appsTitle.setText(emptyApps ? "Aplikasi" : "Aplikasi (" + items.size() + ")");
            appsList.setVisibility(emptyApps ? View.GONE : View.VISIBLE);
            appsEmpty.setVisibility(emptyApps ? View.VISIBLE : View.GONE);
        }

        dialog.show();
    }

    private void dismissSheets() {
        dismissFilterPopup();
        dismissDaySheet();
        dismissHistorySheet();
    }

    private void dismissFilterPopup() {
        if (filterPopup != null) {
            filterPopup.dismiss();
            filterPopup = null;
        }
    }

    private void dismissDaySheet() {
        if (daySheetDialog != null) {
            daySheetDialog.dismiss();
            daySheetDialog = null;
        }
    }

    private void dismissHistorySheet() {
        if (historySheetDialog != null) {
            historySheetDialog.dismiss();
            historySheetDialog = null;
        }
    }

    private void bindSourceNote(TextView view, UsageSource source) {
        if (source == UsageSource.CACHE) {
            view.setText("Data dari riwayat lokal (cache).");
            view.setVisibility(View.VISIBLE);
        } else if (source == UsageSource.MIXED) {
            view.setText("Sebagian data dari riwayat lokal (cache).");
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private String historySourceLabel(UsageSource source) {
        if (source == UsageSource.CACHE) return "cache";
        if (source == UsageSource.MIXED) return "sebagian cache";
        return null;
    }

    private PreparedSnapshot prepareSnapshot(UsageSnapshot snapshot) {
        Map<String, AppInfo> infos = new HashMap<>();
        for (UsageApp app : snapshot.apps) {
            addAppInfo(app, infos);
        }
        for (UsageDay day : snapshot.days) {
            for (UsageApp app : day.apps) {
                addAppInfo(app, infos);
            }
        }
        return new PreparedSnapshot(snapshot, infos);
    }

    private void addAppInfo(UsageApp app, Map<String, AppInfo> infos) {
        String key = appKey(app);
        if (infos.containsKey(key)) return;
        infos.put(key, resolveAppInfo(app));
    }

    private AppInfo appInfo(Map<String, AppInfo> infos, UsageApp app) {
        AppInfo info = infos == null ? null : infos.get(appKey(app));
        if (info != null) return info;
        return new AppInfo(fallbackLabel(app), null);
    }

    private AppInfo resolveAppInfo(UsageApp app) {
        PackageManager pm = activity.getPackageManager();
        String label = null;
        Drawable icon = null;

        if (app.packageName != null && !app.packageName.isEmpty()) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(app.packageName, 0);
                CharSequence appLabel = pm.getApplicationLabel(info);
                if (appLabel != null) label = appLabel.toString();
                icon = pm.getApplicationIcon(info);
            } catch (Exception ignored) {
            }
        }

        if (label == null || label.isEmpty()) {
            label = fallbackLabel(app);
        }
        if (icon == null) {
            try {
                icon = pm.getDefaultActivityIcon();
            } catch (Exception ignored) {
            }
        }
        return new AppInfo(label, icon);
    }

    private String fallbackLabel(UsageApp app) {
        if (app.packageName != null && !app.packageName.isEmpty()) {
            return app.packageName;
        }
        return "UID " + app.uid;
    }

    private String appKey(UsageApp app) {
        if (app.packageName != null && !app.packageName.isEmpty()) {
            return app.packageName;
        }
        return "uid:" + app.uid;
    }

    private String formatRange(UsageSnapshot snapshot) {
        UsageQuery query = snapshot.query;
        if (query.period == UsagePeriod.TODAY) {
            return RANGE_FORMAT.format(query.today());
        }
        return RANGE_FORMAT.format(query.startDate()) + " – " + RANGE_FORMAT.format(query.today());
    }

    private String formatPeak(UsageSnapshot snapshot) {
        UsageBucket peak = snapshot.peak;
        if (peak == null) return "Puncak —";
        ZonedDateTime time = Instant.ofEpochMilli(peak.startMs).atZone(snapshot.query.zone);
        String label = snapshot.query.period == UsagePeriod.TODAY
                ? String.format(Locale.US, "%02d:00", time.getHour())
                : SHORT_DATE_FORMAT.format(time);
        return "Puncak " + label;
    }

    private String formatPeriodLabel(UsagePeriod period) {
        if (period == UsagePeriod.DAYS_7) return "7 hari terakhir";
        if (period == UsagePeriod.DAYS_30) return "30 hari terakhir";
        return "Hari ini";
    }

    private String formatNetworkLabel(UsageNetwork network) {
        if (network == UsageNetwork.WIFI) return "WiFi";
        if (network == UsageNetwork.MOBILE) return "Seluler";
        return "Semua";
    }

    private String formatDayLabel(String date) {
        try {
            return SHORT_DATE_FORMAT.format(LocalDate.parse(date));
        } catch (Exception e) {
            return date;
        }
    }

    private String formatFullDayLabel(String date) {
        try {
            return FULL_DATE_FORMAT.format(LocalDate.parse(date));
        } catch (Exception e) {
            return date;
        }
    }

    private String formatPercent(long part, long total) {
        if (total <= 0) return "—";
        if (part <= 0) return "0%";
        double percent = part * 100.0 / total;
        if (percent < 1.0) return "<1%";
        return String.format(Locale.US, "%.0f%%", percent);
    }

    private String formatBytes(long bytes) {
        return Formatter.formatFileSize(activity, Math.max(0L, bytes));
    }

    private static final class AppInfo {
        final String label;
        final Drawable icon;

        AppInfo(String label, Drawable icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class DonutEntry {
        final String label;
        final Drawable icon;
        final long totalBytes;
        final int color;

        DonutEntry(String label, Drawable icon, long totalBytes, int color) {
            this.label = label;
            this.icon = icon;
            this.totalBytes = totalBytes;
            this.color = color;
        }
    }

    private static final class PreparedSnapshot {
        final UsageSnapshot snapshot;
        final Map<String, AppInfo> appInfos;

        PreparedSnapshot(UsageSnapshot snapshot, Map<String, AppInfo> appInfos) {
            this.snapshot = snapshot;
            this.appInfos = appInfos;
        }
    }
}
