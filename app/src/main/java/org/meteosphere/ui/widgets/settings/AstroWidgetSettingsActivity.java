package org.meteosphere.ui.widgets.settings;

import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.view.OneShotPreDrawListener;
import org.meteosphere.data.model.Place;
import org.meteosphere.R;
import org.meteosphere.ui.widgets.AstroWidget;

/** The Sun or the Moon widget's location and background, as the earthquake widget has them, with no settings of its own */
public class AstroWidgetSettingsActivity extends EarthquakeWidgetSettingsActivity {

    // As tall as a row of the launcher's cells, with room for the place
    private static final int PREVIEW_HEIGHT_DP = 110;

    private boolean isMoon;

    @Override
    protected void onCreate(Bundle bundle) {
        // Before the title is set
        Bundle extras = getIntent().getExtras();
        this.isMoon = extras != null && AstroWidget.isMoonWidget(this, extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
        super.onCreate(bundle);
    }

    @Override
    protected int titleRes() {
        return this.isMoon ? R.string.moon_widget_settings : R.string.sun_widget_settings;
    }

    // In place of the period and the order a preview one row high
    @Override
    protected void setUpOwnSettings() {
        findViewById(R.id.llQuakePeriod).setVisibility(View.GONE);
        findViewById(R.id.llQuakeShow).setVisibility(View.GONE);
        findViewById(R.id.flQuakeWidgetPreview).getLayoutParams().height =
                Math.round(PREVIEW_HEIGHT_DP * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void loadPreview() {
        // The widget is drawn narrow or wide by the preview's width, not known yet as the screen is made
        View preview = findViewById(R.id.flQuakeWidgetPreview);
        if (preview.getWidth() == 0) {
            OneShotPreDrawListener.add(preview, this::showPreview);
        } else {
            showPreview();
        }
    }

    @Override
    protected RemoteViews buildPreview(int heightDp, int backgroundColor, int fontColor, Place location) {
        int widthDp = Math.round(findViewById(R.id.flQuakeWidgetPreview).getWidth() / getResources().getDisplayMetrics().density);
        return AstroWidget.build(this, 0, this.isMoon, widthDp, heightDp, backgroundColor, fontColor, location, System.currentTimeMillis());
    }

    // None of its own
    @Override
    protected void putOwnSettings(WidgetConfig.Editor editor) {
    }

    @Override
    protected void redraw() {
        AstroWidget.updateAll(this, this.isMoon);
    }
}
