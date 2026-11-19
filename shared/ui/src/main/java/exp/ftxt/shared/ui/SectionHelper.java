package exp.ftxt.shared.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

public class SectionHelper {

    public static void setupCollapsible(View header, View container) {
        TextView title = findTitle(header);
        if (title == null) return;
        String clean = title.getText().toString().replaceAll("^[▸▾]\\s*", "");
        title.setText("▾ " + clean);
        header.setTag(Boolean.TRUE);
        header.setOnClickListener(v -> {
            boolean expanded = (boolean) header.getTag();
            container.setVisibility(expanded ? View.GONE : View.VISIBLE);
            title.setText((expanded ? "▸ " : "▾ ") + clean);
            header.setTag(!expanded);
        });
    }

    private static TextView findTitle(View view) {
        if (view instanceof TextView && !(view instanceof CompoundButton)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView title = findTitle(group.getChildAt(i));
                if (title != null) return title;
            }
        }
        return null;
    }
}
