/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.shape.implement;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL13;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.shape.Shape;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;

public class Blur implements Shape, QuickImports {
    private static final int DEFAULT_SAMPLES = 24;
    private static final int MAX_SAMPLES = 64;
    private static final ShaderProgramKey BLUR_KEY = new ShaderProgramKey(Identifier.of("minecraft", "core/blur"), VertexFormats.POSITION, Defines.EMPTY);
    private static final ShaderProgramKey GLASS_KEY = new ShaderProgramKey(Identifier.of("minecraft", "core/hud_glass"), VertexFormats.POSITION, Defines.EMPTY);
    public Framebuffer input;
    public Vector2f resolution = new Vector2f();
    private final Vector3f posScratch = new Vector3f();
    private final Vector3f sizeScratch = new Vector3f();
    private final Vector4f roundScratch = new Vector4f();
    /** true когда рисуем HUD (InGameHudMixin), false для ClickGUI и др. */
    public static boolean hudContext = false;
    /** TabVisuals blur config (was driven by the LoxeDLC HUD feature). */
    public static volatile boolean hudBlurEnabled = false;
    public static volatile boolean glassMode = false;
    public static volatile float glassBlurSize = 96f;
    public static volatile float glassBrightness = 0.1f;
    public static volatile float glassChromatic = 0f;

    @Override
    public void render(ShapeProperties shape) {
        renderInternal(shape, DEFAULT_SAMPLES);
    }

    /** Более плотная выборка для больших полноэкранных blur-областей. */
    public void renderSmooth(ShapeProperties shape, int samples) {
        renderInternal(shape, Math.max(DEFAULT_SAMPLES, Math.min(MAX_SAMPLES, samples)));
    }

    private void renderInternal(ShapeProperties shape, int samples) {
        FontRenderer.flushDeferred();
        boolean useGlass = hudContext && glassMode;
        if (hudContext && !useGlass && !hudBlurEnabled) {
            return;
        }
        // window check BEFORE blend/begin: the old position (after the buffer
        // was begun) leaked blend state and an unfinished Tessellator buffer
        var currentWindow = mc.getWindow();
        if (currentWindow == null) {
            return;
        }

        float quality = shape.getQuality();
        if (quality <= 0) {
            rectangle.render(shape);
            return;
        }
        // setup() не вызывался — сэмплить нечего, шейдер прочитал бы мусор
        // из юнита текстуры (атлас шрифта и т.п.).
        if (input == null) {
            rectangle.render(shape);
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float scale = (float) currentWindow.getScaleFactor();
        float alpha = RenderSystem.getShaderColor()[3];
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, posScratch).mul(scale);
        Vector3f size = matrix4f.getScale(sizeScratch).mul(scale);
        // Копия, а не мутация getRound(): повторный рендер того же properties
        // раньше компаундил радиус умножением на scale.
        Vector4f round = roundScratch.set(shape.getRound()).mul(size.y);
        float softness = shape.getSoftness();
        float thickness = shape.getThickness();
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawEngine.quad(matrix4f, buffer, shape.getX() - softness / 2, shape.getY() - softness / 2, shape.getWidth() + softness, shape.getHeight() + softness);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        if (input != null) RenderSystem.bindTexture(input.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(useGlass ? GLASS_KEY : BLUR_KEY);
        shader(pos, round, softness, thickness, width, height, shader, currentWindow.getHeight());
        shader.getUniformOrDefault("Quality").set(quality);
        shader.getUniformOrDefault("SampleCount").set((float) samples);
        ShaderUniform(shape, alpha, shader);
        shader.getUniformOrDefault("InputResolution").set(resolution.x, resolution.y);
        if (useGlass) {
            shader.getUniformOrDefault("BlurSize").set(glassBlurSize * 1.5f);
            shader.getUniformOrDefault("Brightness").set(glassBrightness);
            shader.getUniformOrDefault("ChromaticStrength").set(glassChromatic);
            shader.getUniformOrDefault("Time").set((float) (System.currentTimeMillis() % 100000) / 1000f);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    static void ShaderUniform(ShapeProperties shape, float alpha, ShaderProgram shader) {
        var colors = shape.getColor();
        setRgbaUniform(shader, "color1", colors.x, alpha);
        setRgbaUniform(shader, "color2", colors.y, alpha);
        setRgbaUniform(shader, "color3", colors.z, alpha);
        setRgbaUniform(shader, "color4", colors.w, alpha);
        setRgbaUniform(shader, "outlineColor", shape.getOutlineColor(), alpha);
    }

    static void shaderUniform(ShapeProperties shape, float alpha, ShaderProgram shader) {
        var colors = shape.getColor();
        setRgbaUniform(shader, "color1", colors.x, alpha);
        setRgbaUniform(shader, "color2", colors.y, alpha);
    }

    /** Unpack ARGB once per corner — avoids repeated ColorAssist.multAlpha/redf chains. */
    private static void setRgbaUniform(ShaderProgram shader, String name, int color, float alphaMul) {
        float r = ((color >> 16) & 255) / 255.0f;
        float g = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        float a = (((color >> 24) & 255) / 255.0f) * alphaMul;
        shader.getUniformOrDefault(name).set(r, g, b, a);
    }

    static void shader(Vector3f pos, Vector4f round, float softness, float thickness, float width, float height, ShaderProgram shader, int height2) {
        assert shader != null;
        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(pos.x, height2 - height - pos.y);
        shader.getUniformOrDefault("radius").set(round);
        shader.getUniformOrDefault("softness").set(softness);
        shader.getUniformOrDefault("thickness").set(thickness);
    }

    public void setup() {
        Framebuffer buffer = mc.getFramebuffer();
        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();
        if (input == null) {
            input = new SimpleFramebuffer(fbW, fbH, false);
        } else if (input.textureWidth != fbW || input.textureHeight != fbH) {
            // Resize before copy — old path copied then resized (wasted a full blit).
            input.resize(fbW, fbH);
        }
        input.beginWrite(false);
        buffer.draw(input.textureWidth, input.textureHeight);
        buffer.beginWrite(false);
        resolution.set((float) buffer.textureWidth, (float) buffer.textureHeight);
    }
}
