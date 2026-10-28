package org.meteosphere.util;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import java.io.IOException;
import java.text.DateFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.meteosphere.R;
import org.meteosphere.settings.AppSettings;

/** The app's small helpers: its broadcasts and services, numbers and dates, the device's place, views and widgets */
public final class AppUtils {

    private static final String TAG = "AppUtils";

    /** Coarse and fine at once: from Android 12 the dialog lets the user give only the approximate one */
    public static final String[] LOCATION_PERMISSIONS = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
    /** The time on the 24-hour clock and on the 12-hour one, the same in every language; the callers pick the locale */
    public static final String TIME_24_HOUR = "H:mm";
    public static final String TIME_12_HOUR = "h:mm a";

    // The languages the app's date patterns are written for; the others put the same fields in their own order
    private static final Set<String> PATTERN_LANGUAGES = Set.of("ru", "uk", "pl", "en");
    // Without the widget's own size: as wide as the screen less the launcher's margins
    private static final int FALLBACK_MARGIN_DP = 16;
    // A widget higher than the screen by its options takes most of the screen's height instead
    private static final float TOO_HIGH_PART = 0.7f;
    // The package of Android's own chooser, which resolves an intent several apps answer with no default among them
    private static final String CHOOSER_PACKAGE = "android";

    private AppUtils() {
    }

    // --- Broadcasts and services ---

    /**
     * Broadcasts the intent to the app alone. The manifest's receivers get no implicit broadcasts since Android 8; one
     * for the package reaches them
     */
    public static void sendAppBroadcast(Context context, Intent intent) {
        context.sendBroadcast(intent.setPackage(context.getPackageName()));
    }

    /** Registers the receiver for the app's own broadcasts and the system's, none from other apps */
    public static void registerAppReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter) {
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** Starts the service; false when Android refuses it, as it does to an app in the background */
    public static boolean startServiceSafely(Context context, Intent intent) {
        try {
            context.startService(intent);
            return true;
        } catch (IllegalStateException e) {
            Log.w(TAG, "Can't start the service in the background", e);
            return false;
        }
    }

    /** Whether one of the app's services of the class runs: Android lists an app its own */
    @SuppressWarnings("deprecation")
    public static boolean serviceRuns(Context context, Class<?> serviceClass) {
        ComponentName service = new ComponentName(context, serviceClass);
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (service.equals(info.service)) {
                return true;
            }
        }
        return false;
    }

    // --- Numbers and texts ---

    /** Rounded to hundredths: the places' and the device's positions are compared so */
    public static double roundToHundredths(double value) {
        return Math.round(100 * value) / 100.0;
    }

    /** A whole percentage as the language writes it: "64 %" in Russian, "64%" in English, "%64" in Turkish */
    public static String percent(Locale locale, long value) {
        return NumberFormat.getPercentInstance(locale).format(value / 100.0);
    }

    /** The same format, its short month names without a final ".", which some languages add: "23 окт", not "23 окт." */
    public static SimpleDateFormat withoutMonthDots(SimpleDateFormat format) {
        DateFormatSymbols symbols = format.getDateFormatSymbols();
        String[] months = symbols.getShortMonths();
        for (int i = 0; i < months.length; i++) {
            if (months[i].endsWith(".")) {
                months[i] = months[i].substring(0, months[i].length() - 1);
            }
        }
        symbols.setShortMonths(months);
        format.setDateFormatSymbols(symbols);
        return format;
    }

    /**
     * The date pattern for the locale: as it is written for Russian, Ukrainian, Polish and English, else the one the
     * language puts the same fields in
     */
    public static String datePattern(Locale locale, String pattern) {
        if (PATTERN_LANGUAGES.contains(locale.getLanguage())) {
            return pattern;
        }
        return DateFormat.getBestDateTimePattern(locale, skeleton(pattern));
    }

    // The pattern's fields without what is between them: "d MMM, HH:mm" is "dMMMHHmm"
    static String skeleton(String pattern) {
        StringBuilder skeleton = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                skeleton.append(c);
            }
        }
        return skeleton.toString();
    }

    /** The colour with the alpha of the factor, its red, green and blue kept */
    public static int withAlpha(int color, float factor) {
        return ((int) (255 * factor) << 24) | (color & 0x00FFFFFF);
    }

    // --- Places and the device ---

    /**
     * The locality at the position in the app's language, by Android's geocoder; null without a position, without a
     * geocoder or an address, or when it fails. It may ask the network, so not on the main thread
     */
    @SuppressWarnings("deprecation")
    public static String localityAt(Context context, Location location) {
        if (location == null || !Geocoder.isPresent()) {
            return null;
        }
        Geocoder geocoder = new Geocoder(context, Locale.forLanguageTag(AppSettings.getInstance().language));
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            return addresses == null || addresses.isEmpty() ? null : addresses.get(0).getLocality();
        } catch (IOException e) {
            Log.w(TAG, "Can't find the locality", e);
            return null;
        }
    }

    /** Whether the app may know the device's position, the approximate one at least */
    public static boolean mayLocate(Context context) {
        for (String permission : LOCATION_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    /** Whether the app may know the device's position and the GPS or the network can find it */
    public static boolean canLocate(Context context) {
        if (!mayLocate(context)) {
            return false;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    // --- Views ---

    /** The group and every view in it, all the way down, enabled or not */
    public static void setAllEnabled(ViewGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                setAllEnabled((ViewGroup) child, enabled);
            } else {
                child.setEnabled(enabled);
            }
        }
    }

    /**
     * The icon that marks airports and stations in the lists, a square as high as a capital of the text: its own, so
     * that tinting it changes no other
     */
    public static Drawable createPlaceKindIcon(Context context, int textSize, int iconRes) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        Rect bounds = new Rect();
        paint.getTextBounds("W", 0, 1, bounds);
        int size = -bounds.top;
        Drawable icon = ContextCompat.getDrawable(context, iconRes).mutate();
        icon.setBounds(0, 0, size, size);
        return icon;
    }

    /**
     * The place search's field of the item's action view, open at once, without the magnifier and the plate's line.
     * No searchable configuration: it brought only the voice search button, whose answer the app never read, while the
     * keyboard's own voice input types into the field
     */
    @SuppressWarnings("deprecation")
    public static void setUpSearchField(Context context, MenuItem item, MenuItemCompat.OnActionExpandListener expandListener,
                                       SearchView.OnQueryTextListener queryListener) {
        MenuItemCompat.setOnActionExpandListener(item, expandListener);
        SearchView searchView = (SearchView) item.getActionView();
        searchView.setOnQueryTextListener(queryListener);
        searchView.setIconifiedByDefault(false);
        searchView.findViewById(androidx.appcompat.R.id.search_mag_icon).setLayoutParams(new LinearLayout.LayoutParams(0, 0));
        searchView.setQueryHint(context.getString(R.string.search_hint));
        searchView.findViewById(androidx.appcompat.R.id.search_plate).setBackground(null);
    }

    // --- Widgets ---

    /**
     * The widget's size in pixels on the home screen in portrait: as wide as the least width the launcher gives and as
     * high as the largest height. Without them, as wide as the screen less the margins and as high as the provider's
     * shape makes it; 0 by 0 without the provider either
     */
    public static Point widgetSize(Context context, int appWidgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = 0;
        int height = 0;
        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        if (options != null) {
            // The fractions cut off: the chart widgets are pictures of this size, and a pixel more would change them
            width = Math.min((int) (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) * metrics.density), metrics.widthPixels);
            height = (int) (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) * metrics.density);
            if (height > metrics.heightPixels) {
                height = (int) (TOO_HIGH_PART * metrics.heightPixels);
            }
        }
        if (width == 0 || height == 0) {
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(appWidgetId);
            if (info != null) {
                width = metrics.widthPixels - (int) (FALLBACK_MARGIN_DP * metrics.density);
                height = heightByShape(width, info.minWidth, info.minHeight);
            }
        }
        return new Point(width, height);
    }

    /**
     * The height of a widget as wide as given in the provider's shape, its least width and height, the fraction cut
     * off; 0 for none
     */
    static int heightByShape(int width, int minWidth, int minHeight) {
        return minWidth > 0 ? (int) (width * (float) minHeight / minWidth) : 0;
    }

    /**
     * The package of the app Android opens for its alarms, the phone's clock; "" for none, or when Android would ask
     * the user to choose one
     */
    public static String phoneClockPackage(Context context) {
        ResolveInfo info = context.getPackageManager().resolveActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null || CHOOSER_PACKAGE.equals(info.activityInfo.packageName)) {
            return "";
        }
        return info.activityInfo.packageName;
    }

    /** The next alarm clock's weekday and time on the phone's clock, "Mon 7:30" or "Mon 7:30 AM"; "" for none */
    public static String nextAlarmText(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo alarm = manager.getNextAlarmClock();
        if (alarm == null) {
            return "";
        }
        String pattern = DateFormat.is24HourFormat(context) ? "ccc H:mm" : "ccc h:mm a";
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return new SimpleDateFormat(pattern, locale).format(new Date(alarm.getTriggerTime()));
    }
}
