package org.meteosphere.ui.details;

import android.content.res.Resources;
import java.util.ArrayList;
import org.meteosphere.R;
import org.meteosphere.astro.Sun;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;
import org.meteosphere.util.WindText;

/**
 * The insights of a city's details, what is worth knowing of the days to come: the precipitation, a strong wind, a
 * geomagnetic storm, a much warmer or colder tomorrow, the next sunrise or sunset and a phase of the Moon
 */
final class InsightsBuilder {

    private final DetailsFormat format;
    private final Resources res;
    private final Place location;
    private final long nowUtc;
    // The local day at the place as the Sun counts it: midnight read as UTC
    private final long dayStart;

    InsightsBuilder(DetailsFormat format, Place location, long nowUtc, long dayStart) {
        this.format = format;
        this.res = format.res;
        this.location = location;
        this.nowUtc = nowUtc;
        this.dayStart = dayStart;
    }

    void build(DetailsModel model, Weather current, ArrayList<Weather> hours,
               ArrayList<DayWeather> days, DayWeather today) {
        ArrayList<Weather> timeline = timeline(current, hours);
        int start = firstWet(timeline);
        DetailsModel.Insight precipitation = precipitation(timeline, start, days);
        // Precipitation within a day comes first, a later one after the rest of the weather
        boolean soon = start >= 0 && timeline.get(start).time - this.format.nowLocal < 24 * DetailsFormat.HOUR_MS;
        if (soon) {
            model.insights.add(precipitation);
        }
        add(model, wind(current, hours));
        add(model, storm(current, timeline));
        add(model, trend(today, days));
        if (!soon) {
            model.insights.add(precipitation);
        }
        add(model, sun());
        add(model, moon(model));
    }

    private static void add(DetailsModel model, DetailsModel.Insight insight) {
        if (insight != null) {
            model.insights.add(insight);
        }
    }

    /** The precipitation's sentence alone, as the widgets tell it */
    String precipitationText(Weather current, ArrayList<Weather> hours, ArrayList<DayWeather> days) {
        ArrayList<Weather> timeline = timeline(current, hours);
        return precipitation(timeline, firstWet(timeline), days).text;
    }

    // The weather now, then the hours after it
    private static ArrayList<Weather> timeline(Weather current, ArrayList<Weather> hours) {
        ArrayList<Weather> timeline = new ArrayList<>();
        timeline.add(current);
        timeline.addAll(hours);
        return timeline;
    }

    // The first hour with precipitation, -1 for none
    private static int firstWet(ArrayList<Weather> timeline) {
        for (int i = 0; i < timeline.size(); i++) {
            if (timeline.get(i).hasPrecipitation()) {
                return i;
            }
        }
        return -1;
    }

    // The next spell from the hour it starts at within the hours, otherwise the next wet day
    private DetailsModel.Insight precipitation(ArrayList<Weather> timeline, int start, ArrayList<DayWeather> days) {
        String[] kinds = this.res.getStringArray(R.array.details_precipitation_kinds);
        DetailsModel.Insight precipitation = new DetailsModel.Insight();
        precipitation.icon = R.drawable.ic_details_precipitation;
        precipitation.title = this.res.getString(R.string.precipitation);
        if (start >= 0) {
            Weather first = timeline.get(start);
            int kind = Math.max(0, Weather.precipitationKind(first.icon));
            double sum = 0;
            int end = start;
            while (end < timeline.size() && timeline.get(end).hasPrecipitation()) {
                sum += timeline.get(end).precipitation;
                end++;
            }
            if (start == 0) {
                precipitation.text = end < timeline.size()
                        ? this.res.getString(R.string.details_precipitation_now_until, kinds[kind], this.format.time(timeline.get(end).time))
                        : this.res.getString(R.string.details_precipitation_now, kinds[kind]);
            } else {
                long time = first.time;
                precipitation.text = this.res.getString(R.string.details_precipitation_from, kinds[kind], this.format.when(time));
            }
            precipitation.value = sum >= 0.05 ? this.format.precipitation(sum) + " " + this.format.precipitationUnit() : null;
            return precipitation;
        }
        DayWeather wet = null;
        for (DayWeather day : days) {
            if (day.date > this.format.nowLocal + HoursBuilder.HOURS * DetailsFormat.HOUR_MS - DetailsFormat.DAY_MS
                    && (day.precipitation >= 0.3 || Weather.precipitationKind(day.icon) >= 0)) {
                wet = day;
                break;
            }
        }
        if (wet != null) {
            int kind = Math.max(0, Weather.precipitationKind(wet.icon));
            precipitation.text = this.res.getString(R.string.details_precipitation_on_day, kinds[kind], this.format.onDay(wet.date));
            precipitation.value = wet.precipitation >= 0.05 ? this.format.precipitation(wet.precipitation) + " " + this.format.precipitationUnit() : null;
        } else {
            precipitation.text = this.res.getString(R.string.details_precipitation_none);
        }
        return precipitation;
    }

    // Strong wind within a day
    private DetailsModel.Insight wind(Weather current, ArrayList<Weather> hours) {
        long nowLocal = this.format.nowLocal;
        int maxWind = current.windSpeed;
        long maxWindTime = nowLocal;
        for (Weather hour : hours) {
            if (hour.time - nowLocal <= 24 * DetailsFormat.HOUR_MS && hour.windSpeed > maxWind) {
                maxWind = hour.windSpeed;
                maxWindTime = hour.time;
            }
        }
        if (maxWind < this.format.settings.strongWind) {
            return null;
        }
        DetailsModel.Insight wind = new DetailsModel.Insight();
        wind.icon = R.drawable.ic_details_wind;
        wind.title = this.res.getString(R.string.details_wind);
        wind.text = this.res.getString(R.string.details_wind_strong, maxWindTime == nowLocal
                ? this.res.getString(R.string.details_now_lc) : this.format.when(maxWindTime));
        wind.value = WindText.of(this.format.context, maxWind, this.format.settings.windUnit);
        return wind;
    }

    // A geomagnetic storm within the days
    private DetailsModel.Insight storm(Weather current, ArrayList<Weather> timeline) {
        int maxKp = -1;
        long maxKpTime = 0;
        for (Weather hour : timeline) {
            if (hour.kp > maxKp) {
                maxKp = hour.kp;
                maxKpTime = hour == current ? this.format.nowLocal : hour.time;
            }
        }
        if (maxKp < 5) {
            return null;
        }
        DetailsModel.Insight storm = new DetailsModel.Insight();
        storm.icon = R.drawable.ic_details_geomagnetic;
        storm.title = this.res.getString(R.string.details_magnetic);
        storm.text = this.res.getString(R.string.details_kp_storm, this.format.dayWord(maxKpTime));
        storm.value = this.res.getString(R.string.kp_index) + " " + maxKp;
        return storm;
    }

    // Tomorrow much warmer or colder, by the highest temperatures, with tomorrow's highest
    private DetailsModel.Insight trend(DayWeather today, ArrayList<DayWeather> days) {
        if (today == null || days.size() <= 1) {
            return null;
        }
        int change = this.format.degrees(days.get(1).temperatureMax) - this.format.degrees(today.temperatureMax);
        if (Math.abs(change) < 4) {
            return null;
        }
        DetailsModel.Insight trend = new DetailsModel.Insight();
        trend.icon = R.drawable.ic_details_thermometer;
        trend.title = this.res.getString(R.string.temperature);
        trend.text = this.res.getString(change > 0 ? R.string.details_warmer : R.string.details_colder, Math.abs(change) + "°");
        trend.value = this.format.temperature(days.get(1).temperatureMax);
        return trend;
    }

    // The next sunrise or sunset
    private DetailsModel.Insight sun() {
        Sun.Day sunToday = SunMoonBuilder.sunDay(this.location, this.dayStart);
        long localTz = this.location.toLocal(this.nowUtc);
        long event;
        boolean isSunset = false;
        if (sunToday.rise != Sun.NONE && sunToday.rise > localTz) {
            event = sunToday.rise;
        } else if (sunToday.set != Sun.NONE && sunToday.set > localTz) {
            event = sunToday.set;
            isSunset = true;
        } else {
            Sun.Day tomorrow = SunMoonBuilder.sunDay(this.location, this.dayStart + DetailsFormat.DAY_MS);
            event = tomorrow.rise;
        }
        if (event == Sun.NONE) {
            return null;
        }
        DetailsModel.Insight sun = new DetailsModel.Insight();
        sun.icon = isSunset ? R.drawable.ic_astro_sunset : R.drawable.ic_astro_sunrise;
        sun.title = this.res.getString(R.string.tab_sun);
        sun.text = this.res.getString(isSunset ? R.string.details_sun_sets : R.string.details_sun_rises, this.format.when(event));
        sun.value = this.format.time(event);
        return sun;
    }

    // A principal phase of the Moon within three days
    private DetailsModel.Insight moon(DetailsModel model) {
        if (model.moonNext == null || model.moonNextDays > 3) {
            return null;
        }
        DetailsModel.Insight moon = new DetailsModel.Insight();
        moon.icon = R.drawable.ic_details_moon;
        moon.title = this.res.getString(R.string.moon);
        moon.text = model.moonNext;
        return moon;
    }
}
