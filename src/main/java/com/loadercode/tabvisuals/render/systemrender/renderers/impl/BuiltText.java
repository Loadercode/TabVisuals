/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.systemrender.renderers.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.loadercode.tabvisuals.render.atlasfont.msdf.MsdfFont;
import com.loadercode.tabvisuals.render.atlasfont.providers.ColorProvider;
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


public record BuiltText(
        MsdfFont font,
        String text,
    	float size,
        float thickness,
        int color,
		float smoothness,
        float spacing,
		int outlineColor,
		float outlineThickness
    ) implements IRenderer {

	private static final ShaderProgramKey MSDF_FONT_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("msdf_font"),
		VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
	
	@Override
    public void render(Matrix4f matrix, float x, float y, float z) {
		boolean outlineEnabled = (this.outlineThickness > 0.0f);
		ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
		GlUniform rangeUniform = ShaderUniforms.get(shader, "msdf_font", "Range");
		GlUniform thicknessUniform = ShaderUniforms.get(shader, "msdf_font", "Thickness");
		GlUniform smoothnessUniform = ShaderUniforms.get(shader, "msdf_font", "Smoothness");
		GlUniform outlineUniform = ShaderUniforms.get(shader, "msdf_font", "Outline");
		GlUniform outlineThicknessUniform = outlineEnabled ? ShaderUniforms.get(shader, "msdf_font", "OutlineThickness") : null;
		GlUniform outlineColorUniform = outlineEnabled ? ShaderUniforms.get(shader, "msdf_font", "OutlineColor") : null;
		if (!ShaderUniforms.allPresent(rangeUniform, thicknessUniform, smoothnessUniform, outlineUniform)
				|| (outlineEnabled && !ShaderUniforms.allPresent(outlineThicknessUniform, outlineColorUniform))) {
			// uniform'ов нет — пропускаем draw-call, GL-состояние ещё не тронуто
			return;
		}
		rangeUniform.set(this.font.getAtlas().range());
		thicknessUniform.set(this.thickness);
		smoothnessUniform.set(this.smoothness);
		outlineUniform.set(outlineEnabled ? 1 : 0);

		if (outlineEnabled) {
			outlineThicknessUniform.set(this.outlineThickness);
			float[] outlineComponents = ColorProvider.normalize(this.outlineColor);
			outlineColorUniform.set(outlineComponents[0], outlineComponents[1],
				outlineComponents[2], outlineComponents[3]);
		}

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();

		RenderSystem.setShaderTexture(0, this.font.getTextureId());
		
		BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		this.font.applyGlyphs(matrix, builder, this.text, this.size,
			(this.thickness + this.outlineThickness * 0.5f) * 0.5f * this.size, this.spacing,
				x, y + this.font.getMetrics().baselineHeight() * this.size, z, this.color);
		
		BufferRenderer.drawWithGlobalProgram(builder.end());

		RenderSystem.setShaderTexture(0, 0);

		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

}