package org.meteosphere.data;

import org.meteosphere.data.model.Place;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.data.wpr.RemoteWeatherProvider;
import org.meteosphere.addon.AddOns;

/** Entry point for weather data, forwards the requests to the provider chosen in the settings. */
public final class WeatherClient {

    private static volatile WeatherProvider sProvider = new RemoteWeatherProvider(WeatherProviders.DEFAULT_WEATHER_SOURCE);

    private WeatherClient() {
    }

    public static void setProvider(WeatherProvider provider) {
        sProvider = provider;
    }

    public static ArrayList<Place> findLocations(String query, String lang, int timeoutMs) throws WeatherApiException {
        return sProvider.findLocations(query, lang, timeoutMs);
    }

    public static ArrayList<Place> getLocationsNear(double lat, double lng, int count, String lang, boolean withFacts, int timeoutMs) throws WeatherApiException {
        return sProvider.getLocationsNear(lat, lng, count, lang, withFacts, timeoutMs);
    }

    // The add-ons put their weather over the provider's forecast
    public static ArrayList<Place> getForecast(int locationId, String lang, int timeoutMs) throws WeatherApiException {
        ArrayList<Place> res = sProvider.getForecast(locationId, lang, timeoutMs);
        for (Place place : res) {
            AddOns.apply(place);
        }
        return res;
    }

    public static ArrayList<Place> getLocationsByIds(List<Integer> locationIds, String lang, int timeoutMs) throws WeatherApiException {
        return sProvider.getLocationsByIds(locationIds, lang, timeoutMs);
    }
}
