package org.meteosphere.ui.quakes;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.meteosphere.R;
import org.meteosphere.data.quake.Earthquake;

/**
 * The earthquakes' list: the map card and the summary card, then the earthquakes, under the titles of their days when
 * in time order; the map over the list's skeleton while it is on its way. A tap on one opens it. A list set anew
 * moves into place as its motion says.
 */
final class EarthquakeListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int TYPE_MAP = 3;
    static final int TYPE_SUMMARY = 0;
    static final int TYPE_DAY = 1;
    static final int TYPE_QUAKE = 2;
    static final int TYPE_SKELETON = 4;

    /** The list just there */
    static final int MOTION_NONE = 0;
    /** What is new comes one after another and what is gone goes, as the list first comes or changes its period */
    static final int MOTION_GROW = 1;
    /** The earthquakes new since the list before come with waves on the map, as a refresh brings them */
    static final int MOTION_NEW = 2;
    /** The same earthquakes in another order: the old lines fade out as the new ones fade in */
    static final int MOTION_REORDER = 3;
    /** Another place: the distances change in their lines and the place glides on the map */
    static final int MOTION_PLACE = 4;

    // The cards over the earthquakes, and the skeleton of a list on its way
    private static final Object MAP = new Object();
    private static final Object SUMMARY = new Object();
    private static final Object SKELETON = new Object();
    // A line kept, bound again where it is
    private static final Object PAYLOAD = new Object();

    final EarthquakeFormatter formatter;
    private final Consumer<Earthquake> onOpen;
    // The earthquakes in the order shown, the map's dots in the same order, and their period in days
    List<Earthquake> earthquakes = new ArrayList<>();
    int days;
    // Where the distances are from, if anywhere
    boolean hasReference;
    double referenceLatitude;
    double referenceLongitude;
    // The cards, the day starts of the titles and the earthquakes, in the order shown
    private final List<Object> items = new ArrayList<>();
    private boolean byDay;
    // No list to show yet: the map over the skeleton, its waves and sheen only while a list is on its way
    private boolean isWaiting;
    private boolean isLoading;
    // The earthquake picked on the map, and the one to pick again as the list first comes, as in a tab made again
    private String pickedId;
    private String repickId;
    // How the list set last moves into place; the map takes it once
    private int motion;
    private int mapMotion;

    EarthquakeListAdapter(EarthquakeFormatter formatter, Consumer<Earthquake> onOpen) {
        this.formatter = formatter;
        this.onOpen = onOpen;
    }

    /** Where the distances are from, none when has is false */
    void setReference(boolean has, double latitude, double longitude) {
        this.hasReference = has;
        this.referenceLatitude = latitude;
        this.referenceLongitude = longitude;
    }

    void open(Earthquake earthquake) {
        this.onOpen.accept(earthquake);
    }

    /** With no earthquakes to show the next list is the map waiting over the skeleton, not nothing */
    void setWaiting(boolean waiting) {
        this.isWaiting = waiting;
    }

    boolean isWaiting() {
        return this.isWaiting && this.earthquakes.isEmpty();
    }

    boolean isShowingSkeleton() {
        return this.items.contains(SKELETON);
    }

    /** A list on its way: the waiting map's waves and the skeleton's sheen move only then */
    void setLoading(boolean loading) {
        if (loading == this.isLoading) {
            return;
        }
        this.isLoading = loading;
        if (isWaiting()) {
            notifyItemRangeChanged(0, this.items.size(), PAYLOAD);
        }
    }

    boolean isLoading() {
        return this.isLoading;
    }

    /** The earthquake whose line shows under the map, null for none */
    void setPicked(String id) {
        this.pickedId = id;
    }

    String pickedId() {
        return this.pickedId;
    }

    /** The earthquake to pick again, without motion, once a list is there */
    void repick(String id) {
        this.repickId = id;
    }

    /** The earthquake to pick again, once */
    String takeRepick() {
        String id = this.repickId;
        this.repickId = null;
        return id;
    }

    /** How the map moves to the list set last, once */
    int takeMapMotion() {
        int motion = this.mapMotion;
        this.mapMotion = MOTION_NONE;
        return motion;
    }

    private static final class QuakeHolder extends RecyclerView.ViewHolder {
        final TextView tvMagnitude;
        final TextView tvPlace;
        final TextView tvTime;
        final TextView tvDepth;
        final TextView tvAgo;
        final TextView tvDistance;

        // Which corners of its day's card the row's press keeps to: -1 none set yet
        private int shape = -1;

        /** The row's press rounded where it ends its day's card */
        void setGroupShape(boolean hasAbove, boolean hasBelow) {
            int shape = (hasAbove ? 1 : 0) | (hasBelow ? 2 : 0);
            if (shape == this.shape) {
                return;
            }
            this.shape = shape;
            float radius = 24 * this.itemView.getResources().getDisplayMetrics().density;
            float top = hasAbove ? 0 : radius;
            float bottom = hasBelow ? 0 : radius;
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xffffffff);
            mask.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
            int ripple = MaterialColors.getColor(this.itemView, androidx.appcompat.R.attr.colorControlHighlight);
            this.itemView.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(ripple), null, mask));
        }

        QuakeHolder(View view) {
            super(view);
            this.tvMagnitude = view.findViewById(R.id.tvMagnitude);
            this.tvPlace = view.findViewById(R.id.tvPlace);
            this.tvTime = view.findViewById(R.id.tvTime);
            this.tvDepth = view.findViewById(R.id.tvDepth);
            this.tvAgo = view.findViewById(R.id.tvAgo);
            this.tvDistance = view.findViewById(R.id.tvDistance);
        }
    }

    /**
     * The earthquakes in the order shown, under the titles of their days or not, of the period in days; the lines and
     * the map move from the list before as the motion says
     */
    void setEarthquakes(List<Earthquake> earthquakes, boolean byDay, int days, int motion) {
        List<Object> before = new ArrayList<>(this.items);
        this.earthquakes = earthquakes;
        this.byDay = byDay;
        this.days = days;
        this.items.clear();
        if (!earthquakes.isEmpty()) {
            this.items.add(MAP);
            this.items.add(SUMMARY);
        } else if (this.isWaiting) {
            this.items.add(MAP);
            this.items.add(SKELETON);
        }
        long day = Long.MIN_VALUE;
        for (Earthquake earthquake : earthquakes) {
            long start = EarthquakeFormatter.dayStart(earthquake.time);
            if (byDay && start != day) {
                this.items.add(start);
                day = start;
            }
            this.items.add(earthquake);
        }
        this.motion = motion;
        this.mapMotion = motion;
        if (motion == MOTION_NONE) {
            notifyDataSetChanged();
            return;
        }
        boolean reorder = motion == MOTION_REORDER;
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return before.size();
            }

            @Override
            public int getNewListSize() {
                return items.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Object old = before.get(oldPosition);
                Object item = items.get(newPosition);
                if (old == MAP || old == SUMMARY || old == SKELETON) {
                    return old == item;
                }
                // In another order every line comes anew
                if (reorder) {
                    return false;
                }
                if (old instanceof Earthquake && item instanceof Earthquake) {
                    return Objects.equals(((Earthquake) old).id, ((Earthquake) item).id);
                }
                return old.equals(item);
            }

            // Bound again in place, each holder showing its changes its own way
            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return false;
            }

            @Override
            public Object getChangePayload(int oldPosition, int newPosition) {
                return PAYLOAD;
            }
        }, false).dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return this.items.size();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = this.items.get(position);
        if (item == MAP) {
            return TYPE_MAP;
        }
        if (item == SUMMARY) {
            return TYPE_SUMMARY;
        }
        if (item == SKELETON) {
            return TYPE_SKELETON;
        }
        return item instanceof Earthquake ? TYPE_QUAKE : TYPE_DAY;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MAP) {
            return new EarthquakeMapHolder(inflater.inflate(R.layout.item_quake_map, parent, false), this);
        }
        if (viewType == TYPE_SUMMARY) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_quake_summary, parent, false)) {
            };
        } else if (viewType == TYPE_SKELETON) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_quake_skeleton, parent, false)) {
            };
        } else if (viewType == TYPE_DAY) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_quake_day, parent, false)) {
            };
        }
        return new QuakeHolder(inflater.inflate(R.layout.item_earthquake, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        bind(holder, position, false);
    }

    // A line kept through a change shows what changed in it
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
        bind(holder, position, !payloads.isEmpty() && this.motion != MOTION_NONE);
    }

    private void bind(RecyclerView.ViewHolder holder, int position, boolean changing) {
        long now = System.currentTimeMillis();
        Object item = this.items.get(position);
        if (item == MAP) {
            ((EarthquakeMapHolder) holder).bind(takeMapMotion());
        } else if (item == SUMMARY) {
            EarthquakeSummary.bind(holder.itemView, this, changing);
        } else if (item == SKELETON) {
            ((QuakeSkeletonView) holder.itemView).setSheen(this.isLoading);
        } else if (item instanceof Long) {
            ((TextView) holder.itemView).setText(this.formatter.day((Long) item, now));
        } else if (item instanceof Earthquake) {
            bindQuake((QuakeHolder) holder, (Earthquake) item, now, changing);
            ((QuakeHolder) holder).setGroupShape(position > 0 && getItemViewType(position - 1) == TYPE_QUAKE,
                    position + 1 < getItemCount() && getItemViewType(position + 1) == TYPE_QUAKE);
        }
    }

    String distance(Earthquake earthquake) {
        return this.formatter.distance(earthquake.distanceKm(this.referenceLatitude, this.referenceLongitude));
    }

    private void bindQuake(QuakeHolder holder, Earthquake earthquake, long now, boolean changing) {
        holder.tvMagnitude.setText(this.formatter.magnitude(earthquake.magnitude));
        holder.tvMagnitude.setTextColor(EarthquakeFormatter.magnitudeTextColor(earthquake.magnitude));
        ViewCompat.setBackgroundTintList(holder.tvMagnitude,
                ColorStateList.valueOf(EarthquakeFormatter.magnitudeColor(earthquake.magnitude)));
        holder.tvPlace.setText(earthquake.placeName());
        // Under the title of its day the time is enough
        holder.tvTime.setText(this.byDay ? this.formatter.time(earthquake.time) : this.formatter.clock(earthquake.time, now));
        holder.tvDepth.setText(this.formatter.distance(earthquake.depthKm));
        // The last day's events get a filled pill and bold text, older ones an outlined pill and regular gray
        boolean isRecent = EarthquakeFormatter.isRecent(earthquake.time, now);
        holder.tvAgo.setText(this.formatter.ago(earthquake.time, now));
        holder.tvAgo.setBackgroundResource(isRecent ? R.drawable.bg_ago_recent : R.drawable.bg_ago_old);
        holder.tvAgo.setTypeface(isRecent ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        holder.tvAgo.setTextColor(MaterialColors.getColor(holder.tvAgo, isRecent
                ? com.google.android.material.R.attr.colorOnSurface : com.google.android.material.R.attr.colorOnSurfaceVariant));
        holder.tvDistance.animate().cancel();
        holder.tvDistance.setAlpha(1);
        if (this.hasReference) {
            String distance = distance(earthquake);
            // Another place's distance fades in over the one before
            if (changing && holder.tvDistance.getVisibility() == View.VISIBLE && !TextUtils.equals(distance, holder.tvDistance.getText())) {
                holder.tvDistance.setAlpha(0);
                holder.tvDistance.animate().alpha(1).setStartDelay(0).setDuration(260).setInterpolator(QuakeMotion.DECELERATE).start();
            }
            holder.tvDistance.setText(distance);
            holder.tvDistance.setVisibility(View.VISIBLE);
        } else {
            holder.tvDistance.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(view -> open(earthquake));
    }
}
