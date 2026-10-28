package org.meteosphere.data.local;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

// Sunrise and sunset of a local day, the way the app expects them: the minutes of the day,
// and the local time read as UTC in seconds. The wpr server has the same class.
final class SunTimes {

    private static final int MINUTES_PER_DAY = 24 * 60;

    final int riseMinutes;
    final int setMinutes;
    final int dayDuration;
    final long sunrise;
    final long sunset;

    private SunTimes(long dayStart, int riseSeconds, int setSeconds) {
        this.riseMinutes = riseSeconds / 60;
        this.setMinutes = setSeconds / 60;
        this.dayDuration = setMinutes - riseMinutes;
        this.sunrise = dayStart + riseSeconds;
        this.sunset = dayStart + setSeconds;
    }

    // Seconds of the date at 00:00 UTC, the date is yyyy-MM-dd
    private static long dayStart(String date) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.parse(date).getTime() / 1000;
    }

    private static int secondsOfDay(String time) {
        // 06:31:13
        return Integer.parseInt(time.substring(0, 2)) * 3600 + Integer.parseInt(time.substring(3, 5)) * 60
                + (time.length() >= 8 ? Integer.parseInt(time.substring(6, 8)) : 0);
    }

    // From the local times of a forecast, null if there is no sunrise or sunset
    static SunTimes fromLocalTimes(String date, String sunrise, String sunset) throws ParseException {
        if (sunrise == null || sunset == null) {
            return null;
        }
        return new SunTimes(dayStart(date), secondsOfDay(sunrise), secondsOfDay(sunset));
    }

    // From the instants of a forecast in seconds, null if there is no sunrise or sunset
    static SunTimes fromInstants(String date, Long sunrise, Long sunset, TimeZone timeZone) throws ParseException {
        if (sunrise == null || sunset == null) {
            return null;
        }
        long dayStart = dayStart(date);
        return new SunTimes(dayStart, localSecondsOfDay(sunrise, dayStart, timeZone), localSecondsOfDay(sunset, dayStart, timeZone));
    }

    private static int localSecondsOfDay(long instant, long dayStart, TimeZone timeZone) {
        long local = instant + timeZone.getOffset(instant * 1000) / 1000;
        return (int) Math.floorMod(local - dayStart, (long) MINUTES_PER_DAY * 60);
    }

    // By the NOAA solar equations (https://gml.noaa.gov/grad/solcalc/solareqns.PDF), within a couple of minutes.
    // StrictMath gives the same seconds on the server and in the app.
    static SunTimes calculate(String date, double lat, double lng, int utcOffsetMinutes) throws ParseException {
        long dayStart = dayStart(date);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(dayStart * 1000);
        double gamma = 2 * StrictMath.PI / 365 * (cal.get(Calendar.DAY_OF_YEAR) - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * StrictMath.cos(gamma) - 0.032077 * StrictMath.sin(gamma)
                - 0.014615 * StrictMath.cos(2 * gamma) - 0.040849 * StrictMath.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * StrictMath.cos(gamma) + 0.070257 * StrictMath.sin(gamma)
                - 0.006758 * StrictMath.cos(2 * gamma) + 0.000907 * StrictMath.sin(2 * gamma)
                - 0.002697 * StrictMath.cos(3 * gamma) + 0.00148 * StrictMath.sin(3 * gamma);
        double latRad = StrictMath.toRadians(lat);
        double cosHourAngle = StrictMath.cos(StrictMath.toRadians(90.833)) / (StrictMath.cos(latRad) * StrictMath.cos(decl))
                - StrictMath.tan(latRad) * StrictMath.tan(decl);
        if (cosHourAngle >= 1) {
            // Polar night
            return new SunTimes(dayStart, 0, 0);
        }
        if (cosHourAngle <= -1) {
            // Polar day, the app treats a day of 1440 minutes as daylight all the time
            return new SunTimes(dayStart, 0, MINUTES_PER_DAY * 60);
        }
        double hourAngle = StrictMath.toDegrees(StrictMath.acos(cosHourAngle));
        double riseMinutes = 720 - 4 * (lng + hourAngle) - eqTime + utcOffsetMinutes;
        double setMinutes = 720 - 4 * (lng - hourAngle) - eqTime + utcOffsetMinutes;
        return new SunTimes(dayStart, toSecondsOfDay(riseMinutes), toSecondsOfDay(setMinutes));
    }

    private static int toSecondsOfDay(double minutes) {
        long seconds = StrictMath.round(minutes * 60);
        return (int) Math.floorMod(seconds, (long) MINUTES_PER_DAY * 60);
    }
}
