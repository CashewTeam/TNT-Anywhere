package com.connect_screen.mirror;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class StripedBackgroundDrawable extends Drawable {
    private static final int STRIPE_ALPHA_WHITE = 0x1AFFFFFF;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float stripeWidthPx;
    private final float stripeStepPx;

    public StripedBackgroundDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        stripeWidthPx = 4f * density;
        stripeStepPx = 8f * density;
        backgroundPaint.setColor(ContextCompat.getColor(context, R.color.ui_background));
        stripePaint.setColor(STRIPE_ALPHA_WHITE);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawRect(getBounds(), backgroundPaint);
        int saveCount = canvas.save();
        canvas.clipRect(getBounds());
        for (float x = getBounds().left; x < getBounds().right; x += stripeStepPx) {
            canvas.drawRect(x, getBounds().top, x + stripeWidthPx, getBounds().bottom, stripePaint);
        }
        canvas.restoreToCount(saveCount);
    }

    @Override
    public void setAlpha(int alpha) {
        backgroundPaint.setAlpha(alpha);
        stripePaint.setAlpha(Math.round(alpha * 0.2f));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        backgroundPaint.setColorFilter(colorFilter);
        stripePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
