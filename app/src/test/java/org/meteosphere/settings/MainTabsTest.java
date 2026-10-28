package org.meteosphere.settings;

import static org.junit.Assert.assertEquals;
import static org.meteosphere.settings.AppSettings.TAB_EARTHQUAKES;
import static org.meteosphere.settings.AppSettings.TAB_FAVORITES;
import static org.meteosphere.settings.AppSettings.TAB_MOON;
import static org.meteosphere.settings.AppSettings.TAB_NEARBY;
import static org.meteosphere.settings.AppSettings.TAB_SUN;

import java.util.List;
import org.junit.Test;

// The main screen's tabs from the order and the hidden ones saved in the settings
public class MainTabsTest {

    private static final List<Integer> DEFAULT = List.of(TAB_NEARBY, TAB_FAVORITES, TAB_MOON, TAB_SUN, TAB_EARTHQUAKES);

    @Test
    public void defaultOrderBeforeAnySaved() {
        assertEquals(DEFAULT, AppSettings.sortedMainTabs(""));
        assertEquals(DEFAULT, AppSettings.mainTabs("", ""));
    }

    @Test
    public void savedOrder() {
        assertEquals(List.of(TAB_SUN, TAB_FAVORITES, TAB_NEARBY, TAB_EARTHQUAKES, TAB_MOON), AppSettings.sortedMainTabs("4,1,0,2,3"));
    }

    // A tab added after the order was saved comes after the saved ones, in the default order
    @Test
    public void missingTabAtTheEnd() {
        assertEquals(List.of(TAB_FAVORITES, TAB_NEARBY, TAB_EARTHQUAKES, TAB_MOON, TAB_SUN), AppSettings.sortedMainTabs("1,0,2"));
    }

    // An unknown tab, one twice and a broken piece are left out
    @Test
    public void unknownTabsLeftOut() {
        assertEquals(List.of(TAB_SUN, TAB_NEARBY, TAB_FAVORITES, TAB_MOON, TAB_EARTHQUAKES), AppSettings.sortedMainTabs("4,7,-1,0,4,x"));
    }

    @Test
    public void hiddenLeftOut() {
        assertEquals(List.of(TAB_FAVORITES, TAB_SUN), AppSettings.mainTabs("1,4,0,3,2", "0,3,2"));
        // Hidden, a tab missing from the order isn't added at the end either
        assertEquals(List.of(TAB_NEARBY, TAB_FAVORITES, TAB_SUN), AppSettings.mainTabs("0,1", "2,3"));
    }

    // The favourites are always shown
    @Test
    public void favoritesNeverHidden() {
        assertEquals(List.of(TAB_FAVORITES, TAB_EARTHQUAKES), AppSettings.mainTabs("1,2,0,3,4", "0,1,3,4"));
    }
}
