package org.meteosphere.settings;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.data.model.Place;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The locations kept between launches: the cached locations with their forecasts, the current location and the
 * last search results, each a JSON file of its own under files/weather. They used to be strings in the shared
 * preferences, which then weighed half a megabyte: every setting waited for the whole file to be parsed, and
 * every forecast update wrote all of it again. Now an update writes only the location it changed.
 *
 * A location is parsed when it is first asked for, on the asking thread, while a background thread parses the
 * others ahead: opening one location's details waits for its file only, not for all of them.
 *
 * One per process, unlike AppSettings, which init() recreates: a new instance must not read back files
 * whose writes are still queued. The files are read once; from then on the memory is what counts and the
 * files only follow it.
 */
final class LocationStore {

    private static final String TAG = "LocationStore";
    private static final String SUFFIX = ".json";

    private static LocationStore instance;

    // All the cached locations, parsed or not yet
    private final Set<Integer> ids = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer, Place> parsed = new ConcurrentHashMap<>();
    // Parsing, adding and removing a location happen under its lock, so that one does not undo another
    private final ConcurrentHashMap<Integer, Object> locks = new ConcurrentHashMap<>();

    private final Object currentPlaceLock = new Object();
    private Place currentPlace;
    // Read from its file or set since the start; guarded by currentPlaceLock
    private boolean isCurrentPlaceKnown;

    private final Object foundPlacesLock = new Object();
    private List<Place> foundPlaces;

    private final File locationsDir;
    private final File currentPlaceFile;
    private final File foundPlacesFile;
    // The ids are known: the files are listed
    private final CountDownLatch indexed = new CountDownLatch(1);
    // What each file is to hold once written, null for a deleted one; a file is written once for several updates
    private final HashMap<File, String> pendingWrites = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, TAG));

    static synchronized LocationStore get(Context context) {
        if (instance == null) {
            instance = new LocationStore(context);
        }
        return instance;
    }

    private LocationStore(Context context) {
        File dir = new File(context.getFilesDir(), "weather");
        this.locationsDir = new File(dir, "locations");
        this.currentPlaceFile = new File(dir, "current" + SUFFIX);
        this.foundPlacesFile = new File(dir, "found_places" + SUFFIX);
        this.executor.execute(() -> {
            try {
                listFiles();
            } catch (Exception e) {
                Log.e(TAG, "Can't list the locations", e);
            } finally {
                this.indexed.countDown();
            }
            for (Integer id : this.ids) {
                get(id);
            }
        });
    }

    private void awaitIndexed() {
        boolean interrupted = false;
        while (true) {
            try {
                this.indexed.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runs on the store's thread, after the writes queued before it. */
    void runInBackground(Runnable runnable) {
        this.executor.execute(runnable);
    }

    List<Integer> ids() {
        awaitIndexed();
        return new ArrayList<>(this.ids);
    }

    boolean contains(int locationId) {
        awaitIndexed();
        return this.ids.contains(locationId);
    }

    /** The stored object itself, parsed now unless it is already; null when there is no such location. */
    Place get(int locationId) {
        Place location = this.parsed.get(locationId);
        if (location != null) {
            return location;
        }
        awaitIndexed();
        synchronized (lock(locationId)) {
            location = this.parsed.get(locationId);
            if (location == null && this.ids.contains(locationId)) {
                location = parseLocation(read(locationFile(locationId)));
                if (location != null) {
                    this.parsed.put(locationId, location);
                } else {
                    this.ids.remove(locationId);
                }
            }
            return location;
        }
    }

    List<Place> getAll() {
        ArrayList<Place> list = new ArrayList<>();
        for (Integer id : ids()) {
            Place location = get(id);
            if (location != null) {
                list.add(location);
            }
        }
        return list;
    }

    /** Adds a location that is not stored yet, and writes it. */
    void add(Place location) {
        awaitIndexed();
        synchronized (lock(location.id)) {
            this.parsed.put(location.id, location);
            this.ids.add(location.id);
        }
        save(location);
    }

    /** Writes the location as it is now; call after changing the object get() returned. */
    void save(Place location) {
        try {
            schedule(locationFile(location.id), location.toJson().toString());
        } catch (JSONException e) {
            Log.e(TAG, "Can't save location " + location.id, e);
        }
    }

    void remove(int locationId) {
        awaitIndexed();
        synchronized (lock(locationId)) {
            this.ids.remove(locationId);
            this.parsed.remove(locationId);
        }
        schedule(locationFile(locationId), null);
    }

    Place getCurrentPlace() {
        awaitIndexed();
        synchronized (this.currentPlaceLock) {
            if (!this.isCurrentPlaceKnown) {
                this.currentPlace = parseLocation(read(this.currentPlaceFile));
                this.isCurrentPlaceKnown = true;
            }
            return this.currentPlace;
        }
    }

    /**
     * Keeps a copy of the location as the current one; those holding the one before keep theirs. Does not wait
     * for the files: the application forgets the current location at its start when it has no permission.
     */
    void setCurrentPlace(Place location) {
        synchronized (this.currentPlaceLock) {
            this.isCurrentPlaceKnown = true;
            if (location == null) {
                this.currentPlace = null;
                schedule(this.currentPlaceFile, null);
                return;
            }
            try {
                JSONObject json = location.toJson();
                this.currentPlace = Place.fromJson(json);
                schedule(this.currentPlaceFile, json.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Can't save the current location", e);
            }
        }
    }

    List<Place> getFoundPlaces() {
        awaitIndexed();
        synchronized (this.foundPlacesLock) {
            if (this.foundPlaces == null) {
                this.foundPlaces = parseLocations(read(this.foundPlacesFile));
            }
            return new ArrayList<>(this.foundPlaces);
        }
    }

    /** Keeps copies, so that the caller's objects can change. */
    void setFoundPlaces(List<Place> locations) {
        JSONArray array = new JSONArray();
        try {
            if (locations != null) {
                for (Place location : locations) {
                    array.put(location.toJson());
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Can't save the found locations", e);
            return;
        }
        String json = array.toString();
        synchronized (this.foundPlacesLock) {
            this.foundPlaces = parseLocations(json);
            schedule(this.foundPlacesFile, json);
        }
    }

    private Object lock(int locationId) {
        return this.locks.computeIfAbsent(locationId, id -> new Object());
    }

    private File locationFile(int locationId) {
        return new File(this.locationsDir, locationId + SUFFIX);
    }

    private void listFiles() {
        String[] names = this.locationsDir.list();
        if (names == null) {
            return;
        }
        for (String name : names) {
            // AtomicFile may have left a file's backup or its unfinished new version beside it
            String fileName = name.replaceFirst("\\.(bak|new)$", "");
            if (fileName.endsWith(SUFFIX)) {
                try {
                    this.ids.add(Integer.parseInt(fileName.substring(0, fileName.length() - SUFFIX.length())));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Not a location: " + name);
                }
            }
        }
    }

    private void schedule(File file, String content) {
        synchronized (this.pendingWrites) {
            boolean isQueued = this.pendingWrites.containsKey(file);
            this.pendingWrites.put(file, content);
            if (isQueued) {
                return;
            }
        }
        this.executor.execute(() -> {
            String latest;
            synchronized (this.pendingWrites) {
                latest = this.pendingWrites.remove(file);
            }
            if (latest != null) {
                write(file, latest);
            } else {
                new AtomicFile(file).delete();
            }
        });
    }

    private static void write(File file, String content) {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            Log.e(TAG, "Can't create " + dir);
            return;
        }
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = atomicFile.startWrite();
            out.write(content.getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(out);
        } catch (IOException e) {
            if (out != null) {
                atomicFile.failWrite(out);
            }
            Log.e(TAG, "Can't write " + file, e);
        }
    }

    private static String read(File file) {
        try {
            return new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Can't read " + file, e);
            return null;
        }
    }

    private static Place parseLocation(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            // Null for a place an older build saved, without an id
            return Place.fromJson(new JSONObject(json));
        } catch (Exception e) {
            Log.e(TAG, "Can't parse a location", e);
            return null;
        }
    }

    private static List<Place> parseLocations(String json) {
        ArrayList<Place> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Place location = Place.fromJson(array.getJSONObject(i));
                if (location != null) {
                    list.add(location);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't parse the locations", e);
        }
        return list;
    }
}
