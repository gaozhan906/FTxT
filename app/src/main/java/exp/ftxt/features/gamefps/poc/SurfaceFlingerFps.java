package exp.ftxt.features.gamefps.poc;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;

public final class SurfaceFlingerFps {
    private static final String TAG = "GameFpsPoC";
    private static final long STALE_NS = 2_000_000_000L;

    private SurfaceFlingerFps() {}

    public static ArrayList<String> findLayers(String pkg, long timeoutMs) {
        ArrayList<String> layers = new ArrayList<>();
        ShizukuShell.Result res = ShizukuShell.execArgs(
                new String[]{"dumpsys", "SurfaceFlinger", "--list"}, timeoutMs);
        if (!res.ok || res.output == null) {
            Log.d(TAG, "SurfaceFlinger --list failed: " + res.error);
            return layers;
        }
        for (String raw : res.output.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || !line.contains(pkg)) continue;
            if (!layers.contains(line)) layers.add(line);
        }
        Collections.sort(layers, (a, b) -> layerScore(b) - layerScore(a));
        return layers;
    }

    private static int layerScore(String line) {
        if (line.contains("SurfaceView")) return 3;
        if (line.contains("/")) return 2;
        return 1;
    }

    public static float readFps(String layer, long timeoutMs) {
        ShizukuShell.Result res = ShizukuShell.execArgs(
                new String[]{"dumpsys", "SurfaceFlinger", "--latency", layer}, timeoutMs);
        if (!res.ok || res.output == null) {
            Log.d(TAG, "SurfaceFlinger --latency failed: " + res.error);
            return -1;
        }
        String[] lines = res.output.split("\n");
        if (lines.length < 2) return -1;

        ArrayList<Long> stamps = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split("\\s+");
            if (parts.length < 3) continue;
            try {
                long actual = Long.parseLong(parts[1]);
                if (actual > 0 && actual < Long.MAX_VALUE / 2) {
                    stamps.add(actual);
                }
            } catch (Exception ignored) {}
        }
        if (stamps.size() < 2) return -1;

        Collections.sort(stamps);
        long newest = stamps.get(stamps.size() - 1);
        long age = System.nanoTime() - newest;
        if (age > STALE_NS || age < -STALE_NS) return 0f;

        int start = Math.max(0, stamps.size() - 60);
        long sum = 0;
        int count = 0;
        for (int i = start + 1; i < stamps.size(); i++) {
            long delta = stamps.get(i) - stamps.get(i - 1);
            if (delta > 0 && delta < 1_000_000_000L) {
                sum += delta;
                count++;
            }
        }
        if (count == 0) return 0f;
        double avg = (double) sum / count;
        if (avg <= 0) return 0f;
        return (float) (1_000_000_000.0 / avg);
    }
}
