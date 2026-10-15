/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.renderers.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.loadercode.tabvisuals.render.atlasfont.providers.ResourceProvider;
import com.loadercode.tabvisuals.render.systemrender.renderers.IRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadColorState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadRadiusState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.SizeState;

public record BuiltBorder(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float thickness,
        float internalSmoothness, float externalSmoothness
    ) implements IRenderer {

    private static final ShaderProgramKey RECTANGLE_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("border"),
        VertexFormats.POSITION_COLOR, Defines.EMPTY);

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        float width = this.size.width(), height = this.size.height();
        ShaderProgram shader = RenderSystem.setShader(RECTANGLE_SHADER_KEY);
        GlUniform sizeUniform = ShaderUniforms.get(shader, "border", "Size");
        GlUniform radiusUniform = ShaderUniforms.get(shader, "border", "Radius");
        GlUniform thicknessUniform = ShaderUniforms.get(shader, "border", "Thickness");
        GlUniform smoothnessUniform = ShaderUniforms.get(shader, "border", "Smoothness");
        if (!ShaderUniforms.allPresent(sizeUniform, radiusUniform, thicknessUniform, smoothnessUniform)) {
            // uniform'ов нет — пропускаем draw-call, GL-состояние ещё не тронуто
            return;
        }
        sizeUniform.set(width, height);
        radiusUniform.set(this.radius.radius1(), this.radius.radius2(),
            this.radius.radius3(), this.radius.radius4());
        thicknessUniform.set(thickness);
        smoothnessUniform.set(this.internalSmoothness, this.externalSmoothness);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

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