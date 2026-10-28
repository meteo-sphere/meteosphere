package org.meteosphere.ui.icons;

import androidx.core.graphics.ColorUtils;

/** The temperatures' colours, from red at 40 °C through yellow, green and blue to violet at −40 °C */
public final class TemperatureColors {

    // The scale from the warmest, a colour at each point; in between they blend, past the ends they stay
    private static final int[] SCALE_CELSIUS = {40, 30, 20, 10, 2, 1, 0, -1, -2, -10, -20, -30, -40};
    private static final int[] SCALE_COLORS = {0xFF0000, 0xFF7800, 0xFFD300, 0x66D915, 0xAAE2B8, 0xBFD9CF, 0xC3D2D6, 0xABD3E0,
            0x95D6EE, 0x20C5FF, 0x1045FF, 0x6700F9, 0x5600AF};
    private static final int OPAQUE = 0xFF000000;
    // The CIELAB lightness large text keeps on the app's dark surfaces, at least, and on its light ones, at most
    private static final double DARK_SURFACE_MIN_LIGHTNESS = 70;
    private static final double LIGHT_SURFACE_MAX_LIGHTNESS = 60;

    private TemperatureColors() {
    }

    /** The opaque colour of the temperature in °C */
    public static int of(int celsius) {
        if (celsius >= SCALE_CELSIUS[0]) {
            return OPAQUE | SCALE_COLORS[0];
        }
        int last = SCALE_CELSIUS.length - 1;
        if (celsius <= SCALE_CELSIUS[last]) {
            return OPAQUE | SCALE_COLORS[last];
        }
        // The colder point at or under the temperature, the warmer one before it above
        int colder = 1;
        while (SCALE_CELSIUS[colder] > celsius) {
            colder++;
        }
        int warmer = colder - 1;
        int color = OPAQUE;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int from = (SCALE_COLORS[colder] >> shift) & 0xFF;
            int to = (SCALE_COLORS[warmer] >> shift) & 0xFF;
            // In whole numbers, multiplied before divided: the shades between the points depend on it
            int channel = from + (to - from) * (celsius - SCALE_CELSIUS[colder]) / (SCALE_CELSIUS[warmer] - SCALE_CELSIUS[colder]);
            color |= channel << shift;
        }
        return color;
    }

    /**
     * The colour kept legible as large text on the app's dark or light surface: lightened or darkened in CIELAB to the
     * lightness it needs there, its hue kept. Always converted back from CIELAB, opaque, also when its lightness is
     * fine: the screens' temperatures always went through it, which may move a channel by one
     */
    public static int forSurface(int color, boolean darkSurface) {
        double[] lab = new double[3];
        ColorUtils.colorToLAB(color, lab);
        double lightness = darkSurface ? Math.max(lab[0], DARK_SURFACE_MIN_LIGHTNESS) : Math.min(lab[0], LIGHT_SURFACE_MAX_LIGHTNESS);
        return ColorUtils.LABToColor(lightness, lab[1], lab[2]);
    }
}
