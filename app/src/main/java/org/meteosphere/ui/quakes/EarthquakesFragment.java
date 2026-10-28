package org.meteosphere.ui.quakes;

import android.graphics.Rect;
import android.os.Parcelable;
import org.meteosphere.ui.astro.AstroPlacePicker;
import org.meteosphere.ui.glass.GlassStrip;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.data.quake.UsgsEarthquakes;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.widgets.EarthquakeWidget;
import org.meteosphere.ui.common.BackgroundWork;

/**
 * Earthquakes of magnitude 5 and above from USGS for the last day, week or month, with the distance to a location: the
 * one passed in, otherwise the current or the first favorite one. A tab of the main screen and the content of
 * EarthquakesActivity. The list is loaded when the fragment first becomes visible, and the sort goes to the options
 * menu. The list with its map and summary cards is EarthquakeListAdapter's; a list taking a while shows the map waiting
 * over the list's skeleton, and each list moves into place from the one before.
 */
public final class EarthquakesFragment extends Fragment {

    private static final String TAG = "EarthquakesFragment";

    private static final String ARG_LATITUDE = "latitude";
    private static final String ARG_LONGITUDE = "longitude";
    private static final String ARG_LOCATION_NAME = "location_name";
    private static final String ARG_DAYS = "days";
    private static final String ARG_TAB = "tab";
    // The main screen makes its tabs again each time it comes back: its tab's scroll and the earthquake picked on its
    // map kept for the next one
    private static Parcelable tabListState;
    private static String tabPickedId;
    private static final String PREF_DAYS = "org.meteosphere.earthquakes_days";
    private static final long SPINNER_DELAY_MS = 300;
    private static final String PREF_BY_DISTANCE = "org.meteosphere.earthquakes_by_distance";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Results of a request made before the period changed are dropped
    private final BackgroundWork loadWork = new BackgroundWork(this.executor);
    // A load taking a while: the list of the period before goes, and the map waits over the list's skeleton
    private final Runnable showWaiting = () -> {
        if (this.swipeRefresh != null) {
            this.adapter.setWaiting(true);
            this.earthquakes = new ArrayList<>();
            showEarthquakes(EarthquakeListAdapter.MOTION_GROW);
        }
    };

    private boolean isLoading;
    // The lists of the periods loaded lately, so switching back to one shows it at once, with no load at all; kept
    // for the screens made again too, as the main screen's tab, which shows it from its first frame
    private static final java.util.Map<Integer, List<Earthquake>> loadedLists = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> loadedTimes = new java.util.HashMap<>();
    // The period of the list shown, which lags the chips while a new one loads
    private int shownDays;

    private UsgsEarthquakes usgs;
    private EarthquakeFormatter formatter;
    private EarthquakeListAdapter adapter;
    private QuakeListAnimator listAnimator;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private List<Earthquake> earthquakes = new ArrayList<>();
    private int days;
    private boolean sortByDistance;
    private boolean hasReference;
    private double referenceLatitude;
    private double referenceLongitude;
    private String referenceName;
    private boolean isLoaded;
    private View placeView;
    private MaterialButtonToggleGroup sortToggle;
    private int shownLocationId;
    // The scroll to go back to once the list has come
    private Parcelable pendingListState;

    /** Distances to the current or the first favorite location */
    public static EarthquakesFragment newInstance() {
        return new EarthquakesFragment();
    }

    /** Distances to the location */
    public static EarthquakesFragment newInstance(double latitude, double longitude, String locationName) {
        EarthquakesFragment fragment = new EarthquakesFragment();
        Bundle args = new Bundle();
        args.putDouble(ARG_LATITUDE, latitude);
        args.putDouble(ARG_LONGITUDE, longitude);
        args.putString(ARG_LOCATION_NAME, locationName);
        fragment.setArguments(args);
        return fragment;
    }

    /** The main screen's tab, whose scroll is kept for the tab made again */
    public EarthquakesFragment asTab() {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        args.putBoolean(ARG_TAB, true);
        setArguments(args);
        return this;
    }

    private boolean isTab() {
        return getArguments() != null && getArguments().getBoolean(ARG_TAB);
    }

    /** Opens on the period rather than the tab's, and choosing another one leaves the tab's as it is */
    public EarthquakesFragment withDays(int days) {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        args.putInt(ARG_DAYS, days);
        setArguments(args);
        return this;
    }

    private boolean hasOwnDays() {
        return getArguments() != null && getArguments().containsKey(ARG_DAYS);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings settings = AppSettings.getInstance();
        this.usgs = new UsgsEarthquakes(requireContext().getCacheDir());
        this.formatter = new EarthquakeFormatter(requireContext(), settings.use24Hours, settings.distanceUnit);
        this.adapter = new EarthquakeListAdapter(this.formatter, this::openEarthquake);
        this.days = hasOwnDays() ? requireArguments().getInt(ARG_DAYS) : settings.preferences.getInt(PREF_DAYS, 7);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_earthquakes, container, false);
        this.placeView = view.findViewById(R.id.flEarthquakesPlace);
        this.sortToggle = view.findViewById(R.id.tgEarthquakesSort);
        updateReference();
        this.sortToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            boolean byDistance = checkedId == R.id.btnEarthquakesSortDistance;
            // Also checked from the code when the place changes, which is no choice to save
            if (isChecked && byDistance != this.sortByDistance) {
                this.sortByDistance = byDistance;
                AppSettings.getInstance().preferences.edit().putBoolean(PREF_BY_DISTANCE, byDistance).apply();
                showEarthquakes(EarthquakeListAdapter.MOTION_REORDER);
            }
        });

        ChipGroup chipGroup = view.findViewById(R.id.cgPeriod);
        chipGroup.check(this.days == 1 ? R.id.chipDay : (this.days == 30 ? R.id.chipMonth : R.id.chipWeek));
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checkedId = checkedIds.get(0);
            this.days = checkedId == R.id.chipDay ? 1 : (checkedId == R.id.chipMonth ? 30 : 7);
            if (!hasOwnDays()) {
                AppSettings.getInstance().preferences.edit().putInt(PREF_DAYS, this.days).apply();
            }
            // The list of the period before stays until the new one comes, cleared only if it takes a while
            load(false);
        });

        this.recyclerView = view.findViewById(R.id.rvEarthquakes);
        this.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        // Each day's earthquakes on a card of their own, with lines between them
        this.recyclerView.addItemDecoration(new EarthquakeDayCards(this.recyclerView));
        this.listAnimator = new QuakeListAnimator(getResources().getDisplayMetrics().density);
        this.recyclerView.setItemAnimator(this.listAnimator);
        this.recyclerView.setAdapter(this.adapter);
        this.tvEmpty = view.findViewById(R.id.tvEmpty);
        this.swipeRefresh = view.findViewById(R.id.srlEarthquakes);
        // The list sits in a FrameLayout with the empty text, so tell the layout when the list can scroll up
        this.swipeRefresh.setOnChildScrollUpCallback((parent, child) -> this.recyclerView.canScrollVertically(-1));
        this.swipeRefresh.setOnRefreshListener(() -> load(true));
        // The spinner on the theme's surface, not the default white disk that glares in the dark theme
        this.swipeRefresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(this.swipeRefresh,
                com.google.android.material.R.attr.colorSurfaceContainerHigh));
        this.swipeRefresh.setColorSchemeColors(MaterialColors.getColor(this.swipeRefresh, androidx.appcompat.R.attr.colorPrimary));
        // The place, order and periods stay pinned on glass; the list runs up under them, and the spinner comes out
        // from under them
        GlassStrip strip = view.findViewById(R.id.earthquakesStrip);
        strip.setList(this.recyclerView);
        float density = getResources().getDisplayMetrics().density;
        strip.setOnContentTopListener(top -> this.swipeRefresh.setProgressViewOffset(false,
                top - Math.round(40 * density), top + Math.round(24 * density)));
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.isLoaded = false;
        this.pendingListState = isTab() ? tabListState : null;
        if (this.pendingListState != null) {
            this.adapter.repick(tabPickedId);
        }
        // From the first frame the list loaded lately for the period, or the map and the skeleton standing in its
        // place, never an empty screen
        List<Earthquake> fresh = freshList(this.days);
        if (fresh != null) {
            this.earthquakes = fresh;
            this.shownDays = this.days;
        } else {
            this.adapter.setWaiting(true);
        }
        showEarthquakes(EarthquakeListAdapter.MOTION_NONE);
        if (isMenuVisible()) {
            loadOnce();
        }
    }

    // A ViewPager shows the menu of the current page only, so this also tells when the tab is opened
    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (menuVisible && getView() != null) {
            loadOnce();
            // The location changed on another tab
            if (!isFixedReference() && this.shownLocationId != AstroPlacePicker.shownLocationId()) {
                onReferenceChanged();
            }
        }
    }

    private boolean isFixedReference() {
        Bundle args = getArguments();
        return args != null && args.containsKey(ARG_LATITUDE);
    }

    // The distances and the place chip for the location: the one passed in, otherwise the one of the tabs
    private void updateReference() {
        String referenceName = findReference();
        this.referenceName = referenceName;
        this.adapter.setReference(this.hasReference, this.referenceLatitude, this.referenceLongitude);
        this.sortByDistance = this.hasReference && AppSettings.getInstance().preferences.getBoolean(PREF_BY_DISTANCE, false);
        Chip chip = this.placeView.findViewById(R.id.chipAstroPlace);
        if (isFixedReference()) {
            AstroPlacePicker.bindFixed(chip, referenceName);
        } else {
            this.shownLocationId = AstroPlacePicker.shownLocationId();
            AstroPlacePicker.bind(chip, AppSettings.getInstance().getAstroLocation(), this::onReferenceChanged);
        }
        // Without a place there is neither the chip nor the order by distance: no row for them
        this.placeView.setVisibility(referenceName != null ? View.VISIBLE : View.INVISIBLE);
        ((View) this.placeView.getParent()).setVisibility(referenceName != null ? View.VISIBLE : View.GONE);
        this.sortToggle.check(this.sortByDistance ? R.id.btnEarthquakesSortDistance : R.id.btnEarthquakesSortTime);
        this.sortToggle.setVisibility(this.hasReference ? View.VISIBLE : View.GONE);
    }

    private void onReferenceChanged() {
        updateReference();
        showEarthquakes(this.sortByDistance ? EarthquakeListAdapter.MOTION_REORDER : EarthquakeListAdapter.MOTION_PLACE);
    }

    private void loadOnce() {
        if (!this.isLoaded) {
            this.isLoaded = true;
            load(false);
        }
    }

    // The location for the distances: the one passed in, otherwise the one chosen for the tabs
    private String findReference() {
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_LATITUDE)) {
            this.hasReference = true;
            this.referenceLatitude = args.getDouble(ARG_LATITUDE);
            this.referenceLongitude = args.getDouble(ARG_LONGITUDE);
            return args.getString(ARG_LOCATION_NAME);
        }
        Place location = AppSettings.getInstance().getAstroLocation();
        this.hasReference = location != null;
        if (location == null) {
            return null;
        }
        this.referenceLatitude = location.latitude;
        this.referenceLongitude = location.longitude;
        return location.displayName();
    }

    // The period's list loaded within the time it stays fresh, else null
    private static List<Earthquake> freshList(int days) {
        Long loadedTime = loadedTimes.get(days);
        return loadedTime != null && System.currentTimeMillis() - loadedTime < UsgsEarthquakes.FRESH_MS ? loadedLists.get(days) : null;
    }

    private void load(boolean refresh) {
        List<Earthquake> fresh = refresh ? null : freshList(this.days);
        if (fresh != null) {
            this.loadWork.drop();
            this.mainHandler.removeCallbacks(this.showWaiting);
            this.isLoading = false;
            this.swipeRefresh.setRefreshing(false);
            this.adapter.setWaiting(false);
            this.adapter.setLoading(false);
            // Shown already from the first frame
            if (fresh == this.earthquakes && this.shownDays == this.days) {
                return;
            }
            this.earthquakes = fresh;
            this.shownDays = this.days;
            showEarthquakes(EarthquakeListAdapter.MOTION_GROW);
            return;
        }
        this.isLoading = true;
        this.adapter.setLoading(true);
        final int requestDays = this.days;
        // A pull shows its own spinner; otherwise the skeleton comes only if the list takes a while, as the saved ones
        // come at once and it would just flash
        this.mainHandler.removeCallbacks(this.showWaiting);
        if (!refresh) {
            this.mainHandler.postDelayed(this.showWaiting, SPINNER_DELAY_MS);
        }
        final Context appContext = requireContext().getApplicationContext();
        this.loadWork.runLatest(() -> {
            UsgsEarthquakes.Result result = null;
            try {
                result = this.usgs.load(requestDays, refresh);
            } catch (Exception e) {
                Log.w(TAG, "Can't load the earthquakes", e);
            }
            // The widgets of the period show it too
            if (result != null && !result.isSaved) {
                EarthquakeWidget.updateAll(appContext, requestDays, result.earthquakes);
            }
            // Kept for a switch back even when the chips moved on meanwhile, so before the result is dropped
            final UsgsEarthquakes.Result loaded = result;
            this.mainHandler.post(() -> keep(requestDays, loaded));
            return result;
        }, result -> onLoaded(result, refresh));
    }

    // A saved list of a failed request isn't fresh
    private void keep(int requestDays, UsgsEarthquakes.Result result) {
        if (result != null && !result.isSaved) {
            loadedLists.put(requestDays, result.earthquakes);
            loadedTimes.put(requestDays, System.currentTimeMillis());
        }
    }

    // A pull's list brings its new earthquakes with waves; another comes one after another
    private void onLoaded(UsgsEarthquakes.Result result, boolean refresh) {
        if (getView() == null) {
            return;
        }
        this.mainHandler.removeCallbacks(this.showWaiting);
        this.isLoading = false;
        this.swipeRefresh.setRefreshing(false);
        boolean wasWaiting = this.adapter.isWaiting();
        this.adapter.setWaiting(false);
        this.adapter.setLoading(false);
        if (result == null) {
            // The list of another period isn't left under the chips, nor the waiting map
            if (this.shownDays != this.days || wasWaiting) {
                this.earthquakes = new ArrayList<>();
                showEarthquakes(EarthquakeListAdapter.MOTION_GROW);
            }
            if (this.earthquakes.isEmpty()) {
                this.tvEmpty.setText(R.string.earthquakes_error);
                showEmpty(true);
            } else {
                Snackbar.make(this.recyclerView, R.string.earthquakes_error, Snackbar.LENGTH_LONG).show();
            }
            return;
        }
        this.earthquakes = result.earthquakes;
        this.shownDays = this.days;
        showEarthquakes(refresh ? EarthquakeListAdapter.MOTION_NEW : EarthquakeListAdapter.MOTION_GROW);
        if (result.isSaved) {
            Snackbar.make(this.recyclerView, R.string.earthquakes_saved, Snackbar.LENGTH_LONG).show();
        }
    }

    // The list moving into place from the one before as the motion says, but for the tab made again, which stands
    // where it was
    private void showEarthquakes(int motion) {
        List<Earthquake> sorted = new ArrayList<>(this.earthquakes);
        if (this.sortByDistance) {
            sorted.sort(Comparator.comparingDouble(quake -> quake.distanceKm(this.referenceLatitude, this.referenceLongitude)));
        } else {
            sorted.sort((a, b) -> Long.compare(b.time, a.time));
        }
        boolean restore = this.pendingListState != null && !sorted.isEmpty();
        // The tab made again stands as it was, unless its list comes in place of the skeleton
        if ((restore && !this.adapter.isShowingSkeleton()) || !QuakeMotion.isOn(requireContext())) {
            motion = EarthquakeListAdapter.MOTION_NONE;
        }
        this.listAnimator.setRising(motion == EarthquakeListAdapter.MOTION_GROW || motion == EarthquakeListAdapter.MOTION_NEW);
        this.adapter.setEarthquakes(sorted, !this.sortByDistance, this.shownDays, motion);
        // The tab made again goes back where it was; another list starts from its top
        if (restore) {
            this.recyclerView.getLayoutManager().onRestoreInstanceState(this.pendingListState);
            this.pendingListState = null;
        } else {
            this.recyclerView.scrollToPosition(0);
        }
        this.tvEmpty.setText(R.string.earthquakes_empty);
        // "No earthquakes" only once the list has come, never while it is loading or before
        showEmpty(sorted.isEmpty() && !this.isLoading && !this.adapter.isWaiting());
    }

    // The text under the strip fading in, once the list it stands for has gone; shown already, it goes on fading in as
    // it was, whatever text it now has
    private void showEmpty(boolean show) {
        if (!show) {
            this.tvEmpty.animate().cancel();
            this.tvEmpty.setAlpha(1);
            this.tvEmpty.setVisibility(View.GONE);
        } else if (this.tvEmpty.getVisibility() != View.VISIBLE) {
            this.tvEmpty.setVisibility(View.VISIBLE);
            this.tvEmpty.setAlpha(0);
            this.tvEmpty.animate().alpha(1).setStartDelay(150).setDuration(300).setInterpolator(QuakeMotion.DECELERATE).start();
        }
    }

    @Override
    public void onDestroyView() {
        // A list never shown has no scroll of its own: the one kept stays
        if (isTab() && this.pendingListState == null && !this.earthquakes.isEmpty()) {
            tabListState = this.recyclerView.getLayoutManager().onSaveInstanceState();
            tabPickedId = this.adapter.pickedId();
        }
        this.mainHandler.removeCallbacks(this.showWaiting);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }

    // The earthquake over the whole window, growing out of the pinned controls' strip as it lies with the list at its
    // top, the same however far the list has scrolled and whichever line opened it; Back shrinks it into the strip
    private void openEarthquake(Earthquake earthquake) {
        GlassStrip strip = requireView().findViewById(R.id.earthquakesStrip);
        int[] at = new int[2];
        strip.getRestingLocationInWindow(at);
        EarthquakeFragment.showOver(requireActivity(), EarthquakeFragment.arguments(earthquake, this.referenceLatitude,
                this.referenceLongitude, this.hasReference ? this.referenceName : null),
                new Rect(at[0], at[1], at[0] + strip.getWidth(), at[1] + strip.getHeight()));
    }
}
