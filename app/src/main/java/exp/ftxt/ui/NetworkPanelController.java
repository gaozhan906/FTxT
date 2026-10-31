package exp.ftxt.ui;

import android.view.View;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import exp.ftxt.MainActivity;
import exp.ftxt.R;

public class NetworkPanelController {

    private BottomNavigationView netBottomNav;
    private View networkSubSpeedPage;
    private View networkSubUsagePage;
    private NetworkOverlayController overlayController;
    private NetworkUsageController usageController;

    public NetworkPanelController(MainActivity activity, View rootView) {
        bindViews(rootView);
        overlayController = new NetworkOverlayController(activity, rootView);
        usageController = new NetworkUsageController(activity, rootView);
        setupSubTabs();
    }

    public void onPanelShown() {
        if (usageController != null) usageController.onPanelShown();
        if (overlayController != null) overlayController.refresh();
    }

    public void onPanelHidden() {
        if (usageController != null) usageController.onPanelHidden();
    }

    public void showLoadPresetDialog() {
        if (overlayController != null) overlayController.showLoadPresetDialog();
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
    }

    private void bindViews(View rootView) {
        netBottomNav = rootView.findViewById(R.id.netBottomNav);
        networkSubSpeedPage = rootView.findViewById(R.id.networkSubSpeedPage);
        networkSubUsagePage = rootView.findViewById(R.id.networkSubUsagePage);
    }

    private void setupSubTabs() {
        netBottomNav.setOnItemSelectedListener(item -> {
            selectSubTab(item.getItemId() == R.id.netTabNavOverlay ? 1 : 0);
            return true;
        });
        selectSubTab(0);
    }

    private void selectSubTab(int index) {
        boolean usage = index == 0;
        networkSubSpeedPage.setVisibility(usage ? View.GONE : View.VISIBLE);
        networkSubUsagePage.setVisibility(usage ? View.VISIBLE : View.GONE);
        if (usage && usageController != null) usageController.refresh();
    }
}
