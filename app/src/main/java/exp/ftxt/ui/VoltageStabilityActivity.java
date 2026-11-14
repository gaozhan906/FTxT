package exp.ftxt.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import exp.ftxt.R;
import exp.ftxt.features.battery_stats.BatteryHistoryDb;
import exp.ftxt.features.battery_stats.BatteryReading;
import exp.ftxt.features.battery_stats.VoltageStabilityAnalyzer;

public class VoltageStabilityActivity extends AppCompatActivity {

    private static final long[] WINDOWS = {
            5L * 60_000L,
            30L * 60_000L,
            60L * 60_000L,
            6L * 60L * 60_000L,
            24L * 60L * 60_000L
    };
    private static final String[] WINDOW_LABELS = {"5m", "30m", "1j", "6j", "24j"};
    private static final int DEFAULT_RANGE = 2;
    private static final long LIVE_INTERVAL_MS = 1_000L;
    private static final long HISTORY_INTERVAL_MS = 10_000L;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor();

    private TextView conditionBadge;
    private TextView metricsLeft;
    private TextView metricsRight;
    private TextView statusText;
    private TextView historyStatus;
    private VoltageStabilityChartView chartView;
    private final TextView[] rangeViews = new TextView[WINDOWS.length];

    private int rangeIndex = DEFAULT_RANGE;
    private int labelColor;
    private int headerColor;
    private boolean resumed;
    private boolean queryInFlight;

    public static void start(Context context) {
        context.startActivity(new Intent(context, VoltageStabilityActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voltage_stability);

        labelColor = getColor(R.color.bat_monitor_label);
        headerColor = getColor(R.color.bat_monitor_header);

        conditionBadge = findViewById(R.id.vsConditionBadge);
        metricsLeft = findViewById(R.id.vsMetricsLeft);
        metricsRight = findViewById(R.id.vsMetricsRight);
        statusText = findViewById(R.id.vsStatusText);
        historyStatus = findViewById(R.id.vsHistoryStatus);
        chartView = findViewById(R.id.vsChart);

        findViewById(R.id.vsBack).setOnClickListener(v -> finish());

        int[] ids = {R.id.vsRange0, R.id.vsRange1, R.id.vsRange2, R.id.vsRange3, R.id.vsRange4};
        for (int i = 0; i < ids.length; i++) {
            final int index = i;
            rangeViews[i] = findViewById(ids[i]);
            rangeViews[i].setOnClickListener(v -> selectRange(index));
        }
        updateRangeHighlight();
        refreshLive();
    }

    private void selectRange(int index) {
        if (index < 0 || index >= WINDOWS.length || index == rangeIndex) return;
        rangeIndex = index;
        updateRangeHighlight();
        queryHistory();
    }

    private void updateRangeHighlight() {
        for (int i = 0; i < rangeViews.length; i++) {
            if (rangeViews[i] == null) continue;
            boolean active = i == rangeIndex;
            rangeViews[i].setTextColor(active ? headerColor : labelColor);
            rangeViews[i].setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private final Runnable liveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            refreshLive();
            uiHandler.postDelayed(this, LIVE_INTERVAL_MS);
        }
    };

    private final Runnable historyRunnable = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            queryHistory();
            uiHandler.postDelayed(this, HISTORY_INTERVAL_MS);
        }
    };

    private void refreshLive() {
        VoltageStabilityAnalyzer.State state = VoltageStabilityAnalyzer.getState();

        conditionBadge.setText("● " + state.conditionLabel());
        conditionBadge.setTextColor(conditionColor(state.condition));

        SpannableStringBuilder left = new SpannableStringBuilder();
        appendLine(left, "Tegangan", state.updatedAt > 0L
                ? String.format(Locale.US, "%.3f V", state.voltageV) : "—");
        appendLine(left, "Arus", state.updatedAt > 0L
                ? String.format(Locale.US, "%+d mA", state.currentMa) : "—");
        appendLine(left, "Voltage Drop", state.updatedAt > 0L
                ? String.format(Locale.US, "%.0f mV", state.voltageDropMv) : "—");
        appendLine(left, "Recovery", Float.isNaN(state.recoveryMv)
                ? "—" : String.format(Locale.US, "%+.0f mV", state.recoveryMv));
        metricsLeft.setText(left);

        SpannableStringBuilder right = new SpannableStringBuilder();
        appendLine(right, "Recovery Time", formatRecoveryTime(state.recoveryTimeMs));
        appendLine(right, "Est. R", state.resistanceMohm > 0f
                ? String.format(Locale.US, "%.1f mΩ", state.resistanceMohm) : "—");
        appendLine(right, "Variasi", Float.isNaN(state.voltageStdDevMv)
                ? "—" : String.format(Locale.US, "%.1f mV", state.voltageStdDevMv));
        appendLine(right, "Sampel", String.valueOf(state.observationSamples));
        metricsRight.setText(right);

        statusText.setText(state.measurementStatus);
    }

    private String formatRecoveryTime(long ms) {
        if (ms < 0L) return "—";
        if (ms < 60_000L) return String.format(Locale.US, "%.1f dtk", ms / 1000f);
        long totalSec = (ms + 500L) / 1000L;
        return (totalSec / 60L) + "m " + (totalSec % 60L) + "s";
    }

    private void appendLine(SpannableStringBuilder sb, String label, String value) {
        String padded = String.format(Locale.US, "%-14s", label);
        int start = sb.length();
        sb.append(padded).append(value).append("\n");
        sb.setSpan(new ForegroundColorSpan(labelColor),
                start, start + padded.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private int conditionColor(int condition) {
        switch (condition) {
            case VoltageStabilityAnalyzer.CONDITION_CHARGING:
                return getColor(R.color.bat_condition_charging);
            case VoltageStabilityAnalyzer.CONDITION_DISCHARGING:
                return getColor(R.color.bat_condition_discharging);
            case VoltageStabilityAnalyzer.CONDITION_IDLE:
                return getColor(R.color.bat_condition_idle);
            default:
                return labelColor;
        }
    }

    private void queryHistory() {
        if (queryInFlight || isFinishing() || isDestroyed()) return;
        queryInFlight = true;
        final int queryRange = rangeIndex;
        final long now = System.currentTimeMillis();
        final long from = now - WINDOWS[queryRange];
        historyStatus.setText("Memuat riwayat…");

        queryExecutor.execute(() -> {
            ArrayList<VoltageStabilityAnalyzer.Point> points;
            try {
                BatteryReading.Snapshot[] samples =
                        BatteryHistoryDb.get(VoltageStabilityActivity.this).querySamples(from);
                points = VoltageStabilityAnalyzer.buildHistory(samples, 900);
            } catch (Exception e) {
                points = new ArrayList<>();
            }
            final ArrayList<VoltageStabilityAnalyzer.Point> result = points;
            uiHandler.post(() -> {
                queryInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                if (queryRange != rangeIndex) {
                    queryHistory();
                    return;
                }
                chartView.setData(result);
                historyStatus.setText(result.isEmpty()
                        ? "Belum ada data pada rentang ini"
                        : result.size() + " titik · " + WINDOW_LABELS[queryRange] + " terakhir");
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        uiHandler.removeCallbacks(liveRunnable);
        uiHandler.removeCallbacks(historyRunnable);
        uiHandler.post(liveRunnable);
        uiHandler.post(historyRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        uiHandler.removeCallbacks(liveRunnable);
        uiHandler.removeCallbacks(historyRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        queryExecutor.shutdownNow();
        uiHandler.removeCallbacksAndMessages(null);
    }
}
