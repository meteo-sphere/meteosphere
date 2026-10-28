package org.meteosphere.ui.details;

import android.content.Context;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;
import org.meteosphere.addon.AddOns;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.WeatherProviders;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.ui.icons.WeatherIcons;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.sky.SkyWeather;
import org.meteosphere.util.WindText;

/**
 * Builds a city's DetailsModel on a background thread: copies the forecast out of the settings, converts the
 * units, formats every text, rasterises the icons and computes the Sun and the Moon. The page only binds it. The header
 * and the weather now are built here, the hours, the days, the insights, the tiles, the Sun and the Moon by builders of
 * their own.
 */
final class DetailsModelBuilder {

    static final int WIND = 2;
    static final int HUMIDITY = 3;
    static final int PRESSURE = 4;
    static final int GEOMAGNETIC = 5;
    static final int PRECIPITATION = 6;
    static final int MOON = AppSettings.MOON_DETAIL_WIDGET;
    static final int EARTHQUAKES = AppSettings.EARTHQUAKE_DETAIL_WIDGET;
    static final int SUN_NOW = AppSettings.SUN_NOW_DETAIL_WIDGET;

    /** The places whose forecast failed to load: their pages show what they have instead of waiting */
    static final java.util.Set<Integer> loadFailed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The settings the texts depend on, read on the main thread */
    static final class Settings {
        int temperatureUnit;
        int windUnit;
        int pressureUnit;
        int precipitationUnit;
        boolean is24Hour;
        String weatherSource;
        final ArrayList<Integer> parameters = new ArrayList<>();
        int strongWind;
        int distanceUnit;

        static Settings read(Context context) {
            AppSettings appSettings = AppSettings.getInstance();
            Settings settings = new Settings();
            settings.temperatureUnit = appSettings.temperatureUnit;
            settings.windUnit = appSettings.windSpeedUnit;
            settings.pressureUnit = appSettings.pressureUnit;
            settings.precipitationUnit = appSettings.precipitationUnit;
            settings.is24Hour = appSettings.use24Hours;
            settings.weatherSource = appSettings.weatherSource;
            List<Integer> used = appSettings.getShownDetailWidgets();
            if (used != null) {
                settings.parameters.addAll(used);
            }
            settings.strongWind = context.getResources().getInteger(R.integer.strong_wind_mps);
            settings.distanceUnit = appSettings.distanceUnit;
            return settings;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Settings)) {
                return false;
            }
            Settings s = (Settings) o;
            return this.temperatureUnit == s.temperatureUnit && this.windUnit == s.windUnit && this.pressureUnit == s.pressureUnit
                    && this.precipitationUnit == s.precipitationUnit && this.is24Hour == s.is24Hour && this.distanceUnit == s.distanceUnit
                    && String.valueOf(this.weatherSource).equals(String.valueOf(s.weatherSource)) && this.parameters.equals(s.parameters);
        }

        @Override
        public int hashCode() {
            return this.parameters.hashCode();
        }
    }

    private final Context context;
    private final Settings settings;
    // The M5+ earthquakes of the last day the world over, null while they load
    private final List<Earthquake> earthquakes;
    private final boolean earthquakesFailed;

    // How the place's values and times are told, from its time now
    private DetailsFormat format;
    private long nowUtc;
    // The local day at the place as the Sun and the Moon count it: midnight read as UTC
    private long dayStart;

    DetailsModelBuilder(Context context, Settings settings, List<Earthquake> earthquakes, boolean earthquakesFailed) {
        this.earthquakes = earthquakes;
        this.earthquakesFailed = earthquakesFailed;
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    DetailsModel build(int locationId) {
        DetailsModel model = new DetailsModel();
        Place location = header(model, locationId);
        if (location == null) {
            return model;
        }
        sunAndMoon(model, location);
        try {
            weather(model, location);
            return model;
        } catch (RuntimeException e) {
            // A forecast the builder can't take: the page still shows the place, its Sun and Moon and no weather,
            // rather than staying empty
            android.util.Log.e("DetailsModelBuilder", "Building the weather of " + locationId, e);
            DetailsModel bare = new DetailsModel();
            Place again = header(bare, locationId);
            if (again != null) {
                sunAndMoon(bare, again);
            }
            return bare;
        }
    }

    // The place's name, its time and its sky; the place, null if there is none
    private Place header(DetailsModel model, int locationId) {
        model.locationId = locationId;
        model.builtAt = android.os.SystemClock.uptimeMillis();
        model.isCurrentLocation = locationId == -1;
        Place location = AppSettings.getInstance().getPlace(locationId);
        model.location = location;
        if (location == null) {
            model.name = "";
            model.backgroundLayers = SkyWeather.of((Place) null);
            model.baseColor = SkyWeather.color(model.backgroundLayers);
            return null;
        }
        model.name = location.displayName();
        // Its own name too when it was given another
        model.otherName = Objects.equals(model.name, location.name) ? null : location.name;

        Calendar localNow = location.localNow();
        this.format = new DetailsFormat(this.context, this.settings, localNow.getTimeInMillis());
        this.nowUtc = System.currentTimeMillis();
        this.dayStart = ForecastFormat.floorDay(location.toLocal(this.nowUtc));
        model.dateTime = this.format.dateTime(localNow);
        model.backgroundLayers = SkyWeather.of(location);
        model.baseColor = SkyWeather.color(model.backgroundLayers);
        model.isDay = location.isDay();
        String source = WeatherProviders.FORECA.equals(this.settings.weatherSource) ? "Foreca" : "Open-Meteo";
        // The add-on's name first when its weather went into the forecast
        String addOn = AddOns.sourceName(this.context, location.addOnSource);
        model.source = addOn != null ? this.context.getString(R.string.source_with_add_on, addOn, source) : source;
        model.updated = location.sourceTime > 0 ? this.format.updated(location.sourceTime) : "";
        model.forecastHash = forecastHash(location);
        model.southernHemisphere = location.latitude < 0;
        return location;
    }

    private void sunAndMoon(DetailsModel model, Place location) {
        new SunMoonBuilder(this.format, location, this.nowUtc, this.dayStart).build(model);
    }

    // The weather now and to come
    private void weather(DetailsModel model, Place location) {
        Weather current = location.nowWeather();
        if (current == null) {
            return;
        }
        DetailsFormat format = this.format;
        model.hasData = true;
        model.temperature = format.temperature(current.temperature);
        // The sun or the moon by the place's sunrise and sunset now, as the widgets have it: the current weather may be
        // an hour that began before the sunset, as Foreca's is, or the forecast's nearest hour
        model.icon = WeatherIcons.getInstance(this.context).getWeatherDrawable(current.icon(model.isDay), false);
        model.condition = current.description();
        if (current.feelsLike != current.temperature) {
            model.feelsLike = format.res.getString(R.string.details_feels_like, format.temperature(current.feelsLike));
        }

        model.windSpeed = String.valueOf(format.windSpeed(current.windSpeed));
        model.windUnit = WindText.unitName(this.context, current.windSpeed, this.settings.windUnit);
        if (current.windSpeed > 0 && current.windSector > 0 && current.windSector < format.windDirections.length) {
            model.windDirection = format.windDirections[current.windSector];
            model.windArrow = ForecastFormat.windArrow(current.windSpeed, current.windSector);
        }
        // As the favourites' cards tell it
        model.strongWind = current.windSpeed >= this.settings.strongWind;

        DaysBuilder daily = new DaysBuilder(format, location);
        ArrayList<DayWeather> days = daily.upcoming();
        DayWeather today = !days.isEmpty() && ForecastFormat.floorDay(days.get(0).date) == ForecastFormat.floorDay(format.nowLocal)
                ? days.get(0) : null;
        if (today != null) {
            model.highLow = format.res.getString(R.string.details_high_low, format.temperature(today.temperatureMax), format.temperature(today.temperatureMin));
        }

        HoursBuilder hourly = new HoursBuilder(format, location);
        ArrayList<Weather> hours = hourly.upcoming(current);
        model.hourly = hourly.strip(current, model.isDay, hours);
        model.summary = hourly.summary(current, hours, today);
        if (!days.isEmpty()) {
            model.days = daily.list(days, current);
            model.dailyStrip = daily.strip(days);
        }
        shown(model, current, hours, days);
        new InsightsBuilder(format, location, this.nowUtc, this.dayStart).build(model, current, hours, days, today);
        new TilesBuilder(format, location, this.nowUtc, this.earthquakes, this.earthquakesFailed).build(model, current, hours, today, days);
        model.waitsForForecast = days.isEmpty() && (model.hourly == null || model.hourly.columns.size() <= 1)
                && location.loadedAt == 0 && !loadFailed.contains(location.id);
    }

    // What the header, the hours' strip and the days show, as they read: an update that changes none of it isn't flashed
    private static void shown(DetailsModel model, Weather current, ArrayList<Weather> hours, ArrayList<DayWeather> days) {
        model.shownNow = current.icon + "|" + model.temperature + "|" + model.condition + "|" + model.feelsLike + "|"
                + model.windSpeed + "|" + model.windDirection;
        if (model.hourly != null) {
            HashMap<Long, Weather> byTime = new HashMap<>();
            for (Weather hour : hours) {
                byTime.put(hour.time, hour);
            }
            // The hours after now, not the sun's events between them
            for (ForecastStripView.Column column : model.hourly.columns) {
                Weather hour = column.event == ForecastStripView.EVENT_NONE ? byTime.get(column.time) : null;
                if (hour != null) {
                    model.shownHours.put(column.time, hour.icon + "|" + shown(column));
                }
            }
        }
        if (model.days != null && model.dailyStrip != null) {
            for (int i = 0; i < days.size() && i < model.days.size() && i < model.dailyStrip.columns.size(); i++) {
                DailyRangeView.Day day = model.days.get(i);
                model.shownDays.put(days.get(i).date, days.get(i).icon + "|" + day.precipitation + "|" + day.min + "|" + day.max
                        + "|" + shown(model.dailyStrip.columns.get(i)));
            }
        }
    }

    // A column's figures and its rows' values as the strip writes them
    private static String shown(ForecastStripView.Column column) {
        StringBuilder text = new StringBuilder().append(column.value).append('|').append(column.value2);
        if (column.cells != null) {
            for (ForecastStripView.Cell cell : column.cells) {
                if (cell != null) {
                    text.append('|').append(cell.text).append('/').append(cell.text2).append('/').append(cell.arrow);
                }
            }
        }
        return text.toString();
    }

    // The weather the place was loaded with: the current, the days and the hours, and when the source gave them
    private static int forecastHash(Place location) {
        try {
            JSONObject json = location.toJson();
            return Objects.hash(json.opt("source_time"), json.opt("add_on_source"), String.valueOf(json.opt("now")),
                    String.valueOf(json.opt("days")), String.valueOf(json.opt("hours")));
        } catch (JSONException e) {
            return 0;
        }
    }
}
