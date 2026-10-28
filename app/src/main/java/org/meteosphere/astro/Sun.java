package org.meteosphere.astro;

/**
 * The Sun as seen from a place: sunrise, sunset, civil twilight, solar noon and the length of the day,
 * the zodiac sign, the solstices and equinoxes. Computed on the device like the Moon, with the solar position
 * of Meeus chapter 25, within a minute of the USNO tables. That position is good to 0.01 degree, a quarter
 * of an hour of the Sun's motion, so the solstices and equinoxes come from Meeus chapter 27 instead.
 *
 * Instants are UTC millis; days and their times are the local wall time read as UTC, as in Moon.
 */
public final class Sun {

    /** No such time during the day */
    public static final long NONE = Moon.NONE;

    public static final int MARCH_EQUINOX = 0;
    public static final int JUNE_SOLSTICE = 1;
    public static final int SEPTEMBER_EQUINOX = 2;
    public static final int DECEMBER_SOLSTICE = 3;

    // The center of the Sun at sunrise and sunset, with the refraction and the semidiameter, and at civil twilight
    private static final double RISE_SET_ALTITUDE = -0.8333;
    private static final double CIVIL_TWILIGHT_ALTITUDE = -6;
    // Mean motion of the Sun along the ecliptic, degrees a day
    private static final double MEAN_LONGITUDE_RATE = 360 / 365.2422;
    private static final long DAY_MS = Moon.DAY_MS;
    private static final long HOUR_MS = Moon.HOUR_MS;

    /** A local day at a place */
    public static final class Day {
        /** Local times of sunrise and sunset to the minute, NONE if the sun doesn't rise or set that day */
        public final long rise;
        public final long set;
        /** Local times of the start and end of the civil twilight, the sun 6 degrees below the horizon, or NONE */
        public final long dawn;
        public final long dusk;
        /** Local time of the solar noon, the sun highest */
        public final long noon;
        /** Altitude of the sun at the solar noon, degrees */
        public final double noonAltitude;
        /** Time with the sun above the horizon during the day, millis */
        public final long dayLength;
        /** The sun stays above the horizon all day, or below it */
        public final boolean polarDay;
        public final boolean polarNight;

        Day(long rise, long set, long dawn, long dusk, long noon, double noonAltitude, long dayLength, boolean polarDay, boolean polarNight) {
            this.rise = rise;
            this.set = set;
            this.dawn = dawn;
            this.dusk = dusk;
            this.noon = noon;
            this.noonAltitude = noonAltitude;
            this.dayLength = dayLength;
            this.polarDay = polarDay;
            this.polarNight = polarNight;
        }
    }

    /** A solstice or an equinox at a UTC instant to the minute */
    public static final class Season {
        public final int season;
        public final long time;

        Season(int season, long time) {
            this.season = season;
            this.time = time;
        }
    }

    private Sun() {
    }

    /** The local day starting at localDayStart (00:00 local time read as UTC) at a place utcOffsetMinutes ahead of UTC */
    public static Day day(long localDayStart, int utcOffsetMinutes, double lat, double lng) {
        long offset = utcOffsetMinutes * 60000L;
        long start = localDayStart - offset;
        long end = start + DAY_MS;
        long[] riseSet = crossings(start, end, lat, lng, RISE_SET_ALTITUDE);
        long[] twilight = crossings(start, end, lat, lng, CIVIL_TWILIGHT_ALTITUDE);
        long noon = transit(start + DAY_MS / 2 - Math.round(lng / 360 * DAY_MS), lng);
        double noonAltitude = altitude(noon, lat, lng);
        long dayLength = timeAbove(start, end, lat, lng);
        boolean polarDay = riseSet[0] == NONE && riseSet[1] == NONE && dayLength == DAY_MS;
        boolean polarNight = riseSet[0] == NONE && riseSet[1] == NONE && dayLength == 0;
        return new Day(local(riseSet[0], offset), local(riseSet[1], offset), local(twilight[0], offset), local(twilight[1], offset),
                Moon.roundToMinute(noon) + offset, noonAltitude, dayLength, polarDay, polarNight);
    }

    /** The Sun entering a zodiac sign at a UTC instant to the minute */
    public static final class SignChange {
        public final int sign;
        public final long time;

        SignChange(int sign, long time) {
            this.sign = sign;
            this.time = time;
        }
    }

    /**
     * The tropical zodiac sign the Sun is in at the instant, 0 Aries to 11 Pisces. Aries, Cancer, Libra and Capricorn
     * start at the equinoxes and solstices, so within a degree of those their more precise times decide.
     */
    public static int zodiacSign(long utcMillis) {
        double longitude = longitude(utcMillis);
        long quarter = Math.round(longitude / 90);
        if (Math.abs(longitude - quarter * 90) < 1) {
            int season = (int) (quarter % 4);
            return utcMillis >= seasonTime(year(utcMillis), season) ? season * 3 : (season * 3 + 11) % 12;
        }
        return (int) (longitude / 30);
    }

    /** The first sign the Sun enters after the instant, about a month on; at the equinoxes and solstices as nextSeason */
    public static SignChange nextSign(long utcMillis) {
        int sign = (zodiacSign(utcMillis) + 1) % 12;
        long time = reachLongitude(utcMillis, sign * 30);
        if (sign % 3 == 0) {
            time = seasonTime(year(time), sign / 3);
        }
        return new SignChange(sign, time);
    }

    /** UTC instant to the minute when the Sun enters the next sign after the instant */
    public static long nextSignChange(long utcMillis) {
        return nextSign(utcMillis).time;
    }

    private static int year(long utcMillis) {
        return java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).getYear();
    }

    /** The first solstice or equinox after the instant */
    public static Season nextSeason(long utcMillis) {
        int year = year(utcMillis);
        for (int y = year - 1; y <= year + 1; y++) {
            for (int season = MARCH_EQUINOX; season <= DECEMBER_SOLSTICE; season++) {
                long time = seasonTime(y, season);
                if (time > utcMillis) {
                    return new Season(season, time);
                }
            }
        }
        throw new AssertionError("No season after " + utcMillis);
    }

    // Meeus 27.B (years 2000 to 3000), 27.C: the mean instant in JDE and the 24 periodic terms A, B, C
    private static final double[][] SEASON_MEAN = {
            {2451623.80984, 365242.37404, 0.05169, -0.00411, -0.00057},
            {2451716.56767, 365241.62603, 0.00325, 0.00888, -0.00030},
            {2451810.21715, 365242.01767, -0.11575, 0.00337, 0.00078},
            {2451900.05952, 365242.74049, -0.06223, -0.00823, 0.00032},
    };
    private static final double[][] SEASON_TERMS = {
            {485, 324.96, 1934.136}, {203, 337.23, 32964.467}, {199, 342.08, 20.186}, {182, 27.85, 445267.112},
            {156, 73.14, 45036.886}, {136, 171.52, 22518.443}, {77, 222.54, 65928.934}, {74, 296.72, 3034.906},
            {70, 243.58, 9037.513}, {58, 119.81, 33718.147}, {52, 297.17, 150.678}, {50, 21.02, 2281.226},
            {45, 247.54, 29929.562}, {44, 325.15, 31555.956}, {29, 60.93, 4443.417}, {18, 155.12, 67555.328},
            {17, 288.79, 4562.452}, {16, 198.04, 62894.029}, {14, 199.76, 31436.921}, {12, 95.39, 14577.848},
            {12, 287.11, 31931.756}, {12, 320.81, 34777.259}, {9, 227.73, 1222.114}, {8, 15.45, 16859.074},
    };

    // UTC instant to the minute of the solstice or equinox of the year (Meeus 27)
    static long seasonTime(int year, int season) {
        double y = (year - 2000) / 1000.0;
        double[] mean = SEASON_MEAN[season];
        double jde0 = mean[0] + mean[1] * y + mean[2] * y * y + mean[3] * y * y * y + mean[4] * y * y * y * y;
        double t = (jde0 - 2451545.0) / 36525;
        double w = Math.toRadians(35999.373 * t - 2.47);
        double deltaLambda = 1 + 0.0334 * Math.cos(w) + 0.0007 * Math.cos(2 * w);
        double sum = 0;
        for (double[] term : SEASON_TERMS) {
            sum += term[0] * Math.cos(Math.toRadians(term[1] + term[2] * t));
        }
        double jde = jde0 + 0.00001 * sum / deltaLambda;
        return Moon.roundToMinute(Math.round((jde - Moon.DELTA_T_DAYS - 2440587.5) * DAY_MS));
    }

    // Instant to the minute after the start when the longitude reaches the target, by Newton's method
    private static long reachLongitude(long utcMillis, double target) {
        long t = utcMillis + Math.round(Moon.normalize360(target - longitude(utcMillis)) / MEAN_LONGITUDE_RATE * DAY_MS);
        for (int i = 0; i < 10; i++) {
            double diff = Moon.normalize180(longitude(t) - target);
            double rate = Moon.normalize180(longitude(t + DAY_MS) - longitude(t)) / DAY_MS;
            long step = Math.round(-diff / rate);
            t += step;
            if (Math.abs(step) < 1000) {
                break;
            }
        }
        return Moon.roundToMinute(t);
    }

    // The first rise above and set below the altitude between the instants, UTC to the minute, or NONE
    private static long[] crossings(long start, long end, double lat, double lng, double threshold) {
        long rise = NONE;
        long set = NONE;
        double prev = altitude(start, lat, lng) - threshold;
        for (long t = start + HOUR_MS; t <= end; t += HOUR_MS) {
            double cur = altitude(t, lat, lng) - threshold;
            if (prev <= 0 && cur > 0 && rise == NONE) {
                long crossing = crossing(t - HOUR_MS, t, lat, lng, threshold, 10000);
                if (crossing < end) {
                    rise = Moon.roundToMinute(crossing);
                }
            } else if (prev > 0 && cur <= 0 && set == NONE) {
                long crossing = crossing(t - HOUR_MS, t, lat, lng, threshold, 10000);
                if (crossing < end) {
                    set = Moon.roundToMinute(crossing);
                }
            }
            prev = cur;
        }
        return new long[]{rise, set};
    }

    // Instant of the crossing of the altitude between t0 and t1 by bisection, to the precision
    private static long crossing(long t0, long t1, double lat, double lng, double threshold, long precision) {
        boolean above0 = altitude(t0, lat, lng) > threshold;
        while (t1 - t0 > precision) {
            long mid = (t0 + t1) >>> 1;
            if ((altitude(mid, lat, lng) > threshold) == above0) {
                t0 = mid;
            } else {
                t1 = mid;
            }
        }
        return (t0 + t1) / 2;
    }

    // Time with the sun above the horizon between the instants, to the second so its change from day to day shows
    private static long timeAbove(long start, long end, double lat, double lng) {
        long total = 0;
        long segmentStart = altitude(start, lat, lng) > RISE_SET_ALTITUDE ? start : NONE;
        double prev = altitude(start, lat, lng) - RISE_SET_ALTITUDE;
        for (long t = start + HOUR_MS; t <= end; t += HOUR_MS) {
            double cur = altitude(t, lat, lng) - RISE_SET_ALTITUDE;
            if ((prev > 0) != (cur > 0)) {
                long crossing = crossing(t - HOUR_MS, t, lat, lng, RISE_SET_ALTITUDE, 1000);
                if (cur > 0) {
                    segmentStart = crossing;
                } else if (segmentStart != NONE) {
                    total += crossing - segmentStart;
                    segmentStart = NONE;
                }
            }
            prev = cur;
        }
        if (segmentStart != NONE) {
            total += end - segmentStart;
        }
        return Math.min(DAY_MS, Math.round(total / 1000.0) * 1000);
    }

    // Instant near the guess when the Sun crosses the meridian, the local hour angle zero
    private static long transit(long guess, double lng) {
        long t = guess;
        for (int i = 0; i < 5; i++) {
            double t2 = Moon.centuries(t);
            double[] equatorial = Moon.equatorial(position(t2)[0], 0, t2);
            double hourAngle = Moon.normalize180(Moon.siderealTime(Moon.julianDay(t)) + lng - Math.toDegrees(equatorial[0]));
            // The hour angle grows by about 360.9856 degrees a day
            long step = Math.round(-hourAngle / 360.9856 * DAY_MS);
            t += step;
            if (Math.abs(step) < 1000) {
                break;
            }
        }
        return t;
    }

    /** Geocentric altitude of the center of the Sun at the instant and place in degrees, without the refraction */
    public static double altitude(long utcMillis, double lat, double lng) {
        return Moon.altitude(utcMillis, position(Moon.centuries(utcMillis))[0], 0, lat, lng);
    }

    static double longitude(long utcMillis) {
        return position(Moon.centuries(utcMillis))[0];
    }

    private static long local(long utc, long offset) {
        return utc == NONE ? NONE : utc + offset;
    }

    // Geocentric longitude in degrees with the aberration, without the nutation, and distance in AU (Meeus 25)
    static double[] position(double t) {
        double t2 = t * t;
        double meanLongitude = 280.46646 + 36000.76983 * t + 0.0003032 * t2;
        double anomaly = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t2);
        double eccentricity = 0.016708634 - 0.000042037 * t - 0.0000001267 * t2;
        double center = (1.914602 - 0.004817 * t - 0.000014 * t2) * Math.sin(anomaly)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * anomaly)
                + 0.000289 * Math.sin(3 * anomaly);
        double trueAnomaly = anomaly + Math.toRadians(center);
        double distance = 1.000001018 * (1 - eccentricity * eccentricity) / (1 + eccentricity * Math.cos(trueAnomaly));
        return new double[]{Moon.normalize360(meanLongitude + center - 0.00569), distance};
    }
}
