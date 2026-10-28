package org.meteosphere.background;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

// When the alarm draws the weather widgets anew: half a minute after the first start of an hour after they were last all
// drawn, now when that is past
public class HourRedrawTimeTest {

    private static final String MOSCOW = "Europe/Moscow";
    private static final String KOLKATA = "Asia/Kolkata";

    // Moscow's clock on the day
    private static long at(int hour, int minute, int second) {
        return ZonedDateTime.of(2026, 10, 23, hour, minute, second, 0, ZoneId.of(MOSCOW)).toInstant().toEpochMilli();
    }

    private static long time(long drawn, long now, String... places) {
        List<TimeZone> zones = new ArrayList<>();
        for (String place : places) {
            zones.add(TimeZone.getTimeZone(place));
        }
        return WidgetUpdater.hourRedrawTime(drawn, now, TimeZone.getTimeZone(MOSCOW), zones);
    }

    @Test
    public void nextHour() {
        assertEquals(at(20, 0, 30), time(at(19, 10, 0), at(19, 50, 0)));
        // After the alarm's own drawing, the hour after
        assertEquals(at(21, 0, 30), time(at(20, 0, 30), at(20, 0, 31)));
    }

    // An hour not drawn yet: at once
    @Test
    public void pastTarget() {
        assertEquals(at(19, 50, 0), time(at(18, 10, 0), at(19, 50, 0)));
        // Set again by a tick while the hour's alarm still waits in its window: kept on that hour, not moved past it
        assertEquals(at(20, 4, 0), time(at(19, 0, 31), at(20, 4, 0)));
    }

    // Set again in the half minute after an hour starts, as by the tick at its start: that hour's still
    @Test
    public void halfMinuteAfterTheHour() {
        assertEquals(at(20, 0, 30), time(at(19, 0, 31), at(20, 0, 5)));
        // Drawn then: the app's clock may still show the hour before
        assertEquals(at(20, 0, 30), time(at(20, 0, 10), at(20, 0, 12)));
    }

    // The clock set back: as drawn now
    @Test
    public void drawnInTheFuture() {
        assertEquals(at(20, 0, 30), time(at(22, 0, 0), at(19, 50, 0)));
    }

    // Never drawn: at once
    @Test
    public void noneKept() {
        assertEquals(at(19, 50, 0), time(0, at(19, 50, 0)));
        assertEquals(at(19, 50, 0), time(0, at(19, 50, 0), KOLKATA));
    }

    @Test
    public void placeHourFirst() {
        assertEquals(at(20, 30, 30), time(at(20, 5, 0), at(20, 6, 0), KOLKATA));
        assertEquals(at(20, 0, 30), time(at(19, 52, 0), at(19, 53, 0), KOLKATA));
    }
}
