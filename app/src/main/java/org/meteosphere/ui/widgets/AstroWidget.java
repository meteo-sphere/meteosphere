package org.meteosphere.ui.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.content.ContextCompat;
import java.util.TimeZone;
import org.meteosphere.data.model.Place;
import org.meteosphere.astro.Moon;
import org.meteosphere.astro.Sun;
import org.meteosphere.ui.astro.MoonPhaseDrawable;
import org.meteosphere.ui.sky.MoonPicture;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.astro.AstroActivity;
import org.meteosphere.ui.widgets.settings.WidgetConfig;
import org.meteosphere.ui.astro.AstroFormatter;
import org.meteosphere.util.AppUtils;

/**
 * The Sun or the Moon in one row: the zodiac sign it is in, the next sign and when it comes, today's rise and set at the
 * current location or the one chosen for the widget, and for the Moon its phase now and the next principal phase. All
 * computed on the device; redrawn at the place's midnight, at a change of sign or phase, and every half an hour for the
 * Moon's lit share. The background and the text as the weather widgets have them, set per widget.
 */
public abstract class AstroWidget extends AppWidgetProvider {

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long DAY_MS = 24 * 60 * MINUTE_MS;
    // Android 12 lets an inexact alarm be up to 10 minutes late; a little after the moment so it has surely passed
    private static final long ALARM_WINDOW_MS = 10 * MINUTE_MS;
    private static final long ALARM_DELAY_MS = 30 * 1000L;
    // A screen opened from the widget is alone in the app's task, so back closes the app rather than showing the tabs
    private static final int OPEN_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
    // Made anew each time: an updated one would keep the launch flags it was first made with
    private static final int PENDING_FLAGS = PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    private static final int MOON_PICTURE_DP = 44;
    // Narrower than this the widgets show the rise and set with arrows rather than icons, and the Moon one leaves out the
    // name of the next phase, for the sign to keep its room
    private static final int NARROW_DP = 340;
    // Lower than this there is no room for the place under the rest
    private static final int CITY_MIN_HEIGHT_DP = 105;
    // Behind the moon on the light background, as the Moon tab has it
    private static final int MOON_SKY_COLOR = 0xff2b3038;
    private static final float MOON_SKY_SHARE = 0.91f;
    // The real moon's dark side: faint and see-through on the dark background, as the drawn one's; dim in earthshine
    // on the light one's disk of night sky
    private static final float MOON_DARK_SIDE = 0.6f;
    private static final float MOON_DARK_SIDE_ALPHA = 0.25f;
    private static final float MOON_DARK_SIDE_ON_SKY = 0.12f;
    private static final int[] PHASE_ICONS = {R.drawable.ic_stat_moon_new, R.drawable.ic_stat_moon_first_quarter,
            R.drawable.ic_stat_moon_full, R.drawable.ic_stat_moon_last_quarter};

    protected abstract boolean isMoon();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAll(context, isMoon());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateAll(context, isMoon());
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            WidgetConfig.delete(context, widgetId);
        }
    }

    @Override
    public void onDisabled(Context context) {
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(updateIntent(context, isMoon(), new int[0]));
    }

    private static Class<? extends AstroWidget> providerClass(boolean isMoon) {
        return isMoon ? MoonWidget.class : SunWidget.class;
    }

    /** Whether the widget is a Moon one, false for a Sun one or another kind */
    public static boolean isMoonWidget(Context context, int widgetId) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId);
        return info != null && info.provider.getClassName().equals(MoonWidget.class.getName());
    }

    /** Redraws the Sun or the Moon widgets and sets the alarm for the next change any of them shows */
    public static void updateAll(Context context, boolean isMoon) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        long now = System.currentTimeMillis();
        long nextChange = Long.MAX_VALUE;
        int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, providerClass(isMoon)));
        for (int widgetId : widgetIds) {
            WidgetConfig preferences = new WidgetConfig(context, widgetId);
            Place location = EarthquakeWidget.location(context, preferences);
            // In portrait the widget is as wide as the least width given and as tall as the largest height
            Bundle options = manager.getAppWidgetOptions(widgetId);
            int widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            manager.updateAppWidget(widgetId, build(context, widgetId, isMoon, widthDp, heightDp, preferences.getGlassColor(),
                    preferences.getContentColor(), location, now));
            nextChange = Math.min(nextChange, nextChange(isMoon, timeZone(location), now));
        }
        if (nextChange != Long.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setWindow(AlarmManager.RTC, nextChange + ALARM_DELAY_MS,
                    ALARM_WINDOW_MS, updateIntent(context, isMoon, widgetIds));
        }
    }

    // The provider calls onUpdate only when the ids come with it
    private static PendingIntent updateIntent(Context context, boolean isMoon, int[] widgetIds) {
        Intent intent = new Intent(context, providerClass(isMoon)).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // The place's time zone; a place kept without one, and no place at all, take the phone's
    private static TimeZone timeZone(Place location) {
        return location != null && (location.timeZoneId != null || location.utcOffsetMinutes != 0) ? location.timeZone() : TimeZone.getDefault();
    }

    // UTC instant of the next local midnight, change of sign or, for the Moon, principal phase
    private static long nextChange(boolean isMoon, TimeZone timeZone, long now) {
        long today = Math.floorDiv(now + timeZone.getOffset(now), DAY_MS) * DAY_MS;
        long tomorrow = today + DAY_MS;
        long midnight = tomorrow - Place.offsetMinutesOfDay(timeZone, tomorrow) * MINUTE_MS;
        return isMoon ? Math.min(midnight, Math.min(Moon.nextSignChange(now), Moon.nextPhase(now).time))
                : Math.min(midnight, Sun.nextSignChange(now));
    }

    /** The widget's views at the instant, widthDp by heightDp or 0 if unknown; widgetId 0 for a preview that opens nothing */
    public static RemoteViews build(Context context, int widgetId, boolean isMoon, int widthDp, int heightDp, int backgroundColor,
                                    int fontColor, Place location, long now) {
        if (AppSettings.getInstance() == null) {
            AppSettings.init(context);
        }
        AstroFormatter formatter = new AstroFormatter(context, AppSettings.getInstance().use24Hours);
        boolean isLight = Color.red(backgroundColor) == 255;
        int secondaryColor = AppUtils.withAlpha(fontColor, 0.6f);
        int sunColor = ContextCompat.getColor(context, isLight ? R.color.astro_widget_sun_light : R.color.astro_widget_sun_dark);
        int sunsetColor = ContextCompat.getColor(context, isLight ? R.color.astro_widget_sunset_light : R.color.astro_widget_sunset_dark);
        int moonColor = ContextCompat.getColor(context, isLight ? R.color.astro_widget_moon_light : R.color.astro_widget_moon_dark);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_astro);
        views.setImageViewResource(R.id.imvBackground, isLight ? R.drawable.bg_widget_light : R.drawable.bg_widget_dark);
        views.setInt(R.id.imvBackground, "setAlpha", Color.alpha(backgroundColor));
        for (int id : new int[]{R.id.imvAstroDivider, R.id.imvAstroPhaseDivider}) {
            views.setInt(id, "setBackgroundColor", AppUtils.withAlpha(fontColor, 0.25f));
        }
        for (int id : new int[]{R.id.tvAstroSign, R.id.tvAstroNextSignTime, R.id.tvAstroRise, R.id.tvAstroSet, R.id.tvAstroPhaseDate}) {
            views.setTextColor(id, fontColor);
        }
        for (int id : new int[]{R.id.tvAstroIllumination, R.id.tvAstroNextSign, R.id.tvAstroPhaseName, R.id.tvAstroPhaseTime}) {
            views.setTextColor(id, secondaryColor);
        }

        // Today at the place; without one only the signs are known
        TimeZone timeZone = timeZone(location);
        long today = Math.floorDiv(now + timeZone.getOffset(now), DAY_MS) * DAY_MS;
        int offsetMinutes = Place.offsetMinutesOfDay(timeZone, today);
        double latitude = location != null ? location.latitude : 0;
        double longitude = location != null ? location.longitude : 0;
        long rise = Moon.NONE;
        long set = Moon.NONE;
        int sign;
        long nextSignTime;
        if (isMoon) {
            if (location != null) {
                Moon.Day day = Moon.day(today, offsetMinutes, latitude, longitude);
                rise = day.rise;
                set = day.set;
            }
            sign = Moon.zodiacSign(now);
            nextSignTime = Moon.nextSignChange(now);
        } else {
            if (location != null) {
                Sun.Day day = Sun.day(today, offsetMinutes, latitude, longitude);
                rise = day.rise;
                set = day.set;
            }
            sign = Sun.zodiacSign(now);
            nextSignTime = Sun.nextSignChange(now);
        }
        // Both move on through the signs in order
        int nextSign = (sign + 1) % 12;

        views.setImageViewResource(R.id.imvAstroSign, AstroFormatter.zodiacIcon(sign));
        views.setInt(R.id.imvAstroSign, "setColorFilter", isMoon ? fontColor : sunColor);
        views.setTextViewText(R.id.tvAstroSign, context.getResources().getStringArray(R.array.zodiac_signs)[sign]);
        views.setImageViewResource(R.id.imvAstroNextSign, AstroFormatter.zodiacIcon(nextSign));
        // Opaque and faded as a whole: a translucent tint over the white glyph would come out lighter than the text
        views.setInt(R.id.imvAstroNextSign, "setColorFilter", fontColor);
        views.setInt(R.id.imvAstroNextSign, "setImageAlpha", Color.alpha(secondaryColor));
        views.setTextViewText(R.id.tvAstroNextSign, context.getString(R.string.astro_widget_next_sign,
                context.getResources().getStringArray(R.array.zodiac_signs)[nextSign]));
        views.setTextViewText(R.id.tvAstroNextSignTime, formatter.shortDateTime(nextSignTime + timeZone.getOffset(nextSignTime)));

        // The rise and set as the Sun and Moon tabs have them: the icon on a disk of its color
        views.setImageViewResource(R.id.imvAstroRise, isMoon ? R.drawable.ic_moon_rise : R.drawable.ic_astro_sunrise);
        views.setImageViewResource(R.id.imvAstroSet, isMoon ? R.drawable.ic_moon_set : R.drawable.ic_astro_sunset);
        views.setInt(R.id.imvAstroRise, "setColorFilter", isMoon ? moonColor : sunColor);
        views.setInt(R.id.imvAstroSet, "setColorFilter", isMoon ? moonColor : sunsetColor);
        views.setInt(R.id.imvAstroRise, "setBackgroundResource", isMoon
                ? (isLight ? R.drawable.bg_widget_astro_moon_light : R.drawable.bg_widget_astro_moon_dark)
                : (isLight ? R.drawable.bg_widget_astro_sun_light : R.drawable.bg_widget_astro_sun_dark));
        views.setInt(R.id.imvAstroSet, "setBackgroundResource", isMoon
                ? (isLight ? R.drawable.bg_widget_astro_moon_light : R.drawable.bg_widget_astro_moon_dark)
                : (isLight ? R.drawable.bg_widget_astro_sunset_light : R.drawable.bg_widget_astro_sunset_dark));
        // A launcher may apply the views over the ones it shows, as Samsung's does when the size changes, so both ways of
        // the narrow and the wide widget set all they change
        boolean isNarrow = widthDp > 0 && widthDp < NARROW_DP;
        // Narrow, arrows of the icons' colors rather than the disks, for the sign to keep its room
        views.setViewVisibility(R.id.imvAstroRise, isNarrow ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.imvAstroSet, isNarrow ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.imvAstroRiseArrow, isNarrow ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.imvAstroSetArrow, isNarrow ? View.VISIBLE : View.GONE);
        views.setInt(R.id.imvAstroRiseArrow, "setColorFilter", isMoon ? moonColor : sunColor);
        views.setInt(R.id.imvAstroSetArrow, "setColorFilter", isMoon ? moonColor : sunsetColor);
        views.setTextViewText(R.id.tvAstroRise, formatter.clock(rise));
        views.setTextViewText(R.id.tvAstroSet, formatter.clock(set));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setNarrowSizes(views, isMoon && isNarrow);
            for (int id : new int[]{R.id.tvAstroRise, R.id.tvAstroSet}) {
                views.setViewLayoutMargin(id, RemoteViews.MARGIN_LEFT, isNarrow ? 0 : 8, TypedValue.COMPLEX_UNIT_DIP);
            }
        }

        views.setViewVisibility(R.id.imvAstroSun, isMoon ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.imvAstroMoon, isMoon ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.tvAstroIllumination, isMoon ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.imvAstroPhaseDivider, isMoon ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.llAstroPhase, isMoon ? View.VISIBLE : View.GONE);
        if (isMoon) {
            // The Moon's column of the next phase takes room from the rest
            views.setTextViewTextSize(R.id.tvAstroSign, TypedValue.COMPLEX_UNIT_SP, 19);
            views.setTextViewTextSize(R.id.tvAstroRise, TypedValue.COMPLEX_UNIT_SP, 17);
            views.setTextViewTextSize(R.id.tvAstroSet, TypedValue.COMPLEX_UNIT_SP, 17);
            views.setImageViewBitmap(R.id.imvAstroMoon, moonPicture(context, Moon.age(now), latitude < 0, isLight,
                    location != null ? Moon.disc(now, latitude, longitude) : null));
            views.setTextViewText(R.id.tvAstroIllumination, formatter.illumination(Moon.illumination(now)));
            Moon.Phase phase = Moon.nextPhase(now);
            long phaseTime = phase.time + timeZone.getOffset(phase.time);
            views.setImageViewResource(R.id.imvAstroPhase, PHASE_ICONS[phase.phase / 2]);
            views.setInt(R.id.imvAstroPhase, "setColorFilter", fontColor);
            views.setTextViewText(R.id.tvAstroPhaseName, context.getResources().getStringArray(R.array.moon_widget_phases)[phase.phase / 2]);
            views.setViewVisibility(R.id.tvAstroPhaseName, isNarrow ? View.GONE : View.VISIBLE);
            views.setTextViewText(R.id.tvAstroPhaseDate, formatter.shortDate(phaseTime));
            views.setTextViewText(R.id.tvAstroPhaseTime, formatter.clock(phaseTime));
        } else {
            views.setInt(R.id.imvAstroSun, "setColorFilter", sunColor);
        }

        String city = cityName(context, location);
        boolean showCity = city != null && heightDp >= CITY_MIN_HEIGHT_DP;
        views.setViewVisibility(R.id.tvAstroCity, showCity ? View.VISIBLE : View.GONE);
        if (showCity) {
            views.setTextViewText(R.id.tvAstroCity, city);
            views.setTextColor(R.id.tvAstroCity, secondaryColor);
        }

        if (widgetId != 0) {
            Intent open = AstroActivity.newIntent(context, location, isMoon).addFlags(OPEN_FLAGS);
            views.setOnClickPendingIntent(R.id.flAstroWidget, PendingIntent.getActivity(context, widgetId, open, PENDING_FLAGS));
        }
        return views;
    }

    // The name the place was given, or its own; the current location's own, not "Current location"
    private static String cityName(Context context, Place location) {
        if (location == null) {
            return null;
        }
        String custom = location.customName;
        boolean hasCustom = custom != null && !custom.isEmpty() && !custom.equals(context.getString(R.string.current_location));
        String name = hasCustom ? custom : location.name;
        return name == null || name.isEmpty() ? null : name;
    }

    // "↑ 22:19" with the arrow in the color
    // The narrow Moon's smaller moon and less room between the columns, or the sizes of widget_astro.xml; sizes of views
    // can be set since Android 12
    @android.annotation.TargetApi(Build.VERSION_CODES.S)
    private static void setNarrowSizes(RemoteViews views, boolean isNarrow) {
        views.setViewLayoutWidth(R.id.llAstroPicture, isNarrow ? 40 : 56, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutWidth(R.id.imvAstroMoon, isNarrow ? 34 : MOON_PICTURE_DP, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutHeight(R.id.imvAstroMoon, isNarrow ? 34 : MOON_PICTURE_DP, TypedValue.COMPLEX_UNIT_DIP);
        views.setTextViewTextSize(R.id.tvAstroIllumination, TypedValue.COMPLEX_UNIT_SP, isNarrow ? 12 : 14);
        views.setViewLayoutMargin(R.id.llAstroSign, RemoteViews.MARGIN_LEFT, isNarrow ? 6 : 10, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutMargin(R.id.imvAstroPhaseDivider, RemoteViews.MARGIN_LEFT, isNarrow ? 5 : 8, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutMargin(R.id.imvAstroPhaseDivider, RemoteViews.MARGIN_RIGHT, 4, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutMargin(R.id.imvAstroDivider, RemoteViews.MARGIN_LEFT, isNarrow ? 5 : 8, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewLayoutMargin(R.id.imvAstroDivider, RemoteViews.MARGIN_RIGHT, isNarrow ? 4 : 8, TypedValue.COMPLEX_UNIT_DIP);
    }

    // The moon as the Moon tab draws it today: as it is seen from the place at the widget's update, the real one, its
    // dark side dim, on a disk of night sky on the light background; without a place, the drawn one, dim and white
    private static Bitmap moonPicture(Context context, double age, boolean southernHemisphere, boolean isLight,
            double[] disc) {
        int size = Math.round(MOON_PICTURE_DP * context.getResources().getDisplayMetrics().density);
        MoonPhaseDrawable drawable = new MoonPhaseDrawable(size);
        drawable.setPhase(age, southernHemisphere);
        if (disc != null) {
            drawable.setPicture(MoonPicture.draw(context.getResources(), size, disc,
                    isLight ? MOON_DARK_SIDE_ON_SKY : MOON_DARK_SIDE, isLight ? 1 : MOON_DARK_SIDE_ALPHA));
        }
        if (isLight) {
            drawable.setSkyColor(MOON_SKY_COLOR);
            drawable.setSkyShare(MOON_SKY_SHARE);
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
}
