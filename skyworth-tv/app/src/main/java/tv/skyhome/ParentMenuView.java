package tv.skyhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Inert parent-menu echo. Each line's left half is outside the screen. */
final class ParentMenuView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> labels = new ArrayList<>();
    private String heading = "";
    private int selection = -1, scrollY;
    private float textSp = 23;

    ParentMenuView(Context context) {
        super(context);
        setFocusable(false);setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void showMenu(String title, List<String> entries, int selected, int offset, float size) {
        heading=title;labels.clear();labels.addAll(entries);
        selection=selected;scrollY=offset;textSp=size;invalidate();
    }

    private float dp(float n) { return n*getResources().getDisplayMetrics().density; }
    private float sp(float n) { return n*getResources().getDisplayMetrics().scaledDensity; }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        paint.setShader(new LinearGradient(0,0,Math.max(1,w),0,
                new int[]{Color.WHITE,Color.WHITE,Color.TRANSPARENT},
                new float[]{0,.60f,1},Shader.TileMode.CLAMP));
    }

    private void line(Canvas canvas, String value, float top, float height, float size, int alpha) {
        paint.setTextSize(sp(size));paint.setAlpha(alpha);
        Paint.FontMetrics fm=paint.getFontMetrics();
        float baseline=top+(height-(fm.descent-fm.ascent))/2-fm.ascent;
        canvas.drawText(value,-paint.measureText(value)/2,baseline,paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        line(canvas,heading,0,dp(34),12,160);
        int save=canvas.save();canvas.clipRect(0,dp(34),getWidth(),getHeight());
        for(int i=0;i<labels.size();i++) {
            float y=dp(40+i*54)-scrollY;
            if(y+dp(54)<dp(34)||y>getHeight())continue;
            boolean selected=i==selection;
            line(canvas,labels.get(i),y,dp(54),textSp*(selected?1.2f:1f),selected?255:170);
        }
        canvas.restoreToCount(save);
    }
}
