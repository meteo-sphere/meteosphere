package org.meteosphere.ui.main;

import org.meteosphere.ui.glass.GlassBlur;
import org.meteosphere.ui.glass.GlassEdge;
import org.meteosphere.ui.glass.GlassRedraw;
import org.meteosphere.ui.glass.GlassStyle;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingtoolbar.FloatingToolbarLayout;
import com.google.android.material.shape.MaterialShapeDrawable;
import java.util.ArrayList;

/**
 * The main screen's tabs on Material's floating toolbar: an icon button per tab, the chosen one checked and with
 * its name. The tabs are the settings' list, in their order, so a new tab is just one more button. A name too long
 * for the bar is set smaller in two lines, hyphenated; only when even that doesn't fit is the tab left with its icon.
 *
 * The toolbar is frosted glass, dark in the dark theme and light in the light one: from Android 12 what lies under it
 * is blurred on the GPU, redrawn only when something under it scrolls or is laid out again; before that it is the same
 * tint, denser, without the blur.
 */
public class TabPillBar extends FloatingToolbarLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private final float density;
    private final LinearLayout row;
    private final ArrayList<MaterialButton> buttons = new ArrayList<>();
    private final ArrayList<CharSequence> names = new ArrayList<>();
    private int selected = -1;
    // How the chosen tab shows its name: in a line, smaller in two lines, or not at all when neither fits
    private static final int NAME_ONE_LINE = 0;
    private static final int NAME_TWO_LINES = 1;
    private static final int NAME_NONE = 2;
    private int nameMode = NAME_ONE_LINE;
    // The width of the two lines' text, the button set to it so they break as they were measured
    private int twoLinesWidth;
    private final TextPaint namePaint;
    private final TextPaint smallNamePaint;
    private OnTabSelectedListener listener;

    private final GlassStyle style;
    // What is blurred: the pages, over the window's background
    private View blurSource;
    private final GlassBlur glass;
    private final GlassEdge edge;
    private final GlassRedraw redraw = new GlassRedraw(this);

    public TabPillBar(Context context) {
        this(context, null);
    }

    public TabPillBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.density = getResources().getDisplayMetrics().density;
        this.glass = new GlassBlur(GlassBlur.BLUR_DP * this.density);
        this.row = new LinearLayout(getContext());
        this.row.setOrientation(LinearLayout.HORIZONTAL);
        this.row.setGravity(Gravity.CENTER_VERTICAL);
        addView(this.row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setWillNotDraw(false);
        this.style = GlassStyle.of(context);
        if (getBackground() instanceof MaterialShapeDrawable) {
            ((MaterialShapeDrawable) getBackground()).setFillColor(ColorStateList.valueOf(this.style.tint(false)));
        }
        this.edge = new GlassEdge(this.style, this.density);
        this.namePaint = textPaint(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        this.smallNamePaint = textPaint(context, com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
    }

    // A text appearance's paint, with the locale of the text, for its hyphenation
    private static TextPaint textPaint(Context context, int appearance) {
        TextView view = new TextView(context);
        view.setTextAppearance(appearance);
        return new TextPaint(view.getPaint());
    }

    /** The view whose drawing is blurred under the toolbar, with the window's background under it */
    public void setBlurSource(View source) {
        this.blurSource = source;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.redraw.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        this.redraw.detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.edge.setSize(w, h);
    }

    @Override
    public void draw(Canvas canvas) {
        this.glass.draw(canvas, this, this.blurSource, getHeight() / 2f);
        super.draw(canvas);
        this.edge.drawPill(canvas);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public void setTabs(int[] iconRes, CharSequence[] names) {
        this.row.removeAllViews();
        this.buttons.clear();
        this.names.clear();
        this.selected = -1;
        for (int i = 0; i < iconRes.length; i++) {
            // The toolbar's theme gives its icon buttons their style, colours and checked state
            MaterialButton button = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialIconButtonStyle);
            button.setIconResource(iconRes[i]);
            button.setCheckable(true);
            button.setContentDescription(names[i]);
            button.setSingleLine(true);
            // The icon buttons' own sizes are for a row of actions: a tab is 48 square, the chosen one as wide as its name
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            button.setIconSize(Math.round(24 * this.density));
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            // White on the dark glass, dark on the light one; the chosen tab on a pill of its own
            button.setBackgroundTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{this.style.barChecked, 0x00000000}));
            button.setIconTint(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{this.style.text, this.style.barIconDim}));
            button.setRippleColor(ColorStateList.valueOf(this.style.ripple));
            int index = i;
            button.setOnClickListener(v -> {
                // A tap doesn't uncheck the chosen one: the pager says which is chosen
                button.setChecked(index == this.selected);
                if (this.listener != null) {
                    this.listener.onTabSelected(index);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Math.round(48 * this.density));
            if (i > 0) {
                params.setMarginStart(Math.round(4 * this.density));
            }
            this.row.addView(button, params);
            this.buttons.add(button);
            this.names.add(names[i]);
        }
    }

    /** Checks the tab chosen: it grows with its name, the others shrink to their icons */
    public void setSelected(int index, boolean animate) {
        if (index == this.selected || index < 0 || index >= this.buttons.size()) {
            return;
        }
        if (animate && isLaidOut()) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(180);
            TransitionManager.beginDelayedTransition(this, transition);
        }
        this.selected = index;
        for (int i = 0; i < this.buttons.size(); i++) {
            this.buttons.get(i).setChecked(i == index);
        }
        applyLabels();
        // Whether the new name fits is decided while measuring
        requestLayout();
    }

    private void applyLabels() {
        for (int i = 0; i < this.buttons.size(); i++) {
            MaterialButton button = this.buttons.get(i);
            boolean labelled = i == this.selected && this.nameMode != NAME_NONE;
            boolean twoLines = labelled && this.nameMode == NAME_TWO_LINES;
            button.setTextAppearance(twoLines ? com.google.android.material.R.style.TextAppearance_Material3_LabelMedium
                    : com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            if (twoLines) {
                button.setSingleLine(false);
                button.setMaxLines(2);
                button.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
                button.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
                // The two lines flush at their start, beside the icon
                button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            } else {
                button.setSingleLine(true);
                button.setGravity(Gravity.CENTER);
            }
            button.setText(labelled ? this.names.get(i) : null);
            button.setIconPadding(labelled ? labelGap() : 0);
            int start = labelled ? labelStart() : Math.round(12 * this.density);
            int end = labelled ? labelEnd() : Math.round(12 * this.density);
            button.setPaddingRelative(start, 0, end, 0);
            ViewGroup.LayoutParams params = button.getLayoutParams();
            if (params != null) {
                params.width = twoLines ? start + button.getIconSize() + labelGap() + this.twoLinesWidth + end : ViewGroup.LayoutParams.WRAP_CONTENT;
                button.setLayoutParams(params);
            }
            // The text colour follows the checked state as the icon's does
            button.setTextColor(button.getIconTint());
        }
    }

    private int labelStart() {
        return Math.round(16 * this.density);
    }

    private int labelEnd() {
        return Math.round(20 * this.density);
    }

    private int labelGap() {
        return Math.round(8 * this.density);
    }

    // With many tabs on a narrow screen the chosen one's name goes smaller in two lines, and only then is dropped,
    // rather than the bar running off the screen
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (this.selected >= 0 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            MaterialButton chosen = this.buttons.get(this.selected);
            int otherWidth = 0;
            for (int i = 0; i < this.buttons.size(); i++) {
                if (i != this.selected) {
                    MaterialButton button = this.buttons.get(i);
                    button.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                    otherWidth += button.getMeasuredWidth();
                }
            }
            // The room left for the name beside the others, the gaps and the chosen tab's own padding and icon
            int room = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight() - otherWidth
                    - Math.round((this.buttons.size() - 1) * 4 * this.density) - labelStart() - labelEnd() - chosen.getIconSize() - labelGap();
            CharSequence name = this.names.get(this.selected);
            int nameMode = NAME_ONE_LINE;
            int twoLinesWidth = this.twoLinesWidth;
            if (this.namePaint.measureText(name.toString()) > room) {
                nameMode = NAME_NONE;
                if (room > 0) {
                    StaticLayout lines = StaticLayout.Builder.obtain(name, 0, name.length(), this.smallNamePaint, room)
                            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
                            .setIncludePad(false)
                            .build();
                    if (lines.getLineCount() <= 2) {
                        float widest = 0;
                        for (int line = 0; line < lines.getLineCount(); line++) {
                            widest = Math.max(widest, lines.getLineMax(line));
                        }
                        nameMode = NAME_TWO_LINES;
                        twoLinesWidth = (int) Math.ceil(widest) + 1;
                    }
                }
            }
            // Only a change is applied: the buttons laid out again would measure the bar again
            if (nameMode != this.nameMode || twoLinesWidth != this.twoLinesWidth) {
                this.nameMode = nameMode;
                this.twoLinesWidth = twoLinesWidth;
                applyLabels();
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
