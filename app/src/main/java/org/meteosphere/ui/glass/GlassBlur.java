package org.meteosphere.ui.glass;

import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import java.util.Collections;
import java.util.List;

/**
 * Frosted glass under a view: what lies under it in its window, the window's background and source views, blurred
 * on the GPU from Android 12. The source's own drawing is reused: only these two small nodes are recorded again,
 * when the view is drawn. Before Android 12 nothing is drawn and the view's tint stands alone.
 */
public final class GlassBlur {

    static final boolean SUPPORTED = Build.VERSION.SDK_INT >= 31;
    /** The blur of the toolbars, the pills and the strips; the menus' cards blur a little more */
    public static final float BLUR_DP = 22;

    private static final float SATURATION = 1.5f;
    // The blur works on a picture this much smaller, drawn back up: a sixteenth of the pixels, looking the same
    private static final float SCALE = 0.25f;

    private final float blur;
    private final int[] viewLocation = new int[2];
    private final int[] sourceLocation = new int[2];
    private Object blurNode;
    // The blurred picture cut to the view's rounded shape by its outline, the cheap clip
    private Object shapeNode;

    public GlassBlur(float blurPixels) {
        this.blur = blurPixels;
    }

    // Main thread: while the sources are drawn for the blur
    private static int drawingSources;

    /**
     * Whether a view is being drawn under glass, to be blurred: details too fine to show through it, as the drops,
     * can be left out. Main thread.
     */
    public static boolean drawingUnderGlass() {
        return drawingSources > 0;
    }

    /** Draws the glass under the view, rounded by the radius; source is what is blurred over the window's background */
    public void draw(Canvas canvas, View view, View source, float cornerRadius) {
        if (source != null) {
            draw(canvas, view, Collections.singletonList(source), cornerRadius);
        }
    }

    /**
     * As draw, the sources blurred together over the window's background: those shown, each where it is in the
     * window
     */
    public void draw(Canvas canvas, View view, List<View> sources, float cornerRadius) {
        draw(canvas, view, sources, cornerRadius, 1f);
    }

    /** As draw, the glass shown as much as alpha, 0 to 1, as it fades in */
    public void draw(Canvas canvas, View view, List<View> sources, float cornerRadius, float alpha) {
        if (SUPPORTED && alpha > 0 && !sources.isEmpty() && canvas.isHardwareAccelerated() && view.getWidth() > 0 && view.getHeight() > 0) {
            drawBlur(canvas, view, sources, cornerRadius, alpha);
        }
    }

    @android.annotation.TargetApi(31)
    private void drawBlur(Canvas canvas, View view, List<View> sources, float cornerRadius, float alpha) {
        int margin = Math.round(this.blur);
        int width = view.getWidth();
        int height = view.getHeight();
        RenderNode node = (RenderNode) this.blurNode;
        if (node == null) {
            node = new RenderNode("GlassBlur");
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(SATURATION);
            float scaledBlur = this.blur * SCALE;
            node.setRenderEffect(RenderEffect.createChainEffect(
                    RenderEffect.createBlurEffect(scaledBlur, scaledBlur, Shader.TileMode.CLAMP),
                    RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(saturation))));
            this.blurNode = node;
        }
        // The window's background and the sources as they are under the view and a blur's reach around it
        node.setPosition(0, 0, Math.round((width + 2 * margin) * SCALE), Math.round((height + 2 * margin) * SCALE));
        view.getLocationInWindow(this.viewLocation);
        RecordingCanvas recording = node.beginRecording();
        recording.scale(SCALE, SCALE);
        recording.translate(margin - this.viewLocation[0], margin - this.viewLocation[1]);
        Drawable windowBackground = view.getRootView().getBackground();
        if (windowBackground != null) {
            windowBackground.draw(recording);
        }
        drawingSources++;
        try {
            for (int i = 0; i < sources.size(); i++) {
                View source = sources.get(i);
                if (source == null || !source.isShown() || source.getAlpha() <= 0) {
                    continue;
                }
                source.getLocationInWindow(this.sourceLocation);
                // A view's own alpha is its parent's to apply, as when a sky fades in over another
                int saved = source.getAlpha() < 1
                        ? recording.saveLayerAlpha(null, Math.round(source.getAlpha() * 255))
                        : recording.save();
                recording.translate(this.sourceLocation[0] - source.getScrollX(), this.sourceLocation[1] - source.getScrollY());
                source.draw(recording);
                recording.restoreToCount(saved);
            }
        } finally {
            drawingSources--;
        }
        node.endRecording();

        RenderNode shape = (RenderNode) this.shapeNode;
        if (shape == null) {
            shape = new RenderNode("GlassShape");
            shape.setClipToOutline(true);
            this.shapeNode = shape;
        }
        shape.setAlpha(alpha);
        if (shape.getWidth() != width || shape.getHeight() != height) {
            shape.setPosition(0, 0, width, height);
            Outline outline = new Outline();
            outline.setRoundRect(0, 0, width, height, cornerRadius);
            shape.setOutline(outline);
        }
        RecordingCanvas shaped = shape.beginRecording();
        shaped.translate(-margin, -margin);
        shaped.scale(1 / SCALE, 1 / SCALE);
        shaped.drawRenderNode(node);
        shape.endRecording();
        canvas.drawRenderNode(shape);
    }
}
