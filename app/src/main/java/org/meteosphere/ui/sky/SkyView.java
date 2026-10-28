package org.meteosphere.ui.sky;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RecordingCanvas;
import android.media.Image;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.animation.AnimationUtils;
import androidx.appcompat.widget.AppCompatImageView;
import java.util.Random;
import org.meteosphere.BuildConfig;
import org.meteosphere.R;
import org.meteosphere.ui.glass.GlassBlur;

/**
 * The weather's sky behind the city details. With the animated weather on, as it is by default from Android 13,
 * it is drawn live: the clouds drift and change shape, rain and snow fall, stars twinkle, lightning flashes. The sky
 * and its clouds are SkyRenderer's frames, drawn half the size with OpenGL and stretched over the view, being soft;
 * the stars and the drops are drawn over them at full size by shaders, being sharp. Otherwise, and until it is given
 * a scene, it is an ImageView showing the composed picture.
 */
public class SkyView extends AppCompatImageView {

    private static final float CLOUD_SCALE = 1 / 2f;
    // Steps through the clouds each frame
    private static final float CLOUD_STEPS = 64;
    // Frames drawn more after a fresh start while the sky stands still, each smoothing the average further
    private static final int SETTLE_FRAMES = 10;
    // The sky is drawn again at most this often, the drops falling, and the clouds with the stars as often: each frame
    // of the clouds marches one pixel in sixteen, so that a pixel's own marches come about twice a second. Every frame
    // of the sky draws the whole screen over it again, glass and all.
    private static final long FRAME_NS = 1_000_000_000L / 30;
    private static final long CLOUD_FRAME_NS = 1_000_000_000L / 30;
    // A frame a little early still counts, frames not falling exactly on the periods
    private static final long FRAME_SLACK_NS = 4_000_000L;
    // How fast the wind carries the clouds, in the sky's units a second (their base is one unit up, so the near
    // ones cross the view faster), and how fast they change shape. Not wrapped: the shaders scale both by uneven
    // factors, so any wrap would make every cloud jump.
    static final float WIND = 0.035f;
    static final float CHANGE = 0.012f;

    private SkyScene scene;
    // The scene's number, counted over all the views, and the shown frame's: a frame of the weather before isn't shown
    private static long weathers;
    private long weather;
    private long frameWeather = -1;
    // When it was first drawn without a frame of its weather, the window's colour showing instead: 0 not yet, -1 once a
    // frame of it has been drawn
    private long blankSinceMs;
    private SkyShaders shaders;
    // The renderer's frames for this view's size, and the last two frames come: the one drawn and the one before,
    // which the system may still be drawing
    private SkyRenderer.Target target;
    private Bitmap frame;
    private Image shownImage;
    private Image previousImage;
    private final Paint framePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect frameSource = new Rect();
    private final Rect frameDestination = new Rect();
    private Paint starsPaint;
    private final Paint dropsPaint = new Paint();
    private final Paint moonPaint = new Paint();
    private final RectF moonBounds = new RectF();
    // Debug builds: the renderer's GPU timings, over the sky, as lines of the text they were split from
    private Paint timingsPaint;
    private String timingsText;
    private String[] timingsLines;
    // A frame of the clouds to ask for, and whether afresh, the average forgotten
    private boolean cloudsDirty = true;
    private boolean fresh = true;
    private int settle;
    private float crop = 0.5f;
    private float clearTop = SkyShaders.CLEAR_NOWHERE;
    private float clearBottom = SkyShaders.CLEAR_NOWHERE;
    // How much of the sky shows, 0 to 1, under another or over it, as in a swipe
    private float shownShare = 1;
    // Where the rain and the snow fade out down the view
    private final float[] dropsFade = {SkyShaders.CLEAR_NOWHERE, SkyShaders.CLEAR_NOWHERE, SkyShaders.CLEAR_NOWHERE,
            SkyShaders.CLEAR_NOWHERE};
    private Runnable onCloudsDrawn;
    private boolean animating;
    private long lastCloudsNs;
    private long lastFrameNs;
    // One clock for every sky: a city's sky passing from one view to the other, as a swipe starts or ends, goes on
    // where it was rather than jumping to the other view's time. It stops while the skies may not move, so that a
    // sky drawn again meanwhile, as the screen comes back or a swipe shows it, stands where it stood.
    private static long clockStartMs = -1;
    private static float stoppedAt = -1;
    private final Random random = new Random();
    private long flashStartNs = Long.MIN_VALUE;
    private long nextFlashNs;
    private float flash;
    private float boltSeed;
    // A frame of the clouds come and waiting for the drops' next frame to be drawn with it
    private boolean cloudsWaiting;
    private float boltAcross;
    private final Choreographer.FrameCallback frameCallback = this::onFrame;
    // The system's animations turned off or on: the sky stops or moves again. Made from Android 13 only, its type
    // being unknown before.
    private Object scaleListener;
    private boolean listeningToScale;
    // Its own record: isAttachedToWindow() still says yes while onDetachedFromWindow runs
    private boolean attached;
    // Whether the skies may move: the animated weather and the system's animations on, the battery not saved
    private boolean moving;

    public SkyView(Context context) {
        super(context);
    }

    public SkyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Whether the sky can be drawn by shaders here: from Android 13 */
    public static boolean supported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    /**
     * Whether the animated weather is on in the settings, as it is unless turned off, where it can be, and OpenGL
     * didn't fail to draw it
     */
    public static boolean liveEnabled(Context context) {
        return supported() && !SkyRenderer.failed() && PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.key_live_sky), true);
    }

    /** The weather to draw, null to show the picture instead; only from Android 13 */
    public void setScene(SkyScene scene) {
        if (scene == null ? this.scene == null : scene.equals(this.scene)) {
            return;
        }
        this.scene = scene;
        this.weather = ++weathers;
        this.blankSinceMs = 0;
        if (scene != null && supported()) {
            if (this.shaders == null) {
                this.shaders = new SkyShaders(getContext());
                this.starsPaint = SkyShaders.starsPaint(this.shaders.stars);
                this.dropsPaint.setShader(this.shaders.drops);
                this.moonPaint.setShader(this.shaders.moon);
            }
            this.shaders.setScene(scene);
            setImageDrawable(null);
        } else {
            releaseFrames();
        }
        this.flash = 0;
        this.nextFlashNs = 0;
        startAfresh();
        updateAnimation();
        invalidate();
    }

    // The next frame of the clouds drawn afresh, the average of the frames before forgotten
    private void startAfresh() {
        this.cloudsDirty = true;
        this.fresh = true;
    }

    /** The weather it draws, null for none */
    public SkyScene getScene() {
        return this.scene;
    }

    /** Whether it shows its weather drawn live: a frame of it has come */
    public boolean showsScene() {
        return this.scene != null && this.frame != null && this.frameWeather == this.weather;
    }

    /**
     * How long it has been drawn without a frame of its weather, the window's colour showing instead, while no frame of
     * it has been drawn yet; 0 once one has, or before it was drawn at all
     */
    public long blankMs() {
        return this.blankSinceMs > 0 ? SystemClock.uptimeMillis() - this.blankSinceMs : 0;
    }

    /**
     * Thins the clouds out down the view, from the top's fraction of its height to the bottom's, into an even colour
     * under the texts over it
     */
    public void setClearBelow(float top, float bottom) {
        this.clearTop = top;
        this.clearBottom = bottom;
        startAfresh();
        invalidate();
    }

    /**
     * How much of the sky shows, 0 to 1, as another fades in over it or it over another: one only partly shown is moved
     * on by the wind, its clouds marched less often
     */
    public void setShownShare(float share) {
        this.shownShare = share;
    }

    /**
     * Fades the rain and the snow out down the view, each from the first fraction of its height to the second, so that
     * they fall only in the top of it; SkyShaders.CLEAR_NOWHERE as the first for all the way down
     */
    public void setDropsFade(float rainFrom, float rainGone, float snowFrom, float snowGone) {
        this.dropsFade[0] = rainFrom;
        this.dropsFade[1] = rainGone;
        this.dropsFade[2] = snowFrom;
        this.dropsFade[3] = snowGone;
        invalidate();
    }

    /** Told each time a new frame of the clouds comes, for glass over the sky to draw it again */
    public void setOnCloudsDrawn(Runnable onCloudsDrawn) {
        this.onCloudsDrawn = onCloudsDrawn;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.scene == null || this.shaders == null || !(canvas instanceof RecordingCanvas)) {
            super.onDraw(canvas);
            return;
        }
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        drawSky((RecordingCanvas) canvas, width, height);
    }

    // The skies' time in seconds: going on while they may move, standing still while they may not
    private static float clock(boolean moving) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        if (clockStartMs < 0) {
            clockStartMs = now;
        }
        if (!moving) {
            if (stoppedAt < 0) {
                stoppedAt = (now - clockStartMs) / 1000f;
            }
            return stoppedAt;
        }
        if (stoppedAt >= 0) {
            // On from where it stopped
            clockStartMs = now - Math.round(stoppedAt * 1000);
            stoppedAt = -1;
        }
        return (now - clockStartMs) / 1000f;
    }

    @android.annotation.TargetApi(33)
    private void drawSky(RecordingCanvas canvas, int width, int height) {
        float time = clock(this.moving);
        int layerWidth = Math.max(1, Math.round(width * CLOUD_SCALE));
        int layerHeight = Math.max(1, Math.round(height * CLOUD_SCALE));
        if (this.target == null || this.target.width != layerWidth || this.target.height != layerHeight) {
            releaseFrames();
            this.target = SkyRenderer.get(getContext()).createTarget(layerWidth, layerHeight, this::onCloudsFrame);
            startAfresh();
        }
        if (this.cloudsDirty) {
            requestClouds(time);
        }
        // Its own drawing, the one seen, not the glass's over it
        boolean seen = !GlassBlur.drawingUnderGlass();
        if (!showsScene()) {
            // The window's colour shows until the first frame of the weather comes, not the weather before
            if (seen && this.blankSinceMs == 0) {
                this.blankSinceMs = SystemClock.uptimeMillis();
            }
            return;
        }
        if (seen) {
            this.blankSinceMs = -1;
        }
        // The frame's top half is the sky; its bottom half how far one sees through the clouds, for the stars
        this.frameSource.set(0, 0, layerWidth, layerHeight);
        this.frameDestination.set(0, 0, width, height);
        canvas.drawBitmap(this.frame, this.frameSource, this.frameDestination, this.framePaint);
        // The moon over the sky, the clouds in front of it as much as they are thick; drawn over its disc only
        if (this.scene.hasMoon()) {
            this.shaders.setMoonFrame(width, height, this.crop, this.frame, layerWidth / (float) width,
                    layerHeight / (float) height, layerHeight, this.scene, this.moonBounds);
            canvas.drawRect(this.moonBounds, this.moonPaint);
        }
        if (this.scene.hasStars()) {
            this.shaders.setStarsFrame(width, height, this.crop, time % 600, this.frame, layerWidth / (float) width,
                    layerHeight / (float) height, layerHeight, this.clearTop, this.clearBottom);
            canvas.drawPaint(this.starsPaint);
        }
        // Not under glass: blurred, the drops hardly show, and drawing them again for each piece of glass cost as much
        // as the sky itself, each of their frames
        if (this.scene.hasDrops() && seen) {
            this.shaders.setDropsFrame(width, height, time % 600);
            this.shaders.setDropsFade(this.dropsFade[0], this.dropsFade[1], this.dropsFade[2], this.dropsFade[3]);
            canvas.drawPaint(this.dropsPaint);
        }
        // By the sky that shows most, not twice as the next city's comes in over it
        if (SkyTimings.SHOWN && BuildConfig.DEBUG && seen && getAlpha() > 0.5f) {
            drawTimings(canvas, width, height);
        }
    }

    // Debug builds: the GPU's time for the sky's passes, on the right between the wind and the forecast
    private void drawTimings(Canvas canvas, int width, int height) {
        String text = SkyTimings.text();
        if (text == null) {
            return;
        }
        if (!text.equals(this.timingsText)) {
            this.timingsText = text;
            this.timingsLines = text.split("\n");
        }
        float density = getResources().getDisplayMetrics().density;
        if (this.timingsPaint == null) {
            this.timingsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.timingsPaint.setColor(0xFFFFFFFF);
            this.timingsPaint.setTextSize(13 * density);
            this.timingsPaint.setTextAlign(Paint.Align.RIGHT);
            this.timingsPaint.setShadowLayer(2 * density, 0, 0, 0xFF000000);
        }
        float x = width - 16 * density;
        float y = height * 0.27f;
        float line = this.timingsPaint.getFontSpacing();
        for (String row : this.timingsLines) {
            canvas.drawText(row, x, y, this.timingsPaint);
            y += line;
        }
    }

    // Asks the renderer for a frame of the clouds at the time
    @android.annotation.TargetApi(33)
    private void requestClouds(float time) {
        requestClouds(time, false);
    }

    // As requestClouds, only lit again by the lightning if flashOnly: the clouds not marched
    @android.annotation.TargetApi(33)
    private void requestClouds(float time, boolean flashOnly) {
        if (this.target == null || this.scene == null) {
            return;
        }
        this.cloudsDirty = false;
        SkyRenderer.Params params = new SkyRenderer.Params();
        params.scene = this.scene;
        params.crop = this.crop;
        // From the weather's moment on, as its still pictures show it
        params.drift = (this.scene.moment + time) * WIND;
        params.evolve = (this.scene.moment + time) * CHANGE;
        params.clearTop = this.clearTop;
        params.clearBottom = this.clearBottom;
        params.flash = this.flash;
        // The bolt shows as the clouds light up
        params.boltSeed = this.boltSeed;
        params.boltBrightness = Math.min(this.flash, 1f);
        params.boltAcross = this.boltAcross;
        params.steps = CLOUD_STEPS;
        params.fresh = this.fresh;
        params.still = !this.moving;
        params.weather = this.weather;
        params.carry = this.shownShare < 1;
        params.flashOnly = flashOnly;
        if (this.fresh && !this.moving) {
            this.settle = SETTLE_FRAMES;
        }
        this.fresh = false;
        SkyRenderer.get(getContext()).render(this.target, params);
    }

    // A new frame of the clouds: shown, the one before it kept while the system may still draw it, the one before
    // that given back
    private void onCloudsFrame(SkyRenderer.Target from, Bitmap bitmap, Image image) {
        // Not one of the frames given back, as of another size
        if (from != this.target || this.scene == null) {
            image.close();
            return;
        }
        if (this.previousImage != null) {
            this.previousImage.close();
        }
        this.previousImage = this.shownImage;
        this.shownImage = image;
        this.frame = bitmap;
        this.frameWeather = image.getTimestamp() >>> SkyRenderer.FRAME_TAG;
        if (this.animating && this.scene.hasDrops() && this.flash == 0) {
            // Shown with the drops' next frame, a drops' period on at most: one redraw of the screen for both, not
            // one for each, the clouds changing too slowly for the wait to show; lightning shows at once
            this.cloudsWaiting = true;
        } else {
            showClouds();
        }
        // Standing still, it smooths a fresh start with a few more frames of the same moment
        if (!this.animating && this.settle > 0) {
            this.settle--;
            requestClouds(clock(this.moving));
        }
    }

    // The clouds' new frame drawn, and the glass over the sky drawn again with it
    private void showClouds() {
        this.cloudsWaiting = false;
        invalidate();
        if (this.onCloudsDrawn != null) {
            this.onCloudsDrawn.run();
        }
    }

    // Gives back the renderer's frames and the images shown
    private void releaseFrames() {
        // A target is made only where the sky is drawn live, from Android 13
        if (this.target != null && Build.VERSION.SDK_INT >= 33) {
            SkyRenderer.get(getContext()).release(this.target);
            this.target = null;
        }
        this.frame = null;
        this.frameWeather = -1;
        this.blankSinceMs = 0;
        this.cloudsWaiting = false;
        if (this.shownImage != null) {
            this.shownImage.close();
            this.shownImage = null;
        }
        if (this.previousImage != null) {
            this.previousImage.close();
            this.previousImage = null;
        }
    }

    @Override
    public void setAlpha(float alpha) {
        boolean appears = getAlpha() <= 0 && alpha > 0;
        boolean vanishes = getAlpha() > 0 && alpha <= 0;
        // Coming out of nothing, as the next city's sky does when a swipe starts: its clouds drawn for now at once,
        // not left as they were when it was last seen
        if (appears && this.scene != null) {
            startAfresh();
            invalidate();
        }
        super.setAlpha(alpha);
        // Unseen it waits for no frames
        if (appears || vanishes) {
            updateAnimation();
        }
    }

    // --- Animation ---

    private void onFrame(long nowNs) {
        if (!this.animating) {
            return;
        }
        Choreographer.getInstance().postFrameCallback(this.frameCallback);
        // The drops, and the clouds with the stars, each on its own period: a new frame of the clouds is asked for
        // here and, where drops fall, drawn with their next frame, otherwise when it comes
        if (this.scene.hasDrops() && nowNs - this.lastFrameNs >= FRAME_NS - FRAME_SLACK_NS) {
            this.lastFrameNs = nowNs;
            if (this.cloudsWaiting) {
                showClouds();
            } else {
                invalidate();
            }
        }
        // Lightning lights the clouds up on every frame of the screen while it flashes, and only that: the clouds
        // composed again, not marched, which on every frame of a 120 Hz screen held up its drawing
        boolean flash = this.scene.hasLightning() && updateFlash(nowNs);
        boolean clouds = false;
        if (nowNs - this.lastCloudsNs >= CLOUD_FRAME_NS - FRAME_SLACK_NS) {
            this.lastCloudsNs = nowNs;
            clouds = true;
        }
        if (clouds || this.cloudsDirty) {
            requestClouds(clock(this.moving));
        } else if (flash) {
            requestClouds(clock(this.moving), true);
        }
    }

    // Lightning now and then: two quick pulses; true while the clouds light up or go dark again
    private boolean updateFlash(long nowNs) {
        if (this.nextFlashNs == 0) {
            this.nextFlashNs = nowNs + (2000 + this.random.nextInt(5000)) * 1_000_000L;
        }
        if (nowNs >= this.nextFlashNs) {
            this.flashStartNs = nowNs;
            // Each flash its own bolt, somewhere off the texts on the left
            this.boltSeed = this.random.nextInt(1000);
            this.boltAcross = 0.55f + 0.2f * this.random.nextFloat();
            this.nextFlashNs = nowNs + (3000 + this.random.nextInt(7000)) * 1_000_000L;
        }
        float t = (nowNs - this.flashStartNs) / 1e9f;
        float before = this.flash;
        if (t >= 0 && t < 0.6f) {
            float a = (t - 0.05f) / 0.03f;
            float b = (t - 0.22f) / 0.06f;
            this.flash = (float) (Math.exp(-a * a) + 0.6 * Math.exp(-b * b));
        } else {
            this.flash = 0;
        }
        return this.flash != before;
    }

    private void updateAnimation() {
        if (supported()) {
            listenToScale(this.scene != null && this.attached);
        }
        updateLayer();
        this.moving = animationsAllowed();
        if (!this.moving) {
            // Stopped where it was last drawn, not wherever it has got to by the next time something draws it
            clock(false);
        }
        boolean animate = this.scene != null && this.shaders != null && this.attached
                && getWindowVisibility() == VISIBLE && isShown() && getAlpha() > 0 && this.moving;
        if (animate == this.animating) {
            return;
        }
        this.animating = animate;
        if (animate) {
            Choreographer.getInstance().postFrameCallback(this.frameCallback);
        } else {
            Choreographer.getInstance().removeFrameCallback(this.frameCallback);
            if (this.cloudsWaiting) {
                // No drops' frame to come for it
                showClouds();
            }
            if (this.flash != 0) {
                // Not left lit by lightning
                this.flash = 0;
                startAfresh();
                invalidate();
            }
        }
    }

    // A sky with drops or stars, while it shows, is drawn into a layer of its own: their full-screen shaders then run
    // as often as the sky itself is drawn again, not on every frame of the screen around it, as a page scrolls or a
    // swipe moves, many more on a 90 or 120 Hz screen
    private void updateLayer() {
        boolean layer = this.scene != null && this.shaders != null && this.attached && getAlpha() > 0
                && (this.scene.hasDrops() || this.scene.hasStars() || this.scene.hasMoon());
        int type = layer ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE;
        if (getLayerType() != type) {
            setLayerType(type, null);
        }
    }

    // Not with the animated weather turned off in the settings, the system's animations off, or saving the battery:
    // the sky stands still then
    private boolean animationsAllowed() {
        Context context = getContext();
        if (!liveEnabled(context)) {
            return false;
        }
        PowerManager power = context.getSystemService(PowerManager.class);
        return ValueAnimator.areAnimatorsEnabled() && (power == null || !power.isPowerSaveMode());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.attached = true;
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.attached = false;
        updateAnimation();
        // Gives back the renderer's frames; drawn again when shown again
        releaseFrames();
    }

    // While it has a sky to draw
    @android.annotation.TargetApi(33)
    private void listenToScale(boolean listen) {
        if (listen == this.listeningToScale) {
            return;
        }
        this.listeningToScale = listen;
        if (this.scaleListener == null) {
            // Told on any thread
            this.scaleListener = (ValueAnimator.DurationScaleChangeListener) scale -> post(this::updateAnimation);
        }
        ValueAnimator.DurationScaleChangeListener listener = (ValueAnimator.DurationScaleChangeListener) this.scaleListener;
        if (listen) {
            ValueAnimator.registerDurationScaleChangeListener(listener);
        } else {
            ValueAnimator.unregisterDurationScaleChangeListener(listener);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            // Drawn afresh, at the time then, when the window shows again
            startAfresh();
        }
        updateAnimation();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        updateAnimation();
    }
}
