package org.meteosphere.ui.widgets.settings;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.meteosphere.R;
import org.meteosphere.settings.AppLanguage;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.EdgeToEdgeUtils;
import org.meteosphere.ui.common.SearchBackCallback;
import org.meteosphere.ui.locations.PlaceSearchFragment;
import org.meteosphere.util.AppUtils;

/**
 * What the widgets' settings screens share: the widget they set up, the toolbar with the check that applies the
 * settings, the search for a place opening in the toolbar over the settings, and the question when no place is chosen.
 * WeatherWidgetSettingsActivity sets up the weather widgets, EarthquakeWidgetSettingsActivity the earthquake one and
 * AstroWidgetSettingsActivity the Sun and the Moon ones
 */
public abstract class WidgetSettingsActivity extends AppCompatActivity implements MenuItemCompat.OnActionExpandListener,
        SearchView.OnQueryTextListener, PlaceSearchFragment.OnPlacePickedListener {

    private static final String TAG_LOCATION_SEARCH = "location_search";

    /** The widget set up, 0 for none */
    protected int appWidgetId;

    private SearchBackCallback searchBackCallback;
    private MenuItem applyItem;
    private MenuItem searchItem;
    // Null while the search is closed
    private PlaceSearchFragment search;
    // How far the keyboard rises over the navigation bar: the search's list keeps that much room at its end
    private int keyboardInset;

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    /** Whether the launcher has the widget and it is the screen given that sets it up */
    protected static boolean configures(Context context, int widgetId, ComponentName screen) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId);
        return info != null && screen.equals(info.configure);
    }

    /**
     * Shows the layout under the toolbar with the title, the settings cancelled till they are applied; false when the
     * screen finishes at once, not being the one of the widget
     */
    protected boolean openScreen(int layout, int title) {
        setResult(RESULT_CANCELED);
        this.appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        // Other apps may open the screens too: they would change any widget of the app
        if (!configures(this, this.appWidgetId, getComponentName())) {
            finish();
            return false;
        }
        if (AppSettings.getInstance() == null) {
            AppSettings.init(getApplicationContext());
        }
        // The wallpaper shows through over the preview only in a translucent window; the theme's background keeps the
        // rest of the screen opaque
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(layout);
        Toolbar toolbar = findViewById(R.id.tbWidget);
        setSupportActionBar(toolbar);
        setTitle(title);
        // A search that was open came back with the screen
        this.search = (PlaceSearchFragment) getSupportFragmentManager().findFragmentByTag(TAG_LOCATION_SEARCH);
        EdgeToEdgeUtils.applyToolbarInsetsUnderKeyboard(this, findViewById(R.id.appBar), false, (navigationBar, keyboard) -> {
            this.keyboardInset = Math.max(0, keyboard - navigationBar);
            if (this.search != null) {
                this.search.setBottomInset(this.keyboardInset);
            }
        });
        this.searchBackCallback = new SearchBackCallback(this, toolbar);
        return true;
    }

    /** The view the search goes in */
    protected abstract int searchContainer();

    /**
     * Hides the settings as the search comes or shows them as it goes, in the transaction that adds or removes it; the
     * transaction is null when there is no search to remove, and for a search that came back with the screen
     */
    protected abstract void showSettings(boolean shown, FragmentTransaction transaction);

    // A search that came back with the screen is shown over the settings again; only now, as the subclass' onCreate
    // has set up what showSettings hides
    @Override
    protected void onPostCreate(Bundle bundle) {
        super.onPostCreate(bundle);
        if (this.search != null) {
            showSettings(false, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_widget_settings, menu);
        this.applyItem = menu.findItem(R.id.itemApplyWidgetSettings);
        this.searchItem = menu.findItem(R.id.action_search);
        AppUtils.setUpSearchField(this, this.searchItem, this, this);
        // A search that came back with the screen gets its field back
        if (this.search != null) {
            expandSearch();
        }
        return super.onCreateOptionsMenu(menu);
    }

    // The search's field in the toolbar in place of the check
    private void expandSearch() {
        this.searchItem.setVisible(true);
        this.searchItem.expandActionView();
        this.applyItem.setVisible(false);
    }

    /** Opens the search for a place over the settings; the place picked comes to onPlacePicked */
    protected void openLocationSearch() {
        if (this.search != null) {
            return;
        }
        expandSearch();
        this.search = PlaceSearchFragment.newInstance(PlaceSearchFragment.MODE_PICK);
        this.search.setBottomInset(this.keyboardInset);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        showSettings(false, transaction);
        transaction.add(searchContainer(), this.search, TAG_LOCATION_SEARCH);
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        transaction.commit();
    }

    /** Closes the search, the settings shown again */
    protected void closeLocationSearch() {
        // Collapsing the field closes the search, as Back does
        if (this.searchItem.isActionViewExpanded()) {
            this.searchItem.collapseActionView();
        } else {
            hideLocationSearch();
        }
    }

    private void hideLocationSearch() {
        this.searchBackCallback.setEnabled(false);
        if (this.search != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.remove(this.search);
            showSettings(true, transaction);
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
            transaction.commit();
            this.search = null;
        } else {
            showSettings(true, null);
        }
        this.applyItem.setVisible(true);
        this.searchItem.setVisible(false);
    }

    /** Tells that no place is chosen: one is picked in the search, or useCurrent takes the current location */
    protected void askForLocation(Runnable useCurrent) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.no_place_message)
                .setPositiveButton(R.string.no_place_choose, (dialog, which) -> openLocationSearch())
                .setNegativeButton(R.string.no_place_automatic, (dialog, which) -> useCurrent.run())
                .show();
    }

    // Back closes the search rather than the screen
    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        this.searchBackCallback.setEnabled(true);
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        hideLocationSearch();
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
}
