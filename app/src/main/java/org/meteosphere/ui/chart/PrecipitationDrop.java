package org.meteosphere.ui.chart;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The drop before a precipitation amount: faint where empty, filled from the bottom in the precipitation's blue as
 * far as the amount reaches, on a log scale up to 10 mm in an hour or 30 mm in a day. The details' cards and the chart
 * widgets draw it.
 */
public final class PrecipitationDrop {

    public static final int COLOR = 0xff9fd0ff;
    private static final int EMPTY_COLOR = 0x38ffffff;
    // The amounts that fill a drop
    private static final double FULL_HOUR_MM = 10;
    private static final double FULL_DAY_MM = 30;

    private final Path path = new Path();
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Half the drop's width; it is 3.4 of these tall, its point above
    private final float radius;

    /** The details' drop, on their dark glass */
    public PrecipitationDrop(float density) {
        this(3.4f * density, EMPTY_COLOR, COLOR);
    }

    /** A drop of the half width in pixels, in its colours when empty and where filled */
    public PrecipitationDrop(float radius, int emptyColor, int fillColor) {
        this.radius = radius;
        this.emptyPaint.setColor(emptyColor);
        this.fillPaint.setColor(fillColor);
    }

    /** How full an hour's amount fills the drop, 0 to 1 */
    public static float hourFill(double millimetres) {
        return fill(millimetres, FULL_HOUR_MM);
    }

    /** How full a day's amount fills the drop, 0 to 1 */
    public static float dayFill(double millimetres) {
        return fill(millimetres, FULL_DAY_MM);
    }

    // 0.1 of the full amount fills it by half
    private static float fill(double millimetres, double full) {
        if (millimetres <= 0) {
            return 0;
        }
        return (float) Math.min(1, Math.log10(millimetres * 100 / full + 1) / Math.log10(101));
    }

    public float width() {
        return 2 * this.radius;
    }

    /** The drop with the middle of its height at cx, cy, as the middle of the digits beside it */
    public void draw(Canvas canvas, float cx, float cy, float fill) {
        float r = this.radius;
        // The point reaches 2.3 radii up, the bottom 1.1 down: moved so the middle of the two is at cy
        float y = cy + 0.6f * r;
        float top = y - 2.3f * r;
        float bottom = y + 1.1f * r;
        this.path.reset();
        this.path.moveTo(cx, top);
        this.path.cubicTo(cx + 0.4f * r, y - 1.3f * r, cx + r, y - 0.6f * r, cx + r, y + 0.1f * r);
        this.path.cubicTo(cx + r, y + 0.75f * r, cx + 0.55f * r, bottom, cx, bottom);
        this.path.cubicTo(cx - 0.55f * r, bottom, cx - r, y + 0.75f * r, cx - r, y + 0.1f * r);
        this.path.cubicTo(cx - r, y - 0.6f * r, cx - 0.4f * r, y - 1.3f * r, cx, top);
        this.path.close();
        canvas.drawPath(this.path, this.emptyPaint);
        if (fill > 0) {
            canvas.save();
            canvas.clipRect(cx - r - 1, bottom - (bottom - top) * fill, cx + r + 1, bottom + 1);
            canvas.drawPath(this.path, this.fillPaint);
            canvas.restore();
        }
    }
}
