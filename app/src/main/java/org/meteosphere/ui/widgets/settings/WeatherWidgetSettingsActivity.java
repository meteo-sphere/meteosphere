package org.meteosphere.ui.widgets.settings;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.fragment.app.FragmentTransaction;
import org.meteosphere.R;
import org.meteosphere.data.model.Place;

/**
 * A weather widget's settings, as it is put on the home screen or from its gear: WeatherWidgetSettingsFragment, with
 * the search for a place in its frame while it is hidden
 */
public class WeatherWidgetSettingsActivity extends WidgetSettingsActivity {

    private static final String TAG_SETTINGS = "settings";

    private WeatherWidgetSettingsFragment settings;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (!openScreen(R.layout.activity_weather_widget_settings, R.string.weather_widget_settings)) {
            return;
        }
        // Made again, as for a new theme or language, the screen has its settings back with what was chosen
        this.settings = (WeatherWidgetSettingsFragment) getSupportFragmentManager().findFragmentByTag(TAG_SETTINGS);
        if (this.settings == null) {
            this.settings = WeatherWidgetSettingsFragment.newInstance(this.appWidgetId);
            getSupportFragmentManager().beginTransaction().add(R.id.flWidgetSettings, this.settings, TAG_SETTINGS).commit();
        }
    }

    @Override
    protected int searchContainer() {
        return R.id.flWidgetSettings;
    }

    @Override
    protected void showSettings(boolean shown, FragmentTransaction transaction) {
        // Without a search's transaction in one of its own, if the settings aren't as asked already
        if (transaction == null && this.settings.isHidden() != shown) {
            return;
        }
        FragmentTransaction change = transaction != null ? transaction : getSupportFragmentManager().beginTransaction();
        if (shown) {
            change.show(this.settings);
        } else {
            change.hide(this.settings);
        }
        if (transaction == null) {
            change.commit();
        }
    }

    @Override
    public void onPlacePicked(Place location) {
        this.settings.setLocation(location);
        closeLocationSearch();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.itemApplyWidgetSettings) {
            this.settings.apply();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
