package org.meteosphere.astro;

import android.content.Context;
import android.util.Log;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** The eclipses of the NASA catalog in the eclipses.csv asset, read once. */
public final class EclipseCatalog {

    private static final String TAG = "EclipseCatalog";
    private static List<Eclipse> eclipses;

    private EclipseCatalog() {
    }

    public static synchronized List<Eclipse> get(Context context) {
        if (eclipses == null) {
            try (Reader reader = new InputStreamReader(context.getAssets().open("eclipses.csv"), StandardCharsets.UTF_8)) {
                eclipses = Collections.unmodifiableList(Eclipse.parse(reader));
            } catch (Exception e) {
                Log.e(TAG, "Can't read the eclipses", e);
                return Collections.emptyList();
            }
        }
        return eclipses;
    }
}
