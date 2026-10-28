package org.meteosphere.ui.locations;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.location.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;
import org.meteosphere.ui.icons.TemperatureColors;
import org.meteosphere.ui.icons.WeatherIcons;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.sky.SkyWeather;
import org.meteosphere.util.Units;
import org.meteosphere.util.WindText;

/**
 * A row of the nearby tab, made off the main thread from the saved list of places: the nearest town on its sky, a
 * section's title, or a place's tile, its weather icon made already.
 */
final class NearbyPlace {

    static final int TYPE_HERO = 0;
    static final int TYPE_HEADER = 1;
    static final int TYPE_TILE = 2;


    final int type;
    Place location;
    String header;
    String name;
    String distance;
    // Degrees clockwise from north, NaN when unknown or too near to tell
    float bearing = Float.NaN;
    String temperature;
    int temperatureColor;
    String condition;
    // The wind now, null when the lists show only strong winds and this one isn't
    String wind;
    boolean strongWind;
    Drawable icon;
    List<String> skyLayers;
    int skyColor;

    private NearbyPlace(int type) {
        this.type = type;
    }

    /**
     * Background thread: the rows for the saved places, or null when there are none fresh enough. The tiles' icons
     * are drawn for the surface's darkness, the town's on the sky for its own.
     */
    static ArrayList<NearbyPlace> load(Context context, boolean darkSurface) {
        AppSettings settings = AppSettings.getInstance();
        ArrayList<Place> saved;
        // An older list waits for the next search
        if (System.currentTimeMillis() - settings.getNearbyTime().getTimeInMillis() >= WeatherUpdater.NEARBY_FRESH_MS
                || (saved = settings.getNearbyPlaces()) == null) {
            return null;
        }
        // The nearby places are the towns of the GeoNames dump, it has no airports or stations
        ArrayList<Place> towns = new ArrayList<>();
        for (Place location : saved) {
            if (location.now != null) {
                towns.add(location);
            }
        }
        Collections.sort(towns, (a, b) -> Float.compare(a.distanceKm, b.distanceKm));

        Builder builder = new Builder(context, darkSurface, settings.getPosition());
        ArrayList<NearbyPlace> rows = new ArrayList<>();
        if (!towns.isEmpty()) {
            rows.add(builder.place(TYPE_HERO, towns.remove(0), false));
        }
        Resources res = context.getResources();
        if (!towns.isEmpty()) {
            rows.add(header(res.getString(R.string.nearby_header)));
            for (Place town : towns) {
                rows.add(builder.place(TYPE_TILE, town, !darkSurface));
            }
        }
        return rows;
    }

    private static NearbyPlace header(String title) {
        NearbyPlace row = new NearbyPlace(TYPE_HEADER);
        row.header = title;
        return row;
    }

    /** Makes the places' rows; the settings are read once for all of them */
    private static final class Builder {
        private final Context context;
        private final Resources res;
        private final boolean darkSurface;
        private final Location here;
        private final int temperatureUnit;
        private final int windUnit;
        private final int distanceUnit;
        private final boolean onlyStrongWind;
        private final int strongWind;
        private final WeatherIcons icons;

        Builder(Context context, boolean darkSurface, Location here) {
            this.context = context.getApplicationContext();
            this.res = this.context.getResources();
            this.darkSurface = darkSurface;
            this.here = here;
            AppSettings settings = AppSettings.getInstance();
            this.temperatureUnit = settings.temperatureUnit;
            this.windUnit = settings.windSpeedUnit;
            this.distanceUnit = settings.distanceUnit;
            this.onlyStrongWind = settings.onlyStrongWind;
            this.strongWind = this.res.getInteger(R.integer.strong_wind_mps);
            this.icons = WeatherIcons.getInstance(this.context);
        }

        NearbyPlace place(int type, Place location, boolean lightSurface) {
            NearbyPlace row = new NearbyPlace(type);
            row.location = location;
            row.name = location.displayName();
            row.distance = distance(location.distanceKm);
            if (this.here != null && Units.distance(location.distanceKm, this.distanceUnit) > 0) {
                Location there = new Location("");
                there.setLatitude(location.latitude);
                there.setLongitude(location.longitude);
                row.bearing = (this.here.bearingTo(there) + 360) % 360;
            }
            Weather now = location.now;
            int windSpeed = now.windSpeed;
            row.temperature = Units.temperatureText(Units.temperature(now.temperature, this.temperatureUnit), this.temperatureUnit) + "°";
            row.temperatureColor = TemperatureColors.forSurface(TemperatureColors.of(now.temperature), this.darkSurface);
            row.condition = now.description();
            if (!this.onlyStrongWind || windSpeed >= this.strongWind) {
                row.wind = WindText.of(this.context, windSpeed, this.windUnit);
                row.strongWind = windSpeed >= this.strongWind;
            }
            // Each row's its own, for its view
            row.icon = this.icons.getWeatherDrawable(now.icon(location.isDay()), lightSurface);
            if (type == TYPE_HERO) {
                row.skyLayers = SkyWeather.of(location);
                row.skyColor = SkyWeather.color(row.skyLayers);
            }
            return row;
        }

        private String distance(float km) {
            int converted = Units.distance(km, this.distanceUnit);
            if (converted <= 0) {
                return this.res.getString(this.distanceUnit == 0 ? R.string.distance_under_km : R.string.distance_under_mile);
            }
            String unit;
            switch (this.distanceUnit) {
                case 0:
                    unit = this.res.getStringArray(R.array.distance_units)[0];
                    break;
                case 1:
                    unit = this.res.getQuantityString(R.plurals.miles, converted);
                    break;
                default:
                    unit = "";
                    break;
            }
            return converted + " " + unit;
        }
    }
}
