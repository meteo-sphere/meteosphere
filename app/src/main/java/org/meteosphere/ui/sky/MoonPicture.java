package org.meteosphere.ui.sky;

import android.content.res.Resources;
import android.graphics.Bitmap;

/**
 * The moon as it is seen from a place, as a picture for the screens' cards, drawn on the CPU on any Android, off the
 * main thread (the first one takes a few hundred milliseconds before the code is warm, the next a dozen): the
 * night sky's moon of sky_moon.agsl, NASA's nearside map lit as much as its phase angle says from the side its bright
 * limb points to, its north turned as it is seen there (Moon.disc). Its dark side is the same map, as dim and as see-
 * through as asked, in earthshine.
 */
public final class MoonPicture {

    // As sky_moon.agsl's colour of its light
    private static final float[] TINT = {0.96f, 0.95f, 0.9f};
    private static int[] map;
    private static int mapSize;
    // The last few pictures, by their size, disc in half degrees and dark side: the city pages' cards of one moon, and
    // a page drawn again, share theirs rather than each spending a few dozen milliseconds on it
    private static final int CACHED = 6;
    private static final java.util.LinkedHashMap<String, Bitmap> cache = new java.util.LinkedHashMap<String, Bitmap>(
            CACHED, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> eldest) {
            return size() > CACHED;
        }
    };

    private MoonPicture() {
    }

    private static synchronized void load(Resources resources) {
        if (map == null) {
            Bitmap bitmap = SkyRenderer.moonMap(resources);
            mapSize = bitmap.getWidth();
            map = new int[mapSize * mapSize];
            bitmap.getPixels(map, 0, mapSize, 0, 0, mapSize, mapSize);
            bitmap.recycle();
        }
    }

    /**
     * The disc, size pixels across, transparent round it: its disc as Moon.disc gives it, in radians; its dark side as
     * bright as the map times dark and as opaque as darkAlpha, 0 to 1
     */
    public static Bitmap draw(Resources resources, int size, double[] disc, float dark, float darkAlpha) {
        double[] rounded = new double[3];
        StringBuilder key = new StringBuilder().append(size).append('/').append(dark).append('/').append(darkAlpha);
        for (int i = 0; i < 3; i++) {
            long halfDegrees = Math.round(Math.toDegrees(disc[i]) * 2);
            rounded[i] = Math.toRadians(halfDegrees / 2.0);
            key.append('/').append(halfDegrees);
        }
        synchronized (cache) {
            Bitmap cached = cache.get(key.toString());
            if (cached != null) {
                return cached;
            }
        }
        Bitmap picture = render(resources, size, rounded, dark, darkAlpha);
        synchronized (cache) {
            cache.put(key.toString(), picture);
        }
        return picture;
    }

    private static Bitmap render(Resources resources, int size, double[] disc, float dark, float darkAlpha) {
        load(resources);
        float radius = size / 2f;
        // The sun's direction from the moon, toward the bright limb as far round from the viewer as the phase angle
        double sinPhase = Math.sin(disc[0]);
        float sunX = (float) (-Math.sin(disc[1]) * sinPhase);
        float sunY = (float) (Math.cos(disc[1]) * sinPhase);
        float sunZ = (float) Math.cos(disc[0]);
        // Its north and east on the picture
        float northX = (float) -Math.sin(disc[2]);
        float northY = (float) Math.cos(disc[2]);
        float eastX = northY;
        float eastY = -northX;
        // The terminator a pixel and a half wide
        float soft = 1.5f / radius;
        int[] pixels = new int[size * size];
        float[] rgb = new float[3];
        for (int y = 0; y < size; y++) {
            float ly = (radius - y - 0.5f) / radius;
            for (int x = 0; x < size; x++) {
                float lx = (x + 0.5f - radius) / radius;
                float r2 = lx * lx + ly * ly;
                float r = (float) Math.sqrt(r2);
                // Its edge smoothed over a pixel
                float edge = Math.min(Math.max((1 - r) * radius + 0.5f, 0), 1);
                if (edge <= 0) {
                    continue;
                }
                float nz = (float) Math.sqrt(Math.max(1 - r2, 0));
                float lit = smoothstep(-soft, soft, lx * sunX + ly * sunY + nz * sunZ);
                double lat = Math.asin(Math.min(Math.max(lx * northX + ly * northY, -1), 1));
                double lon = Math.atan2(lx * eastX + ly * eastY, nz);
                sample((float) (0.5 + lon / Math.PI) * mapSize - 0.5f, (float) (0.5 - lat / Math.PI) * mapSize - 0.5f,
                        rgb);
                float light = dark + (1 - dark) * lit;
                float alpha = edge * (darkAlpha + (1 - darkAlpha) * lit);
                pixels[y * size + x] = ((int) (alpha * 255 + 0.5f) << 24)
                        | (channel(rgb[0] * TINT[0] * light) << 16)
                        | (channel(rgb[1] * TINT[1] * light) << 8)
                        | channel(rgb[2] * TINT[2] * light);
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    // The map at a point of its texels, linearly between them, its edges held, 0 to 1 a channel
    private static void sample(float u, float v, float[] rgb) {
        u = Math.min(Math.max(u, 0), mapSize - 1);
        v = Math.min(Math.max(v, 0), mapSize - 1);
        int x0 = (int) u;
        int y0 = (int) v;
        int x1 = Math.min(x0 + 1, mapSize - 1);
        int y1 = Math.min(y0 + 1, mapSize - 1);
        float fx = u - x0;
        float fy = v - y0;
        int a = map[y0 * mapSize + x0];
        int b = map[y0 * mapSize + x1];
        int c = map[y1 * mapSize + x0];
        int d = map[y1 * mapSize + x1];
        for (int i = 0; i < 3; i++) {
            int shift = 16 - 8 * i;
            float top = ((a >> shift) & 0xff) * (1 - fx) + ((b >> shift) & 0xff) * fx;
            float bottom = ((c >> shift) & 0xff) * (1 - fx) + ((d >> shift) & 0xff) * fx;
            rgb[i] = (top * (1 - fy) + bottom * fy) / 255f;
        }
    }

    private static int channel(float value) {
        return Math.min(Math.max((int) (value * 255 + 0.5f), 0), 255);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
        return t * t * (3 - 2 * t);
    }
}
