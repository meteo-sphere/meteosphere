package org.meteosphere;

import android.app.Application;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import org.meteosphere.background.BackgroundTaskWorker;
import org.meteosphere.background.DailyForecastReceiver;
import org.meteosphere.background.EarthquakeAlertWorker;
import org.meteosphere.background.EclipseAlertWorker;
import org.meteosphere.background.MoonPhaseAlertWorker;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.background.WidgetUpdater;
import org.meteosphere.data.http.RequestStats;
import org.meteosphere.settings.AppLanguage;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.settings.ThemeMode;
import org.meteosphere.ui.notification.WeatherNotification;
import org.meteosphere.ui.sky.SkyBackgrounds;
import org.meteosphere.util.AppUtils;

/**
 * The app's start: the settings read, the work and the alarms set; the places named anew when the app's language
 * changed since they were last named, also while it runs
 */
public class MeteosphereApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppSettings.init(this);
        RequestStats.init(this);
        takeNewLanguage();
        ThemeMode.apply(this);
        WeatherNotification.createChannel(this);
        BackgroundTaskWorker.schedulePeriodicRefresh(this);
        EarthquakeAlertWorker.schedule(this);
        MoonPhaseAlertWorker.schedule(this, false);
        EclipseAlertWorker.schedule(this, false);
        DailyForecastReceiver.schedule(this);
        AppSettings settings = AppSettings.getInstance();
        settings.removeUnusedPlacesInBackground();
        // The place the device was at may be far by now, and without the permission it can't be found anew
        if (!AppUtils.mayLocate(this)) {
            settings.savePosition(null);
            settings.saveCurrentPlace(null);
        }
    }

    // The system's settings may give the app another language while it runs
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AppLanguage.apply(this);
        if (!AppLanguage.of(this).equals(AppSettings.getInstance().language)) {
            AppSettings.init(this);
        }
        takeNewLanguage();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        SkyBackgrounds.getInstance(this).trimMemory(level);
    }

    // Once, by the language the places were last named in: their names asked for in the new one and the widgets
    // drawn with them
    private void takeNewLanguage() {
        if (AppSettings.getInstance().takeNewLanguage()) {
            WeatherUpdater.loadNames(this);
            WidgetUpdater.refreshAll(this, true);
        }
    }
}
