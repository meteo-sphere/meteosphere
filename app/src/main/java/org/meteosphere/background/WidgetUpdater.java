package org.meteosphere.background;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.data.WeatherApiException;
import org.meteosphere.data.WeatherClient;
import org.meteosphere.data.model.Place;
import org.meteosphere.location.PositionService;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.widgets.WeatherWidgets;
import org.meteosphere.ui.widgets.settings.WidgetConfig;
import org.meteosphere.util.AppUtils;

/**
 * Keeps the weather widgets up to date: draws them from the weather the app keeps, and loads it anew when it is due,
 * a widget showing "Updating…" meanwhile and "Updated" a moment after; an alarm draws them anew as each hour starts.
 * Its jobs run one after another on {@link BackgroundTaskWorker}'s widget queue; the calls "now" draw on the calling
 * thread
 */
public final class WidgetUpdater {

    private static final String TAG = "WidgetUpdater";

    /** A widget's states, WeatherWidgets.Request's: its place, "Updating…" with a spinner, or "Updated" */
    public static final int STATE_NORMAL = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_REFRESHED = 2;

    /** The widget's id in the intents of its taps */
    public static final String EXTRA_WIDGET = "org.meteosphere.extra.WIDGET_ID";
    /** A stored place got the forecast a widget loaded, its id in "place_id": the city page shows it */
    public static final String ACTION_WIDGET_LOADED = "org.meteosphere.action.WIDGET_PLACE_LOADED";

    private static final String ACTION_DRAW = "org.meteosphere.action.DRAW_WIDGET";
    private static final String ACTION_DRAW_ALL = "org.meteosphere.action.DRAW_ALL_WIDGETS";
    private static final String ACTION_REFRESH = "org.meteosphere.action.REFRESH_ONE_WIDGET";
    private static final String ACTION_REFRESH_ALL = "org.meteosphere.action.REFRESH_ALL_WIDGETS";
    private static final String EXTRA_STATE = "state";
    private static final String EXTRA_FORCED = "forced";
    // As WeatherUpdater's broadcasts have it
    private static final String EXTRA_PLACE_ID = "place_id";
    // A widget's own, so that a new refresh replaces the one waiting
    private static final String NORMAL_AGAIN = "org.meteosphere.work.widget_normal.";

    // "Updating…" stays at least this long, for a refresh that loads nothing to be seen as one
    private static final long LOADING_MS = 3000;
    private static final long REFRESHED_MS = 2000;
    private static final long REFRESH_ALL_WAIT_MS = 2000;
    // An older position may be far from where the device is now
    private static final long POSITION_MAX_AGE_MS = 30 * 60 * 1000L;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    // As AstroWidget's: Android 12 lets an inexact alarm be up to 10 minutes late
    private static final long REDRAW_WINDOW_MS = 10 * MINUTE_MS;
    // A margin past the hour, so that the drawing surely reads the new hour
    private static final long REDRAW_DELAY_MS = 30 * 1000L;
    // A change of the offset may leave the clock off the hour after a jump to it, as Lord Howe's of half an hour does;
    // the next jump then lands on it
    private static final int HOUR_STEPS = 4;

    private WidgetUpdater() {
    }

    /**
     * Draws the widget in the state from the weather it has, on the calling thread, which isn't the main one: for a
     * widget just set up, which the launcher shows with a spinner till it is drawn
     */
    public static void drawNow(Context context, int widgetId, int state) {
        draw(context, widgetId, state);
    }

    /**
     * Draws every weather widget from the weather it has, on the calling thread, which isn't the main one; then sets
     * the next hour's alarm
     */
    public static void drawAllNow(Context context) {
        AppSettings settings = AppSettings.getInstance();
        // From the start: a widget drawn before an hour began shows the hour before
        long start = System.currentTimeMillis();
        for (int widgetId : settings.getWidgets()) {
            draw(context, widgetId, STATE_NORMAL);
        }
        settings.saveWidgetsDrawnTime(start);
        scheduleHourRedraw(context);
    }

    /** Draws the widget anew from the weather it has, loading nothing */
    public static void redraw(Context context, int widgetId) {
        enqueue(context, drawJob(widgetId, STATE_NORMAL));
    }

    /** Draws every weather widget anew from the weather it has, loading nothing; then sets the next hour's alarm */
    static void redrawAll(Context context) {
        enqueue(context, new Intent(ACTION_DRAW_ALL));
    }

    /** Refreshes the widget: "Updating…", its weather loaded when it is due, then "Updated" for a moment */
    public static void refresh(Context context, int widgetId, boolean forced) {
        enqueue(context, new Intent(ACTION_REFRESH).putExtra(EXTRA_WIDGET, widgetId).putExtra(EXTRA_FORCED, forced));
    }

    /** Refreshes the widgets that are due, all of them when forced */
    public static void refreshAll(Context context, boolean forced) {
        enqueue(context, new Intent(ACTION_REFRESH_ALL).putExtra(EXTRA_FORCED, forced));
    }

    private static void enqueue(Context context, Intent job) {
        BackgroundTaskWorker.enqueue(context, BackgroundTaskWorker.TARGET_WIDGET, job);
    }

    private static Intent drawJob(int widgetId, int state) {
        return new Intent(ACTION_DRAW).putExtra(EXTRA_WIDGET, widgetId).putExtra(EXTRA_STATE, state);
    }

    /** Runs a job queued here */
    static void run(Context context, Intent job) {
        int widgetId = job.getIntExtra(EXTRA_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID);
        boolean forced = job.getBooleanExtra(EXTRA_FORCED, false);
        String action = job.getAction();
        if (ACTION_DRAW.equals(action)) {
            draw(context, widgetId, job.getIntExtra(EXTRA_STATE, STATE_NORMAL));
        } else if (ACTION_DRAW_ALL.equals(action)) {
            drawAllNow(context);
        } else if (ACTION_REFRESH.equals(action)) {
            refreshNow(context, widgetId, forced);
        } else if (ACTION_REFRESH_ALL.equals(action)) {
            refreshAllNow(context, forced);
        }
    }

    // --- Drawing ---

    // Nothing for a widget the launcher doesn't know or one of no size yet. One that fails to draw stays as it was,
    // and the widgets and the jobs after it are drawn still
    private static void draw(Context context, int widgetId, int state) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager.getAppWidgetInfo(widgetId) == null) {
            return;
        }
        Point size = AppUtils.widgetSize(context, widgetId);
        if (size.x == 0 || size.y == 0) {
            return;
        }
        try {
            WidgetConfig preferences = new WidgetConfig(context, widgetId);
            WeatherWidgets.Request request = new WeatherWidgets.Request(preferences.getKind(), size.x, size.y);
            request.place = AppSettings.getInstance().getPlace(preferences.getPlaceId());
            request.backgroundColor = preferences.getGlassColor();
            request.showClock = preferences.isClockShown();
            request.showWind = preferences.isShowWind();
            request.showPrecipitation = preferences.isShowPrecipitation();
            request.state = state;
            WeatherWidgets.Drawn drawn = WeatherWidgets.draw(context, widgetId, request);
            if (drawn == null) {
                return;
            }
            if (drawn.withTime != preferences.isTimeDrawn()) {
                preferences.edit().setTimeDrawn(drawn.withTime).apply();
            }
            manager.updateAppWidget(widgetId, drawn.views);
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Can't draw the widget " + widgetId, e);
        }
    }

    // Normal again a moment after "Updated"
    private static void normalLater(Context context, int widgetId) {
        BackgroundTaskWorker.enqueueDelayed(context, BackgroundTaskWorker.TARGET_WIDGET, drawJob(widgetId, STATE_NORMAL),
                NORMAL_AGAIN + widgetId, REFRESHED_MS);
    }

    // --- The redraw at the start of an hour ---

    /**
     * Sets the alarm that draws the weather widgets anew as an hour starts by the phone's clock or a widget's place's,
     * whichever comes first: the weather now, the chart's now column and the date go by the hour, and the app may be
     * frozen by then, no load or refresh coming. Set again at every drawing of them all and every refresh, it replaces
     * the one set, never past an hour still to be drawn; with no weather widget left, takes it away
     */
    public static void scheduleHourRedraw(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent redraw = hourRedrawIntent(context);
        AppSettings settings = AppSettings.getInstance();
        List<Integer> widgetIds = settings.getWidgets();
        if (widgetIds.isEmpty()) {
            alarmManager.cancel(redraw);
            return;
        }
        // Each place once, however many widgets show it; the current location's for -1, none for a widget without one
        Set<Integer> locationIds = new HashSet<>();
        for (int widgetId : widgetIds) {
            locationIds.add(new WidgetConfig(context, widgetId).getPlaceId());
        }
        List<TimeZone> zones = new ArrayList<>();
        for (int locationId : locationIds) {
            TimeZone zone = settings.getLocationTimeZone(locationId);
            if (zone != null) {
                zones.add(zone);
            }
        }
        long time = hourRedrawTime(settings.getWidgetsDrawnTime(), System.currentTimeMillis(), TimeZone.getDefault(), zones);
        // RTC wakes nothing: due while the screen is off, it comes when the screen turns on
        alarmManager.setWindow(AlarmManager.RTC, time, REDRAW_WINDOW_MS, redraw);
    }

    /**
     * When the alarm draws the widgets anew: the first start of an hour, plus half a minute, after they were last all
     * drawn, as one drawn in the half minute after an hour starts may show the hour before by the app's clock. Set
     * again while it waits, as by a refresh, it so never moves past an hour still to be drawn; now when that is past.
     * A last drawing ahead of now, the clock set back, counts as now; 0, none, as long ago
     */
    static long hourRedrawTime(long drawn, long now, TimeZone phone, List<TimeZone> places) {
        long since = Math.min(drawn, now);
        return Math.max(now, nextHourStart(since - REDRAW_DELAY_MS, phone, places) + REDRAW_DELAY_MS);
    }

    // The same each time, so that setting the alarm again replaces it
    private static PendingIntent hourRedrawIntent(Context context) {
        Intent intent = new Intent(context, WidgetUpdateReceiver.class).setAction(WidgetUpdateReceiver.ACTION_REDRAW_WIDGETS);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The earliest start of a whole hour after the moment, by the phone's clock or any of the places' */
    static long nextHourStart(long now, TimeZone phone, List<TimeZone> places) {
        long next = nextHourStart(now, phone);
        for (TimeZone zone : places) {
            next = Math.min(next, nextHourStart(now, zone));
        }
        return next;
    }

    // The next whole hour of the zone's clock
    private static long nextHourStart(long now, TimeZone zone) {
        return nextTimeInHour(now, zone, 0);
    }

    /**
     * The first moment after the given one when the zone's clock is the time past a whole hour, jumping to it by the
     * offset where each jump starts: the notification's half past as well
     */
    static long nextTimeInHour(long now, TimeZone zone, long pastHourMs) {
        long time = now + 1;
        for (int i = 0; i < HOUR_STEPS; i++) {
            long pastTarget = Math.floorMod(time + zone.getOffset(time) - pastHourMs, HOUR_MS);
            if (pastTarget == 0) {
                break;
            }
            time += HOUR_MS - pastTarget;
        }
        return time;
    }

    // --- Refreshing ---

    private static void refreshNow(Context context, int widgetId, boolean forced) {
        // One gone from the home screen has nothing to show its weather on
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) != null) {
            refreshShown(context, widgetId, forced);
        }
        scheduleHourRedraw(context);
    }

    private static void refreshShown(Context context, int widgetId, boolean forced) {
        long start = System.currentTimeMillis();
        draw(context, widgetId, STATE_LOADING);
        WidgetConfig preferences = new WidgetConfig(context, widgetId);
        int locationId = preferences.getPlaceId();
        boolean loads = refreshLoads(forced, AppSettings.getInstance().getPlace(locationId), preferences.isTimeDrawn(), start,
                UpdatePeriod.ms(context));
        if (loads) {
            loadWeather(context, locationId, forced);
        }
        waitUntil(start + LOADING_MS);
        draw(context, widgetId, STATE_REFRESHED);
        if (loads) {
            preferences.edit().setLastRefresh(System.currentTimeMillis()).apply();
        }
        normalLater(context, widgetId);
    }

    /** refreshAll() on the calling thread, as the periodic refresh has it on its own */
    static void refreshAllNow(Context context, boolean forced) {
        refreshDue(context, forced);
        scheduleHourRedraw(context);
    }

    private static void refreshDue(Context context, boolean forced) {
        AppSettings settings = AppSettings.getInstance();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        long now = System.currentTimeMillis();
        long periodMs = UpdatePeriod.ms(context);
        List<Integer> due = new ArrayList<>();
        for (int widgetId : settings.getWidgets()) {
            // One gone from the home screen has nothing to show its weather on
            if (manager.getAppWidgetInfo(widgetId) == null) {
                continue;
            }
            WidgetConfig preferences = new WidgetConfig(context, widgetId);
            boolean withTime = preferences.isTimeDrawn();
            // Whether the place is kept, without copying its forecasts
            if (refreshedWithAll(forced, settings.hasCachedLocation(preferences.getPlaceId()), withTime, preferences.getLastRefresh(),
                    now, periodMs)) {
                due.add(widgetId);
                draw(context, widgetId, withTime ? STATE_NORMAL : STATE_LOADING);
            }
        }
        if (due.isEmpty()) {
            return;
        }
        waitUntil(System.currentTimeMillis() + REFRESH_ALL_WAIT_MS);
        for (int widgetId : due) {
            WidgetConfig preferences = new WidgetConfig(context, widgetId);
            boolean withTime = preferences.isTimeDrawn();
            boolean loaded = loadWeather(context, preferences.getPlaceId(), forced);
            if (loaded) {
                preferences.edit().setLastRefresh(System.currentTimeMillis()).apply();
            }
            // "Updated" after a failure, as after any load for a widget without the time: once for one that is both
            if (!loaded || !withTime) {
                draw(context, widgetId, STATE_REFRESHED);
            }
            if (withTime) {
                draw(context, widgetId, STATE_NORMAL);
            } else {
                normalLater(context, widgetId);
            }
        }
    }

    /** Whether refreshing the widget alone loads its weather: forced, without weather, showing the time or due */
    static boolean refreshLoads(boolean forced, Place place, boolean withTime, long now, long periodMs) {
        return forced || place == null || withTime || UpdatePeriod.isDue(place.loadedAt, now, periodMs);
    }

    /**
     * Whether a refresh of all the widgets takes this one: forced, without weather, showing the time, or refreshed
     * long enough ago
     */
    static boolean refreshedWithAll(boolean forced, boolean hasWeather, boolean withTime, long lastRefresh, long now, long periodMs) {
        return forced || !hasWeather || withTime || UpdatePeriod.isDue(lastRefresh, now, periodMs);
    }

    // Waits till the time; interrupted, it ends early and keeps the interruption for the work's end
    private static void waitUntil(long time) {
        long left = time - System.currentTimeMillis();
        if (left <= 0) {
            return;
        }
        try {
            Thread.sleep(left);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Loading ---

    /** Whether loading the weather asks for anything: forced, or the place kept missing or due */
    static boolean loadAsks(boolean forced, Place stored, long now, long periodMs) {
        return forced || stored == null || UpdatePeriod.isDue(stored.loadedAt, now, periodMs);
    }

    // The weather of the widget's place loaded when it is due, and the current location's found anew; false when a
    // request failed. An answer without a place or a forecast isn't a failure: the load is done, keeping nothing
    private static boolean loadWeather(Context context, int locationId, boolean forced) {
        AppSettings settings = AppSettings.getInstance();
        long now = System.currentTimeMillis();
        long periodMs = UpdatePeriod.ms(context);
        Place stored = settings.getPlace(locationId);
        if (!loadAsks(forced, stored, now, periodMs)) {
            return true;
        }
        try {
            if (locationId == -1) {
                loadCurrentLocation(context, settings, now, periodMs);
            } else if (loadAsks(false, stored, now, periodMs)) {
                // Forced or not, a place's forecast is asked for only when there is none or it is due
                Place forecast = forecast(context, settings, locationId);
                if (forecast != null) {
                    store(context, settings, locationId, forecast);
                }
            }
            return true;
        } catch (WeatherApiException | RuntimeException e) {
            Log.w(TAG, "Can't load the weather of the place " + locationId, e);
            return false;
        }
    }

    // The current location as the device's position has it: the nearest place asked for when the device moved, then
    // its forecast when due
    private static void loadCurrentLocation(Context context, AppSettings settings, long now, long periodMs) throws WeatherApiException {
        Place current = settings.getCurrentPlace();
        int target = current != null ? current.id : -1;
        Location position = position(context, settings);
        // Kept only once the place at it is settled: kept before, a failure would leave the old city as one the device
        // hasn't moved from, till it moved again
        Location newPosition = null;
        if (position != null) {
            Location kept = settings.getPosition();
            if (current == null || kept == null || moved(kept.getLatitude(), position.getLatitude())
                    || moved(kept.getLongitude(), position.getLongitude())) {
                ArrayList<Place> nearest = WeatherClient.getLocationsNear(position.getLatitude(), position.getLongitude(), 1,
                        settings.language, false, timeoutMs(context));
                // None near: the place before stays, and the position is asked about again next time
                if (!nearest.isEmpty()) {
                    target = nearest.get(0).id;
                    newPosition = position;
                }
            }
        }
        if (target == -1) {
            return;
        }
        Place stored = keptPlace(settings, current, target);
        Place place;
        if (loadAsks(false, stored, now, periodMs)) {
            place = forecast(context, settings, target);
            // No forecast in the answer: nothing kept, the position neither
            if (place == null) {
                return;
            }
            if (settings.getStoredIds().contains(target)) {
                store(context, settings, target, place);
            }
        } else {
            // A copy: the one kept is the app's, named as itself
            place = stored.copy();
        }
        // Before the naming, which finds the locality at the position kept
        if (newPosition != null) {
            settings.savePosition(newPosition);
        }
        WeatherUpdater.nameCurrentLocation(context, place);
        settings.saveCurrentPlace(place);
    }

    // The place as the app keeps it: the current location when it is the place, or the stored one, whichever is
    // younger, as the same city may be kept both ways; null for neither
    private static Place keptPlace(AppSettings settings, Place current, int locationId) {
        Place stored = settings.getPlace(locationId);
        if (current != null && current.id == locationId && (stored == null || current.loadedAt > stored.loadedAt)) {
            return current;
        }
        return stored;
    }

    /**
     * Whether a coordinate moved from the one kept by 0.01° or more, both rounded to that. Their difference may fall a
     * hair short of the step as a double, so the rounded values are compared
     */
    static boolean moved(double kept, double now) {
        return AppUtils.roundToHundredths(kept) != AppUtils.roundToHundredths(now);
    }

    // The device's last position younger than half an hour, when the app may know it and a provider is on: the network's,
    // or the GPS while the app's setting lets it be used. Null for none
    private static Location position(Context context, AppSettings settings) {
        if (!AppUtils.mayLocate(context)) {
            return null;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                && !(settings.withGps && manager.isProviderEnabled(LocationManager.GPS_PROVIDER))) {
            return null;
        }
        Location position = PositionService.lastKnownPosition(context);
        return position != null && System.currentTimeMillis() - position.getTime() < POSITION_MAX_AGE_MS ? position : null;
    }

    // The place's forecast, its load time now so that it isn't asked for again at once; null when the answer has none
    private static Place forecast(Context context, AppSettings settings, int locationId) throws WeatherApiException {
        ArrayList<Place> forecasts = WeatherClient.getForecast(locationId, settings.language, timeoutMs(context));
        if (forecasts.isEmpty()) {
            return null;
        }
        Place forecast = forecasts.get(0);
        forecast.loadedAt = System.currentTimeMillis();
        return forecast;
    }

    // The forecast kept as the place's, and the city page told
    private static void store(Context context, AppSettings settings, int locationId, Place forecast) {
        settings.storePlace(forecast);
        AppUtils.sendAppBroadcast(context, new Intent(ACTION_WIDGET_LOADED).putExtra(EXTRA_PLACE_ID, locationId));
    }

    private static int timeoutMs(Context context) {
        return context.getResources().getInteger(R.integer.request_timeout_ms);
    }
}
