package org.meteosphere.data.model;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

// The weather now: the current one for an hour and ten minutes after its time, then the forecast's hour nearest to now
public class PlaceNowTest {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;

    private static Weather at(long time) {
        Weather weather = new Weather();
        weather.time = time;
        return weather;
    }

    // In UTC, the current weather of 10:00 and the hours from 09:00 to 14:00, now being the time given after 10:00
    private static Place place(long sinceTen) {
        long ten = System.currentTimeMillis() - sinceTen;
        Place place = new Place();
        place.timeZoneId = "UTC";
        place.now = at(ten);
        for (int i = -1; i <= 4; i++) {
            place.hours.add(at(ten + i * HOUR_MS));
        }
        return place;
    }

    // Past the hour, while the next one may be on its way
    @Test
    public void currentForAnHourAndTen() {
        Place place = place(59 * MINUTE_MS);
        assertSame(place.now, place.nowWeather());
        place = place(69 * MINUTE_MS);
        assertSame(place.now, place.nowWeather());
    }

    @Test
    public void thenTheNearestHour() {
        // 11:10: 11:00, not the 10:00 that ended
        Place place = place(70 * MINUTE_MS);
        assertSame(place.hours.get(2), place.nowWeather());
        // 11:40: 12:00
        place = place(100 * MINUTE_MS);
        assertSame(place.hours.get(3), place.nowWeather());
        // 13:29: 13:00
        place = place(209 * MINUTE_MS);
        assertSame(place.hours.get(4), place.nowWeather());
    }

    // Long without an update, past the forecast's hours: nothing stands for now
    @Test
    public void noneAfterTheForecast() {
        Place place = place(5 * HOUR_MS);
        assertNull(place.nowWeather());
    }
}
