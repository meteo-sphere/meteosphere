package org.meteosphere.ui.widgets.settings;

import android.content.Context;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;

/** The place the weather widgets' settings show their kinds with: a forecast made up from 11 May 2015 on */
final class WidgetSample {

    private WidgetSample() {
    }

    static Place place(Context context) {
        Place place = new Place();
        place.id = -1;
        place.name = "";
        Weather now = hour(new Date(), "d.c2.r3.st", 2.4, 23, 21, 3, 2, 70, 755);
        now.dayLengthMinutes = 1440;
        place.now = now;
        // Every three hours from 10:00 UTC
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.set(Calendar.YEAR, 2015);
        calendar.set(Calendar.MONTH, Calendar.MAY);
        calendar.set(Calendar.DAY_OF_MONTH, 11);
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        place.addHour(hour(calendar.getTime(), "c4.r2", 1.2, 17, 22, 1, 4, 50, 745));
        calendar.add(Calendar.HOUR, 3);
        place.addHour(hour(calendar.getTime(), "d.c3.r1", 0.3, 20, 22, 2, 5, 60, 750));
        calendar.add(Calendar.HOUR, 3);
        place.addHour(hour(calendar.getTime(), "d.c2", 0, 23, 22, 2, 5, 60, 750));
        calendar.add(Calendar.HOUR, 3);
        place.addHour(hour(calendar.getTime(), "d", 0, 25, 15, 2, 5, 60, 750));
        calendar.add(Calendar.HOUR, 3);
        place.addHour(hour(calendar.getTime(), "n", 0, 21, 21, 2, 5, 60, 750));
        calendar.add(Calendar.HOUR, 3);
        place.addHour(hour(calendar.getTime(), "n.c2", 0, 19, 22, 2, 5, 60, 750));
        // A day after another from 10:09 UTC that day
        Calendar days = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        days.set(Calendar.YEAR, 2015);
        days.set(Calendar.MONTH, Calendar.MAY);
        days.set(Calendar.DAY_OF_MONTH, 11);
        days.set(Calendar.HOUR_OF_DAY, 10);
        days.set(Calendar.MINUTE, 9);
        place.addDay(day(days.getTime(), 12, 23, "d.c2", 0, 3, 2));
        days.add(Calendar.DAY_OF_YEAR, 1);
        place.addDay(day(days.getTime(), 13, 24, "d", 0, 4, 3));
        days.add(Calendar.DAY_OF_YEAR, 1);
        place.addDay(day(days.getTime(), 9, 21, "d.c3.r2", 3.5, 6, 4));
        days.add(Calendar.DAY_OF_YEAR, 1);
        place.addDay(day(days.getTime(), 7, 19, "c4.r3.st", 8.0, 8, 5));
        days.add(Calendar.DAY_OF_YEAR, 1);
        place.addDay(day(days.getTime(), 10, 22, "d.c3", 0, 5, 6));
        days.add(Calendar.DAY_OF_YEAR, 1);
        place.addDay(day(days.getTime(), 12, 23, "d.c2", 0, 3, 2));
        place.name = context.getResources().getString(R.string.place_title);
        return place;
    }

    private static Weather hour(Date time, String icon, double precipitation, int temperature, int feelsLike,
                                int windSpeed, int windSector, int humidity, int pressure) {
        Weather hour = new Weather();
        hour.time = time.getTime();
        hour.icon = icon;
        hour.precipitation = precipitation;
        hour.temperature = temperature;
        hour.feelsLike = feelsLike;
        hour.windSpeed = windSpeed;
        hour.windSector = windSector;
        hour.humidity = humidity;
        hour.pressure = pressure;
        return hour;
    }

    private static DayWeather day(Date date, int min, int max, String icon, double precipitation, int windSpeedMax, int windSector) {
        DayWeather day = new DayWeather();
        day.date = date.getTime();
        day.temperatureMin = min;
        day.temperatureMax = max;
        day.icon = icon;
        day.precipitation = precipitation;
        day.windSpeedMax = windSpeedMax;
        day.windSector = windSector;
        return day;
    }
}
