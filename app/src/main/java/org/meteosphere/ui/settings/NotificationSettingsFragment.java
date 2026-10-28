package org.meteosphere.ui.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.meteosphere.R;
import org.meteosphere.background.NotificationUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.HeightAnimation;
import org.meteosphere.ui.glass.GlassMenu;
import org.meteosphere.ui.notification.WeatherNotification;
import org.meteosphere.util.AppUtils;

/**
 * The weather notification's settings: whether it is shown, its place, the current location or one picked in the
 * search, its background and whether it shows the next hours. They are the screen's own till the bar's tick applies
 * them, Back leaving the saved ones, and come back when the screen is made again
 */
public final class NotificationSettingsFragment extends Fragment {

    private static final String TAG = "NotificationSettingsFragment";

    private static final String STATE_ENABLED = "enabled";
    private static final String STATE_AUTO_LOCATION = "auto_location";
    private static final String STATE_LOCATION_ID = "location_id";
    private static final String STATE_SHOW_FORECAST = "show_forecast";
    private static final String STATE_LOOK = "look";

    /** The activity the screen is shown in, which opens the place search over it and applies it */
    public interface OnNotificationScreenListener {
        void onPickPlace();

        /** With true the screen is applied at once, as by the bar's tick; with false the activity goes back */
        void onAnswer(boolean apply);
    }

    private OnNotificationScreenListener listener;

    // The screen's own values till the tick applies them
    private boolean enabled;
    private boolean autoLocation;
    // The place chosen otherwise, -1 for none, and its name
    private int locationId = -1;
    private String locationName;
    private boolean showForecast;
    private int look;

    // "Automatic" was chosen in the tick's dialog without the location permission: once it is granted the activity gets
    // the answer it would have got at once with it. Not kept when the screen is made again, as the answer could then
    // come before the activity has its tick to apply with
    private boolean answerWhenLocated;
    private boolean answerApply;

    private String[] looks;
    private ViewGroup autoLocationRow;
    private ViewGroup locationRow;
    private ViewGroup lookRow;
    private ViewGroup forecastRow;
    private MaterialSwitch switchEnabled;
    private MaterialSwitch switchAutoPlace;
    private MaterialSwitch switchForecast;
    private TextView tvAutoLocation;
    private TextView tvPlaceName;
    private TextView tvLook;

    // From Android 13 the notification isn't shown without the permission: refused, it is turned back off
    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    this.switchEnabled.setChecked(false);
                }
            });

    // Without the permission the current location can't be found: refused, the switch goes off and a place is to be
    // picked
    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                boolean answer = this.answerWhenLocated;
                this.answerWhenLocated = false;
                if (!AppUtils.mayLocate(requireContext())) {
                    this.switchAutoPlace.setChecked(false);
                } else if (answer) {
                    answer(this.answerApply);
                }
            });

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Activity activity = getActivity();
        this.listener = activity instanceof OnNotificationScreenListener ? (OnNotificationScreenListener) activity : null;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requireActivity().setTitle(R.string.pref_notification);
        AppSettings settings = AppSettings.getInstance();
        int chosenId;
        if (bundle == null) {
            this.enabled = settings.isStatusBarNotificationOn();
            chosenId = settings.getStatusBarNotificationPlaceId();
            // As saved, also while the device's position can't be found now: the screen changes only what is changed
            // on it
            this.autoLocation = isAutomatic(chosenId);
            this.showForecast = settings.isStatusBarNotificationForecastShown();
            this.look = settings.getStatusBarNotificationLook();
        } else {
            this.enabled = bundle.getBoolean(STATE_ENABLED);
            this.autoLocation = bundle.getBoolean(STATE_AUTO_LOCATION);
            chosenId = bundle.getInt(STATE_LOCATION_ID, -1);
            this.showForecast = bundle.getBoolean(STATE_SHOW_FORECAST);
            this.look = bundle.getInt(STATE_LOOK);
        }
        // Also a place picked in the search, kept since; gone when cleaned up as unused while the app was let go
        Place place = chosenPlace(chosenId, settings::getPlace);
        this.locationId = place != null ? place.id : -1;
        this.locationName = place != null ? place.name : null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        return inflater.inflate(R.layout.fragment_notification_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        SettingsSegments.setListPadding(view, true);
        ViewGroup enabledRow = view.findViewById(R.id.llNotificationOn);
        this.autoLocationRow = view.findViewById(R.id.llNotificationAutoLocation);
        this.locationRow = view.findViewById(R.id.llNotificationLocation);
        this.lookRow = view.findViewById(R.id.llNotificationLook);
        this.forecastRow = view.findViewById(R.id.llNotificationForecast);
        this.switchEnabled = view.findViewById(R.id.swNotificationOn);
        this.switchAutoPlace = view.findViewById(R.id.swNotificationAutoLocation);
        this.switchForecast = view.findViewById(R.id.swNotificationForecast);
        this.tvAutoLocation = view.findViewById(R.id.tvNotificationAutoLocation);
        this.tvPlaceName = view.findViewById(R.id.tvNotificationLocation);
        this.tvLook = view.findViewById(R.id.tvNotificationLook);
        this.looks = getResources().getStringArray(R.array.notification_looks);

        // One group, the place's row always between the middle ones: closed, the rows round it meet
        SettingsSegments.apply(enabledRow, true, false);
        SettingsSegments.apply(this.autoLocationRow, false, false);
        SettingsSegments.apply(this.locationRow, false, false);
        SettingsSegments.apply(this.lookRow, false, false);
        SettingsSegments.apply(this.forecastRow, false, true);

        // The values before the switches listen, so that no permission is asked for as the screen opens
        this.switchEnabled.setChecked(this.enabled);
        this.switchAutoPlace.setChecked(this.autoLocation);
        this.switchForecast.setChecked(this.showForecast);
        showLocationName();
        this.locationRow.getLayoutParams().height = this.autoLocation ? 0 : this.locationRow.getMinimumHeight();
        this.tvLook.setText(this.looks[this.look]);
        showEnabled();

        // The switches don't take taps themselves: their rows toggle them
        enabledRow.setOnClickListener(row -> this.switchEnabled.toggle());
        this.autoLocationRow.setOnClickListener(row -> this.switchAutoPlace.toggle());
        this.forecastRow.setOnClickListener(row -> this.switchForecast.toggle());
        this.locationRow.setOnClickListener(row -> selectLocation());
        this.lookRow.setOnClickListener(row -> chooseLook());
        this.switchEnabled.setOnCheckedChangeListener((button, isChecked) -> onEnabledChanged(isChecked));
        this.switchAutoPlace.setOnCheckedChangeListener((button, isChecked) -> onAutoLocationChanged(isChecked));
        this.switchForecast.setOnCheckedChangeListener((button, isChecked) -> this.showForecast = isChecked);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        // From the values rather than the views, which are gone while the search covers the screen
        state.putBoolean(STATE_ENABLED, this.enabled);
        state.putBoolean(STATE_AUTO_LOCATION, this.autoLocation);
        state.putInt(STATE_LOCATION_ID, this.locationId);
        state.putBoolean(STATE_SHOW_FORECAST, this.showForecast);
        state.putInt(STATE_LOOK, this.look);
    }

    private void onEnabledChanged(boolean on) {
        this.enabled = on;
        showEnabled();
        if (on && Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            this.notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // The rows under the first greyed while the notification is off, the current location's summary also while that is
    private void showEnabled() {
        AppUtils.setAllEnabled(this.autoLocationRow, this.enabled);
        AppUtils.setAllEnabled(this.locationRow, this.enabled);
        AppUtils.setAllEnabled(this.lookRow, this.enabled);
        AppUtils.setAllEnabled(this.forecastRow, this.enabled);
        this.tvAutoLocation.setEnabled(this.enabled && this.autoLocation);
    }

    // --- The place ---

    // The current location closes the place's row; turned off, the row opens for a place to be picked anew
    private void onAutoLocationChanged(boolean on) {
        this.autoLocation = on;
        this.tvAutoLocation.setEnabled(this.enabled && on);
        if (on) {
            if (!AppUtils.mayLocate(requireContext())) {
                this.locationPermissionLauncher.launch(AppUtils.LOCATION_PERMISSIONS);
            }
            this.locationId = -1;
            this.locationName = null;
            HeightAnimation.collapse(this.locationRow);
        } else {
            showLocationName();
            HeightAnimation.expand(this.locationRow);
        }
    }

    private void showLocationName() {
        this.tvPlaceName.setText(this.locationName != null ? this.locationName : getString(R.string.place_not_chosen));
    }

    private void selectLocation() {
        if (this.listener == null) {
            Log.w(TAG, "No place search: the activity isn't the screen's listener");
            return;
        }
        this.listener.onPickPlace();
    }

    private void answer(boolean apply) {
        if (this.listener == null) {
            Log.w(TAG, "Not applied: the activity isn't the screen's listener");
            return;
        }
        this.listener.onAnswer(apply);
    }

    /** The place picked in the search, which covers the screen meanwhile: the views may be gone, made again with it */
    public void setNotificationLocation(Place place) {
        this.locationId = place.id;
        this.locationName = place.name;
        // The notification shows the forecast the app keeps for its place; one unused is cleaned up at the app's next
        // start
        AppSettings settings = AppSettings.getInstance();
        if (!settings.hasCachedLocation(place.id)) {
            settings.storePlace(place);
        }
        if (getView() != null) {
            showLocationName();
        }
    }

    // --- The look ---

    // The looks on glass at their row, as the other settings' choices
    private void chooseLook() {
        List<GlassMenu.Item> items = new ArrayList<>(this.looks.length);
        for (int i = 0; i < this.looks.length; i++) {
            int icon = i == WeatherNotification.LOOK_SKY ? R.drawable.ic_menu_sky : R.drawable.ic_menu_device;
            items.add(new GlassMenu.Item(icon, this.looks[i], null, i == this.look, false));
        }
        SettingsChoiceMenu.show(this.lookRow, items, which -> {
            this.look = which;
            this.tvLook.setText(this.looks[which]);
        });
    }

    // --- Applying ---

    /**
     * Whether the tick may apply the screen; otherwise it asks for a place, one picked in the search or the current
     * location, the activity being told autoLocate once the current location is taken
     */
    public boolean checkPlace(boolean autoLocate) {
        if (mayApply(this.enabled, this.autoLocation, this.locationId)) {
            return true;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.no_place_message)
                .setPositiveButton(R.string.no_place_choose, (dialog, which) -> selectLocation())
                .setNegativeButton(R.string.no_place_automatic, (dialog, which) -> useAutoLocation(autoLocate))
                .show();
        return false;
    }

    // The current location turned on as by its row; the activity is told at once, or once the permission the row asks
    // for is granted
    private void useAutoLocation(boolean apply) {
        boolean permitted = AppUtils.mayLocate(requireContext());
        this.answerWhenLocated = !permitted;
        this.answerApply = apply;
        this.switchAutoPlace.setChecked(true);
        if (permitted) {
            answer(apply);
        }
    }

    /** Saves the values and has the notification drawn with them, by the bar's tick only */
    public void apply() {
        AppSettings.getInstance().saveStatusBarNotification(this.enabled, this.autoLocation ? -1 : this.locationId, this.showForecast, this.look);
        NotificationUpdater.update(requireActivity());
    }

    // --- The decisions ---

    /** Whether the tick may apply: the notification off, or on with the current location or a place chosen */
    static boolean mayApply(boolean enabled, boolean autoLocation, int locationId) {
        return !enabled || autoLocation || locationId != -1;
    }

    /** Whether the saved place's id stands for the current location: -1 */
    static boolean isAutomatic(int savedLocationId) {
        return savedLocationId == -1;
    }

    /**
     * The place of the id from those the app keeps, null for none: -1, though the app keeps the current location under
     * it, and a place it no longer keeps
     */
    static Place chosenPlace(int locationId, IntFunction<Place> stored) {
        return locationId == -1 ? null : stored.apply(locationId);
    }
}
