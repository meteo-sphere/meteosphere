package org.meteosphere.data.local;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;

// kp_expected.txt is what the KpIndex of the wpr server gives for kp_forecast.json and kp_outlook.txt
public class KpIndexTest {

    @Test
    public void gradesMatchServer() throws Exception {
        KpIndex kpIndex = new KpIndex();
        kpIndex.setData(KpIndex.parseKpForecast(OpenMeteoMapperTest.read("kp_forecast.json")),
                KpIndex.parseOutlook(OpenMeteoMapperTest.read("kp_outlook.txt")));
        int checked = 0;
        for (String line : OpenMeteoMapperTest.read("kp_expected.txt").split("\n")) {
            // grade <UTC millis> <Kp> or daymax <yyyy-MM-dd> <time zone> <Kp>
            String[] parts = line.split(" ");
            if (parts[0].equals("grade")) {
                assertEquals(line, parts[2], String.valueOf(kpIndex.getGrade(new Date(Long.parseLong(parts[1])))));
            } else {
                assertEquals(line, parts[3], String.valueOf(kpIndex.getDayMax(parts[1], TimeZone.getTimeZone(parts[2]))));
            }
            checked++;
        }
        assertEquals(2430, checked);
    }
}
