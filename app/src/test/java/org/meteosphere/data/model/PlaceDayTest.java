package org.meteosphere.data.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Day or night now: by today's sunrise and sunset, by the weather now's own without the day, the sun up all day in
// polar day and never in polar night
public class PlaceDayTest {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;

    // In UTC, with the weather now of this minute
    private static Place place() {
        Place place = new Place();
        place.timeZoneId = "UTC";
        place.now = new Weather();
        place.now.time = System.currentTimeMillis() / MINUTE_MS * MINUTE_MS;
        return place;
    }

    private static DayWeather today(int dayLengthMinutes) {
        DayWeather day = new DayWeather();
        day.date = Math.floorDiv(System.currentTimeMillis(), DAY_MS) * DAY_MS;
        day.dayLengthMinutes = dayLengthMinutes;
        return day;
    }

    @Test
    public void polarDay() {
        Place place = place();
        place.addDay(today(24 * 60));
        assertTrue(place.isDay());
    }

    @Test
    public void polarNight() {
        Place place = place();
        place.addDay(today(0));
        // Even with the weather now's sun up: today's day tells
        place.now.sunrise = place.now.time - HOUR_MS;
        place.now.sunset = place.now.time + HOUR_MS;
        place.now.dayLengthMinutes = 120;
        assertFalse(place.isDay());
    }

    // The places of the nearby list have the weather now alone
    @Test
    public void withoutTodayBySunOfTheWeatherNow() {
        Place place = place();
        place.now.sunrise = place.now.time - HOUR_MS;
        place.now.sunset = place.now.time + HOUR_MS;
        place.now.dayLengthMinutes = 120;
        assertTrue(place.isDay());
        place.now.sunrise = place.now.time - 3 * HOUR_MS;
        place.now.sunset = place.now.time - HOUR_MS;
        assertFalse(place.isDay());
    }

    // The weather now an hour of the forecast, which has no sun of its own
    @Test
    public void withoutTodayAndCurrentWeatherNotKnown() {
        Place place = place();
        place.now.time -= 2 * HOUR_MS;
        place.now.sunrise = place.now.time - HOUR_MS;
        place.now.sunset = place.now.time + 3 * HOUR_MS;
        place.now.dayLengthMinutes = 240;
        Weather hour = new Weather();
        hour.time = place.now.time + 2 * HOUR_MS;
        place.addHour(hour);
        assertFalse(place.isDay());
        // Without the hour nothing stands for now
        place.hours.clear();
        assertFalse(place.isDay());
    }
}
