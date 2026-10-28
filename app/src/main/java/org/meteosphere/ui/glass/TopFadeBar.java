package org.meteosphere.ui.glass;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * The bar of a screen opened on its own with a tab, the Moon's, the Sun's or the earthquakes': clear over the tab,
 * which fills the screen under it. The tab's row of controls stays pinned under the bar, with no glass: the content
 * dissolves into the window's background as it scrolls up under them, under a light shade on a dark screen.
 */
public final class TopFadeBar {

    // How far under the row the content is seen fully again
    private static final int FADE_UNDER_ROW_DP = 48;
    // How dark the shade is at the top of a dark screen
    private static final float SHADE = 0.3f;

    private final GlassStrip bar;
    private final boolean shaded;
    private GlassStrip strip;
    private TopFade fade;

    private TopFadeBar(GlassStrip bar, boolean shaded) {
        this.bar = bar;
        this.shaded = shaded;
    }

    /** Pins the row of the tab shown under the bar; overSky for a screen over a sky, shaded whatever the theme */
    public static TopFadeBar attach(AppCompatActivity activity, GlassStrip bar, boolean overSky) {
        TopFadeBar fadeBar = new TopFadeBar(bar, overSky || !GlassStyle.of(activity).light);
        bar.setGlassAlpha(0);
        // Its height takes the status bar's once the insets come
        bar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> fadeBar.pin());
        activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment fragment, @NonNull View view, Bundle bundle) {
                GlassStrip found = GlassStrip.find(view);
                if (found != null) {
                    fadeBar.strip = found;
                    // The row's height is known once it is laid out
                    found.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> fadeBar.pin());
                    fadeBar.pin();
                }
            }
        }, false);
        return fadeBar;
    }

    /** The window's background changed, as a sky came: the fade shows it anew */
    public void onBackgroundChanged() {
        if (this.fade != null) {
            this.fade.invalidate();
        }
    }

    private void pin() {
        if (this.strip == null || this.bar.getHeight() == 0) {
            return;
        }
        this.strip.setUnderBar(this.bar.getHeight());
        if (this.fade == null) {
            // Under the row, over the content
            ViewGroup parent = (ViewGroup) this.strip.getParent();
            this.fade = new TopFade(this.strip.getContext());
            parent.addView(this.fade, parent.indexOfChild(this.strip), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (this.strip.getHeight() > 0) {
            // The content thins out from the bar's bottom, seen through the row a little, gone under the title
            int barBottom = this.bar.getHeight();
            int rowBottom = this.strip.getTop() + barBottom + this.strip.getHeight();
            int fadeUnderRow = Math.round(FADE_UNDER_ROW_DP * this.strip.getResources().getDisplayMetrics().density);
            this.fade.set(barBottom, rowBottom + fadeUnderRow, this.shaded ? SHADE : 0);
        }
    }
}
