package org.meteosphere.ui.widgets.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TransparencyTest {

    // As the weather and the earthquake widgets' settings each worked it out
    @Test
    public void percentOfAlpha() {
        for (int alpha = 0; alpha <= 255; alpha++) {
            int weather = Math.max(0, Math.min(100, Math.round(100f - alpha / 2.55f)));
            int quake = Math.max(0, Math.min(100, Math.round(100 - alpha / 2.55f)));
            assertEquals(weather, WidgetSettingsRows.transparency(alpha));
            assertEquals(quake, WidgetSettingsRows.transparency(alpha));
        }
    }

    @Test
    public void alphaOfPercent() {
        for (int step = 0; step <= 1000; step++) {
            float percent = step / 10f;
            assertEquals(Math.round(255f - Math.round(percent) * 2.55f), WidgetSettingsRows.alpha(Math.round(percent)));
            assertEquals(Math.round(255 - percent * 2.55f), WidgetSettingsRows.alpha(percent));
        }
    }
}
