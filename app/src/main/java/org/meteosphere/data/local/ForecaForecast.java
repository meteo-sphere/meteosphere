package org.meteosphere.data.local;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Foreca Weather API responses: https://developer.foreca.com
final class ForecaForecast {

    // The current weather or an hour of the forecast. The time is local with its offset, e.g. 2026-10-01T09:38+03:00
    static final class Hour {
        String time;
        // d200: day or night, cloudiness, precipitation rate and type, see ForecaMapper.getIcon()
        String symbol;
        Float temperature;
        Float feelsLikeTemp;
        Integer relHumidity;
        Float windSpeed;
        Integer windDir;
        // The rate in mm/h for the current weather, the amount in mm for an hour of the forecast
        Float precip;
        // At the sea level, hPa
        Float pressure;
    }

    static final class Day {
        String date;
        String symbol;
        Float maxTemp;
        Float minTemp;
        Integer maxRelHumidity;
        Integer minRelHumidity;
        Float maxWindSpeed;
        Integer windDir;
        Float maxWindGust;
        Float precipAccum;
        Float pressure;
        // Local time, 06:31:13, missing in the polar day and night
        String sunrise;
        String sunset;
    }

    private ForecaForecast() {
    }

    private static String parseString(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value instanceof String ? (String) value : null;
    }

    private static Float parseFloat(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value instanceof Number ? ((Number) value).floatValue() : null;
    }

    private static Integer parseInt(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static Hour parseHour(JSONObject obj, String precipKey) {
        Hour res = new Hour();
        res.time = parseString(obj, "time");
        res.symbol = parseString(obj, "symbol");
        res.temperature = parseFloat(obj, "temperature");
        res.feelsLikeTemp = parseFloat(obj, "feelsLikeTemp");
        res.relHumidity = parseInt(obj, "relHumidity");
        res.windSpeed = parseFloat(obj, "windSpeed");
        res.windDir = parseInt(obj, "windDir");
        res.precip = parseFloat(obj, precipKey);
        res.pressure = parseFloat(obj, "pressure");
        return res;
    }

    // {"current":{"time":"2026-10-01T09:38+03:00","symbol":"d200",...}}
    static Hour parseCurrent(String json) throws JSONException {
        return parseHour(new JSONObject(json).getJSONObject("current"), "precipRate");
    }

    // {"forecast":[{"time":"2026-10-01T09:00+03:00","symbol":"d200",...},...]}
    static List<Hour> parseHourly(String json) throws JSONException {
        JSONArray items = new JSONObject(json).getJSONArray("forecast");
        List<Hour> res = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            res.add(parseHour(items.getJSONObject(i), "precipAccum"));
        }
        return res;
    }

    // {"forecast":[{"date":"2026-10-01","symbol":"d300",...},...]}
    static List<Day> parseDaily(String json) throws JSONException {
        JSONArray items = new JSONObject(json).getJSONArray("forecast");
        List<Day> res = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.getJSONObject(i);
            Day day = new Day();
            day.date = parseString(obj, "date");
            day.symbol = parseString(obj, "symbol");
            day.maxTemp = parseFloat(obj, "maxTemp");
            day.minTemp = parseFloat(obj, "minTemp");
            day.maxRelHumidity = parseInt(obj, "maxRelHumidity");
            day.minRelHumidity = parseInt(obj, "minRelHumidity");
            day.maxWindSpeed = parseFloat(obj, "maxWindSpeed");
            day.windDir = parseInt(obj, "windDir");
            day.maxWindGust = parseFloat(obj, "maxWindGust");
            day.precipAccum = parseFloat(obj, "precipAccum");
            day.pressure = parseFloat(obj, "pressure");
            day.sunrise = parseString(obj, "sunrise");
            day.sunset = parseString(obj, "sunset");
            res.add(day);
        }
        return res;
    }
}
