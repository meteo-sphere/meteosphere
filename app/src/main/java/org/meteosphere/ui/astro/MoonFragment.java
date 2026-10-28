package org.meteosphere.ui.astro;

import android.os.Parcelable;
import org.meteosphere.ui.common.CollapsibleSection;
import org.meteosphere.ui.glass.GlassStrip;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import com.google.android.material.color.MaterialColors;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.data.model.Place;
import org.meteosphere.astro.AstroIO;
import org.meteosphere.astro.Eclipse;
import org.meteosphere.astro.Moon;
import org.meteosphere.R;
import org.meteosphere.ui.icons.ArrowText;
import org.meteosphere.ui.sky.MoonPicture;
import org.meteosphere.util.AppUtils;

/**
 * The moon tab: today's moon at the current or first favorite location with its phase and light, moonrise and
 * moonset, its age and sign and a card of what comes next; then, folding away, the next principal phases, the lunar
 * eclipses and the days of the next month. Computed on the device off the main thread.
 */
public final class MoonFragment extends AstroTabFragment<MoonFragment.MoonMonth> {

    private static final int DAYS = 30;
    private static final int NEXT_PHASES = 4;
    private static final int ECLIPSES = 4;
    // Night sky behind the moon pictures, so their lit part shows in the light theme too
    private static final int SKY_COLOR = 0xff2b3038;
    // Today's moon across, and its real moon's dark side as bright as its picture times this
    private static final int TODAY_DP = 112;
    private static final float TODAY_DARK_SIDE = 0.12f;
    private static final int APSIDES = 4;
    // Perigees and apogees come every two weeks, so this many days past the month always holds the next ones
    private static final long APSIDES_BEYOND_MS = 16 * DAY_MS;
    private static final int NEXT_EVENTS = 2;
    // A full moon nearer than this is a supermoon
    private static final double SUPERMOON_KM = 360000;
    private static final String KEY_PHASES = "moon_phases";
    private static final String KEY_ECLIPSES = "moon_eclipses";
    private static final String KEY_DAYS = "moon_days";
    private static final String KEY_MONTH = "moon_month";
    // Steps of the month's light per day
    private static final int MONTH_STEPS_PER_DAY = 4;

    // The main screen makes its tabs again each time it comes back: its tab's scroll kept for the next one
    private static Parcelable tabListState;
    // The scroll to go back to once the month is in the list
    private Parcelable pendingListState;
    private final MoonAdapter adapter = new MoonAdapter();

    /** What the tab shows: the Moon's month */
    static final class MoonMonth extends Shown {
        Moon.Day todayMoon;
        double age;
        // Today's real moon as it is seen from the place now, drawn off the main thread; null without a place
        Bitmap todayPicture;
        double illumination;
        int sign;
        long signUntil;
        final List<Moon.Phase> nextPhases = new ArrayList<>();
        final List<Eclipse> eclipses = new ArrayList<>();
        final List<Eclipse.Local> eclipseLocals = new ArrayList<>();
        final List<Long> days = new ArrayList<>();
        final List<Moon.Day> dayMoons = new ArrayList<>();
        final List<Integer> daySigns = new ArrayList<>();
        // Change of the light over the last day, -1..1
        double illuminationChange;
        long nextNewMoon;
        // The nearest of the phases, eclipses, perigees and apogees
        final List<Event> next = new ArrayList<>();
        // The light from today's midnight over DAYS days, MONTH_STEPS_PER_DAY a day, and the phases and apsides
        // in those days
        float[] monthIllumination;
        final List<Moon.Phase> monthPhases = new ArrayList<>();
        final List<Moon.Apsis> monthApsides = new ArrayList<>();
        // The next perigees and apogees from now, for the next event card
        final List<Moon.Apsis> nextApsides = new ArrayList<>();
    }

    /** A coming principal phase, lunar eclipse, perigee or apogee */
    private static final class Event {
        final long time;
        Moon.Phase phase;
        Eclipse eclipse;
        Eclipse.Local local;
        Moon.Apsis apsis;
        boolean supermoon;

        Event(long time) {
            this.time = time;
        }
    }

    /** The location chosen for the tabs */
    public static MoonFragment newInstance() {
        return new MoonFragment();
    }

    /** The location given, which can't be changed here, like the one the weather details were opened for */
    public static MoonFragment newInstance(Place location) {
        return withLocation(new MoonFragment(), location);
    }

    // Drawn over the night sky
    @Override
    protected boolean isDaySky() {
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_moon, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.rvMoon);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(this.adapter);
        // A new moment binds the header in place: no cross-fade that would cut the ripple of the arrow tapped
        ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        // The place and the moment stay pinned on glass over the days, shown once there is a month to show
        GlassStrip strip = AstroStrip.add((ViewGroup) view, sky());
        strip.setList(recyclerView);
        strip.setVisibility(View.INVISIBLE);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.pendingListState = fixedLocation() == null ? tabListState : null;
        load();
    }

    @Override
    public void onDestroyView() {
        // A month never shown has no scroll of its own: the one kept stays
        if (fixedLocation() == null && this.pendingListState == null && this.adapter.getItemCount() > 0) {
            tabListState = ((RecyclerView) getView().findViewById(R.id.rvMoon)).getLayoutManager().onSaveInstanceState();
        }
        super.onDestroyView();
    }

    @Override
    protected void show(MoonMonth month) {
        View view = getView();
        // The pinned place and moment of the month, shown once there is a month to show
        bindControls(view, month);
        view.findViewById(R.id.astroStrip).setVisibility(View.VISIBLE);
        this.adapter.setMonth(month);
        // Before the list is laid out with the month: laid out empty, it would lose it
        if (this.pendingListState != null) {
            ((RecyclerView) view.findViewById(R.id.rvMoon)).getLayoutManager().onRestoreInstanceState(this.pendingListState);
            this.pendingListState = null;
        }
    }

    // Without a location the days follow the phone's time zone and there is no moonrise or moonset
    /** The heavy part of the tab for a place and a local day, kept by AstroCache */
    private static final class MoonDays {
        Moon.Day todayMoon;
        // The days after today and the sign at each one's local noon
        Moon.Day[] dayMoons;
        int[] daySigns;
        // The Moon's height every DAY_STEP_MINUTES over the strip of days
        float[] stripAltitudes;
        float[] monthIllumination;
        Moon.Phase[] monthPhases;
        // The perigees and apogees from the start of the day, over the month and some days on for the next event card
        Moon.Apsis[] apsides;
        long[] eclipseTimes;
        Eclipse.Local[] eclipseLocals;

        Eclipse.Local local(Eclipse eclipse) {
            for (int i = 0; i < this.eclipseTimes.length; i++) {
                if (this.eclipseTimes[i] == eclipse.greatest) {
                    return this.eclipseLocals[i];
                }
            }
            return null;
        }
    }

    private static final AstroCache.Codec<MoonDays> MOON_DAYS = new AstroCache.Codec<MoonDays>() {
        @Override
        public void write(DataOutputStream out, MoonDays value) throws IOException {
            AstroIO.write(out, value.todayMoon);
            out.writeInt(value.dayMoons.length);
            for (int i = 0; i < value.dayMoons.length; i++) {
                AstroIO.write(out, value.dayMoons[i]);
                out.writeInt(value.daySigns[i]);
            }
            AstroIO.write(out, value.stripAltitudes);
            AstroIO.write(out, value.monthIllumination);
            out.writeInt(value.monthPhases.length);
            for (Moon.Phase phase : value.monthPhases) {
                AstroIO.write(out, phase);
            }
            out.writeInt(value.apsides.length);
            for (Moon.Apsis apsis : value.apsides) {
                AstroIO.write(out, apsis);
            }
            out.writeInt(value.eclipseTimes.length);
            for (int i = 0; i < value.eclipseTimes.length; i++) {
                out.writeLong(value.eclipseTimes[i]);
                out.writeBoolean(value.eclipseLocals[i] != null);
                if (value.eclipseLocals[i] != null) {
                    AstroIO.write(out, value.eclipseLocals[i]);
                }
            }
        }

        @Override
        public MoonDays read(DataInputStream in) throws IOException {
            MoonDays value = new MoonDays();
            value.todayMoon = AstroIO.readMoonDay(in);
            int days = in.readInt();
            value.dayMoons = new Moon.Day[days];
            value.daySigns = new int[days];
            for (int i = 0; i < days; i++) {
                value.dayMoons[i] = AstroIO.readMoonDay(in);
                value.daySigns[i] = in.readInt();
            }
            value.stripAltitudes = AstroIO.readFloats(in);
            value.monthIllumination = AstroIO.readFloats(in);
            value.monthPhases = new Moon.Phase[in.readInt()];
            for (int i = 0; i < value.monthPhases.length; i++) {
                value.monthPhases[i] = AstroIO.readPhase(in);
            }
            value.apsides = new Moon.Apsis[in.readInt()];
            for (int i = 0; i < value.apsides.length; i++) {
                value.apsides[i] = AstroIO.readApsis(in);
            }
            int eclipses = in.readInt();
            value.eclipseTimes = new long[eclipses];
            value.eclipseLocals = new Eclipse.Local[eclipses];
            for (int i = 0; i < eclipses; i++) {
                value.eclipseTimes[i] = in.readLong();
                value.eclipseLocals[i] = in.readBoolean() ? AstroIO.readLocal(in) : null;
            }
            return value;
        }
    };

    private static MoonDays computeDays(Place location, TimeZone timeZone, long today, List<Eclipse> catalog) {
        double latitude = location != null ? location.latitude : 0;
        double longitude = location != null ? location.longitude : 0;
        MoonDays value = new MoonDays();
        value.todayMoon = Moon.day(today, Place.offsetMinutesOfDay(timeZone, today), latitude, longitude);
        value.dayMoons = new Moon.Day[DAYS];
        value.daySigns = new int[DAYS];
        for (int i = 1; i <= DAYS; i++) {
            long day = today + i * DAY_MS;
            int offsetMinutes = Place.offsetMinutesOfDay(timeZone, day);
            value.dayMoons[i - 1] = Moon.day(day, offsetMinutes, latitude, longitude);
            // The sign at local noon
            value.daySigns[i - 1] = Moon.zodiacSign(day + DAY_MS / 2 - offsetMinutes * MINUTE_MS);
        }
        long todayStartUtc = utcStart(timeZone, today);
        long stripStart = AstroTime.fromLocal(timeZone, today - STRIP_DAYS * DAY_MS);
        long stripEnd = AstroTime.fromLocal(timeZone, today + (STRIP_DAYS + 1) * DAY_MS);
        value.stripAltitudes = new float[(int) ((stripEnd - stripStart) / (DAY_STEP_MINUTES * MINUTE_MS)) + 1];
        for (int i = 0; i < value.stripAltitudes.length; i++) {
            value.stripAltitudes[i] = (float) Moon.altitude(stripStart + i * DAY_STEP_MINUTES * MINUTE_MS, latitude, longitude);
        }
        value.monthIllumination = new float[DAYS * MONTH_STEPS_PER_DAY + 1];
        for (int i = 0; i < value.monthIllumination.length; i++) {
            value.monthIllumination[i] = (float) Moon.illumination(todayStartUtc + i * DAY_MS / MONTH_STEPS_PER_DAY);
        }
        long monthEnd = todayStartUtc + DAYS * DAY_MS;
        List<Moon.Phase> phases = new ArrayList<>();
        for (Moon.Phase phase = Moon.nextPhase(todayStartUtc); phase.time < monthEnd; phase = Moon.nextPhase(phase.time + MINUTE_MS)) {
            phases.add(phase);
        }
        value.monthPhases = phases.toArray(new Moon.Phase[0]);
        List<Moon.Apsis> apsides = new ArrayList<>();
        for (Moon.Apsis apsis = Moon.nextApsis(todayStartUtc); apsis.time < monthEnd + APSIDES_BEYOND_MS; apsis = Moon.nextApsis(apsis.time + DAY_MS)) {
            apsides.add(apsis);
        }
        value.apsides = apsides.toArray(new Moon.Apsis[0]);
        // One more than shown, for when one passes during the day
        List<Eclipse> eclipses = Eclipse.upcoming(catalog, todayStartUtc, ECLIPSES + 1, false);
        value.eclipseTimes = new long[eclipses.size()];
        value.eclipseLocals = new Eclipse.Local[eclipses.size()];
        for (int i = 0; i < eclipses.size(); i++) {
            value.eclipseTimes[i] = eclipses.get(i).greatest;
            value.eclipseLocals[i] = location != null ? eclipses.get(i).local(latitude, longitude) : null;
        }
        return value;
    }


    @Override
    protected MoonMonth compute(Context context, Place location, long now, List<Eclipse> catalog) {
        MoonMonth month = new MoonMonth();
        month.hasPlace = location != null;
        month.location = location;
        TimeZone timeZone = location != null ? location.timeZone() : TimeZone.getDefault();
        double latitude = location != null ? location.latitude : 0;
        double longitude = location != null ? location.longitude : 0;
        month.timeZone = timeZone;
        month.southernHemisphere = latitude < 0;
        month.todayPicture = location == null ? null : MoonPicture.draw(context.getResources(),
                Math.round(TODAY_DP * context.getResources().getDisplayMetrics().density),
                Moon.disc(now, latitude, longitude), TODAY_DARK_SIDE, 1);
        month.today = Math.floorDiv(month.local(now), DAY_MS) * DAY_MS;
        MoonDays days = AstroCache.get(context, "moon", location, timeZone, month.today, MOON_DAYS,
                () -> computeDays(location, timeZone, month.today, catalog));
        month.todayMoon = days.todayMoon;
        for (int i = 1; i <= DAYS; i++) {
            month.days.add(month.today + i * DAY_MS);
            month.dayMoons.add(days.dayMoons[i - 1]);
            month.daySigns.add(days.daySigns[i - 1]);
        }
        month.stripStart = AstroTime.fromLocal(timeZone, month.today - STRIP_DAYS * DAY_MS);
        month.stripAltitudes = days.stripAltitudes;
        month.monthIllumination = days.monthIllumination;
        long monthEnd = month.today - Place.offsetMinutesOfDay(timeZone, month.today) * MINUTE_MS + DAYS * DAY_MS;
        month.monthPhases.addAll(java.util.Arrays.asList(days.monthPhases));
        for (Moon.Apsis apsis : days.apsides) {
            if (apsis.time < monthEnd) {
                month.monthApsides.add(apsis);
            }
            if (apsis.time > now && month.nextApsides.size() < APSIDES) {
                month.nextApsides.add(apsis);
            }
        }
        month.age = Moon.age(now);
        month.illumination = Moon.illumination(now);
        month.illuminationChange = month.illumination - Moon.illumination(now - DAY_MS);
        month.sign = Moon.zodiacSign(now);
        month.signUntil = month.local(Moon.nextSignChange(now));
        Moon.Phase phase = Moon.nextPhase(now);
        for (int i = 0; i < NEXT_PHASES; i++) {
            month.nextPhases.add(phase);
            phase = Moon.nextPhase(phase.time + MINUTE_MS);
        }
        for (Eclipse eclipse : Eclipse.upcoming(catalog, now, ECLIPSES, false)) {
            month.eclipses.add(eclipse);
            Eclipse.Local local = days.local(eclipse);
            month.eclipseLocals.add(location == null ? null : local != null ? local : eclipse.local(latitude, longitude));
        }
        Moon.Phase newMoon = Moon.nextPhase(now);
        while (newMoon.phase != Moon.NEW_MOON) {
            newMoon = Moon.nextPhase(newMoon.time + MINUTE_MS);
        }
        month.nextNewMoon = newMoon.time;
        month.now = now;
        month.nowAltitude = (float) Moon.altitude(now, latitude, longitude);
        nextEvents(month);
        return month;
    }

    private static void nextEvents(MoonMonth month) {
        List<Event> events = new ArrayList<>();
        for (Moon.Phase phase : month.nextPhases) {
            Event event = new Event(phase.time);
            event.phase = phase;
            event.supermoon = phase.phase == Moon.FULL_MOON && Moon.distance(phase.time) < SUPERMOON_KM;
            events.add(event);
        }
        for (int i = 0; i < month.eclipses.size(); i++) {
            Event event = new Event(month.eclipses.get(i).greatest);
            event.eclipse = month.eclipses.get(i);
            event.local = month.eclipseLocals.get(i);
            events.add(event);
        }
        for (Moon.Apsis apsis : month.nextApsides) {
            Event event = new Event(apsis.time);
            event.apsis = apsis;
            events.add(event);
        }
        events.sort((a, b) -> Long.compare(a.time, b.time));
        month.next.addAll(events.subList(0, Math.min(NEXT_EVENTS, events.size())));
    }

    // "in 21 days", "today"
    private String daysLeft(MoonMonth month, long time) {
        int days = (int) Math.max(0, (Math.floorDiv(month.local(time), DAY_MS) * DAY_MS - month.today) / DAY_MS);
        return days == 0 ? getString(R.string.sun_event_today) : getString(R.string.sun_event_in_days, days, daysPlural(days));
    }

    private String daysPlural(int days) {
        return getResources().getQuantityString(R.plurals.days, days);
    }

    private String eventName(Event event) {
        if (event.phase != null) {
            return formatter.phaseName(event.phase.phase);
        }
        if (event.eclipse != null) {
            return formatter.eclipseName(event.eclipse);
        }
        return getString(event.apsis.perigee ? R.string.moon_perigee : R.string.moon_apogee);
    }

    // A phase's moon, an eclipse's picture, or the perigee or apogee in silver on a faint silver disk
    private void eventIcon(ImageView view, Event event, MoonMonth month, int sizeDp, int paddingDp) {
        view.setImageTintList(null);
        view.setBackground(null);
        view.setPadding(0, 0, 0, 0);
        if (event.phase != null) {
            view.setImageDrawable(moonPicture(view, sizeDp, event.phase.phase / 8.0, month.southernHemisphere, false));
        } else if (event.eclipse != null) {
            view.setImageResource(AstroFormatter.eclipseIcon(event.eclipse));
        } else {
            int padding = Math.round(paddingDp * getResources().getDisplayMetrics().density);
            view.setImageResource(event.apsis.perigee ? R.drawable.ic_moon_perigee : R.drawable.ic_moon_apogee);
            view.setImageTintList(ColorStateList.valueOf(moonColor()));
            view.setBackgroundResource(R.drawable.bg_magnitude);
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(tonal()));
            view.setPadding(padding, padding, padding, padding);
        }
    }

    // The Moon's silver for its icons and lines, like the amber of the sun tab, deeper in the light theme
    private int moonColor() {
        return ContextCompat.getColor(sky(), R.color.astro_moon);
    }

    private int tonal() {
        return ContextCompat.getColor(sky(), R.color.astro_moon_container);
    }

    private int onTonal() {
        return ContextCompat.getColor(sky(), R.color.astro_on_moon_container);
    }

    // The nearest event large with the days left in a pill, the one after it in two lines
    private void bindNext(View card, MoonMonth month) {
        card.setVisibility(month.next.isEmpty() ? View.GONE : View.VISIBLE);
        if (month.next.isEmpty()) {
            return;
        }
        Event first = month.next.get(0);
        eventIcon(card.findViewById(R.id.ivNextIcon), first, month, 48, 12);
        ((TextView) card.findViewById(R.id.tvNextName)).setText(eventName(first));
        String time = formatter.dateTime(month.local(first.time));
        if (first.apsis != null) {
            time += " \u00b7 " + getString(R.string.moon_distance_km, String.format(Locale.getDefault(), "%,d", Math.round(first.apsis.distanceKm)));
        } else if (first.local != null) {
            time += " \u00b7 " + getString(first.local.visible ? R.string.eclipse_alert_visible : R.string.eclipse_alert_not_visible);
        } else if (first.supermoon) {
            time += " \u00b7 " + getString(R.string.moon_supermoon);
        }
        ((TextView) card.findViewById(R.id.tvNextTime)).setText(time);
        TextView days = card.findViewById(R.id.tvNextDays);
        days.setText(daysLeft(month, first.time));
        days.setTextColor(onTonal());
        ViewCompat.setBackgroundTintList(days, ColorStateList.valueOf(tonal()));
        boolean hasSecond = month.next.size() > 1;
        card.findViewById(R.id.vNextDivider).setVisibility(hasSecond ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.llNextSecond).setVisibility(hasSecond ? View.VISIBLE : View.GONE);
        if (hasSecond) {
            Event second = month.next.get(1);
            eventIcon(card.findViewById(R.id.ivNextSecondIcon), second, month, 24, 5);
            ((TextView) card.findViewById(R.id.tvNextSecond)).setText(eventName(second));
            ((TextView) card.findViewById(R.id.tvNextSecondDate)).setText(formatter.date(month.local(second.time)));
            ((TextView) card.findViewById(R.id.tvNextSecondDays)).setText(daysLeft(month, second.time));
        }
    }

    // A tile of item_astro_tile with its icon in the Moon's silver
    private void tile(View tile, int icon, int label, String value, String note) {
        ImageView iconView = tile.findViewById(R.id.ivTileIcon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(moonColor()));
        ((TextView) tile.findViewById(R.id.tvTileLabel)).setText(label);
        ((TextView) tile.findViewById(R.id.tvTileValue)).setText(value);
        ((TextView) tile.findViewById(R.id.tvTileNote)).setText(note);
    }

    private MoonPhaseDrawable moonPicture(View view, int sizeDp, double age, boolean southernHemisphere, boolean isPrincipalPhase) {
        float density = getResources().getDisplayMetrics().density;
        MoonPhaseDrawable drawable = new MoonPhaseDrawable(Math.round(sizeDp * density));
        drawable.setPhase(age, southernHemisphere);
        drawable.setSkyColor(SKY_COLOR);
        // A thin rim of sky on the light theme, where a wide one reads as a heavy black ring
        TypedValue share = new TypedValue();
        getResources().getValue(R.dimen.moon_sky_share, share, true);
        drawable.setSkyShare(share.getFloat());
        if (isPrincipalPhase) {
            drawable.setRing(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface), 1.5f * density);
        }
        return drawable;
    }

    private class MoonAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_DAY = 1;

        MoonAdapter() {
            setHasStableIds(true);
        }

        private MoonMonth month;
        private boolean daysCollapsed = CollapsibleSection.isCollapsed(KEY_DAYS);

        void setMonth(MoonMonth month) {
            this.month = month;
            notifyDataSetChanged();
        }

        // The days come and go after the header as their title folds
        void setDaysCollapsed(boolean collapsed) {
            if (collapsed == this.daysCollapsed) {
                return;
            }
            this.daysCollapsed = collapsed;
            if (this.month != null) {
                if (collapsed) {
                    notifyItemRangeRemoved(1, this.month.days.size());
                } else {
                    notifyItemRangeInserted(1, this.month.days.size());
                }
            }
        }

        @Override
        public int getItemCount() {
            return this.month == null ? 0 : 1 + (this.daysCollapsed ? 0 : this.month.days.size());
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_DAY;
        }

        // The header and each day after today keep their holders across a reload
        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return viewType == TYPE_HEADER
                    ? new HeaderHolder(inflater.inflate(R.layout.item_moon_header, parent, false))
                    : new DayHolder(inflater.inflate(R.layout.item_moon_day, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).bind(this.month);
            } else {
                ((DayHolder) holder).bind(this.month, position - 1);
            }
        }
    }

    private class HeaderHolder extends RecyclerView.ViewHolder {

        final View view;
        final ImageView ivMoon;
        final TextView tvPhase;
        final TextView tvIllumination;
        final TextView tvIlluminationChange;
        final View riseSet;
        final TextView tvRise;
        final TextView tvSet;
        final LinearLayout llNextPhases;
        final LinearLayout llEclipses;

        HeaderHolder(View view) {
            super(view);
            this.view = view;
            this.ivMoon = view.findViewById(R.id.ivMoonToday);
            this.tvPhase = view.findViewById(R.id.tvMoonPhase);
            this.tvIllumination = view.findViewById(R.id.tvMoonIllumination);
            this.tvIlluminationChange = view.findViewById(R.id.tvMoonIlluminationChange);
            this.tvIllumination.setTextColor(onTonal());
            ViewCompat.setBackgroundTintList(this.tvIllumination, ColorStateList.valueOf(tonal()));
            this.riseSet = view.findViewById(R.id.llMoonRiseSet);
            this.tvRise = view.findViewById(R.id.tvMoonRise);
            this.tvSet = view.findViewById(R.id.tvMoonSet);
            this.llNextPhases = view.findViewById(R.id.llNextPhases);
            this.llEclipses = view.findViewById(R.id.llMoonEclipses);
            for (int id : new int[]{R.id.ivMoonRise, R.id.ivMoonSet}) {
                ImageView icon = view.findViewById(id);
                icon.setImageTintList(ColorStateList.valueOf(moonColor()));
                ViewCompat.setBackgroundTintList(icon, ColorStateList.valueOf(tonal()));
            }
            CollapsibleSection.bind(view.findViewById(R.id.headerMoonMonth), R.string.moon_month_title, view.findViewById(R.id.llMoonMonth), KEY_MONTH);
            ((SunDayChart) view.findViewById(R.id.moonDayChart)).setColor(moonColor(), false);
            ((MoonMonthChart) view.findViewById(R.id.moonMonthChart)).setColor(moonColor());
            ((MoonMonthChart) view.findViewById(R.id.moonMonthChart)).setSelectionColor(ContextCompat.getColor(sky(), R.color.astro_moon_selection));
            CollapsibleSection.bind(view.findViewById(R.id.headerMoonPhases), R.string.moon_next_phases, this.llNextPhases, KEY_PHASES);
            CollapsibleSection.bind(view.findViewById(R.id.headerMoonEclipses), R.string.lunar_eclipses_title, this.llEclipses, KEY_ECLIPSES);
            CollapsibleSection.bind(view.findViewById(R.id.headerMoonDays), R.string.moon_by_day, KEY_DAYS, adapter::setDaysCollapsed);
        }

        void bind(MoonMonth month) {
            // Today's moon as it is seen from the place now, the real one, its dark side dim in earthshine
            MoonPhaseDrawable today = moonPicture(this.ivMoon, TODAY_DP, month.age, month.southernHemisphere, false);
            today.setPicture(month.todayPicture);
            if (month.todayPicture != null) {
                // The real moon stands on the screen's own night, without the drawn one's disk of sky round it, as
                // large as on it
                today.setSkyHidden(true);
            }
            this.ivMoon.setImageDrawable(today);
            this.tvPhase.setText(formatter.phaseName(month.todayMoon.phase));
            // "64%" in a silver pill, and under it "−11% in a day"
            this.tvIllumination.setText(formatter.illumination(month.illumination));
            long change = Math.round(month.illuminationChange * 100);
            String signedChange = (change > 0 ? "+" : change < 0 ? "\u2212" : "")
                    + AppUtils.percent(getResources().getConfiguration().getLocales().get(0), Math.abs(change));
            this.tvIlluminationChange.setText(getString(R.string.moon_illumination_change, signedChange));
            this.riseSet.setVisibility(month.hasPlace ? View.VISIBLE : View.GONE);
            this.tvRise.setText(month.todayMoon.rise == Moon.NONE ? "\u2014" : formatter.clock(month.todayMoon.rise));
            this.tvSet.setText(month.todayMoon.set == Moon.NONE ? "\u2014" : formatter.clock(month.todayMoon.set));

            Locale locale = getResources().getConfiguration().getLocales().get(0);
            int toNewMoon = (int) Math.max(0, (Math.floorDiv(month.local(month.nextNewMoon), DAY_MS) * DAY_MS - month.today) / DAY_MS);
            tile(this.view.findViewById(R.id.tileMoonAge), R.drawable.ic_moon_age, R.string.moon_tile_age,
                    getString(R.string.moon_age_days, String.format(locale, "%.1f", month.age * Moon.SYNODIC_MONTH)),
                    toNewMoon == 0 ? getString(R.string.moon_new_today) : getString(R.string.moon_until_new, toNewMoon, daysPlural(toNewMoon)));
            tile(this.view.findViewById(R.id.tileMoonSign), AstroFormatter.zodiacIcon(month.sign), R.string.sun_tile_sign,
                    formatter.zodiacName(month.sign), getString(R.string.moon_sign_until, formatter.dateTime(month.signUntil)));
            bindNext(this.view.findViewById(R.id.cardMoonNext), month);
            bindChartsInto(this.view, month);

            this.llNextPhases.removeAllViews();
            for (Moon.Phase phase : month.nextPhases) {
                View row = LayoutInflater.from(this.llNextPhases.getContext()).inflate(R.layout.item_moon_phase, this.llNextPhases, false);
                ImageView icon = row.findViewById(R.id.ivPhase);
                icon.setImageDrawable(moonPicture(icon, 36, phase.phase / 8.0, month.southernHemisphere, false));
                ((TextView) row.findViewById(R.id.tvPhaseName)).setText(formatter.phaseName(phase.phase));
                ((TextView) row.findViewById(R.id.tvPhaseTime)).setText(formatter.dateTime(month.local(phase.time)) + " \u00b7 " + daysLeft(month, phase.time));
                this.llNextPhases.addView(row);
            }
            this.llEclipses.removeAllViews();
            for (int i = 0; i < month.eclipses.size(); i++) {
                Eclipse eclipse = month.eclipses.get(i);
                this.llEclipses.addView(SunFragment.eventRow(this.llEclipses, ContextCompat.getDrawable(requireContext(), AstroFormatter.eclipseIcon(eclipse)),
                        formatter.eclipseName(eclipse), formatter.dateTime(month.local(eclipse.greatest)),
                        SunFragment.visibility(getResources(), formatter, eclipse, month.eclipseLocals.get(i), month.timeZone),
                        month.eclipseLocals.get(i) == null ? null : month.eclipseLocals.get(i).visible));
            }
        }
    }

    // The Moon's height over the days around the day shown with it now, and its light over the month with the
    // phases, perigees and apogees
    private void bindChartsInto(View view, MoonMonth month) {
        SunDayChart dayChart = view.findViewById(R.id.moonDayChart);
        bindDayStrip(dayChart, month);
        dayChart.setVisibility(month.hasPlace ? View.VISIBLE : View.GONE);
        MoonMonthChart chart = view.findViewById(R.id.moonMonthChart);
        long todayStartUtc = month.today - Place.offsetMinutesOfDay(month.timeZone, month.today) * MINUTE_MS;
        chart.setData(month.today, DAYS, month.monthIllumination);
        float[] phaseDays = new float[month.monthPhases.size()];
        Drawable[] phaseIcons = new Drawable[phaseDays.length];
        for (int i = 0; i < phaseDays.length; i++) {
            Moon.Phase phase = month.monthPhases.get(i);
            phaseDays[i] = (float) (phase.time - todayStartUtc) / DAY_MS;
            phaseIcons[i] = moonPicture(chart, 16, phase.phase / 8.0, month.southernHemisphere, false);
        }
        chart.setPhases(phaseDays, phaseIcons);
        float[] apsisDays = new float[month.monthApsides.size()];
        String[] apsisNames = new String[apsisDays.length];
        for (int i = 0; i < apsisDays.length; i++) {
            Moon.Apsis apsis = month.monthApsides.get(i);
            apsisDays[i] = (float) (apsis.time - todayStartUtc) / DAY_MS;
            apsisNames[i] = getString(apsis.perigee ? R.string.moon_perigee : R.string.moon_apogee).toLowerCase(Locale.getDefault());
        }
        chart.setApsides(apsisDays, apsisNames);
        TextView selectedDay = view.findViewById(R.id.tvMoonSelectedDay);
        selectedDay.setText(monthDayText(month, chart.getSelected()));
        chart.setOnDaySelectedListener(day -> selectedDay.setText(monthDayText(month, day)));
    }

    // "Fri, 2 October · 64% · Waning gibbous"
    private String monthDayText(MoonMonth month, int day) {
        Moon.Day moon = day == 0 ? month.todayMoon : month.dayMoons.get(day - 1);
        // Today's light is the one now, as in the header
        double illumination = day == 0 ? month.illumination : moon.illumination;
        return formatter.day(month.today + day * DAY_MS) + " \u00b7 " + formatter.illumination(illumination)
                + " \u00b7 " + formatter.phaseName(moon.phase);
    }

    private class DayHolder extends RecyclerView.ViewHolder {

        final ImageView ivMoon;
        final TextView tvDate;
        final TextView tvInfo;
        final TextView tvSign;
        final View riseSet;
        final TextView tvRise;
        final TextView tvSet;

        DayHolder(View view) {
            super(view);
            this.ivMoon = view.findViewById(R.id.ivMoonDay);
            this.tvDate = view.findViewById(R.id.tvMoonDayDate);
            this.tvInfo = view.findViewById(R.id.tvMoonDayInfo);
            this.tvSign = view.findViewById(R.id.tvMoonDaySign);
            this.riseSet = view.findViewById(R.id.llMoonDayRiseSet);
            this.tvRise = view.findViewById(R.id.tvMoonDayRise);
            this.tvSet = view.findViewById(R.id.tvMoonDaySet);
        }

        void bind(MoonMonth month, int index) {
            Moon.Day day = month.dayMoons.get(index);
            boolean isPrincipalPhase = day.phaseTime != Moon.NONE;
            this.ivMoon.setImageDrawable(moonPicture(this.ivMoon, 44, day.age, month.southernHemisphere, isPrincipalPhase));
            this.tvDate.setText(formatter.day(month.days.get(index)));
            String phase = isPrincipalPhase
                    ? getString(R.string.moon_phase_at, formatter.phaseName(day.phase), formatter.clock(day.phaseTime))
                    : formatter.phaseName(day.phase) + " \u00b7 " + formatter.illumination(day.illumination);
            this.tvInfo.setText(phase);
            int sign = month.daySigns.get(index);
            this.tvSign.setText(formatter.zodiacName(sign));
            Drawable icon = ContextCompat.getDrawable(requireContext(), AstroFormatter.zodiacIcon(sign)).mutate();
            icon.setTint(this.tvSign.getCurrentTextColor());
            int size = Math.round(this.tvSign.getTextSize() * 1.1f);
            icon.setBounds(0, 0, size, size);
            this.tvSign.setCompoundDrawablesRelative(icon, null, null, null);
            this.tvSign.setCompoundDrawablePadding(Math.round(4 * getResources().getDisplayMetrics().density));
            this.riseSet.setVisibility(month.hasPlace ? View.VISIBLE : View.GONE);
            this.tvRise.setText(ArrowText.of(requireContext(), formatter.rise(day)));
            this.tvSet.setText(ArrowText.of(requireContext(), formatter.set(day)));
        }
    }
}
