package org.meteosphere.ui.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.Test;

// What the city page opens and how its pages change: the favourites' pages, a widget's place, the page shown after a
// delete, and the current location's page as the settings change
public class CityPageTest {

    private static final int RIGA = 456172;
    private static final int TALLINN = 588409;
    private static final int VILNIUS = 593116;
    private static final List<Integer> FAVORITES = List.of(RIGA, TALLINN, VILNIUS);
    private static final IntPredicate ALL_STORED = id -> true;

    // --- The favourites' pages ---

    // The current location's page first, then the favourites in their order, whichever is opened
    @Test
    public void currentShownAndKept() {
        assertEquals(List.of(-1, RIGA, TALLINN, VILNIUS), DetailsActivity.favoritePages(-1, true, true, FAVORITES, ALL_STORED));
        assertEquals(List.of(-1, RIGA, TALLINN, VILNIUS), DetailsActivity.favoritePages(TALLINN, true, true, FAVORITES, ALL_STORED));
    }

    // Without a current location kept there is no page of it, also when it is the place opened
    @Test
    public void currentNotKept() {
        assertEquals(FAVORITES, DetailsActivity.favoritePages(TALLINN, false, true, FAVORITES, ALL_STORED));
        assertEquals(FAVORITES, DetailsActivity.favoritePages(-1, false, true, FAVORITES, ALL_STORED));
        assertEquals(FAVORITES, DetailsActivity.favoritePages(TALLINN, false, false, FAVORITES, ALL_STORED));
    }

    // The settings don't show it: a favourite opens the favourites alone
    @Test
    public void currentNotShown() {
        assertEquals(FAVORITES, DetailsActivity.favoritePages(RIGA, true, false, FAVORITES, ALL_STORED));
    }

    // Opened while the settings don't show it, the current location is alone; not kept either, there is no page
    @Test
    public void currentOpenedNotShown() {
        assertEquals(List.of(-1), DetailsActivity.favoritePages(-1, true, false, FAVORITES, ALL_STORED));
        assertEquals(List.of(), DetailsActivity.favoritePages(-1, false, false, FAVORITES, ALL_STORED));
        assertTrue(DetailsActivity.currentAlone(-1, false));
        assertFalse(DetailsActivity.currentAlone(-1, true));
        assertFalse(DetailsActivity.currentAlone(RIGA, false));
    }

    // A favourite without a stored place has no page; none stored and no current location, there are no pages
    @Test
    public void favoritesNotStored() {
        IntPredicate stored = Set.of(RIGA, VILNIUS)::contains;
        assertEquals(List.of(-1, RIGA, VILNIUS), DetailsActivity.favoritePages(RIGA, true, true, FAVORITES, stored));
        assertEquals(List.of(RIGA, VILNIUS), DetailsActivity.favoritePages(TALLINN, false, true, FAVORITES, stored));
        assertEquals(List.of(), DetailsActivity.favoritePages(RIGA, false, true, FAVORITES, id -> false));
        assertEquals(List.of(-1), DetailsActivity.favoritePages(RIGA, true, true, List.of(), ALL_STORED));
    }

    // --- A widget's place ---

    @Test
    public void widgetCurrentLocation() {
        assertEquals(DetailsActivity.ACTION_OPEN_FAVORITES, DetailsActivity.widgetAction(-1, true, false, false));
        assertNull(DetailsActivity.widgetAction(-1, false, false, false));
    }

    @Test
    public void widgetStoredFavorite() {
        assertEquals(DetailsActivity.ACTION_OPEN_FAVORITES, DetailsActivity.widgetAction(RIGA, true, true, true));
        assertEquals(DetailsActivity.ACTION_OPEN_FAVORITES, DetailsActivity.widgetAction(RIGA, false, true, true));
    }

    @Test
    public void widgetStoredPlaceAlone() {
        assertEquals(DetailsActivity.ACTION_OPEN_PLACE, DetailsActivity.widgetAction(RIGA, true, true, false));
        assertEquals(DetailsActivity.ACTION_OPEN_PLACE, DetailsActivity.widgetAction(RIGA, false, true, false));
    }

    // Nothing stored for it yet, a favourite or not: the page closes
    @Test
    public void widgetPlaceNotStored() {
        assertNull(DetailsActivity.widgetAction(RIGA, true, false, true));
        assertNull(DetailsActivity.widgetAction(RIGA, true, false, false));
        assertNull(DetailsActivity.widgetAction(RIGA, false, false, false));
    }

    // --- The place shown, and saved as the last shown, after a delete ---

    // The next one takes the deleted page's place
    @Test
    public void deleteInTheMiddle() {
        assertEquals(VILNIUS, DetailsActivity.placeAfterDelete(FAVORITES, TALLINN));
        assertEquals(TALLINN, DetailsActivity.placeAfterDelete(FAVORITES, RIGA));
        assertEquals(TALLINN, DetailsActivity.placeAfterDelete(List.of(RIGA, TALLINN), RIGA));
        assertEquals(TALLINN, DetailsActivity.placeAfterDelete(List.of(-1, RIGA, TALLINN), RIGA));
    }

    // The one before it, which is the last now, the current location's too
    @Test
    public void deleteTheLast() {
        assertEquals(TALLINN, DetailsActivity.placeAfterDelete(FAVORITES, VILNIUS));
        assertEquals(RIGA, DetailsActivity.placeAfterDelete(List.of(RIGA, TALLINN), TALLINN));
        assertEquals(-1, DetailsActivity.placeAfterDelete(List.of(-1, RIGA), RIGA));
    }

    // None: the page is left for the list, which saves no place as the last shown
    @Test
    public void deleteTheOnly() {
        assertEquals(DetailsActivity.NO_LOCATION, DetailsActivity.placeAfterDelete(List.of(RIGA), RIGA));
    }

    // --- The settings' change of the pages ---

    // Its page first while one is kept; already there, nothing changes
    @Test
    public void currentTurnedOn() {
        assertEquals(List.of(-1, RIGA, TALLINN), DetailsActivity.pagesAfterSettings(List.of(RIGA, TALLINN), true, true, false));
        assertEquals(List.of(-1), DetailsActivity.pagesAfterSettings(List.of(), true, true, false));
        assertEquals(List.of(-1, RIGA), DetailsActivity.pagesAfterSettings(List.of(-1, RIGA), true, true, false));
    }

    @Test
    public void currentTurnedOnNotKept() {
        assertEquals(List.of(RIGA, TALLINN), DetailsActivity.pagesAfterSettings(List.of(RIGA, TALLINN), true, false, false));
    }

    @Test
    public void currentTurnedOff() {
        assertEquals(List.of(RIGA, TALLINN), DetailsActivity.pagesAfterSettings(List.of(-1, RIGA, TALLINN), false, true, false));
        assertEquals(List.of(RIGA, TALLINN), DetailsActivity.pagesAfterSettings(List.of(RIGA, TALLINN), false, true, false));
    }

    // Its page the only one, none is left: the page closes
    @Test
    public void currentTurnedOffNoFavorites() {
        assertEquals(List.of(), DetailsActivity.pagesAfterSettings(List.of(-1), false, true, false));
        assertEquals(List.of(), DetailsActivity.pagesAfterSettings(List.of(-1), false, false, false));
    }

    // Opened alone, it stays
    @Test
    public void currentTurnedOffOpenedAlone() {
        assertEquals(List.of(-1), DetailsActivity.pagesAfterSettings(List.of(-1), false, true, true));
    }
}
