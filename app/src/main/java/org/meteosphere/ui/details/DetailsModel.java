package org.meteosphere.ui.details;

import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.meteosphere.data.model.Place;

/** Everything a city's page shows, prepared off the main thread by DetailsModelBuilder */
final class DetailsModel {

    static final class Insight {
        int icon;
        String title;
        String text;
        String value;
    }

    static final class Tile {
        int icon;
        String title;
        String value;
        String unit;
        String caption;
        int dialMode;
        float dialValue;
        int dialColor;
        String north;
        // The earthquakes around the city, for the RADAR dial
        java.util.List<MetricDialView.RadarPoint> radar;
        // A tap opens the earthquakes around the city
        boolean opensEarthquakes;
    }

    int locationId;
    // When it was built, SystemClock.uptimeMillis(); 0 once it is out of date, to be built again
    long builtAt;
    // The forecast's own copy, for the screens opened from the page
    Place location;
    boolean hasData;
    // Only the weather now, the place's forecast never loaded and on its way: the page waits for it rather than
    // showing what it has and then moving it down under what comes
    boolean waitsForForecast;
    boolean isCurrentLocation;
    String name;
    // The other name of the place to show on a tap: the real one under a custom one
    String otherName;
    String temperature;
    Drawable icon;
    String condition;
    String highLow;
    String feelsLike;
    String dateTime;
    // The wind now: its speed and unit, where it blows from, null when calm, and an arrow's degrees, where it blows
    // to, NaN for none
    String windSpeed;
    String windUnit;
    String windDirection;
    float windArrow = Float.NaN;
    boolean strongWind;

    List<String> backgroundLayers;
    boolean isDay;
    int baseColor;

    String summary;
    ForecastStripView.Data hourly;
    final ArrayList<Insight> insights = new ArrayList<>();
    ArrayList<DailyRangeView.Day> days;
    ForecastStripView.Data dailyStrip;
    final ArrayList<Tile> tiles = new ArrayList<>();

    boolean hasSun;
    boolean hasSunNow;
    String sunTitle;
    float[] sunAltitudes;
    int sunNowMinutes;
    float sunNowAltitude;
    String sunrise;
    String sunset;
    String twilight;

    double moonAge;
    // The real moon as it is seen from the place now, the moon card's picture of it
    android.graphics.Bitmap moonPicture;
    boolean southernHemisphere;
    String moonPhase;
    String moonIllumination;
    String moonNext;
    int moonNextDays = Integer.MAX_VALUE;
    String moonrise;
    String moonset;

    String source;
    String updated;
    // The forecast as loaded: another one, not the same built again as the minute passes, flashes over the cards when
    // it changes what they show
    int forecastHash;
    // What the cards show of the weather now, of each hour by its time and of each day by its date
    String shownNow;
    final HashMap<Long, String> shownHours = new HashMap<>();
    final HashMap<Long, String> shownDays = new HashMap<>();

    /** Whether the weather now, or an hour or a day both show, reads otherwise than before: not an hour come or gone */
    boolean showsOtherWeatherThan(DetailsModel before) {
        return !Objects.equals(this.shownNow, before.shownNow) || differ(this.shownHours, before.shownHours)
                || differ(this.shownDays, before.shownDays);
    }

    private static boolean differ(HashMap<Long, String> shown, HashMap<Long, String> before) {
        for (Map.Entry<Long, String> entry : shown.entrySet()) {
            String was = before.get(entry.getKey());
            if (was != null && !was.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }
}
