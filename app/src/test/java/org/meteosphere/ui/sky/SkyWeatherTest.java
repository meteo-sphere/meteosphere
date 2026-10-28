package org.meteosphere.ui.sky;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SkyWeatherTest {

    // The time of day by isDay whatever the icon says; an unknown weather a clear sky
    @Test
    public void tellsTheTimeOfDayAlways() {
        assertEquals(sky("night"), SkyWeather.of("", false));
        assertEquals(sky("day"), SkyWeather.of("nodata", true));
        assertEquals(sky("day"), SkyWeather.of("d", true));
        assertEquals(sky("night"), SkyWeather.of("d", false));
        assertEquals(sky("day"), SkyWeather.of("n.c1.r4.x", true));
    }

    @Test
    public void readsTheCloudsAndTheFog() {
        assertEquals(sky("night", "clouds3"), SkyWeather.of("n.c3", false));
        assertEquals(sky("day", "clouds2"), SkyWeather.of("d.c2", true));
        assertEquals(sky("day", "clouds4"), SkyWeather.of("c4", true));
        assertEquals(sky("night", "mist"), SkyWeather.of("mist", false));
    }

    // Sleet is rain and snow of its strength
    @Test
    public void readsTheRainTheSnowAndTheStorm() {
        assertEquals(sky("day", "snow1", "mist"), SkyWeather.of("s1.mist", true));
        assertEquals(sky("day", "clouds4", "rain1"), SkyWeather.of("c4.r1", true));
        assertEquals(sky("night", "clouds4", "rain2", "snow2"), SkyWeather.of("c4.rs2", false));
        assertEquals(sky("day", "clouds2", "rain2", "storm"), SkyWeather.of("d.c2.r2.st", true));
        assertEquals(sky("day", "clouds3", "snow3"), SkyWeather.of("d.c3.s3", true));
    }

    @Test
    public void colorsTheSkyByTheFogTheOvercastAndTheTimeOfDay() {
        assertEquals(opaque(36, 56, 69), SkyWeather.color(sky("day", "mist")));
        assertEquals(opaque(21, 25, 30), SkyWeather.color(sky("night", "clouds3", "mist")));
        assertEquals(opaque(31, 52, 65), SkyWeather.color(sky("day", "clouds4", "rain2")));
        assertEquals(opaque(15, 17, 23), SkyWeather.color(sky("night", "clouds4")));
        assertEquals(opaque(7, 49, 67), SkyWeather.color(sky("day", "clouds3")));
        assertEquals(opaque(19, 18, 27), SkyWeather.color(sky("night", "moon26_27_0")));
        // The fog's over the overcast's
        assertEquals(opaque(36, 56, 69), SkyWeather.color(sky("day", "clouds4", "mist")));
    }

    private static List<String> sky(String... words) {
        return Arrays.asList(words);
    }

    private static int opaque(int red, int green, int blue) {
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
