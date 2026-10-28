package org.meteosphere.ui.common;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

/**
 * Custom back handling for an activity. Since API 36 the system no longer calls
 * {@code Activity.onBackPressed()}, so back logic has to go through the dispatcher.
 */
public abstract class BackPressHandler extends OnBackPressedCallback {

    private final ComponentActivity activity;

    /** Registers the handler; create it after {@code super.onCreate()} so that it runs before fragment back stacks. */
    public BackPressHandler(ComponentActivity activity) {
        super(true);
        this.activity = activity;
        activity.getOnBackPressedDispatcher().addCallback(activity, this);
    }

    /** Continues with the default back behavior: pops the fragment back stack or finishes the activity. */
    public final void proceed() {
        setEnabled(false);
        this.activity.getOnBackPressedDispatcher().onBackPressed();
        setEnabled(true);
    }
}
