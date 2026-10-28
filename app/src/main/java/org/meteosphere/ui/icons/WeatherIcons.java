package org.meteosphere.ui.icons;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import org.meteosphere.R;

/**
 * The weather icons: vector drawables drawn by tools/weather_icons.py, named by the parts of the weather like
 * d.c3.r2.st, each in the colours of the dark surfaces and the skies and in deeper ones that read on a light surface
 */
public final class WeatherIcons {

    /** Mist over another weather icon, of its size: a band of it along the bottom */
    public static final String MIST_OVERLAY = "mist_overlay";
    private static final String NO_DATA = "nodata";
    // The icons' box: 64 by 48
    private static final float HEIGHT_TO_WIDTH = 48f / 64f;

    private static WeatherIcons instance = null;
    private final Resources resources;
    // The drawables by the icons' names: for the dark surfaces and for the light ones
    private final HashMap<String, int[]> icons = new HashMap<>();

    private WeatherIcons(Context context) {
        this.resources = context.getApplicationContext().getResources();
        String[] names = this.resources.getStringArray(R.array.weather_icon_names);
        TypedArray dark = this.resources.obtainTypedArray(R.array.weather_icons);
        TypedArray light = this.resources.obtainTypedArray(R.array.weather_icons_light);
        for (int i = 0; i < names.length; i++) {
            this.icons.put(names[i], new int[]{dark.getResourceId(i, 0), light.getResourceId(i, 0)});
        }
        dark.recycle();
        light.recycle();
    }

    public static synchronized WeatherIcons getInstance(Context context) {
        if (instance == null) {
            instance = new WeatherIcons(context);
        }
        return instance;
    }

    /** The icon's drawable resource; nodata's for a weather the set has no icon of */
    public int getResource(String iconName, boolean lightSurface) {
        int[] icon = iconName != null ? this.icons.get(iconName) : null;
        if (icon == null) {
            icon = this.icons.get(NO_DATA);
        }
        return icon[lightSurface ? 1 : 0];
    }

    /**
     * The icon as a drawable of its own. A vector keeps a picture of the size it was last drawn at, shared by the
     * drawables of its resource until they are mutated: drawn at another size somewhere else, a shared one would be
     * drawn again each time or scaled
     */
    public Drawable getWeatherDrawable(String iconName, boolean lightSurface) {
        return this.resources.getDrawable(getResource(iconName, lightSurface), null).mutate();
    }

    /** The icon at the top of a square, as the widgets' and the notification's cells show it, at any size */
    public Drawable getSquareWeatherDrawable(String iconName) {
        return getSquareWeatherDrawable(iconName, false);
    }

    /** As getSquareWeatherDrawable, in the colours of the dark surfaces or the deeper ones of the light */
    public Drawable getSquareWeatherDrawable(String iconName, boolean lightSurface) {
        return new SquareIcon(getWeatherDrawable(iconName, lightSurface));
    }

    // The icon at the top of a square, drawn at whatever size the square is
    private static final class SquareIcon extends Drawable {
        private final Drawable icon;

        SquareIcon(Drawable icon) {
            this.icon = icon;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            this.icon.setBounds(bounds.left, bounds.top, bounds.right, bounds.top + Math.round(bounds.width() * HEIGHT_TO_WIDTH));
        }

        @Override
        public void draw(Canvas canvas) {
            this.icon.draw(canvas);
        }

        @Override
        public int getIntrinsicWidth() {
            return this.icon.getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return this.icon.getIntrinsicWidth();
        }

        @Override
        public void setAlpha(int alpha) {
            this.icon.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            this.icon.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
