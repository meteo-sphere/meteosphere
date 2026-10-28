package org.meteosphere.data.sun;

import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.data.http.HttpUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pictures of the Sun from Helioviewer (api.helioviewer.org, by NASA and ESA): the frames of SDO and SOHO at any
 * moment, drawn at the size asked. Free and without a key; the app asks it directly, as the earthquakes, since the
 * Sun is the same for everyone. SDO's own site serves only the latest pictures and was down for weeks in 2026.
 * Pictures are kept in a dir of the app cache. All of it blocks: call it off the main thread.
 */
public final class SunImages {

    private static final String TAG = "SunImages";

    private static final String API = "https://api.helioviewer.org/v2/";
    private static final int TIMEOUT_MS = 30000;
    /** The latest frame is looked up again after this long; SDO's reach Helioviewer some 20 minutes late anyway */
    public static final long FRESH_MS = 15 * 60 * 1000L;
    // Pictures not looked at for this long are deleted, and the oldest ones over the size
    private static final long KEEP_MS = 3 * 24 * 60 * 60 * 1000L;
    private static final long MAX_BYTES = 80L * 1024 * 1024;
    private static final String LATEST = "latest_";
    /** A tile's side in pixels: the parts of a picture looked at closer are asked for on a grid of them */
    public static final int TILE = 512;

    /** A frame of a channel: when it was taken and the Sun's radius in it */
    public static final class Frame {
        public final SunChannel channel;
        /** UTC */
        public final long time;
        /** Arc seconds, 0 when unknown */
        public final double radius;

        Frame(SunChannel channel, long time, double radius) {
            this.channel = channel;
            this.time = time;
            this.radius = radius;
        }
    }

    private static boolean isPruned;
    // When a request to Helioviewer last failed, System.nanoTime(), 0 for never
    private static volatile long failedAt;

    private final File dir;

    public SunImages(File cacheDir) {
        this.dir = new File(cacheDir, "sun");
    }

    /**
     * The channel's latest frame: the one looked up in the last minutes, or a new lookup when online is set, or the
     * last one known when that fails
     */
    public Frame latest(SunChannel channel, boolean online) throws IOException {
        File file = new File(this.dir, LATEST + channel.key);
        Frame saved = readLatest(channel, file);
        if (saved != null && (!online || System.currentTimeMillis() - file.lastModified() < FRESH_MS)) {
            return saved;
        }
        if (!online) {
            throw new IOException("No saved frame of " + channel.key);
        }
        try {
            Frame frame = closest(channel, System.currentTimeMillis());
            writeLatest(file, frame);
            return frame;
        } catch (IOException e) {
            if (saved == null) {
                throw e;
            }
            Log.w(TAG, "Showing the saved frame: " + e);
            return saved;
        }
    }

    /** Whether a request to Helioviewer failed in the last withinMs: the cards wait before asking again */
    public static boolean failedWithin(long withinMs) {
        long at = failedAt;
        return at != 0 && System.nanoTime() - at < withinMs * 1_000_000L;
    }

    /** The frame of the channel nearest the time */
    public Frame closest(SunChannel channel, long time) throws IOException {
        String response;
        try {
            response = HttpUtils.get(API + "getClosestImage/?date=" + iso(time) + "&sourceId=" + channel.sourceId, TIMEOUT_MS);
        } catch (IOException e) {
            failedAt = System.nanoTime();
            throw e;
        }
        try {
            JSONObject json = new JSONObject(response);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(json.getString("date"));
            // The radius in the source's pixels, which are of scale arc seconds
            double radius = json.optDouble("rsun", 0) * json.optDouble("scale", 0);
            return new Frame(channel, date.getTime(), Double.isNaN(radius) ? 0 : radius);
        } catch (JSONException | ParseException | NullPointerException e) {
            failedAt = System.nanoTime();
            throw new IOException("Bad frame of " + channel.key + ": " + response, e);
        }
    }

    /** The whole picture of image() when it was drawn before, without asking for it, or null */
    public File kept(SunChannel channel, long time, int size) {
        File file = file(channel, time, size);
        return file.exists() ? file : null;
    }

    private File file(SunChannel channel, long time, int size) {
        return new File(this.dir, channel.key + "_" + time / 1000 + "_" + size + ".png");
    }

    /** The whole picture of the frame nearest the time, size pixels square: from the disk when it was drawn before */
    public File image(SunChannel channel, long time, int size) throws IOException {
        return fetch(url(channel, time, channel.field() / size, 0, 0, size, size), file(channel, time, size), channel);
    }

    /**
     * A tile of the frame nearest the time: the one at column and row, from the left and the top, of the grid of
     * 2^level tiles a side over the whole picture; from the disk when it was drawn before
     */
    public File tile(SunChannel channel, long time, int level, int column, int row) throws IOException {
        int count = 1 << level;
        double field = channel.field();
        double x = ((column + 0.5) / count - 0.5) * field;
        double y = ((row + 0.5) / count - 0.5) * field;
        return fetch(url(channel, time, field / (count * TILE), x, y, TILE, TILE), tileFile(channel, time, level, column, row), channel);
    }

    /** The tile of tile() when it was drawn before, without asking for it, or null */
    public File keptTile(SunChannel channel, long time, int level, int column, int row) {
        File file = tileFile(channel, time, level, column, row);
        if (!file.exists()) {
            return null;
        }
        file.setLastModified(System.currentTimeMillis());
        return file;
    }

    private File tileFile(SunChannel channel, long time, int level, int column, int row) {
        return new File(this.dir, channel.key + "_" + time / 1000 + "_t" + level + "_" + column + "_" + row + ".png");
    }

    /** The level of the finest tiles worth asking for, at the channel's finest scale */
    public static int finestLevel(SunChannel channel) {
        return (int) Math.floor(Math.log(channel.field() / (TILE * channel.finest())) / Math.log(2) + 1e-6);
    }

    // The picture at the url, kept in the file: from the disk when it was drawn before
    private File fetch(String url, File file, SunChannel channel) throws IOException {
        prune();
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis());
            return file;
        }
        // Tiles are fetched side by side: made by another one, the dir is there
        if (!this.dir.mkdirs() && !this.dir.isDirectory()) {
            throw new IOException("Can't make " + this.dir);
        }
        try {
            HttpUtils.download(url, file, TIMEOUT_MS);
        } catch (IOException e) {
            failedAt = System.nanoTime();
            throw e;
        }
        // An error comes as a page, not as a picture
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0) {
            file.delete();
            failedAt = System.nanoTime();
            throw new IOException("Not a picture of " + channel.key);
        }
        return file;
    }

    private static String url(SunChannel channel, long time, double scale, double x, double y, int width, int height) {
        return String.format(Locale.US, "%stakeScreenshot/?date=%s&imageScale=%.4f&layers=%%5B%s,1,100%%5D&x0=%.1f&y0=%.1f"
                + "&width=%d&height=%d&display=true&watermark=false", API, iso(time), scale, channel.layer, x, y, width, height);
    }

    private static String iso(long time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(time));
    }

    // "time radius": the frame, the file's date when it was looked up
    private static Frame readLatest(SunChannel channel, File file) {
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = in.read(bytes);
            String[] parts = new String(bytes, 0, Math.max(read, 0), StandardCharsets.UTF_8).trim().split(" ");
            return new Frame(channel, Long.parseLong(parts[0]), Double.parseDouble(parts[1]));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void writeLatest(File file, Frame frame) {
        if (!this.dir.isDirectory() && !this.dir.mkdirs()) {
            return;
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(String.format(Locale.US, "%d %.4f", frame.time, frame.radius).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "Can't save the frame: " + e);
        }
    }

    // Once a process: the pictures not looked at for days, then the oldest ones over the size
    private void prune() {
        synchronized (SunImages.class) {
            if (isPruned) {
                return;
            }
            isPruned = true;
        }
        File[] files = this.dir.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) {
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        long now = System.currentTimeMillis();
        long bytes = 0;
        for (File file : files) {
            bytes += file.length();
            if (now - file.lastModified() > KEEP_MS || bytes > MAX_BYTES) {
                file.delete();
            }
        }
    }
}
