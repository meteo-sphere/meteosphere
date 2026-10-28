package org.meteosphere.ui.details;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import org.meteosphere.ui.chart.PrecipitationDrop;

/**
 * The days as rows: the day, its weather icon, the precipitation, the minimum, a bar of the day's range against
 * the week's, the maximum. The bar is coloured by temperature; today's has a dot at the temperature now.
 * Drawn in one view from prepared data, the bars' gradients made once per data.
 */
public class DailyRangeView extends View {

    private static final int DIM_TEXT = 0xb8ffffff;

    public static final class Day {
        public String name;
        public Drawable icon;
        public String precipitation;
        // How full its drop is, 0 to 1
        public float precipitationFill;
        public String min;
        public String max;
        public float minValue;
        public float maxValue;
        public int minColor;
        public int maxColor;
        // The temperature now on today's bar, NaN for none
        public float nowValue = Float.NaN;
    }

    private final float density;
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint minPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint precipitationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect iconRect = new Rect();
    private final RectF barRect = new RectF();
    private final float rowHeight;
    private final PrecipitationDrop drop;

    private ArrayList<Day> days;
    private float rangeMin;
    private float rangeMax;
    private LinearGradient[] gradients;
    // Horizontal layout for the current width
    private float iconCenter;
    private float precipitationLeft;
    private float minRight;
    private float barLeft;
    private float barRight;
    private float maxRight;
    private boolean precipitationUnderIcon;
    private static final int MIN_BAR_DP = 64;

    public DailyRangeView(Context context) {
        this(context, null);
    }

    public DailyRangeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        this.namePaint.setColor(0xffffffff);
        this.namePaint.setTextSize(17 * sp);
        this.namePaint.setTypeface(medium);
        this.minPaint.setColor(DIM_TEXT);
        this.minPaint.setTextSize(17 * sp);
        this.minPaint.setTextAlign(Paint.Align.RIGHT);
        this.maxPaint.setColor(0xffffffff);
        this.maxPaint.setTextSize(17 * sp);
        this.maxPaint.setTypeface(medium);
        this.maxPaint.setTextAlign(Paint.Align.RIGHT);
        this.precipitationPaint.setColor(PrecipitationDrop.COLOR);
        this.precipitationPaint.setTextSize(14 * sp);
        this.trackPaint.setColor(0x29ffffff);
        this.dotPaint.setColor(0xffffffff);
        this.dotRingPaint.setColor(0x66203448);
        this.dotRingPaint.setStyle(Paint.Style.STROKE);
        this.dotRingPaint.setStrokeWidth(2 * this.density);
        this.rowHeight = 50 * this.density;
        this.drop = new PrecipitationDrop(this.density);
    }

    public void setDays(ArrayList<Day> days) {
        this.days = days;
        this.rangeMin = Float.MAX_VALUE;
        this.rangeMax = -Float.MAX_VALUE;
        if (days != null) {
            for (Day day : days) {
                this.rangeMin = Math.min(this.rangeMin, day.minValue);
                this.rangeMax = Math.max(this.rangeMax, day.maxValue);
            }
        }
        layoutColumns();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = this.days != null ? this.days.size() : 0;
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(count * this.rowHeight + getPaddingTop() + getPaddingBottom()));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutColumns();
    }

    private void layoutColumns() {
        int width = getWidth();
        if (width == 0 || this.days == null) {
            return;
        }
        float left = getPaddingLeft();
        float right = width - getPaddingRight();
        float nameWidth = 0;
        float minWidth = 0;
        float maxWidth = 0;
        float precipitationWidth = 0;
        for (Day day : this.days) {
            nameWidth = Math.max(nameWidth, this.namePaint.measureText(day.name));
            minWidth = Math.max(minWidth, this.minPaint.measureText(day.min));
            maxWidth = Math.max(maxWidth, this.maxPaint.measureText(day.max));
            if (day.precipitation != null) {
                precipitationWidth = Math.max(precipitationWidth, this.precipitationPaint.measureText(day.precipitation) + 10 * this.density);
            }
        }
        float gap = 8 * this.density;
        float iconWidth = 34 * this.density;
        this.iconCenter = left + nameWidth + gap + iconWidth / 2;
        // The precipitation gets a column of its own when the bar keeps room enough, otherwise it goes under the icon
        float fixed = nameWidth + gap + iconWidth + gap + minWidth + gap + gap + maxWidth;
        this.precipitationUnderIcon = precipitationWidth > 0 && right - left - fixed - precipitationWidth - gap < MIN_BAR_DP * this.density;
        this.precipitationLeft = this.iconCenter + iconWidth / 2 + 2 * this.density;
        float minLeft = this.precipitationUnderIcon || precipitationWidth == 0
                ? this.iconCenter + iconWidth / 2 + gap : this.precipitationLeft + precipitationWidth + gap;
        this.minRight = minLeft + minWidth;
        this.barLeft = this.minRight + gap;
        this.maxRight = right;
        this.barRight = right - maxWidth - gap;
        // A gradient per day across its own bar, from the colour of its minimum to its maximum
        this.gradients = new LinearGradient[this.days.size()];
        for (int i = 0; i < this.days.size(); i++) {
            Day day = this.days.get(i);
            float x0 = barX(day.minValue);
            float x1 = Math.max(barX(day.maxValue), x0 + 1);
            this.gradients[i] = new LinearGradient(x0, 0, x1, 0, day.minColor, day.maxColor, Shader.TileMode.CLAMP);
        }
    }

    private float barX(float value) {
        if (this.rangeMax - this.rangeMin < 0.001f) {
            return (this.barLeft + this.barRight) / 2;
        }
        return this.barLeft + (value - this.rangeMin) / (this.rangeMax - this.rangeMin) * (this.barRight - this.barLeft);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.days == null || this.gradients == null || this.gradients.length != this.days.size()) {
            return;
        }
        Paint.FontMetrics metrics = this.namePaint.getFontMetrics();
        float barHeight = 5 * this.density;
        for (int i = 0; i < this.days.size(); i++) {
            Day day = this.days.get(i);
            float top = getPaddingTop() + i * this.rowHeight;
            float center = top + this.rowHeight / 2;
            float baseline = center - (metrics.ascent + metrics.descent) / 2;
            canvas.drawText(day.name, getPaddingLeft(), baseline, this.namePaint);
            float iconCenterY = this.precipitationUnderIcon && day.precipitation != null ? center - 6 * this.density : center;
            if (day.icon != null) {
                float iconHeight = (this.precipitationUnderIcon && day.precipitation != null ? 22 : 26) * this.density;
                float iconWidth = Math.min(day.icon.getIntrinsicWidth() * iconHeight / day.icon.getIntrinsicHeight(), 36 * this.density);
                float h = day.icon.getIntrinsicHeight() * iconWidth / day.icon.getIntrinsicWidth();
                this.iconRect.set(Math.round(this.iconCenter - iconWidth / 2), Math.round(iconCenterY - h / 2),
                        Math.round(this.iconCenter + iconWidth / 2), Math.round(iconCenterY + h / 2));
                day.icon.setBounds(this.iconRect);
                day.icon.draw(canvas);
            }
            if (day.precipitation != null && this.precipitationUnderIcon) {
                float textWidth = this.precipitationPaint.measureText(day.precipitation);
                float x = this.iconCenter - (textWidth + 10 * this.density) / 2;
                float y = center + 15 * this.density;
                this.drop.draw(canvas, x + 3.5f * this.density, y, day.precipitationFill);
                canvas.drawText(day.precipitation, x + 10 * this.density, y - (this.precipitationPaint.ascent() + this.precipitationPaint.descent()) / 2, this.precipitationPaint);
            } else if (day.precipitation != null) {
                this.drop.draw(canvas, this.precipitationLeft + 3.5f * this.density, center, day.precipitationFill);
                canvas.drawText(day.precipitation, this.precipitationLeft + 10 * this.density,
                        center - (this.precipitationPaint.ascent() + this.precipitationPaint.descent()) / 2, this.precipitationPaint);
            }
            canvas.drawText(day.min, this.minRight, baseline, this.minPaint);
            canvas.drawText(day.max, this.maxRight, baseline, this.maxPaint);
            this.barRect.set(this.barLeft, center - barHeight / 2, this.barRight, center + barHeight / 2);
            canvas.drawRoundRect(this.barRect, barHeight / 2, barHeight / 2, this.trackPaint);
            this.barRect.set(barX(day.minValue), center - barHeight / 2, Math.max(barX(day.maxValue), barX(day.minValue) + barHeight), center + barHeight / 2);
            this.barPaint.setShader(this.gradients[i]);
            canvas.drawRoundRect(this.barRect, barHeight / 2, barHeight / 2, this.barPaint);
            if (!Float.isNaN(day.nowValue)) {
                float x = barX(Math.max(day.minValue, Math.min(day.maxValue, day.nowValue)));
                canvas.drawCircle(x, center, 5.5f * this.density, this.dotPaint);
                canvas.drawCircle(x, center, 5.5f * this.density, this.dotRingPaint);
            }
        }
    }
}
