package org.meteosphere.data;

/** A request for locations or weather that failed; the message tells how, for the log */
public final class WeatherApiException extends Exception {

    public WeatherApiException(String message) {
        super(message);
    }

    public WeatherApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
