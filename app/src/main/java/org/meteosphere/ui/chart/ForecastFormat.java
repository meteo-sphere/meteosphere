package org.meteosphere.ui.chart;

import android.content.res.Resources;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.ui.icons.TemperatureColors;
import org.meteosphere.util.Units;

/**
 * The values of a forecast's columns as the details' strips and the chart widgets write them. Times are local ones
 * read as UTC.
 */
public final class ForecastFormat {

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    // The forecasts' directions of the wind, 45 degrees apart
    private static final int DIRECTIONS = 8;

    private ForecastFormat() {
    }

    /** "12°" in the unit */
    public static String temperature(int celsius, int unit) {
        return Units.temperatureText(Units.temperature(celsius, unit), 1) + "°";
    }

    /** A temperature's colour, light enough to read on a dark surface or dark enough on a light one */
    public static int temperatureColor(int celsius, boolean darkSurface) {
        return TemperatureColors.forSurface(TemperatureColors.of(celsius), darkSurface);
    }

    /** The amount in the unit: "0.4" millimetres, "0.02" inches */
    public static String precipitation(double millimetres, int unit) {
        double value = Units.precipitation(millimetres, unit);
        return String.format(Locale.getDefault(), unit == Units.INCHES ? "%.2f" : "%.1f", value);
    }

    /**
     * The degrees of the wind's arrow, pointing where the wind blows to: the direction it comes from is 1 for north to 8
     * for north-west, clockwise, as R.array.wind_directions has them, so north's arrow is 180; NaN for a calm or no
     * direction
     */
    public static float windArrow(int speed, int direction) {
        return speed > 0 && direction >= 1 && direction <= DIRECTIONS ? (180 + 45 * (direction - 1)) % 360 : Float.NaN;
    }

    /** "Today" or "Thu, 16", the weekdays as R.array.weekdays_short has them */
    public static String dayLabel(Resources res, String[] weekdaysShort, long localDay, long nowLocal) {
        if (floorDay(localDay) == floorDay(nowLocal)) {
            return res.getString(R.string.details_today);
        }
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(localDay);
        return res.getString(R.string.format_weekday_day, weekdaysShort[calendar.get(Calendar.DAY_OF_WEEK) - 1], calendar.get(Calendar.DAY_OF_MONTH));
    }

    /** The midnight the local day starts at */
    public static long floorDay(long localTime) {
        return Math.floorDiv(localTime, DAY_MS) * DAY_MS;
    }
}
