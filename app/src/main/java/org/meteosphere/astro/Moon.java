package org.meteosphere.astro;

/**
 * The Moon as seen from a place: phase, illuminated fraction, moonrise and moonset.
 * Computed on the device, so it is the same for every data provider and weather source.
 *
 * Positions follow Meeus, "Astronomical Algorithms" (2nd ed.): the Moon by chapter 47 with the full tables
 * 47.A and 47.B (about 10" in longitude), the Sun by chapter 25 (in Sun). Phase times come within a couple of minutes
 * and moonrise and moonset within a couple of minutes of the USNO tables.
 *
 * Instants are UTC millis. Days and their rise, set and phase times use the app convention instead:
 * the local wall time read as UTC, the way Place and the forecasts keep them.
 */
public final class Moon {

    public static final int NEW_MOON = 0;
    public static final int WAXING_CRESCENT = 1;
    public static final int FIRST_QUARTER = 2;
    public static final int WAXING_GIBBOUS = 3;
    public static final int FULL_MOON = 4;
    public static final int WANING_GIBBOUS = 5;
    public static final int LAST_QUARTER = 6;
    public static final int WANING_CRESCENT = 7;

    /** No moonrise, moonset or principal phase during the day */
    public static final long NONE = Long.MIN_VALUE;

    static final long DAY_MS = 86400000L;
    static final long HOUR_MS = 3600000L;
    static final long MINUTE_MS = 60000L;
    // TT - UT is about 69 s in the 2020s, a few seconds either way don't matter at a minute's precision
    static final double DELTA_T_DAYS = 69.0 / 86400;
    private static final double AU_KM = 149597870.7;
    private static final double EARTH_RADIUS_KM = 6378.14;
    // Mean motion of the elongation, degrees a day
    private static final double MEAN_ELONGATION_RATE = 360 / 29.530588853;
    // Mean motion of the Moon along the ecliptic, degrees a day
    private static final double MEAN_LONGITUDE_RATE = 360 / 27.321582;
    /** Length of the synodic month, days */
    public static final double SYNODIC_MONTH = 29.530588853;

    /** A principal phase: NEW_MOON, FIRST_QUARTER, FULL_MOON or LAST_QUARTER, at a UTC instant to the minute */
    public static final class Phase {
        public final int phase;
        public final long time;

        Phase(int phase, long time) {
            this.phase = phase;
            this.time = time;
        }
    }

    /** A local day at a place */
    public static final class Day {
        /** One of the eight phases: the principal one if it happens during the day, otherwise the one in between */
        public final int phase;
        /** Local time of the principal phase to the minute, NONE if the day has none */
        public final long phaseTime;
        /** Share of the synodic month at local noon: 0 new moon, 0.25 first quarter, 0.5 full, 0.75 last quarter */
        public final double age;
        /** Illuminated fraction of the disk at local noon, 0..1 */
        public final double illumination;
        /** Local times of moonrise and moonset to the minute, NONE if the moon doesn't rise or set that day */
        public final long rise;
        public final long set;
        /** No rise and no set, and the moon stays above the horizon all day */
        public final boolean alwaysUp;

        Day(int phase, long phaseTime, double age, double illumination, long rise, long set, boolean alwaysUp) {
            this.phase = phase;
            this.phaseTime = phaseTime;
            this.age = age;
            this.illumination = illumination;
            this.rise = rise;
            this.set = set;
            this.alwaysUp = alwaysUp;
        }
    }

    private Moon() {
    }

    /**
     * The local day starting at localDayStart (00:00 local time read as UTC) at a place whose clocks
     * are utcOffsetMinutes ahead of UTC.
     */
    public static Day day(long localDayStart, int utcOffsetMinutes, double lat, double lng) {
        long offset = utcOffsetMinutes * 60000L;
        long start = localDayStart - offset;
        long end = start + DAY_MS;

        // The elongation passes at most one multiple of 90 degrees a day
        int startQuarter = (int) (elongation(start) / 90);
        int endQuarter = (int) (elongation(end) / 90);
        int phase;
        long phaseTime = NONE;
        if (startQuarter != endQuarter) {
            phase = endQuarter * 2;
            phaseTime = roundToMinute(refinePhase(start + DAY_MS / 2, endQuarter * 90)) + offset;
        } else {
            phase = startQuarter * 2 + 1;
        }

        long noon = start + DAY_MS / 2;
        double age = elongation(noon) / 360;
        double illumination = illumination(noon);

        long rise = NONE;
        long set = NONE;
        double prev = altitudeAboveRiseSet(start, lat, lng);
        boolean alwaysUp = prev > 0;
        for (int hour = 1; hour <= 24; hour++) {
            long t = start + hour * HOUR_MS;
            double cur = altitudeAboveRiseSet(t, lat, lng);
            if (prev <= 0 && cur > 0 && rise == NONE) {
                long crossing = crossing(t - HOUR_MS, t, lat, lng);
                if (crossing < end) {
                    rise = roundToMinute(crossing) + offset;
                }
            } else if (prev > 0 && cur <= 0 && set == NONE) {
                long crossing = crossing(t - HOUR_MS, t, lat, lng);
                if (crossing < end) {
                    set = roundToMinute(crossing) + offset;
                }
            }
            alwaysUp &= cur > 0;
            prev = cur;
        }
        return new Day(phase, phaseTime, age, illumination, rise, set, alwaysUp && rise == NONE && set == NONE);
    }

    /** The first principal phase after the instant */
    public static Phase nextPhase(long utcMillis) {
        double elongation = elongation(utcMillis);
        int quarter = (int) (elongation / 90) + 1;
        long time = refinePhase(utcMillis + Math.round((quarter * 90 - elongation) / MEAN_ELONGATION_RATE * DAY_MS), quarter * 90);
        if (time <= utcMillis) {
            // The elongation was a hair short of the quarter
            quarter++;
            time = refinePhase(time + DAY_MS * 7, quarter * 90);
        }
        return new Phase(quarter % 4 * 2, roundToMinute(time));
    }

    /** The tropical zodiac sign the Moon is in at the instant, 0 Aries to 11 Pisces: one every 30 degrees of longitude */
    public static int zodiacSign(long utcMillis) {
        return (int) (longitude(utcMillis) / 30);
    }

    /** UTC instant to the minute when the Moon enters the next sign after the instant, about every two and a half days */
    public static long nextSignChange(long utcMillis) {
        double longitude = longitude(utcMillis);
        double target = (Math.floor(longitude / 30) + 1) * 30;
        long t = utcMillis + Math.round((target - longitude) / MEAN_LONGITUDE_RATE * DAY_MS);
        for (int i = 0; i < 10; i++) {
            double diff = normalize180(longitude(t) - target);
            double rate = normalize180(longitude(t + HOUR_MS) - longitude(t)) / HOUR_MS;
            long step = Math.round(-diff / rate);
            t += step;
            if (Math.abs(step) < 1000) {
                break;
            }
        }
        return roundToMinute(t);
    }

    /** The Moon's geocentric altitude in degrees over the horizon of the place at the instant */
    public static double altitude(long utcMillis, double lat, double lng) {
        double[] moon = moonPosition(centuries(utcMillis));
        return altitude(utcMillis, moon[0], moon[1], lat, lng);
    }

    /** Distance between the centers of the Earth and the Moon at the instant, in km */
    public static double distance(long utcMillis) {
        return moonPosition(centuries(utcMillis))[2];
    }

    /** A perigee or an apogee: when the Moon is nearest to or farthest from the Earth in its orbit, and how far */
    public static final class Apsis {
        public final boolean perigee;
        public final long time;
        public final double distanceKm;

        Apsis(boolean perigee, long time, double distanceKm) {
            this.perigee = perigee;
            this.time = time;
            this.distanceKm = distanceKm;
        }
    }

    /** The first perigee or apogee after the instant, to the minute: the turn of the distance found hour by hour, then narrowed */
    public static Apsis nextApsis(long utcMillis) {
        double previous = distance(utcMillis);
        double current = distance(utcMillis + HOUR_MS);
        for (long t = utcMillis + HOUR_MS; ; t += HOUR_MS) {
            double next = distance(t + HOUR_MS);
            boolean perigee = current < previous && current <= next;
            if (perigee || (current > previous && current >= next)) {
                long low = t - HOUR_MS;
                long high = t + HOUR_MS;
                while (high - low > MINUTE_MS) {
                    long a = low + (high - low) / 3;
                    long b = high - (high - low) / 3;
                    if ((distance(a) < distance(b)) == perigee) {
                        high = b;
                    } else {
                        low = a;
                    }
                }
                long time = roundToMinute((low + high) / 2);
                return new Apsis(perigee, time, distance(time));
            }
            previous = current;
            current = next;
        }
    }

    // Geocentric ecliptic longitude for the equinox of the date, 0..360; the nutation, under 20", is left out
    static double longitude(long utcMillis) {
        return moonPosition(centuries(utcMillis))[0];
    }

    /** Share of the synodic month at the instant: 0 new moon, 0.25 first quarter, 0.5 full, 0.75 last quarter */
    public static double age(long utcMillis) {
        return elongation(utcMillis) / 360;
    }

    /** Illuminated fraction of the disk at the instant, 0..1 */
    public static double illumination(long utcMillis) {
        double t = centuries(utcMillis);
        double[] moon = moonPosition(t);
        double[] sun = Sun.position(t);
        // Meeus 48.2 and 48.3, with the geocentric elongation psi
        double cosPsi = Math.cos(Math.toRadians(moon[1])) * Math.cos(Math.toRadians(moon[0] - sun[0]));
        double psi = Math.acos(cosPsi);
        double sunKm = sun[1] * AU_KM;
        double phaseAngle = Math.atan2(sunKm * Math.sin(psi), moon[2] - sunKm * cosPsi);
        return (1 + Math.cos(phaseAngle)) / 2;
    }

    // Instant near the guess when the elongation is the target, by Newton's method
    /**
     * The moon's disc as seen from a place at the instant, in radians: [0] the phase angle, the sun's angle from the
     * observer as seen from the moon, 0 full to pi new; [1] where its bright limb points and [2] where its north pole
     * points, each from the local vertical, positive toward the left of the sky as one faces it (from north through
     * east). Meeus 48.5 for the bright limb and 14.1 for the parallactic angle that turns both from celestial north to
     * the vertical; the pole is taken as the ecliptic's, from which the moon's axis is 1.5 degrees off, its librations
     * left out.
     */
    public static double[] disc(long utcMillis, double lat, double lng) {
        double t = centuries(utcMillis);
        double[] moon = moonPosition(t);
        double[] sun = Sun.position(t);
        double cosPsi = Math.cos(Math.toRadians(moon[1])) * Math.cos(Math.toRadians(moon[0] - sun[0]));
        double psi = Math.acos(cosPsi);
        double sunKm = sun[1] * AU_KM;
        double phaseAngle = Math.atan2(sunKm * Math.sin(psi), moon[2] - sunKm * cosPsi);
        double[] moonEq = equatorial(moon[0], moon[1], t);
        double[] sunEq = equatorial(sun[0], 0, t);
        double epsilon = Math.toRadians(23.4392911 - 0.0130042 * t);
        double limb = positionAngle(moonEq, sunEq[0], sunEq[1]);
        double pole = positionAngle(moonEq, Math.toRadians(270), Math.PI / 2 - epsilon);
        double phi = Math.toRadians(lat);
        double hourAngle = Math.toRadians(siderealTime(julianDay(utcMillis)) + lng) - moonEq[0];
        double parallactic = Math.atan2(Math.sin(hourAngle),
                Math.tan(phi) * Math.cos(moonEq[1]) - Math.sin(moonEq[1]) * Math.cos(hourAngle));
        return new double[]{phaseAngle, limb - parallactic, pole - parallactic};
    }

    // The position angle of the moon's bright limb, from celestial north through east, in radians: Meeus 48.5
    static double brightLimb(double t) {
        double[] moon = moonPosition(t);
        double[] sun = Sun.position(t);
        double[] sunEq = equatorial(sun[0], 0, t);
        return positionAngle(equatorial(moon[0], moon[1], t), sunEq[0], sunEq[1]);
    }

    // The position angle of a point of the sky (right ascension and declination in radians) as seen from a body there,
    // from celestial north through east: Meeus 48.5
    private static double positionAngle(double[] at, double ra, double dec) {
        return Math.atan2(Math.cos(dec) * Math.sin(ra - at[0]),
                Math.sin(dec) * Math.cos(at[1]) - Math.cos(dec) * Math.sin(at[1]) * Math.cos(ra - at[0]));
    }

    private static long refinePhase(long guess, double target) {
        long t = guess;
        for (int i = 0; i < 10; i++) {
            double diff = normalize180(elongation(t) - target);
            double rate = normalize180(elongation(t + HOUR_MS) - elongation(t)) / HOUR_MS;
            long step = Math.round(-diff / rate);
            t += step;
            if (Math.abs(step) < 1000) {
                break;
            }
        }
        return t;
    }

    static long roundToMinute(long millis) {
        return Math.floorDiv(millis + 30000, 60000) * 60000;
    }

    // Moon minus Sun in geocentric longitude, 0..360. The nutation is the same for both and cancels out.
    static double elongation(long utcMillis) {
        double t = centuries(utcMillis);
        return normalize360(moonPosition(t)[0] - Sun.position(t)[0]);
    }

    // Instant of the rise or set between t0 and t1 by bisection, to 10 seconds
    private static long crossing(long t0, long t1, double lat, double lng) {
        boolean above0 = altitudeAboveRiseSet(t0, lat, lng) > 0;
        while (t1 - t0 > 10000) {
            long mid = (t0 + t1) >>> 1;
            if ((altitudeAboveRiseSet(mid, lat, lng) > 0) == above0) {
                t0 = mid;
            } else {
                t1 = mid;
            }
        }
        return (t0 + t1) / 2;
    }

    // Geocentric altitude in degrees above the one of moonrise and moonset (Meeus 15): the upper limb
    // on the horizon with the standard refraction, so it is positive while the moon is up
    static double altitudeAboveRiseSet(long utcMillis, double lat, double lng) {
        double t = centuries(utcMillis);
        double[] moon = moonPosition(t);
        double parallax = Math.toDegrees(Math.asin(EARTH_RADIUS_KM / moon[2]));
        return altitude(utcMillis, moon[0], moon[1], lat, lng) - (0.7275 * parallax - 0.5667);
    }

    // Geocentric altitude in degrees of a body at the ecliptic longitude and latitude, for the equinox of the date
    static double altitude(long utcMillis, double longitude, double latitude, double lat, double lng) {
        double jd = julianDay(utcMillis);
        double[] equatorial = equatorial(longitude, latitude, centuries(utcMillis));
        double phi = Math.toRadians(lat);
        double hourAngle = Math.toRadians(siderealTime(jd) + lng) - equatorial[0];
        return Math.toDegrees(Math.asin(Math.sin(phi) * Math.sin(equatorial[1]) + Math.cos(phi) * Math.cos(equatorial[1]) * Math.cos(hourAngle)));
    }

    // Right ascension and declination in radians of the ecliptic longitude and latitude in degrees
    static double[] equatorial(double longitude, double latitude, double t) {
        double lambda = Math.toRadians(longitude);
        double beta = Math.toRadians(latitude);
        double epsilon = Math.toRadians(23.4392911 - 0.0130042 * t);
        double ra = Math.atan2(Math.sin(lambda) * Math.cos(epsilon) - Math.tan(beta) * Math.sin(epsilon), Math.cos(lambda));
        double dec = Math.asin(Math.sin(beta) * Math.cos(epsilon) + Math.cos(beta) * Math.sin(epsilon) * Math.sin(lambda));
        return new double[]{ra, dec};
    }

    // Meeus 12.4, the mean sidereal time at Greenwich in degrees
    static double siderealTime(double jd) {
        double tu = (jd - 2451545.0) / 36525;
        return 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * tu * tu - tu * tu * tu / 38710000;
    }

    // Julian centuries of Dynamical Time since J2000.0
    static double centuries(long utcMillis) {
        return (julianDay(utcMillis) + DELTA_T_DAYS - 2451545.0) / 36525;
    }

    static double julianDay(long utcMillis) {
        return utcMillis / (double) DAY_MS + 2440587.5;
    }

    // Meeus 47.A: D, M, M', F, then the longitude term in 1e-6 degrees and the distance term in 1e-3 km
    private static final int[][] LONGITUDE_DISTANCE = {
            {0, 0, 1, 0, 6288774, -20905355},
            {2, 0, -1, 0, 1274027, -3699111},
            {2, 0, 0, 0, 658314, -2955968},
            {0, 0, 2, 0, 213618, -569925},
            {0, 1, 0, 0, -185116, 48888},
            {0, 0, 0, 2, -114332, -3149},
            {2, 0, -2, 0, 58793, 246158},
            {2, -1, -1, 0, 57066, -152138},
            {2, 0, 1, 0, 53322, -170733},
            {2, -1, 0, 0, 45758, -204586},
            {0, 1, -1, 0, -40923, -129620},
            {1, 0, 0, 0, -34720, 108743},
            {0, 1, 1, 0, -30383, 104755},
            {2, 0, 0, -2, 15327, 10321},
            {0, 0, 1, 2, -12528, 0},
            {0, 0, 1, -2, 10980, 79661},
            {4, 0, -1, 0, 10675, -34782},
            {0, 0, 3, 0, 10034, -23210},
            {4, 0, -2, 0, 8548, -21636},
            {2, 1, -1, 0, -7888, 24208},
            {2, 1, 0, 0, -6766, 30824},
            {1, 0, -1, 0, -5163, -8379},
            {1, 1, 0, 0, 4987, -16675},
            {2, -1, 1, 0, 4036, -12831},
            {2, 0, 2, 0, 3994, -10445},
            {4, 0, 0, 0, 3861, -11650},
            {2, 0, -3, 0, 3665, 14403},
            {0, 1, -2, 0, -2689, -7003},
            {2, 0, -1, 2, -2602, 0},
            {2, -1, -2, 0, 2390, 10056},
            {1, 0, 1, 0, -2348, 6322},
            {2, -2, 0, 0, 2236, -9884},
            {0, 1, 2, 0, -2120, 5751},
            {0, 2, 0, 0, -2069, 0},
            {2, -2, -1, 0, 2048, -4950},
            {2, 0, 1, -2, -1773, 4130},
            {2, 0, 0, 2, -1595, 0},
            {4, -1, -1, 0, 1215, -3958},
            {0, 0, 2, 2, -1110, 0},
            {3, 0, -1, 0, -892, 3258},
            {2, 1, 1, 0, -810, 2616},
            {4, -1, -2, 0, 759, -1897},
            {0, 2, -1, 0, -713, -2117},
            {2, 2, -1, 0, -700, 2354},
            {2, 1, -2, 0, 691, 0},
            {2, -1, 0, -2, 596, 0},
            {4, 0, 1, 0, 549, -1423},
            {0, 0, 4, 0, 537, -1117},
            {4, -1, 0, 0, 520, -1571},
            {1, 0, -2, 0, -487, -1739},
            {2, 1, 0, -2, -399, 0},
            {0, 0, 2, -2, -381, -4421},
            {1, 1, 1, 0, 351, 0},
            {3, 0, -2, 0, -340, 0},
            {4, 0, -3, 0, 330, 0},
            {2, -1, 2, 0, 327, 0},
            {0, 2, 1, 0, -323, 1165},
            {1, 1, -1, 0, 299, 0},
            {2, 0, 3, 0, 294, 0},
            {2, 0, -1, -2, 0, 8752},
    };

    // Meeus 47.B: D, M, M', F, then the latitude term in 1e-6 degrees
    private static final int[][] LATITUDE = {
            {0, 0, 0, 1, 5128122},
            {0, 0, 1, 1, 280602},
            {0, 0, 1, -1, 277693},
            {2, 0, 0, -1, 173237},
            {2, 0, -1, 1, 55413},
            {2, 0, -1, -1, 46271},
            {2, 0, 0, 1, 32573},
            {0, 0, 2, 1, 17198},
            {2, 0, 1, -1, 9266},
            {0, 0, 2, -1, 8822},
            {2, -1, 0, -1, 8216},
            {2, 0, -2, -1, 4324},
            {2, 0, 1, 1, 4200},
            {2, 1, 0, -1, -3359},
            {2, -1, -1, 1, 2463},
            {2, -1, 0, 1, 2211},
            {2, -1, -1, -1, 2065},
            {0, 1, -1, -1, -1870},
            {4, 0, -1, -1, 1828},
            {0, 1, 0, 1, -1794},
            {0, 0, 0, 3, -1749},
            {0, 1, -1, 1, -1565},
            {1, 0, 0, 1, -1491},
            {0, 1, 1, 1, -1475},
            {0, 1, 1, -1, -1410},
            {0, 1, 0, -1, -1344},
            {1, 0, 0, -1, -1335},
            {0, 0, 3, 1, 1107},
            {4, 0, 0, -1, 1021},
            {4, 0, -1, 1, 833},
            {0, 0, 1, -3, 777},
            {4, 0, -2, 1, 671},
            {2, 0, 0, -3, 607},
            {2, 0, 2, -1, 596},
            {2, -1, 1, -1, 491},
            {2, 0, -2, 1, -451},
            {0, 0, 3, -1, 439},
            {2, 0, 2, 1, 422},
            {2, 0, -3, -1, 421},
            {2, 1, -1, 1, -366},
            {2, 1, 0, 1, -351},
            {4, 0, 0, 1, 331},
            {2, -1, 1, 1, 315},
            {2, -2, 0, -1, 302},
            {0, 0, 1, 3, -283},
            {2, 1, 1, -1, -229},
            {1, 1, 0, -1, 223},
            {1, 1, 0, 1, 223},
            {0, 1, -2, -1, -220},
            {2, 1, -1, -1, -220},
            {1, 0, 1, 1, -185},
            {2, -1, -2, -1, 181},
            {0, 1, 2, 1, -177},
            {4, 0, -2, -1, 176},
            {4, -1, -1, -1, 166},
            {1, 0, 1, -1, -164},
            {4, 0, 1, -1, 132},
            {1, 0, -1, -1, -119},
            {4, -1, 0, -1, 115},
            {2, -2, 0, 1, 107},
    };

    // Geocentric ecliptic longitude and latitude in degrees and distance in km for the mean equinox
    // of the date, without the nutation (Meeus 47). t is in Julian centuries of Dynamical Time.
    static double[] moonPosition(double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double t4 = t3 * t;
        double meanLongitude = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841 - t4 / 65194000;
        double elongation = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868 - t4 / 113065000;
        double sunAnomaly = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000;
        double moonAnomaly = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699 - t4 / 14712000;
        double latitudeArgument = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000 + t4 / 863310000;
        double a1 = Math.toRadians(119.75 + 131.849 * t);
        double a2 = Math.toRadians(53.09 + 479264.290 * t);
        double a3 = Math.toRadians(313.45 + 481266.484 * t);
        // The eccentricity of the Earth orbit scales the terms with the Sun anomaly
        double e = 1 - 0.002516 * t - 0.0000074 * t2;

        double d = Math.toRadians(normalize360(elongation));
        double m = Math.toRadians(normalize360(sunAnomaly));
        double mp = Math.toRadians(normalize360(moonAnomaly));
        double f = Math.toRadians(normalize360(latitudeArgument));
        double lp = Math.toRadians(normalize360(meanLongitude));

        double sumL = 0;
        double sumR = 0;
        for (int[] row : LONGITUDE_DISTANCE) {
            double arg = row[0] * d + row[1] * m + row[2] * mp + row[3] * f;
            double scale = row[1] == 0 ? 1 : (Math.abs(row[1]) == 1 ? e : e * e);
            sumL += row[4] * scale * Math.sin(arg);
            sumR += row[5] * scale * Math.cos(arg);
        }
        double sumB = 0;
        for (int[] row : LATITUDE) {
            double arg = row[0] * d + row[1] * m + row[2] * mp + row[3] * f;
            double scale = row[1] == 0 ? 1 : (Math.abs(row[1]) == 1 ? e : e * e);
            sumB += row[4] * scale * Math.sin(arg);
        }
        sumL += 3958 * Math.sin(a1) + 1962 * Math.sin(lp - f) + 318 * Math.sin(a2);
        sumB += -2235 * Math.sin(lp) + 382 * Math.sin(a3) + 175 * Math.sin(a1 - f) + 175 * Math.sin(a1 + f)
                + 127 * Math.sin(lp - mp) - 115 * Math.sin(lp + mp);

        return new double[]{
                normalize360(meanLongitude + sumL / 1e6),
                sumB / 1e6,
                385000.56 + sumR / 1000
        };
    }

    static double normalize360(double degrees) {
        double result = degrees % 360;
        return result < 0 ? result + 360 : result;
    }

    static double normalize180(double degrees) {
        double result = normalize360(degrees);
        return result >= 180 ? result - 360 : result;
    }
}
