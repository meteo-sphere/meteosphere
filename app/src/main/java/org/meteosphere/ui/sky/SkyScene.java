package org.meteosphere.ui.sky;

import java.util.List;

/**
 * What the sky shader of the animated weather draws for a weather: its colours, its clouds, the sun or the moon, the
 * stars, rain, snow, mist and lightning. Read from the sky's words, as SkyWeather describes the weather.
 */
public final class SkyScene {

    public static final int BODY_NONE = 0;
    public static final int BODY_SUN = 1;
    public static final int BODY_MOON = 2;
    /**
     * The sky's word marking the moon over the horizon at night, followed by its disc as Moon.disc gives it, in whole
     * steps of PHASE_STEP, LIMB_STEP and LIMB_STEP degrees, apart: its phase angle, where its bright limb points and
     * where its north points, each from the vertical toward the left of the sky. As steps, so that a still made for it
     * serves for a while: the phase moves about two degrees in four hours, the limb and the pole up to ten in one.
     */
    public static final String MOON_LAYER = "moon";
    public static final int PHASE_STEP = 2;
    public static final int LIMB_STEP = 10;

    final float[] zenith;
    final float[] horizon;
    final float[] light;
    final float[] ambient;
    // The sky's light on the clouds' tops; their bottoms get less (Horizon: ambient increases over height)
    final float[] ambientTop;
    // The clouds' type, as Nubis's: 0 stratus, 0.5 stratocumulus, 1 cumulus; and how much rain or snow they hold
    final float cloudType;
    final float precipitation;
    // The share of the sky the clouds cover, 0 to 1: the clouds' noise is equalized, so a share is a threshold
    final float coverage;
    final float sigma;
    final int body;
    final float bodyX;
    final float bodyY;
    // The moon's disc, in radians: its phase angle, 0 full to pi new, where its bright limb points and where its
    // north points, each from the vertical toward the left of the sky
    final float moonPhase;
    final float moonLimb;
    final float moonFace;
    final float glow;
    final float stars;
    final float mist;
    final float rain;
    final float snow;
    final float dropsBrightness;
    final boolean storm;
    final float seed;
    /**
     * The moment its clouds are shown at, in seconds of the wind and of their change: the stills are drawn at it, the
     * same every time, and the live sky starts from it. Picked by the user from shots of each weather.
     */
    final float moment;
    private final List<String> sky;

    private SkyScene(Builder b, List<String> sky) {
        this.zenith = b.zenith;
        this.horizon = b.horizon;
        this.light = b.light;
        this.ambient = b.ambient;
        this.ambientTop = b.ambient.clone();
        this.cloudType = b.cloudType;
        this.precipitation = b.precipitation;
        this.coverage = b.coverage;
        this.sigma = b.sigma;
        this.body = b.body;
        this.bodyX = b.bodyX;
        this.bodyY = b.bodyY;
        this.moonPhase = b.moonPhase;
        this.moonLimb = b.moonLimb;
        this.moonFace = b.moonFace;
        this.glow = b.glow;
        this.stars = b.stars;
        this.mist = b.mist;
        this.rain = b.rain;
        this.snow = b.snow;
        this.dropsBrightness = b.dropsBrightness;
        this.storm = b.storm;
        this.seed = b.seed;
        this.moment = b.moment;
        this.sky = sky;
    }

    /** Whether lightning flashes now and then */
    public boolean hasLightning() {
        return this.storm;
    }

    /** Whether stars show: drawn over the clouds, as sharp as the screen */
    public boolean hasStars() {
        return this.stars > 0;
    }

    /** Whether the moon shows: drawn over the clouds, as sharp as the screen, hidden as much as they are thick */
    public boolean hasMoon() {
        return this.body == BODY_MOON;
    }

    /** The moon's word for its disc as Moon.disc gives it, in radians */
    public static String moonLayer(double[] disc) {
        long phase = Math.round(Math.toDegrees(disc[0]) / PHASE_STEP);
        long limb = Math.floorMod(Math.round(Math.toDegrees(disc[1]) / LIMB_STEP), 360 / LIMB_STEP);
        long face = Math.floorMod(Math.round(Math.toDegrees(disc[2]) / LIMB_STEP), 360 / LIMB_STEP);
        return MOON_LAYER + phase + "_" + limb + "_" + face;
    }

    /** Whether rain or snow falls: drawn over the clouds */
    public boolean hasDrops() {
        return this.rain > 0 || this.snow > 0;
    }

    public static SkyScene from(List<String> sky) {
        boolean night = !sky.contains(SkyWeather.DAY);
        int clouds = SkyWeather.strength(sky, SkyWeather.CLOUDS);
        int rain = SkyWeather.strength(sky, SkyWeather.RAIN);
        int snow = SkyWeather.strength(sky, SkyWeather.SNOW);
        float[] moon = null;
        for (String word : sky) {
            if (word.startsWith(MOON_LAYER)) {
                String[] parts = word.substring(MOON_LAYER.length()).split("_");
                try {
                    moon = new float[]{(float) Math.toRadians(Integer.parseInt(parts[0]) * PHASE_STEP),
                            (float) Math.toRadians(Integer.parseInt(parts[1]) * LIMB_STEP),
                            (float) Math.toRadians(Integer.parseInt(parts[2]) * LIMB_STEP)};
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    moon = null;
                }
            }
        }
        boolean storm = sky.contains(SkyWeather.STORM);
        boolean sleet = rain > 0 && snow > 0;
        Builder b = new Builder();
        if (night) {
            nightSky(b, clouds, rain, snow);
        } else {
            daySky(b, clouds, rain, snow);
        }
        if (storm) {
            scale(b.zenith, 0.88f);
            scale(b.horizon, 0.88f);
            scale(b.ambient, 0.88f);
            b.storm = true;
        }
        // Rain clouds a thick deck, stratocumulus; showers and storms heaps, cumulus
        if (rain > 0 || snow > 0) {
            b.precipitation = Math.max(rain, snow) / 3f;
            b.cloudType = storm || (clouds > 0 && clouds < 3) ? 1f : 0.5f;
        }
        b.rain = rain == 0 ? 0 : (sleet ? 0.5f : 1f) * (0.35f + 0.325f * (rain - 1));
        b.snow = snow == 0 ? 0 : (sleet ? 0.5f : 1f) * (0.35f + 0.325f * (snow - 1));
        b.dropsBrightness = night ? 0.55f : 1f;
        b.mist = sky.contains(SkyWeather.MIST) ? 0.65f : 0;
        if (snow > 0) {
            // Falling snow hides the distance: a veil, lighter at night
            // Chosen from the variants shown: a middling veil
            b.mist = Math.max(b.mist, (night ? 0.048f : 0.096f) + 0.036f * snow);
        }
        b.moment = moment(night, clouds, rain, snow, storm);
        if (night && moon != null && clouds <= 3 && rain == 0 && snow == 0) {
            b.body = BODY_MOON;
            b.bodyX = 0.66f;
            b.bodyY = 0.05f;
            b.moonPhase = moon[0];
            b.moonLimb = moon[1];
            b.moonFace = moon[2];
            b.glow = 0.35f;
        }
        return new SkyScene(b, sky);
    }

    // The moments the user picked from shots of each weather (2026-10-19), the fog's and the night's storm's those of
    // their clouds
    private static float moment(boolean night, int clouds, int rain, int snow, boolean storm) {
        if (storm && !night) {
            return 350;
        }
        if (snow > 0 && rain == 0) {
            return 50;
        }
        if (rain > 0 || snow > 0) {
            return night ? 200 : 150;
        }
        if (clouds >= 4) {
            return 200;
        }
        if (clouds == 3) {
            return night ? 250 : 200;
        }
        if (clouds == 2) {
            return night ? 200 : 500;
        }
        return 0;
    }

    // The clouds' density: their extinction per unit of the sky (1500 m) at a density of 1, as the sampler's
    // densities run; chosen, the same in every weather
    private static final float SIGMA = 20f;

    private static void daySky(Builder b, int clouds, int rain, int snow) {
        // Rain and snow fall from a grey-blue deck, as in the pictures, a little darker the harder
        if (snow > 0 && rain == 0) {
            b.set(0x8EA6BD, 0xB5C6D4, 0xF0F4F8, 0x7E9AB8, 0.9f, SIGMA, 41);
        } else if (rain > 0 || snow > 0) {
            boolean showers = clouds > 0 && clouds < 3;
            b.set(0x85A0BA, 0xAABDCE, 0xE4ECF5, 0x7290B5, showers ? 0.6f : 0.9f, SIGMA, 21);
            if (rain == 3) {
                scale(b.zenith, 0.9f);
                scale(b.horizon, 0.9f);
                scale(b.light, 0.9f);
                scale(b.ambient, 0.9f);
            }
        } else if (clouds >= 4) {
            // Light grey-blue, as the pictures' overcast day: a deck of stratocumulus
            b.set(0x8AA3BC, 0xB0C2D2, 0xEAF0F7, 0x7896BA, 0.85f, SIGMA, 21);
            b.cloudType = 0.5f;
        } else if (clouds == 3) {
            // Cumulus mediocris: a third of the ground under heaps of middling height
            b.set(0x2276C4, 0x4A98D4, 0, 0x8AA2C2, 0.35f, SIGMA, 13);
            b.cloudType = 0.85f;
            b.light = new float[]{0.74f, 0.73f, 0.7f};
            b.sun(0.5f);
        } else if (clouds == 2) {
            // Fair-weather cumulus: a few low, flat heaps
            b.set(0x1774C6, 0x3A92D2, 0, 0x93A9C9, 0.15f, SIGMA, 11);
            b.cloudType = 0.6f;
            b.light = new float[]{0.86f, 0.83f, 0.77f};
            b.sun(0.8f);
        } else {
            b.set(0x1270C4, 0x348ED0, 0, 0x9DB5D8, 0f, SIGMA, 7);
            b.light = new float[]{1.05f, 1.0f, 0.92f};
            b.sun(1f);
        }
    }

    private static void nightSky(Builder b, int clouds, int rain, int snow) {
        if (snow > 0 && rain == 0) {
            b.set(0x1A1E26, 0x2E333C, 0x666B75, 0x22262E, 0.9f, SIGMA, 41);
        } else if (rain > 0 || snow > 0) {
            b.set(0x0C0E13, 0x1E2229, 0x3A3E47, 0x15181E, 0.9f, SIGMA, 21);
        } else if (clouds >= 4) {
            b.set(0x0E1117, 0x232833, 0x4A505C, 0x1A1E26, 0.85f, SIGMA, 21);
            b.cloudType = 0.5f;
        } else {
            // The clouds only faintly lit by the moon and the town below, as the pictures' dark night clouds
            b.set(0x02040E, 0x0E1A38, 0, 0x1C2433, clouds == 3 ? 0.35f : clouds == 2 ? 0.15f : 0f, SIGMA, 31);
            b.cloudType = clouds == 3 ? 0.85f : 0.6f;
            b.light = clouds == 3 ? new float[]{0.2f, 0.22f, 0.27f} : new float[]{0.24f, 0.26f, 0.32f};
            b.stars = clouds == 3 ? 0.5f : clouds == 2 ? 0.85f : 1f;
        }
    }

    private static void scale(float[] color, float by) {
        for (int i = 0; i < color.length; i++) {
            color[i] *= by;
        }
    }

    private static float[] rgb(int color) {
        return new float[]{((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f};
    }

    private static final class Builder {
        float[] zenith;
        float[] horizon;
        float[] light;
        float[] ambient;
        float coverage;
        float sigma;
        // Fair-weather heaps unless set otherwise
        float cloudType = 1f;
        float precipitation;
        int body = BODY_NONE;
        float bodyX;
        float bodyY;
        float moonPhase;
        float moonLimb;
        float moonFace;
        float glow;
        float stars;
        float mist;
        float rain;
        float snow;
        float dropsBrightness = 1;
        boolean storm;
        float seed;
        float moment;

        void set(int zenith, int horizon, int light, int ambient, float coverage, float sigma, float seed) {
            this.zenith = rgb(zenith);
            this.horizon = rgb(horizon);
            this.light = rgb(light);
            this.ambient = rgb(ambient);
            this.coverage = coverage;
            this.sigma = sigma;
            this.seed = seed;
        }

        // The sun above the view on the left, out of it, as the pictures had it: the clouds lit from above and the
        // side, not seen against the light
        void sun(float glow) {
            this.body = BODY_SUN;
            this.bodyX = 0.3f;
            this.bodyY = -0.45f;
            this.glow = glow;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SkyScene && ((SkyScene) other).sky.equals(this.sky);
    }

    @Override
    public int hashCode() {
        return this.sky.hashCode();
    }
}
