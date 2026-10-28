package org.meteosphere.ui.settings;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

// The items under the rows as a row is dragged past others
public class RowDragTest {

    private static List<String> moved(int from, int to) {
        List<String> items = new ArrayList<>(List.of("a", "b", "c", "d"));
        RowDrag.move(items, from, to);
        return items;
    }

    @Test
    public void pastTheNextOne() {
        assertEquals(List.of("a", "c", "b", "d"), moved(1, 2));
    }

    // A fast drag skips rows: those between shift by one
    @Test
    public void pastSeveralDown() {
        assertEquals(List.of("b", "c", "a", "d"), moved(0, 2));
    }

    @Test
    public void pastSeveralUp() {
        assertEquals(List.of("d", "a", "b", "c"), moved(3, 0));
    }
}
