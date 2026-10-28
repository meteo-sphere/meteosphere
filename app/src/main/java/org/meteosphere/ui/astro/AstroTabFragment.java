package org.meteosphere.ui.astro;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.google.android.material.chip.Chip;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.R;
import org.meteosphere.astro.Eclipse;
import org.json.JSONException;
import org.json.JSONObject;
import org.meteosphere.astro.EclipseCatalog;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.BackgroundWork;
import org.meteosphere.ui.sky.SkyTheme;

/**
 * The Moon or the Sun tab, for a place and a moment: the place chosen for the tabs, or one given that can't be
 * changed, like the weather details' one; the moment now or one picked, its own. What the tab shows is computed off the
 * main thread, only the last moment asked for, and shown once it comes; now moves on while the tab is on screen.
 */
abstract class AstroTabFragment<T extends AstroTabFragment.Shown> extends Fragment {

    protected static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    protected static final long DAY_MS = 24 * HOUR_MS;
    // The day chart's strip reaches this many days back and ahead, the body's height every DAY_STEP_MINUTES over it
    protected static final int STRIP_DAYS = 7;
    protected static final int DAY_STEP_MINUTES = 10;

    private static final String ARG_LOCATION = "location";
    // The main screen makes its tabs again each time it comes back: each tab's moment kept for the next one
    private static final Map<Class<?>, AstroTime.State> tabTimes = new HashMap<>();

    /** What a tab shows: times are local ones read as UTC unless named UTC */
    static class Shown {
        Place location;
        boolean hasPlace;
        boolean southernHemisphere;
        // The time zone of the place, to turn instants into its local times
        TimeZone timeZone;
        long today;
        // UTC, and the body's height then
        long now;
        float nowAltitude;
        // The day chart's strip of days around today: its first midnight as UTC, and the body's height every
        // DAY_STEP_MINUTES from it to the midnight after the last day
        long stripStart;
        float[] stripAltitudes;

        long local(long utcMillis) {
            return utcMillis + this.timeZone.getOffset(utcMillis);
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // A moment passed by while waiting, as days are dragged through, isn't computed; only the last one's comes
    private final BackgroundWork computeWork = new BackgroundWork(this.executor);
    // The moment the tab shows, its own
    private final AstroTime time = new AstroTime();
    private final NowTicker ticker = new NowTicker(this.mainHandler, this.time, this::load);
    protected AstroFormatter formatter;
    private Context skyContext;
    private int shownLocationId;
    // The place and the start of the strip the day chart shows, for the next strip to follow on from it
    private String shownPlace;
    private long shownStripStart;

    /** The tab of the place given, which can't be changed then */
    protected static <F extends AstroTabFragment<?>> F withLocation(F fragment, Place location) {
        Bundle args = new Bundle();
        // As its JSON, the place isn't Parcelable; without it the tab shows the place chosen for the tabs
        try {
            if (location != null) {
                args.putString(ARG_LOCATION, location.toJson().toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        fragment.setArguments(args);
        return fragment;
    }

    /** Whether the tab is drawn over the day sky, the Sun's, rather than the night one */
    protected abstract boolean isDaySky();

    /** What the tab shows for the place at the moment, UTC; off the main thread */
    protected abstract T compute(Context context, Place location, long now, List<Eclipse> catalog);

    /** Shows what was computed; the view is there */
    protected abstract void show(T shown);

    /** The place given, or null for the one chosen for the tabs */
    protected Place fixedLocation() {
        String json = getArguments() == null ? null : getArguments().getString(ARG_LOCATION);
        try {
            return json != null ? Place.fromJson(new JSONObject(json)) : null;
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.formatter = new AstroFormatter(requireContext(), AppSettings.getInstance().use24Hours);
        AstroTime.State kept = tabTimes.get(getClass());
        if (fixedLocation() == null && kept != null) {
            this.time.restore(kept);
        }
    }

    // Drawn over its sky: the views and their colours come from the sky's dark theme
    @Override
    public LayoutInflater onGetLayoutInflater(Bundle savedInstanceState) {
        LayoutInflater inflater = SkyTheme.inflater(super.onGetLayoutInflater(savedInstanceState), isDaySky());
        this.skyContext = inflater.getContext();
        return inflater;
    }

    protected Context sky() {
        return this.skyContext != null ? this.skyContext : requireContext();
    }

    @Override
    public void onDestroyView() {
        if (fixedLocation() == null) {
            tabTimes.put(getClass(), this.time.state());
        }
        super.onDestroyView();
    }

    // Opened after the location was changed on another tab
    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (menuVisible && getView() != null && fixedLocation() == null && this.shownLocationId != AstroPlacePicker.shownLocationId()) {
            load();
        }
        this.ticker.update(isOnScreen());
    }

    protected final void load() {
        Place location = fixedLocation() != null ? fixedLocation() : AppSettings.getInstance().getAstroLocation();
        this.shownLocationId = AstroPlacePicker.shownLocationId();
        long now = this.time.moment();
        this.ticker.shown();
        List<Eclipse> catalog = EclipseCatalog.get(requireContext());
        Context context = requireContext().getApplicationContext();
        this.computeWork.runLatest(() -> compute(context, location, now, catalog), shown -> {
            if (getView() != null) {
                show(shown);
                this.ticker.update(isOnScreen());
            }
        });
    }

    /** The pinned place and moment of what is shown */
    protected void bindControls(View view, T shown) {
        this.time.bind(view.findViewById(R.id.llAstroTime), shown.timeZone, AppSettings.getInstance().use24Hours,
                getChildFragmentManager(), this::load);
        Chip chipPlace = view.findViewById(R.id.chipAstroPlace);
        if (fixedLocation() != null) {
            AstroPlacePicker.bindFixed(chipPlace, shown.location.displayName());
        } else {
            AstroPlacePicker.bind(chipPlace, shown.location, this::load);
        }
    }

    /**
     * The strip of days around the day shown; the day dragged or flung into the window becomes the day shown as it
     * comes, as the arrows by the date make it
     */
    protected void bindDayStrip(SunDayChart chart, T shown) {
        // One more for the midnight the strip ends at; the midnights and the hours as the clock of the place shows
        // them, so a day the clocks change on is as long as it is
        SunDayChart.Day[] days = new SunDayChart.Day[2 * STRIP_DAYS + 2];
        for (int i = 0; i < days.length; i++) {
            long day = shown.today + (i - STRIP_DAYS) * DAY_MS;
            int[] hours = new int[SunDayChart.Day.HOURS.length];
            for (int h = 0; h < hours.length; h++) {
                hours[h] = stripMinutes(shown, AstroTime.fromLocal(shown.timeZone, day + SunDayChart.Day.HOURS[h] * HOUR_MS));
            }
            days[i] = new SunDayChart.Day(stripMinutes(shown, AstroTime.fromLocal(shown.timeZone, day)), hours, this.formatter.weekDay(day));
        }
        // Set first: a strip that comes while it moves may tell a day of its own at once
        chart.setOnDayListener(index -> {
            this.time.pickDay(shown.timeZone, shown.today + (index - STRIP_DAYS) * DAY_MS, this::load);
            // The new date at once, before the day is computed
            this.time.bind(getView().findViewById(R.id.llAstroTime), shown.timeZone, AppSettings.getInstance().use24Hours,
                    getChildFragmentManager(), this::load);
        });
        // A double tap goes back to now, as the way back by the moment does; the first drag tells it once
        chart.setOnDoubleTapListener(() -> this.time.backToNow(getView().findViewById(R.id.llAstroTime), this::load));
        chart.setOnDragEndListener(this::showDoubleTapHint);
        String place = shown.location == null ? "" : shown.location.latitude + "," + shown.location.longitude;
        int shift = place.equals(this.shownPlace) ? (int) ((shown.stripStart - this.shownStripStart) / MINUTE_MS) : SunDayChart.NONE;
        this.shownPlace = place;
        this.shownStripStart = shown.stripStart;
        chart.setDays(shown.stripAltitudes, DAY_STEP_MINUTES, days, STRIP_DAYS, stripMinutes(shown, shown.now), shown.nowAltitude, shift);
    }

    // Once for all the tabs, marked as it shows; a toast, so it neither stops the drags nor waits for an answer
    private void showDoubleTapHint() {
        AppSettings settings = AppSettings.getInstance();
        if (settings.isDayStripHintShown()) {
            return;
        }
        settings.saveDayStripHintShown();
        Toast.makeText(requireContext(), R.string.astro_day_strip_hint, Toast.LENGTH_LONG).show();
    }

    // Minutes from the strip's start to the UTC instant
    private static int stripMinutes(Shown shown, long utc) {
        return (int) ((utc - shown.stripStart) / MINUTE_MS);
    }

    // The local midnight starting the day as UTC, by the offset of its noon as the day's times are; the day chart's
    // strip has the midnight the clock shows instead, AstroTime.fromLocal
    protected static long utcStart(TimeZone timeZone, long localDay) {
        return localDay - Place.offsetMinutesOfDay(timeZone, localDay) * MINUTE_MS;
    }

    @Override
    public void onResume() {
        super.onResume();
        this.ticker.update(isOnScreen());
    }

    @Override
    public void onPause() {
        this.ticker.update(false);
        super.onPause();
    }

    // The tab on screen: the page shown, the app in front
    protected boolean isOnScreen() {
        return getView() != null && isResumed() && isMenuVisible();
    }

    @Override
    public void onDestroy() {
        this.executor.shutdownNow();
        super.onDestroy();
    }
}
