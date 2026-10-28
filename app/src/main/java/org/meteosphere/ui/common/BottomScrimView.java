package org.meteosphere.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * The shade at the bottom of a screen whose content runs under its buttons, as Samsung Weather has it: the sky and the
 * cards darken towards the screen's edge, so the white buttons and the navigation bar read over a bright sky. Clear at
 * its top, it eases in rather than growing evenly, so no edge shows where it starts.
 */
public class BottomScrimView extends View {

    private static final int STOPS = 8;
    // How dark it is at the screen's edge
    private static final float MAX_ALPHA = 0.45f;
    // How far it reaches over the top of the buttons, to fade out where nothing needs it
    private static final int ABOVE_DP = 56;

    private final Paint paint = new Paint();

    public BottomScrimView(Context context) {
        super(context);
    }

    public BottomScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Lies under buttons whose top stands this many pixels over the screen's bottom edge */
    public void reachOver(int buttonsTop) {
        ViewGroup.LayoutParams params = getLayoutParams();
        int height = buttonsTop + Math.round(ABOVE_DP * getResources().getDisplayMetrics().density);
        if (params.height != height) {
            params.height = height;
            setLayoutParams(params);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int[] colors = new int[STOPS];
        float[] positions = new float[STOPS];
        for (int i = 0; i < STOPS; i++) {
            float t = i / (float) (STOPS - 1);
            // Smoothstep: no line at the top where it starts, nor a flat band at the bottom
            float eased = t * t * (3 - 2 * t);
            colors[i] = Math.round(eased * MAX_ALPHA * 255) << 24;
            positions[i] = t;
        }
        this.paint.setShader(new LinearGradient(0, 0, 0, h, colors, positions, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), this.paint);
    }
}
