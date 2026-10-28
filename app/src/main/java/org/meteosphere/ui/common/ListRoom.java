package org.meteosphere.ui.common;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import org.meteosphere.R;

/**
 * Room at the ends of scrolling lists for the bars and the keyboard over them: added to a list's own padding, kept as
 * it was the first time, and the content scrolls through the room rather than being clipped at it
 */
public final class ListRoom {

    private ListRoom() {
    }

    /** The first list or scroll view in the tree, the view itself first; null if there is none */
    public static View find(View view) {
        if (isScrolling(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The room at the end of every list or scroll view in the tree, none of them looked into */
    public static void atEnd(View view, int room) {
        if (isScrolling(view)) {
            setBottom(view, room);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                atEnd(group.getChildAt(i), room);
            }
        }
    }

    /** The list's own top padding and the room over it, its content kept where it stood from the padding */
    public static void setTop(View list, int room) {
        TopPadding.set(list, own(list, R.id.tag_own_top_padding, list.getPaddingTop()) + room);
        scrollThrough(list);
    }

    /** The list's own bottom padding and the room under it */
    public static void setBottom(View list, int room) {
        int bottom = own(list, R.id.tag_own_bottom_padding, list.getPaddingBottom()) + room;
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), bottom);
        scrollThrough(list);
    }

    private static boolean isScrolling(View view) {
        return view instanceof AbsListView || view instanceof RecyclerView || view instanceof NestedScrollView || view instanceof ScrollView;
    }

    // The padding the view had before any room, kept under the tag the first time
    private static int own(View view, int tag, int padding) {
        Object own = view.getTag(tag);
        if (own instanceof Integer) {
            return (Integer) own;
        }
        view.setTag(tag, padding);
        return padding;
    }

    private static void scrollThrough(View list) {
        if (list instanceof ViewGroup) {
            ((ViewGroup) list).setClipToPadding(false);
        }
    }
}
