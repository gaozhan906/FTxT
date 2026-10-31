package exp.ftxt.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;

import java.util.ArrayList;
import java.util.List;

public class UsageDonutView extends View {

    public static final class Slice {
        public final float fraction;
        public final int color;

        public Slice(float fraction, int color) {
            this.fraction = fraction;
            this.color = color;
        }
    }

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Slice> slices = new ArrayList<>();

    private String centerValue = "";
    private String centerLabel = "";

    public UsageDonutView(Context context) {
        super(context);
        init();
    }

    public UsageDonutView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UsageDonutView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.BUTT);
        trackPaint.setColor(0x33888888);
        valuePaint.setColor(resolveThemeColor(android.R.attr.textColorPrimary, 0xFF000000));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelPaint.setColor(resolveThemeColor(android.R.attr.textColorSecondary, 0xFF8A8A8E));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private int resolveThemeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            ColorStateList colors = AppCompatResources.getColorStateList(
                    getContext(), value.resourceId);
            if (colors != null) return colors.getDefaultColor();
        }
        return value.data;
    }

    public void setData(List<Slice> data, String value, String label) {
        slices.clear();
        if (data != null) slices.addAll(data);
        centerValue = value == null ? "" : value;
        centerLabel = label == null ? "" : label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float size = Math.min(width, height);
        float stroke = size * 0.14f;
        float inset = stroke / 2f + dp(2);
        float left = (width - size) / 2f + inset;
        float top = (height - size) / 2f + inset;
        float right = (width + size) / 2f - inset;
        float bottom = (height + size) / 2f - inset;
        RectF oval = new RectF(left, top, right, bottom);

        arcPaint.setStrokeWidth(stroke);
        trackPaint.setStrokeWidth(stroke);
        canvas.drawArc(oval, 0f, 360f, false, trackPaint);

        if (!slices.isEmpty()) {
            float start = -90f;
            float gap = slices.size() > 1
                    ? Math.min(2.5f, 360f / slices.size() * 0.12f)
                    : 0f;
            for (Slice slice : slices) {
                float sweep = Math.max(0f, slice.fraction * 360f);
                if (sweep <= 0f) continue;
                float drawSweep = sweep > gap ? sweep - gap : sweep;
                arcPaint.setColor(slice.color);
                canvas.drawArc(oval, start, drawSweep, false, arcPaint);
                start += sweep;
            }
        }

        float valueSize = size * 0.13f;
        float labelSize = size * 0.075f;
        valuePaint.setTextSize(valueSize);
        labelPaint.setTextSize(labelSize);

        Paint.FontMetrics valueMetrics = valuePaint.getFontMetrics();
        float valueHeight = valueMetrics.descent - valueMetrics.ascent;
        Paint.FontMetrics labelMetrics = labelPaint.getFontMetrics();
        float labelHeight = labelMetrics.descent - labelMetrics.ascent;
        float gap = centerLabel.isEmpty() ? 0f : dp(2);
        float totalHeight = valueHeight + (centerLabel.isEmpty() ? 0f : gap + labelHeight);
        float startTop = height / 2f - totalHeight / 2f;
        float centerX = width / 2f;

        canvas.drawText(centerValue, centerX, startTop - valueMetrics.ascent, valuePaint);
        if (!centerLabel.isEmpty()) {
            float labelTop = startTop + valueHeight + gap;
            canvas.drawText(centerLabel, centerX, labelTop - labelMetrics.ascent, labelPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
