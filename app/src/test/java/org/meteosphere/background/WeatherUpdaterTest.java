package org.meteosphere.background;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.meteosphere.data.model.Place;

// Which places the weather jobs ask for, and how the current location gets its place
public class WeatherUpdaterTest {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long PERIOD_MS = 30 * MINUTE_MS;
    private static final long NOW = 1_800_000_000_000L;
    private static final int RIGA = 456172;
    private static final int JURMALA = 459201;

    private static Place place(int id, long loadedAgoMs) {
        Place place = new Place();
        place.id = id;
        place.loadedAt = NOW - loadedAgoMs;
        return place;
    }

    private static Place at(int id, float distanceKm) {
        Place place = new Place();
        place.id = id;
        place.distanceKm = distanceKm;
        return place;
    }

    // A stored place is asked for with all the others when due, forced, or awaiting an add-on's weather
    @Test
    public void storedAsked() {
        long fresh = NOW - 10 * MINUTE_MS;
        assertFalse(WeatherUpdater.storedAsked(false, fresh, false, NOW, PERIOD_MS));
        assertTrue(WeatherUpdater.storedAsked(true, fresh, false, NOW, PERIOD_MS));
        assertTrue(WeatherUpdater.storedAsked(false, fresh, true, NOW, PERIOD_MS));
        assertTrue(WeatherUpdater.storedAsked(false, NOW - 31 * MINUTE_MS, false, NOW, PERIOD_MS));
        // Added to the favorites with no weather yet
        assertTrue(WeatherUpdater.storedAsked(false, 0, false, NOW, PERIOD_MS));
    }

    // The current location stays as it is while fresh, takes the stored place's weather while that is, else asks
    @Test
    public void currentWay() {
        Place currentFresh = place(RIGA, 10 * MINUTE_MS);
        Place currentDue = place(RIGA, 40 * MINUTE_MS);
        Place storedFresh = place(RIGA, 5 * MINUTE_MS);
        Place storedDue = place(RIGA, 40 * MINUTE_MS);
        assertEquals(WeatherUpdater.CurrentWay.NONE, WeatherUpdater.currentWay(-1, false, currentFresh, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.NONE, WeatherUpdater.currentWay(-1, true, null, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.FRESH, WeatherUpdater.currentWay(RIGA, false, currentFresh, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.FRESH, WeatherUpdater.currentWay(RIGA, false, currentFresh, storedDue, NOW, PERIOD_MS));
        // Forced, the stored place's weather that isn't due still does
        assertEquals(WeatherUpdater.CurrentWay.STORED, WeatherUpdater.currentWay(RIGA, true, currentFresh, storedFresh, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.STORED, WeatherUpdater.currentWay(RIGA, false, currentDue, storedFresh, NOW, PERIOD_MS));
        // Another place than the current location's
        assertEquals(WeatherUpdater.CurrentWay.STORED, WeatherUpdater.currentWay(RIGA, false, place(JURMALA, 0), storedFresh, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.STORED, WeatherUpdater.currentWay(RIGA, false, null, storedFresh, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.REQUEST, WeatherUpdater.currentWay(RIGA, false, currentDue, storedDue, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.REQUEST, WeatherUpdater.currentWay(RIGA, false, currentDue, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.REQUEST, WeatherUpdater.currentWay(RIGA, true, currentFresh, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.REQUEST, WeatherUpdater.currentWay(RIGA, false, place(JURMALA, 0), null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.CurrentWay.REQUEST, WeatherUpdater.currentWay(RIGA, false, null, null, NOW, PERIOD_MS));
    }

    // One place is loaded only when stored and due or forced; the current location at it gives its weather unless forced
    @Test
    public void placeWay() {
        Place storedFresh = place(RIGA, 10 * MINUTE_MS);
        Place storedDue = place(RIGA, 40 * MINUTE_MS);
        Place currentFresh = place(RIGA, 5 * MINUTE_MS);
        Place currentDue = place(RIGA, 35 * MINUTE_MS);
        assertEquals(WeatherUpdater.PlaceWay.NONE, WeatherUpdater.placeWay(-1, true, storedDue, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.NONE, WeatherUpdater.placeWay(RIGA, true, null, currentFresh, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.NONE, WeatherUpdater.placeWay(RIGA, false, storedFresh, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.FROM_CURRENT, WeatherUpdater.placeWay(RIGA, false, storedDue, currentFresh, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.REQUEST, WeatherUpdater.placeWay(RIGA, true, storedDue, currentFresh, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.REQUEST, WeatherUpdater.placeWay(RIGA, true, storedFresh, null, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.REQUEST, WeatherUpdater.placeWay(RIGA, false, storedDue, currentDue, NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.REQUEST, WeatherUpdater.placeWay(RIGA, false, storedDue, place(JURMALA, 0), NOW, PERIOD_MS));
        assertEquals(WeatherUpdater.PlaceWay.REQUEST, WeatherUpdater.placeWay(RIGA, false, storedDue, null, NOW, PERIOD_MS));
    }

    // The device moved once either coordinate is in another hundredth of a degree
    @Test
    public void moved() {
        assertFalse(WeatherUpdater.moved(56.95, 24.10, 56.95, 24.10));
        assertFalse(WeatherUpdater.moved(56.951, 24.101, 56.954, 24.104));
        assertTrue(WeatherUpdater.moved(56.95, 24.10, 56.97, 24.10));
        assertTrue(WeatherUpdater.moved(56.95, 24.10, 56.95, 24.12));
        assertTrue(WeatherUpdater.moved(-33.86, 151.21, -33.87, 151.21));
        // 0.29 - 0.28 is a hair under 0.01 as doubles
        assertTrue(WeatherUpdater.moved(0.28, 10.0, 0.29, 10.0));
        assertTrue(WeatherUpdater.moved(10.0, 0.28, 10.0, 0.29));
    }

    // The place at the device's position is asked for with no position kept, no current location yet, or once moved
    @Test
    public void nearestAsked() {
        assertFalse(WeatherUpdater.nearestAsked(true, true, false));
        assertTrue(WeatherUpdater.nearestAsked(false, true, false));
        assertTrue(WeatherUpdater.nearestAsked(true, false, false));
        assertTrue(WeatherUpdater.nearestAsked(true, true, true));
        assertTrue(WeatherUpdater.nearestAsked(false, false, false));
    }

    // The nearby places are asked for without a position before, without a list, for an old list, or once moved
    @Test
    public void nearbyAsked() {
        long fresh = 10 * MINUTE_MS;
        assertFalse(WeatherUpdater.nearbyAsked(true, false, true, fresh, 60 * MINUTE_MS));
        assertTrue(WeatherUpdater.nearbyAsked(false, false, true, fresh, 60 * MINUTE_MS));
        assertTrue(WeatherUpdater.nearbyAsked(true, false, false, fresh, 60 * MINUTE_MS));
        assertTrue(WeatherUpdater.nearbyAsked(true, true, true, fresh, 60 * MINUTE_MS));
        // Old by NEARBY_FRESH_MS when the update period is longer
        assertFalse(WeatherUpdater.nearbyAsked(true, false, true, WeatherUpdater.NEARBY_FRESH_MS - 1, 60 * MINUTE_MS));
        assertTrue(WeatherUpdater.nearbyAsked(true, false, true, WeatherUpdater.NEARBY_FRESH_MS, 60 * MINUTE_MS));
        // And by the update period when that is shorter
        assertFalse(WeatherUpdater.nearbyAsked(true, false, true, 5 * MINUTE_MS - 1, 5 * MINUTE_MS));
        assertTrue(WeatherUpdater.nearbyAsked(true, false, true, 5 * MINUTE_MS, 5 * MINUTE_MS));
    }

    // A new language asks for the names of the stored, the current, the nearby and the found places, each once
    @Test
    public void nameIds() {
        Place current = at(RIGA, 0);
        List<Place> nearby = Arrays.asList(at(JURMALA, 20), at(RIGA, 0), at(461114, 30));
        List<Place> found = Arrays.asList(at(588409, 0), at(JURMALA, 0));
        assertEquals(Arrays.asList(1, RIGA, 2, JURMALA, 461114, 588409),
                WeatherUpdater.nameIds(Arrays.asList(1, RIGA, 2), current, nearby, found));
        // No current location, no nearby list kept
        assertEquals(Arrays.asList(1, 588409, JURMALA), WeatherUpdater.nameIds(Collections.singletonList(1), null, null, found));
        assertEquals(Collections.emptyList(), WeatherUpdater.nameIds(Collections.emptyList(), null, null, Collections.emptyList()));
    }

    // The ids go to the server at most a hundred at a time, in their order
    @Test
    public void parts() {
        assertEquals(0, WeatherUpdater.parts(ids(0), WeatherUpdater.NAMES_PART).size());
        assertEquals(Collections.singletonList(ids(100)), WeatherUpdater.parts(ids(100), WeatherUpdater.NAMES_PART));
        List<List<Integer>> parts = WeatherUpdater.parts(ids(250), WeatherUpdater.NAMES_PART);
        assertEquals(3, parts.size());
        assertEquals(100, parts.get(0).size());
        assertEquals(100, parts.get(1).size());
        assertEquals(Arrays.asList(200, 201), parts.get(2).subList(0, 2));
        assertEquals(50, parts.get(2).size());
        assertEquals(Arrays.asList(0, 1), WeatherUpdater.parts(ids(101), WeatherUpdater.NAMES_PART).get(0).subList(0, 2));
        assertEquals(Collections.singletonList(100), WeatherUpdater.parts(ids(101), WeatherUpdater.NAMES_PART).get(1));
    }

    private static List<Integer> ids(int count) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(i);
        }
        return ids;
    }

    // The current location goes to the nearby place of the least distance, whatever the list's order
    @Test
    public void nearest() {
        assertNull(WeatherUpdater.nearest(Collections.emptyList()));
        Place near = at(JURMALA, 2.5f);
        assertSame(near, WeatherUpdater.nearest(Arrays.asList(at(RIGA, 12), near, at(461114, 3))));
        Place first = at(RIGA, 1);
        assertSame(first, WeatherUpdater.nearest(Arrays.asList(first, at(JURMALA, 1))));
    }
}
