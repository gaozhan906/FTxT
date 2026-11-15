package exp.ftxt.features.network_stats.app_usage;

import android.graphics.Color;

import exp.ftxt.shared.config.BackgroundConfig;
import exp.ftxt.shared.config.ShadowConfig;

public class AppUsageConfig {
    public static final int MAX_ROWS = 5;

    public static boolean enabled = false;
    public static float size = 12f;
    public static int color = Color.GREEN;
    public static int labelColor = Color.CYAN;
    public static boolean textBold = false;
    public static boolean textItalic = false;

    public static final int UNIT_KB = 0;
    public static final int UNIT_BPS = 1;
    public static int unitMode = UNIT_KB;
    public static boolean unitTrimmed = false;
    public static boolean showDecimal = false;
    public static int decimalPlaces = 1;

    public static int maxRows = 3;
    public static int mode = ConnectingAppSource.MODE_TOP_TALKERS;
    public static float updateInterval = 1f;
    public static long thresholdBps = 1024L;
    public static int holdMs = 3000;
    public static boolean showIcon = true;
    public static boolean showWhenIdle = true;

    public static ShadowConfig shadow = new ShadowConfig();
    public static BackgroundConfig bg = new BackgroundConfig();
    public static boolean touchPassthrough = true;
    public static boolean safeArea = true;
    public static float posX = 0.5f;
    public static float posY = 0.12f;
}
