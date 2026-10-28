package org.meteosphere.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.meteosphere.settings.AppSettings;

// What the main screen's tabs do as they come: the page they open again, whether they look for the device's position,
// and the page they show first
public class MainScreenTest {

    private static final int RIGA = 456172;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final long NOW = 1_800_000_000_000L;

    // --- The page opened again ---

    // The current location among the favourites' pages, only while one is kept
    @Test
    public void currentLocation() {
        assertEquals(MainTabsFragment.RESTORE_FAVORITES, MainTabsFragment.restoreChoice(-1, true, false, false));
        assertEquals(MainTabsFragment.RESTORE_NOTHING, MainTabsFragment.restoreChoice(-1, false, false, false));
    }

    @Test
    public void storedFavorite() {
        assertEquals(MainTabsFragment.RESTORE_FAVORITES, MainTabsFragment.restoreChoice(RIGA, true, true, true));
        assertEquals(MainTabsFragment.RESTORE_FAVORITES, MainTabsFragment.restoreChoice(RIGA, false, true, true));
    }

    @Test
    public void storedPlaceFound() {
        assertEquals(MainTabsFragment.RESTORE_FOUND, MainTabsFragment.restoreChoice(RIGA, true, true, false));
        assertEquals(MainTabsFragment.RESTORE_FOUND, MainTabsFragment.restoreChoice(RIGA, false, true, false));
    }

    // A favourite no longer stored opens nothing, with or without a current location
    @Test
    public void placeNoLongerStored() {
        assertEquals(MainTabsFragment.RESTORE_NOTHING, MainTabsFragment.restoreChoice(RIGA, false, false, true));
        assertEquals(MainTabsFragment.RESTORE_NOTHING, MainTabsFragment.restoreChoice(RIGA, true, false, true));
        assertEquals(MainTabsFragment.RESTORE_NOTHING, MainTabsFragment.restoreChoice(RIGA, true, false, false));
    }

    // 0, never shown, and -2, left for the tabs, are no places
    @Test
    public void noPlace() {
        assertEquals(MainTabsFragment.RESTORE_NOTHING, MainTabsFragment.restoreChoice(0, true, true, false));
        assertEquals(MainTabsFragment.RESTORE_NOTHING, MainTabsFragment.restoreChoice(-2, true, true, false));
        assertEquals(MainTabsFragment.RESTORE_NOTHING, MainTabsFragment.restoreChoice(-2, false, false, false));
    }

    // --- The search for the device's position ---

    @Test
    public void searchDueAtFiveMinutes() {
        assertTrue(MainTabsFragment.isLocationSearchDue(NOW - 5 * MINUTE_MS, NOW - 5 * MINUTE_MS, NOW));
        assertTrue(MainTabsFragment.isLocationSearchDue(NOW - 60 * MINUTE_MS, NOW - 6 * MINUTE_MS, NOW));
        assertFalse(MainTabsFragment.isLocationSearchDue(NOW - 5 * MINUTE_MS + 1, NOW - 60 * MINUTE_MS, NOW));
        assertFalse(MainTabsFragment.isLocationSearchDue(NOW, NOW, NOW));
        // Never before
        assertTrue(MainTabsFragment.isLocationSearchDue(0, 0, NOW));
    }

    // A search that found nothing saved no places, but its time keeps the next one off for five minutes
    @Test
    public void searchWithoutPlacesCounts() {
        assertFalse(MainTabsFragment.isLocationSearchDue(NOW - 60 * MINUTE_MS, NOW - MINUTE_MS, NOW));
        assertFalse(MainTabsFragment.isLocationSearchDue(0, NOW - 5 * MINUTE_MS + 1, NOW));
        assertTrue(MainTabsFragment.isLocationSearchDue(0, NOW - 5 * MINUTE_MS, NOW));
    }

    @Test
    public void searchDueWithClockSetBack() {
        assertTrue(MainTabsFragment.isLocationSearchDue(NOW + MINUTE_MS, NOW - 60 * MINUTE_MS, NOW));
        assertTrue(MainTabsFragment.isLocationSearchDue(NOW - 60 * MINUTE_MS, NOW + 1, NOW));
    }

    // --- The page shown first ---

    @Test
    public void lastTabWhenShown() {
        List<Integer> pages = List.of(AppSettings.TAB_NEARBY, AppSettings.TAB_FAVORITES, AppSettings.TAB_MOON, AppSettings.TAB_SUN);
        assertEquals(0, MainTabsFragment.firstPosition(pages, AppSettings.TAB_NEARBY));
        assertEquals(1, MainTabsFragment.firstPosition(pages, AppSettings.TAB_FAVORITES));
        assertEquals(3, MainTabsFragment.firstPosition(pages, AppSettings.TAB_SUN));
    }

    // A tab hidden since, or no tab known, gives the favourites
    @Test
    public void favoritesOtherwise() {
        List<Integer> pages = List.of(AppSettings.TAB_NEARBY, AppSettings.TAB_MOON, AppSettings.TAB_FAVORITES);
        assertEquals(2, MainTabsFragment.firstPosition(pages, AppSettings.TAB_EARTHQUAKES));
        assertEquals(2, MainTabsFragment.firstPosition(pages, -1));
        assertEquals(0, MainTabsFragment.firstPosition(List.of(AppSettings.TAB_FAVORITES), AppSettings.TAB_SUN));
    }
}
