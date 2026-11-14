package exp.ftxt.features.battery_stats;

import android.os.BatteryManager;
import android.os.SystemClock;

import java.util.Locale;

import exp.ftxt.features.battery.R;

/**
 * Menentukan ikon status pengisian dari sampel baterai dan status bypass.
 * Kecepatan pengisian memakai {@code charge_type} bila terbaca; bila tidak,
 * estimasi daya/arus dengan hysteresis agar ikon tidak berkedip.
 */
public final class ChargingStatusIcon {

    public static final int NONE = 0;
    public static final int DISCHARGING = 1;
    public static final int CHARGING = 2;
    public static final int CHARGING_FAST = 3;
    public static final int USB = 4;
    public static final int WIRELESS = 5;
    public static final int FULL = 6;
    public static final int BYPASS = 7;
    public static final int BYPASS_SUSPECT = 8;
    public static final int CHARGE_LIMIT = 9;
    public static final int PAUSED = 10;

    private static final long FAST_CONFIRM_MS = 8_000L;
    private static final long NORMAL_CONFIRM_MS = 15_000L;
    private static final long CHARGE_TYPE_CACHE_MS = 15_000L;
    private static final double FAST_POWER_W = 15d;
    private static final int FAST_CURRENT_MA = 3_000;

    private static String chargeType;
    private static long chargeTypeAt;
    private static double smoothedPower;
    private static long fastSince;
    private static long normalSince;
    private static boolean fastLatched;

    private ChargingStatusIcon() {}

    public static synchronized int resolve(BatteryReading.Snapshot s) {
        if (s == null) return NONE;

        if (BatteryStatsConfig.bypassDetection) {
            BypassChargingDetector.State bypass = BypassChargingDetector.getState();
            if (bypass != null) {
                if (bypass.state == BypassChargingDetector.STATE_BYPASS) {
                    resetSpeed();
                    return BYPASS;
                }
                if (bypass.state == BypassChargingDetector.STATE_SUSPECT) {
                    resetSpeed();
                    return BYPASS_SUSPECT;
                }
                if (bypass.state == BypassChargingDetector.STATE_CHARGE_LIMIT) {
                    resetSpeed();
                    return CHARGE_LIMIT;
                }
            }
        }

        if (s.pluggedInt == 0) {
            resetSpeed();
            return DISCHARGING;
        }
        if (s.statusInt == BatteryManager.BATTERY_STATUS_FULL) {
            resetSpeed();
            return FULL;
        }
        if (s.statusInt == BatteryManager.BATTERY_STATUS_CHARGING) {
            if (isFastCharging(s)) return CHARGING_FAST;
            switch (s.pluggedInt) {
                case BatteryManager.BATTERY_PLUGGED_USB:
                    return USB;
                case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                    return WIRELESS;
                default:
                    return CHARGING;
            }
        }
        if (s.statusInt == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || s.statusInt == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            return PAUSED;
        }
        return NONE;
    }

    public static int iconRes(int status) {
        switch (status) {
            case DISCHARGING: return R.drawable.ic_battery_discharging;
            case CHARGING: return R.drawable.ic_charge_normal;
            case CHARGING_FAST: return R.drawable.ic_charge_fast;
            case USB: return R.drawable.ic_usb;
            case WIRELESS: return R.drawable.ic_wireless;
            case FULL: return R.drawable.ic_battery_full;
            case BYPASS:
            case BYPASS_SUSPECT: return R.drawable.ic_bypass;
            case CHARGE_LIMIT:
            case PAUSED: return R.drawable.ic_charge_limit;
            default: return 0;
        }
    }

    public static float iconAlpha(int status) {
        return status == BYPASS_SUSPECT ? 0.6f : 1f;
    }

    private static boolean isFastCharging(BatteryReading.Snapshot s) {
        String type = chargeType();
        if (type != null) {
            String lower = type.toLowerCase(Locale.US);
            if (lower.contains("bypass")) return false;
            if (isFastType(lower)) {
                fastSince = 0L;
                normalSince = 0L;
                fastLatched = true;
                return true;
            }
            if (lower.contains("normal") || lower.contains("slow")
                    || lower.contains("trickle")) {
                resetSpeed();
                return false;
            }
        }

        double power = s.powerW;
        if (power > 0d) {
            smoothedPower = smoothedPower <= 0d
                    ? power : smoothedPower * 0.65d + power * 0.35d;
        } else {
            smoothedPower = 0d;
        }

        boolean rawFast = smoothedPower >= FAST_POWER_W
                || Math.abs(s.currentMa) >= FAST_CURRENT_MA;
        long now = SystemClock.elapsedRealtime();
        if (rawFast) {
            normalSince = 0L;
            if (fastSince == 0L) fastSince = now;
            if (now - fastSince >= FAST_CONFIRM_MS) fastLatched = true;
        } else {
            fastSince = 0L;
            if (normalSince == 0L) normalSince = now;
            if (now - normalSince >= NORMAL_CONFIRM_MS) fastLatched = false;
        }
        return fastLatched;
    }

    private static boolean isFastType(String type) {
        return type.contains("fast") || type.contains("quick") || type.contains("super")
                || type.contains("vooc") || type.contains("flash") || type.contains("warp")
                || type.contains("dart") || type.contains("turbo") || type.contains("hyper")
                || type.contains("pd") || type.contains("pps");
    }

    private static String chargeType() {
        long now = SystemClock.elapsedRealtime();
        if (chargeTypeAt == 0L || now - chargeTypeAt >= CHARGE_TYPE_CACHE_MS) {
            chargeType = BatteryReading.readSysfsText("charge_type");
            chargeTypeAt = now;
        }
        return chargeType;
    }

    private static void resetSpeed() {
        smoothedPower = 0d;
        fastSince = 0L;
        normalSince = 0L;
        fastLatched = false;
    }
}
