package org.meteosphere.ui.widgets;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.icu.util.ULocale;
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
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.ui.chart.ValueColors;
import org.meteosphere.ui.details.PrecipitationSentence;
import org.meteosphere.ui.icons.TemperatureColors;
import org.meteosphere.ui.icons.WeatherIcons;
import org.meteosphere.util.AppUtils;

/**
 * The 4x2 weather widget with the clock: the temperature now with the day's highest over its lowest, the place and the
 * wind under them, the icon at the top right, the date over the clock at the bottom right and the precipitation's
 * sentence left of them. Laid out at the widget's size with the sizes at full scale; where the widget is too small for
 * them all shrink together, by steps down to a least scale.
 */
final class NowAndClockWidget {

    private static final int MIN_SCALE_PERCENT = 70;
    private static final int SCALE_STEP_PERCENT = 5;
    // The day's arrows as tall as this much of the text's size, and a quarter of it after them
    private static final float RANGE_ARROW = 1.12f;
    private static final float RANGE_ARROW_GAP = 0.25f;
    // The icon's height to its width, as the weather icons' box has them
    private static final float ICON_HEIGHT = 0.75f;

    private NowAndClockWidget() {
    }

    static RemoteViews build(WidgetFrame frame) {
        Content content = new Content(frame);
        for (int percent = 100; ; percent -= SCALE_STEP_PERCENT) {
            float scale = percent / 100f;
            RemoteViews views = views(frame, content, scale);
            View laid = frame.layOut(views);
            if (percent <= MIN_SCALE_PERCENT || fits(frame, laid, scale, content.sentence != null)) {
                placeRefreshTap(frame, views, laid);
                return views;
            }
        }
    }

    // What the widget shows, the same at every scale
    private static final class Content {
        String temperature = "";
        // Null without today's day
        String highest;
        String lowest;
        String wind = "";
        float windDegrees = Float.NaN;
        int windColor;
        String icon = "nodata";
        // Null for none
        String sentence;
        // The clocks' patterns, and what they show now: the launcher shows that until they first tick
        final String datePattern;
        final CharSequence date;
        final String timePattern;
        final CharSequence time;

        Content(WidgetFrame frame) {
            Resources res = frame.res;
            this.windColor = frame.dim;
            Weather now = frame.now();
            if (now != null) {
                this.temperature = frame.temperature(now.temperature);
                this.icon = frame.nowIcon(now);
                this.wind = frame.wind(now.windSpeed, now.windSector, false);
                this.windDegrees = frame.windDegrees(now.windSpeed, now.windSector);
                int strong = ValueColors.wind(now.windSpeed, res.getInteger(R.integer.strong_wind_mps));
                // The strong wind's yellows and reds deepened to read on the light glass
                if (strong != 0) {
                    this.windColor = frame.light ? TemperatureColors.forSurface(strong, false) : strong;
                }
                String sentence = PrecipitationSentence.of(frame.context, frame.place, now, frame.nowLocal);
                this.sentence = sentence == null || sentence.isEmpty() ? null : sentence;
            }
            DayWeather today = frame.today();
            if (today != null) {
                this.highest = frame.temperature(today.temperatureMax);
                this.lowest = frame.temperature(today.temperatureMin);
            }

            Calendar clock = frame.preview ? frame.request.time : Calendar.getInstance();
            Locale locale = res.getConfiguration().getLocales().get(0);
            String datePattern = AppUtils.datePattern(locale, "EEE, d MMMM");
            // The launcher writes the date in the phone's language: in another one the app's date is a fixed text, right
            // till the widget is redrawn, as the preview's moment is
            this.datePattern = frame.preview || !launcherSpeaks(frame.context, locale)
                    ? quoted(new SimpleDateFormat(datePattern, locale).format(clock.getTime())) : datePattern;
            this.date = DateFormat.format(this.datePattern, clock);
            String timePattern = frame.settings.use24Hours ? "H:mm" : "h:mm";
            this.timePattern = frame.preview ? quoted(DateFormat.format(timePattern, clock).toString()) : timePattern;
            this.time = DateFormat.format(this.timePattern, clock);
        }
    }

    // The views at the scale
    private static RemoteViews views(WidgetFrame frame, Content content, float scale) {
        Resources res = frame.res;
        RemoteViews views = frame.views(R.layout.widget_now_and_clock);
        float textSize = res.getDimension(R.dimen.widget_now_clock_text) * scale;

        // The temperature now, and the day's highest over its lowest
        text(views, R.id.tvTemperature, content.temperature, res.getDimension(R.dimen.widget_now_clock_temperature) * scale, frame.text);
        views.setViewVisibility(R.id.llRange, content.highest != null ? View.VISIBLE : View.GONE);
        if (content.highest != null) {
            views.setViewPadding(R.id.llRange, px(res, R.dimen.widget_now_clock_range_gap, scale), 0, 0, 0);
            views.setViewPadding(R.id.llLow, 0, px(res, R.dimen.widget_now_clock_range_lines, scale), 0, 0);
            int arrow = Math.round(RANGE_ARROW * textSize);
            int gap = Math.round(RANGE_ARROW_GAP * textSize);
            views.setImageViewBitmap(R.id.imvHigh, frame.picture(R.drawable.ic_arrow_up, frame.text, arrow));
            views.setImageViewBitmap(R.id.imvLow, frame.picture(R.drawable.ic_arrow_down, frame.dim, arrow));
            views.setViewPadding(R.id.tvHigh, gap, 0, 0, 0);
            views.setViewPadding(R.id.tvLow, gap, 0, 0, 0);
            text(views, R.id.tvHigh, content.highest, textSize, frame.text);
            text(views, R.id.tvLow, content.lowest, textSize, frame.dim);
        }

        // The place's line, and the wind under it
        frame.placeLine(views, textSize, scale);
        views.setViewPadding(R.id.tvPlace, px(res, R.dimen.widget_place_gap, scale), 0, 0, 0);
        views.setViewPadding(R.id.llWind, 0, px(res, R.dimen.widget_now_clock_wind_gap, scale), 0, 0);
        frame.windArrow(views, R.id.imvWind, content.windDegrees, content.windColor, scale);
        views.setViewPadding(R.id.imvWind, 0, 0, px(res, R.dimen.widget_now_clock_wind_arrow_gap, scale), 0);
        text(views, R.id.tvWind, content.wind, textSize, content.windColor);

        // The icon as wide as its size, not a square's
        int iconWidth = px(res, R.dimen.widget_now_clock_icon, scale);
        Drawable icon = WeatherIcons.getInstance(frame.context).getWeatherDrawable(content.icon, frame.light);
        views.setImageViewBitmap(R.id.imvIcon, WidgetFrame.weatherPicture(icon, iconWidth, Math.round(iconWidth * ICON_HEIGHT)));

        // The date over the clock, the precipitation left of them with its last line on the clock's baseline: both at
        // the bottom, the sentence raised by the difference of their parts under the baseline
        float timeSize = res.getDimension(R.dimen.widget_now_clock_time) * scale;
        clock(views, R.id.tcDate, content.datePattern, content.date, textSize, frame.text);
        clock(views, R.id.tcTime, content.timePattern, content.time, timeSize, frame.text);
        frame.clockTap(views, R.id.llClock);
        views.setViewVisibility(R.id.tvSentence, content.sentence != null ? View.VISIBLE : View.GONE);
        if (content.sentence != null) {
            text(views, R.id.tvSentence, content.sentence, textSize, frame.text);
            views.setViewPadding(R.id.tvSentence, 0, 0, px(res, R.dimen.widget_now_clock_clear, scale), descent(timeSize) - descent(textSize));
        }
        return views;
    }

    /**
     * Whether all is shown at the scale: the temperatures each whole and the icon clear of them, the temperatures and
     * the icon clear of the clock, and with a sentence the wind over it and the sentence as wide as the clock at least
     */
    private static boolean fits(WidgetFrame frame, View laid, float scale, boolean sentence) {
        if (!whole(laid, R.id.tvTemperature) || !whole(laid, R.id.tvHigh) || !whole(laid, R.id.tvLow)) {
            return false;
        }
        Rect temperatures = WidgetFrame.bounds(laid, R.id.llTemperatures);
        Rect icon = WidgetFrame.bounds(laid, R.id.imvIcon);
        Rect clock = WidgetFrame.bounds(laid, R.id.llClock);
        if (temperatures.right + px(frame.res, R.dimen.widget_now_clock_clear, scale) > icon.left
                || Rect.intersects(temperatures, clock) || Rect.intersects(icon, clock)) {
            return false;
        }
        if (!sentence) {
            return true;
        }
        TextView text = laid.findViewById(R.id.tvSentence);
        return WidgetFrame.bounds(laid, R.id.llWind).bottom <= WidgetFrame.bounds(laid, R.id.tvSentence).top
                && text.getWidth() - text.getPaddingRight() >= clock.width();
    }

    // The text not cut by its view
    private static boolean whole(View laid, int id) {
        TextView view = laid.findViewById(id);
        String text = view.getText().toString();
        return text.isEmpty() || Math.ceil(view.getPaint().measureText(text)) <= view.getWidth() - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
    }

    // The refresh's tap as wide as the padding and the button from the widget's edge, reaching over and under the
    // place's line; put there by the padding of the frame it is in, where views can't be moved before Android 12
    private static void placeRefreshTap(WidgetFrame frame, RemoteViews views, View laid) {
        Rect line = WidgetFrame.bounds(laid, R.id.llPlace);
        int reach = frame.res.getDimensionPixelSize(R.dimen.widget_now_clock_refresh_reach);
        int top = Math.max(0, line.top - reach);
        views.setViewPadding(R.id.flRefreshHolder, 0, top, 0, 0);
        views.setViewPadding(R.id.flRefresh, 0, 0, 0, line.bottom + reach - top);
    }

    private static void text(RemoteViews views, int id, CharSequence text, float size, int color) {
        views.setTextViewText(id, text);
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, size);
        views.setTextColor(id, color);
    }

    // A clock the launcher keeps going in the pattern, whatever the phone's 12 or 24 hours
    private static void clock(RemoteViews views, int id, String pattern, CharSequence now, float size, int color) {
        views.setCharSequence(id, "setFormat12Hour", pattern);
        views.setCharSequence(id, "setFormat24Hour", pattern);
        text(views, id, now, size, color);
    }

    private static int px(Resources res, int dimen, float scale) {
        return Math.round(res.getDimension(dimen) * scale);
    }

    // The part of a line of the default font under its baseline
    private static int descent(float size) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        return paint.getFontMetricsInt().descent;
    }

    // A fixed text for a date pattern
    private static String quoted(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    /**
     * Whether the launcher, writing dates in the phone's language, writes them in the app's: the same language in the
     * same script. Android 13 writes them in the phone's for an app with a language of its own
     */
    private static boolean launcherSpeaks(Context context, Locale app) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU && hasOwnLanguage(context)) {
            return false;
        }
        ULocale phone = ULocale.addLikelySubtags(ULocale.forLocale(Resources.getSystem().getConfiguration().getLocales().get(0)));
        ULocale own = ULocale.addLikelySubtags(ULocale.forLocale(app));
        return phone.getLanguage().equals(own.getLanguage()) && phone.getScript().equals(own.getScript());
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    private static boolean hasOwnLanguage(Context context) {
        return !context.getSystemService(LocaleManager.class).getApplicationLocales().isEmpty();
    }
}
