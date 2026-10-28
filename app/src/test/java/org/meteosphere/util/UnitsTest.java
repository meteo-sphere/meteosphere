package org.meteosphere.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

// The kept values in the settings' units, computed as the screens have always shown them
public class UnitsTest {

    private static final int UNKNOWN = 9;

    // Fahrenheit rounded to the nearest whole degree, -0.4 to 0; Celsius and an unknown unit as kept
    @Test
    public void temperature() {
        assertEquals(32, Units.temperature(0, Units.FAHRENHEIT));
        assertEquals(212, Units.temperature(100, Units.FAHRENHEIT));
        assertEquals(-40, Units.temperature(-40, Units.FAHRENHEIT));
        // 98.6, 33.8, 30.2, -0.4 and 1.4
        assertEquals(99, Units.temperature(37, Units.FAHRENHEIT));
        assertEquals(34, Units.temperature(1, Units.FAHRENHEIT));
        assertEquals(30, Units.temperature(-1, Units.FAHRENHEIT));
        assertEquals(0, Units.temperature(-18, Units.FAHRENHEIT));
        assertEquals(1, Units.temperature(-17, Units.FAHRENHEIT));
        assertEquals(-5, Units.temperature(-5, Units.CELSIUS));
        assertEquals(-5, Units.temperature(-5, UNKNOWN));
    }

    @Test
    public void windSpeedFactor() {
        assertEquals(1f, Units.windFactor(Units.METRES_PER_SECOND), 0);
        assertEquals(3.6f, Units.windFactor(Units.KM_PER_HOUR), 0);
        assertEquals(2.2369363f, Units.windFactor(Units.MILES_PER_HOUR), 0);
        assertEquals(1.9438444f, Units.windFactor(Units.KNOTS), 0);
        assertEquals(1f, Units.windFactor(UNKNOWN), 0);
    }

    // Rounded to the nearest whole number of the unit
    @Test
    public void windSpeed() {
        assertEquals(0, Units.windSpeed(0, Units.KM_PER_HOUR));
        assertEquals(7, Units.windSpeed(7, Units.METRES_PER_SECOND));
        assertEquals(7, Units.windSpeed(7, UNKNOWN));
        // 3.6, 18 and 36
        assertEquals(4, Units.windSpeed(1, Units.KM_PER_HOUR));
        assertEquals(18, Units.windSpeed(5, Units.KM_PER_HOUR));
        assertEquals(36, Units.windSpeed(10, Units.KM_PER_HOUR));
        // 2.24, 6.71 and 22.37
        assertEquals(2, Units.windSpeed(1, Units.MILES_PER_HOUR));
        assertEquals(7, Units.windSpeed(3, Units.MILES_PER_HOUR));
        assertEquals(22, Units.windSpeed(10, Units.MILES_PER_HOUR));
        // 1.94, 9.72 and 19.44
        assertEquals(2, Units.windSpeed(1, Units.KNOTS));
        assertEquals(10, Units.windSpeed(5, Units.KNOTS));
        assertEquals(19, Units.windSpeed(10, Units.KNOTS));
    }

    // Whole hectopascals and millibars, hundredths of inches; millimetres and an unknown unit as kept
    @Test
    public void pressure() {
        assertEquals(760f, Units.pressure(760, Units.MM_HG), 0);
        assertEquals(760f, Units.pressure(760, UNKNOWN), 0);
        // 1013.33 and 1000
        assertEquals(1013f, Units.pressure(760, Units.HECTOPASCALS), 0);
        assertEquals(1013f, Units.pressure(760, Units.MILLIBARS), 0);
        assertEquals(1000f, Units.pressure(750, Units.HECTOPASCALS), 0);
        // 29.921 and 29.528
        assertEquals(29.92f, Units.pressure(760, Units.INCHES_HG), 0);
        assertEquals(29.53f, Units.pressure(750, Units.INCHES_HG), 0);
        assertEquals(0f, Units.pressure(0, Units.INCHES_HG), 0);
        // 127 mm are 5 inches, to the float's precision
        assertEquals(5f, Units.pressure(127, Units.INCHES_HG), 0);
    }

    // The fraction cut off, not rounded, below zero towards it too
    @Test
    public void distance() {
        assertEquals(9, Units.distance(9.99f, Units.KILOMETRES));
        assertEquals(-1, Units.distance(-1.5f, Units.KILOMETRES));
        assertEquals(9, Units.distance(9.99f, UNKNOWN));
        assertEquals(0, Units.distance(0f, Units.MILES));
        // 62.14 and 0.994
        assertEquals(62, Units.distance(100f, Units.MILES));
        assertEquals(0, Units.distance(1.6f, Units.MILES));
        assertEquals(1, Units.distance(1.7f, Units.MILES));
    }

    // Inches by the float 25.4, so that 25.4 mm come a hair over an inch
    @Test
    public void precipitation() {
        assertEquals(0.4, Units.precipitation(0.4, Units.MILLIMETRES), 0);
        assertEquals(0.4, Units.precipitation(0.4, UNKNOWN), 0);
        assertEquals(0, Units.precipitation(0, Units.INCHES), 0);
        assertEquals(25.4 / 25.399999618530273, Units.precipitation(25.4, Units.INCHES), 0);
        assertNotEquals(1.0, Units.precipitation(25.4, Units.INCHES), 0);
        assertEquals(0.5 / 25.399999618530273, Units.precipitation(0.5, Units.INCHES), 0);
    }

    // The Celsius style has a plus above zero, the other none; both the minus sign below
    @Test
    public void temperatureText() {
        assertEquals("0", Units.temperatureText(0, 0));
        assertEquals("0", Units.temperatureText(0, 1));
        assertEquals("+5", Units.temperatureText(5, 0));
        assertEquals("5", Units.temperatureText(5, 1));
        assertEquals("−5", Units.temperatureText(-5, 0));
        assertEquals("−12", Units.temperatureText(-12, 1));
    }
}
