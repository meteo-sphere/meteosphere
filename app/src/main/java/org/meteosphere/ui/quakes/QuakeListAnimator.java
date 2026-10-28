package org.meteosphere.ui.quakes;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The earthquakes' list moving rather than jumping: the lines that go fade out, those that stay slide to their new
 * places, and those that come fade in, rising one after another when the list comes or changes its period. A line
 * changed in place is bound again where it is, with no fade of its own.
 */
final class QuakeListAnimator extends SimpleItemAnimator {

    private static final long REMOVE_MS = 150;
    private static final long MOVE_MS = 320;
    private static final long ADD_MS = 320;
    // The lines coming rise one after another, the last of them no later than STAGGER_MAX_MS
    private static final long STAGGER_MS = 40;
    private static final long STAGGER_MAX_MS = 360;

    private final float rise;
    private final List<RecyclerView.ViewHolder> pendingRemoves = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> pendingMoves = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> pendingAdds = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> running = new ArrayList<>();
    // The lines of the next changes rise into place one after another, rather than only fading in
    private boolean rising;

    QuakeListAnimator(float density) {
        this.rise = 24 * density;
        // A changed line keeps its holder, bound again in place with the list's payload
        setSupportsChangeAnimations(false);
    }

    /** The lines the next changes bring rise into place one after another, or only fade in */
    void setRising(boolean rising) {
        this.rising = rising;
    }

    @Override
    public boolean animateRemove(RecyclerView.ViewHolder holder) {
        endAnimation(holder);
        this.pendingRemoves.add(holder);
        return true;
    }

    @Override
    public boolean animateAdd(RecyclerView.ViewHolder holder) {
        endAnimation(holder);
        holder.itemView.setAlpha(0);
        holder.itemView.setTranslationY(this.rising ? this.rise : 0);
        this.pendingAdds.add(holder);
        return true;
    }

    @Override
    public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
        View view = holder.itemView;
        // From where it shows, as it may be moving yet
        fromX += (int) view.getTranslationX();
        fromY += (int) view.getTranslationY();
        endAnimation(holder);
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (dx == 0 && dy == 0) {
            dispatchMoveFinished(holder);
            return false;
        }
        view.setTranslationX(-dx);
        view.setTranslationY(-dy);
        this.pendingMoves.add(holder);
        return true;
    }

    @Override
    public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromLeft, int fromTop,
            int toLeft, int toTop) {
        if (oldHolder == newHolder) {
            return animateMove(oldHolder, fromLeft, fromTop, toLeft, toTop);
        }
        // A line made anew, which the payloads leave out: the new one just there
        endAnimation(oldHolder);
        dispatchChangeFinished(oldHolder, true);
        if (newHolder != null) {
            endAnimation(newHolder);
            dispatchChangeFinished(newHolder, false);
        }
        return false;
    }

    @Override
    public void runPendingAnimations() {
        for (RecyclerView.ViewHolder holder : this.pendingRemoves) {
            View view = holder.itemView;
            start(holder, view.animate().alpha(0).setStartDelay(0).setDuration(REMOVE_MS).setInterpolator(QuakeMotion.ACCELERATE), () -> {
                view.setAlpha(1);
                dispatchRemoveFinished(holder);
            });
        }
        for (RecyclerView.ViewHolder holder : this.pendingMoves) {
            View view = holder.itemView;
            start(holder, view.animate().translationX(0).translationY(0).setStartDelay(0).setDuration(MOVE_MS)
                    .setInterpolator(QuakeMotion.EMPHASIZED), () -> {
                view.setTranslationX(0);
                view.setTranslationY(0);
                dispatchMoveFinished(holder);
            });
        }
        // The top one first
        this.pendingAdds.sort(Comparator.comparingInt(holder -> holder.itemView.getTop()));
        for (int i = 0; i < this.pendingAdds.size(); i++) {
            RecyclerView.ViewHolder holder = this.pendingAdds.get(i);
            View view = holder.itemView;
            long delay = this.rising ? Math.min(STAGGER_MAX_MS, i * STAGGER_MS) : 0;
            dispatchAddStarting(holder);
            start(holder, view.animate().alpha(1).translationY(0).setStartDelay(delay).setDuration(ADD_MS)
                    .setInterpolator(QuakeMotion.DECELERATE), () -> {
                view.setAlpha(1);
                view.setTranslationY(0);
                dispatchAddFinished(holder);
            });
        }
        this.pendingRemoves.clear();
        this.pendingMoves.clear();
        this.pendingAdds.clear();
        this.rising = false;
    }

    // The holder's animation started, ending in its line put right and told once, ended or cancelled
    private void start(RecyclerView.ViewHolder holder, android.view.ViewPropertyAnimator animation, Runnable end) {
        this.running.add(holder);
        animation.setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                holder.itemView.animate().setListener(null);
                if (running.remove(holder)) {
                    end.run();
                    finishWhenDone();
                }
            }
        }).start();
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder holder) {
        View view = holder.itemView;
        // A running one ends through its listener
        view.animate().cancel();
        if (this.pendingRemoves.remove(holder)) {
            view.setAlpha(1);
            dispatchRemoveFinished(holder);
        }
        if (this.pendingMoves.remove(holder)) {
            view.setTranslationX(0);
            view.setTranslationY(0);
            dispatchMoveFinished(holder);
        }
        if (this.pendingAdds.remove(holder)) {
            view.setAlpha(1);
            view.setTranslationY(0);
            dispatchAddFinished(holder);
        }
        finishWhenDone();
    }

    @Override
    public void endAnimations() {
        for (RecyclerView.ViewHolder holder : new ArrayList<>(this.pendingRemoves)) {
            endAnimation(holder);
        }
        for (RecyclerView.ViewHolder holder : new ArrayList<>(this.pendingMoves)) {
            endAnimation(holder);
        }
        for (RecyclerView.ViewHolder holder : new ArrayList<>(this.pendingAdds)) {
            endAnimation(holder);
        }
        for (RecyclerView.ViewHolder holder : new ArrayList<>(this.running)) {
            holder.itemView.animate().cancel();
        }
        dispatchAnimationsFinished();
    }

    @Override
    public boolean isRunning() {
        return !this.pendingRemoves.isEmpty() || !this.pendingMoves.isEmpty() || !this.pendingAdds.isEmpty() || !this.running.isEmpty();
    }

    private void finishWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
        }
    }
}
