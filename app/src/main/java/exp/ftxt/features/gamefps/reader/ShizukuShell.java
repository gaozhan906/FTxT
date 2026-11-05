package exp.ftxt.features.gamefps.reader;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuShell {
    private static final String TAG = "GameFpsReader";

    private static final Object SESSION_LOCK = new Object();
    private static ShellSession session;

    public static class Result {
        public boolean ok;
        public String output;
        public String error;
        public int exitCode = -1;
    }

    public static Result execArgs(String[] argv, long timeoutMs) {
        StringBuilder sb = new StringBuilder();
        for (String arg : argv) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(shellQuote(arg));
        }
        String cmd = sb.toString();
        Result viaSession = execViaSession(cmd, timeoutMs);
        if (viaSession != null) return viaSession;
        return execOnce(argv, cmd, timeoutMs);
    }

    private static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static void closeSession() {
        synchronized (SESSION_LOCK) {
            if (session != null) {
                session.close();
                session = null;
            }
        }
    }

    private static Result execViaSession(String cmd, long timeoutMs) {
        synchronized (SESSION_LOCK) {
            try {
                if (session == null || !session.isAlive()) {
                    if (session != null) session.close();
                    session = new ShellSession();
                }
                return session.run(cmd, timeoutMs);
            } catch (Throwable t) {
                Log.w(TAG, "shell session failed, fallback to one-shot", t);
                if (session != null) {
                    session.close();
                    session = null;
                }
                return null;
            }
        }
    }

    private static class ShellSession {
        private final Process process;
        private final BufferedWriter writer;
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final AtomicInteger seq = new AtomicInteger();
        private volatile boolean alive = true;

        ShellSession() throws Exception {
            process = Shizuku.newProcess(new String[]{"sh"}, null, null);
            if (process == null) throw new IllegalStateException("Shizuku.newProcess returned null");
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            Thread thread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.put(line);
                    }
                } catch (Throwable ignored) {
                } finally {
                    alive = false;
                    try { lines.put("__FTXT_EOF__"); } catch (Throwable ignored) {}
                }
            });
            thread.setDaemon(true);
            thread.setName("ftxt-shizuku-reader");
            thread.start();
        }

        boolean isAlive() {
            return alive;
        }

        synchronized Result run(String cmd, long timeoutMs) throws Exception {
            if (!alive) throw new IllegalStateException("shell session not alive");
            int id = seq.incrementAndGet();
            String marker = "__FTXT_END_" + id + "__";
            writer.write("{ " + cmd + " ; } 2>&1; rc=$?; echo; echo \"" + marker + " $rc\"");
            writer.newLine();
            writer.flush();

            Result res = new Result();
            StringBuilder out = new StringBuilder();
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) throw new TimeoutException("shell command timeout");
                String line = lines.poll(remaining, TimeUnit.MILLISECONDS);
                if (line == null) throw new TimeoutException("shell command timeout");
                if ("__FTXT_EOF__".equals(line)) throw new IllegalStateException("shell session EOF");
                if (line.startsWith(marker)) {
                    String tail = line.substring(marker.length()).trim();
                    try {
                        res.exitCode = Integer.parseInt(tail);
                    } catch (Exception ignored) {}
                    res.output = out.toString();
                    res.error = "";
                    res.ok = res.exitCode == 0
                            || res.output.contains("Total frames rendered")
                            || res.output.contains("Janky frames")
                            || res.output.contains("Graphics info");
                    return res;
                }
                out.append(line).append('\n');
            }
        }

        void close() {
            alive = false;
            try { writer.close(); } catch (Throwable ignored) {}
            try { process.destroyForcibly(); } catch (Throwable ignored) {}
        }
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

    private static Result execOnce(String[] argv, String cmd, long timeoutMs) {
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
