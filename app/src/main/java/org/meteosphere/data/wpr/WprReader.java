package org.meteosphere.data.wpr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.meteosphere.data.http.RequestStats;
import org.meteosphere.data.model.Place;

/** Reads the answers of the wpr server */
public final class WprReader {

    private WprReader() {
    }

    /** {"places": [...]} of places and forecast, the places in the saved format; ones without an id are left out */
    public static ArrayList<Place> places(String json) throws JSONException {
        JSONArray array = new JSONObject(json).getJSONArray("places");
        ArrayList<Place> res = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Place place = Place.fromJson(array.getJSONObject(i));
            if (place != null) {
                res.add(place);
            }
        }
        return res;
    }

    /**
     * {"first": F, "today": T, "sources": {"name": [counts from F to T]}}, the requests a day the server sent. An answer
     * that can't be right fails as one that can't be read: the chart and the table index the counts by day
     */
    public static RequestStats.Counts stats(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        long first = root.getLong("first");
        long today = root.getLong("today");
        if (today < first) {
            throw new JSONException("Today " + today + " is before the first day " + first);
        }
        JSONObject sources = root.getJSONObject("sources");
        Map<String, int[]> bySource = new TreeMap<>();
        for (Iterator<String> it = sources.keys(); it.hasNext(); ) {
            String source = it.next();
            JSONArray array = sources.getJSONArray(source);
            if (array.length() != today - first + 1) {
                throw new JSONException(array.length() + " days of " + source + " from " + first + " to " + today);
            }
            int[] counts = new int[array.length()];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = array.getInt(i);
            }
            bySource.put(source, counts);
        }
        return new RequestStats.Counts(first, today, bySource);
    }

    /** The message of an answer other than HTTP 200: the server's own, {"error": "text"}, when it gave one */
    public static String error(int status, String body) {
        if (body != null) {
            try {
                String text = new JSONObject(body).optString("error");
                if (!text.isEmpty()) {
                    return text;
                }
            } catch (JSONException e) {
                // A proxy's page rather than the server's answer
            }
        }
        return "HTTP " + status;
    }
}
