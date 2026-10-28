package org.meteosphere.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

// The expected times come from the USNO tables (aa.usno.navy.mil/api), given to the minute
public class MoonTest {

    private static final long MINUTE = 60000;

    private static final double MOSCOW_LAT = 55.7520;
    private static final double MOSCOW_LNG = 37.6178;

    // Meeus example 47.a: 1992 April 12 at 0h TD
    @Test
    public void positionMatchesMeeusExample() {
        double t = (2448724.5 - 2451545.0) / 36525;
        double[] moon = Moon.moonPosition(t);
        assertEquals(133.162655, moon[0], 0.000002);
        assertEquals(-3.229126, moon[1], 0.000002);
        assertEquals(368409.7, moon[2], 0.1);
    }

    // Meeus example 48.a: the same instant, k = 0.6786
    @Test
    public void illuminationMatchesMeeusExample() throws Exception {
        assertEquals(0.6786, Moon.illumination(date("1992-04-12") - 69000), 0.0005);
    }

    @Test
    public void principalPhasesComeWithinMinutesOfUsno() throws Exception {
        assertPhase("2026-01-03", Moon.FULL_MOON, "10:03");
        assertPhase("2026-10-10", Moon.NEW_MOON, "15:50");
        assertPhase("2026-10-18", Moon.FIRST_QUARTER, "16:12");
        assertPhase("2026-10-26", Moon.FULL_MOON, "04:12");
        assertPhase("2026-11-01", Moon.LAST_QUARTER, "20:28");
    }

    @Test
    public void daysBetweenPrincipalPhases() throws Exception {
        Moon.Day day = Moon.day(date("2026-10-01"), 180, MOSCOW_LAT, MOSCOW_LNG);
        assertEquals(Moon.WANING_GIBBOUS, day.phase);
        assertEquals(Moon.NONE, day.phaseTime);
        assertEquals(0.74, day.illumination, 0.01);
        assertTrue(day.age > 0.5 && day.age < 0.75);
        assertEquals(Moon.WANING_CRESCENT, Moon.day(date("2026-10-05"), 180, MOSCOW_LAT, MOSCOW_LNG).phase);
        assertEquals(Moon.WAXING_CRESCENT, Moon.day(date("2026-10-13"), 180, MOSCOW_LAT, MOSCOW_LNG).phase);
        assertEquals(Moon.WAXING_GIBBOUS, Moon.day(date("2026-10-22"), 180, MOSCOW_LAT, MOSCOW_LNG).phase);
    }

    @Test
    public void riseAndSetComeWithinMinutesOfUsno() throws Exception {
        assertRiseSet("2026-10-01", 180, MOSCOW_LAT, MOSCOW_LNG, "19:18", "13:32");
        assertRiseSet("2026-10-10", 180, MOSCOW_LAT, MOSCOW_LNG, "06:33", "17:07");
        // New York in winter time and Sydney in summer time
        assertRiseSet("2026-12-03", -300, 40.7128, -74.0060, "01:43", "13:15");
        assertRiseSet("2026-10-26", 660, -33.8679, 151.2073, "19:32", "05:32");
    }

    @Test
    public void handlesDaysWithoutRiseOrSet() throws Exception {
        // Moscow: the moon sets at 16:25 and rises again after midnight
        Moon.Day noRise = Moon.day(date("2026-10-05"), 180, MOSCOW_LAT, MOSCOW_LNG);
        assertEquals(Moon.NONE, noRise.rise);
        assertWithinMinutes(time("2026-10-05", "16:25"), noRise.set);
        assertFalse(noRise.alwaysUp);
        // Tromso: above the horizon all day
        Moon.Day polar = Moon.day(date("2026-07-15"), 120, 69.6492, 18.9553);
        assertEquals(Moon.NONE, polar.rise);
        assertEquals(Moon.NONE, polar.set);
        assertTrue(polar.alwaysUp);
    }

    // At a full moon the Moon is opposite the Sun: on 2026-10-26 the Sun is early in Scorpio, the Moon in Taurus.
    // At the new moon of 2026-10-10 both are in Libra.
    @Test
    public void findsTheZodiacSign() throws Exception {
        assertEquals(1, Moon.zodiacSign(time("2026-10-26", "04:12")));
        assertEquals(6, Moon.zodiacSign(time("2026-10-10", "15:50")));
    }

    @Test
    public void findsWhenTheMoonEntersTheNextSign() throws Exception {
        long start = time("2026-10-01", "00:00");
        int sign = Moon.zodiacSign(start);
        long change = Moon.nextSignChange(start);
        // Every two to three days, into the next sign
        assertTrue(change - start < 3 * 24 * 60 * MINUTE);
        assertEquals(sign, Moon.zodiacSign(change - 2 * MINUTE));
        assertEquals((sign + 1) % 12, Moon.zodiacSign(change + 2 * MINUTE));
        long next = Moon.nextSignChange(change + MINUTE);
        assertTrue(next - change > 2 * 24 * 60 * MINUTE && next - change < 3 * 24 * 60 * MINUTE);
    }

    private static void assertPhase(String date, int phase, String utcTime) throws Exception {
        Moon.Day day = Moon.day(date(date), 0, 0, 0);
        assertEquals(phase, day.phase);
        assertWithinMinutes(time(date, utcTime), day.phaseTime);
    }

    private static void assertRiseSet(String date, int utcOffsetMinutes, double lat, double lng, String rise, String set) throws Exception {
        Moon.Day day = Moon.day(date(date), utcOffsetMinutes, lat, lng);
        assertWithinMinutes(time(date, rise), day.rise);
        assertWithinMinutes(time(date, set), day.set);
    }

    // The supermoon perigee of 14 November 2016: 356 509 km at 11:23 UTC, and the apogee before it, 406 662 km
    // on 31 October at 19:30 UTC
    @Test
    public void apsides() throws Exception {
        Moon.Apsis perigee = Moon.nextApsis(time("2016-11-10", "00:00"));
        assertTrue(perigee.perigee);
        assertTrue("perigee at " + perigee.time, Math.abs(perigee.time - time("2016-11-14", "11:23")) <= 20 * MINUTE);
        assertEquals(356509, perigee.distanceKm, 30);
        Moon.Apsis apogee = Moon.nextApsis(time("2016-10-28", "00:00"));
        assertFalse(apogee.perigee);
        assertTrue("apogee at " + apogee.time, Math.abs(apogee.time - time("2016-10-31", "19:30")) <= 60 * MINUTE);
        assertEquals(406662, apogee.distanceKm, 50);
    }

    // assertEquals with a delta would round the millis to a float
    private static void assertWithinMinutes(long expected, long actual) {
        assertTrue("expected " + expected + " but was " + actual, Math.abs(actual - expected) <= 2 * MINUTE);
    }

    // 00:00 of the date read as UTC
    private static long date(String date) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(date).getTime();
    }

    private static long time(String date, String time) throws Exception {
        return date(date) + (Integer.parseInt(time.substring(0, 2)) * 60L + Integer.parseInt(time.substring(3, 5))) * MINUTE;
    }

    // Meeus example 48.a: chi = 285.0 degrees
    @Test
    public void brightLimbMatchesMeeusExample() {
        double t = (2448724.5 - 2451545.0) / 36525;
        assertEquals(285.0, Moon.normalize360(Math.toDegrees(Moon.brightLimb(t))), 0.5);
    }

    // At the first quarter, highest in the sky, its bright limb points right in the north and left in the south, the
    // sun being west of it; half of it is lit
    @Test
    public void firstQuarterIsLitRightInTheNorthAndLeftInTheSouth() throws Exception {
        long quarter = Moon.nextPhase(date("2026-10-15")).time;
        double[] north = Moon.disc(highest(quarter, MOSCOW_LAT, MOSCOW_LNG), MOSCOW_LAT, MOSCOW_LNG);
        double[] south = Moon.disc(highest(quarter, -33.87, 151.21), -33.87, 151.21);
        assertEquals(90, Math.toDegrees(north[0]), 2);
        assertEquals(-90, Moon.normalize180(Math.toDegrees(north[1])), 30);
        assertEquals(90, Moon.normalize180(Math.toDegrees(south[1])), 30);
    }

    // Three days after the new moon, low in the evening after sunset, it is lit from below, where the sun has set; in
    // Delhi, as in Moscow it sets with the sun this time of the year
    @Test
    public void youngMoonIsLitFromBelowInTheEvening() throws Exception {
        double lat = 28.61;
        double lng = 77.21;
        long day = date("2026-10-13");
        long at = -1;
        for (long time = day + 6 * 3600000L; time < day + 20 * 3600000L; time += 10 * MINUTE) {
            if (Moon.altitude(time, lat, lng) > 2 && Sun.altitude(time, lat, lng) < -2) {
                at = time;
                break;
            }
        }
        assertTrue(at > 0);
        double[] disc = Moon.disc(at, lat, lng);
        assertTrue(Math.toDegrees(disc[0]) > 120);
        assertTrue(Math.cos(disc[1]) < 0);
    }

    // The time the moon is highest at a place within half a day of the instant
    private static long highest(long around, double lat, double lng) {
        long best = around;
        for (long time = around - 12 * 3600000L; time <= around + 12 * 3600000L; time += MINUTE) {
            if (Moon.altitude(time, lat, lng) > Moon.altitude(best, lat, lng)) {
                best = time;
            }
        }
        return best;
    }
}
