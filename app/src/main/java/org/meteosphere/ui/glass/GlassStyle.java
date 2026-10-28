package org.meteosphere.ui.glass;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;

/**
 * The colours of the frosted glass, after the app's theme: dark glass with white text in the dark theme, light glass
 * with dark text in the light one. The theme is the activity's, not the view's: the Moon and Sun pages are drawn in a
 * night theme over their skies, and the glass over them still follows the app.
 */
public final class GlassStyle {

    final boolean light;
    public final int text;
    final int textDim;
    final int checkedFill;
    // The tabs' toolbar: its chosen tab's pill and the other tabs' icons
    public final int barChecked;
    public final int barIconDim;
    public final int ripple;
    final int divider;
    // The edge: lighter where the light falls on it, at the top
    public final int edgeTop;
    public final int edgeBottom;
    // Light glass on a light page stands out by its shadow; on the dark glass it hardly shows
    final float shadowAlpha;

    private GlassStyle(boolean light) {
        this.light = light;
        if (light) {
            this.text = 0xff181c22;
            this.textDim = 0xc7181c22;
            this.checkedFill = 0x1f181c22;
            this.barChecked = 0x1f181c22;
            this.barIconDim = 0xd9181c22;
            this.ripple = 0x1f181c22;
            this.divider = 0x1f181c22;
            this.edgeTop = 0xe6ffffff;
            this.edgeBottom = 0x1a181c22;
            this.shadowAlpha = 0.9f;
        } else {
            this.text = 0xffffffff;
            this.textDim = 0xc7ffffff;
            this.checkedFill = 0x29ffffff;
            this.barChecked = 0x33ffffff;
            this.barIconDim = 0xd9ffffff;
            this.ripple = 0x33ffffff;
            this.divider = 0x26ffffff;
            this.edgeTop = 0x2effffff;
            this.edgeBottom = 0x0fffffff;
            this.shadowAlpha = 0.5f;
        }
    }

    /**
     * The glass of the context's own theme rather than the activity's: a strip pinned on the Moon's or the Sun's tab
     * belongs to the tab's night theme over its sky, and is dark glass as the tab's cards are
     */
    static GlassStyle ofOwnTheme(Context context) {
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return new GlassStyle(night != Configuration.UI_MODE_NIGHT_YES);
    }

    /** The dark glass, for white content over a sky whatever the theme */
    static GlassStyle dark() {
        return new GlassStyle(false);
    }

    public static GlassStyle of(Context context) {
        Context themed = activityOf(context);
        int night = (themed != null ? themed : context).getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return new GlassStyle(night != Configuration.UI_MODE_NIGHT_YES);
    }

    /** The glass's tint: denser under a menu's text than under the tabs' icons, and denser still without the blur */
    public int tint(boolean menu) {
        if (!GlassBlur.SUPPORTED) {
            return this.light ? 0xf2f8f9ff : menu ? 0xf2161a22 : 0xd910141c;
        }
        if (this.light) {
            return menu ? 0xbff8f9ff : 0xa6f8f9ff;
        }
        return menu ? 0x9910141c : 0x7510141c;
    }

    static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
