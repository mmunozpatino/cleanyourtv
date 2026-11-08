package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Frosted glass without a live blur (config.xml frosted_glass, Look). When a wallpaper is decoded, a copy about
 * 1/16 of its size is box-blurred once ({@link #make}). Glass surfaces (the dock band, the top pills) then draw the
 * part of that copy that lies behind them through a {@link BitmapShader}, scaled back up with bilinear filtering,
 * which reads as the wallpaper seen through frosted glass. Drawing it costs one textured round rect; nothing is
 * blurred while moving around, so it suits a 1 GB TV.
 */
final class FrostedGlass {
    private static final int SCALE = 16;
    /** How much of the blur covers the sharp wallpaper (0-255): lower is more transparent glass (Look.glassOpacity). */
    private static int opacity = 150;

    static void setOpacity(int value) {
        opacity = Math.max(0, Math.min(255, value));
        for (WeakReference<View> ref : watchers) {
            View v = ref.get();
            if (v != null) v.invalidate();
        }
    }

    private FrostedGlass() {}

    /** Blurred copies by the wallpaper bitmap they were made from (decode runs ahead of show). */
    private static final Map<Bitmap, Bitmap> pending = Collections.synchronizedMap(new WeakHashMap<>());
    private static Bitmap blurred;
    private static BitmapShader shader;
    /** The wallpaper view: the blurred copy covers exactly its bounds. */
    private static WeakReference<View> wallpaperView = new WeakReference<>(null);
    private static final List<WeakReference<View>> watchers = new ArrayList<>();
    private static final Matrix matrix = new Matrix();
    private static final int[] hostLoc = new int[2];
    private static final int[] wallLoc = new int[2];

    /** Off the UI thread, from the software bitmap before it goes to the GPU: remember a blurred copy for it. */
    static void make(Bitmap source, Bitmap shown) {
        try {
            int w = Math.max(8, source.getWidth() / SCALE), h = Math.max(5, source.getHeight() / SCALE);
            Bitmap small = Bitmap.createScaledBitmap(source, w, h, true);
            Bitmap argb = small.copy(Bitmap.Config.ARGB_8888, true);
            if (small != source && small != argb) small.recycle();
            if (argb == null) return;
            int[] px = new int[w * h];
            argb.getPixels(px, 0, w, 0, 0, w, h);
            for (int pass = 0; pass < 3; pass++) {
                px = boxBlur(px, w, h, 2, true);
                px = boxBlur(px, w, h, 2, false);
            }
            argb.setPixels(px, 0, w, 0, 0, w, h);
            pending.put(shown, argb);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            // No glass for this picture: the surfaces keep their plain tint.
        }
    }

    /** On the UI thread, once {@code shown} is on screen in {@code view}. */
    static void show(Bitmap shown, View view) {
        Bitmap next = shown == null ? null : pending.remove(shown);
        set(next, view);
    }

    /** No wallpaper (or a new Home): the surfaces fall back to their plain tint. */
    static void reset() {
        set(null, null);
        pending.clear();
        // Watchers stay (weak references): the settings panel outlives a Home recreated behind it.
        watchers.removeIf(ref -> ref.get() == null);
    }

    private static void set(Bitmap next, View view) {
        // The old copy is ~14 KB and left to the GC: a view of the Home being replaced may still draw it.
        blurred = next;
        shader = next == null ? null : new BitmapShader(next, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        wallpaperView = new WeakReference<>(view);
        for (WeakReference<View> ref : watchers) {
            View v = ref.get();
            if (v != null) v.invalidate();
        }
    }

    /** A view that draws glass and must redraw when the picture behind it changes. */
    static void watch(View view) {
        watchers.add(new WeakReference<>(view));
    }

    /**
     * Fills {@code rect} (in {@code host}'s coordinates) with the blurred wallpaper behind it. Returns false when
     * there is nothing to draw, so the caller keeps its plain tint.
     */
    static boolean draw(Canvas canvas, View host, RectF rect, float radius, Paint paint) {
        View wall = wallpaperView.get();
        if (shader == null || wall == null || wall.getWidth() == 0 || wall.getHeight() == 0) return false;
        // On screen, not in the window: the settings side panel is its own window over Home.
        host.getLocationOnScreen(hostLoc);
        wall.getLocationOnScreen(wallLoc);
        // Partly see-through: the sharp wallpaper behind still shows under the frost.
        paint.setAlpha(opacity);
        matrix.setScale(wall.getWidth() / (float) blurred.getWidth(), wall.getHeight() / (float) blurred.getHeight());
        matrix.postTranslate(wallLoc[0] - hostLoc[0], wallLoc[1] - hostLoc[1]);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        paint.setFilterBitmap(true);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
        return true;
    }

    /** One horizontal or vertical box-blur pass over packed ARGB pixels, clamped at the edges. */
    private static int[] boxBlur(int[] in, int w, int h, int r, boolean horizontal) {
        int[] out = new int[in.length];
        int span = 2 * r + 1;
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int sr = 0, sg = 0, sb = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int p = horizontal ? in[line * w + j] : in[j * w + line];
                    sr += (p >> 16) & 0xFF;
                    sg += (p >> 8) & 0xFF;
                    sb += p & 0xFF;
                }
                int idx = horizontal ? line * w + i : i * w + line;
                out[idx] = 0xFF000000 | ((sr / span) << 16) | ((sg / span) << 8) | (sb / span);
            }
        }
        return out;
    }
}
