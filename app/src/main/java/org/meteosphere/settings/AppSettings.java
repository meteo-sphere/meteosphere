package org.meteosphere.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.meteosphere.BuildConfig;
import org.meteosphere.R;
import org.meteosphere.addon.AddOns;
import org.meteosphere.data.WeatherClient;
import org.meteosphere.data.WeatherProviders;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;
import org.meteosphere.ui.notification.WeatherNotification;
import org.meteosphere.ui.widgets.settings.WidgetConfig;

/**
 * The app's settings and what it remembers between launches, in the default preferences, and its way to the places
 * LocationStore keeps. The settings are read once, as the instance is made: init() makes a new one at the app's start
 * and whenever they change. The units of a first start are those of the app's language
 */
public final class AppSettings {

    private static final String TAG = "AppSettings";

    /** The city page's tiles that aren't the weather's own; the Sun now is a card of the Sun tab and the sun's page */
    public static final int MOON_DETAIL_WIDGET = 7;
    public static final int EARTHQUAKE_DETAIL_WIDGET = 8;
    public static final int SUN_NOW_DETAIL_WIDGET = 9;

    /** The main screen's tabs, also as saved for the last one shown */
    public static final int TAB_NEARBY = 0;
    public static final int TAB_FAVORITES = 1;
    public static final int TAB_EARTHQUAKES = 2;
    public static final int TAB_MOON = 3;
    public static final int TAB_SUN = 4;
    // Their order until the settings change it; a tab missing from the saved order takes its place from here
    private static final List<Integer> DEFAULT_MAIN_TABS = List.of(TAB_NEARBY, TAB_FAVORITES, TAB_MOON, TAB_SUN, TAB_EARTHQUAKES);

    private static final String KEY_FAVORITES = "org.meteosphere.favorites";
    private static final String KEY_WIDGET_IDS = "org.meteosphere.weather_widgets";
    private static final String KEY_POSITION = "org.meteosphere.position";
    private static final String KEY_NEARBY = "org.meteosphere.nearby_places";
    private static final String KEY_NEARBY_TIME = "org.meteosphere.nearby_time";
    private static final String KEY_LOCATION_SEARCH_TIME = "org.meteosphere.location_search_time";
    private static final String KEY_SHOWN_SCREEN = "org.meteosphere.shown_screen";
    private static final String KEY_LAST_MAIN_TAB = "org.meteosphere.main_tab";
    private static final String KEY_DETAILS_DAYS_EXPANDED = "org.meteosphere.details_days_expanded";
    private static final String KEY_DAY_STRIP_HINT_SHOWN = "org.meteosphere.day_strip_hint_shown";
    private static final String KEY_SHOWN_PLACE = "org.meteosphere.shown_place";
    private static final String KEY_WIDGETS_DRAWN = "org.meteosphere.widgets_drawn";
    private static final String KEY_NOTIFICATION_DRAWN = "org.meteosphere.notification_drawn";
    private static final String KEY_SORTED_DETAIL_WIDGETS = "org.meteosphere.detail_widgets_order";
    private static final String KEY_SHOWN_DETAIL_WIDGETS = "org.meteosphere.shown_detail_widgets";
    private static final String KEY_SORTED_MAIN_TABS = "org.meteosphere.sorted_main_tabs";
    private static final String KEY_HIDDEN_MAIN_TABS = "org.meteosphere.hidden_main_tabs";
    private static final String KEY_ASTRO_LOCATION = "org.meteosphere.astro_location";
    private static final String KEY_NOTIFICATION_ON = "org.meteosphere.notification_on";
    private static final String KEY_NOTIFICATION_PLACE = "org.meteosphere.notification_place_id";
    private static final String KEY_NOTIFICATION_FORECAST = "org.meteosphere.notification_forecast_shown";
    private static final String KEY_NOTIFICATION_LOOK = "org.meteosphere.notification_look";

    private static volatile AppSettings instance;

    public final SharedPreferences preferences;
    public final Context context;

    /** The units, as the preferences' lists number them */
    public final int distanceUnit;
    public final int temperatureUnit;
    public final int windSpeedUnit;
    public final int pressureUnit;
    public final int precipitationUnit;
    /** The wind shown in the lists only when it is strong */
    public final boolean onlyStrongWind;
    public final boolean use24Hours;
    public final boolean withGps;
    public final boolean currentShown;
    /** 0 for the automatic period */
    public final int refreshMinutes;
    /** WeatherProviders' ids */
    public final String dataProvider;
    public final String weatherSource;
    /** The Foreca key given in the settings, empty for none */
    public final String forecaKey;
    /** AppLanguage.of()'s */
    public final String language;

    /** The favourites' ids in their order, null until they are loaded */
    public volatile CopyOnWriteArrayList<Integer> favoriteIds;
    /** The weather widgets' ids, null until they are loaded */
    public volatile CopyOnWriteArrayList<Integer> widgetIds;

    private final LocationStore store;
    private final LanguageUnits languageUnits;

    /** Applies the app's language and reads the settings anew; at the app's start and whenever they change */
    public static synchronized void init(Context context) {
        AppLanguage.apply(context);
        instance = new AppSettings(context);
    }

    /** The instance made last, null before init() */
    public static AppSettings getInstance() {
        return instance;
    }

    private AppSettings(Context context) {
        this.context = context;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.store = LocationStore.get(context);
        this.language = AppLanguage.of(context);
        this.languageUnits = languageUnits(this.language);
        if (!this.preferences.contains(key(R.string.key_names_language))) {
            saveFirstStart(this.language);
        }
        this.distanceUnit = number(R.string.key_distance_unit, this.languageUnits.distance);
        this.temperatureUnit = number(R.string.key_temperature_unit, this.languageUnits.temperature);
        this.windSpeedUnit = number(R.string.key_wind_unit, this.languageUnits.windSpeed);
        this.pressureUnit = number(R.string.key_pressure_unit, this.languageUnits.pressure);
        this.precipitationUnit = number(R.string.key_precipitation_unit, this.languageUnits.precipitation);
        this.onlyStrongWind = this.preferences.getBoolean(key(R.string.key_wind_when_strong), false);
        this.use24Hours = clock24Setting();
        this.withGps = this.preferences.getBoolean(key(R.string.key_search_by_gps), true);
        this.currentShown = this.preferences.getBoolean(key(R.string.key_show_current), true);
        this.refreshMinutes = number(R.string.key_refresh_period, 0);
        this.dataProvider = WeatherProviders.dataProvider(
                this.preferences.getString(key(R.string.key_data_provider), WeatherProviders.DEFAULT_DATA_PROVIDER));
        this.weatherSource = this.preferences.getString(key(R.string.key_weather_source), WeatherProviders.DEFAULT_WEATHER_SOURCE);
        this.forecaKey = this.preferences.getString(key(R.string.key_foreca_key), "");
        WeatherClient.setProvider(WeatherProviders.get(context, this.dataProvider, this.weatherSource, forecaToken()));
        AddOns.init(context);
    }

    private String key(int keyId) {
        return this.context.getString(keyId);
    }

    // A list setting, kept as a string of its number. One that isn't a number gives the default: the app still starts
    private int number(int keyId, int defaultValue) {
        String key = key(keyId);
        String text = this.preferences.getString(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Not a number in " + key + ": " + text);
            return defaultValue;
        }
    }

    // --- The units and the clock ---

    /**
     * The units and the clock of a language, numbered as the preferences' lists have them. None of them is saved until
     * the user chooses it: they follow the app's language till then
     */
    static final class LanguageUnits {
        final int distance;
        final int temperature;
        final int windSpeed;
        final int pressure;
        final int precipitation;
        final boolean is24Hour;

        LanguageUnits(int distance, int temperature, int windSpeed, int pressure, int precipitation, boolean is24Hour) {
            this.distance = distance;
            this.temperature = temperature;
            this.windSpeed = windSpeed;
            this.pressure = pressure;
            this.precipitation = precipitation;
            this.is24Hour = is24Hour;
        }
    }

    /** By the language's own code, as AppLanguage.of() has it: "zh-Hant" is Chinese as "zh" is */
    static LanguageUnits languageUnits(String language) {
        int dash = language.indexOf('-');
        switch (dash < 0 ? language : language.substring(0, dash)) {
            // km, °C, m/s, mmHg, mm
            case "ru":
            case "uk":
            case "pl":
                return new LanguageUnits(0, 0, 0, 0, 0, true);
            // Miles, °F, mph, inHg, inches and the 12-hour clock
            case "en":
                return new LanguageUnits(1, 1, 2, 3, 1, false);
            // km, °C, m/s, hPa, mm
            case "zh":
            case "ja":
                return new LanguageUnits(0, 0, 0, 1, 0, true);
            // km, °C, km/h, hPa, mm
            default:
                return new LanguageUnits(0, 0, 1, 1, 0, true);
        }
    }

    /** A unit's list value by its key: the one saved, or the app language's own while the units aren't chosen */
    public String unitSetting(String key) {
        return this.preferences.getString(key, String.valueOf(languageUnit(key)));
    }

    /** The 24-hour clock as saved, or the app language's own while the user hasn't switched it */
    public boolean clock24Setting() {
        return this.preferences.getBoolean(key(R.string.key_clock_24), this.languageUnits.is24Hour);
    }

    private int languageUnit(String key) {
        if (key.equals(key(R.string.key_temperature_unit))) {
            return this.languageUnits.temperature;
        } else if (key.equals(key(R.string.key_wind_unit))) {
            return this.languageUnits.windSpeed;
        } else if (key.equals(key(R.string.key_pressure_unit))) {
            return this.languageUnits.pressure;
        } else if (key.equals(key(R.string.key_distance_unit))) {
            return this.languageUnits.distance;
        } else if (key.equals(key(R.string.key_precipitation_unit))) {
            return this.languageUnits.precipitation;
        }
        throw new IllegalArgumentException("Not a unit: " + key);
    }

    // --- The first start ---

    // All saved at once with the language, which tells the next starts from the first one. The units and the clock
    // aren't, see LanguageUnits
    private void saveFirstStart(String language) {
        this.preferences.edit()
                .putBoolean(key(R.string.key_wind_when_strong), true)
                .putBoolean(key(R.string.key_search_by_gps), true)
                .putBoolean(key(R.string.key_show_current), true)
                .putString(key(R.string.key_refresh_period), "0")
                .putString(key(R.string.key_names_language), language)
                .apply();
    }

    // --- Foreca ---

    /** The key the device asks Foreca with */
    public String forecaToken() {
        return forecaToken(this.forecaKey);
    }

    /** The key given in the settings, or the build's own without one */
    public static String forecaToken(String forecaKey) {
        return forecaKey != null && !forecaKey.isEmpty() ? forecaKey : BuildConfig.FORECA_TOKEN;
    }

    // --- The language ---

    /**
     * Whether the app's language is another than the one the places were last named in; then it becomes that one. A
     * language never kept counts as the same
     */
    public boolean takeNewLanguage() {
        String key = key(R.string.key_names_language);
        String language = AppLanguage.of(this.context);
        if (language.equals(this.preferences.getString(key, language))) {
            return false;
        }
        this.preferences.edit().putString(key, language).apply();
        return true;
    }

    // --- The places ---

    /** The current location for -1, otherwise a copy of the stored place; null when there is none */
    public Place getPlace(int locationId) {
        if (locationId == -1) {
            return this.store.getCurrentPlace();
        }
        Place stored = this.store.get(locationId);
        return stored != null ? stored.copy() : null;
    }

    /** A stored place's name as shown, without copying its forecasts; null when there is none */
    public String getLocationDisplayName(int locationId) {
        Place stored = this.store.get(locationId);
        if (stored == null) {
            return null;
        }
        synchronized (stored) {
            return stored.displayName();
        }
    }

    /** The current location's for -1, a stored place's otherwise, without copying forecasts; null when there is none */
    public TimeZone getLocationTimeZone(int locationId) {
        Place place = locationId == -1 ? this.store.getCurrentPlace() : this.store.get(locationId);
        if (place == null) {
            return null;
        }
        synchronized (place) {
            return place.timeZone();
        }
    }

    /** Whether the current location is known, for -1; whether the place is stored, otherwise */
    public boolean hasCachedLocation(int locationId) {
        return locationId == -1 ? this.store.getCurrentPlace() != null : this.store.contains(locationId);
    }

    /** The stored places themselves, not copies */
    public synchronized ArrayList<Place> getStoredPlaces() {
        return new ArrayList<>(this.store.getAll());
    }

    public synchronized ArrayList<Integer> getStoredIds() {
        return new ArrayList<>(this.store.ids());
    }

    /**
     * Stores the place's forecast. A place stored already keeps its object and its custom name, which weather updates
     * arrive without, and takes the rest of the forecast
     */
    public void storePlace(Place place) {
        Place stored = this.store.get(place.id);
        if (stored == null) {
            this.store.add(place.copy());
            return;
        }
        // Copied before, under the new place's own lock, so that the stored one's is held for the setting alone
        ArrayList<DayWeather> days = place.daysCopy();
        ArrayList<Weather> hours = place.hoursCopy();
        // A copy made for a screen takes the lock too: it has the old forecast or the new one, whole
        synchronized (stored) {
            stored.now = place.now;
            stored.loadedAt = place.loadedAt;
            stored.sourceTime = place.sourceTime;
            stored.addOnSource = place.addOnSource;
            stored.days = days;
            stored.hours = hours;
            stored.utcOffsetMinutes = place.utcOffsetMinutes;
            stored.elevation = place.elevation;
            stored.name = place.name;
            stored.country = place.country;
            stored.region = place.region;
            if (place.timeZoneId != null) {
                stored.timeZoneId = place.timeZoneId;
            }
        }
        this.store.save(stored);
    }

    /** Each stored place of the map takes its entry's name, country and region */
    public void renameLocations(Map<Integer, Place> named) {
        for (Map.Entry<Integer, Place> entry : named.entrySet()) {
            Place stored = this.store.get(entry.getKey());
            if (stored == null) {
                continue;
            }
            Place names = entry.getValue();
            synchronized (stored) {
                stored.name = names.name;
                stored.country = names.country;
                stored.region = names.region;
            }
            this.store.save(stored);
        }
    }

    /** The name the user gave a stored place, null for none; storePlace() keeps it */
    public void setLocationCustomName(int locationId, String customName) {
        Place stored = this.store.get(locationId);
        if (stored == null) {
            return;
        }
        synchronized (stored) {
            stored.customName = customName;
        }
        this.store.save(stored);
    }

    public void removePlace(int locationId) {
        this.store.remove(locationId);
    }

    public Place getCurrentPlace() {
        return this.store.getCurrentPlace();
    }

    public void saveCurrentPlace(Place location) {
        this.store.setCurrentPlace(location);
    }

    /** The places the search found last */
    public ArrayList<Place> getFoundPlaces() {
        return new ArrayList<>(this.store.getFoundPlaces());
    }

    public void saveFoundPlaces(ArrayList<Place> locations) {
        this.store.setFoundPlaces(locations);
    }

    /** Whether anything shows the place: the city page last shown, a weather widget, the notification or the favourites */
    public boolean isPlaceUsed(int locationId) {
        return usedLocationIds().contains(locationId);
    }

    private Set<Integer> usedLocationIds() {
        Set<Integer> used = new HashSet<>(favorites());
        used.add(getShownPlace());
        used.add(getStatusBarNotificationPlaceId());
        for (int widgetId : widgets()) {
            used.add(new WidgetConfig(this.context, widgetId).getPlaceId());
        }
        return used;
    }

    /** Removes every stored place nothing shows */
    public void removeUnusedPlaces() {
        Set<Integer> used = usedLocationIds();
        for (int locationId : this.store.ids()) {
            if (!used.contains(locationId)) {
                this.store.remove(locationId);
            }
        }
    }

    /** removeUnusedPlaces() on the store's thread */
    public void removeUnusedPlacesInBackground() {
        // Loaded here: loaded lazily on the store's thread, they could replace the lists this thread changes meanwhile
        favorites();
        widgets();
        this.store.runInBackground(this::removeUnusedPlaces);
    }

    // --- The favourites ---

    /** Reads the favourites anew */
    public void readFavorites() {
        this.favoriteIds = new CopyOnWriteArrayList<>(parseFavoriteIds(this.preferences.getString(KEY_FAVORITES, "")));
    }

    // The favourites as loaded, loaded now unless they are
    private CopyOnWriteArrayList<Integer> favorites() {
        CopyOnWriteArrayList<Integer> ids = this.favoriteIds;
        if (ids == null) {
            readFavorites();
            ids = this.favoriteIds;
        }
        return ids;
    }

    public ArrayList<Integer> getFavorites() {
        return new ArrayList<>(favorites());
    }

    /** Adds the place at the end; false when it is a favourite already */
    public boolean addFavorite(int locationId) {
        if (!favorites().addIfAbsent(locationId)) {
            return false;
        }
        writeFavorites();
        return true;
    }

    /** Removes the favourite, and its stored place too when asked and nothing else shows it */
    public void removeFavorite(int locationId, boolean removeIfUnused) {
        favorites().remove(Integer.valueOf(locationId));
        writeFavorites();
        if (removeIfUnused && !isPlaceUsed(locationId)) {
            removePlace(locationId);
        }
    }

    public void removeFavorite(int locationId) {
        removeFavorite(locationId, true);
    }

    /** The favourites in the list's order, each once */
    public void setFavoriteLocations(List<Integer> locationIds) {
        this.favoriteIds = new CopyOnWriteArrayList<>(new LinkedHashSet<>(locationIds));
        writeFavorites();
    }

    public void writeFavorites() {
        this.preferences.edit().putString(KEY_FAVORITES, favoritesText(favorites())).apply();
    }

    /** The favourites' ids of their text, "2643743,2510911,": each once in its first place, broken pieces left out */
    static List<Integer> parseFavoriteIds(String text) {
        return new ArrayList<>(new LinkedHashSet<>(numbers(text, ",")));
    }

    /** Each id followed by a comma */
    static String favoritesText(List<Integer> locationIds) {
        StringBuilder text = new StringBuilder();
        for (int locationId : locationIds) {
            text.append(locationId).append(',');
        }
        return text.toString();
    }

    // --- The widgets' ids ---

    /** Reads the weather widgets' ids anew */
    public void readWidgetIds() {
        this.widgetIds = new CopyOnWriteArrayList<>(parseWidgetIds(this.preferences.getString(KEY_WIDGET_IDS, "")));
    }

    // The widgets' ids as loaded, loaded now unless they are
    private CopyOnWriteArrayList<Integer> widgets() {
        CopyOnWriteArrayList<Integer> ids = this.widgetIds;
        if (ids == null) {
            readWidgetIds();
            ids = this.widgetIds;
        }
        return ids;
    }

    public ArrayList<Integer> getWidgets() {
        return new ArrayList<>(widgets());
    }

    public void putWidgetId(int widgetId) {
        if (widgets().addIfAbsent(widgetId)) {
            writeWidgetIds();
        }
    }

    public void removeWidgetId(int widgetId) {
        widgets().removeIf(id -> id == widgetId);
        writeWidgetIds();
    }

    public void writeWidgetIds() {
        this.preferences.edit().putString(KEY_WIDGET_IDS, widgetIdsText(widgets())).apply();
    }

    /** The widgets' ids of their text, separated by spaces, broken pieces left out */
    static List<Integer> parseWidgetIds(String text) {
        return numbers(text, " ");
    }

    static String widgetIdsText(List<Integer> widgetIds) {
        return joined(widgetIds, " ");
    }

    // --- The positions and the nearby places ---

    /** The device's position, null for none */
    public void savePosition(Location location) {
        String text = location != null ? positionText(location.getLatitude(), location.getLongitude(), location.getProvider()) : "";
        this.preferences.edit().putString(KEY_POSITION, text).apply();
    }

    /** The device's position kept, of its provider; null for none */
    public Location getPosition() {
        String text = this.preferences.getString(KEY_POSITION, "");
        double[] coordinates = positionCoordinates(text);
        if (coordinates == null) {
            return null;
        }
        String[] pieces = text.split(" ");
        Location location = new Location(pieces.length > 2 ? pieces[2] : "");
        location.setLatitude(coordinates[0]);
        location.setLongitude(coordinates[1]);
        return location;
    }

    /** "latitude longitude provider", the numbers with every digit the double has */
    static String positionText(double latitude, double longitude, String provider) {
        return latitude + " " + longitude + " " + (provider != null ? provider : "");
    }

    /** The latitude and the longitude of the text, read as doubles: as floats they would lose digits. Null for none */
    static double[] positionCoordinates(String text) {
        String[] pieces = text.split(" ");
        if (pieces.length < 2) {
            return null;
        }
        try {
            return new double[] {Double.parseDouble(pieces[0]), Double.parseDouble(pieces[1])};
        } catch (NumberFormatException e) {
            Log.w(TAG, "Not a position: " + text);
            return null;
        }
    }

    /** Keeps the nearby places, null as none, as loaded now; a place that can't be written leaves the list kept as it was */
    public void saveNearbyPlaces(ArrayList<Place> locations) {
        JSONArray array = new JSONArray();
        try {
            if (locations != null) {
                for (Place location : locations) {
                    array.put(location.toJson());
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Can't save the nearby places", e);
            return;
        }
        this.preferences.edit()
                .putString(KEY_NEARBY, array.toString())
                .putLong(KEY_NEARBY_TIME, System.currentTimeMillis())
                .apply();
    }

    /** The nearby places kept, without those an older build wrote; null when none are kept or they can't be read */
    public ArrayList<Place> getNearbyPlaces() {
        String json = this.preferences.getString(KEY_NEARBY, null);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONArray array = new JSONArray(json);
            ArrayList<Place> locations = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Place location = Place.fromJson(array.getJSONObject(i));
                if (location != null) {
                    locations.add(location);
                }
            }
            return locations;
        } catch (JSONException | RuntimeException e) {
            Log.w(TAG, "Can't read the nearby places", e);
            return null;
        }
    }

    /** When the nearby places were loaded, at 0 for never */
    public Calendar getNearbyTime() {
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(this.preferences.getLong(KEY_NEARBY_TIME, 0));
        return time;
    }

    public void saveNearbyTime(Calendar time) {
        this.preferences.edit().putLong(KEY_NEARBY_TIME, time.getTimeInMillis()).apply();
    }

    /** When places were last searched for, 0 for never */
    public long getLastLocationSearchTime() {
        return this.preferences.getLong(KEY_LOCATION_SEARCH_TIME, 0);
    }

    public void saveLastLocationSearchTime(long time) {
        this.preferences.edit().putLong(KEY_LOCATION_SEARCH_TIME, time).apply();
    }

    // --- The screens' memory ---

    /** The class name of the screen shown last, empty for none */
    public String getShownScreen() {
        return this.preferences.getString(KEY_SHOWN_SCREEN, "");
    }

    public void saveShownScreen(String screen) {
        this.preferences.edit().putString(KEY_SHOWN_SCREEN, screen).apply();
    }

    public int getLastMainTab() {
        return this.preferences.getInt(KEY_LAST_MAIN_TAB, TAB_NEARBY);
    }

    public void setLastMainTab(int tab) {
        this.preferences.edit().putInt(KEY_LAST_MAIN_TAB, tab).apply();
    }

    public boolean isDetailsDaysExpanded() {
        return this.preferences.getBoolean(KEY_DETAILS_DAYS_EXPANDED, false);
    }

    public void saveDetailsDaysExpanded(boolean expanded) {
        this.preferences.edit().putBoolean(KEY_DETAILS_DAYS_EXPANDED, expanded).apply();
    }

    public boolean isDayStripHintShown() {
        return this.preferences.getBoolean(KEY_DAY_STRIP_HINT_SHOWN, false);
    }

    public void saveDayStripHintShown() {
        this.preferences.edit().putBoolean(KEY_DAY_STRIP_HINT_SHOWN, true).apply();
    }

    /** The place the city page showed last */
    public int getShownPlace() {
        return this.preferences.getInt(KEY_SHOWN_PLACE, 0);
    }

    public void saveShownPlace(int locationId) {
        this.preferences.edit().putInt(KEY_SHOWN_PLACE, locationId).apply();
    }

    /** When the weather widgets were last all drawn, 0 for never */
    public long getWidgetsDrawnTime() {
        return this.preferences.getLong(KEY_WIDGETS_DRAWN, 0);
    }

    public void saveWidgetsDrawnTime(long time) {
        this.preferences.edit().putLong(KEY_WIDGETS_DRAWN, time).apply();
    }

    /** When the weather notification was last drawn, 0 for never */
    public long getNotificationDrawnTime() {
        return this.preferences.getLong(KEY_NOTIFICATION_DRAWN, 0);
    }

    public void saveNotificationDrawnTime(long time) {
        this.preferences.edit().putLong(KEY_NOTIFICATION_DRAWN, time).apply();
    }

    // --- The lists of numbers ---

    /** The numbers joined by commas, "" for none */
    public static String listText(List<Integer> list) {
        return list != null ? joined(list, ",") : "";
    }

    /** The numbers of the text, broken pieces left out; null for "", which tells a list never saved */
    public static List<Integer> parseList(String text) {
        return text != null && !text.isEmpty() ? numbers(text, ",") : null;
    }

    private static String joined(List<Integer> numbers, String separator) {
        StringBuilder text = new StringBuilder();
        for (int number : numbers) {
            if (text.length() > 0) {
                text.append(separator);
            }
            text.append(number);
        }
        return text.toString();
    }

    // The empty pieces and those that aren't numbers left out
    private static List<Integer> numbers(String text, String separator) {
        ArrayList<Integer> numbers = new ArrayList<>();
        for (String piece : text.split(separator)) {
            String number = piece.trim();
            if (number.isEmpty()) {
                continue;
            }
            try {
                numbers.add(Integer.parseInt(number));
            } catch (NumberFormatException e) {
                // Left out
            }
        }
        return numbers;
    }

    // --- The city page's tiles ---

    /** The order the tiles were last arranged in, null before they were */
    public List<Integer> getSortedDetailWidgets() {
        return parseList(this.preferences.getString(KEY_SORTED_DETAIL_WIDGETS, ""));
    }

    public void setSortedDetailWidgets(List<Integer> widgets) {
        this.preferences.edit().putString(KEY_SORTED_DETAIL_WIDGETS, listText(widgets)).apply();
    }

    /** The tiles shown; all of them in their order before any were chosen */
    public List<Integer> getShownDetailWidgets() {
        List<Integer> used = parseList(this.preferences.getString(KEY_SHOWN_DETAIL_WIDGETS, ""));
        if (used != null) {
            return used;
        }
        ArrayList<Integer> all = new ArrayList<>();
        for (int widget = 0; widget <= SUN_NOW_DETAIL_WIDGET; widget++) {
            all.add(widget);
        }
        return all;
    }

    public void setShownDetailWidgets(List<Integer> widgets) {
        this.preferences.edit().putString(KEY_SHOWN_DETAIL_WIDGETS, listText(widgets)).apply();
    }

    public boolean isSunNowShown() {
        return getShownDetailWidgets().contains(SUN_NOW_DETAIL_WIDGET);
    }

    // --- The main screen's tabs ---

    /** All the tabs in their order */
    public List<Integer> getSortedMainTabs() {
        return sortedMainTabs(this.preferences.getString(KEY_SORTED_MAIN_TABS, ""));
    }

    /** The tabs shown, in their order */
    public List<Integer> getMainTabs() {
        return mainTabs(this.preferences.getString(KEY_SORTED_MAIN_TABS, ""), this.preferences.getString(KEY_HIDDEN_MAIN_TABS, ""));
    }

    /** Both written at once */
    public void saveMainTabs(List<Integer> sorted, List<Integer> hidden) {
        this.preferences.edit()
                .putString(KEY_SORTED_MAIN_TABS, listText(sorted))
                .putString(KEY_HIDDEN_MAIN_TABS, listText(hidden))
                .apply();
    }

    /** The saved order's tabs, each once and only the app's, then those it misses in the default order, a new tab too */
    static List<Integer> sortedMainTabs(String sortedText) {
        ArrayList<Integer> tabs = new ArrayList<>();
        List<Integer> saved = parseList(sortedText);
        if (saved != null) {
            for (int tab : saved) {
                if (DEFAULT_MAIN_TABS.contains(tab) && !tabs.contains(tab)) {
                    tabs.add(tab);
                }
            }
        }
        for (int tab : DEFAULT_MAIN_TABS) {
            if (!tabs.contains(tab)) {
                tabs.add(tab);
            }
        }
        return tabs;
    }

    /** The tabs in their order without the hidden ones; the favourites are never hidden */
    static List<Integer> mainTabs(String sortedText, String hiddenText) {
        List<Integer> tabs = sortedMainTabs(sortedText);
        List<Integer> hidden = parseList(hiddenText);
        if (hidden != null) {
            tabs.removeIf(tab -> tab != TAB_FAVORITES && hidden.contains(tab));
        }
        return tabs;
    }

    // --- The places of the tabs ---

    /** The place of the Moon's, the Sun's and the earthquakes' tabs: -1 for the current location, or a favourite's id */
    public int getAstroLocationId() {
        return this.preferences.getInt(KEY_ASTRO_LOCATION, -1);
    }

    public void saveAstroLocationId(int locationId) {
        this.preferences.edit().putInt(KEY_ASTRO_LOCATION, locationId).apply();
    }

    /** The favourite chosen while it is still one and kept, otherwise the current location or the first favourite */
    public Place getAstroLocation() {
        int locationId = getAstroLocationId();
        if (locationId != -1 && favorites().contains(locationId)) {
            Place place = getPlace(locationId);
            if (place != null) {
                return place;
            }
        }
        return getCurrentOrFirstFavoriteLocation();
    }

    /** The current location, or the first favourite while the current location's position is unknown; null for neither */
    public Place getCurrentOrFirstFavoriteLocation() {
        Place current = getCurrentPlace();
        // At 0, 0 it has no position
        if (current != null && (current.latitude != 0 || current.longitude != 0)) {
            return current;
        }
        List<Integer> favorites = favorites();
        return favorites.isEmpty() ? null : getPlace(favorites.get(0));
    }

    // --- The notification and the daily forecast ---

    public boolean isStatusBarNotificationOn() {
        return this.preferences.getBoolean(KEY_NOTIFICATION_ON, false);
    }

    /** -1 for the current location */
    public int getStatusBarNotificationPlaceId() {
        return this.preferences.getInt(KEY_NOTIFICATION_PLACE, -1);
    }

    public boolean isStatusBarNotificationForecastShown() {
        return this.preferences.getBoolean(KEY_NOTIFICATION_FORECAST, false);
    }

    /** One of WeatherNotification's looks */
    public int getStatusBarNotificationLook() {
        return this.preferences.getInt(KEY_NOTIFICATION_LOOK, WeatherNotification.LOOK_SYSTEM);
    }

    /** All four written at once, so that none is left behind */
    public void saveStatusBarNotification(boolean enabled, int locationId, boolean showForecast, int look) {
        this.preferences.edit()
                .putBoolean(KEY_NOTIFICATION_ON, enabled)
                .putInt(KEY_NOTIFICATION_PLACE, locationId)
                .putBoolean(KEY_NOTIFICATION_FORECAST, showForecast)
                .putInt(KEY_NOTIFICATION_LOOK, look)
                .apply();
    }

    /** The daily forecast's place: 0 for none, -1 for the current location */
    public int getDailyForecastLocationId() {
        return number(R.string.key_daily_forecast, 0);
    }
}
