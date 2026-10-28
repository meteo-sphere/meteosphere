package org.meteosphere.util;

import android.content.Context;
import android.content.res.Resources;
import org.meteosphere.R;

/** The wind's speed with its unit in the language, and the forecasts' headings' wind */
public final class WindText {

    private static final String NO_BREAK_SPACE = " ";
    private static final String WORD_JOINER = "⁠";
    private static final String EN_DASH = "–";

    private WindText() {
    }

    /** The unit after the speed in metres per second converted to it, "mph" and "knots" in the plural it takes */
    public static String unitName(Context context, int metresPerSecond, int unit) {
        Resources res = context.getResources();
        switch (unit) {
            case Units.METRES_PER_SECOND:
            case Units.KM_PER_HOUR:
                return res.getStringArray(R.array.wind_units)[unit];
            case Units.MILES_PER_HOUR:
                return res.getQuantityString(R.plurals.mph, Units.windSpeed(metresPerSecond, unit));
            case Units.KNOTS:
                return res.getQuantityString(R.plurals.knots, Units.windSpeed(metresPerSecond, unit));
            default:
                return "";
        }
    }

    /** "5 m/s": the speed in the unit, a space and the unit */
    public static String of(Context context, int metresPerSecond, int unit) {
        return Units.windSpeed(metresPerSecond, unit) + " " + unitName(context, metresPerSecond, unit);
    }

    /**
     * The wind of a forecast's heading, "Wind NE, 1–3 m/s", by the direction of the strongest wind: the calm's word
     * when even that is none, and without the direction when it has none
     */
    public static String summary(Context context, int minWind, int maxWind, int direction, int unit) {
        Resources res = context.getResources();
        String[] directions = res.getStringArray(R.array.wind_directions);
        if (maxWind == 0) {
            return directions[0];
        }
        String name = direction >= 1 && direction < directions.length ? directions[direction] : "";
        // A no-break space before the unit and a word joiner after each of its "/" keep "1–3 m/s" on one line
        String unitText = unitName(context, maxWind, unit).replace("/", "/" + WORD_JOINER);
        String text = res.getString(R.string.details_summary_wind, name, speedText(minWind, maxWind, unit) + NO_BREAK_SPACE + unitText);
        // Without a direction the pattern leaves "Wind , 3 m/s"
        return text.replace(" ,", ",");
    }

    /** The speeds from the weakest wind to the strongest in the unit, "1–3", or one number when they are the same */
    static String speedText(int minWind, int maxWind, int unit) {
        int low = Units.windSpeed(minWind, unit);
        int high = Units.windSpeed(maxWind, unit);
        return low == high ? String.valueOf(high) : low + EN_DASH + high;
    }
}
