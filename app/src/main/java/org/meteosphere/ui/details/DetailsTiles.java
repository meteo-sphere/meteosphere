package org.meteosphere.ui.details;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.R;
import org.meteosphere.ui.icons.ArrowText;

/** The tiles of a city's page, two to a row: a parameter's value and caption by its dial, the earthquakes' a button */
final class DetailsTiles {

    private final Context context;
    private final float density;
    private final LinearLayout tiles;
    private final ArrayList<View> tileViews = new ArrayList<>();
    private final Runnable openEarthquakes;

    DetailsTiles(Context context, LinearLayout tiles, Runnable openEarthquakes) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        this.tiles = tiles;
        this.openEarthquakes = openEarthquakes;
    }

    /** The tiles' cards, in their order */
    List<View> views() {
        return this.tileViews;
    }

    void bind(ArrayList<DetailsModel.Tile> list, ColorStateList cardTint) {
        if (this.tileViews.size() != list.size()) {
            this.tiles.removeAllViews();
            this.tileViews.clear();
            LayoutInflater inflater = LayoutInflater.from(this.context);
            int gap = Math.round(12 * this.density);
            LinearLayout row = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(this.context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(Math.round(16 * this.density), 0, Math.round(16 * this.density), gap);
                    this.tiles.addView(row, rowParams);
                }
                View tile = inflater.inflate(R.layout.item_details_tile, row, false);
                if (i % 2 == 1) {
                    ((LinearLayout.LayoutParams) tile.getLayoutParams()).setMarginStart(gap);
                }
                row.addView(tile);
                this.tileViews.add(tile);
                TextView caption = tile.findViewById(R.id.tvTileCaption);
                caption.setTag(R.id.tag_own_text_size, caption.getTextSize());
                caption.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (right - left != oldRight - oldLeft) {
                        fitLines(caption);
                    }
                });
            }
        }
        for (int i = 0; i < list.size(); i++) {
            DetailsModel.Tile tile = list.get(i);
            View view = this.tileViews.get(i);
            view.setBackgroundTintList(cardTint);
            TextView title = view.findViewById(R.id.tvTileTitle);
            title.setText(tile.title);
            title.setCompoundDrawablesRelativeWithIntrinsicBounds(tile.icon, 0, 0, 0);
            TextView value = view.findViewById(R.id.tvTileValue);
            value.setText(valueWithUnit(tile.value, tile.unit));
            TextView caption = view.findViewById(R.id.tvTileCaption);
            caption.setText(ArrowText.of(this.context, tile.caption));
            fitLines(caption);
            MetricDialView dial = view.findViewById(R.id.dialTile);
            if (tile.opensEarthquakes) {
                view.setOnClickListener(v -> this.openEarthquakes.run());
                view.setForeground(ContextCompat.getDrawable(this.context, R.drawable.fg_details_card_ripple));
            } else {
                view.setOnClickListener(null);
                view.setClickable(false);
                view.setForeground(null);
            }
            if (tile.dialMode == MetricDialView.COMPASS) {
                dial.setCompass(tile.dialValue, tile.north);
            } else if (tile.dialMode == MetricDialView.RADAR) {
                dial.setRadar(tile.radar, tile.north);
            } else if (tile.dialMode == MetricDialView.WAVES) {
                dial.setWaves(tile.dialColor);
            } else if (tile.dialMode == MetricDialView.LEVEL) {
                dial.setLevel(tile.dialValue, tile.dialColor);
            } else {
                dial.setGauge(tile.dialValue, tile.dialColor);
            }
        }
    }

    // Each of the caption's lines in a line of its own: a line too long for a narrow screen or a large font is set
    // smaller rather than wrapped, which would push the line under it out of the tile
    private void fitLines(TextView view) {
        Object own = view.getTag(R.id.tag_own_text_size);
        float size = own instanceof Float ? (Float) own : view.getTextSize();
        int available = view.getWidth() - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
        CharSequence text = view.getText();
        if (available > 0 && text.length() > 0) {
            float least = 10 * this.context.getResources().getDisplayMetrics().scaledDensity;
            android.text.TextPaint paint = new android.text.TextPaint(view.getPaint());
            float widest = widestLine(text, paint, size);
            if (widest > available) {
                // Down in proportion, then a step at a time while the text's own measure still doesn't fit
                size = Math.max(least, size * available / widest);
                while (size > least && widestLine(text, paint, size) > available) {
                    size = Math.max(least, size - 0.5f * this.density);
                }
            }
        }
        if (view.getTextSize() != size) {
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
        }
    }

    // The widest of the text's lines, as a TextView lays them out at that size
    private static float widestLine(CharSequence text, android.text.TextPaint paint, float size) {
        paint.setTextSize(size);
        float widest = 0;
        int start = 0;
        while (start <= text.length()) {
            int end = android.text.TextUtils.indexOf(text, '\n', start);
            if (end < 0) {
                end = text.length();
            }
            widest = Math.max(widest, (float) Math.ceil(android.text.Layout.getDesiredWidth(text, start, end, paint)));
            start = end + 1;
        }
        return widest;
    }

    // The value large and its unit small after it
    private static CharSequence valueWithUnit(String value, String unit) {
        if (unit == null || unit.isEmpty()) {
            return value;
        }
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(value);
        int start = text.length();
        text.append(unit.equals("%") ? unit : " " + unit);
        text.setSpan(new android.text.style.RelativeSizeSpan(0.5f), start, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new android.text.style.ForegroundColorSpan(0xb8ffffff), start, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
}
