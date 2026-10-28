package org.meteosphere.ui.widgets.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.meteosphere.ui.widgets.WidgetKind;

// A widget's settings as a new widget has them, and as the editor writes them
public class WidgetConfigTest {

    @Test
    public void defaults() {
        WidgetConfig preferences = new WidgetConfig(new MapPreferences());
        assertEquals(WidgetKind.CHART_HOURLY, preferences.getKind());
        assertEquals(-1, preferences.getPlaceId());
        assertFalse(preferences.hasLocation());
        assertEquals(0, preferences.getLastRefresh());
        assertFalse(preferences.isTimeDrawn());
        assertEquals(0xff000000, preferences.getGlassColor());
        assertEquals(0xffffffff, preferences.getContentColor());
        assertFalse(preferences.isClockShown());
        assertEquals("", preferences.getClockApp());
        assertFalse(preferences.isNowShown());
        assertTrue(preferences.isShowWind());
        assertTrue(preferences.isShowPrecipitation());
        assertNull(preferences.getLocationName());
        assertEquals(0, preferences.getLocationLatitude(), 0);
        assertEquals(0, preferences.getLocationLongitude(), 0);
        assertNull(preferences.getLocationTimeZone());
        assertEquals(1, preferences.getQuakeDays());
        assertFalse(preferences.isQuakeStrongest());
        assertEquals(0, preferences.getDetailsTab());
    }

    // All of them at once, none before apply()
    @Test
    public void editor() {
        WidgetConfig preferences = new WidgetConfig(new MapPreferences());
        WidgetConfig.Editor editor = preferences.edit()
                .setKind(WidgetKind.FACT_DAILY)
                .setPlaceId(456172)
                .setLastRefresh(1_800_000_000_000L)
                .setTimeDrawn(true)
                .setGlassColor(0x80ffffff)
                .setContentColor(0xff222222)
                .setClockShown(true)
                .setClockApp("com.google.android.deskclock")
                .setNowShown(true)
                .setShowWind(false)
                .setShowPrecipitation(false)
                .setLocationPlace("Riga", 56.946, 24.10589, "Europe/Riga")
                .setQuakeDays(30)
                .setQuakeStrongest(true);
        assertEquals(-1, preferences.getPlaceId());
        assertFalse(preferences.hasLocation());
        editor.apply();
        assertEquals(WidgetKind.FACT_DAILY, preferences.getKind());
        assertEquals(456172, preferences.getPlaceId());
        assertTrue(preferences.hasLocation());
        assertEquals(1_800_000_000_000L, preferences.getLastRefresh());
        assertTrue(preferences.isTimeDrawn());
        assertEquals(0x80ffffff, preferences.getGlassColor());
        assertEquals(0xff222222, preferences.getContentColor());
        assertTrue(preferences.isClockShown());
        assertEquals("com.google.android.deskclock", preferences.getClockApp());
        assertTrue(preferences.isNowShown());
        assertFalse(preferences.isShowWind());
        assertFalse(preferences.isShowPrecipitation());
        assertEquals("Riga", preferences.getLocationName());
        assertEquals(56.946, preferences.getLocationLatitude(), 0);
        assertEquals(24.10589, preferences.getLocationLongitude(), 0);
        assertEquals("Europe/Riga", preferences.getLocationTimeZone());
        assertEquals(30, preferences.getQuakeDays());
        assertTrue(preferences.isQuakeStrongest());
        assertEquals(1, preferences.getDetailsTab());
    }

    // The current location chosen is a place chosen too
    @Test
    public void currentLocationChosen() {
        WidgetConfig preferences = new WidgetConfig(new MapPreferences());
        preferences.edit().setPlaceId(-1).apply();
        assertTrue(preferences.hasLocation());
        assertEquals(-1, preferences.getPlaceId());
    }

    // A place without a time zone has none, also over one chosen before
    @Test
    public void placeWithoutTimeZone() {
        WidgetConfig preferences = new WidgetConfig(new MapPreferences());
        preferences.edit().setLocationPlace("Riga", 56.946, 24.10589, "Europe/Riga").apply();
        preferences.edit().setLocationPlace("Tokyo", 35.6895, 139.69171, null).apply();
        assertEquals("Tokyo", preferences.getLocationName());
        assertNull(preferences.getLocationTimeZone());
    }

    // The hours for the kinds by hours, the days for the rest
    @Test
    public void detailsTab() {
        for (int kind = WidgetKind.CHART_HOURLY; kind <= WidgetKind.NOW_AND_CLOCK; kind++) {
            WidgetConfig preferences = new WidgetConfig(new MapPreferences());
            preferences.edit().setKind(kind).apply();
            assertEquals("kind " + kind, WidgetKind.isHourly(kind) ? 0 : 1, preferences.getDetailsTab());
        }
    }

    // SharedPreferences in a map, its editor's changes kept apart till they are applied; a null string removes the key
    private static final class MapPreferences implements SharedPreferences {

        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(this.values);
        }

        @Override
        public String getString(String key, String defValue) {
            return this.values.containsKey(key) ? (String) this.values.get(key) : defValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return this.values.containsKey(key) ? (Set<String>) this.values.get(key) : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            return this.values.containsKey(key) ? (Integer) this.values.get(key) : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            return this.values.containsKey(key) ? (Long) this.values.get(key) : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            return this.values.containsKey(key) ? (Float) this.values.get(key) : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return this.values.containsKey(key) ? (Boolean) this.values.get(key) : defValue;
        }

        @Override
        public boolean contains(String key) {
            return this.values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MapEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        private final class MapEditor implements Editor {

            private final Map<String, Object> changes = new HashMap<>();
            private boolean cleared;

            private Editor put(String key, Object value) {
                this.changes.put(key, value);
                return this;
            }

            @Override
            public Editor putString(String key, String value) {
                return put(key, value);
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                return put(key, values);
            }

            @Override
            public Editor putInt(String key, int value) {
                return put(key, value);
            }

            @Override
            public Editor putLong(String key, long value) {
                return put(key, value);
            }

            @Override
            public Editor putFloat(String key, float value) {
                return put(key, value);
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                return put(key, value);
            }

            @Override
            public Editor remove(String key) {
                return put(key, null);
            }

            @Override
            public Editor clear() {
                this.cleared = true;
                return this;
            }

            @Override
            public boolean commit() {
                if (this.cleared) {
                    MapPreferences.this.values.clear();
                }
                for (Map.Entry<String, Object> change : this.changes.entrySet()) {
                    if (change.getValue() == null) {
                        MapPreferences.this.values.remove(change.getKey());
                    } else {
                        MapPreferences.this.values.put(change.getKey(), change.getValue());
                    }
                }
                return true;
            }

            @Override
            public void apply() {
                commit();
            }
        }
    }
}
