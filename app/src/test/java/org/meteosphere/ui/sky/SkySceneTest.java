package org.meteosphere.ui.sky;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SkySceneTest {

    private static final float DELTA = 1e-6f;

    // A clear day has the sun, a clear night the stars; the drops are dimmer at night
    @Test
    public void readsTheDayOrTheNight() {
        SkyScene day = scene("day");
        assertEquals(SkyScene.BODY_SUN, day.body);
        assertFalse(day.hasStars());
        assertEquals(1f, day.dropsBrightness, DELTA);
        SkyScene night = scene("night");
        assertEquals(SkyScene.BODY_NONE, night.body);
        assertTrue(night.hasStars());
        assertEquals(0.55f, night.dropsBrightness, DELTA);
    }

    // No words at all, which a sky never is, is a clear night
    @Test
    public void readsNoWordsAsAClearNight() {
        SkyScene scene = SkyScene.from(Collections.emptyList());
        assertTrue(scene.hasStars());
        assertEquals(0f, scene.coverage, DELTA);
    }

    @Test
    public void readsTheCloudCover() {
        assertEquals(0f, scene("day").coverage, DELTA);
        assertEquals(0.15f, scene("day", "clouds2").coverage, DELTA);
        assertEquals(0.35f, scene("day", "clouds3").coverage, DELTA);
        assertEquals(0.85f, scene("day", "clouds4").coverage, DELTA);
        assertEquals(0.15f, scene("night", "clouds2").coverage, DELTA);
        assertEquals(0.35f, scene("night", "clouds3").coverage, DELTA);
        assertEquals(0.85f, scene("night", "clouds4").coverage, DELTA);
        // Overcast, the stars gone
        assertFalse(scene("night", "clouds4").hasStars());
    }

    @Test
    public void readsTheRainAndTheSnow() {
        SkyScene drizzle = scene("day", "clouds4", "rain1");
        assertEquals(0.35f, drizzle.rain, DELTA);
        assertEquals(0f, drizzle.snow, DELTA);
        assertEquals(1 / 3f, drizzle.precipitation, DELTA);
        assertEquals(1f, scene("day", "clouds4", "rain3").rain, DELTA);
        SkyScene snow = scene("night", "clouds4", "snow2");
        assertEquals(0f, snow.rain, DELTA);
        assertEquals(0.675f, snow.snow, DELTA);
        assertEquals(2 / 3f, snow.precipitation, DELTA);
        // Sleet: half of each
        SkyScene sleet = scene("night", "clouds4", "rain2", "snow2");
        assertEquals(0.3375f, sleet.rain, DELTA);
        assertEquals(0.3375f, sleet.snow, DELTA);
        assertFalse(scene("day", "clouds4").hasDrops());
    }

    @Test
    public void readsTheStormAndTheFog() {
        assertTrue(scene("day", "clouds4", "rain3", "storm").hasLightning());
        assertFalse(scene("day", "clouds4", "rain3").hasLightning());
        assertEquals(0.65f, scene("day", "clouds3", "mist").mist, DELTA);
        assertEquals(0f, scene("day", "clouds3").mist, DELTA);
    }

    // The moon at night under thin clouds, as its word has it; none by day or behind an overcast
    @Test
    public void readsTheMoon() {
        SkyScene scene = scene("night", "clouds2", "moon26_27_0");
        assertTrue(scene.hasMoon());
        assertEquals(Math.toRadians(52), scene.moonPhase, 1e-5);
        assertEquals(Math.toRadians(270), scene.moonLimb, 1e-5);
        assertEquals(0f, scene.moonFace, DELTA);
        assertFalse(scene("night", "clouds4", "moon26_27_0").hasMoon());
        assertFalse(scene("day", "moon26_27_0").hasMoon());
    }

    // The words of an icon's sky give its scene: a storm's heaps of rain clouds
    @Test
    public void readsTheSkyOfAnIcon() {
        SkyScene scene = SkyScene.from(SkyWeather.of("d.c2.r2.st", true));
        assertEquals(0.675f, scene.rain, DELTA);
        assertTrue(scene.hasLightning());
        assertEquals(1f, scene.cloudType, DELTA);
    }

    private static SkyScene scene(String... words) {
        return SkyScene.from(Arrays.asList(words));
    }
}
