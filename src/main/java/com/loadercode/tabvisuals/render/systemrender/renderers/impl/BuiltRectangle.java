/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.renderers.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.loadercode.tabvisuals.render.atlasfont.providers.ResourceProvider;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadColorState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadRadiusState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.SizeState;
import com.loadercode.tabvisuals.render.systemrender.renderers.IRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.Objects;

public record BuiltRectangle(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness
    ) implements IRenderer {

    private static final ShaderProgramKey RECTANGLE_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("rectangle"),
        VertexFormats.POSITION_COLOR, Defines.EMPTY);

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        float width = this.size.width(), height = this.size.height();
        ShaderProgram shader = RenderSystem.setShader(RECTANGLE_SHADER_KEY);
        assert shader != null;
        Objects.requireNonNull(shader.getUniform("Size")).set(width, height);
        Objects.requireNonNull(shader.getUniform("Radius")).set(this.radius.radius1(), this.radius.radius2(),
            this.radius.radius3(), this.radius.radius4());
        Objects.requireNonNull(shader.getUniform("Smoothness")).set(this.smoothness);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, x, y, z).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

}