package org.meteosphere.ui.widgets.settings;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.slider.Slider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.glass.GlassMenu;
import org.meteosphere.ui.settings.SettingsChoiceMenu;
import org.meteosphere.ui.widgets.EarthquakeWidget;
import org.meteosphere.ui.common.BackgroundWork;
import org.meteosphere.util.AppUtils;

/**
 * The earthquake widget's location, the current one or a city found by the search, its period and whether the latest or
 * the strongest come first, and its background, dark or light and how transparent, under a preview of it on the home
 * screen's wallpaper; laid out as the weather widgets' settings. AstroWidgetSettingsActivity keeps the location and the
 * background for the Sun and Moon widgets.
 */
public class EarthquakeWidgetSettingsActivity extends WidgetSettingsActivity {

    private static final int[] PERIOD_DAYS = {1, 7, 30};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BackgroundWork previewWork = new BackgroundWork(this.executor);

    private WidgetConfig widgetConfig;
    private int backgroundColor;
    private int fontColor;
    // The chosen city, null for none while the current location is off
    private Place chosenLocation;
    private int days;
    private boolean strongest;
    // The lists of the periods loaded, by the days; a null list when it couldn't be
    private final Map<Integer, List<Earthquake>> loadedLists = new HashMap<>();

    private FrameLayout previewContainer;
    private View wallpaperWindow;
    private LinearLayout content;
    private View searchContainer;
    private SwitchCompat switchAutoPlace;
    private View llChosenPlace;
    private TextView tvPlaceName;
    private TextView tvSummaryPeriod;
    private TextView tvSummaryShow;
    private TextView tvSummaryGlass;

    // Without the permission the current location can't be found, so it is turned off again
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> granted) -> {
                if (!AppUtils.mayLocate(this)) {
                    this.switchAutoPlace.setChecked(false);
                }
            });

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (!openScreen(R.layout.activity_earthquake_widget_settings, titleRes())) {
            return;
        }
        this.widgetConfig = new WidgetConfig(this, this.appWidgetId);
        this.backgroundColor = this.widgetConfig.getGlassColor();
        this.fontColor = this.widgetConfig.getContentColor();
        this.previewContainer = findViewById(R.id.flQuakeWidgetPreview);
        this.wallpaperWindow = findViewById(R.id.wallpaperWindow);
        this.content = findViewById(R.id.llSettingsContent);
        this.searchContainer = findViewById(R.id.flQuakeLocationSearch);

        setUpLocation();
        setUpOwnSettings();
        setUpLook();
        WidgetSettingsRows.segment(this.content);

        // The wallpaper as tall as the preview with its margins
        ViewGroup.MarginLayoutParams preview = (ViewGroup.MarginLayoutParams) this.previewContainer.getLayoutParams();
        this.wallpaperWindow.getLayoutParams().height = preview.height + preview.topMargin + preview.bottomMargin;

        loadPreview();
    }

    // Dark or light, chosen on glass at its row, and how transparent
    private void setUpLook() {
        String[] themes = getResources().getStringArray(R.array.widget_glass_names);
        View themeRow = WidgetSettingsRows.row(this.content, R.id.llGlass, R.drawable.ic_settings_theme, getString(R.string.background));
        this.tvSummaryGlass = WidgetSettingsRows.summary(themeRow, themes[themeIndex()]);
        themeRow.setOnClickListener(view -> {
            List<GlassMenu.Item> items = new ArrayList<>();
            items.add(new GlassMenu.Item(R.drawable.ic_menu_dark, themes[0], null, themeIndex() == 0, false));
            items.add(new GlassMenu.Item(R.drawable.ic_menu_light, themes[1], null, themeIndex() == 1, false));
            SettingsChoiceMenu.show(themeRow, items, which -> {
                int color = ContextCompat.getColor(this, which == 0 ? R.color.widget_base_dark : R.color.widget_base_light);
                this.backgroundColor = Color.argb(Color.alpha(this.backgroundColor), Color.red(color), Color.green(color), Color.blue(color));
                this.fontColor = ContextCompat.getColor(this, which == 0 ? R.color.widget_base_text_dark : R.color.widget_base_text_light);
                showTheme();
                showPreview();
            });
        });
        View transparencyRow = WidgetSettingsRows.row(this.content, R.id.llTransparency, R.drawable.ic_settings_transparency, getString(R.string.widget_transparency));
        TextView value = transparencyRow.findViewById(R.id.tvRowValue);
        Slider slider = transparencyRow.findViewById(R.id.slRow);
        int transparency = WidgetSettingsRows.transparency(Color.alpha(this.backgroundColor));
        slider.setValue(transparency);
        value.setText(AppUtils.percent(getResources().getConfiguration().getLocales().get(0), transparency));
        slider.addOnChangeListener((view, progress, fromUser) -> {
            value.setText(AppUtils.percent(getResources().getConfiguration().getLocales().get(0), Math.round(progress)));
            this.backgroundColor = Color.argb(WidgetSettingsRows.alpha(progress), Color.red(this.backgroundColor), Color.green(this.backgroundColor), Color.blue(this.backgroundColor));
            // Only the background's alpha, as the widget sets it: the preview isn't made anew at each step of the drag.
            // One still loading gets it when it comes
            ImageView background = this.previewContainer.findViewById(R.id.imvBackground);
            if (background != null) {
                background.setImageAlpha(Color.alpha(this.backgroundColor));
            }
        });
    }

    protected int titleRes() {
        return R.string.earthquake_widget_settings;
    }

    // The real list of the period in the preview, the saved one when it is fresh
    protected void loadPreview() {
        final int requestDays = this.days;
        if (this.loadedLists.containsKey(requestDays)) {
            showPreview();
            return;
        }
        Context context = getApplicationContext();
        this.previewWork.run(() -> EarthquakeWidget.load(context, requestDays), loaded -> {
            this.loadedLists.put(requestDays, loaded);
            showPreview();
        });
    }

    // The period, a day, a week or a month, and the latest or the strongest first, chosen on glass at their rows
    protected void setUpOwnSettings() {
        this.days = this.widgetConfig.getQuakeDays();
        this.strongest = this.widgetConfig.isQuakeStrongest();
        String[] periods = {getString(R.string.earthquakes_period_day), getString(R.string.earthquakes_period_week),
                getString(R.string.earthquakes_period_month)};
        String[] orders = {getString(R.string.earthquake_widget_show_latest), getString(R.string.earthquake_widget_show_strongest)};
        View periodRow = WidgetSettingsRows.row(this.content, R.id.llQuakePeriod, R.drawable.ic_settings_quake_period, getString(R.string.earthquake_widget_period));
        View showRow = WidgetSettingsRows.row(this.content, R.id.llQuakeShow, R.drawable.ic_settings_quake_order, getString(R.string.earthquake_widget_show));
        this.tvSummaryPeriod = WidgetSettingsRows.summary(periodRow, "");
        this.tvSummaryShow = WidgetSettingsRows.summary(showRow, "");
        showList(periods, orders);
        periodRow.setOnClickListener(view -> SettingsChoiceMenu.show(periodRow, choices(periods, periodIndex()), which -> {
            this.days = PERIOD_DAYS[which];
            showList(periods, orders);
            loadPreview();
        }));
        showRow.setOnClickListener(view -> SettingsChoiceMenu.show(showRow, choices(orders, this.strongest ? 1 : 0), which -> {
            this.strongest = which == 1;
            showList(periods, orders);
            showPreview();
        }));
    }

    private static List<GlassMenu.Item> choices(String[] titles, int chosen) {
        List<GlassMenu.Item> items = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            items.add(new GlassMenu.Item(0, titles[i], null, i == chosen, false));
        }
        return items;
    }

    private int periodIndex() {
        return this.days == 30 ? 2 : (this.days == 7 ? 1 : 0);
    }

    private void showList(String[] periods, String[] orders) {
        this.tvSummaryPeriod.setText(periods[periodIndex()]);
        this.tvSummaryShow.setText(orders[this.strongest ? 1 : 0]);
    }

    // A new widget takes the current location when it can be found, as a new weather widget does
    private void setUpLocation() {
        View autoRow = WidgetSettingsRows.row(this.content, R.id.llAutoPlace, R.drawable.ic_settings_gps, getString(R.string.place_automatic));
        this.llChosenPlace = WidgetSettingsRows.row(this.content, R.id.llChosenPlace, R.drawable.ic_settings_location, getString(R.string.widget_settings_city));
        this.tvPlaceName = WidgetSettingsRows.summary(this.llChosenPlace, "");
        boolean isAuto;
        if (this.widgetConfig.hasLocation()) {
            isAuto = this.widgetConfig.getPlaceId() == -1;
            if (!isAuto) {
                this.chosenLocation = EarthquakeWidget.location(this, this.widgetConfig);
            }
        } else {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            isAuto = AppUtils.mayLocate(this) && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        }
        this.switchAutoPlace = WidgetSettingsRows.switchOf(autoRow, isAuto);
        showChosenLocation();
        this.llChosenPlace.setVisibility(isAuto ? View.GONE : View.VISIBLE);
        this.switchAutoPlace.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked && !AppUtils.mayLocate(this)) {
                this.permissionLauncher.launch(AppUtils.LOCATION_PERMISSIONS);
            }
            TransitionManager.beginDelayedTransition(this.content);
            this.llChosenPlace.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            WidgetSettingsRows.segment(this.content);
            showPreview();
        });
        this.llChosenPlace.setOnClickListener(view -> openLocationSearch());
    }

    private void showChosenLocation() {
        this.tvPlaceName.setText(this.chosenLocation == null ? getString(R.string.place_not_chosen) : this.chosenLocation.name);
    }

    // The location the widget will have: the current one, or the chosen city
    private Place previewPlace() {
        if (!this.switchAutoPlace.isChecked()) {
            return this.chosenLocation;
        }
        Place current = AppSettings.getInstance().getCurrentPlace();
        return current == null || (current.latitude == 0 && current.longitude == 0) ? null : current;
    }

    // 0 dark, 1 light: the weather widgets tell them by the background
    private int themeIndex() {
        return Color.red(this.backgroundColor) == 255 ? 1 : 0;
    }

    private void showTheme() {
        this.tvSummaryGlass.setText(getResources().getStringArray(R.array.widget_glass_names)[themeIndex()]);
    }

    protected void showPreview() {
        if (isFinishing()) {
            return;
        }
        int heightDp = Math.round(this.previewContainer.getLayoutParams().height / getResources().getDisplayMetrics().density);
        RemoteViews views = buildPreview(heightDp, this.backgroundColor, this.fontColor, previewPlace());
        if (views == null) {
            return;
        }
        this.previewContainer.removeAllViews();
        // Inflated as the launcher does, without the AppCompat views RemoteViews can't set
        this.previewContainer.addView(views.apply(getApplicationContext(), this.previewContainer));
    }

    /** The widget as it will look, null while its data loads */
    protected RemoteViews buildPreview(int heightDp, int backgroundColor, int fontColor, Place location) {
        if (!this.loadedLists.containsKey(this.days)) {
            return null;
        }
        return EarthquakeWidget.build(this, 0, this.loadedLists.get(this.days), EarthquakeWidget.rowsFor(heightDp),
                backgroundColor, fontColor, location, this.days, this.strongest);
    }

    /** The widget's own settings, written with the rest */
    protected void putOwnSettings(WidgetConfig.Editor editor) {
        editor.setQuakeDays(this.days).setQuakeStrongest(this.strongest);
    }

    /** Draws the widget anew with the settings written */
    protected void redraw() {
        EarthquakeWidget.loadAndUpdate(this);
    }

    @Override
    protected int searchContainer() {
        return R.id.flQuakeLocationSearch;
    }

    // The search in its own frame over the settings
    @Override
    protected void showSettings(boolean shown, FragmentTransaction transaction) {
        this.searchContainer.setVisibility(shown ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onPlacePicked(Place location) {
        this.chosenLocation = location;
        showChosenLocation();
        showPreview();
        closeLocationSearch();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.itemApplyWidgetSettings) {
            if (!this.switchAutoPlace.isChecked() && this.chosenLocation == null) {
                askForLocation(() -> this.switchAutoPlace.setChecked(true));
                return true;
            }
            WidgetConfig.Editor editor = this.widgetConfig.edit();
            if (this.switchAutoPlace.isChecked()) {
                editor.setPlaceId(-1);
            } else {
                editor.setPlaceId(this.chosenLocation.id)
                        .setLocationPlace(this.chosenLocation.name, this.chosenLocation.latitude, this.chosenLocation.longitude,
                                this.chosenLocation.timeZoneId);
            }
            editor.setGlassColor(this.backgroundColor).setContentColor(this.fontColor);
            putOwnSettings(editor);
            editor.apply();
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, this.appWidgetId));
            redraw();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }
}
