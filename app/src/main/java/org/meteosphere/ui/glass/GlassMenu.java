package org.meteosphere.ui.glass;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.RequiresApi;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.material.chip.Chip;
import java.util.List;
import org.meteosphere.R;

/**
 * A Material 3 menu dropping from a view, on the frosted glass of the tabs' toolbar, dark or light with the theme:
 * what lies under it is blurred from Android 12. It is a view over the activity's content rather than a window of its
 * own, so that the blur can draw what is under it; a tap outside or Back closes it. Back closes it first, before the
 * keyboard or an expanded search in the bar hears of it, as it would a popup window: the menu holds the focus while it
 * is open and gives it back as it closes.
 */
public final class GlassMenu {

    /** A choice of the menu; without an icon when it is 0, greyed out and not to be chosen when not enabled */
    public static final class Item {
        public final int icon;
        public final CharSequence title;
        final CharSequence subtitle;
        final boolean checked;
        final boolean dividerAfter;
        final boolean enabled;

        public Item(int icon, CharSequence title, CharSequence subtitle, boolean checked, boolean dividerAfter) {
            this(icon, title, subtitle, checked, dividerAfter, true);
        }

        public Item(int icon, CharSequence title, CharSequence subtitle, boolean checked, boolean dividerAfter, boolean enabled) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.checked = checked;
            this.dividerAfter = dividerAfter;
            this.enabled = enabled;
        }
    }

    public interface OnItemClick {
        void onItemClick(int index);
    }

    private final Activity activity;
    private final ViewGroup decor;
    private final FrameLayout overlay;
    private final GlassPanel panel;
    private final float density;
    private final GlassStyle style;
    private final OnBackPressedCallback back;
    private final Runnable onDismiss;
    private final int placement;
    private boolean opensUp;
    private boolean dismissed;
    private View previousFocus;
    private Object systemBack;
    // The view it was opened from: the menu goes with it, as when the main screen makes its tabs again on coming back,
    // and a choice would reach a tab no longer there
    private final View anchor;
    private final View.OnAttachStateChangeListener anchorGone = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View view) {
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            dismiss();
        }
    };

    // Where the menu stands by its anchor
    private static final int UNDER = 0;
    private static final int AT_ROW = 1;
    private static final int ABOVE = 2;

    private GlassMenu(Activity activity, View anchor, int placement, List<Item> items, OnItemClick listener, Runnable onDismiss) {
        this.placement = placement;
        this.onDismiss = onDismiss;
        this.activity = activity;
        this.anchor = anchor;
        this.decor = (ViewGroup) activity.getWindow().getDecorView();
        Context context = anchor.getContext();
        this.density = context.getResources().getDisplayMetrics().density;
        this.style = GlassStyle.of(activity);

        this.overlay = new FrameLayout(context) {
            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                        dismiss();
                    }
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }
        };
        this.overlay.setClickable(true);
        this.overlay.setFocusableInTouchMode(true);
        this.overlay.setOnClickListener(v -> dismiss());

        this.panel = new GlassPanel(context, activity.findViewById(android.R.id.content), this.density, this.style);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(6);
        list.setPadding(0, pad, 0, pad);
        boolean choice = false;
        for (Item item : items) {
            choice |= item.checked;
        }
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int index = i;
            View row = row(context, item, choice);
            if (item.enabled) {
                row.setOnClickListener(v -> {
                    dismiss();
                    listener.onItemClick(index);
                });
            }
            list.addView(row);
            if (item.dividerAfter && i < items.size() - 1) {
                View divider = new View(context);
                divider.setBackgroundColor(this.style.divider);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
                params.setMargins(dp(16), dp(6), dp(16), dp(6));
                list.addView(divider, params);
            }
        }
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(list);
        this.panel.addView(scroll);

        int[] location = new int[2];
        anchor.getLocationInWindow(location);
        int windowWidth = this.decor.getWidth();
        int windowHeight = this.decor.getHeight();
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (placement == ABOVE) {
            // Over the anchor, a bar at the bottom, ending where it ends
            int right = location[0] + anchor.getWidth();
            int bottom = location[1] - dp(8);
            this.opensUp = true;
            this.panel.setLimits(dp(220), Math.min(dp(320), right - dp(16)), bottom - dp(16) - systemBarHeight(true));
            panelParams.rightMargin = windowWidth - right;
            panelParams.bottomMargin = windowHeight - bottom;
            panelParams.gravity = Gravity.BOTTOM | Gravity.END;
        } else if (placement == AT_ROW) {
            // From a row's end, over its lower edge; above the row when there's more room there than below it. It stays
            // off the window's sides, moved in from a narrow anchor's end when it is wider than the room before it
            int right = Math.min(location[0] + anchor.getWidth(), windowWidth - dp(16));
            int maxWidth = Math.min(dp(320), windowWidth - dp(32));
            int overlap = dp(6);
            int belowTop = location[1] + anchor.getHeight() - overlap;
            int aboveBottom = location[1] + overlap;
            int roomBelow = windowHeight - belowTop - dp(16) - systemBarHeight(false);
            int roomAbove = aboveBottom - dp(16) - systemBarHeight(true);
            this.panel.setLimits(dp(220), maxWidth, windowHeight);
            this.panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            right = Math.max(right, dp(16) + this.panel.getMeasuredWidth());
            this.opensUp = this.panel.getMeasuredHeight() > roomBelow && roomAbove > roomBelow;
            this.panel.setLimits(dp(220), maxWidth, this.opensUp ? roomAbove : roomBelow);
            panelParams.rightMargin = windowWidth - right;
            if (this.opensUp) {
                panelParams.bottomMargin = windowHeight - aboveBottom;
                panelParams.gravity = Gravity.BOTTOM | Gravity.END;
            } else {
                panelParams.topMargin = belowTop;
                panelParams.gravity = Gravity.TOP | Gravity.END;
            }
        } else {
            // Under the anchor, as the chip shows itself: a chip is taller than it looks, for its touch target
            int visualHeight = anchor instanceof Chip ? Math.round(((Chip) anchor).getChipMinHeight()) : anchor.getHeight();
            int top = location[1] + (anchor.getHeight() + visualHeight) / 2 + dp(6);
            int left = location[0];
            int bottomRoom = windowHeight - top - dp(16) - systemBarHeight(false);
            this.panel.setLimits(dp(220), Math.min(dp(320), windowWidth - left - dp(16)), bottomRoom);
            panelParams.leftMargin = left;
            panelParams.topMargin = top;
            panelParams.gravity = Gravity.TOP | Gravity.START;
        }
        this.overlay.addView(this.panel, panelParams);

        this.back = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                dismiss();
            }
        };
    }

    /** Shows the items under the anchor; the listener is told which one was chosen, the menu closing first */
    public static GlassMenu show(View anchor, List<Item> items, OnItemClick listener) {
        return show(anchor, UNDER, items, listener, null);
    }

    /** Shows the items over the anchor, a bar at the bottom of the screen, its end at the anchor's end */
    public static GlassMenu showAbove(View anchor, List<Item> items, OnItemClick listener) {
        return show(anchor, ABOVE, items, listener, null);
    }

    /**
     * Shows the items from a list row's end, as a setting's choices. onDismiss runs whenever the menu closes, before
     * the listener hears of a choice.
     */
    public static GlassMenu showAtRow(View row, List<Item> items, OnItemClick listener, Runnable onDismiss) {
        return show(row, AT_ROW, items, listener, onDismiss);
    }

    private static GlassMenu show(View anchor, int placement, List<Item> items, OnItemClick listener, Runnable onDismiss) {
        Activity activity = GlassStyle.activityOf(anchor.getContext());
        if (activity == null) {
            return null;
        }
        GlassMenu menu = new GlassMenu(activity, anchor, placement, items, listener, onDismiss);
        menu.previousFocus = menu.decor.findFocus();
        menu.decor.addView(menu.overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        anchor.addOnAttachStateChangeListener(menu.anchorGone);
        menu.overlay.requestFocus();
        if (activity instanceof ComponentActivity) {
            ((ComponentActivity) activity).getOnBackPressedDispatcher().addCallback(menu.back);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            menu.systemBack = SystemBack.register(activity, menu::dismiss);
        }
        View panel = menu.panel;
        panel.setAlpha(0f);
        // Growing from the anchor: down from its start, or from its end, up when it opens above it
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        panel.setPivotX(placement == UNDER ? 0 : panel.getMeasuredWidth());
        panel.setPivotY(menu.opensUp ? panel.getMeasuredHeight() : 0);
        panel.setScaleY(0.9f);
        panel.setTranslationY(menu.opensUp ? menu.dp(4) : -menu.dp(4));
        panel.animate().alpha(1f).scaleY(1f).translationY(0).setDuration(160).setInterpolator(new DecelerateInterpolator());
        return menu;
    }

    /** Closes the menu, as a tap outside does */
    public void dismiss() {
        if (this.dismissed) {
            return;
        }
        this.dismissed = true;
        this.anchor.removeOnAttachStateChangeListener(this.anchorGone);
        this.back.remove();
        if (Build.VERSION.SDK_INT >= 33 && this.systemBack != null) {
            SystemBack.unregister(this.activity, this.systemBack);
        }
        this.overlay.setClickable(false);
        // The focus goes back where it was, or nowhere as before: removed with the focus, the menu would hand it to the
        // first list that takes it
        if (this.previousFocus != null && this.previousFocus.isAttachedToWindow() && this.previousFocus.isShown()) {
            this.previousFocus.requestFocus();
        } else {
            this.overlay.clearFocus();
        }
        this.panel.animate().alpha(0f).setDuration(100).withEndAction(() -> this.decor.removeView(this.overlay));
        if (this.onDismiss != null) {
            this.onDismiss.run();
        }
    }

    // A row of a menu of choices, where one is checked, is told checked or not to TalkBack too, not only by its tick
    private View row(Context context, Item item, boolean choice) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setMinimumHeight(dp(item.subtitle != null ? 56 : 48));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        shape.setColor(item.checked ? this.style.checkedFill : 0);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(12));
        mask.setColor(0xffffffff);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(this.style.ripple), shape, mask));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(dp(6), 0, dp(6), 0);
        row.setLayoutParams(rowParams);

        if (item.icon != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(item.icon);
            icon.setImageTintList(ColorStateList.valueOf(item.checked ? this.style.text : this.style.textDim));
            row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(item.title);
        title.setTextColor(this.style.text);
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (item.checked) {
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        texts.addView(title);
        if (item.subtitle != null) {
            TextView subtitle = new TextView(context);
            subtitle.setText(item.subtitle);
            subtitle.setTextColor(this.style.textDim);
            subtitle.setTextSize(12);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subtitle);
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.setMargins(item.icon != 0 ? dp(12) : dp(4), 0, dp(12), 0);
        row.addView(texts, textParams);

        if (item.checked) {
            ImageView check = new ImageView(context);
            check.setImageResource(R.drawable.ic_check);
            check.setImageTintList(ColorStateList.valueOf(this.style.text));
            row.addView(check, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        row.setContentDescription(item.subtitle != null ? item.title + ", " + item.subtitle : item.title);
        if (choice) {
            ViewCompat.setAccessibilityDelegate(row, new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCheckable(true);
                    info.setChecked(item.checked);
                }
            });
        }
        if (!item.enabled) {
            // Material's disabled content
            row.setAlpha(0.38f);
            row.setEnabled(false);
        }
        return row;
    }

    // The status bar's height at the top, the navigation bar's or the keyboard's at the bottom
    private int systemBarHeight(boolean top) {
        androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(this.decor);
        if (insets == null) {
            return 0;
        }
        androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                | androidx.core.view.WindowInsetsCompat.Type.ime());
        return top ? bars.top : bars.bottom;
    }

    private int dp(float value) {
        return Math.round(value * this.density);
    }

    /**
     * The system's Back callback from Android 13, over the bar's own: an expanded search takes Back that way before the
     * activity hears of it. Kept apart so that older systems never load its classes.
     */
    @RequiresApi(33)
    private static final class SystemBack {
        static Object register(Activity activity, Runnable onBack) {
            OnBackInvokedCallback callback = onBack::run;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            return callback;
        }

        static void unregister(Activity activity, Object callback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) callback);
        }
    }

    /** The menu's card: the glass, its tint and a light edge, its content within the limits it is given */
    private static final class GlassPanel extends FrameLayout {
        private final View source;
        private final GlassStyle style;
        private final GlassBlur glass;
        private final float radius;
        private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GlassEdge edge;
        private final RectF bounds = new RectF();
        private int minWidth;
        private int maxWidth;
        private int maxHeight;

        GlassPanel(Context context, View source, float density, GlassStyle style) {
            super(context);
            this.source = source;
            this.style = style;
            this.glass = new GlassBlur(24 * density);
            this.radius = 16 * density;
            this.tintPaint.setColor(style.tint(true));
            this.edge = new GlassEdge(style, density);
            setWillNotDraw(false);
            setClickable(true);
            setElevation(6 * density);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), GlassPanel.this.radius);
                    // A soft shadow: the dark glass lets most of it through
                    outline.setAlpha(GlassPanel.this.style.shadowAlpha);
                }
            });
            setClipToOutline(true);
        }

        void setLimits(int minWidth, int maxWidth, int maxHeight) {
            this.minWidth = minWidth;
            this.maxWidth = Math.max(minWidth, maxWidth);
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(this.maxWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(Math.max(0, this.maxHeight), MeasureSpec.AT_MOST));
            if (getMeasuredWidth() < this.minWidth) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(this.minWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(0, this.maxHeight), MeasureSpec.AT_MOST));
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            this.bounds.set(0, 0, w, h);
            this.edge.setSize(w, h);
        }

        @Override
        public void draw(Canvas canvas) {
            this.glass.draw(canvas, this, this.source, this.radius);
            canvas.drawRoundRect(this.bounds, this.radius, this.radius, this.tintPaint);
            super.draw(canvas);
            this.edge.draw(canvas, this.radius);
        }
    }
}
