package org.meteosphere.background;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.widgets.EarthquakeWidget;

/**
 * Runs the app's background handlers ({@link WeatherUpdater}, {@link WidgetUpdater}) and the earthquake widgets' loading
 * on WorkManager.
 * Since API 26 an app in the background can't start plain services, while these handlers are
 * triggered from widgets, receivers and other background contexts.
 */
public class BackgroundTaskWorker extends Worker {

    private static final String TAG = "BackgroundTaskWorker";

    static final String TARGET_WEATHER = "weather";
    static final String TARGET_WIDGET = "widget";
    private static final String TARGET_PERIODIC_REFRESH = "periodic_refresh";
    private static final String TARGET_EARTHQUAKE_WIDGETS = "earthquake_widgets";

    private static final String QUEUE_WEATHER = "org.meteosphere.work.weather";
    private static final String QUEUE_WIDGET = "org.meteosphere.work.widget";
    private static final String QUEUE_EARTHQUAKE_WIDGETS = "org.meteosphere.work.earthquake_widgets";
    private static final String PERIODIC_REFRESH = "org.meteosphere.work.periodic_refresh";

    private static final String KEY_TARGET = "target";
    private static final String KEY_ACTION = "action";
    private static final String PREFIX_INT = "int:";
    private static final String PREFIX_LONG = "long:";
    private static final String PREFIX_BOOLEAN = "bool:";
    private static final String PREFIX_STRING = "str:";
    private static final String PREFIX_LOCATION = "loc:";
    private static final String PREFIX_LOCATION_PROVIDER = "locp:";

    public BackgroundTaskWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Queues the intent for its handler; intents of one handler run one at a time, in order. */
    static void enqueue(Context context, String target, Intent intent) {
        String queue = TARGET_WEATHER.equals(target) ? QUEUE_WEATHER : QUEUE_WIDGET;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackgroundTaskWorker.class)
                .setInputData(toData(target, intent))
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(queue, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    /** Runs the intent after a delay, replacing a pending run with the same unique name. */
    static void enqueueDelayed(Context context, String target, Intent intent, String uniqueName, long delayMs) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackgroundTaskWorker.class)
                .setInputData(toData(target, intent))
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request);
    }

    /** Loads the earthquake widgets' lists anew and redraws the widgets; not queued again while a load waits or runs */
    public static void enqueueEarthquakeWidgets(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackgroundTaskWorker.class)
                .setInputData(new Data.Builder().putString(KEY_TARGET, TARGET_EARTHQUAKE_WIDGETS).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE_EARTHQUAKE_WIDGETS, ExistingWorkPolicy.KEEP, request);
    }

    /**
     * Schedules the periodic refresh of weather data, widgets and the status bar notification,
     * which replaces the always-running service of older Android versions.
     */
    public static void schedulePeriodicRefresh(Context context) {
        long periodMinutes = Math.max(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / 60000, AppSettings.getInstance().refreshMinutes);
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(BackgroundTaskWorker.class, periodMinutes, TimeUnit.MINUTES)
                .setInputData(new Data.Builder().putString(KEY_TARGET, TARGET_PERIODIC_REFRESH).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_REFRESH, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            run();
        } catch (RuntimeException e) {
            // A failed work would cancel every work queued after it, a new widget's first drawing among them
            Log.w(TAG, "Background task failed", e);
        }
        return Result.success();
    }

    private void run() {
        Context context = getApplicationContext();
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context);
        }
        String target = getInputData().getString(KEY_TARGET);
        if (TARGET_PERIODIC_REFRESH.equals(target)) {
            if (AppSettings.getInstance().getWidgets().isEmpty() && !AppSettings.getInstance().isStatusBarNotificationOn()) {
                return;
            }
            WeatherUpdater.run(context, new Intent(WeatherUpdater.ACTION_LOAD_ALL));
            WidgetUpdater.refreshAllNow(context, false);
            NotificationUpdater.update(context);
        } else if (TARGET_WEATHER.equals(target)) {
            WeatherUpdater.run(context, toIntent(getInputData()));
        } else if (TARGET_WIDGET.equals(target)) {
            WidgetUpdater.run(context, toIntent(getInputData()));
        } else if (TARGET_EARTHQUAKE_WIDGETS.equals(target)) {
            EarthquakeWidget.loadAndUpdateNow(context);
        }
    }

    @SuppressWarnings("deprecation")
    private static Data toData(String target, Intent intent) {
        Data.Builder builder = new Data.Builder()
                .putString(KEY_TARGET, target)
                .putString(KEY_ACTION, intent.getAction());
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                if (value instanceof Integer) {
                    builder.putInt(PREFIX_INT + key, (Integer) value);
                } else if (value instanceof Long) {
                    builder.putLong(PREFIX_LONG + key, (Long) value);
                } else if (value instanceof Boolean) {
                    builder.putBoolean(PREFIX_BOOLEAN + key, (Boolean) value);
                } else if (value instanceof String) {
                    builder.putString(PREFIX_STRING + key, (String) value);
                } else if (value instanceof Location) {
                    Location location = (Location) value;
                    builder.putDoubleArray(PREFIX_LOCATION + key, new double[]{
                            location.getLatitude(), location.getLongitude(), location.getTime(),
                            location.hasAccuracy() ? location.getAccuracy() : -1});
                    builder.putString(PREFIX_LOCATION_PROVIDER + key, location.getProvider());
                } else if (value != null) {
                    throw new IllegalArgumentException("Unsupported extra '" + key + "': " + value.getClass());
                }
            }
        }
        return builder.build();
    }

    private static Intent toIntent(Data data) {
        Intent intent = new Intent(data.getString(KEY_ACTION));
        for (String key : data.getKeyValueMap().keySet()) {
            if (key.startsWith(PREFIX_INT)) {
                intent.putExtra(key.substring(PREFIX_INT.length()), data.getInt(key, 0));
            } else if (key.startsWith(PREFIX_LONG)) {
                intent.putExtra(key.substring(PREFIX_LONG.length()), data.getLong(key, 0));
            } else if (key.startsWith(PREFIX_BOOLEAN)) {
                intent.putExtra(key.substring(PREFIX_BOOLEAN.length()), data.getBoolean(key, false));
            } else if (key.startsWith(PREFIX_STRING)) {
                intent.putExtra(key.substring(PREFIX_STRING.length()), data.getString(key));
            } else if (key.startsWith(PREFIX_LOCATION)) {
                String name = key.substring(PREFIX_LOCATION.length());
                double[] fields = data.getDoubleArray(key);
                Location location = new Location(data.getString(PREFIX_LOCATION_PROVIDER + name));
                location.setLatitude(fields[0]);
                location.setLongitude(fields[1]);
                location.setTime((long) fields[2]);
                if (fields[3] >= 0) {
                    location.setAccuracy((float) fields[3]);
                }
                intent.putExtra(name, location);
            }
        }
        return intent;
    }
}
