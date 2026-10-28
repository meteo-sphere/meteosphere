package org.meteosphere.ui.details;

import android.content.res.Resources;
import androidx.core.graphics.ColorUtils;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import org.meteosphere.R;
import org.meteosphere.astro.Sun;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.chart.ValueColors;
import org.meteosphere.util.WindText;

/** The hours of a city's details: the strip of them, with the sunrises and sunsets between, and the summary over it */
final class HoursBuilder {

    // The hours shown, from now on
    static final int HOURS = 72;

    private final DetailsFormat format;
    private final Place location;
    private final String[] monthsInDate;

    HoursBuilder(DetailsFormat format, Place location) {
        this.format = format;
        this.location = location;
        this.monthsInDate = format.res.getStringArray(R.array.months_in_date);
    }

    // The hours after the current weather's, up to three days
    ArrayList<Weather> upcoming(Weather current) {
        ArrayList<Weather> result = new ArrayList<>();
        long nowLocal = this.format.nowLocal;
        long after = Math.max(nowLocal, current.time);
        for (Weather hour : this.location.hoursCopy()) {
            long time = hour.time;
            if (time > after && time <= nowLocal + HOURS * DetailsFormat.HOUR_MS) {
                result.add(hour);
            }
        }
        return result;
    }

    // The current weather first, its sun or moon by isDay as the big icon over the strip has it
    ForecastStripView.Data strip(Weather current, boolean isDay, ArrayList<Weather> hours) {
        ArrayList<Integer> rows = this.format.rows(false);
        ForecastStripView.Data data = new ForecastStripView.Data();
        for (int id : rows) {
            data.rowLabels.add(this.format.rowLabel(id));
        }
        ArrayList<Long> times = new ArrayList<>();
        ForecastStripView.Column now = column(current, rows);
        now.icon = this.format.stripIcon(current.icon(isDay));
        now.label = this.format.res.getString(R.string.details_now);
        now.strongLabel = true;
        now.time = this.format.nowLocal;
        data.columns.add(now);
        times.add(this.format.nowLocal);
        for (Weather hour : hours) {
            long time = hour.time;
            ForecastStripView.Column column = column(hour, rows);
            column.label = this.format.hour(time);
            column.time = time;
            data.columns.add(column);
            times.add(time);
        }
        insertSunEvents(data, times);
        // The day over each column, its own name over its hours
        for (int i = 0; i < data.columns.size(); i++) {
            data.columns.get(i).day = dayName(times.get(i));
            data.columns.get(i).dayShort = todayOrTomorrow(times.get(i));
        }
        return data;
    }

    // "Сегодня · Ср, 14 октября", "Завтра · Чт, 15 октября", "Пт, 16 октября"
    private String dayName(long time) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(time);
        String date = this.format.res.getString(R.string.format_weekday_day_month, this.format.weekday(time),
                calendar.get(Calendar.DAY_OF_MONTH), this.monthsInDate[calendar.get(Calendar.MONTH)]);
        String word = todayOrTomorrow(time);
        return word != null ? word + " · " + date : date;
    }

    // "Сегодня", "Завтра", null for the other days
    private String todayOrTomorrow(long time) {
        long days = this.format.daysFromToday(time);
        return days == 0 ? this.format.res.getString(R.string.details_today)
                : days == 1 ? this.format.res.getString(R.string.details_tomorrow) : null;
    }

    private ForecastStripView.Column column(Weather hour, ArrayList<Integer> rows) {
        ForecastStripView.Column column = new ForecastStripView.Column();
        column.icon = this.format.stripIcon(hour.icon);
        column.value = this.format.temperature(hour.temperature);
        column.curve = this.format.degrees(hour.temperature);
        column.color = DetailsFormat.temperatureColor(hour.temperature);
        column.cells = new ForecastStripView.Cell[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            ForecastStripView.Cell cell = new ForecastStripView.Cell();
            switch (rows.get(r)) {
                case DetailsModelBuilder.WIND:
                    this.format.windCell(cell, hour.windSpeed, hour.windSector);
                    cell.color = ValueColors.wind(hour.windSpeed, this.format.settings.strongWind);
                    break;
                case DetailsModelBuilder.HUMIDITY:
                    cell.text = String.valueOf(hour.humidity);
                    cell.color = ValueColors.humidity(hour.humidity);
                    break;
                case DetailsModelBuilder.PRESSURE:
                    cell.text = this.format.pressure(hour.pressure);
                    cell.color = ValueColors.pressure(hour.pressure, TilesBuilder.usualPressure(this.location.elevation));
                    break;
                case DetailsModelBuilder.GEOMAGNETIC:
                    cell.text = String.valueOf(hour.kp);
                    cell.color = DetailsFormat.kpColor(hour.kp);
                    break;
                case DetailsModelBuilder.PRECIPITATION:
                    this.format.precipitationCell(cell, hour.precipitation, false);
                    break;
            }
            column.cells[r] = cell;
        }
        return column;
    }

    // Sunrises and sunsets between the hours, as columns of their own
    private void insertSunEvents(ForecastStripView.Data data, ArrayList<Long> times) {
        if (times.size() < 2) {
            return;
        }
        long first = times.get(0);
        long last = times.get(times.size() - 1);
        for (long day = ForecastFormat.floorDay(first); day <= last; day += DetailsFormat.DAY_MS) {
            Sun.Day sun = SunMoonBuilder.sunDay(this.location, day);
            long[] events = {sun.rise, sun.set};
            for (int e = 0; e < 2; e++) {
                long time = events[e];
                if (time == Sun.NONE || time <= first || time >= last) {
                    continue;
                }
                int index = 1;
                while (index < times.size() && times.get(index) < time) {
                    index++;
                }
                ForecastStripView.Column previous = data.columns.get(index - 1);
                ForecastStripView.Column next = data.columns.get(index);
                float share = (time - times.get(index - 1)) / (float) (times.get(index) - times.get(index - 1));
                ForecastStripView.Column column = new ForecastStripView.Column();
                column.event = e == 0 ? ForecastStripView.EVENT_SUNRISE : ForecastStripView.EVENT_SUNSET;
                column.label = this.format.time(time);
                column.time = time;
                column.eventText = this.format.res.getString(e == 0 ? R.string.sunrise : R.string.sunset);
                column.curve = previous.curve + (next.curve - previous.curve) * share;
                column.color = ColorUtils.blendARGB(previous.color, next.color, share);
                data.columns.add(index, column);
                times.add(index, time);
            }
        }
    }

    // The weather now, the day's high or the night's low, and the wind of the next twelve hours
    String summary(Weather current, ArrayList<Weather> hours, DayWeather today) {
        Resources res = this.format.res;
        long nowLocal = this.format.nowLocal;
        List<String> sentences = new ArrayList<>();
        if (current.description() != null && !current.description().isEmpty()) {
            sentences.add(current.description());
        }
        long end = nowLocal + 12 * DetailsFormat.HOUR_MS;
        int localHour = (int) ((nowLocal - ForecastFormat.floorDay(nowLocal)) / DetailsFormat.HOUR_MS);
        if (today != null && localHour < 14) {
            sentences.add(res.getString(R.string.details_summary_day_max, this.format.temperature(today.temperatureMax)));
        } else {
            int min = current.temperature;
            for (Weather hour : hours) {
                if (hour.time <= end) {
                    min = Math.min(min, hour.temperature);
                }
            }
            sentences.add(res.getString(R.string.details_summary_night_min, this.format.temperature(min)));
        }
        int minWind = current.windSpeed;
        int maxWind = current.windSpeed;
        for (Weather hour : hours) {
            if (hour.time <= end) {
                minWind = Math.min(minWind, hour.windSpeed);
                maxWind = Math.max(maxWind, hour.windSpeed);
            }
        }
        sentences.add(WindText.summary(this.format.context, minWind, maxWind, current.windSector,
                this.format.settings.windUnit));
        StringBuilder text = new StringBuilder();
        for (String sentence : sentences) {
            // "Cloudy. ", "多云。"; a description may come with its own full stop
            text.append(res.getString(R.string.sentence, sentence.replaceAll("[.。]$", "")));
        }
        return text.toString().trim();
    }
}
