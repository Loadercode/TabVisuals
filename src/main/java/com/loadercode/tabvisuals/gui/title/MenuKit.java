// language: Java, file: MenuKit.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.systemrender.builders.Builder;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadColorState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadRadiusState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.SizeState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Shared toolkit of the main menu: the resolution-independent canvas, the
 * palette, frame-rate independent easing and the few draw primitives the
 * menu needs beyond {@link UiRender} (one-quad soft glow, rounded textures,
 * crisp icons).
 *
 * <p>The canvas: the menu is laid out on a virtual surface ~540 units tall,
 * so it looks identical at every window size and GUI scale. One unit is
 * {@link #unitPx()} physical pixels.
 */
public final class MenuKit {

    // ------------------------------------------------------------ palette
    public static final int ACCENT = 0xFFA78BFA;
    public static final int ACCENT_DEEP = 0xFF8B5CF6;
    public static final int ACCENT_SOFT = 0xFFC4B5FD;
    public static final int TEXT = 0xFFF4F2FB;
    public static final int TEXT_2 = 0xFFB9B6CC;
    public static final int TEXT_3 = 0xFF8A879F;
    public static final int TEXT_4 = 0xFF5F5C74;
    public static final int CARD = 0x0EFFFFFF;
    public static final int CARD_HOVER = 0x1AFFFFFF;
    public static final int LINE = 0x14FFFFFF;
    public static final int LINE_HOVER = 0x2CFFFFFF;
    public static final int DANGER = 0xFFFF6B81;
    public static final int GOLD = 0xFFF5C451;
    public static final int OK = 0xFF4ADE80;

    /** Canvas height in units — every layout is designed against it. */
    public static final float DESIGN_H = 540f;
    private static final float DESIGN_MIN_W = 880f;

    private MenuKit() {
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    // ------------------------------------------------------------- canvas

    /** Physical pixels per canvas unit. */
    public static float unitPx() {
        Window w = mc().getWindow();
        if (w == null) {
            return 2f;
        }
        float px = Math.min(w.getFramebufferHeight() / DESIGN_H, w.getFramebufferWidth() / DESIGN_MIN_W);
        return Math.max(0.5f, px);
    }

    /** GUI units per canvas unit — the matrix scale applied before drawing on the canvas. */
    public static float guiPerUnit() {
        Window w = mc().getWindow();
        double gs = w == null ? 2.0 : w.getScaleFactor();
        return (float) (unitPx() / (gs > 0 ? gs : 1.0));
    }

    public static float canvasW() {
        Window w = mc().getWindow();
        return w == null ? 960f : w.getFramebufferWidth() / unitPx();
    }

    public static float canvasH() {
        Window w = mc().getWindow();
        return w == null ? DESIGN_H : w.getFramebufferHeight() / unitPx();
    }

    /** Snap a canvas coordinate to the physical pixel grid (crisp hairlines). */
    public static float snap(float v) {
        float px = unitPx();
        return Math.round(v * px) / px;
    }

    // ------------------------------------------------------------ easing

    /** Frame-rate independent exponential approach; settles exactly on the target. */
    public static float approach(float cur, float target, float speed, float dt) {
        float next = cur + (target - cur) * (1f - (float) Math.exp(-speed * dt));
        return Math.abs(target - next) < 0.0005f ? target : next;
    }

    public static float clamp01(float v) {
        return v != v ? 0f : v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static float easeOutQuint(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u * u * u;
    }

    public static float easeInOutCubic(float t) {
        float x = clamp01(t);
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    /** Staggered entry factor: 0 until {@code delay}, then quint-out over {@code dur} ms. */
    public static float enter(long now, long start, long delay, long dur) {
        return easeOutQuint((now - start - delay) / (float) dur);
    }

    /**
     * Critically damped spring — used where a value must glide AND carry
     * momentum (the nav indicator, the lyric scroller). Stable for any dt.
     */
    public static final class Spring {
        public float value, velocity;
        private final float omega;
        private boolean init;

        public Spring(float omega) {
            this.omega = omega;
        }

        public float update(float target, float dt) {
            if (!init) {
                value = target;
                init = true;
                return value;
            }
            float x = value - target;
            float exp = (float) Math.exp(-omega * dt);
            float tmp = (velocity + omega * x) * dt;
            velocity = (velocity - omega * tmp) * exp;
            value = target + (x + tmp) * exp;
            if (Math.abs(value - target) < 0.01f && Math.abs(velocity) < 0.01f) {
                value = target;
                velocity = 0f;
            }
            return value;
        }

        public void reset() {
            init = false;
            velocity = 0f;
        }
    }

    /** Hover / press / appear state of one interactive element. */
    public static final class Anim {
        public float hover, press;

        /** Updates from the drawn rect; returns whether the mouse is over it. */
        public boolean update(MenuFrame f, float x, float y, float w, float h) {
            return update(f, x, y, w, h, false);
        }

        /** {@code force}: keyboard focus — lit as if hovered. */
        public boolean update(MenuFrame f, float x, float y, float w, float h, boolean force) {
            boolean over = force || f.hover(x, y, w, h);
            hover = approach(hover, over ? 1f : 0f, 16f, f.dt);
            press = approach(press, over && pressed == this ? 1f : 0f, 26f, f.dt);
            return over;
        }

        /** Uniform press sink around the rect centre. */
        public float scale() {
            return 1f - 0.035f * press;
        }
    }

    /** The element currently held down by the left mouse button (cleared on release). */
    public static Anim pressed;

    // ----------------------------------------------------------- colour

    public static int alpha(int argb, float k) {
        return UiRender.mulAlpha(argb, k);
    }

    public static int mix(int a, int b, float k) {
        return UiRender.mix(a, b, k);
    }

    // ------------------------------------------------------------- shapes

    public static void rect(MatrixStack m, float x, float y, float w, float h, float r, int argb) {
        if ((argb >>> 24) == 0 || !(w > 0f) || !(h > 0f)) {
            return;
        }
        UiRender.rect(m, x, y, w, h, Math.min(r, Math.min(w, h) / 2f), argb);
    }

    public static void outline(MatrixStack m, float x, float y, float w, float h, float r, int argb) {
        if ((argb >>> 24) == 0 || !(w > 0f) || !(h > 0f)) {
            return;
        }
        UiRender.outline(m, x, y, w, h, Math.min(r, Math.min(w, h) / 2f), argb, 1f);
    }

    /** Card: translucent glass fill + hairline. */
    public static void card(MatrixStack m, float x, float y, float w, float h, float r, int fill, int line) {
        rect(m, x, y, w, h, r, fill);
        outline(m, x, y, w, h, r, line);
    }

    /** Rounded four-corner gradient. */
    public static void gradient(MatrixStack m, float x, float y, float w, float h, float r,
                                int tl, int bl, int br, int tr) {
        if (!(w > 0f) || !(h > 0f)) {
            return;
        }
        com.loadercode.tabvisuals.render.interfaces.QuickImports.rectangle.render(
                com.loadercode.tabvisuals.render.shape.ShapeProperties.create(m, x, y, w, h)
                        .round(Math.min(r, Math.min(w, h) / 2f))
                        .color(bl, tl, br, tr)
                        .build());
    }

    /** Violet artwork placeholder tile (no cover / letter tiles). */
    public static void tile(MatrixStack m, float x, float y, float w, float h, float r, float a) {
        gradient(m, x, y, w, h, r, alpha(0xFF6D4AE0, a), alpha(0xFF3B2A8C, a), alpha(0xFF8B6CF6, a),
                alpha(0xFFB9A6FF, a));
        glow(m, x + w * 0.7f, y + h * 0.25f, w * 0.6f, h * 0.6f, alpha(0xFFFFFFFF, 0.18f * a));
    }

    /** Scales the matrix around (cx, cy) — pair with {@code m.pop()}. */
    public static void pushScale(MatrixStack m, float cx, float cy, float s) {
        m.push();
        if (Math.abs(s - 1f) > 0.0005f) {
            m.translate(cx, cy, 0f);
            m.scale(s, s, 1f);
            m.translate(-cx, -cy, 0f);
        }
    }

    // ---------------------------------------------------------- soft glow

    private static final ShaderProgramKey GLOW_KEY = new ShaderProgramKey(
            Identifier.of("minecraft", "core/softglow"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    private static boolean glowFailed;
    private static BufferBuilder batch;
    private static int batchCount;

    /** One-quad radial glow (elliptical). Falls back to a soft rounded rect if the shader is missing. */
    public static void glow(MatrixStack m, float cx, float cy, float rx, float ry, int argb) {
        if ((argb >>> 24) == 0 || !(rx > 0f) || !(ry > 0f)) {
            return;
        }
        if (beginGlow()) {
            addGlow(m, cx, cy, rx, ry, argb);
            endGlow();
        } else {
            float d = Math.min(rx, ry);
            UiRender.rect(m, cx - rx * 0.6f, cy - ry * 0.6f, rx * 1.2f, ry * 1.2f, d * 0.6f,
                    UiRender.mulAlpha(argb, 0.35f));
        }
    }

    /** Opens a batch of glow quads (one draw call for many). Returns false when glows are unavailable. */
    public static boolean beginGlow() {
        if (glowFailed) {
            return false;
        }
        if (batch != null) {
            return true;
        }
        try {
            FontRenderer.flushDeferred();
            ShaderProgram p = RenderSystem.setShader(GLOW_KEY);
            if (p == null) {
                glowFailed = true;
                return false;
            }
        } catch (Throwable t) {
            glowFailed = true;
            TabVisualsClient.LOGGER.warn("softglow shader unavailable", t);
            return false;
        }
        batch = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        batchCount = 0;
        return true;
    }

    public static void addGlow(MatrixStack m, float cx, float cy, float rx, float ry, int argb) {
        if (batch == null || (argb >>> 24) == 0) {
            return;
        }
        Matrix4f mat = m.peek().getPositionMatrix();
        batch.vertex(mat, cx - rx, cy - ry, 0f).texture(-1f, -1f).color(argb);
        batch.vertex(mat, cx - rx, cy + ry, 0f).texture(-1f, 1f).color(argb);
        batch.vertex(mat, cx + rx, cy + ry, 0f).texture(1f, 1f).color(argb);
        batch.vertex(mat, cx + rx, cy - ry, 0f).texture(1f, -1f).color(argb);
        batchCount++;
    }

    public static void endGlow() {
        BufferBuilder b = batch;
        batch = null;
        if (b == null) {
            return;
        }
        BuiltBuffer built = b.endNullable();
        if (built == null || batchCount == 0) {
            if (built != null) {
                built.close();
            }
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.setShader(GLOW_KEY);
            BufferRenderer.drawWithGlobalProgram(built);
        } catch (Throwable t) {
            try {
                built.close();
            } catch (Throwable ignored) {
            }
            glowFailed = true;
            TabVisualsClient.LOGGER.warn("softglow draw failed", t);
        }
        RenderSystem.disableBlend();
    }

    // ----------------------------------------------------------- textures

    /**
     * Rounded, UV-cropped texture quad with a tint/fade colour. Textures
     * registered by the client default to nearest filtering; {@code smooth}
     * switches the bound texture to linear (covers, logos).
     */
    public static void texture(MatrixStack m, Identifier id, float x, float y, float w, float h, float radius,
                               float u0, float v0, float u1, float v1, int argb, boolean smooth) {
        if (id == null || (argb >>> 24) == 0 || !(w > 0f) || !(h > 0f)) {
            return;
        }
        AbstractTexture tex;
        try {
            tex = mc().getTextureManager().getTexture(id);
        } catch (Throwable t) {
            return;
        }
        if (tex == null) {
            return;
        }
        int gl = tex.getGlId();
        if (smooth) {
            tex.setFilter(true, false);   // no-op when already linear
        }
        FontRenderer.flushDeferred();
        Builder.texture()
                .size(new SizeState(w, h))
                .radius(new QuadRadiusState(Math.max(0f, Math.min(radius, Math.min(w, h) / 2f))))
                .color(new QuadColorState(argb))
                .smoothness(0.8f)
                .texture(u0, v0, u1 - u0, v1 - v0, gl)
                .build()
                .render(m.peek().getPositionMatrix(), x, y, 0f);
    }

    /** Square crop-fill of a {@code srcW x srcH} texture into a rounded square. */
    public static void cover(MatrixStack m, Identifier id, int srcW, int srcH, float x, float y, float size,
                             float radius, int argb) {
        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
        if (srcW > 0 && srcH > 0 && srcW != srcH) {
            if (srcW > srcH) {
                float f = (srcW - srcH) / (2f * srcW);
                u0 = f;
                u1 = 1f - f;
            } else {
                float f = (srcH - srcW) / (2f * srcH);
                v0 = f;
                v1 = 1f - f;
            }
        }
        texture(m, id, x, y, size, size, radius, u0, v0, u1, v1, argb, true);
    }

    /** Minecraft skin face (+ hat layer) from a 64x64 skin texture. */
    public static void skinFace(MatrixStack m, Identifier skin, float x, float y, float size, float radius, int argb) {
        if (skin == null) {
            return;
        }
        float t = 1f / 64f;
        texture(m, skin, x, y, size, size, radius, 8 * t, 8 * t, 16 * t, 16 * t, argb, false);
        texture(m, skin, x, y, size, size, radius, 40 * t, 8 * t, 48 * t, 16 * t, argb, false);
    }

    // -------------------------------------------------------------- icons

    /**
     * Icon glyph centred on (cx, cy). {@code size} is a font size like
     * {@link MenuFont}'s (the glyph box is about size/2 canvas units),
     * rasterised at the physical size so it stays sharp (icon fonts go up to 32px).
     */
    public static void icon(MatrixStack m, Icons.Glyph glyph, float cx, float cy, float size, int argb) {
        if (glyph == null || (argb >>> 24) < 3) {
            return;
        }
        // icon glyphs sit small in their em box — draw at twice the nominal size
        float fs = size * 2f;
        int n = Math.max(6, Math.min(32, Math.round(fs * unitPx() / 2f)));
        float k = fs / n;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(k, k, 1f);
        UiRender.iconCentered(m, glyph, 0f, 0f, n, argb);
        m.pop();
    }

    // --------------------------------------------------------------- text

    /** Letter-spaced uppercase label; words separated by a wider gap. */
    public static String spaced(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 2);
        String[] words = s.split(" ");
        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                sb.append("   ");
            }
            for (int i = 0; i < words[w].length(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(words[w].charAt(i));
            }
        }
        return sb.toString();
    }

    /** mm:ss for a millisecond position. */
    public static String time(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long m = s / 60L;
        s %= 60L;
        return m + (s < 10 ? ":0" : ":") + s;
    }

    public static MenuFont font(float size, Fonts.Type type) {
        return new MenuFont(size, type);
    }
}
