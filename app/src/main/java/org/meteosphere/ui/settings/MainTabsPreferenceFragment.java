package org.meteosphere.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.meteosphere.R;
import org.meteosphere.settings.AppSettings;

/**
 * The main screen's tabs: their order, dragged, and which of them are shown, the favourites always. They are the
 * screen's own till the bar's tick applies them, Back leaving the saved ones, and come back when the screen is made
 * again
 */
public final class MainTabsPreferenceFragment extends Fragment {

    private static final String STATE_ORDER = "order";
    private static final String STATE_HIDDEN = "hidden";

    private ArrayList<Integer> order;
    private final Set<Integer> shown = new HashSet<>();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ArrayList<Integer> hidden = bundle != null ? bundle.getIntegerArrayList(STATE_HIDDEN) : null;
        ArrayList<Integer> order = bundle != null ? bundle.getIntegerArrayList(STATE_ORDER) : null;
        if (order == null || hidden == null) {
            AppSettings settings = AppSettings.getInstance();
            order = new ArrayList<>(settings.getSortedMainTabs());
            this.shown.addAll(settings.getMainTabs());
        } else {
            for (int tab : order) {
                if (!hidden.contains(tab)) {
                    this.shown.add(tab);
                }
            }
        }
        this.order = order;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        RecyclerView list = new RecyclerView(inflater.getContext());
        // For the list's place to come back when the screen is made again
        list.setId(android.R.id.list);
        list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setLayoutManager(new LinearLayoutManager(inflater.getContext()));
        SettingsSegments.setListPadding(list, true);
        return list;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        RecyclerView list = (RecyclerView) view;
        RowDrag drag = new RowDrag(this.order);
        list.setAdapter(new OrderRow.Adapter(this.order, this.shown, tab -> getString(titleOf(tab)),
                tab -> tab == AppSettings.TAB_FAVORITES, drag));
        drag.attachTo(list);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putIntegerArrayList(STATE_ORDER, this.order);
        state.putIntegerArrayList(STATE_HIDDEN, hidden());
    }

    /** Applied by the bar's tick only */
    public void apply() {
        AppSettings.getInstance().saveMainTabs(this.order, hidden());
    }

    // The tabs unticked, in their order
    private ArrayList<Integer> hidden() {
        ArrayList<Integer> hidden = new ArrayList<>();
        for (int tab : this.order) {
            if (!this.shown.contains(tab)) {
                hidden.add(tab);
            }
        }
        return hidden;
    }

    // As the main screen's bar names the tab
    private static int titleOf(int tab) {
        switch (tab) {
            case AppSettings.TAB_NEARBY:
                return R.string.tab_nearby;
            case AppSettings.TAB_FAVORITES:
                return R.string.tab_favorites;
            case AppSettings.TAB_MOON:
                return R.string.moon;
            case AppSettings.TAB_SUN:
                return R.string.tab_sun;
            case AppSettings.TAB_EARTHQUAKES:
                return R.string.tab_earthquakes;
            default:
                throw new IllegalArgumentException("No tab " + tab);
        }
    }
}
