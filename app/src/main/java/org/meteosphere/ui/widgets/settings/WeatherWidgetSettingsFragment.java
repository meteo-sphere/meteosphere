package org.meteosphere.ui.widgets.settings;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.R;
import org.meteosphere.background.WidgetUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.BackgroundWork;
import org.meteosphere.ui.glass.GlassMenu;
import org.meteosphere.ui.settings.SettingsChoiceMenu;
import org.meteosphere.ui.widgets.WeatherWidget1x1;
import org.meteosphere.ui.widgets.WeatherWidget2x1;
import org.meteosphere.ui.widgets.WeatherWidget4x1;
import org.meteosphere.ui.widgets.WeatherWidgetClock;
import org.meteosphere.ui.widgets.WeatherWidget4x2;
import org.meteosphere.ui.widgets.WeatherWidget4x3;
import org.meteosphere.ui.widgets.WeatherWidgets;
import org.meteosphere.ui.widgets.WidgetKind;
import org.meteosphere.util.AppUtils;

/**
 * A weather widget's settings: its kinds, by hours and by days, as previews on the home screen's wallpaper pinned over
 * the rows; its place, what it shows besides the forecast, its glass and the app its clock opens. The choices not
 * applied yet come back when the screen is made again; WeatherWidgetSettingsActivity has them applied
 */
public final class WeatherWidgetSettingsFragment extends Fragment {

    private static final String ARG_WIDGET_ID = "widget_id";

    private static final String STATE_PAGE = "page";
    private static final String STATE_AUTO_LOCATION = "auto_location";
    private static final String STATE_LOCATION_ID = "location_id";
    private static final String STATE_LOCATION_NAME = "location_name";
    private static final String STATE_CONTENT_COLOR = "content_color";
    private static final String STATE_GLASS_COLOR = "glass_color";
    private static final String STATE_NOW_SHOWN = "now_shown";
    private static final String STATE_SHOW_WIND = "show_wind";
    private static final String STATE_SHOW_PRECIPITATION = "show_precipitation";
    private static final String STATE_CLOCK_APP = "clock_app";

    /** The glasses as R.array.widget_glass_names names them */
    static final int GLASS_DARK = 0;
    static final int GLASS_LIGHT = 1;

    // Round the stage, and under the preview's buttons
    private static final int STAGE_MARGIN_DP = 16;
    private static final int TYPES_TOP_DP = 12;
    private static final int TYPES_BOTTOM_DP = 4;

    // The alarm the previews with a clock show
    private static final String PREVIEW_ALARM = "7:00";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BackgroundWork appsWork = new BackgroundWork(this.executor);

    private int widgetId;
    // The class name of the widget's provider, which tells the size of widget
    private String provider;
    private boolean hasClock;
    private int[] kinds;
    // -1 for the current location, also while the switch is off till a place is picked
    private int locationId;
    // The name in the place's row, null for none
    private String locationName;
    // The place before the current location was turned on without the permission: back to it if that is denied
    private int locationBefore = -1;
    private int fontColor;
    private int backgroundColor;
    private String clockApp;
    // The launchable apps by name, null till loaded
    private List<App> apps;
    private boolean appsLoading;

    // The previews' sizes come from the screen's size, the widgets' margin on the home screen, the widget's size and the
    // stage's width, in pixels
    private int screenWidth;
    private int screenHeight;
    private int widgetMargin;
    private int widgetWidth;
    private int widgetHeight;
    private int stageWidth;

    private LinearLayout content;
    private ViewPager pager;
    private MaterialButtonToggleGroup typeGroup;
    private SwitchCompat switchAutoPlace;
    private View llChosenPlace;
    private TextView tvPlaceName;
    private SwitchCompat switchCurrentWeather;
    private SwitchCompat switchWind;
    private SwitchCompat switchPrecipitation;
    private TextView tvSummaryGlass;
    private Slider slider;
    private TextView tvClockApplication;

    // Without the permission the current location can't be found: the switch goes off, the place before coming back
    // when the app keeps it
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> granted) -> {
                if (AppUtils.mayLocate(requireContext())) {
                    return;
                }
                this.switchAutoPlace.setChecked(false);
                Place before = this.locationBefore == -1 ? null : AppSettings.getInstance().getPlace(this.locationBefore);
                if (before != null) {
                    this.locationId = before.id;
                    showLocationName(before.name);
                }
            });

    static WeatherWidgetSettingsFragment newInstance(int widgetId) {
        WeatherWidgetSettingsFragment fragment = new WeatherWidgetSettingsFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_WIDGET_ID, widgetId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.widgetId = requireArguments().getInt(ARG_WIDGET_ID);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        // Made again with a screen that finishes at once, the widget gone from the launcher
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(requireContext()).getAppWidgetInfo(this.widgetId);
        if (info == null) {
            return null;
        }
        this.provider = info.provider.getClassName();
        return inflater.inflate(R.layout.fragment_weather_widget_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        this.content = view.findViewById(R.id.llSettingsContent);
        this.hasClock = hasClock(this.provider);
        WidgetConfig preferences = new WidgetConfig(requireContext(), this.widgetId);
        if (bundle == null) {
            this.fontColor = preferences.getContentColor();
            this.backgroundColor = preferences.getGlassColor();
            this.locationId = preferences.getPlaceId();
            this.clockApp = preferences.getClockApp();
            if (this.clockApp.isEmpty() && this.hasClock) {
                this.clockApp = AppUtils.phoneClockPackage(requireContext());
            }
        } else {
            this.fontColor = bundle.getInt(STATE_CONTENT_COLOR);
            this.backgroundColor = bundle.getInt(STATE_GLASS_COLOR);
            this.locationId = bundle.getInt(STATE_LOCATION_ID);
            this.clockApp = bundle.getString(STATE_CLOCK_APP);
        }
        setUpLocation(bundle);
        setUpShown(preferences, bundle);
        setUpLook();
        setUpClock();
        WidgetSettingsRows.segment(this.content);
        this.kinds = kinds(this.provider, this.switchCurrentWeather.isChecked());
        setUpStage(view, bundle != null ? bundle.getInt(STATE_PAGE) : pageOf(this.kinds, preferences.getKind()));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        // Nothing chosen without the view
        if (this.pager == null) {
            return;
        }
        state.putInt(STATE_PAGE, this.pager.getCurrentItem());
        state.putBoolean(STATE_AUTO_LOCATION, this.switchAutoPlace.isChecked());
        state.putInt(STATE_LOCATION_ID, this.locationId);
        state.putString(STATE_LOCATION_NAME, this.locationName);
        state.putInt(STATE_CONTENT_COLOR, this.fontColor);
        state.putInt(STATE_GLASS_COLOR, this.backgroundColor);
        state.putBoolean(STATE_NOW_SHOWN, this.switchCurrentWeather.isChecked());
        state.putBoolean(STATE_SHOW_WIND, this.switchWind.isChecked());
        state.putBoolean(STATE_SHOW_PRECIPITATION, this.switchPrecipitation.isChecked());
        state.putString(STATE_CLOCK_APP, this.clockApp);
    }

    @Override
    public void onDestroyView() {
        // No menu at a row no longer there: the apps are loaded again on the next tap
        this.appsWork.drop();
        this.appsLoading = false;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }

    // --- The place ---

    // The current location, or a place picked in the search; a new widget takes the current location when it can be
    // found
    private void setUpLocation(Bundle state) {
        boolean auto;
        if (state != null) {
            auto = state.getBoolean(STATE_AUTO_LOCATION);
            this.locationName = state.getString(STATE_LOCATION_NAME);
        } else {
            AppSettings settings = AppSettings.getInstance();
            boolean isNew = !settings.getWidgets().contains(this.widgetId);
            auto = isNew ? AppUtils.canLocate(requireContext()) : this.locationId == -1;
            Place place = isNew || auto ? null : settings.getPlace(this.locationId);
            this.locationName = place != null ? place.name : null;
        }
        View autoRow = WidgetSettingsRows.row(this.content, R.id.llAutoPlace, R.drawable.ic_settings_gps, getString(R.string.place_automatic));
        this.switchAutoPlace = WidgetSettingsRows.switchOf(autoRow, auto);
        this.llChosenPlace = WidgetSettingsRows.row(this.content, R.id.llChosenPlace, R.drawable.ic_settings_location, getString(R.string.widget_settings_city));
        this.tvPlaceName = WidgetSettingsRows.summary(this.llChosenPlace, "");
        showLocationName(this.locationName);
        this.llChosenPlace.setVisibility(auto ? View.GONE : View.VISIBLE);
        this.switchAutoPlace.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                if (!AppUtils.mayLocate(requireContext())) {
                    this.locationBefore = this.locationId;
                    this.permissionLauncher.launch(AppUtils.LOCATION_PERMISSIONS);
                }
                this.locationId = -1;
            } else {
                // The place is picked again
                showLocationName(null);
            }
            TransitionManager.beginDelayedTransition(this.content);
            this.llChosenPlace.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            WidgetSettingsRows.segment(this.content);
        });
        this.llChosenPlace.setOnClickListener(view -> ((WidgetSettingsActivity) requireActivity()).openLocationSearch());
    }

    private void showLocationName(String name) {
        this.locationName = name;
        this.tvPlaceName.setText(name != null ? name : getString(R.string.place_not_chosen));
    }

    /** The place picked in the search */
    void setLocation(Place place) {
        this.locationId = place.id;
        showLocationName(place.name);
    }

    // --- What the widget shows ---

    // The weather now over the 4x3's chart, the wind and the precipitation under the charts
    private void setUpShown(WidgetConfig preferences, Bundle state) {
        this.switchCurrentWeather = shownRow(R.id.llShowNow, R.drawable.ic_settings_forecast, R.string.widget_current_weather,
                state != null ? state.getBoolean(STATE_NOW_SHOWN) : preferences.isNowShown(), offersCurrentWeather(this.provider));
        this.switchWind = shownRow(R.id.llShowWind, R.drawable.ic_settings_widget_wind, R.string.widget_settings_show_wind,
                state != null ? state.getBoolean(STATE_SHOW_WIND) : preferences.isShowWind(), isChart(this.provider));
        this.switchPrecipitation = shownRow(R.id.llShowPrecipitation, R.drawable.ic_settings_precipitation, R.string.widget_settings_show_precipitation,
                state != null ? state.getBoolean(STATE_SHOW_PRECIPITATION) : preferences.isShowPrecipitation(), isChart(this.provider));
    }

    // The row's switch, the previews drawn anew as it changes; the row only where the widget has what it shows
    private SwitchCompat shownRow(int id, int icon, int title, boolean checked, boolean shown) {
        View row = WidgetSettingsRows.row(this.content, id, icon, getString(title));
        row.setVisibility(shown ? View.VISIBLE : View.GONE);
        SwitchCompat button = WidgetSettingsRows.switchOf(row, checked);
        button.setOnCheckedChangeListener((view, isChecked) -> redraw());
        return button;
    }

    // --- The look ---

    // The glass, dark or light, chosen on glass at its row, and how transparent it is
    private void setUpLook() {
        String[] glasses = getResources().getStringArray(R.array.widget_glass_names);
        View glassRow = WidgetSettingsRows.row(this.content, R.id.llGlass, R.drawable.ic_settings_theme, getString(R.string.background));
        this.tvSummaryGlass = WidgetSettingsRows.summary(glassRow, glasses[glass(this.backgroundColor)]);
        glassRow.setOnClickListener(view -> {
            int current = glass(this.backgroundColor);
            List<GlassMenu.Item> items = new ArrayList<>();
            items.add(new GlassMenu.Item(R.drawable.ic_menu_dark, glasses[GLASS_DARK], null, current == GLASS_DARK, false));
            items.add(new GlassMenu.Item(R.drawable.ic_menu_light, glasses[GLASS_LIGHT], null, current == GLASS_LIGHT, false));
            SettingsChoiceMenu.show(glassRow, items, which -> {
                if (which == glass(this.backgroundColor)) {
                    return;
                }
                Context context = requireContext();
                this.backgroundColor = backgroundColor(ContextCompat.getColor(context, which == GLASS_DARK ? R.color.widget_base_dark : R.color.widget_base_light),
                        this.slider.getValue());
                this.fontColor = ContextCompat.getColor(context, which == GLASS_DARK ? R.color.widget_base_text_dark : R.color.widget_base_text_light);
                this.tvSummaryGlass.setText(glasses[which]);
                redraw();
            });
        });
        View transparencyRow = WidgetSettingsRows.row(this.content, R.id.llTransparency, R.drawable.ic_settings_transparency, getString(R.string.widget_transparency));
        TextView value = transparencyRow.findViewById(R.id.tvRowValue);
        this.slider = transparencyRow.findViewById(R.id.slRow);
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        int transparency = WidgetSettingsRows.transparency(Color.alpha(this.backgroundColor));
        this.slider.setValue(transparency);
        value.setText(AppUtils.percent(locale, transparency));
        this.slider.addOnChangeListener((view, progress, fromUser) -> {
            value.setText(AppUtils.percent(locale, Math.round(progress)));
            this.backgroundColor = backgroundColor(this.backgroundColor, progress);
            fadeGlass();
        });
    }

    /** The glass of the background's colour: the dark one's green is 0 */
    static int glass(int backgroundColor) {
        return (backgroundColor >> 8 & 0xff) == 0 ? GLASS_DARK : GLASS_LIGHT;
    }

    /** The background of the colour given, as transparent as given, 0 to 100 % */
    static int backgroundColor(int color, float transparency) {
        return WidgetSettingsRows.alpha(transparency) << 24 | color & 0xffffff;
    }

    // --- The clock ---

    // The app a tap on the clock opens, for the widgets with a clock
    private void setUpClock() {
        View clockRow = WidgetSettingsRows.row(this.content, R.id.llClockApp, R.drawable.ic_settings_clock, getString(R.string.widget_clock_app));
        clockRow.setVisibility(this.hasClock ? View.VISIBLE : View.GONE);
        this.tvClockApplication = WidgetSettingsRows.summary(clockRow, appLabel(this.clockApp));
        clockRow.setOnClickListener(view -> chooseClockApplication(clockRow));
    }

    // The app's name, R.string.widget_clock_app_none for none or while it isn't installed
    private CharSequence appLabel(String packageName) {
        PackageManager packageManager = requireContext().getPackageManager();
        if (!packageName.isEmpty()) {
            try {
                return packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0));
            } catch (PackageManager.NameNotFoundException e) {
                // Uninstalled since it was chosen
            }
        }
        return getString(R.string.widget_clock_app_none);
    }

    // The launchable apps on glass at the row; asking the system for them takes a while, so they are loaded off the
    // main thread the first time
    private void chooseClockApplication(View row) {
        if (this.apps != null) {
            showApps(row);
            return;
        }
        if (this.appsLoading) {
            return;
        }
        this.appsLoading = true;
        PackageManager packageManager = requireContext().getPackageManager();
        this.appsWork.run(() -> launchableApps(packageManager), loaded -> {
            this.appsLoading = false;
            this.apps = loaded;
            // Not over the search or another screen opened meanwhile
            if (isResumed() && !isHidden()) {
                showApps(row);
            }
        });
    }

    private void showApps(View row) {
        List<GlassMenu.Item> items = new ArrayList<>(this.apps.size());
        for (App app : this.apps) {
            items.add(new GlassMenu.Item(0, app.label, null, app.packageName.equals(this.clockApp), false));
        }
        SettingsChoiceMenu.show(row, items, which -> {
            App app = this.apps.get(which);
            this.clockApp = app.packageName;
            this.tvClockApplication.setText(app.label);
        });
    }

    /** A launchable app */
    private static final class App {
        final String packageName;
        final String label;

        App(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    // One for each app, though it may have several launchable activities, by name
    private static List<App> launchableApps(PackageManager packageManager) {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, App> apps = new HashMap<>();
        for (ResolveInfo info : packageManager.queryIntentActivities(launcher, 0)) {
            String packageName = info.activityInfo.packageName;
            if (!apps.containsKey(packageName)) {
                apps.put(packageName, new App(packageName, info.activityInfo.applicationInfo.loadLabel(packageManager).toString()));
            }
        }
        List<App> sorted = new ArrayList<>(apps.values());
        sorted.sort(Comparator.comparing((App app) -> app.label, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    // --- The preview ---

    // The previews on the wallpaper, pinned over the rows, with the kinds' buttons under them
    private void setUpStage(View view, int page) {
        ViewGroup holder = view.findViewById(R.id.flStageHolder);
        getLayoutInflater().inflate(R.layout.widget_settings_stage, holder, true);
        this.pager = holder.findViewById(R.id.vpWidgetSamples);
        this.typeGroup = holder.findViewById(R.id.tgWidgetType);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int margin = dp(STAGE_MARGIN_DP);
        this.screenWidth = metrics.widthPixels;
        this.screenHeight = metrics.heightPixels;
        this.widgetMargin = getResources().getDimensionPixelSize(R.dimen.widget_margin);
        this.stageWidth = this.screenWidth - 2 * margin;
        Point widgetSize = AppUtils.widgetSize(requireContext(), this.widgetId);
        this.widgetWidth = widgetSize.x;
        this.widgetHeight = widgetSize.y;

        PreviewSize size = previewSize(this.kinds[0], this.screenWidth, this.screenHeight, this.widgetMargin, this.widgetWidth,
                this.widgetHeight, this.hasClock);
        ViewGroup.MarginLayoutParams pagerParams = (ViewGroup.MarginLayoutParams) this.pager.getLayoutParams();
        pagerParams.height = pagerHeight(size.pagerHeight, this.stageWidth, homeWidth(this.screenWidth, this.widgetMargin));
        pagerParams.topMargin = margin;
        pagerParams.bottomMargin = margin;
        holder.findViewById(R.id.wallpaperWindow).getLayoutParams().height = pagerParams.height + pagerParams.topMargin + pagerParams.bottomMargin;

        ((ViewGroup.MarginLayoutParams) this.typeGroup.getLayoutParams()).setMargins(margin, dp(TYPES_TOP_DP), margin, dp(TYPES_BOTTOM_DP));
        String[] types = getResources().getStringArray(R.array.widget_forecast_kinds);
        ((MaterialButton) holder.findViewById(R.id.btnTypeFirst)).setText(types[0]);
        ((MaterialButton) holder.findViewById(R.id.btnTypeSecond)).setText(types[1]);
        this.typeGroup.setVisibility(this.kinds.length > 1 ? View.VISIBLE : View.GONE);

        this.pager.setAdapter(new Pages());
        this.pager.setCurrentItem(page, false);
        this.typeGroup.check(button(page));
        this.typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                this.pager.setCurrentItem(checkedId == R.id.btnTypeFirst ? 0 : 1);
            }
        });
        this.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                WeatherWidgetSettingsFragment.this.typeGroup.check(button(position));
            }
        });
    }

    private static int button(int page) {
        return page == 0 ? R.id.btnTypeFirst : R.id.btnTypeSecond;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // The previews drawn anew, the page shown kept; the 4x3's kinds follow the weather now
    private void redraw() {
        this.kinds = kinds(this.provider, this.switchCurrentWeather.isChecked());
        this.pager.getAdapter().notifyDataSetChanged();
    }

    // The previews' glass alone, as the widget sets it: they aren't drawn anew at each step of the slider's drag
    private void fadeGlass() {
        float alpha = Color.alpha(this.backgroundColor) / 255f;
        for (int i = 0; i < this.pager.getChildCount(); i++) {
            View glass = this.pager.getChildAt(i).findViewById(R.id.imvBackground);
            if (glass != null) {
                glass.setAlpha(alpha);
            }
        }
    }

    // The widget of the kind at its size, at the top of its page in the middle, scaled down to the stage where wider
    private View page(int kind) {
        Context context = requireContext().getApplicationContext();
        FrameLayout page = new FrameLayout(requireContext());
        PreviewSize size = previewSize(kind, this.screenWidth, this.screenHeight, this.widgetMargin, this.widgetWidth, this.widgetHeight,
                this.hasClock);
        WeatherWidgets.Request request = new WeatherWidgets.Request(kind, size.width, size.height);
        request.place = WidgetSample.place(context);
        request.backgroundColor = this.backgroundColor;
        request.showClock = this.hasClock;
        request.showWind = this.switchWind.isChecked();
        request.showPrecipitation = this.switchPrecipitation.isChecked();
        // The moment the sample's forecast is made up from
        Calendar time = Calendar.getInstance();
        time.set(2015, Calendar.MAY, 11, 10, 9);
        View preview = WeatherWidgets.preview(context, request, time, PREVIEW_ALARM, getString(R.string.place_title));
        if (preview != null) {
            page.addView(preview, new FrameLayout.LayoutParams(size.width, size.height, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            float scale = scale(this.stageWidth, size.width);
            preview.setPivotX(size.width / 2f);
            preview.setPivotY(0);
            preview.setScaleX(scale);
            preview.setScaleY(scale);
        }
        return page;
    }

    // A page for each kind, all of them made anew on a change
    private final class Pages extends PagerAdapter {

        @Override
        public int getCount() {
            return WeatherWidgetSettingsFragment.this.kinds.length;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View page = page(WeatherWidgetSettingsFragment.this.kinds[position]);
            container.addView(page);
            return page;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }
    }

    // --- Applying ---

    /** Writes the settings and has the widget drawn with them, the screen finishing; asks for a place when there is none */
    void apply() {
        Activity activity = requireActivity();
        // Removed from the home screen meanwhile, maybe before the settings got their view: nothing to set up, and the
        // app mustn't keep it in its list
        if (getView() == null || AppWidgetManager.getInstance(activity).getAppWidgetInfo(this.widgetId) == null) {
            activity.finish();
            return;
        }
        if (!this.switchAutoPlace.isChecked() && this.locationId == -1) {
            ((WidgetSettingsActivity) activity).askForLocation(() -> {
                this.switchAutoPlace.setChecked(true);
                // Otherwise the permission is asked for, and the settings are applied again
                if (AppUtils.mayLocate(activity)) {
                    apply();
                }
            });
            return;
        }
        new WidgetConfig(activity, this.widgetId).edit()
                .setKind(this.kinds[this.pager.getCurrentItem()])
                .setPlaceId(this.locationId)
                .setContentColor(this.fontColor)
                .setGlassColor(this.backgroundColor)
                .setClockShown(this.hasClock)
                .setClockApp(this.clockApp)
                .setNowShown(this.switchCurrentWeather.isChecked())
                .setShowWind(this.switchWind.isChecked())
                .setShowPrecipitation(this.switchPrecipitation.isChecked())
                .apply();
        activity.setResult(Activity.RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, this.widgetId));
        AppSettings.getInstance().putWidgetId(this.widgetId);
        Context context = activity.getApplicationContext();
        int widgetId = this.widgetId;
        // On a thread of its own: queued work may wait long, and the launcher shows its spinner till the widget is drawn
        new Thread(() -> {
            WidgetUpdater.drawNow(context, widgetId, WidgetUpdater.STATE_LOADING);
            WidgetUpdater.refresh(context, widgetId, true);
        }).start();
        activity.finish();
    }

    // --- The decisions ---

    /**
     * The kinds a widget of the provider shows, by hours first; the 4x3's with the weather now over the chart while
     * that is on
     */
    static int[] kinds(String provider, boolean showCurrentWeather) {
        if (provider.equals(WeatherWidget1x1.class.getName())) {
            return new int[]{WidgetKind.ICON_HOURLY, WidgetKind.ICON_DAILY};
        }
        if (provider.equals(WeatherWidget2x1.class.getName())) {
            return new int[]{WidgetKind.FACT_HOURLY, WidgetKind.FACT_DAILY};
        }
        if (provider.equals(WeatherWidget4x1.class.getName())) {
            return new int[]{WidgetKind.FACT_AND_FORECAST_HOURLY, WidgetKind.FACT_AND_FORECAST_DAILY};
        }
        if (provider.equals(WeatherWidgetClock.class.getName())) {
            return new int[]{WidgetKind.NOW_AND_CLOCK};
        }
        if (provider.equals(WeatherWidget4x3.class.getName()) && showCurrentWeather) {
            return new int[]{WidgetKind.CHART_HOURLY_WITH_NOW, WidgetKind.CHART_DAILY_WITH_NOW};
        }
        return new int[]{WidgetKind.CHART_HOURLY, WidgetKind.CHART_DAILY};
    }

    /** Whether the widget has a clock: the 4x3 and the weather now with the clock */
    static boolean hasClock(String provider) {
        return provider.equals(WeatherWidget4x3.class.getName()) || provider.equals(WeatherWidgetClock.class.getName());
    }

    /** Whether the widget is a chart, with the wind and the precipitation under it or not */
    static boolean isChart(String provider) {
        return provider.equals(WeatherWidget4x2.class.getName()) || provider.equals(WeatherWidget4x3.class.getName());
    }

    /** Whether the widget may have the weather now over its chart: the 4x3 */
    static boolean offersCurrentWeather(String provider) {
        return provider.equals(WeatherWidget4x3.class.getName());
    }

    /** The kind's page among the kinds, the first when it isn't there */
    static int pageOf(int[] kinds, int kind) {
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] == kind) {
                return i;
            }
        }
        return 0;
    }

    /** A preview's size in pixels, and the pager's height it is made for before the stage scales it down */
    static final class PreviewSize {
        final int width;
        final int height;
        final int pagerHeight;

        PreviewSize(int width, int height, int pagerHeight) {
            this.width = width;
            this.height = height;
            this.pagerHeight = pagerHeight;
        }
    }

    /** A widget's width on the home screen, in pixels */
    static int homeWidth(int screenWidth, int widgetMargin) {
        return screenWidth - 2 * widgetMargin;
    }

    /**
     * The preview of the kind, from the screen's size, the widgets' margin on the home screen and the widget's size,
     * in pixels, and whether it has a clock
     */
    static PreviewSize previewSize(int kind, int screenWidth, int screenHeight, int widgetMargin, int widgetWidth, int widgetHeight,
                                   boolean hasClock) {
        int home = homeWidth(screenWidth, widgetMargin);
        int small = (int) (screenHeight / 5.5);
        switch (kind) {
            case WidgetKind.ICON_HOURLY:
            case WidgetKind.ICON_DAILY:
                return new PreviewSize(screenWidth / 4, small, 2 * small);
            case WidgetKind.FACT_AND_FORECAST_HOURLY:
            case WidgetKind.FACT_AND_FORECAST_DAILY:
                return new PreviewSize(Math.min(home, (int) (0.9 * screenWidth)), small, 2 * small);
            case WidgetKind.FACT_HOURLY:
            case WidgetKind.FACT_DAILY:
                return new PreviewSize((int) (screenWidth / 2.2), small, 2 * small);
            case WidgetKind.NOW_AND_CLOCK:
                // Shaped as the widget is, or as 360 by 188 while the launcher hasn't told its size
                int height = widgetWidth == 0 || widgetHeight == 0 ? Math.round(home * 188 / 360f)
                        : Math.round(home * widgetHeight / (float) widgetWidth);
                return new PreviewSize(home, height, height);
            default:
                int chart = hasClock ? (int) (0.9 * home) : widgetHeight;
                return new PreviewSize(home, chart, chart);
        }
    }

    /** How much a preview of the width is scaled down to fit the stage, 1 where it fits */
    static float scale(int stageWidth, int width) {
        return Math.min(1f, stageWidth / (float) width);
    }

    /** The pager's height for the height it is made for, scaled down as a widget as wide as on the home screen */
    static int pagerHeight(int madeFor, int stageWidth, int homeWidth) {
        return Math.round(madeFor * scale(stageWidth, homeWidth));
    }
}
