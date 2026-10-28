package org.meteosphere.data.local;

import static org.meteosphere.data.local.OpenMeteoMapperTest.STUB_GEOMAGNETIC;
import static org.meteosphere.data.local.OpenMeteoMapperTest.canonical;
import static org.meteosphere.data.local.OpenMeteoMapperTest.read;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.model.WeatherDescriptions;
import org.meteosphere.data.wpr.WprReader;
import org.json.JSONObject;
import org.junit.Test;

// The foreca_*_wpr.json files are what ForecaHelper of the wpr server made of the same Foreca responses
// with the geomagnetic activity of STUB_GEOMAGNETIC.
public class ForecaMapperTest {

    @Test
    public void forecastMatchesServer() throws Exception {
        GeoLocation geoLocation = GeoLocation.parseJson(new JSONObject(read("geo_get_524901.json")));
        Place actual = OpenMeteoMapper.fromGeoLocation(geoLocation);
        ForecaMapper.applyLocation(actual, geoLocation,
                ForecaForecast.parseCurrent(read("foreca_current_524901.json")),
                ForecaForecast.parseHourly(read("foreca_hourly_524901.json")),
                ForecaForecast.parseDaily(read("foreca_daily_524901.json")), STUB_GEOMAGNETIC);

        ArrayList<Place> expected = WprReader.places(read("foreca_forecast_524901_wpr.json"));
        assertEquals(1, expected.size());
        assertEquals(canonical(expected.get(0)), canonical(actual));
        assertEquals(72, actual.hours.size());
        assertEquals(7, actual.days.size());
    }

    // Without the daily forecast the sunrise and sunset are calculated
    @Test
    public void listFactsMatchServer() throws Exception {
        List<GeoLocation> geoLocations = Geocoder.parseSearchResults(read("geo_search_mos.json")).subList(0, 4);
        ArrayList<Place> actual = new ArrayList<>();
        for (GeoLocation geoLocation : geoLocations) {
            Place place = OpenMeteoMapper.fromGeoLocation(geoLocation);
            ForecaMapper.applyFact(place, geoLocation, ForecaForecast.parseCurrent(read("foreca_current_" + geoLocation.id + ".json")),
                    Collections.emptyList(), STUB_GEOMAGNETIC);
            actual.add(place);
        }

        ArrayList<Place> expected = WprReader.places(read("foreca_cities_wpr.json"));
        assertEquals(4, expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(canonical(expected.get(i)), canonical(actual.get(i)));
        }
    }

    // Every symbol of https://developer.foreca.com/resources, by day and by night
    @Test
    public void everySymbolHasAnIcon() {
        String[] codes = {"000", "100", "200", "300", "400", "500", "600", "210", "310", "410", "220", "320", "420", "430",
                "240", "340", "440", "211", "311", "411", "221", "321", "421", "431", "212", "312", "412", "222", "322", "422", "432"};
        for (String dayNight : new String[]{"d", "n"}) {
            for (String code : codes) {
                String icon = ForecaMapper.getIcon(dayNight + code);
                assertTrue(dayNight + code + " -> " + icon, new File("src/main/res/drawable/ic_weather_" + icon.replace('.', '_') + ".xml").exists());
            }
        }
        assertEquals("d.c3.r2.st", ForecaMapper.getIcon("d240"));
        assertEquals("c4.s3", ForecaMapper.getIcon("n432"));
        assertEquals("mist", ForecaMapper.getIcon("n600"));
        assertEquals("nodata", ForecaMapper.getIcon(null));
    }

    // Every symbol becomes a WMO code the app describes in each of its languages
    @Test
    public void everySymbolIsDescribed() {
        String[] codes = {"000", "100", "200", "300", "400", "500", "600", "210", "310", "410", "220", "320", "420", "430",
                "240", "340", "440", "211", "311", "411", "221", "321", "421", "431", "212", "312", "412", "222", "322", "422", "432"};
        for (String dayNight : new String[]{"d", "n"}) {
            for (String code : codes) {
                int wmo = ForecaMapper.getWmo(dayNight + code);
                for (String lang : WeatherDescriptions.LANGUAGES) {
                    assertFalse(dayNight + code + " -> " + wmo + " " + lang, WeatherDescriptions.get(wmo, lang).isEmpty());
                }
            }
        }
        // The cloudiness without precipitation, as LocationsHelper of the server gives its levels
        assertEquals(0, ForecaMapper.getWmo("d000"));
        assertEquals(1, ForecaMapper.getWmo("n100"));
        assertEquals(1, ForecaMapper.getWmo("d500"));
        assertEquals(2, ForecaMapper.getWmo("d200"));
        assertEquals(2, ForecaMapper.getWmo("d300"));
        assertEquals(3, ForecaMapper.getWmo("d400"));
        assertEquals(45, ForecaMapper.getWmo("d600"));
        // Slight precipitation, showers and precipitation of rain, sleet and snow, whatever the clouds
        assertEquals(61, ForecaMapper.getWmo("d210"));
        assertEquals(80, ForecaMapper.getWmo("d320"));
        assertEquals(63, ForecaMapper.getWmo("d430"));
        assertEquals(68, ForecaMapper.getWmo("d211"));
        assertEquals(83, ForecaMapper.getWmo("d321"));
        assertEquals(69, ForecaMapper.getWmo("d431"));
        assertEquals(71, ForecaMapper.getWmo("d412"));
        assertEquals(85, ForecaMapper.getWmo("d222"));
        assertEquals(73, ForecaMapper.getWmo("n432"));
        assertEquals(95, ForecaMapper.getWmo("d240"));
        assertEquals(-1, ForecaMapper.getWmo(null));
    }
}
