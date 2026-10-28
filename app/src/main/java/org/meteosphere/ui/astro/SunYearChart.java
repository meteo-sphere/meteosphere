package org.meteosphere.ui.astro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import com.google.android.material.color.MaterialColors;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Sunrise and sunset day by day over a year: the daylight between them as an amber band, the civil twilight
 * around it fainter, the hours of the day upwards and the months along. The solstices and equinoxes sit on the
 * sunset line, the longest and shortest day or the polar day and night are written out. Touching it picks a day.
 */
public class SunYearChart extends ScrubChart {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int SUN_COLOR = 0xffffb300;
    private static final int MARK_SIZE_DP = 14;

    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint twilightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bandPath = new Path();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private long firstDay;
    // Minutes of the local day, -1 when there is none
    private int[] rise;
    private int[] set;
    private int[] dawn;
    private int[] dusk;
    private boolean[] polarDay;
    private int[] markDays;
    private Drawable[] markIcons;
    private int minDay = -1;
    private int maxDay = -1;
    private String minText;
    private String maxText;
    private String polarDayName;
    private String polarNightName;
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SunYearChart(Context context) {
        this(context, null);
    }

    public SunYearChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        this.dayPaint.setColor((SUN_COLOR & 0x00ffffff) | 0x66000000);
        this.twilightPaint.setColor((SUN_COLOR & 0x00ffffff) | 0x26000000);
        this.linePaint.setColor(SUN_COLOR);
        this.linePaint.setStrokeWidth(2 * this.density);
        this.linePaint.setStyle(Paint.Style.STROKE);
        int onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff888888);
        this.gridPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x33888888));
        this.gridPaint.setStrokeWidth(this.density);
        this.textPaint.setColor(onSurfaceVariant);
        this.textPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        this.selectionPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xff000000));
        this.selectionPaint.setStrokeWidth(1.5f * this.density);
        this.labelPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xff000000));
        this.labelPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        this.labelPaint.setFakeBoldText(true);
    }

    /** Days to mark, like the solstices and equinoxes, with the icon of each */
    public void setMarks(int[] days, Drawable[] icons) {
        this.markDays = days;
        this.markIcons = icons;
        invalidate();
    }

    /** The shortest and the longest day with their lengths written out, -1 for none */
    public void setDayLengthExtremes(int minDay, String minText, int maxDay, String maxText) {
        this.minDay = minDay;
        this.minText = minText;
        this.maxDay = maxDay;
        this.maxText = maxText;
        invalidate();
    }

    /** What to write over the days the Sun doesn't set and the days it doesn't rise */
    public void setPolarNames(String polarDayName, String polarNightName) {
        this.polarDayName = polarDayName;
        this.polarNightName = polarNightName;
        invalidate();
    }

    /** The days from firstDay (local midnight read as UTC), their sunrise, sunset and twilight in minutes or -1 */
    public void setData(long firstDay, int[] rise, int[] set, int[] dawn, int[] dusk, boolean[] polarDay) {
        this.firstDay = firstDay;
        this.rise = rise;
        this.set = set;
        this.dawn = dawn;
        this.dusk = dusk;
        this.polarDay = polarDay;
        showDays(firstDay);
        invalidate();
    }

    /** The color of the line of the day picked */
    public void setSelectionColor(int color) {
        this.selectionPaint.setColor(color);
        invalidate();
    }

    private float left() {
        return 28 * this.density;
    }

    private float bottom() {
        return getHeight() - 20 * this.density;
    }

    private float top() {
        return 6 * this.density;
    }

    private float dayWidth() {
        return (getWidth() - left()) / this.rise.length;
    }

    private float y(int minutes) {
        return bottom() - (bottom() - top()) * minutes / MINUTES_PER_DAY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.rise == null || this.rise.length == 0) {
            return;
        }
        float left = left();
        float width = dayWidth();
        // Hours: 0, 6, 12, 18, 24
        for (int hour = 0; hour <= 24; hour += 6) {
            float y = y(hour * 60);
            canvas.drawLine(left, y, getWidth(), y, this.gridPaint);
            String label = String.valueOf(hour);
            canvas.drawText(label, left - 6 * this.density - this.textPaint.measureText(label), y + this.textPaint.getTextSize() / 3, this.textPaint);
        }
        int[][] twilight = new int[this.rise.length][];
        int[][] day = new int[this.rise.length][];
        for (int i = 0; i < this.rise.length; i++) {
            twilight[i] = span(this.dawn[i], this.dusk[i], this.rise[i] != -1 || this.set[i] != -1 || this.polarDay[i]);
            day[i] = span(this.rise[i], this.set[i], this.polarDay[i]);
        }
        drawBand(canvas, twilight, this.twilightPaint);
        drawBand(canvas, day, this.dayPaint);
        drawLine(canvas, this.rise);
        drawLine(canvas, this.set);
        drawMonths(canvas);
        drawMarks(canvas);
        // The polar day and night named over their days, else the longest and the shortest day
        if (!drawPolar(canvas, true, this.polarDayName)) {
            drawExtreme(canvas, this.maxDay, this.maxText);
        }
        if (!drawPolar(canvas, false, this.polarNightName)) {
            drawExtreme(canvas, this.minDay, this.minText);
        }
        // The day picked
        int selected = getSelected();
        float x = left + (selected + 0.5f) * width;
        canvas.drawLine(x, top(), x, bottom(), this.selectionPaint);
        if (this.rise[selected] != -1) {
            canvas.drawCircle(x, y(this.rise[selected]), 3.5f * this.density, this.linePaint);
        }
        if (this.set[selected] != -1) {
            canvas.drawCircle(x, y(this.set[selected]), 3.5f * this.density, this.linePaint);
        }
    }

    // Each run of days with a span as one shape, its top along the ends and its bottom back along the starts,
    // so the translucent band has no seams
    private void drawBand(Canvas canvas, int[][] spans, Paint paint) {
        float width = dayWidth();
        int i = 0;
        while (i < spans.length) {
            if (spans[i] == null) {
                i++;
                continue;
            }
            int first = i;
            while (i < spans.length && spans[i] != null) {
                i++;
            }
            this.bandPath.reset();
            this.bandPath.moveTo(left() + first * width, y(spans[first][1]));
            for (int j = first; j < i; j++) {
                this.bandPath.lineTo(left() + (j + 0.5f) * width, y(spans[j][1]));
            }
            this.bandPath.lineTo(left() + i * width, y(spans[i - 1][1]));
            this.bandPath.lineTo(left() + i * width, y(spans[i - 1][0]));
            for (int j = i - 1; j >= first; j--) {
                this.bandPath.lineTo(left() + (j + 0.5f) * width, y(spans[j][0]));
            }
            this.bandPath.lineTo(left() + first * width, y(spans[first][0]));
            this.bandPath.close();
            canvas.drawPath(this.bandPath, paint);
        }
    }

    // The icon of each marked day on its sunset line; on the top edge in a polar day, at noon in a polar night
    private void drawMarks(Canvas canvas) {
        if (this.markDays == null) {
            return;
        }
        int size = Math.round(MARK_SIZE_DP * this.density);
        for (int i = 0; i < this.markDays.length; i++) {
            int day = this.markDays[i];
            if (day < 0 || day >= this.rise.length) {
                continue;
            }
            float x = left() + (day + 0.5f) * dayWidth();
            float cy = y(this.set[day] != -1 ? this.set[day] : (this.polarDay[day] ? MINUTES_PER_DAY : MINUTES_PER_DAY / 2));
            int l = Math.round(Math.max(0, Math.min(getWidth() - size, x - size / 2f)));
            int t = Math.round(Math.max(0, cy - size / 2f));
            this.markIcons[i].setBounds(l, t, l + size, t + size);
            this.markIcons[i].draw(canvas);
        }
    }

    // The length of the day written over its sunset line, clear of a mark there; under it when there is no room
    private void drawExtreme(Canvas canvas, int day, String text) {
        if (text == null || day < 0 || day >= this.rise.length || this.rise[day] == -1 || this.set[day] == -1) {
            return;
        }
        float gap = (MARK_SIZE_DP / 2f + 8) * this.density;
        float line = y(this.set[day]);
        float baseline = line - gap;
        if (baseline - this.labelPaint.getTextSize() < top()) {
            baseline = line + gap + this.labelPaint.getTextSize();
        }
        drawLabel(canvas, text, left() + (day + 0.5f) * dayWidth(), baseline);
    }

    // The name over the middle of the first run of polar days or nights, about noon height; false without one
    private boolean drawPolar(Canvas canvas, boolean isDay, String name) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < this.rise.length; i++) {
            boolean isPolar = this.rise[i] == -1 && this.set[i] == -1 && this.polarDay[i] == isDay;
            if (isPolar && first == -1) {
                first = i;
            }
            if (first != -1) {
                if (!isPolar) {
                    break;
                }
                last = i;
            }
        }
        if (first == -1 || name == null) {
            return false;
        }
        // The polar night's solstice sits at noon height, so its name goes over it
        float noon = y(MINUTES_PER_DAY / 2);
        float baseline = isDay ? noon + this.labelPaint.getTextSize() / 3 : noon - (MARK_SIZE_DP / 2f + 8) * this.density;
        drawLabel(canvas, name, left() + (first + last + 1) / 2f * dayWidth(), baseline);
        return true;
    }

    private void drawLabel(Canvas canvas, String text, float centerX, float baseline) {
        float textWidth = this.labelPaint.measureText(text);
        float x = Math.max(left() + 2 * this.density, Math.min(getWidth() - textWidth - 2 * this.density, centerX - textWidth / 2));
        canvas.drawText(text, x, baseline, this.labelPaint);
    }

    // From start to end minutes; a missing one is the edge of the day, both missing all day if up, else nothing
    private static int[] span(int start, int end, boolean isUp) {
        if (start == -1 && end == -1) {
            return isUp ? new int[]{0, MINUTES_PER_DAY} : null;
        }
        int from = start == -1 ? 0 : start;
        int to = end == -1 || end < from ? MINUTES_PER_DAY : end;
        return new int[]{from, to};
    }

    private void drawLine(Canvas canvas, int[] minutes) {
        float width = dayWidth();
        float previousX = 0;
        float previousY = 0;
        boolean hasPrevious = false;
        for (int i = 0; i < minutes.length; i++) {
            if (minutes[i] == -1) {
                hasPrevious = false;
                continue;
            }
            float x = left() + (i + 0.5f) * width;
            float y = y(minutes[i]);
            // A time passing midnight from one day to the next isn't joined across the chart
            if (hasPrevious && Math.abs(minutes[i] - minutes[i - 1]) < MINUTES_PER_DAY / 2) {
                canvas.drawLine(previousX, previousY, x, y, this.linePaint);
            }
            previousX = x;
            previousY = y;
            hasPrevious = true;
        }
    }

    // A tick at the start of each month and its name, three letters at most, in the middle of its column; the
    // months cut off at the ends are named too when the name fits in what is shown of them
    private void drawMonths(Canvas canvas) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        SimpleDateFormat monthFormat = new SimpleDateFormat("LLL", locale);
        monthFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        float width = dayWidth();
        int start = 0;
        for (int i = 1; i <= this.rise.length; i++) {
            boolean isEnd = i == this.rise.length;
            if (!isEnd) {
                calendar.setTimeInMillis(this.firstDay + i * 86400000L);
                if (calendar.get(Calendar.DAY_OF_MONTH) != 1) {
                    continue;
                }
                float x = left() + i * width;
                canvas.drawLine(x, top(), x, bottom() + 4 * this.density, this.gridPaint);
            }
            // The month from start to i
            calendar.setTimeInMillis(this.firstDay + start * 86400000L);
            String month = monthFormat.format(calendar.getTime()).replace(".", "");
            month = month.substring(0, Math.min(3, month.length()));
            float textWidth = this.textPaint.measureText(month);
            float from = left() + start * width;
            float to = left() + i * width;
            if (to - from >= textWidth + 2 * this.density) {
                canvas.drawText(month, (from + to - textWidth) / 2, getHeight() - 5 * this.density, this.textPaint);
            }
            start = i;
        }
    }

    @Override
    protected int dayCount() {
        return this.rise == null ? 0 : this.rise.length;
    }

    @Override
    protected int dayAt(float x) {
        return (int) ((x - left()) / dayWidth());
    }
}
