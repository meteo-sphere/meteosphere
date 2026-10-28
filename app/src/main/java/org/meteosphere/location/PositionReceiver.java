package org.meteosphere.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import androidx.core.content.IntentCompat;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.settings.AppSettings;

/**
 * Hears what a step of {@link PositionService}'s search found: keeps a new position, then has the places near it
 * asked for, a step that found nothing telling the nearby tab so too
 */
public class PositionReceiver extends BroadcastReceiver {

    /** A step of the search ended: its provider in EXTRA_PROVIDER, the position in EXTRA_POSITION, none if it found none */
    public static final String ACTION_POSITION_FOUND = "org.meteosphere.action.POSITION_FOUND";
    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_POSITION = "position";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_POSITION_FOUND.equals(intent.getAction())) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (AppSettings.getInstance() == null) {
            AppSettings.init(appContext);
        }
        AppSettings settings = AppSettings.getInstance();
        // Read before the new one replaces it: the nearby places go by how far the device moved from it
        Location before = settings.getPosition();
        Location found = IntentCompat.getParcelableExtra(intent, EXTRA_POSITION, Location.class);
        if (found != null) {
            settings.savePosition(found);
        }
        WeatherUpdater.findNearbyPlaces(appContext, found, before, intent.getStringExtra(EXTRA_PROVIDER));
    }
}
