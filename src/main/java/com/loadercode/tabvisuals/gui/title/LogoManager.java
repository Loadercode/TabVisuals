// language: Java, file: LogoManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * Loads the client logo asset (assets/tabvisuals/textures/logo.png) once,
 * off-thread, and exposes it as a texture. When the asset is absent the
 * accessors return null / a default aspect — screens then fall back to the
 * text mark, so the file is optional (real degradation, no stub).
 */
public final class LogoManager {

    private static final Identifier LOGO_ID = Identifier.of("tabvisuals", "logo");

    private static volatile boolean tried;
    private static volatile Identifier tex;
    private static volatile int texW;
    private static volatile int texH;
    /** Pre-shrunk copies (linear filtered) — the 1254px source aliases badly when drawn at ~40px. */
    private static final Identifier LOGO_MD = Identifier.of("tabvisuals", "logo_md");
    private static final Identifier LOGO_SM = Identifier.of("tabvisuals", "logo_sm");
    private static volatile Identifier texMd;
    private static volatile Identifier texSm;
    private static volatile boolean failed;

    /** True once the logo asset could not be loaded — screens then draw a text mark. */
    public static boolean failed() {
        return failed;
    }

    private LogoManager() {
    }

    /** Logo texture, or null when the asset is not present. */
    public static Identifier texture() {
        ensure();
        return tex;
    }

    /**
     * Logo texture best suited to be drawn {@code physPx} physical pixels
     * tall (full image height): picks the smallest copy that is still at
     * least that big, falling back to the source. Same UVs as {@link #texture()}.
     */
    public static Identifier textureFor(float physPx) {
        ensure();
        if (physPx <= 150f && texSm != null) {
            return texSm;
        }
        if (physPx <= 480f && texMd != null) {
            return texMd;
        }
        return tex;
    }

    /** Width / height of the logo image (for aspect-correct drawing). */
    public static float aspect() {
        return texW > 0 && texH > 0 ? (float) texW / texH : 1f;
    }

    /** Pixel size of the logo image, 0 before it is loaded. */
    public static int width() {
        return texW;
    }

    public static int height() {
        return texH;
    }

    /** Square-ish downscale (aspect kept) on the worker thread; null when not smaller. */
    private static NativeImage shrink(NativeImage src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        int side = Math.max(w, h);
        if (side <= maxSide) {
            return null;
        }
        int tw = Math.max(1, Math.round(w * maxSide / (float) side));
        int th = Math.max(1, Math.round(h * maxSide / (float) side));
        // resize in premultiplied alpha: transparent pixels keep arbitrary RGB
        // in the source, and a straight-alpha filter bleeds it into the edges
        NativeImage pre = new NativeImage(w, h, false);
        NativeImage out = new NativeImage(tw, th, false);
        try {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = src.getColorArgb(x, y);
                    int a = c >>> 24;
                    int r = ((c >> 16) & 0xFF) * a / 255, g = ((c >> 8) & 0xFF) * a / 255, b = (c & 0xFF) * a / 255;
                    pre.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            pre.resizeSubRectTo(0, 0, w, h, out);
            for (int y = 0; y < th; y++) {
                for (int x = 0; x < tw; x++) {
                    int c = out.getColorArgb(x, y);
                    int a = c >>> 24;
                    if (a == 0) {
                        out.setColorArgb(x, y, 0);
                        continue;
                    }
                    int r = Math.min(255, ((c >> 16) & 0xFF) * 255 / a);
                    int g = Math.min(255, ((c >> 8) & 0xFF) * 255 / a);
                    int b = Math.min(255, (c & 0xFF) * 255 / a);
                    out.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            return out;
        } catch (Throwable t) {
            out.close();
            return null;
        } finally {
            pre.close();
        }
    }

    private static Identifier register(MinecraftClient mc, Identifier id, NativeImage img) {
        if (img == null) {
            return null;
        }
        try {
            NativeImageBackedTexture t = new NativeImageBackedTexture(img);
            t.setFilter(true, false);
            mc.getTextureManager().registerTexture(id, t);
            return id;
        } catch (Throwable e) {
            img.close();
            return null;
        }
    }

    private static void ensure() {
        if (tried) {
            return;
        }
        tried = true;
        Thread t = new Thread(() -> {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                try (InputStream in = mc.getResourceManager()
                        .getResource(Identifier.of("tabvisuals", "textures/logo.png"))
                        .orElseThrow().getInputStream()) {
                    NativeImage img = NativeImage.read(in);
                    NativeImage md = shrink(img, 512);
                    NativeImage sm = shrink(img, 160);
                    // execute() may run the task later; the images are owned by
                    // the task from here and closed there on failure
                    mc.execute(() -> {
                        try {
                            mc.getTextureManager().registerTexture(LOGO_ID, new NativeImageBackedTexture(img));
                            texW = img.getWidth();
                            texH = img.getHeight();
                            tex = LOGO_ID;
                        } catch (Throwable texErr) {
                            img.close();
                        }
                        texMd = register(mc, LOGO_MD, md);
                        texSm = register(mc, LOGO_SM, sm);
                    });
                }
            } catch (Throwable ignored) {
                // no logo asset — screens keep the text mark
                failed = true;
            }
        }, "tabvisuals-logo");
        t.setDaemon(true);
        t.start();
    }
}
