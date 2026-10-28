package org.meteosphere.ui.settings;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.Test;

// The city page's tiles on their settings screen: the dragged ones' order from the saved one, and what is saved
public class ParametersTest {

    @Test
    public void noOrderSaved() {
        assertEquals(List.of(2, 3, 4, 5, 6, 7), ParametersFragment.draggedOrder(null));
    }

    // The page's own and the fixed ones aren't dragged; those the order lacks follow by their ids
    @Test
    public void savedOrderWithOthersAndGaps() {
        assertEquals(List.of(6, 3, 2, 4, 5, 7), ParametersFragment.draggedOrder(List.of(0, 1, 6, 8, 3, 9, 2)));
    }

    @Test
    public void eachOnce() {
        assertEquals(List.of(5, 4, 2, 3, 6, 7), ParametersFragment.draggedOrder(List.of(5, 5, 4)));
    }

    @Test
    public void savedOrder() {
        assertEquals(List.of(0, 1, 6, 3, 2, 4, 5, 7, 8, 9), ParametersFragment.savedOrder(List.of(6, 3, 2, 4, 5, 7)));
    }

    // The page's own are always shown, the others when ticked, all in the saved order
    @Test
    public void shownFromTheTicks() {
        List<Integer> order = ParametersFragment.savedOrder(List.of(7, 3, 2, 4, 5, 6));
        assertEquals(List.of(0, 1, 7, 3, 9), ParametersFragment.shownOf(order, Set.of(9, 3, 7)));
        assertEquals(List.of(0, 1), ParametersFragment.shownOf(order, Set.of()));
    }
}
