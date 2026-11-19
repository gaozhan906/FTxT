package exp.ftxt.core.appmanager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

public final class AppActions {
    private AppActions() {
    }

    public static Intent open(Context context, AppInfo app) {
        return context.getPackageManager().getLaunchIntentForPackage(app.packageName);
    }

    public static Intent uninstall(AppInfo app) {
        return new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName));
    }

    public static Intent settings(AppInfo app) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.packageName));
    }

    public static Intent specialAccess(String action, String packageName) {
        Intent intent = new Intent(action);
        try {
            intent.setData(Uri.parse("package:" + packageName));
        } catch (Throwable ignored) {
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
