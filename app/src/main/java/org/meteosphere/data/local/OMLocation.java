package org.meteosphere.data.local;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

// Open-Meteo forecast response: https://open-meteo.com/en/docs
final class OMLocation {

    Float latitude;
    Float longitude;
    Integer utcOffsetSeconds;
    String timezone;
    String timezoneAbbreviation;
    Integer elevation;

    String currentWeatherTime;
    Float currentTemperature;
    Float currentWindSpeed;
    Integer currentWindDirection;
    Integer currentWeatherCode;

    List<String> hourlyTimeList = new ArrayList<>();
    // Missing values are null
    Map<String, List<Number>> hourlyParamsMap = new HashMap<>();

    List<String> dailyTimeList = new ArrayList<>();
    // Missing values are null, sunrise and sunset are the local time as UTC seconds
    Map<String, List<Number>> dailyParamsMap = new HashMap<>();

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

    static OMLocation parseJson(String json) throws JSONException {
        return parseJson(new JSONObject(json));
    }

    // Open-Meteo returns an array for several coordinates and an object for a single one.
    // Returns a list in the order of the requested coordinates, with null for the broken items.
    static List<OMLocation> parseJsonList(String json) throws JSONException {
        Object obj = new JSONTokener(json).nextValue();
        List<OMLocation> res = new ArrayList<>();
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i = 0; i < array.length(); i++) {
                try {
                    res.add(parseJson(array.getJSONObject(i)));
                } catch (JSONException e) {
                    res.add(null);
                }
            }
        } else if (obj instanceof JSONObject) {
            res.add(parseJson((JSONObject) obj));
        } else {
            throw new JSONException("Unexpected Open-Meteo response");
        }
        return res;
    }

    // The JSON of each location of a response, as parseJsonList() reads them
    static List<String> splitJsonList(String json) throws JSONException {
        Object obj = new JSONTokener(json).nextValue();
        List<String> res = new ArrayList<>();
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            for (int i = 0; i < array.length(); i++) {
                res.add(array.getJSONObject(i).toString());
            }
        } else if (obj instanceof JSONObject) {
            res.add(obj.toString());
        } else {
            throw new JSONException("Unexpected Open-Meteo response");
        }
        return res;
    }

    private static List<Number> parseNumbers(JSONArray params) {
        List<Number> res = new ArrayList<>(params.length());
        for (int i = 0; i < params.length(); i++) {
            Object param = params.opt(i);
            // Doubles, like the JSON parser of the server gives for fractions
            res.add(param instanceof Number ? (Number) ((Number) param).doubleValue() : null);
        }
        return res;
    }

    private static OMLocation parseJson(JSONObject jsonObj) throws JSONException {
        OMLocation omLocation = new OMLocation();

        omLocation.latitude = parseFloat(jsonObj, "latitude");
        omLocation.longitude = parseFloat(jsonObj, "longitude");
        omLocation.utcOffsetSeconds = parseInt(jsonObj, "utc_offset_seconds");
        omLocation.timezone = parseString(jsonObj, "timezone");
        omLocation.timezoneAbbreviation = parseString(jsonObj, "timezone_abbreviation");
        omLocation.elevation = parseInt(jsonObj, "elevation");

        // The local time of sunrise and sunset is read as UTC
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        JSONObject currWeatherObj = jsonObj.getJSONObject("current_weather");
        omLocation.currentWeatherTime = parseString(currWeatherObj, "time");
        omLocation.currentTemperature = parseFloat(currWeatherObj, "temperature");
        omLocation.currentWindSpeed = parseFloat(currWeatherObj, "windspeed");
        omLocation.currentWindDirection = parseInt(currWeatherObj, "winddirection");
        omLocation.currentWeatherCode = parseInt(currWeatherObj, "weathercode");

        JSONObject hourlyObj = jsonObj.getJSONObject("hourly");
        for (Iterator<String> it = hourlyObj.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONArray params = hourlyObj.getJSONArray(name);
            if (name.equals("time")) {
                for (int i = 0; i < params.length(); i++) {
                    omLocation.hourlyTimeList.add(params.getString(i));
                }
            } else {
                omLocation.hourlyParamsMap.put(name, parseNumbers(params));
            }
        }

        JSONObject dailyObj = jsonObj.getJSONObject("daily");
        for (Iterator<String> it = dailyObj.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONArray params = dailyObj.getJSONArray(name);
            if (name.equals("time")) {
                for (int i = 0; i < params.length(); i++) {
                    omLocation.dailyTimeList.add(params.getString(i));
                }
            } else if (name.equals("sunrise") || name.equals("sunset")) {
                List<Number> paramsList = new ArrayList<>();
                for (int i = 0; i < params.length(); i++) {
                    long time;
                    try {
                        Date date = df.parse(params.getString(i));
                        time = date.getTime() / 1000L;
                    } catch (ParseException e) {
                        time = 0L;
                    }
                    paramsList.add(time);
                }
                omLocation.dailyParamsMap.put(name, paramsList);
            } else {
                omLocation.dailyParamsMap.put(name, parseNumbers(params));
            }
        }
        return omLocation;
    }
}
