package exp.ftxt.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import exp.ftxt.R;

public class GameSearchAdapter extends BaseAdapter implements Filterable {

    public static class Entry {
        public final ApplicationInfo info;
        public final String label;
        public final String pkg;

        public Entry(ApplicationInfo info, String label) {
            this.info = info;
            this.label = label;
            this.pkg = info.packageName;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final int MAX_RESULTS = 30;

    private final PackageManager pm;
    private final LayoutInflater inflater;
    private List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();

    public GameSearchAdapter(Context context) {
        this.pm = context.getPackageManager();
        this.inflater = LayoutInflater.from(context);
    }

    public void setApps(List<ApplicationInfo> apps) {
        all = new ArrayList<>();
        if (apps != null) {
            for (ApplicationInfo ai : apps) {
                String label;
                try {
                    label = String.valueOf(pm.getApplicationLabel(ai));
                } catch (Exception e) {
                    label = ai.packageName;
                }
                all.add(new Entry(ai, label));
            }
            Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
        }
        filtered = new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setEntries(List<Entry> entries) {
        all = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        filtered = new ArrayList<>();
        notifyDataSetChanged();
    }

    public Entry findSelected(CharSequence text) {
        if (text == null) return null;
        String value = text.toString().trim();
        for (Entry e : all) {
            if (value.equals(e.label) || value.equals(e.pkg)
                    || value.equals(e.label + " (" + e.pkg + ")")) {
                return e;
            }
        }
        return null;
    }

    @Override
    public int getCount() {
        return filtered.size();
    }

    @Override
    public Entry getItem(int position) {
        return filtered.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_game_search, parent, false);
        }
        Entry entry = filtered.get(position);
        ImageView icon = view.findViewById(R.id.imgSearchIcon);
        TextView name = view.findViewById(R.id.txtSearchName);
        TextView pkg = view.findViewById(R.id.txtSearchPkg);
        name.setText(entry.label);
        pkg.setText(entry.pkg);
        try {
            Drawable drawable = pm.getApplicationIcon(entry.info);
            icon.setImageDrawable(drawable);
        } catch (Exception e) {
            icon.setImageDrawable(null);
        }
        return view;
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    private final Filter filter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            String query = constraint == null ? "" : constraint.toString().trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                results.values = new ArrayList<Entry>();
                results.count = 0;
                return results;
            }
            ArrayList<Entry> matches = new ArrayList<>();
            for (Entry entry : all) {
                String label = entry.label.toLowerCase(Locale.ROOT);
                String pkg = entry.pkg.toLowerCase(Locale.ROOT);
                if (label.contains(query) || pkg.contains(query)) {
                    matches.add(entry);
                    if (matches.size() >= MAX_RESULTS) break;
                }
            }
            results.values = matches;
            results.count = matches.size();
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filtered = results.values == null
                    ? new ArrayList<>() : (ArrayList<Entry>) results.values;
            notifyDataSetChanged();
        }
    };
}
