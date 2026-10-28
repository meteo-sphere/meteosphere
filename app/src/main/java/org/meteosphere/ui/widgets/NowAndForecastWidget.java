package org.meteosphere.ui.widgets;

import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.ui.chart.ForecastFormat;

/**
 * The 4x1 weather widget: the weather now as the 2x1 has it at the left, and three hours or days at the right, each
 * its time or day over its icon and temperature.
 */
final class NowAndForecastWidget {

    static final int COLUMNS = 3;
    // An hour of the forecast is shown till an hour and a half after it
    private static final long SHOWN_AFTER_MS = 90 * 60 * 1000L;
    private static final int[] LABELS = {R.id.tvForecastLabel1, R.id.tvForecastLabel2, R.id.tvForecastLabel3};
    private static final int[] ICONS = {R.id.imvForecastIcon1, R.id.imvForecastIcon2, R.id.imvForecastIcon3};
    private static final int[] TEMPERATURES = {R.id.tvForecastTemperature1, R.id.tvForecastTemperature2, R.id.tvForecastTemperature3};

    private NowAndForecastWidget() {
    }

    static RemoteViews build(WidgetFrame frame, boolean daily) {
        Resources res = frame.res;
        RemoteViews views = frame.views(R.layout.widget_now_and_forecast);
        // The weather now by days too
        String nowIcon = NowWidget.fill(frame, views, false);
        views.setInt(R.id.imvDivider, "setBackgroundColor", frame.divider);
        String[] icons = new String[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            icons[i] = "nodata";
            views.setTextColor(LABELS[i], frame.dim);
            views.setTextColor(TEMPERATURES[i], frame.text);
            views.setTextViewText(LABELS[i], "");
            views.setTextViewText(TEMPERATURES[i], "");
        }
        if (daily) {
            String[] weekdays = res.getStringArray(R.array.weekdays_short);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            List<DayWeather> days = frame.place != null ? days(frame.place.daysCopy(), frame.nowLocal) : new ArrayList<>();
            for (int i = 0; i < COLUMNS; i++) {
                views.setTextViewTextSize(TEMPERATURES[i], TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.widget_forecast_range));
                if (i < days.size()) {
                    DayWeather day = days.get(i);
                    calendar.setTimeInMillis(day.date);
                    views.setTextViewText(LABELS[i], res.getString(R.string.format_weekday_day, weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1],
                            calendar.get(Calendar.DAY_OF_MONTH)));
                    views.setTextViewText(TEMPERATURES[i], frame.range(day, true));
                    icons[i] = day.icon;
                }
            }
        } else {
            SimpleDateFormat format = frame.settings.use24Hours
                    ? new SimpleDateFormat("HH:mm", Locale.getDefault()) : new SimpleDateFormat("h a", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            List<Weather> hours = frame.place != null ? hours(frame.place.hoursCopy(), frame.nowLocal) : new ArrayList<>();
            for (int i = 0; i < hours.size(); i++) {
                Weather hour = hours.get(i);
                views.setTextViewText(LABELS[i], format.format(new Date(hour.time)));
                views.setTextViewText(TEMPERATURES[i], WidgetFrame.medium(frame.temperature(hour.temperature)));
                icons[i] = hour.icon;
            }
        }
        View laid = NowWidget.fitTemperature(frame, views);
        if (daily) {
            // The days' ranges never cut by their narrow columns, nor touching their neighbours
            laid = frame.fitTexts(views, laid, res.getDimension(R.dimen.widget_forecast_range), res.getDimension(R.dimen.widget_forecast_range_gap),
                    TEMPERATURES);
        }
        frame.squareIcon(views, laid, R.id.imvIcon, nowIcon);
        for (int i = 0; i < COLUMNS; i++) {
            frame.squareIcon(views, laid, ICONS[i], icons[i]);
        }
        return views;
    }

    /** The first hours after an hour and a half before now, the times local ones read as UTC */
    static List<Weather> hours(List<Weather> hours, long nowLocal) {
        List<Weather> shown = new ArrayList<>();
        for (Weather hour : hours) {
            if (hour.time > nowLocal - SHOWN_AFTER_MS) {
                shown.add(hour);
                if (shown.size() == COLUMNS) {
                    break;
                }
            }
        }
        return shown;
    }

    /**
     * The first days dated after the start of today: from tomorrow, the days being dated at their midnights; the
     * preview's sample, dated in the morning, has today too
     */
    static List<DayWeather> days(List<DayWeather> days, long nowLocal) {
        long today = ForecastFormat.floorDay(nowLocal);
        List<DayWeather> shown = new ArrayList<>();
        for (DayWeather day : days) {
            if (day.date > today) {
                shown.add(day);
                if (shown.size() == COLUMNS) {
                    break;
                }
            }
        }
        return shown;
    }
}
