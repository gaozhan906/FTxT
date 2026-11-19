package exp.ftxt.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import exp.ftxt.core.appmanager.AppInfo;

public final class AppShareHelper {
    private AppShareHelper() {
    }

    public static List<Uri> prepare(Context context, List<AppInfo> apps) {
        File dir = new File(context.getCacheDir(), "appmanager");
        if (!dir.exists() && !dir.mkdirs()) return new ArrayList<Uri>();

        List<Uri> uris = new ArrayList<Uri>();
        for (AppInfo app : apps) {
            if (app.sourceDir == null) continue;
            File source = new File(app.sourceDir);
            if (!source.exists()) continue;
            File target = new File(dir, app.packageName.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk");
            if (copy(source, target)) {
                uris.add(FileProvider.getUriForFile(
                        context, context.getPackageName() + ".fileprovider", target));
            }
        }
        return uris;
    }

    public static Intent sendIntent(Context context, List<Uri> uris) {
        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<Uri>(uris));
        }
        intent.setType("application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "Bagikan aplikasi");
    }

    private static boolean copy(File source, File target) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target);
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
