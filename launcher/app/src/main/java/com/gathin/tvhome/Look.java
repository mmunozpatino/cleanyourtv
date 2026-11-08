package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * Two looks picked in Launcher settings: monochrome app icons, each app's logo alone in white on a glass card, the
 * focused one at full white as the accent (config.xml mono_icons); and frosted glass behind the dock and the top pills
 * (config.xml frosted_glass). The choice on the TV wins over config.xml, like the other Launcher settings.
 */
final class Look {
    static final String MONO_ICONS_KEY = "mono_icons";
    static final String FROSTED_GLASS_KEY = "frosted_glass";
    /** The white accent: the focused tile's glow colour while icons are monochrome. */
    static final int MONO_ACCENT = 0xFFEDEFF5;
    /** A resting logo's opacity; the focused one is fully white. */
    static final float REST_ALPHA = 0.62f;

    /** Size the logo is worked out at: about a tile, so the result is sharp without costing much. */
    private static final int W = 320, H = 180;
    /** Colour distance (0-255, max channel) from the background below which a pixel is background... */
    private static final float LO = 34f;
    /** ...and above which it is all logo; in between it is a soft edge. */
    private static final float HI = 96f;
    /** Background samples taken around the edge: a gradient background (Disney+) matches one of them. */
    private static final int SAMPLES = 40;

    private Look() {}

    static boolean monoIcons(Context ctx) {
        return prefs(ctx).getBoolean(MONO_ICONS_KEY, ctx.getResources().getBoolean(R.bool.mono_icons));
    }

    static boolean frostedGlass(Context ctx) {
        return glassLevel(ctx) > 0;
    }

    /** Glass in four steps: 0 off, 1 clear (most see-through), 2 medium, 3 frosted (least see-through). */
    static final String GLASS_LEVEL_KEY = "glass_level";
    /** How much of the blur covers the sharp wallpaper, per glass level (0-255). */
    private static final int[] GLASS_OPACITY = {0, 80, 150, 225};

    static int glassLevel(Context ctx) {
        SharedPreferences p = prefs(ctx);
        int fallback = p.getBoolean(FROSTED_GLASS_KEY, ctx.getResources().getBoolean(R.bool.frosted_glass)) ? 2 : 0;
        return Math.max(0, Math.min(3, p.getInt(GLASS_LEVEL_KEY, fallback)));
    }

    static int glassOpacity(Context ctx) {
        return GLASS_OPACITY[glassLevel(ctx)];
    }

    /** The app box (the dock band) shown or not; unset follows the theme's dock_band. */
    static final String APP_BOX_KEY = "app_box";
    /** The app box's size: 0 compact, 1 normal, 2 roomy (its padding around the tiles). */
    static final String APP_BOX_SIZE_KEY = "app_box_size";
    private static final float[] BOX_PADDING = {0.45f, 1f, 1.45f};

    static int appBoxSize(Context ctx) {
        return Math.max(0, Math.min(2, prefs(ctx).getInt(APP_BOX_SIZE_KEY, 1)));
    }

    static float appBoxPadding(Context ctx) {
        return BOX_PADDING[appBoxSize(ctx)];
    }

    /** Varied greetings with greeting_name (config.xml varied_greetings): a different line each hour of the day. */
    static String greeting(Context ctx, int hour, int dayOfYear, String name) {
        int array = hour < 5 ? R.array.greetings_night : hour < 12 ? R.array.greetings_morning
                : hour < 17 ? R.array.greetings_afternoon : hour < 22 ? R.array.greetings_evening : R.array.greetings_night;
        String[] lines = ctx.getResources().getStringArray(array);
        if (lines.length == 0) return name;
        return String.format(lines[Math.floorMod(dayOfYear * 24 + hour, lines.length)], name);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    /** The card behind a white logo: a light glass fill, so the wallpaper and the dock band show through. */
    static Drawable glassCard() {
        GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x26FFFFFF, 0x0DFFFFFF});
        card.setStroke(1, 0x33FFFFFF);
        return card;
    }

    /** Resting logos are dimmer, the focused one full white: the row's accent. */
    static void focus(boolean focused, View... views) {
        for (View v : views) v.setAlpha(focused ? 1f : REST_ALPHA);
    }

    /**
     * The logo in {@code art} alone, in white on transparent. The background is whatever the banner's edge shows (a
     * flat colour, a gradient, or transparency for an icon); every pixel far enough from all of the edge's colours is
     * logo. So Netflix's red letters on white become white letters, HBO Max's white letters on black stay white, and
     * YouTube's play button keeps its triangle cut out.
     */
    static Drawable whiteLogo(Resources res, Drawable art) {
        if (art == null) return null;
        try {
            Bitmap src = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Drawable copy = art.getConstantState() != null ? art.getConstantState().newDrawable(res).mutate() : art;
            fit(copy, W, H);
            copy.draw(new Canvas(src));
            int[] px = new int[W * H];
            src.getPixels(px, 0, W, 0, 0, W, H);
            int[] bg = edgeSamples(px);
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                float d = 255f;
                for (int s : bg) {
                    d = Math.min(d, distance(p, s));
                    if (d < LO) break;
                }
                float m = d <= LO ? 0f : d >= HI ? 1f : smooth((d - LO) / (HI - LO));
                int a = Math.round(m * 255f);
                px[i] = (a << 24) | 0xFFFFFF;
            }
            src.setPixels(px, 0, W, 0, 0, W, H);
            return new BitmapDrawable(res, src);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Banners fill the box (centre crop, like the tile); square icons sit centred at half the height. */
    private static void fit(Drawable d, int w, int h) {
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, w, h);
            return;
        }
        boolean bannerShaped = iw > ih * 1.3f;
        if (bannerShaped) {
            float scale = Math.max(w / (float) iw, h / (float) ih);
            int dw = Math.round(iw * scale), dh = Math.round(ih * scale);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
        } else {
            int side = h / 2;
            d.setBounds((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2);
        }
    }

    /** Colours around the edge, a pixel in from it (anti-aliased borders and rounded corners are skipped). */
    private static int[] edgeSamples(int[] px) {
        int[] out = new int[SAMPLES];
        int per = SAMPLES / 4;
        for (int k = 0; k < per; k++) {
            int x = 2 + k * (W - 4) / per, y = 2 + k * (H - 4) / per;
            out[k] = px[2 * W + x];                    // top
            out[per + k] = px[(H - 3) * W + x];        // bottom
            out[2 * per + k] = px[y * W + 2];          // left
            out[3 * per + k] = px[y * W + (W - 3)];    // right
        }
        return out;
    }

    /** Largest difference over alpha and the colour channels, colours weighted by how opaque both pixels are. */
    private static float distance(int p, int q) {
        int pa = p >>> 24, qa = q >>> 24;
        float w = Math.min(pa, qa) / 255f;
        float dr = Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) * w;
        float dg = Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF)) * w;
        float db = Math.abs((p & 0xFF) - (q & 0xFF)) * w;
        return Math.max(Math.abs(pa - qa), Math.max(dr, Math.max(dg, db)));
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }
}
