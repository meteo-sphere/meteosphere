package org.meteosphere.ui.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Instead of glass under a screen's header: the window's background, the sky or the plain colour, laid over the
 * content down to the header's controls, then thinning out below them, so that the content dissolves into the
 * background as it scrolls up under the header. Optionally a shade from the screen's top for the header to read. It
 * stays where it is, from the top of the screen; a header that moves only moves where it hides and fades.
 */
public final class TopFade extends View {

    private static final int SHADE_STOPS = 16;

    private final Paint maskPaint = new Paint();
    private final Paint shadePaint = new Paint(Paint.DITHER_FLAG);
    // Both shaders run over 0 to 1 and are stretched to where they lie now, so a moving header makes no new ones
    private final LinearGradient mask = new LinearGradient(0, 0, 0, 1, 0xff000000, 0x00000000, Shader.TileMode.CLAMP);
    private final Matrix matrix = new Matrix();
    private final int[] location = new int[2];
    // The content hidden down to here, fading in to fully seen at fadeBottom
    private int hiddenBottom;
    private int fadeBottom;
    private float shade = -1;
    // How much of it shows, 0 to 1: kept in the paints rather than the view's alpha, which would take a layer more
    private float strength = 1;

    public TopFade(Context context) {
        super(context);
        this.maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        this.maskPaint.setShader(this.mask);
    }

    /** The content hidden down to hiddenBottom, seen fully again from fadeBottom on; shade the darkness at the top */
    public void set(int hiddenBottom, int fadeBottom, float shade) {
        if (hiddenBottom == this.hiddenBottom && fadeBottom == this.fadeBottom && shade == this.shade) {
            return;
        }
        if (shade != this.shade) {
            int[] colors = new int[SHADE_STOPS];
            float[] positions = new float[SHADE_STOPS];
            for (int i = 0; i < SHADE_STOPS; i++) {
                float t = i / (float) (SHADE_STOPS - 1);
                // Darkest at the top, thinning out softly to nothing where the content is seen again
                float eased = (1 - t) * (1 - t);
                colors[i] = Math.round(eased * shade * 255) << 24;
                positions[i] = t;
            }
            this.shadePaint.setShader(new LinearGradient(0, 0, 0, 1, colors, positions, Shader.TileMode.CLAMP));
            this.shade = shade;
        }
        this.hiddenBottom = hiddenBottom;
        this.fadeBottom = fadeBottom;
        this.matrix.setScale(1, Math.max(1, fadeBottom - hiddenBottom));
        this.matrix.postTranslate(0, hiddenBottom);
        this.mask.setLocalMatrix(this.matrix);
        this.matrix.setScale(1, Math.max(1, fadeBottom));
        this.shadePaint.getShader().setLocalMatrix(this.matrix);
        invalidate();
    }

    /** How much of the fade below the part hidden outright, and of the shade, shows, 0 for none to 1 */
    public void setStrength(float strength) {
        strength = Math.max(0f, Math.min(1f, strength));
        if (strength != this.strength) {
            this.strength = strength;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        if (this.fadeBottom <= this.hiddenBottom || this.strength <= 0) {
            return;
        }
        int alpha = Math.round(this.strength * 255);
        Drawable background = getRootView().getBackground();
        if (background != null) {
            getLocationInWindow(this.location);
            // Straight down to the controls, whole: a layer isn't drawn under the status bar
            if (this.hiddenBottom > 0) {
                int saved = canvas.save();
                canvas.clipRect(0, 0, width, this.hiddenBottom);
                GlassStrip.drawWindowBackground(canvas, background, this.location, 1f);
                canvas.restoreToCount(saved);
            }
            // The mask as strong as the fade shows: the background over the content thins out with it
            int layer = canvas.saveLayer(0, this.hiddenBottom, width, this.fadeBottom, null);
            GlassStrip.drawWindowBackground(canvas, background, this.location, 1f);
            this.maskPaint.setAlpha(alpha);
            canvas.drawRect(0, this.hiddenBottom, width, this.fadeBottom, this.maskPaint);
            canvas.restoreToCount(layer);
        }
        if (this.shade > 0) {
            this.shadePaint.setAlpha(alpha);
            canvas.drawRect(0, 0, width, this.fadeBottom, this.shadePaint);
        }
    }

}
