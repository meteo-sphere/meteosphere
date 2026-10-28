package org.meteosphere.ui.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.MaterialColors;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The requests a day to the data sources as bars, the sources stacked in their colours, today's at the right end. The
 * days scroll sideways; a tap picks a day, lit behind its bar with its date under it, and a tap on it again lets it go.
 */
public final class RequestChartView extends View {

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    // The scale's top over the most requests in sight: the picked day's light rises a little over its bar
    private static final float HEADROOM = 1.08f;

    /** The days in sight changed: the first and the last, as UTC days since 1970 */
    public interface OnRangeListener {
        void onRangeChanged(long first, long last);
    }

    /** A day was picked, as UTC days since 1970, or let go: -1 */
    public interface OnDayListener {
        void onDayPicked(long day);
    }

    /** A source's requests a day, from the chart's first day to today */
    public static final class Series {
        final int color;
        final int[] counts;

        public Series(int color, int[] counts) {
            this.color = color;
            this.counts = counts;
        }
    }

    private final float density;
    private final float barWidth;
    private final float step;
    private final float axisHeight;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pickedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final OverScroller scroller;
    private final GestureDetector gestures;
    private final int touchSlop;
    private final SimpleDateFormat axisFormat;

    private List<Series> series = Collections.emptyList();
    private long first;
    private int days;
    private int[] totals = new int[0];
    // The day picked, counted from the first; -1 for none
    private int picked = -1;
    // How far the days are scrolled back from today, px: 0 shows today at the right end
    private float offset;
    // The scale's top, in requests: eases to the days in sight as they scroll
    private float scale;
    private long shownFirst = Long.MIN_VALUE;
    private long shownLast = Long.MIN_VALUE;
    private OnRangeListener rangeListener;
    private OnDayListener dayListener;
    private float downX;
    private float downY;
    private boolean dragging;

    public RequestChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        this.barWidth = 14 * this.density;
        this.step = 20 * this.density;
        this.axisHeight = 22 * this.density;
        this.basePaint.setStrokeWidth(this.density);
        this.basePaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics());
        this.axisPaint.setTextSize(textSize);
        this.axisPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        this.pickedPaint.setTextSize(textSize);
        this.pickedPaint.setFakeBoldText(true);
        this.pickedPaint.setColor(primary);
        this.lightPaint.setColor(ColorUtils.setAlphaComponent(primary, 0x1f));
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        this.axisFormat = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dM"), locale);
        this.axisFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.scroller = new OverScroller(context);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                pick(dayAt(e.getX()));
                performClick();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (RequestChartView.this.dragging) {
                    scrollTo(RequestChartView.this.offset - distanceX);
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (RequestChartView.this.dragging) {
                    RequestChartView.this.scroller.fling(Math.round(RequestChartView.this.offset), 0, Math.round(velocityX), 0, 0, Math.round(maxOffset()), 0, 0);
                    postInvalidateOnAnimation();
                }
                return true;
            }
        });
    }

    public void setOnRangeListener(OnRangeListener listener) {
        this.rangeListener = listener;
    }

    public void setOnDayListener(OnDayListener listener) {
        this.dayListener = listener;
    }

    /** The sources' requests a day from the first day to today, today's last; back at today, no day picked */
    public void setData(long first, List<Series> series) {
        this.first = first;
        this.series = new ArrayList<>(series);
        this.days = 0;
        for (Series one : series) {
            this.days = Math.max(this.days, one.counts.length);
        }
        this.totals = new int[this.days];
        for (Series one : series) {
            for (int i = 0; i < one.counts.length; i++) {
                this.totals[i] += one.counts[i];
            }
        }
        this.picked = -1;
        this.scroller.forceFinished(true);
        this.offset = 0;
        this.scale = 0;
        this.shownFirst = Long.MIN_VALUE;
        invalidate();
    }

    private float plotRight() {
        return getWidth() - getPaddingRight();
    }

    private float maxOffset() {
        float room = plotRight() - getPaddingLeft();
        return Math.max(0, this.days * this.step - room);
    }

    // The left edge of the day's bar, the days counted from the first
    private float barLeft(int day) {
        return plotRight() - this.barWidth - (this.days - 1 - day) * this.step + this.offset;
    }

    // The day whose column is at x, counted from the first; -1 for none
    private int dayAt(float x) {
        if (x < getPaddingLeft() || x > plotRight()) {
            return -1;
        }
        int day = this.days - 1 - Math.round((plotRight() - this.barWidth / 2 + this.offset - x) / this.step);
        return day >= 0 && day < this.days ? day : -1;
    }

    // The day picked, or the one picked let go by a tap on it again or beside the days
    private void pick(int day) {
        this.picked = day == this.picked ? -1 : day;
        invalidate();
        if (this.dayListener != null) {
            this.dayListener.onDayPicked(this.picked < 0 ? -1 : this.first + this.picked);
        }
    }

    private void scrollTo(float offset) {
        float clamped = Math.max(0, Math.min(maxOffset(), offset));
        if (clamped != this.offset) {
            this.offset = clamped;
            invalidate();
        }
    }

    @Override
    public void computeScroll() {
        if (this.scroller.computeScrollOffset()) {
            scrollTo(this.scroller.getCurrX());
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        scrollTo(this.offset);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.days == 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                this.scroller.forceFinished(true);
                this.dragging = false;
                this.downX = event.getX();
                this.downY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                // Sideways the days scroll, when there are more than in sight; up and down the page does
                if (!this.dragging && maxOffset() > 0) {
                    float dx = Math.abs(event.getX() - this.downX);
                    float dy = Math.abs(event.getY() - this.downY);
                    if (dx > this.touchSlop && dx > dy) {
                        this.dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                break;
            default:
                break;
        }
        this.gestures.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.days == 0) {
            return;
        }
        float left = getPaddingLeft();
        float right = plotRight();
        float top = getPaddingTop();
        float base = getHeight() - getPaddingBottom() - this.axisHeight;
        float height = base - top;
        long today = this.first + this.days - 1;

        // The days in sight, and the most requests among them
        int from = this.days - 1;
        int to = 0;
        int most = 0;
        for (int i = 0; i < this.days; i++) {
            float x = barLeft(i);
            if (x + this.barWidth < left || x > right) {
                continue;
            }
            from = Math.min(from, i);
            to = Math.max(to, i);
            most = Math.max(most, this.totals[i]);
        }
        if (from > to) {
            return;
        }
        if (this.first + from != this.shownFirst || this.first + to != this.shownLast) {
            this.shownFirst = this.first + from;
            this.shownLast = this.first + to;
            if (this.rangeListener != null) {
                // Not in the middle of drawing: the listener changes other views' texts
                long shownFirst = this.shownFirst;
                long shownLast = this.shownLast;
                post(() -> this.rangeListener.onRangeChanged(shownFirst, shownLast));
            }
        }
        float target = Math.max(1, most) * HEADROOM;
        if (this.scale <= 0) {
            this.scale = target;
        } else if (Math.abs(target - this.scale) > target * 0.005f) {
            this.scale += (target - this.scale) * 0.25f;
            postInvalidateOnAnimation();
        } else {
            this.scale = target;
        }

        canvas.save();
        canvas.clipRect(left, 0, right, getHeight());
        // The picked day's column lit behind its bar, one without requests too
        if (this.picked >= from && this.picked <= to) {
            float center = barLeft(this.picked) + this.barWidth / 2;
            float radius = 4 * this.density;
            this.rect.set(center - this.step / 2, top, center + this.step / 2, base);
            canvas.drawRoundRect(this.rect, radius, radius, this.lightPaint);
        }
        for (int i = from; i <= to; i++) {
            float x = barLeft(i);
            float y = base;
            // Today's isn't over: the others a little paler beside it; the picked one alone in full when one is
            int alpha = this.picked >= 0 ? (i == this.picked ? 255 : 110) : (i == this.days - 1 ? 255 : 200);
            for (Series one : this.series) {
                if (i >= one.counts.length || one.counts[i] == 0) {
                    continue;
                }
                float h = one.counts[i] / this.scale * height;
                this.barPaint.setColor(one.color);
                this.barPaint.setAlpha(alpha);
                canvas.drawRect(x, y - h, x + this.barWidth, y, this.barPaint);
                y -= h;
            }
        }
        // Today's day ending under its bar, the picked day's under its own, and Mondays under theirs where there is
        // room, all going with the days
        float gap = 6 * this.density;
        float labelY = base + this.axisHeight - 6 * this.density;
        boolean pickedShown = this.picked >= 0 && this.picked < this.days - 1;
        float pickedLeft = 0;
        float pickedRight = 0;
        if (pickedShown) {
            String label = label(this.picked);
            float width = this.pickedPaint.measureText(label);
            pickedLeft = Math.max(left, Math.min(right - width, barLeft(this.picked) + (this.barWidth - width) / 2));
            pickedRight = pickedLeft + width;
            canvas.drawText(label, pickedLeft, labelY, this.pickedPaint);
        }
        String todayLabel = label(this.days - 1);
        Paint todayPaint = this.picked == this.days - 1 ? this.pickedPaint : this.axisPaint;
        float todayRight = barLeft(this.days - 1) + this.barWidth;
        float todayLeft = todayRight - todayPaint.measureText(todayLabel);
        boolean todayShown = !pickedShown || todayLeft >= pickedRight + gap;
        if (todayShown) {
            canvas.drawText(todayLabel, todayLeft, labelY, todayPaint);
        }
        for (int i = from; i < this.days - 1 && i <= to; i++) {
            if (i == this.picked || Math.floorMod(this.first + i + 3, 7) != 0) {
                continue;
            }
            String label = label(i);
            float width = this.axisPaint.measureText(label);
            float labelLeft = barLeft(i) + (this.barWidth - width) / 2;
            boolean clear = labelLeft >= left && (!todayShown || labelLeft + width + gap <= todayLeft)
                    && (!pickedShown || labelLeft + width + gap <= pickedLeft || labelLeft >= pickedRight + gap);
            if (clear) {
                canvas.drawText(label, labelLeft, labelY, this.axisPaint);
            }
        }
        canvas.restore();
        canvas.drawLine(left, base + this.density / 2, right, base + this.density / 2, this.basePaint);
    }

    private String label(int day) {
        return this.axisFormat.format(new Date((this.first + day) * DAY_MS));
    }
}
