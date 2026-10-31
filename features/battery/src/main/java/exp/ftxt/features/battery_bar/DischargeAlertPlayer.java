package exp.ftxt.features.battery_bar;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DischargeAlertPlayer {

    private static final int SAMPLE_RATE = 44100;
    private static final int REPEAT_GAP_MS = 120;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean released = false;

    public DischargeAlertPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void play() {
        if (released) return;
        final int pitch = clamp(BatteryBarConfig.dischargeAlertPitch, 200, 6000);
        final int duration = clamp(BatteryBarConfig.dischargeAlertDuration, 20, 500);
        final int volume = clamp(BatteryBarConfig.dischargeAlertVolume, 0, 100);
        final int repeat = clamp(BatteryBarConfig.dischargeAlertRepeat, 1, 10);
        executor.execute(() -> playTone(pitch, duration, volume, repeat));
        if (BatteryBarConfig.dischargeAlertVibrate) {
            scheduleVibrations(clamp(BatteryBarConfig.dischargeAlertVibrateDuration, 10, 500), duration, repeat);
        }
    }

    public static void preview(Context context) {
        final DischargeAlertPlayer player = new DischargeAlertPlayer(context);
        player.play();
        int duration = clamp(BatteryBarConfig.dischargeAlertDuration, 20, 500);
        int repeat = clamp(BatteryBarConfig.dischargeAlertRepeat, 1, 10);
        int total = duration * repeat + REPEAT_GAP_MS * (repeat - 1);
        int vibrateDuration = BatteryBarConfig.dischargeAlertVibrate
                ? clamp(BatteryBarConfig.dischargeAlertVibrateDuration, 10, 500) : 0;
        int lastVibrateEnd = (repeat - 1) * (duration + REPEAT_GAP_MS) + vibrateDuration;
        player.handler.postDelayed(player::release, Math.max(total, lastVibrateEnd) + 300L);
    }

    public void release() {
        released = true;
        executor.shutdownNow();
    }

    private void playTone(int pitchHz, int durationMs, int volumePercent, int repeat) {
        if (released || volumePercent <= 0) return;

        int sampleCount = Math.max(1, SAMPLE_RATE * durationMs / 1000);
        int gapSamples = SAMPLE_RATE * REPEAT_GAP_MS / 1000;
        int totalSamples = sampleCount * repeat + gapSamples * (repeat - 1);
        short[] samples = new short[totalSamples];
        double amplitude = 32767.0 * (volumePercent / 100.0);
        int fadeSamples = Math.min(sampleCount / 4, SAMPLE_RATE / 200);
        if (fadeSamples < 1) fadeSamples = 1;

        for (int r = 0; r < repeat; r++) {
            int offset = r * (sampleCount + gapSamples);
            for (int i = 0; i < sampleCount; i++) {
                double envelope = 1.0;
                if (i < fadeSamples) envelope = i / (double) fadeSamples;
                else if (i >= sampleCount - fadeSamples) envelope = (sampleCount - 1 - i) / (double) fadeSamples;
                double value = Math.sin(2.0 * Math.PI * pitchHz * i / SAMPLE_RATE);
                samples[offset + i] = (short) Math.round(value * amplitude * envelope);
            }
        }
        if (released) return;

        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(samples.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
        } catch (Exception e) {
            return;
        }

        try {
            track.write(samples, 0, samples.length);
            track.play();
        } catch (Exception e) {
            track.release();
            return;
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        Runnable releaseTrack = () -> {
            if (!finished.compareAndSet(false, true)) return;
            try {
                track.stop();
            } catch (Exception ignored) {
            }
            try {
                track.release();
            } catch (Exception ignored) {
            }
        };
        try {
            track.setNotificationMarkerPosition(totalSamples);
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack t) {
                    releaseTrack.run();
                }

                @Override
                public void onPeriodicNotification(AudioTrack t) {
                }
            });
        } catch (Exception ignored) {
        }
        handler.postDelayed(releaseTrack, totalSamples * 1000L / SAMPLE_RATE + 200L);
    }

    private void scheduleVibrations(int vibrateDuration, int beepDuration, int repeat) {
        long interval = beepDuration + REPEAT_GAP_MS;
        for (int i = 0; i < repeat; i++) {
            long delay = i * interval;
            if (delay == 0) {
                if (!released) vibrate(vibrateDuration);
            } else {
                handler.postDelayed(() -> {
                    if (!released) vibrate(vibrateDuration);
                }, delay);
            }
        }
    }

    private void vibrate(int durationMs) {
        Vibrator vibrator = getVibrator();
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
        }
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager != null ? manager.getDefaultVibrator() : null;
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
