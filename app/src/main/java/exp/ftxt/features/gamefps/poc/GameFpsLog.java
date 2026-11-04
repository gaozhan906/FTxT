package exp.ftxt.features.gamefps.poc;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public final class GameFpsLog {
    private static final int MAX_LINES = 600;
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private GameFpsLog() {}

    public static synchronized void add(String message) {
        if (message == null) return;
        String line = TIME.format(new Date()) + "  " + message;
        LINES.addLast(line);
        while (LINES.size() > MAX_LINES) {
            LINES.removeFirst();
        }
    }

    public static synchronized String snapshot() {
        if (LINES.isEmpty()) return "(belum ada log)";
        StringBuilder sb = new StringBuilder();
        for (String line : LINES) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        LINES.clear();
    }

    public static String save(Context ctx) {
        String content;
        synchronized (GameFpsLog.class) {
            StringBuilder sb = new StringBuilder();
            sb.append("=== FTxT Game FPS Log ===\n");
            sb.append("Waktu : ")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date()))
                    .append('\n');
            sb.append("Model : ").append(Build.MODEL).append('\n');
            sb.append("Android: ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("Shizuku: binder=").append(ShizukuHelper.isBinderAlive())
                    .append(" permission=").append(ShizukuHelper.hasPermission()).append('\n');
            sb.append("----------------------------------------\n");
            if (LINES.isEmpty()) {
                sb.append("(belum ada log)\n");
            } else {
                for (String line : LINES) {
                    sb.append(line).append('\n');
                }
            }
            content = sb.toString();
        }

        String fileName = "FTxT_gamefps_" + System.currentTimeMillis() + ".txt";
        try {
            return writeToDocuments(ctx, content, fileName);
        } catch (Exception e) {
            return "Gagal menyimpan: " + e.getMessage();
        }
    }

    private static String writeToDocuments(Context ctx, String content, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/FTxT/Log_GameFps");
            Uri uri = ctx.getContentResolver().insert(
                    MediaStore.Files.getContentUri("external"), values);
            if (uri == null) return "Gagal membuat file";
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) return "Gagal membuka file";
                os.write(content.getBytes("UTF-8"));
            }
            return "Documents/FTxT/Log_GameFps/" + fileName;
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "FTxT/Log_GameFps");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes("UTF-8"));
            }
            return file.getAbsolutePath();
        }
    }

    public static synchronized List<String> tail(int count) {
        List<String> all = new ArrayList<>(LINES);
        int from = Math.max(0, all.size() - count);
        return new ArrayList<>(all.subList(from, all.size()));
    }
}
