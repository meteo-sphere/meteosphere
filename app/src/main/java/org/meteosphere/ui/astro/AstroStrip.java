package org.meteosphere.ui.astro;

import org.meteosphere.ui.glass.GlassStrip;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import org.meteosphere.R;

/** The Moon's and the Sun's place and moment, pinned on glass over the top of their tab */
final class AstroStrip {

    private AstroStrip() {
    }

    /**
     * Adds the strip over the tab's content, in the tab's own night theme over its sky: its controls are white on the
     * sky, and its glass is dark, as the tab's cards are
     */
    static GlassStrip add(ViewGroup root, Context skyContext) {
        GlassStrip strip = (GlassStrip) LayoutInflater.from(skyContext).inflate(R.layout.view_astro_strip, root, false);
        root.addView(strip);
        return strip;
    }
}
