package org.meteosphere.data.local;

import org.meteosphere.data.http.HttpUtils;
import android.util.Log;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// https://open-meteo.com/en/docs/geocoding-api
final class Geocoder {

    private static final String TAG = "Geocoder";

    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1";
    private static final String DEFAULT_LANGUAGE = "ru";
    private static final int MAX_COUNT = 100;
    // A place hardly ever changes; a renamed one is asked for again after this
    private static final long PLACE_TTL_MS = 365L * 24 * 60 * 60 * 1000;
    // The same words are looked for again and again while typing; the places found are kept as places too
    private static final long SEARCH_TTL_MS = 24L * 60 * 60 * 1000;
    // The ids Open-Meteo doesn't know, so a list that has one doesn't ask for it each time
    private static final long UNKNOWN_TTL_MS = 24L * 60 * 60 * 1000;
    private static final ThreadPoolExecutor LOOKUP_EXECUTOR = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    static {
        LOOKUP_EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private Geocoder() {
    }

    // The remote server gets "ua" for Ukrainian, accept it here as well. Open-Meteo keeps the Chinese names under zh only,
    // Traditional Chinese gets them too.
    static String normalizeLanguage(String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return DEFAULT_LANGUAGE;
        }
        String res = lang.trim().toLowerCase(Locale.ROOT);
        return res.equals("ua") ? "uk" : res.equals("zh-hant") ? "zh" : res;
    }

    private static String placeKey(int id, String lang) {
        return "place|" + id + "|" + lang;
    }

    private static String unknownKey(int id, String lang) {
        return "unknown|" + id + "|" + lang;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    static List<GeoLocation> search(String name, int count, String lang, int timeoutMs) throws IOException, JSONException {
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.length() < 2) {
            // Open-Meteo returns nothing for less than 2 characters
            return new ArrayList<>();
        }
        int clampedCount = Math.max(1, Math.min(MAX_COUNT, count));
        return parseSearchResults(DbCache.get("search|" + clampedCount + "|" + lang + "|" + trimmedName.toLowerCase(Locale.ROOT),
                SEARCH_TTL_MS, () -> searchAgain(trimmedName, clampedCount, lang, timeoutMs)));
    }

    // The response, with its places kept as places too
    private static String searchAgain(String name, int count, String lang, int timeoutMs) throws IOException, JSONException {
        String url = GEOCODING_URL + "/search?name=" + encode(name) + "&count=" + count + "&language=" + encode(lang) + "&format=json";
        String response = HttpUtils.get(url, timeoutMs);
        JSONArray results = new JSONObject(response).optJSONArray("results");
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                GeoLocation location = GeoLocation.parseJson(result);
                if (location != null && DbCache.get(placeKey(location.id, lang)) == null) {
                    DbCache.put(placeKey(location.id, lang), result.toString(), PLACE_TTL_MS);
                }
            }
        }
        return response;
    }

    static List<GeoLocation> parseSearchResults(String json) throws JSONException {
        List<GeoLocation> res = new ArrayList<>();
        JSONArray results = new JSONObject(json).optJSONArray("results");
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                GeoLocation location = GeoLocation.parseJson(results.getJSONObject(i));
                if (location != null) {
                    res.add(location);
                }
            }
        }
        return res;
    }

    // Returns null if the location is unknown. Each place is asked for once a year in a language.
    static GeoLocation getById(int id, String lang, int timeoutMs) throws IOException, JSONException {
        if (DbCache.get(unknownKey(id, lang)) != null) {
            return null;
        }
        String json = DbCache.get(placeKey(id, lang), PLACE_TTL_MS, () -> getAgain(id, lang, timeoutMs));
        return json != null ? GeoLocation.parseJson(new JSONObject(json)) : null;
    }

    private static String getAgain(int id, String lang, int timeoutMs) throws IOException {
        String url = GEOCODING_URL + "/get?id=" + id + "&language=" + encode(lang);
        try {
            return HttpUtils.get(url, timeoutMs);
        } catch (HttpUtils.HttpStatusException e) {
            // 400 {"reason":"Location ID not found.","error":true}
            if (e.statusCode == 400 || e.statusCode == 404) {
                DbCache.put(unknownKey(id, lang), "", UNKNOWN_TTL_MS);
                return null;
            }
            throw e;
        }
    }

    // Returns the found locations keyed by id, unknown ids and failed lookups are skipped
    static Map<Integer, GeoLocation> getByIds(Collection<Integer> ids, String lang, int timeoutMs) {
        Map<Integer, Future<GeoLocation>> futures = new LinkedHashMap<>();
        for (Integer id : ids) {
            futures.put(id, LOOKUP_EXECUTOR.submit(() -> getById(id, lang, timeoutMs)));
        }
        Map<Integer, GeoLocation> res = new LinkedHashMap<>();
        for (Map.Entry<Integer, Future<GeoLocation>> entry : futures.entrySet()) {
            try {
                GeoLocation location = entry.getValue().get();
                if (location != null) {
                    res.put(entry.getKey(), location);
                }
            } catch (ExecutionException e) {
                Log.w(TAG, "Lookup of " + entry.getKey() + " failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return res;
    }
}
