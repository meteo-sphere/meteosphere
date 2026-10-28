package org.meteosphere.data.model;

import static org.junit.Assert.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class PlaceTimeZoneTest {

    // Riga leaves summer time on the last Sunday of October 2026, the 25th
    @Test
    public void followsDaylightSavingTimeOfTheTimeZone() throws Exception {
        Place riga = new Place();
        riga.utcOffsetMinutes = 180;
        riga.timeZoneId = "Europe/Riga";
        assertEquals(180, riga.offsetMinutesOfDay(date("2026-10-24")));
        assertEquals(120, riga.offsetMinutesOfDay(date("2026-10-25")));
        assertEquals(120, riga.offsetMinutesOfDay(date("2027-02-06")));
        // An eclipse at 15:59 UTC on 2027-02-06 is at 17:59 in Riga
        assertEquals(date("2027-02-06") + (17 * 60 + 59) * 60000L, riga.toLocal(date("2027-02-06") + (15 * 60 + 59) * 60000L));
    }

    @Test
    public void keepsTheCurrentOffsetWithoutATimeZone() throws Exception {
        Place old = new Place();
        old.utcOffsetMinutes = 180;
        assertEquals(180, old.offsetMinutesOfDay(date("2027-02-06")));
    }

    // The offset kept from the last forecast may be out by an hour since: now at the place goes by the time zone
    @Test
    public void tellsNowByTheTimeZoneRatherThanTheKeptOffset() {
        Place riga = new Place();
        riga.timeZoneId = "Europe/Riga";
        riga.utcOffsetMinutes = TimeZone.getTimeZone("Europe/Riga").getOffset(System.currentTimeMillis()) / 60000 + 60;
        long now = System.currentTimeMillis();
        long expected = now + TimeZone.getTimeZone("Europe/Riga").getOffset(now);
        assertEquals(expected, riga.localNow().getTimeInMillis(), 2000);
    }

    @Test
    public void tellsNowByTheKeptOffsetWithoutATimeZone() {
        Place old = new Place();
        old.utcOffsetMinutes = 90;
        long expected = System.currentTimeMillis() + 90 * 60000L;
        assertEquals(expected, old.localNow().getTimeInMillis(), 2000);
    }

    private static long date(String date) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(date).getTime();
    }
}
