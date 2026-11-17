package exp.ftxt.shared.ui;

import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * Pelacak inset IME bersama agar posisi overlay bisa menghindari keyboard.
 */
public final class OverlayInsets {

    public static final int BEHAVIOR_STAY = 0;
    public static final int BEHAVIOR_HIDE = 1;
    public static final int BEHAVIOR_SHIFT = 2;

    private static int imeBottomPx;
    private static int behavior = BEHAVIOR_STAY;

    private OverlayInsets() {
    }

    public static int imeBottom() {
        return imeBottomPx;
    }

    public static void setBehavior(int value) {
        behavior = (value >= BEHAVIOR_STAY && value <= BEHAVIOR_SHIFT)
                ? value : BEHAVIOR_STAY;
    }

    public static int behavior() {
        return behavior;
    }

    public static boolean shouldHide() {
        return behavior == BEHAVIOR_HIDE && imeBottomPx > 0;
    }

    /**
     * Baca tinggi keyboard saat ini. Mengembalikan true jika nilainya berubah.
     */
    public static boolean refresh(WindowManager wm) {
        int bottom = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            try {
                WindowInsets insets = wm.getCurrentWindowMetrics().getWindowInsets();
                bottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
            } catch (Throwable ignored) {
            }
        }
        if (bottom == imeBottomPx) return false;
        imeBottomPx = bottom;
        return true;
    }

    public static int clampY(int y, int screenHeight, int viewHeight) {
        if (behavior != BEHAVIOR_SHIFT || imeBottomPx <= 0 || viewHeight <= 0) return y;
        int maxY = Math.max(0, screenHeight - imeBottomPx - viewHeight);
        return Math.min(y, maxY);
    }
}
