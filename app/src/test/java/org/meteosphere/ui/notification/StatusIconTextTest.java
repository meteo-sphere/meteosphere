package org.meteosphere.ui.notification;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The status bar icon's text of the temperature: the degree above zero, the minus sign below, a hyphen for two digits
public class StatusIconTextTest {

    @Test
    public void aboveZero() {
        assertEquals("5°", WeatherNotification.statusIconText(5));
        assertEquals("25°", WeatherNotification.statusIconText(25));
    }

    @Test
    public void zero() {
        assertEquals("0", WeatherNotification.statusIconText(0));
    }

    // The minus sign for one digit; a hyphen, narrower, for two, which leave no room for a degree either
    @Test
    public void belowZero() {
        assertEquals("−5", WeatherNotification.statusIconText(-5));
        assertEquals("-15", WeatherNotification.statusIconText(-15));
        assertEquals("-10", WeatherNotification.statusIconText(-10));
        assertEquals("−9", WeatherNotification.statusIconText(-9));
    }
}
