package org.meteosphere.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

// The expected times come from the USNO tables (aa.usno.navy.mil/api), given to the minute
public class SunTest {

    private static final long MINUTE = 60000;

    @Test
    public void riseSetTwilightAndNoonComeWithinMinutesOfUsno() throws Exception {
        // Moscow
        Sun.Day moscow = Sun.day(date("2026-10-01"), 180, 55.7520, 37.6178);
        assertTime("2026-10-01", "06:32", moscow.rise);
        assertTime("2026-10-01", "18:05", moscow.set);
        assertTime("2026-10-01", "05:55", moscow.dawn);
        assertTime("2026-10-01", "18:42", moscow.dusk);
        assertTime("2026-10-01", "12:19", moscow.noon);
        assertEquals(moscow.set - moscow.rise, moscow.dayLength, 2 * MINUTE);
        // Riga at the equinox, Sydney in summer time
        Sun.Day riga = Sun.day(date("2026-03-20"), 120, 56.9496, 24.1052);
        assertTime("2026-03-20", "06:26", riga.rise);
        assertTime("2026-03-20", "18:37", riga.set);
        Sun.Day sydney = Sun.day(date("2026-12-21"), 660, -33.8679, 151.2073);
        assertTime("2026-12-21", "05:41", sydney.rise);
        assertTime("2026-12-21", "20:05", sydney.set);
        assertTime("2026-12-21", "05:11", sydney.dawn);
        assertTime("2026-12-21", "20:35", sydney.dusk);
    }

    @Test
    public void handlesPolarDayAndNight() throws Exception {
        // Tromso: the midnight sun, then the polar night with a few hours of civil twilight
        Sun.Day summer = Sun.day(date("2026-06-21"), 120, 69.6492, 18.9553);
        assertTrue(summer.polarDay);
        assertEquals(Sun.NONE, summer.rise);
        assertEquals(24 * 60 * MINUTE, summer.dayLength);
        Sun.Day winter = Sun.day(date("2026-12-21"), 60, 69.6492, 18.9553);
        assertTrue(winter.polarNight);
        assertFalse(winter.polarDay);
        assertEquals(0, winter.dayLength);
        assertTime("2026-12-21", "09:31", winter.dawn);
        assertTime("2026-12-21", "13:53", winter.dusk);
        assertTrue(winter.noonAltitude < 0);
    }

    // USNO: equinox 2026-03-20 14:46, solstice 06-21 08:24, equinox 09-23 00:05, solstice 12-21 20:50 UTC
    @Test
    public void findsTheSolsticesAndEquinoxes() throws Exception {
        Sun.Season season = Sun.nextSeason(utc("2026-01-01 00:00"));
        String[] expected = {"2026-03-20 14:46", "2026-06-21 08:24", "2026-09-23 00:05", "2026-12-21 20:50"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(i, season.season);
            assertWithinMinutes(utc(expected[i]), season.time);
            season = Sun.nextSeason(season.time + MINUTE);
        }
    }

    @Test
    public void findsTheZodiacSign() throws Exception {
        // Libra from the September equinox, Scorpio about a month later
        assertEquals(6, Sun.zodiacSign(utc("2026-10-01 00:00")));
        long change = Sun.nextSignChange(utc("2026-10-01 00:00"));
        assertEquals(7, Sun.zodiacSign(change + 2 * MINUTE));
        assertEquals(6, Sun.zodiacSign(change - 2 * MINUTE));
        assertTrue(change > utc("2026-10-22 00:00") && change < utc("2026-10-24 00:00"));
    }

    // Scorpio, Sagittarius, Capricorn at the December solstice, ... Libra at the September equinox: one after another
    @Test
    public void walksTheSignsThroughTheYear() throws Exception {
        long after = utc("2026-10-01 00:00");
        int sign = Sun.zodiacSign(after);
        long previous = after;
        for (int i = 0; i < 12; i++) {
            Sun.SignChange change = Sun.nextSign(after);
            sign = (sign + 1) % 12;
            assertEquals(sign, change.sign);
            assertEquals(sign, Sun.zodiacSign(change.time + MINUTE));
            assertEquals((sign + 11) % 12, Sun.zodiacSign(change.time - MINUTE));
            if (i > 0) {
                assertTrue(change.time - previous > 28L * 24 * 60 * MINUTE && change.time - previous < 33L * 24 * 60 * MINUTE);
            }
            previous = change.time;
            after = change.time + MINUTE;
        }
        // Capricorn starts at the December solstice
        assertEquals(Sun.nextSeason(utc("2026-10-01 00:00")).time, Sun.nextSign(utc("2026-12-01 00:00")).time);
    }

    private static void assertTime(String date, String time, long actual) throws Exception {
        assertWithinMinutes(date(date) + (Integer.parseInt(time.substring(0, 2)) * 60L + Integer.parseInt(time.substring(3, 5))) * MINUTE, actual);
    }

    private static void assertWithinMinutes(long expected, long actual) {
        assertTrue("expected " + expected + " but was " + actual, Math.abs(actual - expected) <= 2 * MINUTE);
    }

    // 00:00 of the date read as UTC
    private static long date(String date) throws Exception {
        return utc(date + " 00:00");
    }

    private static long utc(String time) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(time).getTime();
    }
}
