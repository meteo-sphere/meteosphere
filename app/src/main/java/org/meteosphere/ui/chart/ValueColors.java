package org.meteosphere.ui.chart;

/**
 * The colours of the wind, humidity and pressure values far from the usual, as the geomagnetic row colours its
 * storms: a strong wind yellow to red, dry air amber and damp air blue, low pressure violet and high pressure amber
 * to orange. The usual values stay white.
 */
public final class ValueColors {

    private static final int YELLOW = 0xffffe082;
    private static final int AMBER = 0xffffcc80;
    private static final int ORANGE = 0xffffb74d;
    private static final int RED = 0xffef9a9a;
    private static final int BLUE = 0xff81d4fa;
    // Light, to read on a grey sky: a deeper violet would be too dark there
    private static final int VIOLET = 0xffd0bcff;
    // The second line, the day's lowest pressure, as dim as the white one
    private static final int DIM = 0xb8000000;

    private ValueColors() {
    }

    /**
     * The colour of a strong wind, from the slowest strong one, in metres per second whatever unit it is shown in; 0
     * for one not strong
     */
    public static int wind(int ms, int strong) {
        return ms < strong ? 0 : ms <= 10 ? YELLOW : ms <= 14 ? ORANGE : RED;
    }

    /** 0 for the usual humidity */
    public static int humidity(int percent) {
        if (percent < 30) {
            return AMBER;
        }
        if (percent > 85) {
            return BLUE;
        }
        return 0;
    }

    /** The day's lowest pressure under its highest, dimmer; 0 for the usual or none */
    public static int pressureLow(int mmHg, double usual) {
        int color = mmHg > 0 ? pressure(mmHg, usual) : 0;
        return color != 0 ? (color & 0x00ffffff) | DIM : 0;
    }

    /**
     * The pressure in millimetres of mercury whatever unit it is shown in, 0 for the usual: around the pressure usual
     * at the place's height, from 10 mm under it to 10 over, white; 750 to 770 at sea level
     */
    public static int pressure(int mmHg, double usual) {
        if (mmHg < usual - 10) {
            return VIOLET;
        }
        if (mmHg > usual + 15) {
            return ORANGE;
        }
        if (mmHg > usual + 10) {
            return AMBER;
        }
        return 0;
    }
}
