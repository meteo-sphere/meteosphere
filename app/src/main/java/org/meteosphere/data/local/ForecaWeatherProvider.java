package org.meteosphere.data.local;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import org.meteosphere.data.http.HttpUtils;
import org.meteosphere.data.model.Place;
import org.json.JSONException;

/** The weather from Foreca, the same as the wpr server gives with source=foreca. */
public final class ForecaWeatherProvider extends LocalWeatherProvider {

    private static final String TAG = "ForecaWeatherProvider";

    /** What Foreca says to a key: takes it, refuses it, answers something else, or can't be reached */
    public enum KeyCheck { ACCEPTED, REFUSED, BUSY, UNREACHABLE }

    private final ForecaClient client;

    /** Asks Foreca whether it takes the key, with one request past the caches; off the main thread */
    public static KeyCheck checkKey(String key, int timeoutMs) {
        // A header takes printable ASCII only, the connection throws on anything else: a Cyrillic letter, a no-break space
        if (!key.chars().allMatch(c -> c > ' ' && c < 0x7f)) {
            return KeyCheck.REFUSED;
        }
        try {
            ForecaClient.check(key, timeoutMs);
            return KeyCheck.ACCEPTED;
        } catch (HttpUtils.HttpStatusException e) {
            return e.statusCode == 401 || e.statusCode == 403 ? KeyCheck.REFUSED : KeyCheck.BUSY;
        } catch (IOException e) {
            return KeyCheck.UNREACHABLE;
        }
    }

    public ForecaWeatherProvider(Context context, String token) {
        super(context);
        this.client = new ForecaClient(token);
    }

    @Override
    Place loadForecast(GeoLocation geoLocation, int timeoutMs) throws IOException, JSONException, ParseException {
        String location = ForecaClient.location(geoLocation);
        ForecaForecast.Hour current = client.getCurrent(location, timeoutMs);
        List<ForecaForecast.Hour> hours = client.getHourly(location, timeoutMs);
        List<ForecaForecast.Day> days = client.getDaily(location, timeoutMs);
        kpIndex.refreshIfNeeded(timeoutMs);
        Place place = OpenMeteoMapper.fromGeoLocation(geoLocation);
        ForecaMapper.applyLocation(place, geoLocation, current, hours, days, kpIndex);
        // When the current weather came, the freshest of the place's three answers
        place.sourceTime = ForecaClient.currentLoadedAt(location);
        return place;
    }

    @Override
    void applyFacts(List<GeoLocation> geoLocations, List<Place> places, int timeoutMs) {
        kpIndex.refreshIfNeeded(timeoutMs);
        for (int i = 0; i < places.size(); i++) {
            try {
                ForecaForecast.Hour current = client.getCurrent(ForecaClient.location(geoLocations.get(i)), timeoutMs);
                ForecaMapper.applyFact(places.get(i), geoLocations.get(i), current, Collections.emptyList(), kpIndex);
            } catch (IOException | JSONException | ParseException | RuntimeException e) {
                Log.w(TAG, "No current weather for " + geoLocations.get(i).id, e);
            }
        }
    }
}
