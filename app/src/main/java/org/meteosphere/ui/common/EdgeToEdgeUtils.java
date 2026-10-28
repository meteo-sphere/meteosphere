package org.meteosphere.ui.common;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Since API 35 activities are drawn behind the system bars; these helpers keep the layout clear of them. */
public final class EdgeToEdgeUtils {

    private EdgeToEdgeUtils() {
    }

    // Drawn behind the system bars, with no band of Android's behind the navigation buttons: the content, the shade
    // under the tabs' bar or the window's background shows there, the buttons' colour following the theme
    private static void enable(Activity activity) {
        if (activity instanceof ComponentActivity) {
            EdgeToEdge.enable((ComponentActivity) activity);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * Extends the toolbar behind the status bar and keeps the rest of the content clear of the other system bars
     * and of the keyboard, which no longer resizes the window.
     * Also makes the system bars transparent with icons matching the light/dark theme on every API level.
     */
    public static void applyToolbarInsets(Activity activity, final View toolbar) {
        applyToolbarInsets(activity, toolbar, null);
    }

    /** Told the height of the navigation bar, 0 while the keyboard is up; answers whether the content runs under it now */
    public interface OnNavigationBarInset {
        boolean onNavigationBarInset(int bottom);
    }

    /**
     * As applyToolbarInsets, with the content running under the navigation bar when a listener is given: it is told
     * the bar's height, for its scrolling views to keep that much room at their end. The keyboard still lifts the
     * content.
     */
    public static void applyToolbarInsets(Activity activity, final View toolbar, final OnNavigationBarInset underNavigationBar) {
        enable(activity);
        View content = activity.findViewById(android.R.id.content);
        final int toolbarPaddingTop = toolbar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(content, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat windowInsets) {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
                toolbar.setPadding(toolbar.getPaddingLeft(), toolbarPaddingTop + bars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());
                int keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                if (underNavigationBar != null && underNavigationBar.onNavigationBarInset(keyboard > 0 ? 0 : bars.bottom)) {
                    view.setPadding(bars.left, 0, bars.right, keyboard);
                } else {
                    view.setPadding(bars.left, 0, bars.right, bars.bottom);
                }
                return WindowInsetsCompat.CONSUMED;
            }
        });
    }

    /** Told the heights of the navigation bar and of the keyboard, 0 when it is down, over the bottom of the content */
    public interface OnBottomInsets {
        void onBottomInsets(int navigationBar, int keyboard);
    }

    /**
     * As applyToolbarInsets, with the keyboard lying over the screen rather than cutting it short: the content runs
     * under it, and under the navigation bar too if underNavigationBar, else it is kept clear of the bar. The listener
     * gives the scrolling views room at their end for what lies over them.
     */
    public static void applyToolbarInsetsUnderKeyboard(Activity activity, final View toolbar, final boolean underNavigationBar,
                                                       final OnBottomInsets listener) {
        enable(activity);
        View content = activity.findViewById(android.R.id.content);
        final int toolbarPaddingTop = toolbar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            toolbar.setPadding(toolbar.getPaddingLeft(), toolbarPaddingTop + bars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());
            view.setPadding(bars.left, 0, bars.right, underNavigationBar ? 0 : bars.bottom);
            listener.onBottomInsets(bars.bottom, windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
