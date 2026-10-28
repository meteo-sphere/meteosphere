package org.meteosphere.ui.locations;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.R;
import org.meteosphere.background.WeatherUpdater;
import org.meteosphere.data.model.Place;
import org.meteosphere.location.PositionService;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.BackgroundWork;
import org.meteosphere.ui.glass.GlassMenu;
import org.meteosphere.ui.icons.ShadowedDrawable;
import org.meteosphere.util.AppUtils;

/**
 * The places around the device: the nearest town on a card of its own sky, then the other places as tiles two by
 * two under their sections' titles, each telling which way and how far it is. The rows are made on a background
 * thread from the list the last search for the position saved; without the location permission the tab asks for it.
 */
public final class NearbyFragment extends Fragment {

    private static final ExecutorService ROWS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> new Thread(() -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        runnable.run();
    }, "NearbyPlaces"));

    private static final String TAG = "NearbyFragment";
    // What WeatherUpdater tells of the search a new list came from
    private static final String EXTRA_FOUND_BY = "found_by";
    private static final String TYPE_GPS = "gps";
    private static final int COLUMNS = 2;
    // The strong wind's pill on the town's sky, padded at its sides
    private static final int WIND_PILL_PADDING_DP = 7;
    private static final int PIN_SIZE_DP = 16;

    // The rows as last shown and the settings their texts were made with: the main screen makes this fragment again
    // each time it comes back, and it shows them at once rather than an empty tab
    private static List<NearbyPlace> lastRows;
    private static String lastRowsSettings;
    // Where the list was scrolled to as the view went, put back once the list has rows again
    private static Parcelable listState;

    private OnNearbyListener actionListener;
    private final BackgroundWork rowsWork = new BackgroundWork(ROWS_EXECUTOR);
    // The tab asks for the permission itself rather than through the main screen
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> onPermissionAnswered());

    private RecyclerView list;
    private NearbyAdapter adapter;
    private View emptyView;
    private View progressView;
    private View permissionView;

    // A new list of places was saved, after a search by the GPS or the network
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            load(TYPE_GPS.equals(intent.getStringExtra(EXTRA_FOUND_BY)), true);
        }
    };

    /** The fragment the tab is in, told of the place tapped and of the one to add to the favourites */
    public interface OnNearbyListener {
        void onNearbyTapped(Place location);

        void onAddFavorite(Place location);
    }

    public static NearbyFragment newInstance() {
        return new NearbyFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        this.actionListener = parent instanceof OnNearbyListener ? (OnNearbyListener) parent : null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        return inflater.inflate(R.layout.fragment_nearby, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        this.list = view.findViewById(R.id.rvNearby);
        this.emptyView = view.findViewById(R.id.tvNearbyEmpty);
        this.progressView = view.findViewById(R.id.piNearby);
        this.permissionView = view.findViewById(R.id.llNearbyPermission);
        NearbyAdapter adapter = new NearbyAdapter(requireContext());
        this.adapter = adapter;
        GridLayoutManager grid = new GridLayoutManager(requireContext(), COLUMNS);
        // The town's card and the titles across both columns, the tiles in one; by the list's own adapter, also in a
        // layout as the view goes
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                NearbyPlace row = adapter.getItem(position);
                return row != null && row.type == NearbyPlace.TYPE_TILE ? 1 : COLUMNS;
            }
        });
        this.list.setLayoutManager(grid);
        this.list.setHasFixedSize(true);
        this.list.setAdapter(this.adapter);
        view.findViewById(R.id.btnNearbyPermission).setOnClickListener(button -> requestPermission());
        AppUtils.registerAppReceiver(requireContext(), this.receiver, new IntentFilter(WeatherUpdater.ACTION_NEARBY_CHANGED));
        if (mayLocate()) {
            if (lastRows != null && rowSettings(isDarkSurface()).equals(lastRowsSettings)) {
                showRows(lastRows, lastRowsSettings);
            }
            load(false, true);
        } else {
            showPermissionRequest();
        }
    }

    @Override
    public void onDestroyView() {
        this.rowsWork.drop();
        listState = this.list.getLayoutManager().onSaveInstanceState();
        requireContext().unregisterReceiver(this.receiver);
        this.list = null;
        this.adapter = null;
        this.emptyView = null;
        this.progressView = null;
        this.permissionView = null;
        super.onDestroyView();
    }

    /** Whether the activity may know the device's position; true without one, as nothing is to be asked then */
    public boolean mayLocate() {
        Activity activity = getActivity();
        return activity == null || AppUtils.mayLocate(activity);
    }

    /** The rows made again with the settings' new units, language or wind, without the progress */
    public void refresh() {
        if (this.list != null && mayLocate()) {
            load(false, false);
        }
    }

    /** The location permission was granted or refused: with it the position is looked for and the rows load */
    public void onLocationPermissionChanged() {
        if (this.list == null) {
            return;
        }
        if (!mayLocate()) {
            showPermissionRequest();
            return;
        }
        PositionService.start(requireActivity(), AppSettings.getInstance().withGps);
        this.list.setVisibility(View.VISIBLE);
        this.permissionView.setVisibility(View.GONE);
        load(false, true);
    }

    private void showPermissionRequest() {
        this.list.setVisibility(View.INVISIBLE);
        this.permissionView.setVisibility(View.VISIBLE);
    }

    // Granted meanwhile, it is not asked for
    private void requestPermission() {
        if (mayLocate()) {
            onLocationPermissionChanged();
            return;
        }
        this.permissionLauncher.launch(AppUtils.LOCATION_PERMISSIONS);
    }

    /**
     * Given, the position is looked for. Refused for good, Android asks no more, and only the app's page in the
     * system's settings can give it: a dialog offers to go there. Refused once, the tab still shows its button
     */
    private void onPermissionAnswered() {
        if (mayLocate()) {
            onLocationPermissionChanged();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return;
        }
        // The activity's context, as the dialog may outlive the tab: the main screen makes the tabs anew as it comes back
        Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.nearby_permission_needed)
                .setPositiveButton(R.string.nearby_permission_settings, (dialog, which) -> openAppDetails(context))
                .show();
    }

    private static void openAppDetails(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.getPackageName(), null)));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No page of the app's details: " + e);
        }
    }

    // --- The rows ---

    private boolean isDarkSurface() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    // What the rows' texts and colours are made with
    private static String rowSettings(boolean darkSurface) {
        AppSettings settings = AppSettings.getInstance();
        return settings.language + "|" + settings.temperatureUnit + "|" + settings.windSpeedUnit + "|" + settings.distanceUnit
                + "|" + settings.onlyStrongWind + "|" + darkSurface;
    }

    /**
     * Makes the rows on the background thread, a load under way replaced. Without a list fresh enough the progress
     * shows while a search for the position goes on: not once a search by the GPS ended with the list, else when
     * asked or while the search's service runs
     */
    private void load(boolean isGps, boolean showProgress) {
        if (this.list == null) {
            return;
        }
        if (this.permissionView.getVisibility() == View.VISIBLE && mayLocate()) {
            this.permissionView.setVisibility(View.GONE);
            this.list.setVisibility(View.VISIBLE);
        }
        Context context = requireContext().getApplicationContext();
        boolean darkSurface = isDarkSurface();
        String settings = rowSettings(darkSurface);
        this.rowsWork.runLatest(() -> NearbyPlace.load(context, darkSurface), rows -> {
            if (this.list == null) {
                return;
            }
            if (rows != null) {
                this.progressView.setVisibility(View.GONE);
                this.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.INVISIBLE);
                showRows(rows, settings);
                return;
            }
            showRows(new ArrayList<>(), settings);
            boolean searching = !isGps && (showProgress || AppUtils.serviceRuns(context, PositionService.class));
            this.progressView.setVisibility(searching ? View.VISIBLE : View.GONE);
            this.emptyView.setVisibility(searching ? View.INVISIBLE : View.VISIBLE);
        });
    }

    private void showRows(List<NearbyPlace> rows, String settings) {
        this.adapter.setRows(rows);
        lastRows = new ArrayList<>(rows);
        lastRowsSettings = settings;
        // Once the list has its rows, before it is laid out with them: a list laid out empty loses it
        if (listState != null && this.adapter.getItemCount() > 0) {
            this.list.getLayoutManager().onRestoreInstanceState(listState);
            listState = null;
        }
    }

    // --- The rows' actions ---

    private void onRowClick(NearbyPlace row) {
        if (row != null && row.location != null && this.actionListener != null) {
            this.actionListener.onNearbyTapped(row.location);
        }
    }

    // On the frosted glass at the card or the tile, as the other menus of the app; a favourite isn't added again
    private boolean onRowLongClick(View anchor, NearbyPlace row) {
        if (row == null || row.location == null) {
            return false;
        }
        Place place = row.location;
        boolean favorite = AppSettings.getInstance().getFavorites().contains(place.id);
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_menu_favorite_add, getString(R.string.favorites_add), null, false, false,
                !favorite));
        GlassMenu.showAtRow(anchor, items, index -> {
            if (this.actionListener != null) {
                this.actionListener.onAddFavorite(place);
            }
        }, null);
        return true;
    }

    // --- The list ---

    private final class HeroHolder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView sky;
        final ImageView icon;
        final TextView name;
        final TextView temperature;
        final TextView condition;
        final TextView wind;
        NearbyPlace row;

        HeroHolder(View view) {
            super(view);
            this.card = view.findViewById(R.id.nearbyHero);
            this.sky = view.findViewById(R.id.ivHeroSky);
            this.icon = view.findViewById(R.id.ivHeroIcon);
            this.name = view.findViewById(R.id.tvHeroName);
            this.temperature = view.findViewById(R.id.tvHeroTemperature);
            this.condition = view.findViewById(R.id.tvHeroCondition);
            this.wind = view.findViewById(R.id.tvHeroWind);
            // The sky is clipped to the card's rounded corners
            this.card.setClipToOutline(true);
            // The pin before "You are here" and the wind's icon shadowed as the texts on the sky
            TextView here = view.findViewById(R.id.tvHeroHere);
            Drawable pin = ContextCompat.getDrawable(view.getContext(), R.drawable.ic_place).mutate();
            int pinSize = Math.round(PIN_SIZE_DP * view.getResources().getDisplayMetrics().density);
            pin.setBounds(0, 0, pinSize, pinSize);
            pin.setTint(Color.WHITE);
            here.setCompoundDrawablesRelative(ShadowedDrawable.like(pin, here), null, null, null);
            ShadowedDrawable.applyTo(this.wind);
            this.card.setOnClickListener(v -> onRowClick(this.row));
            this.card.setOnLongClickListener(v -> onRowLongClick(v, this.row));
        }
    }

    private final class TileHolder extends RecyclerView.ViewHolder {
        final View tile;
        final TextView name;
        final ImageView bearing;
        final TextView distance;
        final TextView wind;
        final TextView temperature;
        final ImageView icon;
        NearbyPlace row;

        TileHolder(View view) {
            super(view);
            this.tile = view.findViewById(R.id.nearbyTile);
            this.name = view.findViewById(R.id.tvTileName);
            this.bearing = view.findViewById(R.id.ivTileBearing);
            this.distance = view.findViewById(R.id.tvTileDistance);
            this.wind = view.findViewById(R.id.tvTileWind);
            this.temperature = view.findViewById(R.id.tvTileTemperature);
            this.icon = view.findViewById(R.id.ivTileIcon);
            this.tile.setOnClickListener(v -> onRowClick(this.row));
            this.tile.setOnLongClickListener(v -> onRowLongClick(v, this.row));
        }
    }

    /** The town's card, the sections' titles and the tiles, as NearbyPlace made them */
    private final class NearbyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<NearbyPlace> rows = new ArrayList<>();
        private final LayoutInflater inflater;
        private final CardSkies skies;
        private final int windPillPadding;
        private final int strongWindColor;
        private final int windColor;

        NearbyAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
            this.skies = new CardSkies(context, R.dimen.nearby_hero_height);
            this.windPillPadding = Math.round(WIND_PILL_PADDING_DP * context.getResources().getDisplayMetrics().density);
            this.strongWindColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError, Color.RED);
            this.windColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        }

        void setRows(List<NearbyPlace> rows) {
            this.rows.clear();
            this.rows.addAll(rows);
            notifyDataSetChanged();
        }

        NearbyPlace getItem(int position) {
            return position >= 0 && position < this.rows.size() ? this.rows.get(position) : null;
        }

        @Override
        public int getItemCount() {
            return this.rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return this.rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == NearbyPlace.TYPE_HERO) {
                return new HeroHolder(this.inflater.inflate(R.layout.item_nearby_hero, parent, false));
            }
            if (viewType == NearbyPlace.TYPE_TILE) {
                return new TileHolder(this.inflater.inflate(R.layout.item_nearby_tile, parent, false));
            }
            return new RecyclerView.ViewHolder(this.inflater.inflate(R.layout.item_nearby_header, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            NearbyPlace row = this.rows.get(position);
            if (holder instanceof HeroHolder) {
                bindHero((HeroHolder) holder, row);
            } else if (holder instanceof TileHolder) {
                bindTile((TileHolder) holder, row);
            } else {
                ((TextView) holder.itemView).setText(row.header);
            }
        }

        private void bindHero(HeroHolder holder, NearbyPlace row) {
            holder.row = row;
            holder.name.setText(row.name);
            holder.temperature.setText(row.temperature);
            holder.condition.setText(row.condition);
            holder.icon.setImageDrawable(row.icon);
            holder.wind.setVisibility(row.wind != null ? View.VISIBLE : View.GONE);
            if (row.wind != null) {
                holder.wind.setText(row.wind);
                if (row.strongWind) {
                    holder.wind.setBackgroundResource(R.drawable.bg_city_wind_strong);
                } else {
                    holder.wind.setBackground(null);
                }
                int side = row.strongWind ? this.windPillPadding : 0;
                holder.wind.setPaddingRelative(side, holder.wind.getPaddingTop(), side, holder.wind.getPaddingBottom());
            }
            holder.card.setContentDescription(row.name + ", " + row.temperature + ", " + row.condition);
            // Changed at once: the town's sky isn't faded from another's
            this.skies.bind(holder.sky, null, row.skyLayers, row.skyColor, NearbyFragment.this.list, false);
        }

        private void bindTile(TileHolder holder, NearbyPlace row) {
            holder.row = row;
            holder.name.setText(row.name);
            boolean hasBearing = !Float.isNaN(row.bearing);
            holder.bearing.setVisibility(hasBearing ? View.VISIBLE : View.GONE);
            if (hasBearing) {
                holder.bearing.setRotation(row.bearing);
            }
            holder.distance.setText(row.distance);
            holder.wind.setVisibility(row.wind != null ? View.VISIBLE : View.GONE);
            if (row.wind != null) {
                holder.wind.setText(" · " + row.wind);
                holder.wind.setTextColor(row.strongWind ? this.strongWindColor : this.windColor);
            }
            holder.temperature.setText(row.temperature);
            holder.temperature.setTextColor(row.temperatureColor);
            holder.icon.setImageDrawable(row.icon);
            holder.tile.setContentDescription(row.name + ", " + row.distance + ", " + row.temperature + ", " + row.condition);
        }
    }
}
