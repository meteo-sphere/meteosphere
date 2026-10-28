package org.meteosphere.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.icu.util.ULocale;
import android.os.LocaleList;
import java.util.Locale;
import org.meteosphere.R;

/**
 * The app's language: the one Android picked the app's texts in, by the language of the app on Android 13+ (the
 * system's App languages, offered from res/xml/locales_config.xml) or else by the phone's languages; English when
 * none of them is the app's. Dates, numbers and the system's own texts follow it too: a screen or the application
 * whose locale is in another language, as on a phone in a language the app doesn't have, gets the app's.
 */
public final class AppLanguage {

    private AppLanguage() {
    }

    /** The language of the texts, as R.array.language_codes has it: "ru", "zh" for Simplified Chinese, "zh-Hant" */
    public static String of(Context context) {
        return context.getString(R.string.app_language);
    }

    /** A screen's context in the app's language: the same context when its locale already is; for attachBaseContext */
    public static Context wrap(Context base) {
        Configuration configuration = base.getResources().getConfiguration();
        Locale locale = locale(configuration.getLocales(), of(base));
        if (locale.equals(configuration.getLocales().get(0))) {
            return base;
        }
        Configuration overridden = new Configuration(configuration);
        overridden.setLocale(locale);
        return base.createConfigurationContext(overridden);
    }

    /** The application's context and the default locale in the app's language, for the widgets, notifications and work */
    public static void apply(Context appContext) {
        Resources resources = appContext.getResources();
        Configuration configuration = resources.getConfiguration();
        Locale locale = locale(configuration.getLocales(), of(appContext));
        Locale.setDefault(locale);
        if (!locale.equals(configuration.getLocales().get(0))) {
            Configuration overridden = new Configuration(configuration);
            overridden.setLocale(locale);
            resources.updateConfiguration(overridden, resources.getDisplayMetrics());
        }
    }

    // The first of the locales in the language and its script, for its region's formats; else the language's own,
    // Chinese as China or Taiwan writes it, where the libraries keep their Chinese
    private static Locale locale(LocaleList locales, String language) {
        Locale wanted = Locale.forLanguageTag(language);
        String script = script(wanted);
        for (int i = 0; i < locales.size(); i++) {
            Locale locale = locales.get(i);
            if (locale.getLanguage().equals(wanted.getLanguage()) && script(locale).equals(script)) {
                return locale;
            }
        }
        switch (language) {
            case "zh":
                return Locale.SIMPLIFIED_CHINESE;
            case "zh-Hant":
                return Locale.TRADITIONAL_CHINESE;
            default:
                return wanted;
        }
    }

    // The script a locale is written in, the likely one when it names none: Hant for zh-TW, Hans for zh, Latn for nl
    private static String script(Locale locale) {
        return ULocale.addLikelySubtags(ULocale.forLocale(locale)).getScript();
    }
}
