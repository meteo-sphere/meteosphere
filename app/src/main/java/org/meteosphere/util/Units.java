package org.meteosphere.util;

/**
 * The units of the settings, and the values the app keeps in °C, m/s, mmHg, km and mm converted to them; an unknown
 * unit counts as the one kept. Each is computed in floats or doubles exactly as below, for the screens to show the
 * same numbers
 */
public final class Units {

    public static final int CELSIUS = 0;
    public static final int FAHRENHEIT = 1;

    public static final int METRES_PER_SECOND = 0;
    public static final int KM_PER_HOUR = 1;
    public static final int MILES_PER_HOUR = 2;
    public static final int KNOTS = 3;

    public static final int MM_HG = 0;
    public static final int HECTOPASCALS = 1;
    public static final int MILLIBARS = 2;
    public static final int INCHES_HG = 3;

    public static final int KILOMETRES = 0;
    public static final int MILES = 1;

    public static final int MILLIMETRES = 0;
    public static final int INCHES = 1;

    private static final float KMH_PER_MS = 3.6f;
    private static final float MPH_PER_MS = 2.2369363f;
    private static final float KNOTS_PER_MS = 1.9438444f;
    private static final float MMHG_PER_HPA = 0.75f;
    private static final float MM_PER_INCH = 25.4f;
    private static final double MILES_PER_KM = 0.6214;
    private static final String MINUS = "−";

    private Units() {
    }

    public static int temperature(int celsius, int unit) {
        return unit == FAHRENHEIT ? Math.round(celsius * 9f / 5f + 32f) : celsius;
    }

    /** The speed in the unit of one metre per second */
    public static float windFactor(int unit) {
        switch (unit) {
            case KM_PER_HOUR:
                return KMH_PER_MS;
            case MILES_PER_HOUR:
                return MPH_PER_MS;
            case KNOTS:
                return KNOTS_PER_MS;
            default:
                return 1f;
        }
    }

    public static int windSpeed(int metresPerSecond, int unit) {
        return Math.round(metresPerSecond * windFactor(unit));
    }

    /** Whole hectopascals and millibars, inches of mercury to hundredths */
    public static float pressure(int mmHg, int unit) {
        switch (unit) {
            case HECTOPASCALS:
            case MILLIBARS:
                return Math.round(mmHg / MMHG_PER_HPA);
            case INCHES_HG:
                return (float) AppUtils.roundToHundredths(mmHg / MM_PER_INCH);
            default:
                return mmHg;
        }
    }

    /** Whole kilometres or miles, the fraction cut off */
    public static int distance(float km, int unit) {
        return unit == MILES ? (int) (km * MILES_PER_KM) : (int) km;
    }

    /** Inches by the float 25.4 widened to a double, 25.399999618530273 */
    public static double precipitation(double mm, int unit) {
        return unit == INCHES ? mm / MM_PER_INCH : mm;
    }

    /** "−5" with the minus sign below zero, "0", and above zero "+5" in style 0, the Celsius one, or "5" in another */
    public static String temperatureText(int value, int style) {
        if (value < 0) {
            return MINUS + -value;
        }
        if (value > 0 && style == CELSIUS) {
            return "+" + value;
        }
        return String.valueOf(value);
    }
}
