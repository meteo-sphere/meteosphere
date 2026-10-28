package org.meteosphere.ui.quakes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AnimationUtils;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.meteosphere.R;

/**
 * A Mercator map of the world centered on Greenwich with the earthquakes as dots, their size and color by the
 * magnitude, a white ring on those of the last day, and the location as a blue dot. Pinching zooms, dragging moves, a
 * double tap comes closer and a tap picks the earthquake under it. Latitudes past 83 north and 70 south, where Mercator
 * swells, are cut off. Dots set anew grow in and shrink away, a pick rings its dot, and while a list is on its way
 * waves spread from the location.
 */
public class QuakeMapView extends View {

    private static final double TOP_LATITUDE = 83;
    private static final double BOTTOM_LATITUDE = -70;
    private static final float MAX_ZOOM = 8;

    /** Dots set anew are just there */
    public static final int MOTION_NONE = 0;
    /** The new dots grow in one after another, the oldest first, those of the last day with a wave; the gone shrink */
    public static final int MOTION_GROW = 1;
    /** The new dots grow in at once with waves spreading from them, as earthquakes just come; the gone shrink */
    public static final int MOTION_NEW = 2;

    private static final long GROW_MS = 380;
    // The new dots of a list come one after another within this time
    private static final long GROW_SPREAD_MS = 650;
    private static final long SHRINK_MS = 220;
    private static final long WAVE_MS = 950;
    private static final long WAVE_GAP_MS = 260;
    private static final long PICK_MS = 260;
    private static final long UNPICK_MS = 160;
    private static final long PICK_WAVE_MS = 700;
    private static final long ZOOM_MS = 320;
    private static final long PLACE_MS = 450;
    private static final long WAIT_EVERY_MS = 1100;
    private static final long WAIT_WAVE_MS = 1900;

    /** A dot: which earthquake and when, where, how strong and in what color, and whether it is of the last day */
    public static final class Dot {
        final String id;
        final long time;
        final double latitude;
        final double longitude;
        final double magnitude;
        final int color;
        final boolean isRecent;

        public Dot(String id, long time, double latitude, double longitude, double magnitude, int color, boolean isRecent) {
            this.id = id;
            this.time = time;
            this.latitude = latitude;
            this.longitude = longitude;
            this.magnitude = magnitude;
            this.color = color;
            this.isRecent = isRecent;
        }
    }

    // A dot as drawn: since when it grows in and with how many waves, 0 for there all along, and since when it goes
    private static final class Shown {
        final Dot dot;
        long comeAt;
        int waves;
        long goAt = Long.MAX_VALUE;

        Shown(Dot dot) {
            this.dot = dot;
        }
    }

    /** Told when the map leaves the whole world for a closer look, and when it comes back to it */
    public interface OnZoomListener {
        void onZoomChanged(boolean zoomedIn);
    }

    /** Told the index of the dot tapped, or -1 for a tap off the dots */
    public interface OnDotTappedListener {
        void onDotTapped(int index);
    }

    private final Paint oceanPaint = new Paint();
    private final Paint landPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path landPath = new Path();
    private final Path drawnPath = new Path();
    private final Matrix matrix = new Matrix();
    private final float density;
    private final int placeColor;
    private final int noPlaceWaveColor;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    // The land in map units: x 0..1 west to east, y 0..1 from TOP_LATITUDE down to BOTTOM_LATITUDE
    private float[][] land;
    private boolean isLandBuilt;
    // The ocean and the land as last drawn, kept while only the dots move; API 29 and up
    private LandLayer landLayer;
    // The dots as set, in the order of their indices
    private final List<Dot> dots = new ArrayList<>();
    // The dots drawn, those going away too, the weaker first so the stronger lie on top
    private final List<Shown> shown = new ArrayList<>();
    private boolean hasReference;
    private double referenceLatitude;
    private double referenceLongitude;
    // The location gliding from where it was, in map units, since placeAt
    private float placeFromX;
    private float placeFromY;
    private long placeAt;
    private int selected = -1;
    // The dot picked and since when, and the one picked before, letting go since unpickedAt
    private Dot picked;
    private long pickedAt;
    private Dot unpicked;
    private long unpickedAt;
    // Waves spreading from the location while a list is on its way: from waitSince until waitUntil, MAX_VALUE while
    // waiting
    private long waitSince;
    private long waitUntil;
    private OnDotTappedListener listener;
    private OnZoomListener zoomListener;
    private boolean wasZoomedIn;
    private float zoom = 1;
    // The map's top left corner on the view
    private float offsetX;
    private float offsetY;
    private boolean isFitted;
    // A zoom on its way since zoomAt: from and to which zoom and middle of the view in map units
    private long zoomAt;
    // A wave spreading from a still view's earthquake since epicenterWaveAt, 0 for none
    private long epicenterWaveAt;
    private long zoomMs;
    private float zoomFrom;
    private float zoomTo;
    private double centerFromX;
    private double centerFromY;
    private double centerToX;
    private double centerToY;
    private boolean isZoomToWorld;
    // A still view of one part of the world, wrapping across the date line, set by setView
    private boolean isFixedView;
    private double viewLatitude;
    private double viewLongitude;
    private double viewSpan;
    // The still view's zoom and middle in map units, found by findFixedView
    private float fixedZoom;
    private double fixedCenterX;
    private double fixedCenterY;
    // A compact map over a list: the whole width, a band of latitudes, taps only, see setCompact
    private boolean isCompact;
    private double compactTop;
    private double compactBottom;
    // Points the still view keeps in sight, with room around them: the epicenter and the location
    private double[] viewLatitudes;
    private double[] viewLongitudes;
    // The system's animation speed as the last motion began, 0 with animations off
    private float motionScale = 1;

    public QuakeMapView(Context context) {
        this(context, null);
    }

    public QuakeMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        this.oceanPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, 0xff1d2026));
        this.landPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xff3a3f48));
        this.landPaint.setStyle(Paint.Style.FILL);
        this.coastPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0xff4a505a));
        this.coastPaint.setStyle(Paint.Style.STROKE);
        this.coastPaint.setStrokeWidth(0.6f * this.density);
        this.ringPaint.setStyle(Paint.Style.STROKE);
        this.wavePaint.setStyle(Paint.Style.STROKE);
        this.selectionPaint.setStyle(Paint.Style.STROKE);
        this.placeColor = ContextCompat.getColor(context, R.color.quake_map_place);
        this.noPlaceWaveColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff888888);
        this.landPath.setFillType(Path.FillType.EVEN_ODD);
        this.scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        this.gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (isCompact && zoom <= 1) {
                    return false;
                }
                offsetX -= distanceX;
                offsetY -= distanceY;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoomInAt(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                select(dotAt(e.getX(), e.getY()), true);
                return true;
            }
        });
    }

    /** The rings of the land as longitude, latitude pairs in degrees */
    public void setLand(float[][] rings) {
        this.land = rings;
        // Now rather than in the first frame, which may be a screen's growing in
        buildLand();
        if (this.landLayer != null) {
            this.landLayer.forget();
        }
        invalidate();
    }

    /** The dots, none of them picked; the motion tells how the dots new and gone since the dots before show it */
    public void setDots(List<Dot> dots, int motion) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        this.motionScale = QuakeMotion.scale(getContext());
        boolean animate = motion != MOTION_NONE && this.motionScale > 0;
        Map<String, Shown> before = new HashMap<>();
        List<Shown> next = new ArrayList<>();
        for (Shown shown : this.shown) {
            if (shown.goAt == Long.MAX_VALUE) {
                before.put(shown.dot.id, shown);
            } else if (animate && progress(now, shown.goAt, SHRINK_MS) < 1) {
                // Still going away
                next.add(shown);
            }
        }
        List<Shown> coming = new ArrayList<>();
        for (Dot dot : dots) {
            Shown old = before.remove(dot.id);
            Shown shown = new Shown(dot);
            if (old != null) {
                // A dot kept goes on as it went, growing in yet if it was
                shown.comeAt = old.comeAt;
                shown.waves = old.waves;
            } else if (animate) {
                coming.add(shown);
            }
            next.add(shown);
        }
        if (animate) {
            coming.sort((a, b) -> Long.compare(a.dot.time, b.dot.time));
            for (int i = 0; i < coming.size(); i++) {
                Shown shown = coming.get(i);
                long delay = motion == MOTION_GROW && coming.size() > 1 ? GROW_SPREAD_MS * i / (coming.size() - 1) : 0;
                shown.comeAt = later(now, delay);
                shown.waves = motion == MOTION_NEW ? 3 : (shown.dot.isRecent ? 1 : 0);
            }
            for (Shown gone : before.values()) {
                gone.goAt = now;
                next.add(gone);
            }
        }
        next.sort((a, b) -> Double.compare(a.dot.magnitude, b.dot.magnitude));
        this.shown.clear();
        this.shown.addAll(next);
        this.dots.clear();
        this.dots.addAll(dots);
        this.selected = -1;
        pick(null, animate);
        invalidate();
    }

    /**
     * A still view centered on the point with the span of longitude across, the world repeated east and west so
     * any part of it can be in the middle; no zooming or dragging then, and a scroll around goes on past it
     */
    public void setView(double latitude, double longitude, double spanDegrees) {
        this.isFixedView = true;
        this.viewLatitude = latitude;
        this.viewLongitude = longitude;
        this.viewSpan = Math.max(10, Math.min(360, spanDegrees));
        this.isFitted = false;
        invalidate();
    }

    /**
     * A still view keeping the points in sight with room around, as close as that allows but showing at least
     * minSpanDegrees of longitude; the first point is the one the view is built around
     */
    public void showPoints(double[] latitudes, double[] longitudes, double minSpanDegrees) {
        this.viewLatitudes = latitudes;
        this.viewLongitudes = longitudes;
        setView(latitudes[0], longitudes[0], minSpanDegrees);
    }

    /** In a still view a wave spreads from its earthquake, as from a new earthquake's dot on the list's map; once */
    public void playEpicenterWave() {
        if (this.epicenterWaveAt != 0) {
            return;
        }
        this.motionScale = QuakeMotion.scale(getContext());
        if (this.motionScale > 0) {
            this.epicenterWaveAt = AnimationUtils.currentAnimationTimeMillis();
            invalidate();
        }
    }

    /**
     * The whole world across, between the latitudes up and down, as tall as that band is for the width: a map
     * that lies in a list. Showing the whole world it leaves one finger's drags to the list and the pager; pinching
     * or a double tap zooms it, and zoomed in it is dragged around as a map is. A tap picks a dot.
     */
    public void setCompact(double topLatitude, double bottomLatitude) {
        this.isCompact = true;
        this.compactTop = topLatitude;
        this.compactBottom = bottomLatitude;
        this.isFitted = false;
        requestLayout();
        invalidate();
    }

    /** The location as a blue dot, or none with null; gliding there from where it was with animate */
    public void setReference(Double latitude, Double longitude, boolean animate) {
        boolean has = latitude != null && longitude != null;
        long now = AnimationUtils.currentAnimationTimeMillis();
        if (has && this.hasReference && animate && (latitude != this.referenceLatitude || longitude != this.referenceLongitude)) {
            // From where it shows now, as it may be gliding yet
            float glide = QuakeMotion.EMPHASIZED.getInterpolation(progress(now, this.placeAt, PLACE_MS));
            this.placeFromX = this.placeFromX + (unitX(this.referenceLongitude) - this.placeFromX) * glide;
            this.placeFromY = this.placeFromY + (unitY(this.referenceLatitude) - this.placeFromY) * glide;
            this.motionScale = QuakeMotion.scale(getContext());
            this.placeAt = now;
        } else if (!has || !animate || !this.hasReference) {
            this.placeAt = 0;
        }
        this.hasReference = has;
        if (has) {
            this.referenceLatitude = latitude;
            this.referenceLongitude = longitude;
        }
        if (this.placeAt == 0) {
            this.placeFromX = has ? unitX(longitude) : 0;
            this.placeFromY = has ? unitY(latitude) : 0;
        }
        invalidate();
    }

    /** Waves spreading from the location, or from the middle without one, while a list is on its way */
    public void setWaiting(boolean waiting) {
        boolean isWaiting = this.waitUntil == Long.MAX_VALUE;
        if (waiting == isWaiting) {
            return;
        }
        long now = AnimationUtils.currentAnimationTimeMillis();
        if (waiting) {
            this.motionScale = QuakeMotion.scale(getContext());
            if (this.motionScale <= 0) {
                return;
            }
            this.waitSince = now;
            this.waitUntil = Long.MAX_VALUE;
        } else {
            // The waves out already spread to their end
            this.waitUntil = now;
        }
        invalidate();
    }

    public void setOnZoomListener(OnZoomListener listener) {
        this.zoomListener = listener;
    }

    /** Back to the whole world, gliding there */
    public void zoomOut() {
        if (getWidth() == 0) {
            showWorld();
            return;
        }
        float height = getWidth() * ASPECT;
        float top = this.isCompact ? -unitY(this.compactTop) * height : (getHeight() - height) / 2;
        animateZoom(1, 0.5, (getHeight() / 2f - top) / height, ZOOM_MS, true);
    }

    // At once, as a pinch ends there
    private void showWorld() {
        this.zoomAt = 0;
        this.zoom = 1;
        this.isFitted = false;
        invalidate();
        notifyZoom(false);
    }

    private void notifyZoom(boolean zoomedIn) {
        if (zoomedIn != this.wasZoomedIn) {
            this.wasZoomedIn = zoomedIn;
            if (this.zoomListener != null) {
                this.zoomListener.onZoomChanged(zoomedIn);
            }
        }
    }

    public void setOnDotTappedListener(OnDotTappedListener listener) {
        this.listener = listener;
    }

    /** Marks the dot, -1 for none, and tells the listener; its ring pops out with animate, else is just there */
    public void select(int index, boolean animate) {
        Dot dot = index >= 0 && index < this.dots.size() ? this.dots.get(index) : null;
        this.motionScale = QuakeMotion.scale(getContext());
        pick(dot, animate && this.motionScale > 0);
        this.selected = dot != null ? index : -1;
        invalidate();
        if (this.listener != null) {
            this.listener.onDotTapped(this.selected);
        }
    }

    // The ring goes to the dot, the one before letting go
    private void pick(Dot dot, boolean animate) {
        if (dot == this.picked) {
            return;
        }
        long now = AnimationUtils.currentAnimationTimeMillis();
        this.unpicked = animate ? this.picked : null;
        this.unpickedAt = now;
        this.picked = dot;
        this.pickedAt = animate ? now : 0;
    }

    private static double mercator(double latitude) {
        return Math.log(Math.tan(Math.PI / 4 + Math.toRadians(latitude) / 2));
    }

    private static final double TOP = mercator(TOP_LATITUDE);
    private static final double BOTTOM = mercator(BOTTOM_LATITUDE);
    // Height of the map for a width of 1
    private static final float ASPECT = (float) ((TOP - BOTTOM) / (2 * Math.PI));

    private static float unitX(double longitude) {
        return (float) ((longitude + 180) / 360);
    }

    private static float unitY(double latitude) {
        double clamped = Math.max(BOTTOM_LATITUDE, Math.min(TOP_LATITUDE, latitude));
        return (float) ((TOP - mercator(clamped)) / (TOP - BOTTOM));
    }

    private float mapWidth() {
        return getWidth() * this.zoom;
    }

    private float mapHeight() {
        return mapWidth() * ASPECT;
    }

    // In a still view the copy of the world nearest the middle
    private float screenX(double longitude) {
        float x = this.offsetX + unitX(longitude) * mapWidth();
        if (this.isFixedView) {
            float middle = getWidth() / 2f;
            while (x - middle > mapWidth() / 2) {
                x -= mapWidth();
            }
            while (middle - x > mapWidth() / 2) {
                x += mapWidth();
            }
        }
        return x;
    }

    private float screenY(double latitude) {
        return this.offsetY + unitY(latitude) * mapHeight();
    }

    // How far a motion of the time made since the start has gone, 0 to 1; a start of 0 is a motion long done
    private float progress(long now, long start, long ms) {
        if (start == 0) {
            return 1;
        }
        float duration = ms * this.motionScale;
        if (duration <= 0) {
            return now >= start ? 1 : 0;
        }
        return Math.max(0, Math.min(1, (now - start) / duration));
    }

    // A time the motion's own ms after the moment, at the system's animation speed
    private long later(long moment, long ms) {
        return moment + (long) (ms * this.motionScale);
    }

    // As tall as the map is for the width, unless told a height
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width * ASPECT * (this.isCompact ? unitY(this.compactBottom) - unitY(this.compactTop) : 1));
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.isFitted = false;
    }

    // The whole width at first, centered up and down
    private void fit() {
        if (this.isCompact) {
            this.zoom = 1;
            this.offsetX = 0;
            this.offsetY = -unitY(this.compactTop) * mapHeight();
        } else if (this.isFixedView) {
            findFixedView();
            showCenter(this.fixedZoom, this.fixedCenterX, this.fixedCenterY);
        } else {
            this.zoom = 1;
            this.offsetX = 0;
            this.offsetY = (getHeight() - mapHeight()) / 2;
        }
        this.isFitted = true;
    }

    // The still view's zoom and middle: the span asked for, closer when the points are near
    private void findFixedView() {
        float zoom = (float) (360 / this.viewSpan);
        double centerX = unitX(this.viewLongitude);
        double centerY = unitY(this.viewLatitude);
        if (this.viewLatitudes != null && this.viewLatitudes.length > 1) {
            // From the first point the others the shorter way round, in map units of a width of 1
            double minX = 0;
            double maxX = 0;
            double minY = unitY(this.viewLatitudes[0]);
            double maxY = minY;
            for (int i = 1; i < this.viewLatitudes.length; i++) {
                double dx = (((this.viewLongitudes[i] - this.viewLongitudes[0]) + 540) % 360 - 180) / 360;
                minX = Math.min(minX, dx);
                maxX = Math.max(maxX, dx);
                double y = unitY(this.viewLatitudes[i]);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            // Room at the sides and at the top for the dots
            float margin = 36 * this.density;
            float zoomX = (float) ((getWidth() - 2 * margin) / Math.max(1e-6, (maxX - minX) * getWidth()));
            float zoomY = (float) ((getHeight() - 2 * margin) / Math.max(1e-6, (maxY - minY) * getWidth() * ASPECT));
            zoom = Math.max(1, Math.min(zoom, Math.min(zoomX, zoomY)));
            centerX = unitX(this.viewLongitudes[0]) + (minX + maxX) / 2;
            centerY = (minY + maxY) / 2;
        }
        this.fixedZoom = zoom;
        this.fixedCenterX = centerX;
        this.fixedCenterY = centerY;
    }

    // The zoom with the point of map units in the middle of the view, the map kept inside as each view keeps it
    private void showCenter(float zoom, double centerX, double centerY) {
        this.zoom = zoom;
        this.offsetX = (float) (getWidth() / 2f - centerX * mapWidth());
        this.offsetY = (float) (getHeight() / 2f - centerY * mapHeight());
        if (!this.isFixedView) {
            clamp();
        } else if (mapHeight() <= getHeight()) {
            // A still view goes on east and west; up and down it stays inside, or centered while shorter
            this.offsetY = (getHeight() - mapHeight()) / 2;
        } else {
            this.offsetY = Math.min(0, Math.max(getHeight() - mapHeight(), this.offsetY));
        }
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        this.zoomAt = 0;
        float newZoom = Math.max(1, Math.min(MAX_ZOOM, this.zoom * factor));
        if (this.isCompact && newZoom <= 1.001f) {
            // Out to the whole world: its band again, as the list shows it
            showWorld();
            return;
        }
        float applied = newZoom / this.zoom;
        this.offsetX = focusX - (focusX - this.offsetX) * applied;
        this.offsetY = focusY - (focusY - this.offsetY) * applied;
        this.zoom = newZoom;
        clamp();
        invalidate();
        notifyZoom(this.zoom > 1);
    }

    // Twice as close around the point, gliding there; the point stays under the finger as the map stays inside
    private void zoomInAt(float focusX, float focusY) {
        float to = Math.min(MAX_ZOOM, this.zoom * 2);
        if (to <= this.zoom) {
            return;
        }
        float width = getWidth() * to;
        float height = width * ASPECT;
        float x = Math.min(0, Math.max(getWidth() - width, focusX - (focusX - this.offsetX) * to / this.zoom));
        float y = height <= getHeight() ? (getHeight() - height) / 2
                : Math.min(0, Math.max(getHeight() - height, focusY - (focusY - this.offsetY) * to / this.zoom));
        animateZoom(to, (getWidth() / 2f - x) / width, (getHeight() / 2f - y) / height, ZOOM_MS, false);
    }

    // The zoom and the middle of the view in map units glide there, the zoom evenly in its ratio
    private void animateZoom(float toZoom, double toCenterX, double toCenterY, long ms, boolean toWorld) {
        this.motionScale = QuakeMotion.scale(getContext());
        this.zoomFrom = this.zoom;
        this.centerFromX = (getWidth() / 2f - this.offsetX) / mapWidth();
        this.centerFromY = (getHeight() / 2f - this.offsetY) / mapHeight();
        this.zoomTo = toZoom;
        this.centerToX = toCenterX;
        this.centerToY = toCenterY;
        this.zoomMs = ms;
        this.isZoomToWorld = toWorld;
        this.zoomAt = AnimationUtils.currentAnimationTimeMillis();
        notifyZoom(toZoom > 1);
        invalidate();
    }

    // A zoom on its way moved on to the moment; true while it goes on
    private boolean stepZoom(long now) {
        if (this.zoomAt == 0) {
            return false;
        }
        float t = progress(now, this.zoomAt, this.zoomMs);
        float e = QuakeMotion.EMPHASIZED.getInterpolation(t);
        showCenter((float) (this.zoomFrom * Math.pow(this.zoomTo / this.zoomFrom, e)),
                this.centerFromX + (this.centerToX - this.centerFromX) * e, this.centerFromY + (this.centerToY - this.centerFromY) * e);
        if (t < 1) {
            return true;
        }
        this.zoomAt = 0;
        if (this.isZoomToWorld) {
            // The whole world exactly as it was fitted
            this.isFitted = false;
        }
        return false;
    }

    // The map covers the view across; up and down it stays inside, or centered while shorter than the view
    private void clamp() {
        this.offsetX = Math.min(0, Math.max(getWidth() - mapWidth(), this.offsetX));
        if (mapHeight() <= getHeight()) {
            this.offsetY = (getHeight() - mapHeight()) / 2;
        } else {
            this.offsetY = Math.min(0, Math.max(getHeight() - mapHeight(), this.offsetY));
        }
    }

    // The land in map units, once
    private void buildLand() {
        this.landPath.reset();
        if (this.land != null) {
            for (float[] ring : this.land) {
                for (int i = 0; i < ring.length; i += 2) {
                    float x = unitX(ring[i]);
                    float y = unitY(ring[i + 1]);
                    if (i == 0) {
                        this.landPath.moveTo(x, y);
                    } else {
                        this.landPath.lineTo(x, y);
                    }
                }
                this.landPath.close();
            }
        }
        this.isLandBuilt = true;
    }

    private float radius(double magnitude) {
        return (3 + (float) Math.max(0, magnitude - 5) * 3.5f) * this.density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = AnimationUtils.currentAnimationTimeMillis();
        boolean moving = stepZoom(now);
        if (!this.isFitted) {
            fit();
        }
        if (!this.isLandBuilt) {
            buildLand();
        }
        // The land drawn once into a layer while the map stands, so moving dots don't draw it again; moving, it is
        // drawn as it goes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
            if (this.landLayer == null) {
                this.landLayer = new LandLayer();
            }
            this.landLayer.draw(this, canvas, this.zoomAt == 0 && !(this.isCompact && this.zoom > 1));
        } else {
            drawLand(canvas);
        }
        moving |= drawDots(canvas, now);
        moving |= drawPicked(canvas, now);
        moving |= drawWaiting(canvas, now);
        moving |= drawPlace(canvas, now);
        if (moving) {
            postInvalidateOnAnimation();
        }
    }

    // The ocean and the land; a still view repeats the world east and west of it, so the middle can be anywhere
    private void drawLand(Canvas canvas) {
        int copies = this.isFixedView ? 1 : 0;
        float left = this.isFixedView ? 0 : this.offsetX;
        float right = this.isFixedView ? getWidth() : this.offsetX + mapWidth();
        canvas.drawRect(left, this.offsetY, right, this.offsetY + mapHeight(), this.oceanPaint);
        for (int copy = -copies; copy <= copies; copy++) {
            this.matrix.setScale(mapWidth(), mapHeight());
            this.matrix.postTranslate(this.offsetX + copy * mapWidth(), this.offsetY);
            this.landPath.transform(this.matrix, this.drawnPath);
            canvas.drawPath(this.drawnPath, this.landPaint);
            canvas.drawPath(this.drawnPath, this.coastPaint);
        }
    }

    // The dots as they grow in, stand and shrink away, with their waves; true while any of them moves
    private boolean drawDots(Canvas canvas, long now) {
        boolean moving = false;
        int outline = this.oceanPaint.getColor();
        for (int i = this.shown.size() - 1; i >= 0; i--) {
            if (progress(now, this.shown.get(i).goAt, SHRINK_MS) >= 1) {
                this.shown.remove(i);
            }
        }
        for (Shown shown : this.shown) {
            Dot dot = shown.dot;
            float come = progress(now, shown.comeAt, GROW_MS);
            float go = progress(now, shown.goAt, SHRINK_MS);
            moving |= come < 1 || shown.goAt != Long.MAX_VALUE;
            float x = screenX(dot.longitude);
            float y = screenY(dot.latitude);
            float r = radius(dot.magnitude);
            if (this.isFixedView) {
                // The one earthquake of a still view: larger, with rings around it, and a wave from it once
                r = Math.max(r, 6 * this.density);
                drawWave(canvas, x, y, r * 2, dot.color, 150 / 255f, 1.5f);
                drawWave(canvas, x, y, r * 3, dot.color, 80 / 255f, 1.5f);
                float wave = progress(now, this.epicenterWaveAt == 0 ? 0 : later(this.epicenterWaveAt, 80), WAVE_MS);
                moving |= wave < 1;
                if (wave > 0 && wave < 1) {
                    drawWave(canvas, x, y, r + QuakeMotion.DECELERATE.getInterpolation(wave) * 26 * this.density, dot.color,
                            0.75f * (1 - wave), 2);
                }
            }
            for (int k = 0; k < shown.waves; k++) {
                float wave = progress(now, later(shown.comeAt, 80 + k * WAVE_GAP_MS), WAVE_MS);
                moving |= wave < 1;
                if (wave > 0 && wave < 1) {
                    drawWave(canvas, x, y, r + QuakeMotion.DECELERATE.getInterpolation(wave) * 26 * this.density, dot.color,
                            0.75f * (1 - wave), 2);
                }
            }
            if (come <= 0) {
                continue;
            }
            float size = QuakeMotion.POP.getInterpolation(come) * (1 - QuakeMotion.ACCELERATE.getInterpolation(go));
            int alpha = Math.round(255 * Math.min(1, come * 3) * (1 - go));
            this.dotPaint.setColor(dot.color);
            this.dotPaint.setAlpha(alpha);
            canvas.drawCircle(x, y, r * size, this.dotPaint);
            this.ringPaint.setColor(dot.isRecent ? 0xffffffff : outline);
            this.ringPaint.setAlpha(alpha);
            this.ringPaint.setStrokeWidth((dot.isRecent ? 1.5f : 1) * this.density);
            canvas.drawCircle(x, y, r * size, this.ringPaint);
        }
        return moving;
    }

    private void drawWave(Canvas canvas, float x, float y, float radius, int color, float alpha, float strokeDp) {
        this.wavePaint.setColor(color);
        this.wavePaint.setAlpha(Math.round(255 * alpha));
        this.wavePaint.setStrokeWidth(strokeDp * this.density);
        canvas.drawCircle(x, y, radius, this.wavePaint);
    }

    // The ring of the dot picked popping out with a wave, the one let go shrinking into its dot
    private boolean drawPicked(Canvas canvas, long now) {
        boolean moving = false;
        if (this.unpicked != null) {
            float t = progress(now, this.unpickedAt, UNPICK_MS);
            if (t < 1) {
                moving = true;
                float size = 1 - QuakeMotion.ACCELERATE.getInterpolation(t);
                drawPickRing(canvas, this.unpicked, size, size);
            } else {
                this.unpicked = null;
            }
        }
        if (this.picked != null) {
            float t = progress(now, this.pickedAt, PICK_MS);
            float wave = progress(now, this.pickedAt, PICK_WAVE_MS);
            moving |= wave < 1;
            drawPickRing(canvas, this.picked, QuakeMotion.POP.getInterpolation(t), Math.min(1, t * 2));
            if (wave < 1) {
                float r = radius(this.picked.magnitude) + 4 * this.density;
                drawWave(canvas, screenX(this.picked.longitude), screenY(this.picked.latitude),
                        r + QuakeMotion.DECELERATE.getInterpolation(wave) * 22 * this.density, this.picked.color, 0.7f * (1 - wave), 2);
            }
        }
        return moving;
    }

    // A ring in the dot's colour with the map showing between, from the dot's edge at 0 to its place at 1
    private void drawPickRing(Canvas canvas, Dot dot, float size, float alpha) {
        this.selectionPaint.setColor(dot.color);
        this.selectionPaint.setAlpha(Math.round(255 * Math.max(0, Math.min(1, alpha))));
        this.selectionPaint.setStrokeWidth(2.4f * this.density);
        canvas.drawCircle(screenX(dot.longitude), screenY(dot.latitude), radius(dot.magnitude) + 4.6f * this.density * size,
                this.selectionPaint);
    }

    // The waves of a list on its way; true while they spread
    private boolean drawWaiting(Canvas canvas, long now) {
        if (this.waitSince == 0) {
            return false;
        }
        double every = WAIT_EVERY_MS * this.motionScale;
        double life = WAIT_WAVE_MS * this.motionScale;
        if (every <= 0) {
            // Animations turned off meanwhile
            this.waitSince = 0;
            return false;
        }
        boolean moving = this.waitUntil == Long.MAX_VALUE;
        float x = this.hasReference ? placeX(now) : getWidth() / 2f;
        float y = this.hasReference ? placeY(now) : getHeight() / 2f;
        long last = Math.min(now, this.waitUntil);
        for (long k = Math.max(0, (long) Math.ceil((now - this.waitSince - life) / every)); this.waitSince + k * every <= last; k++) {
            double t = (now - this.waitSince - k * every) / life;
            if (t < 0 || t >= 1) {
                continue;
            }
            moving = true;
            float spread = QuakeMotion.DECELERATE.getInterpolation((float) t);
            drawWave(canvas, x, y, (6 + 50 * spread) * this.density, this.hasReference ? this.placeColor : this.noPlaceWaveColor,
                    0.55f * (1 - (float) t), 1.5f);
        }
        if (!moving) {
            this.waitSince = 0;
        }
        return moving;
    }

    // The location: a blue dot in a white ring and a pale halo, gliding to a new place
    private boolean drawPlace(Canvas canvas, long now) {
        if (!this.hasReference) {
            return false;
        }
        boolean moving = progress(now, this.placeAt, PLACE_MS) < 1;
        float x = placeX(now);
        float y = placeY(now);
        float size = this.density;
        this.placePaint.setColor(this.placeColor);
        this.placePaint.setAlpha(46);
        canvas.drawCircle(x, y, 12 * size, this.placePaint);
        this.placePaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, 6.5f * size, this.placePaint);
        this.placePaint.setColor(this.placeColor);
        canvas.drawCircle(x, y, 4.8f * size, this.placePaint);
        return moving;
    }

    private float placeX(long now) {
        float glide = QuakeMotion.EMPHASIZED.getInterpolation(progress(now, this.placeAt, PLACE_MS));
        if (glide >= 1) {
            return screenX(this.referenceLongitude);
        }
        return this.offsetX + (this.placeFromX + (unitX(this.referenceLongitude) - this.placeFromX) * glide) * mapWidth();
    }

    private float placeY(long now) {
        float glide = QuakeMotion.EMPHASIZED.getInterpolation(progress(now, this.placeAt, PLACE_MS));
        if (glide >= 1) {
            return screenY(this.referenceLatitude);
        }
        return this.offsetY + (this.placeFromY + (unitY(this.referenceLatitude) - this.placeFromY) * glide) * mapHeight();
    }

    // The nearest dot within reach of the finger, -1 for none
    private int dotAt(float x, float y) {
        int best = -1;
        float bestDistance = 24 * this.density;
        for (int i = 0; i < this.dots.size(); i++) {
            Dot dot = this.dots.get(i);
            float distance = (float) Math.hypot(screenX(dot.longitude) - x, screenY(dot.latitude) - y) - radius(dot.magnitude);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.isFixedView) {
            return super.onTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && this.zoomAt != 0) {
            // A finger stops a glide where it is
            this.zoomAt = 0;
            notifyZoom(this.zoom > 1);
        }
        if (this.isCompact) {
            // Zoomed in, or pinching, the map keeps the gesture; showing the whole world the list and the pager
            // take one finger's drags
            if ((action == MotionEvent.ACTION_DOWN && this.zoom > 1) || action == MotionEvent.ACTION_POINTER_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            boolean handled = this.scaleDetector.onTouchEvent(event);
            handled |= this.gestureDetector.onTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return handled || super.onTouchEvent(event);
        }
        // The pager and the list don't take the drags of the map
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = this.scaleDetector.onTouchEvent(event);
        handled |= this.gestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return handled || super.onTouchEvent(event);
    }

    // The ocean and the land recorded for the view of the map as it is, drawn again from that while the view stays,
    // through a layer of their own while the map stands still
    @RequiresApi(Build.VERSION_CODES.Q)
    private static final class LandLayer {
        private final RenderNode node = new RenderNode("land");
        private float zoom = -1;
        private float x;
        private float y;
        private int width;
        private int height;

        void forget() {
            this.zoom = -1;
        }

        void draw(QuakeMapView map, Canvas canvas, boolean still) {
            // The system wipes the recording while the window is hidden, as under another screen
            if (!this.node.hasDisplayList() || this.zoom != map.zoom || this.x != map.offsetX || this.y != map.offsetY || this.width != map.getWidth()
                    || this.height != map.getHeight()) {
                this.node.setPosition(0, 0, map.getWidth(), map.getHeight());
                RecordingCanvas recording = this.node.beginRecording();
                try {
                    map.drawLand(recording);
                } finally {
                    this.node.endRecording();
                }
                this.zoom = map.zoom;
                this.x = map.offsetX;
                this.y = map.offsetY;
                this.width = map.getWidth();
                this.height = map.getHeight();
            }
            this.node.setUseCompositingLayer(still, null);
            canvas.drawRenderNode(this.node);
        }
    }
}
