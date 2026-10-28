package org.meteosphere.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.json.JSONException;

// The caches of the local provider in one SQLite database, as DbCache of wpr keeps its: a value by its key until it
// expires, so a new process asks the sources for nothing it still has. Callers of a key that is being loaded wait
// for that load and get its value or its failure, so each value is asked for once however many need it at a time.
// Without init(), as in the unit tests, nothing is kept.
final class DbCache {

    private static final String TAG = "DbCache";
    private static final String NAME = "local_cache.db";
    private static final long CLEANUP_INTERVAL_MS = 60 * 60 * 1000;

    interface Loader {
        // null for none, which isn't stored
        String load() throws IOException, JSONException;
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, NAME, null, 1);
            setWriteAheadLoggingEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE cache (key TEXT PRIMARY KEY, value TEXT NOT NULL, expires INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX cache_expires ON cache (expires)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private static final Map<String, CompletableFuture<String>> LOADING = new ConcurrentHashMap<>();
    private static Helper sHelper;
    private static long sLastCleanup;

    private DbCache() {
    }

    // The database opens on the first use, off the main thread
    static synchronized void init(Context context) {
        if (sHelper == null) {
            sHelper = new Helper(context.getApplicationContext());
        }
    }

    private static synchronized SQLiteDatabase db() {
        if (sHelper == null) {
            return null;
        }
        try {
            return sHelper.getWritableDatabase();
        } catch (SQLiteException e) {
            Log.w(TAG, "Can't open the cache", e);
            return null;
        }
    }

    // The value, null if there is none or it has expired
    static String get(String key) {
        SQLiteDatabase db = db();
        if (db == null) {
            return null;
        }
        try (Cursor cursor = db.rawQuery("SELECT value FROM cache WHERE key = ? AND expires > ?",
                new String[]{key, String.valueOf(System.currentTimeMillis())})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } catch (SQLiteException e) {
            Log.w(TAG, "Can't read " + key, e);
            return null;
        }
    }

    // When the value kept for ttlMs came from its source, in ms; 0 if there is none or it has expired
    static long loadedAt(String key, long ttlMs) {
        SQLiteDatabase db = db();
        if (db == null) {
            return 0;
        }
        try (Cursor cursor = db.rawQuery("SELECT expires FROM cache WHERE key = ? AND expires > ?",
                new String[]{key, String.valueOf(System.currentTimeMillis())})) {
            return cursor.moveToFirst() ? cursor.getLong(0) - ttlMs : 0;
        } catch (SQLiteException e) {
            Log.w(TAG, "Can't read " + key, e);
            return 0;
        }
    }

    static void put(String key, String value, long ttlMs) {
        SQLiteDatabase db = db();
        if (db == null) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            ContentValues values = new ContentValues();
            values.put("key", key);
            values.put("value", value);
            values.put("expires", now + ttlMs);
            db.insertWithOnConflict("cache", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            boolean cleanup;
            synchronized (DbCache.class) {
                cleanup = now - sLastCleanup > CLEANUP_INTERVAL_MS;
                if (cleanup) {
                    sLastCleanup = now;
                }
            }
            if (cleanup) {
                db.delete("cache", "expires <= ?", new String[]{String.valueOf(now)});
            }
        } catch (SQLiteException e) {
            Log.w(TAG, "Can't store " + key, e);
        }
    }

    // The stored value, or the one the loader gives, which is stored for ttlMs
    static String get(String key, long ttlMs, Loader loader) throws IOException, JSONException {
        String stored = get(key);
        if (stored != null) {
            return stored;
        }
        CompletableFuture<String> own = new CompletableFuture<>();
        CompletableFuture<String> running = LOADING.putIfAbsent(key, own);
        if (running != null) {
            return join(running);
        }
        try {
            // A load that has just finished
            String value = get(key);
            if (value == null) {
                value = loader.load();
                if (value != null) {
                    put(key, value, ttlMs);
                }
            }
            own.complete(value);
            return value;
        } catch (IOException | JSONException | RuntimeException e) {
            own.completeExceptionally(e);
            throw e;
        } finally {
            LOADING.remove(key, own);
            if (!own.isDone()) {
                own.completeExceptionally(new IOException("The load failed"));
            }
        }
    }

    private static String join(CompletableFuture<String> running) throws IOException, JSONException {
        try {
            return running.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof JSONException) {
                throw (JSONException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IOException(cause);
        }
    }
}
