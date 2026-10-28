package org.meteosphere.ui.settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;

/** The rows of a settings screen in rounded groups, a group running from one category title to the next */
final class SegmentedPreferenceAdapter extends PreferenceGroupAdapter {

    SegmentedPreferenceAdapter(PreferenceGroup group) {
        super(group);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        if (isRow(position)) {
            SettingsSegments.apply(holder.itemView, !isRow(position - 1), !isRow(position + 1));
        }
    }

    // The titles of the categories and the logo stand apart from the groups
    private boolean isRow(int position) {
        if (position < 0 || position >= getItemCount()) {
            return false;
        }
        Preference preference = getItem(position);
        return !(preference instanceof PreferenceCategory) && !(preference instanceof LogoPreference);
    }
}
