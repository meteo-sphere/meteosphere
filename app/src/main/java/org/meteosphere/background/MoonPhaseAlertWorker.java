package org.meteosphere.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.meteosphere.astro.Moon;
import org.meteosphere.astro.MoonPhaseAlerts;
import org.meteosphere.astro.QuietHours;
import org.meteosphere.ui.astro.MoonPhaseDrawable;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;

/**
 * Announces the moon phases chosen in the settings: "Full moon, today at 14:12" at 6:00, or "tonight at 2:12" at 20:00
 * the evening before, never at night. Each run announces what is due and plans itself for the next announcement.
 */
public class MoonPhaseAlertWorker extends Worker {

    private static final String TAG = "MoonPhaseAlertWorker";
    private static final String WORK_NAME = "moon_phase_alerts";
    private static final String NOTIFICATION_CHANNEL_ID = "moon_phases";
    private static final int NOTIFICATION_ID = 1;
    // Night sky behind the moon picture, so its lit part shows in the light theme too
    private static final int SKY_COLOR = 0xff2b3038;
    // UTC instant of the last phase announced, so none is announced twice
    private static final String PREF_LAST_PHASE = "org.meteosphere.moon_phase_alerts_last";

    public MoonPhaseAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** The principal phases to announce, none if off */
    public static List<Integer> phases(Context context) {
        switch (AppSettings.getInstance().preferences.getString(context.getString(R.string.key_moon_phase_alerts), "off")) {
            case "full":
                return Collections.singletonList(Moon.FULL_MOON);
            case "new_full":
                return Arrays.asList(Moon.NEW_MOON, Moon.FULL_MOON);
            case "all":
                return Arrays.asList(Moon.NEW_MOON, Moon.FIRST_QUARTER, Moon.FULL_MOON, Moon.LAST_QUARTER);
            default:
                return Collections.emptyList();
        }
    }

    public static boolean isEnabled(Context context) {
        return !phases(context).isEmpty();
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
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MoonPhaseAlertWorker.class)
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
        List<Integer> phases = phases(context);
        if (phases.isEmpty()) {
            return Result.success();
        }
        TimeZone timeZone = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        SharedPreferences preferences = AppSettings.getInstance().preferences;
        // Only the phases to come, a past one isn't news
        Moon.Phase phase = MoonPhaseAlerts.nextPhase(Math.max(now, preferences.getLong(PREF_LAST_PHASE, 0) + 60000), phases);
        long notifyTime = QuietHours.notifyTime(phase.time, timeZone);
        if (notifyTime <= now + 60000) {
            if (QuietHours.isNight(now, timeZone)) {
                // Run too late, past 23:00: nothing at night, the morning decides again
                enqueue(context, QuietHours.nextMorning(now, timeZone) - now, ExistingWorkPolicy.APPEND_OR_REPLACE);
                return Result.success();
            }
            showNotification(context, phase, timeZone);
            preferences.edit().putLong(PREF_LAST_PHASE, phase.time).apply();
            phase = MoonPhaseAlerts.nextPhase(phase.time + 60000, phases);
            notifyTime = QuietHours.notifyTime(phase.time, timeZone);
        }
        enqueue(context, notifyTime - now, ExistingWorkPolicy.APPEND_OR_REPLACE);
        return Result.success();
    }

    // The phase in the status bar too
    private static int smallIcon(int phase) {
        switch (phase) {
            case Moon.NEW_MOON:
                return R.drawable.ic_stat_moon_new;
            case Moon.FIRST_QUARTER:
                return R.drawable.ic_stat_moon_first_quarter;
            case Moon.LAST_QUARTER:
                return R.drawable.ic_stat_moon_last_quarter;
            default:
                return R.drawable.ic_stat_moon_full;
        }
    }

    // The moon of the phase as on the details screen, on a dark disk to show on light notifications too
    private static Bitmap moonPicture(Context context, int phase) {
        int size = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(SKY_COLOR);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, background);
        // The phases are eighths of the synodic month
        new MoonPhaseDrawable.MoonPainter().draw(canvas, size / 2f, size / 2f, size * 0.38f, phase / 8.0, false);
        return bitmap;
    }

    // "Full moon" and "Today at 14:12" or "Tonight at 2:12"
    private static void showNotification(Context context, Moon.Phase phase, TimeZone timeZone) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.pref_moon_phase_alerts),
                    NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        boolean is24HourFormat = AppSettings.getInstance().use24Hours;
        SimpleDateFormat timeFormat = new SimpleDateFormat(is24HourFormat ? "HH:mm" : "h:mm a", is24HourFormat ? Locale.getDefault() : Locale.US);
        timeFormat.setTimeZone(timeZone);
        String when = context.getString(QuietHours.isNight(phase.time, timeZone) ? R.string.moon_alert_tonight : R.string.moon_alert_today,
                timeFormat.format(new Date(phase.time)));
        Intent openApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(smallIcon(phase.phase))
                .setLargeIcon(moonPicture(context, phase.phase))
                .setColor(SKY_COLOR)
                .setContentTitle(context.getResources().getStringArray(R.array.moon_phases)[phase.phase])
                .setContentText(when)
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
}
