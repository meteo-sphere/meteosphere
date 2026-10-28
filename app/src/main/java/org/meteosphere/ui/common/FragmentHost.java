package org.meteosphere.ui.common;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import org.meteosphere.R;

/**
 * A tab opened on its own, in activity_fragment_host, as from the city details: its lists run under the navigation bar
 * as the main screen's tabs do, fading out towards the screen's edge, over a shade when the tab is dark.
 */
public final class FragmentHost {

    // How far over the navigation bar the content fades out
    private static final int FADE_OVER_BAR_DP = 32;

    private FragmentHost() {
    }

    /** As EdgeToEdgeUtils.applyToolbarInsets, with the tab's lists under the navigation bar; shaded on a dark tab */
    public static void applyInsets(Activity activity, View toolbar, boolean shaded) {
        ViewGroup host = activity.findViewById(R.id.flFragment);
        BottomFadeFrame fade = (BottomFadeFrame) host.getParent();
        BottomScrimView scrim = activity.findViewById(R.id.bottomScrim);
        int fadeOverBar = Math.round(FADE_OVER_BAR_DP * activity.getResources().getDisplayMetrics().density);
        int[] room = {0};
        // The tab's view comes after the insets: its lists get their room as it is added
        host.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                ListRoom.atEnd(child, room[0]);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
        EdgeToEdgeUtils.applyToolbarInsets(activity, toolbar, bottom -> {
            room[0] = bottom;
            for (int i = 0; i < host.getChildCount(); i++) {
                ListRoom.atEnd(host.getChildAt(i), bottom);
            }
            // Nothing lies over the content while the keyboard is up
            fade.setFadeLength(bottom > 0 ? bottom + fadeOverBar : 0);
            scrim.setVisibility(shaded && bottom > 0 ? View.VISIBLE : View.GONE);
            scrim.reachOver(bottom);
            return true;
        });
    }
}
