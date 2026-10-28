package org.meteosphere.ui.sky;

import org.meteosphere.R;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import androidx.annotation.RequiresApi;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The shaders drawn over SkyRenderer's sky and clouds from Android 13, at full size: res/raw/sky_stars.agsl for the
 * stars, sky_moon.agsl for the moon, sky_drops.agsl for rain and snow, over the live sky (the stills draw them as
 * sky_overlay.frag). Each view has its own instance, a RuntimeShader being no one else's.
 */
@RequiresApi(33)
public final class SkyShaders {

    /** The sky's shape the views cut theirs from, as the pictures it replaces: 2 wide, 3 high */
    static final float SKY_WIDTH = 960;
    static final float SKY_HEIGHT = 1440;
    private static final int NOISE_SIZE = 256;

    /** No thinning of the clouds down the output, for both of its heights: the whole sky as it is */
    public static final float CLEAR_NOWHERE = -1;

    private static String starsSource;
    private static String dropsSource;
    private static String moonSource;
    private static Bitmap noiseBitmap;
    private static Bitmap moonBitmap;
    // As sky_moon.agsl's
    private static final float MOON_RADIUS = 0.031f;

    public final RuntimeShader stars;
    public final RuntimeShader drops;
    public final RuntimeShader moon;

    public SkyShaders(Context context) {
        load(context.getResources());
        this.stars = new RuntimeShader(starsSource);
        this.drops = new RuntimeShader(dropsSource);
        this.moon = new RuntimeShader(moonSource);
        this.stars.setInputShader("noise", noiseShader());
        this.drops.setInputShader("noise", noiseShader());
        BitmapShader moonMap = new BitmapShader(moonBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        moonMap.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        this.moon.setInputShader("moonMap", moonMap);
        setDropsFade(CLEAR_NOWHERE, CLEAR_NOWHERE, CLEAR_NOWHERE, CLEAR_NOWHERE);
    }

    /** The paint adding the stars' shader to what is drawn under it */
    public static Paint starsPaint(RuntimeShader stars) {
        Paint paint = new Paint();
        paint.setShader(stars);
        paint.setBlendMode(BlendMode.PLUS);
        return paint;
    }

    private static synchronized void load(Resources resources) {
        if (starsSource == null) {
            starsSource = read(resources, R.raw.sky_stars);
            dropsSource = read(resources, R.raw.sky_drops);
            moonSource = read(resources, R.raw.sky_moon);
            noiseBitmap = makeNoise();
            moonBitmap = SkyRenderer.moonMap(resources);
        }
    }

    private static String read(Resources resources, int id) {
        try (InputStream in = resources.openRawResource(id); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException("Reading the sky's shader", e);
        }
    }

    // SkyRenderer's value noise, which its stills read too
    private static Bitmap makeNoise() {
        return Bitmap.createBitmap(SkyRenderer.noisePixels(), NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888);
    }

    private static BitmapShader noiseShader() {
        BitmapShader shader = new BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        return shader;
    }

    /** The weather drawn by the shaders */
    public void setScene(SkyScene scene) {
        this.stars.setFloatUniform("coverage", scene.coverage);
        this.stars.setFloatUniform("body", scene.bodyX, scene.bodyY, scene.body);
        this.stars.setFloatUniform("stars", scene.stars);
        this.drops.setFloatUniform("rain", scene.rain);
        this.drops.setFloatUniform("snow", scene.snow);
        this.drops.setFloatUniform("brightness", scene.dropsBrightness);
        this.moon.setFloatUniform("body", scene.bodyX, scene.bodyY);
        this.moon.setFloatUniform("disc", scene.moonPhase, scene.moonLimb, scene.moonFace);
    }

    /**
     * The moon's frame, as the stars': width x height cut at the crop from the 2:3 sky, its light added to SkyRenderer's
     * frame, the sky over how far one sees through it (down by the offset); into bounds, the part of the output its
     * disc covers, the shader's to draw over the frame drawn there
     */
    public void setMoonFrame(float width, float height, float crop, Bitmap frame, float scaleX, float scaleY,
            float offset, SkyScene scene, RectF bounds) {
        float scale = Math.max(width / SKY_WIDTH, height / SKY_HEIGHT);
        float viewWidth = width / scale / SKY_WIDTH;
        float viewHeight = height / scale / SKY_HEIGHT;
        float left = (1 - viewWidth) / 2;
        float top = (1 - viewHeight) * crop;
        this.moon.setFloatUniform("size", width, height);
        this.moon.setFloatUniform("view", left, top, viewWidth, viewHeight);
        BitmapShader clouds = new BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        clouds.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        this.moon.setInputShader("clouds", clouds);
        this.moon.setFloatUniform("cloudsScale", scaleX, scaleY);
        this.moon.setFloatUniform("cloudsOffset", offset);
        // Its middle where its ray points, and its radius a quarter wider than its angle spans down the view (its
        // height a radian a unit) or across it (a unit of its width two thirds of a radian, a little more off its
        // middle, as the ray's offset is a tangent)
        float x = (scene.bodyX - left) / viewWidth * width;
        float y = (scene.bodyY - top) / viewHeight * height;
        float radius = 1.25f * MOON_RADIUS * Math.max(height / viewHeight, width / (viewWidth * 0.6667f));
        bounds.set(x - radius, y - radius, x + radius, y + radius);
    }

    /**
     * The stars' frame, width x height cut at the crop from the 2:3 sky; they twinkle as the time says and are hidden
     * by the clouds as the see-through map says: its pixel for an output pixel at its place times the scale, down by
     * the offset. The clouds thin out between two heights of the output (0 its top, 1 its bottom; CLEAR_NOWHERE for
     * none).
     */
    public void setStarsFrame(float width, float height, float crop, float time, Bitmap seeThrough, float scaleX,
            float scaleY, float offset, float clearTop, float clearBottom) {
        float scale = Math.max(width / SKY_WIDTH, height / SKY_HEIGHT);
        float viewWidth = width / scale / SKY_WIDTH;
        float viewHeight = height / scale / SKY_HEIGHT;
        this.stars.setFloatUniform("size", width, height);
        this.stars.setFloatUniform("view", (1 - viewWidth) / 2, (1 - viewHeight) * crop, viewWidth, viewHeight);
        this.stars.setFloatUniform("time", time);
        this.stars.setFloatUniform("clear", clearTop, clearBottom);
        BitmapShader clouds = new BitmapShader(seeThrough, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        clouds.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        this.stars.setInputShader("clouds", clouds);
        this.stars.setFloatUniform("cloudsScale", scaleX, scaleY);
        this.stars.setFloatUniform("cloudsOffset", offset);
    }

    /**
     * Where the rain and the snow fade out down the view, from one height to the next, 0 its top to 1 its bottom;
     * CLEAR_NOWHERE as the first for none
     */
    public void setDropsFade(float rainFrom, float rainGone, float snowFrom, float snowGone) {
        this.drops.setFloatUniform("rainFade", rainFrom, rainGone);
        this.drops.setFloatUniform("snowFade", snowFrom, snowGone);
    }

    public void setDropsFrame(float width, float height, float time) {
        this.drops.setFloatUniform("size", width, height);
        this.drops.setFloatUniform("time", time);
    }
}
