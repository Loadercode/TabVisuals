/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.renderers.impl;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.loadercode.tabvisuals.render.atlasfont.providers.ResourceProvider;
import com.loadercode.tabvisuals.render.systemrender.renderers.IRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadColorState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadRadiusState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.SizeState;

import java.util.Objects;

public record BuiltBlur(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float blurRadius
) implements IRenderer {

    private static final ShaderProgramKey BLUR_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("blur"),
            VertexFormats.POSITION_COLOR, Defines.EMPTY);

    private static final Supplier<SimpleFramebuffer> TEMP_FBO_SUPPLIER = Suppliers
            .memoize(() -> new SimpleFramebuffer(1, 1, true));

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        Framebuffer mainFbo = MinecraftClient.getInstance().getFramebuffer();

        if (mainFbo == null || mainFbo.textureWidth <= 0 || mainFbo.textureHeight <= 0) {
            return;
        }

        SimpleFramebuffer tempFbo = TEMP_FBO_SUPPLIER.get();

        if (tempFbo.textureWidth != mainFbo.textureWidth || tempFbo.textureHeight != mainFbo.textureHeight) {
            tempFbo.resize(mainFbo.textureWidth, mainFbo.textureHeight);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        tempFbo.beginWrite(false);
        mainFbo.draw(mainFbo.textureWidth, mainFbo.textureHeight);

        mainFbo.beginWrite(false);

        RenderSystem.setShaderTexture(0, tempFbo.getColorAttachment());

        float width = this.size.width();
        float height = this.size.height();
        ShaderProgram shader = RenderSystem.setShader(BLUR_SHADER_KEY);
        assert shader != null;
        Objects.requireNonNull(shader.getUniform("Size")).set(width, height);
        Objects.requireNonNull(shader.getUniform("Radius")).set(this.radius.radius1(), this.radius.radius2(),
                this.radius.radius3(), this.radius.radius4());
        Objects.requireNonNull(shader.getUniform("Smoothness")).set(this.smoothness);
        Objects.requireNonNull(shader.getUniform("BlurRadius")).set(this.blurRadius);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, x, y, z).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}