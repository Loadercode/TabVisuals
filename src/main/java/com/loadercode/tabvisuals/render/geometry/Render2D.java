/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.geometry;

import com.mojang.blaze3d.systems.RenderSystem;
import com.loadercode.tabvisuals.render.item.ItemRender;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL40C;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.color.ColorAssist;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class Render2D implements QuickImports {
    private final List<Quad> QUAD = new ArrayList<>();

    public void onRender(DrawContext context) {
        FontRenderer.flushDeferred();
        MatrixStack matrix = context.getMatrices();
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();
        if (!QUAD.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            QUAD.forEach(quad -> drawEngine.quad(matrix4f, buffer,quad.x,quad.y,quad.width,quad.height,quad.color));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.disableBlend();
            QUAD.clear();
        }
    }

    public void defaultDrawStack(DrawContext context, ItemStack stack, float x, float y, boolean rect, boolean drawItemInSlot, float scale) {
        FontRenderer.flushDeferred();
        MatrixStack matrix = context.getMatrices();
        if (rect) blur.render(ShapeProperties.create(matrix, x, y, 16 * scale + 2, 16 * scale + 2).round(2).color(ColorAssist.HALF_BLACK).build());
        matrix.push();
        matrix.translate(x + 1, y + 1, 0);
        matrix.scale(scale, scale, 1);
        context.drawItem(stack, 0, 0);
        if (drawItemInSlot) context.drawStackOverlay(mc.textRenderer, stack, 0, 0);
        matrix.pop();
    }

    public void drawStack(MatrixStack matrix, ItemStack stack, float x, float y, boolean rect, float scale) {
        FontRenderer.flushDeferred();
        float posX = x + 1;
        float posY = y + 1;
        float padding = 1;

        matrix.push();
        matrix.translate(posX, posY, 0);
        if (rect) blur.render(ShapeProperties.create(matrix, -padding, -padding, 16 * scale + padding * 2, 16 * scale + padding * 2).round(1.5F).color(ColorAssist.HALF_BLACK).build());
        matrix.scale(scale, scale, 1);
        ItemRender.drawItem(matrix, stack, 0, 0, true, true);
        matrix.pop();
    }

    public void drawTexture(DrawContext context, Identifier id,float x,float y, int size) {
        MatrixStack matrix = context.getMatrices();
        if (id != null) {
            matrix.push();
            matrix.translate(x, y, 0);
            matrix.scale(size, size, 1);

            RenderSystem.enableBlend();
            drawTexture(matrix, id, 0, 0, 1, 1, size, size, size, size, size, size, -1);
            RenderSystem.disableBlend();

            matrix.translate(-x, -y, 0);
            matrix.pop();
        }
    }

    public static Color applyOpacity(Color color, float opacity) {
        opacity = Math.min(1, Math.max(0, opacity));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * opacity));
    }

    public static int applyOpacity(int color_int, float opacity) {
        opacity = Math.min(1, Math.max(0, opacity));
        Color color = new Color(color_int);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * opacity)).getRGB();
    }

    public void drawTexture(DrawContext context, Identifier id,float x,float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor) {
        drawTexture(context, id, x, y, size, round, uvSize, regionSize, textureSize, backgroundColor, -1);
    }

    public void drawTexture(DrawContext context, Identifier id,float x,float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor, int color) {
        drawTexture(context, id, x, y, size, round, uvSize, regionSize, textureSize, backgroundColor, color, 0f);
    }

    /**
     * Рисует текстуру с опциональной обрезкой снизу (для обложек Spotify — вотермарка внизу).
     * @param cropBottomRatio доля высоты текстуры, которую обрезать снизу (0 = без обрезки, 0.15 = обрезать 15% снизу)
     */
    public void drawTexture(DrawContext context, Identifier id, float x, float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor, int color, float cropBottomRatio) {
        MatrixStack matrix = context.getMatrices();
        rectangle.render(ShapeProperties.create(matrix,x,y,size,size).round(round).color(backgroundColor).build());

        if (id != null) {
            matrix.push();
            matrix.translate(x, y, 0);
            matrix.scale(size, size, 1);

            RenderSystem.enableBlend();
            if (cropBottomRatio > 0 && cropBottomRatio < 1) {
                RenderSystem.defaultBlendFunc();
                int regionH = (int) (textureSize * (1f - cropBottomRatio));
                drawTexture(matrix, id, 0, 0, 1, 1, 0, textureSize, regionH, 0, 0, textureSize, textureSize, color);
            } else if (uvSize == textureSize && regionSize == textureSize) {
                // Полная текстура (обложка): стандартный альфа-блендинг, UV (0,0)-(1,1)
                RenderSystem.defaultBlendFunc();
                drawTexture(matrix, id, 0, 0, 1, 1, 0, textureSize, textureSize, 0, 0, textureSize, textureSize, color);
            } else {
                // Регион текстуры (голова в TargetHud: 8x8 из 64x64 и т.д.):
                // стандартный альфа-блендинг, как в соседних ветках — иначе
                // источник смешивался по альфе НАЗНАЧЕНИЯ и подложка головы
                // рисовалась без прозрачности.
                RenderSystem.defaultBlendFunc();
                drawTexture(matrix, id, 0, 0, 1, 1, uvSize, uvSize, regionSize, regionSize, textureSize, textureSize, color);
            }
            RenderSystem.disableBlend();

            matrix.translate(-x, -y, 0);
            matrix.pop();
        }
    }

    public void drawSprite(MatrixStack matrix, Sprite sprite, float x, float y, float width, int height) {
        if (width != 0 && height != 0) {
            drawTexturedQuad(matrix, sprite.getAtlasId(), x, x + width, y, y + height, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), -1);
        }
    }

    public void drawSprite(MatrixStack matrix, Sprite sprite, float x, float y, float width, int height, int color) {
        if (width != 0 && height != 0) {
            drawTexturedQuad(matrix, sprite.getAtlasId(), x, x + width, y, y + height, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), color);
        }
    }

    public void drawTexture(MatrixStack matrix, Identifier texture, int x, int y, float width, float height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        drawTexture(matrix, texture, x, x + width, y, y + height, 0, regionWidth, regionHeight, u, v, textureWidth, textureHeight, color);
    }

    public void drawTexture(MatrixStack matrix, Identifier texture, float x1, float x2, float y1, float y2, float z, int regionWidth, int regionHeight, float u, float v, int textureWidth, int textureHeight, int color) {
        drawTexturedQuad(matrix, texture, x1, x2, y1, y2, (u + 0.0F) / (float)textureWidth, (u + (float)regionWidth) / (float)textureWidth, (v + 0.0F) / (float)textureHeight, (v + (float)regionHeight) / (float)textureHeight, color);
    }

    public void drawTexturedQuad(MatrixStack matrix, Identifier texture, float x1, float x2, float y1, float y2, float u1, float u2, float v1, float v2, int color) {
        FontRenderer.flushDeferred();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();
        buffer.vertex(matrix4f, x1, y1, 0).texture(u1, v1).color(color);
        buffer.vertex(matrix4f, x1, y2, 0).texture(u1, v2).color(color);
        buffer.vertex(matrix4f, x2, y2, 0).texture(u2, v2).color(color);
        buffer.vertex(matrix4f, x2, y1, 0).texture(u2, v1).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public void drawCircle(MatrixStack matrix, float x, float y, float radius, int color) {
        FontRenderer.flushDeferred();
        int segments = 16;
        float angleStep = (float) (2 * Math.PI / segments);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segments; i++) {
            float angle1 = i * angleStep;
            float angle2 = (i + 1) * angleStep;
            float x1 = x + radius * (float) Math.cos(angle1);
            float y1 = y + radius * (float) Math.sin(angle1);
            float x2 = x + radius * (float) Math.cos(angle2);
            float y2 = y + radius * (float) Math.sin(angle2);

            buffer.vertex(matrix4f, x, y, 0).color(color);
            buffer.vertex(matrix4f, x1, y1, 0).color(color);
            buffer.vertex(matrix4f, x2, y2, 0).color(color);
            buffer.vertex(matrix4f, x, y, 0).color(color);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }
    /** Central shadow toggle for TabVisuals (was: LoxeDLC Hud feature flag). */
    public static volatile boolean elementShadowEnabled = true;

    public static void renderElementShadow(MatrixStack matrix, float x, float y, float width, float height) {
        if (!elementShadowEnabled) {
            return;
        }

        // Параметры тени (смещение и размытие/интенсивность)
        float shadowOffset = 2.0f;
        int shadowColor = new Color(0, 0, 0, 150).getRGB(); // Полупрозрачный черный

        // Вариант 1: Обычный сглаженный затененный прямоугольник под элементом
        // Используем твой стандартный rectangle/Render2D (аналогично тому, как у тебя в коде)
        // Рисуем с небольшим отступом (blur/glow эффект, если твоя система рендера поддерживает скругления и градиенты)

        // Пример для ShapeProperties (как в твоих Binds/Armor):
        // rectangle.render(ShapeProperties.create(matrix, x - 1, y - 1, width + 2, height + 2)
        //         .round(4) // Скругление под стать элементам худа
        //         .color(shadowColor)
        //         .build());
    }

    public static void endBuilding(BufferBuilder bb) {
        BuiltBuffer builtBuffer = bb.endNullable();
        if (builtBuffer != null)
            BufferRenderer.drawWithGlobalProgram(builtBuffer);
    }

    public void drawQuad(float x, float y, float width, float height, int color) {
        QUAD.add(new Quad(x, y, width, height, ColorAssist.multAlpha(color,RenderSystem.getShaderColor()[3])));
    }

    public record Quad(float x, float y, float width, float height, int color) {}
}
