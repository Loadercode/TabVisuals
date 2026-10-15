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

    // Иконки насыщения — лежат в assets/tabvisuals/fonts/icons_fud_*.png
    private static final Identifier SATURATION_ICON_1 = Identifier.of("tabvisuals", "fonts/icons_fud_1.png");
    private static final Identifier SATURATION_ICON_2 = Identifier.of("tabvisuals", "fonts/icons_fud_2.png");
    private static final Identifier SATURATION_ICON_3 = Identifier.of("tabvisuals", "fonts/icons_fud_3.png");
    private static final Identifier SATURATION_ICON_4 = Identifier.of("tabvisuals", "fonts/icons_fud_4.png");

    // Чуть меньше ванильных, чтобы не казались слишком большими
    private static final float ICON_SIZE = 9f;
    private static final int FOOD_CELLS = 10;
    private static final float CELL_SPACING = -1f;
    private static final float ENTRANCE_SECONDS = 0.35f;

    /** Плавно заполняемая сытость каждой ячейки, 0..2 */
    private final float[] cellFill = new float[FOOD_CELLS];
    /** Отскок ячейки при появлении, 0..1 */
    private final float[] cellPop = new float[FOOD_CELLS];
    private final float[] lastCellTarget = new float[FOOD_CELLS];
    private float lastTotal = -1f;
    /** Красная вспышка всех иконок при потере сытости, 0..1 */
    private float dropFlash;

    private long animLastNs;
    private long entranceStartNs;
    private float animDt;

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

            // Обновляем базовые координаты драггебла, чтобы хитбокс совпадал с визуалом;
            // округление вместо (int)-усечения — иконки не уезжали на пиксель влево
            setX(Math.round(baseX));
            setY(Math.round(baseY));
            baseX = getX();
            baseY = getY();
        }

        anim_update();
        float enter = entrance_progress();

        // Вспышка при падении сытости (сравниваем общий уровень с прошлым кадром)
        float totalNow = 0f;
        for (int i = 0; i < FOOD_CELLS; i++) {
            totalNow += MathHelper.clamp(saturation - i * 2f, 0f, 2f);
        }
        if (lastTotal >= 0f && totalNow < lastTotal - 0.05f) dropFlash = 1f;
        lastTotal = totalNow;
        dropFlash = approach_smooth(dropFlash, 0f, 5f, animDt);

        Hud hud = Hud.getInstance();
        matrices.push();
        matrices.translate(0f, (1f - enter) * 6f, 0f);

        if (hud != null && hud.shouldDrawHudBlur()) {
            blur.render(ShapeProperties.create(matrices, baseX - 2f, baseY - 2f, totalWidth + 4f, iconSize + 4f)
                    .round(3f)
                    .quality(hud.getBlurQuality())
                    .color(ColorAssist.multAlpha(ColorAssist.getBlurColor(), enter))
                    .build());
        }

        int flashColor = ColorAssist.interpolateColor(ColorAssist.WHITE,
                ColorAssist.pack(255, 90, 90, 255), dropFlash);

        // Рисуем ячейки слева направо
        for (int i = 0; i < FOOD_CELLS; i++) {
            // Сколько "сытости" приходится на ячейку (по 2 единицы на ячейку)
            float saturationForCell = MathHelper.clamp(saturation - i * 2f, 0f, 2f);

            // Плавная заливка: иконка переключается по анимированному значению
            cellFill[i] = approach_smooth(cellFill[i], saturationForCell, 14f, animDt);
            if (saturationForCell > 0f && lastCellTarget[i] <= 0f) cellPop[i] = 1f;
            lastCellTarget[i] = saturationForCell;
            cellPop[i] = approach_smooth(cellPop[i], 0f, 8f, animDt);

            float fill = cellFill[i];
            Identifier iconTexture;
            if (fill >= 1.98f) {
                iconTexture = SATURATION_ICON_1;
            } else if (fill >= 1.48f) {
                iconTexture = SATURATION_ICON_2;
            } else if (fill >= 0.98f) {
                iconTexture = SATURATION_ICON_3;
            } else if (fill > 0.02f) {
                iconTexture = SATURATION_ICON_4;
            } else {
                continue;
            }

            float cellX = baseX + i * (iconSize + CELL_SPACING);
            // Яркость ячейки растёт вместе с её заливкой
            float cellAlpha = enter * MathHelper.clamp(0.45f + 0.55f * (fill / 2f), 0f, 1f);
            int cellColor = ColorAssist.multAlpha(flashColor, cellAlpha);

            // Отскок: слегка вырастает из центра ячейки
            float pop = cellPop[i];
            float scale = 1f + 0.18f * pop;
            float pivotX = cellX + iconSize / 2f;
            float pivotY = baseY + iconSize;

            matrices.push();
            matrices.translate(pivotX, pivotY, 0f);
            matrices.scale(scale, scale, 1f);
            matrices.translate(-pivotX, -pivotY, 0f);

            try {
                Render2D.drawTexture(
                        matrices,
                        iconTexture,
                        Math.round(cellX),
                        Math.round(baseY),
                        iconSize,
                        iconSize,
                        0,
                        0,
                        (int) iconSize,
                        (int) iconSize,
                        (int) iconSize,
                        (int) iconSize,
                        cellColor
                );
            } catch (Exception ignored) {
            }

            matrices.pop();
        }

        matrices.pop();

        setWidth((int) totalWidth);
        setHeight((int) ICON_SIZE);
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
