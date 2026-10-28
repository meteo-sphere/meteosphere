package org.meteosphere.ui.details;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;

/**
 * A page bound again, its forecast updated or another minute come, moving rather than jumping: whatever the bind moves
 * slides to its new place, as the cards when the header loses its feels-like line, and a header's line that shows
 * something else fades from the old into the new
 */
final class BindMotion {

    private static final long SLIDE_MS = 300;
    // The old text goes before the new one comes, the two hardly over each other; a line gone goes at once, as the
    // lines under it slide up over its place
    private static final long FADE_OUT_MS = 300;
    private static final long GONE_OUT_MS = 120;
    private static final long FADE_IN_DELAY_MS = 220;
    private static final long FADE_IN_MS = 500;

    private final ViewGroup header;
    private final ViewGroup content;
    private final boolean fade;
    private final boolean slide;
    // The header's lines, where they stood, what they showed and how it looked
    private final View[] lines;
    private final int[] lineTops;
    private final boolean[] linesShown;
    private final String[] lineSigns;
    private final Bitmap[] linePictures;
    // The content's parts after the header, where they stood
    private final View[] parts;
    private final int[] partTops;
    private final boolean[] partsShown;
    private final ArrayList<View> moving = new ArrayList<>();
    private final ArrayList<Drawable> ghosts = new ArrayList<>();
    private final ArrayList<Animator> animators = new ArrayList<>();
    private ViewTreeObserver.OnPreDrawListener waiting;

    /**
     * Before the bind: the header's lines kept as they look when they may change (fade), where everything stands when it
     * may move on screen (slide)
     */
    BindMotion(ViewGroup header, ViewGroup content, boolean fade, boolean slide) {
        this.header = header;
        this.content = content;
        this.fade = fade;
        this.slide = slide;
        int lineCount = header.getChildCount();
        this.lines = new View[lineCount];
        this.lineTops = new int[lineCount];
        this.linesShown = new boolean[lineCount];
        this.lineSigns = new String[lineCount];
        this.linePictures = new Bitmap[lineCount];
        for (int i = 0; i < lineCount; i++) {
            View line = header.getChildAt(i);
            this.lines[i] = line;
            this.lineTops[i] = line.getTop();
            this.linesShown[i] = line.getVisibility() == View.VISIBLE;
            if (fade) {
                this.lineSigns[i] = sign(line);
                this.linePictures[i] = this.linesShown[i] ? picture(line) : null;
            }
        }
        int partCount = content.getChildCount();
        this.parts = new View[partCount];
        this.partTops = new int[partCount];
        this.partsShown = new boolean[partCount];
        for (int i = 0; i < partCount; i++) {
            View part = content.getChildAt(i);
            this.parts[i] = part;
            this.partTops[i] = part.getTop();
            this.partsShown[i] = part != header && part.getVisibility() == View.VISIBLE;
        }
    }

    /** After the bind: once the page is laid out anew, the moves from the places before */
    void play() {
        this.waiting = () -> {
            this.content.getViewTreeObserver().removeOnPreDrawListener(this.waiting);
            this.waiting = null;
            move();
            return true;
        };
        this.content.getViewTreeObserver().addOnPreDrawListener(this.waiting);
    }

    /** Everything at its new place at once: another bind comes, or the page goes */
    void finish() {
        if (this.waiting != null) {
            this.content.getViewTreeObserver().removeOnPreDrawListener(this.waiting);
            this.waiting = null;
        }
        for (Animator animator : new ArrayList<>(this.animators)) {
            animator.end();
        }
        this.animators.clear();
        for (View view : this.moving) {
            view.animate().cancel();
            view.setTranslationY(0);
            view.setAlpha(1);
        }
        this.moving.clear();
        for (Drawable ghost : this.ghosts) {
            this.header.getOverlay().remove(ghost);
        }
        this.ghosts.clear();
    }

    private void move() {
        for (int i = 0; i < this.lines.length; i++) {
            View line = this.lines[i];
            boolean shown = line.getVisibility() == View.VISIBLE;
            if (this.fade && !sign(line).equals(this.lineSigns[i])) {
                if (this.linePictures[i] != null) {
                    ghost(this.linePictures[i], line.getLeft(), this.lineTops[i], shown ? FADE_OUT_MS : GONE_OUT_MS);
                }
                if (shown) {
                    fadeIn(line);
                }
            } else if (this.slide && shown && this.linesShown[i] && line.getTop() != this.lineTops[i]) {
                slide(line, this.lineTops[i] - line.getTop());
            }
        }
        if (!this.slide) {
            return;
        }
        for (int i = 0; i < this.parts.length; i++) {
            View part = this.parts[i];
            if (this.partsShown[i] && part.getVisibility() == View.VISIBLE && part.getTop() != this.partTops[i]) {
                slide(part, this.partTops[i] - part.getTop());
            }
        }
    }

    private void slide(View view, int from) {
        this.moving.add(view);
        view.setTranslationY(from);
        view.animate().translationY(0).setStartDelay(0).setDuration(SLIDE_MS).setInterpolator(new DecelerateInterpolator());
    }

    private void fadeIn(View view) {
        this.moving.add(view);
        view.setAlpha(0);
        view.animate().alpha(1).setStartDelay(FADE_IN_DELAY_MS).setDuration(FADE_IN_MS);
    }

    // The line as it looked, over the header where it stood, fading out
    private void ghost(Bitmap picture, int left, int top, long duration) {
        BitmapDrawable ghost = new BitmapDrawable(this.header.getResources(), picture);
        ghost.setBounds(left, top, left + picture.getWidth(), top + picture.getHeight());
        this.header.getOverlay().add(ghost);
        this.ghosts.add(ghost);
        ObjectAnimator out = ObjectAnimator.ofInt(ghost, "alpha", 255, 0).setDuration(duration);
        out.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                BindMotion.this.header.getOverlay().remove(ghost);
                BindMotion.this.ghosts.remove(ghost);
                BindMotion.this.animators.remove(animation);
            }
        });
        this.animators.add(out);
        out.start();
    }

    private static Bitmap picture(View view) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        Bitmap picture = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(picture));
        return picture;
    }

    // What a line shows: its texts, what is seen of it, the wind arrow's turn
    private static String sign(View view) {
        StringBuilder sign = new StringBuilder().append(view.getVisibility());
        if (view instanceof TextView) {
            sign.append('|').append(((TextView) view).getText());
        } else if (view instanceof ImageView) {
            sign.append('|').append(view.getRotation());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                sign.append('[').append(sign(group.getChildAt(i))).append(']');
            }
        }
        return sign.toString();
    }
}
