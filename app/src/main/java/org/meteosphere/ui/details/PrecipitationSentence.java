package org.meteosphere.ui.details;

import android.content.Context;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;
import org.meteosphere.ui.chart.ForecastFormat;

/**
 * The sentence of the city page's precipitation insight, for the widgets: "Rain until 17:00", "Rain tomorrow at 9:00",
 * "Snow on Saturday" or "No precipitation in the days to come"
 */
public final class PrecipitationSentence {

    private PrecipitationSentence() {
    }

    /** At the place's time nowLocal, its local time read as UTC, with its weather then */
    public static String of(Context context, Place location, Weather current, long nowLocal) {
        DetailsModelBuilder.Settings settings = DetailsModelBuilder.Settings.read(context);
        DetailsFormat format = new DetailsFormat(context, settings, nowLocal);
        // The precipitation's sentence needs neither the instant nor the day the Sun is counted by
        InsightsBuilder insights = new InsightsBuilder(format, location, System.currentTimeMillis(),
                ForecastFormat.floorDay(nowLocal));
        return insights.precipitationText(current, new HoursBuilder(format, location).upcoming(current),
                new DaysBuilder(format, location).upcoming());
    }
}
