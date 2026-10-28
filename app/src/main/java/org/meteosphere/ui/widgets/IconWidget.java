package org.meteosphere.ui.widgets;

import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;
import org.meteosphere.R;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;

/** The 1x1 weather widget: the icon of the weather now or of today, the temperature now or today's range under it. */
final class IconWidget {

    private IconWidget() {
    }

    static RemoteViews build(WidgetFrame frame, boolean daily) {
        RemoteViews views = frame.views(R.layout.widget_icon);
        frame.placeLine(views);
        String icon = "nodata";
        String temperature = "";
        views.setTextColor(R.id.tvTemperature, frame.text);
        if (daily) {
            // The lowest with its "…" dim, the highest in a text of its own after it, both in the same size in whole
            // pixels, as the layout has the highest's
            String highest = "";
            DayWeather today = frame.today();
            if (today != null) {
                icon = today.icon;
                temperature = frame.temperature(today.temperatureMin) + "…";
                highest = frame.temperature(today.temperatureMax);
            }
            views.setTextViewTextSize(R.id.tvTemperature, TypedValue.COMPLEX_UNIT_PX, frame.res.getDimensionPixelSize(R.dimen.widget_icon_range));
            views.setTextColor(R.id.tvTemperature, frame.dim);
            views.setViewVisibility(R.id.tvTemperatureHigh, View.VISIBLE);
            views.setTextViewText(R.id.tvTemperatureHigh, highest);
            views.setTextColor(R.id.tvTemperatureHigh, frame.text);
        } else {
            Weather now = frame.now();
            if (now != null) {
                icon = frame.nowIcon(now);
                temperature = frame.temperature(now.temperature);
            }
        }
        views.setTextViewText(R.id.tvTemperature, temperature);
        View laid = frame.layOut(views);
        if (daily) {
            laid = fitRange(frame, views, laid);
        }
        frame.squareIcon(views, laid, R.id.imvIcon, icon);
        return views;
    }

    // The range never cut by a narrow widget: both its texts smaller together where their row is too wide for the
    // widget, as large as fits. The views then as laid out
    private static View fitRange(WidgetFrame frame, RemoteViews views, View laid) {
        TextView lowest = laid.findViewById(R.id.tvTemperature);
        TextView highest = laid.findViewById(R.id.tvTemperatureHigh);
        // The row as wide as both, or as the widget lets it be
        float room = ((View) lowest.getParent()).getWidth();
        float usual = frame.res.getDimensionPixelSize(R.dimen.widget_icon_range);
        TextPaint paint = new TextPaint();
        float size = WidgetFrame.fittingSize(usual, new float[]{room},
                in -> new float[]{WidgetFrame.width(lowest, in, paint) + WidgetFrame.width(highest, in, paint)});
        if (size >= usual) {
            return laid;
        }
        views.setTextViewTextSize(R.id.tvTemperature, TypedValue.COMPLEX_UNIT_PX, size);
        views.setTextViewTextSize(R.id.tvTemperatureHigh, TypedValue.COMPLEX_UNIT_PX, size);
        return frame.layOut(views);
    }
}
