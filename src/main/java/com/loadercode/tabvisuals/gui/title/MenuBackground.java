// language: Java, file: MenuBackground.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Full-screen animated backdrop rendered by the core shader
 * (assets/minecraft/shaders/core/menubg): a per-pixel smooth diagonal gradient
 * with gaussian teal/blue glows that slowly drift, a vignette and a dither so
 * 8-bit output never bands — the "photo-like" background. Falls back to a plain
 * 4-corner gradient when the shader cannot load.
 */
public final class MenuBackground {

    private static final ShaderProgramKey KEY =
            new ShaderProgramKey(Identifier.of("minecraft", "core/menubg"), VertexFormats.POSITION, Defines.EMPTY);

    private static volatile boolean failed;

    private MenuBackground() {
    }

    /**
     * @param alpha global fade (entry transitions), 0..1
     * @param now   wall-clock milliseconds — drives the glow drift
     */
    public static void render(MatrixStack m, float w, float h, float alpha, long now) {
        if (!failed) {
            try {
                FontRenderer.flushDeferred();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                ShaderProgram shader = RenderSystem.setShader(KEY);
                shader.getUniformOrDefault("ScreenSize").set(w, h);
                shader.getUniformOrDefault("Time").set((now % 1_000_000L) / 1000f);
                shader.getUniformOrDefault("Alpha").set(alpha);

                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
                Matrix4f mat = m.peek().getPositionMatrix();
                buffer.vertex(mat, 0f, 0f, 0f);
                buffer.vertex(mat, 0f, h, 0f);
                buffer.vertex(mat, w, h, 0f);
                buffer.vertex(mat, w, 0f, 0f);
                BufferRenderer.drawWithGlobalProgram(buffer.end());
                RenderSystem.disableBlend();
                return;
            } catch (Throwable t) {
                failed = true;
                TabVisualsClient.LOGGER.warn("menu background shader failed, using plain gradient", t);
            }
        }
        UiRender.gradient(m, 0, 0, w, h, 0xFF0C3242, 0xFF071E2C, 0xFF04101A, 0xFF061420);
        if (alpha < 1f) {
            UiRender.rect(m, 0, 0, w, h, 0, UiRender.mulAlpha(0xFF07101A, 1f - alpha));
        }
    }
}
