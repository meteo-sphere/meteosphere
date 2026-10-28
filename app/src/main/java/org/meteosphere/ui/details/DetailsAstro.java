package org.meteosphere.ui.details;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.function.Consumer;
import org.meteosphere.R;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.ui.astro.AstroFormatter;
import org.meteosphere.ui.astro.MoonPhaseDrawable;
import org.meteosphere.ui.astro.SunDayChart;
import org.meteosphere.ui.astro.SunNowCard;

/** The Sun's and the Moon's cards of a city's page, the Sun's with its picture now; a tap opens their tab */
final class DetailsAstro {

    // The moon card's moon across, and its real moon's dark side as bright and as opaque: a faint see-through disc on
    // the glass, as the drawn one's (SunMoonBuilder draws it)
    static final int MOON_DP = 88;
    static final float DARK_SIDE = 0.6f;
    static final float DARK_SIDE_ALPHA = 0.22f;

    private final View cardSun;
    private final TextView tvSunTitle;
    private final SunDayChart sunChart;
    private final TextView tvSunrise;
    private final TextView tvSunset;
    private final View llTwilight;
    private final TextView tvTwilight;
    private final View rowSunNow;
    private final View sunNowDivider;
    private final SunNowCard sunNow;
    private final View cardMoon;
    private final MoonPhaseDrawable moonDrawable;
    private final TextView tvMoonPhase;
    private final TextView tvMoonInfo;
    private final TextView tvMoonrise;
    private final TextView tvMoonset;

    /** open is told whether the Moon's card was tapped or the Sun's */
    DetailsAstro(Context context, View root, Consumer<Boolean> open) {
        this.cardSun = root.findViewById(R.id.cardSun);
        this.tvSunTitle = root.findViewById(R.id.tvSunTitle);
        this.sunChart = root.findViewById(R.id.sunChart);
        this.tvSunrise = root.findViewById(R.id.tvSunrise);
        this.tvSunset = root.findViewById(R.id.tvSunset);
        this.llTwilight = root.findViewById(R.id.llTwilight);
        this.tvTwilight = root.findViewById(R.id.tvTwilight);
        this.cardMoon = root.findViewById(R.id.cardMoon);
        this.tvMoonPhase = root.findViewById(R.id.tvMoonPhase);
        this.tvMoonInfo = root.findViewById(R.id.tvMoonInfo);
        this.tvMoonrise = root.findViewById(R.id.tvMoonrise);
        this.tvMoonset = root.findViewById(R.id.tvMoonset);

        this.sunChart.setSurfaceColors(0x59ffffff, 0x99ffffff);
        this.sunChart.setColor(0xffffd54f, true);
        int moonSize = Math.round(MOON_DP * context.getResources().getDisplayMetrics().density);
        this.moonDrawable = new MoonPhaseDrawable(moonSize);
        this.moonDrawable.setMoonColors(0x33ffffff, 0xffeef2f6);
        ((ImageView) root.findViewById(R.id.ivMoon)).setImageDrawable(this.moonDrawable);
        this.cardSun.setOnClickListener(v -> open.accept(false));
        this.cardMoon.setOnClickListener(v -> open.accept(true));

        this.rowSunNow = root.findViewById(R.id.rowSunNow);
        this.sunNowDivider = root.findViewById(R.id.vSunNowDivider);
        this.sunNow = new SunNowCard(this.rowSunNow, root.findViewById(R.id.ivSunNowRow),
                root.findViewById(R.id.tvSunNowRowChannel), root.findViewById(R.id.tvSunNowRowTime),
                root.findViewById(R.id.piSunNowRow), new AstroFormatter(context, AppSettings.getInstance().use24Hours));
    }

    void bind(DetailsModel model, boolean waiting, ColorStateList cardTint) {
        this.cardSun.setBackgroundTintList(cardTint);
        this.cardMoon.setBackgroundTintList(cardTint);
        boolean hasPlace = model.location != null;
        DetailsPage.setVisible(this.cardSun, hasPlace && !waiting && model.hasSun);
        DetailsPage.setVisible(this.cardMoon, hasPlace && !waiting);
        // The Sun now unless the weather parameters turn it off
        DetailsPage.setVisible(this.rowSunNow, model.hasSunNow);
        DetailsPage.setVisible(this.sunNowDivider, model.hasSunNow);
        if (hasPlace && model.hasSunNow) {
            // A newer picture is asked for at most every few minutes
            this.sunNow.refresh(true);
        }
        if (!hasPlace) {
            return;
        }
        this.tvSunTitle.setText(model.sunTitle);
        this.sunChart.setDay(model.sunAltitudes, model.sunNowMinutes, model.sunNowAltitude);
        this.tvSunrise.setText(model.sunrise);
        this.tvSunset.setText(model.sunset);
        DetailsPage.setVisible(this.llTwilight, model.twilight != null);
        this.tvTwilight.setText(model.twilight);
        this.moonDrawable.setPhase(model.moonAge, model.southernHemisphere);
        // The real moon as it is seen there now
        this.moonDrawable.setPicture(model.moonPicture);
        this.tvMoonPhase.setText(model.moonPhase);
        this.tvMoonInfo.setText(model.moonIllumination + "\n" + model.moonNext);
        this.tvMoonrise.setText(model.moonrise);
        this.tvMoonset.setText(model.moonset);
    }
}
