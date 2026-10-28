package org.meteosphere.ui.widgets;

/**
 * The weather widgets' kinds as their settings keep them: each size of widget has one by hours and one by days, the
 * chart's also with the weather now over it; the weather now with the clock has one
 */
public final class WidgetKind {

    /** The chart, 4x2 and 4x3 */
    public static final int CHART_HOURLY = 0;
    public static final int CHART_DAILY = 1;
    /** The icon and the temperature, 1x1 */
    public static final int ICON_HOURLY = 2;
    public static final int ICON_DAILY = 3;
    /** The weather now and the forecast in a row, 4x1 */
    public static final int FACT_AND_FORECAST_HOURLY = 4;
    public static final int FACT_AND_FORECAST_DAILY = 5;
    /** The weather now and the next hours or days, 2x1 */
    public static final int FACT_HOURLY = 6;
    public static final int FACT_DAILY = 7;
    /** The chart with the weather now over it, 4x3 */
    public static final int CHART_HOURLY_WITH_NOW = 8;
    public static final int CHART_DAILY_WITH_NOW = 9;
    /** The weather now with the clock, 4x2 */
    public static final int NOW_AND_CLOCK = 10;

    private WidgetKind() {
    }

    /** By hours, the rest by days; the weather now goes with the hours */
    public static boolean isHourly(int kind) {
        return kind == CHART_HOURLY || kind == ICON_HOURLY || kind == FACT_AND_FORECAST_HOURLY || kind == FACT_HOURLY
                || kind == CHART_HOURLY_WITH_NOW || kind == NOW_AND_CLOCK;
    }

    /** The chart with the weather now over it */
    public static boolean withNow(int kind) {
        return kind == CHART_HOURLY_WITH_NOW || kind == CHART_DAILY_WITH_NOW;
    }
}
