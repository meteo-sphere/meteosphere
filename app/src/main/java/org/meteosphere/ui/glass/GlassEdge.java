package org.meteosphere.ui.glass;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** The edge of a piece of glass: a hairline just inside it, lighter at the top where the light falls on it */
public final class GlassEdge {

    private final GlassStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    public GlassEdge(GlassStyle style, float density) {
        this.style = style;
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeWidth(Math.max(1, density));
    }

    /** From the view's onSizeChanged */
    public void setSize(int width, int height) {
        float edge = this.paint.getStrokeWidth() / 2;
        this.bounds.set(edge, edge, width - edge, height - edge);
        this.paint.setShader(new LinearGradient(0, 0, 0, height, this.style.edgeTop, this.style.edgeBottom, Shader.TileMode.CLAMP));
    }

    public void draw(Canvas canvas, float radius) {
        canvas.drawRoundRect(this.bounds, radius, radius, this.paint);
    }

    /** Round at the ends, as a pill is */
    public void drawPill(Canvas canvas) {
        draw(canvas, this.bounds.height() / 2);
    }
}
