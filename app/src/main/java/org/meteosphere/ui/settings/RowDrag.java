package org.meteosphere.ui.settings;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * The drag of an order screen's rows, up and down only: by the handle at once, or by a long press anywhere on a row.
 * The rows' items move in their list with the row dragged, which is lifted just over the others, as the helper draws
 * it, and round on its own
 */
final class RowDrag extends ItemTouchHelper.Callback {

    private static final float LIFTED_ALPHA = 0.8f;

    private final List<?> items;
    private final ItemTouchHelper helper = new ItemTouchHelper(this);
    private RecyclerView list;

    /** The items the list's rows show, in their order, which the drag changes */
    RowDrag(List<?> items) {
        this.items = items;
    }

    void attachTo(RecyclerView list) {
        this.list = list;
        this.helper.attachToRecyclerView(list);
    }

    /**
     * The row's drag starts as soon as a finger goes down on its handle. The handle keeps the touch: lifted without
     * moving, the finger only grabbed the row, its tick as it was
     */
    @SuppressLint("ClickableViewAccessibility")
    void dragByHandle(RecyclerView.ViewHolder row, View handle) {
        handle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                this.helper.startDrag(row);
            }
            return true;
        });
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder row) {
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder row, int direction) {
    }

    @Override
    public boolean onMove(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder row, @NonNull RecyclerView.ViewHolder target) {
        int from = row.getBindingAdapterPosition();
        int to = target.getBindingAdapterPosition();
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || adapter == null) {
            return false;
        }
        move(this.items, from, to);
        adapter.notifyItemMoved(from, to);
        SettingsSegments.refresh(list, row);
        return true;
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder row, int actionState) {
        super.onSelectedChanged(row, actionState);
        if (row != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            row.itemView.setAlpha(LIFTED_ALPHA);
            SettingsSegments.refresh(this.list, row);
        }
    }

    // Once the row has settled in its place; the helper sets its elevation back
    @Override
    public void clearView(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder row) {
        super.clearView(list, row);
        row.itemView.setAlpha(1);
        SettingsSegments.refresh(list, null);
    }

    /** The item taken out of its place and put in the new one, those between shifting, also past several of them */
    static <T> void move(List<T> items, int from, int to) {
        items.add(to, items.remove(from));
    }
}
