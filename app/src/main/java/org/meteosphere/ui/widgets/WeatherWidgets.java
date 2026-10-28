package org.meteosphere.ui.widgets;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;
import org.meteosphere.R;
import org.meteosphere.background.WidgetUpdater;
import org.meteosphere.data.model.Place;

/**
 * The weather widgets' pictures, for the home screen and for the preview in their settings: the icon (1x1), the
 * weather now (2x1), the weather now and the forecast (4x1), the weather now with the clock (4x2), and the chart under
 * the clock or not (4x2, 4x3). Each is laid out at its size in pixels before the launcher gets it, so its pictures are
 * made at the sizes they are shown at and the launcher scales none; the preview is the same views made into a View.
 */
public final class WeatherWidgets {

    private WeatherWidgets() {
    }

    /** What a widget shows and how, as its settings and the moment have it */
    public static final class Request {
        final int kind;
        final int width;
        final int height;
        /** Null for none: the widget's texts are empty */
        public Place place;
        /** The colour the widget's settings keep: the light glass when its red is 255, its alpha the glass' opacity */
        public int backgroundColor;
        public boolean showClock;
        public boolean showWind;
        public boolean showPrecipitation;
        /** One of WidgetUpdater.STATE_*: the place, "Updating…" with a spinner, or "Updated" */
        public int state = WidgetUpdater.STATE_NORMAL;
        // The preview's: the moment it shows, the next alarm and the place's label
        Calendar time;
        String alarm;
        String placeName;

        /** A widget of the kind, WidgetKind's, of the size in pixels */
        public Request(int kind, int width, int height) {
            this.kind = kind;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * The views for the launcher, and whether they show the time: such a widget is drawn anew with every refresh of
     * all, as its date and the clock's alarm may have changed
     */
    public static final class Drawn {
        public final RemoteViews views;
        public final boolean withTime;

        Drawn(RemoteViews views, boolean withTime) {
            this.views = views;
            this.withTime = withTime;
        }
    }

    /** The widget on the home screen, null for an unknown kind */
    public static Drawn draw(Context context, int widgetId, Request request) {
        return draw(new WidgetFrame(context, widgetId, request));
    }

    /**
     * The widget as a View for the preview in its settings, opening nothing: at the time given, read as the place's
     * local time, with the alarm and the label of the place given. Its glass, R.id.imvBackground, is as transparent as
     * the background as a View of its own, for the settings to change that alone. Null for an unknown kind
     */
    public static View preview(Context context, Request request, Calendar time, String alarm, String placeName) {
        request.time = time;
        request.alarm = alarm;
        request.placeName = placeName;
        Drawn drawn = draw(new WidgetFrame(context, 0, request));
        if (drawn == null) {
            return null;
        }
        View view = drawn.views.apply(context, null);
        view.findViewById(R.id.imvBackground).setAlpha(Color.alpha(request.backgroundColor) / 255f);
        return view;
    }

    private static Drawn draw(WidgetFrame frame) {
        switch (frame.request.kind) {
            case WidgetKind.ICON_HOURLY:
            case WidgetKind.ICON_DAILY:
                return new Drawn(IconWidget.build(frame, frame.request.kind == WidgetKind.ICON_DAILY), false);
            case WidgetKind.FACT_HOURLY:
            case WidgetKind.FACT_DAILY:
                return new Drawn(NowWidget.build(frame, frame.request.kind == WidgetKind.FACT_DAILY), false);
            case WidgetKind.FACT_AND_FORECAST_HOURLY:
            case WidgetKind.FACT_AND_FORECAST_DAILY:
                return new Drawn(NowAndForecastWidget.build(frame, frame.request.kind == WidgetKind.FACT_AND_FORECAST_DAILY), false);
            case WidgetKind.NOW_AND_CLOCK:
                return new Drawn(NowAndClockWidget.build(frame), true);
            case WidgetKind.CHART_HOURLY:
            case WidgetKind.CHART_HOURLY_WITH_NOW:
            case WidgetKind.CHART_DAILY:
            case WidgetKind.CHART_DAILY_WITH_NOW:
                return ChartWidgetViews.build(frame, !WidgetKind.isHourly(frame.request.kind));
            default:
                return null;
        }
    }
}
