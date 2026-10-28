package org.meteosphere.ui.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.Build;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import org.meteosphere.R;
import org.meteosphere.util.AppUtils;

/**
 * The chart widgets, 4x2 and 4x3: ChartWidget's card at the bottom under the clock, or in the middle without it. The
 * clock has the date and the next alarm over the time, or where the card leaves it low the time between them.
 */
final class ChartWidgetViews {

    // The clock goes above a card lower than this share of the widget, and is the low one over a card this high
    private static final float CLOCK_ABOVE = 0.85f;
    private static final float CLOCK_EXTENDED = 0.75f;
    // "99:99" as wide as the widest time
    private static final String WIDEST_TIME = "99:99";
    // The time, and the date and the alarm beside the low one, within this share of their room
    private static final float TIME_ROOM = 0.95f;

    private ChartWidgetViews() {
    }

    static WeatherWidgets.Drawn build(WidgetFrame frame, boolean daily) {
        Context context = frame.context;
        WeatherWidgets.Request request = frame.request;
        ChartWidget.Model model = ChartWidget.build(context, frame.place, frame.preview ? frame.nowLocal : 0, daily,
                WidgetKind.withNow(request.kind), ChartWidget.columns(context, request.width), frame.placeLabel(), frame.light,
                request.showWind, request.showPrecipitation);
        model.settingsIcon = Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
        // The current location, which the app may not find
        if (frame.place == null && request.placeName == null && !AppUtils.mayLocate(context)) {
            model.message = context.getString(R.string.widget_place_needed);
        }
        int cardHeight = ChartWidget.height(context, model);
        boolean clock = clockAbove(request.showClock, cardHeight, request.height);
        // A card taller than the widget drawn wider by as much, for the launcher to shrink to the widget's height
        int cardWidth = cardHeight > request.height ? Math.round(request.width * cardHeight / (float) request.height) : request.width;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_chart);
        if (frame.preview) {
            // The glass apart, as transparent as the settings make it
            views.setViewVisibility(R.id.imvBackground, View.VISIBLE);
            views.setImageViewBitmap(R.id.imvBackground, ChartWidget.background(context, model, cardWidth));
        }
        views.setImageViewBitmap(R.id.imvChart, ChartWidget.render(context, model, cardWidth, request.backgroundColor, !frame.preview));
        frame.openTap(views, R.id.flCard);
        frame.refreshTap(views, R.id.flRefresh);
        views.setViewVisibility(R.id.flSettings, model.settingsIcon ? View.VISIBLE : View.GONE);
        if (model.settingsIcon) {
            frame.settingsTap(views, R.id.flSettings);
        }
        views.setViewVisibility(R.id.flClock, clock ? View.VISIBLE : View.GONE);
        if (clock) {
            clock(frame, views, extendedClock(cardHeight, request.height));
        }
        return new WeatherWidgets.Drawn(views, clock);
    }

    /** Whether the clock goes above the card: when it is on and the card leaves it room */
    static boolean clockAbove(boolean showClock, int cardHeight, int widgetHeight) {
        return showClock && cardHeight < CLOCK_ABOVE * widgetHeight;
    }

    /** Whether the clock above the card is the low one, the time between the date and the alarm */
    static boolean extendedClock(int cardHeight, int widgetHeight) {
        return cardHeight >= CLOCK_EXTENDED * widgetHeight;
    }

    /** Whether a text beside the low clock's time fits the half of the width the time leaves */
    static boolean fitsBeside(float textWidth, float timeWidth, float width) {
        return textWidth <= TIME_ROOM * (width - timeWidth) / 2;
    }

    // The clock in the room above the card, in white over any glass: the time a clock the launcher keeps going, the
    // date and the alarm as they are now, redrawn with the widget
    private static void clock(WidgetFrame frame, RemoteViews views, boolean extended) {
        Resources res = frame.res;
        views.setViewVisibility(R.id.llClockNormal, extended ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.llClockExtended, extended ? View.VISIBLE : View.GONE);
        Calendar now = frame.preview ? frame.request.time : Calendar.getInstance();
        Locale locale = res.getConfiguration().getLocales().get(0);
        String date = new SimpleDateFormat(res.getString(extended ? R.string.widget_clock_date_two_lines : R.string.widget_clock_date), locale)
                .format(now.getTime());
        String alarm = frame.preview ? frame.request.alarm : AppUtils.nextAlarmText(frame.context);
        boolean hasAlarm = alarm != null && !alarm.isEmpty();
        int time = extended ? R.id.tcClockTime2 : R.id.tcClockTime;
        String pattern = frame.settings.use24Hours ? "H:mm" : "h:mm";
        // The preview's time fixed
        if (frame.preview) {
            pattern = "'" + DateFormat.format(pattern, now) + "'";
        }
        views.setCharSequence(time, "setFormat12Hour", pattern);
        views.setCharSequence(time, "setFormat24Hour", pattern);
        // Shown until the clock first ticks, and measured
        CharSequence timeText = DateFormat.format(pattern, now);
        views.setTextViewText(time, timeText);
        if (extended) {
            views.setTextViewText(R.id.tvClockDate2, date);
            views.setTextViewText(R.id.tvClockAlarm2, hasAlarm ? alarm : "");
        } else {
            views.setTextViewText(R.id.tvClockDate, date);
            views.setTextViewText(R.id.tvClockAlarm, hasAlarm ? alarm : "");
            views.setViewVisibility(R.id.imvClockAlarm, hasAlarm ? View.VISIBLE : View.GONE);
            views.setViewVisibility(R.id.tvClockAlarm, hasAlarm ? View.VISIBLE : View.GONE);
        }
        frame.clockTap(views, R.id.flClock);

        // The time as large as the room lets it be: the time's own width under the date, the whole width between the
        // date and the alarm; its height the time's own
        View laid = frame.layOut(views);
        TextView timeView = laid.findViewById(time);
        TextPaint paint = new TextPaint(timeView.getPaint());
        float size = timeSize(paint, res.getDimensionPixelSize(R.dimen.widget_clock_min_time), extended ? frame.request.width : timeView.getWidth(),
                timeView.getHeight(), frame.density);
        paint.setTextSize(size);
        views.setTextViewTextSize(time, TypedValue.COMPLEX_UNIT_PX, size);
        if (!extended) {
            // Under the date, pulled up by the room from the font's top to its ascent, which digits never reach, in whole
            // pixels
            Paint.FontMetrics line = paint.getFontMetrics();
            views.setViewPadding(time, 0, (int) (line.top - line.ascent), 0, 0);
        } else {
            // Between the date and the alarm, in the middle of the room's height
            float timeWidth = paint.measureText(timeText.toString());
            TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            small.setTextSize(res.getDimension(R.dimen.widget_clock_text));
            String[] lines = date.split("\n");
            boolean dateFits = fitsBeside(small.measureText(lines[lines.length - 1]), timeWidth, frame.request.width);
            float alarmWidth = res.getDimension(R.dimen.widget_clock_alarm_icon) + res.getDimension(R.dimen.widget_clock_alarm_text_gap)
                    + small.measureText(hasAlarm ? alarm : "");
            boolean alarmFits = hasAlarm && fitsBeside(alarmWidth, timeWidth, frame.request.width);
            // One that doesn't fit is hidden, keeping its room for the time to stay in the middle
            views.setViewVisibility(R.id.tvClockDate2, dateFits ? View.VISIBLE : View.INVISIBLE);
            views.setViewVisibility(R.id.llClockAlarm2, alarmFits ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * The time's size in pixels: from the least, in whole pixels, up by a dp while the widest time in it is narrower
     * than the share of the room's width and its line, from the font's top to its bottom, lower than the room; then a
     * dp less
     */
    static float timeSize(TextPaint paint, int least, float width, int height, float density) {
        TextPaint measure = new TextPaint(paint);
        float size = least;
        while (true) {
            measure.setTextSize(size);
            Paint.FontMetrics line = measure.getFontMetrics();
            if (measure.measureText(WIDEST_TIME) >= TIME_ROOM * width || line.bottom - line.top >= height) {
                break;
            }
            size += density;
        }
        return size - density;
    }
}
