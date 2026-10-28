package org.meteosphere.ui.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.background.BackgroundTaskWorker;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.data.quake.UsgsEarthquakes;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.quakes.EarthquakeActivity;
import org.meteosphere.ui.quakes.EarthquakesActivity;
import org.meteosphere.ui.widgets.settings.WidgetConfig;
import org.meteosphere.ui.quakes.EarthquakeFormatter;
import org.meteosphere.util.AppUtils;

/**
 * The M5+ earthquakes of the last day, week or month, the latest or the strongest first, with the distances to the current
 * location or the one chosen for the widget. As many rows as the height takes; the background and the text as the weather
 * widgets have them, set per widget.
 */
public class EarthquakeWidget extends AppWidgetProvider {

    private static final String TAG = "EarthquakeWidget";
    private static final int MAX_ROWS = 8;
    // In dp: the padding, the header and a row
    private static final int PADDING_DP = 16;
    private static final int HEADER_DP = 30;
    // The least a row takes: the magnitude's badge, 40 dp, and some room between the badges; the rows share the height.
    // A Samsung on Android 17 gives a widget of two cells 202 dp, which takes three rows
    private static final int ROW_DP = 50;
    // A screen opened from the widget is alone in the app's task, so back closes the app rather than showing the tabs
    private static final int OPEN_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
    // Made anew each time: an updated one would keep the launch flags it was first made with
    private static final int PENDING_FLAGS = PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    // Draws from the saved lists only: what waits here is never behind a request
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        redraw(context, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        redraw(context, goAsync());
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            WidgetConfig.delete(context, widgetId);
        }
    }

    /** Redraws the widgets at once from the saved lists, then has the lists loaded anew in the background; from any thread */
    public static void loadAndUpdate(Context context) {
        redraw(context, null);
    }

    /**
     * The widgets drawn from the saved lists, then their loading queued as background work. A broadcast is held only for
     * the drawing: held over a request, one hanging for its timeouts, it would outlast the system's wait for it, and the
     * system would tell that the app isn't responding
     */
    private static void redraw(Context context, PendingResult pendingResult) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                // Each period once, however many widgets show it
                Set<Integer> drawn = new HashSet<>();
                for (int widgetId : widgetIds(appContext)) {
                    int days = new WidgetConfig(appContext, widgetId).getQuakeDays();
                    if (drawn.add(days)) {
                        List<Earthquake> saved = saved(appContext, days);
                        if (saved != null) {
                            updateAll(appContext, days, saved);
                        }
                    }
                }
                BackgroundTaskWorker.enqueueEarthquakeWidgets(appContext);
            } finally {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        });
    }

    /** Loads the lists of the widgets' periods, the saved ones when they are fresh, and redraws the widgets. Blocks */
    public static void loadAndUpdateNow(Context context) {
        Set<Integer> loaded = new HashSet<>();
        for (int widgetId : widgetIds(context)) {
            int days = new WidgetConfig(context, widgetId).getQuakeDays();
            if (loaded.add(days)) {
                updateAll(context, days, load(context, days));
            }
        }
    }

    // The list saved last, younger than a day; null for none or one that can't be read
    private static List<Earthquake> saved(Context context, int days) {
        try {
            return new UsgsEarthquakes(context.getCacheDir()).saved(days);
        } catch (Exception e) {
            Log.w(TAG, "Can't read the saved earthquakes", e);
            return null;
        }
    }

    /** The earthquakes of the last days, null if there are none saved and they can't be loaded. Blocks. */
    public static List<Earthquake> load(Context context, int days) {
        try {
            return new UsgsEarthquakes(context.getCacheDir()).load(days, false).earthquakes;
        } catch (Exception e) {
            Log.w(TAG, "Can't load the earthquakes", e);
            return null;
        }
    }

    /** Redraws the widgets of the period with its list of the last days; null for none to show */
    public static void updateAll(Context context, int days, List<Earthquake> earthquakes) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int widgetId : widgetIds(context)) {
            WidgetConfig preferences = new WidgetConfig(context, widgetId);
            if (preferences.getQuakeDays() != days) {
                continue;
            }
            manager.updateAppWidget(widgetId, build(context, widgetId, earthquakes, rowsFor(context, manager.getAppWidgetOptions(widgetId)),
                    preferences.getGlassColor(), preferences.getContentColor(), location(context, preferences), days,
                    preferences.isQuakeStrongest()));
        }
    }

    /**
     * Where the distances are from: the current location, or the chosen one, as the app keeps it when it does (a favorite
     * keeps its new name) and as the widget saved it otherwise; null if it isn't known
     */
    public static Place location(Context context, WidgetConfig preferences) {
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context);
        }
        AppSettings settings = AppSettings.getInstance();
        int locationId = preferences.getPlaceId();
        Place location = locationId == -1 || settings.hasCachedLocation(locationId) ? settings.getPlace(locationId) : null;
        if (location == null && locationId != -1 && preferences.getLocationName() != null) {
            location = new Place();
            location.id = locationId;
            location.name = preferences.getLocationName();
            location.latitude = preferences.getLocationLatitude();
            location.longitude = preferences.getLocationLongitude();
            location.timeZoneId = preferences.getLocationTimeZone();
        }
        return location == null || (location.latitude == 0 && location.longitude == 0) ? null : location;
    }

    private static int[] widgetIds(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, EarthquakeWidget.class));
    }

    // In portrait the widget is as tall as the largest height given
    private static int rowsFor(Context context, Bundle options) {
        int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        return heightDp > 0 ? rowsFor(heightDp) : 4;
    }

    /** How many rows fit a widget this tall */
    public static int rowsFor(int heightDp) {
        return Math.max(1, Math.min(MAX_ROWS, (heightDp - PADDING_DP - HEADER_DP) / ROW_DP));
    }

    /**
     * The widget's views with the list of the last days, the strongest first or the latest, and the distances from the
     * location, none if it is null; widgetId 0 for a preview that opens nothing
     */
    public static RemoteViews build(Context context, int widgetId, List<Earthquake> earthquakes, int rows, int backgroundColor, int fontColor,
                                    Place location, int days, boolean strongest) {
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context);
        }
        AppSettings settings = AppSettings.getInstance();
        EarthquakeFormatter formatter = new EarthquakeFormatter(context, settings.use24Hours, settings.distanceUnit);
        boolean isLight = Color.red(backgroundColor) == 255;
        int secondaryColor = AppUtils.withAlpha(fontColor, 0.6f);
        int accentColor = ContextCompat.getColor(context, isLight ? R.color.earthquake_widget_accent_light : R.color.earthquake_widget_accent_dark);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_earthquakes);
        views.setImageViewResource(R.id.imvBackground, isLight ? R.drawable.bg_widget_light : R.drawable.bg_widget_dark);
        views.setInt(R.id.imvBackground, "setAlpha", Color.alpha(backgroundColor));
        // A red arrow up rather than the seismogram when the strongest come first
        views.setImageViewResource(R.id.imvQuakeIcon, strongest ? R.drawable.ic_quake_strongest : R.drawable.ic_stat_earthquake);
        views.setInt(R.id.imvQuakeIcon, "setColorFilter", strongest ? ContextCompat.getColor(context,
                isLight ? R.color.earthquake_widget_strongest_light : R.color.earthquake_widget_strongest_dark) : accentColor);
        views.setTextColor(R.id.tvQuakeTitle, accentColor);
        views.setTextColor(R.id.tvQuakeLocation, secondaryColor);
        views.setTextColor(R.id.tvQuakeEmpty, secondaryColor);
        String period = context.getString(EarthquakeFormatter.periodTitle(days));
        views.setTextViewText(R.id.tvQuakeTitle, earthquakes == null ? period
                : context.getString(R.string.earthquake_widget_title, period, earthquakes.size()));
        if (strongest && earthquakes != null) {
            // The strongest first, the later of the equal ones; the list from USGS is the latest first
            earthquakes = new ArrayList<>(earthquakes);
            earthquakes.sort(Earthquake.STRONGEST_FIRST);
        }

        String locationName = location == null ? null : location.displayName();
        views.setTextViewText(R.id.tvQuakeLocation, locationName == null ? "" : locationName);

        views.removeAllViews(R.id.llQuakeRows);
        if (earthquakes == null || earthquakes.isEmpty()) {
            views.setTextViewText(R.id.tvQuakeEmpty, context.getString(earthquakes == null ? R.string.earthquakes_error : R.string.earthquakes_empty));
            views.setViewVisibility(R.id.tvQuakeEmpty, View.VISIBLE);
            views.setViewVisibility(R.id.llQuakeRows, View.GONE);
        } else {
            views.setViewVisibility(R.id.tvQuakeEmpty, View.GONE);
            views.setViewVisibility(R.id.llQuakeRows, View.VISIBLE);
            long now = System.currentTimeMillis();
            int count = Math.min(rows, earthquakes.size());
            for (int i = 0; i < count; i++) {
                Earthquake earthquake = earthquakes.get(i);
                RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_earthquake_row);
                row.setInt(R.id.imvRowBadge, "setColorFilter", EarthquakeFormatter.magnitudeColor(earthquake.magnitude));
                row.setTextViewText(R.id.tvRowMagnitude, formatter.magnitude(earthquake.magnitude));
                row.setTextColor(R.id.tvRowMagnitude, EarthquakeFormatter.magnitudeTextColor(earthquake.magnitude));
                row.setTextViewText(R.id.tvRowPlace, earthquake.placeName());
                row.setTextColor(R.id.tvRowPlace, fontColor);
                // The distance to the location, or the time on the phone's clock without one
                row.setTextViewText(R.id.tvRowSubtitle, location == null ? formatter.clock(earthquake.time, now)
                        : formatter.distance(earthquake.distanceKm(location.latitude, location.longitude)));
                row.setTextColor(R.id.tvRowSubtitle, secondaryColor);
                // As in the list: the last day's in a filled pill, older ones outlined and gray
                boolean isRecent = EarthquakeFormatter.isRecent(earthquake.time, now);
                row.setTextViewText(R.id.tvRowAgo, formatter.ago(earthquake.time, now));
                row.setTextColor(R.id.tvRowAgo, isRecent ? fontColor : secondaryColor);
                row.setInt(R.id.tvRowAgo, "setBackgroundResource", isRecent
                        ? (isLight ? R.drawable.bg_widget_pill_light : R.drawable.bg_widget_pill_dark)
                        : (isLight ? R.drawable.bg_widget_pill_old_light : R.drawable.bg_widget_pill_old_dark));
                if (widgetId != 0) {
                    Intent open = EarthquakeActivity.newIntent(context, earthquake, location == null ? 0 : location.latitude,
                            location == null ? 0 : location.longitude, locationName).addFlags(OPEN_FLAGS);
                    row.setOnClickPendingIntent(R.id.llQuakeRow, PendingIntent.getActivity(context, widgetId * (MAX_ROWS + 1) + i + 1, open,
                            PENDING_FLAGS));
                }
                views.addView(R.id.llQuakeRows, row);
            }
            // A short list keeps the rows as tall as a full one would have them
            for (int i = count; i < rows; i++) {
                views.addView(R.id.llQuakeRows, new RemoteViews(context.getPackageName(), R.layout.widget_earthquake_row_space));
            }
        }
        if (widgetId != 0) {
            Intent openList = EarthquakesActivity.newIntent(context, location, days).addFlags(OPEN_FLAGS);
            views.setOnClickPendingIntent(R.id.flQuakeWidget, PendingIntent.getActivity(context, widgetId * (MAX_ROWS + 1), openList,
                    PENDING_FLAGS));
        }
        return views;
    }
}
