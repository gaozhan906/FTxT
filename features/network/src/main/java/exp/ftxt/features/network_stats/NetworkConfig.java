package exp.ftxt.features.network_stats;

import android.graphics.Color;

import exp.ftxt.shared.config.BackgroundConfig;
import exp.ftxt.shared.config.ShadowConfig;

public class NetworkConfig {
    public static final int DEFAULT_WIDTH_DP = 120;
    public static final int MIN_WIDTH_DP = 10;
    public static final int MAX_WIDTH_DP = 160;
    public static final int DEFAULT_BAR_COLOR_DOWN = 0x804CAF50;
    public static final int DEFAULT_BAR_COLOR_UP = 0x802196F3;

    public static boolean enabled = false;
    public static float size = 12f;
    public static int color = Color.GREEN;
    public static int labelColor = Color.CYAN;
    public static boolean textBold = false;
    public static boolean textItalic = false;
    public static final int UNIT_KB = 0;
    public static final int UNIT_BPS = 1;
    public static boolean showDecimal = false;
    public static int decimalPlaces = 1;
    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;
    public static int textAlign = ALIGN_CENTER;
    public static int unitMode = UNIT_KB;
    public static boolean unitTrimmed = false;
    public static int barColorDown = DEFAULT_BAR_COLOR_DOWN;
    public static int barColorUp = DEFAULT_BAR_COLOR_UP;
    public static boolean barEnabled = true;
    public static boolean barInverted = false;
    public static boolean verticalLayout = false;
    public static int displayWidthDp = DEFAULT_WIDTH_DP;
    public static ShadowConfig shadow = new ShadowConfig();
    public static BackgroundConfig bg = new BackgroundConfig();
    public static boolean touchPassthrough = true;
    public static boolean safeArea = true;
    public static boolean showOnlyValue = false;
    public static float posX = 0.75f;
    public static float posY = 0.05f;
    public static float updateInterval = 1f;
}
