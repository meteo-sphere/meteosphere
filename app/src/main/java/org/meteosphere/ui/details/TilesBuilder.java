package org.meteosphere.ui.details;

import android.content.res.Resources;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.R;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.chart.PrecipitationDrop;
import org.meteosphere.ui.quakes.EarthquakeFormatter;
import org.meteosphere.util.WindText;

/** The tiles of a city's details: the weather now, each parameter with its dial, and the earthquakes around */
final class TilesBuilder {

    // The earthquakes on the tile's radar: as far as this, nearer ones spread out on a logarithmic scale, on which
    // the inner ring stands at NEAR_KM
    private static final double RADAR_KM = 5000;
    private static final double RADAR_SCALE_KM = 200;
    // Near enough to the city for its nearest earthquake to take the tile from the world's strongest
    private static final double NEAR_KM = 1000;

    private static final int HUMIDITY_COLOR = 0xff8fd0ff;
    private static final int PRESSURE_COLOR = 0xffb9a8ff;
    // The dew point by the Magnus formula over water: its coefficients, the second in °C
    private static final double MAGNUS_B = 17.62;
    private static final double MAGNUS_C = 243.12;
    // The pressure dial from 40 mmHg under to 30 over the pressure usual at the place's height: 720 to 790 at sea level
    private static final double SEA_LEVEL_MMHG = 760;
    private static final int PRESSURE_DIAL_BELOW = 40;
    private static final int PRESSURE_DIAL_SPAN = 70;

    private final DetailsFormat format;
    private final Resources res;
    private final Place location;
    private final long nowUtc;
    // The M5+ earthquakes of the last day the world over, null while they load
    private final List<Earthquake> earthquakes;
    private final boolean earthquakesFailed;

    TilesBuilder(DetailsFormat format, Place location, long nowUtc, List<Earthquake> earthquakes, boolean earthquakesFailed) {
        this.format = format;
        this.res = format.res;
        this.location = location;
        this.nowUtc = nowUtc;
        this.earthquakes = earthquakes;
        this.earthquakesFailed = earthquakesFailed;
    }

    /**
     * The pressure usual at the height, mmHg, by the barometric formula wpr brings Foreca's sea level pressure down to
     * the place with: the pressure shown is the place's
     */
    static double usualPressure(int elevation) {
        return SEA_LEVEL_MMHG * Math.pow(1 - 2.25577e-5 * elevation, 5.25588);
    }

    // The tiles in the order of the settings, the earthquakes' last
    void build(DetailsModel model, Weather current, ArrayList<Weather> hours,
               DayWeather today, ArrayList<DayWeather> days) {
        for (int id : this.format.settings.parameters) {
            DetailsModel.Tile tile;
            switch (id) {
                case DetailsModelBuilder.WIND:
                    tile = wind(current, today);
                    break;
                case DetailsModelBuilder.HUMIDITY:
                    tile = humidity(current);
                    break;
                case DetailsModelBuilder.PRESSURE:
                    tile = pressure(current, hours);
                    break;
                case DetailsModelBuilder.GEOMAGNETIC:
                    tile = geomagnetic(current);
                    break;
                case DetailsModelBuilder.PRECIPITATION:
                    tile = precipitation(current, today, days);
                    break;
                default:
                    continue;
            }
            model.tiles.add(tile);
        }
        if (this.format.settings.parameters.contains(DetailsModelBuilder.EARTHQUAKES)) {
            model.tiles.add(earthquakes());
        }
    }

    private DetailsModel.Tile wind(Weather current, DayWeather today) {
        String[] directions = this.format.windDirections;
        DetailsModel.Tile tile = new DetailsModel.Tile();
        tile.icon = R.drawable.ic_details_wind;
        tile.title = this.res.getString(R.string.details_wind);
        tile.value = String.valueOf(this.format.windSpeed(current.windSpeed));
        tile.unit = WindText.unitName(this.format.context, current.windSpeed, this.format.settings.windUnit);
        boolean calm = current.windSpeed == 0 || current.windSector <= 0 || current.windSector >= directions.length;
        StringBuilder caption = new StringBuilder(calm ? directions[0] : directions[current.windSector]);
        if (today != null) {
            caption.append('\n').append(this.res.getString(R.string.details_wind_today_max,
                    String.valueOf(this.format.windSpeed(today.windSpeedMax))));
        }
        tile.caption = caption.toString();
        tile.dialMode = MetricDialView.COMPASS;
        tile.dialValue = ForecastFormat.windArrow(current.windSpeed, current.windSector);
        tile.north = directions[1];
        return tile;
    }

    private DetailsModel.Tile humidity(Weather current) {
        DetailsModel.Tile tile = new DetailsModel.Tile();
        tile.icon = R.drawable.ic_details_humidity;
        tile.title = this.res.getString(R.string.humidity);
        tile.value = String.valueOf(current.humidity);
        tile.unit = "%";
        if (current.humidity > 0) {
            double gamma = Math.log(current.humidity / 100.0) + MAGNUS_B * current.temperature / (MAGNUS_C + current.temperature);
            int dewPoint = (int) Math.round(MAGNUS_C * gamma / (MAGNUS_B - gamma));
            tile.caption = this.res.getString(R.string.details_dew_point, this.format.temperature(dewPoint));
        }
        tile.dialMode = MetricDialView.LEVEL;
        tile.dialValue = current.humidity / 100f;
        tile.dialColor = HUMIDITY_COLOR;
        return tile;
    }

    private DetailsModel.Tile pressure(Weather current, ArrayList<Weather> hours) {
        DetailsModel.Tile tile = new DetailsModel.Tile();
        tile.icon = R.drawable.ic_details_pressure;
        tile.title = this.res.getString(R.string.pressure);
        tile.value = this.format.pressure(current.pressure);
        String unit = this.res.getStringArray(R.array.pressure_units)[this.format.settings.pressureUnit];
        int later = current.pressure;
        for (Weather hour : hours) {
            if (hour.time - this.format.nowLocal <= 6 * DetailsFormat.HOUR_MS) {
                later = hour.pressure;
            }
        }
        int trend = later - current.pressure;
        // The unit is long: it goes under the value, with the trend
        tile.caption = unit + "\n" + (trend >= 1 ? "↗ " + this.res.getString(R.string.details_pressure_rising)
                : trend <= -1 ? "↘ " + this.res.getString(R.string.details_pressure_falling)
                : "→ " + this.res.getString(R.string.details_pressure_steady));
        tile.dialMode = MetricDialView.GAUGE;
        tile.dialValue = (float) ((current.pressure - usualPressure(this.location.elevation) + PRESSURE_DIAL_BELOW) / PRESSURE_DIAL_SPAN);
        tile.dialColor = PRESSURE_COLOR;
        return tile;
    }

    private DetailsModel.Tile geomagnetic(Weather current) {
        DetailsModel.Tile tile = new DetailsModel.Tile();
        tile.icon = R.drawable.ic_details_geomagnetic;
        tile.title = this.res.getString(R.string.details_magnetic);
        tile.value = String.valueOf(current.kp);
        String[] levels = this.res.getStringArray(R.array.details_kp_levels);
        tile.caption = this.res.getString(R.string.kp_index) + "\n"
                + levels[current.kp <= 2 ? 0 : current.kp <= 4 ? 1 : current.kp <= 6 ? 2 : 3];
        tile.dialMode = MetricDialView.GAUGE;
        tile.dialValue = current.kp / 9f;
        tile.dialColor = DetailsFormat.kpColor(current.kp);
        return tile;
    }

    // Today's, and the next wet day of the week
    private DetailsModel.Tile precipitation(Weather current, DayWeather today, ArrayList<DayWeather> days) {
        DetailsModel.Tile tile = new DetailsModel.Tile();
        tile.icon = R.drawable.ic_details_precipitation;
        tile.title = this.res.getString(R.string.precipitation);
        double sum = today != null ? today.precipitation : current.precipitation;
        tile.value = sum >= 0.05 ? this.format.precipitation(sum) : "0";
        tile.unit = this.format.precipitationUnit();
        StringBuilder caption = new StringBuilder(this.res.getString(R.string.details_precipitation_today));
        DayWeather next = null;
        for (DayWeather day : days) {
            if (day != today && day.precipitation >= 0.05) {
                next = day;
                break;
            }
        }
        caption.append('\n').append(next != null
                ? this.res.getString(R.string.details_precipitation_next, this.format.weekday(next.date), this.format.precipitation(next.precipitation))
                : this.res.getString(R.string.details_precipitation_week_none));
        tile.caption = caption.toString();
        tile.dialMode = MetricDialView.LEVEL;
        tile.dialValue = (float) Math.min(1, sum / 10);
        tile.dialColor = PrecipitationDrop.COLOR;
        return tile;
    }

    // The M5+ earthquakes of the last day: the nearest within NEAR_KM of the city, on a radar of those around it, or else
    // the world's strongest, its waves in its magnitude's colour
    private DetailsModel.Tile earthquakes() {
        DetailsModel.Tile tile = new DetailsModel.Tile();
        tile.icon = R.drawable.ic_details_earthquake;
        tile.title = this.res.getString(R.string.earthquakes);
        tile.opensEarthquakes = true;
        tile.north = this.format.windDirections[1];
        tile.dialMode = MetricDialView.RADAR;
        tile.radar = new ArrayList<>();
        List<Earthquake> earthquakes = this.earthquakes;
        if (earthquakes == null) {
            tile.value = "\u2014";
            tile.caption = this.res.getString(this.earthquakesFailed ? R.string.details_quakes_no_data : R.string.details_quakes_loading);
            return tile;
        }
        if (earthquakes.isEmpty()) {
            tile.value = "0";
            tile.caption = this.res.getString(R.string.details_quakes_day_none);
            return tile;
        }
        double latitude = this.location.latitude;
        double longitude = this.location.longitude;
        EarthquakeFormatter formatter = new EarthquakeFormatter(this.format.context, this.format.settings.is24Hour, this.format.settings.distanceUnit);
        Earthquake nearest = null;
        double nearestKm = Double.MAX_VALUE;
        Earthquake strongest = null;
        ArrayList<Earthquake> around = new ArrayList<>();
        for (Earthquake earthquake : earthquakes) {
            double km = earthquake.distanceKm(latitude, longitude);
            if (km < nearestKm) {
                nearestKm = km;
                nearest = earthquake;
            }
            if (strongest == null || earthquake.magnitude > strongest.magnitude) {
                strongest = earthquake;
            }
            if (km <= RADAR_KM) {
                around.add(earthquake);
            }
        }
        if (nearestKm > NEAR_KM) {
            tile.value = "M" + formatter.magnitude(strongest.magnitude);
            tile.caption = this.res.getString(R.string.details_quakes_strongest_day) + "\n" + formatter.shortDateTime(strongest.time, this.nowUtc);
            tile.dialMode = MetricDialView.WAVES;
            tile.dialColor = EarthquakeFormatter.magnitudeColor(strongest.magnitude);
            return tile;
        }
        tile.value = "M" + formatter.magnitude(nearest.magnitude);
        tile.caption = formatter.shortDateTime(nearest.time, this.nowUtc) + "\n" + formatter.agoLong(nearest.time, this.nowUtc);
        // Nearest first: the radar draws them last, on top
        around.sort((a, b) -> Double.compare(a.distanceKm(latitude, longitude), b.distanceKm(latitude, longitude)));
        for (Earthquake earthquake : around) {
            double km = earthquake.distanceKm(latitude, longitude);
            float fraction = (float) (Math.log1p(km / RADAR_SCALE_KM) / Math.log1p(RADAR_KM / RADAR_SCALE_KM));
            tile.radar.add(new MetricDialView.RadarPoint((float) earthquake.bearingFrom(latitude, longitude), fraction,
                    EarthquakeFormatter.magnitudeColor(earthquake.magnitude), 2.5f + (float) (earthquake.magnitude - 5) * 1.2f));
        }
        return tile;
    }
}
