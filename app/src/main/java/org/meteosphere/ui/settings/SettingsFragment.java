package org.meteosphere.ui.settings;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.meteosphere.R;
import org.meteosphere.addon.AddOns;
import org.meteosphere.background.BackgroundTaskWorker;
import org.meteosphere.background.DailyForecastReceiver;
import org.meteosphere.background.EarthquakeAlertWorker;
import org.meteosphere.background.EclipseAlertWorker;
import org.meteosphere.background.MoonPhaseAlertWorker;
import org.meteosphere.data.WeatherProviders;
import org.meteosphere.data.local.ForecaWeatherProvider;
import org.meteosphere.location.PositionService;
import org.meteosphere.settings.AppLanguage;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.settings.ThemeMode;
import org.meteosphere.ui.common.BackgroundWork;
import org.meteosphere.ui.sky.SkyView;
import org.meteosphere.util.Units;
import org.meteosphere.util.WindText;

/**
 * The settings' main list: the rows in their groups, the screens they open among them, the rows shown only where they
 * work, and the Foreca key asked for when the device itself would ask Foreca without one
 */
public final class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = "SettingsFragment";

    private static final String STATE_ASKED = "asked";

    private static final int KEY_CHECK_TIMEOUT_MS = 15000;
    private static final String FORECA_SITE = "developer.foreca.com";
    private static final String MASK = "••••";

    // What the notifications are asked for, to be turned back when they are refused
    private static final int ASKED_NONE = 0;
    private static final int ASKED_DAILY_FORECAST = 1;
    private static final int ASKED_ALERTS = 2;

    // The units as the units screen lists them, and their names
    private static final int[] UNIT_KEYS = {R.string.key_temperature_unit, R.string.key_wind_unit, R.string.key_pressure_unit,
            R.string.key_distance_unit, R.string.key_precipitation_unit};
    private static final int[] UNIT_NAMES = {R.array.temperature_units, R.array.wind_units, R.array.pressure_units,
            R.array.distance_units, R.array.precipitation_units};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // The key's check, dropped with its dialog and with the view
    private final BackgroundWork keyCheck = new BackgroundWork(this.executor);

    private SharedPreferences preferences;
    private Preference units;
    private Preference strongWind;
    private ListPreference dataProvider;
    private ListPreference weatherSource;
    private Preference forecaKey;
    private ListPreference dailyForecast;
    // The provider's or the source's choice waiting for a Foreca key, and the key's dialog while it is open
    private ListPreference waitingList;
    private String waitingValue;
    private AlertDialog keyDialog;
    private int asked = ASKED_NONE;

    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), this::onNotificationsAnswered);

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        if (bundle != null) {
            this.asked = bundle.getInt(STATE_ASKED, ASKED_NONE);
        }
        setPreferencesFromResource(R.xml.settings, rootKey);
        this.preferences = getPreferenceManager().getSharedPreferences();
        setUpLanguage();
        this.units = preference(R.string.key_units_screen);
        setUpUpdatePeriod();
        setUpDataSource();
        setUpClock();
        afterSaving(preference(R.string.key_theme), ThemeMode::apply);
        this.strongWind = preference(R.string.key_wind_when_strong);
        preference(R.string.key_live_sky).setVisible(SkyView.supported());
        setUpLocation();
        setUpDailyForecast();
        setUpAlerts();
        setUpAddOns();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        setDivider(null);
        // The logo gives the room above
        SettingsSegments.setListPadding(getListView(), false);
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        return new SegmentedPreferenceAdapter(preferenceScreen);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (!SettingsChoiceMenu.show(this, preference)) {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    // Back from the units screen, the units may be others
    @Override
    public void onResume() {
        requireActivity().setTitle(R.string.settings);
        super.onResume();
        showUnits();
        showStrongWind();
        showClock();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(STATE_ASKED, this.asked);
    }

    @Override
    public void onDestroyView() {
        this.keyCheck.drop();
        if (this.keyDialog != null) {
            this.keyDialog.dismiss();
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        this.executor.shutdown();
        super.onDestroy();
    }

    private <T extends Preference> T preference(int key) {
        return findPreference(getString(key));
    }

    private void setChoice(int key, String value) {
        ListPreference list = preference(key);
        list.setValue(value);
    }

    /** The choice saved before the action, which reads it back; the list then doesn't save it again */
    public static void afterSaving(ListPreference list, Consumer<String> action) {
        list.setOnPreferenceChangeListener((preference, value) -> {
            list.setValue((String) value);
            action.accept((String) value);
            return false;
        });
    }

    // --- General ---

    // The app's language is the system's to choose, from Android 13
    private void setUpLanguage() {
        Preference language = preference(R.string.key_names_language);
        if (Build.VERSION.SDK_INT < 33) {
            language.setVisible(false);
            return;
        }
        Resources resources = getResources();
        language.setSummary(languageName(AppLanguage.of(requireContext()), resources.getStringArray(R.array.language_codes),
                resources.getStringArray(R.array.language_names)));
        language.setOnPreferenceClickListener(row -> {
            openLanguageSettings();
            return true;
        });
    }

    // The app's details where the phone has no page of the app's language
    @RequiresApi(33)
    private void openLanguageSettings() {
        Uri app = Uri.fromParts("package", requireContext().getPackageName(), null);
        try {
            startActivity(new Intent(Settings.ACTION_APP_LOCALE_SETTINGS, app));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No page of the app's language: " + e);
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app));
            } catch (ActivityNotFoundException details) {
                Log.w(TAG, "No page of the app's details: " + details);
            }
        }
    }

    // The units' settings, as the units screen may have changed them
    private void showUnits() {
        AppSettings settings = AppSettings.getInstance();
        this.units.setSummary(unitsSummary(key -> settings.unitSetting(getString(key)), getResources()::getStringArray));
    }

    // The strong wind's speed in the wind's unit
    private void showStrongWind() {
        int speed = getResources().getInteger(R.integer.strong_wind_mps);
        int unit = unitIndex(AppSettings.getInstance().unitSetting(getString(R.string.key_wind_unit)),
                getResources().getStringArray(R.array.wind_units).length);
        String speedText = String.valueOf(Units.windSpeed(speed, unit));
        this.strongWind.setSummary(getString(R.string.pref_only_strong_wind_summary, speedText,
                WindText.unitName(requireContext(), speed, unit)));
    }

    // The app language's own clock until the user switches it: the row doesn't save the one it shows, only a switch
    private void setUpClock() {
        preference(R.string.key_clock_24).setOnPreferenceChangeListener((row, value) -> {
            this.preferences.edit().putBoolean(row.getKey(), (Boolean) value).apply();
            return true;
        });
    }

    // As each time the screen shows: a row not saved keeps the state it had when the screen is made again, as for a new
    // language, whose clock may be another
    private void showClock() {
        SwitchPreferenceCompat clock = preference(R.string.key_clock_24);
        clock.setChecked(AppSettings.getInstance().clock24Setting());
    }

    // The new period holds at once rather than from the app's next start
    private void setUpUpdatePeriod() {
        afterSaving(preference(R.string.key_refresh_period), value -> {
            Context app = requireContext().getApplicationContext();
            AppSettings.init(app);
            BackgroundTaskWorker.schedulePeriodicRefresh(app);
        });
    }

    // --- The data source and the Foreca key ---

    private void setUpDataSource() {
        this.dataProvider = preference(R.string.key_data_provider);
        this.dataProvider.setVisible(WeatherProviders.hasServer());
        this.dataProvider.setOnPreferenceChangeListener((preference, value) -> choose(this.dataProvider, (String) value));
        this.weatherSource = preference(R.string.key_weather_source);
        this.weatherSource.setOnPreferenceChangeListener((preference, value) -> choose(this.weatherSource, (String) value));
        this.forecaKey = preference(R.string.key_foreca_key);
        this.forecaKey.setOnPreferenceClickListener(row -> {
            showKeyDialog();
            return true;
        });
        showForecaKey();
    }

    // Made at once, unless the device itself would then ask Foreca without a key: then once one is given, not at all
    // when the key's dialog is cancelled. The list doesn't make the choice itself
    private boolean choose(ListPreference list, String value) {
        String provider = list == this.dataProvider ? value : this.dataProvider.getValue();
        String source = list == this.weatherSource ? value : this.weatherSource.getValue();
        if (WeatherProviders.asksForeca(provider, source) && AppSettings.forecaToken(givenKey()).isEmpty()) {
            this.waitingList = list;
            this.waitingValue = value;
            showKeyDialog();
        } else {
            list.setValue(value);
            showForecaKey();
        }
        return false;
    }

    // The key given here, empty for none
    private String givenKey() {
        return this.preferences.getString(getString(R.string.key_foreca_key), "");
    }

    // Only while the device itself asks Foreca, with the key it asks with
    private void showForecaKey() {
        this.forecaKey.setVisible(WeatherProviders.asksForeca(this.dataProvider.getValue(), this.weatherSource.getValue()));
        this.forecaKey.setSummary(forecaKeySummary(AppSettings.forecaToken(givenKey())));
    }

    private void showKeyDialog() {
        if (this.keyDialog != null) {
            return;
        }
        Context context = requireContext();
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_foreca_key, null);
        TextView message = content.findViewById(R.id.tvForecaKeyMessage);
        TextInputLayout field = content.findViewById(R.id.tilForecaKey);
        EditText input = content.findViewById(R.id.etForecaKey);
        String text = getString(R.string.foreca_key_message, FORECA_SITE);
        SpannableString linked = new SpannableString(text);
        int start = text.indexOf(FORECA_SITE);
        if (start >= 0) {
            linked.setSpan(new URLSpan("https://" + FORECA_SITE), start, start + FORECA_SITE.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        message.setText(linked);
        message.setMovementMethod(LinkMovementMethod.getInstance());
        // The key given here, never the build's own: saved, it would outlive a new build's key
        input.setText(givenKey());
        input.setSelection(input.length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_foreca_key)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        // Gone, cancelled or with the key taken, the check under way gives nothing and the choice waits no longer
        dialog.setOnDismissListener(gone -> {
            this.keyCheck.drop();
            this.waitingList = null;
            this.waitingValue = null;
            if (this.keyDialog == gone) {
                this.keyDialog = null;
            }
        });
        this.keyDialog = dialog;
        dialog.show();

        // OK closes the dialog only once the key is checked
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(button -> checkKey(dialog, field, ok, input.getText().toString()));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            // Another key: the check of the one before is of no use
            @Override
            public void afterTextChanged(Editable s) {
                SettingsFragment.this.keyCheck.drop();
                field.setHelperText(null);
                field.setError(null);
                ok.setEnabled(true);
            }
        });
        // The keyboard's Done and a hardware Enter, on its key down, do what OK does
        input.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId != EditorInfo.IME_ACTION_DONE && !enter) {
                return false;
            }
            if ((!enter || event.getAction() == KeyEvent.ACTION_DOWN) && ok.isEnabled()) {
                ok.performClick();
            }
            return true;
        });
    }

    // Asked of Foreca off the main thread: an empty key needs no check
    private void checkKey(AlertDialog dialog, TextInputLayout field, Button ok, String typed) {
        String key = typed.trim();
        if (key.isEmpty()) {
            giveKey(key);
            dialog.dismiss();
            return;
        }
        ok.setEnabled(false);
        field.setError(null);
        field.setHelperText(getString(R.string.foreca_key_checking));
        this.keyCheck.runLatest(() -> ForecaWeatherProvider.checkKey(key, KEY_CHECK_TIMEOUT_MS), check -> {
            ok.setEnabled(true);
            if (check == ForecaWeatherProvider.KeyCheck.ACCEPTED) {
                giveKey(key);
                dialog.dismiss();
                return;
            }
            field.setHelperText(null);
            field.setError(getString(check == ForecaWeatherProvider.KeyCheck.REFUSED ? R.string.foreca_key_refused
                    : check == ForecaWeatherProvider.KeyCheck.BUSY ? R.string.foreca_key_busy : R.string.foreca_key_unchecked));
        });
    }

    // With a key now the waiting choice is made; without one, the device no longer asks Foreca
    private void giveKey(String key) {
        this.preferences.edit().putString(getString(R.string.key_foreca_key), key).apply();
        if (!AppSettings.forecaToken(key).isEmpty()) {
            if (this.waitingList != null) {
                this.waitingList.setValue(this.waitingValue);
            }
        } else if (WeatherProviders.asksForeca(this.dataProvider.getValue(), this.weatherSource.getValue())) {
            this.weatherSource.setValue(WeatherProviders.OPEN_METEO);
        }
        this.waitingList = null;
        this.waitingValue = null;
        showForecaKey();
    }

    // --- The location ---

    private void setUpLocation() {
        preference(R.string.key_search_by_gps).setOnPreferenceChangeListener((preference, on) -> {
            if (!(Boolean) on) {
                PositionService.stop(requireContext());
            }
            return true;
        });
        // The daily forecast offers the current location only while it is on
        preference(R.string.key_show_current).setOnPreferenceChangeListener((preference, on) -> {
            offerDailyForecastPlaces((Boolean) on);
            return true;
        });
    }

    // --- The notifications ---

    private void setUpDailyForecast() {
        this.dailyForecast = preference(R.string.key_daily_forecast);
        // As saved: AppSettings' own may be older
        offerDailyForecastPlaces(this.preferences.getBoolean(getString(R.string.key_show_current), true));
        afterSaving(this.dailyForecast, value -> {
            DailyForecastReceiver.schedule(requireContext());
            if (!"0".equals(value)) {
                askNotifications(ASKED_DAILY_FORECAST);
            }
        });
    }

    // A place no longer offered turns the forecast off
    private void offerDailyForecastPlaces(boolean currentLocation) {
        if (!offerPlaces(requireContext(), this.dailyForecast, R.string.pref_daily_forecast_off, currentLocation)) {
            this.dailyForecast.setValue("0");
            DailyForecastReceiver.schedule(requireContext());
        }
    }

    private void setUpAlerts() {
        for (int key : new int[]{R.string.key_earthquake_alerts_world, R.string.key_earthquake_alerts_nearby,
                R.string.key_moon_phase_alerts, R.string.key_eclipse_alerts}) {
            afterSaving(preference(key), value -> {
                Context context = requireContext();
                scheduleAlerts(context);
                if (EarthquakeAlertWorker.isEnabled(context) || MoonPhaseAlertWorker.isEnabled(context) || EclipseAlertWorker.isEnabled(context)) {
                    askNotifications(ASKED_ALERTS);
                }
            });
        }
    }

    private static void scheduleAlerts(Context context) {
        EarthquakeAlertWorker.schedule(context);
        MoonPhaseAlertWorker.schedule(context, true);
        EclipseAlertWorker.schedule(context, true);
    }

    // Asked for where Android asks for them; with them, only told of when the app's notifications are turned off
    private void askNotifications(int asked) {
        Context context = requireContext();
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            this.asked = asked;
            this.notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Toast.makeText(context, R.string.earthquake_alerts_denied, Toast.LENGTH_LONG).show();
        }
    }

    // Refused, what they were asked for is turned back off
    private void onNotificationsAnswered(boolean granted) {
        int asked = this.asked;
        this.asked = ASKED_NONE;
        if (granted) {
            return;
        }
        Context context = requireContext();
        if (asked == ASKED_DAILY_FORECAST) {
            this.dailyForecast.setValue("0");
            DailyForecastReceiver.schedule(context);
        } else if (asked == ASKED_ALERTS) {
            setChoice(R.string.key_earthquake_alerts_world, "0");
            setChoice(R.string.key_earthquake_alerts_nearby, "0");
            setChoice(R.string.key_moon_phase_alerts, "off");
            setChoice(R.string.key_eclipse_alerts, EclipseAlertWorker.MODE_OFF);
            scheduleAlerts(context);
        }
        Toast.makeText(context, R.string.earthquake_alerts_denied, Toast.LENGTH_LONG).show();
    }

    // --- Experimental ---

    // The add-ons' rows; none in the public build
    private void setUpAddOns() {
        PreferenceCategory category = preference(R.string.key_category_experimental);
        AddOns.addSettings(this, category);
        category.setVisible(category.getPreferenceCount() > 0);
    }

    // --- The places offered ---

    /**
     * Offers none first, the current location while asked, then the favourites stored, by their names; false when the
     * place chosen is no longer among them
     */
    public static boolean offerPlaces(Context context, ListPreference list, int none, boolean currentLocation) {
        AppSettings settings = AppSettings.getInstance();
        List<CharSequence> names = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        names.add(context.getString(none));
        values.add("0");
        if (currentLocation) {
            names.add(context.getString(R.string.current_location));
            values.add("-1");
        }
        for (int locationId : settings.getFavorites()) {
            String name = locationId > 0 ? settings.getLocationDisplayName(locationId) : null;
            if (name != null) {
                names.add(name);
                values.add(String.valueOf(locationId));
            }
        }
        list.setEntries(names.toArray(new CharSequence[0]));
        list.setEntryValues(values.toArray(new CharSequence[0]));
        return list.findIndexOfValue(list.getValue()) >= 0;
    }

    // --- The decisions ---

    /** The language by its own name, as the list of the app's languages names it; the code itself if it isn't there */
    static String languageName(String code, String[] codes, String[] names) {
        for (int i = 0; i < codes.length && i < names.length; i++) {
            if (codes[i].equals(code)) {
                return names[i];
            }
        }
        return code;
    }

    /** The saved units by their names, in the units screen's order, of the saved values and the names by their ids */
    static String unitsSummary(IntFunction<String> saved, IntFunction<String[]> names) {
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            String[] unitNames = names.apply(UNIT_NAMES[i]);
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(unitNames[unitIndex(saved.apply(UNIT_KEYS[i]), unitNames.length)]);
        }
        return summary.toString();
    }

    /** The unit's index as saved; the first for a value that isn't a number or is out of range */
    static int unitIndex(String saved, int count) {
        try {
            int index = Integer.parseInt(saved);
            return index >= 0 && index < count ? index : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The key the device would ask Foreca with, hidden but for its last four characters when it is longer than 8; null for none */
    static String forecaKeySummary(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return key.length() > 8 ? MASK + key.substring(key.length() - 4) : MASK;
    }
}
