package org.meteosphere.ui.astro;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import java.util.Collections;
import java.util.List;

/**
 * A picture of the Sun, square across the screen: pinch or double tap to look closer, drag to move about. Finer parts
 * of the picture, tiles asked for when the view settles, are laid over the part it shows. Not looked at closer, the
 * picture follows a finger sideways and, let go far enough or flung, gives way to the next channel's or the one before.
 */
public final class SunImageView extends View {

    /** Told the part of the picture in view, as fractions of its sides, when the view stops moving */
    interface OnSettledListener {
        void onSettled(RectF visible, float zoom);
    }

    /** Told the part of the picture in view, as fractions of its sides, each time it moves or zooms */
    interface OnMovedListener {
        void onMoved(RectF visible, float zoom);
    }

    /** A finer part of the picture over the part of it within rect, fractions of its sides; finer levels go over */
    static final class Tile {
        final RectF rect;
        final int level;
        final Bitmap bitmap;

        Tile(RectF rect, int level, Bitmap bitmap) {
            this.rect = rect;
            this.level = level;
            this.bitmap = bitmap;
        }
    }

    /** The channels on either side: whether there is one, and going to it; direction 1 is the next, -1 the one before */
    interface OnSwipeListener {
        boolean canSwipe(int direction);

        void onSwipe(int direction);
    }

    private static final float MAX_ZOOM = 12;
    // Let go past this part of the width, the picture gives way; without a channel there it follows the finger less
    private static final float SWIPE_PART = 0.25f;
    private static final float SWIPE_RESISTANCE = 0.25f;
    // The next picture comes in from this part of the width
    private static final float SWIPE_ENTER_PART = 0.35f;
    private static final float DOUBLE_TAP_ZOOM = 3;
    private static final long SETTLE_MS = 250;
    private static final long CROSSFADE_MS = 200;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    // Without smoothed edges, so tiles side by side leave no seam
    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private Bitmap image;
    // The picture before, fading out under the new one
    private Bitmap previous;
    private float fade = 1;
    private ValueAnimator fadeAnimator;
    // The coarser first
    private List<Tile> tiles = Collections.emptyList();
    // The middle of the view on the picture, in fractions of its sides, and how much larger than the view it is
    private float centerX = 0.5f;
    private float centerY = 0.5f;
    private float zoom = 1;
    private ValueAnimator animator;
    private OnSettledListener onSettledListener;
    private OnMovedListener onMovedListener;
    private OnSwipeListener onSwipeListener;
    // The picture moved sideways by a finger, as far as it follows it, and how far the finger went
    private float swipeX;
    private float swipeDrag;
    private boolean isSwiping;
    private int flingDirection;
    private final float minFlingVelocity;
    private ValueAnimator swipeAnimator;
    private final Runnable settled = () -> {
        if (this.onSettledListener != null) {
            this.onSettledListener.onSettled(visible(), this.zoom);
        }
    };

    public SunImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomAt(SunImageView.this.zoom * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        this.scaleDetector.setQuickScaleEnabled(false);
        this.minFlingVelocity = 600 * context.getResources().getDisplayMetrics().density;
        this.gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (SunImageView.this.zoom > 1.001f) {
                    float size = getWidth() * SunImageView.this.zoom;
                    moveTo(SunImageView.this.centerX + distanceX / size, SunImageView.this.centerY + distanceY / size);
                } else if (e1 != null && !SunImageView.this.scaleDetector.isInProgress() && e2.getPointerCount() == 1) {
                    swipeBy(e1, e2, distanceX);
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (SunImageView.this.isSwiping && Math.abs(velocityX) > Math.abs(velocityY) && Math.abs(velocityX) > SunImageView.this.minFlingVelocity) {
                    SunImageView.this.flingDirection = velocityX < 0 ? 1 : -1;
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                animateZoom(SunImageView.this.zoom > 1.05f ? 1 : DOUBLE_TAP_ZOOM, e.getX(), e.getY());
                return true;
            }
        });
    }

    void setOnSettledListener(OnSettledListener listener) {
        this.onSettledListener = listener;
    }

    void setOnMovedListener(OnMovedListener listener) {
        this.onMovedListener = listener;
    }

    void setOnSwipeListener(OnSwipeListener listener) {
        this.onSwipeListener = listener;
    }

    /** The picture shown, fading in over the one before when crossfade is set */
    void setImage(Bitmap image, boolean crossfade) {
        if (this.fadeAnimator != null) {
            this.fadeAnimator.cancel();
        }
        boolean fades = crossfade && this.image != null && image != null && image != this.image;
        this.previous = fades ? this.image : null;
        this.image = image;
        this.tiles = Collections.emptyList();
        this.fade = 1;
        if (fades) {
            this.fade = 0;
            this.fadeAnimator = ValueAnimator.ofFloat(0, 1);
            this.fadeAnimator.setDuration(CROSSFADE_MS);
            this.fadeAnimator.addUpdateListener(animation -> {
                this.fade = (float) animation.getAnimatedValue();
                invalidate();
            });
            this.fadeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    SunImageView.this.previous = null;
                    SunImageView.this.fade = 1;
                    invalidate();
                }
            });
            this.fadeAnimator.start();
        }
        invalidate();
    }

    /** The finer parts of the picture laid over it, the coarser first; those out of view are passed over */
    void setTiles(List<Tile> tiles) {
        this.tiles = tiles;
        invalidate();
    }

    float getZoom() {
        return this.zoom;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (this.animator != null) {
                this.animator.cancel();
            }
            // A picture going or coming is let arrive at once
            while (this.swipeAnimator != null && this.swipeAnimator.isRunning()) {
                this.swipeAnimator.end();
            }
        }
        this.scaleDetector.onTouchEvent(event);
        this.gestureDetector.onTouchEvent(event);
        if (this.isSwiping && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN)) {
            finishSwipe(action == MotionEvent.ACTION_UP);
        }
        return true;
    }

    // Sideways the picture follows the finger, less where there is no channel to go to; up and down it stays
    private void swipeBy(MotionEvent down, MotionEvent move, float distanceX) {
        if (!this.isSwiping) {
            if (Math.abs(move.getX() - down.getX()) <= Math.abs(move.getY() - down.getY())) {
                return;
            }
            this.isSwiping = true;
            this.swipeDrag = this.swipeX;
            this.flingDirection = 0;
        }
        this.swipeDrag -= distanceX;
        this.swipeX = canSwipe(this.swipeDrag < 0 ? 1 : -1) ? this.swipeDrag : this.swipeDrag * SWIPE_RESISTANCE;
        invalidate();
    }

    private boolean canSwipe(int direction) {
        return this.onSwipeListener != null && this.onSwipeListener.canSwipe(direction);
    }

    // Far enough or flung that way, the picture goes and the next one comes in from the other side; else it comes back
    private void finishSwipe(boolean released) {
        this.isSwiping = false;
        int direction = this.swipeX < 0 ? 1 : -1;
        boolean far = Math.abs(this.swipeX) > getWidth() * SWIPE_PART || this.flingDirection == direction;
        if (released && far && canSwipe(direction)) {
            animateSwipe(-direction * getWidth(), 150, new AccelerateInterpolator(), () -> {
                this.onSwipeListener.onSwipe(direction);
                this.swipeX = direction * getWidth() * SWIPE_ENTER_PART;
                animateSwipe(0, 220, new DecelerateInterpolator(), null);
            });
        } else {
            animateSwipe(0, 200, new DecelerateInterpolator(), null);
        }
    }

    private void animateSwipe(float target, long duration, TimeInterpolator interpolator, Runnable onEnd) {
        ValueAnimator animator = ValueAnimator.ofFloat(this.swipeX, target);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(animation -> {
            this.swipeX = (float) animation.getAnimatedValue();
            invalidate();
        });
        if (onEnd != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        this.swipeAnimator = animator;
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (this.image == null) {
            return;
        }
        float size = getWidth() * this.zoom;
        float left = getWidth() / 2f - this.centerX * size + this.swipeX;
        float top = getHeight() / 2f - this.centerY * size;
        // Fainter the farther it is moved aside
        int alpha = Math.round(255 * (1 - 0.7f * Math.min(1, Math.abs(this.swipeX) / Math.max(1, getWidth()))));
        this.rect.set(left, top, left + size, top + size);
        if (this.previous != null) {
            this.paint.setAlpha(alpha);
            canvas.drawBitmap(this.previous, null, this.rect, this.paint);
        }
        this.paint.setAlpha(Math.round(alpha * this.fade));
        canvas.drawBitmap(this.image, null, this.rect, this.paint);
        this.tilePaint.setAlpha(alpha);
        for (Tile tile : this.tiles) {
            this.rect.set(left + tile.rect.left * size, top + tile.rect.top * size, left + tile.rect.right * size, top + tile.rect.bottom * size);
            if (this.rect.intersects(0, 0, getWidth(), getHeight())) {
                canvas.drawBitmap(tile.bitmap, null, this.rect, this.tilePaint);
            }
        }
    }

    /** The part of the picture in view, as fractions of its sides */
    RectF visible() {
        float half = 0.5f / this.zoom;
        return new RectF(this.centerX - half, this.centerY - half, this.centerX + half, this.centerY + half);
    }

    // The point under the view's x and y stays there
    private void zoomAt(float zoom, float x, float y) {
        float size = getWidth() * this.zoom;
        float pointX = this.centerX + (x - getWidth() / 2f) / size;
        float pointY = this.centerY + (y - getHeight() / 2f) / size;
        this.zoom = Math.max(1, Math.min(MAX_ZOOM, zoom));
        size = getWidth() * this.zoom;
        moveTo(pointX - (x - getWidth() / 2f) / size, pointY - (y - getHeight() / 2f) / size);
    }

    // The picture always covers the view
    private void moveTo(float x, float y) {
        float half = 0.5f / this.zoom;
        this.centerX = Math.max(half, Math.min(1 - half, x));
        this.centerY = Math.max(half, Math.min(1 - half, y));
        invalidate();
        if (this.onMovedListener != null) {
            this.onMovedListener.onMoved(visible(), this.zoom);
        }
        removeCallbacks(this.settled);
        postDelayed(this.settled, SETTLE_MS);
    }

    private void animateZoom(float target, float x, float y) {
        if (this.animator != null) {
            this.animator.cancel();
        }
        this.animator = ValueAnimator.ofFloat(this.zoom, target);
        this.animator.setDuration(250);
        this.animator.setInterpolator(new DecelerateInterpolator());
        this.animator.addUpdateListener(animation -> zoomAt((float) animation.getAnimatedValue(), x, y));
        this.animator.start();
    }
}
