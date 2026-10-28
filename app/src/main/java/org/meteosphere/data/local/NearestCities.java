package org.meteosphere.data.local;

import org.meteosphere.data.http.HttpUtils;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Reverse geocoding over the GeoNames dump: https://download.geonames.org/export/dump/readme.txt
// The ids are GeoNames ids, the same ones Open-Meteo geocoding uses.
// The dump (about 11 MB) is downloaded once and kept as a compact index of ids and coordinates.
final class NearestCities {

    private static final String TAG = "NearestCities";

    private static final String DUMP_URL = "https://download.geonames.org/export/dump/cities1000.zip";
    private static final String DUMP_ENTRY = "cities1000.txt";
    private static final String DUMP_FILE = "cities1000.zip";
    private static final String INDEX_FILE = "cities1000.bin";
    // The index is the magic, the count, then all the ids, latitudes and longitudes
    private static final int INDEX_MAGIC = 0x4D434931;
    private static final int DOWNLOAD_TIMEOUT_MS = 60_000;
    private static final long RETRY_INTERVAL_MS = 10 * 60 * 1000;
    private static final double EARTH_RADIUS_KM = 6371.0;

    // Sections, historical, abandoned and destroyed populated places
    private static final Set<String> EXCLUDED_FEATURE_CODES = new HashSet<>(Arrays.asList("PPLX", "PPLH", "PPLQ", "PPLW", "PPLCH"));

    static final class Nearest {
        final int id;
        final float distance;

        Nearest(int id, float distance) {
            this.id = id;
            this.distance = distance;
        }
    }

    static final class Cities {
        final int[] ids;
        final float[] lats;
        final float[] lngs;

        Cities(int[] ids, float[] lats, float[] lngs) {
            this.ids = ids;
            this.lats = lats;
            this.lngs = lngs;
        }
    }

    private final File dir;
    private volatile Cities cities;
    private long lastLoadAttempt;

    NearestCities(File dir) {
        this.dir = dir;
    }

    synchronized boolean ensureLoaded() {
        if (cities != null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (lastLoadAttempt != 0 && now - lastLoadAttempt < RETRY_INTERVAL_MS) {
            return false;
        }
        lastLoadAttempt = now;
        File indexFile = new File(dir, INDEX_FILE);
        if (indexFile.exists()) {
            try {
                cities = readIndex(indexFile);
                return true;
            } catch (IOException e) {
                Log.w(TAG, "Broken " + indexFile + ", downloading the dump again", e);
                indexFile.delete();
            }
        }
        File dumpFile = new File(dir, DUMP_FILE);
        try {
            HttpUtils.download(DUMP_URL, dumpFile, DOWNLOAD_TIMEOUT_MS);
            Cities loaded;
            try (InputStream in = new FileInputStream(dumpFile)) {
                loaded = parseDump(in);
            }
            writeIndex(loaded, indexFile);
            cities = loaded;
            Log.i(TAG, "Loaded " + loaded.ids.length + " cities from " + DUMP_URL);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to load cities from " + DUMP_URL, e);
            return false;
        } finally {
            dumpFile.delete();
        }
    }

    static Cities parseDump(InputStream zipStream) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(zipStream);
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (DUMP_ENTRY.equals(entry.getName())) {
                return parseCities(new BufferedReader(new InputStreamReader(zipInputStream, StandardCharsets.UTF_8), 64 * 1024));
            }
        }
        throw new IOException(DUMP_ENTRY + " not found in the dump");
    }

    private static Cities parseCities(BufferedReader in) throws IOException {
        int size = 0;
        int[] ids = new int[200_000];
        float[] lats = new float[ids.length];
        float[] lngs = new float[ids.length];
        int[] tabs = new int[8];
        String line;
        while ((line = in.readLine()) != null) {
            // geonameid, name, asciiname, alternatenames, latitude, longitude, feature class, feature code, ...
            int tabCount = 0;
            for (int i = line.indexOf('\t'); i >= 0 && tabCount < tabs.length; i = line.indexOf('\t', i + 1)) {
                tabs[tabCount++] = i;
            }
            if (tabCount < tabs.length || EXCLUDED_FEATURE_CODES.contains(line.substring(tabs[6] + 1, tabs[7]))) {
                continue;
            }
            if (size == ids.length) {
                ids = Arrays.copyOf(ids, size * 2);
                lats = Arrays.copyOf(lats, size * 2);
                lngs = Arrays.copyOf(lngs, size * 2);
            }
            try {
                ids[size] = Integer.parseInt(line.substring(0, tabs[0]));
                lats[size] = Float.parseFloat(line.substring(tabs[3] + 1, tabs[4]));
                lngs[size] = Float.parseFloat(line.substring(tabs[4] + 1, tabs[5]));
                size++;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Skipping broken line: " + line);
            }
        }
        if (size == 0) {
            throw new IOException("No cities in the dump");
        }
        return new Cities(Arrays.copyOf(ids, size), Arrays.copyOf(lats, size), Arrays.copyOf(lngs, size));
    }

    static void writeIndex(Cities cities, File file) throws IOException {
        int count = cities.ids.length;
        ByteBuffer buffer = ByteBuffer.allocate(8 + 12 * count);
        buffer.putInt(INDEX_MAGIC).putInt(count);
        buffer.asIntBuffer().put(cities.ids);
        buffer.position(buffer.position() + 4 * count);
        buffer.asFloatBuffer().put(cities.lats);
        buffer.position(buffer.position() + 4 * count);
        buffer.asFloatBuffer().put(cities.lngs);
        File tmpFile = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmpFile)) {
            out.write(buffer.array());
        }
        if (!tmpFile.renameTo(file)) {
            tmpFile.delete();
            throw new IOException("Can't rename " + tmpFile + " to " + file);
        }
    }

    static Cities readIndex(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            in.readFully(bytes);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if (bytes.length < 8 || buffer.getInt() != INDEX_MAGIC) {
            throw new IOException("Not a cities index");
        }
        int count = buffer.getInt();
        if (count <= 0 || bytes.length != 8 + 12L * count) {
            throw new IOException("Truncated cities index");
        }
        int[] ids = new int[count];
        float[] lats = new float[count];
        float[] lngs = new float[count];
        buffer.asIntBuffer().get(ids);
        buffer.position(buffer.position() + 4 * count);
        buffer.asFloatBuffer().get(lats);
        buffer.position(buffer.position() + 4 * count);
        buffer.asFloatBuffer().get(lngs);
        return new Cities(ids, lats, lngs);
    }

    private static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    // Returns the nearest cities sorted by distance, or null if the dump is not loaded
    List<Nearest> find(double lat, double lng, int count) {
        if (!ensureLoaded()) {
            return null;
        }
        return find(cities, lat, lng, count);
    }

    static List<Nearest> find(Cities snapshot, double lat, double lng, int count) {
        int limit = Math.max(1, count);
        PriorityQueue<Nearest> farthestFirst = new PriorityQueue<>(limit + 1, (a, b) -> Float.compare(b.distance, a.distance));
        for (int i = 0; i < snapshot.ids.length; i++) {
            float distance = (float) distanceKm(lat, lng, snapshot.lats[i], snapshot.lngs[i]);
            if (farthestFirst.size() < limit) {
                farthestFirst.add(new Nearest(snapshot.ids[i], distance));
            } else if (distance < farthestFirst.peek().distance) {
                farthestFirst.poll();
                farthestFirst.add(new Nearest(snapshot.ids[i], distance));
            }
        }
        List<Nearest> res = new ArrayList<>(farthestFirst);
        res.sort((a, b) -> Float.compare(a.distance, b.distance));
        return res;
    }
}
