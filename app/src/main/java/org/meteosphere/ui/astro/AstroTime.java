package org.meteosphere.ui.astro;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.content.res.ColorStateList;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.R;

/**
 * The moment a Moon or Sun tab shows, each tab its own: now, or one picked with the arrows of view_astro_time by
 * the day or the hour, as the segment tapped says, or with the calendar and the clock. The main screen's tabs keep it
 * in memory for the tabs it makes again each time it comes back; not saved, so the app starts at now again.
 */
final class AstroTime {

    private static final long HOUR_MS = 60 * 60 * 1000L;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final String TAG_DATE = "astro_date";
    private static final String TAG_TIME = "astro_time";
    // The arrows and the way back come and go in this long, the fill of a segment fades as long
    private static final long ARROWS_MS = 250;
    private static final int[] ARROWS = {R.id.btnAstroTimePrev, R.id.btnAstroTimeNext, R.id.btnAstroTimeNow};

    // UTC millis of the moment picked, null for now
    private Long picked;
    // The segment the arrows step by: none until one is tapped, then the date or the time
    private static final int NONE = 0;
    private static final int DAY = 1;
    private static final int HOUR = 2;
    private int segment = NONE;
    // How far the arrows and the way back are shown, 0 to 1, where they are going and the animation taking them
    private float arrowsShown;
    private float arrowsTarget;
    private ValueAnimator arrowsAnimator;

    /** The moment picked and the segment the arrows step by, for a tab made again to go on from */
    static final class State {
        final Long picked;
        final int segment;

        State(Long picked, int segment) {
            this.picked = picked;
            this.segment = segment;
        }
    }

    State state() {
        return new State(this.picked, this.segment);
    }

    /** Goes on from the tab before: its moment and segment, set before anything is bound or computed */
    void restore(State state) {
        this.picked = state.picked;
        this.segment = state.segment;
    }

    /** The moment to show, UTC millis */
    long moment() {
        return this.picked != null ? this.picked : System.currentTimeMillis();
    }

    boolean isNow() {
        return this.picked == null;
    }

    private void pick(Long moment, Runnable onChanged) {
        this.picked = moment;
        onChanged.run();
    }

    /**
     * Moves the moment to the local day given, at the hour it is at on the clock of the place, as the arrows do by
     * the date, which it picks for them
     */
    void pickDay(TimeZone timeZone, long localDay, Runnable onChanged) {
        long moment = moment();
        long local = moment + timeZone.getOffset(moment);
        this.segment = DAY;
        pick(fromLocal(timeZone, localDay + Math.floorMod(local, DAY_MS)), onChanged);
    }

    /**
     * Back to now, putting the control of view_astro_time back as it opened: no segment, no arrows; nothing to do
     * there already
     */
    void backToNow(View root, Runnable onChanged) {
        if (isNow() && this.segment == NONE) {
            return;
        }
        this.segment = NONE;
        pick(null, onChanged);
        // Closed at once, not once now is computed
        segments(root);
    }

    /** Shows the moment in the time zone of the place and acts on the arrows, the segments and the way back to now */
    void bind(View root, TimeZone timeZone, boolean is24HourFormat, FragmentManager fragments, Runnable onChanged) {
        TextView date = root.findViewById(R.id.tvAstroDate);
        TextView time = root.findViewById(R.id.tvAstroTime);
        long moment = moment();
        Locale locale = root.getResources().getConfiguration().getLocales().get(0);
        // "Fri, 02.10": numbers keep the width the same from day to day, so the arrows around stay put
        SimpleDateFormat dateFormat = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEddMM"), locale);
        dateFormat.setTimeZone(timeZone);
        SimpleDateFormat timeFormat = is24HourFormat ? new SimpleDateFormat("HH:mm", locale) : new SimpleDateFormat("h:mm a", Locale.US);
        timeFormat.setTimeZone(timeZone);
        date.setText(dayText(dateFormat, moment, locale));
        time.setText(timeFormat.format(new Date(moment)));
        fitWidths(date, time, dateFormat, timeFormat, locale);
        segments(root);

        root.findViewById(R.id.btnAstroTimeNow).setOnClickListener(view -> backToNow(root, onChanged));
        root.findViewById(R.id.btnAstroTimePrev).setOnClickListener(view -> pick(step(timeZone, -1), onChanged));
        root.findViewById(R.id.btnAstroTimeNext).setOnClickListener(view -> pick(step(timeZone, 1), onChanged));
        // A tap picks the segment the arrows step by; a tap on the one picked opens its calendar or clock
        date.setOnClickListener(view -> {
            if (this.segment != DAY) {
                this.segment = DAY;
                segments(root);
            } else {
                pickDate(timeZone, fragments, onChanged);
            }
        });
        time.setOnClickListener(view -> {
            if (this.segment != HOUR) {
                this.segment = HOUR;
                segments(root);
            } else {
                pickTime(timeZone, is24HourFormat, fragments, onChanged);
            }
        });
        // A calendar or a clock made again with the screen, as for a new theme, comes back without its OK
        Fragment shownDate = fragments.findFragmentByTag(TAG_DATE);
        if (shownDate instanceof MaterialDatePicker) {
            @SuppressWarnings("unchecked")
            MaterialDatePicker<Long> picker = (MaterialDatePicker<Long>) shownDate;
            onDatePicked(picker, timeZone, onChanged);
        }
        Fragment shownTime = fragments.findFragmentByTag(TAG_TIME);
        if (shownTime instanceof MaterialTimePicker) {
            onTimePicked((MaterialTimePicker) shownTime, timeZone, onChanged);
        }
    }

    // The picked segment filled, the text in the accent while not at now; the arrows and the way back once a segment
    // is picked, the way back closing them at now too. The fill fades in and out, and the arrows and the way back
    // grow in and shrink away, the place and the moment beside them making room as they go.
    private void segments(View root) {
        TextView date = root.findViewById(R.id.tvAstroDate);
        TextView time = root.findViewById(R.id.tvAstroTime);
        int fill = MaterialColors.getColor(date, com.google.android.material.R.attr.colorSecondaryContainer);
        int onFill = MaterialColors.getColor(date, com.google.android.material.R.attr.colorOnSecondaryContainer);
        int text = MaterialColors.getColor(date, isNow() ? com.google.android.material.R.attr.colorOnSurfaceVariant : androidx.appcompat.R.attr.colorPrimary);
        boolean animate = root.isLaidOut();
        for (TextView view : new TextView[]{date, time}) {
            boolean isPicked = this.segment == (view == time ? HOUR : DAY);
            Animator fading = (Animator) view.getTag(R.id.tag_fading);
            boolean wasPicked = view.getBackground() != null && fading == null;
            if (isPicked && !wasPicked) {
                if (fading != null) {
                    fading.cancel();
                }
                // Rounded on the outer side only, straight where the two segments meet
                Drawable background = ContextCompat.getDrawable(view.getContext(),
                        view == time ? R.drawable.bg_astro_time_segment_end : R.drawable.bg_astro_time_segment_start).mutate();
                view.setBackground(background);
                ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(fill));
                if (animate) {
                    ObjectAnimator.ofInt(background, "alpha", 0, 255).setDuration(ARROWS_MS).start();
                }
            } else if (!isPicked && wasPicked) {
                if (animate) {
                    fadeOut(view);
                } else {
                    view.setBackground(null);
                }
            }
            view.setTextColor(isPicked && isNow() ? onFill : text);
        }
        showArrows(root, this.segment != NONE, animate);
    }

    // The segment's fill faded away, then gone
    private static void fadeOut(View view) {
        Drawable background = view.getBackground();
        ObjectAnimator fade = ObjectAnimator.ofInt(background, "alpha", background.getAlpha(), 0).setDuration(ARROWS_MS);
        fade.addListener(new AnimatorListenerAdapter() {
            private boolean isCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                this.isCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                view.setTag(R.id.tag_fading, null);
                if (!this.isCancelled && view.getBackground() == background) {
                    view.setBackground(null);
                }
            }
        });
        view.setTag(R.id.tag_fading, fade);
        fade.start();
    }

    // The arrows and the way back grown in from nothing, or shrunk away, from where they are now
    private void showArrows(View root, boolean shown, boolean animate) {
        float target = shown ? 1 : 0;
        if (this.arrowsAnimator != null && this.arrowsAnimator.isRunning()) {
            if (this.arrowsTarget == target) {
                return;
            }
            this.arrowsAnimator.cancel();
        }
        this.arrowsTarget = target;
        if (!animate || this.arrowsShown == target) {
            this.arrowsShown = target;
            layArrows(root, target);
            return;
        }
        if (!shown) {
            // The ripple of the tap on the way back would be left behind as it shrinks away
            for (int id : ARROWS) {
                View button = root.findViewById(id);
                button.setPressed(false);
                button.jumpDrawablesToCurrentState();
            }
        }
        ValueAnimator animator = ValueAnimator.ofFloat(this.arrowsShown, target);
        animator.setDuration(Math.round(ARROWS_MS * Math.abs(target - this.arrowsShown)));
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(animation -> {
            this.arrowsShown = (float) animation.getAnimatedValue();
            layArrows(root, this.arrowsShown);
        });
        this.arrowsAnimator = animator;
        animator.start();
    }

    // Each as wide as the share shown of its own width, its icon fading in once there is room for it
    private static void layArrows(View root, float shown) {
        for (int id : ARROWS) {
            View button = root.findViewById(id);
            ViewGroup.LayoutParams params = button.getLayoutParams();
            Object own = button.getTag(R.id.tag_own_width);
            if (!(own instanceof Integer)) {
                own = params.width;
                button.setTag(R.id.tag_own_width, own);
            }
            params.width = Math.round((Integer) own * shown);
            button.setLayoutParams(params);
            button.setAlpha(Math.max(0f, (shown - 0.4f) / 0.6f));
            button.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private static String dayText(SimpleDateFormat format, long moment, Locale locale) {
        String day = format.format(new Date(moment));
        return day.substring(0, 1).toUpperCase(locale) + day.substring(1);
    }

    // Each segment as wide as its longest text of the year and its padding, a hair over the text as the date is in
    // numbers, so the arrows stay put
    private static void fitWidths(TextView date, TextView time, SimpleDateFormat dateFormat, SimpleDateFormat timeFormat, Locale locale) {
        float padding = 2 * 9 * date.getResources().getDisplayMetrics().density;
        float dateWidth = 0;
        long day = 0;
        for (int i = 0; i < 366; i++, day += DAY_MS) {
            dateWidth = Math.max(dateWidth, date.getPaint().measureText(dayText(dateFormat, day, locale)));
        }
        float timeWidth = 0;
        for (int hour = 0; hour < 24; hour++) {
            timeWidth = Math.max(timeWidth, time.getPaint().measureText(timeFormat.format(new Date(hour * HOUR_MS + 59 * 60000L))));
        }
        setWidth(date, Math.round(dateWidth + padding));
        setWidth(time, Math.round(timeWidth + padding));
    }

    private static void setWidth(View view, int width) {
        if (view.getLayoutParams().width != width) {
            view.getLayoutParams().width = width;
            view.requestLayout();
        }
    }

    // A day on keeps the hour on the clock of the place, across a change of its daylight saving time too
    private long step(TimeZone timeZone, int direction) {
        long moment = moment();
        if (this.segment == HOUR) {
            return moment + direction * HOUR_MS;
        }
        long local = moment + timeZone.getOffset(moment) + direction * DAY_MS;
        return fromLocal(timeZone, local);
    }

    /** UTC millis of a wall time of the place read as UTC */
    static long fromLocal(TimeZone timeZone, long local) {
        long guess = local - timeZone.getOffset(local);
        return local - timeZone.getOffset(guess);
    }

    private void pickDate(TimeZone timeZone, FragmentManager fragments, Runnable onChanged) {
        if (fragments.findFragmentByTag(TAG_DATE) != null) {
            return;
        }
        long moment = moment();
        long local = moment + timeZone.getOffset(moment);
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(local - Math.floorMod(local, DAY_MS))
                .build();
        onDatePicked(picker, timeZone, onChanged);
        picker.show(fragments, TAG_DATE);
    }

    // The day picked, at the moment's time of day: the picker gives it as UTC midnight
    private void onDatePicked(MaterialDatePicker<Long> picker, TimeZone timeZone, Runnable onChanged) {
        long moment = moment();
        long timeOfDay = Math.floorMod(moment + timeZone.getOffset(moment), DAY_MS);
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(day -> pick(fromLocal(timeZone, day + timeOfDay), onChanged));
    }

    private void pickTime(TimeZone timeZone, boolean is24HourFormat, FragmentManager fragments, Runnable onChanged) {
        if (fragments.findFragmentByTag(TAG_TIME) != null) {
            return;
        }
        long moment = moment();
        long local = moment + timeZone.getOffset(moment);
        int minutes = (int) (Math.floorMod(local, DAY_MS) / 60000);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(is24HourFormat ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(minutes / 60)
                .setMinute(minutes % 60)
                .build();
        onTimePicked(picker, timeZone, onChanged);
        picker.show(fragments, TAG_TIME);
    }

    // The time picked, on the moment's day
    private void onTimePicked(MaterialTimePicker picker, TimeZone timeZone, Runnable onChanged) {
        long moment = moment();
        long local = moment + timeZone.getOffset(moment);
        long dayStart = local - Math.floorMod(local, DAY_MS);
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(view ->
                pick(fromLocal(timeZone, dayStart + (picker.getHour() * 60L + picker.getMinute()) * 60000), onChanged));
    }
}
