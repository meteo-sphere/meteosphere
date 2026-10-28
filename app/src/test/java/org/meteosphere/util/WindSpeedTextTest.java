package org.meteosphere.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The speeds of a forecast heading's wind, from the weakest to the strongest in the unit
public class WindSpeedTextTest {

    @Test
    public void range() {
        assertEquals("1–3", WindText.speedText(1, 3, Units.METRES_PER_SECOND));
        // 1.94 and 3.89 knots
        assertEquals("2–4", WindText.speedText(1, 2, Units.KNOTS));
        assertEquals("0–18", WindText.speedText(0, 5, Units.KM_PER_HOUR));
    }

    // One number when both are the same in the unit
    @Test
    public void same() {
        assertEquals("3", WindText.speedText(3, 3, Units.METRES_PER_SECOND));
        assertEquals("4", WindText.speedText(1, 1, Units.KM_PER_HOUR));
    }
}
