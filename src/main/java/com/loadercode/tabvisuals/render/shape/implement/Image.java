/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.shape.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.Shape;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

@Setter
@Accessors(chain = true)
public class Image implements Shape, QuickImports {
    private String texture;

    @Override
    public void render(ShapeProperties shape) {
        if (texture == null || texture.isEmpty()) {
            return;
        }

        FontRenderer.flushDeferred();

        MatrixStack matrix = shape.getMatrix();
        Identifier textureId = Identifier.of(texture);

        try {
            mc.getResourceManager().getResource(textureId).orElseThrow();
        } catch (Exception e) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, textureId);

        float width = shape.getWidth();
        float x = shape.getX() + width;
        float y = shape.getY();

        matrix.push();
        try {
            matrix.translate(x, y, 0.0F);
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
            matrix.translate(-x, -y, 0.0F);

            drawEngine.quad(matrix.peek().getPositionMatrix(), x, y, shape.getHeight(), width, shape.getColor().x);
        } catch (Exception ignored) {
        } finally {
            // pop/blend в finally: исключение из quad оставляло matrix-стек
            // и blend включёнными на весь остаток кадра (рецепт EPRender).
            matrix.pop();
            RenderSystem.disableBlend();
        }
    }
}
