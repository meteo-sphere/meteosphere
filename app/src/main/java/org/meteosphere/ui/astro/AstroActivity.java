package org.meteosphere.ui.astro;

import org.meteosphere.ui.glass.TopFadeBar;
import org.meteosphere.ui.sky.SkyBackgroundDrawable;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.color.MaterialColors;
import android.view.MenuItem;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.json.JSONException;
import org.json.JSONObject;
import org.meteosphere.data.model.Place;
import org.meteosphere.R;
import org.meteosphere.ui.common.FragmentHost;
import org.meteosphere.settings.AppLanguage;

/** The Sun or the Moon tab for one location, opened from the sun and the moon of the weather details */
public class AstroActivity extends AppCompatActivity {

    private static final String EXTRA_PLACE = "place";
    private static final String EXTRA_MOON = "moon";

    // The place as its JSON; without it the tab shows the place chosen for the tabs
    public static Intent newIntent(Context context, Place location, boolean isMoon) {
        Intent intent = new Intent(context, AstroActivity.class).putExtra(EXTRA_MOON, isMoon);
        try {
            if (location != null) {
                intent.putExtra(EXTRA_PLACE, location.toJson().toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return intent;
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
        // Over its sky, shaded at the bottom whatever the theme
        FragmentHost.applyInsets(this, findViewById(R.id.appBar), true);
        setSupportActionBar((Toolbar) findViewById(R.id.tbFragment));
        boolean isMoon = getIntent().getBooleanExtra(EXTRA_MOON, false);
        // Over its sky, the night's for the Moon and the day's for the Sun, as on the main screen's tabs
        Toolbar toolbar = findViewById(R.id.tbFragment);
        SkyBackgroundDrawable sky = new SkyBackgroundDrawable(MaterialColors.getColor(getWindow().getDecorView(), android.R.attr.colorBackground));
        getWindow().setBackgroundDrawable(sky);
        // The content dissolves into its sky under the tab's row, under a shade
        TopFadeBar bar = TopFadeBar.attach(this, findViewById(R.id.appBar), true);
        AstroSkies.load(this, !isMoon, (layers, bitmap) -> {
            sky.setSkies(bitmap, 1, null, 0);
            bar.onBackgroundChanged();
        });
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(isMoon ? R.string.moon : R.string.tab_sun);
        }
        // After the up arrow is there, for it to turn white as well
        AstroSkies.applyContentColors(this, toolbar, 1);
        if (bundle == null) {
            Place location = null;
            String json = getIntent().getStringExtra(EXTRA_PLACE);
            try {
                location = json != null ? Place.fromJson(new JSONObject(json)) : null;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.flFragment, isMoon ? MoonFragment.newInstance(location) : SunFragment.newInstance(location))
                    .commit();
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
