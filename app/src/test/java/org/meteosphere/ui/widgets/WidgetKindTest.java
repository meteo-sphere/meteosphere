package org.meteosphere.ui.widgets;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WidgetKindTest {

    // By hours: the even kinds, as the details' tab a widget opens has them
    @Test
    public void hourly() {
        for (int kind = -2; kind <= 12; kind++) {
            boolean hourly = kind == 0 || kind == 2 || kind == 4 || kind == 6 || kind == 8 || kind == 10;
            assertEquals("kind " + kind, hourly, WidgetKind.isHourly(kind));
        }
    }

    // The weather now over the chart: the 4x3's own two kinds
    @Test
    public void withNow() {
        for (int kind = -2; kind <= 12; kind++) {
            assertEquals("kind " + kind, kind == 8 || kind == 9, WidgetKind.withNow(kind));
        }
    }
}
