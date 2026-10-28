package org.meteosphere.data.local;

import org.meteosphere.data.http.HttpUtils;
import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import org.meteosphere.data.model.Place;
import org.json.JSONException;

/** The weather from Open-Meteo, the same as the wpr server gives without the source parameter. */
public final class OpenMeteoWeatherProvider extends LocalWeatherProvider {

    private static final String TAG = "OpenMeteoWeatherProvider";

    private static final String OPENMETEO_URL = "https://api.open-meteo.com/v1";
    private static final String HOURLY_PARAMS = "temperature_2m,relativehumidity_2m,apparent_temperature,precipitation,weathercode,surface_pressure,windspeed_10m,winddirection_10m,windgusts_10m";
    private static final String DAILY_PARAMS = "weathercode,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,precipitation_sum,precipitation_hours,windspeed_10m_max,windgusts_10m_max,winddirection_10m_dominant";

    // The weather of a place, its forecast and its current weather alone, which a list asks for, as wpr keeps them:
    // the weather doesn't change faster, so one request per place and a quarter of an hour
    private static final long WEATHER_TTL_MS = 15 * 60 * 1000L;

    public OpenMeteoWeatherProvider(Context context) {
        super(context);
    }

    private static String forecastKey(int id) {
        return "forecast|" + id;
    }

    private static String factKey(int id) {
        return "fact|" + id;
    }

    @Override
    Place loadForecast(GeoLocation geoLocation, int timeoutMs) throws IOException, JSONException {
        String url = OPENMETEO_URL +
                String.format(Locale.US, "/forecast?latitude=%.4f&longitude=%.4f", geoLocation.latitude, geoLocation.longitude) +
                "&hourly=" + HOURLY_PARAMS +
                "&models=best_match" +
                "&daily=" + DAILY_PARAMS +
                "&current_weather=true&windspeed_unit=ms&timezone=auto";
        OMLocation omLocation = OMLocation.parseJson(DbCache.get(forecastKey(geoLocation.id), WEATHER_TTL_MS, () -> HttpUtils.get(url, timeoutMs)));
        kpIndex.refreshIfNeeded(timeoutMs);
        Place place = OpenMeteoMapper.fromGeoLocation(geoLocation);
        OpenMeteoMapper.applyLocation(place, omLocation, kpIndex);
        place.sourceTime = DbCache.loadedAt(forecastKey(geoLocation.id), WEATHER_TTL_MS);
        return place;
    }

    // The current weather of a list: of each place from its forecast or its current weather kept, and of the others
    // in one request, then kept for each of them
    @Override
    void applyFacts(List<GeoLocation> geoLocations, List<Place> places, int timeoutMs) {
        List<OMLocation> omLocations = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < geoLocations.size(); i++) {
            String json = DbCache.get(forecastKey(geoLocations.get(i).id));
            if (json == null) {
                json = DbCache.get(factKey(geoLocations.get(i).id));
            }
            OMLocation omLocation = null;
            if (json != null) {
                try {
                    omLocation = OMLocation.parseJson(json);
                } catch (JSONException e) {
                    json = null;
                }
            }
            if (json == null) {
                missing.add(i);
            }
            omLocations.add(omLocation);
        }
        if (!missing.isEmpty()) {
            StringJoiner latitudes = new StringJoiner(",");
            StringJoiner longitudes = new StringJoiner(",");
            for (int i : missing) {
                latitudes.add(String.format(Locale.US, "%.4f", geoLocations.get(i).latitude.floatValue()));
                longitudes.add(String.format(Locale.US, "%.4f", geoLocations.get(i).longitude.floatValue()));
            }
            String url = OPENMETEO_URL + "/forecast?latitude=" + latitudes + "&longitude=" + longitudes +
                    "&hourly=" + HOURLY_PARAMS +
                    "&models=best_match" +
                    "&daily=sunrise,sunset" +
                    "&current_weather=true&windspeed_unit=ms&timezone=auto&forecast_days=1";
            try {
                List<String> jsons = OMLocation.splitJsonList(HttpUtils.get(url, timeoutMs));
                if (jsons.size() != missing.size()) {
                    Log.e(TAG, "Unexpected Open-Meteo response for " + missing.size() + " locations");
                } else {
                    for (int k = 0; k < missing.size(); k++) {
                        int i = missing.get(k);
                        DbCache.put(factKey(geoLocations.get(i).id), jsons.get(k), WEATHER_TTL_MS);
                        omLocations.set(i, OMLocation.parseJson(jsons.get(k)));
                    }
                }
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Failed to load the current weather", e);
            }
        }
        kpIndex.refreshIfNeeded(timeoutMs);
        applyFacts(places, omLocations, kpIndex);
    }

    static void applyFacts(List<Place> places, List<OMLocation> omLocations, GeomagneticSource geomagnetic) {
        for (int i = 0; i < places.size(); i++) {
            if (omLocations.get(i) == null) {
                continue;
            }
            try {
                OpenMeteoMapper.applyFact(places.get(i), omLocations.get(i), geomagnetic);
            } catch (RuntimeException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }
}
