package org.meteosphere.ui.widgets;

import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.Calendar;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;

/**
 * The 2x1 weather widget: the icon of the weather now beside its temperature over the wind, or by days today's date,
 * range and highest wind. The 4x1 has the same weather now at its left.
 */
final class NowWidget {

    private NowWidget() {
    }

    static RemoteViews build(WidgetFrame frame, boolean daily) {
        RemoteViews views = frame.views(R.layout.widget_now);
        String icon = fill(frame, views, daily);
        // Today's range never cut by a narrow widget
        View laid = daily ? frame.fitTexts(views, frame.layOut(views), frame.res.getDimension(R.dimen.widget_now_range), 0, R.id.tvTemperature)
                : fitTemperature(frame, views);
        frame.squareIcon(views, laid, R.id.imvIcon, icon);
        return views;
    }

    /** Fills widget_now_part with the weather now, or today's; the name of its icon, drawn once the part is laid out */
    static String fill(WidgetFrame frame, RemoteViews views, boolean daily) {
        Resources res = frame.res;
        frame.placeLine(views);
        String icon = "nodata";
        CharSequence temperature = "";
        String wind = "";
        float degrees = Float.NaN;
        if (daily) {
            views.setViewVisibility(R.id.tvDate, View.VISIBLE);
            views.setTextViewText(R.id.tvDate, frame.place != null ? date(res, frame.nowLocal) : "");
            views.setTextColor(R.id.tvDate, frame.dim);
            views.setTextViewTextSize(R.id.tvTemperature, TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.widget_now_range));
            DayWeather today = frame.today();
            if (today != null) {
                icon = today.icon;
                temperature = frame.range(today, false);
                wind = frame.wind(today.windSpeedMax, today.windSector, true);
                degrees = frame.windDegrees(today.windSpeedMax, today.windSector);
            }
        } else {
            Weather now = frame.now();
            if (now != null) {
                icon = frame.nowIcon(now);
                temperature = frame.temperature(now.temperature);
                wind = frame.wind(now.windSpeed, now.windSector, false);
                degrees = frame.windDegrees(now.windSpeed, now.windSector);
            }
        }
        views.setTextViewText(R.id.tvTemperature, temperature);
        views.setTextColor(R.id.tvTemperature, frame.text);
        views.setTextViewText(R.id.tvWind, wind);
        views.setTextColor(R.id.tvWind, frame.dim);
        frame.windArrow(views, R.id.imvWind, degrees, frame.dim, 1);
        return icon;
    }

    /**
     * The widget laid out with the temperature now at its size, or smaller where the room over the wind is too low for
     * its digits, centred in the room, to be whole down to the baseline. The views then as laid out
     */
    static View fitTemperature(WidgetFrame frame, RemoteViews views) {
        View laid = frame.layOut(views);
        TextView temperature = laid.findViewById(R.id.tvTemperature);
        String text = temperature.getText().toString();
        int room = temperature.getHeight();
        if (text.isEmpty() || room <= 0) {
            return laid;
        }
        TextPaint paint = temperature.getPaint();
        Paint.FontMetricsInt line = paint.getFontMetricsInt();
        Rect digits = new Rect();
        paint.getTextBounds(text, 0, text.length(), digits);
        // Centred in a lower room the line loses as much at its top as at its bottom: the digits stay whole while that
        // is no more than the gap over them, which is less than the line's part under the baseline
        int gap = digits.top - line.ascent;
        int needed = line.descent - line.ascent - 2 * gap;
        if (needed <= room) {
            return laid;
        }
        views.setTextViewTextSize(R.id.tvTemperature, TypedValue.COMPLEX_UNIT_PX, paint.getTextSize() * room / needed);
        return frame.layOut(views);
    }

    // "Mo, May 11" of the local time
    private static String date(Resources res, long nowLocal) {
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        local.setTimeInMillis(nowLocal);
        return res.getString(R.string.format_weekday_day_month, res.getStringArray(R.array.weekdays_short)[local.get(Calendar.DAY_OF_WEEK) - 1],
                local.get(Calendar.DAY_OF_MONTH), res.getStringArray(R.array.months_short)[local.get(Calendar.MONTH)]);
    }
}
