package org.meteosphere.data.wpr;

import android.util.Log;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import org.json.JSONException;
import org.meteosphere.BuildConfig;
import org.meteosphere.data.WeatherApiException;
import org.meteosphere.data.WeatherProvider;
import org.meteosphere.data.WeatherProviders;
import org.meteosphere.data.http.HttpUtils;
import org.meteosphere.data.http.RequestStats;
import org.meteosphere.data.model.Place;

/**
 * The places and the weather from the wpr server at BuildConfig.WPR_URL, which makes them from the source chosen.
 * The language goes as the app has it; the server converts it for its sources.
 */
public final class RemoteWeatherProvider implements WeatherProvider {

    private static final String TAG = "RemoteWeatherProvider";

    // foreca, or null for Open-Meteo, which the server takes without the parameter
    private final String source;

    public RemoteWeatherProvider(String weatherSource) {
        this.source = WeatherProviders.FORECA.equals(weatherSource) ? WeatherProviders.FORECA : null;
    }

    @Override
    public ArrayList<Place> findLocations(String query, String lang, int timeoutMs) throws WeatherApiException {
        return places(request("places", timeoutMs, "q", query, "lang", lang, "source", this.source));
    }

    @Override
    public ArrayList<Place> getLocationsNear(double lat, double lng, int count, String lang, boolean withFacts, int timeoutMs) throws WeatherApiException {
        return places(request("places", timeoutMs, "lat", coordinate(lat), "lon", coordinate(lng), "count", String.valueOf(count),
                "lang", lang, "source", this.source, "now", withFacts ? "1" : null));
    }

    @Override
    public ArrayList<Place> getForecast(int locationId, String lang, int timeoutMs) throws WeatherApiException {
        return places(request("forecast", timeoutMs, "id", String.valueOf(locationId), "lang", lang, "source", this.source));
    }

    // The names don't depend on the source
    @Override
    public ArrayList<Place> getLocationsByIds(List<Integer> locationIds, String lang, int timeoutMs) throws WeatherApiException {
        StringJoiner ids = new StringJoiner(",");
        for (Integer id : locationIds) {
            ids.add(String.valueOf(id));
        }
        return places(request("places", timeoutMs, "ids", ids.toString(), "lang", lang));
    }

    /** The requests a day the server sent to its sources; off the main thread */
    public static RequestStats.Counts loadStats(int timeoutMs) throws WeatherApiException {
        String body = request("stats", timeoutMs);
        try {
            return WprReader.stats(body);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
            throw new WeatherApiException("No stats", e);
        }
    }

    private static String coordinate(double degrees) {
        return String.format(Locale.US, "%.4f", degrees);
    }

    private static ArrayList<Place> places(String body) throws WeatherApiException {
        try {
            return WprReader.places(body);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
            throw new WeatherApiException("Can't read the response", e);
        }
    }

    // A GET of the path with the parameters given as names and values, the ones with a null value left out. Fails the
    // way the local providers do, or with the server's own message for an answer other than HTTP 200
    private static String request(String path, int timeoutMs, String... params) throws WeatherApiException {
        try {
            StringJoiner query = new StringJoiner("&", "?", "");
            query.setEmptyValue("");
            for (int i = 0; i < params.length; i += 2) {
                if (params[i + 1] != null) {
                    query.add(params[i] + "=" + URLEncoder.encode(params[i + 1], "UTF-8"));
                }
            }
            String base = BuildConfig.WPR_URL.endsWith("/") ? BuildConfig.WPR_URL : BuildConfig.WPR_URL + "/";
            return HttpUtils.getUncounted(base + path + query, timeoutMs);
        } catch (HttpUtils.HttpStatusException e) {
            throw new WeatherApiException(WprReader.error(e.statusCode, e.body), e);
        } catch (SocketTimeoutException e) {
            throw new WeatherApiException("Timeout", e);
        } catch (UnknownHostException e) {
            throw new WeatherApiException("Unknown host", e);
        } catch (IOException e) {
            Log.w(TAG, e.getMessage(), e);
            throw new WeatherApiException("No connection", e);
        }
    }
}
