package org.meteosphere.ui.icons;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The temperatures' colours: the scale's own at its points, blended in whole numbers between them, the ends' beyond
public class TemperatureColorsTest {

    @Test
    public void atThePoints() {
        int[] celsius = {40, 30, 20, 10, 2, 1, 0, -1, -2, -10, -20, -30, -40};
        int[] colors = {0xFFFF0000, 0xFFFF7800, 0xFFFFD300, 0xFF66D915, 0xFFAAE2B8, 0xFFBFD9CF, 0xFFC3D2D6, 0xFFABD3E0, 0xFF95D6EE,
                0xFF20C5FF, 0xFF1045FF, 0xFF6700F9, 0xFF5600AF};
        for (int i = 0; i < celsius.length; i++) {
            assertEquals(celsius[i] + " °C", colors[i], TemperatureColors.of(celsius[i]));
        }
    }

    // From the colder point by the share of the way to the warmer one, the fractions cut towards zero
    @Test
    public void betweenThePoints() {
        // Green from 0x78 down to 0: 120 - 600 / 10 and 120 - 360 / 10
        assertEquals(0xFFFF3C00, TemperatureColors.of(35));
        assertEquals(0xFFFF5400, TemperatureColors.of(33));
        // Red 102 + 76.5 and blue 21 - 10.5: 178, not 179, and 11, not 10
        assertEquals(0xFFB2D60B, TemperatureColors.of(15));
        // Five eighths of the way from -10 to -2: 32 + 73.1, 197 + 10.6, 255 - 10.6
        assertEquals(0xFF69CFF5, TemperatureColors.of(-5));
    }

    @Test
    public void beyondTheEnds() {
        assertEquals(0xFFFF0000, TemperatureColors.of(41));
        assertEquals(0xFFFF0000, TemperatureColors.of(60));
        assertEquals(0xFF5600AF, TemperatureColors.of(-41));
        assertEquals(0xFF5600AF, TemperatureColors.of(-90));
    }
}
