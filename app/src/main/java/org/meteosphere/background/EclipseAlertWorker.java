package org.meteosphere.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.meteosphere.data.model.Place;
import org.meteosphere.astro.Eclipse;
import org.meteosphere.astro.QuietHours;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.astro.AstroFormatter;
import org.meteosphere.astro.EclipseCatalog;

/**
 * Announces the eclipses of the NASA catalog, all of them or the ones seen at the location of the Moon and Sun tabs:
 * "Annular solar eclipse, today at 16:20, seen here, 48% covered" at 6:00, or at 20:00 the evening before one at night.
 * Each run announces what is due and plans itself for the next announcement, like MoonPhaseAlertWorker.
 */
public class EclipseAlertWorker extends Worker {

    private static final String TAG = "EclipseAlertWorker";
    private static final String WORK_NAME = "eclipse_alerts";
    private static final String NOTIFICATION_CHANNEL_ID = "eclipses";
    private static final int NOTIFICATION_ID = 1;
    // Greatest eclipse of the last one announced, so none is announced twice
    private static final String PREF_LAST_ECLIPSE = "org.meteosphere.eclipse_alerts_last";
    private static final int LOOK_AHEAD = 10;

    public static final String MODE_OFF = "off";
    public static final String MODE_ALL = "all";
    public static final String MODE_VISIBLE = "visible";

    // An eclipse to announce with how it looks from the place, null without one
    private static final class Upcoming {
        final Eclipse eclipse;
        final Eclipse.Local local;
        // UTC instant the announcement speaks of: the local greatest if seen, the greatest otherwise
        final long time;

        Upcoming(Eclipse eclipse, Eclipse.Local local) {
            this.eclipse = eclipse;
            this.local = local;
            this.time = local != null && local.visible ? local.max : eclipse.greatest;
        }
    }

    public EclipseAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static String mode(Context context) {
        return AppSettings.getInstance().preferences.getString(context.getString(R.string.key_eclipse_alerts), MODE_OFF);
    }

    public static boolean isEnabled(Context context) {
        return !MODE_OFF.equals(mode(context));
    }

    /** Plans the next announcement, again from the start when the settings changed, otherwise only if none is planned */
    public static void schedule(Context context, boolean settingsChanged) {
        if (!isEnabled(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
            return;
        }
        enqueue(context, 0, settingsChanged ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP);
    }

    private static void enqueue(Context context, long delayMs, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(EclipseAlertWorker.class)
                .setInitialDelay(Math.max(0, delayMs), TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context);
        }
        String mode = mode(context);
        if (MODE_OFF.equals(mode)) {
            return Result.success();
        }
        Place location = AppSettings.getInstance().getAstroLocation();
        List<Eclipse> catalog = EclipseCatalog.get(context);
        TimeZone timeZone = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        SharedPreferences preferences = AppSettings.getInstance().preferences;
        Upcoming next = next(catalog, location, mode, now, preferences.getLong(PREF_LAST_ECLIPSE, 0));
        if (next == null) {
            return Result.success();
        }
        long notifyTime = QuietHours.notifyTime(next.time, timeZone);
        if (notifyTime <= now + 60000) {
            if (QuietHours.isNight(now, timeZone)) {
                // Run too late, past 23:00: nothing at night, the morning decides again
                enqueue(context, QuietHours.nextMorning(now, timeZone) - now, ExistingWorkPolicy.APPEND_OR_REPLACE);
                return Result.success();
            }
            showNotification(context, next, timeZone);
            preferences.edit().putLong(PREF_LAST_ECLIPSE, next.eclipse.greatest).apply();
            next = next(catalog, location, mode, now, next.eclipse.greatest);
            if (next == null) {
                return Result.success();
            }
            notifyTime = QuietHours.notifyTime(next.time, timeZone);
        }
        enqueue(context, notifyTime - now, ExistingWorkPolicy.APPEND_OR_REPLACE);
        return Result.success();
    }

    // The first eclipse after the last announced one still to come, seen at the place unless all are wanted
    private static Upcoming next(List<Eclipse> catalog, Place location, String mode, long now, long lastAnnounced) {
        for (Eclipse eclipse : Eclipse.upcoming(catalog, now, LOOK_AHEAD)) {
            if (eclipse.greatest <= lastAnnounced) {
                continue;
            }
            Eclipse.Local local = location != null ? eclipse.local(location.latitude, location.longitude) : null;
            if (MODE_VISIBLE.equals(mode) && (local == null || !local.visible)) {
                continue;
            }
            Upcoming upcoming = new Upcoming(eclipse, local);
            // A past one isn't news
            if (upcoming.time > now) {
                return upcoming;
            }
        }
        return null;
    }

    // "Annular solar eclipse" and "Today at 16:20, seen here, 48% covered"
    private static void showNotification(Context context, Upcoming upcoming, TimeZone timeZone) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.pref_eclipse_alerts),
                    NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        boolean is24HourFormat = AppSettings.getInstance().use24Hours;
        AstroFormatter formatter = new AstroFormatter(context, is24HourFormat);
        SimpleDateFormat timeFormat = new SimpleDateFormat(is24HourFormat ? "HH:mm" : "h:mm a", is24HourFormat ? Locale.getDefault() : Locale.US);
        timeFormat.setTimeZone(timeZone);
        Eclipse eclipse = upcoming.eclipse;
        Eclipse.Local local = upcoming.local;
        StringBuilder text = new StringBuilder(context.getString(QuietHours.isNight(upcoming.time, timeZone) ? R.string.moon_alert_tonight : R.string.moon_alert_today,
                timeFormat.format(new Date(upcoming.time))));
        if (local != null) {
            text.append(context.getString(R.string.list_separator));
            if (!local.visible) {
                text.append(context.getString(R.string.eclipse_alert_not_visible));
            } else if (eclipse.solar) {
                text.append(context.getString(R.string.eclipse_alert_visible_covered, formatter.illumination(local.obscuration)));
            } else {
                text.append(context.getString(R.string.eclipse_alert_visible));
            }
        }
        Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(eclipse.solar ? R.drawable.ic_stat_eclipse_solar : R.drawable.ic_stat_eclipse_lunar)
                .setLargeIcon(picture(context, eclipse))
                .setColor(0xff2b3038)
                .setContentTitle(formatter.eclipseName(eclipse))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true);
        if (openApp != null) {
            builder.setContentIntent(PendingIntent.getActivity(context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        try {
            NotificationManagerCompat.from(context).notify(TAG, NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to notify", e);
        }
    }

    private static Bitmap picture(Context context, Eclipse eclipse) {
        int size = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(context, AstroFormatter.eclipseIcon(eclipse));
        drawable.setBounds(0, 0, size, size);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
}
