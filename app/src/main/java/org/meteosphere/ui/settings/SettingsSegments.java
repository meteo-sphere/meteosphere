package org.meteosphere.ui.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import org.meteosphere.R;

/**
 * Settings rows as segments of rounded groups, as in Android 16: the group's outer corners round, the inner ones only
 * softened, a thin gap between the rows. The gap is drawn into each row's background, so a row changing its place in
 * the group, as while the rows are dragged, only changes its background.
 */
public final class SettingsSegments {

    private static final int SINGLE = 0;
    private static final int FIRST = 1;
    private static final int MIDDLE = 2;
    private static final int LAST = 3;

    private SettingsSegments() {
    }

    /** Draws the row as a segment of its group, the first and the last of it rounded on their outer side */
    public static void apply(View row, boolean first, boolean last) {
        int kind = first ? (last ? SINGLE : FIRST) : (last ? LAST : MIDDLE);
        Object background = row.getTag(R.id.tag_segment_background);
        if (!(background instanceof Drawable) || !Integer.valueOf(kind).equals(row.getTag(R.id.tag_segment_kind))) {
            background = background(row.getContext(), first, last);
            row.setTag(R.id.tag_segment_background, background);
            row.setTag(R.id.tag_segment_kind, kind);
        }
        // A row binding again is given its own background back first, as a preference's row is
        if (row.getBackground() != background) {
            // The inset of the gap would become the row's padding: the row keeps its own
            int start = row.getPaddingStart();
            int top = row.getPaddingTop();
            int end = row.getPaddingEnd();
            int bottom = row.getPaddingBottom();
            row.setBackground((Drawable) background);
            row.setPaddingRelative(start, top, end, bottom);
        }
    }

    /** Lights a row while its choices are open over it, in the shape of its segment */
    public static void setLit(View row, boolean lit) {
        if (!lit) {
            row.setForeground(null);
            return;
        }
        Object kind = row.getTag(R.id.tag_segment_kind);
        boolean first = !(kind instanceof Integer) || (Integer) kind == SINGLE || (Integer) kind == FIRST;
        boolean last = !(kind instanceof Integer) || (Integer) kind == SINGLE || (Integer) kind == LAST;
        GradientDrawable shape = shape(row.getContext(), first, last);
        shape.setColor(MaterialColors.compositeARGBWithAlpha(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface), 26));
        row.setForeground(inset(row.getContext(), shape, first, last));
    }

    /** Draws the rows of a list after a move: each in its new place, the lifted one round on its own */
    static void refresh(RecyclerView list, RecyclerView.ViewHolder lifted) {
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (adapter == null) {
            return;
        }
        int count = adapter.getItemCount();
        for (int i = 0; i < list.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = list.getChildViewHolder(list.getChildAt(i));
            int position = holder.getBindingAdapterPosition();
            if (holder == lifted) {
                apply(holder.itemView, true, true);
            } else if (position != RecyclerView.NO_POSITION) {
                apply(holder.itemView, position == 0, position == count - 1);
            }
        }
    }

    /**
     * Sets a list's groups in from the screen's sides, with room above them unless the list begins with its own, and
     * below them. The scroll bar stays at the edge.
     */
    static void setListPadding(View list, boolean roomAbove) {
        Resources resources = list.getResources();
        int margin = resources.getDimensionPixelSize(R.dimen.settings_list_margin);
        list.setPadding(margin, roomAbove ? resources.getDimensionPixelSize(R.dimen.settings_list_top) : 0, margin, resources.getDimensionPixelSize(R.dimen.settings_list_bottom));
        list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
    }

    private static Drawable background(Context context, boolean first, boolean last) {
        GradientDrawable shape = shape(context, first, last);
        shape.setColor(ContextCompat.getColor(context, R.color.settings_row));
        // Without a mask the ripple keeps to the shape, its corners included
        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorControlHighlight, 0)), shape, null);
        return inset(context, ripple, first, last);
    }

    // The segment's outline: round outer corners, softened inner ones
    private static GradientDrawable shape(Context context, boolean first, boolean last) {
        Resources resources = context.getResources();
        float outer = resources.getDimension(R.dimen.settings_row_corner_outer);
        float inner = resources.getDimension(R.dimen.settings_row_corner_inner);
        float top = first ? outer : inner;
        float bottom = last ? outer : inner;
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        return shape;
    }

    // Half the gap on each side a row meets another
    private static Drawable inset(Context context, Drawable drawable, boolean first, boolean last) {
        int halfGap = context.getResources().getDimensionPixelSize(R.dimen.settings_row_gap) / 2;
        return new InsetDrawable(drawable, 0, first ? 0 : halfGap, 0, last ? 0 : halfGap);
    }
}
