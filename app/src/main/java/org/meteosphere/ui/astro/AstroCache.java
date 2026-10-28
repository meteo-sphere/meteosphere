package org.meteosphere.ui.astro;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Supplier;
import org.meteosphere.data.model.Place;

/**
 * Keeps what the Sun and Moon tabs compute for a place and a local day, the heavy part that doesn't change with the
 * hour: in memory for switching tabs and places, and in the cache folder for the next start of the app. A new day,
 * another place or time zone, or a new install of the app computes it again.
 */
public final class AstroCache {

    private static final String TAG = "AstroCache";
    private static final String FOLDER = "astro";
    private static final int MEMORY_ENTRIES = 8;
    // Files of earlier days are removed after this
    private static final long KEEP_MS = 2 * 24 * 60 * 60 * 1000L;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    /** Writes a value to a stream and reads it back */
    public interface Codec<T> {
        void write(DataOutputStream out, T value) throws IOException;

        T read(DataInputStream in) throws IOException;
    }

    private static final Map<String, Object> memory = new LinkedHashMap<String, Object>(MEMORY_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > MEMORY_ENTRIES;
        }
    };

    private AstroCache() {
    }

    /** The kind of value for the place (null for none) and the local day, from memory, the disk or computed */
    @SuppressWarnings("unchecked")
    public static <T> T get(Context context, String kind, Place location, TimeZone timeZone, long localDay,
                            Codec<T> codec, Supplier<T> compute) {
        String key = key(context, kind, location, timeZone, localDay);
        synchronized (memory) {
            Object value = memory.get(key);
            if (value != null) {
                return (T) value;
            }
        }
        File folder = new File(context.getCacheDir(), FOLDER);
        File file = new File(folder, key);
        T value = null;
        if (file.exists()) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                value = codec.read(in);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Unreadable " + file.getName(), e);
                file.delete();
            }
        }
        if (value == null) {
            value = compute.get();
            write(folder, file, value, codec);
        }
        synchronized (memory) {
            memory.put(key, value);
        }
        return value;
    }

    // Written aside and renamed, so a reader never sees half a file; then the files of old days go
    private static <T> void write(File folder, File file, T value, Codec<T> codec) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return;
        }
        File temp = new File(folder, file.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
            codec.write(out, value);
        } catch (IOException e) {
            Log.w(TAG, "Not cached " + file.getName(), e);
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            temp.delete();
        }
        long now = System.currentTimeMillis();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File old : files) {
                if (now - old.lastModified() > KEEP_MS) {
                    old.delete();
                }
            }
        }
    }

    // "sun_52.3700_4.9000_Europe_Amsterdam_20728_1790899200000": the time of the install keeps a new build from
    // reading what an older one wrote
    private static String key(Context context, String kind, Place location, TimeZone timeZone, long localDay) {
        long installed = 0;
        try {
            installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
            // Then only the day and the place tell the files apart
        }
        String place = location == null ? "none" : String.format(Locale.US, "%.4f_%.4f", location.latitude, location.longitude);
        return (kind + "_" + place + "_" + timeZone.getID() + "_" + Math.floorDiv(localDay, DAY_MS) + "_" + installed)
                .replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
