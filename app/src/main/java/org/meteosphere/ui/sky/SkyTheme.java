package org.meteosphere.ui.sky;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.LayoutInflater;
import androidx.appcompat.view.ContextThemeWrapper;
import org.meteosphere.R;

/**
 * The context of a tab drawn over its sky: the app's theme in its dark variant, whatever the app's own, with the
 * sky overlay's translucent cards. Views and colours looked up through it suit the sky behind them.
 *
 * The theme is built afresh from the app's theme under the night resources: a copy of the activity's theme, made
 * under its own configuration, loses part of its styles' chain there.
 */
public final class SkyTheme extends ContextThemeWrapper {

    private final int overlay;
    private Resources.Theme theme;

    private SkyTheme(Context base, int overlay) {
        super(base, R.style.AppTheme);
        this.overlay = overlay;
        // Only the night bit is overridden: the -night resources and the dark side of the DayNight theme. The locales are
        // the screen's: an override replaces the screen's own, and the app's language in it would give way to the phone's
        Configuration night = new Configuration();
        night.uiMode = Configuration.UI_MODE_NIGHT_YES;
        night.setLocales(base.getResources().getConfiguration().getLocales());
        applyOverrideConfiguration(night);
    }

    @Override
    public Resources.Theme getTheme() {
        if (this.theme == null) {
            this.theme = getResources().newTheme();
            this.theme.applyStyle(R.style.AppTheme, true);
            this.theme.applyStyle(this.overlay, true);
        }
        return this.theme;
    }

    public static Context wrap(Context base, boolean day) {
        return new SkyTheme(base, day ? R.style.ThemeOverlay_Meteo_SkyDay : R.style.ThemeOverlay_Meteo_SkyNight);
    }

    /** The dark theme with another overlay over it, one based on the night sky's */
    public static Context wrap(Context base, int overlay) {
        return new SkyTheme(base, overlay);
    }

    public static LayoutInflater inflater(LayoutInflater inflater, boolean day) {
        return inflater.cloneInContext(wrap(inflater.getContext(), day));
    }
}
