package exp.ftxt.features.battery_stats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

public final class VoltageStabilityAnalyzer {

    public static final int CONDITION_UNKNOWN = 0;
    public static final int CONDITION_CHARGING = 1;
    public static final int CONDITION_DISCHARGING = 2;
    public static final int CONDITION_IDLE = 3;

    private static final int DISCHARGE_LOAD_MA = 150;
    private static final int IDLE_LOAD_MA = 120;

    private static final int MIN_DELTA_MA = 200;
    private static final float MIN_DELTA_MV = 15f;
    private static final long MAX_PAIR_MS = 12_000L;
    private static final float MAX_TEMP_DELTA_C = 1.5f;

    private static final float RECOVERY_STABLE_MV = 8f;
    private static final long RECOVERY_STABLE_MS = 4_000L;
    private static final long RECOVERY_TIMEOUT_MS = 60_000L;
    private static final long SAG_FRESH_MS = 120_000L;
    private static final long RESISTANCE_COOLDOWN_MS = 8_000L;

    private static final int MAX_WINDOW = 300;
    private static final int MAX_RESISTANCE_SAMPLES = 15;

    private static final ArrayDeque<BatteryReading.Snapshot> window = new ArrayDeque<>();
    private static final ArrayList<Float> resistanceSamples = new ArrayList<>();

    private static BatteryReading.Snapshot last;
    private static int condition = CONDITION_UNKNOWN;
    private static float windowPeakV = Float.NaN;

    private static long lastSagAt = 0L;
    private static float lastSagDropMv = Float.NaN;

    private static boolean recoveryActive;
    private static long recoveryStartAt;
    private static float recoveryBaselineV;
    private static float recoveryPreviousV;
    private static long recoveryStableSince;
    private static float lastRecoveryMv = Float.NaN;
    private static long lastRecoveryTimeMs = -1L;

    private static long lastResistanceAt = 0L;

    private VoltageStabilityAnalyzer() {}

    public static synchronized void reset() {
        window.clear();
        resistanceSamples.clear();
        last = null;
        condition = CONDITION_UNKNOWN;
        windowPeakV = Float.NaN;
        lastSagAt = 0L;
        lastSagDropMv = Float.NaN;
        recoveryActive = false;
        recoveryStartAt = 0L;
        recoveryBaselineV = 0f;
        recoveryPreviousV = 0f;
        recoveryStableSince = 0L;
        lastRecoveryMv = Float.NaN;
        lastRecoveryTimeMs = -1L;
        lastResistanceAt = 0L;
    }

    public static synchronized void onSample(BatteryReading.Snapshot s) {
        if (s == null || s.time <= 0L || s.voltageV <= 0f) return;

        int nextCondition = classify(s);
        BatteryReading.Snapshot prev = last;
        int prevCondition = condition;

        if (nextCondition == CONDITION_CHARGING) {
            if (recoveryActive && prev != null) finishRecovery(prev, prev.time);
            lastSagAt = 0L;
            lastSagDropMv = Float.NaN;
            window.clear();
        } else if (prev != null && prevCondition != CONDITION_CHARGING) {
            long dt = s.time - prev.time;
            int prevLoad = Math.abs(prev.currentMa);
            int load = Math.abs(s.currentMa);
            int deltaMa = load - prevLoad;
            float deltaV = s.voltageV - prev.voltageV;

            if (dt > 0L && dt <= MAX_PAIR_MS) {
                if (deltaMa >= MIN_DELTA_MA && deltaV <= -MIN_DELTA_MV / 1000f) {
                    lastSagAt = s.time;
                    lastSagDropMv = -deltaV * 1000f;
                }
                if (Math.abs(deltaMa) >= MIN_DELTA_MA
                        && Math.abs(deltaV) * 1000f >= MIN_DELTA_MV) {
                    recordResistance(prev, s, deltaMa, deltaV, dt);
                }
                if (recoveryActive && deltaMa >= MIN_DELTA_MA) {
                    finishRecovery(prev, prev.time);
                } else if (deltaMa <= -MIN_DELTA_MA) {
                    startRecovery(prev, s);
                }
            }

            if (recoveryActive) updateRecovery(s);
        }

        if (nextCondition != condition) {
            window.clear();
            windowPeakV = s.voltageV;
        }
        if (nextCondition != CONDITION_CHARGING) {
            window.addLast(s);
            while (window.size() > MAX_WINDOW) window.removeFirst();
            if (nextCondition == CONDITION_DISCHARGING
                    && (Float.isNaN(windowPeakV) || s.voltageV > windowPeakV)) {
                windowPeakV = s.voltageV;
            }
        }

        condition = nextCondition;
        last = s;
    }

    public static int classify(BatteryReading.Snapshot s) {
        if (s == null) return CONDITION_UNKNOWN;
        if (s.isCharging() || s.pluggedInt != 0) return CONDITION_CHARGING;
        int load = Math.abs(s.currentMa);
        if (load <= IDLE_LOAD_MA) return CONDITION_IDLE;
        if (load >= DISCHARGE_LOAD_MA) return CONDITION_DISCHARGING;
        return CONDITION_IDLE;
    }

    public static synchronized State getState() {
        if (last == null) return State.empty();

        float variance = voltageVarianceMv2();
        float stdDev = variance >= 0f ? (float) Math.sqrt(variance) : Float.NaN;
        float drop = 0f;

        if (condition == CONDITION_DISCHARGING && !Float.isNaN(windowPeakV)) {
            drop = Math.max(0f, (windowPeakV - last.voltageV) * 1000f);
        } else if (lastSagAt > 0L && last.time - lastSagAt <= SAG_FRESH_MS) {
            drop = lastSagDropMv;
        }

        float resistance = resistanceSamples.isEmpty()
                ? -1f : median(resistanceSamples);

        return new State(condition, last.voltageV, last.currentMa,
                drop, lastRecoveryMv, lastRecoveryTimeMs,
                variance, stdDev, resistance, resistanceSamples.size(),
                measurementStatus(), last.time, window.size());
    }

    public static ArrayList<Point> buildHistory(BatteryReading.Snapshot[] asc, int maxPoints) {
        ArrayList<Point> out = new ArrayList<>();
        if (asc == null || asc.length == 0) return out;

        int limit = Math.max(1, maxPoints);
        int step = Math.max(1, asc.length / limit);
        int lastCondition = -1;

        for (int i = 0; i < asc.length; i++) {
            BatteryReading.Snapshot s = asc[i];
            int c = classify(s);
            boolean transition = lastCondition >= 0 && c != lastCondition;

            if (transition && i > 0) {
                BatteryReading.Snapshot before = asc[i - 1];
                if (out.isEmpty() || out.get(out.size() - 1).time != before.time) {
                    out.add(point(before, classify(before)));
                }
            }
            if (i == 0 || i == asc.length - 1 || i % step == 0 || transition) {
                out.add(point(s, c));
            }
            lastCondition = c;
        }
        return out;
    }

    private static Point point(BatteryReading.Snapshot s, int condition) {
        return new Point(s.time, s.voltageV, s.currentMa, s.percent, condition);
    }

    private static void startRecovery(BatteryReading.Snapshot beforeLoadDrop,
                                      BatteryReading.Snapshot afterLoadDrop) {
        if (recoveryActive) return;
        recoveryActive = true;
        recoveryStartAt = afterLoadDrop.time;
        recoveryBaselineV = beforeLoadDrop.voltageV;
        recoveryPreviousV = afterLoadDrop.voltageV;
        recoveryStableSince = afterLoadDrop.time;
    }

    private static void updateRecovery(BatteryReading.Snapshot s) {
        if (!recoveryActive) return;

        if (s.time - recoveryStartAt >= RECOVERY_TIMEOUT_MS) {
            finishRecovery(s, s.time);
            return;
        }

        float movementMv = Math.abs(s.voltageV - recoveryPreviousV) * 1000f;
        if (movementMv <= RECOVERY_STABLE_MV) {
            if (s.time - recoveryStableSince >= RECOVERY_STABLE_MS) {
                finishRecovery(s, s.time);
            }
        } else {
            recoveryStableSince = s.time;
        }
        recoveryPreviousV = s.voltageV;
    }

    private static void finishRecovery(BatteryReading.Snapshot s, long endTime) {
        if (!recoveryActive) return;
        if (endTime >= recoveryStartAt) {
            lastRecoveryMv = (s.voltageV - recoveryBaselineV) * 1000f;
            lastRecoveryTimeMs = endTime - recoveryStartAt;
        }
        recoveryActive = false;
        recoveryStableSince = 0L;
    }

    private static void recordResistance(BatteryReading.Snapshot prev,
                                         BatteryReading.Snapshot now,
                                         int deltaMa, float deltaV, long dt) {
        if (dt <= 0L || dt > MAX_PAIR_MS) return;
        if (prev.tempC > 0f && now.tempC > 0f
                && Math.abs(now.tempC - prev.tempC) > MAX_TEMP_DELTA_C) {
            return;
        }

        double resistance = Math.abs(deltaV) * 1000.0 / Math.abs(deltaMa);
        if (resistance < 1.0 || resistance > 5000.0) return;
        if (now.time - lastResistanceAt < RESISTANCE_COOLDOWN_MS) return;

        resistanceSamples.add((float) resistance);
        while (resistanceSamples.size() > MAX_RESISTANCE_SAMPLES) {
            resistanceSamples.remove(0);
        }
        lastResistanceAt = now.time;
    }

    private static float voltageVarianceMv2() {
        int n = window.size();
        if (n < 2) return Float.NaN;

        double sum = 0d;
        double sumSq = 0d;
        for (BatteryReading.Snapshot s : window) {
            double mv = s.voltageV * 1000d;
            sum += mv;
            sumSq += mv * mv;
        }
        double mean = sum / n;
        double variance = (sumSq / n) - (mean * mean);
        return (float) Math.max(0d, variance);
    }

    private static float median(ArrayList<Float> values) {
        ArrayList<Float> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int n = copy.size();
        if (n == 0) return -1f;
        if (n % 2 == 1) return copy.get(n / 2);
        return (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2f;
    }

    private static String measurementStatus() {
        if (last == null) return "Menunggu data baterai";
        if (condition == CONDITION_CHARGING) {
            return "Charging — analisis discharge dipisahkan";
        }
        if (window.size() < 5) return "Mengumpulkan sampel";
        if (last.currentMa == 0) {
            return "Arus tidak tersedia — estimasi beban belum valid";
        }
        if (recoveryActive) return "Pemulihan tegangan sedang diamati";
        if (!resistanceSamples.isEmpty()
                && last.time - lastResistanceAt <= SAG_FRESH_MS) {
            return "Valid — perubahan beban terukur";
        }
        if (lastSagAt > 0L && last.time - lastSagAt <= SAG_FRESH_MS) {
            return "Perubahan beban terdeteksi";
        }
        return "Stabil — belum ada perubahan beban besar";
    }

    public static final class State {
        public final int condition;
        public final float voltageV;
        public final int currentMa;
        public final float voltageDropMv;
        public final float recoveryMv;
        public final long recoveryTimeMs;
        public final float voltageVarianceMv2;
        public final float voltageStdDevMv;
        public final float resistanceMohm;
        public final int resistanceSampleCount;
        public final String measurementStatus;
        public final long updatedAt;
        public final int observationSamples;

        State(int condition, float voltageV, int currentMa,
              float voltageDropMv, float recoveryMv, long recoveryTimeMs,
              float voltageVarianceMv2, float voltageStdDevMv,
              float resistanceMohm, int resistanceSampleCount,
              String measurementStatus, long updatedAt, int observationSamples) {
            this.condition = condition;
            this.voltageV = voltageV;
            this.currentMa = currentMa;
            this.voltageDropMv = voltageDropMv;
            this.recoveryMv = recoveryMv;
            this.recoveryTimeMs = recoveryTimeMs;
            this.voltageVarianceMv2 = voltageVarianceMv2;
            this.voltageStdDevMv = voltageStdDevMv;
            this.resistanceMohm = resistanceMohm;
            this.resistanceSampleCount = resistanceSampleCount;
            this.measurementStatus = measurementStatus;
            this.updatedAt = updatedAt;
            this.observationSamples = observationSamples;
        }

        static State empty() {
            return new State(CONDITION_UNKNOWN, 0f, 0, 0f,
                    Float.NaN, -1L, Float.NaN, Float.NaN,
                    -1f, 0, "Menunggu data baterai", 0L, 0);
        }

        public String conditionLabel() {
            switch (condition) {
                case CONDITION_CHARGING: return "Charging";
                case CONDITION_DISCHARGING: return "Discharging";
                case CONDITION_IDLE: return "Idle / Recovery";
                default: return "Menunggu";
            }
        }
    }

    public static final class Point {
        public final long time;
        public final float voltageV;
        public final int currentMa;
        public final int percent;
        public final int condition;

        Point(long time, float voltageV, int currentMa, int percent, int condition) {
            this.time = time;
            this.voltageV = voltageV;
            this.currentMa = currentMa;
            this.percent = percent;
            this.condition = condition;
        }
    }
}
