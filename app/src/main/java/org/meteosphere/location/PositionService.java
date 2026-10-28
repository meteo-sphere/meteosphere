package org.meteosphere.location;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import org.meteosphere.ui.widgets.WeatherWidgetProvider;
import org.meteosphere.util.AppUtils;

/**
 * Finds the device's position: the last one a provider knows when it is fresh and near enough, else the network's
 * next one, then the GPS's when the search may use it. Each step tells of what it found, or of nothing, by
 * {@link PositionReceiver#ACTION_POSITION_FOUND}; the service stops once the GPS has had its turn.
 * A new command ends the search before it
 */
public class PositionService extends Service {

    private static final String TAG = "PositionService";

    private static final String ACTION_SEARCH = "org.meteosphere.action.SEARCH_LOCATION";
    private static final String ACTION_STOP = "org.meteosphere.action.STOP_LOCATION_SEARCH";
    private static final String EXTRA_WITH_GPS = "with_gps";

    // A position this near to now is where the device still is
    private static final long FRESH_MS = 300 * 1000L;
    // Coarser than this, the nearest town may be another one
    private static final float USABLE_ACCURACY_M = 3000;
    private static final long NETWORK_WAIT_MS = 5 * 1000L;
    private static final long GPS_WAIT_MS = 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager manager;
    // The last command's, null once it ended
    private Search search;

    // One command's search: what it listens to now, and how long
    private static final class Search {
        final int startId;
        final boolean withGps;
        // Null while nothing is listened to
        String provider;
        LocationListener listener;
        Runnable timeout;

        Search(int startId, boolean withGps) {
            this.startId = startId;
            this.withGps = withGps;
        }
    }

    // Every method its own: before Android 11 the interface has no defaults for the provider's callbacks
    private final class Listener implements LocationListener {
        private final Search search;

        Listener(Search search) {
            this.search = search;
        }

        @Override
        public void onLocationChanged(@NonNull Location location) {
            heard(this.search, this, location);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }
    }

    /** Searches for the device's position, by the GPS too when withGps; nothing without the location permission */
    public static void start(Context context, boolean withGps) {
        if (AppUtils.mayLocate(context)) {
            AppUtils.startServiceSafely(context, new Intent(context, PositionService.class).setAction(ACTION_SEARCH)
                    .putExtra(EXTRA_WITH_GPS, withGps));
        }
    }

    /** Ends the search running, telling nothing; nothing without the location permission */
    public static void stop(Context context) {
        if (AppUtils.mayLocate(context)) {
            AppUtils.startServiceSafely(context, new Intent(context, PositionService.class).setAction(ACTION_STOP));
        }
    }

    /** The GPS's last known position when it is fresh, else the network's; null for none or without the permission */
    public static Location lastKnownPosition(Context context) {
        if (!AppUtils.mayLocate(context)) {
            return null;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location gps = lastKnown(manager, LocationManager.GPS_PROVIDER);
        return isFresh(gps) ? gps : lastKnown(manager, LocationManager.NETWORK_PROVIDER);
    }

    /** Whether the position is less than five minutes from now, either way as the clock may be set back */
    public static boolean isFresh(Location location) {
        return location != null && isFresh(location.getTime(), System.currentTimeMillis());
    }

    static boolean isFresh(long time, long now) {
        return Math.abs(now - time) < FRESH_MS;
    }

    // Whether a last known position does for the search: fresh and near enough
    private static boolean isUsable(Location location) {
        return location != null && isUsable(location.getTime(), location.hasAccuracy(), location.getAccuracy(), System.currentTimeMillis());
    }

    /** Whether a last known position does for the search: fresh, and of a known accuracy under 3 km */
    static boolean isUsable(long time, boolean hasAccuracy, float accuracyM, long now) {
        return isFresh(time, now) && hasAccuracy && accuracyM < USABLE_ACCURACY_M;
    }

    // The provider's last known position; null for none, and when the app may not have it or there is no such provider
    private static Location lastKnown(LocationManager manager, String provider) {
        try {
            return manager.getLastKnownLocation(provider);
        } catch (RuntimeException e) {
            Log.w(TAG, "Can't read the last position of " + provider, e);
            return null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        end();
        if (intent != null && ACTION_SEARCH.equals(intent.getAction())) {
            this.search = new Search(startId, intent.getBooleanExtra(EXTRA_WITH_GPS, false));
            begin(this.search);
        } else {
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        end();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // The GPS's position when it knows one already; else the network's, then the GPS's turn
    private void begin(Search search) {
        if (isEnabled(LocationManager.GPS_PROVIDER)) {
            Location gps = lastKnown(this.manager, LocationManager.GPS_PROVIDER);
            if (isUsable(gps)) {
                tell(LocationManager.GPS_PROVIDER, gps);
                finish(search);
                return;
            }
        }
        if (!isEnabled(LocationManager.NETWORK_PROVIDER)) {
            gpsStep(search);
            return;
        }
        Location network = lastKnown(this.manager, LocationManager.NETWORK_PROVIDER);
        if (isUsable(network)) {
            tell(LocationManager.NETWORK_PROVIDER, network);
            gpsStep(search);
        } else if (!listen(search, LocationManager.NETWORK_PROVIDER, NETWORK_WAIT_MS)) {
            gpsStep(search);
        }
    }

    // The search's last step: the GPS's last known position, or its next one when the search may use it
    private void gpsStep(Search search) {
        if (!isEnabled(LocationManager.GPS_PROVIDER)) {
            tell(LocationManager.GPS_PROVIDER, null);
            finish(search);
            return;
        }
        Location gps = lastKnown(this.manager, LocationManager.GPS_PROVIDER);
        if (isUsable(gps)) {
            tell(LocationManager.GPS_PROVIDER, gps);
            finish(search);
        } else if (!search.withGps || !listen(search, LocationManager.GPS_PROVIDER, GPS_WAIT_MS)) {
            tell(LocationManager.GPS_PROVIDER, null);
            finish(search);
        }
    }

    // Listens to the provider till a position comes or the wait ends; false when the app may not
    private boolean listen(Search search, String provider, long waitMs) {
        Listener listener = new Listener(search);
        try {
            this.manager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.w(TAG, "May not listen to " + provider, e);
            return false;
        }
        search.provider = provider;
        search.listener = listener;
        search.timeout = () -> heard(search, listener, null);
        this.handler.postDelayed(search.timeout, waitMs);
        return true;
    }

    // A position the listener heard, or none in its wait: told of, then the GPS's turn after the network's, or the end
    // after the GPS's. One for a search replaced since, or for a listener the search is done with, is dropped: a late
    // one of the network's would pass for the GPS's
    private void heard(Search search, LocationListener listener, Location location) {
        if (search != this.search || search.listener != listener) {
            return;
        }
        String provider = search.provider;
        stopListening(search);
        tell(provider, location);
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
            gpsStep(search);
        } else {
            finish(search);
        }
    }

    private void stopListening(Search search) {
        if (search.timeout != null) {
            this.handler.removeCallbacks(search.timeout);
        }
        if (search.listener != null) {
            this.manager.removeUpdates(search.listener);
        }
        search.provider = null;
        search.listener = null;
        search.timeout = null;
    }

    // Stops the service, unless a command came since
    private void finish(Search search) {
        stopListening(search);
        if (this.search == search) {
            this.search = null;
        }
        stopSelf(search.startId);
    }

    // The search running ended at once: a new command replaces it, or the service is done
    private void end() {
        if (this.search != null) {
            stopListening(this.search);
            this.search = null;
        }
    }

    private boolean isEnabled(String provider) {
        try {
            return this.manager.isProviderEnabled(provider);
        } catch (RuntimeException e) {
            Log.w(TAG, "Can't tell whether " + provider + " is on", e);
            return false;
        }
    }

    // The position found by the provider, null for none; the widgets at the current location refreshed for a new one
    private void tell(String provider, Location location) {
        Intent intent = new Intent(PositionReceiver.ACTION_POSITION_FOUND)
                .putExtra(PositionReceiver.EXTRA_PROVIDER, provider);
        if (location != null) {
            intent.putExtra(PositionReceiver.EXTRA_POSITION, location);
        }
        AppUtils.sendAppBroadcast(this, intent);
        if (location != null) {
            WeatherWidgetProvider.refreshCurrentLocationWidgets(this);
        }
    }
}
