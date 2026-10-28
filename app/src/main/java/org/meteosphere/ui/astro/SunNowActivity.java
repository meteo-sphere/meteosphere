package org.meteosphere.ui.astro;

import org.meteosphere.ui.sky.SkyTheme;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.slider.Slider;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.meteosphere.data.sun.SunChannel;
import org.meteosphere.data.sun.SunImages;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.common.BackgroundWork;
import org.meteosphere.ui.common.EdgeToEdgeUtils;
import org.meteosphere.settings.AppLanguage;

/**
 * The pictures of the Sun from Helioviewer on black: a channel's latest frame across the screen to look closer at,
 * the channels to pick, and the last day hour by hour to go back through or play. Opened from the sun tab's card.
 */
public class SunNowActivity extends AppCompatActivity {

    private static final String TAG = "SunNowActivity";
    private static final String STATE_CHANNEL = "channel";

    // The latest frame is drawn finer than the day's played ones
    private static final int LATEST_SIZE = 1024;
    private static final int HOUR_SIZE = 512;
    private static final int THUMB_SIZE = 128;
    // The day's frames before the latest, one an hour
    private static final int HOURS = 24;
    private static final long HOUR_MS = 60 * 60 * 1000L;
    // The chips' small pictures are drawn anew this often
    private static final long THUMB_PERIOD_MS = 6 * HOUR_MS;
    private static final long PLAY_FRAME_MS = 150;
    private static final long PLAY_WAIT_MS = 100;
    private static final long PLAY_LOOP_PAUSE_MS = 1000;
    private static final long TEXT_FADE_OUT_MS = 90;
    private static final long TEXT_FADE_IN_MS = 160;
    // Closer than this finer parts of the picture are asked for
    private static final float TILE_ZOOM = 1.4f;
    // The coarsest tiles drawn at least this many of their pixels to the screen's are asked for
    private static final float TILE_SHARPNESS = 0.75f;
    // Helioviewer draws a tile in a second or two, several side by side about as fast as one
    private static final int TILE_THREADS = 6;

    // A channel's colour on its chip until its small picture comes
    private static final Map<SunChannel, Integer> COLORS = new EnumMap<>(SunChannel.class);

    static {
        COLORS.put(SunChannel.CORONA, 0xFFC8883A);
        COLORS.put(SunChannel.LOOPS, 0xFFC9A320);
        COLORS.put(SunChannel.PROMINENCES, 0xFFD23A1E);
        COLORS.put(SunChannel.FLARES, 0xFF1FA5A5);
        COLORS.put(SunChannel.SPOTS, 0xFFE0E0E0);
        COLORS.put(SunChannel.MAGNETIC, 0xFF8A8A8A);
        COLORS.put(SunChannel.EJECTIONS, 0xFF3D6BD9);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // The latest frame and the hours' pictures; those of a channel left, still waiting, are let go
    private final ThreadPoolExecutor loader = new ThreadPoolExecutor(3, 3, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private final ThreadPoolExecutor tiles = new ThreadPoolExecutor(TILE_THREADS, TILE_THREADS, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    // Tiles kept on the disk are read apart, never waiting behind those Helioviewer is drawing
    private final ExecutorService keptTiles = Executors.newFixedThreadPool(2);
    // The tiles being read or fetched or waiting to be, by key
    private final Set<String> tilesAsked = new HashSet<>();
    // Tiles looked for on the disk and not there: asked from Helioviewer once the view stops
    private final Set<String> tilesNotKept = new HashSet<>();
    private boolean isSettled = true;
    // Whether tiles are laid over the picture at its zoom
    private boolean areTilesOn;
    // The tiles come lately, of any channel and frame, up to an eighth of the app's memory: back to a part looked at
    // before, they show at once; those gone from here come from the disk
    private final LruCache<String, SunImageView.Tile> tileCache = new LruCache<String, SunImageView.Tile>(
            (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String key, SunImageView.Tile tile) {
            return tile.bitmap.getByteCount();
        }
    };
    private final ExecutorService thumbs = Executors.newSingleThreadExecutor();
    private SunImages images;
    private AstroFormatter formatter;

    private SunImageView imageView;
    private CircularProgressIndicator progress;
    private TextView channelView;
    private TextView timeView;
    private TextView descriptionView;
    private Slider slider;
    private MaterialButton playButton;
    private ChipGroup chips;

    private SunChannel channel;
    private SunImages.Frame latest;
    // The day's pictures, the oldest first and the latest last; asked for, and those that failed while playing
    private final Bitmap[] frames = new Bitmap[HOURS + 1];
    private final boolean[] asked = new boolean[HOURS + 1];
    private final boolean[] missing = new boolean[HOURS + 1];
    private int shown = HOURS;
    // The frames, dropped when the channel changes
    private final BackgroundWork frameWork = new BackgroundWork(this.loader);
    private boolean isPlaying;
    private boolean isFailed;
    private boolean areThumbsAsked;
    // The channels' small pictures, and the latest pictures of the last channels looked at: shown at once when one
    // is picked again or swiped to, until its picture comes
    private final Map<SunChannel, Bitmap> thumbBitmaps = new EnumMap<>(SunChannel.class);
    private final Map<SunChannel, Bitmap> latestImages = new LinkedHashMap<SunChannel, Bitmap>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SunChannel, Bitmap> eldest) {
            return size() > 3;
        }
    };
    private HorizontalScrollView channelRow;
    // The channel is being picked by a swipe of the picture, the slider moved by a finger
    private boolean isSwipePicking;
    private boolean isScrubbing;
    private final Runnable playNext = this::playNext;

    /** "Corona · 193 Å" */
    static String title(Context context, SunChannel channel) {
        return context.getString(nameOf(channel)) + " · " + channel.label;
    }

    private static int nameOf(SunChannel channel) {
        switch (channel) {
            case LOOPS:
                return R.string.sun_channel_loops;
            case PROMINENCES:
                return R.string.sun_channel_prominences;
            case FLARES:
                return R.string.sun_channel_flares;
            case SPOTS:
                return R.string.sun_channel_spots;
            case MAGNETIC:
                return R.string.sun_channel_magnetic;
            case EJECTIONS:
                return R.string.sun_channel_ejections;
            default:
                return R.string.sun_channel_corona;
        }
    }

    private static int aboutOf(SunChannel channel) {
        switch (channel) {
            case LOOPS:
                return R.string.sun_channel_loops_about;
            case PROMINENCES:
                return R.string.sun_channel_prominences_about;
            case FLARES:
                return R.string.sun_channel_flares_about;
            case SPOTS:
                return R.string.sun_channel_spots_about;
            case MAGNETIC:
                return R.string.sun_channel_magnetic_about;
            case EJECTIONS:
                return R.string.sun_channel_ejections_about;
            default:
                return R.string.sun_channel_corona_about;
        }
    }

    // Dates and numbers in the app's language too, also on a phone in a language the app doesn't have
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        // Always dark, with the Sun's amber for what is picked and played
        Context dark = SkyTheme.wrap(this, R.style.ThemeOverlay_Meteo_SunNow);
        LayoutInflater inflater = LayoutInflater.from(dark);
        setContentView(inflater.inflate(R.layout.activity_sun_now, null));
        EdgeToEdgeUtils.applyToolbarInsets(this, findViewById(R.id.appBar));
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        ((MaterialToolbar) findViewById(R.id.tbSunNow)).setNavigationOnClickListener(view -> finish());

        this.images = new SunImages(getCacheDir());
        this.formatter = new AstroFormatter(this, AppSettings.getInstance().use24Hours);

        this.imageView = findViewById(R.id.sunImage);
        this.progress = findViewById(R.id.piSunNow);
        this.channelView = findViewById(R.id.tvSunChannel);
        this.timeView = findViewById(R.id.tvSunTime);
        this.descriptionView = findViewById(R.id.tvSunDescription);
        this.slider = findViewById(R.id.slSunTime);
        this.playButton = findViewById(R.id.btnSunPlay);
        this.chips = findViewById(R.id.cgSunChannels);

        this.imageView.setOnSettledListener(this::askTile);
        this.imageView.setOnMovedListener(this::moveTiles);
        this.imageView.setOnSwipeListener(new SunImageView.OnSwipeListener() {
            @Override
            public boolean canSwipe(int direction) {
                int index = SunNowActivity.this.channel.ordinal() + direction;
                return index >= 0 && index < SunChannel.values().length;
            }

            @Override
            public void onSwipe(int direction) {
                Chip chip = chipOf(SunChannel.values()[SunNowActivity.this.channel.ordinal() + direction]);
                SunNowActivity.this.isSwipePicking = true;
                chip.setChecked(true);
                SunNowActivity.this.isSwipePicking = false;
                showChip(chip, true);
            }
        });
        this.descriptionView.setOnClickListener(view -> {
            if (this.isFailed) {
                fadeText(this.descriptionView, getString(aboutOf(this.channel)));
                askLatest();
            }
        });
        // The day is counted from the channel's latest frame again, not restored
        this.slider.setSaveEnabled(false);
        this.slider.setLabelFormatter(value -> this.latest == null ? "" : SunNowCard.clock(this.formatter, time((int) value + HOURS)));
        this.slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                SunNowActivity.this.isScrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                SunNowActivity.this.isScrubbing = false;
            }
        });
        this.slider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                stop();
                show((int) value + HOURS);
            }
        });
        this.playButton.setOnClickListener(view -> {
            if (this.isPlaying) {
                stop();
            } else {
                play();
            }
        });

        SunChannel first = channel(bundle != null ? bundle.getString(STATE_CHANNEL) : null);
        Chip firstChip = null;
        for (SunChannel channel : SunChannel.values()) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_sun_channel, this.chips, false);
            chip.setId(View.generateViewId());
            chip.setTag(channel);
            chip.setText(nameOf(channel));
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(COLORS.get(channel));
            chip.setChipIcon(dot);
            this.chips.addView(chip);
            if (channel == first) {
                chip.setChecked(true);
                firstChip = chip;
            }
        }
        // The channel picked before in view
        this.channelRow = findViewById(R.id.hsvSunChannels);
        Chip picked = firstChip;
        this.channelRow.post(() -> {
            showChip(picked, false);
            fitDescription();
        });
        this.chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                select((SunChannel) group.findViewById(checkedIds.get(0)).getTag());
            }
        });
        select(first);
    }

    // A text changed through a short fade rather than at once; none fades out and stays so
    private static void fadeText(TextView view, CharSequence text) {
        view.animate().cancel();
        // Shown already; a fade to another text asked for a moment ago, as two channels were tapped one after the
        // other, is left before it begins
        if (TextUtils.equals(view.getText(), text) && view.getAlpha() == 1) {
            return;
        }
        view.animate().alpha(0f).setDuration(TEXT_FADE_OUT_MS).withEndAction(() -> {
            view.setText(text);
            if (!TextUtils.isEmpty(text)) {
                view.animate().alpha(1f).setDuration(TEXT_FADE_IN_MS).start();
            }
        }).start();
    }

    // The description as tall as the longest one, so what is under it never moves when the channel changes
    private void fitDescription() {
        int width = this.descriptionView.getWidth() - this.descriptionView.getPaddingLeft() - this.descriptionView.getPaddingRight();
        if (width <= 0) {
            return;
        }
        int lines = 1;
        for (SunChannel channel : SunChannel.values()) {
            String text = getString(aboutOf(channel));
            StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), this.descriptionView.getPaint(), width).build();
            lines = Math.max(lines, layout.getLineCount());
        }
        this.descriptionView.setMinLines(lines);
    }

    private Chip chipOf(SunChannel channel) {
        return this.chips.findViewWithTag(channel);
    }

    // The row scrolled as little as it takes for the chip to be wholly in view
    private void showChip(Chip chip, boolean smooth) {
        int left = chip.getLeft();
        int right = chip.getRight() + this.channelRow.getPaddingLeft() + this.channelRow.getPaddingRight();
        int x = this.channelRow.getScrollX();
        int width = this.channelRow.getWidth();
        int target = left < x ? left : right > x + width ? right - width : x;
        if (target == x) {
            return;
        }
        if (smooth) {
            this.channelRow.smoothScrollTo(target, 0);
        } else {
            this.channelRow.scrollTo(target, 0);
        }
    }

    private static SunChannel channel(String key) {
        for (SunChannel channel : SunChannel.values()) {
            if (channel.key.equals(key)) {
                return channel;
            }
        }
        return SunChannel.CORONA;
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(STATE_CHANNEL, this.channel.key);
    }

    @Override
    protected void onPause() {
        stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        this.loader.shutdownNow();
        this.tiles.shutdownNow();
        this.keptTiles.shutdownNow();
        this.thumbs.shutdownNow();
        super.onDestroy();
    }

    private void select(SunChannel channel) {
        if (channel == this.channel) {
            return;
        }
        this.channel = channel;
        stop();
        this.frameWork.drop();
        // The new channel's latest frame mustn't wait behind the hours of the one before, played a moment ago
        this.loader.getQueue().clear();
        dropWaitingTiles();
        Arrays.fill(this.frames, null);
        Arrays.fill(this.asked, false);
        Arrays.fill(this.missing, false);
        this.latest = null;
        // The slider stays where it is: the new channel is shown at the same hour back from its latest frame
        // The texts give way softly; the time comes back with the channel's frame
        fadeText(this.channelView, title(this, channel));
        fadeText(this.descriptionView, getString(aboutOf(channel)));
        fadeText(this.timeView, null);
        // Until its frame comes: at now its last picture or its small one drawn large; back in the day the picture
        // stays, or the small one comes in with a swipe, since its last picture is of another hour
        Bitmap kept = this.shown == HOURS ? this.latestImages.get(channel) : null;
        if (kept == null && (this.shown == HOURS || this.isSwipePicking)) {
            kept = this.thumbBitmaps.get(channel);
        }
        if (kept != null) {
            this.imageView.setImage(kept, !this.isSwipePicking);
        }
        askLatest();
    }

    // The channel's latest frame, which the day's hours are counted back from
    private void askLatest() {
        SunChannel channel = this.channel;
        this.isFailed = false;
        this.progress.show();
        this.frameWork.run(() -> {
            try {
                SunImages.Frame frame = this.images.latest(channel, true);
                return Pair.create(frame, decode(this.images.image(channel, frame.time, LATEST_SIZE)));
            } catch (IOException e) {
                Log.w(TAG, "No latest picture of " + channel.key + ": " + e);
                return null;
            }
        }, loaded -> {
            if (isDestroyed()) {
                return;
            }
            this.progress.hide();
            if (loaded == null || loaded.second == null) {
                this.isFailed = true;
                fadeText(this.descriptionView, getString(R.string.sun_now_failed));
                return;
            }
            this.latest = loaded.first;
            this.frames[HOURS] = loaded.second;
            this.latestImages.put(channel, loaded.second);
            this.asked[HOURS] = true;
            show(this.shown);
            askThumbs();
        });
    }

    // The frame of an hour of the day, the latest last
    private long time(int index) {
        if (index == HOURS) {
            return this.latest.time;
        }
        long lastHour = this.latest.time / HOUR_MS * HOUR_MS;
        return lastHour - (HOURS - 1 - index) * HOUR_MS;
    }

    private void show(int index) {
        this.shown = index;
        Bitmap bitmap = this.frames[index];
        if (this.latest != null) {
            String time = getString(R.string.sun_now_frame, SunNowCard.clock(this.formatter, time(index)));
            // Played or moved through, the time follows at once; a channel's first frame fades in
            if (this.isPlaying || this.timeView.getAlpha() == 1 && this.timeView.getText().length() > 0) {
                this.timeView.animate().cancel();
                this.timeView.setAlpha(1);
                this.timeView.setText(time);
            } else {
                fadeText(this.timeView, time);
            }
        }
        if (bitmap == null) {
            ask(index);
            if (!this.isPlaying) {
                this.progress.show();
            }
            return;
        }
        this.progress.hide();
        // Played or moved through, the frames follow at once; otherwise the new one fades in over the one before
        this.imageView.setImage(bitmap, !this.isPlaying && !this.isScrubbing);
        if (!this.isPlaying && this.imageView.getZoom() >= TILE_ZOOM) {
            askTile(this.imageView.visible(), this.imageView.getZoom());
        }
    }

    private void ask(int index) {
        if (this.asked[index] || this.latest == null) {
            return;
        }
        this.asked[index] = true;
        SunChannel channel = this.channel;
        long time = time(index);
        int size = index == HOURS ? LATEST_SIZE : HOUR_SIZE;
        this.frameWork.run(() -> {
            try {
                return decode(this.images.image(channel, time, size));
            } catch (IOException e) {
                Log.w(TAG, "No picture of " + channel.key + " at " + time + ": " + e);
                return null;
            }
        }, picture -> {
            if (isDestroyed()) {
                return;
            }
            if (picture == null) {
                // Played past; asked again when stopped on
                this.asked[index] = false;
                this.missing[index] = true;
                if (index == this.shown) {
                    this.progress.hide();
                }
                return;
            }
            this.frames[index] = picture;
            this.missing[index] = false;
            if (index == this.shown) {
                show(index);
            }
        });
    }

    private void play() {
        if (this.latest == null) {
            return;
        }
        this.isPlaying = true;
        this.playButton.setIconResource(R.drawable.ic_pause);
        this.playButton.setContentDescription(getString(R.string.sun_now_pause));
        this.progress.hide();
        // The oldest first, as they will be played
        Arrays.fill(this.missing, false);
        for (int i = 0; i <= HOURS; i++) {
            ask(i);
        }
        this.mainHandler.post(this.playNext);
    }

    // After the latest frame a pause, then the oldest again; a frame not here yet is waited for, a failed one skipped
    private void playNext() {
        if (!this.isPlaying) {
            return;
        }
        int next = this.shown;
        do {
            next = next == HOURS ? 0 : next + 1;
        } while (this.missing[next] && next != this.shown);
        if (this.frames[next] == null) {
            this.mainHandler.postDelayed(this.playNext, PLAY_WAIT_MS);
            return;
        }
        show(next);
        this.slider.setValue(next - HOURS);
        this.mainHandler.postDelayed(this.playNext, next == HOURS ? PLAY_LOOP_PAUSE_MS : PLAY_FRAME_MS);
    }

    private void stop() {
        if (!this.isPlaying) {
            return;
        }
        this.isPlaying = false;
        this.mainHandler.removeCallbacks(this.playNext);
        this.playButton.setIconResource(R.drawable.ic_play);
        this.playButton.setContentDescription(getString(R.string.sun_now_play));
        if (this.imageView.getZoom() >= TILE_ZOOM) {
            askTile(this.imageView.visible(), this.imageView.getZoom());
        }
    }

    // The view has stopped: the finer parts of the frame shown over the part of it in view, when looked at closer than
    // its picture is drawn; the tiles kept show at once, the others are asked for
    private void askTile(RectF visible, float zoom) {
        this.isSettled = true;
        dropWaitingTiles();
        wantTiles(visible, zoom);
        showTiles();
    }

    // The view is moving: the tiles coming into view are read from the disk, Helioviewer is asked once it stops
    private void moveTiles(RectF visible, float zoom) {
        this.isSettled = false;
        if (areTilesShown(zoom) != this.areTilesOn) {
            showTiles();
        }
        wantTiles(visible, zoom);
    }

    // The tiles in view at the zoom's level not in memory, the middle first: read from the disk, or asked from
    // Helioviewer when not there and the view has stopped
    private void wantTiles(RectF visible, float zoom) {
        if (!areTilesShown(zoom)) {
            return;
        }
        SunChannel channel = this.channel;
        long time = time(this.shown);
        int level = level(channel, zoom);
        int count = 1 << level;
        if (count * SunImages.TILE <= this.frames[this.shown].getWidth()) {
            return;
        }
        int left = Math.max(0, (int) Math.floor(visible.left * count));
        int right = Math.min(count - 1, (int) Math.ceil(visible.right * count) - 1);
        int top = Math.max(0, (int) Math.floor(visible.top * count));
        int bottom = Math.min(count - 1, (int) Math.ceil(visible.bottom * count) - 1);
        List<int[]> wanted = new ArrayList<>();
        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                wanted.add(new int[] {column, row});
            }
        }
        float middleX = visible.centerX() * count - 0.5f;
        float middleY = visible.centerY() * count - 0.5f;
        Collections.sort(wanted, (a, b) -> Float.compare(distance(a, middleX, middleY), distance(b, middleX, middleY)));
        for (int[] tile : wanted) {
            String key = tileKey(channel, time) + level + "_" + tile[0] + "_" + tile[1];
            if (this.tileCache.get(key) != null || this.tilesAsked.contains(key)) {
                continue;
            }
            boolean isKept = !this.tilesNotKept.contains(key);
            if (isKept || this.isSettled) {
                this.tilesAsked.add(key);
                (isKept ? this.keptTiles : this.tiles).execute(new TileFetch(key, channel, time, level, tile[0], tile[1], !isKept));
            }
        }
    }

    private boolean areTilesShown(float zoom) {
        return zoom >= TILE_ZOOM && !this.isPlaying && this.latest != null && this.frames[this.shown] != null && this.channel.hasDisc();
    }

    // The coarsest level of tiles sharp enough on the screen at the zoom, at most the channel's finest
    private int level(SunChannel channel, float zoom) {
        float wanted = this.imageView.getWidth() * zoom * TILE_SHARPNESS;
        int level = 1;
        while (level < SunImages.finestLevel(channel) && (1 << level) * SunImages.TILE < wanted) {
            level++;
        }
        return level;
    }

    private static float distance(int[] tile, float x, float y) {
        return (tile[0] - x) * (tile[0] - x) + (tile[1] - y) * (tile[1] - y);
    }

    // The start of the keys of a frame's tiles, which end with the level, column and row
    private static String tileKey(SunChannel channel, long time) {
        return channel.key + "_" + time + "_";
    }

    // The frame's tiles in memory over its picture, the coarser under the finer: the view draws those in view as it
    // moves
    private void showTiles() {
        this.areTilesOn = areTilesShown(this.imageView.getZoom());
        if (!this.areTilesOn) {
            this.imageView.setTiles(Collections.emptyList());
            return;
        }
        String key = tileKey(this.channel, time(this.shown));
        List<SunImageView.Tile> shown = new ArrayList<>();
        for (Map.Entry<String, SunImageView.Tile> entry : this.tileCache.snapshot().entrySet()) {
            if (entry.getKey().startsWith(key)) {
                shown.add(entry.getValue());
            }
        }
        Collections.sort(shown, (a, b) -> Integer.compare(a.level, b.level));
        this.imageView.setTiles(shown);
    }

    // Tiles still waiting are no longer asked for: the view has moved, or the channel changed; those being fetched are
    // kept for later
    private void dropWaitingTiles() {
        List<Runnable> waiting = new ArrayList<>();
        this.tiles.getQueue().drainTo(waiting);
        for (Runnable fetch : waiting) {
            this.tilesAsked.remove(((TileFetch) fetch).key);
        }
    }

    // A tile asked for, from the disk or from Helioviewer when online, known by its key while it waits
    private final class TileFetch implements Runnable {
        final String key;
        private final SunChannel channel;
        private final long time;
        private final int level;
        private final int column;
        private final int row;
        private final boolean online;

        TileFetch(String key, SunChannel channel, long time, int level, int column, int row, boolean online) {
            this.key = key;
            this.channel = channel;
            this.time = time;
            this.level = level;
            this.column = column;
            this.row = row;
            this.online = online;
        }

        @Override
        public void run() {
            Bitmap bitmap = null;
            try {
                SunImages images = SunNowActivity.this.images;
                File file = this.online ? images.tile(this.channel, this.time, this.level, this.column, this.row)
                        : images.keptTile(this.channel, this.time, this.level, this.column, this.row);
                if (file != null) {
                    bitmap = decode(file);
                    // Drawn smaller when looked at less close, it stays smooth
                    bitmap.setHasMipMap(true);
                }
            } catch (IOException e) {
                Log.w(TAG, "No tile " + this.key + ": " + e);
            }
            Bitmap tile = bitmap;
            SunNowActivity.this.mainHandler.post(() -> {
                SunNowActivity.this.tilesAsked.remove(this.key);
                if (isDestroyed()) {
                    return;
                }
                if (tile == null) {
                    // Not on the disk: asked from Helioviewer now if the view has stopped, else when it does
                    if (!this.online) {
                        SunNowActivity.this.tilesNotKept.add(this.key);
                        if (SunNowActivity.this.isSettled) {
                            wantTiles(SunNowActivity.this.imageView.visible(), SunNowActivity.this.imageView.getZoom());
                        }
                    }
                    return;
                }
                SunNowActivity.this.tilesNotKept.remove(this.key);
                float count = 1 << this.level;
                RectF rect = new RectF(this.column / count, this.row / count, (this.column + 1) / count, (this.row + 1) / count);
                SunNowActivity.this.tileCache.put(this.key, new SunImageView.Tile(rect, this.level, tile));
                if (this.channel == SunNowActivity.this.channel && SunNowActivity.this.latest != null && time(SunNowActivity.this.shown) == this.time) {
                    showTiles();
                }
            });
        }
    }

    // Each channel's small picture on its chip, once the picture shown has come
    private void askThumbs() {
        if (this.areThumbsAsked) {
            return;
        }
        this.areThumbsAsked = true;
        long time = System.currentTimeMillis() / THUMB_PERIOD_MS * THUMB_PERIOD_MS;
        for (int i = 0; i < this.chips.getChildCount(); i++) {
            Chip chip = (Chip) this.chips.getChildAt(i);
            SunChannel channel = (SunChannel) chip.getTag();
            this.thumbs.execute(() -> {
                try {
                    Bitmap bitmap = decode(this.images.image(channel, time, THUMB_SIZE));
                    RoundedBitmapDrawable round = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                    round.setCircular(true);
                    Drawable icon = round;
                    this.mainHandler.post(() -> {
                        if (!isDestroyed()) {
                            chip.setChipIcon(icon);
                            this.thumbBitmaps.put(channel, bitmap);
                        }
                    });
                } catch (IOException e) {
                    Log.w(TAG, "No small picture of " + channel.key + ": " + e);
                }
            });
        }
    }

    private static Bitmap decode(File file) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        if (bitmap == null) {
            file.delete();
            throw new IOException("Can't decode " + file);
        }
        return bitmap;
    }
}
