package exp.ftxt.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import exp.ftxt.core.appmanager.InstalledApps;

public class AppManagerPackageReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        InstalledApps.invalidate(context);
    }
}
