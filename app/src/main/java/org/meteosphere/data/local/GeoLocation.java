package org.meteosphere.data.local;

import org.json.JSONObject;

final class GeoLocation {

    // {"id":524901,"name":"Москва","latitude":55.75204,"longitude":37.61781,"elevation":155.0,"feature_code":"PPLC",
    // "country_code":"RU","admin1_id":524894,"timezone":"Europe/Moscow","population":10381222,"country_id":2017370,
    // "country":"Россия","admin1":"Москва"}

    Integer id;
    String name;
    Double latitude;
    Double longitude;
    // Meters above the sea level
    Double elevation;
    String featureCode;
    String countryCode;
    Integer countryId;
    String country;
    Integer admin1Id;
    String admin1;
    String timezone;
    Long population;

    // Distance in km from the requested point, only for nearest cities
    Float distance;

    GeoLocation copy() {
        GeoLocation res = new GeoLocation();
        res.id = id;
        res.name = name;
        res.latitude = latitude;
        res.longitude = longitude;
        res.elevation = elevation;
        res.featureCode = featureCode;
        res.countryCode = countryCode;
        res.countryId = countryId;
        res.country = country;
        res.admin1Id = admin1Id;
        res.admin1 = admin1;
        res.timezone = timezone;
        res.population = population;
        res.distance = distance;
        return res;
    }

    private static String parseString(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value instanceof String ? (String) value : null;
    }

    private static Number parseNumber(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value instanceof Number ? (Number) value : null;
    }

    static GeoLocation parseJson(JSONObject obj) {
        Number id = parseNumber(obj, "id");
        Number latitude = parseNumber(obj, "latitude");
        Number longitude = parseNumber(obj, "longitude");
        if (id == null || latitude == null || longitude == null) {
            return null;
        }

        GeoLocation res = new GeoLocation();
        res.id = id.intValue();
        res.name = parseString(obj, "name");
        res.latitude = latitude.doubleValue();
        res.longitude = longitude.doubleValue();
        Number elevation = parseNumber(obj, "elevation");
        res.elevation = elevation != null ? elevation.doubleValue() : null;
        res.featureCode = parseString(obj, "feature_code");
        res.countryCode = parseString(obj, "country_code");
        Number countryId = parseNumber(obj, "country_id");
        res.countryId = countryId != null ? countryId.intValue() : null;
        res.country = parseString(obj, "country");
        Number admin1Id = parseNumber(obj, "admin1_id");
        res.admin1Id = admin1Id != null ? admin1Id.intValue() : null;
        res.admin1 = parseString(obj, "admin1");
        res.timezone = parseString(obj, "timezone");
        Number population = parseNumber(obj, "population");
        res.population = population != null ? population.longValue() : null;
        return res;
    }
}
