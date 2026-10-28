package org.meteosphere.ui.widgets.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.meteosphere.ui.widgets.WidgetKind;

/**
 * A widget's settings and what the app keeps of it, in a file of its own named after the widget. The weather widgets,
 * the earthquake widget and the Sun and Moon widgets all keep theirs here, each the values it has
 */
public final class WidgetConfig {

    private static final String FILE_PREFIX = "org.meteosphere.widget.";

    private static final String KEY_KIND = "kind";
    private static final String KEY_LOCATION_ID = "location_id";
    private static final String KEY_LAST_REFRESH = "last_refresh";
    private static final String KEY_TIME_DRAWN = "time_drawn";
    private static final String KEY_GLASS_COLOR = "glass_color";
    private static final String KEY_CONTENT_COLOR = "content_color";
    private static final String KEY_CLOCK_SHOWN = "clock_shown";
    private static final String KEY_CLOCK_APP = "clock_app";
    private static final String KEY_NOW_SHOWN = "now_shown";
    private static final String KEY_SHOW_WIND = "show_wind";
    private static final String KEY_SHOW_PRECIPITATION = "show_precipitation";
    private static final String KEY_LOCATION_NAME = "location_name";
    private static final String KEY_LOCATION_LATITUDE = "location_latitude";
    private static final String KEY_LOCATION_LONGITUDE = "location_longitude";
    private static final String KEY_LOCATION_TIME_ZONE = "location_time_zone";
    private static final String KEY_QUAKE_DAYS = "quake_days";
    private static final String KEY_QUAKE_STRONGEST = "quake_strongest";

    // The city page's tabs, by hours and by days
    private static final int TAB_HOURS = 0;
    private static final int TAB_DAYS = 1;

    private final SharedPreferences preferences;

    public WidgetConfig(Context context, int widgetId) {
        this(context.getSharedPreferences(fileName(widgetId), Context.MODE_PRIVATE));
    }

    WidgetConfig(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    private static String fileName(int widgetId) {
        return FILE_PREFIX + widgetId;
    }

    /** Deletes the widget's settings, as it is removed from the home screen */
    public static void delete(Context context, int widgetId) {
        context.deleteSharedPreferences(fileName(widgetId));
    }

    /** One of WidgetKind's */
    public int getKind() {
        return this.preferences.getInt(KEY_KIND, WidgetKind.CHART_HOURLY);
    }

    /** The place's id, -1 for the current location */
    public int getPlaceId() {
        return this.preferences.getInt(KEY_LOCATION_ID, -1);
    }

    /** Whether a place, the current location too, was ever chosen in the settings */
    public boolean hasLocation() {
        return this.preferences.contains(KEY_LOCATION_ID);
    }

    /** When the widget's weather was last refreshed, 0 for never */
    public long getLastRefresh() {
        return this.preferences.getLong(KEY_LAST_REFRESH, 0);
    }

    /**
     * Whether the widget shows the time, as it was drawn last: such a one is drawn anew with every refresh of all, as
     * its date and the clock's alarm may have changed
     */
    public boolean isTimeDrawn() {
        return this.preferences.getBoolean(KEY_TIME_DRAWN, false);
    }

    /** The light glass when its red is 255, the dark one otherwise; its alpha the glass' opacity */
    public int getGlassColor() {
        return this.preferences.getInt(KEY_GLASS_COLOR, Color.BLACK);
    }

    public int getContentColor() {
        return this.preferences.getInt(KEY_CONTENT_COLOR, Color.WHITE);
    }

    /** The clock above the chart */
    public boolean isClockShown() {
        return this.preferences.getBoolean(KEY_CLOCK_SHOWN, false);
    }

    /** The package of the app a tap on the clock opens, empty for the phone's own */
    public String getClockApp() {
        return this.preferences.getString(KEY_CLOCK_APP, "");
    }

    /** The weather now over the chart */
    public boolean isNowShown() {
        return this.preferences.getBoolean(KEY_NOW_SHOWN, false);
    }

    /** The wind under the chart */
    public boolean isShowWind() {
        return this.preferences.getBoolean(KEY_SHOW_WIND, true);
    }

    /** The precipitation under the chart */
    public boolean isShowPrecipitation() {
        return this.preferences.getBoolean(KEY_SHOW_PRECIPITATION, true);
    }

    /** The chosen place's name as it was chosen, null for none: the widget shows it while the app keeps no such place */
    public String getLocationName() {
        return this.preferences.getString(KEY_LOCATION_NAME, null);
    }

    public double getLocationLatitude() {
        return Double.longBitsToDouble(this.preferences.getLong(KEY_LOCATION_LATITUDE, 0));
    }

    public double getLocationLongitude() {
        return Double.longBitsToDouble(this.preferences.getLong(KEY_LOCATION_LONGITUDE, 0));
    }

    /** Like "Europe/Riga", null for none */
    public String getLocationTimeZone() {
        return this.preferences.getString(KEY_LOCATION_TIME_ZONE, null);
    }

    /** The earthquake widget's period: 1, 7 or 30 days */
    public int getQuakeDays() {
        return this.preferences.getInt(KEY_QUAKE_DAYS, 1);
    }

    /** Whether the earthquake widget shows the strongest first rather than the latest */
    public boolean isQuakeStrongest() {
        return this.preferences.getBoolean(KEY_QUAKE_STRONGEST, false);
    }

    /** The city page's tab a tap on the widget opens: the hours for a widget by hours, the days for the rest */
    public int getDetailsTab() {
        return WidgetKind.isHourly(getKind()) ? TAB_HOURS : TAB_DAYS;
    }

    public Editor edit() {
        return new Editor(this.preferences.edit());
    }

    /** Several values written at once by apply(), the widget never read with some of them only */
    public static final class Editor {

        private final SharedPreferences.Editor editor;

        private Editor(SharedPreferences.Editor editor) {
            this.editor = editor;
        }

        public Editor setKind(int kind) {
            this.editor.putInt(KEY_KIND, kind);
            return this;
        }

        public Editor setPlaceId(int locationId) {
            this.editor.putInt(KEY_LOCATION_ID, locationId);
            return this;
        }

        public Editor setLastRefresh(long time) {
            this.editor.putLong(KEY_LAST_REFRESH, time);
            return this;
        }

        public Editor setTimeDrawn(boolean withTime) {
            this.editor.putBoolean(KEY_TIME_DRAWN, withTime);
            return this;
        }

        public Editor setGlassColor(int color) {
            this.editor.putInt(KEY_GLASS_COLOR, color);
            return this;
        }

        public Editor setContentColor(int color) {
            this.editor.putInt(KEY_CONTENT_COLOR, color);
            return this;
        }

        public Editor setClockShown(boolean show) {
            this.editor.putBoolean(KEY_CLOCK_SHOWN, show);
            return this;
        }

        public Editor setClockApp(String packageName) {
            this.editor.putString(KEY_CLOCK_APP, packageName);
            return this;
        }

        public Editor setNowShown(boolean show) {
            this.editor.putBoolean(KEY_NOW_SHOWN, show);
            return this;
        }

        public Editor setShowWind(boolean show) {
            this.editor.putBoolean(KEY_SHOW_WIND, show);
            return this;
        }

        public Editor setShowPrecipitation(boolean show) {
            this.editor.putBoolean(KEY_SHOW_PRECIPITATION, show);
            return this;
        }

        /** The chosen place as it was chosen; a time zone of null for none */
        public Editor setLocationPlace(String name, double latitude, double longitude, String timeZoneId) {
            this.editor.putString(KEY_LOCATION_NAME, name)
                    .putLong(KEY_LOCATION_LATITUDE, Double.doubleToLongBits(latitude))
                    .putLong(KEY_LOCATION_LONGITUDE, Double.doubleToLongBits(longitude))
                    .putString(KEY_LOCATION_TIME_ZONE, timeZoneId);
            return this;
        }

        public Editor setQuakeDays(int days) {
            this.editor.putInt(KEY_QUAKE_DAYS, days);
            return this;
        }

        public Editor setQuakeStrongest(boolean strongest) {
            this.editor.putBoolean(KEY_QUAKE_STRONGEST, strongest);
            return this;
        }

        public void apply() {
            this.editor.apply();
        }
    }
}
