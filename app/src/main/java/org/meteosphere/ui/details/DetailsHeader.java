package org.meteosphere.ui.details;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.meteosphere.R;
import org.meteosphere.ui.glass.GlassPill;
import org.meteosphere.ui.icons.ArrowText;
import org.meteosphere.ui.icons.ShadowedDrawable;

/**
 * The top of a city's page: its name, the weather now and the date, folding into the pill as the page scrolls. A tap
 * on the name shows the place's own one for a moment when it was given another.
 */
final class DetailsHeader {

    private static final long OTHER_NAME_MS = 2000;

    private final Context context;
    private final float density;
    private final View header;
    private final TextView tvCity;
    private final TextView tvTemperature;
    private final TextView tvCondition;
    private final TextView tvHighLow;
    private final View llWind;
    private final ImageView ivWindArrow;
    private final TextView tvWindSpeed;
    private final TextView tvWindUnit;
    private final View tvWindStrong;
    private final TextView tvFeelsLike;
    private final TextView tvDate;
    private final GlassPill pill;
    private final TextView tvPillCity;
    private final ImageView ivPillIcon;
    private final TextView tvPillTemperature;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable restoreName = () -> {
        this.showsOtherName = false;
        showName();
    };

    private DetailsModel model;
    private boolean showsOtherName;
    private boolean headerLayer;

    DetailsHeader(Context context, View root, GlassPill pill) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        this.header = root.findViewById(R.id.header);
        this.tvCity = root.findViewById(R.id.tvCity);
        this.tvTemperature = root.findViewById(R.id.tvTemperature);
        this.tvCondition = root.findViewById(R.id.tvCondition);
        this.tvHighLow = root.findViewById(R.id.tvHighLow);
        this.llWind = root.findViewById(R.id.llWind);
        this.ivWindArrow = root.findViewById(R.id.ivWindArrow);
        this.tvWindSpeed = root.findViewById(R.id.tvWindSpeed);
        this.tvWindUnit = root.findViewById(R.id.tvWindUnit);
        this.tvWindStrong = root.findViewById(R.id.tvWindStrong);
        this.tvFeelsLike = root.findViewById(R.id.tvFeelsLike);
        this.tvDate = root.findViewById(R.id.tvDate);
        this.pill = pill;
        this.tvPillCity = root.findViewById(R.id.tvPillCity);
        this.ivPillIcon = root.findViewById(R.id.ivPillIcon);
        this.tvPillTemperature = root.findViewById(R.id.tvPillTemperature);
        this.tvCity.setOnClickListener(v -> toggleName());
    }

    /** What the header shows of a model, for a bind to tell whether its lines change */
    static String shows(DetailsModel model) {
        return model.name + '|' + model.temperature + '|' + model.condition + '|' + model.highLow + '|' + model.feelsLike
                + '|' + model.dateTime + '|' + model.windSpeed + '|' + model.windUnit + '|' + model.windDirection + '|'
                + model.windArrow + '|' + model.strongWind + '|' + model.waitsForForecast;
    }

    void bind(DetailsModel model) {
        this.model = model;
        this.handler.removeCallbacks(this.restoreName);
        this.showsOtherName = false;
        showName();
        if (model.isCurrentLocation) {
            Drawable arrow = ContextCompat.getDrawable(this.context, R.drawable.ic_details_navigation);
            int size = Math.round(18 * this.density);
            arrow.setBounds(0, 0, size, size);
            this.tvCity.setCompoundDrawablesRelative(null, null, ShadowedDrawable.like(arrow, this.tvCity), null);
        } else {
            this.tvCity.setCompoundDrawablesRelative(null, null, null, null);
        }
        this.tvDate.setText(model.dateTime);
        this.tvPillCity.setText(model.name);

        boolean hasData = model.hasData;
        DetailsPage.setVisible(this.tvTemperature, hasData);
        DetailsPage.setVisible(this.tvCondition, hasData && model.condition != null && !model.condition.isEmpty());
        DetailsPage.setVisible(this.tvHighLow, hasData && model.highLow != null);
        DetailsPage.setVisible(this.tvFeelsLike, hasData && model.feelsLike != null);
        DetailsPage.setVisible(this.llWind, hasData && model.windSpeed != null);
        if (hasData) {
            this.tvTemperature.setText(model.temperature);
            this.tvCondition.setText(model.condition);
            this.tvHighLow.setText(ArrowText.of(this.context, model.highLow));
            this.tvFeelsLike.setText(model.feelsLike);
            this.tvPillTemperature.setText(model.temperature);
            this.ivPillIcon.setImageDrawable(model.icon);
            bindWind(model);
        } else {
            this.tvPillTemperature.setText("");
            this.ivPillIcon.setImageDrawable(null);
        }
        // Waiting for the forecast: only the place's name and the loader, the header's lines keeping their room, so
        // that all the weather comes at once from the forecast, nothing shown moving or changing. The weather the list
        // of nearby places had is hours old at times and not shown for it
        boolean waiting = model.waitsForForecast;
        if (waiting) {
            this.tvTemperature.setVisibility(View.INVISIBLE);
            this.llWind.setVisibility(View.INVISIBLE);
            this.tvCondition.setText("\u00A0");
            this.tvCondition.setVisibility(View.INVISIBLE);
            this.tvHighLow.setText("\u00A0");
            this.tvHighLow.setVisibility(View.INVISIBLE);
            this.tvFeelsLike.setVisibility(View.GONE);
            this.tvPillTemperature.setText("");
            this.ivPillIcon.setImageDrawable(null);
        }
        this.tvDate.setVisibility(waiting ? View.INVISIBLE : View.VISIBLE);
    }

    // An arrow where the wind blows to, or the wind's own sign when it is calm; where it blows from after the unit
    private void bindWind(DetailsModel model) {
        if (model.windSpeed == null) {
            return;
        }
        boolean hasArrow = !Float.isNaN(model.windArrow);
        // With the shadow of the wind's figures under it
        int windIconId = hasArrow ? R.drawable.ic_bearing : R.drawable.ic_city_wind;
        Drawable windIcon = ContextCompat.getDrawable(this.context, windIconId);
        this.ivWindArrow.setImageDrawable(ShadowedDrawable.like(windIcon, this.tvWindSpeed));
        this.ivWindArrow.setRotation(hasArrow ? model.windArrow : 0);
        this.tvWindSpeed.setText(model.windSpeed);
        String speed = model.windSpeed + " " + model.windUnit;
        if (model.windDirection != null) {
            this.tvWindUnit.setText(this.context.getString(R.string.details_wind_unit_direction, model.windUnit, model.windDirection));
            speed = model.windDirection + ", " + speed;
        } else {
            this.tvWindUnit.setText(model.windUnit);
        }
        DetailsPage.setVisible(this.tvWindStrong, model.strongWind);
        this.llWind.setContentDescription(model.strongWind
                ? this.context.getString(R.string.details_wind_strong, speed)
                : this.context.getString(R.string.details_wind) + " " + speed);
    }

    private void showName() {
        if (this.model == null) {
            return;
        }
        this.tvCity.setText(this.showsOtherName && this.model.otherName != null ? this.model.otherName : this.model.name);
    }

    // The current location's page shows the place's real name under a custom one for a moment
    private void toggleName() {
        if (this.model == null || this.model.otherName == null) {
            return;
        }
        this.showsOtherName = !this.showsOtherName;
        showName();
        this.handler.removeCallbacks(this.restoreName);
        if (this.showsOtherName) {
            this.handler.postDelayed(this.restoreName, OTHER_NAME_MS);
        }
    }

    // The header drifts up slower than the cards and fades; the pill takes over the city's name
    void collapse(int scrollY) {
        int headerHeight = this.header.getHeight();
        if (headerHeight == 0) {
            return;
        }
        float fade = Math.max(0, Math.min(1, scrollY / (headerHeight * 0.6f)));
        float headerAlpha = 1 - fade;
        this.header.setTranslationY(scrollY * 0.4f);
        this.header.setAlpha(headerAlpha);
        // A layer only while the header is see-through, so that its shadowed texts fade as one picture
        boolean needsLayer = headerAlpha > 0 && headerAlpha < 1;
        if (needsLayer != this.headerLayer) {
            this.headerLayer = needsLayer;
            this.header.setLayerType(needsLayer ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
        }
        this.header.setVisibility(headerAlpha <= 0 ? View.INVISIBLE : View.VISIBLE);
        float pillShow = Math.max(0, Math.min(1, (scrollY - headerHeight * 0.3f) / (headerHeight * 0.2f)));
        this.pill.setAlpha(pillShow);
        this.pill.setVisibility(pillShow <= 0 ? View.INVISIBLE : View.VISIBLE);
        this.pill.setTranslationY((1 - pillShow) * 8 * this.density);
    }

    /** For another city: its own name, nothing of the one before */
    void reuse() {
        this.handler.removeCallbacksAndMessages(null);
        this.model = null;
        this.showsOtherName = false;
    }

    void destroy() {
        this.handler.removeCallbacksAndMessages(null);
    }
}
