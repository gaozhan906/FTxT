package exp.ftxt.features.battery_stats;

public final class BatteryAnomaly {

    public static final String TYPE_DATA_GAP = "DATA_GAP";
    public static final String TYPE_PERCENT_JUMP = "PERCENT_JUMP";
    public static final String TYPE_VOLTAGE_JUMP = "VOLTAGE_JUMP";
    public static final String TYPE_COUNTER_RESET = "COUNTER_RESET";
    public static final String TYPE_STATUS_PLUGGED_MISMATCH = "STATUS_PLUGGED_MISMATCH";
    public static final String TYPE_SENSOR_UNAVAILABLE = "SENSOR_UNAVAILABLE";

    public static final int SEVERITY_LOW = 1;
    public static final int SEVERITY_MEDIUM = 2;
    public static final int SEVERITY_HIGH = 3;

    public final long time;
    public final String type;
    public final int severity;
    public final int percentBefore;
    public final int percentAfter;
    public final float voltageBefore;
    public final float voltageAfter;
    public final int currentBefore;
    public final int currentAfter;
    public final int statusBefore;
    public final int statusAfter;
    public final double prevValue;
    public final double newValue;
    public final String reason;

    public BatteryAnomaly(long time, String type, int severity,
                          BatteryReading.Snapshot before, BatteryReading.Snapshot after,
                          double prevValue, double newValue, String reason) {
        this.time = time;
        this.type = type;
        this.severity = severity;
        this.percentBefore = before != null ? before.percent : -1;
        this.percentAfter = after != null ? after.percent : -1;
        this.voltageBefore = before != null ? before.voltageV : 0f;
        this.voltageAfter = after != null ? after.voltageV : 0f;
        this.currentBefore = before != null ? before.currentMa : 0;
        this.currentAfter = after != null ? after.currentMa : 0;
        this.statusBefore = before != null ? before.statusInt : 0;
        this.statusAfter = after != null ? after.statusInt : 0;
        this.prevValue = prevValue;
        this.newValue = newValue;
        this.reason = reason;
    }
}
