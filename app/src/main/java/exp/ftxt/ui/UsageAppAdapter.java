package exp.ftxt.ui;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import exp.ftxt.R;

public class UsageAppAdapter extends RecyclerView.Adapter<UsageAppAdapter.Holder> {

    public static final class Item {
        public final String label;
        public final Drawable icon;
        public final String detail;
        public final String totalLabel;
        public final String percentLabel;
        public final float fraction;

        public Item(String label, Drawable icon, String detail, String totalLabel,
                    String percentLabel, float fraction) {
            this.label = label;
            this.icon = icon;
            this.detail = detail;
            this.totalLabel = totalLabel;
            this.percentLabel = percentLabel;
            this.fraction = fraction;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public void submit(List<Item> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_usage_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Item item = items.get(position);
        holder.icon.setImageDrawable(item.icon);
        holder.name.setText(item.label);
        holder.detail.setText(item.detail);
        holder.total.setText(item.totalLabel);
        holder.percent.setText(item.percentLabel);
        holder.progress.setProgress(Math.round(Math.max(0f, Math.min(1f, item.fraction)) * 1000));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView detail;
        final TextView total;
        final TextView percent;
        final ProgressBar progress;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.itemUsageAppIcon);
            name = itemView.findViewById(R.id.itemUsageAppName);
            detail = itemView.findViewById(R.id.itemUsageAppDetail);
            total = itemView.findViewById(R.id.itemUsageAppTotal);
            percent = itemView.findViewById(R.id.itemUsageAppPercent);
            progress = itemView.findViewById(R.id.itemUsageAppBar);
        }
    }
}
