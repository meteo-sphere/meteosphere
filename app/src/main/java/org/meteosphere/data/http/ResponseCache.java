package org.meteosphere.data.http;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

// Responses of the sources with a small free quota, kept in a dir of the app cache,
// so a new app process doesn't request them again. Files older than a day are deleted.
public final class ResponseCache {

    private static final String TAG = "ResponseCache";

    private static final long MAX_AGE_MS = 24 * 60 * 60 * 1000L;

    private final File dir;
    private boolean isPruned;

    public ResponseCache(File dir) {
        this.dir = dir;
    }

    private File file(String key) {
        try {
            StringBuilder name = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8))) {
                name.append(String.format(Locale.US, "%02x", b));
            }
            return new File(this.dir, name.append(".json").toString());
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    // Returns null if there is no response younger than ttlMs
    public String get(String key, long ttlMs) throws IOException {
        File file = file(key);
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() >= ttlMs) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            return HttpUtils.readText(in);
        }
    }

    public void put(String key, String response) throws IOException {
        if (!this.dir.isDirectory() && !this.dir.mkdirs()) {
            return;
        }
        prune();
        File file = file(key);
        File tmpFile = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmpFile)) {
            out.write(response.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmpFile.renameTo(file)) {
            tmpFile.delete();
            Log.w(TAG, "Can't cache " + key);
        }
    }

    private synchronized void prune() {
        if (this.isPruned) {
            return;
        }
        this.isPruned = true;
        File[] files = this.dir.listFiles();
        if (files != null) {
            long now = System.currentTimeMillis();
            for (File file : files) {
                if (now - file.lastModified() > MAX_AGE_MS) {
                    file.delete();
                }
            }
        }
    }
}
