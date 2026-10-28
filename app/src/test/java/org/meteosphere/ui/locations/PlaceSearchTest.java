package org.meteosphere.ui.locations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.Test;
import org.meteosphere.R;
import org.meteosphere.data.model.Place;

// The places the search shows before anything is typed, the places found before once one is chosen, and the info
// line's text
public class PlaceSearchTest {

    private static final int RIGA = 456172;
    private static final int OSLO = 3143244;
    private static final int TALLINN = 588409;
    private static final int VILNIUS = 593116;

    private static Place place(int id, String name) {
        Place place = new Place();
        place.id = id;
        place.name = name;
        return place;
    }

    private static List<Integer> ids(List<Place> places) {
        List<Integer> res = new ArrayList<>();
        for (Place place : places) {
            res.add(place.id);
        }
        return res;
    }

    // --- The places found before after a choice ---

    @Test
    public void chosenGoesFirst() {
        List<Place> found = List.of(place(RIGA, "Riga"), place(OSLO, "Oslo"));
        Place tallinn = place(TALLINN, "Tallinn");
        List<Place> after = PlaceSearchFragment.foundAfterChoice(found, tallinn);
        assertEquals(List.of(TALLINN, RIGA, OSLO), ids(after));
        assertSame(tallinn, after.get(0));
    }

    // Chosen again, a place is there once, first, its older entry gone
    @Test
    public void olderEntryGoes() {
        List<Place> found = List.of(place(RIGA, "Riga"), place(OSLO, "Oslo"), place(TALLINN, "Tallinn"));
        assertEquals(List.of(OSLO, RIGA, TALLINN), ids(PlaceSearchFragment.foundAfterChoice(found, place(OSLO, "Oslo"))));
        assertEquals(List.of(RIGA, OSLO, TALLINN), ids(PlaceSearchFragment.foundAfterChoice(found, place(RIGA, "Riga"))));
    }

    @Test
    public void atMostTwentyKept() {
        List<Place> found = new ArrayList<>();
        for (int i = 1; i <= PlaceSearchFragment.FOUND_LIMIT; i++) {
            found.add(place(i, "Place " + i));
        }
        List<Place> after = PlaceSearchFragment.foundAfterChoice(found, place(RIGA, "Riga"));
        assertEquals(20, after.size());
        assertEquals(RIGA, after.get(0).id);
        // The oldest past the 20th dropped
        assertEquals(19, after.get(19).id);
    }

    // One of the full list chosen again keeps the others, none dropped
    @Test
    public void fullListChosenAgain() {
        List<Place> found = new ArrayList<>();
        for (int i = 1; i <= PlaceSearchFragment.FOUND_LIMIT; i++) {
            found.add(place(i, "Place " + i));
        }
        List<Place> after = PlaceSearchFragment.foundAfterChoice(found, place(20, "Place 20"));
        assertEquals(20, after.size());
        assertEquals(20, after.get(0).id);
        assertEquals(19, after.get(19).id);
    }

    @Test
    public void firstChoice() {
        assertEquals(List.of(RIGA), ids(PlaceSearchFragment.foundAfterChoice(List.of(), place(RIGA, "Riga"))));
    }

    // --- The history of each mode ---

    private static final List<Place> FOUND = List.of(place(RIGA, "Riga"), place(OSLO, "Oslo"), place(TALLINN, "Tallinn"));
    private static final List<Integer> FAVORITES = List.of(VILNIUS, OSLO);

    // The favourites as stored, Vilnius not among the places found before
    private static final IntFunction<Place> STORED = id -> id == VILNIUS ? place(VILNIUS, "Vilnius") : id == OSLO ? place(OSLO, "Oslo") : null;

    @Test
    public void searchShowsAllFound() {
        assertEquals(List.of(RIGA, OSLO, TALLINN), ids(PlaceSearchFragment.history(PlaceSearchFragment.MODE_SEARCH, FOUND, FAVORITES, STORED)));
    }

    @Test
    public void addLeavesOutFavorites() {
        assertEquals(List.of(RIGA, TALLINN), ids(PlaceSearchFragment.history(PlaceSearchFragment.MODE_ADD, FOUND, FAVORITES, STORED)));
    }

    // The favourites first in their order, then the places found before that aren't favourites
    @Test
    public void pickShowsFavoritesFirst() {
        assertEquals(List.of(VILNIUS, OSLO, RIGA, TALLINN),
                ids(PlaceSearchFragment.history(PlaceSearchFragment.MODE_PICK, FOUND, FAVORITES, STORED)));
    }

    // A favourite no longer stored is left out
    @Test
    public void pickLeavesOutFavoritesNotStored() {
        IntFunction<Place> onlyOslo = id -> id == OSLO ? place(OSLO, "Oslo") : null;
        assertEquals(List.of(OSLO, RIGA, TALLINN),
                ids(PlaceSearchFragment.history(PlaceSearchFragment.MODE_PICK, FOUND, FAVORITES, onlyOslo)));
    }

    @Test
    public void nothingFoundBefore() {
        assertEquals(List.of(), ids(PlaceSearchFragment.history(PlaceSearchFragment.MODE_SEARCH, List.of(), FAVORITES, STORED)));
        assertEquals(List.of(), ids(PlaceSearchFragment.history(PlaceSearchFragment.MODE_ADD, List.of(), FAVORITES, STORED)));
        assertEquals(List.of(VILNIUS, OSLO), ids(PlaceSearchFragment.history(PlaceSearchFragment.MODE_PICK, List.of(), FAVORITES, STORED)));
    }

    // --- The info line ---

    @Test
    public void answerWithPlacesFolds() {
        assertEquals(0, PlaceSearchFragment.infoText(FOUND, true, false));
    }

    @Test
    public void answerWithoutPlaces() {
        assertEquals(R.string.search_nothing_found, PlaceSearchFragment.infoText(List.of(), true, false));
    }

    // No connection or no server: not as if there were no such place
    @Test
    public void failedSearch() {
        assertEquals(R.string.search_failed, PlaceSearchFragment.infoText(List.of(), true, true));
    }

    // One character typed, or the places found before shown without any
    @Test
    public void noSearch() {
        assertEquals(R.string.search_type_more, PlaceSearchFragment.infoText(List.of(), false, false));
        assertEquals(0, PlaceSearchFragment.infoText(FOUND, false, false));
    }
}
