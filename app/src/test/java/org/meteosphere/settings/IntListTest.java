package org.meteosphere.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

// The lists of numbers the settings keep as text: the city page's tiles and the main screen's tabs
public class IntListTest {

    @Test
    public void joinedByCommas() {
        assertEquals("3,0,12", AppSettings.listText(List.of(3, 0, 12)));
        assertEquals("7", AppSettings.listText(List.of(7)));
    }

    @Test
    public void noneAsEmpty() {
        assertEquals("", AppSettings.listText(null));
        assertEquals("", AppSettings.listText(new ArrayList<>()));
    }

    @Test
    public void readBack() {
        assertEquals(List.of(3, 0, 12), AppSettings.parseList(AppSettings.listText(List.of(3, 0, 12))));
        assertEquals(List.of(-1, 5), AppSettings.parseList("-1,5"));
    }

    // "" tells a list never saved
    @Test
    public void emptyIsNull() {
        assertNull(AppSettings.parseList(""));
    }

    @Test
    public void brokenPiecesLeftOut() {
        assertEquals(List.of(1, 4), AppSettings.parseList("1,x,,4,2.5,"));
        assertEquals(List.of(), AppSettings.parseList("x"));
    }
}
