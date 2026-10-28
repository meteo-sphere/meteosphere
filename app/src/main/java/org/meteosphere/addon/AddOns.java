package org.meteosphere.addon;

import android.content.Context;
import androidx.activity.ComponentActivity;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.meteosphere.BuildConfig;
import org.meteosphere.data.model.Place;

/**
 * The add-ons built in, whose classes their addon.gradle names in BuildConfig.ADD_ONS, and the app's calls passed to
 * each of them. The public build has none
 */
public final class AddOns {

    private static final List<AddOn> ALL = load();

    private AddOns() {
    }

    // By their names rather than by ServiceLoader, which reads the APK at every start of a debug build: some 100 ms.
    // A class missing is a broken build, told at once
    private static List<AddOn> load() {
        List<AddOn> res = new ArrayList<>();
        for (String name : BuildConfig.ADD_ONS) {
            try {
                res.add((AddOn) Class.forName(name).getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No add-on " + name, e);
            }
        }
        return Collections.unmodifiableList(res);
    }

    public static void init(Context app) {
        for (AddOn addOn : ALL) {
            addOn.init(app);
        }
    }

    public static void apply(Place place) {
        for (AddOn addOn : ALL) {
            addOn.apply(place);
        }
    }

    public static boolean awaitsNew(Place place, long now) {
        for (AddOn addOn : ALL) {
            if (addOn.awaitsNew(place, now)) {
                return true;
            }
        }
        return false;
    }

    public static void addSettings(PreferenceFragmentCompat fragment, PreferenceCategory category) {
        for (AddOn addOn : ALL) {
            addOn.addSettings(fragment, category);
        }
    }

    public static void onMainScreen(ComponentActivity activity) {
        for (AddOn addOn : ALL) {
            addOn.onMainScreen(activity);
        }
    }

    /** The source's name of the add-on saved with a place; null for none or one this build doesn't have */
    public static String sourceName(Context context, String id) {
        for (AddOn addOn : ALL) {
            if (addOn.id().equals(id)) {
                return addOn.sourceName(context);
            }
        }
        return null;
    }
}
