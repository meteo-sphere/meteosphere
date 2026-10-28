package org.meteosphere.ui.quakes;

import android.content.Context;
import android.provider.Settings;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/** The earthquake screens' motion: Material's curves, and the system's animation speed, none when it is off */
final class QuakeMotion {

    // Material 3's emphasized curves: what comes slows down into place, what goes speeds away
    static final Interpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    static final Interpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final Interpolator ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    // Android's standard curve, starting gentler than the emphasized one
    static final Interpolator GENTLE = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    // A dot or a ring popping a little past its size before it settles
    static final Interpolator POP = new OvershootInterpolator(1.8f);

    private QuakeMotion() {
    }

    /** The system's animator duration scale: 1 as made, 0 when animations are turned off */
    static float scale(Context context) {
        return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    }

    static boolean isOn(Context context) {
        return scale(context) > 0;
    }
}
