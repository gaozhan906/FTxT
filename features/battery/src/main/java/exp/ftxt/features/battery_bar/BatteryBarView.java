package exp.ftxt.features.battery_bar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import exp.ftxt.shared.color.BatteryColors;

public class BatteryBarView extends View {

    private static final float WAVE_CYCLES = 2f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int fillColor = Color.GREEN;
    private int emptyColor = 0x66000000;
    private boolean showEmptyStrip = true;
    private float radiusPx = 8;
    private float percent = 0f;
    private boolean charging = false;
    private boolean low = false;
    private int lowColor = Color.YELLOW;
    private int colorScheme = BatteryBarConfig.SCHEME_NONE;
    private boolean horizontal = true;
    private boolean invert = false;

    private float fillAlpha = 255f;
    private float shinePos = -1f;
    private float displayPercent = 0f;

    private boolean shineEnabled = true;
    private int shineSpeed = 1800;
    private int shineWidthPercent = 25;

    private boolean fadeEnabled = true;

    private boolean waveEnabled = true;
    private int waveSpeed = 1000;
    private int waveAmplitudePercent = 60;
    private float waveOffset = 0f;

    private boolean chargeWaveEnabled = true;
    private int chargeWaveSpeed = 1000;
    private int chargeWaveAmplitudePercent = 60;
    private float chargeWaveOffset = 0f;

    private boolean edgeGlowEnabled = true;
    private int edgeGlowSpeed = 1200;
    private int edgeGlowIntensity = 70;
    private float edgeGlowPhase = 0f;

    private boolean startupSweepEnabled = true;
    private int startupSweepSpeed = 700;
    private boolean startupSweepDone = false;

    private boolean drainTickEnabled = true;
    private int drainTickSpeed = 500;
    private float drainTickPos = -1f;
    private float drainTickFromPercent = 0f;

    private ValueAnimator fadeAnimator;
    private ValueAnimator shineAnimator;
    private ValueAnimator waveAnimator;
    private ValueAnimator chargeWaveAnimator;
    private ValueAnimator edgeGlowAnimator;
    private ValueAnimator startupSweepAnimator;
    private ValueAnimator drainTickAnimator;

    public BatteryBarView(Context context) {
        super(context);
    }

    public BatteryBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setBarConfig(int fillColor, int emptyColor, boolean showEmptyStrip, float radiusPx,
                             int colorScheme, int lowColor, boolean horizontal) {
        this.fillColor = fillColor;
        this.emptyColor = emptyColor;
        this.showEmptyStrip = showEmptyStrip;
        this.radiusPx = radiusPx;
        this.colorScheme = colorScheme;
        this.lowColor = lowColor;
        this.horizontal = horizontal;
        invalidate();
    }

    public void setBarOrientation(boolean horizontal) {
        this.horizontal = horizontal;
        invalidate();
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
        invalidate();
    }

    public void setShineConfig(boolean enabled, int speedMs, int widthPercent) {
        this.shineEnabled = enabled;
        this.shineSpeed = Math.max(200, speedMs);
        this.shineWidthPercent = Math.max(2, Math.min(98, widthPercent));
        if (shineAnimator != null) {
            stopShine();
        }
        updateAnimators();
        invalidate();
    }

    public void setWaveConfig(boolean enabled, int speedMs, int amplitudePercent) {
        int newSpeed = Math.max(200, speedMs);
        boolean speedChanged = this.waveSpeed != newSpeed;
        this.waveEnabled = enabled;
        this.waveSpeed = newSpeed;
        this.waveAmplitudePercent = Math.max(10, Math.min(100, amplitudePercent));
        if (speedChanged) stopWave();
        updateAnimators();
        invalidate();
    }

    public void setChargeWaveConfig(boolean enabled, int speedMs, int amplitudePercent) {
        int newSpeed = Math.max(200, speedMs);
        boolean speedChanged = this.chargeWaveSpeed != newSpeed;
        this.chargeWaveEnabled = enabled;
        this.chargeWaveSpeed = newSpeed;
        this.chargeWaveAmplitudePercent = Math.max(10, Math.min(100, amplitudePercent));
        if (speedChanged) stopChargeWave();
        updateAnimators();
        invalidate();
    }

    public void setEdgeGlowConfig(boolean enabled, int speedMs, int intensityPercent) {
        this.edgeGlowEnabled = enabled;
        this.edgeGlowSpeed = Math.max(200, Math.min(5000, speedMs));
        this.edgeGlowIntensity = Math.max(10, Math.min(100, intensityPercent));
        updateAnimators();
        invalidate();
    }

    public void setStartupSweepConfig(boolean enabled, int speedMs) {
        this.startupSweepEnabled = enabled;
        this.startupSweepSpeed = Math.max(200, Math.min(5000, speedMs));
        if (!enabled) {
            stopStartupSweep();
            startupSweepDone = true;
            displayPercent = percent;
        }
        invalidate();
    }

    public void setDrainTickConfig(boolean enabled, int speedMs) {
        this.drainTickEnabled = enabled;
        this.drainTickSpeed = Math.max(200, Math.min(5000, speedMs));
        if (!enabled) stopDrainTick();
        invalidate();
    }

    public void updateStatus(float percent, boolean charging, boolean low) {
        float oldPercent = this.percent;
        this.percent = Math.max(0f, Math.min(100f, percent));
        this.charging = charging;
        this.low = low;

        if (!startupSweepDone) {
            if (!startupSweepEnabled) {
                startupSweepDone = true;
                displayPercent = this.percent;
            } else if (startupSweepAnimator == null && this.percent > 0f) {
                startStartupSweep();
            }
        } else if (startupSweepAnimator == null) {
            displayPercent = this.percent;
            if (drainTickEnabled && !charging && this.percent < oldPercent) {
                startDrainTick(oldPercent);
            }
        }

        updateAnimators();
        invalidate();
    }

    private void updateAnimators() {
        if (charging && shineEnabled) {
            startShine();
        } else {
            stopShine();
        }
        if (chargeWaveActive()) {
            startChargeWave();
        } else {
            stopChargeWave();
        }
        if (charging && edgeGlowEnabled) {
            startEdgeGlow();
        } else {
            stopEdgeGlow();
        }
        if (waveActive()) {
            startWave();
        } else {
            stopWave();
        }
        if (low && !charging && fadeEnabled) {
            startFade();
        } else {
            stopFade();
            fillAlpha = 255f;
        }
    }

    private void startShine() {
        if (shineAnimator != null) return;
        shineAnimator = ValueAnimator.ofFloat(-0.5f, 1.5f);
        shineAnimator.setDuration((long) (shineSpeed * WAVE_CYCLES * 2));
        shineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shineAnimator.setInterpolator(new LinearInterpolator());
        shineAnimator.addUpdateListener(a -> {
            shinePos = (float) a.getAnimatedValue();
            invalidate();
        });
        shinePos = -0.5f;
        shineAnimator.start();
    }

    private void stopShine() {
        if (shineAnimator != null) {
            shineAnimator.cancel();
            shineAnimator = null;
        }
        shinePos = -1f;
    }

    private void startWave() {
        if (waveAnimator != null) return;
        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(waveSpeed);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(a -> {
            waveOffset = (float) a.getAnimatedValue();
            invalidate();
        });
        waveAnimator.start();
    }

    private void stopWave() {
        if (waveAnimator != null) {
            waveAnimator.cancel();
            waveAnimator = null;
        }
        waveOffset = 0f;
    }

    private boolean waveActive() {
        return !charging && waveEnabled;
    }

    private boolean chargeWaveActive() {
        return charging && chargeWaveEnabled;
    }

    private void startChargeWave() {
        if (chargeWaveAnimator != null) return;
        chargeWaveAnimator = ValueAnimator.ofFloat(0f, 1f);
        chargeWaveAnimator.setDuration(chargeWaveSpeed);
        chargeWaveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        chargeWaveAnimator.setInterpolator(new LinearInterpolator());
        chargeWaveAnimator.addUpdateListener(a -> {
            chargeWaveOffset = (float) a.getAnimatedValue();
            invalidate();
        });
        chargeWaveAnimator.start();
    }

    private void stopChargeWave() {
        if (chargeWaveAnimator != null) {
            chargeWaveAnimator.cancel();
            chargeWaveAnimator = null;
        }
        chargeWaveOffset = 0f;
    }

    private void startEdgeGlow() {
        if (edgeGlowAnimator != null) return;
        edgeGlowAnimator = ValueAnimator.ofFloat(0f, 1f);
        edgeGlowAnimator.setDuration(edgeGlowSpeed);
        edgeGlowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        edgeGlowAnimator.setInterpolator(new LinearInterpolator());
        edgeGlowAnimator.addUpdateListener(a -> {
            edgeGlowPhase = (float) a.getAnimatedValue();
            invalidate();
        });
        edgeGlowAnimator.start();
    }

    private void stopEdgeGlow() {
        if (edgeGlowAnimator != null) {
            edgeGlowAnimator.cancel();
            edgeGlowAnimator = null;
        }
        edgeGlowPhase = 0f;
    }

    private void startStartupSweep() {
        if (startupSweepAnimator != null || !startupSweepEnabled || percent <= 0f) return;
        displayPercent = 0f;
        startupSweepAnimator = ValueAnimator.ofFloat(0f, percent);
        startupSweepAnimator.setDuration(startupSweepSpeed);
        startupSweepAnimator.setInterpolator(new DecelerateInterpolator());
        startupSweepAnimator.addUpdateListener(a -> {
            displayPercent = (float) a.getAnimatedValue();
            invalidate();
        });
        startupSweepAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startupSweepAnimator = null;
                startupSweepDone = true;
                displayPercent = BatteryBarView.this.percent;
                invalidate();
            }
        });
        startupSweepAnimator.start();
    }

    private void stopStartupSweep() {
        if (startupSweepAnimator != null) {
            startupSweepAnimator.cancel();
            startupSweepAnimator = null;
        }
    }

    private void startDrainTick(float fromPercent) {
        if (!drainTickEnabled || fromPercent <= percent) return;
        if (drainTickAnimator != null) {
            drainTickAnimator.cancel();
            drainTickAnimator = null;
        }
        drainTickFromPercent = fromPercent;
        drainTickPos = 0f;
        drainTickAnimator = ValueAnimator.ofFloat(0f, 1f);
        drainTickAnimator.setDuration(drainTickSpeed);
        drainTickAnimator.setInterpolator(new LinearInterpolator());
        drainTickAnimator.addUpdateListener(a -> {
            drainTickPos = (float) a.getAnimatedValue();
            invalidate();
        });
        drainTickAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                drainTickAnimator = null;
                drainTickPos = -1f;
                invalidate();
            }
        });
        drainTickAnimator.start();
    }

    private void stopDrainTick() {
        if (drainTickAnimator != null) {
            drainTickAnimator.cancel();
            drainTickAnimator = null;
        }
        drainTickPos = -1f;
    }

    private float waveFactor(float t, float offset) {
        float wave = (float) Math.sin(2 * Math.PI * (t * WAVE_CYCLES + offset));
        return (wave + 1f) / 2f;
    }

    private void startFade() {
        if (fadeAnimator != null) return;
        long duration = fadeSpeed;
        fadeAnimator = ValueAnimator.ofFloat(1f, 0.25f);
        fadeAnimator.setDuration(duration);
        fadeAnimator.setRepeatMode(ValueAnimator.REVERSE);
        fadeAnimator.setRepeatCount(ValueAnimator.INFINITE);
        fadeAnimator.addUpdateListener(a -> {
            fillAlpha = 255f * (float) a.getAnimatedValue();
            invalidate();
        });
        fadeAnimator.start();
    }

    private void stopFade() {
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
    }

    private int fadeSpeed = 1800;

    public void setFadeSpeed(int speed) {
        this.fadeSpeed = Math.max(200, Math.min(5000, speed));
    }

    public void setFadeEnabled(boolean enabled) {
        this.fadeEnabled = enabled;
        updateAnimators();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float shownPercent = displayPercent;

        float radius = Math.min(radiusPx, Math.min(w, h) / 2f);
        rect.set(0, 0, w, h);

        if (showEmptyStrip) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(emptyColor);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        int barColor = fillColor;
        if (colorScheme == BatteryBarConfig.SCHEME_HUE) {
            barColor = BatteryColors.hueColor(percent);
        } else if (colorScheme == BatteryBarConfig.SCHEME_CLASSIC) {
            if (percent <= 10f) barColor = Color.RED;
            else if (percent <= 20f) barColor = Color.YELLOW;
            else barColor = Color.GREEN;
        } else if (low) {
            barColor = lowColor;
        }

        if (shownPercent > 0f) {
            paint.setColor(barColor);
            paint.setAlpha((int) fillAlpha);
            if (horizontal) {
                float fillW = Math.max(0, w * (shownPercent / 100f));
                if (invert) {
                    rect.set(w - fillW, 0, w, h);
                } else {
                    rect.set(0, 0, fillW, h);
                }
            } else {
                float fillH = Math.max(0, h * (shownPercent / 100f));
                if (invert) {
                    rect.set(0, 0, w, fillH);
                } else {
                    rect.set(0, h - fillH, w, h);
                }
            }
            if (chargeWaveActive()) {
                drawWaveFill(canvas, radius, barColor, w, h, shownPercent, true, chargeWaveAmplitudePercent / 100f, -chargeWaveOffset);
            } else if (waveActive()) {
                drawWaveFill(canvas, radius, barColor, w, h, shownPercent, false, waveAmplitudePercent / 100f, waveOffset);
            } else {
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            paint.setAlpha(255);
        }

        drawDrainTick(canvas, w, h);
        drawEdgeGlow(canvas, w, h, shownPercent);

        if (charging && shinePos >= -0.5f) {
            float bandWidth = Math.max(10f, (horizontal ? w : h) * (shineWidthPercent / 100f));
            if (horizontal) {
                float fillEdge = invert ? w - w * (shownPercent / 100f) : w * (shownPercent / 100f);
                float bandStart = (invert ? (1f - shinePos) : shinePos) * w - bandWidth / 2f;
                float bandEnd = bandStart + bandWidth;
                if (invert) {
                    drawShineSegment(canvas, radius, Math.max(bandStart, fillEdge), 0, Math.min(bandEnd, w), h, 0xFFFFFFFF);
                    drawShineSegment(canvas, radius, Math.max(bandStart, 0f), 0, Math.min(bandEnd, fillEdge), h, 0x66FFFFFF);
                } else {
                    drawShineSegment(canvas, radius, Math.max(bandStart, 0f), 0, Math.min(bandEnd, fillEdge), h, 0xFFFFFFFF);
                    drawShineSegment(canvas, radius, Math.max(bandStart, fillEdge), 0, Math.min(bandEnd, w), h, 0x66FFFFFF);
                }
            } else {
                float fillEdge = invert ? h * (shownPercent / 100f) : h - h * (shownPercent / 100f);
                float bandStart = (invert ? shinePos : (1f - shinePos)) * h - bandWidth / 2f;
                float bandEnd = bandStart + bandWidth;
                if (invert) {
                    drawShineSegment(canvas, radius, 0, Math.max(bandStart, 0f), w, Math.min(bandEnd, fillEdge), 0xFFFFFFFF);
                    drawShineSegment(canvas, radius, 0, Math.max(bandStart, fillEdge), w, Math.min(bandEnd, h), 0x66FFFFFF);
                } else {
                    drawShineSegment(canvas, radius, 0, Math.max(bandStart, fillEdge), w, Math.min(bandEnd, h), 0xFFFFFFFF);
                    drawShineSegment(canvas, radius, 0, Math.max(bandStart, 0f), w, Math.min(bandEnd, fillEdge), 0x66FFFFFF);
                }
            }
        }
    }

    private float fillEdgeForPercent(float p, int w, int h) {
        if (horizontal) {
            return invert ? w - w * (p / 100f) : w * (p / 100f);
        }
        return invert ? h * (p / 100f) : h - h * (p / 100f);
    }

    private void drawDrainTick(Canvas canvas, int w, int h) {
        if (drainTickPos < 0f || drainTickPos > 1f) return;
        float t = drainTickPos;
        float fade = 1f - t;
        if (fade <= 0f) return;

        float oldEdge = fillEdgeForPercent(drainTickFromPercent, w, h);
        float newEdge = fillEdgeForPercent(percent, w, h);
        float cross = horizontal ? h : w;

        glowPaint.setShader(null);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Color.WHITE);

        int lostAlpha = Math.round(70f * fade);
        if (lostAlpha > 0 && oldEdge != newEdge) {
            glowPaint.setAlpha(lostAlpha);
            if (horizontal) {
                canvas.drawRect(Math.min(oldEdge, newEdge), 0, Math.max(oldEdge, newEdge), h, glowPaint);
            } else {
                canvas.drawRect(0, Math.min(oldEdge, newEdge), w, Math.max(oldEdge, newEdge), glowPaint);
            }
        }

        float half = Math.max(4f, cross * 1.3f);
        int glowAlpha = Math.round(170f * fade);
        if (glowAlpha > 0) {
            glowPaint.setAlpha(glowAlpha);
            if (horizontal) {
                canvas.drawRect(oldEdge - half, 0, oldEdge + half, h, glowPaint);
            } else {
                canvas.drawRect(0, oldEdge - half, w, oldEdge + half, glowPaint);
            }
        }

        float core = Math.max(1.5f, cross * 0.22f);
        int coreAlpha = Math.round(255f * fade);
        if (coreAlpha > 0) {
            glowPaint.setAlpha(coreAlpha);
            if (horizontal) {
                canvas.drawRect(oldEdge - core, 0, oldEdge + core, h, glowPaint);
            } else {
                canvas.drawRect(0, oldEdge - core, w, oldEdge + core, glowPaint);
            }
        }
        glowPaint.setAlpha(255);
    }

    private void drawEdgeGlow(Canvas canvas, int w, int h, float shownPercent) {
        if (!charging || !edgeGlowEnabled || shownPercent <= 0f) return;
        float edge = fillEdgeForPercent(shownPercent, w, h);
        float glowWidth = Math.max(6f, Math.min(24f, (horizontal ? h : w) * 2f));
        float pulse = 0.6f + 0.4f * (float) Math.sin(edgeGlowPhase * 2f * Math.PI);
        int alpha = Math.round(255f * (edgeGlowIntensity / 100f) * pulse);
        if (alpha <= 0) return;

        int bright = (alpha << 24) | 0x00FFFFFF;
        int clear = 0x00FFFFFF;
        float inner = horizontal
                ? edge + (invert ? glowWidth : -glowWidth)
                : edge + (invert ? -glowWidth : glowWidth);
        Shader shader = horizontal
                ? new LinearGradient(edge, 0, inner, 0, bright, clear, Shader.TileMode.CLAMP)
                : new LinearGradient(0, edge, 0, inner, bright, clear, Shader.TileMode.CLAMP);
        glowPaint.setShader(shader);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAlpha(255);
        if (horizontal) {
            canvas.drawRect(Math.min(edge, inner), 0, Math.max(edge, inner), h, glowPaint);
        } else {
            canvas.drawRect(0, Math.min(edge, inner), w, Math.max(edge, inner), glowPaint);
        }
        glowPaint.setShader(null);
    }

    private void drawWaveFill(Canvas canvas, float radius, int barColor, int w, int h,
                              float shownPercent, boolean forceRight, float amp, float offset) {
        paint.setColor(barColor);
        paint.setAlpha(Math.round(fillAlpha * (1f - amp)));
        canvas.drawRoundRect(rect, radius, radius, paint);

        float fillLen = horizontal ? w * (shownPercent / 100f) : h * (shownPercent / 100f);
        int n = Math.max(8, (int) (fillLen / 4f));
        float segLen = fillLen / n;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float boost = amp * waveFactor(t, offset);
            if (boost <= 0.001f) continue;
            paint.setAlpha(Math.round(fillAlpha * boost));
            if (horizontal) {
                float x0, x1;
                if (forceRight) {
                    x0 = segLen * i;
                    x1 = segLen * (i + 1);
                } else if (invert) {
                    x0 = w - segLen * (i + 1);
                    x1 = w - segLen * i;
                } else {
                    x0 = segLen * i;
                    x1 = segLen * (i + 1);
                }
                rect.set(x0, 0, x1, h);
            } else {
                float y0, y1;
                if (forceRight || !invert) {
                    y0 = h - segLen * (i + 1);
                    y1 = h - segLen * i;
                } else {
                    y0 = segLen * i;
                    y1 = segLen * (i + 1);
                }
                rect.set(0, y0, w, y1);
            }
            canvas.drawRect(rect, paint);
        }
        paint.setAlpha(255);
    }

    private void drawShineSegment(Canvas canvas, float radius, float l, float t, float r, float b, int color) {
        if (l >= r || t >= b) return;
        rect.set(l, t, r, b);
        paint.setColor(color);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setXfermode(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopFade();
        stopShine();
        stopWave();
        stopChargeWave();
        stopEdgeGlow();
        stopStartupSweep();
        stopDrainTick();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            updateAnimators();
        } else {
            stopFade();
            stopShine();
            stopWave();
            stopChargeWave();
            stopEdgeGlow();
            stopStartupSweep();
            stopDrainTick();
        }
    }
}
