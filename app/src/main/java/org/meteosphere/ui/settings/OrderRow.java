package org.meteosphere.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.meteosphere.R;

/**
 * A row of the order screens, of settings_order_row: the handle, the title and the tick of an item. Also a fixed row
 * outside a list, its icon in the handle's place
 */
final class OrderRow extends RecyclerView.ViewHolder {

    final ImageView handle;
    final TextView title;
    final CheckBox tick;

    private OrderRow(View row) {
        super(row);
        this.handle = row.findViewById(R.id.ivDragHandle);
        this.title = row.findViewById(R.id.tvRowTitle);
        this.tick = row.findViewById(R.id.cbRowShown);
        // Read as a checkbox with the tick's state, the tick having no focus of its own; a locked row as not enabled
        ViewCompat.setAccessibilityDelegate(row, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(CheckBox.class.getName());
                info.setCheckable(true);
                info.setChecked(OrderRow.this.tick.isChecked());
                info.setEnabled(OrderRow.this.tick.isEnabled());
            }

            // The click's event tells the state the tap gave
            @Override
            public void onInitializeAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event) {
                super.onInitializeAccessibilityEvent(host, event);
                event.setClassName(CheckBox.class.getName());
                event.setChecked(OrderRow.this.tick.isChecked());
            }
        });
    }

    static OrderRow inflate(ViewGroup parent) {
        return new OrderRow(LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_order_row, parent, false));
    }

    /** Shows the item ticked while it is among the ticked ones; a tap on the row toggles it there, none on a locked row */
    void bind(CharSequence title, int item, Set<Integer> ticked, boolean locked) {
        this.title.setText(title);
        this.tick.setChecked(ticked.contains(item));
        this.tick.setEnabled(!locked);
        if (locked) {
            this.itemView.setOnClickListener(null);
            this.itemView.setClickable(false);
            return;
        }
        this.itemView.setOnClickListener(row -> {
            this.tick.toggle();
            if (this.tick.isChecked()) {
                ticked.add(item);
            } else {
                ticked.remove(item);
            }
        });
    }

    /** The rows of the items in their order, each a segment of their group, dragged by the drag given */
    static final class Adapter extends RecyclerView.Adapter<OrderRow> {

        private final List<Integer> items;
        private final Set<Integer> ticked;
        private final IntFunction<CharSequence> titles;
        private final IntPredicate locked;
        private final RowDrag drag;

        Adapter(List<Integer> items, Set<Integer> ticked, IntFunction<CharSequence> titles, IntPredicate locked, RowDrag drag) {
            this.items = items;
            this.ticked = ticked;
            this.titles = titles;
            this.locked = locked;
            this.drag = drag;
        }

        @NonNull
        @Override
        public OrderRow onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            OrderRow row = inflate(parent);
            this.drag.dragByHandle(row, row.handle);
            return row;
        }

        @Override
        public void onBindViewHolder(@NonNull OrderRow row, int position) {
            int item = this.items.get(position);
            row.bind(this.titles.apply(item), item, this.ticked, this.locked.test(item));
            SettingsSegments.apply(row.itemView, position == 0, position == getItemCount() - 1);
        }

        @Override
        public int getItemCount() {
            return this.items.size();
        }
    }
}
