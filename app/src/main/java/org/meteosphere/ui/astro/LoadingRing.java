package org.meteosphere.ui.astro;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.progressindicator.CircularProgressIndicator;

/**
 * The ring of a picture loading, shown only when it takes a while, gone at once when it is done. It sets its
 * visibility itself rather than through Material's show() and hide(): their delays and hide animation lost track on
 * details pages swiped away and back, and a ring left after its picture had loaded turned for good.
 */
public class LoadingRing extends CircularProgressIndicator {

    private static final long SHOW_DELAY_MS = 350;

    private final Runnable delayedShow = () -> setVisibility(VISIBLE);

    public LoadingRing(Context context) {
        super(context);
    }

    public LoadingRing(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void show() {
        removeCallbacks(this.delayedShow);
        postDelayed(this.delayedShow, SHOW_DELAY_MS);
    }

    @Override
    public void hide() {
        removeCallbacks(this.delayedShow);
        setVisibility(GONE);
    }

    // Taken off the window, as a details page swiped away: a show waiting there couldn't be called off by hide()
    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(this.delayedShow);
        super.onDetachedFromWindow();
    }
}
