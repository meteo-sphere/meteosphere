package org.meteosphere.data.http;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * How many requests the app sends each source a day, by UTC days, as wpr counts its own: the sources limit the
 * requests a day. In a database of its own, which outlives the caches; a request counts as it goes out, whatever
 * comes back. Without init(), as in the unit tests, nothing is counted.
 */
public final class RequestStats {

    private static final String TAG = "RequestStats";
    private static final String NAME = "request_stats.db";
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    // A year and a bit, as wpr keeps; older days go
    public static final int KEPT_DAYS = 400;

    /** The requests a day by source: the days from the first to today, a day without requests as 0 */
    public static final class Counts {
        public final long first;
        public final long today;
        public final Map<String, int[]> bySource;

        public Counts(long first, long today, Map<String, int[]> bySource) {
            this.first = first;
            this.today = today;
            this.bySource = bySource;
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context context) {
            super(context, NAME, null, 1);
            setWriteAheadLoggingEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE requests (day INTEGER NOT NULL, source TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY (day, source))");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private static Helper sHelper;
    private static long sCleanedDay;

    private RequestStats() {
    }

    // The database opens on the first request, off the main thread
    public static synchronized void init(Context context) {
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
            Log.w(TAG, "Can't open the counts", e);
            return null;
        }
    }

    /** Today as days since 1970, UTC */
    public static long today() {
        return System.currentTimeMillis() / DAY_MS;
    }

    // The source a URL goes to, by its host, named as wpr names them; another one as its host
    static String source(String url) {
        String host;
        try {
            host = new URL(url).getHost();
        } catch (MalformedURLException e) {
            return "unknown";
        }
        switch (host) {
            case "api.open-meteo.com":
                return "open-meteo";
            case "geocoding-api.open-meteo.com":
                return "open-meteo-geocoding";
            case "weatherapi.foreca.net":
                return "foreca";
            case "services.swpc.noaa.gov":
                return "noaa";
            case "download.geonames.org":
                return "geonames";
            case "earthquake.usgs.gov":
                return "usgs";
            case "api.helioviewer.org":
                return "helioviewer";
            default:
                return host;
        }
    }

    // A request to the URL is going out
    static void count(String url) {
        SQLiteDatabase db = db();
        if (db == null) {
            return;
        }
        String source = source(url);
        long day = today();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("day", day);
            values.put("source", source);
            values.put("count", 0);
            db.insertWithOnConflict("requests", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            db.execSQL("UPDATE requests SET count = count + 1 WHERE day = ? AND source = ?", new Object[]{day, source});
            synchronized (RequestStats.class) {
                if (day != sCleanedDay) {
                    sCleanedDay = day;
                    db.delete("requests", "day <= ?", new String[]{String.valueOf(day - KEPT_DAYS)});
                }
            }
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            Log.w(TAG, "Can't count a request to " + source, e);
        } finally {
            db.endTransaction();
        }
    }

    /** The kept counts, from the first day with a request; null if the database fails. Off the main thread */
    public static Counts load() {
        SQLiteDatabase db = db();
        if (db == null) {
            return null;
        }
        long today = today();
        long first = today;
        Map<String, Map<Long, Integer>> rows = new TreeMap<>();
        try (Cursor cursor = db.rawQuery("SELECT day, source, count FROM requests WHERE day > ? AND day <= ?",
                new String[]{String.valueOf(today - KEPT_DAYS), String.valueOf(today)})) {
            while (cursor.moveToNext()) {
                long day = cursor.getLong(0);
                first = Math.min(first, day);
                rows.computeIfAbsent(cursor.getString(1), source -> new TreeMap<>()).put(day, cursor.getInt(2));
            }
        } catch (SQLiteException e) {
            Log.w(TAG, "Can't read the counts", e);
            return null;
        }
        Map<String, int[]> bySource = new TreeMap<>();
        for (Map.Entry<String, Map<Long, Integer>> source : rows.entrySet()) {
            int[] counts = new int[(int) (today - first + 1)];
            for (Map.Entry<Long, Integer> day : source.getValue().entrySet()) {
                counts[(int) (day.getKey() - first)] = day.getValue();
            }
            bySource.put(source.getKey(), counts);
        }
        return new Counts(first, today, bySource);
    }
}
