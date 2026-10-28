package org.meteosphere.background;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.meteosphere.data.model.Place;

// When the weather widgets' refreshes load and which widgets they take
public class WidgetUpdaterTest {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long PERIOD_MS = 30 * MINUTE_MS;
    private static final long NOW = 1_800_000_000_000L;

    private static Place loadedAgo(long ms) {
        Place place = new Place();
        place.id = 456172;
        place.loadedAt = NOW - ms;
        return place;
    }

    // A widget refreshed alone loads nothing while its place's weather isn't due, unless forced or showing the time
    @Test
    public void refreshLoads() {
        Place fresh = loadedAgo(10 * MINUTE_MS);
        assertFalse(WidgetUpdater.refreshLoads(false, fresh, false, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.refreshLoads(true, fresh, false, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.refreshLoads(false, fresh, true, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.refreshLoads(false, null, false, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.refreshLoads(false, loadedAgo(31 * MINUTE_MS), false, NOW, PERIOD_MS));
        // Due half a minute before the period ends, as UpdatePeriod has it
        assertTrue(WidgetUpdater.refreshLoads(false, loadedAgo(PERIOD_MS - 30 * 1000L), false, NOW, PERIOD_MS));
        assertFalse(WidgetUpdater.refreshLoads(false, loadedAgo(PERIOD_MS - 31 * 1000L), false, NOW, PERIOD_MS));
        // The clock set back by more than the period
        assertTrue(WidgetUpdater.refreshLoads(false, loadedAgo(-31 * MINUTE_MS), false, NOW, PERIOD_MS));
    }

    // A refresh of all takes a widget by its own last refresh, whatever the age of its place's weather
    @Test
    public void refreshedWithAll() {
        long recently = NOW - 10 * MINUTE_MS;
        assertFalse(WidgetUpdater.refreshedWithAll(false, true, false, recently, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.refreshedWithAll(false, true, false, NOW - 31 * MINUTE_MS, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.refreshedWithAll(false, true, false, 0, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.refreshedWithAll(true, true, false, recently, NOW, PERIOD_MS));
        // No place kept, the current location's too
        assertTrue(WidgetUpdater.refreshedWithAll(false, false, false, recently, NOW, PERIOD_MS));
        // Showing the time: drawn anew with every refresh of all, as its date and the clock's alarm may have changed
        assertTrue(WidgetUpdater.refreshedWithAll(false, true, true, NOW, NOW, PERIOD_MS));
    }

    // Loading asks nothing for a place kept with weather that isn't due, unless forced
    @Test
    public void loadAsks() {
        assertFalse(WidgetUpdater.loadAsks(false, loadedAgo(10 * MINUTE_MS), NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.loadAsks(true, loadedAgo(10 * MINUTE_MS), NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.loadAsks(false, null, NOW, PERIOD_MS));
        assertTrue(WidgetUpdater.loadAsks(false, loadedAgo(31 * MINUTE_MS), NOW, PERIOD_MS));
        // A place stored with no weather yet
        assertTrue(WidgetUpdater.loadAsks(false, loadedAgo(NOW), NOW, PERIOD_MS));
    }

    // The current location asks for the nearest place once the position moves to another hundredth of a degree
    @Test
    public void moved() {
        assertFalse(WidgetUpdater.moved(56.95, 56.95));
        assertFalse(WidgetUpdater.moved(56.951, 56.954));
        assertFalse(WidgetUpdater.moved(24.1, 24.104));
        assertTrue(WidgetUpdater.moved(56.954, 56.956));
        assertTrue(WidgetUpdater.moved(56.95, 56.97));
        assertTrue(WidgetUpdater.moved(-33.86, -33.87));
        // 0.29 - 0.28 is a hair under 0.01 as doubles
        assertTrue(WidgetUpdater.moved(0.28, 0.29));
        assertTrue(WidgetUpdater.moved(0.29, 0.28));
    }
}
