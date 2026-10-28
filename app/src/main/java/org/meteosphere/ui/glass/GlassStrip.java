package org.meteosphere.ui.glass;

import org.meteosphere.ui.main.HeaderScroll;
import org.meteosphere.ui.sky.SkyBackgroundDrawable;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.R;
import org.meteosphere.ui.common.ListRoom;

/**
 * A band across the top over content that scrolls under it, whose frosted glass fades in only as the content runs
 * under it: the main screen's bar over the search. What lies under the glass is blurred from Android 12, redrawn only
 * when something scrolls or is laid out again; before that its tint stands alone, denser. Over the tabs the bar shows
 * a plain see-through band instead, as much of it as the glass would show (setBand).
 *
 * A tab's row of controls has no glass: a TopFade under it hides the content. Given its list, it stands under the main
 * screen's title at first and keeps the list's top clear of both; as the list scrolls up it rises into the title's
 * place under the status bar and stays there. On a screen whose bar stays, it stays pinned under the bar.
 */
public class GlassStrip extends LinearLayout {

    /** Gives the views blurred under the strip as it is drawn, those that may be under it now */
    public interface BlurSources {
        void collect(List<View> into);
    }

    /** Told where the list under the strip starts, under the title and the strip as they first stand */
    public interface OnContentTopListener {
        void onContentTop(int top);
    }

    private final GlassBlur glass;
    private int tint;
    // How much of the glass, or of the band in its place, shows, 0 to 1
    private float glassAlpha = 1f;
    // The status bar's height over the strip's top: the blur isn't drawn under the status bar, so there the glass
    // shows the window's background, the sky or the plain colour, under its tint, fading into the blur under it
    private int statusInset;
    private final Paint tintPaint = new Paint();
    private final Paint fadePaint = new Paint();
    private final int[] location = new int[2];
    private int fadeHeight = -1;
    private BlurSources blurSources;
    private final ArrayList<View> sources = new ArrayList<>();
    private View list;
    // The main screen's title over a tab's row at first, which the row rises into as the list scrolls
    private int titleHeight;
    private int ownPaddingTop = -1;
    private OnContentTopListener contentTopListener;
    // Pinned under a screen's bar that stays, rather than rising into a title's place
    private boolean underBar;
    // A TopFade under the row hides the content rather than glass
    private boolean withoutGlass;
    // Instead of glass, a plain band of this colour, see-through as its own alpha says; 0 for glass
    private int bandColor;
    private final Paint bandPaint = new Paint();
    // The list under it followed as it scrolls, which draws the glass again too
    private final GlassRedraw redraw = new GlassRedraw(this, this::followList);

    public GlassStrip(Context context) {
        this(context, null);
    }

    public GlassStrip(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.glass = new GlassBlur(GlassBlur.BLUR_DP * getResources().getDisplayMetrics().density);
        // The menus' denser tint, as the strip carries text and controls, of the strip's own theme
        this.tint = GlassStyle.ofOwnTheme(context).tint(true);
        setWillNotDraw(false);
        // Taps on the strip don't reach the list under it
        setClickable(true);
        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (top != oldTop || bottom != oldBottom) {
                keepListClear();
                followList();
            }
        });
    }

    /** How much of the glass shows, 0 for none, the background clear, to 1 */
    public void setGlassAlpha(float alpha) {
        alpha = Math.max(0f, Math.min(1f, alpha));
        if (alpha != this.glassAlpha) {
            this.glassAlpha = alpha;
            // A row without glass has nothing that changes with it
            if (!this.withoutGlass) {
                invalidate();
            }
        }
    }

    /** The status bar's height over the top of the window, where the glass shows the window's background */
    public void setStatusInset(int inset) {
        if (inset != this.statusInset) {
            this.statusInset = inset;
            invalidate();
        }
    }

    /** The view blurred under the strip, over the window's background */
    public void setBlurSource(View source) {
        setBlurSources(source == null ? null : into -> into.add(source));
    }

    /**
     * The views blurred under the strip, over the window's background. Glass blurs only what scrolls under it, not
     * other glass, so that two strips meet seamlessly.
     */
    public void setBlurSources(BlurSources sources) {
        this.blurSources = sources;
        invalidate();
    }

    /** The list under the strip: it is blurred, its top keeps clear of the strip, and the strip follows its scroll */
    public void setList(View list) {
        this.list = list;
        setBlurSource(list);
        keepListClear();
        followList();
    }

    /**
     * Puts the strip under the main screen's title, the status bar over both: the strip's top padding takes the
     * status bar's room, for when it has risen into the title's place
     */
    public void setHeader(int statusInset, int titleHeight) {
        if (this.ownPaddingTop < 0) {
            this.ownPaddingTop = getPaddingTop();
        }
        this.titleHeight = titleHeight;
        setStatusInset(statusInset);
        if (getPaddingTop() != this.ownPaddingTop + statusInset) {
            setPadding(getPaddingLeft(), this.ownPaddingTop + statusInset, getPaddingRight(), getPaddingBottom());
        }
        keepListClear();
        followList();
    }

    /**
     * Pins the strip under a screen's bar that stays, as tall as given with the status bar: the strip doesn't rise, and
     * has no glass, a TopFade under it hiding the content instead
     */
    void setUnderBar(int barHeight) {
        this.underBar = true;
        this.titleHeight = barHeight;
        setWithoutGlass();
        keepListClear();
        followList();
    }

    /** No glass: a TopFade under the strip hides the content instead */
    public void setWithoutGlass() {
        this.withoutGlass = true;
        invalidate();
    }

    /** A plain band of the colour, its alpha how see-through it is, in place of the glass while given; 0 for glass */
    public void setBand(int color) {
        if (color != this.bandColor) {
            this.bandColor = color;
            invalidate();
        }
    }

    /** The first strip in the tree, the view itself first; null if there is none */
    public static GlassStrip find(View view) {
        if (view instanceof GlassStrip) {
            return (GlassStrip) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                GlassStrip strip = find(group.getChildAt(i));
                if (strip != null) {
                    return strip;
                }
            }
        }
        return null;
    }

    /** The list under the strip, null for none */
    public View getList() {
        return this.list;
    }

    /** Where the strip lies in the window with its list at the top, however far the list has scrolled */
    public void getRestingLocationInWindow(int[] location) {
        getLocationInWindow(location);
        location[1] += Math.round(this.titleHeight - getTranslationY());
    }

    public void setOnContentTopListener(OnContentTopListener listener) {
        this.contentTopListener = listener;
        keepListClear();
    }

    // Under the title, risen into its place as far as the list has scrolled, its glass as the content runs under it
    private void followList() {
        if (this.list == null) {
            invalidate();
            return;
        }
        int depth = HeaderScroll.depth(this.list);
        float shift = this.underBar ? this.titleHeight : this.titleHeight - HeaderScroll.collapse(depth, this.titleHeight);
        if (getTranslationY() != shift) {
            setTranslationY(shift);
        }
        setGlassAlpha(HeaderScroll.glass(getContext(), depth, this.underBar ? 0 : this.titleHeight));
        if (!this.withoutGlass) {
            invalidate();
        }
    }

    private void keepListClear() {
        int contentTop = this.titleHeight + (getVisibility() == GONE ? 0 : getHeight());
        if (this.list != null) {
            ListRoom.setTop(this.list, contentTop);
        }
        if (this.contentTopListener != null) {
            this.contentTopListener.onContentTop(contentTop);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.redraw.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.redraw.detach();
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(Canvas canvas) {
        if (this.glassAlpha > 0 && this.bandColor != 0) {
            this.bandPaint.setColor(this.bandColor);
            this.bandPaint.setAlpha(Math.round((this.bandColor >>> 24) * this.glassAlpha));
            canvas.drawRect(0, 0, getWidth(), getHeight(), this.bandPaint);
        } else if (this.glassAlpha > 0 && !this.withoutGlass) {
            drawGlass(canvas, this.glassAlpha);
        }
        super.draw(canvas);
    }

    private void drawGlass(Canvas canvas, float alpha) {
        this.sources.clear();
        if (this.blurSources != null) {
            this.blurSources.collect(this.sources);
        }
        this.glass.draw(canvas, this, this.sources, 0, alpha);
        int width = getWidth();
        int height = getHeight();
        // The status bar's band, where it is over the strip now
        getLocationInWindow(this.location);
        int bandTop = Math.max(0, -this.location[1]);
        int bandBottom = Math.min(height, this.statusInset - this.location[1]);
        Drawable background = getRootView().getBackground();
        boolean underStatusBar = bandBottom > bandTop && background != null && !isInEditMode();
        if (underStatusBar) {
            // Drawn straight, not through a layer: a layer isn't drawn under the status bar either
            int saved = canvas.save();
            canvas.clipRect(0, bandTop, width, bandBottom);
            drawWindowBackground(canvas, background, alpha);
            canvas.restoreToCount(saved);
        }
        if (underStatusBar && bandBottom < height) {
            // The background fades into the blur over the next stretch under the status bar
            int fade = Math.max(1, Math.min(height - bandBottom, this.statusInset));
            if (this.fadeHeight != fade) {
                this.fadeHeight = fade;
                this.fadePaint.setShader(new LinearGradient(0, 0, 0, fade, 0xff000000, 0x00000000, Shader.TileMode.CLAMP));
                this.fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            }
            int saved = canvas.saveLayer(0, bandBottom, width, bandBottom + fade, null);
            drawWindowBackground(canvas, background, alpha);
            canvas.translate(0, bandBottom);
            canvas.drawRect(0, 0, width, fade, this.fadePaint);
            canvas.restoreToCount(saved);
        }
        // The tint once over all of it: tinted in the fade's layer as well, the stretch under the status bar came out
        // darker while the glass faded in
        this.tintPaint.setColor(this.tint);
        this.tintPaint.setAlpha(Math.round(((this.tint >>> 24) & 0xff) * alpha));
        canvas.drawRect(0, 0, width, height, this.tintPaint);
    }

    // The window's background where it lies under the strip
    private void drawWindowBackground(Canvas canvas, Drawable background, float alpha) {
        drawWindowBackground(canvas, background, this.location, alpha);
    }

    /**
     * The window's background where it lies under a view at location in the window, as much as alpha shows it: a sky
     * at any alpha, another background only whole
     */
    static void drawWindowBackground(Canvas canvas, Drawable background, int[] location, float alpha) {
        int saved = canvas.save();
        canvas.translate(-location[0], -location[1]);
        if (background instanceof SkyBackgroundDrawable) {
            ((SkyBackgroundDrawable) background).draw(canvas, alpha);
        } else if (alpha >= 1) {
            background.draw(canvas);
        }
        canvas.restoreToCount(saved);
    }
}
