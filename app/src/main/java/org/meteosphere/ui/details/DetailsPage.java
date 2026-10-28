package org.meteosphere.ui.details;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.glass.GlassPill;
import org.meteosphere.ui.sky.SkyWeather;

/**
 * A city's page of the details. Binds a prepared DetailsModel to its cards and folds the header into the pill as
 * the page scrolls: only alpha and translation change while scrolling, nothing is laid out again. The header, the
 * tiles and the Sun's and the Moon's cards are bound by parts of their own.
 */
public final class DetailsPage {

    public interface Host {
        /** The page scrolled by dy; the bar at the bottom hides while reading down */
        void onPageScrolled(DetailsPage page, int dy);

        void openAstro(Place location, boolean isMoon);

        void openEarthquakes(Place location);

        /** The skies at the back, blurred under the pill's glass with the page's cards */
        void collectSky(List<View> into);

        /** The days were switched to detail or back to brief on this page, for the other pages and the next time */
        void onDaysExpanded(DetailsPage page, boolean expanded);
    }

    // Room at the end of the content for the cities' bar, 56 dp high on the navigation bar, and a gap over it
    private static final int BAR_ROOM_DP = 84;
    // The pill of the city's name, 44 dp high, stands this far under the status bar
    private static final int PILL_TOP_DP = 6;
    // How far under the status bar the content fades out: past the pill
    private static final int FADE_DP = 92;
    // How far over the navigation bar the content fades out at the bottom: up to the clear bar's top
    private static final int BOTTOM_FADE_DP = 56;
    // A card brought to the top stops this far under the status bar, clear of the pill
    private static final int UNDER_PILL_DP = 56;
    // The cards' corners, as bg_details_card has them
    private static final int CARD_RADIUS_DP = 24;

    int locationId;
    final View root;
    private final Context context;
    private final Host host;
    private final float density;
    private final DetailsScrollView scroll;
    private final LinearLayout content;
    private final ViewGroup headerView;
    private final DetailsHeader header;
    // The moves of the last bind, while they last
    private BindMotion motion;
    private final TextView tvNoData;
    private final View cardHourly;
    private final View piForecast;
    private final View llSource;
    private final TextView tvSummary;
    private final ForecastStripView stripHourly;
    private final View cardInsights;
    private final InsightsPager pagerInsights;
    private final PageDotsView dotsInsights;
    private final InsightsAdapter insightsAdapter;
    private final View cardDaily;
    private final DailyRangeView dailyList;
    private final ForecastStripView stripDaily;
    private final TextView tvDailyMore;
    private final DetailsTiles tiles;
    private final DetailsAstro astro;
    private final TextView tvSource;
    private final TextView tvUpdated;
    private final GlassPill pill;

    private DetailsModel model;
    private boolean dailyExpanded;
    private int cardColor;
    private int topInset;
    // How far down the page is to stand once its cards are laid out, -1 for nowhere in particular
    private int pendingScroll = -1;
    // The page moves itself to keep the card read in place: not the reader's scroll, which hides the cities' bar
    private boolean keepingPlace;
    public DetailsPage(LayoutInflater inflater, ViewGroup parent, int locationId, Host host) {
        this.locationId = locationId;
        this.host = host;
        this.context = parent.getContext();
        this.density = this.context.getResources().getDisplayMetrics().density;
        this.root = inflater.inflate(R.layout.details_page, parent, false);
        this.root.setTag(this);
        this.scroll = this.root.findViewById(R.id.scroll);
        this.content = this.root.findViewById(R.id.content);
        this.pill = this.root.findViewById(R.id.pill);
        this.header = new DetailsHeader(this.context, this.root, this.pill);
        this.headerView = this.root.findViewById(R.id.header);
        this.tvNoData = this.root.findViewById(R.id.tvNoData);
        this.cardHourly = this.root.findViewById(R.id.cardHourly);
        this.piForecast = this.root.findViewById(R.id.piForecast);
        this.llSource = this.root.findViewById(R.id.footer);
        this.tvSummary = this.root.findViewById(R.id.tvSummary);
        this.stripHourly = this.root.findViewById(R.id.stripHourly);
        this.cardInsights = this.root.findViewById(R.id.cardInsights);
        this.pagerInsights = this.root.findViewById(R.id.pagerInsights);
        this.dotsInsights = this.root.findViewById(R.id.dotsInsights);
        this.cardDaily = this.root.findViewById(R.id.cardDaily);
        this.dailyList = this.root.findViewById(R.id.dailyList);
        this.stripDaily = this.root.findViewById(R.id.stripDaily);
        this.tvDailyMore = this.root.findViewById(R.id.tvDailyMore);
        this.tiles = new DetailsTiles(this.context, this.root.findViewById(R.id.tiles), this::openEarthquakes);
        this.astro = new DetailsAstro(this.context, this.root, this::openAstro);
        this.tvSource = this.root.findViewById(R.id.tvSource);
        this.tvUpdated = this.root.findViewById(R.id.tvUpdated);

        // Nothing is shown until the city's model is bound: empty cards would flash while it is built
        this.content.setVisibility(View.INVISIBLE);

        this.insightsAdapter = new InsightsAdapter(inflater);
        this.pagerInsights.setAdapter(this.insightsAdapter);
        this.pagerInsights.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                DetailsPage.this.dotsInsights.setCurrent(position);
            }
        });

        this.scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, oldX, oldY) -> {
            this.header.collapse(y);
            if (!this.keepingPlace) {
                this.host.onPageScrolled(this, y - oldY);
            }
        });
        this.pill.setBlurSources(into -> {
            this.host.collectSky(into);
            into.add(this.scroll);
        });
        this.pill.setOnClickListener(v -> this.scroll.smoothScrollTo(0, 0));
        this.scroll.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (this.pendingScroll >= 0 && this.model != null) {
                int y = this.pendingScroll;
                this.pendingScroll = -1;
                this.scroll.scrollTo(0, y);
            }
        });
        setDailyExpanded(AppSettings.getInstance().isDetailsDaysExpanded());
        this.tvDailyMore.setOnClickListener(v -> {
            setDailyExpanded(!this.dailyExpanded);
            this.host.onDaysExpanded(this, this.dailyExpanded);
        });
    }

    private void openAstro(boolean isMoon) {
        if (this.model != null && this.model.location != null) {
            this.host.openAstro(this.model.location, isMoon);
        }
    }

    private void openEarthquakes() {
        if (this.model != null && this.model.location != null) {
            this.host.openEarthquakes(this.model.location);
        }
    }

    /** Room for the status bar at the top and for the navigation and the cities' bar at the bottom */
    void applyInsets(int top, int bottom) {
        this.topInset = top;
        this.content.setPadding(0, top, 0, bottom + Math.round(BAR_ROOM_DP * this.density));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) this.pill.getLayoutParams();
        params.topMargin = top + Math.round(PILL_TOP_DP * this.density);
        this.pill.setLayoutParams(params);
        // The content fades out under the status bar and the pill, and under the clear bar at the bottom
        this.scroll.setFadingEdgeLength(top + Math.round(FADE_DP * this.density));
        this.scroll.setBottomFadeLength(bottom + Math.round(BOTTOM_FADE_DP * this.density));
    }

    DetailsModel getModel() {
        return this.model;
    }

    void bind(DetailsModel model) {
        // Bound again, as the forecast is updated or the minute passes: the card at the top stays where it is read,
        // whatever grows or shrinks over it
        if (this.model != null && this.pendingScroll < 0 && this.scroll.getScrollY() > 0) {
            keepInPlace(cardAt(this.scroll.getScrollY()));
        }
        // Over a forecast shown, what the bind moves slides and a header's line showing something else fades (see
        // BindMotion); the slides only at the top, where keepInPlace leaves the cards to move
        finishMotion();
        BindMotion motion = null;
        if (this.model != null && this.model.hasData && model.hasData && this.content.isLaidOut()) {
            boolean fade = !DetailsHeader.shows(this.model).equals(DetailsHeader.shows(model));
            boolean slide = this.scroll.getScrollY() == 0;
            if (fade || slide) {
                motion = new BindMotion(this.headerView, this.content, fade, slide);
            }
        }
        // Another forecast than the one shown, not the first one nor the same built again as the minute passes, that changes
        // what the cards show
        boolean updated = this.model != null && this.model.hasData && model.hasData && model.forecastHash != this.model.forecastHash
                && model.showsOtherWeatherThan(this.model);
        this.model = model;
        this.content.setVisibility(View.VISIBLE);
        this.header.bind(model);
        // By the sky behind the cards: a night's, a day's, or a day's bright grey overcast
        List<String> sky = model.backgroundLayers;
        int cardColorId;
        if (sky == null) {
            cardColorId = model.isDay ? R.color.details_sky_card_day : R.color.details_sky_card_night;
        } else if (!sky.contains(SkyWeather.DAY)) {
            cardColorId = R.color.details_sky_card_night;
        } else {
            boolean overcast = sky.contains(SkyWeather.OVERCAST);
            cardColorId = overcast ? R.color.details_sky_card_overcast : R.color.details_sky_card_day;
        }
        this.cardColor = ContextCompat.getColor(this.context, cardColorId);
        ColorStateList cardTint = ColorStateList.valueOf(this.cardColor);
        for (View card : new View[]{this.tvNoData, this.cardHourly, this.cardInsights, this.cardDaily}) {
            card.setBackgroundTintList(cardTint);
        }

        boolean hasData = model.hasData;
        setVisible(this.tvNoData, !hasData && model.location != null && !model.location.hours.isEmpty());
        boolean waiting = model.waitsForForecast;
        setVisible(this.piForecast, waiting);
        setVisible(this.llSource, !waiting);

        boolean hasHourly = hasData && model.hourly != null && model.hourly.columns.size() > 1;
        setVisible(this.cardHourly, hasHourly);
        if (hasHourly) {
            this.tvSummary.setText(model.summary);
            this.stripHourly.setData(model.hourly);
        }

        setVisible(this.cardInsights, hasData && !waiting && !model.insights.isEmpty());
        this.insightsAdapter.setInsights(model.insights);
        this.dotsInsights.setPages(model.insights.size(), this.pagerInsights.getCurrentItem(), false);
        setVisible(this.dotsInsights, model.insights.size() > 1);

        boolean hasDays = hasData && model.days != null && !model.days.isEmpty();
        setVisible(this.cardDaily, hasDays);
        if (hasDays) {
            this.dailyList.setDays(model.days);
            this.stripDaily.setData(model.dailyStrip);
        }

        this.tiles.bind(hasData && !waiting ? model.tiles : new ArrayList<>(), cardTint);
        this.astro.bind(model, waiting, cardTint);

        this.tvSource.setText(hasData ? model.source : "");
        this.tvUpdated.setText(model.updated);
        this.root.post(() -> this.header.collapse(this.scroll.getScrollY()));
        if (updated) {
            flashUpdate();
        }
        if (motion != null) {
            this.motion = motion;
            motion.play();
        }
    }

    private void finishMotion() {
        if (this.motion != null) {
            this.motion.finish();
            this.motion = null;
        }
    }

    // A band of light across the cards shown, once they are laid out with the new forecast, to see that it was updated
    private void flashUpdate() {
        ArrayList<View> cards = new ArrayList<>();
        for (View card : new View[]{this.tvNoData, this.cardHourly, this.cardInsights, this.cardDaily}) {
            cards.add(card);
        }
        cards.addAll(this.tiles.views());
        cards.add(this.root.findViewById(R.id.cardSun));
        cards.add(this.root.findViewById(R.id.cardMoon));
        this.scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                DetailsPage.this.scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                UpdateFlash.play(cards, DetailsPage.this.scroll, CARD_RADIUS_DP * DetailsPage.this.density);
                return true;
            }
        });
        // Drawn at once, even if nothing the page shows has changed
        this.scroll.invalidate();
    }

    // The first card shown from the scroll's top down
    private View cardAt(int y) {
        for (int i = 0; i < this.content.getChildCount(); i++) {
            View child = this.content.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.getBottom() > y) {
                return child;
            }
        }
        return null;
    }

    // Once the cards are laid out again, before they are drawn, the card is moved back to where it stood
    private void keepInPlace(View card) {
        if (card == null) {
            return;
        }
        int offset = card.getTop() - this.scroll.getScrollY();
        this.scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                DetailsPage.this.scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                int y = card.getTop() - offset;
                if (card.getVisibility() == View.VISIBLE && DetailsPage.this.pendingScroll < 0 && DetailsPage.this.scroll.getScrollY() != y) {
                    DetailsPage.this.keepingPlace = true;
                    DetailsPage.this.scroll.scrollTo(0, y);
                    DetailsPage.this.keepingPlace = false;
                }
                return true;
            }
        });
        // Now, not at some later frame with the offset gone stale, a reader's scroll
        this.scroll.invalidate();
    }

    /** The days in detail, as columns with the parameters, or briefly as a list */
    void setDailyExpanded(boolean expanded) {
        this.dailyExpanded = expanded;
        setVisible(this.stripDaily, expanded);
        setVisible(this.dailyList, !expanded);
        this.tvDailyMore.setText(expanded ? R.string.details_daily_less : R.string.details_daily_more);
        this.tvDailyMore.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more, 0);
    }

    int getScrollY() {
        return this.scroll.getScrollY();
    }

    /**
     * Scrolls the page as far down as the one shown, as near as it goes, now or once its cards are laid out: a swipe
     * to the next city keeps the place read
     */
    void scrollWith(int y) {
        if (this.model != null && this.scroll.isLaidOut() && !this.scroll.isLayoutRequested()) {
            this.pendingScroll = -1;
            if (this.scroll.getScrollY() != y) {
                this.scroll.scrollTo(0, y);
            }
        } else {
            this.pendingScroll = y;
        }
    }

    /** Scrolls the page so that the daily card is at the top, for a widget that opens the days */
    void scrollToDays() {
        this.scroll.post(() -> this.scroll.scrollTo(0, Math.max(0, this.cardDaily.getTop() - this.topInset - Math.round(UNDER_PILL_DP * this.density))));
    }

    void invalidateGlass() {
        this.pill.invalidate();
    }

    static void setVisible(View view, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) {
            view.setVisibility(visibility);
        }
    }

    void destroy() {
        finishMotion();
        this.header.destroy();
    }

    /** Takes the page for another city: back to the top, nothing shown until its model is bound */
    void reuse(int locationId) {
        finishMotion();
        this.header.reuse();
        this.locationId = locationId;
        this.model = null;
        this.pendingScroll = -1;
        this.scroll.scrollTo(0, 0);
        this.header.collapse(0);
        // Another city's hours and days start at now and its insights at the first, not where the page's city before
        // was left; how far down it is scrolled still follows the city shown
        this.stripHourly.scrollToStart();
        this.stripDaily.scrollToStart();
        this.pagerInsights.setCurrentItem(0, false);
        this.content.setVisibility(View.INVISIBLE);
    }

    /** The insights as pages of their own; updated in place, so a minute's refresh doesn't jump back to the first */
    private static final class InsightsAdapter extends PagerAdapter {
        private final LayoutInflater inflater;
        private final ArrayList<DetailsModel.Insight> insights = new ArrayList<>();
        private final ArrayList<View> views = new ArrayList<>();

        InsightsAdapter(LayoutInflater inflater) {
            this.inflater = inflater;
        }

        void setInsights(ArrayList<DetailsModel.Insight> insights) {
            boolean sameCount = insights.size() == this.insights.size();
            this.insights.clear();
            this.insights.addAll(insights);
            if (sameCount) {
                for (View view : this.views) {
                    bindView(view, (Integer) view.getTag());
                }
            } else {
                notifyDataSetChanged();
            }
        }

        private void bindView(View view, int position) {
            if (position >= this.insights.size()) {
                return;
            }
            DetailsModel.Insight insight = this.insights.get(position);
            TextView title = view.findViewById(R.id.tvInsightTitle);
            title.setText(insight.title);
            title.setCompoundDrawablesRelativeWithIntrinsicBounds(insight.icon, 0, 0, 0);
            ((TextView) view.findViewById(R.id.tvInsightText)).setText(insight.text);
            TextView value = view.findViewById(R.id.tvInsightValue);
            value.setText(insight.value);
            value.setVisibility(insight.value != null ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getCount() {
            return this.insights.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = this.inflater.inflate(R.layout.item_details_insight, container, false);
            view.setTag(position);
            bindView(view, position);
            container.addView(view);
            this.views.add(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
            this.views.remove(object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }
}
