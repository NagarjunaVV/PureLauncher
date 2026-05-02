package com.purelauncher.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CircularCounterView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    public CircularCounterView(Context context) {
        super(context);
        init();
    }

    public CircularCounterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularCounterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(18f);
        trackPaint.setColor(0x33FFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(18f);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float inset = 20f;
        arcRect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint);
        canvas.drawArc(arcRect, -90f, 252f, false, arcPaint);
    }
}