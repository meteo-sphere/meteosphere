package org.meteosphere.ui.details;

import java.util.ArrayList;
import org.meteosphere.astro.Moon;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.chart.PrecipitationDrop;
import org.meteosphere.ui.chart.ValueColors;
import org.meteosphere.ui.icons.WeatherIcons;

/** The days of a city's details: briefly as a list of their ranges, or in detail as a strip with the parameters */
final class DaysBuilder {

    private final DetailsFormat format;
    private final Place location;

    DaysBuilder(DetailsFormat format, Place location) {
        this.format = format;
        this.location = location;
    }

    // Today and the days after it
    ArrayList<DayWeather> upcoming() {
        ArrayList<DayWeather> result = new ArrayList<>();
        long today = ForecastFormat.floorDay(this.format.nowLocal);
        for (DayWeather day : this.location.daysCopy()) {
            if (day.date >= today) {
                result.add(day);
            }
        }
        return result;
    }

    ArrayList<DailyRangeView.Day> list(ArrayList<DayWeather> days, Weather current) {
        ArrayList<DailyRangeView.Day> result = new ArrayList<>();
        long today = ForecastFormat.floorDay(this.format.nowLocal);
        for (DayWeather forecast : days) {
            DailyRangeView.Day day = new DailyRangeView.Day();
            long date = forecast.date;
            day.name = this.format.dayLabel(date);
            // Each day's its own: the rows draw it 22 or 26 dp high, with the precipitation under it or without
            day.icon = WeatherIcons.getInstance(this.format.context).getWeatherDrawable(forecast.icon, false);
            day.precipitation = forecast.precipitation >= 0.05 ? this.format.precipitation(forecast.precipitation) : null;
            day.precipitationFill = PrecipitationDrop.dayFill(forecast.precipitation);
            day.min = this.format.temperature(forecast.temperatureMin);
            day.max = this.format.temperature(forecast.temperatureMax);
            day.minValue = this.format.degrees(forecast.temperatureMin);
            day.maxValue = this.format.degrees(forecast.temperatureMax);
            day.minColor = DetailsFormat.temperatureColor(forecast.temperatureMin);
            day.maxColor = DetailsFormat.temperatureColor(forecast.temperatureMax);
            if (ForecastFormat.floorDay(date) == today) {
                day.nowValue = this.format.degrees(current.temperature);
            }
            result.add(day);
        }
        return result;
    }

    ForecastStripView.Data strip(ArrayList<DayWeather> days) {
        ArrayList<Integer> rows = this.format.rows(true);
        ForecastStripView.Data data = new ForecastStripView.Data();
        data.hasSecondValue = true;
        data.southernHemisphere = this.location.latitude < 0;
        for (int id : rows) {
            data.rowLabels.add(this.format.rowLabel(id));
        }
        for (DayWeather day : days) {
            long date = day.date;
            ForecastStripView.Column column = new ForecastStripView.Column();
            column.label = this.format.dayLabel(date);
            column.time = date;
            column.strongLabel = ForecastFormat.floorDay(date) == ForecastFormat.floorDay(this.format.nowLocal);
            column.icon = this.format.stripIcon(day.icon);
            column.value = this.format.temperature(day.temperatureMax);
            column.value2 = this.format.temperature(day.temperatureMin);
            column.curve = this.format.degrees(day.temperatureMax);
            column.curve2 = this.format.degrees(day.temperatureMin);
            column.color = DetailsFormat.temperatureColor(day.temperatureMax);
            column.color2 = DetailsFormat.temperatureColor(day.temperatureMin);
            column.cells = new ForecastStripView.Cell[rows.size()];
            for (int r = 0; r < rows.size(); r++) {
                ForecastStripView.Cell cell = new ForecastStripView.Cell();
                switch (rows.get(r)) {
                    case DetailsModelBuilder.WIND:
                        this.format.windCell(cell, day.windSpeedMax, day.windSector);
                        cell.color = ValueColors.wind(day.windSpeedMax, this.format.settings.strongWind);
                        break;
                    case DetailsModelBuilder.HUMIDITY:
                        cell.text = String.valueOf(day.humidityMax);
                        cell.color = ValueColors.humidity(day.humidityMax);
                        break;
                    case DetailsModelBuilder.PRESSURE:
                        cell.text = this.format.pressure(day.pressureMax);
                        cell.text2 = this.format.pressure(day.pressureMin);
                        double usual = TilesBuilder.usualPressure(this.location.elevation);
                        cell.color = ValueColors.pressure(day.pressureMax, usual);
                        cell.color2 = ValueColors.pressureLow(day.pressureMin, usual);
                        break;
                    case DetailsModelBuilder.GEOMAGNETIC:
                        cell.text = String.valueOf(day.kpMax);
                        cell.color = DetailsFormat.kpColor(day.kpMax);
                        break;
                    case DetailsModelBuilder.PRECIPITATION:
                        this.format.precipitationCell(cell, day.precipitation, true);
                        break;
                    case DetailsModelBuilder.MOON:
                        Moon.Day moon = Moon.day(date, this.location.offsetMinutesOfDay(date), this.location.latitude, this.location.longitude);
                        cell.moonAge = moon.age;
                        cell.moonRing = moon.phaseTime != Moon.NONE;
                        cell.text = this.format.astro.illumination(moon.illumination);
                        break;
                }
                column.cells[r] = cell;
            }
            data.columns.add(column);
        }
        return data;
    }
}
