package org.meteosphere.ui.details;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import org.json.JSONException;
import org.json.JSONObject;
import org.meteosphere.R;
import org.meteosphere.background.NotificationUpdater;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.background.WidgetUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppLanguage;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.astro.AstroActivity;
import org.meteosphere.ui.astro.SunNowActivity;
import org.meteosphere.ui.common.BackPressHandler;
import org.meteosphere.ui.glass.GlassMenu;
import org.meteosphere.ui.locations.RenameActivity;
import org.meteosphere.ui.locations.Favorites;
import org.meteosphere.ui.main.MainActivity;
import org.meteosphere.ui.quakes.EarthquakesActivity;
import org.meteosphere.ui.settings.SettingsActivity;
import org.meteosphere.ui.widgets.settings.WidgetConfig;
import org.meteosphere.util.AppUtils;

/**
 * The city page: the favourites' pages, the current location's first, or a place found or nearby alone, opened by the
 * main screen's tabs, by its restore of the page the app was left on, or by a widget. The pages are DetailsScreen's;
 * this opens them at their place, answers their menu, keeps them fresh while they are in front and tells the main
 * screen how they were left
 */
public final class DetailsActivity extends AppCompatActivity implements DetailsScreen.Host {

    private static final String TAG = "DetailsActivity";
    /** A widget's tap: its place's page, the widget's id in WidgetUpdater.EXTRA_WIDGET */
    public static final String ACTION_OPEN_WIDGET_PLACE = "org.meteosphere.action.OPEN_WIDGET_PLACE";
    // The main screen's: the favourites' pages, the current location among them, or one place alone
    static final String ACTION_OPEN_FAVORITES = "org.meteosphere.action.OPEN_FAVORITES";
    static final String ACTION_OPEN_PLACE = "org.meteosphere.action.OPEN_PLACE";
    // The place opened, as its JSON
    private static final String EXTRA_PLACE = Place.class.getCanonicalName();
    // The rename screen's and the weather broadcasts' place, and the settings' answer
    private static final String EXTRA_PLACE_ID = "place_id";
    private static final String EXTRA_DATA_PROVIDER_CHANGED = "data_provider_changed";
    // What the main screen is told: how the page was left, and whether the place shown alone became a favourite
    private static final String EXTRA_BACK_TYPE = "left_by";
    private static final String EXTRA_ADDED_TO_FAVORITES = "added_to_favorites";
    private static final int BACK_TYPE_CITIES = 0;
    private static final int BACK_TYPE_BACK = 1;
    // No place, as the main screen reads the last one shown, and no widget
    static final int NO_LOCATION = -2;
    private static final int NO_WIDGET = -2;
    // The widget's details tab of the days, as WidgetConfig gives it
    private static final int DETAILS_TAB_DAYS = 1;
    private static final String STATE_SHOWN = "shown_location_id";
    private static final String STATE_ADDED = "added_to_favorite";
    private static final int RENAME_CODE = 1;
    private static final int SETTINGS_CODE = 2;

    private BackPressHandler backHandler;
    // Null when the page closed as it was made
    private DetailsScreen screen;
    private View bottomBar;
    // The places' ids, a page each, in the order shown
    private List<Integer> pages = new ArrayList<>();
    private int openedId;
    private boolean showsFavorites;
    // The current location opened while the settings don't show it: its page stays alone whatever they say
    private boolean currentAlone;
    // Back leaves the app the way it came in rather than showing the list
    private boolean fromWidget;
    private boolean addedToFavorites;
    private IntentFilter receiverFilter;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onBroadcast(intent);
        }
    };

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.backHandler = new BackPressHandler(this) {
            @Override
            public void handleOnBackPressed() {
                back();
            }
        };
        AppSettings settings = AppSettings.getInstance();
        Intent intent = getIntent();
        String action = intent.getAction();
        boolean toDays = false;
        if (ACTION_OPEN_WIDGET_PLACE.equals(action)) {
            // Read now rather than as the widget was drawn: its place may have changed since
            WidgetConfig widget = new WidgetConfig(this, intent.getIntExtra(WidgetUpdater.EXTRA_WIDGET, NO_WIDGET));
            this.fromWidget = true;
            this.openedId = widget.getPlaceId();
            action = widgetAction(this.openedId, settings.getCurrentPlace() != null, settings.hasCachedLocation(this.openedId),
                    settings.getFavorites().contains(this.openedId));
            toDays = widget.getDetailsTab() == DETAILS_TAB_DAYS;
            if (action == null) {
                // Nothing stored for it yet: the list under the page shows, and isn't to open the page again
                settings.saveShownScreen(MainActivity.class.getCanonicalName());
                finish();
                return;
            }
        } else {
            this.openedId = placeId(intent);
        }
        this.showsFavorites = ACTION_OPEN_FAVORITES.equals(action);
        if (this.openedId != NO_LOCATION && this.showsFavorites) {
            this.currentAlone = currentAlone(this.openedId, settings.currentShown);
            this.pages = favoritePages(this.openedId, settings.hasCachedLocation(-1), settings.currentShown,
                    settings.getFavorites(), settings::hasCachedLocation);
        } else if (this.openedId != NO_LOCATION && ACTION_OPEN_PLACE.equals(action)) {
            this.pages.add(this.openedId);
            // A place stored without a forecast loads it
            WeatherUpdater.loadAllWeather(getApplicationContext());
        }
        if (this.pages.isEmpty()) {
            finish();
            return;
        }

        drawUnderSystemBars();
        setContentView(R.layout.activity_details);
        settings.saveShownScreen(DetailsActivity.class.getCanonicalName());
        this.screen = new DetailsScreen(this, this);
        this.bottomBar = findViewById(R.id.bottomBar);
        // The pages and the bar keep clear of the bars and the cutout themselves; the sky runs under them
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailsRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            this.screen.applyInsets(bars.top, bars.bottom);
            return insets;
        });
        // Made again, as for the night mode, on the city it was on
        showPages(bundle != null ? bundle.getInt(STATE_SHOWN, this.openedId) : this.openedId);
        this.addedToFavorites = bundle != null && bundle.getBoolean(STATE_ADDED);
        if (toDays && bundle == null) {
            this.screen.scrollToDaysOnce();
        }
        findViewById(R.id.btnCities).setOnClickListener(button -> {
            leaveForList(BACK_TYPE_CITIES);
            finish();
        });
        findViewById(R.id.btnMenu).setOnClickListener(button -> showMenu());
        setResult(RESULT_CANCELED);

        this.receiverFilter = new IntentFilter();
        this.receiverFilter.addAction(WeatherUpdater.ACTION_PLACE_LOADED);
        this.receiverFilter.addAction(WidgetUpdater.ACTION_WIDGET_LOADED);
        this.receiverFilter.addAction(WeatherUpdater.ACTION_PLACE_FAILED);
        // Each minute, for the places' local times
        this.receiverFilter.addAction(Intent.ACTION_TIME_TICK);
        if (this.showsFavorites) {
            this.receiverFilter.addAction(WeatherUpdater.ACTION_CURRENT_CHANGED);
        }
    }

    // The notification drawn again, back if it was swiped away, and the weather due asked for: opened from a widget or
    // the notification, the app may have been away long
    @Override
    protected void onResume() {
        super.onResume();
        AppUtils.registerAppReceiver(this, this.receiver, this.receiverFilter);
        NotificationUpdater.update(this);
        WeatherUpdater.loadAllWeather(getApplicationContext());
        this.screen.refreshShown();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(this.receiver);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (this.screen != null) {
            state.putInt(STATE_SHOWN, this.screen.getCurrentPlaceId());
        }
        state.putBoolean(STATE_ADDED, this.addedToFavorites);
    }

    @Override
    protected void onDestroy() {
        if (this.screen != null) {
            this.screen.destroy();
        }
        super.onDestroy();
    }

    // The sky under the status bar and the navigation bar: both clear, without Android's scrim for contrast, their icons
    // light over it
    @SuppressWarnings("deprecation")
    private void drawUnderSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    // --- The places ---

    // The id of the place in the intent's JSON; NO_LOCATION without one or for JSON that can't be read
    private static int placeId(Intent intent) {
        String json = intent.getStringExtra(EXTRA_PLACE);
        if (json == null) {
            return NO_LOCATION;
        }
        try {
            Place place = Place.fromJson(new JSONObject(json));
            return place != null ? place.id : NO_LOCATION;
        } catch (JSONException e) {
            Log.w(TAG, "The page's place can't be read", e);
            return NO_LOCATION;
        }
    }

    /**
     * How a widget's place opens: the current location among the favourites' pages while one is kept; a stored place
     * among them when it is a favourite, else alone; null, the page closing, while nothing is stored for it yet
     */
    static String widgetAction(int locationId, boolean currentKept, boolean stored, boolean favorite) {
        if (locationId == -1) {
            return currentKept ? ACTION_OPEN_FAVORITES : null;
        }
        if (!stored) {
            return null;
        }
        return favorite ? ACTION_OPEN_FAVORITES : ACTION_OPEN_PLACE;
    }

    /** Whether the current location is opened while the settings don't show it among the favourites: it is alone then */
    static boolean currentAlone(int openedId, boolean currentShown) {
        return openedId == -1 && !currentShown;
    }

    /**
     * The favourites' pages opened at a place: the current location's first while one is kept, when the settings show
     * it or it is the place opened; then each stored favourite in their order, unless the current location is alone
     */
    static List<Integer> favoritePages(int openedId, boolean currentKept, boolean currentShown, List<Integer> favorites,
                                       IntPredicate stored) {
        List<Integer> pages = new ArrayList<>();
        if (currentKept && (currentShown || openedId == -1)) {
            pages.add(-1);
        }
        if (!currentAlone(openedId, currentShown)) {
            for (int id : favorites) {
                if (stored.test(id)) {
                    pages.add(id);
                }
            }
        }
        return pages;
    }

    /**
     * The place shown once the given one's page goes, saved as the last shown before it leaves the favourites: the one
     * taking its place, the one before it when it was the last; NO_LOCATION when it was the only one, the page left for
     * the list
     */
    static int placeAfterDelete(List<Integer> pages, int id) {
        if (pages.size() <= 1) {
            return NO_LOCATION;
        }
        int index = pages.indexOf(id);
        return pages.get(index < pages.size() - 1 ? index + 1 : index - 1);
    }

    /**
     * The favourites' pages after the settings changed: the current location's put first when they show it now and
     * one is kept, taken away when they don't, unless it is alone. None left, the page closes
     */
    static List<Integer> pagesAfterSettings(List<Integer> pages, boolean currentShown, boolean currentKept, boolean currentAlone) {
        List<Integer> changed = new ArrayList<>(pages);
        boolean hasCurrent = changed.contains(-1);
        if (currentShown && currentKept && !hasCurrent) {
            changed.add(0, -1);
        } else if (!currentShown && hasCurrent && !currentAlone) {
            changed.remove(Integer.valueOf(-1));
        }
        return changed;
    }

    // The pages on the place given, or the first when it is not among them; the place shown is saved at once, as a page
    // set on the position it had isn't selected anew
    private void showPages(int shownId) {
        this.screen.setLocations(this.pages, shownId);
        onCityShown(this.screen.getCurrentPlaceId());
    }

    // --- Kept fresh ---

    // While the page is in front: a forecast came after all or none is coming, a minute passed
    private void onBroadcast(Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_TIME_TICK.equals(action)) {
            this.screen.refreshShown();
        } else if (WeatherUpdater.ACTION_CURRENT_CHANGED.equals(action)) {
            DetailsModelBuilder.loadFailed.remove(-1);
            this.screen.refresh(-1);
        } else {
            int id = intent.getIntExtra(EXTRA_PLACE_ID, -1);
            if (WeatherUpdater.ACTION_PLACE_FAILED.equals(action)) {
                // The page shows what it has rather than waiting
                DetailsModelBuilder.loadFailed.add(id);
            } else {
                DetailsModelBuilder.loadFailed.remove(id);
            }
            this.screen.refreshPlace(id);
        }
    }

    // --- The screen's host ---

    // For the main screen to open the page again on it, as the app comes back after it was gone
    @Override
    public void onCityShown(int locationId) {
        AppSettings.getInstance().saveShownPlace(locationId);
    }

    // On the tile's day
    @Override
    public void openEarthquakes(Place location) {
        startActivity(EarthquakesActivity.newIntent(this, location, DetailsScreen.EARTHQUAKE_DAYS));
    }

    @Override
    public void openAstro(Place location, boolean isMoon) {
        startActivity(AstroActivity.newIntent(this, location, isMoon));
    }

    // --- The menu ---

    // Over the bar: the city's actions while its place is stored, then the app's settings set apart
    private void showMenu() {
        AppSettings settings = AppSettings.getInstance();
        int id = this.screen.getCurrentPlaceId();
        Place place = settings.getPlace(id);
        List<GlassMenu.Item> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (place != null) {
            if (!this.showsFavorites && !settings.getFavorites().contains(id)) {
                items.add(new GlassMenu.Item(R.drawable.ic_menu_favorite_add, getString(R.string.favorites_add), null,
                        false, false));
                actions.add(() -> addToFavorites(id));
            } else if (id != -1) {
                items.add(new GlassMenu.Item(R.drawable.ic_menu_delete, getString(R.string.favorites_remove), null, false, false));
                actions.add(() -> deleteFromFavorites(id));
                items.add(new GlassMenu.Item(R.drawable.ic_menu_edit, getString(R.string.rename_place), null, false, false));
                actions.add(() -> startActivityForResult(new Intent(this, RenameActivity.class).putExtra(EXTRA_PLACE_ID, id),
                        RENAME_CODE));
            }
            items.add(new GlassMenu.Item(R.drawable.ic_menu_sun_now, getString(R.string.sun_now), null, false, false));
            actions.add(() -> startActivity(new Intent(this, SunNowActivity.class)));
            // The city's last, the settings apart from them
            items.add(new GlassMenu.Item(R.drawable.ic_stat_earthquake, getString(R.string.earthquakes), null, false, true));
            actions.add(() -> startActivity(EarthquakesActivity.newIntent(this, place)));
        }
        items.add(new GlassMenu.Item(R.drawable.ic_menu_settings, getString(R.string.settings), null, false, false));
        actions.add(() -> startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_CODE));
        GlassMenu.showAbove(this.bottomBar, items, index -> actions.get(index).run());
    }

    // A place shown alone, once there is room; the main screen is told, to show the favourites
    private void addToFavorites(int id) {
        if (!Favorites.hasRoom(this)) {
            return;
        }
        AppSettings.getInstance().addFavorite(id);
        this.addedToFavorites = true;
        Toast.makeText(this, R.string.favorites_added, Toast.LENGTH_SHORT).show();
    }

    // A place shown alone stays, stored still as its page shows it, and goes as the page is left; among the favourites
    // its page goes, the one taking its place shown, and with the only page the page is left as by Back
    private void deleteFromFavorites(int id) {
        AppSettings settings = AppSettings.getInstance();
        if (!this.showsFavorites) {
            settings.removeFavorite(id, false);
            this.addedToFavorites = false;
            Toast.makeText(this, R.string.favorites_removed, Toast.LENGTH_SHORT).show();
            return;
        }
        int next = placeAfterDelete(this.pages, id);
        if (next == NO_LOCATION) {
            back();
        } else {
            this.pages.remove(Integer.valueOf(id));
            showPages(next);
        }
        // Only now, no longer the place shown last: one nothing else shows leaves the store at once, rather than having
        // its forecast loaded with the others until the app's next start
        settings.removeFavorite(id);
    }

    // --- The results ---

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode != RESULT_OK || this.screen == null) {
            return;
        }
        if (requestCode == RENAME_CODE) {
            this.screen.refresh(this.screen.getCurrentPlaceId());
        } else if (requestCode == SETTINGS_CODE) {
            onSettingsResult(intent);
        }
    }

    // A setting the pages show by has changed: the widgets drawn again with it, the weather asked for anew when it
    // comes from elsewhere now, and the favourites' pages with or without the current location as the settings say
    private void onSettingsResult(Intent intent) {
        AppSettings.init(getApplicationContext());
        WidgetUpdater.refreshAll(this, true);
        if (intent != null && intent.getBooleanExtra(EXTRA_DATA_PROVIDER_CHANGED, false)) {
            WeatherUpdater.updateFromNewSource(getApplicationContext());
        }
        if (this.showsFavorites) {
            AppSettings settings = AppSettings.getInstance();
            List<Integer> pages = pagesAfterSettings(this.pages, settings.currentShown, settings.getPlace(-1) != null,
                    this.currentAlone);
            if (pages.isEmpty()) {
                // The current location's page was the only one: there is nothing to open again either
                settings.saveShownScreen("");
                finish();
                return;
            }
            if (!pages.equals(this.pages)) {
                int shown = this.screen.getCurrentPlaceId();
                this.pages = pages;
                showPages(shown);
            }
        }
        // The units and the parameters
        this.screen.onSettingsChanged();
    }

    // --- Leaving ---

    // For the list: the main screen is not to open the page again, a place shown alone that nothing else shows is let
    // go, and the main screen is told how the page was left
    private void leaveForList(int backType) {
        AppSettings settings = AppSettings.getInstance();
        settings.saveShownScreen(MainActivity.class.getCanonicalName());
        settings.saveShownPlace(NO_LOCATION);
        if (!this.showsFavorites && !settings.isPlaceUsed(this.openedId)) {
            settings.removePlace(this.openedId);
        }
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_BACK_TYPE, backType).putExtra(EXTRA_ADDED_TO_FAVORITES, this.addedToFavorites));
    }

    // Opened from a widget, Back leaves the app the way it came in, the list under the page being for the cities'
    // button; finishAffinity takes no result
    private void back() {
        leaveForList(BACK_TYPE_BACK);
        if (this.fromWidget) {
            setResult(RESULT_CANCELED);
            finishAffinity();
        } else {
            this.backHandler.proceed();
        }
    }
}
