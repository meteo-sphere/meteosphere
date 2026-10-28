package org.meteosphere.background;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.junit.Test;

// When the alarm draws the weather notification anew: half a minute after the first half past an hour by the place's
// clock after it was last drawn, now when that is past
public class HalfHourRedrawTimeTest {

    private static final String MOSCOW = "Europe/Moscow";
    private static final String KOLKATA = "Asia/Kolkata";
    private static final String KATHMANDU = "Asia/Kathmandu";
    private static final String BERLIN = "Europe/Berlin";

    // The moment the zone's clock shows the time; a time it shows twice, the first one
    private static long at(String zone, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(2026, month, day, hour, minute, second, 0, ZoneId.of(zone)).toInstant().toEpochMilli();
    }

    // Moscow's clock on the day
    private static long at(int hour, int minute, int second) {
        return at(MOSCOW, 10, 23, hour, minute, second);
    }

    private static long time(long drawn, long now, String zone) {
        return NotificationUpdater.redrawTime(drawn, now, TimeZone.getTimeZone(zone));
    }

    @Test
    public void nextHalfPast() {
        assertEquals(at(19, 30, 30), time(at(19, 10, 0), at(19, 12, 0), MOSCOW));
        assertEquals(at(20, 30, 30), time(at(19, 40, 0), at(19, 41, 0), MOSCOW));
        // After the alarm's own drawing, the half past after
        assertEquals(at(20, 30, 30), time(at(19, 30, 30), at(19, 30, 31), MOSCOW));
    }

    // A half past not drawn yet: at once
    @Test
    public void pastTarget() {
        assertEquals(at(19, 50, 0), time(at(19, 10, 0), at(19, 50, 0), MOSCOW));
        // Set again by a load while the alarm still waits in its window: kept on that half past, not moved past it
        assertEquals(at(20, 34, 0), time(at(19, 30, 31), at(20, 34, 0), MOSCOW));
    }

    // Drawn in the half minute after a half past: the app's clock may still show the hour before
    @Test
    public void halfMinuteAfterHalfPast() {
        assertEquals(at(19, 30, 30), time(at(19, 30, 10), at(19, 30, 12), MOSCOW));
    }

    // The clock set back: as drawn now
    @Test
    public void drawnInTheFuture() {
        assertEquals(at(19, 30, 30), time(at(22, 0, 0), at(19, 10, 0), MOSCOW));
    }

    // Never drawn: at once
    @Test
    public void noneKept() {
        assertEquals(at(19, 10, 0), time(0, at(19, 10, 0), MOSCOW));
    }

    // +5:30 and +5:45: half past by their own clocks, a whole hour and a quarter past by Moscow's
    @Test
    public void zonesOffTheHour() {
        assertEquals(at(KOLKATA, 10, 23, 20, 30, 30), time(at(KOLKATA, 10, 23, 20, 5, 0), at(KOLKATA, 10, 23, 20, 6, 0), KOLKATA));
        assertEquals(at(21, 0, 30), time(at(20, 40, 0), at(20, 41, 0), KOLKATA));
        assertEquals(at(KATHMANDU, 10, 23, 20, 30, 30), time(at(KATHMANDU, 10, 23, 20, 5, 0), at(KATHMANDU, 10, 23, 20, 6, 0), KATHMANDU));
        assertEquals(at(20, 45, 30), time(at(20, 20, 0), at(20, 21, 0), KATHMANDU));
    }

    // The place's clock moved on or back: the next half past on its face
    @Test
    public void daylightSaving() {
        // 1:40 winter time, then 3:00 summer time for 2:00: no 2:30 that night, 3:30 next
        assertEquals(at(BERLIN, 3, 29, 3, 30, 30), time(at(BERLIN, 3, 29, 1, 40, 0), at(BERLIN, 3, 29, 1, 41, 0), BERLIN));
        // 2:40 summer time, then 2:00 winter time for 3:00: 2:30 again, in winter time
        long halfPastTwoInWinterTime = ZonedDateTime.of(2026, 10, 25, 2, 30, 30, 0, ZoneId.of(BERLIN)).withLaterOffsetAtOverlap()
                .toInstant().toEpochMilli();
        assertEquals(halfPastTwoInWinterTime, time(at(BERLIN, 10, 25, 2, 40, 0), at(BERLIN, 10, 25, 2, 41, 0), BERLIN));
    }
}
