package org.meteosphere.data.local;

import java.util.Date;
import java.util.TimeZone;

/** Geomagnetic activity as the planetary K-index, the same for every location. */
interface GeomagneticSource {

    // Kp for the 3-hour interval containing the time, null if unknown
    Integer getGrade(Date date);

    // Largest Kp during the local day (yyyy-MM-dd), null if unknown
    Integer getDayMax(String date, TimeZone timeZone);
}
