package org.meteosphere.astro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * A solar or lunar eclipse of NASA's Five Millennium Catalogs (Espenak and Meeus), kept in the eclipses.csv asset,
 * and how it looks from a place: the catalog gives the time and kind, the visibility is computed here.
 */
public final class Eclipse {

    public static final char TOTAL = 'T';
    public static final char ANNULAR = 'A';
    public static final char HYBRID = 'H';
    public static final char PARTIAL = 'P';
    public static final char PENUMBRAL = 'N';

    private static final long MINUTE_MS = 60000;
    private static final double EARTH_RADIUS_KM = 6378.14;
    private static final double MOON_RADIUS_KM = 1737.4;
    private static final double SUN_RADIUS_KM = 696000;
    private static final double AU_KM = 149597870.7;
    // A solar eclipse lasts up to about six hours over the Earth, so a place sees it within this of the greatest
    private static final long SOLAR_WINDOW_MS = 4 * 60 * MINUTE_MS;

    public final boolean solar;
    /** TOTAL, ANNULAR, HYBRID or PARTIAL for the Sun, TOTAL, PARTIAL or PENUMBRAL for the Moon */
    public final char type;
    /** UTC instant of the greatest eclipse */
    public final long greatest;
    /** Magnitude of the solar eclipse, umbral magnitude of the lunar one */
    public final double magnitude;
    /** Lunar eclipses: how long the penumbral, partial and total phases last, millis, 0 if there is none */
    public final long penumbralDuration;
    public final long partialDuration;
    public final long totalDuration;

    /** How the eclipse looks from a place; times are UTC instants to the minute */
    public static final class Local {
        public final boolean visible;
        /** The part seen: from start to end, the greatest at max; NONE when not visible */
        public final long start;
        public final long max;
        public final long end;
        /** Solar eclipses: share of the Sun's disk covered at max, 0..1 */
        public final double obscuration;

        Local(boolean visible, long start, long max, long end, double obscuration) {
            this.visible = visible;
            this.start = start;
            this.max = max;
            this.end = end;
            this.obscuration = obscuration;
        }
    }

    Eclipse(boolean solar, char type, long greatest, double magnitude, long penumbralDuration, long partialDuration, long totalDuration) {
        this.solar = solar;
        this.type = type;
        this.greatest = greatest;
        this.magnitude = magnitude;
        this.penumbralDuration = penumbralDuration;
        this.partialDuration = partialDuration;
        this.totalDuration = totalDuration;
    }

    /** Lines of "S,time,type,magnitude" or "L,time,type,umbral magnitude,penumbral,partial,total minutes"; # starts a comment */
    public static List<Eclipse> parse(Reader reader) throws IOException {
        List<Eclipse> eclipses = new ArrayList<>();
        BufferedReader lines = new BufferedReader(reader);
        String line;
        while ((line = lines.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split(",");
            boolean solar = fields[0].equals("S");
            long greatest = LocalDateTime.parse(fields[1]).toInstant(ZoneOffset.UTC).toEpochMilli();
            double magnitude = Double.parseDouble(fields[3]);
            if (solar) {
                eclipses.add(new Eclipse(true, fields[2].charAt(0), greatest, magnitude, 0, 0, 0));
            } else {
                eclipses.add(new Eclipse(false, fields[2].charAt(0), greatest, magnitude,
                        minutes(fields[4]), minutes(fields[5]), minutes(fields[6])));
            }
        }
        return eclipses;
    }

    private static long minutes(String value) {
        return Math.round(Double.parseDouble(value) * MINUTE_MS);
    }

    /** The eclipses not over yet at the instant, at most count of them */
    public static List<Eclipse> upcoming(List<Eclipse> eclipses, long utcMillis, int count) {
        return upcoming(eclipses, utcMillis, count, null);
    }

    /** The solar or lunar eclipses not over yet at the instant, at most count of them; null solar for both */
    public static List<Eclipse> upcoming(List<Eclipse> eclipses, long utcMillis, int count, Boolean solar) {
        List<Eclipse> upcoming = new ArrayList<>();
        for (Eclipse eclipse : eclipses) {
            if (solar != null && eclipse.solar != solar) {
                continue;
            }
            long end = eclipse.solar ? eclipse.greatest + SOLAR_WINDOW_MS : eclipse.greatest + eclipse.penumbralDuration / 2;
            if (end > utcMillis) {
                upcoming.add(eclipse);
                if (upcoming.size() == count) {
                    break;
                }
            }
        }
        return upcoming;
    }

    /** How it looks from the place */
    public Local local(double lat, double lng) {
        return this.solar ? solarLocal(lat, lng) : lunarLocal(lat, lng);
    }

    // Minute by minute around the greatest eclipse: the Moon's disk over the Sun's as seen from the place,
    // while the Sun is up. The Moon's parallax, up to a degree, decides where a solar eclipse is seen.
    private Local solarLocal(double lat, double lng) {
        long start = Moon.NONE;
        long end = Moon.NONE;
        long max = Moon.NONE;
        double maxObscuration = 0;
        long from = Moon.roundToMinute(this.greatest - SOLAR_WINDOW_MS);
        for (long t = from; t <= this.greatest + SOLAR_WINDOW_MS; t += MINUTE_MS) {
            if (Sun.altitude(t, lat, lng) < -0.8333) {
                continue;
            }
            double obscuration = obscuration(t, lat, lng);
            if (obscuration > 0) {
                if (start == Moon.NONE) {
                    start = t;
                }
                end = t;
                if (obscuration > maxObscuration) {
                    maxObscuration = obscuration;
                    max = t;
                }
            }
        }
        return new Local(start != Moon.NONE, start, max, end, maxObscuration);
    }

    // Share of the Sun's disk covered by the Moon's at the instant as seen from the place
    static double obscuration(long utcMillis, double lat, double lng) {
        double t = Moon.centuries(utcMillis);
        double[] moon = Moon.moonPosition(t);
        double[] sun = Sun.position(t);
        double[] moonVector = topocentric(utcMillis, moon[0], moon[1], moon[2], lat, lng);
        double[] sunVector = topocentric(utcMillis, sun[0], 0, sun[1] * AU_KM, lat, lng);
        double moonDistance = length(moonVector);
        double sunDistance = length(sunVector);
        double separation = Math.acos(Math.max(-1, Math.min(1, dot(moonVector, sunVector) / (moonDistance * sunDistance))));
        double moonRadius = Math.asin(MOON_RADIUS_KM / moonDistance);
        double sunRadius = Math.asin(SUN_RADIUS_KM / sunDistance);
        return overlap(sunRadius, moonRadius, separation) / (Math.PI * sunRadius * sunRadius);
    }

    // Vector in km from the place to a body at the ecliptic longitude, latitude and distance, in equatorial axes
    private static double[] topocentric(long utcMillis, double longitude, double latitude, double distance, double lat, double lng) {
        double t = Moon.centuries(utcMillis);
        double[] equatorial = Moon.equatorial(longitude, latitude, t);
        double ra = equatorial[0];
        double dec = equatorial[1];
        // The place on the ellipsoid, geocentric (Meeus 11)
        double u = Math.atan(0.99664719 * Math.tan(Math.toRadians(lat)));
        double rhoSin = 0.99664719 * Math.sin(u);
        double rhoCos = Math.cos(u);
        double siderealTime = Math.toRadians(Moon.siderealTime(Moon.julianDay(utcMillis)) + lng);
        return new double[]{
                distance * Math.cos(dec) * Math.cos(ra) - EARTH_RADIUS_KM * rhoCos * Math.cos(siderealTime),
                distance * Math.cos(dec) * Math.sin(ra) - EARTH_RADIUS_KM * rhoCos * Math.sin(siderealTime),
                distance * Math.sin(dec) - EARTH_RADIUS_KM * rhoSin
        };
    }

    // Area shared by two disks of the radii with the centers the distance apart, all small angles
    private static double overlap(double r1, double r2, double distance) {
        if (distance >= r1 + r2) {
            return 0;
        }
        if (distance <= Math.abs(r1 - r2)) {
            double r = Math.min(r1, r2);
            return Math.PI * r * r;
        }
        double a1 = Math.acos((distance * distance + r1 * r1 - r2 * r2) / (2 * distance * r1));
        double a2 = Math.acos((distance * distance + r2 * r2 - r1 * r1) / (2 * distance * r2));
        return r1 * r1 * a1 + r2 * r2 * a2
                - 0.5 * Math.sqrt((-distance + r1 + r2) * (distance + r1 - r2) * (distance - r1 + r2) * (distance + r1 + r2));
    }

    // The main phase, the umbral one or the penumbral one of a penumbral eclipse, as far as the Moon is up
    private Local lunarLocal(double lat, double lng) {
        long duration = this.type == PENUMBRAL ? this.penumbralDuration : this.partialDuration;
        long from = Moon.roundToMinute(this.greatest - duration / 2);
        long to = this.greatest + duration / 2;
        long start = Moon.NONE;
        long end = Moon.NONE;
        for (long t = from; t <= to; t += MINUTE_MS) {
            if (Moon.altitudeAboveRiseSet(t, lat, lng) > 0) {
                if (start == Moon.NONE) {
                    start = t;
                }
                end = t;
            }
        }
        if (start == Moon.NONE) {
            return new Local(false, Moon.NONE, Moon.NONE, Moon.NONE, 0);
        }
        long greatest = Moon.roundToMinute(this.greatest);
        long max = greatest < start ? start : (greatest > end ? end : greatest);
        return new Local(true, start, max, end, 0);
    }

    private static double length(double[] v) {
        return Math.sqrt(dot(v, v));
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
