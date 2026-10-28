package org.meteosphere.astro;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * When to announce what happens at an instant, never at night: something between 6:00 and 23:00 at 6:00 that day
 * ("today at 14:12"), something at night at 20:00 the evening before ("tonight at 2:12"). For the moon phases and
 * the eclipses; plain Java, so the rules are tested without Android.
 */
public final class QuietHours {

    public static final int DAY_START_HOUR = 6;
    public static final int NIGHT_START_HOUR = 23;
    public static final int EVENING_HOUR = 20;

    private QuietHours() {
    }

    /** When to announce what happens at the instant: at 6:00 that day in the daytime, at 20:00 the evening before at night */
    public static long notifyTime(long phaseTime, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(phaseTime);
        if (!isNight(phaseTime, timeZone)) {
            setHour(calendar, DAY_START_HOUR);
            return calendar.getTimeInMillis();
        }
        if (calendar.get(Calendar.HOUR_OF_DAY) < DAY_START_HOUR) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }
        setHour(calendar, EVENING_HOUR);
        return calendar.getTimeInMillis();
    }

    /** From 23:00 to 6:00, when nothing is notified */
    public static boolean isNight(long time, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(time);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return hour >= NIGHT_START_HOUR || hour < DAY_START_HOUR;
    }

    /** The first 6:00 after the instant */
    public static long nextMorning(long time, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(time);
        if (calendar.get(Calendar.HOUR_OF_DAY) >= DAY_START_HOUR) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        setHour(calendar, DAY_START_HOUR);
        return calendar.getTimeInMillis();
    }

    private static void setHour(Calendar calendar, int hour) {
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
