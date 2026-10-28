package org.meteosphere.ui.widgets;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;

// The hours and the days the 4x1 widget shows right of the weather now, the times local ones read as UTC
public class ForecastColumnsTest {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    // 23 October 2026, midnight
    private static final long TODAY = 20749 * DAY_MS;

    private static List<Weather> hours(long first, long step, int count) {
        List<Weather> hours = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Weather hour = new Weather();
            hour.time = first + i * step;
            hours.add(hour);
        }
        return hours;
    }

    private static List<DayWeather> days(long first, int count) {
        List<DayWeather> days = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DayWeather day = new DayWeather();
            day.date = first + i * DAY_MS;
            days.add(day);
        }
        return days;
    }

    private static void assertHours(List<Weather> shown, long... times) {
        assertEquals(times.length, shown.size());
        for (int i = 0; i < times.length; i++) {
            assertEquals(times[i], shown.get(i).time);
        }
    }

    private static void assertDays(List<DayWeather> shown, long... dates) {
        assertEquals(dates.length, shown.size());
        for (int i = 0; i < dates.length; i++) {
            assertEquals(dates[i], shown.get(i).date);
        }
    }

    // At 10:56 the hour of 10:00 is still shown, as on the home screen
    @Test
    public void hoursFromAnHourAndAHalfAgo() {
        List<Weather> hours = hours(TODAY + 8 * HOUR_MS, HOUR_MS, 8);
        assertHours(NowAndForecastWidget.hours(hours, TODAY + 10 * HOUR_MS + 56 * MINUTE_MS),
                TODAY + 10 * HOUR_MS, TODAY + 11 * HOUR_MS, TODAY + 12 * HOUR_MS);
    }

    // An hour just an hour and a half before now has gone
    @Test
    public void hoursAfterNotAt() {
        List<Weather> hours = hours(TODAY + 8 * HOUR_MS, HOUR_MS, 8);
        assertHours(NowAndForecastWidget.hours(hours, TODAY + 10 * HOUR_MS + 30 * MINUTE_MS),
                TODAY + 10 * HOUR_MS, TODAY + 11 * HOUR_MS, TODAY + 12 * HOUR_MS);
        assertHours(NowAndForecastWidget.hours(hours, TODAY + 10 * HOUR_MS + 29 * MINUTE_MS),
                TODAY + 9 * HOUR_MS, TODAY + 10 * HOUR_MS, TODAY + 11 * HOUR_MS);
    }

    // The preview's sample, every three hours from 10:00, at 10:09
    @Test
    public void hoursOfThePreview() {
        List<Weather> hours = hours(TODAY + 10 * HOUR_MS, 3 * HOUR_MS, 6);
        assertHours(NowAndForecastWidget.hours(hours, TODAY + 10 * HOUR_MS + 9 * MINUTE_MS),
                TODAY + 10 * HOUR_MS, TODAY + 13 * HOUR_MS, TODAY + 16 * HOUR_MS);
    }

    // At the forecast's end fewer columns have an hour
    @Test
    public void hoursAtTheEnd() {
        List<Weather> hours = hours(TODAY + 8 * HOUR_MS, HOUR_MS, 5);
        assertHours(NowAndForecastWidget.hours(hours, TODAY + 11 * HOUR_MS), TODAY + 10 * HOUR_MS, TODAY + 11 * HOUR_MS,
                TODAY + 12 * HOUR_MS);
        assertHours(NowAndForecastWidget.hours(hours, TODAY + 13 * HOUR_MS), TODAY + 12 * HOUR_MS);
        assertHours(NowAndForecastWidget.hours(hours, TODAY + 14 * HOUR_MS));
    }

    // The days dated at their midnights: from tomorrow, at any time of today
    @Test
    public void daysFromTomorrow() {
        List<DayWeather> days = days(TODAY - DAY_MS, 7);
        for (long now : new long[]{TODAY, TODAY + 10 * HOUR_MS, TODAY + DAY_MS - MINUTE_MS}) {
            assertDays(NowAndForecastWidget.days(days, now), TODAY + DAY_MS, TODAY + 2 * DAY_MS, TODAY + 3 * DAY_MS);
        }
    }

    // The preview's sample is dated at 10:09: its today is shown too
    @Test
    public void daysOfThePreview() {
        long first = TODAY + 10 * HOUR_MS + 9 * MINUTE_MS;
        List<DayWeather> days = days(first, 6);
        assertDays(NowAndForecastWidget.days(days, first + 30 * 1000L), first, first + DAY_MS, first + 2 * DAY_MS);
    }

    @Test
    public void daysAtTheEnd() {
        List<DayWeather> days = days(TODAY, 3);
        assertDays(NowAndForecastWidget.days(days, TODAY + HOUR_MS), TODAY + DAY_MS, TODAY + 2 * DAY_MS);
        assertDays(NowAndForecastWidget.days(days, TODAY + 3 * DAY_MS));
    }
}
