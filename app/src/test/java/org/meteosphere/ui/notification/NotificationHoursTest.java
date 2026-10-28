package org.meteosphere.ui.notification;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.meteosphere.data.model.Weather;

// The weather notification's three hours: which of the forecast, and their times
public class NotificationHoursTest {

    // A local time read as UTC, as the forecast keeps it
    private static long at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 24, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static List<Weather> forecast(int... hours) {
        List<Weather> forecast = new ArrayList<>();
        for (int hour : hours) {
            Weather weather = new Weather();
            weather.time = at(hour, 0);
            forecast.add(weather);
        }
        return forecast;
    }

    private static List<Long> times(List<Weather> hours) {
        List<Long> times = new ArrayList<>();
        for (Weather hour : hours) {
            times.add(hour.time);
        }
        return times;
    }

    private static List<Long> times(int... hours) {
        return times(forecast(hours));
    }

    // The first three after local now less an hour and a half: the first column moves on at half past
    @Test
    public void afterNowLessNinetyMinutes() {
        List<Weather> forecast = forecast(11, 12, 13, 14, 15, 16, 17);
        assertEquals(times(13, 14, 15), times(WeatherNotification.nextHours(forecast, at(14, 20))));
        assertEquals(times(13, 14, 15), times(WeatherNotification.nextHours(forecast, at(14, 29))));
        // Not the hour exactly an hour and a half ago
        assertEquals(times(14, 15, 16), times(WeatherNotification.nextHours(forecast, at(14, 30))));
        assertEquals(times(14, 15, 16), times(WeatherNotification.nextHours(forecast, at(14, 31))));
    }

    @Test
    public void endOfForecast() {
        List<Weather> forecast = forecast(11, 12, 13, 14);
        assertEquals(times(13, 14), times(WeatherNotification.nextHours(forecast, at(14, 20))));
        assertEquals(times(14), times(WeatherNotification.nextHours(forecast, at(15, 20))));
        assertEquals(times(), times(WeatherNotification.nextHours(forecast, at(16, 20))));
        assertEquals(times(), times(WeatherNotification.nextHours(new ArrayList<>(), at(16, 20))));
    }

    // On the 12-hour clock without ":00", AM and PM apart
    @Test
    public void twelveHour() {
        assertEquals("3", WeatherNotification.hourTime(at(15, 0), false));
        assertEquals("3:30", WeatherNotification.hourTime(at(15, 30), false));
        assertEquals("12", WeatherNotification.hourTime(at(0, 0), false));
        assertEquals("10", WeatherNotification.hourTime(at(10, 0), false));
        assertEquals("PM", WeatherNotification.amPm(at(15, 0), false));
        assertEquals("AM", WeatherNotification.amPm(at(0, 0), false));
    }

    @Test
    public void twentyFourHour() {
        assertEquals("15:00", WeatherNotification.hourTime(at(15, 0), true));
        assertEquals("0:00", WeatherNotification.hourTime(at(0, 0), true));
        assertEquals("", WeatherNotification.amPm(at(15, 0), true));
    }
}
