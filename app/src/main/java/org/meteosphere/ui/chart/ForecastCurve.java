package org.meteosphere.ui.chart;

import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * The temperature curve through a forecast's columns, as the details' strips and the chart widgets draw it: smooth
 * through the columns' points, in the colours of their temperatures along it, with a soft fill fading down under it.
 */
public final class ForecastCurve {

    private ForecastCurve() {
    }

    /**
     * The curve from point from to point to, the path standing at the first: between each two a Catmull-Rom segment as
     * a cubic, its tangents through their neighbours
     */
    public static void append(Path path, float[] x, float[] y, int from, int to) {
        int last = x.length - 1;
        for (int i = from; i < to; i++) {
            int i0 = Math.max(0, i - 1);
            int i2 = i + 1;
            int i3 = Math.min(last, i + 2);
            path.cubicTo(x[i] + (x[i2] - x[i0]) / 6, y[i] + (y[i2] - y[i0]) / 6, x[i2] - (x[i3] - x[i]) / 6, y[i2] - (y[i3] - y[i]) / 6, x[i2], y[i2]);
        }
    }

    /** The outline of the fill under the curve from point from to point to, down to the bottom */
    public static void appendFill(Path path, float[] x, float[] y, int from, int to, float bottom) {
        path.moveTo(x[from], bottom);
        path.lineTo(x[from], y[from]);
        append(path, x, y, from, to);
        path.lineTo(x[to], bottom);
        path.close();
    }

    /** The fill's colour at the top, fading down to nothing */
    public static LinearGradient fillShader(float top, float bottom, int color) {
        return new LinearGradient(0, top, 0, bottom, color, color & 0x00ffffff, Shader.TileMode.CLAMP);
    }

    /** Along the curve each point in its colour */
    public static LinearGradient colorShader(float[] x, int[] colors) {
        float x0 = x[0];
        float x1 = x[x.length - 1];
        float[] positions = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            positions[i] = (x[i] - x0) / (x1 - x0);
        }
        return new LinearGradient(x0, 0, x1, 0, colors, positions, Shader.TileMode.CLAMP);
    }

    /** The lows' colour, a little faded as their curve is the second one */
    public static int low(int color) {
        return (color & 0x00ffffff) | 0xcc000000;
    }
}
