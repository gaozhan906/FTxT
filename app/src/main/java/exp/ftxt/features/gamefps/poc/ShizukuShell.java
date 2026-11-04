package exp.ftxt.features.gamefps.poc;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuShell {
    private static final String TAG = "GameFpsPoC";

    private static String[] splitCommand(String cmd) {
        String[] parts = cmd.trim().split("\\s+");
        String[] out = new String[parts.length];
        int n = 0;
        for (String part : parts) {
            if (!part.isEmpty()) out[n++] = part;
        }
        if (n == parts.length) return out;
        String[] trimmed = new String[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    public static class Result {
        public boolean ok;
        public String output;
        public String error;
        public int exitCode = -1;
    }

    private static boolean waitForCompat(Process proc, long timeoutMs)
            throws InterruptedException {
        if (proc instanceof ShizukuRemoteProcess) {
            try {
                return ((ShizukuRemoteProcess) proc).waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                throw ie;
            } catch (Throwable ignored) {
                Log.w(TAG, "waitForTimeout failed, fallback to polling", ignored);
            }
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                proc.exitValue();
                return true;
            } catch (IllegalThreadStateException e) {
                Thread.sleep(50);
            } catch (RuntimeException e) {
                String msg = String.valueOf(e.getMessage()) + " " + String.valueOf(e.getCause());
                if (msg.contains("hasn't exited") || msg.contains("IllegalThreadState")) {
                    Thread.sleep(50);
                } else {
                    throw e;
                }
            }
        }
        return false;
    }

    public static Result exec(String cmd, long timeoutMs) {
        Result res = new Result();
        if (!ShizukuHelper.isBinderAlive()) {
            res.error = "Shizuku binder not alive";
            return res;
        }
        if (!ShizukuHelper.hasPermission()) {
            res.error = "Shizuku permission not granted";
            return res;
        }

        Process p = null;
        try {
            // Prefer direct argv invocation; sh -c is only a fallback.
            String[] argv = splitCommand(cmd);
            try {
                p = Shizuku.newProcess(argv, null, null);
            } catch (Throwable directErr) {
                Log.w(TAG, "direct exec failed, fallback to sh -c", directErr);
                p = null;
            }
            if (p == null) {
                p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            }
            if (p == null) {
                res.error = "Shizuku.newProcess returned null";
                return res;
            }

            final Process proc = p;
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            Thread tOut = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        out.append(line).append('\n');
                    }
                } catch (Exception e) {
                    Log.w(TAG, "read stdout err", e);
                }
            });
            Thread tErr = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        err.append(line).append('\n');
                    }
                } catch (Exception e) {
                    Log.w(TAG, "read stderr err", e);
                }
            });
            tOut.start();
            tErr.start();

            boolean finished = waitForCompat(proc, timeoutMs);
            if (!finished) {
                try { proc.destroyForcibly(); } catch (Exception ignored) {}
                res.error = "timeout";
                res.output = out.toString();
                return res;
            }

            try { tOut.join(1500); } catch (Exception ignored) {}
            try { tErr.join(500); } catch (Exception ignored) {}

            try {
                res.exitCode = proc.exitValue();
            } catch (Exception ignored) {
                res.exitCode = -1;
            }
            res.output = out.toString();
            res.error = err.toString();
            res.ok = res.exitCode == 0
                    || res.output.contains("Total frames rendered")
                    || res.output.contains("Janky frames")
                    || res.output.contains("Graphics info");
            if (!res.ok && res.error.isEmpty()) {
                res.error = "exit code " + res.exitCode;
            }
            if (res.ok && res.output.isEmpty()) {
                res.ok = false;
                res.error = "output kosong";
            }
            return res;
        } catch (Throwable t) {
            Log.e(TAG, "exec failed", t);
            res.error = t.getMessage();
            return res;
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Exception ignored) {}
            }
        }
    }
}
