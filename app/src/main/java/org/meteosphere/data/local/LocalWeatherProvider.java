package org.meteosphere.data.local;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.WeatherApiException;
import org.meteosphere.data.WeatherProvider;
import org.json.JSONException;

/**
 * What the wpr server does, right on the device: locations from Open-Meteo geocoding, nearby cities
 * from the GeoNames dump and the geomagnetic activity from NOAA.
 * The subclasses get the weather from their sources.
 */
public abstract class LocalWeatherProvider implements WeatherProvider {

    private static final String TAG = "LocalWeatherProvider";

    private static final int SEARCH_COUNT = 10;
    private static final int MAX_CITIES_COUNT = 50;

    // Shared by the providers, so switching between them doesn't load the cities again
    private static NearestCities sNearestCities;
    private static KpIndex sKpIndex;

    private final NearestCities nearestCities;
    final KpIndex kpIndex;

    LocalWeatherProvider(Context context) {
        synchronized (LocalWeatherProvider.class) {
            if (sNearestCities == null) {
                sNearestCities = new NearestCities(context.getNoBackupFilesDir());
                sKpIndex = new KpIndex();
                DbCache.init(context);
            }
            this.nearestCities = sNearestCities;
            this.kpIndex = sKpIndex;
        }
    }

    interface Request<T> {
        T execute() throws IOException, JSONException, ParseException;
    }

    // Fails the way the remote server's client does
    static <T> T execute(Request<T> request) throws WeatherApiException {
        try {
            return request.execute();
        } catch (SocketTimeoutException e) {
            throw new WeatherApiException("Timeout", e);
        } catch (UnknownHostException e) {
            throw new WeatherApiException("Unknown host", e);
        } catch (IOException e) {
            Log.w(TAG, e.getMessage(), e);
            throw new WeatherApiException("No connection", e);
        } catch (JSONException | ParseException | RuntimeException e) {
            Log.e(TAG, e.getMessage(), e);
            throw new WeatherApiException("Can't read the response", e);
        }
    }

    // The location with its current weather and forecast
    abstract Place loadForecast(GeoLocation geoLocation, int timeoutMs) throws IOException, JSONException, ParseException;

    // Sets the current weather of the locations, they stay without it on errors
    abstract void applyFacts(List<GeoLocation> geoLocations, List<Place> places, int timeoutMs);

    private static int clampCount(int count) {
        return Math.max(1, Math.min(MAX_CITIES_COUNT, count));
    }

    @Override
    public ArrayList<Place> findLocations(String query, String lang, int timeoutMs) throws WeatherApiException {
        String normalizedLang = Geocoder.normalizeLanguage(lang);
        // The search results don't show the weather
        return execute(() -> toLocations(Geocoder.search(query, SEARCH_COUNT, normalizedLang, timeoutMs), false, timeoutMs));
    }

    @Override
    public ArrayList<Place> getLocationsNear(double lat, double lng, int count, String lang, boolean withFacts, int timeoutMs) throws WeatherApiException {
        String normalizedLang = Geocoder.normalizeLanguage(lang);
        return execute(() -> toLocations(findNearest(lat, lng, clampCount(count), normalizedLang, timeoutMs), withFacts, timeoutMs));
    }

    @Override
    public ArrayList<Place> getForecast(int locationId, String lang, int timeoutMs) throws WeatherApiException {
        String normalizedLang = Geocoder.normalizeLanguage(lang);
        GeoLocation geoLocation = execute(() -> Geocoder.getById(locationId, normalizedLang, timeoutMs));
        if (geoLocation == null) {
            throw new WeatherApiException("No location " + locationId);
        }
        return execute(() -> {
            ArrayList<Place> res = new ArrayList<>();
            res.add(loadForecast(geoLocation, timeoutMs));
            return res;
        });
    }

    @Override
    public ArrayList<Place> getLocationsByIds(List<Integer> locationIds, String lang, int timeoutMs) throws WeatherApiException {
        String normalizedLang = Geocoder.normalizeLanguage(lang);
        return execute(() -> toLocations(new ArrayList<>(Geocoder.getByIds(locationIds, normalizedLang, timeoutMs).values()), false, timeoutMs));
    }

    private List<GeoLocation> findNearest(double lat, double lng, int count, String lang, int timeoutMs) throws IOException {
        List<NearestCities.Nearest> nearestCitiesList = nearestCities.find(lat, lng, count);
        if (nearestCitiesList == null) {
            throw new IOException("The cities dump is not loaded");
        }
        List<Integer> ids = new ArrayList<>();
        for (NearestCities.Nearest nearest : nearestCitiesList) {
            ids.add(nearest.id);
        }
        Map<Integer, GeoLocation> geoLocationsById = Geocoder.getByIds(ids, lang, timeoutMs);
        List<GeoLocation> res = new ArrayList<>();
        for (NearestCities.Nearest nearest : nearestCitiesList) {
            GeoLocation geoLocation = geoLocationsById.get(nearest.id);
            if (geoLocation != null) {
                geoLocation.distance = nearest.distance;
                res.add(geoLocation);
            }
        }
        return res;
    }

    private ArrayList<Place> toLocations(List<GeoLocation> geoLocations, boolean withFacts, int timeoutMs) {
        ArrayList<Place> places = new ArrayList<>();
        for (GeoLocation geoLocation : geoLocations) {
            places.add(OpenMeteoMapper.fromGeoLocation(geoLocation));
        }
        if (withFacts && !places.isEmpty()) {
            applyFacts(geoLocations, places, timeoutMs);
        }
        return places;
    }
}
