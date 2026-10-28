package org.meteosphere.ui.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.background.NotificationUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.icons.WeatherIcons;
import org.meteosphere.ui.main.MainActivity;
import org.meteosphere.ui.sky.SkyWeather;
import org.meteosphere.ui.sky.SkyBackgrounds;
import org.meteosphere.util.AppUtils;
import org.meteosphere.util.Units;
import org.meteosphere.util.WindText;

/**
 * The weather in the status bar: the weather now of the place chosen in the settings, the temperature as the status
 * bar's icon, and the next three hours when expanded, on the system's own card or over the place's sky.
 * {@link NotificationUpdater} posts it
 */
public final class WeatherNotification {

    // The user's settings of the channel are kept by its id. Of the default importance, though with no sound or
    // vibration: Android 17 hides the status bar's icons of silent notifications, the temperature among them. A
    // channel's importance can't be raised once it is made, hence an id other than the silent one's before
    public static final String CHANNEL_ID = "weather_now";
    /** The looks, AppSettings.getStatusBarNotificationLook()'s: the system's own card, or the place's sky */
    public static final int LOOK_SYSTEM = 0;
    public static final int LOOK_SKY = 1;

    private static final String NO_DATA = "nodata";
    // MainActivity opens this place's details: -1 for the current location
    private static final String EXTRA_PLACE_ID = "notification_location_id";
    private static final long MINUTE_MS = 60 * 1000L;
    private static final int HOURS = 3;
    // The first column moves on at half past an hour, as the weather now does: an hour and a half after the one it shows
    private static final long HOURS_FROM_MS = 90 * MINUTE_MS;
    // The local times kept as UTC, written with the AM and PM of the root locale in every language
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final Locale ROOT_SYMBOLS = Locale.forLanguageTag("us");

    // In dp: the views' heights, of which the skies are half, and the compact view's icon
    private static final int NOW_DP = 64;
    private static final int COMPACT_DP = 48;
    private static final int HOURS_DP = 100;
    private static final int COMPACT_ICON_WIDTH_DP = 56;
    private static final int COMPACT_ICON_HEIGHT_DP = 44;
    // The sky is cut lower than its top, where the weather of a thin strip shows
    private static final float SKY_CROP = 0.35f;
    private static final int SKY_SHADE = 0x33000000;
    private static final int SKY_SECONDARY = 0xCCFFFFFF;
    private static final int SKY_SEPARATOR = 0x40FFFFFF;

    // The status bar's icon: a square of 24 dp, the digit 0 0.54 of it high unless the text is then too wide
    private static final int STATUS_ICON_DP = 24;
    private static final float STATUS_ICON_DIGIT = 0.54f;
    private static final float STATUS_ICON_MAX_WIDTH = 0.97f;
    private static final float STATUS_ICON_LETTER_SPACING = -0.06f;
    // The text's proportions are measured at this size, where whole pixels are fine enough
    private static final float MEASURE_TEXT_SIZE = 1000f;

    private static final int[] HOUR_TIMES = {R.id.tvHour1Time, R.id.tvHour2Time, R.id.tvHour3Time};
    private static final int[] HOUR_AM_PMS = {R.id.tvHour1AmPm, R.id.tvHour2AmPm, R.id.tvHour3AmPm};
    private static final int[] HOUR_ICONS = {R.id.imvHour1Icon, R.id.imvHour2Icon, R.id.imvHour3Icon};
    private static final int[] HOUR_TEMPERATURES = {R.id.tvHour1Temperature, R.id.tvHour2Temperature, R.id.tvHour3Temperature};

    private WeatherNotification() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.pref_notification),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    /**
     * The notification of the place, which is null when the app keeps none; locationId is the one chosen in the
     * settings, -1 for the current location, which the tap opens
     */
    public static Notification build(Context context, Place place, int locationId) {
        Drawing drawing = new Drawing(context, place);
        RemoteViews expanded = AppSettings.getInstance().isStatusBarNotificationForecastShown() ? drawing.hours()
                : Build.VERSION.SDK_INT >= 31 ? drawing.now(false) : null;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(drawing.now(true))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(false)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setContentIntent(open(context, locationId));
        if (expanded != null) {
            builder.setCustomBigContentView(expanded);
        }
        if (drawing.now != null) {
            int temperature = Units.temperature(drawing.now.temperature, drawing.settings.temperatureUnit);
            int size = Math.round(STATUS_ICON_DP * context.getResources().getDisplayMetrics().density);
            builder.setSmallIcon(IconCompat.createWithBitmap(statusIcon(statusIconText(temperature), size)));
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_meteo);
        }
        return builder.build();
    }

    // The place's details. Its own data makes it a PendingIntent of its own: the daily forecast's tap has the same
    // extras
    private static PendingIntent open(Context context, int locationId) {
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra(EXTRA_PLACE_ID, locationId);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.setData(Uri.parse(open.toUri(Intent.URI_INTENT_SCHEME)));
        return PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // --- The status bar's icon ---

    /**
     * The status bar icon's text of the temperature in the user's unit: "5°" above zero, "0", "−5" below with the
     * minus sign, but "-15" with a hyphen, as narrow as two digits want it
     */
    static String statusIconText(int temperature) {
        if (temperature > 0) {
            return temperature + "°";
        }
        if (temperature == 0) {
            return "0";
        }
        return (temperature > -10 ? "−" : "-") + -temperature;
    }

    // The text white and bold in a square, centred both ways by its ink, upright by the digit 0's, so that a minus or a
    // degree doesn't move the digits
    private static Bitmap statusIcon(String text, int size) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setLetterSpacing(STATUS_ICON_LETTER_SPACING);
        paint.setTextSize(MEASURE_TEXT_SIZE);
        Rect zero = new Rect();
        paint.getTextBounds("0", 0, 1, zero);
        Rect ink = new Rect();
        paint.getTextBounds(text, 0, text.length(), ink);
        float textSize = MEASURE_TEXT_SIZE * STATUS_ICON_DIGIT * size / zero.height();
        float width = ink.width() * textSize / MEASURE_TEXT_SIZE;
        if (width > STATUS_ICON_MAX_WIDTH * size) {
            textSize *= STATUS_ICON_MAX_WIDTH * size / width;
        }
        paint.setTextSize(textSize);
        paint.getTextBounds("0", 0, 1, zero);
        paint.getTextBounds(text, 0, text.length(), ink);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawText(text, (size - ink.width()) / 2f - ink.left, (size - zero.height()) / 2f - zero.top, paint);
        return bitmap;
    }

    // --- The texts ---

    /**
     * The weather's line under the place's name: the description, a comma and the wind; the wind alone without a
     * description, and when compact, then with a capital, as it starts the line
     */
    static String weatherText(String description, String wind, boolean compact, Locale locale) {
        if (compact) {
            if (wind.isEmpty()) {
                return wind;
            }
            int first = wind.offsetByCodePoints(0, 1);
            return wind.substring(0, first).toUpperCase(locale) + wind.substring(first);
        }
        return description == null || description.isEmpty() ? wind : description + ", " + wind;
    }

    /**
     * The wind as it follows the weather after a comma: the calm's word without wind, else the pattern with the
     * direction, or without one for none, lower-cased but in German, whose nouns keep their capitals. The pattern is
     * lower-cased before it is filled in, so the directions keep theirs
     */
    static String windText(String[] directions, String withDirection, String withoutDirection, int speed, int sector,
                           String speedText, String unitText, Locale locale) {
        if (speed == 0) {
            return directions[0];
        }
        boolean hasDirection = sector > 0 && sector < directions.length;
        String pattern = hasDirection ? withDirection : withoutDirection;
        if (!"de".equals(locale.getLanguage())) {
            pattern = pattern.toLowerCase(locale);
        }
        return String.format(locale, pattern, speedText, unitText, hasDirection ? directions[sector] : "");
    }

    /** The forecast's hours of the three columns: the first ones after local now less 90 minutes, fewer at its end */
    static List<Weather> nextHours(List<Weather> hours, long localNow) {
        List<Weather> next = new ArrayList<>();
        for (Weather hour : hours) {
            if (hour.time > localNow - HOURS_FROM_MS) {
                next.add(hour);
                if (next.size() == HOURS) {
                    break;
                }
            }
        }
        return next;
    }

    /** An hour's time on its column, a local time read as UTC: "15:00", or "3" and "3:30" on the 12-hour clock */
    static String hourTime(long time, boolean is24Hour) {
        String text = format(is24Hour ? AppUtils.TIME_24_HOUR : "h:mm", time);
        return is24Hour ? text : text.replace(":00", "");
    }

    /** "AM" or "PM" after an hour's time on the 12-hour clock, in every language; none on the 24-hour one */
    static String amPm(long time, boolean is24Hour) {
        return is24Hour ? "" : format("a", time);
    }

    private static String format(String pattern, long time) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, DateFormatSymbols.getInstance(ROOT_SYMBOLS));
        format.setTimeZone(GMT);
        return format.format(new Date(time));
    }

    // --- The views ---

    // One drawing's views of the place, in its look
    private static final class Drawing {

        final Context context;
        final AppSettings settings;
        final Place place;
        // Null without a place or without its weather now
        final Weather now;
        final boolean sky;
        // Before Android 12, the system's theme at the time of drawing; from then on the system takes the colours of
        // its theme as it draws, also when the theme changes later
        final boolean night;
        final WeatherIcons icons;
        // The sky's, in the sky look with a place
        final List<String> layers;

        Drawing(Context context, Place place) {
            this.context = context;
            this.settings = AppSettings.getInstance();
            this.place = place;
            this.now = place != null ? place.nowWeather() : null;
            this.sky = this.settings.getStatusBarNotificationLook() == LOOK_SKY;
            this.night = (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            this.icons = WeatherIcons.getInstance(context);
            this.layers = this.sky && place != null ? SkyWeather.of(place) : null;
        }

        // The weather now; collapsed, compact from Android 12 on, where the system gives the collapsed view 48dp
        RemoteViews now(boolean collapsed) {
            RemoteViews views = new RemoteViews(this.context.getPackageName(), R.layout.notification_now);
            boolean compact = collapsed && Build.VERSION.SDK_INT >= 31;
            if (compact) {
                views.setViewLayoutHeight(R.id.llNowRow, COMPACT_DP, TypedValue.COMPLEX_UNIT_DIP);
                views.setViewLayoutWidth(R.id.imvNowIcon, COMPACT_ICON_WIDTH_DP, TypedValue.COMPLEX_UNIT_DIP);
                views.setViewLayoutHeight(R.id.imvNowIcon, COMPACT_ICON_HEIGHT_DP, TypedValue.COMPLEX_UNIT_DIP);
                views.setInt(R.id.tvNowWeather, "setMaxLines", 1);
            }
            sky(views, R.id.imvNowSky, compact ? COMPACT_DP : NOW_DP);
            icon(views, R.id.imvNowIcon, nowIcon());
            String weather = this.now == null ? "" : weatherText(this.now.description(), wind(this.now), compact, Locale.getDefault());
            views.setTextViewText(R.id.tvNowName, this.now != null ? this.place.name : "");
            views.setTextViewText(R.id.tvNowWeather, weather);
            views.setTextViewText(R.id.tvNowTemperature, this.now != null ? temperature(this.now.temperature) : "");
            main(views, R.id.tvNowName);
            secondary(views, R.id.tvNowWeather);
            temperatureColor(views, R.id.tvNowTemperature);
            return views;
        }

        // The weather now and the next three hours
        RemoteViews hours() {
            RemoteViews views = new RemoteViews(this.context.getPackageName(), R.layout.notification_hours);
            sky(views, R.id.imvHoursSky, HOURS_DP);
            icon(views, R.id.imvHoursIcon, nowIcon());
            views.setTextViewText(R.id.tvHoursTemperature, this.now != null ? temperature(this.now.temperature) : "");
            views.setTextViewText(R.id.tvHoursWind, this.now != null ? wind(this.now) : "");
            views.setTextViewText(R.id.tvHoursName, this.place != null ? this.place.name : "");
            temperatureColor(views, R.id.tvHoursTemperature);
            main(views, R.id.tvHoursWind);
            secondary(views, R.id.tvHoursName);
            separator(views, R.id.imvHoursSeparator);
            List<Weather> hours = this.place != null
                    ? nextHours(this.place.hoursCopy(), this.place.localNow().getTimeInMillis()) : new ArrayList<>();
            boolean is24Hour = this.settings.use24Hours;
            for (int i = 0; i < HOURS; i++) {
                Weather hour = i < hours.size() ? hours.get(i) : null;
                views.setTextViewText(HOUR_TIMES[i], hour != null ? hourTime(hour.time, is24Hour) : "");
                views.setTextViewText(HOUR_AM_PMS[i], hour != null ? amPm(hour.time, is24Hour) : "");
                // The hour's own icon, not by day or night
                icon(views, HOUR_ICONS[i], hour != null ? hour.icon : NO_DATA);
                views.setTextViewText(HOUR_TEMPERATURES[i], hour != null ? temperature(hour.temperature) : "");
                main(views, HOUR_TIMES[i]);
                main(views, HOUR_AM_PMS[i]);
                main(views, HOUR_TEMPERATURES[i]);
            }
            return views;
        }

        private String nowIcon() {
            return this.now != null ? this.now.icon(this.place.isDay()) : NO_DATA;
        }

        private String temperature(int celsius) {
            int unit = this.settings.temperatureUnit;
            return Units.temperatureText(Units.temperature(celsius, unit), unit);
        }

        private String wind(Weather weather) {
            Resources res = this.context.getResources();
            int unit = this.settings.windSpeedUnit;
            return windText(res.getStringArray(R.array.wind_directions), res.getString(R.string.wind_phrase),
                    res.getString(R.string.wind_phrase_no_direction), weather.windSpeed, weather.windSector,
                    String.valueOf(Units.windSpeed(weather.windSpeed, unit)),
                    WindText.unitName(this.context, weather.windSpeed, unit), Locale.getDefault());
        }

        // The place's sky at half the screen's width by half the view's height, soft anyway, a quarter of the bytes to
        // the system. Composed the first time off the main thread, its colour showing meanwhile; the notification is
        // drawn again once it is. None without a place
        private void sky(RemoteViews views, int id, int heightDp) {
            if (this.layers == null) {
                return;
            }
            DisplayMetrics metrics = this.context.getResources().getDisplayMetrics();
            Point size = new Point(Math.max(1, metrics.widthPixels / 2), Math.max(1, Math.round(heightDp * metrics.density) / 2));
            SkyBackgrounds backgrounds = SkyBackgrounds.getInstance(this.context);
            Bitmap composed = backgrounds.getComposed(this.layers, size, SKY_CROP);
            if (composed != null) {
                views.setImageViewBitmap(id, composed);
                views.setInt(id, "setColorFilter", SKY_SHADE);
            } else {
                views.setInt(id, "setBackgroundColor", SkyWeather.color(this.layers));
                Context appContext = this.context.getApplicationContext();
                backgrounds.compose(this.layers, size, SKY_CROP, (composedLayers, bitmap) -> NotificationUpdater.update(appContext));
            }
            views.setViewVisibility(id, View.VISIBLE);
        }

        private void main(RemoteViews views, int id) {
            text(views, id, Color.WHITE, R.color.notification_text);
        }

        private void secondary(RemoteViews views, int id) {
            text(views, id, SKY_SECONDARY, R.color.notification_text_secondary);
        }

        private void text(RemoteViews views, int id, int skyColor, int colorRes) {
            if (this.sky) {
                views.setTextColor(id, skyColor);
            } else if (Build.VERSION.SDK_INT >= 31) {
                views.setColorStateList(id, "setTextColor", colorRes);
            } else {
                views.setTextColor(id, themeColor(colorRes, this.night));
            }
        }

        // By the weather now's temperature in °C, 0 without it, light enough on the dark surfaces, dark enough on the light
        private void temperatureColor(RemoteViews views, int id) {
            int celsius = this.now != null ? this.now.temperature : 0;
            if (this.sky) {
                views.setTextColor(id, ForecastFormat.temperatureColor(celsius, true));
            } else if (Build.VERSION.SDK_INT >= 31) {
                views.setColorInt(id, "setTextColor", ForecastFormat.temperatureColor(celsius, false),
                        ForecastFormat.temperatureColor(celsius, true));
            } else {
                views.setTextColor(id, ForecastFormat.temperatureColor(celsius, this.night));
            }
        }

        private void separator(RemoteViews views, int id) {
            if (this.sky) {
                views.setInt(id, "setBackgroundColor", SKY_SEPARATOR);
            } else if (Build.VERSION.SDK_INT >= 31) {
                views.setColorInt(id, "setBackgroundColor", themeColor(R.color.notification_separator, false),
                        themeColor(R.color.notification_separator, true));
            } else {
                views.setInt(id, "setBackgroundColor", themeColor(R.color.notification_separator, this.night));
            }
        }

        // The sky's icons over it; on the system's card the deeper ones of the light surfaces in the light theme
        private void icon(RemoteViews views, int id, String name) {
            if (this.sky) {
                views.setImageViewResource(id, this.icons.getResource(name, false));
            } else if (Build.VERSION.SDK_INT >= 31) {
                views.setIcon(id, "setImageIcon", Icon.createWithResource(this.context, this.icons.getResource(name, true)),
                        Icon.createWithResource(this.context, this.icons.getResource(name, false)));
            } else {
                views.setImageViewResource(id, this.icons.getResource(name, !this.night));
            }
        }

        // The colour as it is in the light or the dark theme
        private int themeColor(int colorRes, boolean night) {
            Configuration configuration = new Configuration(this.context.getResources().getConfiguration());
            configuration.uiMode = configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            return this.context.createConfigurationContext(configuration).getColor(colorRes);
        }
    }
}
