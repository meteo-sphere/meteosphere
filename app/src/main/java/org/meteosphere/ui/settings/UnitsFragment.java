package org.meteosphere.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.meteosphere.R;
import org.meteosphere.settings.AppSettings;

/**
 * The units: those chosen are kept by the screen until the bar's tick applies them, also when the screen is made again;
 * Back leaves the saved ones. Until the user chooses any, none is saved and they are the app language's own
 */
public final class UnitsFragment extends PreferenceFragmentCompat {

    private static final String STATE_CHOSEN = "chosen";

    private final Map<String, String> chosen = new HashMap<>();
    private SharedPreferences saved;

    @Override
    public final void onCreatePreferences(Bundle bundle, String rootKey) {
        this.saved = PreferenceManager.getDefaultSharedPreferences(requireContext());
        Bundle kept = bundle != null ? bundle.getBundle(STATE_CHOSEN) : null;
        if (kept != null) {
            for (String key : kept.keySet()) {
                this.chosen.put(key, kept.getString(key));
            }
        }
        // Read from the units' settings until another is chosen, written to them by apply. The lists have no defaults:
        // the language's own units are shown without being saved
        getPreferenceManager().setPreferenceDataStore(new PreferenceDataStore() {
            @Override
            public String getString(String key, String defValue) {
                String value = UnitsFragment.this.chosen.get(key);
                return value != null ? value : AppSettings.getInstance().unitSetting(key);
            }

            @Override
            public void putString(String key, String value) {
                UnitsFragment.this.chosen.put(key, value);
            }
        });
        setPreferencesFromResource(R.xml.units, rootKey);
    }

    /** Applied by the bar's tick only */
    public void apply() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            keys.add(getPreferenceScreen().getPreference(i).getKey());
        }
        Map<String, String> units = toSave(keys, this.chosen, AppSettings.getInstance()::unitSetting);
        if (units.isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = this.saved.edit();
        for (Map.Entry<String, String> unit : units.entrySet()) {
            editor.putString(unit.getKey(), unit.getValue());
        }
        editor.apply();
    }

    /**
     * The units saved by the tick: all of them once any chosen is another than its setting, as one unit chosen keeps
     * all from following the language; none while nothing changed, the units still the language's then
     */
    static Map<String, String> toSave(List<String> keys, Map<String, String> chosen, UnaryOperator<String> setting) {
        Map<String, String> units = new HashMap<>();
        boolean changed = false;
        for (String key : keys) {
            String now = setting.apply(key);
            String value = chosen.get(key);
            changed |= value != null && !value.equals(now);
            units.put(key, value != null ? value : now);
        }
        return changed ? units : Collections.emptyMap();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        Bundle kept = new Bundle();
        for (Map.Entry<String, String> unit : this.chosen.entrySet()) {
            kept.putString(unit.getKey(), unit.getValue());
        }
        state.putBundle(STATE_CHOSEN, kept);
    }

    @Override
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        setDivider(null);
        SettingsSegments.setListPadding(getListView(), true);
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        return new SegmentedPreferenceAdapter(preferenceScreen);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (!SettingsChoiceMenu.show(this, preference)) {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public final void onResume() {
        requireActivity().setTitle(R.string.pref_units);
        super.onResume();
    }
}
