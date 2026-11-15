package exp.ftxt.ui;

import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import exp.ftxt.MainActivity;
import exp.ftxt.R;

public class NetworkPanelController {

    private BottomNavigationView netBottomNav;
    private View networkSubSpeedPage;
    private View networkSubUsagePage;
    private View networkOverlaySpeedPage;
    private View networkOverlayAppPage;
    private TextView overlaySubTabSpeed;
    private TextView overlaySubTabApps;
    private NetworkOverlayController overlayController;
    private NetworkUsageController usageController;
    private NetworkAppDataController appDataController;

    private int overlayTabIndex = 0;
    private int overlayTabActiveColor;
    private int overlayTabInactiveColor;

    public NetworkPanelController(MainActivity activity, View rootView) {
        bindViews(rootView);
        overlayTabActiveColor = activity.getColor(R.color.usage_accent);
        overlayTabInactiveColor = activity.getColor(R.color.usage_filter_unselected_text);
        overlayController = new NetworkOverlayController(activity, rootView);
        usageController = new NetworkUsageController(activity, rootView);
        appDataController = new NetworkAppDataController(activity, rootView);
        setupSubTabs();
    }

    public void onPanelShown() {
        if (usageController != null) usageController.onPanelShown();
        if (overlayController != null) overlayController.refresh();
        if (appDataController != null) appDataController.onPanelShown();
    }

    public void onPanelHidden() {
        if (usageController != null) usageController.onPanelHidden();
        if (appDataController != null) appDataController.onPanelHidden();
    }

    public void showLoadPresetDialog() {
        if (overlayTabIndex == 1) {
            if (appDataController != null) appDataController.showLoadPresetDialog();
        } else if (overlayController != null) {
            overlayController.showLoadPresetDialog();
        }
    }

    public void cleanup() {
        if (usageController != null) {
            usageController.cleanup();
            usageController = null;
        }
        if (overlayController != null) {
            overlayController.cleanup();
            overlayController = null;
        }
        if (appDataController != null) {
            appDataController.cleanup();
            appDataController = null;
        }
    }

    private void bindViews(View rootView) {
        netBottomNav = rootView.findViewById(R.id.netBottomNav);
        networkSubSpeedPage = rootView.findViewById(R.id.networkSubSpeedPage);
        networkSubUsagePage = rootView.findViewById(R.id.networkSubUsagePage);
        networkOverlaySpeedPage = rootView.findViewById(R.id.networkOverlaySpeedPage);
        networkOverlayAppPage = rootView.findViewById(R.id.networkOverlayAppPage);
        overlaySubTabSpeed = rootView.findViewById(R.id.overlaySubTabSpeed);
        overlaySubTabApps = rootView.findViewById(R.id.overlaySubTabApps);
    }

    private void setupSubTabs() {
        netBottomNav.setOnItemSelectedListener(item -> {
            selectSubTab(item.getItemId() == R.id.netTabNavOverlay ? 1 : 0);
            return true;
        });
        overlaySubTabSpeed.setOnClickListener(v -> selectOverlaySubTab(0));
        overlaySubTabApps.setOnClickListener(v -> selectOverlaySubTab(1));
        selectSubTab(0);
        selectOverlaySubTab(0);
    }

    private void selectSubTab(int index) {
        boolean usage = index == 0;
        networkSubSpeedPage.setVisibility(usage ? View.GONE : View.VISIBLE);
        networkSubUsagePage.setVisibility(usage ? View.VISIBLE : View.GONE);
        if (usage && usageController != null) usageController.refresh();
        if (!usage && overlayController != null) overlayController.refresh();
    }

    private void selectOverlaySubTab(int index) {
        overlayTabIndex = index;
        networkOverlaySpeedPage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        networkOverlayAppPage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        applyOverlayTabState(overlaySubTabSpeed, index == 0);
        applyOverlayTabState(overlaySubTabApps, index == 1);
        if (index == 1 && appDataController != null) {
            appDataController.onPanelShown();
        }
    }

    private void applyOverlayTabState(TextView tab, boolean active) {
        tab.setTextColor(active ? overlayTabActiveColor : overlayTabInactiveColor);
        tab.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }
}
