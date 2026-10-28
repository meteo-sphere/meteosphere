package org.meteosphere.data.quake;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class EarthquakeTest {

    private static Earthquake quake(String id, double magnitude, String place, long time) {
        return new Earthquake(id, magnitude, place, time, -5.123, 151.987, 10, null);
    }

    // The place USGS gives, or the coordinates when it gives none
    @Test
    public void placeName() {
        assertEquals("80 km ENE of Tadine", quake("a", 5, "80 km ENE of Tadine", 0).placeName());
        assertEquals("-5.12, 151.99", quake("b", 5, "", 0).placeName());
        assertEquals("-5.12, 151.99", quake("c", 5, null, 0).placeName());
    }

    // The strongest first, the later of equal ones
    @Test
    public void strongestFirst() {
        List<Earthquake> list = new ArrayList<>(Arrays.asList(quake("early 6", 6, "", 100), quake("7", 7.1, "", 50),
                quake("late 6", 6, "", 200), quake("5", 5.2, "", 300)));
        list.sort(Earthquake.STRONGEST_FIRST);
        assertEquals("7", list.get(0).id);
        assertEquals("late 6", list.get(1).id);
        assertEquals("early 6", list.get(2).id);
        assertEquals("5", list.get(3).id);
    }
}
