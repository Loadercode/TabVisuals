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
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.shape.Shape;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.color.ColorAssist;

public class Arc implements Shape, QuickImports {
    private final ShaderProgramKey SHADER_KEY = new ShaderProgramKey(Identifier.of("minecraft", "core/arc"), VertexFormats.POSITION, Defines.EMPTY);
    private final Vector3f posScratch = new Vector3f();
    private final Vector3f sizeScratch = new Vector3f();

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
        if (!Blur.drawable(width, height)) {
            return;
        }

        ShaderProgram shader = RenderSystem.setShader(SHADER_KEY);
        if (shader == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float alpha = RenderSystem.getShaderColor()[3];
        Vector3f pos = matrix4f.transformPosition(shape.getX(), shape.getY(), 0, posScratch).mul(scale);
        // The shader compares this radius against a distance normalized to the
        // quad (0..1), so it must not depend on the GUI / matrix scale: it was
        // multiplied by both, i.e. the ring only had its designed size at GUI
        // scale 2 and left the quad at 3 and above. 2 = that reference scale.
        float radius = shape.getRound().x * 2f;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        drawEngine.quad(matrix4f, buffer, shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight());

        shader.getUniformOrDefault("size").set(width, height);
        shader.getUniformOrDefault("location").set(pos.x, currentWindow.getFramebufferHeight() - height - pos.y);
        shader.getUniformOrDefault("radius").set(radius);
        shader.getUniformOrDefault("thickness").set(shape.getThickness());
        shader.getUniformOrDefault("start").set(shape.getStart());
        shader.getUniformOrDefault("end").set(shape.getEnd());
        Blur.shaderUniform(shape, alpha, shader);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }



}
