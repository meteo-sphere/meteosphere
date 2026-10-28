package org.meteosphere.ui.astro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import com.google.android.material.color.MaterialColors;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.util.AppUtils;

/**
 * The Moon's light over the coming month: the illuminated share from 0 to 100% as a silver wave, the principal
 * phases as moons on it, the perigees and apogees as dashed lines named over the chart, and the days along with
 * today at the start. Touching it picks a day.
 */
public class MoonMonthChart extends ScrubChart {

    private static final long DAY_MS = 86400000L;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint apsisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint apsisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;

    private long firstDay;
    private int days;
    // The illuminated share 0..1 at even steps from the first day's midnight to the last day's
    private float[] illumination;
    private float[] markDays;
    private Drawable[] markIcons;
    private float[] apsisDays;
    private String[] apsisNames;

    public MoonMonthChart(Context context) {
        this(context, null);
    }

    public MoonMonthChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        int onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff888888);
        this.linePaint.setStyle(Paint.Style.STROKE);
        this.linePaint.setStrokeWidth(2 * this.density);
        this.linePaint.setStrokeJoin(Paint.Join.ROUND);
        this.gridPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x33888888));
        this.gridPaint.setStrokeWidth(this.density);
        float textSize = 11 * getResources().getDisplayMetrics().scaledDensity;
        this.textPaint.setColor(onSurfaceVariant);
        this.textPaint.setTextSize(textSize);
        this.apsisPaint.setColor(onSurfaceVariant);
        this.apsisPaint.setStrokeWidth(this.density);
        this.apsisPaint.setStyle(Paint.Style.STROKE);
        this.apsisPaint.setPathEffect(new DashPathEffect(new float[]{2 * this.density, 3 * this.density}, 0));
        this.apsisTextPaint.setTextSize(textSize);
        this.apsisTextPaint.setTextAlign(Paint.Align.CENTER);
        this.selectionPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xff000000));
        this.selectionPaint.setStrokeWidth(1.5f * this.density);
        setColor(0xffaebfdd);
    }

    public void setColor(int color) {
        this.linePaint.setColor(color);
        this.areaPaint.setColor((color & 0x00ffffff) | 0x2e000000);
        this.apsisTextPaint.setColor(color);
        this.dotPaint.setColor(color);
        invalidate();
    }

    /** The days from firstDay (local midnight read as UTC) and the light at even steps over them, the last at the end */
    public void setData(long firstDay, int days, float[] illumination) {
        this.firstDay = firstDay;
        this.days = days;
        this.illumination = illumination;
        showDays(firstDay);
        invalidate();
    }

    /** The principal phases at their times in days from the first midnight, with their moons */
    public void setPhases(float[] days, Drawable[] icons) {
        this.markDays = days;
        this.markIcons = icons;
        invalidate();
    }

    /** The perigees and apogees at their times in days from the first midnight, with their names */
    public void setApsides(float[] days, String[] names) {
        this.apsisDays = days;
        this.apsisNames = names;
        invalidate();
    }

    /** The color of the line of the day picked */
    public void setSelectionColor(int color) {
        this.selectionPaint.setColor(color);
        invalidate();
    }

    private float left() {
        return 32 * this.density;
    }

    private float right() {
        return getWidth() - 8 * this.density;
    }

    // Room over the wave for the names of the perigees and apogees and the full moon's picture
    private float top() {
        return 30 * this.density;
    }

    private float bottom() {
        return getHeight() - 20 * this.density;
    }

    private float x(float day) {
        return left() + (right() - left()) * day / this.days;
    }

    private float y(float share) {
        return bottom() - (bottom() - top()) * share;
    }

    // The light at a time in days, read off the steps
    private float shareAt(float day) {
        float position = day / this.days * (this.illumination.length - 1);
        int i = Math.max(0, Math.min(this.illumination.length - 2, (int) position));
        float f = Math.max(0, Math.min(1, position - i));
        return this.illumination[i] + (this.illumination[i + 1] - this.illumination[i]) * f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.illumination == null || this.illumination.length < 2 || this.days == 0) {
            return;
        }
        float share = 0;
        for (String label : new String[]{"0", "50", "100%"}) {
            float y = y(share);
            canvas.drawLine(left(), y, right(), y, this.gridPaint);
            canvas.drawText(label, left() - 5 * this.density - this.textPaint.measureText(label), y + this.textPaint.getTextSize() / 3, this.textPaint);
            share += 0.5f;
        }
        this.path.reset();
        for (int i = 0; i < this.illumination.length; i++) {
            float x = x((float) this.days * i / (this.illumination.length - 1));
            float y = y(this.illumination[i]);
            if (i == 0) {
                this.path.moveTo(x, y);
            } else {
                this.path.lineTo(x, y);
            }
        }
        Path area = new Path(this.path);
        area.lineTo(right(), bottom());
        area.lineTo(left(), bottom());
        area.close();
        canvas.drawPath(area, this.areaPaint);
        canvas.drawPath(this.path, this.linePaint);
        drawDays(canvas);
        if (this.apsisDays != null) {
            for (int i = 0; i < this.apsisDays.length; i++) {
                float day = this.apsisDays[i];
                if (day < 0 || day > this.days) {
                    continue;
                }
                float x = x(day);
                canvas.drawLine(x, top() - 12 * this.density, x, bottom(), this.apsisPaint);
                float half = this.apsisTextPaint.measureText(this.apsisNames[i]) / 2;
                float tx = Math.max(half, Math.min(getWidth() - half, x));
                canvas.drawText(this.apsisNames[i], tx, top() - 16 * this.density, this.apsisTextPaint);
            }
        }
        // The day picked, today at first
        int selected = getSelected();
        float selectedX = x(selected + 0.5f);
        canvas.drawLine(selectedX, top(), selectedX, bottom(), this.selectionPaint);
        canvas.drawCircle(selectedX, y(shareAt(selected + 0.5f)), 4 * this.density, this.dotPaint);
        if (this.markDays != null) {
            int size = Math.round(16 * this.density);
            for (int i = 0; i < this.markDays.length; i++) {
                float day = this.markDays[i];
                if (day < 0 || day > this.days) {
                    continue;
                }
                int cx = Math.round(x(day));
                int cy = Math.round(y(shareAt(day)));
                this.markIcons[i].setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
                this.markIcons[i].draw(canvas);
            }
        }
    }

    // A tick and the day of the month each week, with the month at the first and at the first of a month
    private void drawDays(Canvas canvas) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        SimpleDateFormat monthFormat = new SimpleDateFormat(AppUtils.datePattern(locale, "d MMM"), locale);
        monthFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        float baseline = getHeight() - 5 * this.density;
        float taken = -Float.MAX_VALUE;
        for (int day = 0; day <= this.days; day++) {
            calendar.setTimeInMillis(this.firstDay + day * DAY_MS);
            boolean isMonthStart = calendar.get(Calendar.DAY_OF_MONTH) == 1;
            if (day % 7 != 0 && !isMonthStart) {
                continue;
            }
            float x = x(day + 0.5f);
            String label = day == 0 || isMonthStart ? monthFormat.format(calendar.getTime()).replace(".", "")
                    : String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
            float width = this.textPaint.measureText(label);
            float tx = Math.max(left() - width / 2, Math.min(getWidth() - width, x - width / 2));
            // A label that would run into the one before it is left out
            if (tx < taken + 6 * this.density) {
                continue;
            }
            canvas.drawLine(x, bottom(), x, bottom() + 4 * this.density, this.gridPaint);
            canvas.drawText(label, tx, baseline, this.textPaint);
            taken = tx + width;
        }
    }

    @Override
    protected int dayCount() {
        return this.illumination == null ? 0 : this.days;
    }

    @Override
    protected int dayAt(float x) {
        return (int) ((x - left()) / (right() - left()) * this.days);
    }
}
