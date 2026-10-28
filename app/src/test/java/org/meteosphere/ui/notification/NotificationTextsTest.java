package org.meteosphere.ui.notification;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

// The weather notification's line of the weather under the place's name, and its wind
public class NotificationTextsTest {

    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");
    private static final Locale GERMAN = Locale.GERMAN;
    private static final String[] DIRECTIONS = {"Calm", "N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final String[] DIRECTIONS_RU = {"Безветренно", "С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
    private static final String[] DIRECTIONS_DE = {"Windstill", "N", "NO", "O", "SO", "S", "SW", "W", "NW"};

    private static String wind(int speed, int sector) {
        return WeatherNotification.windText(DIRECTIONS, "Wind %1$s %2$s from %3$s", "Wind %1$s %2$s", speed, sector,
                String.valueOf(speed), "m/s", ENGLISH);
    }

    @Test
    public void descriptionAndWind() {
        assertEquals("Cloudy, wind 3 m/s from NE", WeatherNotification.weatherText("Cloudy", wind(3, 2), false, ENGLISH));
        assertEquals("Облачно, ветер 3 м/с, СВ", WeatherNotification.weatherText("Облачно", "ветер 3 м/с, СВ", false, RUSSIAN));
    }

    @Test
    public void windAlone() {
        assertEquals("wind 3 m/s from NE", WeatherNotification.weatherText("", wind(3, 2), false, ENGLISH));
        assertEquals("wind 3 m/s from NE", WeatherNotification.weatherText(null, wind(3, 2), false, ENGLISH));
    }

    // The wind alone, starting the line with a capital
    @Test
    public void compact() {
        assertEquals("Wind 3 m/s from NE", WeatherNotification.weatherText("Cloudy", wind(3, 2), true, ENGLISH));
        assertEquals("Ветер 3 м/с, СВ", WeatherNotification.weatherText("Облачно", "ветер 3 м/с, СВ", true, RUSSIAN));
        assertEquals("Calm", WeatherNotification.weatherText("Clear", wind(0, 0), true, ENGLISH));
        assertEquals("", WeatherNotification.weatherText("Clear", "", true, ENGLISH));
    }

    // Lower-cased after the comma, the directions kept as they are
    @Test
    public void windLowerCased() {
        assertEquals("wind 3 m/s from NE", wind(3, 2));
        assertEquals("ветер 4 м/с, СЗ", WeatherNotification.windText(DIRECTIONS_RU, "Ветер %1$s %2$s, %3$s", "Ветер %1$s %2$s",
                4, 8, "4", "м/с", RUSSIAN));
    }

    // German keeps its nouns' capitals
    @Test
    public void windInGerman() {
        assertEquals("Wind 4 m/s aus NO", WeatherNotification.windText(DIRECTIONS_DE, "Wind %1$s %2$s aus %3$s", "Wind %1$s %2$s",
                4, 2, "4", "m/s", GERMAN));
    }

    // No wind: the calm's word as it is; a speed without a direction, or with one the list hasn't, by the other pattern
    @Test
    public void calmAndNoDirection() {
        assertEquals("Calm", wind(0, 3));
        assertEquals("wind 3 m/s", wind(3, 0));
        assertEquals("wind 3 m/s", wind(3, 9));
    }
}
