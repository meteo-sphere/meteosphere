package org.meteosphere.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Locale;
import org.junit.Test;

public class WeatherDescriptionsTest {

    // The WMO weather codes Open-Meteo gives and the ones the Foreca symbols become (68, 69, 83)
    private static final int[] WMO_CODES = {0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 68, 69, 71, 73, 75, 77, 80, 81, 82, 83, 85, 86, 95, 96, 99};

    @Test
    public void describesEveryCodeInEveryLanguage() {
        for (int wmo : WMO_CODES) {
            for (String lang : WeatherDescriptions.LANGUAGES) {
                assertFalse(wmo + " " + lang, WeatherDescriptions.get(wmo, lang).isEmpty());
            }
        }
    }

    @Test
    public void usesTheLanguage() {
        assertEquals("Пасмурно", WeatherDescriptions.get(3, "ru"));
        assertEquals("Overcast", WeatherDescriptions.get(3, "en"));
        assertEquals("Похмуро", WeatherDescriptions.get(3, "uk"));
        assertEquals("Pochmurno", WeatherDescriptions.get(3, "pl"));
        assertEquals("Bedeckt", WeatherDescriptions.get(3, "de"));
        assertEquals("くもり", WeatherDescriptions.get(3, "ja"));
    }

    // Without the settings of the app, its language is the default locale's
    @Test
    public void describesTheWeatherInTheAppLanguage() {
        Locale locale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            Weather weather = new Weather();
            assertEquals("", weather.description());
            weather.code = 3;
            assertEquals("Bedeckt", weather.description());
            DayWeather day = new DayWeather();
            day.code = 61;
            assertEquals("Leichter Regen", day.description());
        } finally {
            Locale.setDefault(locale);
        }
    }

    @Test
    public void fallsBackToEnglishAndEmpty() {
        assertEquals("Thunderstorm", WeatherDescriptions.get(95, "ko"));
        assertEquals("", WeatherDescriptions.get(42, "ru"));
    }
}
