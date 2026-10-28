package org.meteosphere.ui.sky;

import java.util.ArrayList;
import java.util.List;
import org.meteosphere.astro.Moon;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;

/**
 * The sky of a weather: the words the skies are drawn, composed and kept by. In this order: DAY or NIGHT, always one;
 * the cloud cover, when there are clouds; rain, snow, the storm and the fog, each when there is one; and the moon's
 * word, as SkyScene.moonLayer makes it, at night with the moon up
 */
public final class SkyWeather {

    public static final String DAY = "day";
    public static final String NIGHT = "night";
    /** Followed by the cloud cover, 2 to 4: the highest only */
    public static final String CLOUDS = "clouds";
    /** The cloud cover of an overcast sky */
    public static final String OVERCAST = CLOUDS + 4;
    // Each followed by its strength, 1 to 3; sleet is both
    public static final String RAIN = "rain";
    public static final String SNOW = "snow";
    public static final String STORM = "storm";
    public static final String MIST = "mist";

    // The plain colours by day and by night: the fog's, the overcast's without fog, and the rest's
    private static final int MIST_DAY = 0xFF243845;
    private static final int MIST_NIGHT = 0xFF15191E;
    private static final int OVERCAST_DAY = 0xFF1F3441;
    private static final int OVERCAST_NIGHT = 0xFF0F1117;
    private static final int OTHER_DAY = 0xFF073143;
    private static final int OTHER_NIGHT = 0xFF13121B;

    private SkyWeather() {
    }

    /**
     * The sky of a weather icon's name, as OpenMeteoMapper and ForecaMapper make them ("d.c3.r2.st", "c4.rs1"), by
     * isDay whatever the icon's own sun or moon: an unknown weather is a clear sky
     */
    public static List<String> of(String icon, boolean isDay) {
        int clouds = 0;
        int rain = 0;
        int snow = 0;
        boolean storm = false;
        boolean mist = false;
        for (String part : icon.split("\\.")) {
            int sleet = strength(part, "rs", 1, 3);
            clouds = Math.max(clouds, strength(part, "c", 2, 4));
            rain = Math.max(rain, Math.max(strength(part, "r", 1, 3), sleet));
            snow = Math.max(snow, Math.max(strength(part, "s", 1, 3), sleet));
            storm |= part.equals("st");
            mist |= part.equals("mist");
        }
        ArrayList<String> sky = new ArrayList<>();
        sky.add(isDay ? DAY : NIGHT);
        if (clouds > 0) {
            sky.add(CLOUDS + clouds);
        }
        if (rain > 0) {
            sky.add(RAIN + rain);
        }
        if (snow > 0) {
            sky.add(SNOW + snow);
        }
        if (storm) {
            sky.add(STORM);
        }
        if (mist) {
            sky.add(MIST);
        }
        return sky;
    }

    /**
     * The sky of the place now, with the moon at night when it is up there, lit and turned as it is seen from the
     * place; a clear night without a place or without its weather now
     */
    public static List<String> of(Place place) {
        if (place == null) {
            return of("", false);
        }
        Weather now = place.nowWeather();
        List<String> sky;
        if (now == null) {
            sky = of("", false);
        } else {
            boolean isDay = place.isDay();
            sky = of(now.icon(isDay), isDay);
        }
        if (sky.get(0).equals(NIGHT)) {
            long time = System.currentTimeMillis();
            if (Moon.altitude(time, place.latitude, place.longitude) > 0) {
                sky.add(SkyScene.moonLayer(Moon.disc(time, place.latitude, place.longitude)));
            }
        }
        return sky;
    }

    /** The plain colour shown before the sky's picture is drawn, and where it can't be; opaque */
    public static int color(List<String> sky) {
        boolean day = !sky.isEmpty() && sky.get(0).equals(DAY);
        if (sky.contains(MIST)) {
            return day ? MIST_DAY : MIST_NIGHT;
        }
        if (sky.contains(OVERCAST)) {
            return day ? OVERCAST_DAY : OVERCAST_NIGHT;
        }
        return day ? OTHER_DAY : OTHER_NIGHT;
    }

    /** The strength of the kind in the sky, CLOUDS 2 to 4, RAIN or SNOW 1 to 3; 0 without it */
    static int strength(List<String> sky, String kind) {
        boolean clouds = kind.equals(CLOUDS);
        int res = 0;
        for (String word : sky) {
            res = Math.max(res, strength(word, kind, clouds ? 2 : 1, clouds ? 4 : 3));
        }
        return res;
    }

    // The strength following the kind in the word, as in "c3", "rs2" or "rain1"; 0 for another word or one out of
    // the range
    private static int strength(String word, String kind, int min, int max) {
        if (word.length() != kind.length() + 1 || !word.startsWith(kind)) {
            return 0;
        }
        int strength = word.charAt(kind.length()) - '0';
        return strength >= min && strength <= max ? strength : 0;
    }
}
