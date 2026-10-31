package exp.ftxt.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import exp.ftxt.R;
import exp.ftxt.features.network_stats.usage.UsageDay;

public class UsageHistoryAdapter extends RecyclerView.Adapter<UsageHistoryAdapter.Holder> {

    public interface OnDayClickListener {
        void onDayClick(UsageDay day);
    }

    public static final class Item {
        public final UsageDay day;
        public final String dateLabel;
        public final String networksLabel;
        public final String totalLabel;
        public final String percentLabel;
        public final String sourceLabel;

        public Item(UsageDay day, String dateLabel, String networksLabel,
                    String totalLabel, String percentLabel, String sourceLabel) {
            this.day = day;
            this.dateLabel = dateLabel;
            this.networksLabel = networksLabel;
            this.totalLabel = totalLabel;
            this.percentLabel = percentLabel;
            this.sourceLabel = sourceLabel;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final OnDayClickListener listener;

    public UsageHistoryAdapter(OnDayClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<Item> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_usage_day, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Item item = items.get(position);
        holder.date.setText(item.dateLabel);
        holder.networks.setText(item.networksLabel);
        holder.total.setText(item.totalLabel);
        holder.percent.setText(item.percentLabel);
        if (item.sourceLabel == null) {
            holder.source.setVisibility(View.GONE);
        } else {
            holder.source.setText(item.sourceLabel);
            holder.source.setVisibility(View.VISIBLE);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDayClick(item.day);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView date;
        final TextView networks;
        final TextView total;
        final TextView percent;
        final TextView source;

        Holder(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.itemUsageDayDate);
            networks = itemView.findViewById(R.id.itemUsageDayNetworks);
            total = itemView.findViewById(R.id.itemUsageDayTotal);
            percent = itemView.findViewById(R.id.itemUsageDayPercent);
            source = itemView.findViewById(R.id.itemUsageDaySource);
        }
    }
}
