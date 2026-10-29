package tv.skyhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.ScrollView;

/** Persistent, non-interactive scroll position; absent when all content fits. */
final class RailScrollView extends ScrollView {
    private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RailScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        // Reserve a gutter so the rail never covers focused or two-line text.
        setPadding(0, 0, dp(20), 0);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        invalidate();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int extent = computeVerticalScrollExtent(), range = computeVerticalScrollRange();
        if (extent <= 0 || range <= extent || getHeight() <= dp(12)) return;
        float top = dp(6), height = getHeight() - dp(12);
        float thumb = Math.min(height, Math.max(dp(28), height * extent / range));
        float position = Math.max(0f, Math.min(1f, computeVerticalScrollOffset() / (float)(range - extent)));
        float x = getWidth() - dp(7), thickness = Math.max(1, dp(1.5f));
        int save = canvas.save();
        // ScrollView's drawing canvas is in content coordinates; keep this overlay in the viewport.
        canvas.translate(getScrollX(), getScrollY());
        railPaint.setColor(0x30d1e5dd);
        canvas.drawRoundRect(x, top, x + thickness, top + height, thickness, thickness, railPaint);
        float thumbTop = top + position * (height - thumb);
        railPaint.setColor(0xb8d1e5dd);
        canvas.drawRoundRect(x, thumbTop, x + thickness, thumbTop + thumb, thickness, thickness, railPaint);
        canvas.restoreToCount(save);
    }
}
