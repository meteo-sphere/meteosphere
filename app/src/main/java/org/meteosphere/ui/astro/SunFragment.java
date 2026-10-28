package org.meteosphere.ui.astro;

import android.view.ViewTreeObserver;
import androidx.core.widget.NestedScrollView;
import org.meteosphere.ui.common.CollapsibleSection;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Function;
import org.meteosphere.data.model.Place;
import org.meteosphere.astro.AstroIO;
import org.meteosphere.astro.Eclipse;
import org.meteosphere.astro.Sun;
import com.google.android.material.color.MaterialColors;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.icons.ArrowText;

/**
 * The sun tab: today's sun at the current or first favorite location with sunrise, sunset, the day length, noon,
 * twilight and the zodiac sign; a chart of the year's sunrises and sunsets; then, as lists that fold, the solstices
 * and equinoxes and the solar eclipses of the coming year with whether they are seen there and the Sun's passages
 * through the signs.
 * Computed on the device off the main thread.
 */
public final class SunFragment extends AstroTabFragment<SunFragment.SunYear> {

    private static final int YEAR_DAYS = 365;
    private static final int SEASONS = 4;
    private static final int ECLIPSES = 4;
    private static final int SIGNS = 12;
    private static final int NEXT_EVENTS = 2;

    // The main screen makes its tabs again each time it comes back: its tab's scroll kept for the next one
    private static int tabScroll;
    // The scroll to go back to once the year is laid out, 0 for none
    private int pendingScroll;
    private View content;
    private SunNowCard sunNow;

    /** What the tab shows: the Sun's year */
    static final class SunYear extends Shown {
        Sun.Day todaySun;
        long todayChange;
        int sign;
        long signUntil;
        final List<Sun.Season> seasons = new ArrayList<>();
        final List<Eclipse> eclipses = new ArrayList<>();
        final List<Eclipse.Local> eclipseLocals = new ArrayList<>();
        final List<Sun.SignChange> signChanges = new ArrayList<>();
        final Sun.Day[] days = new Sun.Day[YEAR_DAYS];
        // The nearest of the solstices and equinoxes, sign changes and solar eclipses
        final List<Event> next = new ArrayList<>();
    }

    /** A coming solstice or equinox, with the sign the Sun enters then if any, a sign change or a solar eclipse */
    private static final class Event {
        final long time;
        int season = -1;
        int sign = -1;
        Eclipse eclipse;
        Eclipse.Local local;

        Event(long time) {
            this.time = time;
        }
    }

    /** What a row of the seasons, the eclipses or the signs shows */
    private static final class Line {
        final int icon;
        final String name;
        final String time;
        final String visibility;
        final Boolean seen;

        Line(int icon, String name, String time, String visibility, Boolean seen) {
            this.icon = icon;
            this.name = name;
            this.time = time;
            this.visibility = visibility;
            this.seen = seen;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Line)) {
                return false;
            }
            Line line = (Line) other;
            return this.icon == line.icon && Objects.equals(this.name, line.name) && Objects.equals(this.time, line.time)
                    && Objects.equals(this.visibility, line.visibility) && Objects.equals(this.seen, line.seen);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.icon, this.name, this.time, this.visibility, this.seen);
        }
    }

    /** The location chosen for the tabs */
    public static SunFragment newInstance() {
        return new SunFragment();
    }

    /** The location given, which can't be changed here, like the one the weather details were opened for */
    public static SunFragment newInstance(Place location) {
        return withLocation(new SunFragment(), location);
    }

    // Drawn over the day sky
    @Override
    protected boolean isDaySky() {
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.content = inflater.inflate(R.layout.fragment_sun, container, false);
        this.content.setVisibility(View.INVISIBLE);
        // The place and the moment stay pinned on glass over the year
        AstroStrip.add((ViewGroup) this.content, sky()).setList(this.content.findViewById(R.id.nsvSun));
        return this.content;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CollapsibleSection.bind(view.findViewById(R.id.headerSunYear), R.string.sun_year_title, view.findViewById(R.id.llSunYear), "sun_year");
        CollapsibleSection.bind(view.findViewById(R.id.headerSunSeasons), R.string.sun_seasons_title, view.findViewById(R.id.llSunSeasons), "sun_seasons");
        CollapsibleSection.bind(view.findViewById(R.id.headerSunEclipses), R.string.solar_eclipses_title, view.findViewById(R.id.llSunEclipses), "sun_eclipses");
        CollapsibleSection.bind(view.findViewById(R.id.headerSunSigns), R.string.sun_signs_title, view.findViewById(R.id.llSunSigns), "sun_signs");
        this.sunNow = new SunNowCard(view.findViewById(R.id.cardSunNow), this.formatter);
        updateSunNow();
        this.pendingScroll = fixedLocation() == null ? tabScroll : 0;
        load();
    }

    @Override
    public void onDestroyView() {
        // A year never shown has no scroll of its own: the one kept stays
        if (fixedLocation() == null && this.content.getVisibility() == View.VISIBLE && this.pendingScroll == 0) {
            tabScroll = ((NestedScrollView) this.content.findViewById(R.id.nsvSun)).getScrollY();
        }
        this.sunNow.destroy();
        this.sunNow = null;
        super.onDestroyView();
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        updateSunNow();
    }

    @Override
    protected void show(SunYear year) {
        bind(year);
        this.content.setVisibility(View.VISIBLE);
        restoreScroll();
        updateSunNow();
    }

    // Once the year is laid out with its days, before it is drawn: scrolled sooner, it would be cut to the empty page
    private void restoreScroll() {
        if (this.pendingScroll == 0) {
            return;
        }
        int y = this.pendingScroll;
        this.pendingScroll = 0;
        NestedScrollView scroll = this.content.findViewById(R.id.nsvSun);
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                scroll.scrollTo(0, y);
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSunNow();
    }

    // The card unless the weather parameters turn it off; a newer picture is asked for only while the tab is on screen
    private void updateSunNow() {
        if (this.sunNow == null || getView() == null) {
            return;
        }
        boolean shown = AppSettings.getInstance().isSunNowShown();
        getView().findViewById(R.id.cardSunNow).setVisibility(shown ? View.VISIBLE : View.GONE);
        if (shown) {
            this.sunNow.refresh(isOnScreen());
        }
    }

    /** The heavy part of the tab for a place and a local day, kept by AstroCache */
    private static final class SunDays {
        Sun.Day[] days;
        // The days of the strip before this one
        Sun.Day[] pastDays;
        // The Sun's height every DAY_STEP_MINUTES over the strip of days
        float[] stripAltitudes;
        long todayChange;
        // How the solar eclipses coming from the start of the day look at the place, by their greatest instant
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

    private static final AstroCache.Codec<SunDays> SUN_DAYS = new AstroCache.Codec<SunDays>() {
        @Override
        public void write(DataOutputStream out, SunDays value) throws IOException {
            out.writeInt(value.days.length);
            for (Sun.Day day : value.days) {
                AstroIO.write(out, day);
            }
            for (Sun.Day day : value.pastDays) {
                AstroIO.write(out, day);
            }
            AstroIO.write(out, value.stripAltitudes);
            out.writeLong(value.todayChange);
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
        public SunDays read(DataInputStream in) throws IOException {
            SunDays value = new SunDays();
            value.days = new Sun.Day[in.readInt()];
            for (int i = 0; i < value.days.length; i++) {
                value.days[i] = AstroIO.readSunDay(in);
            }
            value.pastDays = new Sun.Day[STRIP_DAYS];
            for (int i = 0; i < STRIP_DAYS; i++) {
                value.pastDays[i] = AstroIO.readSunDay(in);
            }
            value.stripAltitudes = AstroIO.readFloats(in);
            value.todayChange = in.readLong();
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

    // The days of the year from the local day, the strip's heights and days before it and the eclipses as seen at the place
    private static SunDays computeDays(Place location, TimeZone timeZone, long today, List<Eclipse> catalog) {
        double latitude = location != null ? location.latitude : 0;
        double longitude = location != null ? location.longitude : 0;
        SunDays value = new SunDays();
        long todayStartUtc = utcStart(timeZone, today);
        long stripStart = AstroTime.fromLocal(timeZone, today - STRIP_DAYS * DAY_MS);
        long stripEnd = AstroTime.fromLocal(timeZone, today + (STRIP_DAYS + 1) * DAY_MS);
        value.stripAltitudes = new float[(int) ((stripEnd - stripStart) / (DAY_STEP_MINUTES * MINUTE_MS)) + 1];
        for (int i = 0; i < value.stripAltitudes.length; i++) {
            value.stripAltitudes[i] = (float) Sun.altitude(stripStart + i * DAY_STEP_MINUTES * MINUTE_MS, latitude, longitude);
        }
        value.pastDays = new Sun.Day[STRIP_DAYS];
        for (int i = 0; i < STRIP_DAYS; i++) {
            long day = today - (STRIP_DAYS - i) * DAY_MS;
            value.pastDays[i] = Sun.day(day, Place.offsetMinutesOfDay(timeZone, day), latitude, longitude);
        }
        value.days = new Sun.Day[YEAR_DAYS];
        for (int i = 0; i < YEAR_DAYS; i++) {
            long day = today + i * DAY_MS;
            value.days[i] = Sun.day(day, Place.offsetMinutesOfDay(timeZone, day), latitude, longitude);
        }
        value.todayChange = value.days[0].dayLength - value.pastDays[STRIP_DAYS - 1].dayLength;
        // One more than shown, for when one passes during the day
        List<Eclipse> eclipses = Eclipse.upcoming(catalog, todayStartUtc, ECLIPSES + 1, true);
        value.eclipseTimes = new long[eclipses.size()];
        value.eclipseLocals = new Eclipse.Local[eclipses.size()];
        for (int i = 0; i < eclipses.size(); i++) {
            value.eclipseTimes[i] = eclipses.get(i).greatest;
            value.eclipseLocals[i] = location != null ? eclipses.get(i).local(latitude, longitude) : null;
        }
        return value;
    }

    // Without a location the days follow the phone's time zone at the equator and the eclipses aren't placed
    @Override
    protected SunYear compute(Context context, Place location, long now, List<Eclipse> catalog) {
        SunYear year = new SunYear();
        year.hasPlace = location != null;
        year.location = location;
        TimeZone timeZone = location != null ? location.timeZone() : TimeZone.getDefault();
        double latitude = location != null ? location.latitude : 0;
        double longitude = location != null ? location.longitude : 0;
        year.timeZone = timeZone;
        year.southernHemisphere = latitude < 0;
        year.today = Math.floorDiv(year.local(now), DAY_MS) * DAY_MS;
        year.now = now;
        year.nowAltitude = (float) Sun.altitude(now, latitude, longitude);
        SunDays days = AstroCache.get(context, "sun", location, timeZone, year.today, SUN_DAYS,
                () -> computeDays(location, timeZone, year.today, catalog));
        year.stripStart = AstroTime.fromLocal(timeZone, year.today - STRIP_DAYS * DAY_MS);
        year.stripAltitudes = days.stripAltitudes;
        System.arraycopy(days.days, 0, year.days, 0, YEAR_DAYS);
        year.todaySun = year.days[0];
        year.todayChange = days.todayChange;
        year.sign = Sun.zodiacSign(now);
        year.signUntil = year.local(Sun.nextSignChange(now));
        Sun.Season season = Sun.nextSeason(now);
        for (int i = 0; i < SEASONS; i++) {
            year.seasons.add(season);
            season = Sun.nextSeason(season.time + MINUTE_MS);
        }
        for (Eclipse eclipse : Eclipse.upcoming(catalog, now, ECLIPSES, true)) {
            year.eclipses.add(eclipse);
            Eclipse.Local local = days.local(eclipse);
            year.eclipseLocals.add(location == null ? null : local != null ? local : eclipse.local(latitude, longitude));
        }
        Sun.SignChange change = Sun.nextSign(now);
        for (int i = 0; i < SIGNS; i++) {
            year.signChanges.add(change);
            change = Sun.nextSign(change.time + MINUTE_MS);
        }
        nextEvents(year);
        return year;
    }

    // The solstices and equinoxes are the Sun entering Aries, Cancer, Libra and Capricorn, so such a sign change
    // joins its season as one event
    private static void nextEvents(SunYear year) {
        List<Event> events = new ArrayList<>();
        for (Sun.Season season : year.seasons) {
            Event event = new Event(season.time);
            event.season = season.season;
            events.add(event);
        }
        for (Sun.SignChange change : year.signChanges) {
            Event joined = null;
            for (Event event : events) {
                if (event.season != -1 && Math.abs(event.time - change.time) < DAY_MS / 2) {
                    joined = event;
                }
            }
            if (joined == null) {
                joined = new Event(change.time);
                events.add(joined);
            }
            joined.sign = change.sign;
        }
        for (int i = 0; i < year.eclipses.size(); i++) {
            Event event = new Event(year.eclipses.get(i).greatest);
            event.eclipse = year.eclipses.get(i);
            event.local = year.eclipseLocals.get(i);
            events.add(event);
        }
        events.sort((a, b) -> Long.compare(a.time, b.time));
        year.next.addAll(events.subList(0, Math.min(NEXT_EVENTS, events.size())));
    }

    private void bind(SunYear year) {
        View view = this.content;
        float density = getResources().getDisplayMetrics().density;
        int sunColor = ContextCompat.getColor(sky(), R.color.astro_sun);
        int tonal = ContextCompat.getColor(sky(), R.color.astro_sun_container);
        bindControls(view, year);
        Sun.Day sun = year.todaySun;

        bindDayChart(view.findViewById(R.id.sunDayChart), year, sunColor);
        boolean polar = sun.polarDay || sun.polarNight;
        view.findViewById(R.id.llSunRiseSet).setVisibility(year.hasPlace && !polar ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.tvSunRise)).setText(clockOrDash(sun.rise));
        ((TextView) view.findViewById(R.id.tvSunSet)).setText(clockOrDash(sun.set));
        TextView polarView = view.findViewById(R.id.tvSunPolar);
        polarView.setVisibility(year.hasPlace && polar ? View.VISIBLE : View.GONE);
        polarView.setText(sun.polarDay ? R.string.sun_polar_day : R.string.sun_polar_night);

        String dayLength = sun.polarDay ? getString(R.string.sun_polar_day)
                : (sun.polarNight ? getString(R.string.sun_polar_night) : this.formatter.duration(sun.dayLength));
        tile(view, R.id.tileDayLength, R.drawable.ic_astro_day_length, R.string.sun_tile_day_length,
                dayLength, getString(R.string.sun_tile_since_yesterday, this.formatter.durationChange(year.todayChange)));
        tile(view, R.id.tileNoon, R.drawable.ic_astro_noon, R.string.sun_tile_noon,
                this.formatter.clock(sun.noon), getString(R.string.sun_noon_height, String.format(Locale.getDefault(), "%.0f", sun.noonAltitude)));
        tile(view, R.id.tileTwilight, R.drawable.ic_astro_twilight, R.string.sun_tile_twilight,
                clockOrDash(sun.dawn) + " \u2013 " + clockOrDash(sun.dusk), null);
        tile(view, R.id.tileSign, AstroFormatter.zodiacIcon(year.sign), R.string.sun_tile_sign, this.formatter.zodiacName(year.sign),
                getString(R.string.moon_sign_until, this.formatter.dateTime(year.signUntil)));
        // Without a location only the sign holds, so it takes the whole row
        int placeTiles = year.hasPlace ? View.VISIBLE : View.GONE;
        view.findViewById(R.id.tileDayLength).setVisibility(placeTiles);
        view.findViewById(R.id.spaceSunTiles).setVisibility(placeTiles);
        view.findViewById(R.id.llSunTiles2).setVisibility(placeTiles);
        bindNext(view.findViewById(R.id.cardSunNext), year);

        List<Line> seasonLines = new ArrayList<>();
        for (Sun.Season season : year.seasons) {
            // The icon of the season named, which swaps in the southern hemisphere
            int named = year.southernHemisphere ? (season.season + 2) % 4 : season.season;
            long days = Math.max(0, (Math.floorDiv(year.local(season.time), DAY_MS) * DAY_MS - year.today) / DAY_MS);
            String when = getString(R.string.sun_in_days, this.formatter.dateTime(year.local(season.time)), days,
                    getResources().getQuantityString(R.plurals.days, (int) days));
            seasonLines.add(new Line(AstroFormatter.seasonIcon(named), this.formatter.seasonName(season.season, year.southernHemisphere), when, null, null));
        }
        LinearLayout seasons = view.findViewById(R.id.llSunSeasons);
        bindLines(seasons, seasonLines, line -> eventRow(seasons, ContextCompat.getDrawable(requireContext(), line.icon), line.name, line.time, null, null));
        List<Line> eclipseLines = new ArrayList<>();
        for (int i = 0; i < year.eclipses.size(); i++) {
            Eclipse eclipse = year.eclipses.get(i);
            Eclipse.Local local = year.eclipseLocals.get(i);
            eclipseLines.add(new Line(AstroFormatter.eclipseIcon(eclipse), this.formatter.eclipseName(eclipse), this.formatter.dateTime(year.local(eclipse.greatest)),
                    visibility(getResources(), this.formatter, eclipse, local, year.timeZone), local == null ? null : local.visible));
        }
        LinearLayout eclipses = view.findViewById(R.id.llSunEclipses);
        bindLines(eclipses, eclipseLines, line -> eventRow(eclipses, ContextCompat.getDrawable(requireContext(), line.icon), line.name, line.time, line.visibility, line.seen));

        SunYearChart chart = view.findViewById(R.id.sunYearChart);
        TextView selectedDay = view.findViewById(R.id.tvSunSelectedDay);
        int[] rise = new int[YEAR_DAYS];
        int[] set = new int[YEAR_DAYS];
        int[] dawn = new int[YEAR_DAYS];
        int[] dusk = new int[YEAR_DAYS];
        boolean[] polarDay = new boolean[YEAR_DAYS];
        for (int i = 0; i < YEAR_DAYS; i++) {
            long dayStart = year.today + i * DAY_MS;
            Sun.Day day = year.days[i];
            rise[i] = minutesOfDay(day.rise, dayStart);
            set[i] = minutesOfDay(day.set, dayStart);
            dawn[i] = minutesOfDay(day.dawn, dayStart);
            dusk[i] = minutesOfDay(day.dusk, dayStart);
            polarDay[i] = day.polarDay;
        }
        chart.setData(year.today, rise, set, dawn, dusk, polarDay);
        chart.setSelectionColor(ContextCompat.getColor(sky(), R.color.astro_sun_selection));
        int[] markDays = new int[year.seasons.size()];
        Drawable[] markIcons = new Drawable[year.seasons.size()];
        for (int i = 0; i < markDays.length; i++) {
            Sun.Season season = year.seasons.get(i);
            markDays[i] = (int) ((Math.floorDiv(year.local(season.time), DAY_MS) * DAY_MS - year.today) / DAY_MS);
            int named = year.southernHemisphere ? (season.season + 2) % 4 : season.season;
            markIcons[i] = ContextCompat.getDrawable(requireContext(), AstroFormatter.seasonIcon(named));
        }
        chart.setMarks(markDays, markIcons);
        int minDay = -1;
        int maxDay = -1;
        for (int i = 0; i < YEAR_DAYS; i++) {
            Sun.Day day = year.days[i];
            if (day.polarDay || day.polarNight) {
                continue;
            }
            if (minDay == -1 || day.dayLength < year.days[minDay].dayLength) {
                minDay = i;
            }
            if (maxDay == -1 || day.dayLength > year.days[maxDay].dayLength) {
                maxDay = i;
            }
        }
        chart.setDayLengthExtremes(minDay, minDay == -1 ? null : this.formatter.duration(year.days[minDay].dayLength),
                maxDay, maxDay == -1 ? null : this.formatter.duration(year.days[maxDay].dayLength));
        chart.setPolarNames(getString(R.string.sun_polar_day), getString(R.string.sun_polar_night));
        chart.setOnDaySelectedListener(index -> selectedDay.setText(ArrowText.of(requireContext(), dayText(year, index))));
        // The year needs a place; with one it is folded or not as the user left it
        View yearHeader = view.findViewById(R.id.headerSunYear);
        View yearContent = view.findViewById(R.id.llSunYear);
        if (year.hasPlace) {
            CollapsibleSection.refresh(yearHeader, yearContent, "sun_year");
        } else {
            yearHeader.setVisibility(View.GONE);
            yearContent.setVisibility(View.GONE);
        }
        selectedDay.setText(ArrowText.of(requireContext(), dayText(year, chart.getSelected())));

        List<Line> signLines = new ArrayList<>();
        for (Sun.SignChange change : year.signChanges) {
            signLines.add(new Line(AstroFormatter.zodiacIcon(change.sign), this.formatter.sunInSignName(change.sign),
                    this.formatter.dateTime(year.local(change.time)), null, null));
        }
        LinearLayout signs = view.findViewById(R.id.llSunSigns);
        bindLines(signs, signLines, line -> {
            View row = LayoutInflater.from(signs.getContext()).inflate(R.layout.item_astro_line, signs, false);
            // The sign's icon in the Sun's amber on its container, as in the next event card
            ImageView badge = row.findViewById(R.id.ivLineBadge);
            badge.setImageResource(line.icon);
            badge.setImageTintList(android.content.res.ColorStateList.valueOf(sunColor));
            androidx.core.view.ViewCompat.setBackgroundTintList(badge, android.content.res.ColorStateList.valueOf(tonal));
            badge.setVisibility(View.VISIBLE);
            ((TextView) row.findViewById(R.id.tvLineName)).setText(line.name);
            ((TextView) row.findViewById(R.id.tvLineTime)).setText(line.time);
            return row;
        });
    }

    // At every minute the rows come out the same until a day or an event passes, or another place or moment is
    // picked: they are made again only when what they show changes
    private static void bindLines(LinearLayout parent, List<Line> lines, Function<Line, View> row) {
        if (lines.equals(parent.getTag(R.id.tag_shown_lines))) {
            return;
        }
        parent.setTag(R.id.tag_shown_lines, lines);
        parent.removeAllViews();
        for (Line line : lines) {
            parent.addView(row.apply(line));
        }
    }

    // The nearest event large with the days left in a pill, the one after it in two lines
    private void bindNext(View card, SunYear year) {
        card.setVisibility(year.next.isEmpty() ? View.GONE : View.VISIBLE);
        if (year.next.isEmpty()) {
            return;
        }
        Event first = year.next.get(0);
        eventIcon(card.findViewById(R.id.ivNextIcon), first, year, 12);
        ((TextView) card.findViewById(R.id.tvNextName)).setText(eventName(first, year));
        String time = this.formatter.dateTime(year.local(first.time));
        if (first.season != -1 && first.sign != -1) {
            time += " · " + this.formatter.sunInSignName(first.sign);
        } else if (first.local != null) {
            time += " · " + getString(first.local.visible ? R.string.eclipse_alert_visible : R.string.eclipse_alert_not_visible);
        }
        ((TextView) card.findViewById(R.id.tvNextTime)).setText(time);
        ((TextView) card.findViewById(R.id.tvNextDays)).setText(daysLeft(first, year));
        boolean hasSecond = year.next.size() > 1;
        card.findViewById(R.id.vNextDivider).setVisibility(hasSecond ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.llNextSecond).setVisibility(hasSecond ? View.VISIBLE : View.GONE);
        if (hasSecond) {
            Event second = year.next.get(1);
            eventIcon(card.findViewById(R.id.ivNextSecondIcon), second, year, 5);
            ((TextView) card.findViewById(R.id.tvNextSecond)).setText(eventName(second, year));
            ((TextView) card.findViewById(R.id.tvNextSecondDate)).setText(this.formatter.date(year.local(second.time)));
            ((TextView) card.findViewById(R.id.tvNextSecondDays)).setText(daysLeft(second, year));
        }
    }

    private String eventName(Event event, SunYear year) {
        if (event.season != -1) {
            return this.formatter.seasonName(event.season, year.southernHemisphere);
        }
        return event.eclipse != null ? this.formatter.eclipseName(event.eclipse) : this.formatter.sunInSignName(event.sign);
    }

    // A season's or an eclipse's own picture; a sign's in amber on a faint amber disk, padded by paddingDp
    private void eventIcon(ImageView view, Event event, SunYear year, int paddingDp) {
        if (event.season != -1 || event.eclipse != null) {
            int named = year.southernHemisphere ? (event.season + 2) % 4 : event.season;
            view.setImageResource(event.eclipse != null ? AstroFormatter.eclipseIcon(event.eclipse) : AstroFormatter.seasonIcon(named));
            view.setImageTintList(null);
            view.setBackground(null);
            view.setPadding(0, 0, 0, 0);
            return;
        }
        int padding = Math.round(paddingDp * getResources().getDisplayMetrics().density);
        view.setImageResource(AstroFormatter.zodiacIcon(event.sign));
        view.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(sky(), R.color.astro_sun)));
        view.setBackgroundResource(R.drawable.bg_magnitude);
        androidx.core.view.ViewCompat.setBackgroundTintList(view, android.content.res.ColorStateList.valueOf(ContextCompat.getColor(sky(), R.color.astro_sun_container)));
        view.setPadding(padding, padding, padding, padding);
    }

    // "in 21 days", "today"
    private String daysLeft(Event event, SunYear year) {
        int days = (int) Math.max(0, (Math.floorDiv(year.local(event.time), DAY_MS) * DAY_MS - year.today) / DAY_MS);
        return days == 0 ? getString(R.string.sun_event_today) : getString(R.string.sun_event_in_days, days,
                getResources().getQuantityString(R.plurals.days, days));
    }

    // "Fri, 23 October · ↑ 08:14 ↓ 18:01 · 9 h 48 min"
    private String dayText(SunYear year, int index) {
        Sun.Day day = year.days[index];
        String length = day.polarDay ? getString(R.string.sun_polar_day)
                : (day.polarNight ? getString(R.string.sun_polar_night) : this.formatter.duration(day.dayLength));
        return this.formatter.day(year.today + index * DAY_MS) + " · " + this.formatter.up(day.rise) + "  " + this.formatter.down(day.set)
                + " · " + length;
    }

    private static int minutesOfDay(long localTime, long dayStart) {
        return localTime == Sun.NONE ? -1 : (int) ((localTime - dayStart) / MINUTE_MS);
    }

    /** "Seen here: 11:41 – 13:10, 15% covered", "Not seen here", or null without a place */
    static String visibility(Resources resources, AstroFormatter formatter, Eclipse eclipse, Eclipse.Local local, TimeZone timeZone) {
        if (local == null) {
            return null;
        }
        if (!local.visible) {
            return resources.getString(R.string.eclipse_not_visible);
        }
        if (eclipse.solar) {
            return resources.getString(R.string.eclipse_visible_covered, formatter.clock(local.start + timeZone.getOffset(local.start)),
                    formatter.clock(local.end + timeZone.getOffset(local.end)),
                    formatter.illumination(local.obscuration));
        }
        return resources.getString(R.string.eclipse_visible, formatter.clock(local.start + timeZone.getOffset(local.start)),
                formatter.clock(local.end + timeZone.getOffset(local.end)));
    }

    /** A row of item_astro_event: the picture, the name, the time and the visibility if any, with an eye when seen */
    static View eventRow(ViewGroup parent, Drawable icon, String name, String time, String visibility, Boolean seen) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_astro_event, parent, false);
        ((ImageView) row.findViewById(R.id.ivEvent)).setImageDrawable(icon);
        ((TextView) row.findViewById(R.id.tvEventName)).setText(name);
        ((TextView) row.findViewById(R.id.tvEventTime)).setText(time);
        TextView visibilityView = row.findViewById(R.id.tvEventVisibility);
        visibilityView.setText(visibility);
        visibilityView.setVisibility(visibility == null ? View.GONE : View.VISIBLE);
        if (Boolean.TRUE.equals(seen)) {
            int primary = MaterialColors.getColor(visibilityView, androidx.appcompat.R.attr.colorPrimary);
            float density = parent.getResources().getDisplayMetrics().density;
            visibilityView.setTextColor(primary);
            Drawable eye = ContextCompat.getDrawable(parent.getContext(), R.drawable.ic_astro_eye).mutate();
            eye.setTint(primary);
            int eyeSize = Math.round(16 * density);
            eye.setBounds(0, 0, eyeSize, eyeSize);
            visibilityView.setCompoundDrawablesRelative(eye, null, null, null);
            visibilityView.setCompoundDrawablePadding(Math.round(4 * density));
        }
        return row;
    }

    // A tile with the icon resource, none for 0
    private static void tile(View root, int tileId, int icon, int label, String value, String note) {
        View tile = root.findViewById(tileId);
        ImageView iconView = tile.findViewById(R.id.ivTileIcon);
        if (icon != 0) {
            iconView.setImageResource(icon);
        }
        iconView.setVisibility(icon != 0 ? View.VISIBLE : View.GONE);
        ((TextView) tile.findViewById(R.id.tvTileLabel)).setText(label);
        ((TextView) tile.findViewById(R.id.tvTileValue)).setText(value);
        TextView noteView = tile.findViewById(R.id.tvTileNote);
        noteView.setText(note);
        noteView.setVisibility(note == null ? View.GONE : View.VISIBLE);
    }

    // The strip of days around the day shown; the day dragged or flung into the window becomes the day shown as it
    // comes, as the arrows by the date make it
    private void bindDayChart(SunDayChart chart, SunYear year, int sunColor) {
        chart.setColor(sunColor, true);
        chart.setVisibility(year.hasPlace ? View.VISIBLE : View.GONE);
        bindDayStrip(chart, year);
    }

    private String clockOrDash(long localTime) {
        return localTime == Sun.NONE ? "—" : this.formatter.clock(localTime);
    }
}
