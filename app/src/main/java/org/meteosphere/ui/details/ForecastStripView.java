package org.meteosphere.ui.details;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.OverScroller;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import org.meteosphere.ui.astro.MoonPhaseDrawable;
import org.meteosphere.R;
import org.meteosphere.ui.chart.ForecastCurve;
import org.meteosphere.ui.chart.PrecipitationDrop;
import org.meteosphere.ui.chart.WindArrow;

/**
 * The forecast as columns scrolled sideways inside a card: a label, the weather icon, the temperature on its point of
 * a smooth curve, then a row per weather parameter with its name pinned on the left. Hours or days; sunrise and sunset
 * can sit between the hours as columns of their own.
 *
 * One view draws the whole strip, and only the columns on screen, from data prepared off the main thread: scrolling
 * redraws a few dozen texts and bitmaps, nothing is measured, laid out or allocated.
 */
public class ForecastStripView extends View {

    public static final int EVENT_NONE = 0;
    public static final int EVENT_SUNRISE = 1;
    public static final int EVENT_SUNSET = 2;

    private static final int COLUMNS_ON_SCREEN = 6;
    private static final float EVENT_WIDTH = 0.85f;
    private static final int EVENT_TEXT_COLOR = 0xffffd27a;
    private static final int DIM_TEXT = 0xb8ffffff;
    private static final int FAINT_TEXT = 0x73ffffff;

    /** A value in a parameter row */
    public static final class Cell {
        public String text;
        // A second line under it, the day's minimum
        public String text2;
        public int color;
        // The second line's colour, 0 for the dim one
        public int color2;
        // Degrees of an arrow drawn before the text, NaN for none
        public float arrow = Float.NaN;
        // How full a precipitation drop drawn before the text is, 0 to 1, NaN for none
        public float drop = Float.NaN;
        // The moon of this age drawn above the text, NaN for none
        public double moonAge = Double.NaN;
        public boolean moonRing;
        public boolean faint;
    }

    public static final class Column {
        // The day over the hours, on a line of its own; null without the line
        public String day;
        // A shorter name of the day for when its columns are narrower than day, like "Today" late in the evening;
        // null for none
        public String dayShort;
        public String label;
        public boolean strongLabel;
        public Drawable icon;
        public String value;
        public String value2;
        public float curve;
        public float curve2 = Float.NaN;
        // The colours of the curves' temperatures at this column, as the widgets have them
        public int color;
        public int color2;
        public int event = EVENT_NONE;
        public String eventText;
        public Cell[] cells;
        // When the column is, its hour's or its day's start or the sun's event, rising along the strip: an update
        // keeps the column at the left edge where it stands. 0 for none
        public long time;
    }

    public static final class Data {
        public final ArrayList<Column> columns = new ArrayList<>();
        public final ArrayList<String> rowLabels = new ArrayList<>();
        // The days show a minimum under each maximum
        public boolean hasSecondValue;
        public boolean southernHemisphere;
        // Rows whose cells have two lines, rows with a moon, the range of the curves
        boolean[] twoLineRows;
        boolean[] moonRows;
        float curveMin;
        float curveMax;
    }

    private final float density;
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strongLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cell2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curve2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final WindArrow windArrow = new WindArrow();
    private final PrecipitationDrop drop;
    // From a value's baseline to the middle of its digits, up
    private final float digitsMiddle;
    // The height of the lows' digits, and how far the temperatures stand off their points
    private final float value2Digits;
    private final float valueGap;
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private final Path gapPath = new Path();
    private final Rect iconRect = new Rect();

    private final MoonPhaseDrawable.MoonPainter moonPainter = new MoonPhaseDrawable.MoonPainter();
    private final Paint moonRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable sunriseIcon;
    private final Drawable sunsetIcon;

    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;
    private VelocityTracker velocityTracker;
    private float downX;
    private float downY;
    private float lastX;
    private boolean dragging;

    private Data data;
    // Layout of the data for the current width
    private float[] columnX;
    private float[] columnWidth;
    private float contentWidth;
    private float columnUnit;
    // The points of the curves: the columns' middles, the values' heights
    private float[] pointX;
    private float[] pointY;
    private float[] pointY2;

    // The day line over the columns, 0 high without it, and its days: each column's day's last column, the days' widths
    private float dayLineHeight;
    private final float dayBaseline;
    // The day's name kept at the left edge stands level with the summary over the strip
    private final float dayInset;
    private int[] dayEnd;
    private float[] dayWidth;
    private float[] dayShortWidth;

    // Vertical layout, from the top, under the day line
    private final float labelBaseline;
    private final float iconTop;
    private final float iconHeight;
    // The weather's icons a little larger than the sunrise's and the sunset's, about the same middle
    private final float weatherIconTop;
    private final float weatherIconHeight;
    private final float valueBaseline;
    private final float curveTop;
    private float curveHeight;
    private float rowsTop;
    private final float rowLabelHeight;
    private final float rowValueHeight;
    private final float rowSecondLineHeight;
    private final float moonSize;
    // Between a row's label and its moons, so today's ring keeps off the label
    private final float moonGap;
    private float totalHeight;

    public ForecastStripView(Context context) {
        this(context, null);
    }

    public ForecastStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        this.labelPaint.setColor(DIM_TEXT);
        this.labelPaint.setTextSize(13 * sp);
        this.labelPaint.setTextAlign(Paint.Align.CENTER);
        this.strongLabelPaint.set(this.labelPaint);
        this.strongLabelPaint.setColor(0xffffffff);
        this.strongLabelPaint.setTypeface(medium);
        this.valuePaint.setColor(0xffffffff);
        this.valuePaint.setTextSize(17 * sp);
        this.valuePaint.setTypeface(medium);
        this.valuePaint.setTextAlign(Paint.Align.CENTER);
        this.value2Paint.setColor(DIM_TEXT);
        this.value2Paint.setTextSize(15 * sp);
        this.value2Paint.setTextAlign(Paint.Align.CENTER);
        Rect digits = new Rect();
        this.value2Paint.getTextBounds("0", 0, 1, digits);
        this.value2Digits = -digits.top;
        this.valueGap = 6.5f * this.density;
        this.eventPaint.setColor(EVENT_TEXT_COLOR);
        this.eventPaint.setTextSize(13 * sp);
        this.eventPaint.setTextAlign(Paint.Align.CENTER);
        this.rowLabelPaint.setColor(FAINT_TEXT);
        this.rowLabelPaint.setTextSize(12 * sp);
        this.cellPaint.setColor(0xffffffff);
        this.cellPaint.setTextSize(15 * sp);
        this.cellPaint.setTextAlign(Paint.Align.CENTER);
        this.cellPaint.getTextBounds("0", 0, 1, digits);
        this.digitsMiddle = -(digits.top + digits.bottom) / 2f;
        this.drop = new PrecipitationDrop(this.density);
        this.cell2Paint.set(this.cellPaint);
        this.cell2Paint.setColor(DIM_TEXT);
        this.cell2Paint.setTextSize(13 * sp);
        this.curvePaint.setStyle(Paint.Style.STROKE);
        this.curvePaint.setStrokeWidth(2.5f * this.density);
        this.curvePaint.setStrokeCap(Paint.Cap.ROUND);
        this.curvePaint.setStrokeJoin(Paint.Join.ROUND);
        this.curve2Paint.set(this.curvePaint);
        this.curve2Paint.setStrokeWidth(2 * this.density);
        this.dotPaint.setColor(0xffffffff);
        this.dividerPaint.setColor(0x24ffffff);
        this.dividerPaint.setStrokeWidth(Math.max(1, this.density * 0.75f));
        this.moonPainter.setColors(0x4dffffff, 0xffeef2f6);
        this.moonRingPaint.setStyle(Paint.Style.STROKE);
        this.moonRingPaint.setStrokeWidth(1.5f * this.density);
        this.moonRingPaint.setColor(0xffffd54f);
        this.sunriseIcon = ContextCompat.getDrawable(context, R.drawable.ic_astro_sunrise).mutate();
        this.sunsetIcon = ContextCompat.getDrawable(context, R.drawable.ic_astro_sunset).mutate();
        this.sunriseIcon.setTint(EVENT_TEXT_COLOR);
        this.sunsetIcon.setTint(0xffff9e80);

        this.dayPaint.setColor(DIM_TEXT);
        this.dayPaint.setTextSize(13 * sp);
        this.dayPaint.setTypeface(medium);
        this.dayBaseline = 20 * this.density;
        this.dayInset = context.getResources().getDimension(R.dimen.details_card_text_inset);
        this.labelBaseline = 22 * this.density;
        this.iconTop = 32 * this.density;
        this.iconHeight = 28 * this.density;
        this.weatherIconTop = 29 * this.density;
        this.weatherIconHeight = 34 * this.density;
        this.valueBaseline = 86 * this.density;
        this.curveTop = 96 * this.density;
        this.rowLabelHeight = 20 * this.density;
        this.rowValueHeight = 26 * this.density;
        this.rowSecondLineHeight = 18 * this.density;
        this.moonSize = 20 * this.density;
        this.moonGap = 8 * this.density;

        this.scroller = new OverScroller(context);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        this.touchSlop = configuration.getScaledTouchSlop();
        this.minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        this.maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        setHorizontalScrollBarEnabled(false);
    }

    /** Shows the data, keeping the scroll position when only the values changed */
    public void setData(Data data) {
        // The column at the left edge and how far into it, to stand there again whatever columns come and go
        long anchorTime = 0;
        float anchorOffset = 0;
        int scrollX = getScrollX();
        if (this.data != null && this.columnX != null && scrollX > 0) {
            int i = 0;
            while (i + 1 < this.columnX.length && this.columnX[i + 1] <= scrollX) {
                i++;
            }
            anchorTime = this.data.columns.get(i).time;
            anchorOffset = Math.min(scrollX - this.columnX[i], this.columnWidth[i]);
        }
        this.data = data;
        if (data != null) {
            int rows = data.rowLabels.size();
            data.twoLineRows = new boolean[rows];
            data.moonRows = new boolean[rows];
            data.curveMin = Float.MAX_VALUE;
            data.curveMax = -Float.MAX_VALUE;
            for (Column column : data.columns) {
                data.curveMin = Math.min(data.curveMin, column.curve);
                data.curveMax = Math.max(data.curveMax, column.curve);
                if (!Float.isNaN(column.curve2)) {
                    data.curveMin = Math.min(data.curveMin, column.curve2);
                    data.curveMax = Math.max(data.curveMax, column.curve2);
                }
                if (column.cells == null) {
                    continue;
                }
                for (int r = 0; r < rows && r < column.cells.length; r++) {
                    Cell cell = column.cells[r];
                    if (cell != null && cell.text2 != null) {
                        data.twoLineRows[r] = true;
                    }
                    if (cell != null && !Double.isNaN(cell.moonAge)) {
                        data.moonRows[r] = true;
                    }
                }
            }
        }
        computeDays();
        computeVertical();
        computeColumns();
        int x = scrollX;
        if (anchorTime != 0 && data != null && this.columnX != null) {
            int j = 0;
            while (j < data.columns.size() && data.columns.get(j).time < anchorTime) {
                j++;
            }
            x = j < data.columns.size() ? Math.round(this.columnX[j] + Math.min(anchorOffset, this.columnWidth[j])) : maxScroll();
        }
        if (x != scrollX) {
            // A fling under way would carry on from where the strip stood
            this.scroller.forceFinished(true);
        }
        scrollTo(x, 0);
        requestLayout();
        invalidate();
    }

    // The runs of columns of the same day, measured once
    private void computeDays() {
        this.dayEnd = null;
        this.dayWidth = null;
        this.dayShortWidth = null;
        this.dayLineHeight = 0;
        if (this.data == null || this.data.columns.isEmpty() || this.data.columns.get(0).day == null) {
            return;
        }
        int count = this.data.columns.size();
        this.dayEnd = new int[count];
        this.dayWidth = new float[count];
        this.dayShortWidth = new float[count];
        for (int start = 0; start < count; ) {
            String day = this.data.columns.get(start).day;
            int end = start;
            while (end + 1 < count && day.equals(this.data.columns.get(end + 1).day)) {
                end++;
            }
            for (int i = start; i <= end; i++) {
                this.dayEnd[i] = end;
            }
            this.dayWidth[start] = this.dayPaint.measureText(day);
            String dayShort = this.data.columns.get(start).dayShort;
            this.dayShortWidth[start] = dayShort != null ? this.dayPaint.measureText(dayShort) : 0;
            start = end + 1;
        }
        this.dayLineHeight = 26 * this.density;
    }

    private void computeVertical() {
        boolean hasSecond = this.data != null && this.data.hasSecondValue;
        this.curveHeight = (hasSecond ? 66 : 50) * this.density;
        float y = this.curveTop + this.curveHeight;
        if (hasSecond) {
            // Room for the lows hanging under their points
            y += 22 * this.density;
        }
        this.rowsTop = y + 6 * this.density;
        y = this.rowsTop;
        if (this.data != null) {
            for (int r = 0; r < this.data.rowLabels.size(); r++) {
                y += rowHeight(r);
            }
        }
        this.totalHeight = this.dayLineHeight + y + 6 * this.density;
    }

    private float rowHeight(int row) {
        float height = this.rowLabelHeight + this.rowValueHeight;
        if (this.data.twoLineRows[row]) {
            height += this.rowSecondLineHeight;
        }
        if (this.data.moonRows[row]) {
            height += this.moonGap + this.moonSize + 4 * this.density;
        }
        return height;
    }

    private void computeColumns() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (this.data == null || width <= 0) {
            this.columnX = null;
            this.contentWidth = 0;
            return;
        }
        this.columnUnit = width / (float) COLUMNS_ON_SCREEN;
        int count = this.data.columns.size();
        this.columnX = new float[count];
        this.columnWidth = new float[count];
        float x = getPaddingLeft();
        for (int i = 0; i < count; i++) {
            float columnWidth = this.columnUnit * (this.data.columns.get(i).event != EVENT_NONE ? EVENT_WIDTH : 1);
            this.columnX[i] = x;
            this.columnWidth[i] = columnWidth;
            x += columnWidth;
        }
        this.contentWidth = x + getPaddingRight();
        this.pointX = new float[count];
        this.pointY = new float[count];
        this.pointY2 = new float[count];
        for (int i = 0; i < count; i++) {
            Column column = this.data.columns.get(i);
            this.pointX[i] = centerX(i);
            this.pointY[i] = curveY(column.curve, this.data.curveMin, this.data.curveMax);
            this.pointY2[i] = curveY(column.curve2, this.data.curveMin, this.data.curveMax);
        }
        // A soft fill under the curve, fading down; vertical only, so it doesn't depend on the scroll
        this.fillPaint.setShader(ForecastCurve.fillShader(this.curveTop, this.curveTop + this.curveHeight, 0x24ffffff));
        // Along the curves each column in the colour of its temperature, made once for the whole width
        if (count > 1) {
            this.curvePaint.setShader(curveShader(false));
            this.curve2Paint.setShader(this.data.hasSecondValue ? curveShader(true) : null);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        computeVertical();
        setMeasuredDimension(width, Math.round(this.totalHeight));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeColumns();
        scrollTo(getScrollX(), 0);
    }

    private float centerX(int i) {
        return this.columnX[i] + this.columnWidth[i] / 2;
    }

    private LinearGradient curveShader(boolean second) {
        int count = this.data.columns.size();
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            Column column = this.data.columns.get(i);
            colors[i] = second ? ForecastCurve.low(column.color2) : column.color;
        }
        return ForecastCurve.colorShader(this.pointX, colors);
    }

    private float curveY(float value, float min, float max) {
        float top = this.curveTop + 8 * this.density;
        float bottom = this.curveTop + this.curveHeight - 8 * this.density;
        if (max - min < 0.001f) {
            return (top + bottom) / 2;
        }
        return bottom - (value - min) / (max - min) * (bottom - top);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.data == null || this.columnX == null || this.data.columns.isEmpty()) {
            return;
        }
        int count = this.data.columns.size();
        float left = getScrollX();
        float right = left + getWidth();
        int first = 0;
        while (first < count - 1 && this.columnX[first] + this.columnWidth[first] < left) {
            first++;
        }
        int last = first;
        while (last < count - 1 && this.columnX[last + 1] < right) {
            last++;
        }

        drawDays(canvas, first, last, left, right);
        canvas.save();
        canvas.translate(0, this.dayLineHeight);
        drawCurves(canvas, Math.max(0, first - 1), Math.min(count - 1, last + 1));

        float iconSize = this.iconHeight;
        for (int i = first; i <= last; i++) {
            Column column = this.data.columns.get(i);
            float cx = centerX(i);
            if (column.event != EVENT_NONE) {
                canvas.drawText(column.label, cx, this.labelBaseline, this.eventPaint);
                Drawable icon = column.event == EVENT_SUNRISE ? this.sunriseIcon : this.sunsetIcon;
                int size = Math.round(iconSize);
                icon.setBounds(Math.round(cx - size / 2f), Math.round(this.iconTop), Math.round(cx + size / 2f), Math.round(this.iconTop + size));
                icon.draw(canvas);
                if (column.eventText != null) {
                    canvas.drawText(column.eventText, cx, this.valueBaseline - 3 * this.density, this.eventPaint);
                }
                continue;
            }
            canvas.drawText(column.label, cx, this.labelBaseline, column.strongLabel ? this.strongLabelPaint : this.labelPaint);
            if (column.icon != null) {
                float scale = this.weatherIconHeight / column.icon.getIntrinsicHeight();
                float iconWidth = column.icon.getIntrinsicWidth() * scale;
                if (iconWidth > this.columnWidth[i] * 0.72f) {
                    iconWidth = this.columnWidth[i] * 0.72f;
                    scale = iconWidth / column.icon.getIntrinsicWidth();
                }
                float iconH = column.icon.getIntrinsicHeight() * scale;
                this.iconRect.set(Math.round(cx - iconWidth / 2), Math.round(this.weatherIconTop + (this.weatherIconHeight - iconH) / 2),
                        Math.round(cx + iconWidth / 2), Math.round(this.weatherIconTop + (this.weatherIconHeight + iconH) / 2));
                column.icon.setBounds(this.iconRect);
                column.icon.draw(canvas);
            }
            // The temperature standing on its point, the low hanging under its own
            float y = this.pointY[i];
            if (column.value != null) {
                canvas.drawText(column.value, cx, y - dotRadius(i, false) - this.valueGap, this.valuePaint);
            }
            this.dotPaint.setColor(column.color);
            canvas.drawCircle(cx, y, dotRadius(i, false), this.dotPaint);
            if (!Float.isNaN(column.curve2)) {
                float y2 = this.pointY2[i];
                if (this.data.hasSecondValue && column.value2 != null) {
                    canvas.drawText(column.value2, cx, y2 + dotRadius(i, true) + this.valueGap + this.value2Digits, this.value2Paint);
                }
                this.dotPaint.setColor(column.color2);
                canvas.drawCircle(cx, y2, dotRadius(i, true), this.dotPaint);
            }
        }

        drawRows(canvas, first, last, left);
        canvas.restore();
    }

    // Each day's name at its first column, the shown one's kept at the left edge until the next day's pushes it out
    private void drawDays(Canvas canvas, int first, int last, float left, float right) {
        if (this.dayEnd == null) {
            return;
        }
        float pad = this.dayInset;
        for (int start = first; start <= last; start = this.dayEnd[start] + 1) {
            int dayStart = start;
            while (dayStart > 0 && this.dayEnd[dayStart - 1] == this.dayEnd[start]) {
                dayStart--;
            }
            int end = this.dayEnd[start];
            float dayLeft = this.columnX[dayStart];
            float dayRight = this.columnX[end] + this.columnWidth[end];
            Column column = this.data.columns.get(dayStart);
            String day = column.day;
            float width = this.dayWidth[dayStart];
            // The whole name doesn't fit over the day's columns: the short one, which then isn't cut at the edge
            if (column.dayShort != null && width > dayRight - dayLeft - 12 * this.density) {
                day = column.dayShort;
                width = this.dayShortWidth[dayStart];
            }
            float x = Math.max(dayLeft + 4 * this.density, left + pad);
            x = Math.min(x, dayRight - width - 8 * this.density);
            canvas.drawText(day, x, this.dayBaseline, this.dayPaint);
        }
    }

    // The now's point larger, the lows' smaller
    private float dotRadius(int i, boolean second) {
        return (second ? 2 : i == 0 ? 3.5f : 2.5f) * this.density;
    }

    // The curves through the columns' values as smooth cubic segments in the colours of the temperatures, with a gap
    // cut around each point drawn on them
    private void drawCurves(Canvas canvas, int from, int to) {
        if (to <= from) {
            return;
        }
        // The fill under the main curve
        this.fillPath.reset();
        ForecastCurve.appendFill(this.fillPath, this.pointX, this.pointY, from, to, this.curveTop + this.curveHeight);
        canvas.drawPath(this.fillPath, this.fillPaint);

        // The gaps around the points, clipped out of the curves alone
        float gap = 2 * this.density;
        this.gapPath.reset();
        for (int i = from; i <= to; i++) {
            Column column = this.data.columns.get(i);
            if (column.event != EVENT_NONE) {
                continue;
            }
            this.gapPath.addCircle(this.pointX[i], this.pointY[i], dotRadius(i, false) + gap, Path.Direction.CW);
            if (!Float.isNaN(column.curve2)) {
                this.gapPath.addCircle(this.pointX[i], this.pointY2[i], dotRadius(i, true) + gap, Path.Direction.CW);
            }
        }
        int saved = canvas.save();
        if (Build.VERSION.SDK_INT >= 26) {
            canvas.clipOutPath(this.gapPath);
        } else {
            canvas.clipPath(this.gapPath, Region.Op.DIFFERENCE);
        }
        if (this.data.hasSecondValue) {
            this.path.reset();
            this.path.moveTo(this.pointX[from], this.pointY2[from]);
            ForecastCurve.append(this.path, this.pointX, this.pointY2, from, to);
            canvas.drawPath(this.path, this.curve2Paint);
        }
        this.path.reset();
        this.path.moveTo(this.pointX[from], this.pointY[from]);
        ForecastCurve.append(this.path, this.pointX, this.pointY, from, to);
        canvas.drawPath(this.path, this.curvePaint);
        canvas.restoreToCount(saved);
    }

    private void drawRows(Canvas canvas, int first, int last, float left) {
        int rows = this.data.rowLabels.size();
        if (rows == 0) {
            return;
        }
        canvas.drawLine(left, this.rowsTop - 3 * this.density, left + getWidth(), this.rowsTop - 3 * this.density, this.dividerPaint);
        Paint.FontMetrics metrics = this.cellPaint.getFontMetrics();
        float y = this.rowsTop;
        for (int r = 0; r < rows; r++) {
            // The row's name stays on the left while the values scroll under it
            canvas.drawText(this.data.rowLabels.get(r), left + getPaddingLeft() + 4 * this.density,
                    y + this.rowLabelHeight - 6 * this.density, this.rowLabelPaint);
            float valueTop = y + this.rowLabelHeight;
            if (this.data.moonRows[r]) {
                valueTop += this.moonGap + this.moonSize + 4 * this.density;
            }
            float baseline = valueTop + (this.rowValueHeight - metrics.ascent - metrics.descent) / 2 - 2 * this.density;
            for (int i = first; i <= last; i++) {
                Column column = this.data.columns.get(i);
                if (column.cells == null || r >= column.cells.length || column.cells[r] == null) {
                    continue;
                }
                drawCell(canvas, column.cells[r], centerX(i), y + this.rowLabelHeight, baseline);
            }
            y += rowHeight(r);
        }
    }

    private void drawCell(Canvas canvas, Cell cell, float cx, float top, float baseline) {
        if (!Double.isNaN(cell.moonAge)) {
            float r = this.moonSize / 2;
            float cy = top + this.moonGap + r;
            this.moonPainter.draw(canvas, cx, cy, r, cell.moonAge, this.data.southernHemisphere);
            if (cell.moonRing) {
                canvas.drawCircle(cx, cy, r + 2 * this.density, this.moonRingPaint);
            }
        }
        this.cellPaint.setColor(cell.color != 0 ? cell.color : (cell.faint ? FAINT_TEXT : 0xffffffff));
        if (!Float.isNaN(cell.arrow)) {
            float textWidth = this.cellPaint.measureText(cell.text);
            float arrowSize = 11 * this.density;
            float gap = 4 * this.density;
            float startX = cx - (arrowSize + gap + textWidth) / 2;
            // The arrow in the value's colour, a strong wind's whole
            this.windArrow.draw(canvas, startX + arrowSize / 2, baseline - arrowSize / 2 + this.density, arrowSize, cell.arrow,
                    cell.color != 0 ? cell.color : 0xffffffff);
            canvas.drawText(cell.text, startX + arrowSize + gap + textWidth / 2, baseline, this.cellPaint);
        } else if (!Float.isNaN(cell.drop)) {
            // The drop beside the digits, the pair centred in the column
            float textWidth = this.cellPaint.measureText(cell.text);
            float dropWidth = this.drop.width();
            float gap = 5 * this.density;
            float startX = cx - (dropWidth + gap + textWidth) / 2;
            this.drop.draw(canvas, startX + dropWidth / 2, baseline - this.digitsMiddle, cell.drop);
            canvas.drawText(cell.text, startX + dropWidth + gap + textWidth / 2, baseline, this.cellPaint);
        } else {
            canvas.drawText(cell.text, cx, baseline, this.cellPaint);
        }
        if (cell.text2 != null) {
            this.cell2Paint.setColor(cell.color2 != 0 ? cell.color2 : DIM_TEXT);
            canvas.drawText(cell.text2, cx, baseline + this.rowSecondLineHeight, this.cell2Paint);
        }
    }

    // --- Scrolling ---

    private int maxScroll() {
        return Math.max(0, Math.round(this.contentWidth) - getWidth());
    }

    /** Back to the first column, as for another city */
    public void scrollToStart() {
        this.scroller.forceFinished(true);
        scrollTo(0, 0);
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(Math.max(0, Math.min(x, maxScroll())), 0);
    }

    // A pager asks the views under the finger, hidden ones too, where they last stood: a hidden strip, the days
    // folded into their list, would keep the swipe from turning to the next city
    @Override
    public boolean canScrollHorizontally(int direction) {
        return isShown() && super.canScrollHorizontally(direction);
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return Math.max(getWidth(), Math.round(this.contentWidth));
    }

    @Override
    protected int computeHorizontalScrollExtent() {
        return getWidth();
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return getScrollX();
    }

    @Override
    public void computeScroll() {
        if (this.scroller.computeScrollOffset()) {
            scrollTo(this.scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.velocityTracker == null) {
            this.velocityTracker = VelocityTracker.obtain();
        }
        this.velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                this.downX = event.getX();
                this.downY = event.getY();
                this.lastX = this.downX;
                this.dragging = false;
                // Catching a fling stops it; where the finger goes next tells whose the gesture is
                this.scroller.abortAnimation();
                return maxScroll() > 0;
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                if (!this.dragging) {
                    float dx = x - this.downX;
                    float dy = Math.abs(event.getY() - this.downY);
                    // Only while the strip has more that way: at its end the swipe is the pager's, to the next city
                    if (Math.abs(dx) > this.touchSlop && Math.abs(dx) > dy && canScrollHorizontally(dx < 0 ? 1 : -1)) {
                        this.dragging = true;
                        this.lastX = x;
                        disallowParents(true);
                    }
                }
                if (this.dragging) {
                    scrollTo(getScrollX() + Math.round(this.lastX - x), 0);
                    this.lastX = x;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (this.dragging) {
                    this.velocityTracker.computeCurrentVelocity(1000, this.maxFlingVelocity);
                    float velocity = this.velocityTracker.getXVelocity();
                    if (Math.abs(velocity) > this.minFlingVelocity) {
                        this.scroller.fling(getScrollX(), 0, -Math.round(velocity), 0, 0, maxScroll(), 0, 0);
                        postInvalidateOnAnimation();
                    }
                }
                endTouch();
                return true;
            case MotionEvent.ACTION_CANCEL:
                endTouch();
                return true;
            default:
                return true;
        }
    }

    private void endTouch() {
        this.dragging = false;
        disallowParents(false);
        if (this.velocityTracker != null) {
            this.velocityTracker.recycle();
            this.velocityTracker = null;
        }
    }

    private void disallowParents(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }
}
