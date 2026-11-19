package exp.ftxt.ui;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import exp.ftxt.R;
import exp.ftxt.core.appmanager.AppInfo;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.Holder> {
    public interface Listener {
        void onOpen(AppInfo app);

        void onSelectionChanged(int count);
    }

    private final Listener listener;
    private final List<AppInfo> apps = new ArrayList<AppInfo>();
    private final Set<String> selected = new HashSet<String>();
    private String query = "";

    public AppListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setApps(List<AppInfo> list) {
        apps.clear();
        if (list != null) apps.addAll(list);
        notifyDataSetChanged();
    }

    public void setQuery(String value) {
        query = value == null ? "" : value;
        notifyDataSetChanged();
    }

    public Set<String> getSelected() {
        return new HashSet<String>(selected);
    }

    public void toggle(String packageName) {
        if (selected.contains(packageName)) selected.remove(packageName);
        else selected.add(packageName);
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selected.size());
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final AppInfo app = apps.get(position);
        Context context = holder.itemView.getContext();

        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(highlight(context, app.label, query));
        holder.meta.setText(buildMeta(app));

        String badge = app.freshBadge(System.currentTimeMillis());
        if (badge == null) {
            holder.badge.setVisibility(View.GONE);
        } else {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(badge);
        }

        final boolean checked = selected.contains(app.packageName);
        holder.check.setVisibility(checked ? View.VISIBLE : View.GONE);
        holder.check.setOnClickListener(v -> toggle(app.packageName));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(app);
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    private CharSequence highlight(Context context, String text, String needle) {
        if (text == null) return "";
        if (needle.isEmpty()) return text;
        String lower = text.toLowerCase(Locale.US);
        int start = lower.indexOf(needle.toLowerCase(Locale.US));
        if (start < 0) return text;
        SpannableString span = new SpannableString(text);
        int end = Math.min(text.length(), start + needle.length());
        span.setSpan(new BackgroundColorSpan(ContextCompat.getColor(
                        context, R.color.usage_accent_track)),
                start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private String buildMeta(AppInfo app) {
        String updated = app.lastUpdateTime > 0
                ? new java.text.SimpleDateFormat("dd/MM/yy", Locale.US)
                .format(new java.util.Date(app.lastUpdateTime))
                : "—";
        String version = app.versionName == null ? "—" : app.versionName;
        return app.category + " · " + app.installer + " · v" + version + " · " + updated;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView check;
        final ImageView icon;
        final TextView name;
        final TextView meta;
        final TextView badge;

        Holder(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.itemAppCheck);
            icon = itemView.findViewById(R.id.itemAppIcon);
            name = itemView.findViewById(R.id.itemAppName);
            meta = itemView.findViewById(R.id.itemAppMeta);
            badge = itemView.findViewById(R.id.itemAppBadge);
        }
    }
}
