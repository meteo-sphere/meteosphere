package org.meteosphere.data.quake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public class UsgsEarthquakesTest {

    // Four events of a real response of 2026-10-01
    @Test
    public void parsesUsgsGeoJson() throws Exception {
        List<Earthquake> earthquakes = UsgsEarthquakes.parse(resource("usgs_m5.geojson"));
        assertEquals(4, earthquakes.size());
        Earthquake first = earthquakes.get(0);
        assertEquals("us6000tytg", first.id);
        assertEquals(5.3, first.magnitude, 0);
        assertEquals("0 km NNE of Yōkaichiba, Japan", first.place);
        assertEquals(1790857631864L, first.time);
        assertEquals(35.7025, first.latitude, 0);
        assertEquals(140.5507, first.longitude, 0);
        assertEquals(41.649, first.depthKm, 0);
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/us6000tytg", first.url);
        assertEquals("mww", first.magnitudeType);
        assertEquals(4, first.felt);
        assertFalse(first.tsunami);
        assertNull(first.alert);
        assertEquals(0, earthquakes.get(1).felt);
        // Port McNeill, Canada: in the ocean and large enough for tsunami.gov, losses unlikely
        assertTrue(earthquakes.get(2).tsunami);
        assertEquals("green", earthquakes.get(2).alert);
        assertEquals("Balleny Islands region", earthquakes.get(1).place);
        assertEquals(6.6, earthquakes.get(3).magnitude, 0);
    }

    @Test
    public void skipsEventsWithoutMagnitudeAndKeepsMissingTexts() throws Exception {
        String geoJson = "{\"features\":["
                + feature("a", "null", "null", "null")
                + "," + feature("b", "4.9", "\"weak\"", "null")
                + "," + feature("c", "5.0", "null", "null")
                + "]}";
        List<Earthquake> earthquakes = UsgsEarthquakes.parse(geoJson);
        assertEquals(1, earthquakes.size());
        assertEquals("c", earthquakes.get(0).id);
        assertEquals("", earthquakes.get(0).place);
        assertNull(earthquakes.get(0).url);
    }

    @Test
    public void measuresGreatCircleDistance() throws Exception {
        Earthquake japan = UsgsEarthquakes.parse(resource("usgs_m5.geojson")).get(0);
        // To Moscow
        assertEquals(7523.4, japan.distanceKm(55.7520, 37.6178), 0.1);
        Earthquake origin = new Earthquake("o", 5, "", 0, 0, 0, 10, null);
        assertEquals(111.195, origin.distanceKm(0, 1), 0.001);
        // From Amsterdam: Japan to the north-east, the origin of the map to the south
        assertEquals(34.8, japan.bearingFrom(52.37, 4.89), 0.1);
        assertEquals(186.2, origin.bearingFrom(52.37, 4.89), 0.1);
        assertEquals(20015.1, origin.distanceKm(0, 180), 0.1);
    }

    private static String feature(String id, String mag, String place, String url) {
        return "{\"id\":\"" + id + "\",\"properties\":{\"mag\":" + mag + ",\"place\":" + place + ",\"time\":1790857631864,\"url\":" + url + "},"
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[140.5,35.7,10]}}";
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = UsgsEarthquakesTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
