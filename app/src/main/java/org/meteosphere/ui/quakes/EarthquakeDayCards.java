package org.meteosphere.ui.quakes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;

/** Each run of earthquakes on a rounded card of its own, with lines between them; the titles of the days over them */
final class EarthquakeDayCards extends RecyclerView.ItemDecoration {
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint();
    private final RectF rect = new RectF();
    private final float radius;
    private final int side;
    private final int lineStart;
    private final int lineEnd;
    private final int gap;
    private final int cardAlpha;
    private final int lineAlpha;

    EarthquakeDayCards(RecyclerView list) {
        float density = list.getResources().getDisplayMetrics().density;
        this.radius = 24 * density;
        this.side = Math.round(16 * density);
        this.lineStart = Math.round(80 * density);
        this.lineEnd = Math.round(16 * density);
        this.gap = Math.round(12 * density);
        this.cardPaint.setColor(MaterialColors.getColor(list, com.google.android.material.R.attr.colorSurfaceContainer));
        this.linePaint.setColor(MaterialColors.getColor(list, com.google.android.material.R.attr.colorOutlineVariant));
        this.linePaint.setStrokeWidth(Math.max(1, density));
        this.cardAlpha = this.cardPaint.getAlpha();
        this.lineAlpha = this.linePaint.getAlpha();
    }

    private static boolean isQuake(RecyclerView parent, int position) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        return adapter != null && position >= 0 && position < adapter.getItemCount()
                && adapter.getItemViewType(position) == EarthquakeListAdapter.TYPE_QUAKE;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (isQuake(parent, position)) {
            // Right under the summary, as by distance with no day's title between, the card keeps away from it
            RecyclerView.Adapter<?> adapter = parent.getAdapter();
            boolean afterSummary = adapter != null && position > 0 && adapter.getItemViewType(position - 1) == EarthquakeListAdapter.TYPE_SUMMARY;
            outRect.set(this.side, afterSummary ? this.gap : 0, this.side, 0);
        }
    }

    @Override
    public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        int count = parent.getChildCount();
        int i = 0;
        while (i < count) {
            View first = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(first);
            if (!isQuake(parent, position)) {
                i++;
                continue;
            }
            // A run of earthquakes on screen; it goes on past the screen when the items beyond are earthquakes too
            int j = i;
            while (j + 1 < count && isQuake(parent, parent.getChildAdapterPosition(parent.getChildAt(j + 1)))) {
                j++;
            }
            View last = parent.getChildAt(j);
            float top = first.getTop() + first.getTranslationY();
            float bottom = last.getBottom() + last.getTranslationY();
            if (isQuake(parent, position - 1)) {
                top = Math.min(top, 0) - 2 * this.radius;
            }
            if (isQuake(parent, parent.getChildAdapterPosition(last) + 1)) {
                bottom = Math.max(bottom, parent.getHeight()) + 2 * this.radius;
            }
            // The card fades with its lines as they come and go
            float alpha = 0;
            for (int k = i; k <= j; k++) {
                alpha = Math.max(alpha, parent.getChildAt(k).getAlpha());
            }
            this.rect.set(first.getLeft(), top, first.getRight(), bottom);
            this.cardPaint.setAlpha(Math.round(this.cardAlpha * alpha));
            canvas.drawRoundRect(this.rect, this.radius, this.radius, this.cardPaint);
            for (int k = i; k < j; k++) {
                View child = parent.getChildAt(k);
                float y = child.getBottom() + child.getTranslationY();
                this.linePaint.setAlpha(Math.round(this.lineAlpha * Math.min(child.getAlpha(), parent.getChildAt(k + 1).getAlpha())));
                canvas.drawLine(child.getLeft() + this.lineStart, y, child.getRight() - this.lineEnd, y, this.linePaint);
            }
            i = j + 1;
        }
    }
}
