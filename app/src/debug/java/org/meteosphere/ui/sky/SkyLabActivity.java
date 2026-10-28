package org.meteosphere.ui.sky;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug builds only: a stand for the animated weather. Started with adb:
 *
 *   am start -n org.meteosphere/.ui.sky.SkyLabActivity --es dump all --ef time 0
 *     writes each weather of WEATHERS, the drawn sky at its moment and time seconds on, as files/skylab/NAME_sky.png
 *     at the screen's size, then finishes; --es dump NAME for one
 *   am start -n org.meteosphere/.ui.sky.SkyLabActivity --es live NAME
 *     shows the weather live over the whole screen, as the details do, the clouds thinning under the cards
 */
public class SkyLabActivity extends Activity {

    private static final String TAG = "SkyLabActivity";
    static final Map<String, List<String>> WEATHERS = new LinkedHashMap<>();
    // A gibbous moon lit from the right, its word as SkyScene.moonLayer makes it
    private static final String MOON = "moon26_27_0";

    static {
        put("clear", SkyWeather.DAY);
        put("c2", SkyWeather.DAY, SkyWeather.CLOUDS + 2);
        put("c3", SkyWeather.DAY, SkyWeather.CLOUDS + 3);
        put("overcast", SkyWeather.DAY, SkyWeather.CLOUDS + 4);
        put("rain", SkyWeather.DAY, SkyWeather.CLOUDS + 4, SkyWeather.RAIN + 2);
        put("snow", SkyWeather.DAY, SkyWeather.CLOUDS + 4, SkyWeather.SNOW + 2);
        put("storm", SkyWeather.DAY, SkyWeather.CLOUDS + 4, SkyWeather.RAIN + 3, SkyWeather.STORM);
        put("mist", SkyWeather.DAY, SkyWeather.CLOUDS + 3, SkyWeather.MIST);
        put("nclear", SkyWeather.NIGHT, MOON);
        put("nc2", SkyWeather.NIGHT, SkyWeather.CLOUDS + 2, MOON);
        put("nc3", SkyWeather.NIGHT, SkyWeather.CLOUDS + 3, MOON);
        put("nc4", SkyWeather.NIGHT, SkyWeather.CLOUDS + 4);
        put("nrain", SkyWeather.NIGHT, SkyWeather.CLOUDS + 4, SkyWeather.RAIN + 2);
        put("nsnow", SkyWeather.NIGHT, SkyWeather.CLOUDS + 4, SkyWeather.SNOW + 2);
        put("nstorm", SkyWeather.NIGHT, SkyWeather.CLOUDS + 4, SkyWeather.RAIN + 3, SkyWeather.STORM);
        put("nmist", SkyWeather.NIGHT, SkyWeather.CLOUDS + 3, SkyWeather.MIST, MOON);
    }

    private static void put(String name, String... sky) {
        WEATHERS.put(name, Arrays.asList(sky));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // Another weather to show or dump
        setIntent(intent);
        show(intent);
    }

    private void show(android.content.Intent intent) {
        String live = intent.getStringExtra("live");
        String dump = intent.getStringExtra("dump");
        float time = intent.getFloatExtra("time", 0);
        Log.i(TAG, "Showing " + live + ", dumping " + dump);
        if (live != null && WEATHERS.containsKey(live)) {
            FrameLayout root = new FrameLayout(this);
            SkyView sky = new SkyView(this);
            root.addView(sky, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            setContentView(root);
            sky.setClearBelow(0.3f, 0.56f);
            sky.setScene(SkyScene.from(WEATHERS.get(live)));
            return;
        }
        if (dump != null) {
            // The screen's own size, as the details show the sky
            android.graphics.Rect bounds = getWindowManager().getMaximumWindowMetrics().getBounds();
            Point size = new Point(bounds.width(), bounds.height());
            File dir = new File(getFilesDir(), "skylab");
            dir.mkdirs();
            new Thread(() -> {
                for (Map.Entry<String, List<String>> weather : WEATHERS.entrySet()) {
                    if (!dump.equals("all") && !dump.equals(weather.getKey())) {
                        continue;
                    }
                    dumpWeather(dir, weather.getKey(), weather.getValue(), size, time);
                }
                Log.i(TAG, "Done");
                runOnUiThread(this::finish);
            }, "SkyLab").start();
        }
    }

    private void dumpWeather(File dir, String name, List<String> words, Point size, float time) {
        // At half the size, as the details draw it, stretched back to the screen's
        SkyScene scene = SkyScene.from(words);
        Bitmap[] sky = SkyRenderer.get(this).renderStill(scene, size.x / 2, size.y / 2,
                SkyBackgrounds.CROP_CENTER, (scene.moment + time) * SkyView.WIND,
                (scene.moment + time) * SkyView.CHANGE);
        if (sky != null) {
            Bitmap full = Bitmap.createScaledBitmap(sky[0], size.x, size.y, true);
            save(full, new File(dir, name + "_sky.png"));
            sky[0].recycle();
            sky[1].recycle();
            full.recycle();
        }
        Log.i(TAG, "Wrote " + name);
    }

    private static void save(Bitmap bitmap, File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            Log.e(TAG, "Writing " + file, e);
        }
    }
}
