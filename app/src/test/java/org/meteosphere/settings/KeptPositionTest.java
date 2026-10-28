package org.meteosphere.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.meteosphere.util.AppUtils;

// The device's position kept in the preferences comes back as it was saved
public class KeptPositionTest {

    private static double[] readBack(double latitude, double longitude, String provider) {
        return AppSettings.positionCoordinates(AppSettings.positionText(latitude, longitude, provider));
    }

    // Every digit saved comes back
    @Test
    public void readBackAsSaved() {
        double[] coordinates = readBack(56.951234567, 24.105, "gps");
        assertEquals(56.951234567, coordinates[0], 0);
        assertEquals(24.105, coordinates[1], 0);
        coordinates = readBack(-33.8688, 151.2093, "network");
        assertEquals(-33.8688, coordinates[0], 0);
        assertEquals(151.2093, coordinates[1], 0);
    }

    // At a hundredth's half the position kept rounds as the device's own; read as a float it rounded a hundredth lower
    @Test
    public void roundsAsTheDevicesOwn() {
        assertEquals(24.11, AppUtils.roundToHundredths(24.105), 0);
        assertEquals(AppUtils.roundToHundredths(24.105), AppUtils.roundToHundredths(readBack(56.95, 24.105, "gps")[1]), 0);
        assertNotEquals(AppUtils.roundToHundredths(24.105), AppUtils.roundToHundredths(Float.parseFloat("24.105")), 0);
    }

    // None kept, and one kept without its provider
    @Test
    public void textWithoutPosition() {
        assertNull(AppSettings.positionCoordinates(""));
        double[] coordinates = AppSettings.positionCoordinates("56.95 24.105");
        assertEquals(56.95, coordinates[0], 0);
        assertEquals(24.105, coordinates[1], 0);
    }
}
