package org.meteosphere.data.http;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The User-Agent every request sends: the app, its version, Android's, the project's address
public class UserAgentTest {

    @Test
    public void appAndAndroid() {
        assertEquals("Meteosphere/1.0.0 (Android 17; +https://github.com/meteo-sphere/meteosphere)",
                HttpUtils.userAgent("1.0.0", "17"));
        assertEquals("Meteosphere/1.2.3 (Android 7.0; +https://github.com/meteo-sphere/meteosphere)",
                HttpUtils.userAgent("1.2.3", "7.0"));
    }
}
