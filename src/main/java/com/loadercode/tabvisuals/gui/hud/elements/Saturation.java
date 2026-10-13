/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

/**
 * Отображает шкалу насыщения (сытости) с иконками, как в другом проекте.
 */
public class Saturation extends AbstractDraggable {

    // Иконки насыщения — лежат в assets/loxedlc/fonts/icons_fud_*.png
    private static final Identifier SATURATION_ICON_1 = Identifier.of("tabvisuals", "fonts/icons_fud_1.png");
    private static final Identifier SATURATION_ICON_2 = Identifier.of("tabvisuals", "fonts/icons_fud_2.png");
    private static final Identifier SATURATION_ICON_3 = Identifier.of("tabvisuals", "fonts/icons_fud_3.png");
    private static final Identifier SATURATION_ICON_4 = Identifier.of("tabvisuals", "fonts/icons_fud_4.png");

    // Чуть меньше ванильных, чтобы не казались слишком большими
    private static final float ICON_SIZE = 9f;
    private static final int FOOD_CELLS = 10;
    private static final float CELL_SPACING = -1f;

    public Saturation() {
        // Имя "Saturation" используется в настройках HUD
        super("Saturation", 10, 190, (int) ((ICON_SIZE + CELL_SPACING) * FOOD_CELLS), (int) ICON_SIZE, true);
    }

    @Override
    public boolean visible() {
        // Элемент включается через HUD → Элементы → "Saturation"
        return Hud.getInstance().interfaceSettings.isSelected("Saturation");
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = context.getMatrices();
        float saturation = mc.player.getHungerManager().getSaturationLevel();

        float iconSize = ICON_SIZE;
        float totalWidth = FOOD_CELLS * iconSize + (FOOD_CELLS - 1) * CELL_SPACING;

        // Позиция по умолчанию — привязка к ванильной полосе голода (справа внизу)
        // Если пользователь не перемещал элемент (координаты ещё стартовые), то выравниваем по голоду.
        float baseX = getX();
        float baseY = getY();
        boolean defaultPos = (getX() == 10 && getY() == 190 && !isDragging());
        if (defaultPos) {
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            // Позиция ванильной полосы голода
            float hungerBarRight = sw / 2f + 91f - 1f;
            float hungerBarTop = sh - 39f;

            // Отрисовываем шкалу насыщения над полосой голода
            baseX = hungerBarRight - totalWidth;
            baseY = hungerBarTop - iconSize - 2f;

            // Если есть шкала воздуха (игрок под водой) — поднимаем ещё выше, чтобы не перекрывалась с пузырьками
            if (mc.player.getAir() < mc.player.getMaxAir() || mc.player.isSubmergedInWater() || mc.player.isTouchingWater()) {
                baseY -= iconSize + 2f;
            }

            // Обновляем базовые координаты драггебла, чтобы хитбокс совпадал с визуа
            setX((int) baseX);
            setY((int) baseY);
        }

        Hud hud = Hud.getInstance();
        if (hud != null && hud.shouldDrawHudBlur()) {
            blur.render(ShapeProperties.create(matrices, baseX - 2f, baseY - 2f, totalWidth + 4f, iconSize + 4f)
                    .round(3f)
                    .quality(hud.getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
        }

        matrices.push();

        // Рисуем ячейки слева направо
        for (int i = 0; i < FOOD_CELLS; i++) {
            // Сколько "сытости" приходится на ячейку (по 2 единицы на ячейку)
            float saturationForCell = MathHelper.clamp(saturation - i * 2f, 0f, 2f);

            Identifier iconTexture = null;
            if (saturationForCell >= 2.0f) {
                iconTexture = SATURATION_ICON_1;
            } else if (saturationForCell >= 1.5f) {
                iconTexture = SATURATION_ICON_2;
            } else if (saturationForCell >= 1.0f) {
                iconTexture = SATURATION_ICON_3;
            } else if (saturationForCell > 0.0f) {
                iconTexture = SATURATION_ICON_4;
            } else {
                continue;
            }

            float cellX = baseX + i * (iconSize + CELL_SPACING);

            try {
                Render2D.drawTexture(
                        matrices,
                        iconTexture,
                        (int) cellX,
                        (int) baseY,
                        iconSize,
                        iconSize,
                        0,
                        0,
                        (int) iconSize,
                        (int) iconSize,
                        (int) iconSize,
                        (int) iconSize,
                        ColorAssist.WHITE
                );
            } catch (Exception ignored) {
            }
        }

        matrices.pop();

        setWidth((int) totalWidth);
        setHeight((int) ICON_SIZE);
    }
}

