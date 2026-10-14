/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.util.Network;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.gui.hud.compat.NameProtect;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

import java.awt.*;
import java.util.Objects;

public class PlayerInfo extends AbstractDraggable {
    /** Длительность въезда: альфа + подъём снизу. */
    private static final float ENTRANCE_MS = 380f;
    /** Пауза отрисовки, после которой въезд проигрывается заново (скрытие/показ HUD). */
    private static final float ENTRANCE_GAP_MS = 350f;
    /** Сдвиг въезда по Y — полоса выезжает снизу. */
    private static final float ENTRANCE_SLIDE_Y = 10f;
    /** Экспоненциальное сглаживание позиции при открытии чата, 1/с. */
    private static final float GLIDE_SPEED = 14f;

    private long entranceStartNanos;
    private long lastDrawNanos;
    private long prevNanos;
    /** Сглаженная Y-позиция: чат пододвигает элемент, а не прыгает им. */
    private float glideY = Float.NaN;

    public PlayerInfo() {
        super("Player Info", 0, 0, 60, 0, false);
    }

    @Override
    public void drawDraggable(DrawContext context) {
        long now = System.nanoTime();
        float dt = prevNanos == 0L ? 0f : Math.min(0.1f, (now - prevNanos) / 1_000_000_000f);
        prevNanos = now;

        if (lastDrawNanos == 0L || entranceStartNanos == 0L
                || (now - lastDrawNanos) / 1_000_000f > ENTRANCE_GAP_MS) {
            entranceStartNanos = now;
        }
        lastDrawNanos = now;

        int offset = HudHelper.isChat(mc.currentScreen) ? -28 : -15;
        float targetY = mc.getWindow().getScaledHeight() + offset;
        if (Float.isNaN(glideY)) {
            glideY = targetY;
        } else {
            glideY += (targetY - glideY) * (1f - (float) Math.exp(-GLIDE_SPEED * dt));
        }
        setY(Math.round(glideY));

        // Соседние элементы (Saturation, Armor) игрока проверывают — здесь
        // requireNonNull бросал NPE, которую выше ловил per-draggable catch:
        // элемент мигал и пропадал из кадра, а в лог падал stacktrace.
        if (mc.player == null) return;
        BlockPos blockPos = mc.player.getBlockPos();
        FontRenderer font = Fonts.getSize(14, Fonts.Type.DEFAULT);

        boolean hideCoords = NameProtect.getInstance() != null && NameProtect.getInstance().isHideCoords();

        double bpsValue = mc.player.getVelocity().horizontalLength() * 20.0;
        String bps = "Bps: " + Calculate.round(bpsValue, 0.25F);
        String tps = "Tps: " + Calculate.round(Network.TPS, 0.1F);
        String xyz = hideCoords ? "Xyz: 0, 0, 0" : "Xyz: " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ();
        String text = xyz + " • " + tps + " • " + bps;

        float width = font.getStringWidth(text) + 10;
        setWidth((int) width);
        setHeight(14);

        // Въезд: альфа набирается быстрее, сдвиг догоняет (длинный хвост).
        float enterT = clamp01((now - entranceStartNanos) / 1_000_000f / ENTRANCE_MS);
        float alpha = 1f - (float) Math.pow(1f - enterT, 3);
        float slideY = (float) Math.pow(1f - enterT, 5) * ENTRANCE_SLIDE_Y;

        MatrixStack matrix = context.getMatrices();
        matrix.push();
        matrix.translate(0.0, (double) slideY, 0.0);

        Hud hud = Hud.getInstance();
        if (hud != null && hud.shouldDrawHudBlur()) {
            blur.render(ShapeProperties.create(matrix, getX(), getY() + 1, width + 4, 14)
                    .round(4)
                    .quality(hud.getBlurQuality())
                    .color(ColorAssist.multAlpha(ColorAssist.getBlurColor(), alpha))
                    .build());
        }

        font.drawGradientString(matrix, text, getX() + 3, getY() + 6.5F,
                ColorAssist.multAlpha(ColorAssist.getText(0.9f), alpha),
                ColorAssist.multAlpha(ColorAssist.getText(), alpha));

        matrix.pop();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
