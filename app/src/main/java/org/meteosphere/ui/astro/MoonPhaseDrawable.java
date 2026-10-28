package org.meteosphere.ui.astro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The moon disk with its lit part, drawn white over a dim disk to sit on the weather backgrounds,
 * or on a disk of night sky of its own for light backgrounds, with a ring around it if asked. Or, given the moon's
 * disc as it is seen from a place now, the real moon's picture, turned and lit as it is (MoonPicture's).
 */
public final class MoonPhaseDrawable extends Drawable {

    private final MoonPainter painter = new MoonPainter();
    private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private double age;
    private boolean southernHemisphere;
    private boolean hasSky;
    // The sky disk left out, the moon as small within its place as on it
    private boolean skyHidden;
    private boolean hasRing;
    // The moon's radius as a share of the sky disk's
    private float skyShare = 0.86f;
    // The real moon's picture, MoonPicture's, drawn over the moon's disc instead of the drawn one; null for the drawn one
    private Bitmap picture;
    private final Paint picturePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF pictureBounds = new RectF();

    /** Draws the moon of the given share of the synodic month at a place */
    public static final class MoonPainter {

        private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint litPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        public MoonPainter() {
            this.darkPaint.setColor(0x40ffffff);
            this.litPaint.setColor(0xffffffff);
        }

        /** The colors of the dark and the lit part of the disk */
        public void setColors(int dark, int lit) {
            this.darkPaint.setColor(dark);
            this.litPaint.setColor(lit);
        }

        // age: 0 new moon, 0.25 first quarter, 0.5 full, 0.75 last quarter. From the southern hemisphere
        // the moon is seen upside down, so the waxing moon is lit on the left.
        public void draw(Canvas canvas, float cx, float cy, float radius, double age, boolean southernHemisphere) {
            canvas.drawCircle(cx, cy, radius, this.darkPaint);
            double cosAge = Math.cos(2 * Math.PI * age);
            // Lit fraction of the disk, the terminator is half an ellipse this wide
            double lit = (1 - cosAge) / 2;
            float terminatorRadius = (float) (radius * Math.abs(cosAge));
            boolean waxing = age < 0.5;

            this.path.reset();
            // The lit limb on the right, from the top down
            this.rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            this.path.arcTo(this.rect, -90, 180, true);
            // And back up along the terminator: bulging right for a crescent, left for a gibbous moon
            if (terminatorRadius < 0.5f) {
                this.path.lineTo(cx, cy - radius);
            } else {
                this.rect.set(cx - terminatorRadius, cy - radius, cx + terminatorRadius, cy + radius);
                this.path.arcTo(this.rect, 90, lit < 0.5 ? -180 : 180, false);
            }
            this.path.close();

            canvas.save();
            if (waxing == southernHemisphere) {
                canvas.scale(-1, 1, cx, cy);
            }
            canvas.drawPath(this.path, this.litPaint);
            canvas.restore();
        }
    }

    public MoonPhaseDrawable(int size) {
        this.size = size;
        setBounds(0, 0, size, size);
        this.ringPaint.setStyle(Paint.Style.STROKE);
    }

    /** The colors of the dark and the lit part of the moon */
    public void setMoonColors(int dark, int lit) {
        this.painter.setColors(dark, lit);
        invalidateSelf();
    }

    /** A disk of the color behind the moon, 0 for none */
    public void setSkyColor(int color) {
        this.hasSky = color != 0;
        this.skyPaint.setColor(color);
        invalidateSelf();
    }

    /** The sky disk not drawn, the moon kept as large as it is on it */
    public void setSkyHidden(boolean hidden) {
        this.skyHidden = hidden;
        invalidateSelf();
    }

    /** How much of the sky disk the moon takes across, 0.86 by default; more leaves a thinner rim of sky */
    public void setSkyShare(float share) {
        this.skyShare = share;
        invalidateSelf();
    }

    /** Room for a ring of the width around it, drawn in the color unless it is transparent; 0 wide for none */
    public void setRing(int color, float width) {
        this.hasRing = width > 0;
        this.ringPaint.setColor(color);
        this.ringPaint.setStrokeWidth(width);
        invalidateSelf();
    }

    /** The real moon's picture, MoonPicture's, instead of the drawn moon; null for the drawn one */
    public void setPicture(Bitmap picture) {
        if (picture != this.picture) {
            this.picture = picture;
            invalidateSelf();
        }
    }

    public void setPhase(double age, boolean southernHemisphere) {
        this.age = age;
        this.southernHemisphere = southernHemisphere;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        if (this.hasRing) {
            float ringRadius = radius - this.ringPaint.getStrokeWidth() / 2;
            canvas.drawCircle(cx, cy, ringRadius, this.ringPaint);
            radius = ringRadius - this.ringPaint.getStrokeWidth() * 2;
        }
        if (this.hasSky) {
            if (!this.skyHidden) {
                canvas.drawCircle(cx, cy, radius, this.skyPaint);
            }
            radius *= this.skyShare;
        }
        if (this.picture != null) {
            this.pictureBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawBitmap(this.picture, null, this.pictureBounds, this.picturePaint);
            return;
        }
        this.painter.draw(canvas, cx, cy, radius, this.age, this.southernHemisphere);
    }

    @Override
    public int getIntrinsicWidth() {
        return this.size;
    }

    @Override
    public int getIntrinsicHeight() {
        return this.size;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
