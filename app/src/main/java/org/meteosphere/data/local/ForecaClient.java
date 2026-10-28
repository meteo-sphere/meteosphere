package org.meteosphere.data.local;

import org.meteosphere.data.http.HttpUtils;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Locale;
import org.json.JSONException;

// Foreca Weather API: https://developer.foreca.com. The Freemium plan allows 2000 requests a day for all
// the devices and the wpr server, so the responses are cached as long as ForecaClient of wpr keeps them.
final class ForecaClient {

    private static final String API_URL = "https://weatherapi.foreca.net/api/v1";
    private static final int HOURLY_PERIODS = 168;
    private static final int DAILY_PERIODS = 7;

    private static final long CURRENT_TTL_MS = 15 * 60 * 1000L;
    private static final long HOURLY_TTL_MS = 60 * 60 * 1000L;
    private static final long DAILY_TTL_MS = 3 * 60 * 60 * 1000L;
    // Foreca answers 429 to more than about 10 requests a second
    private static final long MIN_REQUEST_INTERVAL_MS = 250;
    private static final long RETRY_DELAY_MS = 1000;
    // The app describes the weather by its WMO code, Foreca's own phrases aren't used: one language serves all
    // the app's languages with the same cached responses
    private static final String LANGUAGE = "en";
    // A short answer that tells whether Foreca takes a key: the description of a place, Helsinki
    private static final String CHECK_PATH = "/location/24.9384,60.1699?lang=" + LANGUAGE;

    private static long sLastRequestTime;

    private final String token;

    ForecaClient(String token) {
        this.token = token != null ? token : "";
    }

    // Foreca takes the longitude first
    static String location(GeoLocation geoLocation) {
        return String.format(Locale.US, "%.4f,%.4f", geoLocation.longitude, geoLocation.latitude);
    }

    private static String query(Integer periods) {
        return "?lang=" + LANGUAGE + "&dataset=full&windunit=MS&tempunit=C" + (periods != null ? "&periods=" + periods : "");
    }

    private static String key(String path) {
        return "foreca|" + path;
    }

    private static String currentPath(String location) {
        return "/current/" + location + query(null);
    }

    // Kept in DbCache as ForecaClient of wpr keeps them; simultaneous requests of a path wait for one response
    private String get(String path, long ttlMs, int timeoutMs) throws IOException, JSONException {
        return DbCache.get(key(path), ttlMs, () -> getAgain(path, timeoutMs));
    }

    private String getAgain(String path, int timeoutMs) throws IOException {
        if (token.isEmpty()) {
            throw new IOException("No Foreca key: give it in the settings or as foreca.token in local.properties");
        }
        String response;
        try {
            response = request(path, timeoutMs);
        } catch (HttpUtils.HttpStatusException e) {
            if (e.statusCode != 429) {
                throw e;
            }
            sleep(RETRY_DELAY_MS);
            response = request(path, timeoutMs);
        }
        return response;
    }

    private String request(String path, int timeoutMs) throws IOException {
        synchronized (ForecaClient.class) {
            sleep(sLastRequestTime + MIN_REQUEST_INTERVAL_MS - System.currentTimeMillis());
            sLastRequestTime = System.currentTimeMillis();
        }
        return HttpUtils.get(API_URL + path, "Authorization", "Bearer " + token, timeoutMs);
    }

    private static void sleep(long ms) throws InterruptedIOException {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for Foreca");
        }
    }

    // Asks Foreca once, past the cache, whether it takes the token: its refusal is an HttpStatusException, 401 or 403
    static void check(String token, int timeoutMs) throws IOException {
        new ForecaClient(token).request(CHECK_PATH, timeoutMs);
    }

    ForecaForecast.Hour getCurrent(String location, int timeoutMs) throws IOException, JSONException {
        return ForecaForecast.parseCurrent(get(currentPath(location), CURRENT_TTL_MS, timeoutMs));
    }

    // When the current weather getCurrent() gives came from Foreca, in ms; 0 if it isn't kept
    static long currentLoadedAt(String location) {
        return DbCache.loadedAt(key(currentPath(location)), CURRENT_TTL_MS);
    }

    List<ForecaForecast.Hour> getHourly(String location, int timeoutMs) throws IOException, JSONException {
        return ForecaForecast.parseHourly(get("/forecast/hourly/" + location + query(HOURLY_PERIODS), HOURLY_TTL_MS, timeoutMs));
    }

    List<ForecaForecast.Day> getDaily(String location, int timeoutMs) throws IOException, JSONException {
        return ForecaForecast.parseDaily(get("/forecast/daily/" + location + query(DAILY_PERIODS), DAILY_TTL_MS, timeoutMs));
    }
}
