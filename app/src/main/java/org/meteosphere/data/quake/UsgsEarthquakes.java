package org.meteosphere.data.quake;

import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.data.http.HttpUtils;
import org.meteosphere.data.http.ResponseCache;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Earthquakes of magnitude 5 and above from the USGS catalog (earthquake.usgs.gov/fdsnws/event/1), newest first.
 * Free and without a key; the app asks it directly, not through wpr, since the list is the same for everyone.
 */
public final class UsgsEarthquakes {

    private static final String TAG = "UsgsEarthquakes";

    public static final double MIN_MAGNITUDE = 5;

    private static final String QUERY_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query"
            + "?format=geojson&eventtype=earthquake&orderby=time&minmagnitude=5&starttime=";
    private static final int TIMEOUT_MS = 20000;
    // USGS adds an event within minutes, so a newer response isn't requested again
    public static final long FRESH_MS = 5 * 60 * 1000L;
    // Without a connection the last response is shown while it is younger than a day
    private static final long SAVED_MS = 24 * 60 * 60 * 1000L;

    /** The list and whether it is the saved one because the request failed */
    public static final class Result {
        public final List<Earthquake> earthquakes;
        public final boolean isSaved;

        Result(List<Earthquake> earthquakes, boolean isSaved) {
            this.earthquakes = earthquakes;
            this.isSaved = isSaved;
        }
    }

    private final ResponseCache cache;

    public UsgsEarthquakes(File cacheDir) {
        this.cache = new ResponseCache(new File(cacheDir, "earthquakes"));
    }

    /** The earthquakes of the last days; refresh skips a response of the last minutes. Blocks, call off the main thread. */
    public Result load(int days, boolean refresh) throws IOException, JSONException {
        String key = key(days);
        if (!refresh) {
            String fresh = this.cache.get(key, FRESH_MS);
            if (fresh != null) {
                return new Result(parse(fresh), false);
            }
        }
        String response;
        List<Earthquake> earthquakes;
        try {
            response = HttpUtils.get(QUERY_URL + startTime(days), TIMEOUT_MS);
            // Parsed before it is saved, so a broken response isn't kept
            earthquakes = parse(response);
        } catch (IOException | JSONException e) {
            // No answer, or one that isn't the list, as a Wi-Fi's sign-in page: the list saved before
            String saved = this.cache.get(key, SAVED_MS);
            if (saved == null) {
                throw e;
            }
            Log.w(TAG, "Showing the saved list: " + e);
            return new Result(parse(saved), true);
        }
        try {
            this.cache.put(key, response);
        } catch (IOException e) {
            Log.w(TAG, "Can't save the list: " + e);
        }
        return new Result(earthquakes, false);
    }

    /** The list saved last if it is younger than a day, null if there is none: read from the file, USGS not asked */
    public List<Earthquake> saved(int days) throws IOException, JSONException {
        String saved = this.cache.get(key(days), SAVED_MS);
        return saved == null ? null : parse(saved);
    }

    private static String key(int days) {
        return "usgs_m5_" + days;
    }

    private static String startTime(int days) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L));
    }

    // The GeoJSON of the FDSN query: features with properties (mag, place, time, url, magType, tsunami, felt, alert)
    // and Point geometry (lng, lat, depth)
    static List<Earthquake> parse(String geoJson) throws JSONException {
        JSONArray features = new JSONObject(geoJson).getJSONArray("features");
        List<Earthquake> earthquakes = new ArrayList<>(features.length());
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject properties = feature.getJSONObject("properties");
            JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");
            double magnitude = properties.optDouble("mag", Double.NaN);
            if (Double.isNaN(magnitude) || magnitude < MIN_MAGNITUDE) {
                continue;
            }
            earthquakes.add(new Earthquake(feature.getString("id"), magnitude, string(properties, "place", ""), properties.getLong("time"),
                    coordinates.getDouble(1), coordinates.getDouble(0), coordinates.optDouble(2, 0), string(properties, "url", null),
                    string(properties, "magType", null), properties.optInt("tsunami", 0) == 1, properties.optInt("felt", 0),
                    string(properties, "alert", null)));
        }
        return earthquakes;
    }

    // optString turns a JSON null into "null"
    private static String string(JSONObject object, String name, String fallback) {
        return object.isNull(name) ? fallback : object.optString(name, fallback);
    }
}
