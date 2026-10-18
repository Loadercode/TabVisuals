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

    private LogoManager() {
    }

    /** Logo texture, or null when the asset is not present. */
    public static Identifier texture() {
        ensure();
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
                    boolean[] handedOff = {false};
                    mc.execute(() -> {
                        try {
                            mc.getTextureManager().registerTexture(LOGO_ID, new NativeImageBackedTexture(img));
                            texW = img.getWidth();
                            texH = img.getHeight();
                            tex = LOGO_ID;
                            handedOff[0] = true;
                        } catch (Throwable texErr) {
                            // upload/register failed inside the render task —
                            // the off-heap image would otherwise leak here,
                            // since the outer catch runs on this worker thread
                            // and can no longer see img.
                            img.close();
                        }
                    });
                    // If the client is shutting down the task may never run;
                    // once execute() returns without handing off, nothing else
                    // will close the image, so drop it here.
                    if (!handedOff[0]) {
                        img.close();
                    }
                }
            } catch (Throwable ignored) {
                // no logo asset — screens keep the text mark
            }
        }, "tabvisuals-logo");
        t.setDaemon(true);
        t.start();
    }
}
