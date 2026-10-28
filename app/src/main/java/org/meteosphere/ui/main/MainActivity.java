package org.meteosphere.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import org.meteosphere.R;
import org.meteosphere.addon.AddOns;
import org.meteosphere.background.NotificationUpdater;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.background.WidgetUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.location.PositionService;
import org.meteosphere.settings.AppLanguage;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.EdgeToEdgeUtils;
import org.meteosphere.ui.common.ListRoom;
import org.meteosphere.ui.common.SearchBackCallback;
import org.meteosphere.ui.details.DetailsActivity;
import org.meteosphere.ui.glass.GlassStrip;
import org.meteosphere.ui.locations.Favorites;
import org.meteosphere.ui.locations.PlaceSearchFragment;
import org.meteosphere.ui.settings.SettingsActivity;
import org.meteosphere.util.AppUtils;

/**
 * The main screen: the tabs, made anew each time it starts, and over them the search for a place, its field in the
 * bar. The bar is clear over the tabs, which move it and fade its band in as their lists scroll; over the search it
 * stays, its glass fading in as the places scroll under it. A search open stays as the screen goes and comes back. A
 * notification's tap opens its place's page, and Back from that page leaves the app
 */
public final class MainActivity extends AppCompatActivity implements MainTabsFragment.OnAddTappedListener,
        PlaceSearchFragment.OnPlacePickedListener, MenuItemCompat.OnActionExpandListener, SearchView.OnQueryTextListener {

    // The notification's place, -1 for the current location, -2 for none
    private static final String EXTRA_NOTIFICATION_LOCATION = "notification_location_id";
    private static final int NO_LOCATION = -2;
    // What the settings and a place's page answer with
    private static final String EXTRA_DATA_PROVIDER_CHANGED = "data_provider_changed";
    private static final String EXTRA_BACK_TYPE = "left_by";
    private static final String EXTRA_ADDED_TO_FAVORITES = "added_to_favorites";
    // The page was left by Back rather than by its cities' button
    private static final int BACK_TYPE_BACK = 1;
    private static final String STATE_DIRECT_NAVIGATION = "direct_navigation";
    private static final String TAG_TABS = "tabs";
    private static final String TAG_SEARCH = "location_search";
    private static final int SETTINGS_CODE = 0;

    private GlassStrip appBar;
    private Toolbar toolbar;
    private SearchBackCallback searchBackCallback;
    private Menu menu;
    private MenuItem searchItem;
    // Made as the screen starts, gone as it stops
    private MainTabsFragment tabs;
    // Null while the search is closed; it is not closed as the screen stops
    private PlaceSearchFragment search;
    private int searchMode;
    // The page opened next comes from a notification's tap: Back from it leaves the app rather than showing the tabs
    private boolean directNavigation;
    // The status bar's height and the bar's with it; the navigation bar's and the keyboard's over the screen's bottom
    private int statusInset;
    private int barHeight;
    private int navigationBarInset;
    private int keyboardInset;

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        // The manifest gives the splash's theme, for the window shown as the app starts
        setTheme(R.style.AppTheme);
        // The tabs and the search are made anew rather than given back; only the direct navigation is read back
        super.onCreate(null);
        setContentView(R.layout.activity_main);
        this.appBar = findViewById(R.id.appBar);
        this.toolbar = findViewById(R.id.tbMain);
        setSupportActionBar(this.toolbar);
        this.searchBackCallback = new SearchBackCallback(this, this.toolbar);
        // Clear until the tabs' lists scroll under it
        this.appBar.setGlassAlpha(0);
        this.appBar.addOnLayoutChangeListener((bar, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> onBarLaidOut());
        this.appBar.getViewTreeObserver().addOnScrollChangedListener(this::followSearch);
        // The search's view comes after the transaction adding it: its list is blurred and kept clear of the bar then
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager manager, @NonNull Fragment fragment, @NonNull View view, Bundle saved) {
                if (fragment == MainActivity.this.search) {
                    MainActivity.this.appBar.setBlurSource(searchList(view));
                    keepSearchClear();
                    followSearch();
                }
            }
        }, false);
        // The tabs run under the navigation bar and stay as they are under the keyboard; the search's places scroll
        // on over either
        EdgeToEdgeUtils.applyToolbarInsetsUnderKeyboard(this, this.appBar, true, (navigationBar, keyboard) -> {
            this.navigationBarInset = navigationBar;
            this.keyboardInset = keyboard;
            if (this.tabs != null) {
                this.tabs.setNavigationBarInset(navigationBar);
            }
            keepSearchClear();
        });
        AddOns.onMainScreen(this);
        if (bundle == null) {
            openFromNotification(getIntent());
        } else {
            this.directNavigation = bundle.getBoolean(STATE_DIRECT_NAVIGATION);
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFromNotification(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(STATE_DIRECT_NAVIGATION, this.directNavigation);
    }

    /**
     * The tabs anew, with the bars known so far, hidden while a search left open shows. Made before the screen's own
     * start, so that they are made at once: a result coming right after finds their pages
     */
    @Override
    protected void onStart() {
        this.tabs = MainTabsFragment.newInstance();
        this.tabs.setNavigationBarInset(this.navigationBarInset);
        this.tabs.setHeaderInsets(this.statusInset, this.barHeight - this.statusInset);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction().add(R.id.flMainContent, this.tabs, TAG_TABS);
        if (this.search != null) {
            transaction.hide(this.tabs);
        }
        transaction.commitNowAllowingStateLoss();
        super.onStart();
    }

    // The notification drawn again, back if it was swiped away; the weather due asked for; the page the app was left
    // on opened again, here rather than as the tabs are made, as a notification's tap comes after the start
    @Override
    protected void onResume() {
        super.onResume();
        NotificationUpdater.update(this);
        WeatherUpdater.loadAllWeather(getApplicationContext());
        if (this.tabs != null && !this.tabs.restoreLastScreen()) {
            // A notification's place no longer kept opens nothing: the next page is the user's own
            this.directNavigation = false;
        }
        AppSettings.getInstance().saveShownScreen(MainActivity.class.getCanonicalName());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.tabs != null) {
            getSupportFragmentManager().beginTransaction().remove(this.tabs).commitNowAllowingStateLoss();
            this.tabs = null;
        }
    }

    // A search for the device's position started by the tabs ends with the screen
    @Override
    protected void onDestroy() {
        stopService(new Intent(this, PositionService.class));
        super.onDestroy();
    }

    // --- The notification and the home network ---

    // The notification's place becomes the page shown last, for the restore to open as the screen resumes
    private void openFromNotification(Intent intent) {
        int locationId = intent.getIntExtra(EXTRA_NOTIFICATION_LOCATION, NO_LOCATION);
        if (locationId == NO_LOCATION) {
            return;
        }
        AppSettings settings = AppSettings.getInstance();
        settings.saveShownPlace(locationId);
        settings.saveShownScreen(DetailsActivity.class.getCanonicalName());
        this.directNavigation = true;
    }

    // --- The bar ---

    // The status bar's room is the bar's top padding: the tabs' lists and rows start under the bar, the search's too
    private void onBarLaidOut() {
        int height = this.appBar.getHeight();
        int status = this.appBar.getPaddingTop();
        if (height == this.barHeight && status == this.statusInset) {
            return;
        }
        this.barHeight = height;
        this.statusInset = status;
        this.appBar.setStatusInset(status);
        if (this.tabs != null) {
            this.tabs.setHeaderInsets(status, height - status);
        }
        keepSearchClear();
    }

    // Over the search the bar stays at its place with its title's row, the tabs having moved or faded them, its glass
    // as opaque as the places have run under it
    private void followSearch() {
        View view = this.search != null ? this.search.getView() : null;
        if (view == null) {
            return;
        }
        View list = searchList(view);
        this.appBar.setTranslationY(0);
        this.toolbar.setAlpha(1);
        this.toolbar.setVisibility(View.VISIBLE);
        this.appBar.setGlassAlpha(list == null ? 0 : HeaderScroll.glass(this, HeaderScroll.depth(list), 0));
    }

    // The search's list, looked for once, not on every frame of a scroll
    private static View searchList(View view) {
        Object list = view.getTag(R.id.tag_blurred_list);
        if (!(list instanceof View)) {
            list = view.findViewById(android.R.id.list);
            view.setTag(R.id.tag_blurred_list, list);
        }
        return (View) list;
    }

    // The search's places start under the bar and scroll on over the navigation bar or the keyboard, the higher one
    private void keepSearchClear() {
        View view = this.search != null ? this.search.getView() : null;
        View list = view != null ? ListRoom.find(view) : null;
        if (list != null) {
            ListRoom.setTop(list, this.barHeight);
            ListRoom.setBottom(list, Math.max(this.navigationBarInset, this.keyboardInset));
        }
    }

    // --- The menu ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        this.searchItem = menu.findItem(R.id.action_search);
        AppUtils.setUpSearchField(this, this.searchItem, this, this);
        // As wide as the bar
        ((SearchView) this.searchItem.getActionView()).setMaxWidth(Integer.MAX_VALUE);
        // A search open as the menu is made again keeps its field in the bar
        if (this.search != null) {
            this.searchItem.expandActionView();
            showSettings(false);
        }
        if (this.tabs != null) {
            this.tabs.reapplyChrome();
            this.tabs.updateSearchButton();
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_CODE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettings(boolean shown) {
        if (this.menu != null) {
            this.menu.setGroupVisible(R.id.groupSettings, shown);
        }
    }

    // --- The search ---

    // A favourite is added by the search too, once there is room for it
    @Override
    public void onAddTapped() {
        if (this.searchItem == null || this.search != null || !Favorites.hasRoom(this)) {
            return;
        }
        openSearch(PlaceSearchFragment.MODE_ADD);
        this.searchItem.expandActionView();
    }

    /**
     * Over the tabs, which hide, and without the settings in the bar; the insets given again, for the search's list to
     * get its room. Nothing when one is open, as the field expanded by the add button or a menu made again
     */
    private void openSearch(int mode) {
        if (this.search != null) {
            return;
        }
        this.search = PlaceSearchFragment.newInstance(mode);
        this.searchMode = mode;
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .add(R.id.flMainContent, this.search, TAG_SEARCH);
        if (this.tabs != null) {
            transaction.hide(this.tabs);
        }
        transaction.commitAllowingStateLoss();
        showSettings(false);
        ViewCompat.requestApplyInsets(findViewById(android.R.id.content));
    }

    // The tabs again as they were, their bar's glass rather than the search's, the settings back in the bar
    private void closeSearch(MenuItem item) {
        item.getActionView().clearFocus();
        if (this.search != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .remove(this.search);
            if (this.tabs != null) {
                transaction.show(this.tabs);
            }
            transaction.commitAllowingStateLoss();
            this.search = null;
        }
        this.appBar.setBlurSource(null);
        ViewCompat.requestApplyInsets(findViewById(android.R.id.content));
        showSettings(true);
    }

    // A place to see the weather of opens alone over the search, which shows again on the way back; a new favourite
    // closes it
    @Override
    public void onPlacePicked(Place location) {
        if (this.searchItem != null) {
            this.searchItem.getActionView().clearFocus();
        }
        if (this.searchMode == PlaceSearchFragment.MODE_ADD) {
            Favorites.add(this, location);
            if (this.searchItem != null) {
                this.searchItem.collapseActionView();
            }
        } else if (this.tabs != null) {
            this.tabs.openPlace(location);
        }
    }

    // Tapped, the field opens the search for a place's weather; Back collapses it, the keyboard going first
    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        this.searchBackCallback.setEnabled(true);
        openSearch(PlaceSearchFragment.MODE_SEARCH);
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        this.searchBackCallback.setEnabled(false);
        closeSearch(item);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        if (this.search != null) {
            this.search.search(query);
        }
        return true;
    }

    // The places are looked for as the text changes: the field only hides the keyboard
    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    // --- The results ---

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == SETTINGS_CODE) {
            onSettingsResult(intent);
        } else if (requestCode == MainTabsFragment.REQUEST_FAVORITES || requestCode == MainTabsFragment.REQUEST_CUSTOM) {
            onPageResult(requestCode, intent);
        }
    }

    // A setting the screen shows by has changed: the cards and the nearby places made again with it, the widgets
    // too, and the weather asked for anew when it comes from elsewhere now
    private void onSettingsResult(Intent intent) {
        AppSettings.init(getApplicationContext());
        if (this.tabs != null) {
            this.tabs.favoriteFragment.refreshCards();
            this.tabs.nearbyFragment.refresh();
        }
        WidgetUpdater.refreshAll(this, true);
        if (intent != null && intent.getBooleanExtra(EXTRA_DATA_PROVIDER_CHANGED, false)) {
            WeatherUpdater.updateFromNewSource(getApplicationContext());
        }
    }

    // Back from a page a notification opened leaves the app; a place found added to the favourites on its page has the
    // tabs show the favourites
    private void onPageResult(int requestCode, Intent intent) {
        boolean direct = this.directNavigation;
        this.directNavigation = false;
        if (intent == null) {
            return;
        }
        if (requestCode == MainTabsFragment.REQUEST_CUSTOM && intent.getBooleanExtra(EXTRA_ADDED_TO_FAVORITES, false)) {
            AppSettings.getInstance().setLastMainTab(AppSettings.TAB_FAVORITES);
        }
        if (direct && intent.getIntExtra(EXTRA_BACK_TYPE, 0) == BACK_TYPE_BACK) {
            finish();
        }
    }
}
