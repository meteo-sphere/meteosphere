package org.meteosphere.settings;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

// The favourites' and the weather widgets' ids read from the texts they are kept as
public class KeptIdsTest {

    // Each id followed by a comma
    @Test
    public void favorites() {
        assertEquals(List.of(2643743, 2510911), AppSettings.parseFavoriteIds("2643743,2510911,"));
        assertEquals(List.of(2643743, 2510911), AppSettings.parseFavoriteIds("2643743,2510911"));
        assertEquals("2643743,2510911,", AppSettings.favoritesText(List.of(2643743, 2510911)));
        assertEquals(List.of(), AppSettings.parseFavoriteIds(""));
        assertEquals("", AppSettings.favoritesText(List.of()));
    }

    // Each once, where it came first
    @Test
    public void favoritesOnce() {
        assertEquals(List.of(3, 1, 2), AppSettings.parseFavoriteIds("3,1,3,2,1,"));
    }

    @Test
    public void favoritesWithBrokenPieces() {
        assertEquals(List.of(1, 2), AppSettings.parseFavoriteIds(",1,,abc,2,9999999999,"));
    }

    // Separated by single spaces
    @Test
    public void widgets() {
        assertEquals(List.of(12, 7, 31), AppSettings.parseWidgetIds("12 7 31"));
        assertEquals("12 7 31", AppSettings.widgetIdsText(List.of(12, 7, 31)));
        assertEquals(List.of(), AppSettings.parseWidgetIds(""));
        assertEquals("", AppSettings.widgetIdsText(List.of()));
    }

    @Test
    public void widgetsWithBrokenPieces() {
        assertEquals(List.of(12, 31), AppSettings.parseWidgetIds(" 12  x 31 "));
    }
}
