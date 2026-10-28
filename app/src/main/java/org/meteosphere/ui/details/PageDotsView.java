package org.meteosphere.ui.details;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * A dot per page, the current one white. The current location's page, when first, is a small navigation arrow.
 * Shows at most MAX_DOTS dots, sliding the window with the current page.
 */
public class PageDotsView extends View {

    private static final int MAX_DOTS = 9;

    private final float density;
    private final Paint onPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint offPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private final float dotRadius;
    private final float spacing;
    private int count;
    private int current;
    private boolean firstIsLocation;

    public PageDotsView(Context context) {
        this(context, null);
    }

    public PageDotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        this.onPaint.setColor(0xffffffff);
        this.offPaint.setColor(0x66ffffff);
        this.dotRadius = 3 * this.density;
        this.spacing = 12 * this.density;
    }

    public void setPages(int count, int current, boolean firstIsLocation) {
        this.count = count;
        this.current = current;
        this.firstIsLocation = firstIsLocation;
        requestLayout();
        invalidate();
    }

    public void setCurrent(int current) {
        if (this.current != current) {
            this.current = current;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int shown = Math.min(this.count, MAX_DOTS);
        int width = Math.round(shown * this.spacing + getPaddingLeft() + getPaddingRight());
        int height = Math.round(this.spacing + getPaddingTop() + getPaddingBottom());
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.count <= 1) {
            return;
        }
        int shown = Math.min(this.count, MAX_DOTS);
        int first = Math.max(0, Math.min(this.current - shown / 2, this.count - shown));
        float cy = getHeight() / 2f;
        float startX = (getWidth() - shown * this.spacing) / 2 + this.spacing / 2;
        for (int i = 0; i < shown; i++) {
            int page = first + i;
            float cx = startX + i * this.spacing;
            Paint paint = page == this.current ? this.onPaint : this.offPaint;
            // The window's edge dots are smaller when more pages lie beyond them
            float radius = this.dotRadius;
            if ((i == 0 && first > 0) || (i == shown - 1 && first + shown < this.count)) {
                radius *= 0.6f;
            }
            if (page == 0 && this.firstIsLocation) {
                drawArrow(canvas, cx, cy, paint);
            } else {
                canvas.drawCircle(cx, cy, radius, paint);
            }
        }
    }

    // The navigation arrow of the current location
    private void drawArrow(Canvas canvas, float cx, float cy, Paint paint) {
        float s = 4.5f * this.density;
        this.arrowPath.reset();
        this.arrowPath.moveTo(cx + s, cy - s);
        this.arrowPath.lineTo(cx - s, cy - s * 0.1f);
        this.arrowPath.lineTo(cx - s * 0.05f, cy + s * 0.05f);
        this.arrowPath.lineTo(cx + s * 0.1f, cy + s);
        this.arrowPath.close();
        canvas.drawPath(this.arrowPath, paint);
    }
}
