package org.meteosphere.ui.locations;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;
import org.meteosphere.R;
import org.meteosphere.data.WeatherApiException;
import org.meteosphere.data.WeatherClient;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.BackgroundWork;
import org.meteosphere.ui.common.ListRoom;
import org.meteosphere.ui.glass.GlassMenu;
import org.meteosphere.util.AppUtils;

/**
 * The search for a place, over a screen whose search field types into it: a place's weather from the main screen's
 * search, a new favourite from its add button, or a place for a widget's or the notification's setting. Before two
 * characters are typed it shows the places found before, after the favourites when a place is picked for a setting.
 * Over the places an info line unfolds to tell why there are none.
 */
public final class PlaceSearchFragment extends Fragment {

    private static final String TAG = "PlaceSearchFragment";
    private static final String ARG_MODE = "mode";

    /** A place to see the weather of, from the main screen's search */
    public static final int MODE_SEARCH = 0;
    /** A new favourite, from the main screen's add button */
    public static final int MODE_ADD = 1;
    /** A place for a widget's or the notification's setting */
    public static final int MODE_PICK = 2;

    // The places found before kept at the most, the oldest past it dropped as one is added
    static final int FOUND_LIMIT = 20;
    // A shorter query isn't searched for; a longer one once the typing pauses this long
    private static final int SHORTEST_QUERY = 2;
    private static final long SEARCH_DELAY_MS = 200;
    private static final long INFO_FOLD_MS = 200;

    // What the list shows: the places found before, the search's answer, or no places
    private static final int SHOWN_HISTORY = 0;
    private static final int SHOWN_RESULTS = 1;
    private static final int SHOWN_NONE = 2;

    /** The screen the search is shown over, told of the place chosen */
    public interface OnPlacePickedListener {
        void onPlacePicked(Place location);
    }

    private int mode = MODE_SEARCH;
    private OnPlacePickedListener listener;

    // Several threads, so that a new search needn't wait for an old one's answer, which is dropped anyway
    private final ExecutorService searchExecutor = Executors.newCachedThreadPool(runnable -> new Thread(runnable, "PlaceSearch"));
    private final BackgroundWork searchWork = new BackgroundWork(this.searchExecutor);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // The search waiting for the typing to pause
    private Runnable pendingSearch;

    private List<Place> history = new ArrayList<>();
    private int shown = SHOWN_HISTORY;
    private List<Place> places = new ArrayList<>();

    // The info line's text, kept while it folds, and whether it is to be unfolded
    private int infoText = R.string.search_type_more;
    private boolean infoUnfolded;
    // The room kept at the list's end for the keyboard, also for a view made later
    private int bottomInset;

    private RecyclerView list;
    private PlacesAdapter adapter;
    private TextView infoView;
    private ValueAnimator infoAnimation;
    private Drawable airportIcon;

    public static PlaceSearchFragment newInstance() {
        return newInstance(MODE_SEARCH);
    }

    public static PlaceSearchFragment newInstance(int mode) {
        PlaceSearchFragment fragment = new PlaceSearchFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_MODE, mode);
        fragment.setArguments(arguments);
        return fragment;
    }

    // The fragment it is shown in, else the screen
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Object host = getParentFragment() != null ? getParentFragment() : getActivity();
        this.listener = host instanceof OnPlacePickedListener ? (OnPlacePickedListener) host : null;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Bundle arguments = getArguments();
        this.mode = arguments != null ? arguments.getInt(ARG_MODE, MODE_SEARCH) : MODE_SEARCH;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        return inflater.inflate(R.layout.fragment_place_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        this.list = view.findViewById(android.R.id.list);
        this.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        // The rows change at once, as the typing changes them
        this.list.setItemAnimator(null);
        int iconSize = getResources().getDimensionPixelSize(R.dimen.place_name_text_size);
        this.airportIcon = AppUtils.createPlaceKindIcon(requireContext(), iconSize, R.drawable.ic_place_airport);
        // Drawn black: as the names it stands with, also on the dark theme's surface
        this.airportIcon.setTint(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface));
        this.adapter = new PlacesAdapter(LayoutInflater.from(requireContext()));
        this.list.setAdapter(this.adapter);
        ListRoom.setBottom(this.list, this.bottomInset);
        // Made with the view, so that the places show with the screen at once
        this.history = makeHistory();
        if (this.shown == SHOWN_HISTORY) {
            showHistory();
        }
    }

    // A favourite added or a place found in a screen opened from here comes into the places found before
    @Override
    public void onResume() {
        super.onResume();
        this.history = makeHistory();
        if (this.shown == SHOWN_HISTORY) {
            showHistory();
        }
    }

    @Override
    public void onDestroyView() {
        cancelInfoAnimation();
        this.infoView = null;
        this.adapter = null;
        this.list = null;
        super.onDestroyView();
    }

    // The searches waiting and running go with the screen
    @Override
    public void onDestroy() {
        if (this.pendingSearch != null) {
            this.mainHandler.removeCallbacks(this.pendingSearch);
            this.pendingSearch = null;
        }
        this.searchWork.drop();
        this.searchExecutor.shutdownNow();
        super.onDestroy();
    }

    /** Keeps that much room at the list's end, for the keyboard lying over it; also for the view made later */
    public void setBottomInset(int inset) {
        this.bottomInset = inset;
        if (this.list != null) {
            ListRoom.setBottom(this.list, inset);
        }
    }

    /**
     * Takes the query typed: the places found before when there is none, no places for one character, the places
     * found for it from two once the typing pauses. Each call drops the search waiting or running, so that a late
     * answer for "Mos" doesn't replace the one for "Moscow"
     */
    public void search(String query) {
        if (this.pendingSearch != null) {
            this.mainHandler.removeCallbacks(this.pendingSearch);
            this.pendingSearch = null;
        }
        this.searchWork.drop();
        int length = query == null ? 0 : query.length();
        if (length >= SHORTEST_QUERY) {
            // The answer before was for another query; the places found before stay until the new one comes
            if (this.shown == SHOWN_RESULTS) {
                show(SHOWN_NONE, List.of());
                setInfo(0);
            }
            Runnable search = () -> find(query);
            this.pendingSearch = search;
            this.mainHandler.postDelayed(search, SEARCH_DELAY_MS);
        } else if (length == 1) {
            show(SHOWN_NONE, List.of());
            setInfo(infoText(List.of(), false, false));
        } else {
            showHistory();
        }
    }

    // --- The search ---

    private void find(String query) {
        this.pendingSearch = null;
        if (this.searchExecutor.isShutdown() || !isAdded()) {
            return;
        }
        String language = AppSettings.getInstance().language;
        int timeoutMs = getResources().getInteger(R.integer.request_timeout_ms);
        this.searchWork.run(() -> placesFound(query, language, timeoutMs), this::showAnswer);
    }

    // Background thread: the places found for the query, null when the search failed, as without a connection
    private static ArrayList<Place> placesFound(String query, String language, int timeoutMs) {
        try {
            ArrayList<Place> found = WeatherClient.findLocations(query, language, timeoutMs);
            return found != null ? found : new ArrayList<>();
        } catch (WeatherApiException e) {
            Log.w(TAG, "The place search failed", e);
            return null;
        }
    }

    // Dropped once the view is gone
    private void showAnswer(ArrayList<Place> answer) {
        if (this.list == null) {
            return;
        }
        List<Place> found = answer != null ? answer : List.of();
        show(SHOWN_RESULTS, found);
        setInfo(infoText(found, true, answer == null));
    }

    // --- The places shown ---

    private List<Place> makeHistory() {
        AppSettings settings = AppSettings.getInstance();
        return history(this.mode, settings.getFoundPlaces(), settings.getFavorites(), settings::getPlace);
    }

    private void showHistory() {
        show(SHOWN_HISTORY, this.history);
        setInfo(infoText(this.history, false, false));
    }

    // Another kind of places than the one shown shows from the top
    private void show(int kind, List<Place> places) {
        boolean other = kind != this.shown;
        this.shown = kind;
        this.places = new ArrayList<>(places);
        if (this.adapter != null) {
            this.adapter.notifyDataSetChanged();
        }
        if (other && this.list != null) {
            this.list.scrollToPosition(0);
        }
    }

    /**
     * The places found before as the mode shows them: all of them in their order to see a place's weather; those
     * that aren't favourites for a new favourite; to pick a place, the favourites stored first, then the others
     */
    static List<Place> history(int mode, List<Place> found, List<Integer> favoriteIds, IntFunction<Place> stored) {
        ArrayList<Place> res = new ArrayList<>();
        if (mode == MODE_PICK) {
            for (int id : favoriteIds) {
                Place favorite = stored.apply(id);
                if (favorite != null) {
                    res.add(favorite);
                }
            }
        }
        for (Place place : found) {
            if (mode == MODE_SEARCH || !favoriteIds.contains(place.id)) {
                res.add(place);
            }
        }
        return res;
    }

    /** The places found before once one is chosen: it first, its older entry gone, the oldest past the limit dropped */
    static ArrayList<Place> foundAfterChoice(List<Place> found, Place chosen) {
        ArrayList<Place> res = new ArrayList<>();
        res.add(chosen);
        for (Place place : found) {
            if (res.size() >= FOUND_LIMIT) {
                break;
            }
            if (place.id != chosen.id) {
                res.add(place);
            }
        }
        return res;
    }

    /**
     * The info line's text, 0 when places show and it folds: the search failed, it found none, or none was made, as
     * for one character or before anything was ever found
     */
    static int infoText(List<Place> places, boolean searched, boolean failed) {
        if (!places.isEmpty()) {
            return 0;
        }
        if (!searched) {
            return R.string.search_type_more;
        }
        return failed ? R.string.search_failed : R.string.search_nothing_found;
    }

    // --- The info line ---

    // Unfolds with the text, or folds for 0, keeping the text it had while it folds
    private void setInfo(int text) {
        this.infoUnfolded = text != 0;
        if (text != 0) {
            this.infoText = text;
            if (this.infoView != null) {
                this.infoView.setText(text);
            }
        }
        fitInfo(true);
    }

    /**
     * The info line to its text's height at its width when unfolded, to none otherwise, once its width is known. A
     * change under way is cancelled first; none starts when the line is at that height already
     */
    private void fitInfo(boolean animated) {
        TextView info = this.infoView;
        if (info == null || info.getWidth() == 0) {
            return;
        }
        int height = 0;
        if (this.infoUnfolded) {
            info.measure(View.MeasureSpec.makeMeasureSpec(info.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            height = info.getMeasuredHeight();
        }
        cancelInfoAnimation();
        ViewGroup.LayoutParams params = info.getLayoutParams();
        if (params.height == height) {
            return;
        }
        if (!animated) {
            params.height = height;
            info.requestLayout();
            return;
        }
        ValueAnimator animation = ValueAnimator.ofInt(params.height, height);
        animation.setDuration(INFO_FOLD_MS);
        animation.addUpdateListener(animator -> {
            params.height = (int) animator.getAnimatedValue();
            info.requestLayout();
        });
        this.infoAnimation = animation;
        animation.start();
    }

    private void cancelInfoAnimation() {
        if (this.infoAnimation != null) {
            this.infoAnimation.cancel();
            this.infoAnimation = null;
        }
    }

    // --- The places' actions ---

    // A favourite picked for a setting stays out of the places found before, as it is among the favourites there
    private void onPlaceClick(Place place) {
        AppSettings settings = AppSettings.getInstance();
        if (this.mode != MODE_PICK || !settings.getFavorites().contains(place.id)) {
            settings.saveFoundPlaces(foundAfterChoice(settings.getFoundPlaces(), place));
        }
        if (this.listener != null) {
            this.listener.onPlacePicked(place);
        }
    }

    // On the frosted glass at the row: the places found before can be deleted, a place found added to the favourites;
    // nothing when picking a place for a setting
    private boolean onPlaceLongClick(View row, Place place) {
        if (this.mode == MODE_PICK || this.shown == SHOWN_NONE) {
            return false;
        }
        List<GlassMenu.Item> items = new ArrayList<>();
        if (this.shown == SHOWN_HISTORY) {
            items.add(new GlassMenu.Item(R.drawable.ic_menu_delete, getString(R.string.delete), null, false, false));
            items.add(new GlassMenu.Item(R.drawable.ic_menu_delete_all, getString(R.string.delete_all), null, false, false));
            GlassMenu.showAtRow(row, items, index -> deleteFound(place, index == 1), null);
        } else {
            // As the nearby tab's menu: a favourite already isn't added again
            boolean favorite = AppSettings.getInstance().getFavorites().contains(place.id);
            items.add(new GlassMenu.Item(R.drawable.ic_menu_favorite_add, getString(R.string.favorites_add), null, false, false,
                    !favorite));
            GlassMenu.showAtRow(row, items, index -> {
                if (isAdded()) {
                    Favorites.add(requireActivity(), place);
                }
            }, null);
        }
        return true;
    }

    // The place, or all of them, out of the places found before, which show again
    private void deleteFound(Place place, boolean all) {
        AppSettings settings = AppSettings.getInstance();
        ArrayList<Place> found = new ArrayList<>();
        if (!all) {
            found = settings.getFoundPlaces();
            found.removeIf(old -> old.id == place.id);
        }
        settings.saveFoundPlaces(found);
        this.history = makeHistory();
        if (this.shown == SHOWN_HISTORY) {
            showHistory();
        }
    }

    // --- The list ---

    private final class PlaceHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView region;
        Place place;

        PlaceHolder(View view) {
            super(view);
            this.name = view.findViewById(R.id.tvSearchPlaceName);
            this.region = view.findViewById(R.id.tvSearchPlaceRegion);
            view.setOnClickListener(v -> {
                if (this.place != null) {
                    onPlaceClick(this.place);
                }
            });
            view.setOnLongClickListener(v -> this.place != null && onPlaceLongClick(v, this.place));
        }

        void bind(Place place) {
            this.place = place;
            this.name.setText(place.name);
            this.name.setCompoundDrawablesRelative(place.airport ? PlaceSearchFragment.this.airportIcon : null, null, null, null);
            this.region.setText(place.region);
            this.region.setVisibility(TextUtils.isEmpty(place.region) ? View.GONE : View.VISIBLE);
        }
    }

    /** The info line first, then the places shown */
    private final class PlacesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_INFO = 0;
        private static final int VIEW_PLACE = 1;
        private final LayoutInflater inflater;

        PlacesAdapter(LayoutInflater inflater) {
            this.inflater = inflater;
        }

        @Override
        public int getItemCount() {
            return 1 + PlaceSearchFragment.this.places.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? VIEW_INFO : VIEW_PLACE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_PLACE) {
                return new PlaceHolder(this.inflater.inflate(R.layout.item_place_search_place, parent, false));
            }
            TextView info = (TextView) this.inflater.inflate(R.layout.item_place_search_info, parent, false);
            // Folded at first; once laid out, its width tells the height its text takes
            info.getLayoutParams().height = 0;
            info.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft && v == PlaceSearchFragment.this.infoView) {
                    PlaceSearchFragment.this.mainHandler.post(() -> fitInfo(true));
                }
            });
            return new RecyclerView.ViewHolder(info) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof PlaceHolder) {
                ((PlaceHolder) holder).bind(PlaceSearchFragment.this.places.get(position - 1));
                return;
            }
            TextView info = (TextView) holder.itemView;
            info.setText(PlaceSearchFragment.this.infoText);
            // Another view for the line, as when it scrolled away: at its height at once
            if (info != PlaceSearchFragment.this.infoView) {
                cancelInfoAnimation();
                PlaceSearchFragment.this.infoView = info;
                fitInfo(false);
            }
        }
    }
}
