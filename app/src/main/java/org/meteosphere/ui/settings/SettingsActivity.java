package org.meteosphere.ui.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.meteosphere.R;
import org.meteosphere.background.NotificationUpdater;
import org.meteosphere.data.WeatherProviders;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppLanguage;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.BackPressHandler;
import org.meteosphere.ui.common.EdgeToEdgeUtils;
import org.meteosphere.ui.common.ListRoom;
import org.meteosphere.ui.common.SearchBackCallback;
import org.meteosphere.ui.locations.PlaceSearchFragment;
import org.meteosphere.util.AppUtils;

/**
 * The settings: the main list and the screens its rows open over it, under a large title that shrinks into the bar as
 * a list scrolls up and fades through as the screens change. The bar's tick applies a screen, Back leaves it as saved.
 * Closing, the settings tell the main screen whether a setting it shows by has changed, and whether the weather would
 * now come from elsewhere
 */
public final class SettingsActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        NotificationSettingsFragment.OnNotificationScreenListener, PlaceSearchFragment.OnPlacePickedListener,
        MenuItemCompat.OnActionExpandListener, SearchView.OnQueryTextListener {

    private static final String TAG = "SettingsActivity";

    private static final String EXTRA_DATA_PROVIDER_CHANGED = "data_provider_changed";
    private static final String STATE_NOTE = "note";
    private static final String STATE_BARS_BELOW = "bars_below";
    private static final String TAG_SEARCH = "location_search";

    // The note's first values, which tell where the weather comes from; the others follow them
    static final int NOTE_PROVIDER = 0;
    static final int NOTE_SOURCE = 1;
    static final int NOTE_FORECA_KEY = 2;
    // The noted lists: the five units
    private static final int[] NOTED_LISTS = {R.string.key_temperature_unit, R.string.key_wind_unit, R.string.key_pressure_unit,
            R.string.key_distance_unit, R.string.key_precipitation_unit};

    private AppBarLayout appBar;
    private CollapsingToolbarLayout titleBar;
    private SearchBackCallback searchBackCallback;
    private MenuItem applyItem;
    private MenuItem searchItem;
    // How far the bar is scrolled up, 0 or less
    private int barOffset;
    // Whether the bars of the screens under the shown one were expanded as they were left, the nearest last
    private final ArrayList<Boolean> barsBelow = new ArrayList<>();
    // The room the lists keep at their end, for the navigation bar or the keyboard over them
    private int listRoom;
    // The titles' colour, faded through as the screens change
    private int titleColor;
    private ValueAnimator titleChange;
    // Whether the change under way has given the bar its new title
    private boolean titleSwitched;
    // The values noted as the settings opened, to tell the main screen of a change
    private ArrayList<String> note;

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_settings);
        this.appBar = findViewById(R.id.appBar);
        this.titleBar = findViewById(R.id.collapsingToolbar);
        Toolbar toolbar = findViewById(R.id.tbSettings);
        new BackPressHandler(this) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        };
        setSupportActionBar(toolbar);
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        // The large title is the only one
        actionBar.setDisplayShowTitleEnabled(false);
        this.searchBackCallback = new SearchBackCallback(this, toolbar);
        this.titleColor = MaterialColors.getColor(this.titleBar, com.google.android.material.R.attr.colorOnSurface);
        this.appBar.addOnOffsetChangedListener((bar, offset) -> this.barOffset = offset);

        // Every screen's lists get the room as their view is made, the shown one's again as it changes
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager manager, @NonNull Fragment fragment, @NonNull View view, Bundle saved) {
                ListRoom.atEnd(view, SettingsActivity.this.listRoom);
            }
        }, false);
        EdgeToEdgeUtils.applyToolbarInsetsUnderKeyboard(this, this.appBar, true, (navigationBar, keyboard) -> {
            this.listRoom = Math.max(navigationBar, keyboard);
            Fragment shown = shownScreen();
            if (shown != null && shown.getView() != null) {
                ListRoom.atEnd(shown.getView(), this.listRoom);
            }
        });

        if (bundle == null) {
            this.note = takeNote();
            getSupportFragmentManager().beginTransaction().replace(R.id.flSettingsScreen, new SettingsFragment()).commit();
            setTitle(R.string.settings);
        } else {
            // The screens came back by themselves, their bars with them
            this.note = bundle.getStringArrayList(STATE_NOTE);
            boolean[] bars = bundle.getBooleanArray(STATE_BARS_BELOW);
            if (bars != null) {
                for (boolean expanded : bars) {
                    this.barsBelow.add(expanded);
                }
            }
            setTitle(titleOf(shownScreen()));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putStringArrayList(STATE_NOTE, this.note);
        boolean[] bars = new boolean[this.barsBelow.size()];
        for (int i = 0; i < bars.length; i++) {
            bars[i] = this.barsBelow.get(i);
        }
        state.putBooleanArray(STATE_BARS_BELOW, bars);
    }

    // The settings or the units of the notification may have changed
    @Override
    protected void onStop() {
        super.onStop();
        NotificationUpdater.update(this);
    }

    @Override
    public void finish() {
        ArrayList<String> now = takeNote();
        if (this.note != null && changed(this.note, now)) {
            setResult(RESULT_OK, getIntent().putExtra(EXTRA_DATA_PROVIDER_CHANGED, isDataProviderChanged(this.note, now)));
        } else {
            setResult(RESULT_CANCELED);
        }
        super.finish();
    }

    // --- The bar ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        this.applyItem = menu.findItem(R.id.itemApply);
        this.searchItem = menu.findItem(R.id.action_search);
        AppUtils.setUpSearchField(this, this.searchItem, this, this);
        // A search that was open came back with the screen: its field in the bar again
        if (shownScreen() instanceof PlaceSearchFragment) {
            this.searchItem.setVisible(true);
            this.searchItem.expandActionView();
        }
        showApply();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            goBack();
            return true;
        }
        if (item.getItemId() == R.id.itemApply) {
            applyScreen();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // The tick on the screens with something to apply: not the main list, nor the statistics, nor the search
    private void showApply() {
        if (this.applyItem == null) {
            return;
        }
        Fragment shown = shownScreen();
        this.applyItem.setVisible(shown instanceof UnitsFragment || shown instanceof ParametersFragment
                || shown instanceof MainTabsPreferenceFragment || shown instanceof NotificationSettingsFragment);
    }

    // Scrolled up by less than half its range
    private boolean isBarExpanded() {
        return -this.barOffset * 2 < this.appBar.getTotalScrollRange();
    }

    // --- The screens ---

    private Fragment shownScreen() {
        return getSupportFragmentManager().findFragmentById(R.id.flSettingsScreen);
    }

    // The place search keeps the notification's, unseen under its field
    private static int titleOf(Fragment screen) {
        if (screen instanceof UnitsFragment) {
            return R.string.pref_units;
        } else if (screen instanceof ParametersFragment) {
            return R.string.pref_weather_parameters;
        } else if (screen instanceof MainTabsPreferenceFragment) {
            return R.string.pref_main_tabs;
        } else if (screen instanceof NotificationSettingsFragment || screen instanceof PlaceSearchFragment) {
            return R.string.pref_notification;
        } else if (screen instanceof StatisticsFragment) {
            return R.string.pref_statistics;
        }
        return R.string.settings;
    }

    // A row of the main list opens its screen, kept on the back stack under the row's key. A second tap on the list
    // going away opens nothing more
    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference preference) {
        if (caller != shownScreen() || preference.getFragment() == null) {
            return true;
        }
        Fragment screen = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), preference.getFragment());
        this.barsBelow.add(isBarExpanded());
        changeTitle(true);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.settings_open_enter, R.animator.settings_open_exit, R.animator.settings_close_enter,
                        R.animator.settings_close_exit)
                .replace(R.id.flSettingsScreen, screen, preference.getKey())
                .addToBackStack(preference.getKey())
                .commit();
        // At once, so that the lists start moving with the title
        getSupportFragmentManager().executePendingTransactions();
        setTitle(titleOf(screen));
        showApply();
        return true;
    }

    // Back to the screen below, its title and its bar as they were when it was left
    private void closeScreen() {
        if (this.applyItem != null) {
            this.applyItem.setVisible(false);
        }
        changeTitle(popBarBelow());
        getSupportFragmentManager().popBackStackImmediate();
        setTitle(titleOf(shownScreen()));
        showApply();
    }

    private boolean popBarBelow() {
        return this.barsBelow.isEmpty() || this.barsBelow.remove(this.barsBelow.size() - 1);
    }

    // Back and Up: the search closes; a screen over the main list closes, nothing of it applied; the main list finishes
    private void goBack() {
        if (shownScreen() instanceof PlaceSearchFragment) {
            closeSearch();
        } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            closeScreen();
        } else {
            finish();
        }
    }

    // The tick: the screen shown applied, then closed as by Back
    private void applyScreen() {
        Fragment shown = shownScreen();
        if (shown instanceof NotificationSettingsFragment) {
            NotificationSettingsFragment notification = (NotificationSettingsFragment) shown;
            if (!notification.checkPlace(true)) {
                return;
            }
            notification.apply();
        } else if (shown instanceof UnitsFragment) {
            ((UnitsFragment) shown).apply();
        } else if (shown instanceof ParametersFragment) {
            ((ParametersFragment) shown).apply();
        } else if (shown instanceof MainTabsPreferenceFragment) {
            ((MainTabsPreferenceFragment) shown).apply();
        } else {
            return;
        }
        closeScreen();
    }

    // --- The title ---

    // Set while no change runs, it goes to the large title at once; while one runs, once the title before is out of sight
    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        if (this.titleBar != null && (this.titleChange == null || this.titleSwitched)) {
            this.titleBar.setTitle(title);
        }
    }

    /**
     * The title fades out, then, while neither list is seen, the bar takes the activity's title and its height, expanded
     * or not, and the title fades in, in step with the screens' own fades. A change under way is ended first
     */
    private void changeTitle(boolean expanded) {
        if (this.titleChange != null) {
            this.titleChange.end();
        }
        int fadeOut = getResources().getInteger(R.integer.settings_screen_fade_out);
        int fadeIn = getResources().getInteger(R.integer.settings_screen_fade_in);
        float switchAt = fadeOut / (float) (fadeOut + fadeIn);
        ValueAnimator change = ValueAnimator.ofFloat(0, 1);
        change.setDuration(fadeOut + fadeIn);
        change.setInterpolator(new LinearInterpolator());
        change.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            if (fraction < switchAt) {
                setTitleAlpha(1 - fraction / switchAt);
            } else {
                switchTitle(expanded);
                setTitleAlpha((fraction - switchAt) / (1 - switchAt));
            }
        });
        change.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                switchTitle(expanded);
                setTitleAlpha(1);
                if (SettingsActivity.this.titleChange == animation) {
                    SettingsActivity.this.titleChange = null;
                }
            }
        });
        this.titleSwitched = false;
        this.titleChange = change;
        change.start();
    }

    private void switchTitle(boolean expanded) {
        if (this.titleSwitched) {
            return;
        }
        this.titleSwitched = true;
        this.titleBar.setTitle(getTitle());
        this.appBar.setExpanded(expanded, false);
    }

    // On the expanded title and the collapsed one alike
    private void setTitleAlpha(float alpha) {
        int color = ColorUtils.setAlphaComponent(this.titleColor, Math.round(Color.alpha(this.titleColor) * alpha));
        this.titleBar.setExpandedTitleColor(color);
        this.titleBar.setCollapsedTitleTextColor(color);
    }

    // --- The notification's place search ---

    // Its field in the bar in place of the tick, the large title folded away, the search over the notification's screen
    @Override
    public void onPickPlace() {
        if (this.searchItem == null || shownScreen() instanceof PlaceSearchFragment) {
            return;
        }
        this.barsBelow.add(isBarExpanded());
        this.searchItem.setVisible(true);
        this.searchItem.expandActionView();
        this.applyItem.setVisible(false);
        this.appBar.setExpanded(false, true);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.flSettingsScreen, PlaceSearchFragment.newInstance(PlaceSearchFragment.MODE_PICK), TAG_SEARCH)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(TAG_SEARCH)
                .commit();
        getSupportFragmentManager().executePendingTransactions();
    }

    @Override
    public void onAnswer(boolean apply) {
        if (apply) {
            applyScreen();
        } else {
            goBack();
        }
    }

    @Override
    public void onPlacePicked(Place location) {
        Fragment notification = getSupportFragmentManager().findFragmentByTag(getString(R.string.key_notification_screen));
        if (notification instanceof NotificationSettingsFragment) {
            ((NotificationSettingsFragment) notification).setNotificationLocation(location);
        } else {
            Log.w(TAG, "The place picked has no notification's screen to go to");
        }
        closeSearch();
    }

    // Collapsing the field closes the search, as Back does
    private void closeSearch() {
        if (this.searchItem != null && this.searchItem.isActionViewExpanded()) {
            this.searchItem.collapseActionView();
        } else {
            removeSearch();
        }
    }

    // The notification's screen again with the tick, the bar at once as it was before the search. A search still there
    // once the screen's state is saved comes back with it
    private void removeSearch() {
        if (shownScreen() instanceof PlaceSearchFragment && !getSupportFragmentManager().isStateSaved()) {
            getSupportFragmentManager().popBackStackImmediate();
            this.appBar.setExpanded(popBarBelow(), false);
        }
        if (this.searchItem != null) {
            this.searchItem.setVisible(false);
        }
        showApply();
    }

    // Back collapses the search, the keyboard going first
    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        this.searchBackCallback.setEnabled(true);
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        this.searchBackCallback.setEnabled(false);
        removeSearch();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        Fragment search = getSupportFragmentManager().findFragmentByTag(TAG_SEARCH);
        if (search instanceof PlaceSearchFragment) {
            ((PlaceSearchFragment) search).search(query);
        }
        return true;
    }

    // The places are looked for as the text changes: the field only hides the keyboard
    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    // --- The result ---

    /**
     * The values the main screen is told of a change in, all as saved (the units and the clock as the language has
     * them while unsaved), as AppSettings' fields may be read again meanwhile: where the weather comes from first, then
     * the units, the switches, the tabs, the city page's tiles and the notification. Those that take effect by
     * themselves aren't among them, nor the add-ons' settings
     */
    private ArrayList<String> takeNote() {
        SharedPreferences saved = PreferenceManager.getDefaultSharedPreferences(this);
        AppSettings settings = AppSettings.getInstance();
        ArrayList<String> note = new ArrayList<>();
        note.add(saved.getString(getString(R.string.key_data_provider), WeatherProviders.DEFAULT_DATA_PROVIDER));
        note.add(saved.getString(getString(R.string.key_weather_source), WeatherProviders.DEFAULT_WEATHER_SOURCE));
        note.add(saved.getString(getString(R.string.key_foreca_key), ""));
        for (int key : NOTED_LISTS) {
            note.add(settings.unitSetting(getString(key)));
        }
        note.add(String.valueOf(settings.clock24Setting()));
        note.add(String.valueOf(saved.getBoolean(getString(R.string.key_wind_when_strong), false)));
        note.add(String.valueOf(saved.getBoolean(getString(R.string.key_search_by_gps), true)));
        note.add(String.valueOf(saved.getBoolean(getString(R.string.key_show_current), true)));
        note.add(AppSettings.listText(settings.getSortedMainTabs()));
        note.add(AppSettings.listText(settings.getMainTabs()));
        note.add(AppSettings.listText(settings.getSortedDetailWidgets()));
        note.add(AppSettings.listText(settings.getShownDetailWidgets()));
        note.add(String.valueOf(settings.isStatusBarNotificationOn()));
        note.add(String.valueOf(settings.getStatusBarNotificationPlaceId()));
        note.add(String.valueOf(settings.isStatusBarNotificationForecastShown()));
        note.add(String.valueOf(settings.getStatusBarNotificationLook()));
        return note;
    }

    /**
     * Whether any value noted differs, the provider as the one that works for it: the default kept before any was saved
     * is the same one as the main list's default saved since
     */
    static boolean changed(List<String> before, List<String> now) {
        if (before.size() != now.size()) {
            return true;
        }
        for (int i = 0; i < before.size(); i++) {
            String was = before.get(i);
            String is = now.get(i);
            if (i == NOTE_PROVIDER) {
                was = WeatherProviders.dataProvider(was);
                is = WeatherProviders.dataProvider(is);
            }
            if (!Objects.equals(was, is)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the weather would now come from elsewhere: another provider that works, another source, or, while the
     * device itself asks Foreca, another key it asks with
     */
    static boolean isDataProviderChanged(List<String> before, List<String> now) {
        String provider = now.get(NOTE_PROVIDER);
        String source = now.get(NOTE_SOURCE);
        if (!WeatherProviders.dataProvider(before.get(NOTE_PROVIDER)).equals(WeatherProviders.dataProvider(provider))
                || !Objects.equals(before.get(NOTE_SOURCE), source)) {
            return true;
        }
        return WeatherProviders.asksForeca(provider, source)
                && !AppSettings.forecaToken(before.get(NOTE_FORECA_KEY)).equals(AppSettings.forecaToken(now.get(NOTE_FORECA_KEY)));
    }
}
