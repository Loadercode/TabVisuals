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
import com.loadercode.tabvisuals.render.math.calc.Calculate;

public class EPRender extends AbstractDraggable {
    private static boolean renderingPlayerPreview;

    /** Вход появления: slide + fade укладываются в 0.32 с. */
    private static final float ENTRANCE_SECONDS = 0.32f;
    /** Пауза без отрисовки длиннее этого — элемент скрывался, вход заново. */
    private static final long FRAME_GAP_RESTART_NS = 250_000_000L;
    /** Покойная альфа тени (LoxeDLC рисует 0x66000000). */
    private static final int SHADOW_ALPHA = 0x66;

    private long last_frame_ns;
    private long entrance_start_ns;
    /** -1 — размер ещё не сглаживался, ставится первый целевой. */
    private float smooth_size = -1f;

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

        long now_ns = System.nanoTime();
        if (last_frame_ns == 0L || now_ns - last_frame_ns > FRAME_GAP_RESTART_NS) {
            entrance_start_ns = now_ns;
        }
        float dt = last_frame_ns == 0L ? 1f / 60f
                : Math.min((now_ns - last_frame_ns) / 1_000_000_000f, 0.1f);
        last_frame_ns = now_ns;

        float enter = HudHelper.hud_anim_cubic_out(
                HudHelper.hud_anim_progress(entrance_start_ns, now_ns, ENTRANCE_SECONDS));

        // Ползунок size двигает модель плавно, а не рывками по кадрам
        float target_size = Math.max(16f, hud.epRenderSize.getValue());
        smooth_size = smooth_size < 0f
                ? target_size
                : HudHelper.hud_anim_approach(smooth_size, target_size, 16f, dt);
        int size = Math.max(16, Math.round(smooth_size));

        int width = Math.max(48, size * 2);
        int height = Math.max(72, size * 3);
        setWidth(width);
        setHeight(height);

        int centerX = getX() + width / 2;
        int y2 = getY() + height;

        // Вход: модель въезжает снизу и подрастает, тень проявляется вместе с ней
        float slide_px = (1f - enter) * 7f;
        float scale_in = 0.92f + 0.08f * enter;
        float base_y = y2 - 4 + slide_px;
        float render_size = size * scale_in;

        if (hud.epRenderShadow.isValue()) {
            int shadowWidth = Math.max(18, size);
            int shadowX1 = centerX - shadowWidth / 2;
            int shadowX2 = centerX + shadowWidth / 2;
            // Округление вместо (int)-усечения: тень не дёргалась на дробном slide_px
            context.fill(shadowX1, Math.round(base_y - 4), shadowX2, Math.round(base_y),
                    Math.round(SHADOW_ALPHA * enter) << 24);
        }

        float mouseX = centerX - hud.epRenderYaw.getValue();
        float mouseY = base_y - render_size - hud.epRenderPitch.getValue();
        renderPlayer(context, centerX, base_y, render_size, enter,
                mouseX - centerX, mouseY - (base_y - render_size));
    }

    private void renderPlayer(DrawContext context, float centerX, float baseY, float size,
                              float alpha, float yawOffset, float pitchOffset) {
        EntityRenderer<? super LivingEntity, ?> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
        if (!(baseRenderer instanceof LivingEntityRenderer<?, ?, ?>)) return;

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?> renderer =
                (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>) baseRenderer;

        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        LivingEntityRenderState state = renderer.getAndUpdateRenderState(mc.player, tickDelta);
        MatrixStack matrix = context.getMatrices();
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        matrix.push();
        renderingPlayerPreview = true;
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Альфа входа через общий tint: при alpha == 1 вызов пропускается,
            // покойное состояние не отличается от LoxeDLC.
            Calculate.setAlpha(alpha, () -> {
                matrix.translate(centerX, baseY, 250f);
                matrix.scale(size, size, -size);
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f + yawOffset));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchOffset));
                renderer.render(state, matrix, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);
                consumers.draw();
            });
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
