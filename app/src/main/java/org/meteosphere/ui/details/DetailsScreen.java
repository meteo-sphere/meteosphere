package org.meteosphere.ui.details;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import org.meteosphere.data.model.Place;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.data.quake.UsgsEarthquakes;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.common.BottomScrimView;
import org.meteosphere.ui.sky.SkyScene;
import org.meteosphere.ui.sky.SkyBackgrounds;
import org.meteosphere.ui.sky.SkyView;

/**
 * The city details: a page per city over the fixed sky, the cities' bar at the bottom.
 *
 * Each city's model is built on one background thread and kept until its data or the settings change; pages only
 * bind models. The sky of each city is composed into one picture off the main thread, and a swipe cross-fades two
 * of them, so the screen draws at most two full-screen bitmaps.
 */
public final class DetailsScreen implements DetailsPage.Host {

    // The earthquakes' tile looks at the last day, and opens the earthquakes' screen on it
    public static final int EARTHQUAKE_DAYS = 1;
    // A failed load of the earthquakes, with no list saved either, is tried again after this, as the pages are built
    private static final long EARTHQUAKE_RETRY_MS = 5 * 60 * 1000L;

    public interface Host {
        void onCityShown(int locationId);

        void openAstro(Place location, boolean isMoon);

        void openEarthquakes(Place location);
    }

    private final Activity activity;
    private final Host host;
    private final ViewPager pager;
    private final SkyView background;
    private final SkyView backgroundNext;
    // With the live skies, the city each of the two shows
    private static final int NO_CITY = Integer.MIN_VALUE;
    private int lowerCity = NO_CITY;
    private int upperCity = NO_CITY;
    private boolean skiesLive;
    // The swipe's last place, for the live skies to fade by once the lower one's new weather has come
    private int fadePosition;
    private float fadeOffset;
    private boolean fadeWaiting;
    // The city shown given another weather while nothing moves: its new sky fades in over the old one for this long, once
    // the new one's first frame has come; the city's place and the sky waited for, -1 and null for none
    private static final long WEATHER_FADE_MS = 1200;
    // The new live sky over the old one waits drawn, though hardly seen: one not seen at all may not be drawn; and no
    // longer than this for its first frame. A sky fading in from the window's colour starts as hardly seen
    private static final float WEATHER_WAIT_ALPHA = 1 / 255f;
    private static final long WEATHER_WAIT_MS = 2000;
    private final Runnable weatherWaited = () -> {
        if (this.weatherWaiting != null) {
            startWeatherFade();
        }
    };
    private int weatherPosition = -1;
    private SkyView weatherWaiting;
    private ValueAnimator weatherFade;
    // A live sky coming where no sky showed, only the window's colour: once its first frame has come, it shows at once
    // if the colour showed no longer than a glimpse, as on opening a page again (15 ms on the emulator), otherwise it
    // fades in from the colour for this long, as after a cold start (150-210 ms); null for none
    private static final long SKY_BLANK_MS = 50;
    private static final long SKY_APPEAR_MS = 500;
    private SkyView appearing;
    private ValueAnimator skyAppear;
    private int scrollState = ViewPager.SCROLL_STATE_IDLE;
    private final View bottomBar;
    private final BottomScrimView bottomScrim;
    private final PageDotsView dots;
    private final ArrayList<Integer> locationIds = new ArrayList<>();
    private final HashMap<Integer, DetailsModel> models = new HashMap<>();
    private final HashMap<Integer, Integer> requests = new HashMap<>();
    private final ArrayList<DetailsPage> pages = new ArrayList<>();
    // Pages that left the pager, for the next city: a swipe then binds a page instead of inflating one
    private final java.util.ArrayDeque<DetailsPage> sparePages = new java.util.ArrayDeque<>();
    private static final int MAX_SPARE_PAGES = 2;
    // A model younger than this is shown as it is when its page comes back
    private static final long MODEL_FRESH_MS = 60_000;
    // Where the live sky's clouds thin out down the screen, as fractions of its height: from about the cards' top
    private static final float CLOUDS_THIN_FROM = 0.3f;
    private static final float CLOUDS_GONE_AT = 0.56f;
    // And where its rain and snow fade out, chosen from the variants shown: the rain with the clouds, the snow over the
    // lower part of the header, gone at the cards' top; the stills keep theirs all the way down
    private static final float RAIN_FADES_FROM = 0.3f;
    private static final float RAIN_GONE_AT = 0.56f;
    private static final float SNOW_FADES_FROM = 0.25f;
    private static final float SNOW_GONE_AT = 0.36f;
    // A page's scroll shorter than this, the tremble of a finger, leaves the cities' bar as it is
    private static final int BAR_SLOP_DP = 2;
    private final CitiesAdapter adapter = new CitiesAdapter();
    private final SkyBackgrounds backgrounds;
    private final Point backgroundSize;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int barSlop;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(() -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE);
        runnable.run();
    }, "DetailsModels"));
    private DetailsModelBuilder.Settings settings;
    private int requestCounter;
    private int topInset;
    private int bottomInset;
    private boolean barHidden;
    private boolean destroyed;
    private int scrollToDaysFor = Integer.MIN_VALUE;
    // The M5+ earthquakes of the last day the world over, loaded once for every city's tile; null until they are
    private List<Earthquake> earthquakes;
    private boolean earthquakesFailed;
    private boolean earthquakesRequested;
    private boolean earthquakesLoading;
    private long earthquakesAskedAt;

    public DetailsScreen(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.barSlop = Math.round(BAR_SLOP_DP * activity.getResources().getDisplayMetrics().density);
        this.pager = activity.findViewById(R.id.pagerCities);
        this.background = activity.findViewById(R.id.ivBackground);
        this.backgroundNext = activity.findViewById(R.id.ivBackgroundNext);
        // A sky drawn live changes under the glass without anything moving: the glass draws it again
        this.background.setOnCloudsDrawn(this::onSkyDrawn);
        this.backgroundNext.setOnCloudsDrawn(this::onSkyDrawn);
        // Its clouds over the header only, as the pictures': under the cards they thin into the sky's colour
        this.background.setClearBelow(CLOUDS_THIN_FROM, CLOUDS_GONE_AT);
        this.backgroundNext.setClearBelow(CLOUDS_THIN_FROM, CLOUDS_GONE_AT);
        this.background.setDropsFade(RAIN_FADES_FROM, RAIN_GONE_AT, SNOW_FADES_FROM, SNOW_GONE_AT);
        this.backgroundNext.setDropsFade(RAIN_FADES_FROM, RAIN_GONE_AT, SNOW_FADES_FROM, SNOW_GONE_AT);
        this.bottomBar = activity.findViewById(R.id.bottomBar);
        this.bottomScrim = activity.findViewById(R.id.bottomScrim);
        this.dots = activity.findViewById(R.id.dotsCities);
        this.backgrounds = SkyBackgrounds.getInstance(activity);
        this.backgroundSize = SkyBackgrounds.composedSize(activity);
        this.settings = DetailsModelBuilder.Settings.read(activity);
        loadEarthquakes();
        this.pager.setOffscreenPageLimit(1);
        this.pager.setAdapter(this.adapter);
        this.pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                crossFade(position, offset);
            }

            @Override
            public void onPageSelected(int position) {
                DetailsScreen.this.dots.setCurrent(position);
                showBar(true);
                preloadNeighbours(position);
                DetailsScreen.this.host.onCityShown(DetailsScreen.this.locationIds.get(position));
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                DetailsScreen.this.scrollState = state;
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    crossFade(DetailsScreen.this.pager.getCurrentItem(), 0);
                } else if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    // The cities either side come in as far down as this one
                    int y = shownScroll();
                    DetailsPage shown = pageFor(getCurrentPlaceId());
                    for (DetailsPage page : DetailsScreen.this.pages) {
                        if (page != shown) {
                            page.scrollWith(y);
                        }
                    }
                }
            }
        });
    }

    // --- Cities ---

    /** Shows the cities, on the given one */
    public void setLocations(List<Integer> ids, int currentId) {
        this.locationIds.clear();
        this.locationIds.addAll(ids);
        this.adapter.notifyDataSetChanged();
        int index = Math.max(0, this.locationIds.indexOf(currentId));
        this.pager.setCurrentItem(index, false);
        this.dots.setPages(this.locationIds.size(), index, !this.locationIds.isEmpty() && this.locationIds.get(0) == -1);
        this.dots.setVisibility(this.locationIds.size() > 1 ? View.VISIBLE : View.INVISIBLE);
        crossFade(index, 0);
        preloadNeighbours(index);
        // One page made ahead while the screen is idle, for the first swipe back
        Looper.myQueue().addIdleHandler(() -> {
            if (!this.destroyed && this.sparePages.isEmpty() && this.locationIds.size() > 2) {
                this.sparePages.push(new DetailsPage(LayoutInflater.from(this.activity), this.pager, -2, this));
            }
            return false;
        });
    }

    public int getCurrentPlaceId() {
        int index = this.pager.getCurrentItem();
        return index < this.locationIds.size() ? this.locationIds.get(index) : -2;
    }

    /** The current city's forecast as its page shows it, null until its model is built */
    public Place getCurrentPlace() {
        DetailsModel model = this.models.get(getCurrentPlaceId());
        return model != null ? model.location : null;
    }

    /** Opens the current city's page scrolled to the days, once it is bound */
    public void scrollToDaysOnce() {
        this.scrollToDaysFor = getCurrentPlaceId();
        DetailsPage page = pageFor(this.scrollToDaysFor);
        if (page != null && page.getModel() != null) {
            page.scrollToDays();
            this.scrollToDaysFor = Integer.MIN_VALUE;
        }
    }

    // --- Models ---

    /** Builds the city's model again, its data or the time changed */
    public void refresh(int locationId) {
        if (!this.locationIds.contains(locationId)) {
            return;
        }
        request(locationId);
    }

    /** The forecast of a place was updated: its page, and the current location's when that is the place */
    public void refreshPlace(int placeId) {
        refresh(placeId);
        DetailsModel current = this.models.get(-1);
        if (placeId != -1 && current != null && current.location != null && current.location.id == placeId) {
            refresh(-1);
        }
    }

    /** Builds again the models of the pages that exist, the others are built when they are shown */
    public void refreshShown() {
        loadEarthquakes();
        ArrayList<Integer> shown = new ArrayList<>();
        for (DetailsPage page : this.pages) {
            shown.add(page.locationId);
        }
        // Models of pages out of sight are out of date now: still shown at once by a page that comes, as after a quick
        // swipe over two cities, rather than the page staying empty, and built again then
        for (DetailsModel model : this.models.values()) {
            if (!shown.contains(model.locationId)) {
                model.builtAt = 0;
            }
        }
        for (int id : shown) {
            request(id);
        }
    }

    /** The units or the parameters may have changed */
    public void onSettingsChanged() {
        this.settings = DetailsModelBuilder.Settings.read(this.activity);
        refreshShown();
    }

    // The last day's list, the saved one while it is fresh, off the models' thread: a slow request mustn't hold the pages.
    // Once, unless it failed
    private void loadEarthquakes() {
        if (this.earthquakesLoading || !this.settings.parameters.contains(DetailsModelBuilder.EARTHQUAKES)) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (this.earthquakesRequested && (!this.earthquakesFailed || now - this.earthquakesAskedAt < EARTHQUAKE_RETRY_MS)) {
            return;
        }
        this.earthquakesRequested = true;
        this.earthquakesLoading = true;
        this.earthquakesAskedAt = now;
        File cacheDir = this.activity.getCacheDir();
        new Thread(() -> {
            List<Earthquake> loaded = null;
            try {
                loaded = new UsgsEarthquakes(cacheDir).load(EARTHQUAKE_DAYS, false).earthquakes;
            } catch (Exception e) {
                android.util.Log.w("DetailsScreen", "Loading the earthquakes: " + e);
            }
            List<Earthquake> result = loaded;
            this.mainHandler.post(() -> {
                this.earthquakesLoading = false;
                if (this.destroyed) {
                    return;
                }
                this.earthquakes = result;
                this.earthquakesFailed = result == null;
                refreshShown();
            });
        }, "DetailsEarthquakes").start();
    }

    private void request(int locationId) {
        int token = ++this.requestCounter;
        this.requests.put(locationId, token);
        DetailsModelBuilder.Settings settings = this.settings;
        List<Earthquake> earthquakes = this.earthquakes;
        boolean earthquakesFailed = this.earthquakesFailed;
        this.executor.execute(() -> {
            DetailsModel model;
            try {
                model = new DetailsModelBuilder(this.activity, settings, earthquakes, earthquakesFailed).build(locationId);
            } catch (RuntimeException e) {
                android.util.Log.e("DetailsScreen", "Building the details of " + locationId, e);
                // Not waited for any more: a page made for the place asks again
                this.mainHandler.post(() -> {
                    Integer latest = this.requests.get(locationId);
                    if (latest != null && latest == token) {
                        this.requests.remove(locationId);
                    }
                });
                return;
            }
            // Ahead of the main thread: the sky of the city starts composing at once, unless shaders draw it live
            if (!SkyView.liveEnabled(this.activity)) {
                this.backgrounds.compose(model.backgroundLayers, this.backgroundSize, null);
            }
            this.mainHandler.post(() -> {
                Integer latest = this.requests.get(locationId);
                if (this.destroyed || latest == null || latest != token) {
                    return;
                }
                this.requests.remove(locationId);
                this.models.put(locationId, model);
                onModel(model);
            });
        });
    }

    private void onModel(DetailsModel model) {
        DetailsPage page = pageFor(model.locationId);
        if (page != null) {
            page.bind(model);
            if (this.scrollToDaysFor == model.locationId) {
                this.scrollToDaysFor = Integer.MIN_VALUE;
                page.scrollToDays();
            }
        }
        int index = this.pager.getCurrentItem();
        if (index < this.locationIds.size() && this.locationIds.get(index) == model.locationId && !fadeWeather(index, model)) {
            crossFade(index, 0);
        }
    }

    // How far down the city shown is scrolled; a page made for another city during a swipe stands as far down
    private int shownScroll() {
        DetailsPage shown = pageFor(getCurrentPlaceId());
        return shown != null ? shown.getScrollY() : 0;
    }

    private DetailsPage pageFor(int locationId) {
        for (DetailsPage page : this.pages) {
            if (page.locationId == locationId) {
                return page;
            }
        }
        return null;
    }

    private void preloadNeighbours(int index) {
        for (int i = index - 1; i <= index + 1; i++) {
            if (i < 0 || i >= this.locationIds.size()) {
                continue;
            }
            DetailsModel model = this.models.get(this.locationIds.get(i));
            if (model != null && !SkyView.liveEnabled(this.activity)) {
                this.backgrounds.compose(model.backgroundLayers, this.backgroundSize, null);
            }
        }
    }

    // --- Sky ---

    // The page's sky under the current page, the next one's over it as much as the swipe has shown it
    private void crossFade(int position, float offset) {
        if (this.weatherPosition >= 0) {
            if (position == this.weatherPosition && offset <= 0.001f) {
                // The weather's fade keeps the sky meanwhile
                showWindowColor(position);
                return;
            }
            endWeatherFade();
        }
        if (SkyView.liveEnabled(this.activity)) {
            crossFadeLive(position, offset);
        } else {
            if (this.skiesLive) {
                // Back from the live skies, which may have left the lower one hidden
                this.skiesLive = false;
                this.fadeWaiting = false;
                stopSkyAppear();
                this.lowerCity = NO_CITY;
                this.upperCity = NO_CITY;
                this.background.setAlpha(1f);
            }
            showSky(this.background, position);
            if (offset > 0.001f && position + 1 < this.locationIds.size()) {
                showSky(this.backgroundNext, position + 1);
                this.backgroundNext.setAlpha(offset);
            } else if (this.backgroundNext.getAlpha() != 0) {
                this.backgroundNext.setAlpha(0f);
            }
        }
        if (offset == 0) {
            showWindowColor(position);
        }
    }

    private void showWindowColor(int position) {
        if (position < this.locationIds.size()) {
            DetailsModel model = this.models.get(this.locationIds.get(position));
            if (model != null) {
                this.activity.getWindow().setBackgroundDrawable(new ColorDrawable(model.baseColor));
            }
        }
    }

    /**
     * The city shown given another weather while nothing moves: the new sky fades in over the old one rather than
     * taking its place at once, the live one once its first frame has come. False when there is no old sky to fade from,
     * the new one then shown as crossFade has it
     */
    private boolean fadeWeather(int position, DetailsModel model) {
        endWeatherFade();
        if (this.scrollState != ViewPager.SCROLL_STATE_IDLE || this.fadeOffset > 0.001f) {
            return false;
        }
        boolean live = SkyView.liveEnabled(this.activity);
        int city = model.locationId;
        if (live) {
            if (!this.skiesLive) {
                return false;
            }
            // The one fading in from the window's colour counts as shown: the new weather fades in over it as it is
            SkyView shown;
            if (this.upperCity == city
                    && (this.backgroundNext.getAlpha() >= 1f || this.appearing == this.backgroundNext)) {
                shown = this.backgroundNext;
            } else if (this.lowerCity == city && (this.background.getAlpha() >= 1f || this.appearing == this.background)
                    && this.backgroundNext.getAlpha() <= 0f) {
                shown = this.background;
            } else {
                return false;
            }
            if (!shown.showsScene() || SkyScene.from(model.backgroundLayers).equals(shown.getScene())) {
                return false;
            }
            stopSkyAppear();
            // The new sky over the old one, faded in; or under it, the old one faded out
            SkyView next = shown == this.background ? this.backgroundNext : this.background;
            showLiveSky(next, position);
            next.setAlpha(next == this.backgroundNext ? WEATHER_WAIT_ALPHA : 1f);
            this.weatherPosition = position;
            this.weatherWaiting = next;
            if (next.showsScene()) {
                startWeatherFade();
            } else {
                this.mainHandler.postDelayed(this.weatherWaited, WEATHER_WAIT_MS);
            }
            return true;
        }
        if (this.skiesLive || this.background.getTag() == null || this.backgroundNext.getAlpha() > 0f) {
            return false;
        }
        String tag = this.backgrounds.skyTag(model.backgroundLayers);
        Bitmap composed = this.backgrounds.getComposed(model.backgroundLayers, this.backgroundSize);
        if (tag.equals(this.background.getTag()) || composed == null) {
            return false;
        }
        this.backgroundNext.setScene(null);
        this.backgroundNext.setTag(tag);
        this.backgroundNext.setImageBitmap(composed);
        this.backgroundNext.setAlpha(0f);
        this.weatherPosition = position;
        this.weatherWaiting = this.backgroundNext;
        startWeatherFade();
        return true;
    }

    private void startWeatherFade() {
        this.mainHandler.removeCallbacks(this.weatherWaited);
        boolean over = this.weatherWaiting == this.backgroundNext;
        this.weatherWaiting = null;
        // The upper one from as far as it shows, one stopped fading in from the window's colour too
        ValueAnimator fade = ValueAnimator.ofFloat(this.backgroundNext.getAlpha(), over ? 1f : 0f)
                .setDuration(WEATHER_FADE_MS);
        fade.addUpdateListener(animation -> {
            this.backgroundNext.setAlpha((float) animation.getAnimatedValue());
            if (this.skiesLive) {
                shareLiveSkies();
            }
            invalidateGlass();
        });
        fade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onWeatherFaded();
            }
        });
        this.weatherFade = fade;
        fade.start();
    }

    // The new sky in its place: the picture faded in becomes the lower one's, the live skies stay as the fade left them
    private void onWeatherFaded() {
        int position = this.weatherPosition;
        this.weatherFade = null;
        this.weatherWaiting = null;
        this.weatherPosition = -1;
        if (this.destroyed) {
            return;
        }
        if (!this.skiesLive) {
            this.background.setTag(this.backgroundNext.getTag());
            this.background.setImageDrawable(this.backgroundNext.getDrawable());
            this.background.setAlpha(1f);
            this.backgroundNext.setAlpha(0f);
        }
        if (position == this.pager.getCurrentItem()) {
            crossFade(position, 0);
        }
    }

    // A fade under way ends where it was going, at once; one still waiting for its first frame is left, the next
    // crossFade showing the new weather as it does
    private void endWeatherFade() {
        if (this.weatherFade != null) {
            this.weatherFade.end();
        } else if (this.weatherPosition >= 0) {
            this.mainHandler.removeCallbacks(this.weatherWaited);
            this.weatherWaiting = null;
            this.weatherPosition = -1;
        }
    }

    // The live skies faded over each other, and each told how much of it shows
    private void crossFadeLive(int position, float offset) {
        fadeLiveSkies(position, offset);
        shareLiveSkies();
    }

    // As much of each as shows: the lower under the upper, the upper as opaque as it is
    private void shareLiveSkies() {
        float upper = this.backgroundNext.getAlpha();
        this.background.setShownShare(this.background.getAlpha() * (1 - upper));
        this.backgroundNext.setShownShare(upper);
    }

    // The live skies: each city's sky stays on the view already drawing it, the upper one as opaque as its city's share
    // of the swipe over the lower one. A sky given another weather has nothing to show until its first new frame
    // comes, so the weather changes only on a sky that hardly shows: at a swipe's end the sky that faded in stays,
    // rather than the lower one being given its weather as it is uncovered.
    private void fadeLiveSkies(int position, float offset) {
        int count = this.locationIds.size();
        if (position < 0 || position >= count) {
            return;
        }
        this.fadePosition = position;
        this.fadeOffset = offset;
        this.fadeWaiting = false;
        if (!this.skiesLive) {
            // From the pictures, the lower one perhaps fading in
            this.skiesLive = true;
            this.background.animate().cancel();
        }
        int first = this.locationIds.get(position);
        if (offset <= 0.001f || position + 1 >= count) {
            if (this.upperCity == first && this.backgroundNext.getAlpha() > 0) {
                showLiveSky(this.backgroundNext, position);
                appearSky(this.backgroundNext);
                // Covered, or the window's colour under it: it waits for no frames
                this.background.setAlpha(0f);
            } else {
                showLiveSky(this.background, position);
                if (!this.background.showsScene() && this.backgroundNext.getAlpha() > 0
                        && this.backgroundNext.showsScene()) {
                    // The upper one stays until the lower one's weather has come
                    if (this.appearing == this.background) {
                        stopSkyAppear();
                    }
                    this.background.setAlpha(1f);
                    this.fadeWaiting = true;
                } else {
                    appearSky(this.background);
                    this.backgroundNext.setAlpha(0f);
                }
            }
            return;
        }
        // A swipe: the lower sky fading in from the window's colour, or waiting to, goes on, the other city's coming
        // over it; an upper one stops where it is, the swipe taking it on
        if (this.appearing == this.backgroundNext) {
            stopSkyAppear();
        }
        int second = this.locationIds.get(position + 1);
        int lowerPosition;
        int upperPosition;
        if (this.background.getAlpha() > 0 && (this.lowerCity == first || this.lowerCity == second)) {
            // The lower sky shown keeps its city, the other city's fading in over it
            lowerPosition = this.lowerCity == first ? position : position + 1;
            upperPosition = this.lowerCity == first ? position + 1 : position;
        } else if (this.backgroundNext.getAlpha() > 0 && (this.upperCity == first || this.upperCity == second)) {
            // The upper sky shown keeps its city, fading out as much, the other city's under it
            upperPosition = this.upperCity == first ? position : position + 1;
            lowerPosition = this.upperCity == first ? position + 1 : position;
        } else {
            lowerPosition = position;
            upperPosition = position + 1;
        }
        showLiveSky(this.background, lowerPosition);
        showLiveSky(this.backgroundNext, upperPosition);
        if (this.appearing != this.background) {
            this.background.setAlpha(1f);
        }
        float upperShare = upperPosition == position ? 1 - offset : offset;
        if (!this.background.showsScene() && this.backgroundNext.showsScene()) {
            // The upper one opaque until the lower one's weather has come, rather than the window's colour showing
            // through both; under it that weather then shows at once, the colour unseen
            upperShare = 1f;
            this.fadeWaiting = true;
            if (this.appearing == this.background) {
                stopSkyAppear();
            }
        }
        this.backgroundNext.setAlpha(upperShare);
    }

    /**
     * The live sky showing the city alone, over the window's colour, opaque: unless it is fading in; one waiting for
     * its weather's first frame shows the colour till then
     */
    private void appearSky(SkyView sky) {
        if (!sky.showsScene()) {
            stopSkyAppear();
            sky.setAlpha(1f);
            this.appearing = sky;
        } else if (this.appearing == sky) {
            // Its first frame come before the sky was told of it
            if (this.skyAppear == null) {
                skyFrameCame();
            }
        } else {
            stopSkyAppear();
            sky.setAlpha(1f);
        }
    }

    // The waiting sky's first frame come, not drawn yet: shown at once if the colour showed only for a glimpse,
    // otherwise faded in from it
    private void skyFrameCame() {
        SkyView sky = this.appearing;
        if (sky.blankMs() <= SKY_BLANK_MS) {
            this.appearing = null;
            return;
        }
        sky.setAlpha(WEATHER_WAIT_ALPHA);
        startSkyAppear();
    }

    private void startSkyAppear() {
        SkyView sky = this.appearing;
        ValueAnimator appear = ValueAnimator.ofFloat(sky.getAlpha(), 1f).setDuration(SKY_APPEAR_MS);
        appear.addUpdateListener(animation -> {
            sky.setAlpha((float) animation.getAnimatedValue());
            shareLiveSkies();
            invalidateGlass();
        });
        appear.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Not one stopped, which another may have followed
                if (DetailsScreen.this.skyAppear == animation) {
                    DetailsScreen.this.skyAppear = null;
                    DetailsScreen.this.appearing = null;
                }
            }
        });
        this.skyAppear = appear;
        appear.start();
    }

    // A sky waiting to fade in from the window's colour, or fading in, left as far as it is: the caller sets the skies
    private void stopSkyAppear() {
        ValueAnimator appear = this.skyAppear;
        this.skyAppear = null;
        this.appearing = null;
        if (appear != null) {
            appear.cancel();
        }
    }

    private void showLiveSky(SkyView view, int position) {
        int city = this.locationIds.get(position);
        // A sky fading in from the window's colour given another city: its fade is no longer that city's
        if (view == this.appearing && city != (view == this.background ? this.lowerCity : this.upperCity)) {
            stopSkyAppear();
        }
        if (view == this.background) {
            this.lowerCity = city;
        } else {
            this.upperCity = city;
        }
        showSky(view, position);
    }

    private void showSky(SkyView view, int position) {
        if (position < 0 || position >= this.locationIds.size()) {
            return;
        }
        DetailsModel model = this.models.get(this.locationIds.get(position));
        if (model == null) {
            // Its sky isn't known yet: the window's colour shows through
            view.setScene(null);
            if (view.getTag() != null || view.getDrawable() != null) {
                view.setTag(null);
                view.setImageDrawable(null);
            }
            return;
        }
        if (SkyView.liveEnabled(this.activity)) {
            // Drawn live by shaders, the experimental animated weather: nothing to compose, and no picture shown
            view.setTag(null);
            view.setScene(SkyScene.from(model.backgroundLayers));
            return;
        }
        // The pictures, the animated weather off
        view.setScene(null);
        String tag = this.backgrounds.skyTag(model.backgroundLayers);
        if (tag.equals(view.getTag())) {
            return;
        }
        Bitmap composed = this.backgrounds.getComposed(model.backgroundLayers, this.backgroundSize);
        if (composed != null) {
            view.setTag(tag);
            view.setImageBitmap(composed);
            return;
        }
        // Not composed yet: the weather's colour, then the sky fades in
        view.setTag(null);
        view.setImageDrawable(new ColorDrawable(model.baseColor));
        List<String> layers = model.backgroundLayers;
        this.backgrounds.compose(layers, this.backgroundSize, (composedLayers, bitmap) -> {
            if (this.destroyed || view.getTag() != null) {
                return;
            }
            int current = view == this.background ? this.pager.getCurrentItem() : this.pager.getCurrentItem() + 1;
            DetailsModel now = current < this.locationIds.size() ? this.models.get(this.locationIds.get(current)) : null;
            if (now != null && tag.equals(this.backgrounds.skyTag(now.backgroundLayers))) {
                view.setTag(tag);
                view.setImageBitmap(bitmap);
                if (view == this.background) {
                    view.setAlpha(0f);
                    view.animate().alpha(1).setDuration(180).setUpdateListener(animation -> invalidateGlass());
                }
                invalidateGlass();
            }
        });
    }

    // --- Bar and insets ---

    public void applyInsets(int top, int bottom) {
        this.topInset = top;
        this.bottomInset = bottom;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) this.bottomBar.getLayoutParams();
        // Right on the navigation bar, as Samsung Weather's
        params.bottomMargin = bottom;
        this.bottomBar.setLayoutParams(params);
        this.bottomScrim.reachOver(params.bottomMargin + params.height);
        for (DetailsPage page : this.pages) {
            page.applyInsets(top, bottom);
        }
        for (DetailsPage page : this.sparePages) {
            page.applyInsets(top, bottom);
        }
    }

    @Override
    public void onPageScrolled(DetailsPage page, int dy) {
        // Only the city shown moves the bar, not one scrolled aside to keep up with it
        if (Math.abs(dy) < this.barSlop || page != pageFor(getCurrentPlaceId())) {
            return;
        }
        showBar(dy < 0);
    }

    private void showBar(boolean show) {
        if (this.barHidden == !show) {
            return;
        }
        this.barHidden = !show;
        // The buttons fade where they stand, as Samsung Weather's; the shade under them stays. Faded out, they are
        // taken away so that a tap there reaches the page
        if (show) {
            this.bottomBar.setVisibility(View.VISIBLE);
            this.bottomBar.animate().alpha(1f).setDuration(200);
        } else {
            this.bottomBar.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                // Unless shown again meanwhile
                if (this.barHidden) {
                    this.bottomBar.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    @Override
    public void openAstro(Place location, boolean isMoon) {
        this.host.openAstro(location, isMoon);
    }

    @Override
    public void onDaysExpanded(DetailsPage page, boolean expanded) {
        AppSettings.getInstance().saveDetailsDaysExpanded(expanded);
        for (DetailsPage other : this.pages) {
            if (other != page) {
                other.setDailyExpanded(expanded);
            }
        }
        for (DetailsPage spare : this.sparePages) {
            spare.setDailyExpanded(expanded);
        }
    }

    @Override
    public void openEarthquakes(Place location) {
        this.host.openEarthquakes(location);
    }

    @Override
    public void collectSky(List<View> into) {
        into.add(this.background);
        into.add(this.backgroundNext);
    }

    // A new frame of a sky: the glass draws it again, and the live skies fade on if they waited for it
    private void onSkyDrawn() {
        invalidateGlass();
        if (this.appearing != null && this.skyAppear == null && this.appearing.showsScene()) {
            skyFrameCame();
        }
        if (this.weatherWaiting != null && this.weatherWaiting.showsScene()) {
            startWeatherFade();
        }
        if (this.fadeWaiting && this.background.showsScene()) {
            crossFade(this.fadePosition, this.fadeOffset);
        }
    }

    // The pages' glass pills over the sky draw it again: a sky fading in moves nothing that would tell the glass
    private void invalidateGlass() {
        for (DetailsPage page : this.pages) {
            page.invalidateGlass();
        }
    }

    public void destroy() {
        this.destroyed = true;
        this.mainHandler.removeCallbacks(this.weatherWaited);
        if (this.weatherFade != null) {
            this.weatherFade.cancel();
        }
        stopSkyAppear();
        this.executor.shutdownNow();
        for (DetailsPage page : this.pages) {
            page.destroy();
        }
        this.sparePages.clear();
    }

    /** A page per city, made when the pager needs it and bound to the city's model when that is built */
    private final class CitiesAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return DetailsScreen.this.locationIds.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            int locationId = DetailsScreen.this.locationIds.get(position);
            int shownScroll = shownScroll();
            DetailsPage page = DetailsScreen.this.sparePages.poll();
            if (page != null) {
                page.reuse(locationId);
            } else {
                page = new DetailsPage(LayoutInflater.from(container.getContext()), container, locationId, DetailsScreen.this);
            }
            page.applyInsets(DetailsScreen.this.topInset, DetailsScreen.this.bottomInset);
            container.addView(page.root);
            DetailsScreen.this.pages.add(page);
            DetailsModel model = DetailsScreen.this.models.get(locationId);
            if (model != null) {
                page.bind(model);
            }
            page.scrollWith(shownScroll);
            if (!DetailsScreen.this.requests.containsKey(locationId)
                    && (model == null || android.os.SystemClock.uptimeMillis() - model.builtAt > MODEL_FRESH_MS)) {
                // A page that comes back after a while gets fresh data; until then it shows what it had
                request(locationId);
            }
            return page;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            DetailsPage page = (DetailsPage) object;
            container.removeView(page.root);
            page.destroy();
            DetailsScreen.this.pages.remove(page);
            if (DetailsScreen.this.sparePages.size() < MAX_SPARE_PAGES) {
                DetailsScreen.this.sparePages.push(page);
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return ((DetailsPage) object).root == view;
        }

        @Override
        public int getItemPosition(Object object) {
            int index = DetailsScreen.this.locationIds.indexOf(((DetailsPage) object).locationId);
            return index >= 0 ? index : POSITION_NONE;
        }
    }
}
