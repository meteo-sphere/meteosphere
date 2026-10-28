package org.meteosphere.ui.common;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;

/**
 * A list under an item_astro_section title that a tap on the title folds away or opens; whether it is folded
 * is kept in the settings under the key given, so the list stays as the user left it.
 */
public final class CollapsibleSection {

    private static final String PREF_PREFIX = "org.meteosphere.collapsed.";

    private CollapsibleSection() {
    }

    /** Told whether the list is folded, at once and on each tap */
    public interface Listener {
        void onFolded(boolean collapsed);
    }

    public static void bind(View header, int title, View content, String key) {
        bind(header, title, key, collapsed -> content.setVisibility(collapsed ? View.GONE : View.VISIBLE));
    }

    /** For a list that isn't one view, like the rows of a RecyclerView after the title */
    public static void bind(View header, int title, String key, Listener listener) {
        SharedPreferences preferences = AppSettings.getInstance().preferences;
        String prefKey = PREF_PREFIX + key;
        ((TextView) header.findViewById(R.id.tvSectionTitle)).setText(title);
        show(header, preferences.getBoolean(prefKey, false));
        listener.onFolded(preferences.getBoolean(prefKey, false));
        header.setOnClickListener(view -> {
            boolean collapsed = !preferences.getBoolean(prefKey, false);
            preferences.edit().putBoolean(prefKey, collapsed).apply();
            show(header, collapsed);
            listener.onFolded(collapsed);
        });
    }

    public static boolean isCollapsed(String key) {
        return AppSettings.getInstance().preferences.getBoolean(PREF_PREFIX + key, false);
    }

    /** Shows the title and the list as folded or not, after the list was hidden for having nothing to show */
    public static void refresh(View header, View content, String key) {
        header.setVisibility(View.VISIBLE);
        boolean collapsed = isCollapsed(key);
        content.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        show(header, collapsed);
    }

    // The chevron points down to open a folded list and up to fold an open one
    private static void show(View header, boolean collapsed) {
        ImageView arrow = header.findViewById(R.id.ivSectionArrow);
        arrow.setRotation(collapsed ? 0 : 180);
        ViewCompat.setStateDescription(header, header.getContext().getString(collapsed ? R.string.section_collapsed : R.string.section_expanded));
    }
}
