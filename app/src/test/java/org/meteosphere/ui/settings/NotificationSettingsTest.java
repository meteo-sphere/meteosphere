package org.meteosphere.ui.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.function.IntFunction;
import org.junit.Test;
import org.meteosphere.data.model.Place;

// When the weather notification's settings may be applied, and the place they start with
public class NotificationSettingsTest {

    private static final int RIGA = 456172;

    private static Place place(int id, String name) {
        Place place = new Place();
        place.id = id;
        place.name = name;
        return place;
    }

    // Off, the place doesn't matter; on, the current location or a place chosen will do
    @Test
    public void mayApply() {
        assertTrue(NotificationSettingsFragment.mayApply(false, false, -1));
        assertTrue(NotificationSettingsFragment.mayApply(false, true, RIGA));
        assertTrue(NotificationSettingsFragment.mayApply(true, true, -1));
        assertTrue(NotificationSettingsFragment.mayApply(true, false, RIGA));
        assertFalse(NotificationSettingsFragment.mayApply(true, false, -1));
    }

    // -1 is the current location, which the app keeps under it too: no place chosen besides
    @Test
    public void currentLocationAtStart() {
        assertTrue(NotificationSettingsFragment.isAutomatic(-1));
        assertFalse(NotificationSettingsFragment.isAutomatic(RIGA));
        Place current = place(-1, "Jūrmala");
        assertNull(NotificationSettingsFragment.chosenPlace(-1, id -> current));
    }

    @Test
    public void storedPlaceAtStart() {
        Place riga = place(RIGA, "Riga");
        IntFunction<Place> stored = id -> id == RIGA ? riga : null;
        assertSame(riga, NotificationSettingsFragment.chosenPlace(RIGA, stored));
    }

    // Cleaned up since it was saved: none, and the tick then asks for one
    @Test
    public void placeNoLongerKeptAtStart() {
        assertNull(NotificationSettingsFragment.chosenPlace(RIGA, id -> null));
        assertFalse(NotificationSettingsFragment.mayApply(true, NotificationSettingsFragment.isAutomatic(RIGA), -1));
    }
}
