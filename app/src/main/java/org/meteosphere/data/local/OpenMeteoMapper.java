package org.meteosphere.data.local;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.Weather;

// Builds the app model from Open-Meteo data the same way the wpr server does, with the values rounded as the server
// sends them.
final class OpenMeteoMapper {

    // Used when the geomagnetic activity is unknown
    private static final int DEFAULT_GRADE = 1;
    private static final float HPA_PER_MMHG = 1.3332f;
    private static final int HOURLY_FORECAST_COUNT = 24 * 3;

    private OpenMeteoMapper() {
    }

    private static double roundCoordinate(Double value) {
        return Double.parseDouble(String.format(Locale.US, "%.2f", value.floatValue()));
    }

    // The digits the server sends: 1.2 rather than the float's 1.2000000476837158
    static double roundToTenths(float value) {
        return Double.parseDouble(String.format(Locale.US, "%.1f", value));
    }

    // The app reads the local time of the location as GMT
    static SimpleDateFormat createGmtFormat(String pattern) {
        SimpleDateFormat res = new SimpleDateFormat(pattern, Locale.US);
        res.setTimeZone(TimeZone.getTimeZone("GMT"));
        return res;
    }

    static long parseOrDefault(DateFormat format, String value, long defaultValue) {
        try {
            return format.parse(value).getTime();
        } catch (ParseException e) {
            return defaultValue;
        }
    }

    static Place fromGeoLocation(GeoLocation geoLocation) {
        Place location = new Place();
        location.id = geoLocation.id;
        location.name = geoLocation.name != null ? geoLocation.name : "";
        location.country = geoLocation.country != null ? geoLocation.country : "";
        location.region = geoLocation.admin1 != null ? geoLocation.admin1 : "";
        location.airport = "AIRP".equals(geoLocation.featureCode);
        location.latitude = roundCoordinate(geoLocation.latitude);
        location.longitude = roundCoordinate(geoLocation.longitude);
        if (geoLocation.timezone != null) {
            location.utcOffsetMinutes = TimeZone.getTimeZone(geoLocation.timezone).getOffset(System.currentTimeMillis()) / 60000;
            location.timeZoneId = geoLocation.timezone;
        }
        if (geoLocation.elevation != null) {
            location.elevation = (int) Math.round(geoLocation.elevation);
        }
        if (geoLocation.distance != null) {
            location.distanceKm = (float) roundToTenths(geoLocation.distance);
        }
        return location;
    }

    static int getWindDirection(int degrees) {
        if (degrees < 22) return 1;
        if (degrees < 45 + 22.5) return 2;
        if (degrees < 90 + 22.5) return 3;
        if (degrees < 135 + 22.5) return 4;
        if (degrees < 180 + 22.5) return 5;
        if (degrees < 225 + 22.5) return 6;
        if (degrees < 270 + 22.5) return 7;
        if (degrees < 315 + 22.5) return 8;
        return 1;
    }

    private static Date parseOMDate(String dateStr, DateFormat omdf) {
        try {
            return omdf.parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }

    private static int getHoursInMinutes(Calendar cal, long timestamp) {
        cal.setTimeInMillis(timestamp);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        return h * 60 + m;
    }

    // The sunset's minute of the day less the sunrise's, as the server counts it, the times local ones read as UTC
    private static int dayLengthMinutes(Calendar calUtc, long sunrise, long sunset) {
        return getHoursInMinutes(calUtc, sunset) - getHoursInMinutes(calUtc, sunrise);
    }

    private static String getIcon(Calendar cal, Date date, int wmo) {
        String dayNightPrefix;
        if (date == null) {
            dayNightPrefix = "d";
        } else {
            cal.setTime(date);
            int h = cal.get(Calendar.HOUR_OF_DAY);
            boolean day = h >= 8 && h <= 18;
            dayNightPrefix = day ? "d" : "n";
        }
        String body;
        switch (wmo) {
            case 0:
                body = ""; // "Clear sky";
                break;
            case 1:
                body = "c2"; // "Mainly clear";
                break;
            case 2:
                body = "c3"; // "Partly cloudy";
                break;
            case 3:
                body = "c4"; // "Overcast";
                break;

            case 45:
                body = "mist"; // "Fog";
                break;
            case 48:
                body = "s1.mist"; // "Depositing rime fog";
                break;

            case 51:
                body = "c4.r1"; // "Drizzle: Light intensity";
                break;
            case 53:
                body = "c4.r1"; // "Drizzle: Moderate intensity";
                break;
            case 55:
                body = "c4.r1"; // "Drizzle: Dense intensity";
                break;

            case 56:
                body = "c4.rs1"; // "Freezing Drizzle: Light intensity";
                break;
            case 57:
                body = "c4.rs1"; // "Freezing Drizzle: Dense intensity";
                break;

            case 61:
                body = "c4.r1"; // "Rain: Slight intensity";
                break;
            case 63:
                body = "c4.r2"; // "Rain: Moderate intensity";
                break;
            case 65:
                body = "c4.r3"; // "Rain: Heavy intensity";
                break;

            case 66:
                body = "c4.rs1"; // "Freezing Rain: Light intensity";
                break;
            case 67:
                body = "c4.rs3"; // "Freezing Rain: Heavy intensity";
                break;

            case 71:
                body = "c4.s1"; // "Snow fall: Slight intensity";
                break;
            case 73:
                body = "c4.s2"; // "Snow fall: Moderate intensity";
                break;
            case 75:
                body = "c4.s3"; // "Snow fall: Heavy intensity";
                break;

            case 77:
                body = "c4.s1"; // "Snow grains";
                break;

            case 80:
                body = "c4.r3"; // "Rain showers: Slight intensity";
                break;
            case 81:
                body = "c4.r3"; // "Rain showers: Moderate intensity";
                break;
            case 82:
                body = "c4.r3"; // "Rain showers: Heavy intensity";
                break;

            case 85:
                body = "c4.s3"; // "Snow showers: Light intensity";
                break;
            case 86:
                body = "c4.s3"; // "Snow showers: Heavy intensity";
                break;

            case 95:
                body = "c4.st"; // "Thunderstorm: Slight or moderate";
                break;
            case 96:
                body = "c4.st"; // "Thunderstorm with slight hail";
                break;
            case 99:
                body = "c4.st"; // "Thunderstorm with heavy hail";
                break;

            default:
                body = ""; // "";
                break;
        }
        if (body.isEmpty()) {
            return dayNightPrefix;
        } else {
            return body.startsWith("c2") || body.startsWith("c3") ? dayNightPrefix + "." + body : body;
        }
    }

    private static Map<String, List<Number>> getHoursDayParams(OMLocation omLocation, String date) {
        Map<String, List<Number>> res = new HashMap<>();
        List<String> hourlyTimeList = omLocation.hourlyTimeList;
        for (int i = 0; i < hourlyTimeList.size(); i++) {
            String time = hourlyTimeList.get(i);
            if (time.startsWith(date)) {
                for (Map.Entry<String, List<Number>> paramsEntry : omLocation.hourlyParamsMap.entrySet()) {
                    String paramName = paramsEntry.getKey();
                    List<Number> dayParams = res.computeIfAbsent(paramName, k -> new ArrayList<>());
                    dayParams.add(paramsEntry.getValue().get(i));
                }
            }
        }
        return res;
    }

    private static final class MinMaxNumbers {
        Number min;
        Number max;
    }

    private static MinMaxNumbers getMinMax(List<Number> numbers) {
        if (numbers.isEmpty()) {
            return null;
        }
        MinMaxNumbers res = new MinMaxNumbers();
        res.min = numbers.get(0);
        res.max = numbers.get(0);
        for (int i = 1; i < numbers.size(); i++) {
            Number number = numbers.get(i);
            if (res.min.doubleValue() > number.doubleValue()) {
                res.min = number;
            }
            if (res.max.doubleValue() < number.doubleValue()) {
                res.max = number;
            }
        }
        return res;
    }

    private static void setValues(Weather forecast, Calendar cal, Date hourlyDate, OMLocation omLocation,
                                  int hourlyTimeIndex, float temperature, int kp) {
        Map<String, List<Number>> hourlyParams = omLocation.hourlyParamsMap;
        int weatherCode = hourlyParams.get("weathercode").get(hourlyTimeIndex).intValue();
        float precipitation = hourlyParams.get("precipitation").get(hourlyTimeIndex).floatValue();
        forecast.temperature = Math.round(temperature);
        forecast.pressure = Math.round(hourlyParams.get("surface_pressure").get(hourlyTimeIndex).floatValue() / HPA_PER_MMHG);
        forecast.windSpeed = Math.round(hourlyParams.get("windspeed_10m").get(hourlyTimeIndex).floatValue());
        forecast.windSector = forecast.windSpeed == 0 ? 0 : getWindDirection(hourlyParams.get("winddirection_10m").get(hourlyTimeIndex).intValue());
        forecast.humidity = hourlyParams.get("relativehumidity_2m").get(hourlyTimeIndex).intValue();
        forecast.feelsLike = Math.round(temperature);
        forecast.precipitation = roundToTenths(precipitation);
        forecast.icon = getIcon(cal, hourlyDate, weatherCode);
        forecast.code = weatherCode;
        forecast.kp = kp;
    }

    static int gradeOrDefault(Integer grade) {
        return grade != null ? grade : DEFAULT_GRADE;
    }

    // Sets the current weather and the time zone of the location, the weather given by its WMO code
    static void applyFact(Place place, OMLocation omLocation, GeomagneticSource geomagnetic) {
        DateFormat omdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        TimeZone tz = TimeZone.getTimeZone(omLocation.timezone);
        omdf.setTimeZone(tz);
        Calendar cal = Calendar.getInstance(tz);
        Calendar calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        if (omLocation.utcOffsetSeconds != null) {
            place.utcOffsetMinutes = omLocation.utcOffsetSeconds / 60;
        }

        Weather fact = new Weather();
        String currentWeatherTime = omLocation.currentWeatherTime;
        String currentWeatherDay = currentWeatherTime.substring(0, 10);
        int dailyCurrentTimeIndex = omLocation.dailyTimeList.indexOf(currentWeatherDay);
        // The current weather comes in 15 minute steps, e.g. 2026-09-30T15:45
        int hourlyCurrentTimeIndex = omLocation.hourlyTimeList.indexOf(currentWeatherTime.substring(0, 13) + ":00");
        if (hourlyCurrentTimeIndex == -1) {
            throw new IllegalStateException("No hourly data for " + currentWeatherTime);
        }

        fact.time = parseOrDefault(createGmtFormat("yyyy-MM-dd'T'HH:mm:ss"), currentWeatherTime + ":00", 0);
        Date currWeatherDate = parseOMDate(currentWeatherTime, omdf);
        if (dailyCurrentTimeIndex != -1) {
            List<Number> sunriseList = omLocation.dailyParamsMap.get("sunrise");
            List<Number> sunsetList = omLocation.dailyParamsMap.get("sunset");
            if (!sunriseList.isEmpty() && !sunsetList.isEmpty()) {
                fact.sunrise = sunriseList.get(dailyCurrentTimeIndex).longValue() * 1000;
                fact.sunset = sunsetList.get(dailyCurrentTimeIndex).longValue() * 1000;
                fact.dayLengthMinutes = dayLengthMinutes(calUtc, fact.sunrise, fact.sunset);
            }
        }

        setValues(fact, cal, currWeatherDate, omLocation, hourlyCurrentTimeIndex, omLocation.currentTemperature,
                gradeOrDefault(geomagnetic.getGrade(currWeatherDate != null ? currWeatherDate : new Date())));
        place.now = fact;
    }

    // Sets the current weather, the daily forecast and the hourly one for 3 days
    static void applyLocation(Place place, OMLocation omLocation, GeomagneticSource geomagnetic) {
        applyFact(place, omLocation, geomagnetic);

        DateFormat omdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        TimeZone tz = TimeZone.getTimeZone(omLocation.timezone);
        omdf.setTimeZone(tz);
        Calendar cal = Calendar.getInstance(tz);
        Calendar calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        DateFormat validFormat = createGmtFormat("yyyy-MM-dd'T'HH:mm:ss");
        DateFormat dayFormat = createGmtFormat("yyyy-MM-dd");

        List<Number> tempMinList = omLocation.dailyParamsMap.get("temperature_2m_min");
        List<Number> tempMaxList = omLocation.dailyParamsMap.get("temperature_2m_max");
        List<Number> sunriseList = omLocation.dailyParamsMap.get("sunrise");
        List<Number> sunsetList = omLocation.dailyParamsMap.get("sunset");
        List<Number> weatherCodeList = omLocation.dailyParamsMap.get("weathercode");
        List<Number> precipitationSumList = omLocation.dailyParamsMap.get("precipitation_sum");
        List<Number> windDirectionDominantList = omLocation.dailyParamsMap.get("winddirection_10m_dominant");
        List<String> hourlyTimeList = omLocation.hourlyTimeList;
        List<String> dailyTimeList = omLocation.dailyTimeList;
        String date = null;
        for (int i = 0; i < hourlyTimeList.size(); i++) {
            String time = hourlyTimeList.get(i);
            Date forecastDate = parseOMDate(time, omdf);
            if (date == null || !time.startsWith(date)) {
                date = time.substring(0, 10);
                int dayIndex = dailyTimeList.indexOf(date);
                DayWeather day = new DayWeather();

                day.date = parseOrDefault(dayFormat, date, 0);
                if (!sunriseList.isEmpty() && !sunsetList.isEmpty()) {
                    day.sunrise = sunriseList.get(dayIndex).longValue() * 1000;
                    day.sunset = sunsetList.get(dayIndex).longValue() * 1000;
                    day.dayLengthMinutes = dayLengthMinutes(calUtc, day.sunrise, day.sunset);
                }
                day.temperatureMin = tempMinList.get(dayIndex).intValue();
                day.temperatureMax = tempMaxList.get(dayIndex).intValue();

                Map<String, List<Number>> dayParams = getHoursDayParams(omLocation, date);

                MinMaxNumbers minMaxPressure = getMinMax(dayParams.get("surface_pressure"));
                day.pressureMin = Math.round(minMaxPressure.min.floatValue() / HPA_PER_MMHG);
                day.pressureMax = Math.round(minMaxPressure.max.floatValue() / HPA_PER_MMHG);

                MinMaxNumbers minMaxWindSpeed = getMinMax(dayParams.get("windspeed_10m"));
                day.windSpeedMax = minMaxWindSpeed.max.intValue();

                MinMaxNumbers minMaxHumidity = getMinMax(dayParams.get("relativehumidity_2m"));
                day.humidityMax = minMaxHumidity.max.intValue();

                int weatherCode = weatherCodeList.get(dayIndex).intValue();
                day.precipitation = roundToTenths(precipitationSumList.get(dayIndex).floatValue());
                day.icon = getIcon(cal, null, weatherCode);
                day.code = weatherCode;
                day.windSector = day.windSpeedMax == 0 ? 0 : getWindDirection(windDirectionDominantList.get(dayIndex).intValue());
                day.kpMax = gradeOrDefault(geomagnetic.getDayMax(date, tz));
                place.addDay(day);
            }
            if (i < HOURLY_FORECAST_COUNT) {
                Weather forecast = new Weather();
                forecast.time = parseOrDefault(validFormat, time + ":00", 0);

                int kp = gradeOrDefault(forecastDate != null ? geomagnetic.getGrade(forecastDate) : null);
                setValues(forecast, cal, forecastDate, omLocation, i,
                        omLocation.hourlyParamsMap.get("temperature_2m").get(i).floatValue(), kp);
                place.addHour(forecast);
            }
        }
        place.sortAndDedupe();
    }
}
