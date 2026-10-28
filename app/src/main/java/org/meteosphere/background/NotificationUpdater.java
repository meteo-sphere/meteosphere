package org.meteosphere.background;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.notification.WeatherNotification;

/**
 * Keeps the weather notification of the status bar up to date while it is on: posts it anew from the weather the app
 * keeps whenever what it shows may have changed, asks for its place's weather when that is due, and sets the alarm that
 * draws it anew at half past each hour; takes it away when it is off. While the app sleeps Android freezes it, so the
 * notification is drawn after every load, by the alarm and when the app is opened, as the weather widgets are. Also the
 * receiver of that alarm
 */
public final class NotificationUpdater extends BroadcastReceiver {

    private static final String TAG = "NotificationUpdater";
    private static final String ACTION_REDRAW = "org.meteosphere.action.REDRAW_NOTIFICATION";
    // Without a tag, as an older build posted it: that one is replaced, not doubled
    private static final int NOTIFICATION_ID = 1;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HALF_HOUR_MS = 30 * MINUTE_MS;
    // As WidgetUpdater's: Android 12 lets an inexact alarm be up to 10 minutes late
    private static final long REDRAW_WINDOW_MS = 10 * MINUTE_MS;
    // A margin past the hour and the half hour, so that the drawing surely reads the new one
    private static final long REDRAW_DELAY_MS = 30 * 1000L;
    // A start of the app draws the notification some ten times in a second while the weather loads, and each would
    // queue a request of its own
    private static final long ASK_AGAIN_MS = MINUTE_MS;

    // One drawing at a time, in the order asked, so that the one posted last shows the weather kept last; off the main
    // thread, as the place's forecasts are read
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    // A drawing waits on the executor: one asked for meanwhile is drawn by it, as it reads the weather when it starts
    private static final AtomicBoolean queued = new AtomicBoolean();
    // The drawing thread's: the place whose weather was last asked for, and when
    private static int askedId;
    private static long askedAt;

    /** Draws the notification anew, or takes it away when it is off or the app may not notify; from any thread */
    public static void update(Context context) {
        update(context, null);
    }

    // The alarm's broadcast is held till its drawing is done
    private static void update(Context context, PendingResult pendingResult) {
        Context appContext = context.getApplicationContext();
        if (queued.compareAndSet(false, true)) {
            executor.execute(() -> {
                queued.set(false);
                try {
                    draw(appContext);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Can't draw the notification", e);
                }
            });
        }
        if (pendingResult != null) {
            executor.execute(pendingResult::finish);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_REDRAW.equals(intent.getAction())) {
            return;
        }
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context.getApplicationContext());
        }
        update(context, goAsync());
    }

    private static void draw(Context context) {
        AppSettings settings = AppSettings.getInstance();
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!settings.isStatusBarNotificationOn() || !manager.areNotificationsEnabled()) {
            manager.cancel(NOTIFICATION_ID);
            alarmManager(context).cancel(redrawIntent(context));
            return;
        }
        int locationId = settings.getStatusBarNotificationPlaceId();
        // The current location's for -1
        Place place = settings.getPlace(locationId);
        // From the start: one drawn before a half hour shows the hour before
        long start = System.currentTimeMillis();
        try {
            manager.notify(NOTIFICATION_ID, WeatherNotification.build(context, place, locationId));
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to notify", e);
        }
        settings.saveNotificationDrawnTime(start);
        scheduleRedraw(context, settings, locationId);
        askWeather(context, locationId, place, start);
    }

    // --- The redraw at half past an hour ---

    // The weather now becomes the next hour's forecast at half past an hour, and the forecast's first column moves on:
    // drawn anew then by the place's clock, also when no load comes, as when the phone is offline or Android defers the
    // periodic refresh. Replaces the alarm set, never past a half hour still to be drawn
    private static void scheduleRedraw(Context context, AppSettings settings, int locationId) {
        TimeZone zone = settings.getLocationTimeZone(locationId);
        long time = redrawTime(settings.getNotificationDrawnTime(), System.currentTimeMillis(), zone != null ? zone : TimeZone.getDefault());
        // RTC wakes nothing: due while the screen is off, it comes when the screen turns on
        alarmManager(context).setWindow(AlarmManager.RTC, time, REDRAW_WINDOW_MS, redrawIntent(context));
    }

    /**
     * When the alarm draws the notification anew: the first half past an hour by the zone's clock, plus half a minute,
     * after it was last drawn, as one drawn in the half minute after may show the hour before by the app's clock; now
     * when that is past. A last drawing ahead of now, the clock set back, counts as now; 0, none, as long ago
     */
    static long redrawTime(long drawn, long now, TimeZone zone) {
        long since = Math.min(drawn, now);
        return Math.max(now, WidgetUpdater.nextTimeInHour(since - REDRAW_DELAY_MS, zone, HALF_HOUR_MS) + REDRAW_DELAY_MS);
    }

    // The same each time, so that setting the alarm again replaces it
    private static PendingIntent redrawIntent(Context context) {
        Intent intent = new Intent(context, NotificationUpdater.class).setAction(ACTION_REDRAW);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static AlarmManager alarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    // --- The place's weather ---

    // Loaded when the app keeps none or it is due; the load draws the notification again
    private static void askWeather(Context context, int locationId, Place place, long now) {
        if (!asksWeather(place, now, UpdatePeriod.ms(context), locationId, askedId, askedAt)) {
            return;
        }
        askedId = locationId;
        askedAt = now;
        if (locationId == -1) {
            WeatherUpdater.findCurrentLocation(context);
        } else {
            WeatherUpdater.loadLocationWeather(context, locationId);
        }
    }

    /**
     * Whether the place's weather is asked for: the app keeps none or it is due, unless the same place's was asked for
     * at askedAt less than a minute ago. The clock set back before askedAt asks again
     */
    static boolean asksWeather(Place place, long now, long periodMs, int locationId, int askedId, long askedAt) {
        if (place != null && !UpdatePeriod.isDue(place.loadedAt, now, periodMs)) {
            return false;
        }
        return locationId != askedId || now < askedAt || now - askedAt >= ASK_AGAIN_MS;
    }
}
