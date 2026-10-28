package org.meteosphere.addon;

import android.content.Context;
import androidx.activity.ComponentActivity;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import org.meteosphere.data.model.Place;

/**
 * A part of the app kept outside its public code and built in when local.properties names its folder, found by
 * {@link AddOns}. It may put its own weather into a place's forecast and add its rows to the settings
 */
public interface AddOn {

    /** Saved with the places its weather went into: stays the same from build to build */
    String id();

    /** How the city page names it as a source, before the provider */
    String sourceName(Context context);

    /** Reads its settings anew: at the app's start and whenever they change */
    void init(Context app);

    /** Puts its weather into the provider's forecast of the place, just loaded; sets the place's addOnSource then */
    void apply(Place place);

    /** Whether its weather of the place is about to be renewed: the place is asked for before its update period ends */
    boolean awaitsNew(Place place, long now);

    /** Adds its rows to the settings' category, as the settings screen is made */
    void addSettings(PreferenceFragmentCompat fragment, PreferenceCategory category);

    /** As the main screen is made, before it starts: the time to register for an activity's result */
    void onMainScreen(ComponentActivity activity);
}
