package org.meteosphere.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.meteosphere.BuildConfig;
import org.meteosphere.data.WeatherProviders;

// What the main screen is told as the settings close: whether a value noted changed, and whether the weather would now
// come from elsewhere. Some answers depend on whether the build has the server
public class SettingsResultTest {

    // After the provider, the source and the Foreca key: the units, the switches, the tabs, the city page's tiles and
    // the notification
    private static final List<String> REST = List.of("0", "0", "0", "0", "0", "true", "true", "true", "true",
            "0,1,3,4,2", "0,1,3,4,2", "", "0,1,2,3,4,5,6,7,8,9", "false", "-1", "false", "0");

    private static List<String> note(String provider, String source, String key) {
        List<String> note = new ArrayList<>(List.of(provider, source, key));
        note.addAll(REST);
        return note;
    }

    private static List<String> with(List<String> note, int index, String value) {
        List<String> changed = new ArrayList<>(note);
        changed.set(index, value);
        return changed;
    }

    private static final List<String> BEFORE = note(WeatherProviders.REMOTE, WeatherProviders.OPEN_METEO, "");

    @Test
    public void noChange() {
        assertFalse(SettingsActivity.changed(BEFORE, note(WeatherProviders.REMOTE, WeatherProviders.OPEN_METEO, "")));
        assertFalse(SettingsActivity.isDataProviderChanged(BEFORE, note(WeatherProviders.REMOTE, WeatherProviders.OPEN_METEO, "")));
    }

    // Chosen and then chosen back, the values are as noted
    @Test
    public void changeUndone() {
        List<String> changed = with(BEFORE, SettingsActivity.NOTE_SOURCE, WeatherProviders.FORECA);
        assertTrue(SettingsActivity.changed(BEFORE, changed));
        List<String> undone = with(changed, SettingsActivity.NOTE_SOURCE, WeatherProviders.OPEN_METEO);
        assertFalse(SettingsActivity.changed(BEFORE, undone));
        assertFalse(SettingsActivity.isDataProviderChanged(BEFORE, undone));
    }

    // Any of the units, the switches, the tabs, the tiles and the notification's four
    @Test
    public void eachKindOfChange() {
        for (int i = SettingsActivity.NOTE_FORECA_KEY + 1; i < BEFORE.size(); i++) {
            List<String> changed = with(BEFORE, i, BEFORE.get(i) + "1");
            assertTrue("value " + i, SettingsActivity.changed(BEFORE, changed));
            assertFalse("value " + i, SettingsActivity.isDataProviderChanged(BEFORE, changed));
        }
        assertTrue(SettingsActivity.changed(BEFORE, with(BEFORE, SettingsActivity.NOTE_SOURCE, WeatherProviders.FORECA)));
        assertTrue(SettingsActivity.changed(BEFORE, with(BEFORE, SettingsActivity.NOTE_FORECA_KEY, "key")));
    }

    // The provider counts as the one that works for it: without the server, the device makes the data either way
    @Test
    public void providerAsTheOneThatWorks() {
        List<String> local = with(BEFORE, SettingsActivity.NOTE_PROVIDER, WeatherProviders.LOCAL);
        assertEquals(WeatherProviders.hasServer(), SettingsActivity.changed(BEFORE, local));
        assertEquals(WeatherProviders.hasServer(), SettingsActivity.isDataProviderChanged(BEFORE, local));
        List<String> unknown = with(BEFORE, SettingsActivity.NOTE_PROVIDER, "unknown");
        assertFalse(SettingsActivity.changed(BEFORE, unknown));
        assertFalse(SettingsActivity.isDataProviderChanged(BEFORE, unknown));
    }

    // Noted before any was saved, the default is the one the main list saves when it first shows
    @Test
    public void defaultProviderSavedSince() {
        List<String> unsaved = with(BEFORE, SettingsActivity.NOTE_PROVIDER, WeatherProviders.DEFAULT_DATA_PROVIDER);
        assertFalse(SettingsActivity.changed(unsaved, BEFORE));
    }

    @Test
    public void anotherSource() {
        assertTrue(SettingsActivity.isDataProviderChanged(BEFORE, with(BEFORE, SettingsActivity.NOTE_SOURCE, WeatherProviders.FORECA)));
    }

    // The device itself asks Foreca with the key: another one tells the main screen to load the weather anew
    @Test
    public void anotherKeyWhileTheDeviceAsksForeca() {
        List<String> before = note(WeatherProviders.LOCAL, WeatherProviders.FORECA, "first key");
        List<String> now = note(WeatherProviders.LOCAL, WeatherProviders.FORECA, "second key");
        assertTrue(SettingsActivity.changed(before, now));
        assertTrue(SettingsActivity.isDataProviderChanged(before, now));
    }

    // Asked by the server, or not asked at all, the key changes only the settings
    @Test
    public void anotherKeyWhileTheDeviceDoesNotAskForeca() {
        List<String> before = note(WeatherProviders.LOCAL, WeatherProviders.OPEN_METEO, "first key");
        List<String> now = note(WeatherProviders.LOCAL, WeatherProviders.OPEN_METEO, "second key");
        assertTrue(SettingsActivity.changed(before, now));
        assertFalse(SettingsActivity.isDataProviderChanged(before, now));
        // Without the server the device asks Foreca itself whatever the provider saved
        before = note(WeatherProviders.REMOTE, WeatherProviders.FORECA, "first key");
        now = note(WeatherProviders.REMOTE, WeatherProviders.FORECA, "second key");
        assertEquals(!WeatherProviders.hasServer(), SettingsActivity.isDataProviderChanged(before, now));
    }

    // No key given asks with the build's own: given it, the device asks with the same one
    @Test
    public void theBuildsOwnKeyGiven() {
        List<String> before = note(WeatherProviders.LOCAL, WeatherProviders.FORECA, "");
        List<String> now = note(WeatherProviders.LOCAL, WeatherProviders.FORECA, BuildConfig.FORECA_TOKEN);
        assertFalse(SettingsActivity.isDataProviderChanged(before, now));
    }
}
