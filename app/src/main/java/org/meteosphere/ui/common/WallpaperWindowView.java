package org.meteosphere.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;

/**
 * A window in the screen onto the home screen's wallpaper: it clears what the views under it painted, as a SurfaceView
 * clears its hole, so the wallpaper the system draws behind the screen shows through. The screen's theme shows the
 * wallpaper and has no background of its own; what lies over this view is drawn after it and stays.
 */
public class WallpaperWindowView extends View {

    public WallpaperWindowView(Context context) {
        super(context);
    }

    public WallpaperWindowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }
}
