package org.meteosphere.ui.common;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;

/**
 * Collapses the search expanded in the toolbar on back. The toolbar's own callback outranks the keyboard's,
 * so under predictive back (Android 16) the first back closed the whole search instead of hiding the keyboard.
 * This one has the default priority and lets the keyboard go first. Enable it while the search is expanded.
 */
public final class SearchBackCallback extends OnBackPressedCallback {

    private final Toolbar toolbar;

    /**
     * Create it after {@code setSupportActionBar()}, which turns the toolbar's own callback on,
     * and after the activity's {@link BackPressHandler} so that it runs first.
     */
    public SearchBackCallback(ComponentActivity activity, Toolbar toolbar) {
        super(false);
        this.toolbar = toolbar;
        toolbar.setBackInvokedCallbackEnabled(false);
        activity.getOnBackPressedDispatcher().addCallback(activity, this);
    }

    @Override
    public void handleOnBackPressed() {
        this.toolbar.collapseActionView();
    }
}
