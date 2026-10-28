package org.meteosphere.ui.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// The size the days' ranges take where a narrow widget's columns are too narrow for them: the usual one where they fit,
// else the largest at which all of them do, the same for all
public class TextFitTest {

    // The 4x1's ranges, 15dp at 420 dpi
    private static final float USUAL = 39.375f;

    // Texts as wide as their widths at the usual size give, in the size, in whole pixels as their views take them
    private static WidgetFrame.TextWidths widths(float... usualWidths) {
        return size -> {
            float[] widths = new float[usualWidths.length];
            for (int i = 0; i < widths.length; i++) {
                widths[i] = (float) Math.ceil(usualWidths[i] * size / USUAL);
            }
            return widths;
        };
    }

    private static void assertAllFit(float size, float[] rooms, WidgetFrame.TextWidths widths) {
        float[] at = widths.in(size);
        for (int i = 0; i < at.length; i++) {
            assertTrue("text " + i + " at " + size, at[i] <= rooms[i]);
        }
    }

    // Where they fit nothing changes, nor grows where there is room to spare
    @Test
    public void fitsAtTheUsualSize() {
        float[] rooms = {161, 161, 162};
        assertEquals(USUAL, WidgetFrame.fittingSize(USUAL, rooms, widths(150, 140, 162)), 0);
        assertEquals(USUAL, WidgetFrame.fittingSize(USUAL, rooms, widths(20, 30, 40)), 0);
        assertEquals(USUAL, WidgetFrame.scaledSize(USUAL, new float[]{150, 161}, new float[]{161, 161}), 0);
    }

    // The one too wide by the most sets the size, the same for all three
    @Test
    public void shrinksToFit() {
        float[] rooms = {130, 130, 131};
        WidgetFrame.TextWidths widths = widths(150, 140, 162);
        float size = WidgetFrame.fittingSize(USUAL, rooms, widths);
        assertTrue(size < USUAL);
        assertAllFit(size, rooms, widths);
        // As large as fits: at most a few small steps under the share the widest's room is of its width
        float share = USUAL * 131 / 162;
        assertTrue(size <= share && size > share * 0.95f);
        assertEquals(share, WidgetFrame.scaledSize(USUAL, new float[]{150, 140, 162}, rooms), 0.001);
    }

    // A width that doesn't follow its size exactly, a pixel more than its share: the size steps down till it fits
    @Test
    public void shrinksFurtherWhenTheShareIsTooWide() {
        float[] rooms = {100};
        WidgetFrame.TextWidths widths = size -> new float[]{(float) Math.ceil(120 * size / USUAL) + (size < USUAL ? 1 : 0)};
        float size = WidgetFrame.fittingSize(USUAL, rooms, widths);
        assertAllFit(size, rooms, widths);
        assertTrue(size < USUAL * 100 / 120);
    }

    // A text with no room, as one of a widget not laid out yet, sets nothing
    @Test
    public void noRoomLeftOut() {
        assertEquals(USUAL, WidgetFrame.fittingSize(USUAL, new float[]{0, 161}, widths(150, 150)), 0);
    }
}
