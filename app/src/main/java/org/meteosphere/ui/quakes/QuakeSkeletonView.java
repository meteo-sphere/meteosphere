package org.meteosphere.ui.quakes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.MaterialColors;

/**
 * The earthquakes' summary card and a day of their lines as plain shapes with a sheen passing over them, held in their
 * place while the list is on its way
 */
public final class QuakeSkeletonView extends View {

    private static final long SHEEN_MS = 1300;
    // The summary card, the day's title and its lines, in dp
    private static final float SUMMARY_TOP = 12;
    private static final float SUMMARY_HEIGHT = 200;
    private static final float DAY_TITLE = 44;
    private static final float ROW = 76;
    private static final int ROWS = 3;
    private static final float HEIGHT = SUMMARY_TOP + SUMMARY_HEIGHT + 4 + DAY_TITLE + ROWS * ROW + 8;

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint();
    private final RectF rect = new RectF();
    private final Matrix sheenMatrix = new Matrix();
    private final float density;
    private final int shapeColor;
    private final int sheenColor;
    private LinearGradient sheen;
    private long sheenSince;
    // The sheen passes while the list is on its way, and with animations on
    private boolean isSheenOn;
    private boolean isMotionOn;

    public QuakeSkeletonView(Context context) {
        this(context, null);
    }

    public QuakeSkeletonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        this.cardPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, 0xff1d2026));
        this.shapeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xff3a3f48);
        // Lighter over the shapes in either theme, faintly in the dark one
        this.sheenColor = ColorUtils.blendARGB(this.shapeColor, Color.WHITE, ColorUtils.calculateLuminance(this.shapeColor) > 0.5 ? 0.65f : 0.09f);
        this.shapePaint.setColor(this.shapeColor);
        this.linePaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0xff4a505a));
        this.linePaint.setStrokeWidth(Math.max(1, this.density));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(HEIGHT * this.density));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // A band of light a third of the width wide
        this.sheen = new LinearGradient(0, 0, w / 3f, 0, new int[]{this.shapeColor, this.sheenColor, this.shapeColor}, null,
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.sheenSince = AnimationUtils.currentAnimationTimeMillis();
        this.isMotionOn = QuakeMotion.isOn(getContext());
    }

    /** The sheen passing over the shapes, or the shapes standing plain */
    public void setSheen(boolean on) {
        if (on != this.isSheenOn) {
            this.isSheenOn = on;
            this.sheenSince = AnimationUtils.currentAnimationTimeMillis();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = this.density;
        float width = getWidth();
        boolean moving = this.sheen != null && this.isSheenOn && this.isMotionOn;
        this.shapePaint.setShader(moving ? this.sheen : null);
        if (moving) {
            // The band passes from before the left edge to past the right one
            float phase = ((AnimationUtils.currentAnimationTimeMillis() - this.sheenSince) % SHEEN_MS) / (float) SHEEN_MS;
            this.sheenMatrix.setTranslate(-width / 3f + phase * width * 4 / 3f, 0);
            this.sheen.setLocalMatrix(this.sheenMatrix);
        }
        // The summary: its title, the strongest large, a line, the next two small
        float top = SUMMARY_TOP * d;
        card(canvas, top, SUMMARY_HEIGHT * d);
        bar(canvas, 32 * d, top + 16 * d, width * 0.5f, 12 * d);
        canvas.drawCircle(60 * d, top + 70 * d, 28 * d, this.shapePaint);
        bar(canvas, 102 * d, top + 54 * d, width - 48 * d, 16 * d);
        bar(canvas, 102 * d, top + 78 * d, width * 0.6f, 12 * d);
        canvas.drawLine(32 * d, top + 112 * d, width - 32 * d, top + 112 * d, this.linePaint);
        for (int i = 0; i < 2; i++) {
            float y = top + (140 + i * 38) * d;
            canvas.drawCircle(48 * d, y, 16 * d, this.shapePaint);
            bar(canvas, 76 * d, y - 12 * d, width * 0.7f, 11 * d);
            bar(canvas, 76 * d, y + 3 * d, width * 0.45f, 9 * d);
        }
        // A day's title over its lines
        top += (SUMMARY_HEIGHT + 4) * d;
        bar(canvas, 20 * d, top + 18 * d, 110 * d, 12 * d);
        top += DAY_TITLE * d;
        card(canvas, top, ROWS * ROW * d);
        for (int i = 0; i < ROWS; i++) {
            float y = top + (i + 0.5f) * ROW * d;
            canvas.drawCircle(56 * d, y, 24 * d, this.shapePaint);
            bar(canvas, 96 * d, y - 16 * d, width * 0.62f, 14 * d);
            bar(canvas, 96 * d, y + 6 * d, width * 0.4f, 10 * d);
            bar(canvas, width - 92 * d, y - 16 * d, width - 40 * d, 22 * d);
            if (i < ROWS - 1) {
                float line = top + (i + 1) * ROW * d;
                canvas.drawLine(96 * d, line, width - 32 * d, line, this.linePaint);
            }
        }
        if (moving) {
            postInvalidateOnAnimation();
        }
    }

    private void card(Canvas canvas, float top, float height) {
        float d = this.density;
        this.rect.set(16 * d, top, getWidth() - 16 * d, top + height);
        canvas.drawRoundRect(this.rect, 24 * d, 24 * d, this.cardPaint);
    }

    // A rounded bar from left to right
    private void bar(Canvas canvas, float left, float top, float right, float height) {
        this.rect.set(left, top, right, top + height);
        canvas.drawRoundRect(this.rect, height / 2, height / 2, this.shapePaint);
    }
}
