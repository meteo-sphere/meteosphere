package org.meteosphere.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The units and the clock the app's language gives while the user hasn't chosen them
public class LanguageUnitsTest {

    // The distance, temperature, wind, pressure and precipitation units and the 24-hour clock as one text
    private static String units(String language) {
        AppSettings.LanguageUnits units = AppSettings.languageUnits(language);
        return units.distance + " " + units.temperature + " " + units.windSpeed + " " + units.pressure + " " + units.precipitation
                + " " + units.is24Hour;
    }

    // km, °C, m/s, mmHg, mm
    @Test
    public void russian() {
        assertEquals("0 0 0 0 0 true", units("ru"));
        assertEquals("0 0 0 0 0 true", units("uk"));
        assertEquals("0 0 0 0 0 true", units("pl"));
    }

    // Miles, °F, mph, inches of mercury, inches and the 12-hour clock
    @Test
    public void english() {
        assertEquals("1 1 2 3 1 false", units("en"));
    }

    // km, °C, km/h, hPa, mm
    @Test
    public void german() {
        assertEquals("0 0 1 1 0 true", units("de"));
    }

    // km, °C, m/s, hPa, mm, in Chinese however it is written
    @Test
    public void chinese() {
        assertEquals("0 0 0 1 0 true", units("zh-TW"));
        assertEquals("0 0 0 1 0 true", units("zh-Hant"));
        assertEquals("0 0 0 1 0 true", units("zh"));
    }

    @Test
    public void japanese() {
        assertEquals("0 0 0 1 0 true", units("ja"));
    }
}
