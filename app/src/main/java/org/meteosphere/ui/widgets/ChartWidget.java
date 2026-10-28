package org.meteosphere.ui.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import org.meteosphere.data.model.DayWeather;
import org.meteosphere.data.model.Weather;
import org.meteosphere.data.model.Place;
import org.meteosphere.ui.icons.TemperatureColors;
import org.meteosphere.ui.icons.WeatherIcons;
import org.meteosphere.settings.AppSettings;
import org.meteosphere.R;
import org.meteosphere.ui.chart.ForecastCurve;
import org.meteosphere.ui.chart.ForecastFormat;
import org.meteosphere.ui.chart.PrecipitationDrop;
import org.meteosphere.ui.chart.ValueColors;
import org.meteosphere.ui.chart.WindArrow;
import org.meteosphere.util.Units;
import org.meteosphere.util.WindText;

/**
 * The forecast widgets with a chart, by hours or by days, under a clock or not, drawn as one picture on a card of dark
 * or light glass: the weather now on top when the widget shows it, then a column per hour or day with its time, icon
 * and temperature over the chart of the temperatures, the wind and the precipitation under it, and the place by the
 * refresh button at the bottom. The temperatures stand on their points of the chart, the days' lows hang under
 * theirs; they are white or black, the curves and their points in the colours of the temperatures.
 */
final class ChartWidget {

    private static final float CELL_DP = 58;
    private static final int MIN_COLUMNS = 3;
    private static final int MAX_COLUMNS = 8;
    // The least range of temperatures the chart spans, in degrees: a degree or two apart stays a gentle slope
    private static final float MIN_SPAN = 8;
    // The smallest a column's label too long for it, as "Vandaag" or "23日週五", gets to fit whole; one that doesn't fit
    // even so is cut at its own size
    private static final float MIN_LABEL_DP = 10;

    private ChartWidget() {
    }

    static final class Column {
        String label;
        boolean strong;
        Drawable icon;
        String value;
        String value2;
        float curve;
        float curve2 = Float.NaN;
        int color;
        int color2;
        String precipitation;
        // How full the drop before the precipitation is, 0 to 1
        float precipitationFill;
        String wind;
        String windUnit;
        // Where the wind blows to, NaN for a calm or no direction; the colour of a strong wind, 0 for none
        float windArrow = Float.NaN;
        int windColor;
    }

    static final class Model {
        boolean light;
        // The gear at the top left, before Android 12 reconfigured a widget from the launcher
        boolean settingsIcon;
        boolean daily;
        String place;
        boolean withCurrent;
        String temperature;
        Drawable icon;
        String condition;
        String wind;
        // Shown instead of the forecast when there is none, "No data" when null
        String message;
        final ArrayList<Column> columns = new ArrayList<>();
        boolean hasWind;
        boolean hasPrecipitation;
    }

    /** How many columns fit across the width */
    static int columns(Context context, int width) {
        float cell = CELL_DP * context.getResources().getDisplayMetrics().density;
        return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, (int) (width / cell)));
    }

    /** Whether the widget's background, as its settings keep it, is the light one */
    static boolean isLight(int backgroundColor) {
        return Color.red(backgroundColor) == 255;
    }

    // --- The data ---

    /** The forecast at the place's time now, or at the given local time read as UTC for a preview, 0 for now */
    static Model build(Context context, Place location, long localTime, boolean daily, boolean withCurrent, int columns,
                       String place, boolean light, boolean showWind, boolean showPrecipitation) {
        Resources res = context.getResources();
        AppSettings settings = AppSettings.getInstance();
        int unit = settings.temperatureUnit;
        HashMap<String, Drawable> icons = new HashMap<>();
        Model model = new Model();
        model.light = light;
        model.daily = daily;
        model.place = place;
        model.withCurrent = withCurrent;
        if (location == null) {
            return model;
        }
        long nowLocal = localTime != 0 ? localTime : location.localNow().getTimeInMillis();
        Weather current = location.nowWeather();
        if (withCurrent && current != null) {
            model.temperature = ForecastFormat.temperature(current.temperature, unit);
            // Its own drawable: larger than the columns' icons
            model.icon = WeatherIcons.getInstance(context).getWeatherDrawable(current.icon(location.isDay()), light);
            model.condition = current.description();
            model.wind = wind(context, current.windSpeed, current.windSector);
        }
        if (daily) {
            String[] weekdays = res.getStringArray(R.array.weekdays_short);
            long today = ForecastFormat.floorDay(nowLocal);
            for (DayWeather day : location.daysCopy()) {
                if (day.date < today) {
                    continue;
                }
                long date = day.date;
                Column column = new Column();
                column.label = ForecastFormat.dayLabel(res, weekdays, date, nowLocal);
                column.strong = ForecastFormat.floorDay(date) == today;
                column.icon = icon(context, icons, day.icon, light);
                column.value = ForecastFormat.temperature(day.temperatureMax, unit);
                column.value2 = ForecastFormat.temperature(day.temperatureMin, unit);
                column.curve = Units.temperature(day.temperatureMax, unit);
                column.curve2 = Units.temperature(day.temperatureMin, unit);
                column.color = ForecastFormat.temperatureColor(day.temperatureMax, !light);
                column.color2 = ForecastFormat.temperatureColor(day.temperatureMin, !light);
                column.precipitation = precipitation(day.precipitation, settings.precipitationUnit);
                column.precipitationFill = PrecipitationDrop.dayFill(day.precipitation);
                windCell(context, column, day.windSpeedMax, day.windSector, light);
                model.columns.add(column);
                if (model.columns.size() == columns) {
                    break;
                }
            }
        } else if (current != null) {
            SimpleDateFormat hourFormat = settings.use24Hours
                    ? new SimpleDateFormat("HH:mm", Locale.getDefault()) : new SimpleDateFormat("h a", Locale.US);
            hourFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Column now = hourColumn(context, icons, current, unit, light);
            now.label = res.getString(R.string.details_now);
            now.strong = true;
            model.columns.add(now);
            // The hours after now, or after the current weather's hour when that is a forecast a little ahead
            long after = nowLocal;
            if (current.time > after && current.time - after < 3 * 60 * 60 * 1000L) {
                after = current.time;
            }
            for (Weather hour : location.hoursCopy()) {
                if (model.columns.size() == columns) {
                    break;
                }
                if (hour.time <= after) {
                    continue;
                }
                Column column = hourColumn(context, icons, hour, unit, light);
                column.label = hourFormat.format(new Date(hour.time));
                model.columns.add(column);
            }
        }
        // The rows the widget's settings leave out
        for (Column column : model.columns) {
            if (!showWind) {
                column.wind = null;
            }
            if (!showPrecipitation) {
                column.precipitation = null;
            }
            model.hasWind |= column.wind != null;
            model.hasPrecipitation |= column.precipitation != null;
        }
        return model;
    }

    private static Column hourColumn(Context context, HashMap<String, Drawable> icons, Weather hour, int unit, boolean light) {
        Column column = new Column();
        column.icon = icon(context, icons, hour.icon, light);
        column.value = ForecastFormat.temperature(hour.temperature, unit);
        column.curve = Units.temperature(hour.temperature, unit);
        column.color = ForecastFormat.temperatureColor(hour.temperature, !light);
        column.precipitation = precipitation(hour.precipitation, AppSettings.getInstance().precipitationUnit);
        column.precipitationFill = PrecipitationDrop.hourFill(hour.precipitation);
        windCell(context, column, hour.windSpeed, hour.windSector, light);
        return column;
    }

    private static void windCell(Context context, Column column, int speed, int direction, boolean light) {
        Resources res = context.getResources();
        int unit = AppSettings.getInstance().windSpeedUnit;
        column.wind = String.valueOf(Units.windSpeed(speed, unit));
        column.windUnit = WindText.unitName(context, speed, unit);
        column.windArrow = ForecastFormat.windArrow(speed, direction);
        int color = ValueColors.wind(speed, res.getInteger(R.integer.strong_wind_mps));
        // The strong wind's yellows and reds deepened to read on the light glass
        column.windColor = color != 0 && light ? TemperatureColors.forSurface(color, false) : color;
    }

    private static Drawable icon(Context context, HashMap<String, Drawable> icons, String name, boolean light) {
        Drawable icon = icons.get(name);
        if (icon == null) {
            icon = WeatherIcons.getInstance(context).getWeatherDrawable(name, light);
            icons.put(name, icon);
        }
        return icon;
    }

    // None under a twentieth of a millimetre
    private static String precipitation(double millimetres, int unit) {
        return millimetres < 0.05 ? null : ForecastFormat.precipitation(millimetres, unit);
    }

    // The calm's word without wind, else the wind's phrase, with the direction it has
    private static String wind(Context context, int speed, int direction) {
        Resources res = context.getResources();
        String[] directions = res.getStringArray(R.array.wind_directions);
        if (speed == 0) {
            return directions[0];
        }
        int unit = AppSettings.getInstance().windSpeedUnit;
        String speedText = String.valueOf(Units.windSpeed(speed, unit));
        String unitText = WindText.unitName(context, speed, unit);
        return direction > 0 && direction < directions.length
                ? res.getString(R.string.wind_phrase, speedText, unitText, directions[direction])
                : res.getString(R.string.wind_phrase_no_direction, speedText, unitText);
    }

    // --- The picture ---

    /** The height of the card with all it shows */
    static int height(Context context, Model model) {
        return Math.round(new Painter(context, model).height());
    }

    /**
     * The card at the width and its height, its glass as transparent as the background's alpha; without the glass
     * when it is drawn apart (see background)
     */
    static Bitmap render(Context context, Model model, int width, int backgroundColor, boolean withBackground) {
        Painter painter = new Painter(context, model);
        Bitmap bitmap = Bitmap.createBitmap(width, Math.round(painter.height()), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (withBackground) {
            painter.drawBackground(canvas, bitmap.getWidth(), bitmap.getHeight(), Color.alpha(backgroundColor));
        }
        painter.draw(canvas, bitmap.getWidth());
        return bitmap;
    }

    /** The card's glass alone and opaque, for a preview that changes its transparency */
    static Bitmap background(Context context, Model model, int width) {
        Painter painter = new Painter(context, model);
        Bitmap bitmap = Bitmap.createBitmap(width, Math.round(painter.height()), Bitmap.Config.ARGB_8888);
        painter.drawBackground(new Canvas(bitmap), bitmap.getWidth(), bitmap.getHeight(), 255);
        return bitmap;
    }

    private static final class Painter {

        private final Context context;
        private final Model model;
        private final float d;

        private final int surface;
        private final int text;
        private final int dim;

        private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint strongPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        // A label's paint made to fit its column
        private final TextPaint fitPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint value2Paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint precipitationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint windPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint nowPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint conditionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint nowWindPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint placePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dividerPaint = new Paint();
        private final PrecipitationDrop drop;
        private final WindArrow windArrow = new WindArrow();
        // From the precipitation's baseline to the middle of its digits, up
        private final float precipitationMiddle;
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final Rect iconRect = new Rect();

        // The rows from the top, in pixels
        private final float padX;
        private final float padTop;
        private final float padBottom;
        private final float headerHeight;
        private final float headerGap;
        private final float labelHeight;
        private final float iconHeight;
        private final float valueHeight;
        private final float chartHeight;
        private final float value2Height;
        // The height of the lows' digits, and how far the temperatures stand off their points
        private final float value2Digits;
        private final float valueGap;
        private final float windHeight;
        private final float precipitationHeight;
        private final float footerHeight;

        // Laid out by draw
        private float columnWidth;
        private float chartTop;
        private float chartBottom;
        private float min;
        private float max;
        // The points of the curves: the columns' middles, the values' heights
        private float[] pointX;
        private float[] pointY;
        private float[] pointY2;

        Painter(Context context, Model model) {
            this.context = context;
            this.model = model;
            this.d = context.getResources().getDisplayMetrics().density;
            this.surface = ContextCompat.getColor(context, model.light ? R.color.widget_glass_light : R.color.widget_glass_dark);
            this.text = ContextCompat.getColor(context, model.light ? R.color.widget_text_light : R.color.widget_text_dark);
            this.dim = ContextCompat.getColor(context, model.light ? R.color.widget_text_dim_light : R.color.widget_text_dim_dark);

            Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
            this.labelPaint.setTextSize(14 * this.d);
            this.labelPaint.setColor(this.dim);
            this.labelPaint.setTextAlign(Paint.Align.CENTER);
            this.strongPaint.set(this.labelPaint);
            this.strongPaint.setColor(this.text);
            this.strongPaint.setTypeface(medium);
            this.valuePaint.setTextSize(16 * this.d);
            this.valuePaint.setColor(this.text);
            this.valuePaint.setTypeface(medium);
            this.valuePaint.setTextAlign(Paint.Align.CENTER);
            this.value2Paint.setTextSize(14 * this.d);
            this.value2Paint.setColor(this.dim);
            this.value2Paint.setTextAlign(Paint.Align.CENTER);
            this.precipitationPaint.setTextSize(11 * this.d);
            this.precipitationPaint.setColor(model.light ? 0xff1976d2 : PrecipitationDrop.COLOR);
            // Empty, the drop in a pale shade of its own blue, to read at the widget's small size
            this.drop = new PrecipitationDrop(2.6f * this.d, model.light ? 0x661976d2 : 0x8c9fd0ff, this.precipitationPaint.getColor());
            Rect digits = new Rect();
            this.precipitationPaint.getTextBounds("0", 0, 1, digits);
            this.precipitationMiddle = -(digits.top + digits.bottom) / 2f;
            this.windPaint.setTextSize(13 * this.d);
            this.nowPaint.setTextSize(46 * this.d);
            this.nowPaint.setColor(this.text);
            this.nowPaint.setTypeface(light);
            this.conditionPaint.setTextSize(15 * this.d);
            this.conditionPaint.setColor(this.text);
            this.conditionPaint.setTypeface(medium);
            this.nowWindPaint.setTextSize(13 * this.d);
            this.nowWindPaint.setColor(this.dim);
            this.placePaint.setTextSize(13 * this.d);
            this.placePaint.setColor(this.dim);
            this.curvePaint.setStyle(Paint.Style.STROKE);
            this.curvePaint.setStrokeCap(Paint.Cap.ROUND);
            this.curvePaint.setStrokeJoin(Paint.Join.ROUND);
            this.clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            this.dividerPaint.setColor(ContextCompat.getColor(context, model.light ? R.color.widget_divider_light : R.color.widget_divider_dark));
            this.dividerPaint.setStrokeWidth(Math.max(1, 0.75f * this.d));

            this.padX = 14 * this.d;
            this.padTop = 12 * this.d;
            this.padBottom = 8 * this.d;
            this.headerHeight = model.withCurrent ? 54 * this.d : 0;
            this.headerGap = model.withCurrent ? 10 * this.d : 0;
            this.labelHeight = 18.5f * this.d;
            this.iconHeight = 30 * this.d;
            this.valueHeight = 21 * this.d;
            // The days' two curves need more height than the hours' one
            this.chartHeight = (model.daily ? 50 : 40) * this.d;
            this.value2Height = model.daily ? 19 * this.d : 0;
            this.value2Paint.getTextBounds("0", 0, 1, digits);
            this.value2Digits = -digits.top;
            this.valueGap = 6.5f * this.d;
            this.windHeight = model.hasWind ? 19 * this.d : 0;
            this.precipitationHeight = model.hasPrecipitation ? 15 * this.d : 0;
            this.footerHeight = 22 * this.d;
        }

        float height() {
            if (this.model.columns.isEmpty()) {
                // The message in the middle
                return this.padTop + this.headerHeight + this.headerGap + 80 * this.d + this.footerHeight + this.padBottom;
            }
            return this.padTop + this.headerHeight + this.headerGap + this.labelHeight + 3 * this.d + this.iconHeight + 2 * this.d
                    + this.valueHeight + this.chartHeight + this.value2Height + this.windHeight + this.precipitationHeight
                    + this.footerHeight + this.padBottom;
        }

        void draw(Canvas canvas, int width) {
            float y = this.padTop;
            if (this.model.withCurrent) {
                drawHeader(canvas, width, y);
                y += this.headerHeight;
                canvas.drawLine(this.padX, y + this.headerGap / 2, width - this.padX, y + this.headerGap / 2, this.dividerPaint);
                y += this.headerGap;
            }

            int count = this.model.columns.size();
            if (count == 0) {
                TextPaint paint = new TextPaint(this.conditionPaint);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(this.model.message != null ? this.model.message : this.context.getString(R.string.no_data), width / 2f, y + 40 * this.d - (paint.ascent() + paint.descent()) / 2, paint);
                drawFooter(canvas, width, y + 80 * this.d);
                drawSettingsIcon(canvas);
                return;
            }
            this.columnWidth = (width - 2 * this.padX) / count;

            float labelBaseline = y + this.labelHeight - 4 * this.d;
            float iconTop = y + this.labelHeight + 3 * this.d;
            float valueTop = iconTop + this.iconHeight + 2 * this.d;
            this.chartTop = valueTop + this.valueHeight;
            this.chartBottom = this.chartTop + this.chartHeight;
            float windTop = this.chartBottom + this.value2Height;
            float precipitationTop = windTop + this.windHeight;

            this.min = Float.MAX_VALUE;
            this.max = -Float.MAX_VALUE;
            for (Column column : this.model.columns) {
                this.min = Math.min(this.min, column.curve);
                this.max = Math.max(this.max, column.curve);
                if (!Float.isNaN(column.curve2)) {
                    this.min = Math.min(this.min, column.curve2);
                    this.max = Math.max(this.max, column.curve2);
                }
            }
            if (this.max - this.min < MIN_SPAN) {
                float middle = (this.max + this.min) / 2;
                this.min = middle - MIN_SPAN / 2;
                this.max = middle + MIN_SPAN / 2;
            }
            this.pointX = new float[count];
            this.pointY = new float[count];
            this.pointY2 = new float[count];
            for (int i = 0; i < count; i++) {
                Column column = this.model.columns.get(i);
                this.pointX[i] = centerX(i);
                this.pointY[i] = curveY(column.curve);
                this.pointY2[i] = curveY(column.curve2);
            }

            drawCurves(canvas);

            for (int i = 0; i < count; i++) {
                Column column = this.model.columns.get(i);
                float cx = centerX(i);
                String label = fitLabel(column.label, column.strong ? this.strongPaint : this.labelPaint);
                canvas.drawText(label, cx, labelBaseline, this.fitPaint);
                drawIcon(canvas, column.icon, cx, iconTop);
                // The temperature standing on its point, the low hanging under its own
                canvas.drawText(column.value, cx, curveY(column.curve) - dotRadius(i) - this.valueGap, this.valuePaint);
                if (column.value2 != null) {
                    canvas.drawText(column.value2, cx, curveY(column.curve2) + 2 * this.d + this.valueGap + this.value2Digits, this.value2Paint);
                }
                if (column.wind != null) {
                    drawWind(canvas, column, i == 0, cx, windTop + this.windHeight - 5 * this.d);
                }
                if (column.precipitation != null) {
                    drawPrecipitation(canvas, column, cx, precipitationTop + this.precipitationHeight - 4 * this.d);
                }
            }
            drawFooter(canvas, width, precipitationTop + this.precipitationHeight);
            drawSettingsIcon(canvas);
        }

        private void drawSettingsIcon(Canvas canvas) {
            if (!this.model.settingsIcon) {
                return;
            }
            Drawable gear = ContextCompat.getDrawable(this.context, this.model.light ? R.drawable.ic_widget_gear_on_light : R.drawable.ic_widget_gear_on_dark);
            if (gear != null) {
                // Its own state: a vector shares its alpha and picture with the other widgets' icons
                gear = gear.mutate();
                int left = Math.round(6 * this.d);
                int size = Math.round(14 * this.d);
                gear.setBounds(left, left, left + size, left + size);
                gear.setAlpha(77);
                gear.draw(canvas);
            }
        }

        private float centerX(int i) {
            return this.padX + this.columnWidth * (i + 0.5f);
        }

        private float curveY(float value) {
            float top = this.chartTop + 7 * this.d;
            float bottom = this.chartBottom - 7 * this.d;
            return bottom - (value - this.min) / (this.max - this.min) * (bottom - top);
        }

        // The now's point larger
        private float dotRadius(int i) {
            return (i == 0 ? 3.5f : 2.5f) * this.d;
        }

        // --- Background ---

        // The glass as transparent as the alpha, lit from above, with its edge; off the card's edge as widget_glass_inset
        // has it
        void drawBackground(Canvas canvas, int width, int height, int alpha) {
            float radius = radius();
            float inset = this.context.getResources().getDimension(R.dimen.widget_glass_inset);
            this.rect.set(inset, inset, width - inset, height - inset);
            float top = this.rect.top;
            float bottom = this.rect.bottom;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(this.surface);
            paint.setAlpha(alpha);
            canvas.drawRoundRect(this.rect, radius, radius, paint);
            float share = alpha / 255f;
            int light = ContextCompat.getColor(this.context, this.model.light ? R.color.widget_glass_light_highlight : R.color.widget_glass_dark_highlight);
            paint.setShader(new LinearGradient(0, top, 0, top + (bottom - top) * 0.7f, fade(light, share), 0x00ffffff, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(this.rect, radius, radius, paint);
            float edge = Math.max(1, this.d);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(edge);
            int edgeTop = ContextCompat.getColor(this.context, this.model.light ? R.color.widget_glass_light_edge_top : R.color.widget_glass_dark_edge_top);
            int edgeBottom = ContextCompat.getColor(this.context, this.model.light ? R.color.widget_glass_light_edge_bottom : R.color.widget_glass_dark_edge_bottom);
            paint.setShader(new LinearGradient(0, top, 0, bottom, fade(edgeTop, share), fade(edgeBottom, share), Shader.TileMode.CLAMP));
            this.rect.inset(edge / 2, edge / 2);
            canvas.drawRoundRect(this.rect, radius - edge / 2, radius - edge / 2, paint);
        }

        // As the other widgets' glass: the launcher's own radius since Android 12, see widget_background_corner
        private float radius() {
            return this.context.getResources().getDimension(R.dimen.widget_background_corner);
        }

        // --- Header ---

        private void drawHeader(Canvas canvas, int width, float top) {
            if (this.model.temperature == null) {
                return;
            }
            float x = this.padX + 2 * this.d;
            float center = top + this.headerHeight / 2;
            // The digits, about 0.71 of the size high, centred on the row
            canvas.drawText(this.model.temperature, x, center + this.nowPaint.getTextSize() * 0.355f, this.nowPaint);
            x += this.nowPaint.measureText(this.model.temperature) + 6 * this.d;
            float iconSize = 44 * this.d;
            if (this.model.icon != null) {
                drawIconFit(canvas, this.model.icon, x, center - iconSize / 2, iconSize);
            }
            x += iconSize + 10 * this.d;
            float room = width - this.padX - x;
            if (room <= 0) {
                return;
            }
            float lineGap = 3 * this.d;
            float conditionHeight = -this.conditionPaint.ascent();
            float windHeight = -this.nowWindPaint.ascent();
            boolean hasWind = this.model.wind != null && !this.model.wind.isEmpty();
            float block = conditionHeight + (hasWind ? lineGap + windHeight : 0);
            float y = center - block / 2 + conditionHeight;
            if (this.model.condition != null) {
                canvas.drawText(TextUtils.ellipsize(this.model.condition, this.conditionPaint, room, TextUtils.TruncateAt.END).toString(), x, y, this.conditionPaint);
            }
            if (hasWind) {
                canvas.drawText(TextUtils.ellipsize(this.model.wind, this.nowWindPaint, room, TextUtils.TruncateAt.END).toString(), x,
                        y + lineGap + windHeight, this.nowWindPaint);
            }
        }

        // --- Columns ---

        private void drawIcon(Canvas canvas, Drawable icon, float cx, float top) {
            if (icon == null) {
                return;
            }
            float size = this.iconHeight;
            float scale = size / icon.getIntrinsicHeight();
            float w = icon.getIntrinsicWidth() * scale;
            if (w > this.columnWidth * 0.72f) {
                scale = this.columnWidth * 0.72f / icon.getIntrinsicWidth();
                w = icon.getIntrinsicWidth() * scale;
            }
            float h = icon.getIntrinsicHeight() * scale;
            this.iconRect.set(Math.round(cx - w / 2), Math.round(top + (size - h) / 2), Math.round(cx + w / 2), Math.round(top + (size + h) / 2));
            icon.setBounds(this.iconRect);
            icon.draw(canvas);
        }

        private void drawIconFit(Canvas canvas, Drawable icon, float left, float top, float size) {
            float scale = Math.min(size / icon.getIntrinsicWidth(), size / icon.getIntrinsicHeight());
            float w = icon.getIntrinsicWidth() * scale;
            float h = icon.getIntrinsicHeight() * scale;
            this.iconRect.set(Math.round(left + (size - w) / 2), Math.round(top + (size - h) / 2),
                    Math.round(left + (size + w) / 2), Math.round(top + (size + h) / 2));
            icon.setBounds(this.iconRect);
            icon.draw(canvas);
        }

        // An arrow where the wind blows to and its speed, the first one with its unit when that fits
        private void drawWind(Canvas canvas, Column column, boolean first, float cx, float baseline) {
            String text = column.wind;
            if (first) {
                String withUnit = text + " " + column.windUnit;
                if (this.windPaint.measureText(withUnit) + 15 * this.d < this.columnWidth - 2 * this.d) {
                    text = withUnit;
                }
            }
            int color = column.windColor != 0 ? column.windColor : this.text;
            this.windPaint.setColor(color);
            float textWidth = this.windPaint.measureText(text);
            float arrow = Float.isNaN(column.windArrow) ? 0 : 10 * this.d;
            float gap = arrow > 0 ? 4 * this.d : 0;
            float x = cx - (arrow + gap + textWidth) / 2;
            if (arrow > 0) {
                this.windArrow.draw(canvas, x + arrow / 2, baseline - this.windPaint.getTextSize() * 0.36f, arrow, column.windArrow, color);
            }
            canvas.drawText(text, x + arrow + gap, baseline, this.windPaint);
        }

        // The drop as full as the amount, in the middle of the digits' height, the pair centred in the column
        private void drawPrecipitation(Canvas canvas, Column column, float cx, float baseline) {
            float textWidth = this.precipitationPaint.measureText(column.precipitation);
            float dropWidth = this.drop.width();
            float gap = 4 * this.d;
            float x = cx - (dropWidth + gap + textWidth) / 2;
            this.drop.draw(canvas, x + dropWidth / 2, baseline - this.precipitationMiddle, column.precipitationFill);
            canvas.drawText(column.precipitation, x + dropWidth + gap, baseline, this.precipitationPaint);
        }

        // The label in its column as fitPaint draws it. One too long gets smaller, with a gap kept to its neighbours, when
        // it then fits whole at MIN_LABEL_DP or more; else it is cut at its own size, not both small and cut.
        private String fitLabel(String label, TextPaint paint) {
            float room = this.columnWidth - 2 * this.d;
            this.fitPaint.set(paint);
            float width = paint.measureText(label);
            if (width > room) {
                float size = paint.getTextSize() * (this.columnWidth - 6 * this.d) / width;
                if (size >= MIN_LABEL_DP * this.d) {
                    this.fitPaint.setTextSize(size);
                }
            }
            return TextUtils.ellipsize(label, this.fitPaint, room, TextUtils.TruncateAt.END).toString();
        }

        // --- Curves ---

        private void drawCurves(Canvas canvas) {
            int count = this.model.columns.size();
            if (count > 1) {
                // A soft fill under the main curve, fading down
                this.path.reset();
                ForecastCurve.appendFill(this.path, this.pointX, this.pointY, 0, count - 1, this.chartBottom);
                this.fillPaint.setShader(ForecastCurve.fillShader(this.chartTop, this.chartBottom, this.model.light ? 0x12000000 : 0x2effffff));
                canvas.drawPath(this.path, this.fillPaint);
            }
            // The curves and their points in a layer of their own, so that the gaps around the points show what is
            // under the curves, the glass or, on a clear card, the wallpaper
            int layer = canvas.saveLayer(null, null);
            if (count > 1) {
                if (this.model.daily) {
                    strokeCurve(canvas, true);
                }
                strokeCurve(canvas, false);
            }
            drawDots(canvas);
            canvas.restoreToCount(layer);
        }

        // Along the curve each point in the colour of its temperature
        private void strokeCurve(Canvas canvas, boolean second) {
            int count = this.model.columns.size();
            float[] y = second ? this.pointY2 : this.pointY;
            this.path.reset();
            this.path.moveTo(this.pointX[0], y[0]);
            ForecastCurve.append(this.path, this.pointX, y, 0, count - 1);
            int[] colors = new int[count];
            for (int i = 0; i < count; i++) {
                Column column = this.model.columns.get(i);
                colors[i] = second ? ForecastCurve.low(column.color2) : column.color;
            }
            this.curvePaint.setStrokeWidth((second ? 2 : 2.5f) * this.d);
            this.curvePaint.setShader(ForecastCurve.colorShader(this.pointX, colors));
            canvas.drawPath(this.path, this.curvePaint);
        }

        // Each point with a gap around it cut out of the curves: all the gaps first, so a day's high and low close
        // together don't cut each other
        private void drawDots(Canvas canvas) {
            float gap = 2 * this.d;
            for (int pass = 0; pass < 2; pass++) {
                boolean cut = pass == 0;
                for (int i = 0; i < this.model.columns.size(); i++) {
                    Column column = this.model.columns.get(i);
                    float cx = centerX(i);
                    drawDot(canvas, cx, curveY(column.curve), dotRadius(i), column.color, cut ? gap : -1);
                    if (!Float.isNaN(column.curve2)) {
                        drawDot(canvas, cx, curveY(column.curve2), 2 * this.d, column.color2, cut ? gap : -1);
                    }
                }
            }
        }

        // The point, or with a gap its cut-out around it
        private void drawDot(Canvas canvas, float cx, float cy, float r, int color, float gap) {
            if (gap >= 0) {
                canvas.drawCircle(cx, cy, r + gap, this.clearPaint);
            } else {
                this.dotPaint.setColor(color);
                canvas.drawCircle(cx, cy, r, this.dotPaint);
            }
        }

        // --- Footer ---

        private void drawFooter(Canvas canvas, int width, float top) {
            float center = top + this.footerHeight / 2;
            Drawable refresh = ContextCompat.getDrawable(this.context, this.model.light ? R.drawable.ic_widget_refresh_on_light : R.drawable.ic_widget_refresh_on_dark);
            float size = 16 * this.d;
            float x = this.padX - this.d;
            if (refresh != null) {
                refresh = refresh.mutate();
                refresh.setBounds(Math.round(x), Math.round(center - size / 2), Math.round(x + size), Math.round(center + size / 2));
                refresh.setAlpha(150);
                refresh.draw(canvas);
            }
            x += size + 5 * this.d;
            if (this.model.place != null) {
                float baseline = center - (this.placePaint.ascent() + this.placePaint.descent()) / 2;
                canvas.drawText(TextUtils.ellipsize(this.model.place, this.placePaint, width - this.padX - x, TextUtils.TruncateAt.END).toString(),
                        x, baseline, this.placePaint);
            }
        }

        private static int fade(int color, float alpha) {
            return (color & 0x00ffffff) | (Math.round(Color.alpha(color) * alpha) << 24);
        }
    }
}
