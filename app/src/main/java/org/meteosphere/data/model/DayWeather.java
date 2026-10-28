package org.meteosphere.data.model;

import org.json.JSONException;
import org.json.JSONObject;

/** The weather of one day of the forecast, its date the place's local midnight */
public final class DayWeather {

    public long date;
    public String icon = "nodata";
    public int code = -1;
    public int temperatureMin;
    public int temperatureMax;
    public int pressureMin;
    public int pressureMax;
    public int humidityMax;
    public int windSpeedMax;
    /** The dominant one */
    public int windSector;
    /** The day's sum */
    public double precipitation;
    public int kpMax;
    /** Local time, 0 when the sun doesn't rise or set that day */
    public long sunrise;
    public long sunset;
    /** 0 in polar night, 1440 in polar day */
    public int dayLengthMinutes;

    /** The text of the code in the app's language, empty when there is none */
    public String description() {
        return WeatherDescriptions.get(this.code);
    }

    DayWeather copy() {
        DayWeather res = new DayWeather();
        res.date = this.date;
        res.icon = this.icon;
        res.code = this.code;
        res.temperatureMin = this.temperatureMin;
        res.temperatureMax = this.temperatureMax;
        res.pressureMin = this.pressureMin;
        res.pressureMax = this.pressureMax;
        res.humidityMax = this.humidityMax;
        res.windSpeedMax = this.windSpeedMax;
        res.windSector = this.windSector;
        res.precipitation = this.precipitation;
        res.kpMax = this.kpMax;
        res.sunrise = this.sunrise;
        res.sunset = this.sunset;
        res.dayLengthMinutes = this.dayLengthMinutes;
        return res;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        Place.put(json, "date", this.date);
        if (!this.icon.equals("nodata")) {
            json.put("icon", this.icon);
        }
        if (this.code != -1) {
            json.put("code", this.code);
        }
        Place.put(json, "temp_min", this.temperatureMin);
        Place.put(json, "temp_max", this.temperatureMax);
        Place.put(json, "pressure_min", this.pressureMin);
        Place.put(json, "pressure_max", this.pressureMax);
        Place.put(json, "humidity_max", this.humidityMax);
        Place.put(json, "wind_max", this.windSpeedMax);
        Place.put(json, "wind_sector", this.windSector);
        Place.put(json, "precip", this.precipitation);
        Place.put(json, "kp_max", this.kpMax);
        Place.put(json, "sunrise", this.sunrise);
        Place.put(json, "sunset", this.sunset);
        Place.put(json, "day_length", this.dayLengthMinutes);
        return json;
    }

    static DayWeather fromJson(JSONObject json) {
        DayWeather res = new DayWeather();
        res.date = json.optLong("date");
        res.icon = json.optString("icon", "nodata");
        res.code = json.optInt("code", -1);
        res.temperatureMin = json.optInt("temp_min");
        res.temperatureMax = json.optInt("temp_max");
        res.pressureMin = json.optInt("pressure_min");
        res.pressureMax = json.optInt("pressure_max");
        res.humidityMax = json.optInt("humidity_max");
        res.windSpeedMax = json.optInt("wind_max");
        res.windSector = json.optInt("wind_sector");
        res.precipitation = json.optDouble("precip", 0);
        res.kpMax = json.optInt("kp_max");
        res.sunrise = json.optLong("sunrise");
        res.sunset = json.optLong("sunset");
        res.dayLengthMinutes = json.optInt("day_length");
        return res;
    }
}
