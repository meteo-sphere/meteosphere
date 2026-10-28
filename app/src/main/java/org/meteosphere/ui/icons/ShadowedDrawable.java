package org.meteosphere.ui.icons;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/**
 * An icon with the same soft shadow as the text it stands with: an arrow or a small icon next to text shadowed on a
 * sky would otherwise stand without one. The shadow is the icon's shape blurred, drawn once for its size.
 */
public final class ShadowedDrawable extends Drawable {

    private final Drawable icon;
    private final float radius;
    private final float dx;
    private final float dy;
    private final int color;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int[] offset = new int[2];
    private Bitmap shadow;
    private int shadowWidth = -1;
    private int shadowHeight = -1;

    public ShadowedDrawable(Drawable icon, float radius, float dx, float dy, int color) {
        this.icon = icon;
        this.radius = radius;
        this.dx = dx;
        this.dy = dy;
        this.color = color;
    }

    /** The icon with the shadow of the text's, bounds kept; as it is when the text has no shadow */
    public static Drawable like(Drawable icon, TextView text) {
        if (icon instanceof ShadowedDrawable) {
            ShadowedDrawable old = (ShadowedDrawable) icon;
            if (old.radius == text.getShadowRadius() && old.color == text.getShadowColor()) {
                return icon;
            }
            icon = old.icon;
        }
        if (icon == null || text.getShadowRadius() <= 0) {
            return icon;
        }
        ShadowedDrawable shadowed = new ShadowedDrawable(icon, text.getShadowRadius(), text.getShadowDx(),
                text.getShadowDy(), text.getShadowColor());
        shadowed.setBounds(icon.getBounds());
        return shadowed;
    }

    /** The text's own icons shadowed as its text */
    public static void applyTo(TextView text) {
        Drawable[] icons = text.getCompoundDrawablesRelative();
        text.setCompoundDrawablesRelative(like(icons[0], text), like(icons[1], text), like(icons[2], text),
                like(icons[3], text));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        if (this.shadow == null || bounds.width() != this.shadowWidth || bounds.height() != this.shadowHeight) {
            makeShadow(bounds.width(), bounds.height());
        }
        if (this.shadow != null) {
            this.paint.setColor(this.color);
            canvas.drawBitmap(this.shadow, bounds.left + this.offset[0] + this.dx, bounds.top + this.offset[1] + this.dy,
                    this.paint);
        }
        this.icon.setBounds(bounds);
        this.icon.draw(canvas);
    }

    // The icon's shape at its size, blurred as the text's shadow is
    private void makeShadow(int width, int height) {
        this.shadowWidth = width;
        this.shadowHeight = height;
        if (this.shadow != null) {
            this.shadow.recycle();
        }
        Bitmap shape = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(this.icon.getBounds());
        this.icon.setBounds(0, 0, width, height);
        this.icon.draw(new Canvas(shape));
        this.icon.setBounds(bounds);
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(this.radius, BlurMaskFilter.Blur.NORMAL));
        this.shadow = shape.extractAlpha(blur, this.offset);
        shape.recycle();
    }

    @Override
    public int getIntrinsicWidth() {
        return this.icon.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return this.icon.getIntrinsicHeight();
    }

    @Override
    public void setAlpha(int alpha) {
        this.icon.setAlpha(alpha);
        this.paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        this.icon.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public void setTint(int tint) {
        this.icon.setTint(tint);
    }

    @Override
    public void setTintList(android.content.res.ColorStateList tint) {
        this.icon.setTintList(tint);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
