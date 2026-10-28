package org.meteosphere.ui.quakes;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.transition.Transition;
import androidx.transition.TransitionListenerAdapter;
import androidx.transition.TransitionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.transition.MaterialContainerTransform;
import java.util.Locale;
import org.meteosphere.R;
import org.meteosphere.data.quake.Earthquake;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.common.EdgeToEdgeUtils;

/**
 * One earthquake: its magnitude, place and how long ago on a card in the magnitude's color, the tsunami note, PAGER
 * level and felt reports when USGS gives them, tiles of the time, depth, distance from the list's location and the
 * magnitude scale, and a button to its USGS page. The content of EarthquakeActivity, or laid over the whole window of
 * the list it was opened from: then it grows at once out of a band at the top of the list, whichever line opened it,
 * and Back shrinks it into the band again.
 */
public class EarthquakeFragment extends Fragment {

    private static final String TAG = "EarthquakeFragment";
    private static final String TAG_OVER = "earthquake_over";
    private static final String TSUNAMI_URL = "https://www.tsunami.gov";
    // Degrees of longitude across the map at the most, closer only when the location is near
    private static final double MAP_SPAN = 140;
    private static final long GROW_MS = 350;
    private static final long SHRINK_MS = 400;

    private static final String EXTRA_ID = "id";
    private static final String EXTRA_MAGNITUDE = "magnitude";
    private static final String EXTRA_PLACE = "place";
    private static final String EXTRA_TIME = "time";
    private static final String EXTRA_LATITUDE = "latitude";
    private static final String EXTRA_LONGITUDE = "longitude";
    private static final String EXTRA_DEPTH = "depth";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_MAGNITUDE_TYPE = "magnitude_type";
    private static final String EXTRA_TSUNAMI = "tsunami";
    private static final String EXTRA_FELT = "felt";
    private static final String EXTRA_ALERT = "alert";
    private static final String EXTRA_REFERENCE_LATITUDE = "reference_latitude";
    private static final String EXTRA_REFERENCE_LONGITUDE = "reference_longitude";
    private static final String EXTRA_REFERENCE_NAME = "reference_name";
    // Laid over a list rather than in its own activity, growing out of a band at its top: the band's left, top, right
    // and bottom in the window
    private static final String ARG_OVER = "over";
    private static final String ARG_BAND = "band";

    private OnBackPressedCallback back;
    private boolean isClosing;

    /** The earthquake's arguments, with its distance from the location if a name is given */
    static Bundle arguments(Earthquake earthquake, double referenceLatitude, double referenceLongitude, String referenceName) {
        Bundle arguments = new Bundle();
        arguments.putString(EXTRA_ID, earthquake.id);
        arguments.putDouble(EXTRA_MAGNITUDE, earthquake.magnitude);
        arguments.putString(EXTRA_PLACE, earthquake.place);
        arguments.putLong(EXTRA_TIME, earthquake.time);
        arguments.putDouble(EXTRA_LATITUDE, earthquake.latitude);
        arguments.putDouble(EXTRA_LONGITUDE, earthquake.longitude);
        arguments.putDouble(EXTRA_DEPTH, earthquake.depthKm);
        arguments.putString(EXTRA_URL, earthquake.url);
        arguments.putString(EXTRA_MAGNITUDE_TYPE, earthquake.magnitudeType);
        arguments.putBoolean(EXTRA_TSUNAMI, earthquake.tsunami);
        arguments.putInt(EXTRA_FELT, earthquake.felt);
        arguments.putString(EXTRA_ALERT, earthquake.alert);
        if (referenceName != null) {
            arguments.putDouble(EXTRA_REFERENCE_LATITUDE, referenceLatitude);
            arguments.putDouble(EXTRA_REFERENCE_LONGITUDE, referenceLongitude);
            arguments.putString(EXTRA_REFERENCE_NAME, referenceName);
        }
        return arguments;
    }

    private static Earthquake earthquake(Bundle arguments) {
        return new Earthquake(arguments.getString(EXTRA_ID), arguments.getDouble(EXTRA_MAGNITUDE, 0), arguments.getString(EXTRA_PLACE),
                arguments.getLong(EXTRA_TIME, 0), arguments.getDouble(EXTRA_LATITUDE, 0), arguments.getDouble(EXTRA_LONGITUDE, 0),
                arguments.getDouble(EXTRA_DEPTH, 0), arguments.getString(EXTRA_URL), arguments.getString(EXTRA_MAGNITUDE_TYPE),
                arguments.getBoolean(EXTRA_TSUNAMI, false), arguments.getInt(EXTRA_FELT, 0), arguments.getString(EXTRA_ALERT));
    }

    /**
     * The earthquake over the activity's whole window, growing out of the band given in the window's coordinates at the
     * top of the list, whichever line opened it, and shrinking back into it; one at a time, a tap on another line while
     * one shows does nothing
     */
    static void showOver(FragmentActivity activity, Bundle arguments, Rect band) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG_OVER) != null || activity.getSupportFragmentManager().isStateSaved()) {
            return;
        }
        Bundle over = new Bundle(arguments);
        over.putBoolean(ARG_OVER, true);
        over.putIntArray(ARG_BAND, new int[]{band.left, band.top, band.right, band.bottom});
        EarthquakeFragment fragment = new EarthquakeFragment();
        fragment.setArguments(over);
        // Made at once, so it grows from the next frame on
        activity.getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                .add(android.R.id.content, fragment, TAG_OVER).commitNow();
        fragment.grow();
    }

    private boolean isOver() {
        return getArguments() != null && getArguments().getBoolean(ARG_OVER);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_earthquake, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View appBar = view.findViewById(R.id.appBar);
        if (isOver()) {
            // Under the bars as the screen it lies over is; that one keeps the insets to itself. Made again with the
            // activity, the window has none to tell yet: once it is attached then
            if (!applyBars(view, appBar)) {
                view.post(() -> applyBars(view, appBar));
            }
            this.back = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    close();
                }
            };
            requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), this.back);
        } else {
            EdgeToEdgeUtils.applyToolbarInsets(requireActivity(), appBar);
        }
        MaterialToolbar toolbar = view.findViewById(R.id.tbEarthquake);
        toolbar.setNavigationOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        Bundle arguments = requireArguments();
        Earthquake earthquake = earthquake(arguments);
        AppSettings settings = AppSettings.getInstance();
        EarthquakeFormatter formatter = new EarthquakeFormatter(requireContext(), settings.use24Hours, settings.distanceUnit);
        long now = System.currentTimeMillis();
        int color = EarthquakeFormatter.magnitudeColor(earthquake.magnitude);
        int onColor = EarthquakeFormatter.magnitudeTextColor(earthquake.magnitude);
        String scale = EarthquakeFormatter.scale(earthquake.magnitudeType);

        ((MaterialCardView) view.findViewById(R.id.cardQuakeHero)).setCardBackgroundColor(color);
        TextView magnitude = view.findViewById(R.id.tvQuakeMagnitude);
        magnitude.setText(formatter.magnitude(earthquake.magnitude));
        TextView scaleView = view.findViewById(R.id.tvQuakeScale);
        scaleView.setText(getString(R.string.earthquake_magnitude_scale, scale).trim());
        TextView place = view.findViewById(R.id.tvQuakePlace);
        place.setText(earthquake.placeName());
        TextView ago = view.findViewById(R.id.tvQuakeAgo);
        ago.setText(formatter.agoLong(earthquake.time, now));
        for (TextView text : new TextView[]{magnitude, scaleView, place, ago}) {
            text.setTextColor(onColor);
        }

        String referenceName0 = arguments.getString(EXTRA_REFERENCE_NAME);
        showMap(view, earthquake, color, now, referenceName0 != null ? arguments.getDouble(EXTRA_REFERENCE_LATITUDE, 0) : null,
                referenceName0 != null ? arguments.getDouble(EXTRA_REFERENCE_LONGITUDE, 0) : null);

        ChipGroup badges = view.findViewById(R.id.cgQuakeBadges);
        if (earthquake.tsunami) {
            Chip chip = badge(badges, getString(R.string.earthquake_tsunami),
                    MaterialColors.getColor(badges, androidx.appcompat.R.attr.colorPrimary));
            chip.setChipIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_open_in_new));
            chip.setChipIconTint(ColorStateList.valueOf(MaterialColors.getColor(badges, com.google.android.material.R.attr.colorOnSurface)));
            chip.setOnClickListener(v -> open(TSUNAMI_URL));
        }
        String pager = formatter.pager(earthquake.alert);
        if (pager != null) {
            badge(badges, pager, pagerColor(earthquake.alert));
        }
        if (earthquake.felt > 0) {
            badge(badges, getString(R.string.earthquake_felt, earthquake.felt),
                    MaterialColors.getColor(badges, com.google.android.material.R.attr.colorOnSurfaceVariant));
        }
        badges.setVisibility(badges.getChildCount() > 0 ? View.VISIBLE : View.GONE);

        tile(R.id.tileQuakeTime, R.drawable.ic_quake_time, getString(R.string.earthquake_time), formatter.dateTime(earthquake.time),
                getString(R.string.earthquake_utc, formatter.utcTime(earthquake.time), formatter.agoLong(earthquake.time, now)), color);
        tile(R.id.tileQuakeDepth, R.drawable.ic_depth, getString(R.string.earthquake_depth), formatter.distance(earthquake.depthKm),
                formatter.depthClass(earthquake.depthKm), color);
        String referenceName = arguments.getString(EXTRA_REFERENCE_NAME);
        if (referenceName != null) {
            double latitude = arguments.getDouble(EXTRA_REFERENCE_LATITUDE, 0);
            double longitude = arguments.getDouble(EXTRA_REFERENCE_LONGITUDE, 0);
            // The place by its name, as a name can't be put in the case "from" would need
            tile(R.id.tileQuakeDistance, R.drawable.ic_place, referenceName, formatter.distance(earthquake.distanceKm(latitude, longitude)),
                    formatter.direction(earthquake.bearingFrom(latitude, longitude)), color);
        } else {
            view.findViewById(R.id.tileQuakeDistance).setVisibility(View.GONE);
            view.findViewById(R.id.spaceQuakeTiles).setVisibility(View.GONE);
        }
        tile(R.id.tileQuakeScale, R.drawable.ic_quake_magnitude, getString(R.string.earthquake_magnitude),
                (formatter.magnitude(earthquake.magnitude) + " " + scale).trim(), formatter.scaleName(earthquake.magnitudeType), color);

        View usgs = view.findViewById(R.id.btnQuakeUsgs);
        usgs.setVisibility(earthquake.url != null ? View.VISIBLE : View.GONE);
        usgs.setOnClickListener(v -> open(earthquake.url));
        if (savedInstanceState == null && !isOver()) {
            QuakeMapView map = view.findViewById(R.id.quakeDetailMap);
            map.post(map::playEpicenterWave);
        }
    }

    // The status bar's room over the bar and the navigation bar's under the screen, false when the window has none yet
    private static boolean applyBars(View screen, View appBar) {
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(screen.getRootView());
        if (windowInsets == null) {
            return false;
        }
        Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        appBar.setPadding(appBar.getPaddingLeft(), bars.top, appBar.getPaddingRight(), appBar.getPaddingBottom());
        screen.setPadding(0, 0, 0, bars.bottom);
        return true;
    }

    // The band at the top, an empty view laid out at once in the window's content for the move to start or end at
    private View band(ViewGroup content) {
        int[] band = requireArguments().getIntArray(ARG_BAND);
        int[] at = new int[2];
        content.getLocationInWindow(at);
        int left = band[0] - at[0];
        int top = band[1] - at[1];
        int width = band[2] - band[0];
        int height = band[3] - band[1];
        View view = new View(requireContext());
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left - content.getPaddingLeft();
        params.topMargin = top - content.getPaddingTop();
        content.addView(view, params);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(left, top, left + width, top + height);
        return view;
    }

    // From the band at the top to the whole window; then the wave from the epicenter
    private void grow() {
        View screen = requireView();
        ViewGroup content = requireActivity().findViewById(android.R.id.content);
        if (!QuakeMotion.isOn(requireContext())) {
            shown();
            return;
        }
        View band = band(content);
        MaterialContainerTransform transform = transform(band, screen, true);
        transform.addListener(new TransitionListenerAdapter() {
            @Override
            public void onTransitionEnd(Transition transition) {
                content.removeView(band);
                shown();
            }

            @Override
            public void onTransitionCancel(Transition transition) {
                content.removeView(band);
            }
        });
        screen.setVisibility(View.INVISIBLE);
        TransitionManager.beginDelayedTransition(content, transform);
        screen.setVisibility(View.VISIBLE);
        band.setVisibility(View.INVISIBLE);
    }

    // The band has no line to show: the screen alone grows out of it and fades back into it, on the curves picked, the
    // screen under it not dimmed
    private MaterialContainerTransform transform(View from, View to, boolean growing) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        transform.setStartView(from);
        transform.setEndView(to);
        transform.addTarget(to);
        transform.setDuration(growing ? GROW_MS : SHRINK_MS);
        transform.setInterpolator(growing ? QuakeMotion.EMPHASIZED : QuakeMotion.GENTLE);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setAllContainerColors(Color.TRANSPARENT);
        return transform;
    }

    // Grown: the screen under it out of reach of TalkBack, and the wave from the epicenter
    private void shown() {
        if (getView() == null) {
            return;
        }
        setUnderHidden(true);
        ((QuakeMapView) requireView().findViewById(R.id.quakeDetailMap)).playEpicenterWave();
    }

    private void setUnderHidden(boolean hidden) {
        ViewGroup content = requireActivity().findViewById(android.R.id.content);
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child != getView()) {
                child.setImportantForAccessibility(hidden ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
        }
    }

    // Back over a list: shrinking into the band it grew out of
    private void close() {
        if (this.isClosing || getView() == null) {
            return;
        }
        this.isClosing = true;
        this.back.setEnabled(false);
        View screen = requireView();
        ViewGroup content = requireActivity().findViewById(android.R.id.content);
        // Still growing, it grows to its end at once rather than both moves drawn over each other
        TransitionManager.endTransitions(content);
        setUnderHidden(false);
        if (!QuakeMotion.isOn(requireContext())) {
            remove();
            return;
        }
        View band = band(content);
        MaterialContainerTransform transform = transform(screen, band, false);
        transform.addListener(new TransitionListenerAdapter() {
            @Override
            public void onTransitionEnd(Transition transition) {
                content.removeView(band);
                remove();
            }

            @Override
            public void onTransitionCancel(Transition transition) {
                content.removeView(band);
                remove();
            }
        });
        band.setVisibility(View.INVISIBLE);
        TransitionManager.beginDelayedTransition(content, transform);
        screen.setVisibility(View.INVISIBLE);
        band.setVisibility(View.VISIBLE);
    }

    private void remove() {
        if (isAdded()) {
            getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }

    // The world around the epicenter, with the location in view too when there is one
    private void showMap(View view, Earthquake earthquake, int color, long now, Double referenceLatitude, Double referenceLongitude) {
        QuakeMapView map = view.findViewById(R.id.quakeDetailMap);
        map.setLand(WorldLand.get(requireContext()));
        map.setDots(java.util.Collections.singletonList(new QuakeMapView.Dot(earthquake.id, earthquake.time, earthquake.latitude,
                earthquake.longitude, earthquake.magnitude, color, EarthquakeFormatter.isRecent(earthquake.time, now))), QuakeMapView.MOTION_NONE);
        if (referenceLatitude != null) {
            map.setReference(referenceLatitude, referenceLongitude, false);
            map.showPoints(new double[]{earthquake.latitude, referenceLatitude}, new double[]{earthquake.longitude, referenceLongitude}, MAP_SPAN);
        } else {
            map.showPoints(new double[]{earthquake.latitude}, new double[]{earthquake.longitude}, MAP_SPAN);
        }
    }

    // A badge in a faint wash of its color
    private Chip badge(ChipGroup group, String text, int color) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setClickable(false);
        chip.setChipBackgroundColor(ColorStateList.valueOf((color & 0x00ffffff) | 0x33000000));
        chip.setChipStrokeWidth(0);
        chip.setTextColor(MaterialColors.getColor(group, com.google.android.material.R.attr.colorOnSurface));
        group.addView(chip);
        return chip;
    }

    private static int pagerColor(String alert) {
        switch (alert.toLowerCase(Locale.US)) {
            case "red":
                return 0xffd93025;
            case "orange":
                return 0xfff28c28;
            case "yellow":
                return 0xfffbbc04;
            default:
                return 0xff34a853;
        }
    }

    private void tile(int tileId, int icon, String label, String value, String note, int color) {
        View tile = requireView().findViewById(tileId);
        ImageView iconView = tile.findViewById(R.id.ivTileIcon);
        iconView.setImageResource(icon);
        iconView.setImageTintList(ColorStateList.valueOf(color));
        ((TextView) tile.findViewById(R.id.tvTileLabel)).setText(label);
        ((TextView) tile.findViewById(R.id.tvTileValue)).setText(value);
        TextView noteView = tile.findViewById(R.id.tvTileNote);
        noteView.setText(note);
        noteView.setVisibility(note == null ? View.GONE : View.VISIBLE);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.w(TAG, "Can't open " + url, e);
        }
    }

}
