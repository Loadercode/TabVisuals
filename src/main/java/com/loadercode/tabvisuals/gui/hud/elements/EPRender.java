/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;

public class EPRender extends AbstractDraggable {
    private static boolean renderingPlayerPreview;

    public EPRender() {
        super("EPRender", 20, 200, 96, 144, true);
    }

    public static boolean isRenderingPlayerPreview() {
        return renderingPlayerPreview;
    }

    @Override
    public boolean visible() {
        return mc.player != null && mc.world != null;
    }

    @Override
    public void drawDraggable(DrawContext context) {
        Hud hud = Hud.getInstance();
        if (hud == null || mc.player == null || mc.world == null) return;

        int size = Math.max(16, Math.round(hud.epRenderSize.getValue()));
        int width = Math.max(48, size * 2);
        int height = Math.max(72, size * 3);
        setWidth(width);
        setHeight(height);

        int x1 = getX();
        int y1 = getY();
        int x2 = getX() + width;
        int y2 = getY() + height;
        int centerX = x1 + width / 2;
        int baseY = y2 - 4;

        if (hud.epRenderShadow.isValue()) {
            int shadowWidth = Math.max(18, size);
            int shadowX1 = centerX - shadowWidth / 2;
            int shadowX2 = centerX + shadowWidth / 2;
            context.fill(shadowX1, baseY - 4, shadowX2, baseY, 0x66000000);
        }

        float mouseX = centerX - hud.epRenderYaw.getValue();
        float mouseY = baseY - size - hud.epRenderPitch.getValue();
        renderPlayer(context, centerX, baseY, size, mouseX - centerX, mouseY - (baseY - size));
    }

    private void renderPlayer(DrawContext context, int centerX, int baseY, int size, float yawOffset, float pitchOffset) {
        EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
        if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) return;

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

        float tickDelta = tickCounter.getTickDelta(false);
        LivingEntityRenderState state = renderer.getAndUpdateRenderState(mc.player, tickDelta);
        MatrixStack matrix = context.getMatrices();
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        matrix.push();
        renderingPlayerPreview = true;
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            matrix.translate(centerX, baseY, 250f);
            matrix.scale(size, size, -size);
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f + yawOffset));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchOffset));
            renderer.render(state, matrix, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            consumers.draw();
        } catch (Exception ignored) {
        } finally {
            // Блендинг гасим в finally: иначе исключение из renderer.render/consumers.draw
            // оставляет blend включённым на весь остаток кадра.
            RenderSystem.disableBlend();
            renderingPlayerPreview = false;
            matrix.pop();
        }
    }
}
