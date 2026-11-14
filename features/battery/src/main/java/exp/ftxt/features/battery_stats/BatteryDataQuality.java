package exp.ftxt.features.battery_stats;

public final class BatteryDataQuality {

    public static final int UNKNOWN = 0;
    public static final int VALID = 1;
    public static final int INCOMPLETE = 2;
    public static final int SUSPICIOUS = 3;
    public static final int INVALID = 4;

    public static final int FLAG_TIME_INVALID = 1;
    public static final int FLAG_PERCENT_RANGE = 1 << 1;
    public static final int FLAG_VOLTAGE_MISSING = 1 << 2;
    public static final int FLAG_TEMP_MISSING = 1 << 3;
    public static final int FLAG_CURRENT_MISSING = 1 << 4;
    public static final int FLAG_COUNTER_MISSING = 1 << 5;
    public static final int FLAG_GAP = 1 << 6;
    public static final int FLAG_PERCENT_JUMP = 1 << 7;
    public static final int FLAG_VOLTAGE_JUMP = 1 << 8;
    public static final int FLAG_COUNTER_RESET = 1 << 9;
    public static final int FLAG_STATUS_PLUGGED_MISMATCH = 1 << 10;
    public static final int FLAG_CURRENT_ZERO_CHARGING = 1 << 11;

    public static final long MAX_CONTINUOUS_GAP_MS = 60_000L;
    public static final long JUMP_WINDOW_MS = 60_000L;
    public static final long VOLTAGE_JUMP_WINDOW_MS = 6_000L;
    public static final float PERCENT_JUMP_THRESHOLD = 20f;
    public static final float VOLTAGE_JUMP_THRESHOLD_V = 0.3f;
    public static final float MIN_PLAUSIBLE_VOLTAGE_V = 2.5f;
    public static final float MAX_PLAUSIBLE_VOLTAGE_V = 5.5f;
    public static final float MAX_CAPACITY_RATIO = 1.5f;
    public static final float MIN_CAPACITY_RATIO = 0.2f;

    private static final int INVALID_FLAGS = FLAG_TIME_INVALID | FLAG_PERCENT_RANGE;
    private static final int SUSPICIOUS_FLAGS = FLAG_GAP | FLAG_PERCENT_JUMP
            | FLAG_VOLTAGE_JUMP | FLAG_COUNTER_RESET | FLAG_STATUS_PLUGGED_MISMATCH;

    private BatteryDataQuality() {}

    public static final class Quality {
        public final int status;
        public final int flags;
        public final String reason;

        Quality(int status, int flags, String reason) {
            this.status = status;
            this.flags = flags;
            this.reason = reason;
        }

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        public boolean isUsable() {
            return status == VALID;
        }

        public boolean isStorable() {
            return status != INVALID;
        }
    }

    public static Quality evaluate(BatteryReading.Snapshot prev, BatteryReading.Snapshot cur) {
        if (cur == null) {
            return new Quality(INVALID, FLAG_TIME_INVALID, "sampel null");
        }

        int flags = 0;
        StringBuilder reason = new StringBuilder();

        if (cur.time <= 0L) {
            flags |= FLAG_TIME_INVALID;
            append(reason, "waktu tidak valid");
        }
        if (cur.percent < 0 || cur.percent > 100) {
            flags |= FLAG_PERCENT_RANGE;
            append(reason, "persen di luar 0-100");
        }
        if (cur.voltageV <= 0f) {
            flags |= FLAG_VOLTAGE_MISSING;
            append(reason, "tegangan tidak tersedia");
        }
        if (cur.tempC <= 0f) {
            flags |= FLAG_TEMP_MISSING;
            append(reason, "suhu tidak tersedia");
        }
        if (cur.currentMa == 0 && cur.isCharging()) {
            flags |= FLAG_CURRENT_ZERO_CHARGING;
            append(reason, "arus 0 saat charging");
        }
        if (cur.chargeMah < 0L) {
            flags |= FLAG_COUNTER_MISSING;
            append(reason, "charge counter tidak tersedia");
        }
        if (cur.isCharging() && cur.pluggedInt == 0) {
            flags |= FLAG_STATUS_PLUGGED_MISMATCH;
            append(reason, "status charging tanpa charger terpasang");
        }

        if (prev != null && prev.time > 0L) {
            long dt = cur.time - prev.time;
            if (dt <= 0L) {
                flags |= FLAG_TIME_INVALID;
                append(reason, "waktu tidak monoton");
            } else if (dt > MAX_CONTINUOUS_GAP_MS) {
                flags |= FLAG_GAP;
                append(reason, "jeda data " + (dt / 1000L) + " detik");
            }

            if (dt > 0L && dt <= JUMP_WINDOW_MS
                    && Math.abs(cur.percent - prev.percent) >= PERCENT_JUMP_THRESHOLD) {
                flags |= FLAG_PERCENT_JUMP;
                append(reason, "lonjakan persen " + prev.percent
                        + "->" + cur.percent);
            }
            if (dt > 0L && dt <= VOLTAGE_JUMP_WINDOW_MS
                    && Math.abs(cur.voltageV - prev.voltageV) >= VOLTAGE_JUMP_THRESHOLD_V) {
                flags |= FLAG_VOLTAGE_JUMP;
                append(reason, "lonjakan tegangan");
            }
            if (prev.chargeMah > 0L && cur.chargeMah < 0L) {
                flags |= FLAG_COUNTER_RESET;
                append(reason, "charge counter reset");
            }
        }

        int status;
        if ((flags & INVALID_FLAGS) != 0) {
            status = INVALID;
        } else if ((flags & SUSPICIOUS_FLAGS) != 0) {
            status = SUSPICIOUS;
        } else if (flags != 0) {
            status = INCOMPLETE;
        } else {
            status = VALID;
        }
        return new Quality(status, flags, reason.length() == 0 ? null : reason.toString());
    }

    public static String segmentRejectReason(SessionSegmentBuilder.Segment seg, int designMah) {
        if (seg == null) return "segmen null";
        if (seg.direction == SessionSegmentBuilder.Direction.FLAT) return "arah datar";
        if (seg.startPercent < 0 || seg.endPercent < 0) return "persen tidak lengkap";
        if (seg.direction == SessionSegmentBuilder.Direction.CHARGE
                && seg.endPercent < seg.startPercent) {
            return "arah charge tidak konsisten";
        }
        if (seg.direction == SessionSegmentBuilder.Direction.DISCHARGE
                && seg.endPercent > seg.startPercent) {
            return "arah discharge tidak konsisten";
        }
        if (seg.durationMs() < 60_000L) return "durasi < 1 menit";
        if (seg.maxGapMs >= MAX_CONTINUOUS_GAP_MS) return "gap data >= 60 detik";
        if (seg.mAhIntegral <= 0d) return "integral arus kosong";

        float dPercent = Math.abs(seg.endPercent - seg.startPercent);
        if (dPercent < 5f) return "delta persen < 5%";

        if (seg.voltageMin > 0f
                && (seg.voltageMin < MIN_PLAUSIBLE_VOLTAGE_V
                || seg.voltageMax > MAX_PLAUSIBLE_VOLTAGE_V)) {
            return "tegangan di luar rentang wajar";
        }
        if (seg.counterAvailable) {
            if (seg.direction == SessionSegmentBuilder.Direction.CHARGE
                    && seg.deltaChargeMah < -1d) {
                return "counter turun saat charging";
            }
            if (seg.direction == SessionSegmentBuilder.Direction.DISCHARGE
                    && seg.deltaChargeMah > 1d) {
                return "counter naik saat discharging";
            }
        }
        if (seg.direction == SessionSegmentBuilder.Direction.CHARGE
                && seg.startPercent >= 95) {
            return "top-off (mulai >= 95%)";
        }

        float estimate = (float) (seg.mAhIntegral * 100d / dPercent);
        if (estimate < 500f || estimate > 30000f) return "estimasi di luar rentang";
        if (designMah > 0) {
            if (estimate > designMah * MAX_CAPACITY_RATIO) {
                return "estimasi > 150% design";
            }
            if (estimate < designMah * MIN_CAPACITY_RATIO) {
                return "estimasi < 20% design";
            }
        }
        return null;
    }

    private static void append(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(text);
    }
}
