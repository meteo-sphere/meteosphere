package org.meteosphere.data.local;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;

// Builds the app model from Foreca data the same way ForecaHelper of the wpr server does, with the values rounded as
// the server sends them, see OpenMeteoMapper.
final class ForecaMapper {

    private static final int HOURLY_FORECAST_COUNT = 24 * 3;
    private static final float HPA_PER_MMHG = 1.3332f;

    private ForecaMapper() {
    }

    // 2026-10-01T09:38+03:00 -> 2026-10-01T09:38
    private static String localTime(String time) {
        return time.substring(0, 16);
    }

    // 2026-10-01T09:38+03:00 -> +03:00
    private static String offset(String time) {
        return time.substring(16);
    }

    private static int offsetMinutes(String time) {
        String offset = offset(time);
        if (offset.isEmpty() || offset.equals("Z")) {
            return 0;
        }
        int minutes = Integer.parseInt(offset.substring(1, 3)) * 60 + Integer.parseInt(offset.substring(4, 6));
        return offset.charAt(0) == '-' ? -minutes : minutes;
    }

    private static TimeZone offsetTimeZone(String time) {
        String offset = offset(time);
        return TimeZone.getTimeZone(offset.isEmpty() || offset.equals("Z") ? "GMT" : "GMT" + offset);
    }

    private static Date instant(String time) throws ParseException {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mmXXX", Locale.US).parse(time);
    }

    // Foreca gives the pressure at the sea level, the app shows it at the place, like Open-Meteo surface_pressure
    static Integer stationPressureMmHg(Float seaLevelPressure, double elevation) {
        if (seaLevelPressure == null) {
            return null;
        }
        float pressure = (float) (seaLevelPressure * StrictMath.pow(1 - 2.25577e-5 * elevation, 5.25588));
        return Math.round(pressure / HPA_PER_MMHG);
    }

    // Symbols like d210: day or night, then the cloudiness (0 clear, 1 almost clear, 2 half cloudy, 3 broken,
    // 4 overcast, 5 thin high clouds, 6 fog), the precipitation (0 none, 1 slight, 2 showers, 3 precipitation,
    // 4 thunder) and its type (0 rain, 1 sleet, 2 snow): https://developer.foreca.com/resources
    private static boolean isValidSymbol(String symbol) {
        return symbol != null && symbol.length() == 4;
    }

    private static int cloudiness(String symbol) {
        return symbol.charAt(1) - '0';
    }

    private static int precipitation(String symbol) {
        return symbol.charAt(2) - '0';
    }

    private static int precipitationType(String symbol) {
        return symbol.charAt(3) - '0';
    }

    // The names of the weather icons (res/drawable/ic_weather_*, drawn by tools/weather_icons.py), like d.c3.r2.st
    static String getIcon(String symbol) {
        if (!isValidSymbol(symbol)) {
            return "nodata";
        }
        int cloudiness = cloudiness(symbol);
        if (cloudiness == 6) {
            return "mist";
        }
        int precipitation = precipitation(symbol);
        String dayNight = symbol.charAt(0) == 'n' ? "n" : "d";
        String icon;
        if (cloudiness == 4) {
            icon = "c4";
        } else if (cloudiness == 2 || cloudiness == 3) {
            icon = dayNight + ".c3";
        } else if (cloudiness == 1 || cloudiness == 5 || precipitation > 0) {
            // There are no icons of precipitation from a clear sky
            icon = dayNight + ".c2";
        } else {
            icon = dayNight;
        }
        if (precipitation > 0) {
            int type = precipitationType(symbol);
            // Thunderstorms come with rain showers
            int intensity = precipitation == 4 ? 2 : precipitation;
            icon += "." + (type == 2 ? "s" : type == 1 ? "rs" : "r") + intensity;
        }
        if (precipitation == 4) {
            icon += ".st";
        }
        return icon;
    }

    // The WMO code Open-Meteo would give, so the weather of both sources is described the same way, -1 for none:
    // slight precipitation, showers and precipitation are the slight, the slight showers and the moderate codes
    static int getWmo(String symbol) {
        if (!isValidSymbol(symbol)) {
            return -1;
        }
        int type = precipitationType(symbol);
        switch (precipitation(symbol)) {
            case 1:
                return type == 2 ? 71 : type == 1 ? 68 : 61;
            case 2:
                return type == 2 ? 85 : type == 1 ? 83 : 80;
            case 3:
                return type == 2 ? 73 : type == 1 ? 69 : 63;
            case 4:
                return 95;
            default:
                break;
        }
        switch (cloudiness(symbol)) {
            case 0:
                return 0;
            case 1:
            case 5:
                return 1;
            case 2:
            case 3:
                return 2;
            case 6:
                return 45;
            default:
                return 3;
        }
    }

    static double elevation(GeoLocation geoLocation) {
        return geoLocation.elevation != null ? geoLocation.elevation : 0;
    }

    private static void setValues(Weather forecast, ForecaForecast.Hour hour, double elevation, int kp) {
        float temperature = hour.temperature;
        float precip = hour.precip != null ? hour.precip : 0f;
        forecast.temperature = Math.round(temperature);
        Integer pressure = stationPressureMmHg(hour.pressure, elevation);
        if (pressure != null) {
            forecast.pressure = pressure;
        }
        forecast.windSpeed = Math.round(hour.windSpeed);
        forecast.windSector = forecast.windSpeed == 0 || hour.windDir == null ? 0 : OpenMeteoMapper.getWindDirection(hour.windDir);
        if (hour.relHumidity != null) {
            forecast.humidity = hour.relHumidity;
        }
        forecast.feelsLike = hour.feelsLikeTemp != null ? Math.round(hour.feelsLikeTemp) : forecast.temperature;
        forecast.precipitation = OpenMeteoMapper.roundToTenths(precip);
        forecast.icon = getIcon(hour.symbol);
        forecast.code = getWmo(hour.symbol);
        forecast.kp = kp;
    }

    private static ForecaForecast.Day findDay(List<ForecaForecast.Day> days, String date) {
        for (ForecaForecast.Day day : days) {
            if (date.equals(day.date)) {
                return day;
            }
        }
        return null;
    }

    // Sets the current weather and the time zone of the location. Without the daily forecast,
    // as for the lists of locations, the sunrise and sunset are calculated.
    static void applyFact(Place place, GeoLocation geoLocation, ForecaForecast.Hour current,
                          List<ForecaForecast.Day> days, GeomagneticSource geomagnetic) throws ParseException {
        place.utcOffsetMinutes = offsetMinutes(current.time);

        String localTime = localTime(current.time);
        String date = localTime.substring(0, 10);
        Weather fact = new Weather();
        fact.time = OpenMeteoMapper.parseOrDefault(OpenMeteoMapper.createGmtFormat("yyyy-MM-dd'T'HH:mm:ss"), localTime + ":00", 0);
        ForecaForecast.Day day = findDay(days, date);
        SunTimes sunTimes = day != null
                ? SunTimes.fromLocalTimes(date, day.sunrise, day.sunset)
                : SunTimes.calculate(date, geoLocation.latitude, geoLocation.longitude, place.utcOffsetMinutes);
        if (sunTimes != null) {
            fact.sunrise = sunrise(sunTimes);
            fact.sunset = sunset(sunTimes);
            fact.dayLengthMinutes = sunTimes.dayDuration;
        }
        setValues(fact, current, elevation(geoLocation),
                OpenMeteoMapper.gradeOrDefault(geomagnetic.getGrade(instant(current.time))));
        place.now = fact;
    }

    // In milliseconds, 0 in polar day and night: the sun doesn't rise or set then
    private static long sunrise(SunTimes sunTimes) {
        return isPolar(sunTimes) ? 0 : sunTimes.sunrise * 1000;
    }

    private static long sunset(SunTimes sunTimes) {
        return isPolar(sunTimes) ? 0 : sunTimes.sunset * 1000;
    }

    // As SunTimes.calculate() gives them
    private static boolean isPolar(SunTimes sunTimes) {
        return sunTimes.dayDuration == 0 || sunTimes.dayDuration >= 24 * 60;
    }

    // Sets the current weather, the daily forecast and the hourly one for 3 days
    static void applyLocation(Place place, GeoLocation geoLocation, ForecaForecast.Hour current,
                              List<ForecaForecast.Hour> hours, List<ForecaForecast.Day> days,
                              GeomagneticSource geomagnetic) throws ParseException {
        applyFact(place, geoLocation, current, days, geomagnetic);

        double elevation = elevation(geoLocation);
        TimeZone tz = geoLocation.timezone != null ? TimeZone.getTimeZone(geoLocation.timezone) : offsetTimeZone(current.time);
        DateFormat validFormat = OpenMeteoMapper.createGmtFormat("yyyy-MM-dd'T'HH:mm:ss");
        DateFormat dayFormat = OpenMeteoMapper.createGmtFormat("yyyy-MM-dd");
        List<String> dates = new ArrayList<>();
        for (ForecaForecast.Day forecastDay : days) {
            DayWeather day = new DayWeather();
            dates.add(forecastDay.date);

            day.date = OpenMeteoMapper.parseOrDefault(dayFormat, forecastDay.date, 0);
            SunTimes sunTimes = SunTimes.fromLocalTimes(forecastDay.date, forecastDay.sunrise, forecastDay.sunset);
            if (sunTimes != null) {
                day.sunrise = sunrise(sunTimes);
                day.sunset = sunset(sunTimes);
                day.dayLengthMinutes = sunTimes.dayDuration;
            }
            day.temperatureMin = Math.round(forecastDay.minTemp);
            day.temperatureMax = Math.round(forecastDay.maxTemp);

            // The daily forecast has one pressure: the lowest and the highest come from the day's hours
            Float pressureMin = null;
            Float pressureMax = null;
            for (ForecaForecast.Hour hour : hours) {
                if (!hour.time.startsWith(forecastDay.date)) {
                    continue;
                }
                if (hour.pressure != null) {
                    pressureMin = pressureMin == null ? hour.pressure : Math.min(pressureMin, hour.pressure);
                    pressureMax = pressureMax == null ? hour.pressure : Math.max(pressureMax, hour.pressure);
                }
            }
            Integer pressure = stationPressureMmHg(forecastDay.pressure, elevation);
            Integer dayPressureMin = pressureMin != null ? stationPressureMmHg(pressureMin, elevation) : pressure;
            Integer dayPressureMax = pressureMax != null ? stationPressureMmHg(pressureMax, elevation) : pressure;
            if (dayPressureMin != null) {
                day.pressureMin = dayPressureMin;
            }
            if (dayPressureMax != null) {
                day.pressureMax = dayPressureMax;
            }
            day.windSpeedMax = Math.round(forecastDay.maxWindSpeed);
            day.windSector = day.windSpeedMax == 0 || forecastDay.windDir == null ? 0 : OpenMeteoMapper.getWindDirection(forecastDay.windDir);
            if (forecastDay.maxRelHumidity != null) {
                day.humidityMax = forecastDay.maxRelHumidity;
            }

            float precip = forecastDay.precipAccum != null ? forecastDay.precipAccum : 0f;
            day.precipitation = OpenMeteoMapper.roundToTenths(precip);
            day.icon = getIcon(forecastDay.symbol);
            day.code = getWmo(forecastDay.symbol);
            day.kpMax = OpenMeteoMapper.gradeOrDefault(geomagnetic.getDayMax(forecastDay.date, tz));
            place.addDay(day);
        }

        for (int i = 0; i < Math.min(HOURLY_FORECAST_COUNT, hours.size()); i++) {
            ForecaForecast.Hour hour = hours.get(i);
            String localTime = localTime(hour.time);
            if (!dates.contains(localTime.substring(0, 10))) {
                continue;
            }
            Weather forecast = new Weather();
            forecast.time = OpenMeteoMapper.parseOrDefault(validFormat, localTime + ":00", 0);
            setValues(forecast, hour, elevation, OpenMeteoMapper.gradeOrDefault(geomagnetic.getGrade(instant(hour.time))));
            place.addHour(forecast);
        }
        place.sortAndDedupe();
    }
}
