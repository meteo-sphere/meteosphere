package org.meteosphere.ui.locations;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.meteosphere.R;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppLanguage;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.BackPressHandler;
import org.meteosphere.ui.common.EdgeToEdgeUtils;

/**
 * A stored place's own name for the app: typed in its field, saved by the bar's check, the keyboard's Done or Enter.
 * The place's own name, or nothing, leaves it without one. The screen gives back the place's id, "place_id", as it
 * was opened with it; Up and Back leave the name as it was.
 */
public class RenameActivity extends AppCompatActivity {

    private static final String EXTRA_PLACE_ID = "place_id";

    private int locationId;
    // Null when no place of the id is stored
    private Place place;
    private TextInputEditText field;

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_rename);
        EdgeToEdgeUtils.applyToolbarInsets(this, findViewById(R.id.appBar));
        setSupportActionBar((Toolbar) findViewById(R.id.tbRename));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.rename_title);
        }
        new BackPressHandler(this) {
            @Override
            public void handleOnBackPressed() {
                cancel();
            }
        };

        this.locationId = getIntent().getIntExtra(EXTRA_PLACE_ID, -1);
        // -1 is the current location, which isn't stored as the other places are and has no name of the user's
        this.place = this.locationId != -1 ? AppSettings.getInstance().getPlace(this.locationId) : null;
        TextInputLayout fieldLayout = findViewById(R.id.tilRenameName);
        this.field = findViewById(R.id.etRenameName);
        TextView airport = findViewById(R.id.tvRenameAirport);
        TextView ownName = findViewById(R.id.tvRenameOwnName);
        TextView area = findViewById(R.id.tvRenameArea);
        if (this.place == null) {
            fieldLayout.setEnabled(false);
            return;
        }
        this.field.setText(this.place.displayName());
        this.field.setSelection(this.field.length());
        // Emptied, the field shows the name the place goes back to
        fieldLayout.setPlaceholderText(this.place.name);
        airport.setText(this.place.airport ? getString(R.string.airport) + " " : "");
        ownName.setText(this.place.name);
        area.setText(area(this.place.country, this.place.region));
        // The keyboard's Done and a hardware Enter, on its key down, do what the check does
        this.field.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId != EditorInfo.IME_ACTION_DONE && !enter) {
                return false;
            }
            if (!enter || event.getAction() == KeyEvent.ACTION_DOWN) {
                apply();
            }
            return true;
        });
        // The keyboard comes up for it with the screen, as the manifest asks
        this.field.requestFocus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_rename_place, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            cancel();
            return true;
        }
        if (item.getItemId() == R.id.itemApplyRename) {
            apply();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // The name saved and the place's id given back; without a stored place nothing is saved
    private void apply() {
        hideKeyboard();
        if (this.place == null) {
            cancel();
            return;
        }
        String typed = this.field.getText() != null ? this.field.getText().toString() : "";
        AppSettings.getInstance().setLocationCustomName(this.locationId, customName(typed, this.place.name));
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PLACE_ID, this.locationId));
        finish();
    }

    private void cancel() {
        hideKeyboard();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(this.field.getWindowToken(), 0);
        }
    }

    /**
     * The custom name for the text typed, trimmed: "" for none when it is the place's own name or empty, so that the
     * own name follows the app's language
     */
    static String customName(String typed, String ownName) {
        String name = typed.trim();
        return name.equals(ownName) ? "" : name;
    }

    /** The country, and the region after a comma when there is one */
    static String area(String country, String region) {
        return region == null || region.isEmpty() ? country : country + ", " + region;
    }
}
