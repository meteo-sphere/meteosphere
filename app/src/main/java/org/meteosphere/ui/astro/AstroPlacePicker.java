package org.meteosphere.ui.astro;

import org.meteosphere.ui.glass.GlassMenu;
import android.content.Context;
import android.view.View;
import com.google.android.material.chip.Chip;
import java.util.ArrayList;
import java.util.List;
import org.meteosphere.data.model.Place;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.background.EclipseAlertWorker;

/**
 * The chip of the Moon, Sun and Earthquakes tabs naming their location, one for all three: tapping it lists
 * the current location and the favorites to choose from.
 */
public final class AstroPlacePicker {

    private static final int CURRENT = -1;

    private AstroPlacePicker() {
    }

    /** Names the location shown and opens the list on a tap; onChanged runs once another one is chosen */
    public static void bind(Chip chip, Place shown, Runnable onChanged) {
        Context context = chip.getContext();
        chip.setText(shown == null ? context.getString(R.string.current_location) : shown.displayName());
        View.OnClickListener open = view -> show(chip, onChanged);
        chip.setOnClickListener(open);
        chip.setOnCloseIconClickListener(open);
    }

    /** Names a location that can't be changed here, like the one the earthquake screen was opened for */
    public static void bindFixed(Chip chip, String name) {
        chip.setText(name);
        chip.setCloseIconVisible(false);
        chip.setClickable(false);
    }

    /** The id of the location the tabs show now, to tell when it changed; Integer.MIN_VALUE for none */
    public static int shownLocationId() {
        Place location = AppSettings.getInstance().getAstroLocation();
        return location == null ? Integer.MIN_VALUE : location.id;
    }

    private static void show(Chip chip, Runnable onChanged) {
        Context context = chip.getContext();
        AppSettings settings = AppSettings.getInstance();
        List<Integer> ids = new ArrayList<>();
        List<Place> locations = new ArrayList<>();
        Place current = settings.getCurrentPlace();
        if (current != null && (current.latitude != 0 || current.longitude != 0)) {
            ids.add(CURRENT);
            locations.add(current);
        }
        for (Integer id : settings.getFavorites()) {
            Place location = settings.getPlace(id);
            if (location != null) {
                ids.add(id);
                locations.add(location);
            }
        }
        // The chosen one, otherwise the one shown instead: the first favorite while the current location is unknown
        int chosen = ids.contains(settings.getAstroLocationId()) ? settings.getAstroLocationId() : shownLocationId();
        // The current location with the place it is at, set apart from the favorites
        List<GlassMenu.Item> items = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            boolean isCurrent = ids.get(i) == CURRENT;
            String name = locations.get(i).displayName();
            items.add(isCurrent
                    ? new GlassMenu.Item(R.drawable.ic_details_navigation, context.getString(R.string.current_location), name, chosen == CURRENT, ids.size() > 1)
                    : new GlassMenu.Item(R.drawable.ic_place, name, null, ids.get(i) == chosen, false));
        }
        GlassMenu.show(chip, items, index -> {
            int locationId = ids.get(index);
            if (locationId != settings.getAstroLocationId()) {
                settings.saveAstroLocationId(locationId);
                // The eclipses to announce depend on where they are seen
                EclipseAlertWorker.schedule(context, true);
                onChanged.run();
            }
        });
    }
}
