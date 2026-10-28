package org.meteosphere.data.model;

import org.json.JSONException;
import org.json.JSONObject;

/** The weather of one moment: the weather now or an hour of the forecast, its time the place's local time */
public final class Weather {

    public long time;
    public String icon = "nodata";
    public int code = -1;
    public int temperature;
    public int feelsLike;
    public int pressure;
    public int windSpeed;
    public int windSector;
    public int humidity;
    public double precipitation;
    public int kp;
    // The sun of its day, on the weather now only: a place of the nearby list has no days and still tells day from night
    public long sunrise;
    public long sunset;
    public int dayLengthMinutes;

    /** The text of the code in the app's language, empty when there is none */
    public String description() {
        return WeatherDescriptions.get(this.code);
    }

    /** The icon with the sun or the moon by isDay; the ones without either stay, "nodata" too */
    public String icon(boolean isDay) {
        if (this.icon.equals("d") || this.icon.equals("n") || this.icon.startsWith("d.") || this.icon.startsWith("n.")) {
            return (isDay ? "d" : "n") + this.icon.substring(1);
        }
        return this.icon;
    }

    /** 0 rain, 1 snow, 2 rain with snow, -1 none, by the icon's parts */
    public static int precipitationKind(String icon) {
        if (icon == null) {
            return -1;
        }
        boolean rain = false;
        boolean snow = false;
        for (String part : icon.split("\\.")) {
            // The strength 1-3 follows the kind; "st" is the thunderstorm
            if (part.length() < 2 || !Character.isDigit(part.charAt(part.length() - 1))) {
                continue;
            }
            String kind = part.substring(0, part.length() - 1);
            rain |= kind.equals("r") || kind.equals("rs");
            snow |= kind.equals("s") || kind.equals("rs");
        }
        return rain && snow ? 2 : snow ? 1 : rain ? 0 : -1;
    }

    /** Rain or snow in the icon, or a measurable amount without it */
    public boolean hasPrecipitation() {
        return precipitationKind(this.icon) >= 0 || this.precipitation >= 0.1;
    }

    Weather copy() {
        Weather res = new Weather();
        res.time = this.time;
        res.icon = this.icon;
        res.code = this.code;
        res.temperature = this.temperature;
        res.feelsLike = this.feelsLike;
        res.pressure = this.pressure;
        res.windSpeed = this.windSpeed;
        res.windSector = this.windSector;
        res.humidity = this.humidity;
        res.precipitation = this.precipitation;
        res.kp = this.kp;
        res.sunrise = this.sunrise;
        res.sunset = this.sunset;
        res.dayLengthMinutes = this.dayLengthMinutes;
        return res;
    }

    // The default values are left out, as the server leaves them
    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        Place.put(json, "time", this.time);
        if (!this.icon.equals("nodata")) {
            json.put("icon", this.icon);
        }
        if (this.code != -1) {
            json.put("code", this.code);
        }
        Place.put(json, "temp", this.temperature);
        Place.put(json, "feels_like", this.feelsLike);
        Place.put(json, "pressure", this.pressure);
        Place.put(json, "wind", this.windSpeed);
        Place.put(json, "wind_sector", this.windSector);
        Place.put(json, "humidity", this.humidity);
        Place.put(json, "precip", this.precipitation);
        Place.put(json, "kp", this.kp);
        Place.put(json, "sunrise", this.sunrise);
        Place.put(json, "sunset", this.sunset);
        Place.put(json, "day_length", this.dayLengthMinutes);
        return json;
    }

    static Weather fromJson(JSONObject json) {
        Weather res = new Weather();
        res.time = json.optLong("time");
        res.icon = json.optString("icon", "nodata");
        res.code = json.optInt("code", -1);
        res.temperature = json.optInt("temp");
        res.feelsLike = json.optInt("feels_like");
        res.pressure = json.optInt("pressure");
        res.windSpeed = json.optInt("wind");
        res.windSector = json.optInt("wind_sector");
        res.humidity = json.optInt("humidity");
        res.precipitation = json.optDouble("precip", 0);
        res.kp = json.optInt("kp");
        res.sunrise = json.optLong("sunrise");
        res.sunset = json.optLong("sunset");
        res.dayLengthMinutes = json.optInt("day_length");
        return res;
    }
}
