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
    private final Vector4f roundScratch = new Vector4f();

    @Override
    public void render(ShapeProperties shape) {
        FontRenderer.flushDeferred();

        // Окно проверяем ДО begin(): ранний выход после begin() оставлял
        // Tessellator в состоянии «идёт построение» — следующий чужой begin()
        // падал IllegalStateException посреди кадра.
        var currentWindow = mc.getWindow();
        if (currentWindow == null) {
            return;
        }

        float scale = (float) currentWindow.getScaleFactor();
        Matrix4f matrix4f = shape.getMatrix().peek().getPositionMatrix();
        Vector3f size = matrix4f.getScale(sizeScratch).mul(scale);
        float width = shape.getWidth() * size.x;
        float height = shape.getHeight() * size.y;
        // Collapsed / mirrored / NaN rects have no pixels to cover.
        if (!Blur.drawable(width, height)) {
            return;
        }

        // Before begin(): a missing program (failed shader load) used to NPE
        // with the Tessellator buffer already started.
        ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);
        if (shader == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float alpha = RenderSystem.getShaderColor()[3];
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, posScratch).mul(scale);
        // Копия, а не мутация getRound(): умножение на scale прямо в properties
        // компаундилось при повторном рендере одного и того же объекта.
        Vector4f round = roundScratch.set(shape.getRound()).mul(size.y);

        // Как в Blur/Arc: softness не домножается на альфу шейдера — при
        // fade-анимациях (alpha < 1) прямоугольник раньше сжимался.
        float density = size.y * Blur.REFERENCE_DENSITY;
        float softness = Blur.softnessPx(shape.getSoftness(), density);
        float thickness = Blur.strokePx(shape.getThickness(), density);
        // Without the margin the geometry cut the antialiased fringe off, so
        // edges jumped by whole pixels while a rect moved at sub-pixel speed.
        float padX = (softness + Blur.EDGE_PAD_PX) / size.x;
        float padY = (softness + Blur.EDGE_PAD_PX) / size.y;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawEngine.quad(matrix4f, buffer, shape.getX() - padX, shape.getY() - padY, shape.getWidth() + padX * 2, shape.getHeight() + padY * 2);

        // Framebuffer height, not window height: gl_FragCoord is in framebuffer
        // pixels and the two differ on HiDPI displays.
        Blur.shader(pos, round, softness, thickness, width, height, shader, currentWindow.getFramebufferHeight());
        Blur.ShaderUniform(shape, alpha, shader);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
}
