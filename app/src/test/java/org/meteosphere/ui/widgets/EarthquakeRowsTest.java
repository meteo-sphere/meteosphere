package org.meteosphere.ui.widgets;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// How many earthquakes the widget lists in the height the launcher gives it, in dp
public class EarthquakeRowsTest {

    // Two cells: 202 dp on a Samsung with Android 17, 210 dp on a Pixel
    @Test
    public void twoCells() {
        assertEquals(3, EarthquakeWidget.rowsFor(202));
        assertEquals(3, EarthquakeWidget.rowsFor(210));
    }

    @Test
    public void lowerAndTaller() {
        assertEquals(2, EarthquakeWidget.rowsFor(167));
        assertEquals(4, EarthquakeWidget.rowsFor(246));
        assertEquals(3, EarthquakeWidget.rowsFor(245));
    }

    // At least one, at most eight
    @Test
    public void bounds() {
        assertEquals(1, EarthquakeWidget.rowsFor(40));
        assertEquals(8, EarthquakeWidget.rowsFor(1000));
    }
}
