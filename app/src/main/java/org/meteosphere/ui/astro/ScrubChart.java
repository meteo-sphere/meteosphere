package org.meteosphere.ui.astro;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * A chart of days a finger picks one of, as the Sun's year and the Moon's month: a horizontal drag goes through the
 * days, a vertical one goes to the scrolling list, and the pager doesn't take it meanwhile
 */
public abstract class ScrubChart extends View {

    /** Told the day picked, counted from the first */
    public interface OnDaySelectedListener {
        void onDaySelected(int day);
    }

    private final int touchSlop;
    private int selected;
    private long shownFirstDay = Long.MIN_VALUE;
    private OnDaySelectedListener listener;
    private float downX;
    private float downY;
    private boolean isScrubbing;

    protected ScrubChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /** How many days are shown, 0 before they are */
    protected abstract int dayCount();

    /** The day under the finger, counted from the first, any number: it is kept to the days shown */
    protected abstract int dayAt(float x);

    /** The days shown now start at firstDay: the day picked stays when they are the same, like at each new minute */
    protected void showDays(long firstDay) {
        if (firstDay != this.shownFirstDay || this.selected >= dayCount()) {
            this.selected = 0;
        }
        this.shownFirstDay = firstDay;
    }

    /** The day picked, counted from the first */
    public int getSelected() {
        return this.selected;
    }

    public void setOnDaySelectedListener(OnDaySelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (dayCount() == 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                this.downX = event.getX();
                this.downY = event.getY();
                this.isScrubbing = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                select(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!this.isScrubbing) {
                    float dx = Math.abs(event.getX() - this.downX);
                    float dy = Math.abs(event.getY() - this.downY);
                    if (dy > this.touchSlop && dy > dx) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                        return false;
                    }
                    this.isScrubbing = dx > this.touchSlop;
                }
                select(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    private void select(float x) {
        int day = Math.max(0, Math.min(dayCount() - 1, dayAt(x)));
        if (day != this.selected) {
            this.selected = day;
            invalidate();
            if (this.listener != null) {
                this.listener.onDaySelected(day);
            }
        }
    }
}
