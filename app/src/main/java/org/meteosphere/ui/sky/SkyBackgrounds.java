package org.meteosphere.ui.sky;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SkyBackgrounds {

    // The skies are drawn at most this high: the height of the 2:3 sky they are cut from, SkyShaders.SKY_HEIGHT
    private static final int LAYER_HEIGHT = 1440;
    // A few cities' full-screen backgrounds, about 4 MB each, and the list's card skies, a quarter MB each
    private static final int COMPOSED_CACHE_BYTES = 24 * 1024 * 1024;
    /** Where a picture of another shape is cut from the layers: the middle for the screen */
    public static final float CROP_CENTER = 0.5f;

    // Kept for the whole process, so coming back to the details does not compose the backgrounds again;
    // given back when the system runs short of memory (see trimMemory). Main thread only.
    private final LruCache<String, Bitmap> composedCache = new LruCache<String, Bitmap>(COMPOSED_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };
    // Main thread: the stills to draw ahead, by their keys, in the order asked, and whether one is being drawn
    private final java.util.LinkedHashMap<String, Object[]> preparing = new java.util.LinkedHashMap<>();
    // The cancel of the still being prepared, null when none is: set as a sky is asked for, which it gives way to.
    // Written on the main thread, read by compose on any
    private volatile java.util.concurrent.atomic.AtomicBoolean preparingNow;
    // Callbacks waiting for a background that is being composed, by its key
    private final HashMap<String, ArrayList<OnComposed>> pendingComposes = new HashMap<>();
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // One thread: the skies are drawn one by one
    private final ExecutorService composeExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(() -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        runnable.run();
    }, "WeatherBackgroundCompose"));
    // Compose thread only
    private File stillsDir;
    // This install's time, in the saved stills' names: another install's shaders may draw them otherwise
    private final long installed;
    // The names of the stills saved, as far as known: any thread
    private final java.util.Set<String> savedNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Main thread: the callbacks of skies that came too late to be waited for, by their keys, with their layers, for
    // the still that may yet come
    private final HashMap<String, Object[]> lateWaiters = new HashMap<>();

    // One for the process, so that its caches and its compose thread serve every screen
    private static SkyBackgrounds instance;

    /** The one for the process, made from the application context on first use */
    public static synchronized SkyBackgrounds getInstance(Context context) {
        if (instance == null) {
            instance = new SkyBackgrounds(context.getApplicationContext());
        }
        return instance;
    }

    private SkyBackgrounds(Context context) {
        this.context = context;
        long installed;
        try {
            installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            installed = 0;
        }
        this.installed = installed;
    }

    /** Size of a composed background for the screen: the screen's shape, no taller than the layers' pictures */
    public static Point composedSize(Context context) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.min(metrics.widthPixels, metrics.heightPixels);
        int height = Math.max(metrics.widthPixels, metrics.heightPixels);
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowManager windowManager = context.getSystemService(android.view.WindowManager.class);
            if (windowManager != null) {
                android.graphics.Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
                width = Math.min(bounds.width(), bounds.height());
                height = Math.max(bounds.width(), bounds.height());
            }
        }
        // The pictures are 1440 high: a taller bitmap would only blow them up
        float scale = Math.min(1.0f, LAYER_HEIGHT / (float) height);
        return new Point(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
    }

    /**
     * What a view showing the composed sky of the layers keeps to tell it is still the right one: the layers drawn,
     * the moon's among them, its age drawn too
     */
    public String skyTag(List<String> layers) {
        return layers.toString();
    }

    private String key(List<String> layers, Point size, float crop) {
        return skyTag(layers) + "@" + size.x + "x" + size.y + "/" + crop;
    }

    /** The background of the layers composed into one picture, or null while it isn't composed yet */
    public Bitmap getComposed(List<String> layers, Point size) {
        return getComposed(layers, size, CROP_CENTER);
    }

    /** As getComposed, cut at the crop: 0 the top of the sky, 0.5 its middle */
    public Bitmap getComposed(List<String> layers, Point size, float crop) {
        return this.composedCache.get(key(layers, size, crop));
    }

    /**
     * Composes the layers into one picture off the main thread and hands it to onComposed on the main thread.
     * A picture that is being composed already isn't composed again; onComposed may be null for a preload.
     */
    public void compose(List<String> layers, Point size, OnComposed onComposed) {
        compose(layers, size, CROP_CENTER, onComposed);
    }

    /**
     * Draws the still of the layers ahead and keeps it on disk only, not in memory, when no sky is waited for, one at
     * a time: asked for later, it comes at once. Main thread.
     */
    public void prepare(List<String> layers, Point size, float crop) {
        String key = key(layers, size, crop);
        if (this.composedCache.get(key) != null || this.preparing.containsKey(key)) {
            return;
        }
        this.preparing.put(key, new Object[]{new ArrayList<>(layers), size, crop});
        prepareNext();
    }

    // Main thread: the next still to prepare, when none is being composed or prepared
    private void prepareNext() {
        if (this.preparingNow != null || this.preparing.isEmpty()) {
            return;
        }
        synchronized (this.pendingComposes) {
            if (!this.pendingComposes.isEmpty()) {
                return;
            }
        }
        String key = this.preparing.keySet().iterator().next();
        Object[] job = this.preparing.remove(key);
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean();
        this.preparingNow = cancel;
        this.composeExecutor.execute(() -> {
            boolean done = false;
            try {
                @SuppressWarnings("unchecked")
                List<String> layers = (List<String>) job[0];
                done = prepareNow(layers, (Point) job[1], (float) job[2], key, cancel);
            } finally {
                boolean again = !done;
                this.mainHandler.post(() -> {
                    this.preparingNow = null;
                    // Given way to a sky waited for: drawn again later
                    if (again && !this.preparing.containsKey(key)) {
                        this.preparing.put(key, job);
                    }
                    prepareNext();
                });
            }
        });
    }

    // Compose thread: the still drawn and saved, unless saved already; false if it gave way to a sky waited for
    private boolean prepareNow(List<String> layers, Point size, float crop, String key,
            java.util.concurrent.atomic.AtomicBoolean cancel) {
        if (SkyRenderer.failed()) {
            return true;
        }
        File file = stillFile(key);
        if (file == null || file.exists()) {
            return true;
        }
        Bitmap still = composeStill(layers, size, crop, file, cancel, key);
        if (still != null) {
            saveStill(still, file);
            still.recycle();
            return true;
        }
        return !cancel.get();
    }

    /** As compose, cut at the crop: 0 the top of the sky, 0.5 its middle */
    public void compose(List<String> layers, Point size, float crop, OnComposed onComposed) {
        String key = key(layers, size, crop);
        Bitmap cached = this.composedCache.get(key);
        if (cached != null) {
            if (onComposed != null) {
                onComposed.onComposed(layers, cached);
            }
            return;
        }
        synchronized (this.pendingComposes) {
            ArrayList<OnComposed> waiting = this.pendingComposes.get(key);
            if (waiting != null) {
                if (onComposed != null) {
                    waiting.add(onComposed);
                }
                return;
            }
            waiting = new ArrayList<>();
            if (onComposed != null) {
                waiting.add(onComposed);
            }
            this.pendingComposes.put(key, waiting);
        }
        // A still being drawn ahead gives way to it, unless it is only to be read from its file
        java.util.concurrent.atomic.AtomicBoolean preparing = this.preparingNow;
        if (preparing != null && !this.savedNames.contains(String.valueOf(stillName(key)))) {
            preparing.set(true);
        }
        ArrayList<String> layersCopy = new ArrayList<>(layers);
        this.composeExecutor.execute(() -> {
            Bitmap bitmap = composeNow(layersCopy, size, crop, key);
            if (bitmap != null) {
                // Starts its upload to the GPU ahead of its first frame
                bitmap.prepareToDraw();
            }
            this.mainHandler.post(() -> {
                if (bitmap != null) {
                    this.composedCache.put(key, bitmap);
                }
                ArrayList<OnComposed> callbacks;
                synchronized (this.pendingComposes) {
                    callbacks = this.pendingComposes.remove(key);
                }
                if (bitmap != null && callbacks != null) {
                    for (OnComposed callback : callbacks) {
                        callback.onComposed(layersCopy, bitmap);
                    }
                } else if (bitmap == null && callbacks != null && !callbacks.isEmpty() && !SkyRenderer.failed()) {
                    // Not drawn in time: handed the still if it comes after all
                    this.lateWaiters.put(key, new Object[]{layersCopy, callbacks});
                }
                // Nothing waited for any more, perhaps: the stills to prepare go on
                prepareNext();
            });
        });
    }

    // Compose thread: a still picture of the animated weather's sky at its moment, the same every time; null if the
    // GPU can't draw it, the weather's colour showing instead
    private Bitmap composeNow(List<String> layers, Point size, float crop, String key) {
        if (SkyRenderer.failed()) {
            return null;
        }
        // Drawn once, then read back: the same picture every time, without the GPU's time for it. A still not drawn yet
        // isn't read: BitmapFactory would log the missing file as an error
        File file = stillFile(key);
        Bitmap saved = file == null || !file.exists() ? null : BitmapFactory.decodeFile(file.getPath());
        if (saved != null) {
            // Kept among the stills in use
            file.setLastModified(System.currentTimeMillis());
            return saved;
        }
        Bitmap still = composeStill(layers, size, crop, file, null, key);
        if (still != null) {
            saveStill(still, file);
        }
        return still;
    }

    // The saved stills: no more than these, those used longest ago dropped. Chosen: enough for every size of some
    // twenty cities' weathers (a card's, the details', a tab's, the notification's), about 25 MB at most
    private static final int SAVED_STILLS = 120;

    // Compose thread: where the still of a key is kept, drawn by this install of the app, whose shaders may differ
    // from the one before; null without a cache directory
    private File stillFile(String key) {
        if (this.stillsDir == null) {
            File dir = new File(this.context.getCacheDir(), "skies");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            this.stillsDir = dir;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".part")) {
                        // Half-written, left by a process that died while saving it
                        file.delete();
                    } else {
                        this.savedNames.add(file.getName());
                    }
                }
            }
        }
        String name = stillName(key);
        return name == null ? null : new File(this.stillsDir, name);
    }

    // The file name of the still of a key, drawn by this install; any thread
    private String stillName(String key) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1")
                    .digest((key + "#" + this.installed).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : digest) {
                name.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return name.append(".png").toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    // Compose thread: the still kept losslessly, through a temporary file so that a half-written one is never read
    private void saveStill(Bitmap still, File file) {
        if (file == null) {
            return;
        }
        File part = new File(file.getPath() + ".part");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
            still.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (java.io.IOException e) {
            part.delete();
            return;
        }
        if (!part.renameTo(file)) {
            part.delete();
            return;
        }
        this.savedNames.add(file.getName());
        File[] saved = this.stillsDir.listFiles();
        if (saved != null && saved.length > SAVED_STILLS) {
            Arrays.sort(saved, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < saved.length - SAVED_STILLS; i++) {
                this.savedNames.remove(saved[i].getName());
                saved[i].delete();
            }
        }
    }

    // Main thread: a still come after its time, shown where it was waited for, if anywhere; the waiters all let go if
    // the renderer failed meanwhile, no more stills to come
    private void lateStill(String key, Bitmap still) {
        if (SkyRenderer.failed()) {
            this.lateWaiters.clear();
        }
        Object[] waiting = this.lateWaiters.remove(key);
        if (waiting == null) {
            still.recycle();
            return;
        }
        this.composedCache.put(key, still);
        @SuppressWarnings("unchecked")
        List<String> layers = (List<String>) waiting[0];
        @SuppressWarnings("unchecked")
        List<OnComposed> callbacks = (List<OnComposed>) waiting[1];
        for (OnComposed callback : callbacks) {
            callback.onComposed(layers, still);
        }
    }

    // Compose thread: the sky with its stars and drops, the see-through half dropped; null if it couldn't be drawn,
    // in time or at all, or the cancel was set. One finished after its time is still saved to the file, for next time
    private Bitmap composeStill(List<String> layers, Point size, float crop, File file,
            java.util.concurrent.atomic.AtomicBoolean cancel, String key) {
        Bitmap[] still = SkyRenderer.get(this.context).renderStill(SkyScene.from(layers), size.x, size.y, crop, cancel,
                late -> this.composeExecutor.execute(() -> {
                    if (late == null) {
                        // Not coming: whatever waited for it let go
                        this.mainHandler.post(() -> this.lateWaiters.remove(key));
                        return;
                    }
                    if (file != null && !file.exists()) {
                        saveStill(late, file);
                    }
                    late.prepareToDraw();
                    this.mainHandler.post(() -> lateStill(key, late));
                }));
        if (still == null) {
            return null;
        }
        still[1].recycle();
        return still[0];
    }

    public void trimMemory(int level) {
        // Not on TRIM_MEMORY_BACKGROUND: it arrives half a second after leaving the app, which would make every
        // return compose the backgrounds again. Under real pressure a cached process is killed anyway.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            this.mainHandler.post(this.composedCache::evictAll);
        }
    }

    public interface OnComposed {
        void onComposed(List<String> layers, Bitmap bitmap);
    }
}
