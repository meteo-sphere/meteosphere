package org.meteosphere.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Its content fades out towards the screen's bottom edge, under the buttons lying over it, as the city details' cards
 * do: whatever lists the pages hold. Only the faded band goes through an offscreen layer, the rest is drawn as it is.
 */
public class BottomFadeFrame extends FrameLayout {

    private final Paint fadePaint = new Paint();
    private int fadeLength;

    public BottomFadeFrame(Context context) {
        this(context, null);
    }

    public BottomFadeFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /** How high over the screen's bottom edge the content fades out; 0 for none */
    public void setFadeLength(int length) {
        if (length != this.fadeLength) {
            this.fadeLength = length;
            updateShader(getHeight());
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader(h);
    }

    private void updateShader(int height) {
        this.fadePaint.setShader(new LinearGradient(0, height - this.fadeLength, 0, height, 0, 0xff000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        int top = height - this.fadeLength;
        if (this.fadeLength <= 0 || top <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        // Above the band as it is; the band apart, its content cut away more and more towards the bottom
        int clip = canvas.save();
        canvas.clipRect(0, 0, width, top);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(clip);
        int layer = canvas.saveLayer(0, top, width, height, null);
        super.dispatchDraw(canvas);
        canvas.drawRect(0, top, width, height, this.fadePaint);
        canvas.restoreToCount(layer);
    }
}
