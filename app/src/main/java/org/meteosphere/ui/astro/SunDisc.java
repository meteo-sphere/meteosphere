package org.meteosphere.ui.astro;

import android.graphics.Bitmap;

/**
 * The Sun of a picture cut from its black sky, to lie on a card: the disc whole, and around it the corona as much as
 * it shines, fading out over a fifth of the radius, with a little more contrast and colour. Off the main thread.
 */
final class SunDisc {

    private static final float CONTRAST = 1.25f;
    private static final float SATURATION = 1.3f;
    // How much the corona's light keeps of itself over the card
    private static final float CORONA_GAIN = 1.5f;
    // The corona fades out from this much of the radius, over this much more
    private static final float CORONA_END = 1.22f;
    private static final float CORONA_FADE = 0.2f;

    private SunDisc() {
    }

    /** The disc of radius pixels about the middle of the picture, on a clear background */
    static Bitmap cut(Bitmap picture, float radius) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        int[] pixels = new int[width * height];
        picture.getPixels(pixels, 0, width, 0, 0, width, height);
        float cx = width / 2f;
        float cy = height / 2f;
        float s = SATURATION;
        // The saturation matrix of CSS and SVG filters
        float rr = 0.213f + 0.787f * s, rg = 0.715f - 0.715f * s, rb = 0.072f - 0.072f * s;
        float gr = 0.213f - 0.213f * s, gg = 0.715f + 0.285f * s, gb = 0.072f - 0.072f * s;
        float br = 0.213f - 0.213f * s, bg = 0.715f - 0.715f * s, bb = 0.072f + 0.928f * s;
        float[] limb = limbColor(pixels, width, height, radius);
        for (int y = 0; y < height; y++) {
            float dy = y + 0.5f - cy;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                float dx = x + 0.5f - cx;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int pixel = pixels[i];
                float r = (pixel >> 16) & 0xff;
                float g = (pixel >> 8) & 0xff;
                float b = pixel & 0xff;
                // Whole within the limb, smoothed over three pixels across it
                float inside = clamp((radius + 1.5f - d) / 3f);
                float alpha = inside;
                if (inside < 1) {
                    float t = clamp((radius * CORONA_END - d) / (radius * CORONA_FADE));
                    float corona = Math.min(1, Math.max(r, Math.max(g, b)) / 255f * CORONA_GAIN) * t * t * (3 - 2 * t);
                    alpha = Math.max(inside, corona);
                    if (alpha <= 0) {
                        pixels[i] = 0;
                        continue;
                    }
                    // On black the corona looks as it did: its colour as much brighter as it is clearer, all of it
                    // alike, as clipping each channel apart would turn its faint edge green or red
                    float brighter = Math.min(1 / alpha, 255 / Math.max(1, Math.max(r, Math.max(g, b))));
                    // Its faint edge in the colour of the bright corona at the limb: brightened, its own dark colour
                    // would be red
                    r = limb[0] + (r * brighter - limb[0]) * alpha;
                    g = limb[1] + (g * brighter - limb[1]) * alpha;
                    b = limb[2] + (b * brighter - limb[2]) * alpha;
                }
                r = channel((r - 127.5f) * CONTRAST + 127.5f);
                g = channel((g - 127.5f) * CONTRAST + 127.5f);
                b = channel((b - 127.5f) * CONTRAST + 127.5f);
                int red = channel(rr * r + rg * g + rb * b);
                int green = channel(gr * r + gg * g + gb * b);
                int blue = channel(br * r + bg * g + bb * b);
                pixels[i] = Math.round(alpha * 255) << 24 | red << 16 | green << 8 | blue;
            }
        }
        Bitmap disc = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        disc.setPixels(pixels, 0, width, 0, 0, width, height);
        return disc;
    }

    // The corona's colour just over the limb, its brighter pixels counting more, at full brightness
    private static float[] limbColor(int[] pixels, int width, int height, float radius) {
        float cx = width / 2f;
        float cy = height / 2f;
        double red = 0, green = 0, blue = 0;
        for (int y = 0; y < height; y++) {
            float dy = y + 0.5f - cy;
            for (int x = 0; x < width; x++) {
                float dx = x + 0.5f - cx;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > radius * 1.005f && d < radius * 1.05f) {
                    int pixel = pixels[y * width + x];
                    int r = (pixel >> 16) & 0xff;
                    int g = (pixel >> 8) & 0xff;
                    int b = pixel & 0xff;
                    int weight = Math.max(r, Math.max(g, b));
                    red += r * weight;
                    green += g * weight;
                    blue += b * weight;
                }
            }
        }
        double max = Math.max(red, Math.max(green, blue));
        if (max <= 0) {
            return new float[]{255, 255, 255};
        }
        return new float[]{(float) (red * 255 / max), (float) (green * 255 / max), (float) (blue * 255 / max)};
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
