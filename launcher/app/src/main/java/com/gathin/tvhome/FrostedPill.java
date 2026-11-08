package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * A top-bar pill's background with frosted glass under it (FrostedGlass): the blurred wallpaper behind the pill, then
 * the pill's own drawable (pill_bg: a white tint, a rim, and the solid white of the focused state) on top. Focused,
 * the solid white covers the glass, so the glass is only drawn at rest.
 */
final class FrostedPill extends Drawable {
    private final View host;
    private final Drawable base;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    FrostedPill(View host, Drawable base, float radius) {
        this.host = host;
        this.base = base;
        this.radius = radius;
        base.setCallback(new Callback() {
            @Override public void invalidateDrawable(@NonNull Drawable who) { invalidateSelf(); }
            @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) { scheduleSelf(what, when); }
            @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) { unscheduleSelf(what); }
        });
    }

    /** Wraps {@code view}'s background, if it has one, and redraws it when the wallpaper changes. */
    static void apply(View view, float density) {
        Drawable bg = view.getBackground();
        if (bg == null || bg instanceof FrostedPill) return;
        view.setBackground(new FrostedPill(view, bg, 21 * density));
        FrostedGlass.watch(view);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!host.isFocused() && !host.isActivated()) {
            rect.set(getBounds());
            float r = Math.min(radius, rect.height() / 2f);
            FrostedGlass.draw(canvas, host, rect, r, paint);
        }
        base.draw(canvas);
    }

    @Override protected void onBoundsChange(Rect bounds) { base.setBounds(bounds); }
    @Override public boolean isStateful() { return base.isStateful(); }
    @Override protected boolean onStateChange(int[] state) { boolean changed = base.setState(state); invalidateSelf(); return changed; }
    @Override public boolean getPadding(@NonNull Rect padding) { return base.getPadding(padding); }
    @Override public void setAlpha(int alpha) { base.setAlpha(alpha); paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { base.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void jumpToCurrentState() { base.jumpToCurrentState(); }
}
