package exp.ftxt.features.battery_stats;

import android.content.pm.PackageManager;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rikka.shizuku.Shizuku;

/**
 * Pembaca {@code dumpsys battery} lewat Shizuku (UID shell). Dipakai sebagai
 * sumber otoritatif saat mendeteksi bypass charging. Hasil di-cache dan
 * pemanggilan dibatasi agar tidak membebani shell.
 */
final class DumpsysBatteryProbe {

    private static final long RETRY_INTERVAL_MS = 10_000L;
    private static final long FRESH_MS = 20_000L;
    private static final long EXEC_TIMEOUT_MS = 2_500L;

    private static volatile Data cached;
    private static long lastAttempt;
    private static boolean unavailable;
    private static long unavailableSince;

    private DumpsysBatteryProbe() {}

    static final class Data {
        int status = -1;
        int level = -1;
        int scale = -1;
        int plugged;
        int currentMa;
        boolean hasCurrent;
        boolean bypassHint;

        boolean hasStatus() {
            return status >= 0;
        }

        boolean hasLevel() {
            return level >= 0;
        }

        int percent() {
            if (level < 0) return -1;
            if (scale > 0 && scale != 100) return (level * 100) / scale;
            return level;
        }

        boolean hasPlugged() {
            return plugged != 0;
        }
    }

    static synchronized Data get(boolean plugged) {
        long now = SystemClock.elapsedRealtime();
        if (!plugged) {
            cached = null;
            return null;
        }
        if (unavailable && now - unavailableSince < 60_000L) {
            return null;
        }
        if (cached != null && now - lastAttempt < FRESH_MS) {
            return cached;
        }
        if (now - lastAttempt < RETRY_INTERVAL_MS) {
            return cached;
        }
        lastAttempt = now;

        if (!canUseShizuku()) {
            unavailable = true;
            unavailableSince = now;
            cached = null;
            return null;
        }

        String output = exec(new String[]{"dumpsys", "battery"});
        if (output == null || output.isEmpty()) {
            cached = null;
            return null;
        }
        unavailable = false;
        cached = parse(output);
        return cached;
    }

    private static boolean canUseShizuku() {
        try {
            if (!Shizuku.pingBinder()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String exec(String[] argv) {
        Process process = null;
        try {
            process = Shizuku.newProcess(argv, null, null);
            if (process == null) return null;

            final AtomicReference<String> text = new AtomicReference<>();
            final Process p = process;
            Thread reader = new Thread(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br =
                             new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                } catch (Exception ignored) {}
                text.set(sb.toString());
            }, "ftxt-dumpsys-battery");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                try { process.destroyForcibly(); } catch (Exception ignored) {}
                return null;
            }
            try { reader.join(500); } catch (Exception ignored) {}
            return text.get();
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (process != null) {
                try { process.destroy(); } catch (Exception ignored) {}
            }
        }
    }

    private static Data parse(String output) {
        Data data = new Data();
        String[] lines = output.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int idx = line.indexOf(':');
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim().toLowerCase();
            String value = line.substring(idx + 1).trim();
            try {
                switch (key) {
                    case "status":
                        data.status = Integer.parseInt(value);
                        break;
                    case "level":
                        data.level = Integer.parseInt(value);
                        break;
                    case "scale":
                        data.scale = Integer.parseInt(value);
                        break;
                    case "current now":
                    case "current_now":
                        data.currentMa = Integer.parseInt(value);
                        data.hasCurrent = true;
                        break;
                    case "ac powered":
                    case "usb powered":
                    case "wireless powered":
                    case "dock powered":
                        if ("true".equalsIgnoreCase(value)) data.plugged = 1;
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException ignored) {}
            String lower = line.toLowerCase();
            if (lower.contains("bypass") || lower.contains("misc charging: true")) {
                data.bypassHint = true;
            }
        }
        return data;
    }
}
