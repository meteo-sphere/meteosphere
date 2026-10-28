package org.meteosphere.ui.locations;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import org.meteosphere.ui.sky.SkyView;
import org.meteosphere.ui.sky.SkyWeather;
import org.meteosphere.ui.sky.SkyBackgrounds;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
import android.text.Layout;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.glass.GlassMenu;
import org.meteosphere.ui.icons.ArrowText;
import org.meteosphere.ui.icons.ShadowedDrawable;
import org.meteosphere.util.AppUtils;
import org.meteosphere.ui.common.BackgroundWork;

/**
 * The favourite cities as cards of their own sky. The cards are made on a background thread, from copies of the
 * forecasts, and their skies are composed off the main thread too: a row only binds what is ready. A long press on a
 * favourite lifts its card to be dragged to another place among them, and shows its menu until the card moves.
 */
public final class FavoritesFragment extends Fragment {

    private static final ExecutorService CARD_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> new Thread(() -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        runnable.run();
    }, "FavoriteCards"));

    // The cards as last shown, in their order, and the settings their texts were made with: the main screen makes
    // this fragment again each time it comes back, and it shows them at once rather than an empty page
    private static List<Row> lastRows;
    private static String lastRowsSettings;
    // Where the list was scrolled to, kept for it the same way: coming back, it is where it was
    private static Parcelable listState;

    private OnFavoriteTappedListener actionListener;
    private RecyclerView list;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BackgroundWork cardsWork = new BackgroundWork(CARD_EXECUTOR);
    private boolean isLoaded;
    private View emptyView;
    FavoritesAdapter adapter;
    private ItemTouchHelper dragHelper;
    // A finger on the list or a card being dragged, and cards made meanwhile, made again once it is lifted: bound
    // under the finger they would cancel its long press, and while a card is dragged they have the order before
    private boolean isTouching;
    private boolean isDragging;
    private boolean hasPendingRows;
    // A card being deleted: it goes and the ones under it move up on screen, the cards made again once they have
    private boolean isDeleting;
    private Runnable afterAnimations;
    // The menu of the card lifted by a long press, until the card moves
    private GlassMenu cardMenu;

    private Drawable airportIcon = null;
    private Drawable currentIcon = null;
    // The place's kind icons with the shadow of the name they stand with, made once each
    private final java.util.IdentityHashMap<Drawable, Drawable> shadowedIcons = new java.util.IdentityHashMap<>();
    // Whether the nights' cards are veiled, on the light theme
    private boolean nightVeil;

    public interface OnFavoriteTappedListener {
        void onFavoriteTapped(Place location);
    }

    /** A row of the list: a header, or a city with its card */
    static final class Row {
        static final int TYPE_CITY = 0;
        static final int TYPE_CURRENT_HEADER = 1;
        static final int TYPE_FAVORITES_HEADER = 2;
        final int type;
        final Place location;
        CityCard card;

        Row(int type, Place location) {
            this.type = type;
            this.location = location;
        }
    }

    private final class CardHolder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView sky;
        final View veil;
        final View shade;
        final TextView name;
        final TextView time;
        final TextView wind;
        final TextView condition;
        final TextView temperature;
        final TextView highLow;
        // The row bound, whatever the list's positions are while it makes its rows again
        Row row;

        CardHolder(View view) {
            super(view);
            this.card = view.findViewById(R.id.cityCard);
            this.sky = view.findViewById(R.id.ivCitySky);
            this.veil = view.findViewById(R.id.vCityVeil);
            this.shade = view.findViewById(R.id.vCityShade);
            this.name = view.findViewById(R.id.tvCityName);
            this.time = view.findViewById(R.id.tvCityTime);
            this.wind = view.findViewById(R.id.tvCityWind);
            this.condition = view.findViewById(R.id.tvCityCondition);
            this.temperature = view.findViewById(R.id.tvCityTemperature);
            this.highLow = view.findViewById(R.id.tvCityHighLow);
            // The sky is clipped to the card's rounded corners
            this.card.setClipToOutline(true);
            // Nor the lifted card to the row or its padding, with its shadow
            ((ViewGroup) view).setClipChildren(false);
            ((ViewGroup) view).setClipToPadding(false);
            // The wind's icon shadowed as the texts on the sky
            ShadowedDrawable.applyTo(this.wind);
            this.card.setOnClickListener(v -> onCardClick(this));
            this.card.setOnLongClickListener(v -> onCardLongClick(this));
        }
    }

    public class FavoritesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_CARD = 0;
        private static final int VIEW_HEADER = 1;
        private static final int VIEW_SPACER = 2;
        private static final long ID_CURRENT_HEADER = Long.MIN_VALUE;
        private static final long ID_FAVORITES_HEADER = Long.MIN_VALUE + 1;
        private static final long ID_SPACER = Long.MIN_VALUE + 2;
        private final List<Row> rows = new ArrayList<>();
        private final LayoutInflater inflater;
        final CardSkies skies;
        // The widest high and low and wind's pill among the cards, -1 until measured: every card gives them that much,
        // so that the pills stand in one column from card to card
        private int highLowWidth = -1;
        private int windWidth = -1;

        FavoritesAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
            this.skies = new CardSkies(context, R.dimen.city_card_height);
            // The cards by their places: made again, each keeps its views and its sky
            setHasStableIds(true);
        }

        // The same places in the same order are bound again where they are, without the list's animations; others
        // make the list again
        void setRows(List<Row> rows) {
            boolean samePlaces = rows.size() == this.rows.size();
            for (int i = 0; samePlaces && i < rows.size(); i++) {
                samePlaces = idOf(rows.get(i)) == idOf(this.rows.get(i));
            }
            this.rows.clear();
            this.rows.addAll(rows);
            this.highLowWidth = -1;
            this.windWidth = -1;
            if (samePlaces) {
                notifyItemRangeChanged(0, this.rows.size());
            } else {
                notifyDataSetChanged();
            }
            lastRows = new ArrayList<>(this.rows);
        }

        Row getItem(int position) {
            return position >= 0 && position < this.rows.size() ? this.rows.get(position) : null;
        }

        // A row to another's place, the rows between shifting by one
        void move(int from, int to) {
            this.rows.add(to, this.rows.remove(from));
            notifyItemMoved(from, to);
            lastRows = new ArrayList<>(this.rows);
        }

        void remove(Row row) {
            int position = this.rows.indexOf(row);
            if (position >= 0) {
                this.rows.remove(position);
                notifyItemRemoved(position);
                lastRows = new ArrayList<>(this.rows);
            }
        }

        @Override
        public int getItemCount() {
            // A spacer at the end, so that the last card can scroll clear of the add button
            return this.rows.isEmpty() ? 0 : this.rows.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            Row item = getItem(position);
            return item == null ? VIEW_SPACER : item.type == Row.TYPE_CITY ? VIEW_CARD : VIEW_HEADER;
        }

        @Override
        public long getItemId(int position) {
            Row item = getItem(position);
            return item == null ? ID_SPACER : idOf(item);
        }

        private long idOf(Row item) {
            return item.type == Row.TYPE_CITY ? item.location.id
                    : item.type == Row.TYPE_CURRENT_HEADER ? ID_CURRENT_HEADER : ID_FAVORITES_HEADER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_SPACER) {
                View spacer = new View(parent.getContext());
                spacer.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (1.2d * getResources().getDimensionPixelOffset(R.dimen.favorites_spacer))));
                return new RecyclerView.ViewHolder(spacer) {
                };
            }
            if (viewType == VIEW_HEADER) {
                return new RecyclerView.ViewHolder(this.inflater.inflate(R.layout.item_favorites_header, parent, false)) {
                };
            }
            return new CardHolder(this.inflater.inflate(R.layout.item_city_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row item = getItem(position);
            if (item == null) {
                return;
            }
            if (holder instanceof CardHolder) {
                ((CardHolder) holder).row = item;
                bindCard((CardHolder) holder, item.card, FavoritesFragment.this.list);
                return;
            }
            TextView header = (TextView) holder.itemView;
            ViewGroup.LayoutParams params = header.getLayoutParams();
            int height = getResources().getDimensionPixelOffset(R.dimen.favorites_header_height);
            if (item.type == Row.TYPE_CURRENT_HEADER) {
                params.height = height / 2;
                header.setText("");
            } else {
                params.height = height;
                header.setText(R.string.favorites_header);
            }
            header.setLayoutParams(params);
        }

        private void bindCard(CardHolder holder, CityCard card, ViewGroup parent) {
            if (card == null) {
                return;
            }
            holder.name.setText(card.name);
            // A night's black sky softened to a blue on the light theme's page
            holder.veil.setVisibility(FavoritesFragment.this.nightVeil && card.skyLayers.contains(SkyWeather.NIGHT) ? View.VISIBLE : View.GONE);
            Drawable kindIcon = card.isCurrentLocation ? FavoritesFragment.this.currentIcon
                    : card.airport ? FavoritesFragment.this.airportIcon : null;
            kindIcon = shadowed(kindIcon, holder.name);
            if (card.isCurrentLocation) {
                holder.name.setCompoundDrawablesRelative(null, null, kindIcon, null);
            } else {
                holder.name.setCompoundDrawablesRelative(kindIcon, null, null, null);
            }
            holder.time.setText(card.time);
            holder.wind.setVisibility(card.wind != null ? View.VISIBLE : View.GONE);
            if (card.wind != null) {
                holder.wind.setText(card.wind);
                if (card.strongWind) {
                    holder.wind.setBackgroundResource(R.drawable.bg_city_wind_strong);
                } else {
                    holder.wind.setBackground(null);
                }
            }
            holder.condition.setText(card.condition);
            holder.temperature.setText(card.temperature);
            holder.highLow.setText(ArrowText.of(requireContext(), card.highLow));
            measureColumns(holder);
            setWidth(holder.highLow, this.highLowWidth);
            setWidth(holder.wind, this.windWidth);
            // Mostly composed before the cards come; a card's sky fades to another
            this.skies.bind(holder.sky, holder.shade, card.skyLayers, card.skyColor, parent, true);
        }

        // The widest texts of all the cards, in the views' own paints
        private void measureColumns(CardHolder holder) {
            if (this.highLowWidth >= 0) {
                return;
            }
            float highLow = 0;
            float wind = 0;
            for (Row row : this.rows) {
                if (row.card == null) {
                    continue;
                }
                if (row.card.highLow != null) {
                    highLow = Math.max(highLow, Layout.getDesiredWidth(ArrowText.of(requireContext(), row.card.highLow), holder.highLow.getPaint()));
                }
                if (row.card.wind != null) {
                    wind = Math.max(wind, holder.wind.getPaint().measureText(row.card.wind));
                }
            }
            this.highLowWidth = (int) Math.ceil(highLow) + 1;
            // The icon counted from the drawable itself: a new view's start drawable isn't placed until it is laid out
            Drawable icon = holder.wind.getCompoundDrawablesRelative()[0];
            int iconWidth = icon != null ? icon.getIntrinsicWidth() + holder.wind.getCompoundDrawablePadding() : 0;
            this.windWidth = wind > 0 ? (int) Math.ceil(wind) + holder.wind.getPaddingStart() + holder.wind.getPaddingEnd() + iconWidth : 0;
        }

        private void setWidth(View view, int width) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            int wanted = width > 0 ? width : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (params.width != wanted) {
                params.width = wanted;
                view.setLayoutParams(params);
            }
        }
    }

    public static FavoritesFragment newInstance() {
        return new FavoritesFragment();
    }

    /** Makes the cards again on a background thread: the forecasts, the time or the settings changed */
    public final void refreshCards() {
        if (this.adapter == null || !isAdded()) {
            return;
        }
        Context context = requireContext().getApplicationContext();
        String settings = cardSettings();
        CardSkies skies = this.adapter.skies;
        Point skySize = skies.size(this.list);
        this.cardsWork.runLatest(() -> {
            List<Row> items = loadItems(context);
            composeSkies(skies, items, skySize);
            return items;
        }, items -> {
            if (this.adapter == null || !isAdded()) {
                return;
            }
            // The activity, whose window's size the details' skies take
            prepareDetailsSkies(requireActivity(), items);
            if (this.isTouching || this.isDragging) {
                this.hasPendingRows = true;
                return;
            }
            // Nor in the middle of a deleted card's animations
            RecyclerView.ItemAnimator animator = this.list != null ? this.list.getItemAnimator() : null;
            if (animator != null && animator.isRunning()) {
                this.hasPendingRows = true;
                animator.isRunning(() -> this.mainHandler.post(this::showPendingRows));
                return;
            }
            lastRowsSettings = settings;
            this.adapter.setRows(items);
            restoreListState();
            this.isLoaded = true;
            showEmpty();
        });
    }

    // What the cards' texts are made with
    private static String cardSettings() {
        AppSettings settings = AppSettings.getInstance();
        return settings.language + "|" + settings.temperatureUnit + "|" + settings.windSpeedUnit + "|" + settings.use24Hours
                + "|" + settings.onlyStrongWind;
    }

    // The details' skies of the cities drawn ahead, when no sky is waited for, so that opening a city shows its sky at
    // once rather than holding up the screen while it is drawn; the animated weather draws the details live instead
    private static void prepareDetailsSkies(Context context, List<Row> items) {
        if (SkyView.liveEnabled(context)) {
            return;
        }
        SkyBackgrounds backgrounds = SkyBackgrounds.getInstance(context);
        Point size = SkyBackgrounds.composedSize(context);
        for (Row row : items) {
            if (row.card != null) {
                backgrounds.prepare(row.card.skyLayers, size, SkyBackgrounds.CROP_CENTER);
            }
        }
    }

    // Background thread: the cards' skies composed as soon as the cards are known, not waited for: the cards show at
    // once, those whose skies are not drawn yet over their colours, the skies fading in as they come
    private static void composeSkies(CardSkies skies, List<Row> items, Point size) {
        for (Row row : items) {
            if (row.card != null && skies.composed(row.card.skyLayers, size) == null) {
                skies.compose(row.card.skyLayers, size, null);
            }
        }
    }

    // Background thread: the headers and a card per city, the current location first
    private static List<Row> loadItems(Context context) {
        ArrayList<Row> items = new ArrayList<>();
        CityCard.Builder builder = new CityCard.Builder(context);
        AppSettings settings = AppSettings.getInstance();
        Place current = settings.currentShown ? settings.getCurrentPlace() : null;
        if (current != null) {
            items.add(new Row(Row.TYPE_CURRENT_HEADER, null));
            Place currentItem = new Place();
            currentItem.id = -1;
            Row item = new Row(Row.TYPE_CITY, currentItem);
            item.card = builder.build(current, true);
            items.add(item);
        }
        ArrayList<Integer> ids = settings.getFavorites();
        if (!ids.isEmpty()) {
            items.add(new Row(Row.TYPE_FAVORITES_HEADER, null));
            for (int id : ids) {
                Place location = settings.getPlace(id);
                if (location != null) {
                    Row item = new Row(Row.TYPE_CITY, location);
                    item.card = builder.build(location, false);
                    items.add(item);
                }
            }
        }
        return items;
    }

    // The empty text only once the favourites are known, not while they load
    private void showEmpty() {
        if (this.emptyView != null) {
            this.emptyView.setVisibility(this.isLoaded && this.adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(R.layout.fragment_favorites, viewGroup, false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.actionListener = (OnFavoriteTappedListener) getParentFragment();
        } catch (ClassCastException e) {
        }
    }

    private Drawable shadowed(Drawable icon, TextView text) {
        if (icon == null) {
            return null;
        }
        Drawable shadowed = this.shadowedIcons.get(icon);
        if (shadowed == null) {
            shadowed = ShadowedDrawable.like(icon, text);
            this.shadowedIcons.put(icon, shadowed);
        }
        return shadowed;
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        int iconSize = getResources().getDimensionPixelSize(R.dimen.place_name_text_size);
        this.airportIcon = whiteIcon(AppUtils.createPlaceKindIcon(requireContext(), iconSize, R.drawable.ic_place_airport));
        this.currentIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_details_navigation).mutate();
        int arrowSize = Math.round(16 * getResources().getDisplayMetrics().density);
        this.currentIcon.setBounds(0, 0, arrowSize, arrowSize);
        this.nightVeil = Color.alpha(ContextCompat.getColor(requireContext(), R.color.city_card_night_veil)) != 0;
        this.adapter = new FavoritesAdapter(requireActivity());
        // The cards last shown, or made at once when the settings changed their texts since: never an empty page or
        // old texts when coming back. Only the first time they come when made, as the app starts
        if (lastRows != null) {
            String settings = cardSettings();
            if (!settings.equals(lastRowsSettings)) {
                lastRowsSettings = settings;
                lastRows = loadItems(requireContext().getApplicationContext());
            }
            this.adapter.setRows(lastRows);
            this.isLoaded = true;
        }
        refreshCards();
    }

    // The place kinds' icons are drawn black; on the cards' skies they are white
    private static Drawable whiteIcon(Drawable icon) {
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(0xffffffff);
        }
        return icon;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        this.list = view.findViewById(R.id.rvFavorites);
        this.emptyView = view.findViewById(R.id.tvFavoritesEmpty);
        this.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        this.list.setHasFixedSize(true);
        // A lifted card grows past its row: the rows aren't cut to their bounds
        this.list.setClipChildren(false);
        this.list.setAdapter(this.adapter);
        // Only a dragged card's neighbours, making way for it, and a deleted card with the ones under it move on
        // screen: any other change of the cards shows at once, and a card bound again keeps its views, its sky fading
        // by itself
        DefaultItemAnimator animator = new DefaultItemAnimator() {
            @Override
            public void runPendingAnimations() {
                super.runPendingAnimations();
                // The deletion's animations are on their way: what follows them waits until they are over
                FavoritesFragment.this.isDeleting = false;
                Runnable after = FavoritesFragment.this.afterAnimations;
                if (after != null) {
                    FavoritesFragment.this.afterAnimations = null;
                    isRunning(() -> FavoritesFragment.this.mainHandler.post(after));
                }
            }

            @Override
            public boolean animateAdd(RecyclerView.ViewHolder holder) {
                dispatchAddFinished(holder);
                return false;
            }

            @Override
            public boolean animateRemove(RecyclerView.ViewHolder holder) {
                if (FavoritesFragment.this.isDragging || FavoritesFragment.this.isDeleting) {
                    return super.animateRemove(holder);
                }
                dispatchRemoveFinished(holder);
                return false;
            }

            @Override
            public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
                if (FavoritesFragment.this.isDragging || FavoritesFragment.this.isDeleting) {
                    return super.animateMove(holder, fromX, fromY, toX, toY);
                }
                dispatchMoveFinished(holder);
                return false;
            }
        };
        animator.setSupportsChangeAnimations(false);
        this.list.setItemAnimator(animator);
        // Before the drag's: it sees the touches until the drag takes them, and the drop ends it then
        this.list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    FavoritesFragment.this.isTouching = true;
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    FavoritesFragment.this.isTouching = false;
                    showPendingRows();
                }
                return false;
            }
        });
        this.dragHelper = new ItemTouchHelper(new DragCallback(requireContext()));
        this.dragHelper.attachToRecyclerView(this.list);
        restoreListState();
        showEmpty();
    }

    // Once the list has its cards, before it is laid out with them: a list laid out empty loses it
    private void restoreListState() {
        if (listState != null && this.list != null && this.adapter.getItemCount() > 0) {
            this.list.getLayoutManager().onRestoreInstanceState(listState);
            listState = null;
        }
    }

    @Override
    public void onDestroyView() {
        listState = this.list.getLayoutManager().onSaveInstanceState();
        super.onDestroyView();
    }

    private void showPendingRows() {
        if (this.hasPendingRows && !this.isTouching && !this.isDragging) {
            this.hasPendingRows = false;
            refreshCards();
        }
    }

    private static Row rowOf(RecyclerView.ViewHolder holder) {
        return holder instanceof CardHolder ? ((CardHolder) holder).row : null;
    }

    private static boolean isFavorite(Row row) {
        return row != null && row.type == Row.TYPE_CITY && row.location != null && row.location.id != -1;
    }

    private void onCardClick(CardHolder holder) {
        Row row = holder.row;
        if (this.actionListener != null && row != null && row.location != null) {
            this.actionListener.onFavoriteTapped(row.location);
        }
    }

    // A favourite's card lifts to be dragged, its menu on the frosted glass at the card's end until it moves, as the
    // other menus of the app; the current location stays where it is
    private boolean onCardLongClick(CardHolder holder) {
        Row row = holder.row;
        if (!isFavorite(row)) {
            return false;
        }
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_menu_edit, getString(R.string.rename_place), null, false, false));
        items.add(new GlassMenu.Item(R.drawable.ic_menu_delete, getString(R.string.delete), null, false, false));
        this.cardMenu = GlassMenu.showAtRow(holder.card, items, index -> {
            if (index == 0) {
                Intent intent = new Intent(requireContext().getApplicationContext(), RenameActivity.class);
                intent.putExtra("place_id", row.location.id);
                startActivityForResult(intent, 0);
            } else {
                AppSettings.getInstance().removeFavorite(row.location.id);
                this.isDeleting = true;
                this.afterAnimations = this::refreshCards;
                this.adapter.remove(row);
            }
        }, () -> this.cardMenu = null);
        this.dragHelper.startDrag(holder);
        return true;
    }

    // The dragged card a little bigger, over the others with its shadow
    private void lift(CardHolder holder, boolean up) {
        float density = getResources().getDisplayMetrics().density;
        holder.card.animate().scaleX(up ? 1.03f : 1f).scaleY(up ? 1.03f : 1f).translationZ(up ? 8 * density : 0).setDuration(150);
    }

    // The favourites in the order of their cards
    private void saveOrder() {
        ArrayList<Integer> ids = new ArrayList<>();
        for (Row row : this.adapter.rows) {
            if (isFavorite(row)) {
                ids.add(row.location.id);
            }
        }
        AppSettings.getInstance().setFavoriteLocations(ids);
    }

    /** Drags a favourite's card up or down among the favourites, their order saved once it is dropped elsewhere */
    private final class DragCallback extends ItemTouchHelper.Callback {
        private final int touchSlop;
        private boolean moved;

        DragCallback(Context context) {
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return isFavorite(rowOf(viewHolder)) ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
        }

        // The card's own long press starts the drag, with its menu
        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
            return isFavorite(rowOf(target));
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            adapter.move(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
            this.moved = true;
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder instanceof CardHolder) {
                isDragging = true;
                lift((CardHolder) viewHolder, true);
            }
        }

        // The card follows the finger among the favourites only: not over the current location or past the last one
        @Override
        public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            View item = viewHolder.itemView;
            float drawnY = dY;
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                int position = recyclerView.getChildAdapterPosition(child);
                Row row = adapter.getItem(position);
                if (row != null && row.type == Row.TYPE_FAVORITES_HEADER) {
                    drawnY = Math.max(drawnY, child.getBottom() - item.getTop());
                } else if (row == null && position != RecyclerView.NO_POSITION) {
                    drawnY = Math.min(drawnY, child.getTop() - item.getBottom());
                }
            }
            super.onChildDraw(canvas, recyclerView, viewHolder, dX, drawnY, actionState, isCurrentlyActive);
            // Moved by the finger, the card is dragged and its menu goes
            if (isCurrentlyActive && Math.abs(dY) > this.touchSlop && cardMenu != null) {
                cardMenu.dismiss();
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (viewHolder instanceof CardHolder) {
                lift((CardHolder) viewHolder, false);
            }
            isTouching = false;
            isDragging = false;
            if (this.moved) {
                this.moved = false;
                saveOrder();
            }
            showPendingRows();
        }
    }
}
