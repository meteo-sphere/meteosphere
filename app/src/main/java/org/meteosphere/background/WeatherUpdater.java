package org.meteosphere.background;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;
import androidx.core.content.IntentCompat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.meteosphere.R;
import org.meteosphere.addon.AddOns;
import org.meteosphere.data.WeatherApiException;
import org.meteosphere.data.WeatherClient;
import org.meteosphere.data.model.Place;
import org.meteosphere.location.PositionService;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.widgets.AstroWidget;
import org.meteosphere.ui.widgets.EarthquakeWidget;
import org.meteosphere.util.AppUtils;

/**
 * Loads the weather of the places the app keeps: the stored places when it is due, one of them, the current location
 * by the device's position, the places near the device, and the places' names in a new language. Its jobs run one
 * after another on {@link BackgroundTaskWorker}'s weather queue, and each tells what it changed by a broadcast, a
 * place's id in "place_id"
 */
public final class WeatherUpdater {

    private static final String TAG = "WeatherUpdater";

    /** Loads every stored place whose weather is due, all of them when forced, then the current location's */
    public static final String ACTION_LOAD_ALL = "org.meteosphere.action.LOAD_ALL_WEATHER";
    /** Finds the current location by the device's position; with a place's id in EXTRA_PLACE_ID, makes it that place */
    public static final String ACTION_FIND_CURRENT = "org.meteosphere.action.FIND_CURRENT_LOCATION";
    /** Loads the stored place of the id in EXTRA_PLACE_ID when its weather is due */
    public static final String ACTION_LOAD_PLACE = "org.meteosphere.action.LOAD_PLACE_WEATHER";
    public static final String EXTRA_PLACE_ID = "place_id";
    /** The current location got another place, weather or name */
    public static final String ACTION_CURRENT_CHANGED = "org.meteosphere.action.CURRENT_LOCATION_CHANGED";
    /** The nearby list may be new: a search for the device's position ended a step, how it found it in "found_by" */
    public static final String ACTION_NEARBY_CHANGED = "org.meteosphere.action.NEARBY_PLACES_CHANGED";
    /** A stored place got its forecast, its id in "place_id" */
    public static final String ACTION_PLACE_LOADED = "org.meteosphere.action.PLACE_LOADED";
    /** Any of the stored places changed */
    public static final String ACTION_PLACES_CHANGED = "org.meteosphere.action.PLACES_CHANGED";
    /** A stored place's forecast couldn't be had, its id in "place_id" */
    public static final String ACTION_PLACE_FAILED = "org.meteosphere.action.PLACE_LOAD_FAILED";
    /** How long the nearby list is shown, and not asked for again while the device stays */
    public static final long NEARBY_FRESH_MS = 30 * 60 * 1000L;

    private static final String ACTION_FIND_NEARBY = "org.meteosphere.action.FIND_NEARBY_PLACES";
    private static final String ACTION_UPDATE_NAMES = "org.meteosphere.action.UPDATE_NAMES";
    private static final String ACTION_UPDATE_FROM_NEW_SOURCE = "org.meteosphere.action.UPDATE_FROM_NEW_SOURCE";
    private static final String EXTRA_FORCED = "forced";
    private static final String EXTRA_NEW_POSITION = "new_position";
    private static final String EXTRA_OLD_POSITION = "old_position";
    private static final String EXTRA_TYPE = "type";
    // As the receivers of the broadcasts read them
    private static final String EXTRA_BROADCAST_PLACE_ID = "place_id";
    private static final String EXTRA_FOUND_BY = "found_by";
    // A search by the GPS has ended: the nearby tab shows what it has rather than a progress
    private static final String TYPE_GPS = "gps";

    // An older position may be far from where the device is now
    private static final long POSITION_MAX_AGE_MS = 30 * 60 * 1000L;
    private static final int NEARBY_COUNT = 16;
    // The most ids the server takes in one request
    static final int NAMES_PART = 100;

    /** How the current location becomes a place: not at all, kept as it is, taken from the stored place, or asked for */
    enum CurrentWay { NONE, FRESH, STORED, REQUEST }

    /** How a stored place gets its weather: not at all, from the current location at the same place, or asked for */
    enum PlaceWay { NONE, FROM_CURRENT, REQUEST }

    private WeatherUpdater() {
    }

    /** Loads the weather of every stored place that is due, then the current location's */
    public static void loadAllWeather(Context context) {
        enqueue(context, new Intent(ACTION_LOAD_ALL));
    }

    /** Loads the stored place's weather when it is due */
    public static void loadLocationWeather(Context context, int locationId) {
        enqueue(context, placeJob(locationId, false));
    }

    /** Loads the stored place's weather, due or not: its source changed */
    public static void reloadLocationWeather(Context context, int locationId) {
        enqueue(context, placeJob(locationId, true));
    }

    /** Finds the current location's place by the device's position and loads its weather when it is due */
    public static void findCurrentLocation(Context context) {
        enqueue(context, new Intent(ACTION_FIND_CURRENT));
    }

    /**
     * Asks for the places near the new position, when there is one and the list kept doesn't do for it, then tells of
     * the list with the type, the provider that found the position
     */
    public static void findNearbyPlaces(Context context, Location newPosition, Location oldPosition, String type) {
        enqueue(context, new Intent(ACTION_FIND_NEARBY).putExtra(EXTRA_NEW_POSITION, newPosition)
                .putExtra(EXTRA_OLD_POSITION, oldPosition).putExtra(EXTRA_TYPE, type));
    }

    /** Asks for the names of every place the app keeps in its new language, then redraws all that shows them */
    public static void loadNames(Context context) {
        enqueue(context, new Intent(ACTION_UPDATE_NAMES));
    }

    /** Loads every stored place and the nearby places anew from the source just chosen */
    public static void updateFromNewSource(Context context) {
        enqueue(context, new Intent(ACTION_UPDATE_FROM_NEW_SOURCE));
    }

    private static Intent placeJob(int locationId, boolean forced) {
        return new Intent(ACTION_LOAD_PLACE).putExtra(EXTRA_PLACE_ID, locationId).putExtra(EXTRA_FORCED, forced);
    }

    private static void enqueue(Context context, Intent job) {
        BackgroundTaskWorker.enqueue(context, BackgroundTaskWorker.TARGET_WEATHER, job);
    }

    /**
     * Runs a job on the calling thread, which isn't the main one: one queued here, or one the periodic refresh or the
     * daily forecast runs on its own thread
     */
    static void run(Context context, Intent job) {
        boolean forced = job.getBooleanExtra(EXTRA_FORCED, false);
        int locationId = job.getIntExtra(EXTRA_PLACE_ID, -1);
        String action = job.getAction();
        if (ACTION_LOAD_ALL.equals(action)) {
            updateAll(context, forced);
        } else if (ACTION_FIND_CURRENT.equals(action)) {
            // No id, or -1 as the daily forecast puts for the current location: the place is found by the device's position
            if (locationId == -1) {
                updateCurrentByPosition(context, forced);
            } else {
                updateCurrent(context, locationId, forced, null);
            }
        } else if (ACTION_LOAD_PLACE.equals(action)) {
            updatePlace(context, locationId, forced);
        } else if (ACTION_FIND_NEARBY.equals(action)) {
            updateNearby(context, IntentCompat.getParcelableExtra(job, EXTRA_NEW_POSITION, Location.class),
                    IntentCompat.getParcelableExtra(job, EXTRA_OLD_POSITION, Location.class), job.getStringExtra(EXTRA_TYPE));
        } else if (ACTION_UPDATE_NAMES.equals(action)) {
            updateNames(context);
        } else if (ACTION_UPDATE_FROM_NEW_SOURCE.equals(action)) {
            updateAll(context, true);
            updateNearby(context, AppSettings.getInstance().getPosition(), null, TYPE_GPS);
        }
    }

    // --- The stored places ---

    private static void updateAll(Context context, boolean forced) {
        AppSettings settings = AppSettings.getInstance();
        long periodMs = UpdatePeriod.ms(context);
        boolean loaded = false;
        for (Place place : settings.getStoredPlaces()) {
            long now = System.currentTimeMillis();
            if (!storedAsked(forced, place.loadedAt, AddOns.awaitsNew(place, now), now, periodMs)) {
                continue;
            }
            Place forecast = forecast(context, place.id);
            if (forecast != null) {
                settings.storePlace(forecast);
                broadcastPlace(context, ACTION_PLACE_LOADED, place.id);
                loaded = true;
            } else {
                broadcastPlace(context, ACTION_PLACE_FAILED, place.id);
            }
        }
        if (loaded) {
            AppUtils.sendAppBroadcast(context, new Intent(ACTION_PLACES_CHANGED));
            tellNotification(context);
        }
        Place current = settings.getCurrentPlace();
        if (current != null) {
            updateCurrent(context, current.id, forced, null);
        }
    }

    /**
     * Whether a stored place's forecast is asked for with all the others: forced, due, or an add-on's weather of it
     * about to be renewed before the update period ends
     */
    static boolean storedAsked(boolean forced, long loadedAt, boolean awaitsAddOn, long now, long periodMs) {
        return forced || awaitsAddOn || UpdatePeriod.isDue(loadedAt, now, periodMs);
    }

    private static void updatePlace(Context context, int locationId, boolean forced) {
        AppSettings settings = AppSettings.getInstance();
        Place stored = locationId != -1 ? settings.getPlace(locationId) : null;
        Place current = settings.getCurrentPlace();
        PlaceWay way = placeWay(locationId, forced, stored, current, System.currentTimeMillis(), UpdatePeriod.ms(context));
        int shown = settings.getStatusBarNotificationPlaceId();
        if (way == PlaceWay.FROM_CURRENT) {
            // The same city kept both ways shares the forecast, each keeping the name it has
            Place place = current.copy();
            place.name = stored.name;
            place.country = stored.country;
            place.region = stored.region;
            settings.storePlace(place);
            broadcastPlace(context, ACTION_PLACE_LOADED, locationId);
            // The current location itself stays as it is
            if (shown == locationId) {
                tellNotification(context);
            }
        } else if (way == PlaceWay.REQUEST) {
            Place forecast = forecast(context, locationId);
            if (forecast == null) {
                return;
            }
            settings.storePlace(forecast);
            broadcastPlace(context, ACTION_PLACE_LOADED, locationId);
            if (shown == locationId) {
                tellNotification(context);
            }
            // As it is now, after the request
            current = settings.getCurrentPlace();
            if (current != null && current.id == locationId) {
                Place place = forecast.copy();
                nameCurrentLocation(context, place);
                settings.saveCurrentPlace(place);
                if (shown == -1) {
                    tellNotification(context);
                }
                AppUtils.sendAppBroadcast(context, new Intent(ACTION_CURRENT_CHANGED));
            }
        }
    }

    /**
     * How the stored place gets its weather: only one stored and due, or forced; from the current location while that
     * is the same place with weather that isn't due, unless forced; asked for otherwise
     */
    static PlaceWay placeWay(int locationId, boolean forced, Place stored, Place current, long now, long periodMs) {
        if (locationId == -1 || stored == null || !forced && !UpdatePeriod.isDue(stored.loadedAt, now, periodMs)) {
            return PlaceWay.NONE;
        }
        if (!forced && current != null && current.id == locationId && !UpdatePeriod.isDue(current.loadedAt, now, periodMs)) {
            return PlaceWay.FROM_CURRENT;
        }
        return PlaceWay.REQUEST;
    }

    // --- The current location ---

    // The nearest place asked for when the device moved or there is no current location yet, its weather then;
    // otherwise the place the current location has. A position too old, or none, leaves the place as it is
    private static void updateCurrentByPosition(Context context, boolean forced) {
        AppSettings settings = AppSettings.getInstance();
        Place current = settings.getCurrentPlace();
        Location position = PositionService.lastKnownPosition(context);
        if (position != null && System.currentTimeMillis() - position.getTime() < POSITION_MAX_AGE_MS) {
            Location kept = settings.getPosition();
            boolean moved = kept != null && moved(kept.getLatitude(), kept.getLongitude(), position.getLatitude(), position.getLongitude());
            if (nearestAsked(kept != null, current != null, moved)) {
                Place nearest = nearestPlace(context, position);
                if (nearest != null) {
                    updateCurrent(context, nearest.id, forced, position);
                    return;
                }
            }
        }
        if (current != null) {
            updateCurrent(context, current.id, forced, null);
        }
    }

    /** Whether the place at the device's position is asked for: with no position kept, no current location yet, or a move */
    static boolean nearestAsked(boolean hasKept, boolean hasCurrent, boolean moved) {
        return !hasKept || !hasCurrent || moved;
    }

    /**
     * Makes the current location the place, as currentWay() has it; the device's position given when it found the
     * place. The position is kept once the current location is the place: kept before, a failure would leave the old
     * city as one the device hasn't moved from, till it moved again
     */
    private static void updateCurrent(Context context, int locationId, boolean forced, Location position) {
        AppSettings settings = AppSettings.getInstance();
        Place current = settings.getCurrentPlace();
        Place stored = locationId != -1 ? settings.getPlace(locationId) : null;
        CurrentWay way = currentWay(locationId, forced, current, stored, System.currentTimeMillis(), UpdatePeriod.ms(context));
        Place place;
        if (way == CurrentWay.FRESH && position != null) {
            // Settled at the position all the same, which the next run so doesn't ask about again; the locality at it
            // may be another
            place = current.copy();
        } else if (way == CurrentWay.STORED) {
            place = stored;
        } else if (way == CurrentWay.REQUEST) {
            place = forecast(context, locationId);
            if (place == null) {
                return;
            }
            if (stored != null) {
                settings.storePlace(place);
                // The widgets and the city page showing the stored place draw its new weather
                broadcastPlace(context, ACTION_PLACE_LOADED, locationId);
            }
        } else {
            return;
        }
        // Before the naming, which finds the locality at the position kept
        if (position != null) {
            settings.savePosition(position);
        }
        // A copy already: the place it was made from keeps its own name
        nameCurrentLocation(context, place);
        settings.saveCurrentPlace(place);
        AppUtils.sendAppBroadcast(context, new Intent(ACTION_CURRENT_CHANGED));
        int shown = settings.getStatusBarNotificationPlaceId();
        if (shown == -1 || shown == locationId) {
            tellNotification(context);
        }
    }

    /**
     * How the current location becomes the place: as it is, while it is the place with weather that isn't due and not
     * forced; the stored place's copy while that one's weather isn't due, forced or not; asked for otherwise. None for -1
     */
    static CurrentWay currentWay(int locationId, boolean forced, Place current, Place stored, long now, long periodMs) {
        if (locationId == -1) {
            return CurrentWay.NONE;
        }
        if (!forced && current != null && current.id == locationId && !UpdatePeriod.isDue(current.loadedAt, now, periodMs)) {
            return CurrentWay.FRESH;
        }
        if (stored != null && !UpdatePeriod.isDue(stored.loadedAt, now, periodMs)) {
            return CurrentWay.STORED;
        }
        return CurrentWay.REQUEST;
    }

    /**
     * Names the place as the current location: "Current location" for its own name, and the locality at the position
     * kept, when one is found, for the city's
     */
    static void nameCurrentLocation(Context context, Place place) {
        place.customName = context.getString(R.string.current_location);
        String locality = AppUtils.localityAt(context, AppSettings.getInstance().getPosition());
        if (locality != null && !locality.isEmpty()) {
            place.name = locality;
        }
    }

    /**
     * Whether the position moved from the one kept by 0.01° or more either way, both rounded to that. Their difference
     * may fall a hair short of the step as a double, so the rounded values are compared
     */
    static boolean moved(double keptLatitude, double keptLongitude, double latitude, double longitude) {
        return WidgetUpdater.moved(keptLatitude, latitude) || WidgetUpdater.moved(keptLongitude, longitude);
    }

    // The place nearest to the position; null when there is none or the request failed
    private static Place nearestPlace(Context context, Location position) {
        try {
            ArrayList<Place> places = WeatherClient.getLocationsNear(position.getLatitude(), position.getLongitude(), 1,
                    AppSettings.getInstance().language, false, timeoutMs(context));
            return places.isEmpty() ? null : places.get(0);
        } catch (WeatherApiException e) {
            Log.w(TAG, "Can't find the place at the device's position", e);
            return null;
        }
    }

    // --- The places near the device ---

    private static void updateNearby(Context context, Location newPosition, Location oldPosition, String type) {
        AppSettings settings = AppSettings.getInstance();
        if (newPosition != null) {
            ArrayList<Place> kept = settings.getNearbyPlaces();
            long age = System.currentTimeMillis() - settings.getNearbyTime().getTimeInMillis();
            boolean moved = oldPosition != null && moved(oldPosition.getLatitude(), oldPosition.getLongitude(),
                    newPosition.getLatitude(), newPosition.getLongitude());
            if (nearbyAsked(oldPosition != null, moved, kept != null && !kept.isEmpty(), age, UpdatePeriod.ms(context))) {
                ArrayList<Place> nearby = nearby(context, newPosition);
                if (nearby != null && !nearby.isEmpty()) {
                    settings.saveNearbyPlaces(nearby);
                    enqueue(context, new Intent(ACTION_FIND_CURRENT).putExtra(EXTRA_PLACE_ID, nearest(nearby).id));
                }
            }
        }
        AppUtils.sendAppBroadcast(context, new Intent(ACTION_NEARBY_CHANGED).putExtra(EXTRA_FOUND_BY, type));
    }

    /**
     * Whether the places near a new position are asked for: with no position before it, no list kept, one as old as
     * NEARBY_FRESH_MS or the update period, whichever is shorter, or a move from the position before
     */
    static boolean nearbyAsked(boolean hasOldPosition, boolean moved, boolean hasList, long listAgeMs, long periodMs) {
        return !hasOldPosition || !hasList || listAgeMs >= Math.min(NEARBY_FRESH_MS, periodMs) || moved;
    }

    // The places near the position with their weather now; null when the request failed
    private static ArrayList<Place> nearby(Context context, Location position) {
        try {
            return WeatherClient.getLocationsNear(position.getLatitude(), position.getLongitude(), NEARBY_COUNT,
                    AppSettings.getInstance().language, true, timeoutMs(context));
        } catch (WeatherApiException e) {
            Log.w(TAG, "Can't load the places near the device", e);
            return null;
        }
    }

    /** The place of the least distance; the first of them for the same one, null for none */
    static Place nearest(List<Place> places) {
        Place nearest = null;
        for (Place place : places) {
            if (nearest == null || place.distanceKm < nearest.distanceKm) {
                nearest = place;
            }
        }
        return nearest;
    }

    // --- The names in a new language ---

    // The weather comes as codes the app describes in its own words: only the names are asked for anew
    private static void updateNames(Context context) {
        AppSettings settings = AppSettings.getInstance();
        Place current = settings.getCurrentPlace();
        ArrayList<Place> nearby = settings.getNearbyPlaces();
        ArrayList<Place> found = settings.getFoundPlaces();
        Map<Integer, Place> named = names(context, nameIds(settings.getStoredIds(), current, nearby, found));
        if (!named.isEmpty()) {
            settings.renameLocations(named);
            AppUtils.sendAppBroadcast(context, new Intent(ACTION_PLACES_CHANGED));
            if (nearby != null && takeNames(nearby, named)) {
                // Saving the list makes it one just loaded: renamed, it is as old as it was
                Calendar loadTime = settings.getNearbyTime();
                settings.saveNearbyPlaces(nearby);
                settings.saveNearbyTime(loadTime);
                AppUtils.sendAppBroadcast(context, new Intent(ACTION_NEARBY_CHANGED).putExtra(EXTRA_FOUND_BY, TYPE_GPS));
            }
            if (takeNames(found, named)) {
                settings.saveFoundPlaces(found);
            }
        }
        if (current != null) {
            Place place = current.copy();
            takeName(place, named.get(place.id));
            nameCurrentLocation(context, place);
            settings.saveCurrentPlace(place);
            AppUtils.sendAppBroadcast(context, new Intent(ACTION_CURRENT_CHANGED));
        }
        tellNotification(context);
        for (int widgetId : settings.getWidgets()) {
            WidgetUpdater.redraw(context, widgetId);
        }
        AstroWidget.updateAll(context, false);
        AstroWidget.updateAll(context, true);
        EarthquakeWidget.loadAndUpdate(context);
    }

    /** The ids of every place the app keeps, each once: the stored ones, the current location's, the nearby and the found */
    static List<Integer> nameIds(List<Integer> storedIds, Place current, List<Place> nearby, List<Place> found) {
        Set<Integer> ids = new LinkedHashSet<>(storedIds);
        if (current != null) {
            ids.add(current.id);
        }
        if (nearby != null) {
            for (Place place : nearby) {
                ids.add(place.id);
            }
        }
        for (Place place : found) {
            ids.add(place.id);
        }
        return new ArrayList<>(ids);
    }

    /** The ids in parts of at most the size, in their order */
    static List<List<Integer>> parts(List<Integer> ids, int size) {
        List<List<Integer>> parts = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += size) {
            parts.add(new ArrayList<>(ids.subList(start, Math.min(ids.size(), start + size))));
        }
        return parts;
    }

    // The places' names by their ids. A failed request ends the asking, as the ones after it would likely wait out
    // their timeouts too; the names that came are kept
    private static Map<Integer, Place> names(Context context, List<Integer> ids) {
        Map<Integer, Place> named = new HashMap<>();
        for (List<Integer> part : parts(ids, NAMES_PART)) {
            try {
                for (Place place : WeatherClient.getLocationsByIds(part, AppSettings.getInstance().language, timeoutMs(context))) {
                    named.put(place.id, place);
                }
            } catch (WeatherApiException e) {
                Log.w(TAG, "Can't load the places' names", e);
                break;
            }
        }
        return named;
    }

    // Whether any of the places took a name
    private static boolean takeNames(List<Place> places, Map<Integer, Place> named) {
        boolean took = false;
        for (Place place : places) {
            took |= takeName(place, named.get(place.id));
        }
        return took;
    }

    // Whether the place took the name, none for null
    private static boolean takeName(Place place, Place named) {
        if (named == null) {
            return false;
        }
        place.name = named.name;
        place.country = named.country;
        place.region = named.region;
        return true;
    }

    // --- Common ---

    // The place's forecast, its load time now; null when the answer has none or the request failed
    private static Place forecast(Context context, int locationId) {
        try {
            ArrayList<Place> forecasts = WeatherClient.getForecast(locationId, AppSettings.getInstance().language,
                    timeoutMs(context));
            if (forecasts.isEmpty()) {
                Log.w(TAG, "No forecast for the place " + locationId);
                return null;
            }
            Place forecast = forecasts.get(0);
            forecast.loadedAt = System.currentTimeMillis();
            return forecast;
        } catch (WeatherApiException e) {
            Log.w(TAG, "Can't load the weather of the place " + locationId, e);
            return null;
        }
    }

    private static void broadcastPlace(Context context, String action, int locationId) {
        AppUtils.sendAppBroadcast(context, new Intent(action).putExtra(EXTRA_BROADCAST_PLACE_ID, locationId));
    }

    // The status bar notification drawn anew, while it is on
    private static void tellNotification(Context context) {
        if (AppSettings.getInstance().isStatusBarNotificationOn()) {
            NotificationUpdater.update(context);
        }
    }

    private static int timeoutMs(Context context) {
        return context.getResources().getInteger(R.integer.request_timeout_ms);
    }
}
