package org.meteosphere.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.data.quake.EarthquakeAlerts;
import org.meteosphere.data.quake.UsgsEarthquakes;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.quakes.EarthquakesActivity;
import org.meteosphere.ui.widgets.EarthquakeWidget;
import org.meteosphere.ui.quakes.EarthquakeFormatter;

/**
 * Every 15 minutes looks at the USGS earthquakes of the last day and notifies about the strong ones anywhere
 * and the ones near the favorite and current locations, as the settings say. Each one is notified once.
 */
public class EarthquakeAlertWorker extends Worker {

    private static final String TAG = "EarthquakeAlertWorker";
    private static final String WORK_NAME = "earthquake_alerts";
    private static final String NOTIFICATION_CHANNEL_ID = "earthquakes";
    // Ids of the earthquakes already notified, or seen when the alerts were turned on
    private static final String PREF_NOTIFIED = "org.meteosphere.earthquake_alerts_notified";
    private static final int MAX_REMEMBERED = 200;

    public EarthquakeAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Magnitude of the earthquakes to notify about anywhere, 0 if off */
    public static double worldMagnitude(Context context) {
        return Double.parseDouble(AppSettings.getInstance().preferences.getString(context.getString(R.string.key_earthquake_alerts_world), "0"));
    }

    /** Distance to a location under which an M5+ earthquake is notified, 0 if off */
    public static double nearbyKm(Context context) {
        return Double.parseDouble(AppSettings.getInstance().preferences.getString(context.getString(R.string.key_earthquake_alerts_nearby), "0"));
    }

    public static boolean isEnabled(Context context) {
        return worldMagnitude(context) > 0 || nearbyKm(context) > 0;
    }

    /** Starts or stops the checks as the settings say */
    public static void schedule(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        if (!isEnabled(context)) {
            workManager.cancelUniqueWork(WORK_NAME);
            // Turned on again later, the alerts start from what happens then
            AppSettings.getInstance().preferences.edit().remove(PREF_NOTIFIED).apply();
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(EarthquakeAlertWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context);
        }
        double worldMagnitude = worldMagnitude(context);
        double nearbyKm = nearbyKm(context);
        if (worldMagnitude <= 0 && nearbyKm <= 0) {
            return Result.success();
        }
        List<Earthquake> earthquakes;
        try {
            earthquakes = new UsgsEarthquakes(context.getCacheDir()).load(1, false).earthquakes;
        } catch (Exception e) {
            Log.w(TAG, "Can't load the earthquakes", e);
            return Result.success();
        }
        // The day's widgets show the same list
        EarthquakeWidget.updateAll(context, 1, earthquakes);
        SharedPreferences preferences = AppSettings.getInstance().preferences;
        String saved = preferences.getString(PREF_NOTIFIED, null);
        LinkedHashSet<String> notified = new LinkedHashSet<>();
        if (saved != null && !saved.isEmpty()) {
            notified.addAll(Arrays.asList(saved.split(",")));
        }
        if (saved == null) {
            // The first check since the alerts were turned on: what already happened isn't news
            for (Earthquake earthquake : earthquakes) {
                notified.add(earthquake.id);
            }
        } else {
            List<EarthquakeAlerts.Alert> alerts = EarthquakeAlerts.select(earthquakes, notified, places(), worldMagnitude, nearbyKm);
            if (!alerts.isEmpty() && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                createNotificationChannel(context);
                AppSettings settings = AppSettings.getInstance();
                EarthquakeFormatter formatter = new EarthquakeFormatter(context, settings.use24Hours, settings.distanceUnit);
                for (EarthquakeAlerts.Alert alert : alerts) {
                    showNotification(context, formatter, alert);
                }
            }
            // Also when the notifications are blocked, so they don't all come at once when allowed
            for (EarthquakeAlerts.Alert alert : alerts) {
                notified.add(alert.earthquake.id);
            }
        }
        Iterator<String> oldest = notified.iterator();
        while (notified.size() > MAX_REMEMBERED) {
            oldest.next();
            oldest.remove();
        }
        preferences.edit().putString(PREF_NOTIFIED, String.join(",", notified)).apply();
        return Result.success();
    }

    // The favorite locations and the current one
    private static List<EarthquakeAlerts.Place> places() {
        AppSettings settings = AppSettings.getInstance();
        List<Place> locations = new ArrayList<>();
        for (Integer id : settings.getFavorites()) {
            locations.add(settings.getPlace(id));
        }
        locations.add(settings.getCurrentPlace());
        List<EarthquakeAlerts.Place> places = new ArrayList<>();
        for (Place location : locations) {
            if (location != null && (location.latitude != 0 || location.longitude != 0)) {
                places.add(new EarthquakeAlerts.Place(location.displayName(), location.latitude, location.longitude));
            }
        }
        return places;
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.earthquakes),
                    NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    // "Earthquake M 6,4" and the USGS place; a tap opens the list around the nearest location
    private static void showNotification(Context context, EarthquakeFormatter formatter, EarthquakeAlerts.Alert alert) {
        Earthquake earthquake = alert.earthquake;
        int id = earthquake.id.hashCode();
        String place = earthquake.placeName();
        Intent openList = alert.nearest == null ? EarthquakesActivity.newIntent(context, null)
                : EarthquakesActivity.newIntent(context, alert.nearest.latitude, alert.nearest.longitude, alert.nearest.name);
        openList.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_earthquake)
                .setColor(EarthquakeFormatter.magnitudeColor(earthquake.magnitude))
                .setContentTitle(context.getString(R.string.earthquake_alert_title, formatter.magnitude(earthquake.magnitude)))
                .setContentText(place)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(place))
                .setWhen(earthquake.time)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, id, openList, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (earthquake.url != null) {
            Intent openPage = new Intent(Intent.ACTION_VIEW, Uri.parse(earthquake.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            builder.addAction(0, context.getString(R.string.earthquake_alert_details),
                    PendingIntent.getActivity(context, id + 1, openPage, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        try {
            NotificationManagerCompat.from(context).notify(TAG, id, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to notify", e);
        }
    }
}
