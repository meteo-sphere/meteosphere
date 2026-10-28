package org.meteosphere.data.quake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Which earthquakes to notify about: strong ones anywhere and weaker ones near the user's locations.
 * Plain Java, so the rules are tested without Android.
 */
public final class EarthquakeAlerts {

    /** At most this many notifications at once, the newest, for when the phone was offline for long */
    public static final int MAX_ALERTS = 5;

    /** A location of the user to measure the distances from */
    public static final class Place {
        public final String name;
        public final double latitude;
        public final double longitude;

        public Place(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** An earthquake to notify about with the nearest place, null when there are no places */
    public static final class Alert {
        public final Earthquake earthquake;
        public final Place nearest;
        public final double distanceKm;

        Alert(Earthquake earthquake, Place nearest, double distanceKm) {
            this.earthquake = earthquake;
            this.nearest = nearest;
            this.distanceKm = distanceKm;
        }
    }

    private EarthquakeAlerts() {
    }

    /**
     * The earthquakes not notified yet of magnitude worldMagnitude and above anywhere, or nearer than nearbyKm
     * to a place; 0 turns either rule off. Oldest first, so the newest notification ends up on top.
     */
    public static List<Alert> select(List<Earthquake> earthquakes, Collection<String> notifiedIds, List<Place> places,
                                     double worldMagnitude, double nearbyKm) {
        List<Alert> alerts = new ArrayList<>();
        for (Earthquake earthquake : earthquakes) {
            if (notifiedIds.contains(earthquake.id)) {
                continue;
            }
            Place nearest = null;
            double distanceKm = Double.MAX_VALUE;
            for (Place place : places) {
                double distance = earthquake.distanceKm(place.latitude, place.longitude);
                if (distance < distanceKm) {
                    distanceKm = distance;
                    nearest = place;
                }
            }
            boolean isStrong = worldMagnitude > 0 && earthquake.magnitude >= worldMagnitude;
            boolean isNear = nearbyKm > 0 && nearest != null && distanceKm <= nearbyKm;
            if (isStrong || isNear) {
                alerts.add(new Alert(earthquake, nearest, distanceKm));
            }
        }
        alerts.sort(Comparator.comparingLong(alert -> alert.earthquake.time));
        if (alerts.size() > MAX_ALERTS) {
            return new ArrayList<>(alerts.subList(alerts.size() - MAX_ALERTS, alerts.size()));
        }
        return alerts;
    }
}
