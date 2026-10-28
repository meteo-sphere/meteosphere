package org.meteosphere.ui.widgets.settings;

import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import org.meteosphere.R;
import org.meteosphere.ui.settings.SettingsSegments;

/**
 * The widgets' settings screens' rows, as the app's settings have them: the rows of widget_settings_row and
 * widget_settings_slider_row filled in code and drawn as segments of their groups.
 */
final class WidgetSettingsRows {

    private WidgetSettingsRows() {
    }

    /** How transparent a background of the alpha is, 0 to 100 % */
    static int transparency(int alpha) {
        return Math.max(0, Math.min(100, Math.round(100f - alpha / 2.55f)));
    }

    /** The background's alpha for how transparent it is, 0 to 100 % */
    static int alpha(float transparency) {
        return Math.round(255f - transparency * 2.55f);
    }

    /** The row with its icon and title */
    static View row(View root, int id, int icon, CharSequence title) {
        View row = root.findViewById(id);
        ((ImageView) row.findViewById(R.id.ivRowIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.tvRowTitle)).setText(title);
        return row;
    }

    static TextView summary(View row, CharSequence text) {
        TextView summary = row.findViewById(R.id.tvRowSummary);
        summary.setText(text);
        summary.setVisibility(View.VISIBLE);
        return summary;
    }

    /** The row's switch as saved, the whole row toggling it */
    static SwitchCompat switchOf(View row, boolean checked) {
        SwitchCompat button = row.findViewById(R.id.swRow);
        button.setVisibility(View.VISIBLE);
        button.setChecked(checked);
        row.setOnClickListener(view -> button.toggle());
        return button;
    }

    /**
     * Draws the shown rows as segments of their groups, a group running from one title to the next; a title with no
     * shown rows under it is hidden with them
     */
    static void segment(LinearLayout content) {
        int count = content.getChildCount();
        int i = 0;
        while (i < count) {
            View title = content.getChildAt(i);
            if (!isGroupTitle(title)) {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < count && !isGroupTitle(content.getChildAt(end))) {
                end++;
            }
            int first = -1;
            int last = -1;
            for (int k = i + 1; k < end; k++) {
                if (content.getChildAt(k).getVisibility() == View.VISIBLE) {
                    first = first < 0 ? k : first;
                    last = k;
                }
            }
            title.setVisibility(first < 0 ? View.GONE : View.VISIBLE);
            for (int k = first; k >= 0 && k <= last; k++) {
                View row = content.getChildAt(k);
                if (row.getVisibility() == View.VISIBLE) {
                    SettingsSegments.apply(row, k == first, k == last);
                }
            }
            i = end;
        }
    }

    private static boolean isGroupTitle(View view) {
        return view instanceof TextView && !(view instanceof CompoundButton);
    }
}
