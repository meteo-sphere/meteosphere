package org.meteosphere.ui.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;

/**
 * A row of white content on a pill of dark frosted glass over a sky, as the tabs' toolbar is: the city's name at the
 * top of the details, the bar with the cities at their bottom. What lies under it is blurred from Android 12, redrawn
 * only when something scrolls or is laid out again; before that its tint stands alone, denser.
 */
public class GlassPill extends LinearLayout {

    private final GlassStyle style = GlassStyle.dark();
    private final GlassBlur glass;
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlassEdge edge;
    private final RectF bounds = new RectF();
    private final ArrayList<View> sources = new ArrayList<>();
    private GlassStrip.BlurSources blurSources;
    private final GlassRedraw redraw = new GlassRedraw(this);

    public GlassPill(Context context) {
        this(context, null);
    }

    public GlassPill(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        this.glass = new GlassBlur(GlassBlur.BLUR_DP * density);
        this.tintPaint.setColor(this.style.tint(false));
        this.edge = new GlassEdge(this.style, density);
        setWillNotDraw(false);
    }

    /** The views blurred under the pill, over the window's background: what scrolls under it, not other glass */
    public void setBlurSources(GlassStrip.BlurSources sources) {
        this.blurSources = sources;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.redraw.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.redraw.detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.bounds.set(0, 0, w, h);
        this.edge.setSize(w, h);
    }

    @Override
    public void draw(Canvas canvas) {
        float radius = getHeight() / 2f;
        this.sources.clear();
        if (this.blurSources != null) {
            this.blurSources.collect(this.sources);
        }
        this.glass.draw(canvas, this, this.sources, radius);
        canvas.drawRoundRect(this.bounds, radius, radius, this.tintPaint);
        super.draw(canvas);
        this.edge.drawPill(canvas);
    }
}
