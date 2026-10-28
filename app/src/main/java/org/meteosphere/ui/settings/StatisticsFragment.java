package org.meteosphere.ui.settings;

import android.animation.LayoutTransition;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import com.google.android.material.color.MaterialColors;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.meteosphere.R;
import org.meteosphere.data.WeatherApiException;
import org.meteosphere.data.WeatherProviders;
import org.meteosphere.data.http.RequestStats;
import org.meteosphere.data.wpr.RemoteWeatherProvider;
import org.meteosphere.ui.common.BackgroundWork;

/** The requests a day to the data sources, from this device and from the server, wpr, in the builds with one */
public final class StatisticsFragment extends Fragment {

    private static final int TIMEOUT_MS = 15000;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    // The known sources in the tables' order, as wpr and the app count them, with their names and colours
    private static final String[] SOURCES = {"open-meteo", "open-meteo-geocoding", "foreca", "noaa", "geonames", "usgs", "helioviewer"};
    private static final String[] NAMES = {"Open-Meteo", "Open-Meteo Geocoding", "Foreca", "NOAA SWPC", "GeoNames", "USGS", "Helioviewer"};
    private static final int[] COLORS = {R.color.stats_open_meteo, R.color.stats_open_meteo_geocoding, R.color.stats_foreca, R.color.stats_noaa,
            R.color.stats_geonames, R.color.stats_usgs, R.color.stats_helioviewer};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Both sides' counts, dropped with the view
    private final BackgroundWork work = new BackgroundWork(this.executor);
    private final NumberFormat numbers = NumberFormat.getIntegerInstance();
    // The day picked on a chart over the table, made when one is first picked
    private SimpleDateFormat dayFormat;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SettingsSegments.setListPadding(view, true);
        // The side that asks the sources for the weather goes first: the server, unless the device does it itself
        String dataProvider = WeatherProviders.dataProvider(PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.key_data_provider), WeatherProviders.DEFAULT_DATA_PROVIDER));
        View serverView = view.findViewById(R.id.statsServer);
        ViewGroup column = (ViewGroup) serverView.getParent();
        // The public build has no server to ask
        if (!WeatherProviders.hasServer()) {
            column.removeView(serverView);
        } else if (!WeatherProviders.LOCAL.equals(dataProvider)) {
            column.removeView(serverView);
            column.addView(serverView, 0);
        }
        // The server's counts come a while later: their section grows smoothly, the one under it moving down with it
        growSmoothly(column);
        // The only side needs no title
        Section device = new Section(view.findViewById(R.id.statsDevice), WeatherProviders.hasServer() ? R.string.stats_on_device : 0);
        this.work.run(RequestStats::load, counts -> device.show(counts, R.string.stats_no_requests));
        if (!WeatherProviders.hasServer()) {
            return;
        }
        Section server = new Section(serverView, R.string.stats_on_server);
        this.work.run(() -> {
            try {
                return RemoteWeatherProvider.loadStats(TIMEOUT_MS);
            } catch (WeatherApiException e) {
                return null;
            }
        }, counts -> server.show(counts, R.string.stats_failed));
    }

    @Override
    public void onDestroyView() {
        this.work.drop();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        this.executor.shutdown();
        super.onDestroy();
    }

    private static void growSmoothly(ViewGroup group) {
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        group.setLayoutTransition(transition);
    }

    /** One side's requests: the chart and the table under it */
    private final class Section {
        private final View chartFrame;
        private final RequestChartView chart;
        private final TextView state;
        private final LinearLayout table;
        private final List<int[]> rows = new ArrayList<>();
        private final List<View> rowViews = new ArrayList<>();
        // The heading of the day's column: today, or the day picked
        private TextView dayHeading;
        private long first;
        private long today;

        // Without a title for 0
        Section(View root, @StringRes int title) {
            TextView titleView = root.findViewById(R.id.tvStatsTitle);
            if (title != 0) {
                titleView.setText(title);
            } else {
                titleView.setVisibility(View.GONE);
            }
            growSmoothly((ViewGroup) root);
            this.chartFrame = root.findViewById(R.id.flStatsChart);
            this.chart = root.findViewById(R.id.chartStats);
            this.state = root.findViewById(R.id.tvStatsState);
            this.table = root.findViewById(R.id.llStatsTable);
            showState(R.string.stats_loading);
        }

        private void showState(@StringRes int text) {
            this.chart.setVisibility(View.INVISIBLE);
            this.state.setVisibility(View.VISIBLE);
            this.state.setText(text);
            this.table.setVisibility(View.GONE);
            SettingsSegments.apply(this.chartFrame, true, true);
        }

        // The counts, or the text for none
        void show(RequestStats.Counts counts, @StringRes int none) {
            if (getView() == null) {
                return;
            }
            if (counts == null || counts.bySource.isEmpty()) {
                showState(counts == null ? none : R.string.stats_no_requests);
                return;
            }
            this.first = counts.first;
            this.today = counts.today;
            this.chart.setVisibility(View.VISIBLE);
            this.state.setVisibility(View.GONE);
            this.table.setVisibility(View.VISIBLE);
            SettingsSegments.apply(this.chartFrame, true, false);
            SettingsSegments.apply(this.table, false, true);
            this.table.removeAllViews();
            this.rows.clear();
            this.rowViews.clear();
            addHeader();
            List<RequestChartView.Series> series = new ArrayList<>();
            Map<String, int[]> others = new TreeMap<>(counts.bySource);
            for (int i = 0; i < SOURCES.length; i++) {
                int[] sourceCounts = others.remove(SOURCES[i]);
                if (sourceCounts != null) {
                    series.add(addRow(NAMES[i], COLORS[i], sourceCounts));
                }
            }
            // A host wpr or the app doesn't know by name
            for (Map.Entry<String, int[]> other : others.entrySet()) {
                series.add(addRow(other.getKey(), R.color.stats_other, other.getValue()));
            }
            showDay(-1);
            this.chart.setOnRangeListener(this::showRange);
            this.chart.setOnDayListener(this::showDay);
            this.chart.setData(counts.first, series);
        }

        private void addHeader() {
            View header = LayoutInflater.from(this.table.getContext()).inflate(R.layout.stats_table_row, this.table, false);
            header.findViewById(R.id.ivStatsColor).setVisibility(View.INVISIBLE);
            int color = MaterialColors.getColor(header, com.google.android.material.R.attr.colorOnSurfaceVariant);
            int[] ids = {R.id.tvStatsToday, R.id.tvStatsMax, R.id.tvStatsMin};
            int[] texts = {R.string.stats_today, R.string.stats_max, R.string.stats_min};
            for (int i = 0; i < ids.length; i++) {
                TextView label = header.findViewById(ids[i]);
                label.setText(texts[i]);
                label.setTextColor(color);
                label.setTypeface(Typeface.DEFAULT);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            }
            this.dayHeading = header.findViewById(R.id.tvStatsToday);
            this.table.addView(header);
        }

        private RequestChartView.Series addRow(String name, int colorId, int[] counts) {
            View row = LayoutInflater.from(this.table.getContext()).inflate(R.layout.stats_table_row, this.table, false);
            int color = ContextCompat.getColor(row.getContext(), colorId);
            ((ImageView) row.findViewById(R.id.ivStatsColor)).setImageTintList(ColorStateList.valueOf(color));
            ((TextView) row.findViewById(R.id.tvStatsSource)).setText(name);
            this.table.addView(row);
            this.rows.add(counts);
            this.rowViews.add(row);
            return new RequestChartView.Series(color, counts);
        }

        // The requests on the day picked on the chart, as days since 1970, or today's for none: -1
        private void showDay(long day) {
            if (day < 0 || day == this.today) {
                this.dayHeading.setText(R.string.stats_today);
                day = this.today;
            } else {
                if (StatisticsFragment.this.dayFormat == null) {
                    Locale locale = getResources().getConfiguration().getLocales().get(0);
                    StatisticsFragment.this.dayFormat = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale);
                    StatisticsFragment.this.dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                this.dayHeading.setText(StatisticsFragment.this.dayFormat.format(new Date(day * DAY_MS)));
            }
            int index = (int) (day - this.first);
            for (int i = 0; i < this.rows.size(); i++) {
                int[] counts = this.rows.get(i);
                int count = index >= 0 && index < counts.length ? counts[index] : 0;
                ((TextView) this.rowViews.get(i).findViewById(R.id.tvStatsToday)).setText(StatisticsFragment.this.numbers.format(count));
            }
        }

        // The most and the fewest requests a day among the full days in sight
        private void showRange(long shownFirst, long shownLast) {
            for (int i = 0; i < this.rows.size(); i++) {
                int[] counts = this.rows.get(i);
                int from = (int) (shownFirst - this.first);
                int to = Math.min((int) (shownLast - this.first), counts.length - 2);
                int max = -1;
                int min = -1;
                for (int day = Math.max(0, from); day <= to; day++) {
                    max = Math.max(max, counts[day]);
                    min = min < 0 ? counts[day] : Math.min(min, counts[day]);
                }
                View row = this.rowViews.get(i);
                ((TextView) row.findViewById(R.id.tvStatsMax)).setText(max < 0 ? "—" : StatisticsFragment.this.numbers.format(max));
                ((TextView) row.findViewById(R.id.tvStatsMin)).setText(min < 0 ? "—" : StatisticsFragment.this.numbers.format(min));
            }
        }
    }
}
