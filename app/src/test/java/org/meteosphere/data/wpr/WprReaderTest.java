package org.meteosphere.data.wpr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import org.json.JSONException;
import org.junit.Test;
import org.meteosphere.data.http.RequestStats;
import org.meteosphere.data.model.Place;

public class WprReaderTest {

    // A forecast and a nearby place as the server sends them, between them a place without an id
    private static final String PLACES = "{\"places\": ["
            + "{\"id\": 456172, \"name\": \"Riga\", \"country\": \"Latvia\", \"region\": \"Riga\", \"lat\": 56.95, \"lon\": 24.11,"
            + " \"elevation\": 7, \"tz\": \"Europe/Riga\", \"utc_offset\": 180, \"source_time\": 1792929600000,"
            + " \"now\": {\"time\": 1792940400000, \"icon\": \"n.c3\", \"code\": 2, \"temp\": 6, \"feels_like\": 3, \"pressure\": 762,"
            + " \"wind\": 4, \"wind_sector\": 5, \"humidity\": 88, \"precip\": 0.1, \"kp\": 3,"
            + " \"sunrise\": 1792912980000, \"sunset\": 1792948920000, \"day_length\": 599},"
            + " \"hours\": [{\"time\": 1792940400000, \"icon\": \"c4.r1\", \"code\": 61, \"temp\": 5, \"precip\": 0.4},"
            + " {\"time\": 1792944000000, \"code\": 0, \"temp\": -1}],"
            + " \"days\": [{\"date\": 1792886400000, \"icon\": \"c4.r1\", \"code\": 61, \"temp_min\": 2, \"temp_max\": 9,"
            + " \"precip\": 1.5, \"kp_max\": 4, \"day_length\": 599}]},"
            + "{\"name\": \"Nowhere\"},"
            + "{\"id\": 2345, \"name\": \"Riga Airport\", \"airport\": true, \"distance_km\": 9.3}"
            + "]}";

    @Test
    public void readsThePlaces() throws Exception {
        ArrayList<Place> places = WprReader.places(PLACES);
        assertEquals(2, places.size());

        Place riga = places.get(0);
        assertEquals(456172, riga.id);
        assertEquals("Riga", riga.name);
        assertNull(riga.customName);
        assertEquals("Latvia", riga.country);
        assertEquals(56.95, riga.latitude, 0);
        assertEquals("Europe/Riga", riga.timeZoneId);
        assertEquals(1792929600000L, riga.sourceTime);
        assertEquals(0, riga.loadedAt);
        assertEquals(1792940400000L, riga.now.time);
        assertEquals("n.c3", riga.now.icon);
        assertEquals(3, riga.now.feelsLike);
        assertEquals(5, riga.now.windSector);
        assertEquals(0.1, riga.now.precipitation, 0);
        assertEquals(599, riga.now.dayLengthMinutes);
        assertEquals(2, riga.hours.size());
        assertEquals(0.4, riga.hours.get(0).precipitation, 0);
        // A code 0 is clear sky, not none; a missing icon is none
        assertEquals(0, riga.hours.get(1).code);
        assertEquals("nodata", riga.hours.get(1).icon);
        assertEquals(-1, riga.hours.get(1).temperature);
        assertEquals(1, riga.days.size());
        assertEquals(1792886400000L, riga.days.get(0).date);
        assertEquals(9, riga.days.get(0).temperatureMax);
        assertEquals(1.5, riga.days.get(0).precipitation, 0);
        assertEquals(0, riga.days.get(0).sunrise);

        Place airport = places.get(1);
        assertTrue(airport.airport);
        assertEquals(9.3f, airport.distanceKm, 0);
        assertNull(airport.now);
        assertTrue(airport.hours.isEmpty());
        assertEquals("", airport.region);
    }

    @Test
    public void tellsTheErrorOfTheServer() {
        assertEquals("No place 42", WprReader.error(404, "{\"error\": \"No place 42\"}"));
        // A proxy's page, an answer without the text, none at all
        assertEquals("HTTP 502", WprReader.error(502, "<html><body>Bad Gateway</body></html>"));
        assertEquals("HTTP 500", WprReader.error(500, "{}"));
        assertEquals("HTTP 503", WprReader.error(503, null));
    }

    // The requests a day from the first day to today, as RequestStats counts the device's
    @Test
    public void readsTheStats() throws Exception {
        RequestStats.Counts counts = WprReader.stats("{\"first\": 20380, \"today\": 20382,"
                + " \"sources\": {\"open-meteo\": [120, 0, 37], \"foreca\": [5, 6, 7]}}");
        assertEquals(20380, counts.first);
        assertEquals(20382, counts.today);
        assertEquals(2, counts.bySource.size());
        assertArrayEquals(new int[]{120, 0, 37}, counts.bySource.get("open-meteo"));
        assertArrayEquals(new int[]{5, 6, 7}, counts.bySource.get("foreca"));
        // One day, nothing asked yet
        assertTrue(WprReader.stats("{\"first\": 20382, \"today\": 20382, \"sources\": {}}").bySource.isEmpty());
    }

    // Counts that can't be put on the days fail as an unreadable answer does
    @Test
    public void rejectsStatsThatCantBeRight() {
        assertRejected("{\"first\": 20383, \"today\": 20382, \"sources\": {}}");
        assertRejected("{\"today\": 20382, \"sources\": {\"foreca\": [1]}}");
        assertRejected("{\"first\": 20382, \"sources\": {\"foreca\": [1]}}");
        assertRejected("{\"first\": 20380, \"today\": 20382, \"sources\": {\"open-meteo\": [120, 0, 37], \"foreca\": [6, 7]}}");
        assertRejected("{\"first\": 20380, \"today\": 20382, \"sources\": {\"foreca\": [5, 6, 7, 8]}}");
        assertRejected("{\"first\": 20380, \"today\": 20382}");
    }

    private static void assertRejected(String json) {
        try {
            WprReader.stats(json);
            fail(json);
        } catch (JSONException e) {
            // As expected
        }
    }
}
