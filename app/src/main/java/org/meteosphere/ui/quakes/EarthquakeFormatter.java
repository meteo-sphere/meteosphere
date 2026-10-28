package org.meteosphere.ui.quakes;

import org.meteosphere.util.AppUtils;
import org.meteosphere.util.Units;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.meteosphere.R;

/** Texts of the earthquake list: magnitude, how long ago and when on the phone's clock, depth and distance. */
public final class EarthquakeFormatter {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;

    private final Resources resources;
    private final Locale locale;
    private final int distanceUnit;
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dateTimeFormat;
    private final SimpleDateFormat shortDateTimeFormat;
    private final SimpleDateFormat longDateTimeFormat;
    private final SimpleDateFormat utcFormat;
    private final SimpleDateFormat shortDateFormat;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat weekdayDateFormat;

    public EarthquakeFormatter(Context context, boolean is24HourFormat, int distanceUnit) {
        this.resources = context.getResources();
        this.locale = this.resources.getConfiguration().getLocales().get(0);
        this.distanceUnit = distanceUnit;
        String time = is24HourFormat ? "HH:mm" : "h:mm a";
        // Times with "a" keep the English AM/PM
        Locale timeLocale = is24HourFormat ? this.locale : Locale.US;
        this.timeFormat = new SimpleDateFormat(time, timeLocale);
        this.dateTimeFormat = new SimpleDateFormat(AppUtils.datePattern(timeLocale, "d MMM, " + time), timeLocale);
        this.shortDateTimeFormat = AppUtils.withoutMonthDots(new SimpleDateFormat(AppUtils.datePattern(timeLocale, "d MMM, " + time), timeLocale));
        this.longDateTimeFormat = new SimpleDateFormat(AppUtils.datePattern(timeLocale, "d MMMM, " + time), timeLocale);
        this.utcFormat = new SimpleDateFormat(time, timeLocale);
        this.utcFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        this.shortDateFormat = new SimpleDateFormat(AppUtils.datePattern(this.locale, "d MMM"), this.locale);
        this.dateFormat = new SimpleDateFormat(AppUtils.datePattern(this.locale, "d MMMM"), this.locale);
        this.weekdayDateFormat = new SimpleDateFormat(AppUtils.datePattern(this.locale, "EEEE, d MMMM"), this.locale);
    }

    // The badge colors at M5, M6, M7 and M8: amber, orange, red and maroon; in between they blend, past them they stay
    private static final double[] COLOR_MAGNITUDES = {5, 6, 7, 8};
    private static final int[] MAGNITUDE_COLORS = {0xFFF6BF26, 0xFFF28C28, 0xFFD93025, 0xFF8E1B1B};
    // The text on a badge: dark brown or white, whichever stands out more
    private static final int ON_LIGHT = 0xFF3A2200;
    private static final int ON_DARK = 0xFFFFFFFF;

    /** Color of the magnitude badge, turning by the tenth from amber at 5 through orange and red to maroon at 8 */
    public static int magnitudeColor(double magnitude) {
        if (magnitude <= COLOR_MAGNITUDES[0]) {
            return MAGNITUDE_COLORS[0];
        }
        for (int i = 1; i < COLOR_MAGNITUDES.length; i++) {
            if (magnitude < COLOR_MAGNITUDES[i]) {
                double t = (magnitude - COLOR_MAGNITUDES[i - 1]) / (COLOR_MAGNITUDES[i] - COLOR_MAGNITUDES[i - 1]);
                return blendOklab(MAGNITUDE_COLORS[i - 1], MAGNITUDE_COLORS[i], t);
            }
        }
        return MAGNITUDE_COLORS[MAGNITUDE_COLORS.length - 1];
    }

    /** Color of the text on the magnitude badge */
    public static int magnitudeTextColor(double magnitude) {
        int background = magnitudeColor(magnitude);
        return ColorUtils.calculateContrast(ON_LIGHT, background) >= ColorUtils.calculateContrast(ON_DARK, background) ? ON_LIGHT : ON_DARK;
    }

    // Blended in Oklab, where the lightness goes evenly, unlike sRGB's muddy middle
    private static int blendOklab(int from, int to, double t) {
        double[] a = toOklab(from);
        double[] b = toOklab(to);
        return fromOklab(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
    }

    private static double[] toOklab(int color) {
        double r = toLinear(Color.red(color) / 255.0);
        double g = toLinear(Color.green(color) / 255.0);
        double b = toLinear(Color.blue(color) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    private static int fromOklab(double lightness, double a, double b) {
        double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
        double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
        double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
        return Color.rgb(
                toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
                toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
                toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s));
    }

    private static double toLinear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int toSrgb(double linear) {
        double value = Math.max(0, Math.min(1, linear));
        return (int) Math.round(255 * (value <= 0.0031308 ? value * 12.92 : 1.055 * Math.pow(value, 1 / 2.4) - 0.055));
    }

    /** "За сутки", "За неделю", "За месяц" */
    public static int periodTitle(int days) {
        return days == 1 ? R.string.earthquakes_summary_day : (days == 7 ? R.string.earthquakes_summary_week : R.string.earthquakes_summary_month);
    }

    public String magnitude(double magnitude) {
        return String.format(this.locale, "%.1f", magnitude);
    }

    /** How long ago, short: "now", "38 min", "7 h", "3 d" */
    public String ago(long time, long now) {
        long age = Math.max(0, now - time);
        if (age < MINUTE_MS) {
            return this.resources.getString(R.string.earthquakes_ago_now);
        } else if (age < HOUR_MS) {
            return this.resources.getString(R.string.earthquakes_ago_minutes, age / MINUTE_MS);
        } else if (age < DAY_MS) {
            return this.resources.getString(R.string.earthquakes_ago_hours, age / HOUR_MS);
        }
        return this.resources.getString(R.string.earthquakes_ago_days, age / DAY_MS);
    }

    /** Within the last day: the list highlights how long ago these were */
    public static boolean isRecent(long time, long now) {
        return now - time < DAY_MS;
    }

    /** On the phone's clock: "15:27" today, "30 Sep, 21:21" before */
    public String clock(long time, long now) {
        return (isSameDay(time, now) ? this.timeFormat : this.dateTimeFormat).format(new Date(time));
    }

    /** On the phone's clock, the time only: "07:44" */
    public String time(long time) {
        return this.timeFormat.format(new Date(time));
    }

    /** "Today, 15:27" today, "30 Sep, 21:21" before, on the phone's clock; "17 окт", not "17 окт." */
    public String shortDateTime(long time, long now) {
        return isSameDay(time, now) ? this.resources.getString(R.string.earthquakes_today_at, time(time))
                : this.shortDateTimeFormat.format(new Date(time));
    }

    /** "28 September, 07:41" on the phone's clock */
    public String dateTime(long time) {
        return this.longDateTimeFormat.format(new Date(time));
    }

    /** "04:41" in UTC */
    public String utcTime(long time) {
        return this.utcFormat.format(new Date(time));
    }

    /** "1 Oct" */
    public String shortDate(long time) {
        return this.shortDateFormat.format(new Date(time)).replace("..", ".");
    }

    /** "4 h ago" today, "yesterday, 1 Oct" the day before, "1 Oct" earlier */
    public String shortDate(long time, long now) {
        if (isSameDay(time, now)) {
            return agoLong(time, now);
        }
        String date = shortDate(time);
        return isSameDay(time, now - DAY_MS) ? this.resources.getString(R.string.earthquakes_yesterday_short, date) : date;
    }

    /** The title of the day a list group is in: "Today", "Yesterday, 1 October", "Tuesday, 29 September" */
    public String day(long time, long now) {
        if (isSameDay(time, now)) {
            return this.resources.getString(R.string.earthquakes_today);
        }
        if (isSameDay(time, now - DAY_MS)) {
            return this.resources.getString(R.string.earthquakes_yesterday, this.dateFormat.format(new Date(time)));
        }
        String day = this.weekdayDateFormat.format(new Date(time));
        return day.substring(0, 1).toUpperCase(this.locale) + day.substring(1);
    }

    /** The start of the phone's day of the instant, to tell the days apart */
    public static long dayStart(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** How long ago in words: "just now", "38 min ago", "7 h ago", "4 days ago" */
    public String agoLong(long time, long now) {
        long age = Math.max(0, now - time);
        if (age < MINUTE_MS) {
            return this.resources.getString(R.string.earthquake_ago_now);
        } else if (age < HOUR_MS) {
            return this.resources.getString(R.string.earthquake_ago_minutes, age / MINUTE_MS);
        } else if (age < DAY_MS) {
            return this.resources.getString(R.string.earthquake_ago_hours, age / HOUR_MS);
        }
        int days = (int) (age / DAY_MS);
        return this.resources.getString(R.string.earthquake_ago_days, days, this.resources.getQuantityString(R.plurals.days, days));
    }

    /** "52 earthquakes M5+" */
    public String count(int count) {
        return this.resources.getString(R.string.earthquakes_count, count,
                this.resources.getQuantityString(R.plurals.earthquakes_counted, count));
    }

    /** "to the north-west" of a bearing in degrees, by the eight points of the compass */
    public String direction(double bearing) {
        return this.resources.getStringArray(R.array.earthquake_directions)[(int) Math.round(bearing / 45) % 8];
    }

    /** Shallow above 70 km, intermediate to 300 km, deep below */
    public String depthClass(double km) {
        return this.resources.getString(km < 70 ? R.string.earthquake_depth_shallow
                : (km < 300 ? R.string.earthquake_depth_intermediate : R.string.earthquake_depth_deep));
    }

    /** "Mww" of the USGS magnitude type, empty for none */
    public static String scale(String type) {
        return type == null || type.isEmpty() ? "" : type.substring(0, 1).toUpperCase(Locale.US) + type.substring(1);
    }

    /** What the magnitude was measured by: "moment" for Mw and its kinds, null for a scale without a common name */
    public String scaleName(String type) {
        if (type == null) {
            return null;
        }
        String t = type.toLowerCase(Locale.US);
        if (t.startsWith("mw")) {
            return this.resources.getString(R.string.earthquake_scale_moment);
        } else if (t.startsWith("mb")) {
            return this.resources.getString(R.string.earthquake_scale_body);
        } else if (t.startsWith("ms")) {
            return this.resources.getString(R.string.earthquake_scale_surface);
        } else if (t.startsWith("ml")) {
            return this.resources.getString(R.string.earthquake_scale_local);
        } else if (t.startsWith("md")) {
            return this.resources.getString(R.string.earthquake_scale_duration);
        }
        return null;
    }

    /** "PAGER: green" of the alert level, null for none */
    public String pager(String alert) {
        if (alert == null) {
            return null;
        }
        int index = java.util.Arrays.asList("green", "yellow", "orange", "red").indexOf(alert.toLowerCase(Locale.US));
        return index < 0 ? null : this.resources.getString(R.string.earthquake_pager,
                this.resources.getStringArray(R.array.earthquake_pager_levels)[index]);
    }

    /** In the units of the settings, "1 240 km" */
    public String distance(double km) {
        int value = Units.distance((float) km, this.distanceUnit);
        String unit = this.distanceUnit == Units.MILES
                ? this.resources.getQuantityString(R.plurals.miles, value)
                : this.resources.getStringArray(R.array.distance_units)[0];
        return String.format(this.locale, "%,d", value) + "\u00a0" + unit;
    }

    private static boolean isSameDay(long a, long b) {
        Calendar first = Calendar.getInstance();
        first.setTimeInMillis(a);
        Calendar second = Calendar.getInstance();
        second.setTimeInMillis(b);
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }
}
