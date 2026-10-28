package org.meteosphere.ui.details;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.viewpager.widget.ViewPager;

/**
 * The insights' pager, as tall as its tallest page. The pages are added while the pager itself is measured, so they
 * are measured after that: measuring them first gives no height when the adapter has just changed.
 */
public class InsightsPager extends ViewPager {

    public InsightsPager(Context context) {
        super(context);
    }

    public InsightsPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // The city pager asks the views under the finger, hidden ones too, where they last stood: hidden insights would
    // keep the swipe from turning to the next city
    @Override
    public boolean canScrollHorizontally(int direction) {
        return isShown() && super.canScrollHorizontally(direction);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int childWidthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY);
        int height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(childWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            height = Math.max(height, child.getMeasuredHeight());
        }
        if (height > 0) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height + getPaddingTop() + getPaddingBottom(), MeasureSpec.EXACTLY));
        }
    }
}
