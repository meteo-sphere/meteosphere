package org.meteosphere.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class MoonPhaseAlertsTest {

    private static final TimeZone MOSCOW = TimeZone.getTimeZone("Europe/Moscow");
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final List<Integer> ALL = Arrays.asList(Moon.NEW_MOON, Moon.FIRST_QUARTER, Moon.FULL_MOON, Moon.LAST_QUARTER);

    @Test
    public void announcesDaytimePhasesInTheMorning() throws Exception {
        assertEquals(time("2026-10-26 06:00"), QuietHours.notifyTime(time("2026-10-26 07:12"), MOSCOW));
        assertEquals(time("2026-10-26 06:00"), QuietHours.notifyTime(time("2026-10-26 06:00"), MOSCOW));
        assertEquals(time("2026-10-26 06:00"), QuietHours.notifyTime(time("2026-10-26 22:59"), MOSCOW));
    }

    @Test
    public void announcesNightPhasesTheEveningBefore() throws Exception {
        assertEquals(time("2026-10-25 20:00"), QuietHours.notifyTime(time("2026-10-25 23:00"), MOSCOW));
        assertEquals(time("2026-10-25 20:00"), QuietHours.notifyTime(time("2026-10-25 23:40"), MOSCOW));
        assertEquals(time("2026-10-25 20:00"), QuietHours.notifyTime(time("2026-10-26 02:12"), MOSCOW));
        assertEquals(time("2026-10-25 20:00"), QuietHours.notifyTime(time("2026-10-26 05:59"), MOSCOW));
    }

    @Test
    public void knowsTheNightAndTheNextMorning() throws Exception {
        assertTrue(QuietHours.isNight(time("2026-10-25 23:30"), MOSCOW));
        assertTrue(QuietHours.isNight(time("2026-10-26 03:00"), MOSCOW));
        assertFalse(QuietHours.isNight(time("2026-10-26 06:00"), MOSCOW));
        assertFalse(QuietHours.isNight(time("2026-10-26 22:59"), MOSCOW));
        assertEquals(time("2026-10-26 06:00"), QuietHours.nextMorning(time("2026-10-25 23:30"), MOSCOW));
        assertEquals(time("2026-10-26 06:00"), QuietHours.nextMorning(time("2026-10-26 03:00"), MOSCOW));
        assertEquals(time("2026-10-27 06:00"), QuietHours.nextMorning(time("2026-10-26 06:00"), MOSCOW));
    }

    // USNO: new moon 2026-10-10 15:50, first quarter 10-18 16:12, full moon 10-26 04:12, last quarter 11-01 20:28 UTC
    @Test
    public void findsTheNextWantedPhase() throws Exception {
        long start = utc("2026-10-10 16:00");
        assertPhase(Moon.FIRST_QUARTER, "2026-10-18 16:12", MoonPhaseAlerts.nextPhase(start, ALL));
        assertPhase(Moon.FULL_MOON, "2026-10-26 04:12", MoonPhaseAlerts.nextPhase(start, Collections.singletonList(Moon.FULL_MOON)));
        assertPhase(Moon.LAST_QUARTER, "2026-11-01 20:28", MoonPhaseAlerts.nextPhase(utc("2026-10-26 04:30"), ALL));
        assertNull(MoonPhaseAlerts.nextPhase(start, Collections.emptyList()));
    }

    @Test
    public void nextPhaseWalksTheYearWithUsno() throws Exception {
        // The phases of January 2026 one after another
        String[][] usno = {
                {"4", "2026-01-03 10:03"}, {"6", "2026-01-10 15:48"}, {"0", "2026-01-18 19:52"},
                {"2", "2026-01-26 04:47"}, {"4", "2026-02-01 22:09"}};
        long after = utc("2026-01-01 00:00");
        for (String[] expected : usno) {
            Moon.Phase phase = Moon.nextPhase(after);
            assertPhase(Integer.parseInt(expected[0]), expected[1], phase);
            after = phase.time + 60000;
        }
    }

    private static void assertPhase(int expectedPhase, String expectedUtc, Moon.Phase phase) throws Exception {
        assertEquals(expectedPhase, phase.phase);
        long expected = utc(expectedUtc);
        assertTrue("expected " + expectedUtc + " but was " + phase.time, Math.abs(phase.time - expected) <= 2 * 60000);
    }

    private static long time(String localTime) throws Exception {
        return parse(localTime, MOSCOW);
    }

    private static long utc(String time) throws Exception {
        return parse(time, UTC);
    }

    private static long parse(String time, TimeZone timeZone) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(timeZone);
        return format.parse(time).getTime();
    }
}
