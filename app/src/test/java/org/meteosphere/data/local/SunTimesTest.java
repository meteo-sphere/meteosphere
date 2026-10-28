package org.meteosphere.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SunTimesTest {

    // Foreca gives 06:31:13 and 18:06:15 for Moscow on 2026-10-01
    @Test
    public void calculatesWithinMinutesOfForeca() throws Exception {
        SunTimes foreca = SunTimes.fromLocalTimes("2026-10-01", "06:31:13", "18:06:15");
        SunTimes calculated = SunTimes.calculate("2026-10-01", 55.75204, 37.61781, 180);
        assertEquals(foreca.riseMinutes, calculated.riseMinutes, 3);
        assertEquals(foreca.setMinutes, calculated.setMinutes, 3);
        // The local time read as UTC, the way the app reads the sunrise
        assertEquals(1790836273L, foreca.sunrise);
        assertEquals(391, foreca.riseMinutes);
        assertEquals(695, foreca.dayDuration);
    }

    @Test
    public void handlesPolarDayAndNight() throws Exception {
        assertEquals(1440, SunTimes.calculate("2026-06-21", 78.22, 15.65, 120).dayDuration);
        assertEquals(0, SunTimes.calculate("2026-12-21", 78.22, 15.65, 60).dayDuration);
        assertNull(SunTimes.fromLocalTimes("2026-06-21", null, null));
    }
}
