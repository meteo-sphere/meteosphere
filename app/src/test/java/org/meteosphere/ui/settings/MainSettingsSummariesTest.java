package org.meteosphere.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.meteosphere.R;

// The summaries the main list computes: the units, the Foreca key and the language
public class MainSettingsSummariesTest {

    private static final Map<Integer, String[]> NAMES = Map.of(
            R.array.temperature_units, new String[]{"°C", "°F"},
            R.array.wind_units, new String[]{"м/с", "км/ч", "миль/ч", "уз"},
            R.array.pressure_units, new String[]{"мм рт. ст.", "гПа", "мбар", "дюйм рт. ст."},
            R.array.distance_units, new String[]{"км", "мили"},
            R.array.precipitation_units, new String[]{"мм", "дюймы"});

    private static String summary(String temperature, String wind, String pressure, String distance, String precipitation) {
        Map<Integer, String> saved = new HashMap<>();
        saved.put(R.string.key_temperature_unit, temperature);
        saved.put(R.string.key_wind_unit, wind);
        saved.put(R.string.key_pressure_unit, pressure);
        saved.put(R.string.key_distance_unit, distance);
        saved.put(R.string.key_precipitation_unit, precipitation);
        return SettingsFragment.unitsSummary(saved::get, NAMES::get);
    }

    // In the units screen's order: the pressure before the distance
    @Test
    public void unitsInTheUnitsScreensOrder() {
        assertEquals("°C, м/с, мм рт. ст., км, мм", summary("0", "0", "0", "0", "0"));
        assertEquals("°F, миль/ч, гПа, мили, дюймы", summary("1", "2", "1", "1", "1"));
    }

    @Test
    public void unknownOrOutOfRangeUnitShowsTheFirst() {
        assertEquals("°C, м/с, мм рт. ст., км, мм", summary("x", "4", "-1", "", null));
        assertEquals(3, SettingsFragment.unitIndex("3", 4));
        assertEquals(0, SettingsFragment.unitIndex("1.5", 4));
    }

    @Test
    public void noForecaKey() {
        assertNull(SettingsFragment.forecaKeySummary(""));
        assertNull(SettingsFragment.forecaKeySummary(null));
    }

    // Eight characters and fewer keep all of them hidden
    @Test
    public void shortForecaKey() {
        assertEquals("••••", SettingsFragment.forecaKeySummary("abc"));
        assertEquals("••••", SettingsFragment.forecaKeySummary("abcd1234"));
    }

    @Test
    public void longForecaKeyShowsItsLastFour() {
        assertEquals("••••5678", SettingsFragment.forecaKeySummary("abcd12345678"));
        assertEquals("••••1234", SettingsFragment.forecaKeySummary("abcd-1234"));
    }

    @Test
    public void languageByItsOwnName() {
        String[] codes = {"ru", "en", "zh", "zh-Hant"};
        String[] names = {"Русский", "English", "简体中文", "繁體中文"};
        assertEquals("繁體中文", SettingsFragment.languageName("zh-Hant", codes, names));
        assertEquals("Русский", SettingsFragment.languageName("ru", codes, names));
        assertEquals("eo", SettingsFragment.languageName("eo", codes, names));
    }
}
