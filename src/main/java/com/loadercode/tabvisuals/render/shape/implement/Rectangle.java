/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.shape.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.Shape;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

public class Rectangle implements Shape, QuickImports {
    private final ShaderProgramKey SHADER_KEY = new ShaderProgramKey(Identifier.of("minecraft", "core/round"), VertexFormats.POSITION, Defines.EMPTY);
    private final Vector3f posScratch = new Vector3f();
    private final Vector3f sizeScratch = new Vector3f();

    @Override
    public void render(ShapeProperties shape) {
        FontRenderer.flushDeferred();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float scale = (float) mc.getWindow().getScaleFactor();
        float alpha = RenderSystem.getShaderColor()[3];

        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, posScratch).mul(scale);
        Vector3f size = matrix4f.getScale(sizeScratch).mul(scale);
        Vector4f round = shape.getRound().mul(size.y);

        float softness = shape.getSoftness() * alpha;
        float thickness = shape.getThickness();
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawEngine.quad(matrix4f, buffer, shape.getX() - softness / 2, shape.getY() - softness / 2, shape.getWidth() + softness, shape.getHeight() + softness);

        ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);
        var currentWindow = mc.getWindow();
        if (currentWindow == null) {
            return;
        }
        Blur.shader(pos, round, softness, thickness, width, height, shader, currentWindow.getHeight());
        Blur.ShaderUniform(shape, alpha, shader);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
}
