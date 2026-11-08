package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * The Launcher settings side panel's background: frosted glass over Home (FrostedGlass), a dark tint that fades in
 * from the left so the text stays readable, and a thin light rim along its left edge. Without a wallpaper it is the
 * tint alone, over whatever Home shows.
 */
final class FrostedPanel extends Drawable {
    private final View host;
    private final float density;
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint();
    private final Paint rim = new Paint();
    private final RectF rect = new RectF();

    FrostedPanel(View host, float density) {
        this.host = host;
        this.density = density;
        rim.setColor(0x3DFFFFFF);
        FrostedGlass.watch(host);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        tint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0, 0x8C0B0D12, 0xB30B0D12, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        rect.set(b);
        FrostedGlass.draw(canvas, host, rect, 0f, glass);
        canvas.drawRect(b, tint);
        canvas.drawRect(b.left, b.top, b.left + Math.max(1f, density), b.bottom, rim);
    }

    @Override public void setAlpha(int alpha) { tint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { tint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
