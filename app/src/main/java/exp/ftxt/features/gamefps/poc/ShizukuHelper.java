package exp.ftxt.features.gamefps.poc;

import android.content.pm.PackageManager;
import android.util.Log;

import rikka.shizuku.Shizuku;

public class ShizukuHelper {
    private static final String TAG = "GameFpsPoC";

    public static boolean isBinderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            Log.w(TAG, "pingBinder failed", t);
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            if (!isBinderAlive()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            Log.w(TAG, "checkSelfPermission failed", t);
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            if (isBinderAlive()) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable t) {
            Log.w(TAG, "requestPermission failed", t);
        }
    }

    public static boolean shouldShowRequestPermissionRationale() {
        try {
            return Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable t) {
            return false;
        }
    }
}
