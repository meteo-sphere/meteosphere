package org.meteosphere.ui.quakes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.meteosphere.R;
import org.meteosphere.data.quake.Earthquake;

/** The summary card over the earthquakes' list: how many, and the strongest with when and how far */
final class EarthquakeSummary {

    // The strongest named in the summary card
    private static final int SUMMARY_STRONGEST = 3;
    // From this magnitude all are named, however many
    private static final double SUMMARY_ALL_FROM = 7;
    // A number with its groups of thousands, as the languages write them: "16 288", "16,288", "16.288"
    private static final Pattern NUMBER = Pattern.compile("\\d+([ \\u00a0\\u202f,.]\\d{3})*");

    private EarthquakeSummary() {
    }

    // How many, the strongest large and the next strongest in lines, all from magnitude 7, with when and how far; a
    // card kept through a change rolls its title to the new count and fades its lines in when they changed
    static void bind(View card, EarthquakeListAdapter list, boolean changing) {
        String period = card.getContext().getString(EarthquakeFormatter.periodTitle(list.days));
        roll(card.findViewById(R.id.tvSummaryTitle), period + " \u00b7 " + list.formatter.count(list.earthquakes.size()), changing);
        View strongestRow = card.findViewById(R.id.rowSummaryStrongest);
        LinearLayout more = card.findViewById(R.id.llSummaryMore);
        String linesBefore = lines(strongestRow, more);
        List<Earthquake> strongest = new ArrayList<>(list.earthquakes);
        strongest.sort(Earthquake.STRONGEST_FIRST);
        long now = System.currentTimeMillis();
        row(strongestRow, strongest.get(0), list, now);
        more.removeAllViews();
        // Every one of magnitude 7 and above, and at least the three strongest
        int shown = SUMMARY_STRONGEST;
        while (shown < strongest.size() && strongest.get(shown).magnitude >= SUMMARY_ALL_FROM) {
            shown++;
        }
        for (int i = 1; i < Math.min(shown, strongest.size()); i++) {
            View row = LayoutInflater.from(more.getContext()).inflate(R.layout.item_quake_summary_row, more, false);
            row(row, strongest.get(i), list, now);
            more.addView(row);
        }
        card.findViewById(R.id.vSummaryDivider).setVisibility(more.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        for (View lines : new View[]{strongestRow, more}) {
            lines.animate().cancel();
            lines.setAlpha(1);
            if (changing && !linesBefore.isEmpty() && !linesBefore.equals(lines(strongestRow, more))) {
                lines.setAlpha(0);
                lines.animate().alpha(1).setStartDelay(0).setDuration(300).setInterpolator(QuakeMotion.DECELERATE).start();
            }
        }
    }

    // What the lines say, to tell a change
    private static String lines(View strongestRow, LinearLayout more) {
        StringBuilder text = new StringBuilder();
        for (int i = -1; i < more.getChildCount(); i++) {
            View row = i < 0 ? strongestRow : more.getChildAt(i);
            text.append(((TextView) row.findViewById(R.id.tvRowPlace)).getText()).append('\n')
                    .append(((TextView) row.findViewById(R.id.tvRowWhen)).getText()).append('\n');
        }
        return text.toString().trim();
    }

    // The title rolling up to its new text when changing, else just set
    private static void roll(TextView title, String text, boolean changing) {
        CharSequence shown = title.getText();
        title.animate().cancel();
        if (!changing || shown.length() == 0 || TextUtils.equals(shown, text) || title.getHeight() == 0) {
            title.setText(text);
            title.setAlpha(1);
            title.setTranslationY(0);
            return;
        }
        float shift = title.getHeight() * 0.7f;
        title.animate().translationY(-shift).alpha(0).setStartDelay(0).setDuration(130).setInterpolator(QuakeMotion.ACCELERATE)
                .withEndAction(() -> {
                    title.setText(text);
                    title.setTranslationY(shift);
                    title.animate().translationY(0).alpha(1).setDuration(240).setInterpolator(QuakeMotion.DECELERATE).start();
                }).start();
    }

    // "26 Sep · 16 288 km" with the numbers, today and yesterday in bold, the distance when there is a location
    private static CharSequence whenAndHowFar(Context context, Earthquake earthquake, EarthquakeListAdapter list, long now) {
        String when = list.formatter.shortDate(earthquake.time, now);
        SpannableStringBuilder text = new SpannableStringBuilder(list.hasReference ? when + " \u00b7 " + list.distance(earthquake) : when);
        Matcher number = NUMBER.matcher(text);
        while (number.find()) {
            text.setSpan(new StyleSpan(Typeface.BOLD), number.start(), number.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Today's "6 h ago" whole, and the word "yesterday" before the date
        long day = EarthquakeFormatter.dayStart(earthquake.time);
        int boldTo = day == EarthquakeFormatter.dayStart(now) ? when.length()
                : (day == EarthquakeFormatter.dayStart(now - 24 * 60 * 60 * 1000L) ? yesterdayLength(context, when) : 0);
        if (boldTo > 0) {
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, boldTo, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    // The word "yesterday" of "yesterday, 1 Oct", "昨天，10月1日", without the punctuation after it
    private static int yesterdayLength(Context context, String when) {
        int length = Math.min(context.getString(R.string.earthquakes_yesterday_short).indexOf("%s"), when.length());
        while (length > 0 && !Character.isLetter(when.charAt(length - 1))) {
            length--;
        }
        return length;
    }

    /** A line of the card, or the map's for the dot tapped: the magnitude, the place, when and how far */
    static void row(View row, Earthquake earthquake, EarthquakeListAdapter list, long now) {
        TextView magnitude = row.findViewById(R.id.tvRowMagnitude);
        magnitude.setText(list.formatter.magnitude(earthquake.magnitude));
        magnitude.setTextColor(EarthquakeFormatter.magnitudeTextColor(earthquake.magnitude));
        ViewCompat.setBackgroundTintList(magnitude,
                ColorStateList.valueOf(EarthquakeFormatter.magnitudeColor(earthquake.magnitude)));
        ((TextView) row.findViewById(R.id.tvRowPlace)).setText(earthquake.placeName());
        ((TextView) row.findViewById(R.id.tvRowWhen)).setText(whenAndHowFar(row.getContext(), earthquake, list, now));
        row.setOnClickListener(view -> list.open(earthquake));
    }
}
