package org.meteosphere.ui.astro;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.color.MaterialColors;
import org.meteosphere.ui.sky.SkyWeather;
import org.meteosphere.ui.sky.SkyBackgrounds;

/**
 * The skies of the Moon's and the Sun's screens, a clear night's and a clear day's, the Sun alone in it (chosen over a
 * fair day's clouds behind the charts), and the app bar over them: see-through, with white title, icons and system bar
 * icons while a sky shows.
 */
public final class AstroSkies {

    private AstroSkies() {
    }

    /** Composes the night sky, or the day's, off the main thread and hands it over on the main thread */
    public static void load(Context context, boolean day, SkyBackgrounds.OnComposed onComposed) {
        SkyBackgrounds.getInstance(context).compose(
                day ? SkyWeather.of("d", true) : SkyWeather.of("n", false),
                SkyBackgrounds.composedSize(context), onComposed);
    }

    /**
     * How much a sky shows behind a clear bar, 0 none to 1 fully: in the light theme its title and icons, and the
     * system bars' icons, turn white over it
     */
    public static void applyContentColors(Activity activity, Toolbar toolbar, float sky) {
        boolean onSky = sky > 0.5f;
        boolean lightTheme = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        if (!lightTheme) {
            return;
        }
        // The light theme's dark title, icons and system bar icons turn white over a sky
        int color = onSky ? Color.WHITE : MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface);
        int iconColor = onSky ? Color.WHITE : MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurfaceVariant);
        toolbar.setTitleTextColor(color);
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().mutate().setTint(iconColor);
        }
        for (int i = 0; i < toolbar.getMenu().size(); i++) {
            MenuItemCompat.setIconTintList(toolbar.getMenu().getItem(i), ColorStateList.valueOf(iconColor));
        }
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().mutate().setTint(iconColor);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!onSky);
        controller.setAppearanceLightNavigationBars(!onSky);
    }
}
