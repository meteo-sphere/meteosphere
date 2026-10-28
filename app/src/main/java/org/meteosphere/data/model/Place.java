package org.meteosphere.data.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.ToLongFunction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A place and, once loaded, its weather. The times of the weather are the place's wall clock read as UTC, see
 * {@link #toLocal(long)}; the units are the app's own ones, converted to the user's when shown.
 */
public final class Place {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    // Sources renew the weather now at least once an hour; it stands ten minutes more, while the next one may be on its
    // way (some bring it a minute or two after the hour). Without them the hour's forecast took its place for that
    // minute, though it may tell other weather, and the new one then took it back
    private static final long NOW_TTL_MS = HOUR_MS + 10 * MINUTE_MS;
    // How far from now the hour standing for it may be
    private static final long NEAREST_HOUR_MS = 30 * MINUTE_MS;

    /** The GeoNames id, the same for every provider */
    public int id;
    public String name = "";
    /** The name the user gave it, null when none */
    public String customName;
    public String country = "";
    /** The first-level region */
    public String region = "";
    public boolean airport;
    public double latitude;
    public double longitude;
    /** Metres above the sea, 0 when unknown */
    public int elevation;
    /** Like "Europe/Riga", null when the source gave none */
    public String timeZoneId;
    /** The offset when it was loaded, for a place without timeZoneId */
    public int utcOffsetMinutes;
    /** From the point asked about, in the nearby list */
    public float distanceKm;
    /** When this weather was loaded, by the device's clock */
    public long loadedAt;
    /** When the source made the weather, UTC, 0 when unknown */
    public long sourceTime;
    /** The id of the add-on whose weather went into it, null for none */
    public String addOnSource;
    public Weather now;
    public ArrayList<Weather> hours = new ArrayList<>();
    public ArrayList<DayWeather> days = new ArrayList<>();

    public String displayName() {
        return this.customName != null && !this.customName.isEmpty() ? this.customName : this.name;
    }

    // Under the place's lock, as the stored places are updated under it
    public synchronized Place copy() {
        Place res = new Place();
        res.id = this.id;
        res.name = this.name;
        res.customName = this.customName;
        res.country = this.country;
        res.region = this.region;
        res.airport = this.airport;
        res.latitude = this.latitude;
        res.longitude = this.longitude;
        res.elevation = this.elevation;
        res.timeZoneId = this.timeZoneId;
        res.utcOffsetMinutes = this.utcOffsetMinutes;
        res.distanceKm = this.distanceKm;
        res.loadedAt = this.loadedAt;
        res.sourceTime = this.sourceTime;
        res.addOnSource = this.addOnSource;
        res.now = this.now != null ? this.now.copy() : null;
        for (Weather hour : this.hours) {
            res.hours.add(hour.copy());
        }
        for (DayWeather day : this.days) {
            res.days.add(day.copy());
        }
        return res;
    }

    /** The hours as they are now, for going through while another thread adds to the place */
    public synchronized ArrayList<Weather> hoursCopy() {
        return new ArrayList<>(this.hours);
    }

    public synchronized ArrayList<DayWeather> daysCopy() {
        return new ArrayList<>(this.days);
    }

    public synchronized void addHour(Weather hour) {
        this.hours.add(hour);
    }

    public synchronized void addDay(DayWeather day) {
        this.days.add(day);
    }

    /** The hours by time and the days by date, one of each time or date */
    public synchronized void sortAndDedupe() {
        this.hours = sortedAndDeduped(this.hours, hour -> hour.time);
        this.days = sortedAndDeduped(this.days, day -> day.date);
    }

    // The sort is stable: of the same time the first one stays
    private static <T> ArrayList<T> sortedAndDeduped(List<T> items, ToLongFunction<T> time) {
        ArrayList<T> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(time));
        ArrayList<T> res = new ArrayList<>();
        for (T item : sorted) {
            if (res.isEmpty() || time.applyAsLong(res.get(res.size() - 1)) != time.applyAsLong(item)) {
                res.add(item);
            }
        }
        return res;
    }

    /** The place's time zone; without its id, a fixed one of the offset it had when loaded */
    public TimeZone timeZone() {
        if (this.timeZoneId != null) {
            return TimeZone.getTimeZone(this.timeZoneId);
        }
        int minutes = Math.abs(this.utcOffsetMinutes);
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:%02d", this.utcOffsetMinutes < 0 ? "-" : "+", minutes / 60, minutes % 60));
    }

    /** The instant as the place's local time */
    public long toLocal(long utcMs) {
        return utcMs + timeZone().getOffset(utcMs);
    }

    /** Now at the place by the device's clock, as a calendar of UTC holding local time */
    public Calendar localNow() {
        Calendar res = Calendar.getInstance(UTC);
        res.setTimeInMillis(toLocal(System.currentTimeMillis()));
        return res;
    }

    /** The UTC offset of the local day starting at localDayStart */
    public int offsetMinutesOfDay(long localDayStart) {
        return offsetMinutesOfDay(timeZone(), localDayStart);
    }

    // At its noon, so that the day of a daylight saving change has one offset: the clocks change at night
    public static int offsetMinutesOfDay(TimeZone timeZone, long localDayStart) {
        long noon = localDayStart + 12 * HOUR_MS;
        return (int) (timeZone.getOffset(noon - timeZone.getRawOffset()) / MINUTE_MS);
    }

    /** The day of the calendar's date, null when there is none */
    public DayWeather day(Calendar local) {
        int year = local.get(Calendar.YEAR);
        int dayOfYear = local.get(Calendar.DAY_OF_YEAR);
        Calendar date = Calendar.getInstance(UTC);
        for (DayWeather day : daysCopy()) {
            date.setTimeInMillis(day.date);
            if (date.get(Calendar.YEAR) == year && date.get(Calendar.DAY_OF_YEAR) == dayOfYear) {
                return day;
            }
        }
        return null;
    }

    /**
     * The weather shown as now: the current one for an hour and ten minutes after its time, then the forecast's hour
     * nearest to now within half an hour, the earlier of two as near; null when there is neither
     */
    public Weather nowWeather() {
        Weather current = this.now;
        if (current == null) {
            return null;
        }
        long local = toLocal(System.currentTimeMillis());
        if (local - current.time < NOW_TTL_MS) {
            return current;
        }
        Weather res = null;
        for (Weather hour : hoursCopy()) {
            long distance = Math.abs(hour.time - local);
            if (distance <= NEAREST_HOUR_MS && (res == null || distance < Math.abs(res.time - local)
                    || distance == Math.abs(res.time - local) && hour.time < res.time)) {
                res = hour;
            }
        }
        return res;
    }

    /**
     * Whether the sun is up now, by today's sunrise and sunset, or by those of the weather now when it stands for now
     * and the place has no days, as in the nearby list; false without either
     */
    public boolean isDay() {
        Weather weather = nowWeather();
        if (weather == null) {
            return false;
        }
        Calendar local = localNow();
        DayWeather today = day(local);
        if (today != null) {
            return isSunUp(local.getTimeInMillis(), today.sunrise, today.sunset, today.dayLengthMinutes);
        }
        if (weather == this.now) {
            return isSunUp(local.getTimeInMillis(), weather.sunrise, weather.sunset, weather.dayLengthMinutes);
        }
        return false;
    }

    // A time missing of a day with daylight is the sun not rising or setting that day: up from its start or till its end.
    // The length is less than 0 for a sunset after midnight, the times tell then
    private static boolean isSunUp(long local, long sunrise, long sunset, int dayLengthMinutes) {
        if (dayLengthMinutes >= 24 * 60) {
            return true;
        }
        if (dayLengthMinutes == 0) {
            return false;
        }
        return (sunrise == 0 || local >= sunrise) && (sunset == 0 || local < sunset);
    }

    /** In the format the app saves and the server sends, the default values left out */
    public synchronized JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", this.id);
        put(json, "name", this.name);
        if (this.customName != null) {
            json.put("custom_name", this.customName);
        }
        put(json, "country", this.country);
        put(json, "region", this.region);
        put(json, "airport", this.airport);
        put(json, "lat", this.latitude);
        put(json, "lon", this.longitude);
        put(json, "elevation", this.elevation);
        if (this.timeZoneId != null) {
            json.put("tz", this.timeZoneId);
        }
        put(json, "utc_offset", this.utcOffsetMinutes);
        if (this.distanceKm != 0) {
            // The float's own digits, 2.4 rather than 2.4000000953674316
            json.put("distance_km", Double.parseDouble(Float.toString(this.distanceKm)));
        }
        put(json, "loaded_at", this.loadedAt);
        put(json, "source_time", this.sourceTime);
        put(json, "add_on_source", this.addOnSource);
        if (this.now != null) {
            json.put("now", this.now.toJson());
        }
        if (!this.hours.isEmpty()) {
            JSONArray array = new JSONArray();
            for (Weather hour : this.hours) {
                array.put(hour.toJson());
            }
            json.put("hours", array);
        }
        if (!this.days.isEmpty()) {
            JSONArray array = new JSONArray();
            for (DayWeather day : this.days) {
                array.put(day.toJson());
            }
            json.put("days", array);
        }
        return json;
    }

    /** The place saved by toJson() or sent by the server; null for an object without an id, as an older build saved */
    public static Place fromJson(JSONObject json) {
        if (!json.has("id")) {
            return null;
        }
        Place res = new Place();
        res.id = json.optInt("id");
        res.name = json.optString("name");
        res.customName = json.optString("custom_name", null);
        res.country = json.optString("country");
        res.region = json.optString("region");
        res.airport = json.optBoolean("airport");
        res.latitude = json.optDouble("lat", 0);
        res.longitude = json.optDouble("lon", 0);
        res.elevation = json.optInt("elevation");
        res.timeZoneId = json.optString("tz", null);
        res.utcOffsetMinutes = json.optInt("utc_offset");
        res.distanceKm = (float) json.optDouble("distance_km", 0);
        res.loadedAt = json.optLong("loaded_at");
        res.sourceTime = json.optLong("source_time");
        res.addOnSource = json.optString("add_on_source", null);
        JSONObject now = json.optJSONObject("now");
        res.now = now != null ? Weather.fromJson(now) : null;
        JSONArray hours = json.optJSONArray("hours");
        for (int i = 0; hours != null && i < hours.length(); i++) {
            JSONObject hour = hours.optJSONObject(i);
            if (hour != null) {
                res.hours.add(Weather.fromJson(hour));
            }
        }
        JSONArray days = json.optJSONArray("days");
        for (int i = 0; days != null && i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            if (day != null) {
                res.days.add(DayWeather.fromJson(day));
            }
        }
        return res;
    }

    // The values other than the defaults, for the three classes
    static void put(JSONObject json, String key, int value) throws JSONException {
        if (value != 0) {
            json.put(key, value);
        }
    }

    static void put(JSONObject json, String key, long value) throws JSONException {
        if (value != 0) {
            json.put(key, value);
        }
    }

    static void put(JSONObject json, String key, double value) throws JSONException {
        if (value != 0) {
            json.put(key, value);
        }
    }

    static void put(JSONObject json, String key, boolean value) throws JSONException {
        if (value) {
            json.put(key, true);
        }
    }

    static void put(JSONObject json, String key, String value) throws JSONException {
        if (value != null && !value.isEmpty()) {
            json.put(key, value);
        }
    }
}
