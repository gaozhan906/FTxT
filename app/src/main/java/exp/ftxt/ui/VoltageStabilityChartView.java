package exp.ftxt.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import exp.ftxt.R;
import exp.ftxt.features.battery_stats.VoltageStabilityAnalyzer;

public class VoltageStabilityChartView extends View {

    private static final long GAP_THRESHOLD_MS = 5L * 60_000L;

    private final ArrayList<VoltageStabilityAnalyzer.Point> points = new ArrayList<>();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MM/dd HH:mm", Locale.US);

    private int chargingColor;
    private int dischargingColor;
    private int idleColor;
    private int labelColor;

    public VoltageStabilityChartView(Context context) {
        this(context, null);
    }

    public VoltageStabilityChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        chargingColor = context.getColor(R.color.bat_condition_charging);
        dischargingColor = context.getColor(R.color.bat_condition_discharging);
        idleColor = context.getColor(R.color.bat_condition_idle);
        labelColor = context.getColor(R.color.bat_monitor_label);

        gridPaint.setColor(context.getColor(R.color.bat_chart_grid));
        gridPaint.setStrokeWidth(dp(1f));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp(1.6f));

        labelPaint.setColor(labelColor);
        labelPaint.setTextSize(sp(9f));

        emptyPaint.setColor(labelColor);
        emptyPaint.setTextSize(sp(12f));
        emptyPaint.setTextAlign(Paint.Align.CENTER);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<VoltageStabilityAnalyzer.Point> data) {
        points.clear();
        if (data != null) points.addAll(data);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float left = dp(40f);
        float right = width - dp(8f);
        float top = dp(8f);
        float bottom = height - dp(22f);
        if (right <= left || bottom <= top) return;

        if (points.isEmpty()) {
            canvas.drawText("Belum ada data", width / 2f, height / 2f, emptyPaint);
            return;
        }

        float minV = Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (VoltageStabilityAnalyzer.Point p : points) {
            if (p.voltageV < minV) minV = p.voltageV;
            if (p.voltageV > maxV) maxV = p.voltageV;
        }
        if (maxV - minV < 0.03f) {
            float mid = (minV + maxV) / 2f;
            minV = mid - 0.015f;
            maxV = mid + 0.015f;
        }
        float padV = Math.max(0.005f, (maxV - minV) * 0.08f);
        minV -= padV;
        maxV += padV;

        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
            float value = maxV - (maxV - minV) * i / 4f;
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.US, "%.2f", value),
                    left - dp(4f), y + dp(3f), labelPaint);
        }

        long startTime = points.get(0).time;
        long endTime = points.get(points.size() - 1).time;
        boolean longSpan = endTime - startTime > 24L * 3600_000L;
        SimpleDateFormat fmt = longSpan ? dateTimeFormat : timeFormat;
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(fmt.format(new Date(startTime)), left, height - dp(4f), labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(fmt.format(new Date(endTime)), right, height - dp(4f), labelPaint);

        float span = Math.max(1f, endTime - startTime);
        VoltageStabilityAnalyzer.Point previous = null;
        for (VoltageStabilityAnalyzer.Point p : points) {
            if (previous != null) {
                if (p.time - previous.time <= GAP_THRESHOLD_MS && p.time > previous.time) {
                    float x1 = left + (right - left) * (previous.time - startTime) / span;
                    float y1 = bottom - (bottom - top) * (previous.voltageV - minV) / (maxV - minV);
                    float x2 = left + (right - left) * (p.time - startTime) / span;
                    float y2 = bottom - (bottom - top) * (p.voltageV - minV) / (maxV - minV);
                    linePaint.setColor(colorFor(p.condition));
                    canvas.drawLine(x1, y1, x2, y2, linePaint);
                }
            }
            previous = p;
        }

        VoltageStabilityAnalyzer.Point last = points.get(points.size() - 1);
        float lastX = left + (right - left) * (last.time - startTime) / span;
        float lastY = bottom - (bottom - top) * (last.voltageV - minV) / (maxV - minV);
        dotPaint.setColor(colorFor(last.condition));
        canvas.drawCircle(lastX, lastY, dp(2.5f), dotPaint);
    }

    private int colorFor(int condition) {
        switch (condition) {
            case VoltageStabilityAnalyzer.CONDITION_CHARGING:
                return chargingColor;
            case VoltageStabilityAnalyzer.CONDITION_DISCHARGING:
                return dischargingColor;
            case VoltageStabilityAnalyzer.CONDITION_IDLE:
                return idleColor;
            default:
                return labelColor;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
