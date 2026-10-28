package org.meteosphere.data.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

// The saved places: every field back as it was, the defaults left out, and an older build's places dropped
public class PlaceJsonTest {

    // Moscow on 2025-10-23 with every field other than its default
    private static Place fullPlace() {
        Place place = new Place();
        place.id = 524901;
        place.name = "Moscow";
        place.customName = "Home";
        place.country = "Russia";
        place.region = "Moscow";
        place.airport = true;
        place.latitude = 55.7522;
        place.longitude = 37.6156;
        place.elevation = 144;
        place.timeZoneId = "Europe/Moscow";
        place.utcOffsetMinutes = 180;
        place.distanceKm = 2.4f;
        place.loadedAt = 1761200000000L;
        place.sourceTime = 1761199800000L;
        place.addOnSource = "add-on";
        place.now = weather(1761210000000L);
        place.now.sunrise = 1761191940000L;
        place.now.sunset = 1761228900000L;
        place.now.dayLengthMinutes = 616;
        place.addHour(weather(1761210000000L));
        place.addHour(weather(1761213600000L));
        DayWeather day = new DayWeather();
        day.date = 1761177600000L;
        day.icon = "c4.rs2.st";
        day.code = 95;
        day.temperatureMin = -3;
        day.temperatureMax = 8;
        day.pressureMin = 741;
        day.pressureMax = 748;
        day.humidityMax = 97;
        day.windSpeedMax = 6;
        day.windSector = 7;
        day.precipitation = 4.6;
        day.kpMax = 5;
        day.sunrise = 1761191940000L;
        day.sunset = 1761228900000L;
        day.dayLengthMinutes = 616;
        place.addDay(day);
        return place;
    }

    private static Weather weather(long time) {
        Weather weather = new Weather();
        weather.time = time;
        weather.icon = "d.c2";
        weather.code = 2;
        weather.temperature = 7;
        weather.feelsLike = 5;
        weather.pressure = 745;
        weather.windSpeed = 3;
        weather.windSector = 6;
        weather.humidity = 81;
        weather.precipitation = 0.3;
        weather.kp = 2;
        return weather;
    }

    private static Place roundTrip(Place place) throws Exception {
        return Place.fromJson(new JSONObject(place.toJson().toString()));
    }

    @Test
    public void keepsEveryField() throws Exception {
        Place place = roundTrip(fullPlace());
        assertEquals(524901, place.id);
        assertEquals("Moscow", place.name);
        assertEquals("Home", place.customName);
        assertEquals("Russia", place.country);
        assertEquals("Moscow", place.region);
        assertTrue(place.airport);
        assertEquals(55.7522, place.latitude, 0);
        assertEquals(37.6156, place.longitude, 0);
        assertEquals(144, place.elevation);
        assertEquals("Europe/Moscow", place.timeZoneId);
        assertEquals(180, place.utcOffsetMinutes);
        assertEquals(2.4f, place.distanceKm, 0);
        assertEquals(1761200000000L, place.loadedAt);
        assertEquals(1761199800000L, place.sourceTime);
        assertEquals("add-on", place.addOnSource);

        Weather now = place.now;
        assertEquals(1761210000000L, now.time);
        assertEquals("d.c2", now.icon);
        assertEquals(2, now.code);
        assertEquals(7, now.temperature);
        assertEquals(5, now.feelsLike);
        assertEquals(745, now.pressure);
        assertEquals(3, now.windSpeed);
        assertEquals(6, now.windSector);
        assertEquals(81, now.humidity);
        assertEquals(0.3, now.precipitation, 0);
        assertEquals(2, now.kp);
        assertEquals(1761191940000L, now.sunrise);
        assertEquals(1761228900000L, now.sunset);
        assertEquals(616, now.dayLengthMinutes);

        assertEquals(2, place.hours.size());
        assertEquals(1761213600000L, place.hours.get(1).time);
        assertEquals(745, place.hours.get(1).pressure);

        assertEquals(1, place.days.size());
        DayWeather day = place.days.get(0);
        assertEquals(1761177600000L, day.date);
        assertEquals("c4.rs2.st", day.icon);
        assertEquals(95, day.code);
        assertEquals(-3, day.temperatureMin);
        assertEquals(8, day.temperatureMax);
        assertEquals(741, day.pressureMin);
        assertEquals(748, day.pressureMax);
        assertEquals(97, day.humidityMax);
        assertEquals(6, day.windSpeedMax);
        assertEquals(7, day.windSector);
        assertEquals(4.6, day.precipitation, 0);
        assertEquals(5, day.kpMax);
        assertEquals(1761191940000L, day.sunrise);
        assertEquals(1761228900000L, day.sunset);
        assertEquals(616, day.dayLengthMinutes);

        assertEquals(fullPlace().toJson().toString(), place.toJson().toString());
    }

    // The keys of the saved format, the float's own digits for the distance
    @Test
    public void writesTheSavedFormat() throws Exception {
        JSONObject json = fullPlace().toJson();
        assertEquals("Home", json.getString("custom_name"));
        assertEquals("Europe/Moscow", json.getString("tz"));
        assertEquals(2.4, json.getDouble("distance_km"), 0);
        assertEquals(616, json.getJSONObject("now").getInt("day_length"));
        assertEquals(97, json.getJSONArray("days").getJSONObject(0).getInt("humidity_max"));
        assertEquals(745, json.getJSONArray("hours").getJSONObject(0).getInt("pressure"));
    }

    @Test
    public void leavesOutTheDefaults() throws Exception {
        Place place = new Place();
        place.id = 1;
        place.now = new Weather();
        JSONObject json = place.toJson();
        assertEquals(2, json.length());
        assertEquals(0, json.getJSONObject("now").length());

        Place back = Place.fromJson(json);
        assertEquals("", back.name);
        assertNull(back.customName);
        assertEquals("", back.country);
        assertNull(back.timeZoneId);
        assertFalse(back.airport);
        assertEquals(-1, back.now.code);
        assertEquals("nodata", back.now.icon);
        assertTrue(back.hours.isEmpty());
        assertTrue(back.days.isEmpty());
    }

    // As an older build saved it: dropped rather than read wrong
    @Test
    public void noPlaceWithoutAnId() throws Exception {
        assertNull(Place.fromJson(new JSONObject("{\"location_id\": 524901, \"location_name\": \"Москва\"}")));
    }

    // The copy and the place change apart
    @Test
    public void copiesDeep() {
        Place place = fullPlace();
        Place copy = place.copy();
        copy.now.temperature = 30;
        copy.hours.get(0).temperature = 30;
        copy.days.get(0).temperatureMax = 30;
        copy.addHour(new Weather());
        assertEquals(7, place.now.temperature);
        assertEquals(7, place.hours.get(0).temperature);
        assertEquals(8, place.days.get(0).temperatureMax);
        assertEquals(2, place.hours.size());
    }
}
