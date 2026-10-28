package org.meteosphere.background;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.app.NotificationManagerCompat;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.notification.DailyForecastNotification;

/**
 * Shows the forecast for today at 7:10 and for tomorrow at 20:10 by the device's clock, for the place chosen in the
 * settings. A system alarm wakes the phone for it also when it sleeps; not an exact one, which would need the user's
 * permission, so the system may bring it some minutes late. Each alarm shows the forecast of the time it was set for
 * and sets the next; a new clock time or time zone sets it anew.
 */
public class DailyForecastReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyForecastReceiver";
    private static final String ACTION_SHOW = "org.meteosphere.action.SHOW_DAILY_FORECAST";
    // The time the alarm is set for
    private static final String EXTRA_TIME = "time";
    private static final int NOTIFICATION_ID = 1;
    private static final int[] MINUTES_OF_DAY = {7 * 60 + 10, 20 * 60 + 10};
    // The evening's one is about tomorrow
    private static final int TOMORROW_FROM_HOUR = 14;
    // The forecast loaded at most this long, then shown as kept: a sleeping phone lets the app on the network for some
    // ten seconds after the alarm
    private static final long LOAD_WAIT_MS = 9000;

    /** Sets the next notification's alarm, or none when the setting is off */
    public static void schedule(Context context) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        long time = nextTime(System.currentTimeMillis());
        Intent intent = new Intent(context, DailyForecastReceiver.class).setAction(ACTION_SHOW)
                .putExtra(EXTRA_TIME, time);
        PendingIntent show = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (AppSettings.getInstance().getDailyForecastLocationId() == 0) {
            alarmManager.cancel(show);
            return;
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, show);
    }

    // The first of the notifications' times after now, today's or tomorrow morning's
    private static long nextTime(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        for (int minutes : MINUTES_OF_DAY) {
            calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
            calendar.set(Calendar.MINUTE, minutes % 60);
            if (calendar.getTimeInMillis() > now) {
                return calendar.getTimeInMillis();
            }
        }
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, MINUTES_OF_DAY[0] / 60);
        calendar.set(Calendar.MINUTE, MINUTES_OF_DAY[0] % 60);
        return calendar.getTimeInMillis();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_SHOW.equals(intent.getAction())) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (AppSettings.getInstance() == null) {
            AppSettings.init(appContext);
        }
        long time = intent.getLongExtra(EXTRA_TIME, 0);
        schedule(appContext);
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                show(appContext, time);
            } catch (RuntimeException e) {
                Log.w(TAG, "Daily forecast failed", e);
            } finally {
                result.finish();
            }
        }, "DailyForecast").start();
    }

    // The place's forecast loaded if it is old, as the app loads it, then shown: the morning's for that day, the
    // evening's for the next, also when the alarm comes late; told as today's when that day has come, none when it's
    // gone
    private static void show(Context context, long time) {
        AppSettings settings = AppSettings.getInstance();
        int locationId = settings.getDailyForecastLocationId();
        // The current location turned off keeps its last place, which may be far from here by now
        if (locationId == 0 || locationId == -1 && !settings.currentShown
                || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return;
        }
        Intent load = new Intent(locationId == -1
                ? WeatherUpdater.ACTION_FIND_CURRENT : WeatherUpdater.ACTION_LOAD_PLACE);
        load.putExtra(WeatherUpdater.EXTRA_PLACE_ID, locationId);
        CountDownLatch loaded = new CountDownLatch(1);
        new Thread(() -> {
            try {
                WeatherUpdater.run(context, load);
            } catch (RuntimeException e) {
                Log.w(TAG, "Loading the forecast failed", e);
            } finally {
                loaded.countDown();
            }
        }, "DailyForecastLoad").start();
        try {
            loaded.await(LOAD_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(time);
        if (date.get(Calendar.HOUR_OF_DAY) >= TOMORROW_FROM_HOUR) {
            date.add(Calendar.DAY_OF_MONTH, 1);
        }
        startOfDay(date);
        Calendar today = Calendar.getInstance();
        startOfDay(today);
        if (date.before(today)) {
            return;
        }
        boolean tomorrow = date.after(today);
        Place place = settings.getPlace(locationId);
        Notification notification = place != null
                ? DailyForecastNotification.build(context, place, locationId, date, tomorrow) : null;
        if (notification == null) {
            return;
        }
        DailyForecastNotification.createChannel(context);
        try {
            NotificationManagerCompat.from(context).notify(TAG, NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to notify", e);
        }
    }

    private static void startOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
