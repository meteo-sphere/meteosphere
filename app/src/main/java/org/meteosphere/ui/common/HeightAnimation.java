package org.meteosphere.ui.common;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/** A settings row that opens and closes: its height grows from the one it has to its content's or shrinks to none */
public final class HeightAnimation extends Animation {

    private static final long DURATION_MS = 300;

    private final View view;
    private final int from;
    private final int to;

    private HeightAnimation(View view, int to) {
        this.view = view;
        this.from = view.getHeight();
        this.to = to;
        setDuration(DURATION_MS);
    }

    /** Opens the view to the height its content wants at the width it has */
    public static void expand(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.startAnimation(new HeightAnimation(view, view.getMeasuredHeight()));
    }

    /** Closes the view to no height */
    public static void collapse(View view) {
        view.startAnimation(new HeightAnimation(view, 0));
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        this.view.getLayoutParams().height = (int) (this.from + (this.to - this.from) * interpolatedTime);
        this.view.requestLayout();
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }
}
