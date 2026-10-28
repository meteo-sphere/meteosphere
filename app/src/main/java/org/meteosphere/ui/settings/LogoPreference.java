package org.meteosphere.ui.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import org.meteosphere.BuildConfig;
import org.meteosphere.R;

/** App logo and version shown at the top of the settings list. */
public final class LogoPreference extends Preference {

    public LogoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.settings_logo);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Context context = getContext();
        ImageView logoView = (ImageView) holder.findViewById(R.id.imvLogo);
        // As tall as the old one-word logo (110 × 28) was at a third of the screen's width, so the screen
        // around it keeps its layout; its colors follow the theme from the drawable itself
        logoView.setImageResource(R.drawable.logo);
        ViewGroup.LayoutParams params = logoView.getLayoutParams();
        params.height = context.getResources().getDisplayMetrics().widthPixels / 3 * 28 / 110;
        logoView.setLayoutParams(params);
        ((TextView) holder.findViewById(R.id.tvVersion)).setText(context.getString(R.string.version) + " " + BuildConfig.VERSION_NAME);
    }
}
