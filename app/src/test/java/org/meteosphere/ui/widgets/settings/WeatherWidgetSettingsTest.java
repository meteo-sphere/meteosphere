package org.meteosphere.ui.widgets.settings;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.meteosphere.ui.widgets.WeatherWidget1x1;
import org.meteosphere.ui.widgets.WeatherWidget2x1;
import org.meteosphere.ui.widgets.WeatherWidget4x1;
import org.meteosphere.ui.widgets.WeatherWidgetClock;
import org.meteosphere.ui.widgets.WeatherWidget4x2;
import org.meteosphere.ui.widgets.WeatherWidget4x3;
import org.meteosphere.ui.widgets.WidgetKind;

// What the weather widgets' settings show for each size of widget, how large its previews are, and its glass
public class WeatherWidgetSettingsTest {

    private static final String ICON = WeatherWidget1x1.class.getName();
    private static final String NOW = WeatherWidget2x1.class.getName();
    private static final String NOW_AND_FORECAST = WeatherWidget4x1.class.getName();
    private static final String CHART = WeatherWidget4x2.class.getName();
    private static final String CHART_WITH_CLOCK = WeatherWidget4x3.class.getName();
    private static final String NOW_AND_CLOCK = WeatherWidgetClock.class.getName();

    // By hours, then by days; only the 4x3's follow the weather now
    @Test
    public void kinds() {
        for (boolean showCurrentWeather : new boolean[]{false, true}) {
            assertArrayEquals(new int[]{WidgetKind.ICON_HOURLY, WidgetKind.ICON_DAILY}, WeatherWidgetSettingsFragment.kinds(ICON, showCurrentWeather));
            assertArrayEquals(new int[]{WidgetKind.FACT_HOURLY, WidgetKind.FACT_DAILY}, WeatherWidgetSettingsFragment.kinds(NOW, showCurrentWeather));
            assertArrayEquals(new int[]{WidgetKind.FACT_AND_FORECAST_HOURLY, WidgetKind.FACT_AND_FORECAST_DAILY},
                    WeatherWidgetSettingsFragment.kinds(NOW_AND_FORECAST, showCurrentWeather));
            assertArrayEquals(new int[]{WidgetKind.CHART_HOURLY, WidgetKind.CHART_DAILY}, WeatherWidgetSettingsFragment.kinds(CHART, showCurrentWeather));
            assertArrayEquals(new int[]{WidgetKind.NOW_AND_CLOCK}, WeatherWidgetSettingsFragment.kinds(NOW_AND_CLOCK, showCurrentWeather));
        }
        assertArrayEquals(new int[]{WidgetKind.CHART_HOURLY, WidgetKind.CHART_DAILY}, WeatherWidgetSettingsFragment.kinds(CHART_WITH_CLOCK, false));
        assertArrayEquals(new int[]{WidgetKind.CHART_HOURLY_WITH_NOW, WidgetKind.CHART_DAILY_WITH_NOW},
                WeatherWidgetSettingsFragment.kinds(CHART_WITH_CLOCK, true));
    }

    // The weather now on the 4x3, the wind and the precipitation on both charts, the clock on the 4x3 and the 4x2 with it
    @Test
    public void rows() {
        String[] providers = {ICON, NOW, NOW_AND_FORECAST, CHART, CHART_WITH_CLOCK, NOW_AND_CLOCK};
        for (String provider : providers) {
            assertEquals(provider, provider.equals(CHART_WITH_CLOCK), WeatherWidgetSettingsFragment.offersCurrentWeather(provider));
            assertEquals(provider, provider.equals(CHART) || provider.equals(CHART_WITH_CLOCK), WeatherWidgetSettingsFragment.isChart(provider));
            assertEquals(provider, provider.equals(CHART_WITH_CLOCK) || provider.equals(NOW_AND_CLOCK), WeatherWidgetSettingsFragment.hasClock(provider));
        }
    }

    // The saved kind's page, the first for a kind the widget hasn't
    @Test
    public void pageOfKind() {
        int[] withNow = WeatherWidgetSettingsFragment.kinds(CHART_WITH_CLOCK, true);
        assertEquals(1, WeatherWidgetSettingsFragment.pageOf(withNow, WidgetKind.CHART_DAILY_WITH_NOW));
        assertEquals(0, WeatherWidgetSettingsFragment.pageOf(withNow, WidgetKind.CHART_HOURLY_WITH_NOW));
        // Saved without the weather now, which is on now
        assertEquals(0, WeatherWidgetSettingsFragment.pageOf(withNow, WidgetKind.CHART_DAILY));
        assertEquals(0, WeatherWidgetSettingsFragment.pageOf(WeatherWidgetSettingsFragment.kinds(NOW_AND_CLOCK, false), WidgetKind.CHART_HOURLY));
    }

    // A phone of 1080 by 2340 pixels at 2.75 pixels a dp: the widgets 20 dp in from the sides, 970 wide
    @Test
    public void previewSizes() {
        assertSize(970, 600, 600, WidgetKind.CHART_HOURLY, 1080, 2340, 55, 970, 600, false);
        assertSize(970, 600, 600, WidgetKind.CHART_DAILY, 1080, 2340, 55, 970, 600, false);
        // Under the clock nine tenths as high as wide, whatever the widget's size
        assertSize(970, 873, 873, WidgetKind.CHART_HOURLY_WITH_NOW, 1080, 2340, 55, 970, 600, true);
        assertSize(970, 873, 873, WidgetKind.CHART_DAILY, 1080, 2340, 55, 0, 0, true);
        // 2340 / 5.5 = 425.45 high, twice that for the pager
        assertSize(270, 425, 850, WidgetKind.ICON_HOURLY, 1080, 2340, 55, 970, 600, false);
        assertSize(490, 425, 850, WidgetKind.FACT_DAILY, 1080, 2340, 55, 970, 600, false);
        assertSize(970, 425, 850, WidgetKind.FACT_AND_FORECAST_HOURLY, 1080, 2340, 55, 970, 600, false);
        // Without the margins nine tenths of the screen
        assertSize(972, 425, 850, WidgetKind.FACT_AND_FORECAST_DAILY, 1080, 2340, 0, 970, 600, false);
        // Shaped as the widget: 970 * 523 / 1000 = 507.31; or as 360 by 188: 970 * 188 / 360 = 506.56
        assertSize(970, 507, 507, WidgetKind.NOW_AND_CLOCK, 1080, 2340, 55, 1000, 523, true);
        assertSize(970, 600, 600, WidgetKind.NOW_AND_CLOCK, 1080, 2340, 55, 970, 600, true);
        assertSize(970, 507, 507, WidgetKind.NOW_AND_CLOCK, 1080, 2340, 55, 0, 600, true);
        assertSize(970, 507, 507, WidgetKind.NOW_AND_CLOCK, 1080, 2340, 55, 970, 0, true);
    }

    // The casts truncate: 1079 / 4 = 269.75, 1079 / 2.2 = 490.45, 2400 / 5.5 = 436.36, 0.9 * 969 = 872.1
    @Test
    public void previewSizesTruncated() {
        assertSize(269, 436, 872, WidgetKind.ICON_DAILY, 1079, 2400, 55, 969, 600, false);
        assertSize(490, 436, 872, WidgetKind.FACT_HOURLY, 1079, 2400, 55, 969, 600, false);
        assertSize(969, 872, 872, WidgetKind.CHART_HOURLY, 1079, 2400, 55, 969, 600, true);
    }

    // Scaled down to the stage only where wider than it
    @Test
    public void scaledToStage() {
        assertEquals(1, WeatherWidgetSettingsFragment.scale(992, 970), 0);
        assertEquals(1, WeatherWidgetSettingsFragment.scale(992, 992), 0);
        assertEquals(0.9f, WeatherWidgetSettingsFragment.scale(900, 1000), 1e-6);
        assertEquals(970, WeatherWidgetSettingsFragment.homeWidth(1080, 55));
        // The stage 16 dp in from the sides, wider than a widget on the home screen
        assertEquals(850, WeatherWidgetSettingsFragment.pagerHeight(850, 992, 970));
        // 600 * 900 / 970 = 556.70
        assertEquals(557, WeatherWidgetSettingsFragment.pagerHeight(600, 900, 970));
    }

    // Dark where the background's green is 0, light otherwise
    @Test
    public void glassOfBackground() {
        assertEquals(WeatherWidgetSettingsFragment.GLASS_DARK, WeatherWidgetSettingsFragment.glass(0xff000000));
        assertEquals(WeatherWidgetSettingsFragment.GLASS_DARK, WeatherWidgetSettingsFragment.glass(0x00000000));
        assertEquals(WeatherWidgetSettingsFragment.GLASS_LIGHT, WeatherWidgetSettingsFragment.glass(0xffffffff));
        assertEquals(WeatherWidgetSettingsFragment.GLASS_LIGHT, WeatherWidgetSettingsFragment.glass(0x00ffffff));
        assertEquals(WeatherWidgetSettingsFragment.GLASS_LIGHT, WeatherWidgetSettingsFragment.glass(0x80000100));
        assertEquals(WeatherWidgetSettingsFragment.GLASS_DARK, WeatherWidgetSettingsFragment.glass(0xffff00ff));
    }

    // The glass's colour as transparent as the slider, the slider showing that transparency again
    @Test
    public void backgroundOfGlass() {
        assertEquals(0xff000000, WeatherWidgetSettingsFragment.backgroundColor(0xff000000, 0));
        assertEquals(0x00ffffff, WeatherWidgetSettingsFragment.backgroundColor(0xffffffff, 100));
        // 255 - 50 * 2.55 = 127.5
        assertEquals(0x80ffffff, WeatherWidgetSettingsFragment.backgroundColor(0xffffffff, 50));
        // The colour's own alpha gives way: 255 - 25 * 2.55 = 191.25
        assertEquals(0xbf000000, WeatherWidgetSettingsFragment.backgroundColor(0x40000000, 25));
        for (int transparency = 0; transparency <= 100; transparency++) {
            int dark = WeatherWidgetSettingsFragment.backgroundColor(0xff000000, transparency);
            int light = WeatherWidgetSettingsFragment.backgroundColor(0xffffffff, transparency);
            assertEquals(WeatherWidgetSettingsFragment.GLASS_DARK, WeatherWidgetSettingsFragment.glass(dark));
            assertEquals(WeatherWidgetSettingsFragment.GLASS_LIGHT, WeatherWidgetSettingsFragment.glass(light));
            assertEquals(transparency, WidgetSettingsRows.transparency(dark >>> 24));
            assertEquals(transparency, WidgetSettingsRows.transparency(light >>> 24));
        }
    }

    private static void assertSize(int width, int height, int pagerHeight, int kind, int screenWidth, int screenHeight,
                                   int widgetMargin, int widgetWidth, int widgetHeight, boolean hasClock) {
        WeatherWidgetSettingsFragment.PreviewSize size = WeatherWidgetSettingsFragment.previewSize(kind, screenWidth, screenHeight, widgetMargin,
                widgetWidth, widgetHeight, hasClock);
        String what = "kind " + kind + " on " + screenWidth + "x" + screenHeight;
        assertEquals(what, width, size.width);
        assertEquals(what, height, size.height);
        assertEquals(what, pagerHeight, size.pagerHeight);
    }
}
