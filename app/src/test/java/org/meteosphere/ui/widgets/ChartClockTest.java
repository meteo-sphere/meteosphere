package org.meteosphere.ui.widgets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Where the chart widgets have their clock: above the card or not, over the time or beside it
public class ChartClockTest {

    // Above a card lower than 85% of the widget, when the clock is on
    @Test
    public void clockAbove() {
        assertTrue(ChartWidgetViews.clockAbove(true, 496, 876));
        assertTrue(ChartWidgetViews.clockAbove(true, 849, 1000));
        assertFalse(ChartWidgetViews.clockAbove(true, 850, 1000));
        assertFalse(ChartWidgetViews.clockAbove(true, 1200, 1000));
        assertFalse(ChartWidgetViews.clockAbove(false, 496, 876));
    }

    // Beside the time over a card of 75% of the widget or more, as on the home screen's 4x3 two cells high
    @Test
    public void extendedClock() {
        assertFalse(ChartWidgetViews.extendedClock(496, 876));
        assertFalse(ChartWidgetViews.extendedClock(749, 1000));
        assertTrue(ChartWidgetViews.extendedClock(750, 1000));
        assertTrue(ChartWidgetViews.extendedClock(496, 620));
        assertTrue(ChartWidgetViews.clockAbove(true, 496, 620));
    }

    // The date and the alarm beside the time in 95% of the half of the width it leaves
    @Test
    public void fitsBeside() {
        assertTrue(ChartWidgetViews.fitsBeside(320, 300, 974));
        assertFalse(ChartWidgetViews.fitsBeside(321, 300, 974));
        assertFalse(ChartWidgetViews.fitsBeside(1, 974, 974));
    }
}
