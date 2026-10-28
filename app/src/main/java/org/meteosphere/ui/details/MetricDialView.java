package org.meteosphere.ui.details;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * The picture of a metric tile: a compass with the wind's arrow, an arc gauge with the value's knob, a level bar, a
 * radar of the earthquakes around the city, or an earthquake's waves.
 * Its geometry is made when the size or the value changes, not while drawing.
 */
public class MetricDialView extends View {

    public static final int COMPASS = 0;
    public static final int GAUGE = 1;
    public static final int LEVEL = 2;
    public static final int RADAR = 3;
    public static final int WAVES = 4;

    /** An earthquake on the radar: its bearing from the city in degrees, how far as a share of the radius, its colour */
    public static final class RadarPoint {
        final float bearing;
        final float fraction;
        final int color;
        // In dp, larger for a stronger one
        final float size;

        public RadarPoint(float bearing, float fraction, int color, float size) {
            this.bearing = bearing;
            this.fraction = fraction;
            this.color = color;
            this.size = size;
        }
    }

    private static final float GAUGE_START = 135;
    private static final float GAUGE_SWEEP = 270;

    private final float density;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path arrowPath = new Path();
    private final Path tailPath = new Path();
    private float[] ticks;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<RadarPoint> points = new ArrayList<>();

    private int mode = GAUGE;
    // GAUGE and LEVEL: 0..1; COMPASS: degrees the arrow points to, NaN for calm
    private float value;
    private String north = "N";

    public MetricDialView(Context context) {
        this(context, null);
    }

    public MetricDialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        this.trackPaint.setStyle(Paint.Style.STROKE);
        this.trackPaint.setStrokeCap(Paint.Cap.ROUND);
        this.trackPaint.setColor(0x2effffff);
        this.valuePaint.setStrokeCap(Paint.Cap.ROUND);
        this.levelTrackPaint.setColor(0x2effffff);
        this.tickPaint.setColor(0x59ffffff);
        this.tickPaint.setStrokeWidth(this.density);
        this.knobPaint.setColor(0xffffffff);
        // The arrow's head, where the wind blows to, red as a compass needle's; its tail, where it comes from, dim
        this.arrowPaint.setColor(0xffff5a52);
        this.tailPaint.setColor(0x66ffffff);
        this.textPaint.setColor(0xccffffff);
        this.textPaint.setTextSize(9 * getResources().getDisplayMetrics().scaledDensity);
        this.textPaint.setTextAlign(Paint.Align.CENTER);
        this.ringPaint.setStyle(Paint.Style.STROKE);
        this.ringPaint.setStrokeWidth(this.density);
        this.ringPaint.setColor(0x4dffffff);
        this.pointEdgePaint.setStyle(Paint.Style.STROKE);
        this.pointEdgePaint.setStrokeWidth(this.density);
        this.pointEdgePaint.setColor(0x66000000);
    }

    /** The earthquakes around the city at the centre, the farthest drawn first so the nearest stay on top */
    public void setRadar(List<RadarPoint> points, String north) {
        this.mode = RADAR;
        this.points = points != null ? points : new ArrayList<>();
        this.north = north;
        rebuild();
    }

    /** Waves running out from an earthquake, in its magnitude's colour */
    public void setWaves(int color) {
        this.mode = WAVES;
        this.valuePaint.setColor(color);
        rebuild();
    }

    public void setCompass(float degrees, String north) {
        this.mode = COMPASS;
        this.value = degrees;
        this.north = north;
        rebuild();
    }

    public void setGauge(float fraction, int color) {
        this.mode = GAUGE;
        this.value = Math.max(0, Math.min(1, fraction));
        this.valuePaint.setColor(color);
        this.valuePaint.setStyle(Paint.Style.STROKE);
        rebuild();
    }

    public void setLevel(float fraction, int color) {
        this.mode = LEVEL;
        this.value = Math.max(0, Math.min(1, fraction));
        this.valuePaint.setColor(color);
        this.valuePaint.setStyle(Paint.Style.FILL);
        rebuild();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private void rebuild() {
        float w = getWidth();
        float h = getHeight();
        if (w == 0 || h == 0) {
            invalidate();
            return;
        }
        float cx = w / 2;
        float cy = h / 2;
        float radius = Math.min(w, h) / 2 - 4 * this.density;
        if (this.mode == COMPASS) {
            // Ticks every 10 degrees, longer at the cardinal points
            this.ticks = new float[36 * 4];
            for (int i = 0; i < 36; i++) {
                double angle = Math.toRadians(i * 10);
                float inner = radius - (i % 9 == 0 ? 7 : 4) * this.density;
                this.ticks[i * 4] = cx + (float) (inner * Math.sin(angle));
                this.ticks[i * 4 + 1] = cy - (float) (inner * Math.cos(angle));
                this.ticks[i * 4 + 2] = cx + (float) (radius * Math.sin(angle));
                this.ticks[i * 4 + 3] = cy - (float) (radius * Math.cos(angle));
            }
            float length = radius - 10 * this.density;
            float half = 5.5f * this.density;
            this.arrowPath.reset();
            this.arrowPath.moveTo(cx, cy - length);
            this.arrowPath.lineTo(cx + half, cy);
            this.arrowPath.lineTo(cx - half, cy);
            this.arrowPath.close();
            this.tailPath.reset();
            this.tailPath.moveTo(cx, cy + length);
            this.tailPath.lineTo(cx + half, cy);
            this.tailPath.lineTo(cx - half, cy);
            this.tailPath.close();
        } else if (this.mode == GAUGE) {
            float stroke = 7 * this.density;
            this.trackPaint.setStrokeWidth(stroke);
            this.valuePaint.setStrokeWidth(stroke);
            float r = radius - stroke / 2;
            this.rect.set(cx - r, cy - r, cx + r, cy + r);
        } else {
            float barWidth = 10 * this.density;
            this.rect.set(cx - barWidth / 2, 4 * this.density, cx + barWidth / 2, h - 4 * this.density);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (this.mode == COMPASS) {
            if (this.ticks != null) {
                canvas.drawLines(this.ticks, this.tickPaint);
            }
            float radius = Math.min(getWidth(), getHeight()) / 2f - 4 * this.density;
            canvas.drawText(this.north, cx, cy - radius + 17 * this.density, this.textPaint);
            if (!Float.isNaN(this.value)) {
                canvas.save();
                canvas.rotate(this.value, cx, cy);
                canvas.drawPath(this.tailPath, this.tailPaint);
                canvas.drawPath(this.arrowPath, this.arrowPaint);
                canvas.restore();
            }
            canvas.drawCircle(cx, cy, 2.5f * this.density, this.knobPaint);
        } else if (this.mode == RADAR) {
            float radius = Math.min(getWidth(), getHeight()) / 2f - 4 * this.density;
            canvas.drawCircle(cx, cy, radius, this.ringPaint);
            canvas.drawCircle(cx, cy, radius * 0.55f, this.ringPaint);
            canvas.drawText(this.north, cx, cy - radius + 11 * this.density, this.textPaint);
            for (int i = this.points.size() - 1; i >= 0; i--) {
                RadarPoint point = this.points.get(i);
                double angle = Math.toRadians(point.bearing);
                float r = radius * Math.min(1, point.fraction);
                float x = cx + (float) (r * Math.sin(angle));
                float y = cy - (float) (r * Math.cos(angle));
                float size = point.size * this.density;
                this.pointPaint.setColor(point.color);
                canvas.drawCircle(x, y, size, this.pointPaint);
                canvas.drawCircle(x, y, size, this.pointEdgePaint);
            }
            canvas.drawCircle(cx, cy, 2.5f * this.density, this.knobPaint);
        } else if (this.mode == WAVES) {
            float radius = Math.min(getWidth(), getHeight()) / 2f - 4 * this.density;
            this.ringPaint.setColor(this.valuePaint.getColor());
            this.ringPaint.setStrokeWidth(2 * this.density);
            int[] alphas = {255, 160, 80};
            float[] radii = {0.4f, 0.7f, 1f};
            for (int i = 0; i < 3; i++) {
                this.ringPaint.setAlpha(alphas[i]);
                canvas.drawCircle(cx, cy, radius * radii[i], this.ringPaint);
            }
            this.ringPaint.setColor(0x4dffffff);
            this.ringPaint.setStrokeWidth(this.density);
            this.pointPaint.setColor(this.valuePaint.getColor());
            canvas.drawCircle(cx, cy, radius * 0.18f, this.pointPaint);
        } else if (this.mode == GAUGE) {
            canvas.drawArc(this.rect, GAUGE_START, GAUGE_SWEEP, false, this.trackPaint);
            if (this.value > 0.005f) {
                canvas.drawArc(this.rect, GAUGE_START, GAUGE_SWEEP * this.value, false, this.valuePaint);
            }
            double angle = Math.toRadians(GAUGE_START + GAUGE_SWEEP * this.value);
            float r = this.rect.width() / 2;
            canvas.drawCircle(cx + (float) (r * Math.cos(angle)), cy + (float) (r * Math.sin(angle)), 6 * this.density, this.knobPaint);
        } else {
            float radius = this.rect.width() / 2;
            canvas.drawRoundRect(this.rect, radius, radius, this.levelTrackPaint);
            float top = this.rect.bottom - this.rect.height() * this.value;
            canvas.save();
            canvas.clipRect(this.rect.left, top, this.rect.right, this.rect.bottom);
            canvas.drawRoundRect(this.rect, radius, radius, this.valuePaint);
            canvas.restore();
        }
    }
}
