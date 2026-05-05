package com.purelauncher.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.purelauncher.R;

public class BarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint touchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] samples = new float[]{0.22f, 0.38f, 0.55f, 0.31f, 0.64f, 0.48f, 0.75f};
    private OnBarTouchListener touchListener;
    private int touchedBarIndex = -1;
    private int highlightedBarIndex = -1;

    public interface OnBarTouchListener {
        void onBarTouch(int index, float value);
        void onBarRelease();
    }

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(ContextCompat.getColor(getContext(), R.color.chart_bar));
        touchPaint.setColor(ContextCompat.getColor(getContext(), R.color.chart_bar_active));
    }

    public void setOnBarTouchListener(OnBarTouchListener listener) {
        this.touchListener = listener;
    }

    public void setHighlightedBar(int index) {
        this.highlightedBarIndex = index;
        invalidate();
    }

    public void setSamples(float[] values) {
        if (values == null || values.length == 0) {
            return;
        }
        samples = values.clone();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        int bars = samples.length;
        float gap = width * 0.02f;
        float barWidth = (width - (gap * (bars - 1))) / bars;

        float x = 0f;
        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i];
            float top = height * (1f - sample);
            Paint p = (touchedBarIndex != -1) ? ((i == touchedBarIndex) ? touchPaint : barPaint) : ((i == highlightedBarIndex) ? touchPaint : barPaint);
            canvas.drawRoundRect(x, top, x + barWidth, height, barWidth * 0.35f, barWidth * 0.35f, p);
            x += barWidth + gap;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (samples == null || samples.length == 0) return false;

        float width = getWidth();
        int bars = samples.length;
        float gap = width * 0.02f;
        float barWidth = (width - (gap * (bars - 1))) / bars;

        float touchX = event.getX();
        
        int index = -1;
        float currentX = 0f;
        for (int i = 0; i < bars; i++) {
            if (touchX >= currentX && touchX <= currentX + barWidth + gap / 2f) {
                index = i;
                break;
            }
            currentX += barWidth + gap;
        }
        
        if (index == -1 && touchX > currentX - gap) {
            index = bars - 1;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (index >= 0 && index < bars) {
                    if (touchedBarIndex != index) {
                        touchedBarIndex = index;
                        invalidate();
                        if (touchListener != null) {
                            touchListener.onBarTouch(index, samples[index]);
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchedBarIndex = -1;
                invalidate();
                if (touchListener != null) {
                    touchListener.onBarRelease();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}