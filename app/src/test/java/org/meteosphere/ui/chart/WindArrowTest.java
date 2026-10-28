package org.meteosphere.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// The wind arrow's angle by the direction the wind comes from: it points where the wind blows to
public class WindArrowTest {

    private static final float[] DEGREES = {Float.NaN, 180, 225, 270, 315, 0, 45, 90, 135};

    // 1 north to 8 north-west, clockwise: a north wind's arrow points south
    @Test
    public void eachDirection() {
        for (int direction = 1; direction <= 8; direction++) {
            assertEquals("direction " + direction, DEGREES[direction], ForecastFormat.windArrow(3, direction), 0);
        }
    }

    // 0 is a wind without a direction; a code out of the list has none either
    @Test
    public void noDirection() {
        assertTrue(Float.isNaN(ForecastFormat.windArrow(3, 0)));
        assertTrue(Float.isNaN(ForecastFormat.windArrow(3, 9)));
        assertTrue(Float.isNaN(ForecastFormat.windArrow(3, -1)));
    }

    // No wind, whatever direction the forecast gives
    @Test
    public void calm() {
        for (int direction = 0; direction <= 8; direction++) {
            assertTrue("direction " + direction, Float.isNaN(ForecastFormat.windArrow(0, direction)));
        }
    }
}
