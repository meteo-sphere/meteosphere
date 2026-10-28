package org.meteosphere.ui.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.background.WidgetUpdateReceiver;
import org.meteosphere.background.WidgetUpdater;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.chart.WindArrow;
import org.meteosphere.ui.details.DetailsActivity;
import org.meteosphere.ui.icons.WeatherIcons;
import org.meteosphere.ui.main.MainActivity;
import org.meteosphere.ui.widgets.settings.WeatherWidgetSettingsActivity;
import org.meteosphere.ui.widgets.settings.WidgetConfig;
import org.meteosphere.util.AppUtils;
import org.meteosphere.util.Units;
import org.meteosphere.util.WindText;

/**
 * What the weather widgets share: the glass and the colours on it, the place's line at the bottom, the taps, and the
 * pictures made at the size their views are laid out at; with the request and the moment a widget is drawn at.
 */
final class WidgetFrame {

    private static final int PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    private static final String MEDIUM = "sans-serif-medium";
    // Shrunk by its width's share, a text may still be a pixel too wide: it then shrinks by this much at a time
    private static final float SHRINK_STEP = 0.98f;
    private static final int SHRINK_STEPS = 50;
    private static final String NO_BREAK_SPACE = "\u00a0";

    final Context context;
    final Resources res;
    final float density;
    final WeatherWidgets.Request request;
    final AppSettings settings;
    final Place place;
    // 0 for the preview, which opens nothing
    final int widgetId;
    final boolean preview;
    final boolean light;
    final int text;
    final int dim;
    final int divider;
    // The place's time now, its local time read as UTC; the preview's fixed one
    final long nowLocal;

    WidgetFrame(Context context, int widgetId, WeatherWidgets.Request request) {
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context);
        }
        this.context = context;
        this.res = context.getResources();
        this.density = this.res.getDisplayMetrics().density;
        this.request = request;
        this.settings = AppSettings.getInstance();
        this.place = request.place;
        this.widgetId = widgetId;
        this.preview = request.time != null;
        this.light = ChartWidget.isLight(request.backgroundColor);
        this.text = ContextCompat.getColor(context, this.light ? R.color.widget_text_light : R.color.widget_text_dark);
        this.dim = ContextCompat.getColor(context, this.light ? R.color.widget_text_dim_light : R.color.widget_text_dim_dark);
        this.divider = ContextCompat.getColor(context, this.light ? R.color.widget_divider_light : R.color.widget_divider_dark);
        if (this.preview) {
            long time = request.time.getTimeInMillis();
            this.nowLocal = time + request.time.getTimeZone().getOffset(time);
        } else {
            this.nowLocal = this.place != null ? this.place.localNow().getTimeInMillis() : System.currentTimeMillis();
        }
    }

    // --- The weather ---

    /** The weather now, null without a place or one */
    Weather now() {
        return this.place != null ? this.place.nowWeather() : null;
    }

    /** The now's icon, with the sun or the moon as the place has it; nodata without it */
    String nowIcon(Weather now) {
        return now != null ? now.icon(this.place.isDay()) : "nodata";
    }

    /** Today at the place, by its local date; null without a place or the day */
    DayWeather today() {
        if (this.place == null) {
            return null;
        }
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        local.setTimeInMillis(this.nowLocal);
        return this.place.day(local);
    }

    String temperature(int celsius) {
        return ForecastFormat.temperature(celsius, this.settings.temperatureUnit);
    }

    /** The day's lowest and "…" dim, then its highest; the highest in medium where the others are regular */
    CharSequence range(DayWeather day, boolean mediumHighest) {
        String low = temperature(day.temperatureMin) + "…";
        SpannableStringBuilder range = new SpannableStringBuilder(low).append(temperature(day.temperatureMax));
        range.setSpan(new ForegroundColorSpan(this.dim), 0, low.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (mediumHighest) {
            range.setSpan(new TypefaceSpan(MEDIUM), low.length(), range.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return range;
    }

    /** The text in medium, where its view's font is the regular one */
    static CharSequence medium(String text) {
        SpannableStringBuilder medium = new SpannableStringBuilder(text);
        medium.setSpan(new TypefaceSpan(MEDIUM), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return medium;
    }

    /** The wind's line in the user's unit; with the day's highest wind, "up to" it */
    String wind(int speed, int direction, boolean dayHighest) {
        int unit = this.settings.windSpeedUnit;
        return windText(this.res.getStringArray(R.array.wind_directions), this.res.getString(R.string.widget_wind),
                this.res.getString(R.string.widget_wind_up_to), speed, direction, dayHighest,
                String.valueOf(Units.windSpeed(speed, unit)), WindText.unitName(this.context, speed, unit));
    }

    /**
     * The wind's line of the patterns given: the calm's word without wind, the speed and its unit alone without a
     * direction, else with the direction; with the day's highest wind, "up to" it without the direction
     */
    static String windText(String[] directions, String withDirection, String upTo, int speed, int direction, boolean dayHighest,
                           String speedText, String unitText) {
        if (speed == 0) {
            return directions[0];
        }
        if (dayHighest) {
            return String.format(Locale.ROOT, upTo, speedText, unitText);
        }
        if (direction > 0 && direction < directions.length) {
            return String.format(Locale.ROOT, withDirection, speedText, unitText, directions[direction]);
        }
        return speedText + NO_BREAK_SPACE + unitText;
    }

    /** Where the wind blows to, NaN for a calm or no direction */
    float windDegrees(int speed, int direction) {
        return ForecastFormat.windArrow(speed, direction);
    }

    // --- The views ---

    /** The widget's views with its glass, its taps and, before Android 12, its gear */
    RemoteViews views(int layout) {
        RemoteViews views = new RemoteViews(this.context.getPackageName(), layout);
        views.setImageViewResource(R.id.imvBackground, this.light ? R.drawable.bg_widget_light : R.drawable.bg_widget_dark);
        // The preview's glass takes the background's alpha as a View: its settings change that as the slider moves
        if (!this.preview) {
            views.setInt(R.id.imvBackground, "setImageAlpha", Color.alpha(this.request.backgroundColor));
        }
        boolean gear = Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
        views.setViewVisibility(R.id.imvSettings, gear ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.flSettings, gear ? View.VISIBLE : View.GONE);
        if (gear) {
            views.setImageViewResource(R.id.imvSettings, this.light ? R.drawable.ic_widget_gear_on_light : R.drawable.ic_widget_gear_on_dark);
            settingsTap(views, R.id.flSettings);
        }
        openTap(views, R.id.flWidget);
        refreshTap(views, R.id.flRefresh);
        return views;
    }

    /**
     * The small widgets' place line: the name in a line from the font's top to its bottom taken as a whole, the
     * baseline as far up from the line's bottom as the font reaches under it
     */
    void placeLine(RemoteViews views) {
        float textSize = this.res.getDimension(R.dimen.widget_place_text);
        placeLine(views, textSize, 1);
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        Paint.FontMetrics font = paint.getFontMetrics();
        Paint.FontMetricsInt text = paint.getFontMetricsInt();
        int below = (int) Math.ceil(font.bottom);
        int above = (int) Math.ceil(font.bottom - font.top) - below;
        // The text has no padding of the font's: it gets the rest of the line
        views.setViewPadding(R.id.tvPlace, this.res.getDimensionPixelSize(R.dimen.widget_place_gap), above + text.ascent, 0,
                below - text.descent);
    }

    /**
     * The place's line: the refresh button, or the spinner while the widget loads, in its square at the scale, and the
     * name in the size, or what the widget is doing
     */
    void placeLine(RemoteViews views, float textSize, float scale) {
        views.setTextViewTextSize(R.id.tvPlace, TypedValue.COMPLEX_UNIT_PX, textSize);
        views.setTextViewText(R.id.tvPlace, placeLabel());
        views.setTextColor(R.id.tvPlace, this.dim);
        views.setImageViewBitmap(R.id.imvRefresh, picture(this.light ? R.drawable.ic_widget_refresh_on_light : R.drawable.ic_widget_refresh_on_dark, 0,
                Math.round(this.res.getDimension(R.dimen.widget_refresh_icon) * scale)));
        boolean loading = this.request.state == WidgetUpdater.STATE_LOADING;
        // The button keeps the square for the spinner over it
        views.setViewVisibility(R.id.imvRefresh, loading ? View.INVISIBLE : View.VISIBLE);
        views.setViewVisibility(R.id.pbRefresh, loading ? View.VISIBLE : View.GONE);
    }

    /** The place line's text: the place's name or the one given, "Refreshing…" while loading, "Refreshed" just after */
    String placeLabel() {
        if (this.request.state == WidgetUpdater.STATE_LOADING) {
            return this.res.getString(R.string.widget_refreshing);
        }
        if (this.request.state == WidgetUpdater.STATE_REFRESHED) {
            return this.res.getString(R.string.widget_refreshed);
        }
        if (this.request.placeName != null) {
            return this.request.placeName;
        }
        return this.place != null ? this.place.name : "";
    }

    /** The views made into a View of the widget's size and laid out there, for the sizes of their parts */
    View layOut(RemoteViews views) {
        View view = views.apply(this.context, null);
        view.measure(View.MeasureSpec.makeMeasureSpec(this.request.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(this.request.height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, this.request.width, this.request.height);
        return view;
    }

    /** Where the view is in the laid out widget */
    static Rect bounds(View laid, int id) {
        View view = laid.findViewById(id);
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        ((ViewGroup) laid).offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    // --- Texts too wide for their room ---

    /** The widths texts take in a size, in pixels */
    interface TextWidths {
        float[] in(float size);
    }

    /**
     * The widget laid out again with the texts smaller where any of them is too wide for its view as laid out less the
     * gap, as the days' ranges of a narrow widget would be: all of them in the largest size, no larger than the usual
     * one, at which each fits. The views then as laid out
     */
    View fitTexts(RemoteViews views, View laid, float usual, float gap, int... ids) {
        TextView[] texts = new TextView[ids.length];
        float[] rooms = new float[ids.length];
        for (int i = 0; i < ids.length; i++) {
            texts[i] = laid.findViewById(ids[i]);
            rooms[i] = texts[i].getWidth() - texts[i].getTotalPaddingLeft() - texts[i].getTotalPaddingRight() - gap;
        }
        TextPaint paint = new TextPaint();
        float size = fittingSize(usual, rooms, in -> {
            float[] widths = new float[texts.length];
            for (int i = 0; i < texts.length; i++) {
                widths[i] = width(texts[i], in, paint);
            }
            return widths;
        });
        if (size >= usual) {
            return laid;
        }
        for (int id : ids) {
            views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, size);
        }
        return layOut(views);
    }

    /** The view's text's width in the size, in the whole pixels its view takes */
    static float width(TextView view, float size, TextPaint paint) {
        paint.set(view.getPaint());
        paint.setTextSize(size);
        return (float) Math.ceil(Layout.getDesiredWidth(view.getText(), paint));
    }

    /**
     * The texts' size: the usual one where they all fit their rooms, else the largest smaller one at which they all
     * do, the same for all of them
     */
    static float fittingSize(float usual, float[] rooms, TextWidths widths) {
        float size = scaledSize(usual, widths.in(usual), rooms);
        // A text's width doesn't follow its size exactly, as its glyphs are placed on whole pixels: a little smaller
        // till they all fit
        for (int i = 0; i < SHRINK_STEPS && size < usual && !fit(widths.in(size), rooms); i++) {
            size *= SHRINK_STEP;
        }
        return size;
    }

    /**
     * The size in which texts fit, their widths in the usual size given: the usual one where they all fit, else
     * smaller by as much as the one too wide by the most needs, a width taken to follow its size. A room of nothing
     * is left out
     */
    static float scaledSize(float usual, float[] widths, float[] rooms) {
        float scale = 1;
        for (int i = 0; i < widths.length; i++) {
            if (rooms[i] > 0 && widths[i] > rooms[i]) {
                scale = Math.min(scale, rooms[i] / widths[i]);
            }
        }
        return usual * scale;
    }

    // Whether each text fits its room
    private static boolean fit(float[] widths, float[] rooms) {
        for (int i = 0; i < widths.length; i++) {
            if (rooms[i] > 0 && widths[i] > rooms[i]) {
                return false;
            }
        }
        return true;
    }

    // --- The pictures ---

    /**
     * The weather icon at the top of a square, fitted in its view as laid out, in a picture of the view's size: the
     * launcher shows it unscaled
     */
    void squareIcon(RemoteViews views, View laid, int id, String icon) {
        View view = laid.findViewById(id);
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            views.setImageViewBitmap(id, weatherPicture(WeatherIcons.getInstance(this.context).getSquareWeatherDrawable(icon, this.light),
                    view.getWidth(), view.getHeight()));
        }
    }

    /**
     * A weather icon as large as fits the size, in its middle, both to the fraction of a pixel: an icon's place and size
     * aren't rounded to the pixel
     */
    static Bitmap weatherPicture(Drawable icon, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        int iconWidth = icon.getIntrinsicWidth();
        int iconHeight = icon.getIntrinsicHeight();
        float scale = Math.min(width / (float) iconWidth, height / (float) iconHeight);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate((width - iconWidth * scale) / 2, (height - iconHeight * scale) / 2);
        canvas.scale(scale, scale);
        icon.setBounds(0, 0, iconWidth, iconHeight);
        icon.draw(canvas);
        return bitmap;
    }

    /** The drawable as large as fits the size, in its middle, on whole pixels */
    static Bitmap fitted(Drawable drawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        float scale = Math.min(width / (float) drawable.getIntrinsicWidth(), height / (float) drawable.getIntrinsicHeight());
        int w = Math.round(drawable.getIntrinsicWidth() * scale);
        int h = Math.round(drawable.getIntrinsicHeight() * scale);
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        drawable.setBounds(left, top, left + w, top + h);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    /** An icon of the app in a square of the size, tinted with the colour unless it is 0 */
    Bitmap picture(int drawable, int color, int size) {
        // Its own state: a vector shares its tint and picture with the other views' icons
        Drawable icon = ContextCompat.getDrawable(this.context, drawable).mutate();
        if (color != 0) {
            icon.setTint(color);
        }
        return fitted(icon, size, size);
    }

    /** The wind's arrow of the size in the middle of its square, pointing where the wind blows to */
    static Bitmap windArrow(float degrees, int color, int square, float size) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, square), Math.max(1, square), Bitmap.Config.ARGB_8888);
        new WindArrow().draw(new Canvas(bitmap), square / 2f, square / 2f, size, degrees, color);
        return bitmap;
    }

    /** The wind's arrow before its text, none in a calm or without a direction */
    void windArrow(RemoteViews views, int id, float degrees, int color, float scale) {
        views.setViewVisibility(id, Float.isNaN(degrees) ? View.GONE : View.VISIBLE);
        if (!Float.isNaN(degrees)) {
            views.setImageViewBitmap(id, windArrow(degrees, color, Math.round(this.res.getDimension(R.dimen.widget_wind_square) * scale),
                    this.res.getDimension(R.dimen.widget_wind_arrow) * scale));
        }
    }

    // --- The taps ---

    /** The tap that opens the place's page, over the list of the places for the up arrow */
    void openTap(RemoteViews views, int id) {
        if (this.widgetId == 0) {
            return;
        }
        Intent list = distinct(new Intent(this.context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Intent page = distinct(new Intent(this.context, DetailsActivity.class).setAction(DetailsActivity.ACTION_OPEN_WIDGET_PLACE)
                .putExtra(WidgetUpdater.EXTRA_WIDGET, this.widgetId));
        views.setOnClickPendingIntent(id, PendingIntent.getActivities(this.context, this.widgetId, new Intent[]{list, page}, PENDING_FLAGS));
    }

    void refreshTap(RemoteViews views, int id) {
        if (this.widgetId == 0) {
            return;
        }
        Intent refresh = distinct(new Intent(this.context, WidgetUpdateReceiver.class).setAction(WidgetUpdateReceiver.ACTION_REFRESH_WIDGET)
                .putExtra(WidgetUpdater.EXTRA_WIDGET, this.widgetId));
        views.setOnClickPendingIntent(id, PendingIntent.getBroadcast(this.context, this.widgetId, refresh, PENDING_FLAGS));
    }

    void settingsTap(RemoteViews views, int id) {
        if (this.widgetId == 0) {
            return;
        }
        Intent settings = distinct(new Intent(this.context, WeatherWidgetSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, this.widgetId));
        views.setOnClickPendingIntent(id, PendingIntent.getActivity(this.context, this.widgetId, settings, PENDING_FLAGS));
    }

    /** The tap that opens the clock app chosen for the widget, or the phone's own; none without either */
    void clockTap(RemoteViews views, int id) {
        if (this.widgetId == 0) {
            return;
        }
        String clock = new WidgetConfig(this.context, this.widgetId).getClockApp();
        if (clock.isEmpty()) {
            clock = AppUtils.phoneClockPackage(this.context);
        }
        Intent open = clock.isEmpty() ? null : this.context.getPackageManager().getLaunchIntentForPackage(clock);
        if (open != null) {
            views.setOnClickPendingIntent(id, PendingIntent.getActivity(this.context, this.widgetId, open, PENDING_FLAGS));
        }
    }

    // Its data from its own URI: the system keeps one pending intent of the intents that differ by their extras only,
    // and every widget's would open the first one's place
    private static Intent distinct(Intent intent) {
        return intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
    }
}
