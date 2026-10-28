package org.meteosphere.ui.astro;

import org.meteosphere.util.AppUtils;
import android.content.Context;
import android.content.res.Resources;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.astro.Moon;
import org.meteosphere.R;

/** Texts about the moon and the sun: phase names, the lit share, rise and set with their arrows, dates, durations, zodiac signs, seasons and eclipses. */
public final class AstroFormatter {

    // The zodiac signs from Aries; the variation selector asks for the text glyph, not the emoji
    private static final int ARIES_SYMBOL = 0x2648;
    private static final char TEXT_PRESENTATION = 0xFE0E;

    private final Resources resources;
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat dayFormat;
    private final SimpleDateFormat dateTimeFormat;
    private final SimpleDateFormat shortDateTimeFormat;
    private final SimpleDateFormat shortDateFormat;
    private final SimpleDateFormat yearDateTimeFormat;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat yearDateFormat;
    private final SimpleDateFormat weekDayFormat;
    private final Locale locale;

    public AstroFormatter(Context context, boolean is24HourFormat) {
        this.resources = context.getResources();
        // Two-digit hours keep the times of a column the same width
        this.timeFormat = is24HourFormat
                ? new SimpleDateFormat("HH:mm", Locale.getDefault())
                : new SimpleDateFormat(AppUtils.TIME_12_HOUR, Locale.US);
        // The times are local ones read as UTC
        this.timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Locale locale = this.resources.getConfiguration().getLocales().get(0);
        this.locale = locale;
        this.dayFormat = new SimpleDateFormat(AppUtils.datePattern(locale, "EEE, d MMMM"), locale);
        this.dayFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String time = is24HourFormat ? "HH:mm" : "h:mm a";
        // Times with "a" keep the English AM/PM
        Locale timeLocale = is24HourFormat ? locale : Locale.US;
        this.dateTimeFormat = new SimpleDateFormat(AppUtils.datePattern(timeLocale, "d MMMM, " + time), timeLocale);
        this.dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.shortDateTimeFormat = AppUtils.withoutMonthDots(new SimpleDateFormat(AppUtils.datePattern(timeLocale, "d MMM, " + time), timeLocale));
        this.shortDateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.shortDateFormat = AppUtils.withoutMonthDots(new SimpleDateFormat(AppUtils.datePattern(locale, "d MMM"), locale));
        this.shortDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.yearDateTimeFormat = new SimpleDateFormat(AppUtils.datePattern(timeLocale, "d MMMM yyyy, " + time), timeLocale);
        this.yearDateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.dateFormat = new SimpleDateFormat(AppUtils.datePattern(locale, "d MMMM"), locale);
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.yearDateFormat = new SimpleDateFormat(AppUtils.datePattern(locale, "d MMMM yyyy"), locale);
        this.yearDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.weekDayFormat = new SimpleDateFormat(AppUtils.datePattern(locale, "EEE d"), locale);
        this.weekDayFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /** "Fri, 2 October" of a local time read as UTC */
    public String day(long localTime) {
        return capitalize(this.dayFormat.format(new Date(localTime)));
    }

    /** "2 October, 16:25" of a local time read as UTC, "6 February 2027, 17:59" in another year */
    public String dateTime(long localTime) {
        java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(localTime);
        return (calendar.get(java.util.Calendar.YEAR) == currentYear() ? this.dateTimeFormat : this.yearDateTimeFormat).format(new Date(localTime));
    }

    /** "23 Oct, 11:43" of a local time read as UTC, for the narrow widgets */
    public String shortDateTime(long localTime) {
        return this.shortDateTimeFormat.format(new Date(localTime));
    }

    /** "23 Oct" of a local time read as UTC */
    public String shortDate(long localTime) {
        return this.shortDateFormat.format(new Date(localTime));
    }

    /** "22 December" of a local time read as UTC, "6 February 2027" in another year */
    public String date(long localTime) {
        java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(localTime);
        return (calendar.get(java.util.Calendar.YEAR) == currentYear() ? this.dateFormat : this.yearDateFormat).format(new Date(localTime));
    }

    /** "Fri 9" of a local time read as UTC, for the midnights along a chart */
    public String weekDay(long localTime) {
        return this.weekDayFormat.format(new Date(localTime));
    }

    public String clock(long localTime) {
        return time(localTime);
    }

    /** "♋ Cancer" */
    public String zodiacSign(int sign) {
        return symbol(sign) + "\u00a0" + this.resources.getStringArray(R.array.zodiac_signs)[sign];
    }

    /** "11 h 33 min" */
    public String duration(long millis) {
        long minutes = Math.round(millis / 60000.0);
        return this.resources.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60);
    }

    /** "+2 min 41 s" or "-3 min 5 s", the change of the day length */
    public String durationChange(long millis) {
        long seconds = Math.round(Math.abs(millis) / 1000.0);
        String sign = millis < 0 ? "\u2212" : "+";
        return sign + (seconds >= 60
                ? this.resources.getString(R.string.duration_minutes_seconds, seconds / 60, seconds % 60)
                : this.resources.getString(R.string.duration_seconds, seconds));
    }

    /** The name of the solstice or equinox, the seasons swapped in the southern hemisphere */
    public String seasonName(int season, boolean southernHemisphere) {
        return this.resources.getStringArray(R.array.sun_seasons)[southernHemisphere ? (season + 2) % 4 : season];
    }

    /** "Annular solar eclipse" */
    public String eclipseName(org.meteosphere.astro.Eclipse eclipse) {
        String[] names = this.resources.getStringArray(eclipse.solar ? R.array.solar_eclipses : R.array.lunar_eclipses);
        String types = eclipse.solar ? "TAHP" : "TPN";
        return names[Math.max(0, types.indexOf(eclipse.type))];
    }

    /** The picture of the eclipse kind */
    public static int eclipseIcon(org.meteosphere.astro.Eclipse eclipse) {
        if (eclipse.solar) {
            switch (eclipse.type) {
                case org.meteosphere.astro.Eclipse.TOTAL:
                case org.meteosphere.astro.Eclipse.HYBRID:
                    return R.drawable.ic_eclipse_solar_total;
                case org.meteosphere.astro.Eclipse.ANNULAR:
                    return R.drawable.ic_eclipse_solar_annular;
                default:
                    return R.drawable.ic_eclipse_solar_partial;
            }
        }
        switch (eclipse.type) {
            case org.meteosphere.astro.Eclipse.TOTAL:
                return R.drawable.ic_eclipse_lunar_total;
            case org.meteosphere.astro.Eclipse.PARTIAL:
                return R.drawable.ic_eclipse_lunar_partial;
            default:
                return R.drawable.ic_eclipse_lunar_penumbral;
        }
    }

    /** The icon of the season: 0 spring, 1 summer, 2 autumn, 3 winter, as the names of seasonName */
    public static int seasonIcon(int season) {
        switch (season) {
            case 0:
                return R.drawable.ic_season_spring;
            case 1:
                return R.drawable.ic_season_summer;
            case 2:
                return R.drawable.ic_season_autumn;
            default:
                return R.drawable.ic_season_winter;
        }
    }

    private static final int[] ZODIAC_ICONS = {
            R.drawable.ic_zodiac_aries, R.drawable.ic_zodiac_taurus, R.drawable.ic_zodiac_gemini,
            R.drawable.ic_zodiac_cancer, R.drawable.ic_zodiac_leo, R.drawable.ic_zodiac_virgo,
            R.drawable.ic_zodiac_libra, R.drawable.ic_zodiac_scorpio, R.drawable.ic_zodiac_sagittarius,
            R.drawable.ic_zodiac_capricorn, R.drawable.ic_zodiac_aquarius, R.drawable.ic_zodiac_pisces};

    /** The icon of the sign, 0 Aries to 11 Pisces, the glyph of symbol drawn like the other icons */
    public static int zodiacIcon(int sign) {
        return ZODIAC_ICONS[sign];
    }

    /** "Libra" */
    public String zodiacName(int sign) {
        return this.resources.getStringArray(R.array.zodiac_signs)[sign];
    }

    /** The name in the "Sun in Libra" form without its symbol */
    public String sunInSignName(int sign) {
        return this.resources.getStringArray(R.array.sun_in_sign)[sign];
    }

    /** "♎" as text, not emoji */
    public static String symbol(int sign) {
        return new String(Character.toChars(ARIES_SYMBOL + sign)) + TEXT_PRESENTATION;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    public String phaseName(int phase) {
        return this.resources.getStringArray(R.array.moon_phases)[phase];
    }

    public String illumination(double fraction) {
        return AppUtils.percent(this.locale, Math.round(fraction * 100));
    }

    // The year now, not when the formatter was made: a screen may stay open into the next
    private static int currentYear() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    }

    public String rise(Moon.Day day) {
        return up(day.rise);
    }

    public String set(Moon.Day day) {
        return down(day.set);
    }

    /** "↑ 06:32", a dash for NONE */
    public String up(long localTime) {
        return "\u2191\u2009" + time(localTime);
    }

    /** "↓ 18:05", a dash for NONE */
    public String down(long localTime) {
        return "\u2193\u2009" + time(localTime);
    }

    private String time(long localTime) {
        return localTime == Moon.NONE ? "\u2014" : this.timeFormat.format(new Date(localTime));
    }
}
