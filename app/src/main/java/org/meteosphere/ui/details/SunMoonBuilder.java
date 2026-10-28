package org.meteosphere.ui.details;

import android.content.res.Resources;
import org.meteosphere.R;
import org.meteosphere.astro.Moon;
import org.meteosphere.astro.Sun;
import org.meteosphere.data.model.Place;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.sky.MoonPicture;

/** The Sun's and the Moon's cards of a city's details: the Sun's day and its height through it, the Moon's phase */
final class SunMoonBuilder {

    // The Sun's height through the day, every this many minutes
    private static final int SUN_STEP_MINUTES = 10;

    private final DetailsFormat format;
    private final Place location;
    private final long nowUtc;
    // The local day at the place as the Sun and the Moon count it: midnight read as UTC
    private final long dayStart;

    SunMoonBuilder(DetailsFormat format, Place location, long nowUtc, long dayStart) {
        this.format = format;
        this.location = location;
        this.nowUtc = nowUtc;
        this.dayStart = dayStart;
    }

    /** The Sun's day at the place from the local midnight given, its times local ones */
    static Sun.Day sunDay(Place location, long localDayStart) {
        return Sun.day(localDayStart, location.offsetMinutesOfDay(localDayStart), location.latitude, location.longitude);
    }

    void build(DetailsModel model) {
        Resources res = this.format.res;
        double latitude = this.location.latitude;
        double longitude = this.location.longitude;
        int offset = this.location.offsetMinutesOfDay(this.dayStart);
        Sun.Day sun = Sun.day(this.dayStart, offset, latitude, longitude);
        long dayStartUtc = this.dayStart - offset * DetailsFormat.MINUTE_MS;
        model.hasSun = true;
        model.hasSunNow = this.format.settings.parameters.contains(DetailsModelBuilder.SUN_NOW);
        model.sunAltitudes = new float[24 * 60 / SUN_STEP_MINUTES + 1];
        for (int i = 0; i < model.sunAltitudes.length; i++) {
            model.sunAltitudes[i] = (float) Sun.altitude(dayStartUtc + i * SUN_STEP_MINUTES * DetailsFormat.MINUTE_MS, latitude, longitude);
        }
        model.sunNowMinutes = (int) ((this.location.toLocal(this.nowUtc) - this.dayStart) / DetailsFormat.MINUTE_MS);
        model.sunNowAltitude = (float) Sun.altitude(this.nowUtc, latitude, longitude);
        if (sun.polarDay || sun.polarNight) {
            model.sunTitle = res.getString(R.string.tab_sun) + " · " + res.getString(sun.polarDay ? R.string.details_polar_day : R.string.details_polar_night);
        } else {
            model.sunTitle = res.getString(R.string.details_sun_title, this.format.astro.duration(sun.dayLength));
        }
        model.sunrise = this.format.clock(sun.rise);
        model.sunset = this.format.clock(sun.set);
        model.twilight = sun.dawn != Sun.NONE && sun.dusk != Sun.NONE ? this.format.clock(sun.dawn) + "–" + this.format.clock(sun.dusk) : null;

        model.moonAge = Moon.age(this.nowUtc);
        // Drawn here, off the main thread
        model.moonPicture = MoonPicture.draw(res, Math.round(DetailsAstro.MOON_DP * res.getDisplayMetrics().density),
                Moon.disc(this.nowUtc, latitude, longitude), DetailsAstro.DARK_SIDE, DetailsAstro.DARK_SIDE_ALPHA);
        model.moonIllumination = res.getString(R.string.details_moon_illuminated, this.format.astro.illumination(Moon.illumination(this.nowUtc)));
        Moon.Day moon = Moon.day(this.dayStart, offset, latitude, longitude);
        model.moonPhase = this.format.astro.phaseName(moon.phase);
        model.moonrise = this.format.clock(moon.rise);
        model.moonset = this.format.clock(moon.set);
        Moon.Phase next = Moon.nextPhase(this.nowUtc);
        long nextLocal = this.location.toLocal(next.time);
        int days = (int) ((ForecastFormat.floorDay(nextLocal) - this.dayStart) / DetailsFormat.DAY_MS);
        String phaseName = this.format.astro.phaseName(next.phase);
        model.moonNextDays = days;
        if (days <= 0) {
            model.moonNext = res.getString(R.string.details_moon_today_at, phaseName, this.format.clock(nextLocal));
        } else if (days == 1) {
            model.moonNext = res.getString(R.string.details_moon_tomorrow, phaseName);
        } else {
            model.moonNext = res.getString(R.string.details_moon_in_days, phaseName, days, res.getQuantityString(R.plurals.days, days));
        }
    }
}
