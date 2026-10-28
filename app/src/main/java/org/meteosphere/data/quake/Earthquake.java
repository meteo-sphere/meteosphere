package org.meteosphere.data.quake;

import java.util.Comparator;
import java.util.Locale;

/** An earthquake from the USGS catalog. */
public final class Earthquake {

    /** The strongest first, the later of equal ones */
    public static final Comparator<Earthquake> STRONGEST_FIRST = (a, b) -> a.magnitude != b.magnitude
            ? Double.compare(b.magnitude, a.magnitude) : Long.compare(b.time, a.time);

    private static final double EARTH_RADIUS_KM = 6371.0088;

    public final String id;
    public final double magnitude;
    /** As USGS gives it, in English: "80 km ENE of Tadine, New Caledonia" or "Balleny Islands region" */
    public final String place;
    /** UTC millis */
    public final long time;
    public final double latitude;
    public final double longitude;
    public final double depthKm;
    /** The event page on the USGS site, null if there is none */
    public final String url;
    /** The magnitude scale as USGS names it, "mww", "mb", null if not given */
    public final String magnitudeType;
    /** Large and in an oceanic region, so tsunami.gov has a word on it; it doesn't mean a tsunami came */
    public final boolean tsunami;
    /** How many told USGS they felt it, 0 for none */
    public final int felt;
    /** The PAGER level of likely losses: "green", "yellow", "orange", "red", null if none */
    public final String alert;

    public Earthquake(String id, double magnitude, String place, long time, double latitude, double longitude, double depthKm, String url) {
        this(id, magnitude, place, time, latitude, longitude, depthKm, url, null, false, 0, null);
    }

    public Earthquake(String id, double magnitude, String place, long time, double latitude, double longitude, double depthKm, String url,
                      String magnitudeType, boolean tsunami, int felt, String alert) {
        this.id = id;
        this.magnitude = magnitude;
        this.place = place;
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
        this.depthKm = depthKm;
        this.url = url;
        this.magnitudeType = magnitudeType;
        this.tsunami = tsunami;
        this.felt = felt;
        this.alert = alert;
    }

    /** The place as USGS gives it, or the epicenter's coordinates when it gives none */
    public String placeName() {
        return this.place == null || this.place.isEmpty() ? String.format(Locale.US, "%.2f, %.2f", this.latitude, this.longitude) : this.place;
    }

    /** Initial great-circle bearing from a point to the epicenter, 0 north to 360 clockwise */
    public double bearingFrom(double lat, double lng) {
        double phi1 = Math.toRadians(lat);
        double phi2 = Math.toRadians(this.latitude);
        double dLng = Math.toRadians(this.longitude - lng);
        double y = Math.sin(dLng) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** Great-circle distance from the epicenter to a point, by the haversine formula */
    public double distanceKm(double lat, double lng) {
        double dLat = Math.toRadians(lat - this.latitude);
        double dLng = Math.toRadians(lng - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(lat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
