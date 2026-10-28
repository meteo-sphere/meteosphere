package org.meteosphere.ui.quakes;

import org.meteosphere.ui.glass.TopFadeBar;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.meteosphere.data.model.Place;
import org.meteosphere.R;
import org.meteosphere.ui.common.FragmentHost;
import org.meteosphere.settings.AppLanguage;

/** The earthquake list with the distances to a location, opened from the details screen, the earthquake notifications and the widget. */
public class EarthquakesActivity extends AppCompatActivity {

    private static final String EXTRA_LATITUDE = "latitude";
    private static final String EXTRA_LONGITUDE = "longitude";
    private static final String EXTRA_LOCATION_NAME = "location_name";
    private static final String EXTRA_DAYS = "days";

    /** The list with the distances to the location, or to the current or first favorite one if it is null */
    public static Intent newIntent(Context context, Place location) {
        return location == null ? new Intent(context, EarthquakesActivity.class)
                : newIntent(context, location.latitude, location.longitude, location.displayName());
    }

    /** The list as newIntent(context, location) has it, of the period: 1, 7 or 30 days */
    public static Intent newIntent(Context context, Place location, int days) {
        return newIntent(context, location).putExtra(EXTRA_DAYS, days);
    }

    public static Intent newIntent(Context context, double latitude, double longitude, String locationName) {
        return new Intent(context, EarthquakesActivity.class)
                .putExtra(EXTRA_LATITUDE, latitude)
                .putExtra(EXTRA_LONGITUDE, longitude)
                .putExtra(EXTRA_LOCATION_NAME, locationName);
    }

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_fragment_host);
        // Shaded at the bottom in the dark theme only, as on the main screen's tab
        FragmentHost.applyInsets(this, findViewById(R.id.appBar),
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        setSupportActionBar((Toolbar) findViewById(R.id.tbFragment));
        TopFadeBar.attach(this, findViewById(R.id.appBar), false);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.earthquakes);
        }
        if (bundle == null) {
            Intent intent = getIntent();
            EarthquakesFragment fragment = intent.hasExtra(EXTRA_LATITUDE)
                    ? EarthquakesFragment.newInstance(intent.getDoubleExtra(EXTRA_LATITUDE, 0), intent.getDoubleExtra(EXTRA_LONGITUDE, 0),
                            intent.getStringExtra(EXTRA_LOCATION_NAME))
                    : EarthquakesFragment.newInstance();
            if (intent.hasExtra(EXTRA_DAYS)) {
                fragment.withDays(intent.getIntExtra(EXTRA_DAYS, 1));
            }
            getSupportFragmentManager().beginTransaction().add(R.id.flFragment, fragment).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
