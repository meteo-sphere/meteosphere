package org.meteosphere.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.TreeMap;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.wpr.WprReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

// The *_wpr.json files are what the wpr server made of the same Open-Meteo responses
// with the geomagnetic activity of STUB_GEOMAGNETIC: its sources compiled with KpIndex giving the same values.
// They are read as the app reads the server's answers, so every field the app keeps is compared.
public class OpenMeteoMapperTest {

    static final GeomagneticSource STUB_GEOMAGNETIC = new GeomagneticSource() {
        @Override
        public Integer getGrade(Date date) {
            int v = (int) Math.floorMod(date.getTime() / 10800000L, 10L);
            return v == 9 ? null : v;
        }

        @Override
        public Integer getDayMax(String date, TimeZone timeZone) {
            int v = (Integer.parseInt(date.substring(8)) + timeZone.getRawOffset() / 3600000 + 24) % 10;
            return v == 9 ? null : v;
        }
    };

    // Of the precipitation's kind, hourly and as the daily sums
    private static final String[] KIND_SERIES = {"rain", "showers", "snowfall"};

    static String read(String name) throws IOException {
        try (InputStream in = OpenMeteoMapperTest.class.getResourceAsStream(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // Sorted keys one per line, for readable diffs
    private static void appendCanonical(StringBuilder sb, Object value, String indent) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                String key = it.next();
                sorted.put(key, obj.opt(key));
            }
            sb.append("{\n");
            for (String key : sorted.keySet()) {
                sb.append(indent).append("  ").append(key).append(": ");
                appendCanonical(sb, sorted.get(key), indent + "  ");
                sb.append('\n');
            }
            sb.append(indent).append('}');
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            sb.append("[\n");
            for (int i = 0; i < array.length(); i++) {
                sb.append(indent).append("  ");
                appendCanonical(sb, array.opt(i), indent + "  ");
                sb.append('\n');
            }
            sb.append(indent).append(']');
        } else {
            sb.append(value);
        }
    }

    static String canonical(Place location) throws Exception {
        StringBuilder sb = new StringBuilder();
        appendCanonical(sb, location.toJson(), "");
        return sb.toString();
    }

    @Test
    public void forecastMatchesServer() throws Exception {
        GeoLocation geoLocation = GeoLocation.parseJson(new JSONObject(read("geo_get_524901.json")));
        Place actual = OpenMeteoMapper.fromGeoLocation(geoLocation);
        OpenMeteoMapper.applyLocation(actual, OMLocation.parseJson(read("forecast_524901.json")), STUB_GEOMAGNETIC);

        ArrayList<Place> expected = WprReader.places(read("forecast_524901_wpr.json"));
        assertEquals(1, expected.size());
        assertEquals(canonical(expected.get(0)), canonical(actual));
        assertEquals(72, actual.hours.size());
        assertEquals(7, actual.days.size());
    }

    // The current weather of a list, as the nearby places get it
    @Test
    public void listFactsMatchServer() throws Exception {
        ArrayList<Place> actual = new ArrayList<>();
        for (GeoLocation geoLocation : Geocoder.parseSearchResults(read("geo_search_mos.json"))) {
            actual.add(OpenMeteoMapper.fromGeoLocation(geoLocation));
        }
        List<OMLocation> omLocations = OMLocation.parseJsonList(read("facts_search_mos.json"));
        OpenMeteoWeatherProvider.applyFacts(actual, omLocations, STUB_GEOMAGNETIC);

        ArrayList<Place> expected = WprReader.places(read("cities_facts_mos_wpr.json"));
        assertEquals(10, expected.size());
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(canonical(expected.get(i)), canonical(actual.get(i)));
        }
    }

    // The saved answers have the series of rain, showers and snowfall the app no longer asks for: the places come out
    // the same without them
    private static String withoutKindSeries(String json) throws Exception {
        boolean isList = json.trim().startsWith("[");
        JSONArray locations = isList ? new JSONArray(json) : new JSONArray().put(new JSONObject(json));
        for (int i = 0; i < locations.length(); i++) {
            JSONObject location = locations.getJSONObject(i);
            for (String series : KIND_SERIES) {
                assertNotNull(location.getJSONObject("hourly").remove(series));
                location.getJSONObject("daily").remove(series + "_sum");
            }
        }
        return isList ? locations.toString() : locations.getJSONObject(0).toString();
    }

    @Test
    public void forecastWithoutKindSeriesMatchesServer() throws Exception {
        GeoLocation geoLocation = GeoLocation.parseJson(new JSONObject(read("geo_get_524901.json")));
        Place actual = OpenMeteoMapper.fromGeoLocation(geoLocation);
        OpenMeteoMapper.applyLocation(actual, OMLocation.parseJson(withoutKindSeries(read("forecast_524901.json"))), STUB_GEOMAGNETIC);

        ArrayList<Place> expected = WprReader.places(read("forecast_524901_wpr.json"));
        assertEquals(canonical(expected.get(0)), canonical(actual));
    }

    @Test
    public void listFactsWithoutKindSeriesMatchServer() throws Exception {
        ArrayList<Place> actual = new ArrayList<>();
        for (GeoLocation geoLocation : Geocoder.parseSearchResults(read("geo_search_mos.json"))) {
            actual.add(OpenMeteoMapper.fromGeoLocation(geoLocation));
        }
        List<OMLocation> omLocations = OMLocation.parseJsonList(withoutKindSeries(read("facts_search_mos.json")));
        OpenMeteoWeatherProvider.applyFacts(actual, omLocations, STUB_GEOMAGNETIC);

        ArrayList<Place> expected = WprReader.places(read("cities_facts_mos_wpr.json"));
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(canonical(expected.get(i)), canonical(actual.get(i)));
        }
    }

    // A place again in a new language, as places?ids= gives it
    @Test
    public void placeByIdMatchesServer() throws Exception {
        Place actual = OpenMeteoMapper.fromGeoLocation(GeoLocation.parseJson(new JSONObject(read("geo_get_524901_en.json"))));

        ArrayList<Place> expected = WprReader.places(read("cities_ids_en_wpr.json"));
        assertEquals(1, expected.size());
        assertEquals(canonical(expected.get(0)), canonical(actual));
        assertEquals("Moscow", actual.name);
        assertEquals("Russia", actual.country);
    }
}
