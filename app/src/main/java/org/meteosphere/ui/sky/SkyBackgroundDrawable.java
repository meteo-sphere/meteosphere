package org.meteosphere.ui.sky;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The main screen's window background: the theme's plain colour, with the sky of a tab faded over it. Between two
 * tabs the skies cross-fade as the pager moves, and only these bitmaps are drawn: no view is laid out again.
 */
public final class SkyBackgroundDrawable extends Drawable {

    private final Paint colorPaint = new Paint();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private Bitmap first;
    private float firstAlpha;
    private Bitmap second;
    private float secondAlpha;

    public SkyBackgroundDrawable(int color) {
        this.colorPaint.setColor(color);
    }

    /** The skies shown and how much of each, null for the plain colour; the first is drawn under the second */
    public void setSkies(Bitmap first, float firstAlpha, Bitmap second, float secondAlpha) {
        if (first == this.first && second == this.second && firstAlpha == this.firstAlpha && secondAlpha == this.secondAlpha) {
            return;
        }
        this.first = first;
        this.firstAlpha = firstAlpha;
        this.second = second;
        this.secondAlpha = secondAlpha;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        draw(canvas, 1f);
    }

    /**
     * Draws the background as much as alpha shows it, 0 to 1, without changing the drawable: glass fading in draws
     * it under the status bar, where its blur isn't drawn
     */
    public void draw(Canvas canvas, float alpha) {
        Rect bounds = getBounds();
        boolean firstOpaque = this.first != null && this.firstAlpha >= 1;
        if (!firstOpaque) {
            this.colorPaint.setAlpha(Math.round(Math.min(1, alpha) * 255));
            canvas.drawRect(bounds, this.colorPaint);
            this.colorPaint.setAlpha(255);
        }
        drawSky(canvas, bounds, this.first, this.firstAlpha * alpha);
        drawSky(canvas, bounds, this.second, this.secondAlpha * alpha);
    }

    // The sky cropped to the window's shape about its middle, as the city details show it
    private void drawSky(Canvas canvas, Rect bounds, Bitmap sky, float alpha) {
        if (sky == null || alpha <= 0) {
            return;
        }
        float scale = Math.max(bounds.width() / (float) sky.getWidth(), bounds.height() / (float) sky.getHeight());
        int width = Math.round(bounds.width() / scale);
        int height = Math.round(bounds.height() / scale);
        int left = (sky.getWidth() - width) / 2;
        int top = (sky.getHeight() - height) / 2;
        this.src.set(left, top, left + width, top + height);
        this.bitmapPaint.setAlpha(Math.round(Math.min(1, alpha) * 255));
        canvas.drawBitmap(sky, this.src, bounds, this.bitmapPaint);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
