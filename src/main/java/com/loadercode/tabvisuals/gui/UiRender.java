// language: Java, file: UiRender.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.gui.hud.HudRenderer;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.shape.implement.Blur;
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

    // ----------------------------------------------------- LoxeDLC panels

    /** Filled rounded panel — the base building block for cards and windows. */
    public static void roundedPanel(MatrixStack m, float x, float y, float w, float h, float radius, int color) {
        rect(m, x, y, w, h, radius, color);
    }

    /**
     * Rounded panel with a subtle 1px lighter hairline — LoxeDLC cards/windows
     * carry a thin outline one shade above the fill (the same recipe as the
     * Theme's outline slot: fill lifted 16% toward white).
     */
    public static void outlinedPanel(MatrixStack m, float x, float y, float w, float h, float radius, int color) {
        outlinedPanel(m, x, y, w, h, radius, color, panelOutline(color));
    }

    /** Same, but the hairline colour is given explicitly (e.g. Theme.get().outline()). */
    public static void outlinedPanel(MatrixStack m, float x, float y, float w, float h, float radius, int color, int borderColor) {
        rect(m, x, y, w, h, radius, color);
        outline(m, x, y, w, h, radius, borderColor, 1f);
    }

    /** Hairline colour derived from a fill: 16% toward white, alpha kept. */
    public static int panelOutline(int fill) {
        return withAlpha(mix(fill, 0xFFFFFFFF, 0.16f), (fill >>> 24) & 0xFF);
    }

    /**
     * Panel with a 1px accent-gradient border (the LoxeDLC window hairline,
     * but painted with the client accent gradient). The gradient quad is drawn
     * first and the interior is then re-filled inset by 1px — the round shader
     * only supports a solid outlineColor, so the ring is cut this way. Call
     * this INSTEAD of {@link #roundedPanel}; an opaque fill hides the gradient
     * under the panel, a translucent one picks up a slight accent tint.
     * Corner order passed to {@link #gradient}: tl, bl, br, tr.
     */
    public static void gradientBorder(MatrixStack m, float x, float y, float w, float h, float radius, int fill) {
        int[] g = Theme.accentGradient();
        gradient(m, x, y, w, h, g[0], g[1], g[2], g[3]);
        punchInterior(m, x, y, w, h, radius, fill);
    }

    /** Same, but with an explicit accent colour instead of the theme's. */
    public static void gradientBorder(MatrixStack m, float x, float y, float w, float h, float radius, int fill, int accent) {
        int a = (accent >>> 24) & 0xFF;
        int lo = withAlpha(mix(accent, 0xFFFFFFFF, 0.18f), a);
        int hi = withAlpha(mix(accent, 0xFFFFFFFF, 0.36f), a);
        gradient(m, x, y, w, h, accent, lo, hi, lo);
        punchInterior(m, x, y, w, h, radius, fill);
    }

    /** Interior of a gradientBorder: same rect inset by 1px, radius shrunk to match. */
    private static void punchInterior(MatrixStack m, float x, float y, float w, float h, float radius, int fill) {
        if (w > 2f && h > 2f) {
            rect(m, x + 1f, y + 1f, w - 2f, h - 2f, Math.max(0f, radius - 1f), fill);
        }
    }

    /**
     * Blurred glass panel (LoxeDLC window): the shared blur pipeline samples
     * the frame, a tint of the panel colour darkens it, then the fill goes on
     * top. Falls back to a semi-transparent rounded fill when the blur pass is
     * off for this frame. Draw before the panel's content.
     */
    public static void blurredPanel(MatrixStack m, float x, float y, float w, float h, float radius, int color) {
        blurredPanel(m, x, y, w, h, radius, color, 0.55f);
    }

    /** @param blurTintAlpha darkening tint baked into the blur layer (the ClickGUI uses 0.55) */
    public static void blurredPanel(MatrixStack m, float x, float y, float w, float h, float radius, int color, float blurTintAlpha) {
        // the HUD pass copies the frame itself once per pass (Blur.hudContext);
        // outside it — the ClickGUI-style per-call copy
        boolean blurred = Blur.hudContext || HudRenderer.blurEnabled();
        if (blurred) {
            if (!Blur.hudContext) {
                blur.setup();
            }
            int tint = mulAlpha(color, blurTintAlpha);
            int samples = HudRenderer.blurSamples();
            blur.renderSmooth(ShapeProperties.create(m, x, y, w, h)
                    .round(radius)
                    .quality(samples)
                    .color(tint, tint, tint, tint)
                    .build(), samples);
            rect(m, x, y, w, h, radius, color);
            return;
        }
        // no blur — keep the panel see-through so the backdrop still shows
        rect(m, x, y, w, h, radius, ((color >>> 24) & 0xFF) > 210 ? withAlpha(color, 210) : color);
    }

    /**
     * Soft drop shadow for a panel: stacked rounded rects offset downward with
     * a quadratic alpha falloff (the same layering trick as {@link #glow}).
     * Draw it BEFORE the panel it belongs to.
     *
     * @param rgb   shadow colour (alpha byte ignored) — 0x000000 for the usual black
     * @param alpha global strength, 0..1
     */
    public static void shadow(MatrixStack m, float x, float y, float w, float h, float radius, float alpha) {
        shadow(m, x, y, w, h, radius, 0x000000, alpha);
    }

    public static void shadow(MatrixStack m, float x, float y, float w, float h, float radius, int rgb, float alpha) {
        int layers = 9;
        float spread = 3.5f;
        for (int i = layers; i >= 0; i--) {
            float t = i / (float) layers;
            float grow = spread * t;
            float a = alpha * (1f - t) * (1f - t) * 0.45f;
            if (a <= 0.003f) {
                continue;
            }
            // grows twice as far down as up — reads as a drop, not a halo
            rect(m, x - grow, y + 1.5f - grow * 0.5f, w + grow * 2f, h + grow * 1.5f, radius + grow,
                    withAlpha(rgb, (int) (a * 255f)));
        }
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

    // ----------------------------------------------------- smooth controls

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Lighten toward white by k (alpha kept). */
    public static int lighten(int argb, float k) {
        return mix(argb, 0xFFFFFFFF, clamp01(k));
    }

    /** Uniform press sink: pressT 0..1 -> scale (1 at rest, 0.96 fully pressed). */
    public static float pressScale(float pressT) {
        return 1f - 0.04f * clamp01(pressT);
    }

    /**
     * Smooth button body + centered label. The caller owns the animation state
     * (hoverT / pressT in 0..1, eased FPS-independently) and the hit test
     * (use {@link #inside}). Pressing sinks the button toward 0.96 scale,
     * hovering lifts the fill a few percent toward white — the same feel
     * everywhere in the client.
     */
    public static void button(MatrixStack m, FontRenderer font, float x, float y, float w, float h,
                              float round, String label, float hoverT, float pressT, int bg, int fg) {
        float s = pressScale(pressT);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);
        rect(m, x, y, w, h, round, lighten(bg, 0.07f * clamp01(hoverT)));
        if (font != null && label != null && !label.isEmpty()) {
            font.drawString(m, label, x + (w - font.getStringWidth(label)) / 2f,
                    y + (h - font.getStringHeight(label)) / 2f + 1f, fg);
        }
        m.pop();
    }

    /** Same button, but the body uses the accent gradient border recipe. */
    public static void accentButton(MatrixStack m, FontRenderer font, float x, float y, float w, float h,
                                    float round, String label, float hoverT, float pressT, int fill, int fg) {
        float s = pressScale(pressT);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);
        gradientBorder(m, x, y, w, h, round, lighten(fill, 0.07f * clamp01(hoverT)));
        if (font != null && label != null && !label.isEmpty()) {
            font.drawString(m, label, x + (w - font.getStringWidth(label)) / 2f,
                    y + (h - font.getStringHeight(label)) / 2f + 1f, fg);
        }
        m.pop();
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
