package org.meteosphere.ui.astro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EdgeEffect;
import android.widget.Scroller;
import com.google.android.material.color.MaterialColors;

/**
 * The Sun's height over the local day from midnight to midnight: solid and amber over the horizon with the sky
 * under it tinted, dashed under it, the civil twilight band down to -6 degrees, and the Sun where it is now.
 * The moon tab draws the Moon's height with it in silver and without the twilight.
 * On the sun tab it is a strip of days a day wide that is dragged and flung by the day as a pager is, each day with
 * its date at its midnight; the day mostly in the window is told as it changes, while it moves, for the moment to move
 * there. A double tap on the strip is told too, and so is a drag of it let go.
 */
public class SunDayChart extends View {

    /** A strip that doesn't follow on from the one before */
    public static final int NONE = Integer.MIN_VALUE;

    private static final int SUN_COLOR = 0xffffb300;
    private static final float TWILIGHT_ALTITUDE = -6;
    private static final int MINUTES_PER_DAY = 24 * 60;
    // A fling this fast goes on to the next day, and the strip takes at most this long to settle, as a pager does
    private static final int DAY_FLING_DP = 400;
    private static final int MAX_SETTLE_MS = 600;

    /**
     * A day of the strip: its midnight and its HOURS on the clock in minutes from the strip's start, with the date; 23
     * or 25 hours long when the clocks change on it
     */
    public static final class Day {
        public static final int[] HOURS = {6, 12, 18};

        final int start;
        final int[] hours;
        final String date;

        public Day(int start, int[] hours, String date) {
            this.start = start;
            this.hours = hours;
            this.date = date;
        }
    }

    /** Told the day mostly in the window when it becomes another than the one shown, as its index in the days given */
    public interface OnDayListener {
        void onDay(int index);
    }

    private final Paint horizonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint twilightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint();
    private final Path path = new Path();
    private final float density;

    // The altitude in degrees every step minutes from the start: of the day, the last at 24:00, or of the strip
    private float[] altitudes;
    private float step;
    private float maxAltitude;
    private float minAltitude;
    private int nowMinutes;
    private float nowAltitude;
    private boolean hasTwilight = true;

    // The strip: its days, one more for the midnight it ends at, the one shown, the minutes at the left of the window,
    // the start of the day it is settling on, NaN when it isn't, and the minutes at the scroller's 0 meanwhile
    private Day[] days;
    private int shown;
    private float offset;
    private float settling = Float.NaN;
    private float scrollerOrigin;
    private OnDayListener dayListener;
    private Runnable doubleTapListener;
    private Runnable dragEndListener;
    private LinearGradient leftFade;
    private LinearGradient rightFade;

    private final Scroller scroller;
    private final EdgeEffect leftEdge;
    private final EdgeEffect rightEdge;
    private final int touchSlop;
    private final float dayFling;
    private final int maxFling;
    private VelocityTracker velocity;
    private float downX;
    private float downY;
    private float lastX;
    private boolean isDragging;
    // The touch is the second tap of a double one while it stays a tap
    private final GestureDetector doubleTaps;
    private boolean isDoubleTap;

    public SunDayChart(Context context) {
        this(context, null);
    }

    public SunDayChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0xff888888);
        this.horizonPaint.setColor(outline);
        this.horizonPaint.setStrokeWidth(this.density);
        this.dayPaint.setColor(SUN_COLOR);
        this.dayPaint.setStyle(Paint.Style.STROKE);
        this.dayPaint.setStrokeWidth(2.5f * this.density);
        this.dayPaint.setStrokeCap(Paint.Cap.ROUND);
        this.dayPaint.setStrokeJoin(Paint.Join.ROUND);
        this.nightPaint.setColor(outline);
        this.nightPaint.setStyle(Paint.Style.STROKE);
        this.nightPaint.setStrokeWidth(1.5f * this.density);
        this.nightPaint.setPathEffect(new DashPathEffect(new float[]{4 * this.density, 4 * this.density}, 0));
        this.areaPaint.setColor((SUN_COLOR & 0x00ffffff) | 0x2e000000);
        this.twilightPaint.setColor((SUN_COLOR & 0x00ffffff) | 0x14000000);
        this.sunPaint.setColor(SUN_COLOR);
        this.glowPaint.setColor((SUN_COLOR & 0x00ffffff) | 0x40000000);
        this.markPaint.setColor(SUN_COLOR);
        this.textPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff888888));
        this.textPaint.setTextSize(11 * sp);
        this.textPaint.setTextAlign(Paint.Align.CENTER);
        this.fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        // Easing out as a pager's
        this.scroller = new Scroller(context, t -> {
            t -= 1;
            return t * t * t * t * t + 1;
        });
        this.leftEdge = new EdgeEffect(context);
        this.rightEdge = new EdgeEffect(context);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        this.touchSlop = configuration.getScaledTouchSlop();
        this.dayFling = DAY_FLING_DP * this.density;
        this.maxFling = configuration.getScaledMaximumFlingVelocity();
        this.doubleTaps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                isDoubleTap = true;
                return true;
            }
        });
        this.doubleTaps.setIsLongpressEnabled(false);
    }

    /** The color of the curve, the sky under it and the body now, and whether the twilight band is drawn */
    public void setColor(int color, boolean hasTwilight) {
        this.dayPaint.setColor(color);
        this.areaPaint.setColor((color & 0x00ffffff) | 0x2e000000);
        this.sunPaint.setColor(color);
        this.glowPaint.setColor((color & 0x00ffffff) | 0x40000000);
        this.markPaint.setColor(color);
        this.hasTwilight = hasTwilight;
        invalidate();
    }

    /** The colors of the horizon with the dashed night part, and of the hour labels; the theme's by default */
    public void setSurfaceColors(int lineColor, int textColor) {
        this.horizonPaint.setColor(lineColor);
        this.nightPaint.setColor(lineColor);
        this.textPaint.setColor(textColor);
        invalidate();
    }

    /** The altitudes over the day at even steps from 00:00 to 24:00, and now in minutes of the day with its altitude */
    public void setDay(float[] altitudes, int nowMinutes, float nowAltitude) {
        this.days = null;
        this.offset = 0;
        this.scroller.forceFinished(true);
        setAltitudes(altitudes, (float) MINUTES_PER_DAY / (altitudes.length - 1), nowMinutes, nowAltitude);
    }

    public void setOnDayListener(OnDayListener listener) {
        this.dayListener = listener;
    }

    /** Told as the second tap of a double tap on the strip lets go, the strip standing on a day by then */
    public void setOnDoubleTapListener(Runnable listener) {
        this.doubleTapListener = listener;
    }

    /** Told when the finger dragging the strip lets it go */
    public void setOnDragEndListener(Runnable listener) {
        this.dragEndListener = listener;
    }

    /**
     * Makes it the strip: the altitudes every step minutes from the first midnight to the last, the days with one
     * more for the last midnight, the one shown, and now in minutes from the start with its altitude. shift is how
     * many minutes later this strip starts than the one before at the same place: one being dragged or settling stays
     * where it is, else it stands at the day shown; NONE stands it there whatever.
     */
    public void setDays(float[] altitudes, int stepMinutes, Day[] days, int shown, int nowMinutes, float nowAltitude, int shift) {
        boolean isMoving = this.days != null && shift != NONE && (this.isDragging || !this.scroller.isFinished());
        this.days = days;
        this.shown = shown;
        setAltitudes(altitudes, stepMinutes, nowMinutes, nowAltitude);
        if (isMoving) {
            // Moved on as it was, the scroller too
            this.offset = clamp(this.offset - shift);
            this.settling -= shift;
            this.scrollerOrigin -= shift;
            // Gone on past the day shown while it was computed
            followDay();
        } else {
            this.scroller.forceFinished(true);
            this.settling = Float.NaN;
            this.offset = clamp(days[shown].start);
        }
    }

    private void setAltitudes(float[] altitudes, float step, int nowMinutes, float nowAltitude) {
        this.altitudes = altitudes;
        this.step = step;
        this.nowMinutes = nowMinutes;
        this.nowAltitude = nowAltitude;
        float max = 10;
        float min = -10;
        for (float altitude : altitudes) {
            max = Math.max(max, altitude);
            min = Math.min(min, altitude);
        }
        this.maxAltitude = max + 4;
        this.minAltitude = min - 4;
        invalidate();
    }

    private float pad() {
        return 12 * this.density;
    }

    private float minutesPerPixel() {
        return MINUTES_PER_DAY / (getWidth() - 2 * pad());
    }

    private float x(float minutes) {
        return pad() + (minutes - this.offset) / minutesPerPixel();
    }

    private float y(float altitude) {
        float top = 4 * this.density;
        float bottom = getHeight() - 20 * this.density;
        return top + (bottom - top) * (this.maxAltitude - altitude) / (this.maxAltitude - this.minAltitude);
    }

    private float minutesOf(int index) {
        return this.step * index;
    }

    // From the first day's start to the last one's
    private float minOffset() {
        return this.days[0].start;
    }

    private float maxOffset() {
        return this.days[this.days.length - 2].start;
    }

    private float clamp(float offset) {
        return Math.max(minOffset(), Math.min(maxOffset(), offset));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.leftFade = new LinearGradient(0, 0, pad(), 0, 0xff000000, 0x00000000, Shader.TileMode.CLAMP);
        this.rightFade = new LinearGradient(w - pad(), 0, w, 0, 0x00000000, 0xff000000, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.altitudes == null || this.altitudes.length < 2) {
            return;
        }
        boolean isStrip = this.days != null;
        // The strip fades out at the sides
        int layer = isStrip ? canvas.saveLayer(0, 0, getWidth(), getHeight(), null) : 0;
        // The steps in the window and one more on each side; all of the day
        int first = 0;
        int last = this.altitudes.length - 1;
        if (isStrip) {
            float margin = pad() * minutesPerPixel() + this.step;
            first = Math.max(0, (int) Math.floor((this.offset - margin) / this.step));
            last = Math.min(last, (int) Math.ceil((this.offset + MINUTES_PER_DAY + margin) / this.step));
        }
        float horizon = y(0);
        if (this.hasTwilight) {
            canvas.drawRect(x(minutesOf(first)), horizon, x(minutesOf(last)), y(TWILIGHT_ALTITUDE), this.twilightPaint);
        }

        // The sky under the curve while the Sun is up
        this.path.reset();
        this.path.moveTo(x(minutesOf(first)), horizon);
        for (int i = first; i <= last; i++) {
            this.path.lineTo(x(minutesOf(i)), y(Math.max(0, this.altitudes[i])));
        }
        this.path.lineTo(x(minutesOf(last)), horizon);
        this.path.close();
        canvas.drawPath(this.path, this.areaPaint);

        drawCurve(canvas, true, first, last);
        drawCurve(canvas, false, first, last);
        // On the strip its sides are drawn past the fading
        canvas.drawLine(isStrip ? pad() : 0, horizon, isStrip ? getWidth() - pad() : getWidth(), horizon, this.horizonPaint);

        // Sunrise and sunset where the curve crosses the horizon
        for (int i = first + 1; i <= last; i++) {
            float a0 = this.altitudes[i - 1];
            float a1 = this.altitudes[i];
            if ((a0 > 0) != (a1 > 0)) {
                float minutes = minutesOf(i - 1) + (minutesOf(i) - minutesOf(i - 1)) * a0 / (a0 - a1);
                canvas.drawCircle(x(minutes), horizon, 3.5f * this.density, this.markPaint);
            }
        }
        if (!isStrip) {
            for (int hour = 0; hour <= 24; hour += 6) {
                canvas.drawText(String.valueOf(hour), x(hour * 60), getHeight() - 5 * this.density, this.textPaint);
            }
        }

        float sunX = x(this.nowMinutes);
        float sunY = y(this.nowAltitude);
        boolean isUp = this.nowAltitude > 0;
        this.sunPaint.setAlpha(isUp ? 255 : 120);
        if (isUp) {
            canvas.drawCircle(sunX, sunY, 13 * this.density, this.glowPaint);
        }
        canvas.drawCircle(sunX, sunY, 8 * this.density, this.sunPaint);

        if (isStrip) {
            this.fadePaint.setShader(this.leftFade);
            canvas.drawRect(0, 0, pad(), getHeight(), this.fadePaint);
            this.fadePaint.setShader(this.rightFade);
            canvas.drawRect(getWidth() - pad(), 0, getWidth(), getHeight(), this.fadePaint);
            canvas.restoreToCount(layer);
            // The horizon stays, from side to side as on a single day
            canvas.drawLine(0, horizon, pad(), horizon, this.horizonPaint);
            canvas.drawLine(getWidth() - pad(), horizon, getWidth(), horizon, this.horizonPaint);
            drawHours(canvas);
            drawEdges(canvas);
        }
    }

    // The parts of the curve over the horizon solid, or under it dashed, split where it crosses
    private void drawCurve(Canvas canvas, boolean over, int first, int last) {
        this.path.reset();
        boolean open = false;
        for (int i = first; i <= last; i++) {
            float altitude = this.altitudes[i];
            float x = x(minutesOf(i));
            boolean inside = over ? altitude >= 0 : altitude <= 0;
            if (i > first) {
                float previous = this.altitudes[i - 1];
                if ((previous > 0) != (altitude > 0)) {
                    // Through the crossing, so both parts meet on the horizon
                    float minutes = minutesOf(i - 1) + (minutesOf(i) - minutesOf(i - 1)) * previous / (previous - altitude);
                    if (open) {
                        this.path.lineTo(x(minutes), y(0));
                        open = false;
                    } else {
                        this.path.moveTo(x(minutes), y(0));
                        open = true;
                    }
                }
            }
            if (inside) {
                if (open) {
                    this.path.lineTo(x, y(altitude));
                } else {
                    this.path.moveTo(x, y(altitude));
                    open = true;
                }
            } else {
                open = false;
            }
        }
        canvas.drawPath(this.path, over ? this.dayPaint : this.nightPaint);
    }

    // The hours of each day and its date at midnight, pushed in when they would be cut at the sides. A day longer than
    // the window, when the clocks go back, ends past its side: the next date is pushed in from as far
    private void drawHours(Canvas canvas) {
        float baseline = getHeight() - 5 * this.density;
        for (int i = 0; i < this.days.length; i++) {
            Day day = this.days[i];
            int longer = i == 0 ? 0 : Math.max(0, day.start - this.days[i - 1].start - MINUTES_PER_DAY);
            drawLabel(canvas, day.date, x(day.start), longer / minutesPerPixel(), baseline);
            for (int h = 0; h < Day.HOURS.length; h++) {
                drawLabel(canvas, String.valueOf(Day.HOURS[h]), x(day.hours[h]), 0, baseline);
            }
        }
    }

    // Unless it is off the view, or further past its right side than beyond
    private void drawLabel(Canvas canvas, String text, float x, float beyond, float baseline) {
        if (x < 0 || x > getWidth() + beyond) {
            return;
        }
        float half = this.textPaint.measureText(text) / 2;
        canvas.drawText(text, Math.max(half, Math.min(getWidth() - half, x)), baseline, this.textPaint);
    }

    private void drawEdges(Canvas canvas) {
        if (!this.leftEdge.isFinished()) {
            int save = canvas.save();
            canvas.rotate(270);
            canvas.translate(-getHeight(), 0);
            this.leftEdge.setSize(getHeight(), getWidth());
            if (this.leftEdge.draw(canvas)) {
                postInvalidateOnAnimation();
            }
            canvas.restoreToCount(save);
        }
        if (!this.rightEdge.isFinished()) {
            int save = canvas.save();
            canvas.rotate(90);
            canvas.translate(0, -getWidth());
            this.rightEdge.setSize(getHeight(), getWidth());
            if (this.rightEdge.draw(canvas)) {
                postInvalidateOnAnimation();
            }
            canvas.restoreToCount(save);
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (this.days == null) {
            return false;
        }
        return direction < 0 ? this.offset > minOffset() : this.offset < maxOffset();
    }

    // A horizontal drag moves the strip and flings it on by the day; a vertical one goes to the scrolling list, and the
    // pager doesn't take it meanwhile
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.days == null) {
            return super.onTouchEvent(event);
        }
        if (this.velocity == null) {
            this.velocity = VelocityTracker.obtain();
        }
        this.velocity.addMovement(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            this.isDoubleTap = false;
        }
        this.doubleTaps.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Caught on its way to a day, as a pager is
                this.scroller.forceFinished(true);
                this.downX = event.getX();
                this.downY = event.getY();
                this.lastX = this.downX;
                this.isDragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!this.isDragging) {
                    float dx = Math.abs(event.getX() - this.downX);
                    float dy = Math.abs(event.getY() - this.downY);
                    if (dy > this.touchSlop && dy > dx) {
                        this.isDoubleTap = false;
                        getParent().requestDisallowInterceptTouchEvent(false);
                        endDrag(0);
                        return false;
                    }
                    if (dx <= this.touchSlop) {
                        return true;
                    }
                    this.isDragging = true;
                    this.isDoubleTap = false;
                    this.lastX = event.getX();
                }
                scrollBy(this.lastX - event.getX(), event.getY());
                this.lastX = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (this.isDoubleTap) {
                    // Stood at once on the day it was settling on after the first tap rather than settled, for the
                    // strip that comes of the double tap to find it still
                    endTouch();
                    if (!Float.isNaN(this.settling)) {
                        this.offset = this.settling;
                        this.settling = Float.NaN;
                        followDay();
                        invalidate();
                    }
                    if (this.doubleTapListener != null) {
                        this.doubleTapListener.run();
                    }
                    return true;
                }
                boolean wasDragged = this.isDragging;
                float speed = 0;
                if (wasDragged) {
                    this.velocity.computeCurrentVelocity(1000, this.maxFling);
                    speed = -this.velocity.getXVelocity();
                }
                endDrag(speed);
                if (wasDragged && this.dragEndListener != null) {
                    this.dragEndListener.run();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                endDrag(0);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // Two fingers make no tap
                this.isDoubleTap = false;
                return true;
            default:
                return true;
        }
    }

    private void endTouch() {
        this.isDragging = false;
        if (this.velocity != null) {
            this.velocity.recycle();
            this.velocity = null;
        }
        this.leftEdge.onRelease();
        this.rightEdge.onRelease();
    }

    // Let go at a speed, in pixels a second to the later days: on to a day
    private void endDrag(float speed) {
        endTouch();
        settle(dayFor(speed), Math.abs(speed));
    }

    // The start of the day to settle on: flung, the next one in its way, else the one mostly in the window
    private float dayFor(float speed) {
        int day = 0;
        while (day < this.days.length - 2 && this.days[day + 1].start <= this.offset) {
            day++;
        }
        float from = this.days[day].start;
        float fraction = (this.offset - from) / (this.days[day + 1].start - from);
        if (Math.abs(speed) > this.dayFling ? speed > 0 : fraction > 0.5f) {
            day++;
        }
        return clamp(this.days[Math.min(day, this.days.length - 2)].start);
    }

    // Past an end the edge stretches instead
    private void scrollBy(float pixels, float touchY) {
        float target = this.offset + pixels * minutesPerPixel();
        float over = (target - clamp(target)) / minutesPerPixel();
        if (over < 0) {
            this.leftEdge.onPull(-over / getWidth(), 1 - touchY / getHeight());
        } else if (over > 0) {
            this.rightEdge.onPull(over / getWidth(), touchY / getHeight());
        } else {
            this.leftEdge.onRelease();
            this.rightEdge.onRelease();
        }
        this.offset = clamp(target);
        followDay();
        postInvalidateOnAnimation();
    }

    // On to the day starting at target, in about the time the speed would take, as a pager goes
    private void settle(float target, float speed) {
        float minutesPerPixel = minutesPerPixel();
        int from = Math.round(this.offset / minutesPerPixel);
        int distance = Math.round(target / minutesPerPixel) - from;
        int duration = speed > 0 ? Math.round(4000 * Math.abs(distance) / speed)
                : Math.round((Math.abs(distance) / (float) getWidth() + 1) * 100);
        this.settling = target;
        this.scrollerOrigin = 0;
        this.scroller.startScroll(from, 0, distance, 0, Math.min(duration, MAX_SETTLE_MS));
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (!this.scroller.computeScrollOffset()) {
            return;
        }
        if (!this.scroller.isFinished()) {
            this.offset = clamp(this.scrollerOrigin + this.scroller.getCurrX() * minutesPerPixel());
        } else {
            this.offset = this.settling;
            this.settling = Float.NaN;
        }
        followDay();
        postInvalidateOnAnimation();
    }

    // The day mostly in the window, told when it isn't the one shown
    private void followDay() {
        int day = 0;
        while (day < this.days.length - 2 && (this.days[day].start + this.days[day + 1].start) / 2f < this.offset) {
            day++;
        }
        if (day != this.shown) {
            this.shown = day;
            if (this.dayListener != null) {
                this.dayListener.onDay(day);
            }
        }
    }
}
