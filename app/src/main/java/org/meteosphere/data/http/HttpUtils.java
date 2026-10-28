package org.meteosphere.data.http;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class HttpUtils {

    private static final String TAG = "HttpUtils";
    private static final String USER_AGENT = "Meteo Android v2";

    /** A response other than HTTP 200. */
    public static final class HttpStatusException extends IOException {
        public final int statusCode;
        /** What came with it, like a server's own message; null when nothing did or it couldn't be read */
        public final String body;

        HttpStatusException(String url, int statusCode, String body) {
            super("HTTP " + statusCode + " for " + url);
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private HttpUtils() {
    }

    public static String get(String url, int timeoutMs) throws IOException {
        return send(url, null, null, null, timeoutMs, true);
    }

    // The same with a header, like the Authorization one, which is not logged
    public static String get(String url, String headerName, String headerValue, int timeoutMs) throws IOException {
        return send(url, headerName, headerValue, null, timeoutMs, true);
    }

    // A GET left out of RequestStats: the wpr server counts the requests to the sources itself
    public static String getUncounted(String url, int timeoutMs) throws IOException {
        return send(url, null, null, null, timeoutMs, false);
    }

    // A POST of a JSON body, with a header like get() takes
    public static String postJson(String url, String headerName, String headerValue, String body, int timeoutMs) throws IOException {
        return send(url, headerName, headerValue, body, timeoutMs, true);
    }

    public static void download(String url, File file, int timeoutMs) throws IOException {
        Log.i(TAG, "DOWNLOAD: " + url + " -> " + file);
        RequestStats.count(url);
        HttpURLConnection conn = openConnection(url, timeoutMs);
        try {
            checkStatus(conn, url);
            File tmpFile = new File(file.getPath() + ".tmp");
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmpFile)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            if (!tmpFile.renameTo(file)) {
                tmpFile.delete();
                throw new IOException("Can't rename " + tmpFile + " to " + file);
            }
        } finally {
            conn.disconnect();
        }
    }

    // HttpURLConnection requests and unpacks gzip on its own. A GET without a body, a POST with it.
    private static String send(String url, String headerName, String headerValue, String body, int timeoutMs,
                               boolean counted) throws IOException {
        Log.i(TAG, (body == null ? "GET: " : "POST: ") + url);
        if (counted) {
            RequestStats.count(url);
        }
        HttpURLConnection conn = openConnection(url, timeoutMs);
        try {
            if (headerName != null) {
                conn.setRequestProperty(headerName, headerValue);
            }
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(bytes);
                }
            }
            checkStatus(conn, url);
            try (InputStream in = conn.getInputStream()) {
                return readText(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    public static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void checkStatus(HttpURLConnection conn, String url) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new HttpStatusException(url, responseCode, errorBody(conn));
        }
    }

    // The status alone tells when the body is missing or can't be read
    private static String errorBody(HttpURLConnection conn) {
        try (InputStream in = conn.getErrorStream()) {
            return in != null ? readText(in) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static HttpURLConnection openConnection(String url, int timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoInput(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setUseCaches(false);
        return conn;
    }
}
