package org.meteosphere.ui.sky;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import androidx.annotation.RequiresApi;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import org.meteosphere.BuildConfig;
import org.meteosphere.R;

/**
 * Draws the animated weather's sky and clouds with OpenGL ES 3 on a thread of its own, res/raw/sky_clouds.frag: the
 * clouds marched through as a volume over a 3D noise texture. Each frame's steps start a little further on than the
 * last's and the frames are averaged, so a few steps a frame make a smooth picture.
 *
 * A view's frames come out through an ImageReader as hardware bitmaps twice as high as the sky: its colours on top,
 * how far one sees through the clouds below, for the stars drawn over it; the live sky's, from Android 13. Still
 * pictures are drawn the same way, from many frames, with their stars and drops over them (sky_overlay.frag), and read
 * back as ordinary bitmaps, on any Android.
 */
final class SkyRenderer {

    private static final String TAG = "SkyRenderer";
    // A whole-screen triangle, from the vertex's index alone
    private static final String VERTEX = "#version 300 es\n"
            + "void main() { vec2 p = vec2(float((gl_VertexID & 1) << 2) - 1.0, float((gl_VertexID & 2) << 1) - 1.0);"
            + " gl_Position = vec4(p, 0.0, 1.0); }";
    // A marched pixel's weight against what it had, once a few of its marches are averaged: chosen, its last ten or so
    // making it, as its steps' offsets turn, as many as the frames' average had when every pixel marched each frame;
    // 1 / (n + 1) before, the true average of its n marches so far
    private static final float MIN_WEIGHT = 0.1f;
    // A sky standing still, its animation off: the passes averaged at once when it starts afresh, so that its picture
    // is smooth at once, and the least weight of a pass after
    private static final int FRESH_PASSES = 6;
    private static final float STILL_MIN_WEIGHT = 0.12f;
    // Of the 4x4 pixels of each block, the clouds pass marches one a frame, in a Bayer order (x + 4 y) so that the
    // turns spread over the block
    private static final int TURNS = 16;
    // How far the clouds' frames reach past a view's edges, in their pixels: the wind moves them about a pixel a frame
    // at most, so what it brings in between a pixel's marches, sixteen frames apart, is already there
    private static final int MARGIN = 16;
    // Chosen: how many turns of all the pixels, after a fresh start, the clouds are read smoothed over their
    // neighbours, less and less; each pixel then has as many marches averaged, its steps' noise gone
    private static final int SETTLE_TURNS = 4;
    // A settled sky only partly shown, as in a swipe: up to this many frames of each turn of all its pixels carried
    // over by the wind, none marched. A pixel then waits up to TURNS + this many frames between marches, the wind
    // carrying the nearest clouds about 0.6 of a pixel a frame (0.035 units a second, 2.3 units off at the top, at
    // 30 frames a second): 13 pixels, within the MARGIN they are drawn past the view's edges
    private static final int MAX_CARRIED = 6;
    private static final int[] TURN_CELLS = {0, 10, 2, 8, 5, 15, 7, 13, 1, 11, 3, 9, 4, 14, 6, 12};
    // The turn marching each block's middle, for a fresh start's first picture
    private static final int BLOCK_MIDDLE = -2;
    private static final int STILL_PASSES = 16;
    // A still's pieces: about this many rays each, under a live frame's, so that the screen's drawing waits for no more
    // than that; and how long its caller waits for all of them
    private static final int STILL_RAYS_PER_PIECE = 32768;
    private static final long STILL_TIMEOUT_S = 30;
    // A still's drops, caught in their fall a few seconds in
    private static final float STILL_DROPS_TIME = 3.7f;
    private static final int NOISE_SIZE = 256;
    // A storm's still: how much the bolt lights the clouds, and the bolt, chosen from the variants shown to the user
    private static final float STILL_FLASH = 0.15f;
    private static final float STILL_BOLT_SEED = 11;
    private static final float STILL_BOLT_ACROSS = 0.66f;
    private static final int STILL_STEPS = 64;
    // The weather map's texels across its period: the slice it is drawn from repeats
    // every 128 of its texels, eight of the map's each, read linearly between them as the slice's spline to about a
    // thousandth
    private static final int MAP_SIZE = 1024;
    private static final int MAP_UNIT = 7;
    // The bolt's picture, as the compose pass reads it
    private static final int BOLT_UNIT = 8;

    /** What a frame draws */
    static final class Params {
        SkyScene scene;
        float crop;
        float drift;
        float evolve;
        float clearTop;
        float clearBottom;
        float flash;
        // The bolt of lightning: its shape's seed, how bright, 0 none, and where across the sky it strikes
        float boltSeed;
        float boltBrightness;
        float boltAcross;
        float steps;
        // The average forgotten, as for another weather or after a jump in time
        boolean fresh;
        // The sky standing still, its animation off: a fresh start drawn smooth at once
        boolean still;
        // Which of the view's weathers the frame is of, given back as its image's timestamp's upper bits (FRAME_TAG):
        // a frame of the weather before can still come after another is given
        long weather;
        // The sky only partly shown, as in a swipe: once settled, carried over rather than marched, as far as it can
        boolean carry;
        // Only the lightning changed since the last frame: its clouds composed again, lit as the flash is, not marched
        boolean flashOnly;
    }

    // The frames' timestamps: the weather above these bits, a count of the target's frames below, so that they rise
    static final int FRAME_TAG = 24;

    /**
     * Told on the main thread of each new frame of a target, a hardware bitmap; the image is to be closed once it isn't
     * drawn
     */
    interface FrameListener {
        void onFrame(Target target, Bitmap bitmap, Image image);
    }

    /** A view's frames: made and used on the main thread, drawn on the renderer's */
    static final class Target {
        final int width;
        final int height;
        final FrameListener listener;
        // Renderer thread only
        ImageReader reader;
        EGLSurface surface;
        // The clouds as two frames of them, the one drawn and the one drawn into: each the sun's light and cover, and
        // the sky's light and depth (textures 2i and 2i + 1, drawn by framebuffer i)
        final int[] textures = new int[4];
        final int[] framebuffers = new int[2];
        // The quarter-size marches of a frame, one texel a 4x4 block, as two textures drawn by one framebuffer
        final int[] marched = new int[2];
        final int[] marchedBuffer = new int[1];
        final WeatherMap map = new WeatherMap();
        // The bolt of lightning as the compose pass draws it, core and glow, drawn once a bolt: its seed, where it
        // strikes and the crop it was drawn at, the seed NaN for none yet; and whether it can't be drawn into
        final int[] bolt = new int[1];
        final int[] boltBuffer = new int[1];
        float boltSeed = Float.NaN;
        float boltAcross;
        float boltCrop;
        boolean boltUnsupported;
        int current;
        int frame;
        // Frames presented, for their timestamps to rise
        int presented;
        // Frames drawn since the last fresh start, -1 before the first; and the wind's drift at the last
        int sinceFresh = -1;
        float lastDrift;
        // In the current turn of all the pixels: the frames marched, and those only carried over
        int turned;
        int carried;
        volatile boolean released;
        // The frame asked for and not drawn yet, under the target's lock
        Params pending;
        // The frame come and not yet taken by the view, under the target's lock: only the latest is kept, so at
        // most the view's two, this one and the one being taken out are out of the reader
        Image waiting;
        boolean scheduled;

        Target(int width, int height, FrameListener listener) {
            this.width = width;
            this.height = height;
            this.listener = listener;
        }
    }

    /** The weather map of its owner's scene, drawn as the scene's seed comes; renderer thread */
    private static final class WeatherMap {
        final int[] texture = new int[1];
        final int[] framebuffer = new int[1];
        float seed = Float.NaN;
    }

    private static SkyRenderer instance;

    private final Resources resources;
    private final Handler handler;
    // The frames come out on a thread of their own: on the drawing thread, a frame waiting for a free buffer would
    // wait for itself, the buffers being given back only as the frames before it come out
    private final Handler framesHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Renderer thread only
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLConfig config;
    private EGLSurface idleSurface = EGL14.EGL_NO_SURFACE;
    private int cloudsProgram;
    private int composeProgram;
    private int overlayProgram;
    private int noiseTexture;
    private int shapeTexture;
    private int detailTexture;
    private int moonTexture;
    private int vertexArray;
    private int historyFormat;
    private int historyType;
    // Whether the weather map can be drawn, into two half floats: a byte's steps would show in the clouds' edges
    private boolean mapped;
    private volatile boolean failed;
    private static volatile boolean everFailed;
    private final HashMap<String, Integer> locations = new HashMap<>();
    // Debug builds: the passes timed on the GPU, shown over the sky; null otherwise
    private SkyTimings timings;

    private SkyRenderer(Context context) {
        this.resources = context.getApplicationContext().getResources();
        HandlerThread thread = new HandlerThread("SkyRenderer", Process.THREAD_PRIORITY_DISPLAY);
        thread.start();
        this.handler = new Handler(thread.getLooper());
        HandlerThread frames = new HandlerThread("SkyFrames", Process.THREAD_PRIORITY_DISPLAY);
        frames.start();
        this.framesHandler = new Handler(frames.getLooper());
        this.handler.post(this::setUp);
    }

    /** Whether OpenGL couldn't be set up to draw the sky: the pictures are shown instead */
    static boolean failed() {
        return everFailed;
    }

    static synchronized SkyRenderer get(Context context) {
        if (instance == null) {
            instance = new SkyRenderer(context);
        }
        return instance;
    }

    // --- Views' frames ---

    /** A view's frames of the size; main thread */
    @RequiresApi(33)
    Target createTarget(int width, int height, FrameListener listener) {
        Target target = new Target(width, height, listener);
        this.handler.post(() -> setUpTarget(target));
        return target;
    }

    /** Draws a frame for the view, as soon as the last one is drawn; main thread */
    @RequiresApi(33)
    void render(Target target, Params params) {
        synchronized (target) {
            if (target.pending != null && target.pending.fresh) {
                // A fresh start asked for and not drawn yet stays fresh
                params.fresh = true;
            }
            if (target.pending != null && !target.pending.flashOnly) {
                // Nor is a march asked for lost to a flash's frame after it
                params.flashOnly = false;
            }
            target.pending = params;
            if (target.scheduled) {
                return;
            }
            target.scheduled = true;
        }
        this.handler.post(() -> {
            Params next;
            synchronized (target) {
                next = target.pending;
                target.pending = null;
                target.scheduled = false;
            }
            if (next != null) {
                drawTarget(target, next);
            }
        });
    }

    /** Gives back the view's frames; main thread */
    @RequiresApi(33)
    void release(Target target) {
        synchronized (target) {
            target.pending = null;
        }
        // No more frames delivered from now; its buffers given back on the renderer's thread
        target.released = true;
        this.handler.post(() -> releaseTarget(target));
    }

    // --- Still pictures ---

    /**
     * A still picture of the scene, width x height cut at the crop, drawn from many frames: [0] the sky, [1] how far
     * one sees through the clouds, grey; null if the GPU couldn't draw it. Any thread but the renderer's.
     */
    Bitmap[] renderStill(SkyScene scene, int width, int height, float crop) {
        return renderStill(scene, width, height, crop, null, null);
    }

    /**
     * As renderStill, given up as soon as the cancel is set, if any; one that comes after its caller stopped waiting is
     * handed to late, on the renderer's thread, if any, rather than lost: its sky, to be recycled by it, or null once it
     * is known it won't come
     */
    Bitmap[] renderStill(SkyScene scene, int width, int height, float crop, AtomicBoolean cancel,
            Consumer<Bitmap> late) {
        // At the weather's moment, the same picture every time
        return renderStill(scene, width, height, crop, scene.moment * SkyView.WIND, scene.moment * SkyView.CHANGE,
                cancel, late);
    }

    /**
     * As renderStill, the clouds drifted and changed as much as the drift and evolve say. Drawn in small pieces, each
     * waited for on the GPU, the views' frames and the screen's drawn between them: in one go its GPU time, hundreds of
     * live frames' worth, held up the screen for as long.
     */
    Bitmap[] renderStill(SkyScene scene, int width, int height, float crop, float drift, float evolve) {
        return renderStill(scene, width, height, crop, drift, evolve, null, null);
    }

    private Bitmap[] renderStill(SkyScene scene, int width, int height, float crop, float drift, float evolve,
            AtomicBoolean cancel, Consumer<Bitmap> late) {
        Still still = new Still(scene, width, height, crop, drift, evolve, cancel, late);
        this.handler.post(() -> stepStill(still));
        try {
            if (!still.done.await(STILL_TIMEOUT_S, TimeUnit.SECONDS)) {
                // Its result, when it comes, to late
                synchronized (still) {
                    still.abandoned = true;
                    if (still.result == null) {
                        return null;
                    }
                }
            }
        } catch (InterruptedException e) {
            synchronized (still) {
                still.abandoned = true;
            }
            Thread.currentThread().interrupt();
            return null;
        }
        Bitmap both = still.result;
        if (both == null) {
            return null;
        }
        Bitmap sky = Bitmap.createBitmap(both, 0, 0, width, height);
        Bitmap seeThrough = Bitmap.createBitmap(both, 0, height, width, height);
        both.recycle();
        return new Bitmap[]{sky, seeThrough};
    }

    // --- Renderer thread ---

    private void setUp() {
        try {
            this.display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (!EGL14.eglInitialize(this.display, version, 0, version, 1)) {
                throw new IllegalStateException("eglInitialize");
            }
            int[] attributes = {
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(this.display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
                throw new IllegalStateException("eglChooseConfig");
            }
            this.config = configs[0];
            this.context = EGL14.eglCreateContext(this.display, this.config, EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            if (this.context == EGL14.EGL_NO_CONTEXT) {
                throw new IllegalStateException("eglCreateContext");
            }
            this.idleSurface = EGL14.eglCreatePbufferSurface(this.display, this.config,
                    new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            makeCurrent(this.idleSurface);
            // sky_clouds.frag twice: its clouds pass, and its compose pass
            String sky = read(R.raw.sky_clouds);
            this.cloudsProgram = program(sky.replaceFirst("#version 300 es\n", "#version 300 es\n#define CLOUDS_PASS\n"));
            this.composeProgram = program(sky);
            this.overlayProgram = program(read(R.raw.sky_overlay));
            this.noiseTexture = loadNoise();
            // The clouds' base shape, 128^3 in 16 x 8 slices, and their detail, 32^3 in 8 x 4
            this.shapeTexture = loadVolume(R.raw.sky_cloud_shape, 128, 16);
            this.detailTexture = loadVolume(R.raw.sky_cloud_detail, 32, 8);
            this.moonTexture = loadMoon();
            int[] arrays = new int[1];
            GLES30.glGenVertexArrays(1, arrays, 0);
            this.vertexArray = arrays[0];
            // The average in half floats where they can be drawn into: bytes lose the slow changes of a long average
            String extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
            boolean halfFloat = extensions != null && (extensions.contains("GL_EXT_color_buffer_half_float")
                    || extensions.contains("GL_EXT_color_buffer_float"));
            this.historyFormat = halfFloat ? GLES30.GL_RGBA16F : GLES30.GL_RGBA8;
            this.historyType = halfFloat ? GLES30.GL_HALF_FLOAT : GLES30.GL_UNSIGNED_BYTE;
            this.mapped = halfFloat;
            if (SkyTimings.SHOWN && BuildConfig.DEBUG) {
                this.timings = new SkyTimings(extensions);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Setting up OpenGL", e);
            this.failed = true;
            everFailed = true;
        }
    }

    private void makeCurrent(EGLSurface surface) {
        if (!EGL14.eglMakeCurrent(this.display, surface, surface, this.context)) {
            throw new IllegalStateException("eglMakeCurrent " + EGL14.eglGetError());
        }
    }

    private String read(int id) {
        try (InputStream in = this.resources.openRawResource(id); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException("Reading a shader", e);
        }
    }

    private int program(String fragmentSource) {
        int vertex = shader(GLES30.GL_VERTEX_SHADER, VERTEX);
        int fragment = shader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertex);
        GLES30.glAttachShader(program, fragment);
        GLES30.glLinkProgram(program);
        int[] status = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            throw new IllegalStateException("Linking: " + GLES30.glGetProgramInfoLog(program));
        }
        GLES30.glDeleteShader(vertex);
        GLES30.glDeleteShader(fragment);
        return program;
    }

    private static int shader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            throw new IllegalStateException("Compiling: " + GLES30.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    // A size^3 grey volume from its slices laid out across an atlas in res/raw, as tools/sky_noise_atlas.py makes it,
    // repeating every way
    private int loadVolume(int id, int size, int across) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap atlas;
        try (InputStream in = this.resources.openRawResource(id)) {
            atlas = BitmapFactory.decodeStream(in, null, options);
        } catch (IOException e) {
            throw new IllegalStateException("Reading the clouds' noise", e);
        }
        if (atlas == null) {
            throw new IllegalStateException("Decoding the clouds' noise");
        }
        int[] row = new int[size];
        ByteBuffer volume = ByteBuffer.allocateDirect(size * size * size).order(ByteOrder.nativeOrder());
        for (int z = 0; z < size; z++) {
            int left = (z % across) * size;
            int top = (z / across) * size;
            for (int y = 0; y < size; y++) {
                atlas.getPixels(row, 0, size, left, top + y, size, 1);
                for (int x = 0; x < size; x++) {
                    volume.put((byte) (row[x] >> 16));
                }
            }
        }
        atlas.recycle();
        volume.position(0);
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textures[0]);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8, size, size, size, 0, GLES30.GL_RED,
                GLES30.GL_UNSIGNED_BYTE, volume);
        // With its mip levels: read far off at the level whose texels a pixel covers, as Horizon reads its noise
        // (tex3Dlod), or the texture's repeats alias into streaks running to the horizon
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_3D);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_REPEAT);
        return textures[0];
    }

    @RequiresApi(33)
    private void setUpTarget(Target target) {
        if (this.failed || target.released) {
            // Given back before it was made: nothing to make
            return;
        }
        try {
            // The view's two, the one waiting, the one being taken out, and one for the drawing to go on into
            target.reader = ImageReader.newInstance(target.width, target.height * 2, PixelFormat.RGBA_8888, 5,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            target.reader.setOnImageAvailableListener(reader -> deliver(target), this.framesHandler);
            target.surface = EGL14.eglCreateWindowSurface(this.display, this.config, target.reader.getSurface(),
                    new int[]{EGL14.EGL_NONE}, 0);
            makeCurrent(target.surface);
            EGL14.eglSwapInterval(this.display, 0);
            makeHistory(target.width + 2 * MARGIN, target.height + 2 * MARGIN, target.textures, target.framebuffers);
            makeTargets(quarter(target.width + 2 * MARGIN), quarter(target.height + 2 * MARGIN), target.marched,
                    target.marchedBuffer);
        } catch (RuntimeException e) {
            Log.e(TAG, "Setting up a sky's frames", e);
            target.released = true;
        }
    }

    // A quarter of a size, rounded up: one texel a 4x4 block
    private static int quarter(int size) {
        return (size + 3) / 4;
    }

    private void makeHistory(int width, int height, int[] textures, int[] framebuffers) {
        makeTargets(width, height, textures, framebuffers);
    }

    // As many pairs of textures as framebuffers, each framebuffer drawing its pair at once
    private void makeTargets(int width, int height, int[] textures, int[] framebuffers) {
        GLES30.glGenTextures(textures.length, textures, 0);
        GLES30.glGenFramebuffers(framebuffers.length, framebuffers, 0);
        for (int i = 0; i < textures.length; i++) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[i]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, this.historyFormat, width, height, 0, GLES30.GL_RGBA,
                    this.historyType, null);
            // Linear, for the clouds carried over from where the wind had them, between pixels
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        }
        int[] attachments = {GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_COLOR_ATTACHMENT1};
        for (int i = 0; i < framebuffers.length; i++) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffers[i]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D,
                    textures[2 * i], 0);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT1, GLES30.GL_TEXTURE_2D,
                    textures[2 * i + 1], 0);
            GLES30.glDrawBuffers(2, attachments, 0);
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    @RequiresApi(33)
    private void drawTarget(Target target, Params params) {
        if (this.failed || target.released || target.surface == null) {
            return;
        }
        try {
            makeCurrent(target.surface);
            startTiming(SkyTimings.MAP);
            drawMap(target.map, params.scene);
            stopTiming();
            boolean fresh = params.fresh || target.sinceFresh < 0;
            if (fresh) {
                target.sinceFresh = 0;
                target.lastDrift = params.drift;
                target.turned = 0;
                target.carried = 0;
            }
            boolean flashOnly = params.flashOnly && !fresh && !params.still;
            float shift = flashOnly ? 0 : params.drift - target.lastDrift;
            if (!flashOnly) {
                target.lastDrift = params.drift;
            }
            if (flashOnly) {
                // A flash's frame: the clouds as they are, lit again; the march goes on at its own pace
            } else if (params.still) {
                // Standing still, every pixel each frame, averaged over the frames; all at once when afresh, having no
                // next frames to smooth it in
                int passes = fresh ? FRESH_PASSES : 1;
                startTiming(SkyTimings.MARCH);
                for (int i = 0; i < passes; i++) {
                    float weight = Math.max(1f / (target.sinceFresh + 1), STILL_MIN_WEIGHT);
                    drawClouds(params, target, -1, weight, i == 0 ? shift : 0, params.steps);
                    target.sinceFresh++;
                }
                stopTiming();
            } else if (fresh) {
                // A picture of the clouds for now: one full march a 4x4 block, at its middle, spread over it, as
                // cheap as a turn's frame; the turns then march each pixel. Every pixel at once held up the screen's
                // drawing as a sky came in, and with fewer steps their noise showed
                drawFirst(params, target);
                target.sinceFresh++;
            } else if (params.carry && target.sinceFresh > TURNS * SETTLE_TURNS && target.carried < MAX_CARRIED) {
                // Partly shown and settled: moved on by the wind as it is, its marches waiting a frame
                carryClouds(params, target, shift);
                target.carried++;
            } else {
                // One pixel in sixteen, the rest carried over: each pixel's first full march replaces the quick
                // picture, its next ones averaged in
                int after = target.sinceFresh - 1;
                int cycle = after / TURNS;
                float weight = cycle == 0 ? 1f : Math.max(1f / (cycle + 1), MIN_WEIGHT);
                drawClouds(params, target, after % TURNS, weight, shift, params.steps);
                target.sinceFresh++;
                if (++target.turned >= TURNS) {
                    target.turned = 0;
                    target.carried = 0;
                }
            }
            // Smoothed over neighbours while its pixels have few marches, sharp after a few turns of all of them
            float settled = params.still ? 1f
                    : Math.min(Math.max(target.sinceFresh - 1, 0) / (float) (TURNS * SETTLE_TURNS), 1f);
            if (!params.scene.hasLightning()) {
                // No storm: no bolt's picture kept
                releaseBolt(target);
            }
            startTiming(SkyTimings.COMPOSE);
            int bolt = drawBolt(target, params);
            compose(params, target.width, target.height, MARGIN, target.textures, target.current, settled, 0, bolt);
            stopTiming();
            EGLExt.eglPresentationTimeANDROID(this.display, target.surface,
                    (params.weather << FRAME_TAG) | (++target.presented & ((1L << FRAME_TAG) - 1)));
            EGL14.eglSwapBuffers(this.display, target.surface);
            if (this.timings != null) {
                this.timings.frameDrawn();
            }
        } catch (RuntimeException e) {
            stopTiming();
            Log.e(TAG, "Drawing a sky's frame", e);
        }
    }

    // A fresh start's first picture of the target's clouds: a march at each block's middle, spread over the blocks
    private void drawFirst(Params params, Target target) {
        int march = target.frame;
        target.frame++;
        startTiming(SkyTimings.MARCH);
        drawClouds(params, target.width, target.height, MARGIN, target.textures, target.framebuffers, target.current,
                1, BLOCK_MIDDLE, 1, 0, params.steps, march, target.marched, target.marchedBuffer[0], target.map);
        stopTiming();
        startTiming(SkyTimings.MERGE);
        drawClouds(params, target.width, target.height, MARGIN, target.textures, target.framebuffers, target.current,
                3, BLOCK_MIDDLE, 1, 0, params.steps, march, target.marched, 0, target.map);
        stopTiming();
        target.current = 1 - target.current;
    }

    // A frame of the target's clouds: every pixel marched for a turn of -1, otherwise the turn's pixel of each block
    private void drawClouds(Params params, Target target, int turn, float weight, float shift, float steps) {
        // The steps' offsets move on with each of a pixel's marches: every frame when it marches every pixel, every
        // sixteen frames when it marches one in sixteen
        int march = turn < 0 ? target.frame : target.frame / TURNS;
        target.frame++;
        if (turn < 0) {
            drawClouds(params, target.width, target.height, MARGIN, target.textures, target.framebuffers,
                    target.current, 0, 0, weight, shift, steps, march, null, 0, target.map);
        } else {
            // The turn's pixels marched at a quarter of the size, then put in their places, the rest carried over
            startTiming(SkyTimings.MARCH);
            drawClouds(params, target.width, target.height, MARGIN, target.textures, target.framebuffers,
                    target.current, 1, turn, weight, shift, steps, march, target.marched, target.marchedBuffer[0],
                    target.map);
            stopTiming();
            startTiming(SkyTimings.MERGE);
            drawClouds(params, target.width, target.height, MARGIN, target.textures, target.framebuffers,
                    target.current, 2, turn, weight, shift, steps, march, target.marched, 0, target.map);
            stopTiming();
        }
        target.current = 1 - target.current;
    }

    // A frame of the target's clouds all carried over from the last, moved on by the wind's shift, none marched
    private void carryClouds(Params params, Target target, float shift) {
        startTiming(SkyTimings.MERGE);
        drawClouds(params, target.width, target.height, MARGIN, target.textures, target.framebuffers,
                target.current, 2, 0, 0, shift, params.steps, target.frame / TURNS, target.marched, 0, target.map);
        stopTiming();
        target.current = 1 - target.current;
    }

    // Debug builds: a pass of a live frame timed on the GPU, until stopTiming
    private void startTiming(int pass) {
        if (this.timings != null) {
            this.timings.start(pass);
        }
    }

    private void stopTiming() {
        if (this.timings != null) {
            this.timings.stop();
        }
    }

    // The clouds pass in a mode (sky_clouds.frag's): 0 every pixel marched into the frame of them not shown
    // (1 - current), averaged with the one shown; 1 the turn's pixel of each 4x4 block marched into the marched
    // textures by their framebuffer, or its middle for BLOCK_MIDDLE; 2 those put in their places in the frame not
    // shown and the rest carried over from the one shown, moved on by the wind's shift since; 3 every pixel filled
    // from those marches
    private void drawClouds(Params params, int width, int height, int margin, int[] textures, int[] framebuffers,
            int current, int mode, int turn, float weight, float shift, float steps, int frame, int[] marched,
            int marchedBuffer, WeatherMap map) {
        int next = 1 - current;
        int p = this.cloudsProgram;
        int cloudsWidth = width + 2 * margin;
        int cloudsHeight = height + 2 * margin;
        if (mode == 1) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, marchedBuffer);
            GLES30.glViewport(0, 0, quarter(cloudsWidth), quarter(cloudsHeight));
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffers[next]);
            GLES30.glViewport(0, 0, cloudsWidth, cloudsHeight);
        }
        GLES30.glUseProgram(p);
        GLES30.glUniform1f(location(p, "margin"), margin);
        bindClouds(p, textures, current);
        if (marched != null) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE5);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, marched[0]);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE6);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, marched[1]);
        }
        // Their own units even when unused: left on unit 0 they would share it with the 3D shape, which GL refuses
        GLES30.glUniform1i(location(p, "marchedDirect"), 5);
        GLES30.glUniform1i(location(p, "marchedAmbient"), 6);
        bindMap(p, map.texture[0]);
        GLES30.glUniform1f(location(p, "mode"), mode);
        if (turn == BLOCK_MIDDLE) {
            // Between its four middle pixels, the march at 1.5 + 0.5 in
            GLES30.glUniform2f(location(p, "turnCell"), 1.5f, 1.5f);
        } else {
            int cell = TURN_CELLS[turn];
            GLES30.glUniform2f(location(p, "turnCell"), cell % 4, cell / 4);
        }
        GLES30.glUniform1f(location(p, "weight"), weight);
        GLES30.glUniform1f(location(p, "shift"), shift);
        GLES30.glUniform1f(location(p, "frame"), frame);
        GLES30.glUniform1f(location(p, "steps"), steps);
        setScene(p, params.scene);
        setView(p, width, height, params.crop);
        GLES30.glUniform1f(location(p, "drift"), params.drift);
        GLES30.glUniform1f(location(p, "evolve"), params.evolve);
        GLES30.glUniform2f(location(p, "clear"), params.clearTop, params.clearBottom);
        GLES30.glBindVertexArray(this.vertexArray);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
    }

    // The weather map for a pass of the clouds program: its texture, or none, the slice read as it is
    private void bindMap(int p, int texture) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + MAP_UNIT);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glUniform1i(location(p, "weatherMap"), MAP_UNIT);
        GLES30.glUniform1f(location(p, "mapped"), texture != 0 ? 1 : 0);
    }

    // The scene's weather map into its owner's, unless already of its seed or the sky has no clouds to read it: the
    // slice across its period, by the clouds program's mode 4
    private void drawMap(WeatherMap map, SkyScene scene) {
        if (!this.mapped || map.seed == scene.seed || scene.coverage <= 0) {
            return;
        }
        if (map.texture[0] == 0) {
            GLES30.glGenTextures(1, map.texture, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, map.texture[0]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F, MAP_SIZE, MAP_SIZE, 0, GLES30.GL_RG,
                    GLES30.GL_HALF_FLOAT, null);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
            GLES30.glGenFramebuffers(1, map.framebuffer, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, map.framebuffer[0]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D,
                    map.texture[0], 0);
            GLES30.glDrawBuffers(1, new int[]{GLES30.GL_COLOR_ATTACHMENT0}, 0);
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                // Half floats drawn into, but not single ones: the slice read as it is from now on
                Log.w(TAG, "No weather map in half floats");
                releaseMap(map);
                this.mapped = false;
                return;
            }
        }
        int p = this.cloudsProgram;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, map.framebuffer[0]);
        GLES30.glViewport(0, 0, MAP_SIZE, MAP_SIZE);
        GLES30.glUseProgram(p);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, this.shapeTexture);
        GLES30.glUniform1i(location(p, "shapeTex"), 0);
        // The other samplers on units of their own, as GL refuses two types on one; the map itself not read while
        // drawn into
        GLES30.glUniform1i(location(p, "detailTex"), 2);
        GLES30.glUniform1i(location(p, "cloudsDirect"), 1);
        GLES30.glUniform1i(location(p, "cloudsAmbient"), 4);
        GLES30.glUniform1i(location(p, "marchedDirect"), 5);
        GLES30.glUniform1i(location(p, "marchedAmbient"), 6);
        bindMap(p, 0);
        GLES30.glUniform1f(location(p, "mode"), 4);
        GLES30.glUniform2f(location(p, "size"), MAP_SIZE, MAP_SIZE);
        GLES30.glUniform1f(location(p, "seed"), scene.seed);
        GLES30.glBindVertexArray(this.vertexArray);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        map.seed = scene.seed;
    }

    private void releaseMap(WeatherMap map) {
        if (map.texture[0] != 0) {
            GLES30.glDeleteTextures(1, map.texture, 0);
            GLES30.glDeleteFramebuffers(1, map.framebuffer, 0);
            map.texture[0] = 0;
            map.seed = Float.NaN;
        }
    }

    // The compose pass from the clouds' current frame into the framebuffer, twice as high: the sky on top, how far
    // one sees through it below; the bolt read from its picture, drawBolt's, or drawn here with bolt 0
    private void compose(Params params, int width, int height, int margin, int[] textures, int current,
            float settled, int framebuffer, int bolt) {
        int p = this.composeProgram;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer);
        GLES30.glViewport(0, 0, width, height * 2);
        GLES30.glUseProgram(p);
        GLES30.glUniform1f(location(p, "margin"), margin);
        GLES30.glUniform1f(location(p, "settled"), settled);
        bindClouds(p, textures, current);
        setScene(p, params.scene);
        setView(p, width, height, params.crop);
        GLES30.glUniform1f(location(p, "drift"), params.drift);
        GLES30.glUniform1f(location(p, "evolve"), params.evolve);
        GLES30.glUniform2f(location(p, "clear"), params.clearTop, params.clearBottom);
        GLES30.glUniform1f(location(p, "flash"), params.flash);
        GLES30.glUniform3f(location(p, "bolt"), params.boltSeed, params.boltBrightness, params.boltAcross);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + BOLT_UNIT);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bolt);
        GLES30.glUniform1f(location(p, "boltMapped"), bolt != 0 ? 1 : 0);
        GLES30.glUniform1f(location(p, "boltPass"), 0);
        GLES30.glBindVertexArray(this.vertexArray);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
    }

    // The target's bolt as a picture, drawn when a new one strikes: each of its pixels walked down the bolt's 40 steps
    // and its branches once, not on every frame of the flash. Its texture, or 0 for none, the compose pass then
    // drawing the bolt itself: none showing, or half floats not to be drawn into
    private int drawBolt(Target target, Params params) {
        if (params.boltBrightness <= 0 || !this.mapped || target.boltUnsupported) {
            return 0;
        }
        if (target.boltSeed == params.boltSeed && target.boltAcross == params.boltAcross
                && target.boltCrop == params.crop) {
            return target.bolt[0];
        }
        if (target.bolt[0] == 0) {
            GLES30.glGenTextures(1, target.bolt, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.bolt[0]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F, target.width, target.height, 0,
                    GLES30.GL_RG, GLES30.GL_HALF_FLOAT, null);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glGenFramebuffers(1, target.boltBuffer, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.boltBuffer[0]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D,
                    target.bolt[0], 0);
            GLES30.glDrawBuffers(1, new int[]{GLES30.GL_COLOR_ATTACHMENT0}, 0);
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                // Its bolt drawn in the compose pass, as before, from now on
                releaseBolt(target);
                target.boltUnsupported = true;
                return 0;
            }
        }
        int p = this.composeProgram;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.boltBuffer[0]);
        GLES30.glViewport(0, 0, target.width, target.height);
        GLES30.glUseProgram(p);
        bindClouds(p, target.textures, target.current);
        setScene(p, params.scene);
        setView(p, target.width, target.height, params.crop);
        GLES30.glUniform3f(location(p, "bolt"), params.boltSeed, params.boltBrightness, params.boltAcross);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + BOLT_UNIT);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glUniform1f(location(p, "boltMapped"), 0);
        GLES30.glUniform1f(location(p, "boltPass"), 1);
        GLES30.glBindVertexArray(this.vertexArray);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        target.boltSeed = params.boltSeed;
        target.boltAcross = params.boltAcross;
        target.boltCrop = params.crop;
        return target.bolt[0];
    }

    private void releaseBolt(Target target) {
        if (target.bolt[0] != 0) {
            GLES30.glDeleteTextures(1, target.bolt, 0);
            GLES30.glDeleteFramebuffers(1, target.boltBuffer, 0);
            target.bolt[0] = 0;
        }
        target.boltSeed = Float.NaN;
    }

    // The noises and the clouds' frame for a pass of sky_clouds.frag
    private void bindClouds(int p, int[] textures, int current) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, this.shapeTexture);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, this.detailTexture);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[2 * current]);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[2 * current + 1]);
        GLES30.glUniform1i(location(p, "shapeTex"), 0);
        GLES30.glUniform1i(location(p, "detailTex"), 2);
        GLES30.glUniform1i(location(p, "cloudsDirect"), 1);
        GLES30.glUniform1i(location(p, "cloudsAmbient"), 4);
        // Its own unit in either program, even where the map is not read: a driver may keep it, and left on unit 0 it
        // would share it with the 3D shape, which GL refuses
        GLES30.glUniform1i(location(p, "weatherMap"), MAP_UNIT);
        GLES30.glUniform1i(location(p, "boltMap"), BOLT_UNIT);
    }

    private void setScene(int p, SkyScene scene) {
        GLES30.glUniform3fv(location(p, "zenith"), 1, scene.zenith, 0);
        GLES30.glUniform3fv(location(p, "horizon"), 1, scene.horizon, 0);
        GLES30.glUniform3fv(location(p, "light"), 1, scene.light, 0);
        GLES30.glUniform3fv(location(p, "ambient"), 1, scene.ambient, 0);
        GLES30.glUniform3fv(location(p, "ambientTop"), 1, scene.ambientTop, 0);
        GLES30.glUniform1f(location(p, "coverage"), scene.coverage);
        GLES30.glUniform1f(location(p, "cloudType"), scene.cloudType);
        GLES30.glUniform1f(location(p, "precipitation"), scene.precipitation);
        GLES30.glUniform1f(location(p, "sigma"), scene.sigma);
        GLES30.glUniform3f(location(p, "body"), scene.bodyX, scene.bodyY, scene.body);
        GLES30.glUniform1f(location(p, "glow"), scene.glow);
        GLES30.glUniform1f(location(p, "stars"), scene.stars);
        GLES30.glUniform1f(location(p, "mist"), scene.mist);
        GLES30.glUniform1f(location(p, "seed"), scene.seed);
    }

    // The history out into the framebuffer bound as the output, twice as high: the sky over how far one sees through
    // The output's size and the part of the 2:3 sky it shows, as the pictures were cut: the sky covers the output,
    // centred across, down by the crop; the same for the clouds and what is drawn over them
    private void setView(int p, int width, int height, float crop) {
        float scale = Math.max(width / SkyShaders.SKY_WIDTH, height / SkyShaders.SKY_HEIGHT);
        float viewWidth = width / scale / SkyShaders.SKY_WIDTH;
        float viewHeight = height / scale / SkyShaders.SKY_HEIGHT;
        GLES30.glUniform2f(location(p, "size"), width, height);
        GLES30.glUniform4f(location(p, "view"), (1 - viewWidth) / 2, (1 - viewHeight) * crop, viewWidth, viewHeight);
    }

    // A still's stars, rain and snow over its composed picture (sky on top, see-through below), into the output, as
    // sky_overlay.frag draws them
    private void overlay(SkyScene scene, int picture, int width, int height, float crop, int framebuffer) {
        int p = this.overlayProgram;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer);
        GLES30.glViewport(0, 0, width, height * 2);
        GLES30.glUseProgram(p);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, picture);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.noiseTexture);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.moonTexture);
        GLES30.glUniform1i(location(p, "picture"), 1);
        GLES30.glUniform1i(location(p, "noise"), 3);
        GLES30.glUniform1i(location(p, "moonMap"), 4);
        GLES30.glUniform3f(location(p, "disc"), scene.moonPhase, scene.moonLimb, scene.moonFace);
        setView(p, width, height, crop);
        // The stars as at the start, the drops caught in their fall a few seconds in
        GLES30.glUniform1f(location(p, "starsTime"), 0);
        GLES30.glUniform1f(location(p, "dropsTime"), STILL_DROPS_TIME);
        GLES30.glUniform1f(location(p, "coverage"), scene.coverage);
        GLES30.glUniform3f(location(p, "body"), scene.bodyX, scene.bodyY, scene.body);
        GLES30.glUniform1f(location(p, "stars"), scene.stars);
        GLES30.glUniform2f(location(p, "clear"), SkyShaders.CLEAR_NOWHERE, SkyShaders.CLEAR_NOWHERE);
        GLES30.glUniform1f(location(p, "rain"), scene.rain);
        // A screen's height at the picture's width: a picture as wide as the screen and less high, as a card's, has
        // the screen's drops; a screen's own picture its own height
        android.util.DisplayMetrics metrics = this.resources.getDisplayMetrics();
        float screenShape = Math.max(metrics.widthPixels, metrics.heightPixels)
                / (float) Math.max(1, Math.min(metrics.widthPixels, metrics.heightPixels));
        GLES30.glUniform1f(location(p, "rainHeight"), Math.max(height, width * screenShape));
        GLES30.glUniform1f(location(p, "snow"), scene.snow);
        GLES30.glUniform1f(location(p, "brightness"), scene.dropsBrightness);
        GLES30.glBindVertexArray(this.vertexArray);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
    }

    /** The moon's nearside map, NASA's, as sky_moon.agsl and the stills' sky_overlay.frag read it */
    static Bitmap moonMap(Resources resources) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = resources.openRawResource(R.raw.sky_moon_map)) {
            Bitmap map = BitmapFactory.decodeStream(in, null, options);
            if (map == null) {
                throw new IllegalStateException("Decoding the moon's map");
            }
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("Reading the moon's map", e);
        }
    }

    // The moon's map as a texture, read linearly as SkyShaders' BitmapShader of it, with its mip levels for the cards'
    // small stills, where its disc is a few dozen pixels across and the map's texels would sparkle
    private int loadMoon() {
        Bitmap map = moonMap(this.resources);
        int[] texture = new int[1];
        GLES30.glGenTextures(1, texture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0]);
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, map, 0);
        map.recycle();
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    /** The stars' and drops' value noise, 256 x 256: red random, green the red 37 x 17 texels on, as ARGB */
    static int[] noisePixels() {
        java.util.Random random = new java.util.Random(1);
        int[] red = new int[NOISE_SIZE * NOISE_SIZE];
        for (int i = 0; i < red.length; i++) {
            red[i] = random.nextInt(256);
        }
        int[] pixels = new int[red.length];
        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                int green = red[((y + 17) % NOISE_SIZE) * NOISE_SIZE + (x + 37) % NOISE_SIZE];
                pixels[y * NOISE_SIZE + x] = 0xFF000000 | (red[y * NOISE_SIZE + x] << 16) | (green << 8);
            }
        }
        return pixels;
    }

    // The noise as a texture, read as the shaders' BitmapShader reads it: linearly, repeating
    private int loadNoise() {
        int[] argb = noisePixels();
        ByteBuffer bytes = ByteBuffer.allocateDirect(argb.length * 4).order(ByteOrder.nativeOrder());
        for (int pixel : argb) {
            bytes.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) 0xFF);
        }
        bytes.position(0);
        int[] texture = new int[1];
        GLES30.glGenTextures(1, texture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0]);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, NOISE_SIZE, NOISE_SIZE, 0, GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE, bytes);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
        return texture[0];
    }

    private int location(int program, String name) {
        String key = program + ":" + name;
        Integer location = this.locations.get(key);
        if (location == null) {
            location = GLES30.glGetUniformLocation(program, name);
            this.locations.put(key, location);
        }
        return location;
    }

    // A frame came out, on the frames' thread: kept as the one waiting for the view, the one waiting before given back
    @RequiresApi(33)
    private void deliver(Target target) {
        if (target.released || target.reader == null) {
            return;
        }
        Image image;
        try {
            image = target.reader.acquireLatestImage();
        } catch (IllegalStateException e) {
            Log.w(TAG, "No image free for a frame", e);
            return;
        }
        if (image == null) {
            return;
        }
        boolean post;
        synchronized (target) {
            if (target.waiting != null) {
                target.waiting.close();
            }
            post = target.waiting == null;
            target.waiting = image;
        }
        if (post) {
            this.mainHandler.post(() -> takeFrame(target));
        }
    }

    // Main thread: the frame waiting, to the view as a hardware bitmap
    @RequiresApi(33)
    private void takeFrame(Target target) {
        Image image;
        synchronized (target) {
            image = target.waiting;
            target.waiting = null;
        }
        if (image == null) {
            return;
        }
        if (target.released) {
            image.close();
            return;
        }
        HardwareBuffer buffer = image.getHardwareBuffer();
        Bitmap bitmap = buffer == null ? null : Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
        if (buffer != null) {
            buffer.close();
        }
        if (bitmap == null) {
            image.close();
            return;
        }
        target.listener.onFrame(target, bitmap, image);
    }

    @RequiresApi(33)
    private void releaseTarget(Target target) {
        if (this.failed) {
            return;
        }
        try {
            makeCurrent(this.idleSurface);
            if (target.textures[0] != 0) {
                GLES30.glDeleteTextures(4, target.textures, 0);
                GLES30.glDeleteFramebuffers(2, target.framebuffers, 0);
                GLES30.glDeleteTextures(2, target.marched, 0);
                GLES30.glDeleteFramebuffers(1, target.marchedBuffer, 0);
            }
            releaseMap(target.map);
            releaseBolt(target);
            if (target.surface != null) {
                EGL14.eglDestroySurface(this.display, target.surface);
                target.surface = null;
            }
            synchronized (target) {
                if (target.waiting != null) {
                    target.waiting.close();
                    target.waiting = null;
                }
            }
            if (target.reader != null) {
                target.reader.close();
                target.reader = null;
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Releasing a sky's frames", e);
        }
    }

    /** A still being drawn on the renderer's thread, piece by piece */
    private static final class Still {
        final SkyScene scene;
        final int width;
        final int height;
        final float crop;
        final Params params = new Params();
        // Its clouds at the picture's size
        final int cloudsWidth;
        final int cloudsHeight;
        final int[] textures = new int[4];
        final int[] framebuffers = new int[2];
        // The picture twice as high, and, with stars or drops over it, the sky composed before them
        final int[] output = new int[2];
        final int[] outputBuffer = new int[2];
        final WeatherMap map = new WeatherMap();
        boolean started;
        int pass;
        int row;
        int current;
        // Under the still's lock: its picture, once read back, and whether its caller gave up waiting for it
        Bitmap result;
        boolean abandoned;
        // Renderer thread: whether its late sky was handed over
        boolean lateHanded;
        final AtomicBoolean cancel;
        final Consumer<Bitmap> late;
        final CountDownLatch done = new CountDownLatch(1);

        Still(SkyScene scene, int width, int height, float crop, float drift, float evolve, AtomicBoolean cancel,
                Consumer<Bitmap> late) {
            this.scene = scene;
            this.cancel = cancel;
            this.late = late;
            this.width = width;
            this.height = height;
            this.crop = crop;
            this.cloudsWidth = width;
            this.cloudsHeight = height;
            Params params = this.params;
            params.scene = scene;
            params.crop = crop;
            params.drift = drift;
            params.evolve = evolve;
            params.clearTop = SkyShaders.CLEAR_NOWHERE;
            params.clearBottom = SkyShaders.CLEAR_NOWHERE;
            params.steps = STILL_STEPS;
            if (scene.hasLightning()) {
                // A storm's still with its bolt, as the pictures had one, the clouds a little lit by it
                params.flash = STILL_FLASH;
                params.boltSeed = STILL_BOLT_SEED;
                params.boltBrightness = 1;
                params.boltAcross = STILL_BOLT_ACROSS;
            }
        }
    }

    // One piece of a still: a band of rows of one of its passes of the clouds, waited for on the GPU, then the next
    // piece posted after whatever came meanwhile; once all its passes are done, the picture put together and read back
    private void stepStill(Still still) {
        boolean finished = true;
        try {
            if (this.failed || (still.cancel != null && still.cancel.get())) {
                return;
            }
            makeCurrent(this.idleSurface);
            // Errors before this piece, as the live frames drawn between its pieces raise, are not its own
            drainErrors();
            if (!still.started) {
                still.started = true;
                makeHistory(still.cloudsWidth, still.cloudsHeight, still.textures, still.framebuffers);
                makeOutput(still);
                drawMap(still.map, still.scene);
            }
            if (still.pass < STILL_PASSES) {
                int rows = Math.max(1, STILL_RAYS_PER_PIECE / still.cloudsWidth);
                GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
                GLES30.glScissor(0, still.row, still.cloudsWidth, rows);
                if (this.timings != null) {
                    this.timings.startStill();
                }
                drawClouds(still.params, still.cloudsWidth, still.cloudsHeight, 0, still.textures, still.framebuffers,
                        still.current, 0, 0, 1f / (still.pass + 1), 0, STILL_STEPS, still.pass, null, 0, still.map);
                stopTiming();
                GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
                GLES30.glFinish();
                if (this.timings != null) {
                    this.timings.stillFinished();
                }
                checkStill();
                still.row += rows;
                if (still.row >= still.cloudsHeight) {
                    still.row = 0;
                    still.pass++;
                    still.current = 1 - still.current;
                }
                finished = false;
                this.handler.post(() -> stepStill(still));
                return;
            }
            drainErrors();
            if (this.timings != null) {
                this.timings.startStill();
            }
            Bitmap picture = readStill(still);
            stopTiming();
            if (this.timings != null) {
                this.timings.stillFinished();
            }
            checkStill();
            Bitmap late = null;
            synchronized (still) {
                if (still.abandoned) {
                    late = picture;
                } else {
                    still.result = picture;
                }
            }
            if (late != null) {
                if (still.late != null) {
                    // Its sky only, for its caller's next time
                    Bitmap sky = Bitmap.createBitmap(late, 0, 0, still.width, still.height);
                    late.recycle();
                    still.lateHanded = true;
                    still.late.accept(sky);
                } else {
                    late.recycle();
                }
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            stopTiming();
            Log.e(TAG, "Drawing a still sky", e);
        } finally {
            if (finished) {
                releaseStill(still);
                still.done.countDown();
                // Given up waiting for and not coming after all, as it failed or was cancelled: told, so that whoever
                // waited for it lets go
                boolean told;
                synchronized (still) {
                    told = still.abandoned && !still.lateHanded && still.late != null;
                }
                if (told) {
                    still.late.accept(null);
                }
            }
        }
    }

    // GL's errors so far, forgotten: a few at most, as GL keeps one a kind, not to spin on a context that keeps
    // reporting its loss
    static void drainErrors() {
        for (int i = 0; i < 16 && GLES30.glGetError() != GLES30.GL_NO_ERROR; i++) {
            // drained
        }
    }

    // Any error of GL while drawing a still: its picture may be black or garbage, read back from a GPU that failed,
    // which GL does not throw for. Not to be shown, nor saved for next time: the renderer is taken as failed, the skies
    // showing their colours from then on
    private void checkStill() {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            this.failed = true;
            everFailed = true;
            throw new IllegalStateException("GL error " + error + " drawing a still");
        }
    }

    private void makeOutput(Still still) {
        GLES30.glGenTextures(2, still.output, 0);
        GLES30.glGenFramebuffers(2, still.outputBuffer, 0);
        for (int i = 0; i < 2; i++) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, still.output[i]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, still.width, still.height * 2, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, still.outputBuffer[i]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D,
                    still.output[i], 0);
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    // The still's clouds put together with the sky at full size, its stars and drops over them, read back, twice as
    // high
    private Bitmap readStill(Still still) {
        int width = still.width;
        int height = still.height;
        SkyScene scene = still.scene;
        if (scene.hasStars() || scene.hasDrops() || scene.hasMoon()) {
            compose(still.params, width, height, 0, still.textures, still.current, 1, still.outputBuffer[1], 0);
            overlay(scene, still.output[1], width, height, still.crop, still.outputBuffer[0]);
        } else {
            compose(still.params, width, height, 0, still.textures, still.current, 1, still.outputBuffer[0], 0);
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, still.outputBuffer[0]);
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 2 * 4).order(ByteOrder.nativeOrder());
        GLES30.glReadPixels(0, 0, width, height * 2, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels);
        pixels.position(0);
        Bitmap upsideDown = Bitmap.createBitmap(width, height * 2, Bitmap.Config.ARGB_8888);
        upsideDown.copyPixelsFromBuffer(pixels);
        // GL reads rows from the bottom
        Matrix flip = new Matrix();
        flip.setScale(1, -1);
        Bitmap bitmap = Bitmap.createBitmap(upsideDown, 0, 0, width, height * 2, flip, false);
        upsideDown.recycle();
        return bitmap;
    }

    private void releaseStill(Still still) {
        if (!still.started || this.failed) {
            return;
        }
        try {
            makeCurrent(this.idleSurface);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            GLES30.glDeleteTextures(4, still.textures, 0);
            GLES30.glDeleteFramebuffers(2, still.framebuffers, 0);
            GLES30.glDeleteTextures(2, still.output, 0);
            GLES30.glDeleteFramebuffers(2, still.outputBuffer, 0);
            releaseMap(still.map);
        } catch (RuntimeException e) {
            Log.e(TAG, "Releasing a still sky", e);
        }
    }
}
