package org.meteosphere.ui.locations;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import android.widget.ImageView;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.meteosphere.ui.sky.SkyBackgrounds;

/**
 * The skies of the favourites' and the nearby places' cards: at half a card's size, as the sky is soft and a list of
 * skies stays small in memory, and cut near the top of the sky pictures, where their clouds are
 */
final class CardSkies {

    private static final float CROP = 0.12f;
    // How long a card's sky takes to fade to another
    private static final int FADE_MS = 300;

    // The points a sky's brightness is measured at
    private static final int GRID_COLUMNS = 24;
    private static final int GRID_ROWS = 12;

    private final SkyBackgrounds backgrounds;
    // The skies' brightness, while they are kept; on the main thread only
    private final Map<Bitmap, Float> brightness = new WeakHashMap<>();
    private final Resources resources;
    private final int cardHeight;
    private Point size;

    /** For cards as tall as the dimension given and as wide as their list less their margins */
    CardSkies(Context context, int cardHeightRes) {
        this.backgrounds = SkyBackgrounds.getInstance(context);
        this.resources = context.getResources();
        this.cardHeight = this.resources.getDimensionPixelSize(cardHeightRes);
    }

    /** The skies' size in pixels, by the list's width once it is laid out, else the screen's */
    Point size(View list) {
        if (this.size == null) {
            int width = list != null && list.getWidth() > 0 ? list.getWidth() : this.resources.getDisplayMetrics().widthPixels;
            int cardWidth = width - Math.round(32 * this.resources.getDisplayMetrics().density);
            this.size = new Point(Math.max(1, cardWidth / 2), this.cardHeight / 2);
        }
        return this.size;
    }

    /** The sky composed already, null until it is */
    Bitmap composed(List<String> layers, Point size) {
        return this.backgrounds.getComposed(layers, size, CROP);
    }

    void compose(List<String> layers, Point size, SkyBackgrounds.OnComposed onComposed) {
        this.backgrounds.compose(layers, size, CROP, onComposed);
    }

    /**
     * Shows the sky of the layers on a card: composed already, or its colour until it is. Fading, a card that had
     * another sky keeps it meanwhile, then fades to the new one. The shade under the texts, if any, gets as strong
     * as the sky is bright.
     */
    void bind(ImageView sky, View shade, List<String> layers, int color, View list, boolean fade) {
        // Kept by layers and by whether the animated weather drew it
        String tag = this.backgrounds.skyTag(layers);
        if (tag.equals(sky.getTag())) {
            return;
        }
        boolean hasSky = sky.getTag() != null;
        sky.setTag(tag);
        Point size = size(list);
        Bitmap composed = composed(layers, size);
        if (composed != null) {
            show(sky, composed, fade && hasSky);
            setShade(shade, brightness(composed));
            return;
        }
        if (!fade || !hasSky) {
            sky.setImageDrawable(new ColorDrawable(color));
            setShade(shade, luminance(color));
        }
        compose(layers, size, (composedLayers, bitmap) -> {
            if (tag.equals(sky.getTag())) {
                show(sky, bitmap, fade);
                setShade(shade, brightness(bitmap));
            }
        });
    }

    // A quarter on a dark sky, whole on a bright one: grey overcast, white clouds, fog. On the shade's own drawable,
    // as the view's alpha would draw it through an offscreen layer every frame
    private static void setShade(View shade, float brightness) {
        if (shade != null && shade.getBackground() != null) {
            float bright = Math.max(0f, Math.min(1f, (brightness - 0.3f) / 0.4f));
            shade.getBackground().mutate().setAlpha(Math.round(255 * (0.25f + 0.75f * bright)));
        }
    }

    private static float luminance(int color) {
        return (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255;
    }

    // The sky's mean brightness, 0 to 1, from a grid of its pixels, measured once a sky
    private float brightness(Bitmap bitmap) {
        Float known = this.brightness.get(bitmap);
        if (known != null) {
            return known;
        }
        float sum = 0;
        for (int y = 0; y < GRID_ROWS; y++) {
            for (int x = 0; x < GRID_COLUMNS; x++) {
                sum += luminance(bitmap.getPixel(x * (bitmap.getWidth() - 1) / (GRID_COLUMNS - 1),
                        y * (bitmap.getHeight() - 1) / (GRID_ROWS - 1)));
            }
        }
        float res = sum / (GRID_ROWS * GRID_COLUMNS);
        this.brightness.put(bitmap, res);
        return res;
    }

    private static void show(ImageView sky, Bitmap bitmap, boolean fade) {
        Drawable shown = sky.getDrawable();
        if (!fade || shown == null) {
            sky.setImageBitmap(bitmap);
            return;
        }
        // From the sky the card shows, the new one of a fade still running
        if (shown instanceof TransitionDrawable) {
            shown = ((TransitionDrawable) shown).getDrawable(1);
        }
        TransitionDrawable transition = new TransitionDrawable(new Drawable[]{shown, new BitmapDrawable(sky.getResources(), bitmap)});
        transition.setCrossFadeEnabled(true);
        sky.setImageDrawable(transition);
        transition.startTransition(FADE_MS);
    }
}
