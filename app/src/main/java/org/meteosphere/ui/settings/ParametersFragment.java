package org.meteosphere.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.meteosphere.R;
import org.meteosphere.settings.AppSettings;

/**
 * The city page's tiles: the order of those dragged and which of all are shown, the earthquakes and the Sun now fixed
 * under the others. They are the screen's own till the bar's tick applies them, Back leaving the saved ones, and come
 * back when the screen is made again
 */
public final class ParametersFragment extends Fragment {

    private static final String STATE_ORDER = "order";
    private static final String STATE_TICKED = "ticked";

    // The page's own tiles, always first and shown, and not offered here
    private static final List<Integer> PAGE_OWN = List.of(0, 1);
    private static final int WIND = 2;
    private static final int HUMIDITY = 3;
    private static final int PRESSURE = 4;
    private static final int GEOMAGNETIC = 5;
    private static final int PRECIPITATION = 6;
    private static final int MOON = AppSettings.MOON_DETAIL_WIDGET;
    private static final int EARTHQUAKES = AppSettings.EARTHQUAKE_DETAIL_WIDGET;
    private static final int SUN_NOW = AppSettings.SUN_NOW_DETAIL_WIDGET;

    // The dragged ones in their order, and the tiles ticked among all
    private ArrayList<Integer> order;
    private final Set<Integer> ticked = new HashSet<>();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ArrayList<Integer> order = bundle != null ? bundle.getIntegerArrayList(STATE_ORDER) : null;
        ArrayList<Integer> ticked = bundle != null ? bundle.getIntegerArrayList(STATE_TICKED) : null;
        if (order == null || ticked == null) {
            AppSettings settings = AppSettings.getInstance();
            order = draggedOrder(settings.getSortedDetailWidgets());
            ticked = new ArrayList<>(settings.getShownDetailWidgets());
        }
        this.order = order;
        this.ticked.addAll(ticked);
    }

    // One page scrolling as a whole: the dragged ones' list as tall as its rows, the fixed ones' group under it
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Context context = inflater.getContext();
        NestedScrollView page = new NestedScrollView(context);
        // For the page's place to come back when the screen is made again
        page.setId(android.R.id.list);
        page.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.setClipToPadding(false);
        SettingsSegments.setListPadding(page, true);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        page.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView list = new RecyclerView(context);
        list.setNestedScrollingEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setLayoutManager(new LinearLayoutManager(context));
        column.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        RowDrag drag = new RowDrag(this.order);
        list.setAdapter(new OrderRow.Adapter(this.order, this.ticked, widget -> getString(titleOf(widget)), widget -> false, drag));
        drag.attachTo(list);

        LinearLayout fixed = new LinearLayout(context);
        fixed.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.topMargin = getResources().getDimensionPixelSize(R.dimen.settings_group_gap);
        column.addView(fixed, gap);
        addFixedRow(fixed, EARTHQUAKES, R.drawable.ic_settings_earthquakes, true, false);
        addFixedRow(fixed, SUN_NOW, R.drawable.ic_settings_sun_now, false, true);
        return page;
    }

    // Not dragged: its icon in the handle's place
    private void addFixedRow(ViewGroup group, int widget, int icon, boolean first, boolean last) {
        OrderRow row = OrderRow.inflate(group);
        row.handle.setImageResource(icon);
        row.bind(getString(titleOf(widget)), widget, this.ticked, false);
        SettingsSegments.apply(row.itemView, first, last);
        group.addView(row.itemView);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putIntegerArrayList(STATE_ORDER, this.order);
        state.putIntegerArrayList(STATE_TICKED, new ArrayList<>(this.ticked));
    }

    /** Applied by the bar's tick only */
    public void apply() {
        AppSettings settings = AppSettings.getInstance();
        List<Integer> saved = savedOrder(this.order);
        settings.setSortedDetailWidgets(saved);
        settings.setShownDetailWidgets(shownOf(saved, this.ticked));
    }

    private static int titleOf(int widget) {
        switch (widget) {
            case WIND:
                return R.string.wind_speed;
            case HUMIDITY:
                return R.string.humidity;
            case PRESSURE:
                return R.string.pressure;
            case GEOMAGNETIC:
                return R.string.geomagnetic;
            case PRECIPITATION:
                return R.string.precipitation;
            case MOON:
                return R.string.moon;
            case EARTHQUAKES:
                return R.string.earthquakes;
            case SUN_NOW:
                return R.string.sun_now;
            default:
                throw new IllegalArgumentException("No tile " + widget);
        }
    }

    // --- The decisions ---

    /**
     * The dragged tiles in the saved order, each once, then those it lacks by their ids; the page's own and the fixed
     * ones left out. Null, as before the tiles were ever arranged, gives them all by their ids
     */
    static ArrayList<Integer> draggedOrder(List<Integer> saved) {
        ArrayList<Integer> order = new ArrayList<>();
        if (saved != null) {
            for (int widget : saved) {
                if (widget >= WIND && widget <= MOON && !order.contains(widget)) {
                    order.add(widget);
                }
            }
        }
        for (int widget = WIND; widget <= MOON; widget++) {
            if (!order.contains(widget)) {
                order.add(widget);
            }
        }
        return order;
    }

    /** The order saved: the page's own, the dragged ones in their order, then the fixed ones */
    static ArrayList<Integer> savedOrder(List<Integer> dragged) {
        ArrayList<Integer> order = new ArrayList<>(PAGE_OWN);
        order.addAll(dragged);
        order.add(EARTHQUAKES);
        order.add(SUN_NOW);
        return order;
    }

    /** The tiles shown: those of the order that are the page's own or ticked, in its order */
    static ArrayList<Integer> shownOf(List<Integer> order, Collection<Integer> ticked) {
        ArrayList<Integer> shown = new ArrayList<>();
        for (int widget : order) {
            if (PAGE_OWN.contains(widget) || ticked.contains(widget)) {
                shown.add(widget);
            }
        }
        return shown;
    }
}
