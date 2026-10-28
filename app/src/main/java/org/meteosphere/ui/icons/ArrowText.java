package org.meteosphere.ui.icons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import androidx.core.content.ContextCompat;
import org.meteosphere.R;

/**
 * Draws the arrows of a text (↑ ↓ ↗ ↘ →) as the app's arrow icons: the font's arrows are thin and small next to
 * the digits. An arrow is as tall as the digits, in the text's colour and with its shadow, and the text itself stays
 * as it was, so it still reads the same where spans aren't shown.
 */
public final class ArrowText {

    private static final char[] ARROWS = {'↑', '↓', '↗', '↘', '→'};
    private static final int[] ICONS = {R.drawable.ic_arrow_up, R.drawable.ic_arrow_down, R.drawable.ic_arrow_up_right,
            R.drawable.ic_arrow_down_right, R.drawable.ic_arrow_right};
    // The arrow's box against the text size, and its centre above the baseline: the middle of the digits
    private static final float SIZE = 1.12f;
    private static final float CENTER = 0.36f;
    private static final float GAP = 0.06f;

    private ArrowText() {
    }

    /** The text with its arrows drawn as icons; the text as it is when it has none */
    public static CharSequence of(Context context, CharSequence text) {
        if (text == null) {
            return null;
        }
        SpannableString spannable = null;
        for (int i = 0; i < text.length(); i++) {
            int kind = kindOf(text.charAt(i));
            if (kind < 0) {
                continue;
            }
            if (spannable == null) {
                spannable = new SpannableString(text);
            }
            Drawable icon = ContextCompat.getDrawable(context, ICONS[kind]);
            if (icon != null) {
                spannable.setSpan(new ArrowSpan(icon.mutate()), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return spannable != null ? spannable : text;
    }

    private static int kindOf(char c) {
        for (int k = 0; k < ARROWS.length; k++) {
            if (ARROWS[k] == c) {
                return k;
            }
        }
        return -1;
    }

    private static final class ArrowSpan extends ReplacementSpan {
        private final Drawable icon;
        // The icon with the text's shadow, once the text is seen to have one
        private Drawable shadowed;
        private float shadowedRadius;
        private int shadowedColor;

        ArrowSpan(Drawable icon) {
            this.icon = icon;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                // The line keeps the height of its text
                paint.getFontMetricsInt(fm);
            }
            return Math.round(paint.getTextSize() * (SIZE + 2 * GAP));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            float size = paint.getTextSize() * SIZE;
            float left = x + paint.getTextSize() * GAP;
            float centerY = y - paint.getTextSize() * CENTER;
            Drawable drawn = this.icon;
            // The text's shadow is known from its paint from Android 10
            if (Build.VERSION.SDK_INT >= 29 && paint.getShadowLayerRadius() > 0) {
                if (this.shadowed == null || this.shadowedRadius != paint.getShadowLayerRadius()
                        || this.shadowedColor != paint.getShadowLayerColor()) {
                    this.shadowedRadius = paint.getShadowLayerRadius();
                    this.shadowedColor = paint.getShadowLayerColor();
                    this.shadowed = new ShadowedDrawable(this.icon, paint.getShadowLayerRadius(), paint.getShadowLayerDx(),
                            paint.getShadowLayerDy(), paint.getShadowLayerColor());
                }
                drawn = this.shadowed;
            }
            drawn.setBounds(Math.round(left), Math.round(centerY - size / 2), Math.round(left + size),
                    Math.round(centerY + size / 2));
            // The tint carries the text colour's alpha too
            this.icon.setTint(paint.getColor());
            drawn.draw(canvas);
        }
    }
}
