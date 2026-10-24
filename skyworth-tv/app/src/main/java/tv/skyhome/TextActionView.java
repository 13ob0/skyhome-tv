package tv.skyhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

/** Text grows inside a fixed hit/focus rectangle, so remote navigation never shifts rows. */
final class TextActionView extends TextView {
    private float emphasis = 1f;
    private ValueAnimator animation;
    private boolean ready;

    TextActionView(Context context) { super(context); ready = true; }
    TextActionView(Context context, AttributeSet attrs) { super(context, attrs); ready = true; }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (!ready) return;
        boolean highlighted = isFocused() || isSelected() || isPressed();
        setTextColor(highlighted ? Color.WHITE : 0xff9ca7aa);
        float target = highlighted ? 1.2f : 1f;
        if (animation != null) animation.cancel();
        animation = ValueAnimator.ofFloat(emphasis, target);
        animation.setDuration(150);
        animation.addUpdateListener(a -> { emphasis = (float)a.getAnimatedValue(); invalidate(); });
        animation.start();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Reserve the enlargement inside the cell, including long names and the last grid column.
        int reserve = Math.round(w * .19f);
        if (getPaddingRight() != reserve) setPadding(getPaddingLeft(), getPaddingTop(), reserve, getPaddingBottom());
    }

    @Override protected void onDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.scale(emphasis, emphasis, getPaddingLeft(), getHeight() / 2f);
        super.onDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override protected void onDetachedFromWindow() {
        if (animation != null) animation.cancel();
        super.onDetachedFromWindow();
    }
}
