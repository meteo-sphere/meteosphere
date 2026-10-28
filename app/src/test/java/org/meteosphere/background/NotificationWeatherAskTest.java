package org.meteosphere.background;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.meteosphere.data.model.Place;

// Whether drawing the weather notification asks for its place's weather: none kept or due, not again within a minute
public class NotificationWeatherAskTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final long PERIOD_MS = 60 * MINUTE_MS;
    private static final int PLACE_ID = 524901;
    // Never asked
    private static final int NONE = 0;

    private static Place loadedAgo(long ms) {
        Place place = new Place();
        place.id = PLACE_ID;
        place.loadedAt = NOW - ms;
        return place;
    }

    @Test
    public void missing() {
        assertTrue(NotificationUpdater.asksWeather(null, NOW, PERIOD_MS, PLACE_ID, NONE, 0));
        assertTrue(NotificationUpdater.asksWeather(null, NOW, PERIOD_MS, -1, NONE, 0));
    }

    @Test
    public void dueOrNot() {
        assertTrue(NotificationUpdater.asksWeather(loadedAgo(2 * PERIOD_MS), NOW, PERIOD_MS, PLACE_ID, NONE, 0));
        assertFalse(NotificationUpdater.asksWeather(loadedAgo(10 * MINUTE_MS), NOW, PERIOD_MS, PLACE_ID, NONE, 0));
        // The current location's alike
        assertTrue(NotificationUpdater.asksWeather(loadedAgo(2 * PERIOD_MS), NOW, PERIOD_MS, -1, NONE, 0));
        assertFalse(NotificationUpdater.asksWeather(loadedAgo(10 * MINUTE_MS), NOW, PERIOD_MS, -1, NONE, 0));
    }

    // The drawings while the weather loads ask once
    @Test
    public void notAgainWithinAMinute() {
        Place due = loadedAgo(2 * PERIOD_MS);
        assertFalse(NotificationUpdater.asksWeather(due, NOW, PERIOD_MS, PLACE_ID, PLACE_ID, NOW - 1000));
        assertFalse(NotificationUpdater.asksWeather(due, NOW, PERIOD_MS, PLACE_ID, PLACE_ID, NOW - MINUTE_MS + 1));
        assertFalse(NotificationUpdater.asksWeather(null, NOW, PERIOD_MS, -1, -1, NOW - 1000));
        // A minute on, again
        assertTrue(NotificationUpdater.asksWeather(due, NOW, PERIOD_MS, PLACE_ID, PLACE_ID, NOW - MINUTE_MS));
    }

    // Another place, as the settings chose it, is asked for at once
    @Test
    public void anotherPlace() {
        assertTrue(NotificationUpdater.asksWeather(loadedAgo(2 * PERIOD_MS), NOW, PERIOD_MS, PLACE_ID, -1, NOW - 1000));
        assertTrue(NotificationUpdater.asksWeather(null, NOW, PERIOD_MS, -1, PLACE_ID, NOW - 1000));
    }

    // The clock set back before the last asking: asked again rather than waited for
    @Test
    public void clockSetBack() {
        assertTrue(NotificationUpdater.asksWeather(null, NOW, PERIOD_MS, PLACE_ID, PLACE_ID, NOW + 60 * MINUTE_MS));
    }
}
