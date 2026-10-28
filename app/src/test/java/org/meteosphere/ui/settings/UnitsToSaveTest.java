package org.meteosphere.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

// What the units screen's tick saves: all five once one is chosen anew, nothing while they stay as they are, so that
// units never chosen follow the app's language
public class UnitsToSaveTest {

    private static final List<String> KEYS = List.of("temperature", "wind", "pressure", "distance", "precipitation");

    // The settings as the language gives them: Russian's
    private static final Map<String, String> SETTINGS = Map.of("temperature", "0", "wind", "0", "pressure", "0",
            "distance", "0", "precipitation", "0");

    @Test
    public void nothingChosen() {
        assertTrue(UnitsFragment.toSave(KEYS, Map.of(), SETTINGS::get).isEmpty());
    }

    // Chosen as it was, or chosen and chosen back
    @Test
    public void chosenAsItWas() {
        assertTrue(UnitsFragment.toSave(KEYS, Map.of("wind", "0"), SETTINGS::get).isEmpty());
    }

    // One chosen anew keeps the other four as they are now
    @Test
    public void oneChosen() {
        Map<String, String> saved = UnitsFragment.toSave(KEYS, Map.of("wind", "1"), SETTINGS::get);
        assertEquals(Map.of("temperature", "0", "wind", "1", "pressure", "0", "distance", "0", "precipitation", "0"), saved);
    }

    // Among others chosen as they were
    @Test
    public void oneOfSeveralChosen() {
        Map<String, String> saved = UnitsFragment.toSave(KEYS, Map.of("temperature", "0", "pressure", "1"), SETTINGS::get);
        assertEquals(Map.of("temperature", "0", "wind", "0", "pressure", "1", "distance", "0", "precipitation", "0"), saved);
    }
}
