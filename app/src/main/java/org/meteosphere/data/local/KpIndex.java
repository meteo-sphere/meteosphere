package org.meteosphere.data.local;

import org.meteosphere.data.http.HttpUtils;
import android.util.Log;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Planetary K-index (geomagnetic activity) from NOAA SWPC, it is the same for every location:
// 3-hour values for about 3 days ahead, then the daily largest Kp from the 27-day outlook.
// The responses are kept in DbCache as wpr keeps them, so a new app process doesn't download them again.
final class KpIndex implements GeomagneticSource {

    private static final String TAG = "KpIndex";

    private static final String KP_FORECAST_URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json";
    private static final String OUTLOOK_URL = "https://services.swpc.noaa.gov/text/27-day-outlook.txt";
    private static final long REFRESH_INTERVAL_MS = 60 * 60 * 1000;
    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000;
    private static final long KP_INTERVAL_MS = 3 * 60 * 60 * 1000;

    // 2026 Sep 28      98           5          2
    private static final Pattern OUTLOOK_LINE = Pattern.compile("^(\\d{4} \\w{3} \\d{2})\\s+\\d+\\s+\\d+\\s+(\\d+)\\s*$");

    // Interval start in UTC millis -> Kp
    private volatile NavigableMap<Long, Double> kpByInterval = new TreeMap<>();
    // UTC day as yyyy-MM-dd -> largest Kp
    private volatile Map<String, Integer> kpMaxByDay = new HashMap<>();
    private long lastRefreshAttempt;
    private long lastSuccessfulRefresh;

    void setData(NavigableMap<Long, Double> kpByInterval, Map<String, Integer> kpMaxByDay) {
        this.kpByInterval = kpByInterval;
        this.kpMaxByDay = kpMaxByDay;
    }

    synchronized void refreshIfNeeded(int timeoutMs) {
        long now = System.currentTimeMillis();
        if (now - lastSuccessfulRefresh < REFRESH_INTERVAL_MS || now - lastRefreshAttempt < RETRY_INTERVAL_MS) {
            return;
        }
        lastRefreshAttempt = now;
        NavigableMap<Long, Double> newKpByInterval = null;
        Map<String, Integer> newKpMaxByDay = null;
        try {
            newKpByInterval = parseKpForecast(load(KP_FORECAST_URL, timeoutMs));
        } catch (IOException | JSONException | ParseException e) {
            Log.w(TAG, "Failed to load Kp forecast", e);
        }
        try {
            newKpMaxByDay = parseOutlook(load(OUTLOOK_URL, timeoutMs));
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Failed to load the 27-day outlook", e);
        }
        // Keep the previous data if a source is unavailable
        if (newKpByInterval != null) {
            kpByInterval = newKpByInterval;
        }
        if (newKpMaxByDay != null) {
            kpMaxByDay = newKpMaxByDay;
        }
        if (newKpByInterval != null && newKpMaxByDay != null) {
            lastSuccessfulRefresh = now;
        }
    }

    // The file, kept in DbCache for the refresh interval: a new process doesn't ask again, and a source that failed
    // is asked again without the other
    private static String load(String url, int timeoutMs) throws IOException, JSONException {
        return DbCache.get("noaa|" + url, REFRESH_INTERVAL_MS, () -> HttpUtils.get(url, timeoutMs));
    }

    // [{"time_tag":"2026-09-23T00:00:00","kp":0.33,"observed":"observed","noaa_scale":null}, ...]
    static NavigableMap<Long, Double> parseKpForecast(String json) throws JSONException, ParseException {
        SimpleDateFormat timeTagFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        timeTagFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        NavigableMap<Long, Double> res = new TreeMap<>();
        JSONArray items = new JSONArray(json);
        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.getJSONObject(i);
            Object kp = obj.opt("kp");
            if (kp instanceof Number) {
                res.put(timeTagFormat.parse(obj.getString("time_tag")).getTime(), ((Number) kp).doubleValue());
            }
        }
        return res;
    }

    static Map<String, Integer> parseOutlook(String text) {
        SimpleDateFormat outlookDateFormat = new SimpleDateFormat("yyyy MMM dd", Locale.ENGLISH);
        outlookDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat dayFormat = createUtcDayFormat();
        Map<String, Integer> res = new HashMap<>();
        for (String line : text.split("\n")) {
            Matcher matcher = OUTLOOK_LINE.matcher(line);
            if (matcher.matches()) {
                try {
                    res.put(dayFormat.format(outlookDateFormat.parse(matcher.group(1))), Integer.parseInt(matcher.group(2)));
                } catch (ParseException | NumberFormatException e) {
                    Log.w(TAG, "Failed to parse outlook line: " + line);
                }
            }
        }
        return res;
    }

    private static SimpleDateFormat createUtcDayFormat() {
        SimpleDateFormat res = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        res.setTimeZone(TimeZone.getTimeZone("UTC"));
        return res;
    }

    @Override
    public Integer getGrade(Date date) {
        long time = date.getTime();
        Map.Entry<Long, Double> entry = kpByInterval.floorEntry(time);
        if (entry != null && time < entry.getKey() + KP_INTERVAL_MS) {
            return (int) Math.round(entry.getValue());
        }
        return kpMaxByDay.get(createUtcDayFormat().format(date));
    }

    // Start of the local day, the date is yyyy-MM-dd
    private static long startOfDay(String date, int dayOffset, TimeZone timeZone) {
        Calendar cal = Calendar.getInstance(timeZone);
        cal.clear();
        cal.set(Integer.parseInt(date.substring(0, 4)), Integer.parseInt(date.substring(5, 7)) - 1,
                Integer.parseInt(date.substring(8, 10)) + dayOffset);
        return cal.getTimeInMillis();
    }

    @Override
    public Integer getDayMax(String date, TimeZone timeZone) {
        long dayStart = startOfDay(date, 0, timeZone);
        long dayEnd = startOfDay(date, 1, timeZone);
        NavigableMap<Long, Double> intervals = kpByInterval;
        Integer max = null;
        for (Double kp : intervals.subMap(dayStart - KP_INTERVAL_MS, false, dayEnd, false).values()) {
            int grade = (int) Math.round(kp);
            if (max == null || grade > max) {
                max = grade;
            }
        }
        boolean fullyCovered = max != null && intervals.lastKey() + KP_INTERVAL_MS >= dayEnd;
        Integer outlookMax = kpMaxByDay.get(date);
        if (!fullyCovered && outlookMax != null && (max == null || outlookMax > max)) {
            return outlookMax;
        }
        return max;
    }
}
