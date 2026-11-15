package exp.ftxt.features.network_stats.app_usage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import exp.ftxt.shared.config.BackgroundConfig;
import exp.ftxt.shared.config.ShadowConfig;
import exp.ftxt.shared.ui.ShadowTextView;

public class AppUsageOverlayView extends LinearLayout {

    private static final int ICON_SIZE_DP = 18;
    private static final int ICON_GAP_DP = 6;
    private static final int ROW_GAP_DP = 2;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Row> rows = new ArrayList<>();
    private final ShadowTextView idleText;
    private final int iconSizePx;
    private final int iconGapPx;
    private final int rowGapPx;
    private final int maxTextWidthPx;

    private int valueColor = AppUsageConfig.color;
    private int labelColor = AppUsageConfig.labelColor;
    private boolean textBold = AppUsageConfig.textBold;
    private boolean textItalic = AppUsageConfig.textItalic;
    private int unitMode = AppUsageConfig.unitMode;
    private boolean unitTrimmed = AppUsageConfig.unitTrimmed;
    private boolean showDecimal = AppUsageConfig.showDecimal;
    private int decimalPlaces = AppUsageConfig.decimalPlaces;
    private boolean showIcon = AppUsageConfig.showIcon;
    private int textSize = (int) AppUsageConfig.size;

    private boolean bgEnabled;
    private int bgColor;
    private int bgOffsetX;
    private int bgOffsetY;
    private int bgMargin;
    private int bgRadius;

    public AppUsageOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setGravity(Gravity.START);

        float density = getResources().getDisplayMetrics().density;
        iconSizePx = Math.round(ICON_SIZE_DP * density);
        iconGapPx = Math.round(ICON_GAP_DP * density);
        rowGapPx = Math.round(ROW_GAP_DP * density);
        maxTextWidthPx = Math.round(
                getResources().getDisplayMetrics().widthPixels * 0.8f);

        idleText = new ShadowTextView(context);
        idleText.setSingleLine(true);
        idleText.setIncludeFontPadding(false);
        idleText.setTextSize(textSize);
        idleText.setTextColor(labelColor);
        idleText.setText("Tidak ada aktivitas data");
        addView(idleText);
    }

    public void setOverlayTextSize(float size) {
        textSize = Math.max(6, Math.round(size));
        idleText.setTextSize(textSize);
        for (Row row : rows) {
            row.text.setTextSize(textSize);
        }
        requestLayout();
    }

    public void setTextColors(int color, int labelColor) {
        this.valueColor = color;
        this.labelColor = labelColor;
        idleText.setTextColor(labelColor);
        requestLayout();
    }

    public void setTextStyleConfig(boolean bold, boolean italic) {
        textBold = bold;
        textItalic = italic;
        int style = Typeface.NORMAL;
        if (bold && italic) {
            style = Typeface.BOLD_ITALIC;
        } else if (bold) {
            style = Typeface.BOLD;
        } else if (italic) {
            style = Typeface.ITALIC;
        }
        idleText.setTypeface(Typeface.DEFAULT, style);
        for (Row row : rows) {
            row.text.setTypeface(Typeface.DEFAULT, style);
        }
    }

    public void setUnitConfig(int mode, boolean trimmed) {
        unitMode = mode;
        unitTrimmed = trimmed;
    }

    public void setShowDecimal(boolean show) {
        showDecimal = show;
    }

    public void setDecimalPlaces(int places) {
        decimalPlaces = Math.max(1, Math.min(3, places));
    }

    public void setShowIcon(boolean show) {
        showIcon = show;
        for (Row row : rows) {
            row.icon.setVisibility(show && row.icon.getDrawable() != null
                    ? VISIBLE : GONE);
        }
        requestLayout();
    }

    public void setShadowConfig(ShadowConfig config) {
        idleText.setShadowConfig(config);
        for (Row row : rows) {
            row.text.setShadowConfig(config);
        }
    }

    public void setBackgroundConfig(BackgroundConfig config) {
        bgEnabled = config.enabled;
        bgColor = config.color;
        bgOffsetX = config.offsetX;
        bgOffsetY = config.offsetY;
        bgMargin = config.margin;
        bgRadius = config.radius;
        int pad = config.enabled ? config.padding : 0;
        setPadding(pad, pad, pad, pad);
        requestLayout();
        invalidate();
    }

    public void showIdle() {
        for (Row row : rows) {
            row.container.setVisibility(GONE);
        }
        idleText.setVisibility(VISIBLE);
        requestLayout();
    }

    public void render(List<ConnectingAppSource.AppTraffic> apps) {
        if (apps == null || apps.isEmpty()) {
            showIdle();
            return;
        }

        idleText.setVisibility(GONE);
        ensureRows(apps.size());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (i >= apps.size()) {
                row.container.setVisibility(GONE);
                continue;
            }
            ConnectingAppSource.AppTraffic app = apps.get(i);
            row.container.setVisibility(VISIBLE);
            row.icon.setImageDrawable(app.icon);
            row.icon.setVisibility(showIcon && app.icon != null ? VISIBLE : GONE);
            row.text.setText(buildText(app));
        }
        requestLayout();
    }

    private SpannableStringBuilder buildText(ConnectingAppSource.AppTraffic app) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(app.label);
        builder.setSpan(new ForegroundColorSpan(labelColor), 0, builder.length(), 0);
        appendSpeed(builder, "↓", app.rxBytesPerSec);
        appendSpeed(builder, "↑", app.txBytesPerSec);
        return builder;
    }

    private void appendSpeed(SpannableStringBuilder builder, String arrow,
                             long bytesPerSec) {
        builder.append("  ").append(arrow);
        int arrowStart = builder.length() - arrow.length();
        builder.setSpan(new ForegroundColorSpan(labelColor),
                arrowStart, builder.length(), 0);

        int valueStart = builder.length();
        builder.append(formatValue(bytesPerSec));
        int valueEnd = builder.length();
        builder.setSpan(new ForegroundColorSpan(valueColor),
                valueStart, valueEnd, 0);

        builder.append(unitSuffix(bytesPerSec));
        builder.setSpan(new ForegroundColorSpan(labelColor),
                valueEnd, builder.length(), 0);
    }

    private void ensureRows(int count) {
        while (rows.size() < count) {
            rows.add(createRow());
        }
    }

    private Row createRow() {
        Row row = new Row();
        row.container = new LinearLayout(getContext());
        row.container.setOrientation(HORIZONTAL);
        row.container.setGravity(Gravity.CENTER_VERTICAL);

        if (!rows.isEmpty()) {
            LayoutParams params = new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.topMargin = rowGapPx;
            row.container.setLayoutParams(params);
        }

        row.icon = new ImageView(getContext());
        LayoutParams iconParams = new LayoutParams(iconSizePx, iconSizePx);
        iconParams.setMarginEnd(iconGapPx);
        row.icon.setLayoutParams(iconParams);
        row.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        row.text = new ShadowTextView(getContext());
        row.text.setSingleLine(true);
        row.text.setEllipsize(TextUtils.TruncateAt.END);
        row.text.setIncludeFontPadding(false);
        row.text.setTextSize(textSize);
        row.text.setMaxWidth(maxTextWidthPx);
        row.text.setShadowConfig(AppUsageConfig.shadow);
        row.text.setTypeface(Typeface.DEFAULT, typefaceStyle());

        row.container.addView(row.icon);
        row.container.addView(row.text);
        addView(row.container);
        return row;
    }

    private int typefaceStyle() {
        if (textBold && textItalic) return Typeface.BOLD_ITALIC;
        if (textBold) return Typeface.BOLD;
        if (textItalic) return Typeface.ITALIC;
        return Typeface.NORMAL;
    }

    private String formatValue(long bytesPerSec) {
        long value = Math.max(0L, bytesPerSec);
        if (unitMode == AppUsageConfig.UNIT_BPS) {
            double bits = value * 8.0;
            if (bits >= 1_000_000_000d) return formatNumber(bits / 1_000_000_000d);
            if (bits >= 1_000_000d) return formatNumber(bits / 1_000_000d);
            if (bits >= 1_000d) return formatNumber(bits / 1_000d);
            return formatNumber(bits);
        }
        if (value >= 1024L * 1024L) {
            return formatNumber(value / (1024.0 * 1024.0));
        }
        return formatNumber(value / 1024.0);
    }

    private String unitSuffix(long bytesPerSec) {
        long value = Math.max(0L, bytesPerSec);
        if (unitMode == AppUsageConfig.UNIT_BPS) {
            double bits = value * 8.0;
            String prefix = "";
            if (bits >= 1_000_000_000d) {
                prefix = "G";
            } else if (bits >= 1_000_000d) {
                prefix = "M";
            } else if (bits >= 1_000d) {
                prefix = "K";
            }
            return prefix + (unitTrimmed ? "b" : "bps");
        }
        if (value >= 1024L * 1024L) {
            return unitTrimmed ? "MB" : "MB/s";
        }
        return unitTrimmed ? "KB" : "KB/s";
    }

    private String formatNumber(double value) {
        if (showDecimal) {
            return String.format(Locale.US, "%." + decimalPlaces + "f", value);
        }
        return String.valueOf((long) value);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bgEnabled) {
            bgPaint.setColor(bgColor);
            canvas.save();
            canvas.translate(bgOffsetX, bgOffsetY);
            float left = -bgMargin;
            float top = -bgMargin;
            float right = getWidth() + bgMargin;
            float bottom = getHeight() + bgMargin;
            if (bgRadius > 0) {
                canvas.drawRoundRect(left, top, right, bottom, bgRadius, bgRadius, bgPaint);
            } else {
                canvas.drawRect(left, top, right, bottom, bgPaint);
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }

    private static final class Row {
        LinearLayout container;
        ImageView icon;
        ShadowTextView text;
    }
}
