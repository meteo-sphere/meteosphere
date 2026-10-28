package org.meteosphere.data.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherIconTest {

    // Only the leading d or n is the sun or the moon; "nodata" by day was "dodata", an icon there isn't
    @Test
    public void swapsOnlyTheDayOrNightOfTheIcon() {
        assertEquals("n.c2.r1", icon("d.c2.r1", false));
        assertEquals("d.c3.s2.st", icon("n.c3.s2.st", true));
        assertEquals("d", icon("n", true));
        assertEquals("n", icon("d", false));
        assertEquals("c4.rs1", icon("c4.rs1", true));
        assertEquals("mist", icon("mist", false));
        assertEquals("nodata", icon("nodata", true));
        assertEquals("nodata", icon("nodata", false));
    }

    // The parts r, s and rs with their strength; the thunderstorm and the mist aren't precipitation
    @Test
    public void tellsThePrecipitationByTheIcon() {
        assertEquals(0, Weather.precipitationKind("d.c2.r1"));
        assertEquals(1, Weather.precipitationKind("c4.s3.st"));
        assertEquals(1, Weather.precipitationKind("s1.mist"));
        assertEquals(2, Weather.precipitationKind("c4.rs2"));
        assertEquals(-1, Weather.precipitationKind("c4.st"));
        assertEquals(-1, Weather.precipitationKind("mist"));
        assertEquals(-1, Weather.precipitationKind("nodata"));
        assertEquals(-1, Weather.precipitationKind(null));
    }

    private static String icon(String name, boolean isDay) {
        Weather weather = new Weather();
        weather.icon = name;
        return weather.icon(isDay);
    }
}
