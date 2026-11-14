package exp.ftxt.features.battery_stats;

import android.content.Context;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Deteksi mode pengisian bypass. Status Smart Panel dibaca lebih dulu dari
 * Settings.Global; heuristik sensor, node sysfs, dan dumpsys battery via
 * Shizuku menjadi fallback pada perangkat tanpa status tersebut.
 *
 * <p>Kondisi {@code NOT_CHARGING} di level tinggi diperlakukan sebagai batas
 * cas / charging pause agar battery care tidak salah dibaca sebagai bypass.</p>
 */
public final class BypassChargingDetector {

    public static final int STATE_DISABLED = 0;
    public static final int STATE_UNKNOWN = 1;
    public static final int STATE_NOT_PLUGGED = 2;
    public static final int STATE_CHARGING = 3;
    public static final int STATE_FULL = 4;
    public static final int STATE_SUSPECT = 5;
    public static final int STATE_BYPASS = 6;
    public static final int STATE_CHARGE_LIMIT = 7;

    private static final long CONFIRM_MS = 45_000L;
    private static final long VENDOR_CONFIRM_MS = 5_000L;
    private static final long NODE_CACHE_MS = 15_000L;
    private static final long SMART_PANEL_HOLD_MS = 8_000L;
    private static final int NEAR_ZERO_MA = 80;
    private static final int CHARGE_LIMIT_LEVEL = 80;

    private static final String SMART_PANEL_ENABLED = "game_bypass_charging";
    private static final String SMART_PANEL_ACTIVE = "os_bypass_status";
    private static final int SMART_PANEL_NONE = 0;
    private static final int SMART_PANEL_ARMED = 1;
    private static final int SMART_PANEL_ON = 2;

    private static volatile State state = State.empty();

    private static long suspectSince;
    private static long vendorSince;
    private static long smartPanelSince;
    private static long smartPanelUntil;

    private static long lastNodeRead;
    private static String chargeType;
    private static boolean chargingDisabled;
    private static boolean inputSuspended;

    private BypassChargingDetector() {}

    public static synchronized void reset() {
        state = State.empty();
        suspectSince = 0L;
        vendorSince = 0L;
        smartPanelSince = 0L;
        smartPanelUntil = 0L;
        lastNodeRead = 0L;
        chargeType = null;
        chargingDisabled = false;
        inputSuspended = false;
    }

    public static State getState() {
        return state;
    }

    public static synchronized void onSample(Context context, BatteryReading.Snapshot s) {
        if (!BatteryStatsConfig.bypassDetection) {
            if (state.state != STATE_DISABLED) {
                reset();
                state = new State(STATE_DISABLED, 0L, 0, 0, false, false);
            }
            return;
        }
        if (s == null || s.time <= 0L) {
            reset();
            return;
        }

        boolean plugged = s.pluggedInt != 0;
        DumpsysBatteryProbe.Data dump = plugged ? DumpsysBatteryProbe.get(true) : null;

        int status = dump != null && dump.hasStatus() ? dump.status : s.statusInt;
        int level = dump != null && dump.hasLevel() && dump.percent() >= 0
                ? dump.percent() : s.percent;
        int currentMa = dump != null && dump.hasCurrent ? dump.currentMa : s.currentMa;
        boolean currentKnown = (dump != null && dump.hasCurrent) || s.currentMa != 0;

        if (!plugged && dump != null && dump.hasPlugged()) {
            plugged = true;
        }
        if (!plugged) {
            reset();
            state = new State(STATE_NOT_PLUGGED, 0L, level, currentMa, currentKnown, false);
            return;
        }

        refreshNodes();

        long now = SystemClock.elapsedRealtime();
        int smartPanel = readSmartPanelState(context);

        if (smartPanel == SMART_PANEL_ON) {
            if (smartPanelSince == 0L) smartPanelSince = now;
            smartPanelUntil = now + SMART_PANEL_HOLD_MS;
            suspectSince = 0L;
            vendorSince = 0L;
            state = new State(STATE_BYPASS, smartPanelSince, level, currentMa, currentKnown, true, 3);
            return;
        }
        if (smartPanel == SMART_PANEL_ARMED && now < smartPanelUntil) {
            suspectSince = 0L;
            vendorSince = 0L;
            state = new State(STATE_BYPASS, smartPanelSince, level, currentMa, currentKnown, true, 3);
            return;
        }
        smartPanelSince = 0L;
        smartPanelUntil = 0L;

        boolean vendorSign = containsBypass(chargeType)
                || chargingDisabled
                || inputSuspended
                || (dump != null && dump.bypassHint);

        if (vendorSign) {
            if (vendorSince == 0L) vendorSince = now;
            suspectSince = 0L;
            long since = vendorSince;
            if (now - vendorSince >= VENDOR_CONFIRM_MS) {
                state = new State(STATE_BYPASS, since, level, currentMa, currentKnown, true);
            } else {
                state = new State(STATE_SUSPECT, since, level, currentMa, currentKnown, true);
            }
            return;
        }
        vendorSince = 0L;

        if (status == BatteryManager.BATTERY_STATUS_FULL) {
            suspectSince = 0L;
            state = new State(STATE_FULL, 0L, level, currentMa, currentKnown, false);
            return;
        }

        boolean indication = false;
        int source = 0;

        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            boolean nearZero = currentKnown
                    && Math.abs(currentMa) <= NEAR_ZERO_MA && level < CHARGE_LIMIT_LEVEL;
            if (nearZero) {
                indication = true;
                source = 2;
            }
        } else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            if (level >= CHARGE_LIMIT_LEVEL) {
                suspectSince = 0L;
                state = new State(STATE_CHARGE_LIMIT, 0L, level, currentMa, currentKnown, false);
                return;
            }
            // Arus baterai yang masih besar saat adaptor terpasang lebih mungkin
            // adaptor lemah, bukan bypass. Bypass membuat arus baterai mendekati nol.
            boolean draining = currentKnown && Math.abs(currentMa) > NEAR_ZERO_MA;
            if (!draining) {
                indication = true;
                source = 1;
            }
        }

        if (!indication) {
            suspectSince = 0L;
            int normal = status == BatteryManager.BATTERY_STATUS_CHARGING
                    ? STATE_CHARGING : STATE_UNKNOWN;
            state = new State(normal, 0L, level, currentMa, currentKnown, false);
            return;
        }

        if (suspectSince == 0L) suspectSince = now;
        if (now - suspectSince >= CONFIRM_MS) {
            state = new State(STATE_BYPASS, suspectSince, level, currentMa, currentKnown, false);
        } else {
            state = new State(STATE_SUSPECT, suspectSince, level, currentMa, currentKnown, false);
        }
        state = state.withSource(source);
    }

    private static void refreshNodes() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNodeRead < NODE_CACHE_MS) return;
        lastNodeRead = now;
        chargeType = BatteryReading.readSysfsText("charge_type");
        String enabled = BatteryReading.readSysfsText("battery_charging_enabled");
        if (enabled == null) enabled = BatteryReading.readSysfsText("charging_enabled");
        chargingDisabled = enabled != null && "0".equals(enabled.trim());
        String suspend = BatteryReading.readSysfsText("input_suspend");
        inputSuspended = suspend != null && "1".equals(suspend.trim());
    }

    private static int readSmartPanelState(Context context) {
        try {
            if (Settings.Global.getInt(context.getContentResolver(),
                    SMART_PANEL_ENABLED, 0) != 1) {
                return SMART_PANEL_NONE;
            }
            return Settings.Global.getInt(context.getContentResolver(),
                    SMART_PANEL_ACTIVE, 0) == 1
                    ? SMART_PANEL_ON : SMART_PANEL_ARMED;
        } catch (Exception ignored) {
            return SMART_PANEL_NONE;
        }
    }

    private static boolean containsBypass(String value) {
        return value != null && value.toLowerCase().contains("bypass");
    }

    public static final class State {
        public final int state;
        public final long since;
        public final int level;
        public final int currentMa;
        public final boolean currentKnown;
        public final boolean vendorSign;
        /** 0 = sensor, 1 = status/level, 2 = arus, 3 = Smart Panel. */
        public final int source;

        State(int state, long since, int level, int currentMa,
              boolean currentKnown, boolean vendorSign) {
            this(state, since, level, currentMa, currentKnown, vendorSign, 0);
        }

        State(int state, long since, int level, int currentMa,
              boolean currentKnown, boolean vendorSign, int source) {
            this.state = state;
            this.since = since;
            this.level = level;
            this.currentMa = currentMa;
            this.currentKnown = currentKnown;
            this.vendorSign = vendorSign;
            this.source = source;
        }

        State withSource(int source) {
            return new State(state, since, level, currentMa, currentKnown, vendorSign, source);
        }

        static State empty() {
            return new State(STATE_UNKNOWN, 0L, 0, 0, false, false);
        }

        public boolean isBypass() {
            return state == STATE_BYPASS;
        }

        public boolean isActive() {
            return state == STATE_BYPASS || state == STATE_SUSPECT
                    || state == STATE_CHARGE_LIMIT;
        }

        /** Label pendek untuk overlay/monitor, null bila tidak perlu ditampilkan. */
        public String label() {
            switch (state) {
                case STATE_BYPASS: return "Bypass";
                case STATE_SUSPECT: return "Bypass?";
                case STATE_CHARGE_LIMIT: return "Batas Cas";
                default: return null;
            }
        }
    }
}
