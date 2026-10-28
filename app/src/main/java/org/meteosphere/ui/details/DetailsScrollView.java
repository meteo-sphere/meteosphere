package org.meteosphere.ui.details;

import android.content.Context;
import android.util.AttributeSet;
import androidx.core.widget.NestedScrollView;

/**
 * The page's scroll: the cards fade out under the status bar and the pill at the top, and under the clear bar at the
 * bottom, its buttons drawn straight over them. The bottom fade has a length of its own.
 */
public class DetailsScrollView extends NestedScrollView {

    private int bottomFadeLength;

    public DetailsScrollView(Context context) {
        super(context);
    }

    public DetailsScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** How high the cards fade out from the screen's bottom edge */
    void setBottomFadeLength(int length) {
        this.bottomFadeLength = length;
        invalidate();
    }

    // The fade's length is the top's scaled by this, so it stays put even at the end of the cards
    @Override
    protected float getBottomFadingEdgeStrength() {
        int length = getVerticalFadingEdgeLength();
        return length > 0 ? Math.min(1f, this.bottomFadeLength / (float) length) : 0;
    }
}
