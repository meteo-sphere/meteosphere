package org.meteosphere.ui.main;

import org.meteosphere.ui.astro.AstroSkies;
import org.meteosphere.ui.astro.MoonFragment;
import org.meteosphere.ui.astro.SunFragment;
import org.meteosphere.ui.details.DetailsActivity;
import org.meteosphere.ui.glass.GlassStrip;
import org.meteosphere.ui.glass.TopFade;
import org.meteosphere.ui.locations.FavoritesFragment;
import org.meteosphere.ui.locations.Favorites;
import org.meteosphere.ui.locations.NearbyFragment;
import org.meteosphere.ui.quakes.EarthquakesFragment;
import org.meteosphere.ui.sky.SkyBackgroundDrawable;
import org.meteosphere.ui.sky.SkyView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.graphics.Bitmap;
import androidx.viewpager.widget.PagerAdapter;
import com.google.android.material.color.MaterialColors;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.text.TextUtils;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBar;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import org.json.JSONException;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.util.AppUtils;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.location.PositionService;
import org.meteosphere.data.model.Place;
import org.meteosphere.ui.common.BottomFadeFrame;
import org.meteosphere.ui.common.BottomScrimView;
import org.meteosphere.ui.common.ListRoom;

/**
 * The main screen's tabs, those the settings show in their order: the favourites, the places nearby, the earthquakes,
 * the Moon and the Sun, a page each in a pager under the floating bar of the tabs. The pages run under the main
 * screen's bar, which they move and band, and fade out under the tabs' bar; the Moon's and the Sun's lie over their
 * skies. A place tapped on a page, or found by the search over the tabs, opens its page, whose answer goes to
 * MainActivity
 */
public final class MainTabsFragment extends Fragment implements FavoritesFragment.OnFavoriteTappedListener,
        NearbyFragment.OnNearbyListener {

    private static final String TAG = "MainTabsFragment";
    // How DetailsActivity opens: the favourites' pages, the current location among them, or one place alone
    private static final String ACTION_OPEN_FAVORITES = "org.meteosphere.action.OPEN_FAVORITES";
    private static final String ACTION_OPEN_PLACE = "org.meteosphere.action.OPEN_PLACE";
    /** The requests a place's page answers MainActivity by: from the favourites, and for a place found or nearby */
    static final int REQUEST_FAVORITES = 1;
    static final int REQUEST_CUSTOM = 2;
    // What the restore opens again: nothing, the favourites' pages, or a place found alone
    static final int RESTORE_NOTHING = 0;
    static final int RESTORE_FAVORITES = 1;
    static final int RESTORE_FOUND = 2;

    // The pages, made once with the fragment; the main screen has the favourites and the nearby places made again
    // with new settings
    FavoritesFragment favoriteFragment;
    NearbyFragment nearbyFragment;
    EarthquakesFragment earthquakesFragment;
    MoonFragment moonFragment;
    SunFragment sunFragment;
    ViewPager viewPager;
    FloatingActionButton fabAddFavorite;
    private OnAddTappedListener addListener;
    // The favourites' cards made again as their local times change and as a forecast comes
    private BroadcastReceiver receiver;
    private IntentFilter receiverFilter;
    // Where the favourites' page is among the pages, which the add button comes and goes with
    private int favoritesPosition;
    private TabPillBar tabBar;
    // Under the tabs' bar the pages fade out, over a shade on dark pages: every page in the dark theme, the Moon's and
    // the Sun's skies in the light one
    private BottomFadeFrame pagesFade;
    private BottomScrimView scrim;
    private boolean darkTheme;
    // How far under the header the content is seen fully again, and how dark the shade over a dark page's top is
    private static final int FADE_UNDER_HEADER_DP = 48;
    private static final float TOP_SHADE = 0.3f;
    // How opaque the band under the title of a page without a row of controls is, of 255
    private static final int TITLE_BAND_ALPHA = 204;
    // The cards of a page without a row of controls fade out as the city details' do, past the title
    private static final int WEATHER_FADE_DP = 92;
    private int titleBand;
    // Each page's fade under the header, made once, gone with the page's view
    private final WeakHashMap<View, TopFade> pageFades = new WeakHashMap<>();
    // Height of the navigation bar the pages run under, from MainActivity
    private int navigationBarInset;
    // The window's background: the Moon's and the Sun's tabs over their skies
    private SkyBackgroundDrawable sky;
    private Bitmap nightSky;
    private Bitmap daySky;
    // Whether the animated weather drew the Moon's and the Sun's skies; they are composed again when it changes
    private boolean skiesLive;
    private int skyPosition;
    private float skyOffset;
    private float chromeSky = -1;
    // The current location is looked for again, as the list shows, after this long
    private static final long LOCATION_SEARCH_INTERVAL_MS = 5 * 60 * 1000L;
    // The status bar's and the main screen's title's heights over the pages' tops
    private int statusInset;
    private int titleHeight;
    // The page whose title is shown, -1 before the first; a new one slides this far as the old one fades out and it in
    private static final float TITLE_SHIFT_DP = 24;
    private static final long TITLE_OUT_MS = 100;
    private static final long TITLE_IN_MS = 200;
    private int titlePosition = -1;
    // A jump to a tab further than the next one goes as long and eases as the pager going a page, by ViewPager's own
    // duration and interpolator for it; the jump under way, the pages it goes between, -1 when there is none, and how
    // far it has gone
    private static final long JUMP_MS = 200;
    private static final TimeInterpolator PAGER_EASING = t -> {
        t -= 1.0f;
        return t * t * t * t * t + 1.0f;
    };
    private Animator jump;
    private int jumpFrom = -1;
    private int jumpTo;
    private float jumpProgress;
    private final ViewTreeObserver.OnScrollChangedListener onListScrolled = this::updateHeader;
    // A new instance is created every time the list comes back into view, so start disarmed:
    // with "now" here the first tap within a second after returning from the details screen was dropped.
    // A place's details being opened: more taps meanwhile open nothing more, until the list shows again
    private boolean isOpeningDetails;

    // The tabs shown, AppSettings.TAB_*, in the order and choice of the settings
    private final List<Integer> pages = new ArrayList<>();

    /** The screen the tabs are on, told of a tap on the add button */
    public interface OnAddTappedListener {
        void onAddTapped();
    }

    /** A page per tab shown, in the order of the settings, named as its tab */
    private final class PagesAdapter extends FragmentPagerAdapter {

        // Only the page on screen is resumed: the tabs that tick or ask for newer pictures do it while they are seen
        PagesAdapter(FragmentManager manager) {
            super(manager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (MainTabsFragment.this.pages.get(position)) {
                case AppSettings.TAB_NEARBY:
                    return MainTabsFragment.this.nearbyFragment;
                case AppSettings.TAB_EARTHQUAKES:
                    return MainTabsFragment.this.earthquakesFragment;
                case AppSettings.TAB_MOON:
                    return MainTabsFragment.this.moonFragment;
                case AppSettings.TAB_SUN:
                    return MainTabsFragment.this.sunFragment;
                default:
                    return MainTabsFragment.this.favoriteFragment;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(tabTitle(MainTabsFragment.this.pages.get(position)));
        }

        @Override
        public int getCount() {
            return MainTabsFragment.this.pages.size();
        }
    }

    public static MainTabsFragment newInstance() {
        return new MainTabsFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.addListener = getActivity() instanceof OnAddTappedListener ? (OnAddTappedListener) getActivity() : null;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.favoriteFragment = FavoritesFragment.newInstance();
        this.nearbyFragment = NearbyFragment.newInstance();
        this.earthquakesFragment = EarthquakesFragment.newInstance().asTab();
        this.moonFragment = MoonFragment.newInstance();
        this.sunFragment = SunFragment.newInstance();
        this.receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                MainTabsFragment.this.favoriteFragment.refreshCards();
            }
        };
        this.receiverFilter = new IntentFilter();
        // Each minute, for the cards' local times
        this.receiverFilter.addAction(Intent.ACTION_TIME_TICK);
        this.receiverFilter.addAction(WeatherUpdater.ACTION_PLACES_CHANGED);
        this.receiverFilter.addAction(WeatherUpdater.ACTION_CURRENT_CHANGED);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.fragment_main_tabs, container, false);
        this.pages.clear();
        this.pages.addAll(AppSettings.getInstance().getMainTabs());
        this.favoritesPosition = this.pages.indexOf(AppSettings.TAB_FAVORITES);
        this.viewPager = view.findViewById(R.id.vpTabs);
        this.pagesFade = (BottomFadeFrame) this.viewPager.getParent();
        this.scrim = view.findViewById(R.id.bottomScrim);
        this.darkTheme = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        // Every page is dark in the dark theme; in the light one only the skies are, see showSkies
        this.scrim.setAlpha(this.darkTheme ? 1 : 0);
        // The pages' views come as the pager makes them, later than the insets: each one's lists get their room then
        this.viewPager.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                ListRoom.atEnd(child, roomAtEnd());
                keepRoomAtTop(child);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
        this.viewPager.setAdapter(new PagesAdapter(getChildFragmentManager()));
        // All the tabs kept made, so that a swipe only moves them
        this.viewPager.setOffscreenPageLimit(Math.max(1, this.pages.size() - 1));
        setUpTabBar(view);
        this.fabAddFavorite = view.findViewById(R.id.fabAddFavorite);
        this.fabAddFavorite.setOnClickListener(button -> {
            if (this.addListener != null) {
                this.addListener.onAddTapped();
            }
        });
        this.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                showSky(position, offset);
                updateHeader();
                // Between two pages the add button comes in and goes out with the favourites' page
                if (offset > 0) {
                    int favorites = MainTabsFragment.this.favoritesPosition;
                    showFab(position + 1 == favorites ? offset : position == favorites ? 1 - offset : 0);
                }
            }

            @Override
            public void onPageSelected(int position) {
                MainTabsFragment.this.tabBar.setSelected(position, true);
                showTitle(position);
                updateSearchButton();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    pageSettled();
                }
            }
        });
        AppUtils.registerAppReceiver(requireContext(), this.receiver, this.receiverFilter);
        int position = firstPosition(this.pages, AppSettings.getInstance().getLastMainTab());
        this.viewPager.setCurrentItem(position, false);
        showTitle(position);
        updateSearchButton();
        this.tabBar.setSelected(position, false);
        applyBottomInset(view);
        onPageShown(this.pages.get(position));
        setUpSky();
        return view;
    }

    // Back from a place's page another may open. The skies are composed again for the animated weather turned on or
    // off since, and the device's position is looked for again once the places around are old
    @Override
    public void onResume() {
        super.onResume();
        this.isOpeningDetails = false;
        Context context = requireContext();
        if (SkyView.liveEnabled(context) != this.skiesLive) {
            loadAstroSkies(context);
        }
        AppSettings settings = AppSettings.getInstance();
        long now = System.currentTimeMillis();
        if (isLocationSearchDue(settings.getNearbyTime().getTimeInMillis(), settings.getLastLocationSearchTime(), now)) {
            settings.saveLastLocationSearchTime(now);
            PositionService.start(requireActivity(), settings.withGps);
        }
    }

    // The view goes with the fragment, as the main screen stops; the receiver was registered with it
    @Override
    public void onDestroy() {
        if (this.viewPager != null) {
            requireContext().unregisterReceiver(this.receiver);
        }
        super.onDestroy();
    }

    /** The page shown first: the tab shown last when it is still among the pages, else the favourites, always there */
    static int firstPosition(List<Integer> pages, int lastTab) {
        int position = pages.indexOf(lastTab);
        return position >= 0 ? position : pages.indexOf(AppSettings.TAB_FAVORITES);
    }

    /**
     * Whether the device's position is to be looked for again: the nearby places or the last search for them,
     * whichever is later, five minutes old or more, or later than now, the clock set back. A search that found
     * nothing saves no places, and without its own time it would start again on each return to the app
     */
    static boolean isLocationSearchDue(long placesTime, long searchTime, long now) {
        long last = Math.max(placesTime, searchTime);
        return now < last || now - last >= LOCATION_SEARCH_INTERVAL_MS;
    }

    /**
     * Opens again, when the app was left on a place's page or a notification's tap asks for one, the place shown last:
     * the current location among the favourites while it is kept, a favourite among them, or a place found alone
     * while it is stored. Whether a page opens
     */
    public boolean restoreLastScreen() {
        AppSettings settings = AppSettings.getInstance();
        if (!DetailsActivity.class.getCanonicalName().equalsIgnoreCase(settings.getShownScreen())) {
            return false;
        }
        this.isOpeningDetails = false;
        int id = settings.getShownPlace();
        Place stored = id != -1 ? settings.getPlace(id) : null;
        switch (restoreChoice(id, settings.getCurrentPlace() != null, stored != null, settings.getFavorites().contains(id))) {
            case RESTORE_FAVORITES:
                if (id == -1) {
                    stored = new Place();
                    stored.id = -1;
                }
                onFavoriteTapped(stored);
                return true;
            case RESTORE_FOUND:
                openPlace(stored);
                return true;
            default:
                return false;
        }
    }

    /**
     * What the restore opens for the place shown last: for -1 the favourites' pages while a current location is kept;
     * for a place still stored its page, among the favourites when it is one; nothing for none, as 0 and -2 say
     */
    static int restoreChoice(int lastShown, boolean currentKept, boolean stored, boolean favorite) {
        if (lastShown == -1) {
            return currentKept ? RESTORE_FAVORITES : RESTORE_NOTHING;
        }
        if (lastShown <= 0 || !stored) {
            return RESTORE_NOTHING;
        }
        return favorite ? RESTORE_FAVORITES : RESTORE_FOUND;
    }

    // A favourite's page among the favourites' pages, as the current location's
    @Override
    public void onFavoriteTapped(Place location) {
        if (location != null) {
            openDetails(ACTION_OPEN_FAVORITES, location, REQUEST_FAVORITES);
        }
    }

    @Override
    public void onNearbyTapped(Place location) {
        openPlace(location);
    }

    @Override
    public void onAddFavorite(Place location) {
        if (isAdded()) {
            Favorites.add(requireActivity(), location);
        }
    }

    /**
     * A place found or nearby on a page of its own: as it is stored when it has its hours and its days, else the
     * place given, stored first without a load time so that its forecast is loaded
     */
    public void openPlace(Place location) {
        if (location == null || this.isOpeningDetails) {
            return;
        }
        AppSettings settings = AppSettings.getInstance();
        Place shown = settings.getPlace(location.id);
        if (shown == null || shown.hours.isEmpty() || shown.days.isEmpty()) {
            location.loadedAt = 0;
            settings.storePlace(location);
            shown = location;
        }
        openDetails(ACTION_OPEN_PLACE, shown, REQUEST_CUSTOM);
    }

    // The page is the activity's, which hears its answer by the request; one at a time, till the tabs resume
    private void openDetails(String action, Place place, int request) {
        if (this.isOpeningDetails || !isAdded()) {
            return;
        }
        this.isOpeningDetails = true;
        Intent intent = withPlace(new Intent(requireContext(), DetailsActivity.class).setAction(action), place);
        requireActivity().startActivityForResult(intent, request);
    }

    // The place as its JSON; one that can't be written leaves the page without it, and it closes at once
    private static Intent withPlace(Intent intent, Place place) {
        try {
            intent.putExtra(Place.class.getCanonicalName(), place.toJson().toString());
        } catch (JSONException e) {
            Log.w(TAG, "The place's page opens without its place", e);
        }
        return intent;
    }

    private static int tabTitle(int page) {
        switch (page) {
            case AppSettings.TAB_NEARBY:
                return R.string.tab_nearby;
            case AppSettings.TAB_EARTHQUAKES:
                return R.string.tab_earthquakes;
            case AppSettings.TAB_MOON:
                return R.string.moon;
            case AppSettings.TAB_SUN:
                return R.string.tab_sun;
            default:
                return R.string.tab_favorites;
        }
    }

    // A tab further than the next one is gone to as the pager goes to the next one, not scrolled to past the pages
    // between: the tapped page is set right beside the one shown, the pages between hidden, and the two slide over
    // at the pager's own speed and easing for a page; the skies and the add button change as they would
    private void showPage(int index) {
        if (this.jump != null) {
            // At the page it was going to
            this.jump.end();
        }
        int from = this.viewPager.getCurrentItem();
        View fromPage = pageView(from);
        View toPage = pageView(index);
        if (Math.abs(index - from) <= 1 || !this.viewPager.isLaidOut() || fromPage == null || toPage == null) {
            this.viewPager.setCurrentItem(index, true);
            return;
        }
        int direction = index > from ? 1 : -1;
        int width = fromPage.getWidth();
        List<View> between = new ArrayList<>();
        for (int i = from + direction; i != index; i += direction) {
            View page = pageView(i);
            if (page != null) {
                page.setVisibility(View.INVISIBLE);
                between.add(page);
            }
        }
        int favorites = this.pages.indexOf(AppSettings.TAB_FAVORITES);
        float fabFrom = from == favorites ? 1 : 0;
        float fabTo = index == favorites ? 1 : 0;
        ValueAnimator jump = ValueAnimator.ofFloat(0f, 1f).setDuration(JUMP_MS);
        jump.setInterpolator(PAGER_EASING);
        // Once the pager stands at the tapped page, the page shown is put back where it was and the tapped one beside it
        ValueAnimator.AnimatorUpdateListener move = animation -> {
            float progress = (float) animation.getAnimatedValue();
            this.jumpProgress = progress;
            fromPage.setTranslationX((index - from - direction * progress) * width);
            toPage.setTranslationX(direction * (1 - progress) * width);
            showSky(this.skyPosition, this.skyOffset);
            showFab(fabFrom + (fabTo - fabFrom) * progress);
        };
        jump.addUpdateListener(move);
        // Cancelled only as the view goes
        jump.addListener(new AnimatorListenerAdapter() {
            private boolean isCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                this.isCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                MainTabsFragment.this.jump = null;
                MainTabsFragment.this.jumpFrom = -1;
                fromPage.setTranslationX(0);
                toPage.setTranslationX(0);
                for (View page : between) {
                    page.setVisibility(View.VISIBLE);
                }
                if (!this.isCancelled) {
                    showSky(index, 0);
                    pageSettled();
                }
            }
        });
        this.jumpFrom = from;
        this.jumpTo = index;
        this.jumpProgress = 0;
        this.jump = jump;
        // The title, the tab and the header go to the tapped page at once, as when the pager scrolls to it
        this.viewPager.setCurrentItem(index, false);
        move.onAnimationUpdate(jump);
        jump.start();
    }

    // The add button as much shown as given, 0 to 1: it scales, and shows only once most of it is there
    private void showFab(float shown) {
        this.fabAddFavorite.setScaleX(shown);
        this.fabAddFavorite.setScaleY(shown);
        this.fabAddFavorite.setAlpha(shown < 0.7f ? 0.0f : Math.max(0.0f, 1.0f - (3.0f * (1.0f - shown))));
    }

    // The page the pager came to rest on
    private void pageSettled() {
        int page = this.pages.get(this.viewPager.getCurrentItem());
        onPageShown(page);
        AppSettings.getInstance().setLastMainTab(page);
    }

    // The add button is for the favorites only
    private void onPageShown(int page) {
        float fabScale = page == AppSettings.TAB_FAVORITES ? 1.0f : 0.0f;
        this.fabAddFavorite.setScaleX(fabScale);
        this.fabAddFavorite.setScaleY(fabScale);
        this.fabAddFavorite.setAlpha(fabScale);
    }

    /**
     * The status bar's and the title's heights over the pages: their lists start under the title, or under their
     * rows of controls under it
     */
    public void setHeaderInsets(int statusInset, int titleHeight) {
        if (statusInset == this.statusInset && titleHeight == this.titleHeight) {
            return;
        }
        this.statusInset = statusInset;
        this.titleHeight = titleHeight;
        if (this.viewPager != null) {
            for (int i = 0; i < this.viewPager.getChildCount(); i++) {
                keepRoomAtTop(this.viewPager.getChildAt(i));
            }
            updateHeader();
        }
    }

    // The bar's title is the name of the tab shown; a new one comes in sliding a little the way the pages go, the old
    // one fading out and it in
    private void showTitle(int position) {
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar == null || position < 0 || position >= this.pages.size()) {
            return;
        }
        CharSequence title = this.viewPager.getAdapter().getPageTitle(position);
        Toolbar toolbar = requireActivity().findViewById(R.id.tbMain);
        TextView titleView = titleView(toolbar);
        int from = this.titlePosition;
        this.titlePosition = position;
        if (titleView == null || from < 0 || from == position || !toolbar.isLaidOut()) {
            actionBar.setTitle(title);
            return;
        }
        float shift = TITLE_SHIFT_DP * getResources().getDisplayMetrics().density * (position > from ? 1 : -1);
        // From where it is, when it is still changing
        titleView.animate().cancel();
        titleView.animate().alpha(0f).translationX(-shift).setDuration(TITLE_OUT_MS).setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    actionBar.setTitle(title);
                    titleView.setTranslationX(shift);
                    titleView.animate().alpha(1f).translationX(0f).setDuration(TITLE_IN_MS).setInterpolator(new DecelerateInterpolator()).start();
                }).start();
    }

    /**
     * The search finds places for their weather: it is on the favorites and the nearby pages, not on the Moon's, the
     * Sun's or the earthquakes', which choose their place themselves
     */
    public void updateSearchButton() {
        if (this.viewPager == null || getActivity() == null) {
            return;
        }
        Toolbar toolbar = requireActivity().findViewById(R.id.tbMain);
        MenuItem search = toolbar == null ? null : toolbar.getMenu().findItem(R.id.action_search);
        int position = this.viewPager.getCurrentItem();
        if (search != null && position >= 0 && position < this.pages.size()) {
            int page = this.pages.get(position);
            search.setVisible(page == AppSettings.TAB_FAVORITES || page == AppSettings.TAB_NEARBY);
        }
    }

    // The toolbar's own view of its title
    private static TextView titleView(Toolbar toolbar) {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView && TextUtils.equals(((TextView) child).getText(), toolbar.getTitle())) {
                return (TextView) child;
            }
        }
        return null;
    }

    /**
     * On a page with a row of controls the title goes up as far as the page has scrolled, fading as it goes under
     * the status bar, and the row takes its place. On a page without one the title stays, and a see-through band of
     * the background fades in under it as the content runs under it. The content dissolves into the background under
     * the title and the row, see updatePageFades. Between two pages the pager moves, the bar is in turn as each of
     * them would have it.
     */
    private void updateHeader() {
        if (this.viewPager == null || isHidden() || !isAdded() || this.titleHeight == 0) {
            return;
        }
        GlassStrip bar = requireActivity().findViewById(R.id.appBar);
        View toolbar = requireActivity().findViewById(R.id.tbMain);
        int position = this.viewPager.getCurrentItem();
        float offset = 0;
        if (this.skyPosition == position || this.skyPosition == position - 1) {
            position = this.skyPosition;
            offset = this.skyOffset;
        }
        float collapse = headerCollapse(position);
        float band = barBand(position);
        if (offset > 0 && position + 1 < this.pages.size()) {
            collapse += (headerCollapse(position + 1) - collapse) * offset;
            band += (barBand(position + 1) - band) * offset;
        }
        bar.setTranslationY(-collapse);
        // The title is gone well before it reaches the status bar's icons
        float shown = Math.max(0f, 1f - collapse / (this.titleHeight * 0.4f));
        toolbar.setAlpha(shown);
        toolbar.setVisibility(shown > 0 ? View.VISIBLE : View.INVISIBLE);
        // A see-through band of the background rather than glass, showing as much as the glass would
        if (this.titleBand == 0) {
            this.titleBand = (MaterialColors.getColor(bar, android.R.attr.colorBackground) & 0x00ffffff) | TITLE_BAND_ALPHA << 24;
        }
        bar.setBand(band > 0 ? this.titleBand : 0);
        bar.setGlassAlpha(band);
        updatePageFades(position, offset > 0 ? position + 1 : position);
    }

    /**
     * Each page's content dissolves into the window's background under the header, as on the tabs opened on their
     * own: gone above the row of controls on a page that has one, following it as it rises, seen a little through it
     * and fully again a little under it, a shade from the screen's top on a dark page; on a page without one, the
     * favorites and the nearby, as the city details' cards, under the title's band. Drawn anew as the pages on screen
     * and their lists move, the background staying.
     */
    private void updatePageFades(int first, int last) {
        int fadeUnder = Math.round(FADE_UNDER_HEADER_DP * getResources().getDisplayMetrics().density);
        for (int i = Math.max(0, first); i <= last && i < this.pages.size(); i++) {
            View page = pageView(i);
            if (!(page instanceof ViewGroup)) {
                continue;
            }
            TopFade fade = this.pageFades.get(page);
            GlassStrip strip = hasStrip(page) ? GlassStrip.find(page) : null;
            if (fade == null) {
                ViewGroup root = (ViewGroup) page;
                fade = new TopFade(page.getContext());
                // Under the row, over the content
                int index = strip != null && strip.getParent() == root ? root.indexOfChild(strip) : root.getChildCount();
                root.addView(fade, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                this.pageFades.put(page, fade);
                if (strip != null) {
                    strip.setWithoutGlass();
                    // A row grown or shrunk without a scroll, as for a longer place, moves where the fade lies
                    strip.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        if (bottom - top != oldBottom - oldTop) {
                            updateHeader();
                        }
                    });
                }
            }
            float shade = this.darkTheme || hasSky(i) ? TOP_SHADE : 0;
            if (strip != null) {
                // Where the row stands now, as GlassStrip puts it: under the title, rising into its place
                View list = pageList(i);
                int rowTop = list == null ? this.titleHeight : this.titleHeight - HeaderScroll.collapse(HeaderScroll.depth(list), this.titleHeight);
                fade.set(rowTop + strip.getPaddingTop(), rowTop + strip.getHeight() + fadeUnder, shade);
            } else {
                // As the city details' cards: gone at the screen's top, fading in down to past the title, under the
                // bar's band. The stretch stays put and shows more as the list scrolls, none at rest
                View list = pageList(i);
                int length = this.statusInset + Math.round(WEATHER_FADE_DP * getResources().getDisplayMetrics().density);
                fade.set(0, length, 0);
                fade.setStrength(list == null ? 0 : HeaderScroll.depth(list) / (float) length);
            }
        }
    }

    private View pageView(int position) {
        return ((FragmentPagerAdapter) this.viewPager.getAdapter()).getItem(position).getView();
    }

    private View pageList(int position) {
        View page = pageView(position);
        if (page == null) {
            return null;
        }
        Object list = page.getTag(R.id.tag_blurred_list);
        if (!(list instanceof View)) {
            list = ListRoom.find(page);
            page.setTag(R.id.tag_blurred_list, list);
        }
        return (View) list;
    }

    // Whether the page has a row of controls on glass, looked for once per page, not on every frame of a scroll
    private static boolean hasStrip(View page) {
        if (page == null) {
            return false;
        }
        Object found = page.getTag(R.id.tag_page_strip);
        if (!(found instanceof Boolean)) {
            found = GlassStrip.find(page) != null;
            page.setTag(R.id.tag_page_strip, found);
        }
        return (Boolean) found;
    }

    // How much of the bar's band shows, for a page that has no row of controls: as the content runs under it
    private float barBand(int position) {
        View list = pageList(position);
        if (list == null || hasStrip(pageView(position))) {
            return 0;
        }
        return HeaderScroll.glass(requireContext(), HeaderScroll.depth(list), 0);
    }

    private float headerCollapse(int position) {
        View list = pageList(position);
        if (list == null || !hasStrip(pageView(position))) {
            return 0;
        }
        return HeaderScroll.collapse(HeaderScroll.depth(list), this.titleHeight);
    }

    // A page's row of controls stands under the title, its list under the row; another page's list under the title
    private void keepRoomAtTop(View page) {
        GlassStrip strip = GlassStrip.find(page);
        if (strip != null) {
            strip.setHeader(this.statusInset, this.titleHeight);
            return;
        }
        View list = ListRoom.find(page);
        if (list != null) {
            ListRoom.setTop(list, this.statusInset + this.titleHeight);
        }
    }

    /** The navigation bar's height under the pages; the keyboard lies over them without changing it */
    public void setNavigationBarInset(int bottom) {
        if (bottom == this.navigationBarInset && this.tabBar != null) {
            return;
        }
        this.navigationBarInset = bottom;
        View view = getView();
        if (view == null) {
            return;
        }
        applyBottomInset(view);
    }

    // The toolbar and the add button over the navigation bar, and the lists' room under them
    private void applyBottomInset(View view) {
        boolean hasBar = this.pages.size() > 1;
        ViewGroup.MarginLayoutParams barParams = (ViewGroup.MarginLayoutParams) this.tabBar.getLayoutParams();
        barParams.bottomMargin = this.navigationBarInset + getResources().getDimensionPixelSize(R.dimen.tab_bar_margin);
        this.tabBar.setLayoutParams(barParams);
        ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) this.fabAddFavorite.getLayoutParams();
        fabParams.bottomMargin = this.navigationBarInset + (hasBar ? getResources().getDimensionPixelSize(R.dimen.tab_bar_fab_margin) : fabParams.getMarginEnd());
        this.fabAddFavorite.setLayoutParams(fabParams);
        // Nothing to read over the bottom with the favorites alone
        this.scrim.setVisibility(hasBar ? View.VISIBLE : View.GONE);
        if (!hasBar) {
            this.pagesFade.setFadeLength(0);
        }
        fitUnderBar();
        for (int i = 0; i < this.viewPager.getChildCount(); i++) {
            ListRoom.atEnd(this.viewPager.getChildAt(i), roomAtEnd());
        }
    }

    // The pages fade out up to the bar's top, the shade reaching over it; the bar's height is known once it is laid out
    private void fitUnderBar() {
        if (this.tabBar.getVisibility() == View.VISIBLE && this.tabBar.getHeight() > 0) {
            int barTop = ((ViewGroup.MarginLayoutParams) this.tabBar.getLayoutParams()).bottomMargin + this.tabBar.getHeight();
            this.pagesFade.setFadeLength(barTop);
            this.scrim.reachOver(barTop);
        }
    }

    private int roomAtEnd() {
        return this.navigationBarInset + (this.pages.size() > 1 ? getResources().getDimensionPixelSize(R.dimen.tab_bar_space) : 0);
    }

    // The floating bar of the tabs: an icon per page, in the order of the settings
    private void setUpTabBar(View view) {
        this.tabBar = view.findViewById(R.id.tabPill);
        int[] icons = new int[this.pages.size()];
        CharSequence[] names = new CharSequence[this.pages.size()];
        PagerAdapter adapter = this.viewPager.getAdapter();
        for (int i = 0; i < this.pages.size(); i++) {
            icons[i] = tabIcon(this.pages.get(i));
            names[i] = adapter.getPageTitle(i);
        }
        this.tabBar.setTabs(icons, names);
        this.tabBar.setBlurSource(this.viewPager);
        this.tabBar.setOnTabSelectedListener(this::showPage);
        this.tabBar.addOnLayoutChangeListener((bar, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> fitUnderBar());
        // With the favorites alone there is nothing to switch to
        if (this.pages.size() <= 1) {
            this.tabBar.setVisibility(View.GONE);
        }
    }

    private static int tabIcon(int page) {
        switch (page) {
            case AppSettings.TAB_NEARBY:
                return R.drawable.ic_place;
            case AppSettings.TAB_EARTHQUAKES:
                return R.drawable.ic_stat_earthquake;
            case AppSettings.TAB_MOON:
                return R.drawable.ic_details_moon;
            case AppSettings.TAB_SUN:
                return R.drawable.ic_widget_sun;
            default:
                return R.drawable.ic_details_cities;
        }
    }

    // The night sky for the Moon's tab and the day's for the Sun's, composed off the main thread
    private void setUpSky() {
        Context context = requireContext();
        this.sky = new SkyBackgroundDrawable(MaterialColors.getColor(requireActivity().getWindow().getDecorView(), android.R.attr.colorBackground));
        requireActivity().getWindow().setBackgroundDrawable(this.sky);
        loadAstroSkies(context);
        showSky(this.viewPager.getCurrentItem(), 0);
    }

    private void loadAstroSkies(Context context) {
        this.skiesLive = SkyView.liveEnabled(context);
        if (this.pages.contains(AppSettings.TAB_MOON)) {
            AstroSkies.load(context, false, (layers, bitmap) -> {
                this.nightSky = bitmap;
                refreshSky();
            });
        }
        if (this.pages.contains(AppSettings.TAB_SUN)) {
            AstroSkies.load(context, true, (layers, bitmap) -> {
                this.daySky = bitmap;
                refreshSky();
            });
        }
    }

    private boolean hasSky(int position) {
        if (position < 0 || position >= this.pages.size()) {
            return false;
        }
        int page = this.pages.get(position);
        return page == AppSettings.TAB_MOON || page == AppSettings.TAB_SUN;
    }

    private Bitmap skyOf(int position) {
        if (!hasSky(position)) {
            return null;
        }
        return this.pages.get(position) == AppSettings.TAB_MOON ? this.nightSky : this.daySky;
    }

    private void refreshSky() {
        if (this.sky != null && isAdded() && !isHidden()) {
            showSky(this.skyPosition, this.skyOffset);
            // The toolbar's and the bar's glass blur the sky too
            this.tabBar.invalidate();
            requireActivity().findViewById(R.id.appBar).invalidate();
        }
    }

    // The skies of the page and the next one as much as the pager shows each, or of the pages jumped between as far
    // as the jump has gone
    void showSky(int position, float offset) {
        this.skyPosition = position;
        this.skyOffset = offset;
        if (this.sky == null) {
            return;
        }
        if (this.jumpFrom >= 0) {
            showSkies(this.jumpFrom, this.jumpTo, this.jumpProgress);
        } else {
            showSkies(position, position + 1, offset);
        }
    }

    // The sky of the first page, and of the second one over it as far as given, 0 to 1
    private void showSkies(int first, int second, float amount) {
        Bitmap current = skyOf(first);
        Bitmap next = amount > 0 ? skyOf(second) : null;
        if (current != null && next != null) {
            this.sky.setSkies(current, 1, next, amount);
        } else {
            this.sky.setSkies(current, 1 - amount, next, amount);
        }
        float overSky = (hasSky(first) ? 1 - amount : 0) + (amount > 0 && hasSky(second) ? amount : 0);
        showChromeOverSky(overSky);
        this.scrim.setAlpha(this.darkTheme ? 1 : overSky);
        // The fades of the pages on screen show the sky as it is now
        for (int i : new int[] {first, second}) {
            View page = i >= 0 && i < this.pages.size() ? pageView(i) : null;
            TopFade fade = page == null ? null : this.pageFades.get(page);
            if (fade != null) {
                fade.invalidate();
            }
        }
    }

    /** Colours the bar's title and icons again, its menu made anew: white over a sky in the light theme */
    public void reapplyChrome() {
        this.chromeSky = -1;
        if (this.sky != null && !isHidden()) {
            showSky(this.skyPosition, this.skyOffset);
        }
    }

    private void showChromeOverSky(float amount) {
        // Only when it changes
        if (amount == this.chromeSky || !isAdded()) {
            return;
        }
        this.chromeSky = amount;
        AstroSkies.applyContentColors(requireActivity(), requireActivity().findViewById(R.id.tbMain), amount);
    }

    // The search covers the tabs: no sky behind it, and the bar's glass rather than the tabs' band
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden && getActivity() != null) {
            ((GlassStrip) requireActivity().findViewById(R.id.appBar)).setBand(0);
        }
        if (this.sky == null) {
            return;
        }
        if (hidden) {
            this.sky.setSkies(null, 0, null, 0);
            showChromeOverSky(0);
        } else {
            showSky(this.viewPager.getCurrentItem(), 0);
            updateHeader();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Any list scrolling moves the title and the glass, as the pages' rows follow their own lists
        view.getViewTreeObserver().addOnScrollChangedListener(this.onListScrolled);
        view.post(this::updateHeader);
    }

    @Override
    public void onDestroyView() {
        if (this.jump != null) {
            this.jump.cancel();
        }
        View view = getView();
        if (view != null) {
            view.getViewTreeObserver().removeOnScrollChangedListener(this.onListScrolled);
        }
        super.onDestroyView();
    }
}
