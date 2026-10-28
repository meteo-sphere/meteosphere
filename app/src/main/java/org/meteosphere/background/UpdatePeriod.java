package org.meteosphere.background;

import android.content.Context;
import org.meteosphere.R;
import org.meteosphere.settings.AppSettings;

/** The update period of the settings, at least min_update_period_s, and whether data loaded at a time is due */
final class UpdatePeriod {

    // The refreshes come at intervals of the update period, the periodic refresh and the app's openings, and a load ends
    // a moment after its refresh starts: at the next refresh the data falls short of the period's age by that moment,
    // and without the slack it would not be due there but only at the refresh after it
    private static final long SLACK_MS = 30 * 1000L;

    private UpdatePeriod() {
    }

    static long ms(Context context) {
        return Math.max(context.getResources().getInteger(R.integer.min_update_period_s) * 1000L,
                AppSettings.getInstance().refreshMinutes * 60 * 1000L);
    }

    /** Whether data loaded at the time is to be asked for again by now, the clock set back too */
    static boolean isDue(long loadTime, long now, long periodMs) {
        return Math.abs(now - loadTime) >= periodMs - SLACK_MS;
    }
}
