package org.meteosphere.ui.astro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.data.sun.SunChannel;
import org.meteosphere.data.sun.SunImages;
import org.meteosphere.R;

/**
 * The card of the Sun now, on the sun tab and the city details: SDO's latest corona cut from its black sky, and when
 * it was taken by the phone's clock; it opens the pictures of the Sun. The kept picture shows at once, a newer one is
 * asked for only while the card is on screen, at most every SunImages.FRESH_MS.
 */
public final class SunNowCard {

    private static final String TAG = "SunNowCard";

    private static final SunChannel CHANNEL = SunChannel.CORONA;
    // The picture asked for, a little finer than the card's disc on the densest screens
    private static final int SIZE = 512;
    // After a request to Helioviewer failed, the cards show what is kept for this long before asking again: without a
    // network every details page's card would ask every minute
    private static final long RETRY_MS = 5 * 60 * 1000L;

    private final ImageView image;
    private final TextView time;
    private final LoadingRing progress;
    private final AstroFormatter formatter;
    private final SunImages images;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // The frame shown, or none
    private long shownTime = Long.MIN_VALUE;
    private boolean isLoading;
    // Asked online while loading from the disk: asked again after it
    private boolean isOnlineWaiting;
    private boolean isDestroyed;
    // One thread for all the cards, the details' pages being made and reused often; it doesn't keep the app alive
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SunNowCard");
        thread.setDaemon(true);
        return thread;
    });
    // The last disc cut, shared by the cards of the tab and the details' pages
    private static long cutTime = Long.MIN_VALUE;
    private static Bitmap cutDisc;

    /** The card of the sun tab, item_sun_now */
    SunNowCard(View card, AstroFormatter formatter) {
        this(card, card.findViewById(R.id.ivSunNow), card.findViewById(R.id.tvSunNowChannel), card.findViewById(R.id.tvSunNowTime),
                card.findViewById(R.id.piSunNowCard), formatter);
    }

    /**
     * Pressing clickable opens the pictures; channel, if any, is told the channel's name, time when it was taken,
     * progress turns while a new one is asked for
     */
    public SunNowCard(View clickable, ImageView image, TextView channel, TextView time, LoadingRing progress,
            AstroFormatter formatter) {
        Context context = clickable.getContext();
        this.image = image;
        this.time = time;
        this.progress = progress;
        this.formatter = formatter;
        this.images = new SunImages(context.getCacheDir());
        if (channel != null) {
            channel.setText(SunNowActivity.title(context, CHANNEL));
        }
        clickable.setOnClickListener(view -> view.getContext().startActivity(new Intent(view.getContext(), SunNowActivity.class)));
    }

    /** Shows the kept picture, or a newer one when online: the card is on screen */
    public void refresh(boolean online) {
        if (this.isDestroyed) {
            return;
        }
        if (this.isLoading) {
            this.isOnlineWaiting |= online;
            return;
        }
        this.isLoading = true;
        // Shown only when it takes a while, as in the pictures: the kept frame comes at once
        if (online) {
            this.progress.show();
        }
        long shown = this.shownTime;
        EXECUTOR.execute(() -> {
            SunImages.Frame frame = null;
            Bitmap disc = null;
            boolean failed = false;
            try {
                boolean ask = online && !SunImages.failedWithin(RETRY_MS);
                frame = this.images.latest(CHANNEL, ask);
                if (frame.time != shown) {
                    disc = cut(frame);
                    if (disc == null) {
                        File file = ask ? this.images.image(CHANNEL, frame.time, SIZE) : this.images.kept(CHANNEL, frame.time, SIZE);
                        Bitmap picture = file == null ? null : BitmapFactory.decodeFile(file.getPath());
                        if (picture != null) {
                            disc = SunDisc.cut(picture, radius(frame, picture.getWidth()));
                            keep(frame, disc);
                        }
                    }
                }
            } catch (IOException e) {
                failed = online;
                Log.w(TAG, "No picture: " + e);
            }
            SunImages.Frame shownFrame = frame;
            Bitmap shownDisc = disc;
            boolean shownFailed = failed;
            this.mainHandler.post(() -> {
                this.isLoading = false;
                if (this.isDestroyed) {
                    return;
                }
                this.progress.hide();
                if (shownDisc != null) {
                    boolean first = this.shownTime == Long.MIN_VALUE;
                    this.shownTime = shownFrame.time;
                    this.image.setImageBitmap(shownDisc);
                    this.time.setText(taken(this.shownTime));
                    if (first) {
                        this.image.setAlpha(0f);
                        this.image.animate().alpha(1f).setDuration(200).start();
                    }
                } else if (shownFailed && this.shownTime == Long.MIN_VALUE) {
                    this.time.setText(R.string.sun_now_offline);
                }
                if (this.isOnlineWaiting && !online) {
                    this.isOnlineWaiting = false;
                    refresh(true);
                }
            });
        });
    }

    /** Nothing is shown any more, what was being loaded is dropped */
    public void destroy() {
        this.isDestroyed = true;
    }

    private static synchronized Bitmap cut(SunImages.Frame frame) {
        return frame.time == cutTime ? cutDisc : null;
    }

    private static synchronized void keep(SunImages.Frame frame, Bitmap disc) {
        cutTime = frame.time;
        cutDisc = disc;
    }

    // The Sun's radius in a picture of width pixels; the year's mean when the frame doesn't tell
    static float radius(SunImages.Frame frame, int width) {
        double radius = frame.radius > 0 ? frame.radius : 959.6;
        return (float) (radius * width / frame.channel.field());
    }

    private String taken(long utc) {
        return this.time.getResources().getString(R.string.sun_now_taken, clock(this.formatter, utc));
    }

    /** The time of a frame by the phone's clock, with its date when it was taken on another day */
    static String clock(AstroFormatter formatter, long utc) {
        TimeZone timeZone = TimeZone.getDefault();
        long local = utc + timeZone.getOffset(utc);
        long now = System.currentTimeMillis();
        long today = now + timeZone.getOffset(now);
        Calendar a = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        a.setTimeInMillis(local);
        Calendar b = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        b.setTimeInMillis(today);
        boolean sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        return sameDay ? formatter.clock(local) : formatter.shortDateTime(local);
    }
}
