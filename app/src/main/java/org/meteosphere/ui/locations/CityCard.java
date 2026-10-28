package org.meteosphere.ui.locations;

import android.content.Context;
import android.content.res.Resources;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.sky.SkyWeather;
import org.meteosphere.util.AppUtils;
import org.meteosphere.util.Units;
import org.meteosphere.util.WindText;

/** What a city's card in the favourites shows, made off the main thread from a copy of its forecast */
final class CityCard {

    String name;
    boolean airport;
    boolean isCurrentLocation;
    String time;
    String condition;
    String temperature;
    String highLow;
    // The wind now, null when the list shows only strong winds and this one isn't
    String wind;
    boolean strongWind;
    List<String> skyLayers;
    int skyColor;

    /** Makes the cards on a background thread; the settings are read once for all of them */
    static final class Builder {
        private final Resources res;
        private final Context context;
        private final SimpleDateFormat timeFormat;
        private final int temperatureUnit;
        private final int windUnit;
        private final boolean onlyStrongWind;
        private final int strongWind;
        private final String[] weekdays;

        Builder(Context context) {
            this.context = context.getApplicationContext();
            this.res = this.context.getResources();
            AppSettings settings = AppSettings.getInstance();
            if (settings.use24Hours) {
                this.timeFormat = new SimpleDateFormat(AppUtils.TIME_24_HOUR, Locale.getDefault());
            } else {
                this.timeFormat = new SimpleDateFormat(AppUtils.TIME_12_HOUR, Locale.getDefault());
                this.timeFormat.setDateFormatSymbols(DateFormatSymbols.getInstance(new Locale("us")));
            }
            // The local times are read as UTC
            this.timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            this.temperatureUnit = settings.temperatureUnit;
            this.windUnit = settings.windSpeedUnit;
            this.onlyStrongWind = settings.onlyStrongWind;
            this.strongWind = this.res.getInteger(R.integer.strong_wind_mps);
            // The weekdays as a sentence writes them: "понедельник", "Montag", "月曜日"
            this.weekdays = DateFormatSymbols.getInstance(this.res.getConfiguration().getLocales().get(0)).getWeekdays();
        }

        CityCard build(Place location, boolean isCurrentLocation) {
            CityCard card = new CityCard();
            card.isCurrentLocation = isCurrentLocation;
            card.name = location.displayName();
            card.airport = location.airport;
            card.skyLayers = SkyWeather.of(location);
            card.skyColor = SkyWeather.color(card.skyLayers);

            Calendar local = location.localNow();
            String time = this.timeFormat.format(local.getTime());
            // Another day there than here: "already Monday" or "still Sunday"
            int localDay = local.get(Calendar.DAY_OF_YEAR);
            int phoneDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            if (localDay != phoneDay) {
                time = this.res.getString(localDay > phoneDay ? R.string.city_time_already : R.string.city_time_still,
                        time, this.weekdays[local.get(Calendar.DAY_OF_WEEK)]);
            }
            card.time = time;

            Weather now = location.nowWeather();
            if (now == null) {
                card.temperature = "−";
                return card;
            }
            card.temperature = temperature(now.temperature);
            card.condition = now.description();
            DayWeather today = location.day(local);
            if (today != null) {
                card.highLow = this.res.getString(R.string.details_high_low, temperature(today.temperatureMax), temperature(today.temperatureMin));
            }
            if (!this.onlyStrongWind || now.windSpeed >= this.strongWind) {
                card.wind = WindText.of(this.context, now.windSpeed, this.windUnit);
                card.strongWind = now.windSpeed >= this.strongWind;
            }
            return card;
        }

        private String temperature(int celsius) {
            return Units.temperatureText(Units.temperature(celsius, this.temperatureUnit), 1) + "°";
        }
    }
}
