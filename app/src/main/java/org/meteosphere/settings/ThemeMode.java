package org.meteosphere.settings;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import org.meteosphere.R;

/** The light or dark look chosen in the settings: as on the device, light or dark */
public final class ThemeMode {

    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private ThemeMode() {
    }

    /** Applies the chosen one; the open screens are recreated in it */
    public static void apply(Context context) {
        apply(AppSettings.getInstance().preferences.getString(context.getString(R.string.key_theme), SYSTEM));
    }

    public static void apply(String mode) {
        AppCompatDelegate.setDefaultNightMode(LIGHT.equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
                : DARK.equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
