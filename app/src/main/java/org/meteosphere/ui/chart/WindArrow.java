package org.meteosphere.ui.chart;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** The wind's arrow beside its speed in a forecast's columns: pointing where the wind blows to */
public final class WindArrow {

    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** The arrow size high in its middle at cx, cy, pointing up turned by the degrees */
    public void draw(Canvas canvas, float cx, float cy, float size, float degrees, int color) {
        float h = size / 2;
        this.path.reset();
        this.path.moveTo(cx, cy - h);
        this.path.lineTo(cx + h * 0.72f, cy + h);
        this.path.lineTo(cx, cy + h * 0.5f);
        this.path.lineTo(cx - h * 0.72f, cy + h);
        this.path.close();
        this.paint.setColor(color);
        canvas.save();
        canvas.rotate(degrees, cx, cy);
        canvas.drawPath(this.path, this.paint);
        canvas.restore();
    }
}
