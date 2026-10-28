package org.meteosphere.ui.main;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * How far a list has scrolled from its start, and what the header over it makes of that: the title goes away first,
 * the controls under it rise into its place, and once the content runs under them their glass fades in.
 */
public final class HeaderScroll {

    // Scrolled further than the header could ever take
    static final int FAR = Integer.MAX_VALUE / 4;
    // The glass fades in over this much of the content run under it
    private static final float FADE_DP = 32;

    private HeaderScroll() {
    }

    /** How far the list has scrolled from its start, FAR once its first row is gone */
    public static int depth(View list) {
        if (list instanceof NestedScrollView || list instanceof ScrollView) {
            return list.getScrollY();
        }
        if (list instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) list;
            RecyclerView.LayoutManager layout = recyclerView.getLayoutManager();
            if (layout == null || recyclerView.getChildCount() == 0) {
                return 0;
            }
            View first = recyclerView.getChildAt(0);
            // Where the row stands on screen: after a new data set its adapter position is unknown until the next
            // layout, which a tab reloaded as it is shown may wait for over frames, and the header took it as gone
            if (recyclerView.getChildLayoutPosition(first) != 0) {
                return FAR;
            }
            ViewGroup.LayoutParams params = first.getLayoutParams();
            int margin = params instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) params).topMargin : 0;
            return recyclerView.getPaddingTop() - (layout.getDecoratedTop(first) - margin);
        }
        if (list instanceof AbsListView) {
            AbsListView listView = (AbsListView) list;
            if (listView.getChildCount() == 0) {
                return 0;
            }
            if (listView.getFirstVisiblePosition() != 0) {
                return FAR;
            }
            return listView.getPaddingTop() - listView.getChildAt(0).getTop();
        }
        return 0;
    }

    /** How far the title has gone up, all of it once the list has scrolled as far as it is tall */
    public static int collapse(int depth, int titleHeight) {
        return Math.max(0, Math.min(depth, titleHeight));
    }

    /** How much of the glass shows, 0 to 1: it fades in as the content runs under the controls, once the title is gone */
    public static float glass(Context context, int depth, int titleHeight) {
        float fade = FADE_DP * context.getResources().getDisplayMetrics().density;
        return Math.max(0f, Math.min(1f, (depth - titleHeight) / fade));
    }
}
