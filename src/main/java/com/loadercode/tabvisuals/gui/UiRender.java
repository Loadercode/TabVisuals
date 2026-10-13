// language: Java, file: UiRender.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import net.minecraft.client.util.math.MatrixStack;

/** Thin wrappers over the ported render stack for GUI drawing. All colors are ARGB ints. */
public final class UiRender implements QuickImports {

    private UiRender() {
    }

    public static void rect(MatrixStack m, float x, float y, float w, float h, float round, int argb) {
        rectangle.render(ShapeProperties.create(m, x, y, w, h)
                .round(round)
                .color(argb)
                .build());
    }

    /** Stroke-only rounded rect. */
    public static void outline(MatrixStack m, float x, float y, float w, float h, float round, int argb, float thickness) {
        rectangle.render(ShapeProperties.create(m, x, y, w, h)
                .round(round)
                .thickness(thickness)
                .outlineColor(argb)
                .color(0x00000000)
                .build());
    }

    /**
     * Four-corner gradient quad (round shader mixes per-corner colors).
     * Corner order of the shader: color1 = bottom-left, color2 = top-left,
     * color3 = bottom-right, color4 = top-right.
     */
    public static void gradient(MatrixStack m, float x, float y, float w, float h, int tl, int bl, int br, int tr) {
        rectangle.render(ShapeProperties.create(m, x, y, w, h)
                .color(bl, tl, br, tr)
                .build());
    }

    /**
     * Smooth radial glow: many faint concentric circles with a quadratic
     * alpha falloff and no jitter — stacking is even, so there is no banding
     * or lumpiness (the old jittered version read as "pixelated").
     *
     * @param rgb    glow colour (alpha byte ignored)
     * @param peak   peak alpha at the centre, 0..1
     * @param alpha  global fade multiplier (entry animations)
     */
    public static void glow(MatrixStack m, float cx, float cy, float radius, int rgb, float peak, float alpha) {
        int layers = 44;
        for (int i = layers; i >= 0; i--) {
            float f = i / (float) layers;
            float d = radius * (0.18f + 0.82f * f) * 2f;
            float fall = 1f - f;
            float a = peak * fall * fall * alpha;
            if (a <= 0.002f) {
                continue;
            }
            rect(m, cx - d / 2f, cy - d / 2f, d, d, d / 2f, withAlpha(rgb, (int) (a * 255f)));
        }
    }

    public static void text(FontRenderer font, MatrixStack m, String s, double x, double y, int argb) {
        font.drawString(m, s, x, y, argb);
    }

    public static void textTruncated(FontRenderer font, MatrixStack m, String s, double x, double y, float maxWidth, int argb) {
        font.drawStringWithScroll(m, s, x, y, maxWidth, argb);
    }

    /** Centered text inside a pill; returns the full pill width. */
    public static float pill(MatrixStack m, FontRenderer font, float cx, float y, float h, float pad, String label, int bg, int fg) {
        float tw = font.getStringWidth(label);
        float w = tw + pad * 2;
        float x = cx - w / 2f;
        rect(m, x, y, w, h, h / 2f, bg);
        font.drawString(m, label, x + pad, y + (h - font.getStringHeight(label)) / 2f + 1f, fg);
        return w;
    }

    public static int withAlpha(int argb, int a) {
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int mulAlpha(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    /** Linear ARGB blend of two colors (alpha included). */
    public static int mix(int from, int to, float k) {
        float t = Math.max(0f, Math.min(1f, k));
        int fa = (from >>> 24) & 0xFF, fr = (from >>> 16) & 0xFF, fg = (from >>> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >>> 24) & 0xFF, tr = (to >>> 16) & 0xFF, tg = (to >>> 8) & 0xFF, tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
