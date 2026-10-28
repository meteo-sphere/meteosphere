package org.meteosphere.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class EclipseTest {

    private static final long MINUTE = 60000;

    @Test
    public void readsTheNasaCatalog() throws Exception {
        List<Eclipse> eclipses;
        try (Reader reader = new FileReader("src/main/assets/eclipses.csv")) {
            eclipses = Eclipse.parse(reader);
        }
        assertEquals(343, eclipses.size());
        // After 2026-10-01: the annular eclipse of 2027-02-06 at 16:00:48 TD, then the penumbral lunar one of 02-20
        List<Eclipse> upcoming = Eclipse.upcoming(eclipses, utc("2026-10-01 00:00"), 3);
        Eclipse annular = upcoming.get(0);
        assertTrue(annular.solar);
        assertEquals(Eclipse.ANNULAR, annular.type);
        assertEquals(utc("2027-02-06 15:59:32"), annular.greatest);
        Eclipse penumbral = upcoming.get(1);
        assertFalse(penumbral.solar);
        assertEquals(Eclipse.PENUMBRAL, penumbral.type);
        assertEquals(241 * MINUTE, penumbral.penumbralDuration);
        assertEquals(3, upcoming.size());
    }

    // Totality began at 17:19 UTC in Madras, Oregon on 2017-08-21 and at 18:40 UTC in Dallas on 2024-04-08
    @Test
    public void findsTotalityWhereItWasSeen() throws Exception {
        Eclipse.Local madras = solar("S,2017-08-21T18:25:32,T,1.0306").local(44.6335, -121.1295);
        assertTrue(madras.visible);
        assertEquals(1, madras.obscuration, 0.001);
        assertWithin(utc("2017-08-21 17:20"), madras.max, 2);
        Eclipse.Local dallas = solar("S,2024-04-08T18:16:12,T,1.0566").local(32.7767, -96.7970);
        assertEquals(1, dallas.obscuration, 0.001);
        assertWithin(utc("2024-04-08 18:42"), dallas.max, 2);
    }

    // London saw about 85% of the Sun covered at 09:31 UTC on 2015-03-20; Sydney was in the night on 2026-08-12
    @Test
    public void findsPartialEclipsesAndWhereTheyAreNotSeen() throws Exception {
        Eclipse.Local london = solar("S,2015-03-20T09:45:39,T,1.0445").local(51.5074, -0.1278);
        assertTrue(london.visible);
        assertEquals(0.85, london.obscuration, 0.03);
        assertWithin(utc("2015-03-20 09:31"), london.max, 2);
        assertTrue(london.start < london.max && london.max < london.end);
        assertFalse(solar("S,2026-08-12T17:45:51,T,1.0386").local(-33.8679, 151.2073).visible);
    }

    // The total lunar eclipse of 2025-03-14 at 06:58 UTC: high in the night of New York, set in the morning of Moscow
    @Test
    public void findsWhereLunarEclipsesAreSeen() throws Exception {
        Eclipse lunar = parseOne("L,2025-03-14T06:58:41,T,1.1784,362.6,218.3,65.4");
        Eclipse.Local newYork = lunar.local(40.7128, -74.0060);
        assertTrue(newYork.visible);
        assertWithin(utc("2025-03-14 06:59"), newYork.max, 1);
        assertFalse(lunar.local(55.7520, 37.6178).visible);
    }

    private static Eclipse solar(String line) throws Exception {
        return parseOne(line);
    }

    private static Eclipse parseOne(String line) throws Exception {
        return Eclipse.parse(new StringReader(line)).get(0);
    }

    private static void assertWithin(long expected, long actual, int minutes) {
        assertTrue("expected " + expected + " but was " + actual, Math.abs(actual - expected) <= minutes * MINUTE);
    }

    private static long utc(String time) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat(time.length() > 16 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(time).getTime();
    }
}
