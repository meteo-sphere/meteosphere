package org.meteosphere.ui.quakes;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.meteosphere.R;
import org.meteosphere.data.quake.Earthquake;

/**
 * The map card of the earthquakes' list: the earthquakes as dots on the world, and the one of the dot tapped under it,
 * its line opening and closing as the dot is picked and let go
 */
final class EarthquakeMapHolder extends RecyclerView.ViewHolder {

    private static final long OPEN_MS = 260;
    private static final long CLOSE_MS = 200;

    private final QuakeMapView map;
    private final View selectedRow;
    private final EarthquakeListAdapter list;
    // The earthquake in the line under the map
    private Earthquake shownEarthquake;
    // The line under the map opening or closing, and where it goes
    private ValueAnimator rowAnimator;
    private boolean isRowShown;
    // A pick put back, shown at once
    private boolean isRepicking;

    EarthquakeMapHolder(View view, EarthquakeListAdapter list) {
        super(view);
        this.list = list;
        ((ViewGroup) view).setClipToOutline(true);
        this.map = view.findViewById(R.id.quakeMapCompact);
        // The whole north the map has, where the ridge past Svalbard shakes, and the south down past the
        // South Sandwich and South Shetland Islands: only Antarctica's coast is left out
        this.map.setCompact(83, -64);
        // Its land is a long path: showing the whole world it is drawn once into a layer, a scroll of the list only
        // moves the layer; zoomed in it is drawn as it moves. From Android 10 the map keeps such a layer of the land
        // alone, so its dots move without drawing the land again
        boolean ownLayer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        if (!ownLayer) {
            this.map.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        View world = view.findViewById(R.id.btnQuakeMapWorld);
        world.setOnClickListener(v -> this.map.zoomOut());
        this.map.setOnZoomListener(zoomedIn -> {
            showWorldButton(world, zoomedIn);
            if (!ownLayer) {
                this.map.setLayerType(zoomedIn ? View.LAYER_TYPE_NONE : View.LAYER_TYPE_HARDWARE, null);
            }
        });
        this.map.setLand(WorldLand.get(view.getContext()));
        this.selectedRow = view.findViewById(R.id.rowQuakeMapCompactSelected);
        this.map.setOnDotTappedListener(index -> showSelection(index, !this.isRepicking));
        buildLegend(view.findViewById(R.id.llQuakeLegendCompact));
    }

    /** The list's dots and place, moving from those before as the list's motion says */
    void bind(int motion) {
        boolean moving = motion != EarthquakeListAdapter.MOTION_NONE;
        // The same earthquakes in another order or from another place stand as they are
        int dots = motion == EarthquakeListAdapter.MOTION_NEW ? QuakeMapView.MOTION_NEW
                : (motion == EarthquakeListAdapter.MOTION_GROW ? QuakeMapView.MOTION_GROW : QuakeMapView.MOTION_NONE);
        this.map.setDots(mapDots(this.list.earthquakes), dots);
        this.map.setReference(this.list.hasReference ? this.list.referenceLatitude : null,
                this.list.hasReference ? this.list.referenceLongitude : null, moving);
        this.map.setWaiting(this.list.isWaiting() && this.list.isLoading());
        // The earthquake picked before a tab was made again picked as it was, its line there at once
        String repick = this.list.earthquakes.isEmpty() ? null : this.list.takeRepick();
        int index = repick == null ? -1 : indexOf(repick);
        if (index >= 0) {
            this.isRepicking = true;
            this.map.select(index, false);
            this.isRepicking = false;
        } else {
            showSelection(-1, moving);
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < this.list.earthquakes.size(); i++) {
            if (id.equals(this.list.earthquakes.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private void showSelection(int index, boolean animate) {
        boolean hasSelection = index >= 0 && index < this.list.earthquakes.size();
        View row = this.selectedRow;
        if (hasSelection) {
            Earthquake earthquake = this.list.earthquakes.get(index);
            boolean isOther = earthquake != this.shownEarthquake;
            this.shownEarthquake = earthquake;
            if (isOther && animate && this.isRowShown && this.rowAnimator == null && QuakeMotion.isOn(row.getContext())) {
                // Another earthquake in the line already open: the one before fades out, the new one in
                row.animate().cancel();
                row.animate().alpha(0).setStartDelay(0).setDuration(90).setInterpolator(QuakeMotion.ACCELERATE).withEndAction(() -> {
                    EarthquakeSummary.row(row, earthquake, this.list, System.currentTimeMillis());
                    row.animate().alpha(1).setDuration(180).setInterpolator(QuakeMotion.DECELERATE).start();
                }).start();
            } else if (isOther || !this.isRowShown) {
                EarthquakeSummary.row(row, earthquake, this.list, System.currentTimeMillis());
            }
        } else {
            this.shownEarthquake = null;
        }
        this.list.setPicked(hasSelection ? this.shownEarthquake.id : null);
        showRow(hasSelection, animate);
    }

    // The line under the map opening to its height and fading in, or closing; the list under the card follows
    private void showRow(boolean show, boolean animate) {
        if (show == this.isRowShown) {
            return;
        }
        this.isRowShown = show;
        View row = this.selectedRow;
        row.animate().cancel();
        if (this.rowAnimator != null) {
            this.rowAnimator.cancel();
        }
        ViewGroup.LayoutParams params = row.getLayoutParams();
        int width = ((View) row.getParent()).getWidth();
        if (!animate || width == 0 || !QuakeMotion.isOn(row.getContext())) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            row.setAlpha(1);
            row.setVisibility(show ? View.VISIBLE : View.GONE);
            row.requestLayout();
            return;
        }
        if (row.getVisibility() != View.VISIBLE) {
            row.setAlpha(0);
        }
        int from = row.getVisibility() == View.VISIBLE ? row.getHeight() : 0;
        int to = 0;
        if (show) {
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            to = row.getMeasuredHeight();
        }
        float fromAlpha = row.getAlpha();
        float toAlpha = show ? 1 : 0;
        int toHeight = to;
        row.setVisibility(View.VISIBLE);
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(show ? OPEN_MS : CLOSE_MS);
        animator.setInterpolator(show ? QuakeMotion.DECELERATE : QuakeMotion.EMPHASIZED);
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            params.height = Math.round(from + (toHeight - from) * value);
            row.setAlpha(fromAlpha + (toAlpha - fromAlpha) * value);
            row.requestLayout();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean isCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                this.isCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (rowAnimator == animation) {
                    rowAnimator = null;
                }
                if (!this.isCancelled) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    row.setAlpha(1);
                    row.setVisibility(show ? View.VISIBLE : View.GONE);
                    row.requestLayout();
                }
            }
        });
        this.rowAnimator = animator;
        animator.start();
    }

    // Popping in as the map leaves the whole world, shrinking away as it comes back
    private static void showWorldButton(View button, boolean show) {
        button.animate().cancel();
        if (show) {
            if (button.getVisibility() != View.VISIBLE) {
                button.setAlpha(0);
                button.setScaleX(0.6f);
                button.setScaleY(0.6f);
                button.setVisibility(View.VISIBLE);
            }
            button.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(0).setDuration(220).setInterpolator(QuakeMotion.POP).start();
        } else if (button.getVisibility() == View.VISIBLE) {
            button.animate().alpha(0).scaleX(0.6f).scaleY(0.6f).setStartDelay(0).setDuration(150).setInterpolator(QuakeMotion.ACCELERATE)
                    .withEndAction(() -> button.setVisibility(View.GONE)).start();
        }
    }

    private static List<QuakeMapView.Dot> mapDots(List<Earthquake> earthquakes) {
        long now = System.currentTimeMillis();
        List<QuakeMapView.Dot> dots = new ArrayList<>();
        for (Earthquake earthquake : earthquakes) {
            dots.add(new QuakeMapView.Dot(earthquake.id, earthquake.time, earthquake.latitude, earthquake.longitude, earthquake.magnitude,
                    EarthquakeFormatter.magnitudeColor(earthquake.magnitude), EarthquakeFormatter.isRecent(earthquake.time, now)));
        }
        return dots;
    }

    // "● 5+  ● 6+  ● 7+  ○ past day", the dots as large as on the map
    private static void buildLegend(LinearLayout legend) {
        Context context = legend.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        double[] magnitudes = {5, 6, 7};
        for (double magnitude : magnitudes) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(EarthquakeFormatter.magnitudeColor(magnitude));
            int size = Math.round((3 + (float) (magnitude - 5) * 3.5f) * 2 * density);
            dot.setSize(size, size);
            legend.addView(legendItem(context, dot, String.format(Locale.getDefault(), "%d+", (int) magnitude)));
        }
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(Math.round(1.5f * density), MaterialColors.getColor(legend, com.google.android.material.R.attr.colorOnSurface));
        int size = Math.round(10 * density);
        ring.setSize(size, size);
        legend.addView(legendItem(context, ring, context.getString(R.string.earthquakes_legend_day)));
    }

    private static TextView legendItem(Context context, Drawable icon, String text) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView item = new TextView(context);
        item.setText(text);
        item.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        item.setTextColor(MaterialColors.getColor(item, com.google.android.material.R.attr.colorOnSurfaceVariant));
        item.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        item.setCompoundDrawablePadding(Math.round(5 * density));
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Math.round(16 * density));
        item.setLayoutParams(params);
        return item;
    }
}
