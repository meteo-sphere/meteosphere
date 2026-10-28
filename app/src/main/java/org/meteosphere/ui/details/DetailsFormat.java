package org.meteosphere.ui.details;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.astro.Sun;
import org.meteosphere.ui.astro.AstroFormatter;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.chart.PrecipitationDrop;
import org.meteosphere.ui.icons.WeatherIcons;
import org.meteosphere.util.Units;

/**
 * How a city's details tell its values and times: in the units and the clock of the settings, the days counted from
 * the city's today. Times are the city's local ones read as UTC.
 */
final class DetailsFormat {

    static final long MINUTE_MS = 60000L;
    static final long HOUR_MS = 60 * MINUTE_MS;
    static final long DAY_MS = 24 * HOUR_MS;

    final Context context;
    final Resources res;
    final DetailsModelBuilder.Settings settings;
    final AstroFormatter astro;
    // Calm, then the directions the wind blows from, north the first
    final String[] windDirections;
    // The city's time now
    final long nowLocal;
    private final SimpleDateFormat hourFormat;
    private final SimpleDateFormat timeFormat;
    private final String[] weekdaysShort;
    private final String[] onWeekday;
    // The weather icons of the strips, all drawn at one size: a vector keeps a picture of the size it is drawn at
    private final HashMap<String, Drawable> stripIcons = new HashMap<>();

    DetailsFormat(Context context, DetailsModelBuilder.Settings settings, long nowLocal) {
        this.context = context;
        this.res = context.getResources();
        this.settings = settings;
        this.nowLocal = nowLocal;
        Locale locale = this.res.getConfiguration().getLocales().get(0);
        this.hourFormat = settings.is24Hour ? new SimpleDateFormat("HH:mm", locale) : new SimpleDateFormat("h a", Locale.US);
        this.hourFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.timeFormat = settings.is24Hour ? new SimpleDateFormat("HH:mm", locale) : new SimpleDateFormat("h:mm a", Locale.US);
        this.timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.astro = new AstroFormatter(context, settings.is24Hour);
        this.weekdaysShort = this.res.getStringArray(R.array.weekdays_short);
        this.onWeekday = this.res.getStringArray(R.array.details_on_weekday);
        this.windDirections = this.res.getStringArray(R.array.wind_directions);
    }

    // --- Header ---

    // "Su, October 14, 15:42" of the local time
    String dateTime(Calendar local) {
        return this.res.getString(R.string.details_date_time, this.weekdaysShort[local.get(Calendar.DAY_OF_WEEK) - 1],
                local.get(Calendar.DAY_OF_MONTH), this.res.getStringArray(R.array.months_in_date)[local.get(Calendar.MONTH)],
                this.timeFormat.format(local.getTime()));
    }

    // "Refreshed Oct 14, 15:42": when the source gave the weather, in the phone's time zone, not the city's
    String updated(long utcTime) {
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        local.setTimeInMillis(utcTime + TimeZone.getDefault().getOffset(utcTime));
        return this.res.getString(R.string.updated_time, this.res.getStringArray(R.array.months_short)[local.get(Calendar.MONTH)],
                local.get(Calendar.DAY_OF_MONTH), this.timeFormat.format(local.getTime()));
    }

    // --- Times and days ---

    String time(long localTime) {
        return this.timeFormat.format(new Date(localTime));
    }

    // The time of a sunrise or a moonset, a dash when there is none
    String clock(long localTime) {
        return localTime == Sun.NONE ? "—" : time(localTime);
    }

    // An hour of the strip: "15:00" or "3 PM"
    String hour(long localTime) {
        return this.hourFormat.format(new Date(localTime));
    }

    // "Today" or "Thu, 16"
    String dayLabel(long localDay) {
        return ForecastFormat.dayLabel(this.res, this.weekdaysShort, localDay, this.nowLocal);
    }

    String weekday(long localDay) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(localDay);
        return this.weekdaysShort[calendar.get(Calendar.DAY_OF_WEEK) - 1];
    }

    // The days from the city's today to the day of the local time: 0 today, 1 tomorrow
    long daysFromToday(long localTime) {
        return (ForecastFormat.floorDay(localTime) - ForecastFormat.floorDay(this.nowLocal)) / DAY_MS;
    }

    // "today at 15:00", "tomorrow at 03:00" or "on Thursday at 09:00"
    String when(long localTime) {
        String time = time(localTime);
        long days = daysFromToday(localTime);
        if (days <= 0) {
            return this.res.getString(R.string.details_at_today, time);
        }
        if (days == 1) {
            return this.res.getString(R.string.details_at_tomorrow, time);
        }
        return this.res.getString(R.string.details_at_day, onDay(localTime), time);
    }

    // "today", "tomorrow" or "on Thursday"
    String dayWord(long localTime) {
        long days = daysFromToday(localTime);
        if (days <= 0) {
            return this.res.getString(R.string.details_today_lc);
        }
        if (days == 1) {
            return this.res.getString(R.string.details_tomorrow_lc);
        }
        return onDay(localTime);
    }

    // "on Thursday"
    String onDay(long localTime) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(localTime);
        return this.onWeekday[calendar.get(Calendar.DAY_OF_WEEK) - 1];
    }

    // --- Values ---

    String temperature(int celsius) {
        return ForecastFormat.temperature(celsius, this.settings.temperatureUnit);
    }

    // In the unit of the settings
    int degrees(int celsius) {
        return Units.temperature(celsius, this.settings.temperatureUnit);
    }

    // A temperature's colour, light enough for the sky
    static int temperatureColor(int celsius) {
        return ForecastFormat.temperatureColor(celsius, true);
    }

    // In the unit of the settings
    int windSpeed(int metresPerSecond) {
        return Units.windSpeed(metresPerSecond, this.settings.windUnit);
    }

    String pressure(int mmHg) {
        float value = Units.pressure(mmHg, this.settings.pressureUnit);
        return this.settings.pressureUnit == Units.INCHES_HG
                ? String.format(Locale.getDefault(), "%.2f", value) : String.valueOf(Math.round(value));
    }

    String precipitation(double millimetres) {
        return ForecastFormat.precipitation(millimetres, this.settings.precipitationUnit);
    }

    String precipitationUnit() {
        return this.res.getStringArray(R.array.precipitation_units)[this.settings.precipitationUnit];
    }

    static int kpColor(int kp) {
        if (kp <= 2) {
            return 0xffa5d6a7;
        }
        if (kp == 3) {
            return 0xffd9f0a0;
        }
        if (kp == 4) {
            return 0xffffe082;
        }
        if (kp == 5) {
            return 0xffffb74d;
        }
        return 0xffef9a9a;
    }

    Drawable stripIcon(String name) {
        Drawable icon = this.stripIcons.get(name);
        if (icon == null) {
            icon = WeatherIcons.getInstance(this.context).getWeatherDrawable(name, false);
            this.stripIcons.put(name, icon);
        }
        return icon;
    }

    // --- The strips' rows ---

    // The parameters of the settings that have a row under the hours, or under the days, in their order
    ArrayList<Integer> rows(boolean days) {
        ArrayList<Integer> rows = new ArrayList<>();
        for (int id : this.settings.parameters) {
            if (id == DetailsModelBuilder.WIND || id == DetailsModelBuilder.HUMIDITY || id == DetailsModelBuilder.PRESSURE
                    || id == DetailsModelBuilder.GEOMAGNETIC || id == DetailsModelBuilder.PRECIPITATION || (days && id == DetailsModelBuilder.MOON)) {
                rows.add(id);
            }
        }
        return rows;
    }

    String rowLabel(int id) {
        switch (id) {
            case DetailsModelBuilder.WIND:
                return this.res.getString(R.string.details_row_label, this.res.getString(R.string.wind_speed), this.res.getStringArray(R.array.wind_units)[this.settings.windUnit]);
            case DetailsModelBuilder.HUMIDITY:
                return this.res.getString(R.string.details_row_label, this.res.getString(R.string.humidity), "%");
            case DetailsModelBuilder.PRESSURE:
                return this.res.getString(R.string.details_row_label, this.res.getString(R.string.pressure), this.res.getStringArray(R.array.pressure_units)[this.settings.pressureUnit]);
            case DetailsModelBuilder.GEOMAGNETIC:
                return this.res.getString(R.string.details_row_label, this.res.getString(R.string.geomagnetic), this.res.getString(R.string.kp_index));
            case DetailsModelBuilder.PRECIPITATION:
                return this.res.getString(R.string.details_row_label, this.res.getString(R.string.precipitation), this.res.getStringArray(R.array.precipitation_units)[this.settings.precipitationUnit]);
            default:
                return this.res.getString(R.string.moon_label);
        }
    }

    void windCell(ForecastStripView.Cell cell, int speed, int direction) {
        cell.text = String.valueOf(windSpeed(speed));
        cell.arrow = ForecastFormat.windArrow(speed, direction);
    }

    // The amount after a drop as full as it is, an hour's or a day's; none, an empty drop
    void precipitationCell(ForecastStripView.Cell cell, double millimetres, boolean day) {
        if (millimetres < 0.05) {
            cell.text = "0";
            cell.faint = true;
            cell.drop = 0;
        } else {
            cell.text = precipitation(millimetres);
            cell.color = PrecipitationDrop.COLOR;
            cell.drop = day ? PrecipitationDrop.dayFill(millimetres) : PrecipitationDrop.hourFill(millimetres);
        }
    }
}
