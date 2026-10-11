/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class HandShaderHelper {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Getter
    private static Shader chromaShader;
    @Getter
    private static Shader solidShader;
    @Getter
    private static Shader balatroShader;
    @Getter
    private static Shader smokeShader;
    @Getter
    private static Shader stripesShader;
    @Getter
    private static Shader glowShader;
    @Getter
    private static Shader glassShader;
    @Getter
    private static Shader snowShader;
    @Getter
    private static Shader auroraShader;
    @Getter
    private static Shader starsShader;
    @Getter
    private static Shader endPortalShader;
    @Getter
    private static Shader blendShader;

    @Getter
    private static SimpleFramebuffer copyFbo;
    @Getter
    private static SimpleFramebuffer shader1Fbo;
    @Getter
    private static SimpleFramebuffer shader2Fbo;

    private static int quadVao = -1;

    @Getter
    private static boolean initialized = false;
    private static boolean initializationAttempted = false;

    public static void initShadersIfNeeded() {
        if (initialized || initializationAttempted) return;

        initializationAttempted = true;
        try {
            chromaShader = new Shader("hand", "chroma");
            solidShader = new Shader("hand", "solid");
            balatroShader = new Shader("hand", "balatro");
            smokeShader = new Shader("hand", "smoke");
            stripesShader = new Shader("hand", "stripes");
            glowShader = new Shader("hand", "glow");
            glassShader = new Shader("hand", "glass");
            snowShader = new Shader("hand", "snow");
            auroraShader = new Shader("hand", "aurora");
            starsShader = new Shader("hand", "stars");
            endPortalShader = new Shader("hand", "endportal");
            blendShader = new Shader("hand", "blend");
            initialized = true;
            System.out.println("[HandShaderHelper] All hand shaders initialized successfully.");
        } catch (Exception e) {
            deleteLoadedShaders();
            System.err.println("[HandShaderHelper] Failed to initialize hand shaders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void deleteLoadedShaders() {
        Shader[] shaders = {chromaShader, solidShader, balatroShader, smokeShader, stripesShader,
                glowShader, glassShader, snowShader, auroraShader, starsShader, endPortalShader, blendShader};
        for (Shader shader : shaders) {
            if (shader != null) shader.delete();
        }
        chromaShader = null;
        solidShader = null;
        balatroShader = null;
        smokeShader = null;
        stripesShader = null;
        glowShader = null;
        glassShader = null;
        snowShader = null;
        auroraShader = null;
        starsShader = null;
        endPortalShader = null;
        blendShader = null;
    }

    public static void checkFramebuffers() {
        if (mc == null || mc.getWindow() == null) return;
        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;
        if (copyFbo == null || copyFbo.textureWidth != width || copyFbo.textureHeight != height) {
            if (copyFbo != null) copyFbo.delete();
            if (shader1Fbo != null) shader1Fbo.delete();
            if (shader2Fbo != null) shader2Fbo.delete();
            copyFbo = new SimpleFramebuffer(width, height, true);
            shader1Fbo = new SimpleFramebuffer(width, height, true);
            shader2Fbo = new SimpleFramebuffer(width, height, true);
            mc.getFramebuffer().beginWrite(true);
        }
    }

    /**
     * Copies the main framebuffer (color + depth) to the copy FBO.
     * Uses Minecraft's built-in copyDepthFrom for reliable depth transfer.
     */
    public static void copyMainFramebuffer() {
        Framebuffer mainFbo = mc.getFramebuffer();
        if (mainFbo == null || copyFbo == null) return;

        // Draw color from main to copy
        copyFbo.beginWrite(true);
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        mainFbo.draw(copyFbo.textureWidth, copyFbo.textureHeight);

        // Copy depth buffer using Minecraft's built-in method
        copyFbo.copyDepthFrom(mainFbo);

        // Restore main framebuffer
        mainFbo.beginWrite(true);
    }

    /**
     * Sets uniforms for the smoke hand shader. Called from HandsShader when rendering smoke.
     */
    public static void setSmokeUniforms(Shader shader, float time, Vector2f resolution, Vector3f smokeColor, float smokeIntensity) {
        shader.setUniform1f("time", time);
        shader.setUniform2f("resolution", resolution);
        shader.setUniform3f("smokeColor", smokeColor);
        shader.setUniform1f("smokeIntensity", smokeIntensity);
    }

    /**
     * Draws a fullscreen quad using raw OpenGL VAO/VBO.
     * This bypasses Minecraft's BufferRenderer which would override our custom shader program.
     */
    public static void drawFullScreenQuad() {
        if (quadVao == -1) {
            initQuadBuffers();
        }

        GL30.glBindVertexArray(quadVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        GL30.glBindVertexArray(0);
    }

    private static void initQuadBuffers() {
        float[] vertices = {
                -1.0f, -1.0f, 0.0f,
                 1.0f, -1.0f, 0.0f,
                 1.0f,  1.0f, 0.0f,
                -1.0f,  1.0f, 0.0f
        };

        quadVao = GL30.glGenVertexArrays();
        int quadVbo = GL30.glGenBuffers();

        GL30.glBindVertexArray(quadVao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, quadVbo);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertices, GL30.GL_STATIC_DRAW);

        // layout(location = 0) in vec3 Position
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);

        GL30.glBindVertexArray(0);
    }

}
