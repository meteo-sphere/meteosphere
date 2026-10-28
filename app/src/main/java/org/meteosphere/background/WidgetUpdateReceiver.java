package org.meteosphere.background;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * A weather widget's refresh button, the alarm at the start of an hour, and the clock's changes the widgets showing the
 * time follow: the next alarm, a new time and a new time zone
 */
public class WidgetUpdateReceiver extends BroadcastReceiver {

    /** The refresh button's, with the widget's WidgetUpdater.EXTRA_WIDGET */
    public static final String ACTION_REFRESH_WIDGET = "org.meteosphere.action.REFRESH_WIDGET";
    /** WidgetUpdater's alarm at the start of an hour: the widgets drawn anew, nothing loaded */
    static final String ACTION_REDRAW_WIDGETS = "org.meteosphere.action.REDRAW_WIDGETS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH_WIDGET.equals(action)) {
            WidgetUpdater.refresh(context, intent.getIntExtra(WidgetUpdater.EXTRA_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID), false);
            return;
        }
        if (ACTION_REDRAW_WIDGETS.equals(action)) {
            WidgetUpdater.redrawAll(context);
            return;
        }
        WidgetUpdater.refreshAll(context, false);
        if (Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            // The daily forecast's alarm is set by the clock
            DailyForecastReceiver.schedule(context);
        }
    }
}
