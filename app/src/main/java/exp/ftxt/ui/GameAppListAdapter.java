package exp.ftxt.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import exp.ftxt.R;

public class GameAppListAdapter extends RecyclerView.Adapter<GameAppListAdapter.Holder> {

    public interface OnAppSelected {
        void onSelected(ApplicationInfo appInfo);
    }

    private final PackageManager pm;
    private final List<ApplicationInfo> apps = new ArrayList<>();
    private final OnAppSelected listener;

    public GameAppListAdapter(PackageManager pm, OnAppSelected listener) {
        this.pm = pm;
        this.listener = listener;
    }

    public void setApps(List<ApplicationInfo> list) {
        apps.clear();
        if (list != null) {
            apps.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game_app, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ApplicationInfo ai = apps.get(position);
        h.txtAppName.setText(pm.getApplicationLabel(ai));
        h.txtAppPkg.setText(ai.packageName);
        try {
            Drawable icon = pm.getApplicationIcon(ai);
            h.imgAppIcon.setImageDrawable(icon);
        } catch (Exception e) {
            h.imgAppIcon.setImageDrawable(null);
        }
        h.btnSelectApp.setOnClickListener(v -> {
            if (listener != null) listener.onSelected(ai);
        });
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSelected(ai);
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView imgAppIcon;
        TextView txtAppName, txtAppPkg;
        Button btnSelectApp;

        public Holder(@NonNull View itemView) {
            super(itemView);
            imgAppIcon = itemView.findViewById(R.id.imgAppIcon);
            txtAppName = itemView.findViewById(R.id.txtAppName);
            txtAppPkg = itemView.findViewById(R.id.txtAppPkg);
            btnSelectApp = itemView.findViewById(R.id.btnSelectApp);
        }
    }
}
