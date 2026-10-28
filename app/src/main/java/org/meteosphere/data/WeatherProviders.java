package org.meteosphere.data;

import org.meteosphere.data.wpr.RemoteWeatherProvider;
import android.content.Context;
import org.meteosphere.BuildConfig;
import java.util.HashMap;
import java.util.Map;
import org.meteosphere.data.local.ForecaWeatherProvider;
import org.meteosphere.data.local.OpenMeteoWeatherProvider;

/**
 * The providers to choose from in the settings: where the data is made (the data_provider preference)
 * and where the weather comes from (the weather_source preference). The ids are the preference values.
 */
public final class WeatherProviders {

    // The wpr server, in the builds with its address
    public static final String REMOTE = "remote";
    // The same work right on the device, the only way of the public build
    public static final String LOCAL = "local";
    public static final String DEFAULT_DATA_PROVIDER = hasServer() ? REMOTE : LOCAL;

    public static final String OPEN_METEO = "open_meteo";
    public static final String FORECA = "foreca";
    public static final String DEFAULT_WEATHER_SOURCE = OPEN_METEO;

    // One instance per provider, so their caches outlive the settings reloads
    private static final Map<String, WeatherProvider> sProviders = new HashMap<>();

    private WeatherProviders() {
    }

    /** Whether the app was built with the server's address, wpr.url of local.properties; the public build has none */
    public static boolean hasServer() {
        return !BuildConfig.WPR_URL.isEmpty();
    }

    /** The data provider that works for the chosen one: the server while there is one, the device otherwise */
    public static String dataProvider(String chosen) {
        return hasServer() && !LOCAL.equals(chosen) ? REMOTE : LOCAL;
    }

    /** Whether the device itself asks Foreca for the weather, needing a key, rather than the server with its own */
    public static boolean asksForeca(String dataProvider, String weatherSource) {
        return LOCAL.equals(dataProvider(dataProvider)) && FORECA.equals(weatherSource);
    }

    // Unknown ids fall back to the defaults. The keys are needed for the local providers of their sources only.
    public static synchronized WeatherProvider get(Context context, String dataProvider, String weatherSource, String forecaToken) {
        String providerId = dataProvider(dataProvider);
        String sourceId = FORECA.equals(weatherSource) ? weatherSource : DEFAULT_WEATHER_SOURCE;
        // A new Foreca key makes a new provider
        String key = providerId + "/" + sourceId + (asksForeca(providerId, sourceId) ? "/" + forecaToken : "");
        WeatherProvider provider = sProviders.get(key);
        if (provider == null) {
            provider = create(context.getApplicationContext(), providerId, sourceId, forecaToken);
            sProviders.put(key, provider);
        }
        return provider;
    }

    private static WeatherProvider create(Context context, String dataProvider, String weatherSource, String forecaToken) {
        if (!LOCAL.equals(dataProvider)) {
            return new RemoteWeatherProvider(weatherSource);
        }
        switch (weatherSource) {
            case FORECA:
                return new ForecaWeatherProvider(context, forecaToken);
            case OPEN_METEO:
            default:
                return new OpenMeteoWeatherProvider(context);
        }
    }
}
