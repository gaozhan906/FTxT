package exp.ftxt.features.network_stats.usage;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class UsageChartView extends View {

    private final Paint rxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<UsageBucket> buckets = new ArrayList<>();
    private UsagePeriod period = UsagePeriod.TODAY;
    private ZoneId zone = ZoneId.systemDefault();

    public UsageChartView(Context context) {
        super(context);
        init();
    }

    public UsageChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UsageChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        rxPaint.setColor(0xFF4CAF50);
        txPaint.setColor(0xFF03A9F4);
        int labelColor = resolveThemeColor(android.R.attr.textColorSecondary, 0xFF888888);
        labelPaint.setColor(labelColor);
        labelPaint.setTextSize(sp(10));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        baselinePaint.setColor((labelColor & 0x00FFFFFF) | 0x33000000);
        baselinePaint.setStrokeWidth(dp(1));
    }

    private int resolveThemeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            ColorStateList colors = getContext().getColorStateList(value.resourceId);
            if (colors != null) return colors.getDefaultColor();
        }
        return value.data;
    }

    public void setData(List<UsageBucket> data, UsagePeriod period, ZoneId zone) {
        buckets.clear();
        if (data != null) buckets.addAll(data);
        this.period = period != null ? period : UsagePeriod.TODAY;
        this.zone = zone != null ? zone : ZoneId.systemDefault();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float labelHeight = Math.max(dp(18), sp(14));
        float chartTop = dp(8);
        float chartBottom = getHeight() - labelHeight;
        float chartHeight = chartBottom - chartTop;
        if (chartHeight <= 0) return;

        canvas.drawLine(0, chartBottom, getWidth(), chartBottom, baselinePaint);
        if (buckets.isEmpty()) return;

        long max = 0;
        for (UsageBucket bucket : buckets) {
            max = Math.max(max, bucket.totalBytes());
        }
        if (max <= 0) max = 1;

        int count = buckets.size();
        float slotWidth = (float) getWidth() / count;
        float barWidth = Math.min(dp(18), Math.max(dp(2), slotWidth * 0.55f));

        for (int i = 0; i < count; i++) {
            UsageBucket bucket = buckets.get(i);
            float center = slotWidth * i + slotWidth / 2f;
            float rxHeight = (float) bucket.rxBytes / max * chartHeight;
            float txHeight = (float) bucket.txBytes / max * chartHeight;
            float left = center - barWidth / 2f;
            float right = center + barWidth / 2f;

            if (rxHeight > 0) {
                canvas.drawRect(left, chartBottom - rxHeight, right, chartBottom, rxPaint);
            }
            if (txHeight > 0) {
                canvas.drawRect(left, chartBottom - rxHeight - txHeight, right,
                        chartBottom - rxHeight, txPaint);
            }
        }

        int maxLabels = Math.max(1, (int) (getWidth() / dp(40)));
        int step = (int) Math.ceil(count / (double) maxLabels);
        for (int i = 0; i < count; i += step) {
            String label = formatLabel(buckets.get(i));
            float center = slotWidth * i + slotWidth / 2f;
            canvas.drawText(label, center, getHeight() - dp(4), labelPaint);
        }
    }

    private String formatLabel(UsageBucket bucket) {
        ZonedDateTime time = Instant.ofEpochMilli(bucket.startMs).atZone(zone);
        if (period == UsagePeriod.TODAY) {
            return String.format(java.util.Locale.US, "%02d", time.getHour());
        }
        return String.format(java.util.Locale.US, "%02d/%02d",
                time.getDayOfMonth(), time.getMonthValue());
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
