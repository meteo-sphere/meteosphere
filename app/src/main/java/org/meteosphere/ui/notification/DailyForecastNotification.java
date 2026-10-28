package org.meteosphere.ui.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.main.MainActivity;
import org.meteosphere.util.WindText;

/**
 * The forecast for today in the morning and for tomorrow in the evening, as briefly as the hourly forecast's heading:
 * "Cloudy. Up to +20° in the afternoon. Wind NW, 1–4 m/s.", then the precipitation and a geomagnetic storm if any.
 */
public final class DailyForecastNotification {

    public static final String CHANNEL_ID = "daily_forecast";
    private static final long HOUR = 3600000L;
    private static final long DAY = 24 * HOUR;
    // The day is told from 6:00, or from now when later
    private static final int DAY_FROM_HOUR = 6;
    // More spells of one precipitation than these are told as one with breaks
    private static final int MAX_SPELLS = 3;
    private static final int STORM_KP = 5;
    private static final int REQUEST_OPEN = 1;

    private DailyForecastNotification() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.pref_daily_forecast),
                    NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    /**
     * The notification for the device's date, also at a place where the date is already another, titled as today's or
     * tomorrow's; null when the place's forecast doesn't have that day. The place chosen in the settings is locationId,
     * -1 for the current location, which the tap opens.
     */
    public static Notification build(Context context, Place place, int locationId, Calendar date, boolean tomorrow) {
        long now = place.localNow().getTimeInMillis();
        // The place's day of that date, its local times read as UTC
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.clear();
        calendar.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
        long dayStart = calendar.getTimeInMillis();
        DayWeather day = place.day(calendar);
        if (day == null) {
            return null;
        }
        String text = summary(context, day, place.hoursCopy(),
                Math.max(dayStart + DAY_FROM_HOUR * HOUR, now / HOUR * HOUR), dayStart + DAY);
        // A favorite by the name it was given, the current location by its locality's
        String name = locationId == -1 ? place.name : place.displayName();
        String title = context.getString(tomorrow ? R.string.daily_forecast_tomorrow : R.string.daily_forecast_today, name);

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("notification_location_id", locationId);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.setData(Uri.parse(open.toUri(Intent.URI_INTENT_SCHEME)));
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_meteo)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                // A group of its own: Android 15 would otherwise fold it and the status bar's weather into one
                .setGroup(CHANNEL_ID)
                // Not the status bar notification's request code 0: for the same place both would be one PendingIntent
                .setContentIntent(PendingIntent.getActivity(context, REQUEST_OPEN, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .build();
    }

    // The sentences of the day between from and to, local times read as UTC
    private static String summary(Context context, DayWeather day, ArrayList<Weather> allHours, long from, long to) {
        Resources res = context.getResources();
        AppSettings settings = AppSettings.getInstance();
        List<Weather> hours = new ArrayList<>();
        for (Weather hour : allHours) {
            if (hour.time >= from && hour.time < to) {
                hours.add(hour);
            }
        }
        List<String> sentences = new ArrayList<>();
        String description = day.description();
        if (description != null && !description.isEmpty()) {
            sentences.add(description);
        }
        sentences.add(res.getString(R.string.details_summary_day_max, ForecastFormat.temperature(day.temperatureMax, settings.temperatureUnit)));
        String wind = wind(context, hours);
        if (wind != null) {
            sentences.add(wind);
        }
        sentences.addAll(precipitation(context, hours, to));
        // The 3-hour Kp of the hours told; the day's largest, the night's too, only without them
        int kp = hours.isEmpty() ? day.kpMax : 0;
        for (Weather hour : hours) {
            kp = Math.max(kp, hour.kp);
        }
        if (kp >= STORM_KP) {
            sentences.add(res.getString(R.string.daily_forecast_storm, kp));
        }
        StringBuilder text = new StringBuilder();
        for (String sentence : sentences) {
            // "Cloudy. ", "多云。"; a description may come with its own full stop
            text.append(res.getString(R.string.sentence, sentence.replaceAll("[.。]$", "")));
        }
        return text.toString().trim();
    }

    // "Wind NW, 1–4 m/s", the direction of the strongest wind; null without hours
    private static String wind(Context context, List<Weather> hours) {
        if (hours.isEmpty()) {
            return null;
        }
        Weather strongest = hours.get(0);
        int minWind = Integer.MAX_VALUE;
        for (Weather hour : hours) {
            minWind = Math.min(minWind, hour.windSpeed);
            if (hour.windSpeed > strongest.windSpeed) {
                strongest = hour;
            }
        }
        return WindText.summary(context, minWind, strongest.windSpeed, strongest.windSector,
                AppSettings.getInstance().windSpeedUnit);
    }

    private static final class Spell {
        int kind;
        long start;
        long end;
    }

    /**
     * "Rain from 06:00 to 09:00 and from 15:00": each kind of precipitation a sentence of its spells, a spell lasting
     * to the end of the day without its end. One dry hour doesn't part spells; too many are told as one with breaks.
     */
    private static List<String> precipitation(Context context, List<Weather> hours, long to) {
        List<Spell> spells = new ArrayList<>();
        for (int i = 0; i < hours.size(); i++) {
            Weather hour = hours.get(i);
            if (!hour.hasPrecipitation()) {
                continue;
            }
            // An amount without the icon's precipitation is rain
            int kind = Math.max(0, Weather.precipitationKind(hour.icon));
            long start = hour.time;
            // The hour lasts to the next forecast, which may be three hours on further ahead; the last one as long
            long step = i > 0 ? start - hours.get(i - 1).time : HOUR;
            long end = i + 1 < hours.size() ? hours.get(i + 1).time : Math.min(to, start + step);
            Spell last = spells.isEmpty() ? null : spells.get(spells.size() - 1);
            if (last != null && last.kind == kind && start - last.end <= HOUR) {
                last.end = end;
            } else {
                Spell spell = new Spell();
                spell.kind = kind;
                spell.start = start;
                spell.end = end;
                spells.add(spell);
            }
        }
        Resources res = context.getResources();
        SimpleDateFormat time = AppSettings.getInstance().use24Hours
                ? new SimpleDateFormat("HH:mm", Locale.getDefault()) : new SimpleDateFormat("h:mm a", Locale.US);
        time.setTimeZone(TimeZone.getTimeZone("GMT"));
        String[] kinds = res.getStringArray(R.array.details_precipitation_kinds);
        List<String> sentences = new ArrayList<>();
        int first = 0;
        while (first < spells.size()) {
            int last = first;
            while (last + 1 < spells.size() && spells.get(last + 1).kind == spells.get(first).kind) {
                last++;
            }
            String kind = kinds[spells.get(first).kind];
            if (last - first + 1 > MAX_SPELLS) {
                sentences.add(res.getString(R.string.daily_forecast_precipitation_breaks, kind,
                        span(res, time, spells.get(first).start, spells.get(last).end, to)));
            } else {
                String text = span(res, time, spells.get(first).start, spells.get(first).end, to);
                for (int i = first + 1; i <= last; i++) {
                    text = res.getString(R.string.daily_forecast_and, text, span(res, time, spells.get(i).start, spells.get(i).end, to));
                }
                sentences.add(res.getString(R.string.daily_forecast_precipitation, kind, text));
            }
            first = last + 1;
        }
        return sentences;
    }

    // "from 06:00 to 09:00", or "from 21:00" when it lasts to the end of the day
    private static String span(Resources res, SimpleDateFormat time, long start, long end, long to) {
        return end >= to ? res.getString(R.string.daily_forecast_from, time.format(new Date(start)))
                : res.getString(R.string.daily_forecast_from_to, time.format(new Date(start)), time.format(new Date(end)));
    }
}
