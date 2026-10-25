package space.pengtao.ledger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/** 自定义环形占比图（纯 Canvas，无第三方依赖）。 */
public class PieChartView extends View {

    public static class Slice {
        public int color;
        public float value;

        public Slice(int color, float value) {
            this.color = color;
            this.value = value;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private List<Slice> slices;
    private float total;

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSlices(List<Slice> s) {
        slices = s;
        total = 0;
        if (s != null) {
            for (Slice x : s) total += x.value;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;

        if (slices == null || slices.isEmpty() || total <= 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFE8EBEF);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, r * 0.55f, paint);
            return;
        }

        rect.set(cx - r, cy - r, cx + r, cy + r);
        paint.setStyle(Paint.Style.FILL);
        float start = -90f;
        for (Slice s : slices) {
            float sweep = s.value / total * 360f;
            paint.setColor(s.color);
            canvas.drawArc(rect, start, sweep, true, paint);
            start += sweep;
        }
        // 中间挖白洞，形成环形
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, r * 0.55f, paint);
    }
}
