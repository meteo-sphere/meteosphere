package org.meteosphere.ui.details;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AnimationUtils;
import java.util.List;

/** A soft band of light crossing the cards aslant as the forecast is updated, to see that it was */
final class UpdateFlash extends Drawable {

    private static final long DURATION_MS = 900;
    private static final int ALPHA = 80;

    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private float progress;

    private UpdateFlash(float radius) {
        this.radius = radius;
    }

    /** Crosses the cards shown in the page's frame with the light */
    static void play(List<View> cards, View page, float radius) {
        int pageHeight = page.getHeight();
        int[] pageAt = new int[2];
        page.getLocationInWindow(pageAt);
        int[] at = new int[2];
        for (View card : cards) {
            if (card.getVisibility() != View.VISIBLE || card.getWidth() == 0 || card.getHeight() == 0) {
                continue;
            }
            card.getLocationInWindow(at);
            int top = at[1] - pageAt[1];
            if (top + card.getHeight() < 0 || top > pageHeight) {
                continue;
            }
            UpdateFlash flash = new UpdateFlash(radius);
            flash.setBounds(0, 0, card.getWidth(), card.getHeight());
            card.getOverlay().add(flash);
            long startAt = AnimationUtils.currentAnimationTimeMillis();
            // Driven by the frames' time itself: the system's animations scale must not skip it
            card.postOnAnimation(new Runnable() {
                @Override
                public void run() {
                    float t = (AnimationUtils.currentAnimationTimeMillis() - startAt) / (float) DURATION_MS;
                    if (t >= 1) {
                        card.getOverlay().remove(flash);
                        return;
                    }
                    // Quick at first, settling at the end
                    flash.progress = 1 - (float) Math.pow(1 - Math.max(0, t), 2.4);
                    flash.invalidateSelf();
                    card.postOnAnimation(this);
                }
            });
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        this.rect.set(bounds);
        this.clip.reset();
        this.clip.addRoundRect(this.rect, this.radius, this.radius, Path.Direction.CW);
        float band = Math.max(bounds.width() * 0.5f, bounds.height() * 0.6f);
        float x = -band - bounds.height() * 0.5f + (bounds.width() + band + bounds.height()) * this.progress;
        // Fading in at the start and out at the end, not cut by the card's edges
        float fade = Math.min(1, Math.min(this.progress * 5, (1 - this.progress) * 4));
        int white = (Math.round(ALPHA * fade) << 24) | 0xFFFFFF;
        this.paint.setShader(new LinearGradient(x, 0, x + band, bounds.height() * 0.5f,
                new int[]{0x00FFFFFF, white, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipPath(this.clip);
        canvas.drawRect(this.rect, this.paint);
        canvas.restore();
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
