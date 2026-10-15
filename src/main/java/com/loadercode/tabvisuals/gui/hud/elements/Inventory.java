/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.geometry.Render2D;

import java.awt.*;

public class Inventory extends AbstractDraggable {
    private static final int SLOT_COUNT = 27;
    private static final int BODY_OUTLINE = new Color(33, 33, 33, 255).getRGB();
    private static final int BODY_COLOR_1 = new Color(18, 19, 20, 75).getRGB();
    private static final int BODY_COLOR_2 = new Color(0, 2, 5, 75).getRGB();
    private static final float ENTRANCE_SECONDS = 0.35f;

    private final ItemStack[] stacks = new ItemStack[SLOT_COUNT];
    /** Отскок слота при появлении предмета, 0..1 */
    private final float[] slotPop = new float[SLOT_COUNT];
    private final long[] slotSignature = new long[SLOT_COUNT];
    private boolean hasItems;
    private long itemCount;
    /** Плавно догоняющий itemCount счётчик — ощущение "намотки" числа */
    private float shownCount;
    private FontRenderer fontCached;
    private FontRenderer iconCached;
    private FontRenderer itemsCached;

    private long animLastNs;
    private long entranceStartNs;
    private float animDt;

    public Inventory() {
        super("Inventory", 385, 40, 123, 60, true);
    }

    @Override
    public boolean visible() {
        return hasItems || HudHelper.isChat(mc.currentScreen);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            hasItems = false;
            itemCount = 0;
            return;
        }

        hasItems = false;
        itemCount = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i + 9);
            stacks[i] = stack;
            if (stack.isEmpty()) continue;
            hasItems = true;
            itemCount += stack.getCount();
        }
    }

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        if (fontCached == null) {
            fontCached    = Fonts.getSize(14, Fonts.Type.DEFAULT);
            iconCached    = Fonts.getSize(20, Fonts.Type.ICONS);
            itemsCached   = Fonts.getSize(12, Fonts.Type.DEFAULT);
        }

        anim_update();
        float enter = entrance_progress();

        shownCount = approach_smooth(shownCount, itemCount, 10f, animDt);
        String itemCountText = String.valueOf(Math.round(shownCount));

        matrix.push();
        matrix.translate(0f, (1f - enter) * 6f, 0f);

        int[] gradientColors = ColorAssist.getRectangleGradientColors();
        int fadeGradient0 = ColorAssist.multAlpha(gradientColors[0], enter);
        int fadeGradient1 = ColorAssist.multAlpha(gradientColors[1], enter);
        int fadeGradient2 = ColorAssist.multAlpha(gradientColors[2], enter);
        int fadeGradient3 = ColorAssist.multAlpha(gradientColors[3], enter);
        int fadeOutline   = ColorAssist.multAlpha(ColorAssist.getOutlineColor(), enter);
        int fadeBlur      = ColorAssist.multAlpha(ColorAssist.getBlurColor(), enter);

        blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), 15.5F)
                    .round(4, 0, 4, 0).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(fadeBlur)
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), 15.5F)
                    .round(4, 0, 4, 0)
                    .thickness(0.1f)
                    .outlineColor(fadeOutline)
                    .color(fadeGradient0, fadeGradient1, fadeGradient2, fadeGradient3)
                    .build());
            blur.render(ShapeProperties.create(matrix, getX(), getY() + 16.5f, getWidth(), getHeight() - 17)
                    .round(0, 4, 0, 4).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(fadeBlur)
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY() + 16.5f, getWidth(), getHeight() - 17)
                    .round(0, 4, 0, 4)
                    .thickness(0.1f)
                    .outlineColor(fadeOutline)
                    .color(fadeGradient0, fadeGradient1, fadeGradient2, fadeGradient3)
                .build());

        // Фиксированные координаты справа: getStringWidth переоценивает короткие строки
        itemsCached.drawString(matrix, "Items:", getX() + 76, getY() + 7, ColorAssist.getText(enter));
        itemsCached.drawString(matrix, itemCountText, getX() + 105, getY() + 7, ColorAssist.getText(enter));

        // Маркер и линии сетки: целые координаты + толщина 1, без полупиксельных волосяных линий
        rectangle.render(ShapeProperties.create(matrix, getX() + 18, getY() + 5, 1f, 6).color(ColorAssist.getText(0.5F * enter)).round(0F).build());

        iconCached.drawString(matrix, "F", getX() + HudHelper.hud_px(4.5f), getY() + 6, ColorAssist.getText(enter));
        fontCached.drawString(matrix, getName(), getX() + HudHelper.hud_px(22f), getY() + HudHelper.hud_px(6.5f), ColorAssist.getText(enter));

        int offsetY = 20;
        int offsetX = 4;
        int itemsPerRow = 9;
        int itemIndex = 0;

        int separatorColor = ColorAssist.getText(0.1F * enter);
        float gridX = getX() + 15f;
        float gridY = getY() + 21f;
        for (int col = 0; col < 8; col++) {
            Render2D.drawQuad(gridX + col * 13f, gridY, 1f, 35f, separatorColor);
        }
        for (int row = 0; row < 2; row++) {
            Render2D.drawQuad(getX() + 5f, getY() + 31f + row * 13f, 112f, 1f, separatorColor);
        }

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = stacks[i];
            float itemX = getX() + offsetX + 1;
            float itemY = getY() + offsetY + 1f;

            if (stack != null && !stack.isEmpty()) {
                // Отскок при появлении/изменении стака: подрастает из центра ячейки
                long sig = stack.getItem().hashCode() * 31L + stack.getCount();
                if (sig != slotSignature[i]) slotPop[i] = 1f;
                slotSignature[i] = sig;
                slotPop[i] = approach_smooth(slotPop[i], 0f, 9f, animDt);

                float pop = slotPop[i];
                float scale = 0.5F * (0.85f + 0.15f * enter) * (1f + 0.28f * pop);
                float popShift = -1.1f * pop;
                Render2D.defaultDrawStack(context, stack, itemX - 1f + popShift, itemY - 1f + popShift, false, false, scale);
            } else {
                slotSignature[i] = 0L;
                slotPop[i] = 0f;
            }

            offsetX += 13;
            itemIndex++;

            if (itemIndex % itemsPerRow == 0) {
                offsetY += 13;
                offsetX = 4;
            }
        }

        matrix.pop();
    }

    /** FPS-независимый шаг кадра: dt в секундах с защитой от фризов/alt-tab. */
    private void anim_update() {
        long now = System.nanoTime();
        if (animLastNs == 0L) {
            animDt = 1f / 60f;
            entranceStartNs = now;
        } else {
            animDt = Math.min((now - animLastNs) / 1_000_000_000f, 0.1f);
            // Долгая пауза без отрисовки = элемент скрыт — вход заново
            if (now - animLastNs > 250_000_000L) entranceStartNs = now;
        }
        animLastNs = now;
    }

    /** Вход появления: ease-out cubic 0..1 за ENTRANCE_SECONDS. */
    private float entrance_progress() {
        float t = Math.min((System.nanoTime() - entranceStartNs) / 1_000_000_000f / ENTRANCE_SECONDS, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Экспоненциальное сглаживание к цели; speed — множитель затухания в секунду. */
    private static float approach_smooth(float current, float target, float speed, float dt) {
        return target + (current - target) * (float) Math.exp(-speed * dt);
    }
}
