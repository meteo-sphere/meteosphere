package org.meteosphere.data.quake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class EarthquakeAlertsTest {

    private static final EarthquakeAlerts.Place TASHKENT = new EarthquakeAlerts.Place("Tashkent", 41.2995, 69.2401);
    private static final EarthquakeAlerts.Place RIGA = new EarthquakeAlerts.Place("Riga", 56.9496, 24.1052);

    // Near Baghlan, Afghanistan: 580 km from Tashkent, 4020 km from Riga
    private static final Earthquake AFGHANISTAN = new Earthquake("af", 5.2, "Afghanistan", 3000, 36.1, 68.75, 49, null);
    // New Caledonia, far from everything
    private static final Earthquake NEW_CALEDONIA = new Earthquake("nc", 6.6, "New Caledonia", 1000, -21.2982, 168.61, 10, null);
    private static final Earthquake TONGA = new Earthquake("to", 5.4, "Tonga", 2000, -20.5, -174.0, 10, null);

    private static final List<Earthquake> ALL = Arrays.asList(AFGHANISTAN, TONGA, NEW_CALEDONIA);
    private static final List<EarthquakeAlerts.Place> PLACES = Arrays.asList(RIGA, TASHKENT);

    @Test
    public void notifiesAboutStrongEarthquakesAnywhere() {
        List<EarthquakeAlerts.Alert> alerts = EarthquakeAlerts.select(ALL, Collections.emptySet(), PLACES, 6, 0);
        assertEquals(1, alerts.size());
        assertEquals("nc", alerts.get(0).earthquake.id);
    }

    @Test
    public void notifiesAboutEarthquakesNearThePlaces() {
        List<EarthquakeAlerts.Alert> alerts = EarthquakeAlerts.select(ALL, Collections.emptySet(), PLACES, 0, 600);
        assertEquals(1, alerts.size());
        EarthquakeAlerts.Alert alert = alerts.get(0);
        assertEquals("af", alert.earthquake.id);
        assertEquals("Tashkent", alert.nearest.name);
        assertEquals(579.7, alert.distanceKm, 0.1);
        assertTrue(EarthquakeAlerts.select(ALL, Collections.emptySet(), PLACES, 0, 500).isEmpty());
    }

    @Test
    public void combinesTheRulesOldestFirstAndSkipsNotifiedOnes() {
        List<EarthquakeAlerts.Alert> alerts = EarthquakeAlerts.select(ALL, Collections.emptySet(), PLACES, 6, 1000);
        assertEquals(2, alerts.size());
        assertEquals("nc", alerts.get(0).earthquake.id);
        assertEquals("af", alerts.get(1).earthquake.id);
        assertEquals(1, EarthquakeAlerts.select(ALL, Collections.singleton("nc"), PLACES, 6, 1000).size());
        assertTrue(EarthquakeAlerts.select(ALL, Collections.emptySet(), PLACES, 0, 0).isEmpty());
    }

    @Test
    public void worksWithoutPlaces() {
        List<EarthquakeAlerts.Alert> alerts = EarthquakeAlerts.select(ALL, Collections.emptySet(), Collections.emptyList(), 6, 1000);
        assertEquals(1, alerts.size());
        assertNull(alerts.get(0).nearest);
    }

    @Test
    public void keepsTheNewestWhenThereAreMany() {
        List<Earthquake> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            many.add(new Earthquake("e" + i, 6.5, "", i * 1000L, 0, 0, 10, null));
        }
        List<EarthquakeAlerts.Alert> alerts = EarthquakeAlerts.select(many, Collections.emptySet(), PLACES, 6, 0);
        assertEquals(EarthquakeAlerts.MAX_ALERTS, alerts.size());
        assertEquals("e3", alerts.get(0).earthquake.id);
        assertEquals("e7", alerts.get(4).earthquake.id);
    }
}
