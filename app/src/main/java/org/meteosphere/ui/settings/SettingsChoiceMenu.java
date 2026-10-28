package org.meteosphere.ui.settings;

import android.view.View;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.R;
import org.meteosphere.ui.glass.GlassMenu;

/** A setting's choices on frosted glass, dropping from its row as the city menu drops from its chip */
public final class SettingsChoiceMenu {

    private SettingsChoiceMenu() {
    }

    /** Shows the choices of a list setting at its row; false when it isn't one, or its row isn't on the screen */
    static boolean show(PreferenceFragmentCompat fragment, Preference preference) {
        if (!(preference instanceof ListPreference)) {
            return false;
        }
        RecyclerView list = fragment.getListView();
        if (list == null || !(list.getAdapter() instanceof PreferenceGroupAdapter)) {
            return false;
        }
        int position = ((PreferenceGroupAdapter) list.getAdapter()).getPreferenceAdapterPosition(preference);
        RecyclerView.ViewHolder holder = position == RecyclerView.NO_POSITION ? null : list.findViewHolderForAdapterPosition(position);
        if (holder == null) {
            return false;
        }
        ListPreference listPreference = (ListPreference) preference;
        CharSequence[] entries = listPreference.getEntries();
        CharSequence[] values = listPreference.getEntryValues();
        int chosen = listPreference.findIndexOfValue(listPreference.getValue());
        int[] icons = icons(fragment, listPreference.getKey(), values);
        List<GlassMenu.Item> items = new ArrayList<>(entries.length);
        for (int i = 0; i < entries.length; i++) {
            items.add(new GlassMenu.Item(icons[i], entries[i], null, i == chosen, false));
        }
        show(holder.itemView, items, index -> {
            String value = values[index].toString();
            if (!value.equals(listPreference.getValue()) && listPreference.callChangeListener(value)) {
                listPreference.setValue(value);
            }
        });
        return true;
    }

    /** Shows the choices at a row, which stays lit while they are open */
    public static void show(View row, List<GlassMenu.Item> items, GlassMenu.OnItemClick listener) {
        SettingsSegments.setLit(row, true);
        GlassMenu.showAtRow(row, items, listener, () -> SettingsSegments.setLit(row, false));
    }

    // Icons where they tell the choices apart: the theme's looks and the update's clock; none for the others
    private static int[] icons(PreferenceFragmentCompat fragment, String key, CharSequence[] values) {
        int[] icons = new int[values.length];
        boolean theme = fragment.getString(R.string.key_theme).equals(key);
        boolean updatePeriod = fragment.getString(R.string.key_refresh_period).equals(key);
        for (int i = 0; i < values.length; i++) {
            String value = values[i].toString();
            if (theme) {
                icons[i] = "light".equals(value) ? R.drawable.ic_menu_light : "dark".equals(value) ? R.drawable.ic_menu_dark : R.drawable.ic_menu_device;
            } else if (updatePeriod) {
                icons[i] = "0".equals(value) ? R.drawable.ic_menu_auto : R.drawable.ic_schedule;
            }
        }
        return icons;
    }
}
