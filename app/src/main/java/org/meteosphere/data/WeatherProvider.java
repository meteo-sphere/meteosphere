package org.meteosphere.data;

import java.util.ArrayList;
import java.util.List;
import org.meteosphere.data.model.Place;

/**
 * Source of locations and forecasts. Location ids are GeoNames ids for every provider,
 * so saved locations stay valid after switching between them.
 * The language is the app one ("ru", "en", "uk", "pl"), each provider converts it as needed.
 */
public interface WeatherProvider {

    ArrayList<Place> findLocations(String query, String lang, int timeoutMs) throws WeatherApiException;

    ArrayList<Place> getLocationsNear(double lat, double lng, int count, String lang, boolean withFacts, int timeoutMs) throws WeatherApiException;

    ArrayList<Place> getForecast(int locationId, String lang, int timeoutMs) throws WeatherApiException;

    /** The places without their weather, for their names in the language; the unknown ones left out */
    ArrayList<Place> getLocationsByIds(List<Integer> locationIds, String lang, int timeoutMs) throws WeatherApiException;
}
