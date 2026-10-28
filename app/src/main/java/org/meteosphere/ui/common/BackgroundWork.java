package org.meteosphere.ui.common;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Work off the main thread whose results a screen takes on the main thread. What was asked before drop() gives
 * nothing, as a new request or a view gone makes it stale, and dropped work not started yet isn't done at all.
 * Asked for and dropped on the main thread.
 */
public final class BackgroundWork {

    private final Executor executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // One more with each drop; read by the work's thread
    private volatile int generation;

    public BackgroundWork(Executor executor) {
        this.executor = executor;
    }

    /** Runs the work; its result comes to the main thread unless dropped meanwhile */
    public <T> void run(Supplier<T> work, Consumer<T> onResult) {
        int asked = this.generation;
        this.executor.execute(() -> {
            if (asked != this.generation) {
                return;
            }
            T result = work.get();
            this.mainHandler.post(() -> {
                if (asked == this.generation) {
                    onResult.accept(result);
                }
            });
        });
    }

    /** Only the latest asked for comes: what was asked before is dropped */
    public <T> void runLatest(Supplier<T> work, Consumer<T> onResult) {
        drop();
        run(work, onResult);
    }

    /** The results on their way give nothing, and the work not started yet isn't done */
    public void drop() {
        this.generation++;
    }
}
