package org.meteosphere.ui.locations;

import android.content.Intent;
import android.widget.Toast;
import androidx.fragment.app.FragmentActivity;
import org.meteosphere.R;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.MessageDialog;
import org.meteosphere.util.AppUtils;

/** Adding places to the favorites, as the search, the nearby places, the list's button and the details offer it */
public final class Favorites {

    private Favorites() {
    }

    /** Whether one more place fits in the favorites; when it doesn't, a dialog says so */
    public static boolean hasRoom(FragmentActivity activity) {
        int limit = activity.getResources().getInteger(R.integer.max_favorites);
        if (AppSettings.getInstance().getFavorites().size() < limit) {
            return true;
        }
        String message = activity.getResources().getQuantityString(R.plurals.favorites_limit, limit, limit);
        MessageDialog.newInstance(message).show(activity.getSupportFragmentManager(), null);
        return false;
    }

    /**
     * Adds a place found or nearby when there is room, its forecast loaded at once: a search result may have no
     * weather at all. The favorites' list hears of it. A favorite already only says so, also when the list is full
     */
    public static void add(FragmentActivity activity, Place place) {
        if (AppSettings.getInstance().getFavorites().contains(place.id)) {
            Toast.makeText(activity, R.string.already_in_favorites, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasRoom(activity)) {
            return;
        }
        AppSettings.getInstance().addFavorite(place.id);
        place.loadedAt = 0;
        AppSettings.getInstance().storePlace(place);
        AppUtils.sendAppBroadcast(activity, new Intent(WeatherUpdater.ACTION_PLACES_CHANGED));
        WeatherUpdater.loadAllWeather(activity.getApplicationContext());
    }
}
