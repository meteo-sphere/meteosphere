package org.meteosphere.ui.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.background.BackgroundTaskWorker;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.background.WidgetUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.widgets.settings.WidgetConfig;

/**
 * The weather widgets' provider. Each size of widget has a class of its own, as the launcher keeps a widget by its
 * provider's class name; they all draw through WeatherWidgets by the kind in their settings, and are kept up to date
 * by WidgetUpdater
 */
public abstract class WeatherWidgetProvider extends AppWidgetProvider {

    // As WeatherUpdater's broadcasts have it
    private static final String EXTRA_PLACE_ID = "place_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdater.refreshAll(context, false);
    }

    @Override
    public void onEnabled(Context context) {
        BackgroundTaskWorker.schedulePeriodicRefresh(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        WidgetUpdater.redraw(context, appWidgetId);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        AppSettings settings = AppSettings.getInstance();
        for (int widgetId : appWidgetIds) {
            int locationId = new WidgetConfig(context, widgetId).getPlaceId();
            settings.removeWidgetId(widgetId);
            // Out of the app's list first: the place is in use by the widgets still there only
            if (locationId != -1 && !settings.isPlaceUsed(locationId)) {
                settings.removePlace(locationId);
            }
            WidgetConfig.delete(context, widgetId);
        }
        // By the places of the widgets left, or none
        WidgetUpdater.scheduleHourRedraw(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (WeatherUpdater.ACTION_PLACE_LOADED.equals(action)) {
            int locationId = intent.getIntExtra(EXTRA_PLACE_ID, -1);
            Place current = AppSettings.getInstance().getCurrentPlace();
            boolean currentIsIt = current != null && current.id == locationId;
            for (int widgetId : widgetIds(context)) {
                int shown = new WidgetConfig(context, widgetId).getPlaceId();
                if (shown != -1 ? shown == locationId : currentIsIt) {
                    WidgetUpdater.redraw(context, widgetId);
                }
            }
        } else if (WeatherUpdater.ACTION_CURRENT_CHANGED.equals(action)) {
            for (int widgetId : widgetIds(context)) {
                if (new WidgetConfig(context, widgetId).getPlaceId() == -1) {
                    WidgetUpdater.redraw(context, widgetId);
                }
            }
        }
    }

    // This class' widgets that the app has in its list
    private List<Integer> widgetIds(Context context) {
        List<Integer> ids = AppSettings.getInstance().getWidgets();
        List<Integer> own = new ArrayList<>();
        for (int widgetId : AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, getClass()))) {
            if (ids.contains(widgetId)) {
                own.add(widgetId);
            }
        }
        return own;
    }

    /** Refreshes, forced, every weather widget at the current location: the device's location was just found */
    public static void refreshCurrentLocationWidgets(Context context) {
        for (int widgetId : AppSettings.getInstance().getWidgets()) {
            if (new WidgetConfig(context, widgetId).getPlaceId() == -1) {
                WidgetUpdater.refresh(context, widgetId, true);
            }
        }
    }
}
