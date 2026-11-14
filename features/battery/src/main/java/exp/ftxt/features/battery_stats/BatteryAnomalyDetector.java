package exp.ftxt.features.battery_stats;

import java.util.ArrayList;

public final class BatteryAnomalyDetector {

    private BatteryAnomalyDetector() {}

    public static ArrayList<BatteryAnomaly> detect(BatteryReading.Snapshot prev,
                                                   BatteryReading.Snapshot cur,
                                                   BatteryDataQuality.Quality quality) {
        ArrayList<BatteryAnomaly> out = new ArrayList<>();
        if (cur == null || cur.time <= 0L) return out;

        if (prev == null || prev.time <= 0L) {
            if (cur.voltageV <= 0f || (cur.percent <= 0 && cur.voltageV <= 0f)) {
                out.add(new BatteryAnomaly(cur.time,
                        BatteryAnomaly.TYPE_SENSOR_UNAVAILABLE,
                        BatteryAnomaly.SEVERITY_MEDIUM, null, cur,
                        0d, cur.voltageV, "data sensor baterai tidak tersedia"));
            } else if (quality != null && quality.status == BatteryDataQuality.INVALID) {
                out.add(new BatteryAnomaly(cur.time,
                        BatteryAnomaly.TYPE_SENSOR_UNAVAILABLE,
                        BatteryAnomaly.SEVERITY_MEDIUM, null, cur,
                        0d, cur.voltageV, quality.reason));
            }
            return out;
        }

        long dt = cur.time - prev.time;
        boolean chargerTransition = cur.isCharging() != prev.isCharging()
                || cur.pluggedInt != prev.pluggedInt;

        if (dt > BatteryDataQuality.MAX_CONTINUOUS_GAP_MS) {
            int severity = dt > 900_000L
                    ? BatteryAnomaly.SEVERITY_HIGH
                    : BatteryAnomaly.SEVERITY_MEDIUM;
            out.add(new BatteryAnomaly(cur.time,
                    BatteryAnomaly.TYPE_DATA_GAP, severity, prev, cur,
                    0d, dt / 1000d,
                    "jeda pencatatan " + (dt / 1000L) + " detik"));
        }

        int percentDelta = Math.abs(cur.percent - prev.percent);
        if (percentDelta >= BatteryDataQuality.PERCENT_JUMP_THRESHOLD) {
            int severity;
            String reason;
            if (dt > BatteryDataQuality.MAX_CONTINUOUS_GAP_MS) {
                severity = chargerTransition
                        ? BatteryAnomaly.SEVERITY_LOW
                        : BatteryAnomaly.SEVERITY_HIGH;
                reason = "persen berubah " + prev.percent + "->" + cur.percent
                        + " setelah jeda " + (dt / 1000L) + " detik";
            } else if (dt > 0L && dt <= BatteryDataQuality.JUMP_WINDOW_MS) {
                severity = chargerTransition
                        ? BatteryAnomaly.SEVERITY_LOW
                        : percentDelta >= 30 ? BatteryAnomaly.SEVERITY_HIGH
                        : BatteryAnomaly.SEVERITY_MEDIUM;
                reason = "persen berubah " + prev.percent + "->" + cur.percent
                        + " dalam " + Math.max(1L, dt / 1000L) + " detik";
            } else {
                severity = BatteryAnomaly.SEVERITY_LOW;
                reason = "persen berubah " + prev.percent + "->" + cur.percent;
            }
            out.add(new BatteryAnomaly(cur.time,
                    BatteryAnomaly.TYPE_PERCENT_JUMP, severity, prev, cur,
                    prev.percent, cur.percent, reason));
        }

        if (dt > 0L && dt <= BatteryDataQuality.VOLTAGE_JUMP_WINDOW_MS) {
            float voltageDelta = Math.abs(cur.voltageV - prev.voltageV);
            if (voltageDelta >= BatteryDataQuality.VOLTAGE_JUMP_THRESHOLD_V) {
                int severity = voltageDelta >= 0.5f
                        ? BatteryAnomaly.SEVERITY_HIGH
                        : BatteryAnomaly.SEVERITY_MEDIUM;
                out.add(new BatteryAnomaly(cur.time,
                        BatteryAnomaly.TYPE_VOLTAGE_JUMP, severity, prev, cur,
                        prev.voltageV, cur.voltageV,
                        "tegangan berubah " + String.format("%.2f", prev.voltageV)
                                + "->" + String.format("%.2f", cur.voltageV) + " V"));
            }
        }

        if (prev.chargeMah > 0L && cur.chargeMah < 0L) {
            out.add(new BatteryAnomaly(cur.time,
                    BatteryAnomaly.TYPE_COUNTER_RESET,
                    BatteryAnomaly.SEVERITY_MEDIUM, prev, cur,
                    prev.chargeMah, cur.chargeMah, "charge counter reset"));
        }

        if (cur.isCharging() && cur.pluggedInt == 0) {
            out.add(new BatteryAnomaly(cur.time,
                    BatteryAnomaly.TYPE_STATUS_PLUGGED_MISMATCH,
                    BatteryAnomaly.SEVERITY_MEDIUM, prev, cur,
                    prev.pluggedInt, cur.pluggedInt,
                    "status charging tanpa charger terpasang"));
        }

        if (prev.voltageV > 0f && cur.voltageV <= 0f) {
            out.add(new BatteryAnomaly(cur.time,
                    BatteryAnomaly.TYPE_SENSOR_UNAVAILABLE,
                    BatteryAnomaly.SEVERITY_MEDIUM, prev, cur,
                    prev.voltageV, cur.voltageV, "tegangan tiba-tiba tidak tersedia"));
        }

        if (quality != null && quality.status == BatteryDataQuality.INVALID) {
            String reason = quality.reason != null ? quality.reason : "sampel tidak valid";
            out.add(new BatteryAnomaly(cur.time,
                    BatteryAnomaly.TYPE_SENSOR_UNAVAILABLE,
                    BatteryAnomaly.SEVERITY_MEDIUM, prev, cur,
                    prev.voltageV, cur.voltageV, reason));
        }

        return out;
    }
}
