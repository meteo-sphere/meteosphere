package org.meteosphere.ui.common;

import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

/** A scrolling view's top padding, its content kept where it stood from the padding */
public final class TopPadding {

    private TopPadding() {
    }

    /**
     * Sets the view's top padding. A scroll view moves its content with the padding by itself; a RecyclerView already
     * laid out keeps its rows where they stand on the screen, so a padding set late, as the bar's height is that comes
     * after a screen made again has laid out its tabs, would leave the first rows under the bar: its first row is put
     * back as far from the padding as it was
     */
    public static void set(View view, int top) {
        int old = view.getPaddingTop();
        if (old == top) {
            return;
        }
        int position = RecyclerView.NO_POSITION;
        int offset = 0;
        if (view instanceof RecyclerView && !view.isLayoutRequested()) {
            RecyclerView list = (RecyclerView) view;
            RecyclerView.LayoutManager layout = list.getLayoutManager();
            if (layout instanceof LinearLayoutManager && ((LinearLayoutManager) layout).getOrientation() == RecyclerView.VERTICAL
                    && list.getChildCount() > 0) {
                View first = list.getChildAt(0);
                position = list.getChildLayoutPosition(first);
                offset = OrientationHelper.createVerticalHelper(layout).getDecoratedStart(first) - old;
            }
        }
        view.setPadding(view.getPaddingLeft(), top, view.getPaddingRight(), view.getPaddingBottom());
        if (position != RecyclerView.NO_POSITION) {
            ((LinearLayoutManager) ((RecyclerView) view).getLayoutManager()).scrollToPositionWithOffset(position, offset);
        }
    }
}
