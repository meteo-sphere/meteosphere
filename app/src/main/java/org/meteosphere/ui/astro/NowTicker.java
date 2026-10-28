package org.meteosphere.ui.astro;

import android.os.Handler;

/**
 * Keeps a Moon or Sun tab at now while it is on screen: shows it again at each new minute, and at once when it comes
 * back with a minute passed. A moment picked by hand stays as picked.
 */
final class NowTicker {

    private static final long MINUTE_MS = 60 * 1000L;

    private final Handler handler;
    private final AstroTime time;
    private final Runnable refresh;
    private final Runnable tick;
    // The minute the tab shows, as a count of minutes since 1970
    private long shownMinute = Long.MIN_VALUE;

    /** The tab's moment; refresh computes and shows the tab again, and calls shown() when it starts */
    NowTicker(Handler handler, AstroTime time, Runnable refresh) {
        this.handler = handler;
        this.time = time;
        this.refresh = refresh;
        this.tick = refresh;
    }

    /** The tab is being computed for now */
    void shown() {
        this.shownMinute = this.time.moment() / MINUTE_MS;
    }

    /** Whether the tab is on screen: plans the next minute, shows a minute passed meanwhile, or stops */
    void update(boolean isOnScreen) {
        this.handler.removeCallbacks(this.tick);
        if (!isOnScreen || !this.time.isNow()) {
            return;
        }
        long now = this.time.moment();
        if (now / MINUTE_MS != this.shownMinute) {
            this.refresh.run();
            return;
        }
        // A little past the turn of the minute, so the clock shows the new one
        this.handler.postDelayed(this.tick, MINUTE_MS - Math.floorMod(now, MINUTE_MS) + 100);
    }
}
