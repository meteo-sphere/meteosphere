package org.meteosphere.background;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

// When the weather widgets are next drawn anew: the earliest start of an hour by the phone's clock or the places'
public class NextHourStartTest {

    private static final String MOSCOW = "Europe/Moscow";
    private static final String AMSTERDAM = "Europe/Amsterdam";
    private static final String KOLKATA = "Asia/Kolkata";
    private static final String KATHMANDU = "Asia/Kathmandu";
    private static final String BERLIN = "Europe/Berlin";
    private static final String LORD_HOWE = "Australia/Lord_Howe";

    // The moment the zone's clock shows the time; a time it shows twice, the first one
    private static long at(String zone, int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli();
    }

    private static long next(long now, String phone, String... places) {
        List<TimeZone> zones = new ArrayList<>();
        for (String place : places) {
            zones.add(TimeZone.getTimeZone(place));
        }
        return WidgetUpdater.nextHourStart(now, TimeZone.getTimeZone(phone), zones);
    }

    // Amsterdam's hours start with Moscow's, in winter time and in summer time; Kolkata's half an hour later
    @Test
    public void phoneHourFirst() {
        assertEquals(at(MOSCOW, 2026, 10, 23, 20, 0), next(at(MOSCOW, 2026, 10, 23, 19, 52), MOSCOW, AMSTERDAM, KOLKATA));
        assertEquals(at(MOSCOW, 2026, 7, 1, 20, 0), next(at(MOSCOW, 2026, 7, 1, 19, 52), MOSCOW, AMSTERDAM, KOLKATA));
    }

    @Test
    public void placeHourFirst() {
        assertEquals(at(MOSCOW, 2026, 10, 23, 20, 30), next(at(MOSCOW, 2026, 10, 23, 20, 5), MOSCOW, KOLKATA));
        // +5:45
        assertEquals(at(MOSCOW, 2026, 10, 23, 20, 15), next(at(MOSCOW, 2026, 10, 23, 20, 5), MOSCOW, KATHMANDU));
        assertEquals(at(MOSCOW, 2026, 10, 23, 20, 15), next(at(MOSCOW, 2026, 10, 23, 20, 5), MOSCOW, KOLKATA, KATHMANDU, AMSTERDAM));
    }

    @Test
    public void phoneOnly() {
        assertEquals(at(KATHMANDU, 2026, 10, 23, 23, 0), next(at(KATHMANDU, 2026, 10, 23, 22, 50), KATHMANDU));
        assertEquals(at(KOLKATA, 2026, 10, 23, 13, 0), next(at(KOLKATA, 2026, 10, 23, 12, 1), KOLKATA));
    }

    // Strictly after the moment: at the start of an hour, the next one
    @Test
    public void atTheHour() {
        assertEquals(at(MOSCOW, 2026, 10, 23, 21, 0), next(at(MOSCOW, 2026, 10, 23, 20, 0), MOSCOW));
        assertEquals(at(MOSCOW, 2026, 10, 23, 20, 0), next(at(MOSCOW, 2026, 10, 23, 20, 0) - 1, MOSCOW));
    }

    // The phone's clock moved on or back: the next hour on its face
    @Test
    public void phoneDaylightSaving() {
        // 1:50 winter time, then 3:00 summer time for 2:00
        assertEquals(at(BERLIN, 2026, 3, 29, 3, 0), next(at(BERLIN, 2026, 3, 29, 1, 50), BERLIN));
        // 2:50 summer time, then 2:00 winter time for 3:00
        long twoInWinterTime = ZonedDateTime.of(2026, 10, 25, 2, 0, 0, 0, ZoneId.of(BERLIN)).withLaterOffsetAtOverlap()
                .toInstant().toEpochMilli();
        assertEquals(twoInWinterTime, next(at(BERLIN, 2026, 10, 25, 2, 50), BERLIN));
        // Lord Howe's moves half an hour: 1:50 winter time, then 2:30 summer time for 2:00, and 3:00 the hour after
        assertEquals(at(LORD_HOWE, 2026, 10, 4, 3, 0), next(at(LORD_HOWE, 2026, 10, 4, 1, 50), LORD_HOWE));
        // 1:50 summer time, then 1:30 winter time for 2:00, and 2:00 the hour after
        assertEquals(at(LORD_HOWE, 2026, 4, 5, 2, 0), next(at(LORD_HOWE, 2026, 4, 5, 1, 50), LORD_HOWE));
    }

    // A place's clock moved half an hour, the phone's hours a quarter past by UTC: taken by the hour the place's clock
    // shows after the move, not the one it would have shown before
    @Test
    public void placeDaylightSaving() {
        // Lord Howe's 3:00, a quarter of an hour before Kathmandu's 22:00; its 2:00 by the offset before is no hour
        assertEquals(at(LORD_HOWE, 2026, 10, 4, 3, 0), next(at(LORD_HOWE, 2026, 10, 4, 1, 50), KATHMANDU, LORD_HOWE));
        // Kathmandu's 21:00, a quarter of an hour before Lord Howe's 2:00 winter time; its 2:00 summer time never comes
        assertEquals(at(KATHMANDU, 2026, 4, 4, 21, 0), next(at(LORD_HOWE, 2026, 4, 5, 1, 50), KATHMANDU, LORD_HOWE));
    }
}
