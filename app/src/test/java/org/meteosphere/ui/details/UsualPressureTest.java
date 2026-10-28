package org.meteosphere.ui.details;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UsualPressureTest {

    // At sea level the dial keeps 720 to 790 mmHg
    @Test
    public void seaLevel() {
        assertEquals(760, TilesBuilder.usualPressure(0), 1e-9);
    }

    // Higher up the place's pressure is lower: Moscow's 155 m, a town's 667 m, Mexico City's 2240 m
    @Test
    public void fallsWithHeight() {
        assertEquals(746.1, TilesBuilder.usualPressure(155), 0.1);
        assertEquals(701.8, TilesBuilder.usualPressure(667), 0.1);
        assertEquals(578.7, TilesBuilder.usualPressure(2240), 0.1);
    }
}
