package org.meteosphere.ui.sky;

import android.opengl.GLES30;
import java.util.Arrays;
import java.util.Locale;

/**
 * Debug builds: how long the sky's passes take on the GPU, timed by GL_EXT_disjoint_timer_query, for SkyView to show
 * over the sky. Each second: the live frames' passes, each averaged over the frames, how many frames there were, and
 * how much of the second the sky's drawing and the stills' kept the GPU busy. Renderer thread, its GL context current;
 * text() any thread.
 */
final class SkyTimings {

    // Timed and shown only while the sky's speed is worked on: otherwise a debug build's screens look as the users' do
    static final boolean SHOWN = false;

    // The live frames' passes: the clouds marched, the marches put in their places with the rest carried over, the
    // picture composed, and the weather map, drawn as a scene comes
    static final int MARCH = 0;
    static final int MERGE = 1;
    static final int COMPOSE = 2;
    static final int MAP = 3;
    private static final int PASSES = 4;
    // The frames whose queries are in flight: each read as its slot comes round again, the GPU long done with it, never
    // waited for
    private static final int FRAMES = 4;
    // GL_EXT_disjoint_timer_query's
    private static final int TIME_ELAPSED = 0x88BF;
    private static final int GPU_DISJOINT = 0x8FBB;
    private static final long PERIOD_NS = 1_000_000_000L;

    private static volatile String text;

    private final boolean supported;
    private final int[] queries = new int[FRAMES * PASSES];
    private final boolean[] used = new boolean[FRAMES * PASSES];
    private final boolean[] inFlight = new boolean[FRAMES];
    private final int[] stillQuery = new int[1];
    private final int[] value = new int[1];
    private int slot;
    // A query begun and not ended
    private boolean timing;
    // Over the period so far: each pass's time over the frames read, how many were read, how many drawn, the stills'
    private final long[] passNs = new long[PASSES];
    private int read;
    private int drawn;
    private long stillsNs;
    private long startNs = System.nanoTime();
    // The last frame or still piece: after a pause the period starts again from the next, not over the pause
    private long lastNs = this.startNs;

    SkyTimings(String extensions) {
        boolean supported = extensions != null && extensions.contains("GL_EXT_disjoint_timer_query");
        if (supported) {
            GLES30.glGenQueries(queries.length, queries, 0);
            GLES30.glGenQueries(1, stillQuery, 0);
            // Tried once: an error here, not in the sky's passes, where any marks the renderer failed; errors before it
            // are not its own
            SkyRenderer.drainErrors();
            GLES30.glBeginQuery(TIME_ELAPSED, stillQuery[0]);
            GLES30.glEndQuery(TIME_ELAPSED);
            if (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
                supported = false;
                SkyRenderer.drainErrors();
            }
        }
        this.supported = supported;
        text = supported ? null : "Таймеров видеочипа нет";
    }

    /** The last second's timings, lines apart; null before the first */
    static String text() {
        return text;
    }

    /** Times a pass of the live frame being drawn, until stop */
    void start(int pass) {
        if (this.supported) {
            GLES30.glBeginQuery(TIME_ELAPSED, this.queries[this.slot * PASSES + pass]);
            this.used[this.slot * PASSES + pass] = true;
            this.timing = true;
        }
    }

    /** Times a piece of a still, until stop; its time read by stillFinished, once the GPU is done with it */
    void startStill() {
        if (this.supported) {
            GLES30.glBeginQuery(TIME_ELAPSED, this.stillQuery[0]);
            this.timing = true;
        }
    }

    /** Ends the query begun, if any: also after a pass failed midway, so that the next can begin */
    void stop() {
        if (this.timing) {
            GLES30.glEndQuery(TIME_ELAPSED);
            this.timing = false;
        }
    }

    /** After glFinish: the still's piece's time */
    void stillFinished() {
        if (this.supported) {
            GLES30.glGetQueryObjectuiv(this.stillQuery[0], GLES30.GL_QUERY_RESULT, this.value, 0);
            this.stillsNs += this.value[0] & 0xFFFFFFFFL;
        }
        publish();
    }

    /** A live frame drawn: its queries left in flight, the oldest frame's read if they are in */
    void frameDrawn() {
        this.drawn++;
        if (this.supported) {
            this.inFlight[this.slot] = true;
            this.slot = (this.slot + 1) % FRAMES;
            if (this.inFlight[this.slot]) {
                readFrame(this.slot);
            }
        }
        publish();
    }

    // A frame's queries into the sums, if all are in and the GPU's clock kept on meanwhile; then free for the next
    private void readFrame(int frame) {
        GLES30.glGetIntegerv(GPU_DISJOINT, this.value, 0);
        boolean good = this.value[0] == 0;
        for (int pass = 0; pass < PASSES && good; pass++) {
            int i = frame * PASSES + pass;
            if (this.used[i]) {
                GLES30.glGetQueryObjectuiv(this.queries[i], GLES30.GL_QUERY_RESULT_AVAILABLE, this.value, 0);
                good = this.value[0] != 0;
            }
        }
        for (int pass = 0; pass < PASSES; pass++) {
            int i = frame * PASSES + pass;
            if (good && this.used[i]) {
                GLES30.glGetQueryObjectuiv(this.queries[i], GLES30.GL_QUERY_RESULT, this.value, 0);
                this.passNs[pass] += this.value[0] & 0xFFFFFFFFL;
            }
            this.used[i] = false;
        }
        if (good) {
            this.read++;
        }
        this.inFlight[frame] = false;
    }

    // Once a second: the period's averages as text, the sums started again
    private void publish() {
        long now = System.nanoTime();
        boolean paused = now - this.lastNs > PERIOD_NS;
        this.lastNs = now;
        long period = now - this.startNs;
        if (paused) {
            // The sky stood still meanwhile: the last second's text kept, this frame the period's first
            restart(now);
            return;
        }
        if (period < PERIOD_NS) {
            return;
        }
        if (this.supported) {
            double[] ms = new double[PASSES];
            double frameMs = 0;
            for (int pass = 0; pass < PASSES; pass++) {
                ms[pass] = this.read == 0 ? 0 : this.passNs[pass] / 1e6 / this.read;
                frameMs += ms[pass];
            }
            double perSecond = this.drawn * 1e9 / period;
            // The GPU's share of the second: the frames drawn, each as long as the average read, and the stills
            double framesShare = frameMs * 1e6 * this.drawn / period;
            double stillsShare = (double) this.stillsNs / period;
            text = String.format(Locale.ROOT,
                    "облака %.2f мс\nсведение %.2f мс\nсборка %.2f мс\nкарта %.2f мс\nкадр %.2f мс × %.0f/с = %.0f%%"
                            + "\nснимки %.0f%%",
                    ms[MARCH], ms[MERGE], ms[COMPOSE], ms[MAP], frameMs, perSecond,
                    100 * framesShare, 100 * stillsShare);
        }
        restart(now);
    }

    private void restart(long now) {
        this.startNs = now;
        this.read = 0;
        this.drawn = 0;
        this.stillsNs = 0;
        Arrays.fill(this.passNs, 0);
    }
}
