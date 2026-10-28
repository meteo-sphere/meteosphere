package org.meteosphere.ui.quakes;

import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * The outlines of the land for the earthquake map, from the asset land.bin: the 1:110m land of Natural Earth
 * (public domain, naturalearthdata.com), as "LAND", the number of rings, then each ring's number of points and the
 * points as longitude and latitude in hundredths of a degree, all big-endian, holes among the rings.
 */
public final class WorldLand {

    private static final String TAG = "WorldLand";
    private static final String ASSET = "land.bin";

    private static float[][] rings;

    private WorldLand() {
    }

    /** Each ring as longitude, latitude, longitude, latitude… in degrees; empty if the asset can't be read */
    public static synchronized float[][] get(Context context) {
        if (rings == null) {
            rings = read(context);
        }
        return rings;
    }

    private static float[][] read(Context context) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(context.getAssets().open(ASSET)))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"LAND".equals(new String(magic, "US-ASCII"))) {
                throw new IOException("Not a land file");
            }
            float[][] result = new float[in.readInt()][];
            for (int i = 0; i < result.length; i++) {
                float[] ring = new float[in.readInt() * 2];
                for (int j = 0; j < ring.length; j++) {
                    ring[j] = in.readShort() / 100f;
                }
                result[i] = ring;
            }
            return result;
        } catch (IOException e) {
            Log.w(TAG, "Can't read the land", e);
            return new float[0][];
        }
    }
}
