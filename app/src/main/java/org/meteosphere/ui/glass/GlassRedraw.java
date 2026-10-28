package org.meteosphere.ui.glass;

import android.view.View;
import android.view.ViewTreeObserver;

/** Has a piece of glass drawn again when what lies under it scrolls or is laid out again */
public final class GlassRedraw {

    private final View view;
    // Scrolling lists report it even when they move their children rather than their scroll
    private final ViewTreeObserver.OnScrollChangedListener onScrollChanged;
    private final ViewTreeObserver.OnGlobalLayoutListener onGlobalLayout;

    public GlassRedraw(View view) {
        this(view, view::invalidate);
    }

    /** On a scroll it does what it is given, which draws the glass again among the rest */
    public GlassRedraw(View view, Runnable onScroll) {
        this.view = view;
        this.onScrollChanged = onScroll::run;
        this.onGlobalLayout = view::invalidate;
    }

    /** From the view's onAttachedToWindow */
    public void attach() {
        this.view.getViewTreeObserver().addOnScrollChangedListener(this.onScrollChanged);
        this.view.getViewTreeObserver().addOnGlobalLayoutListener(this.onGlobalLayout);
    }

    /** From the view's onDetachedFromWindow */
    public void detach() {
        this.view.getViewTreeObserver().removeOnScrollChangedListener(this.onScrollChanged);
        this.view.getViewTreeObserver().removeOnGlobalLayoutListener(this.onGlobalLayout);
    }
}
