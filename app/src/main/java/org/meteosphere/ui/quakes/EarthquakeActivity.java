package org.meteosphere.ui.quakes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import org.meteosphere.R;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.settings.AppLanguage;

/** One earthquake on a screen of its own, as a widget opens it: EarthquakeFragment's, which the lists lay over themselves */
public class EarthquakeActivity extends AppCompatActivity {

    /** The earthquake, with its distance from the location if a name is given */
    public static Intent newIntent(Context context, Earthquake earthquake, double referenceLatitude, double referenceLongitude, String referenceName) {
        return new Intent(context, EarthquakeActivity.class)
                .putExtras(EarthquakeFragment.arguments(earthquake, referenceLatitude, referenceLongitude, referenceName));
    }

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        FrameLayout host = new FrameLayout(this);
        host.setId(R.id.flFragment);
        setContentView(host);
        if (bundle == null) {
            EarthquakeFragment fragment = new EarthquakeFragment();
            fragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction().add(R.id.flFragment, fragment).commit();
        }
    }
}
