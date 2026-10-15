/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.renderers.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.loadercode.tabvisuals.render.atlasfont.providers.ResourceProvider;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadColorState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.QuadRadiusState;
import com.loadercode.tabvisuals.render.systemrender.builders.states.SizeState;
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

public record BuiltTexture(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float u, float v,
        float texWidth, float texHeight,
        int textureId
    ) implements IRenderer {

    private static final ShaderProgramKey TEXTURE_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("texture"),
        VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    
    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        float width = this.size.width(), height = this.size.height();
        ShaderProgram shader = RenderSystem.setShader(TEXTURE_SHADER_KEY);
        GlUniform sizeUniform = ShaderUniforms.get(shader, "texture", "Size");
        GlUniform radiusUniform = ShaderUniforms.get(shader, "texture", "Radius");
        GlUniform smoothnessUniform = ShaderUniforms.get(shader, "texture", "Smoothness");
        if (!ShaderUniforms.allPresent(sizeUniform, radiusUniform, smoothnessUniform)) {
            // uniform'ов нет — пропускаем draw-call, GL-состояние ещё не тронуто
            return;
        }
        sizeUniform.set(width, height);
        radiusUniform.set(this.radius.radius1(), this.radius.radius2(),
            this.radius.radius3(), this.radius.radius4());
        smoothnessUniform.set(this.smoothness);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, this.textureId);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, x, y, z).texture(this.u, this.v).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).texture(this.u, this.v + this.texHeight).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).texture(this.u + this.texWidth, this.v + this.texHeight).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).texture(this.u + this.texWidth, this.v).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

}