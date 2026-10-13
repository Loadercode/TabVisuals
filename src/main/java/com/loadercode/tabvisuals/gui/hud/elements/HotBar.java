/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.math.calc.Calculate;

import java.util.Objects;

public class HotBar extends AbstractDraggable {

    private static final int SLOT_OUTLINE = 0xFF000000;
    private static final int SLOT_NUMBER = 0xFFFFFFFF;
    private static final int GRID_COLOR = 0x99000000;
    private static final String[] SLOT_LABELS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final float[] SLOT_NUM_X = new float[9];
    private static final float[] SLOT_NUM_Y = new float[9];

    private float selectItemX;
    private FontRenderer experienceFont;
    private FontRenderer overlayFont;
    private FontRenderer slotFont;

    public HotBar() {
        super("HotBar", 0, 50, 182, 22, false);
    }

    @Override
    public boolean visible() {
        // Включается через HUD → Элементы → "HotBar"
        return Hud.getInstance().interfaceSettings.isSelected("HotBar");
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null) return;

        MatrixStack matrix = context.getMatrices();
        PlayerInventory inventory = Objects.requireNonNull(mc.player).getInventory();
        ItemStack offHand = mc.player.getOffHandStack();

        if (slotFont == null) {
            slotFont = Fonts.getSize(11, Fonts.Type.BOLD);
        }

        // Плавная анимация выделенного слота
        selectItemX = Calculate.interpolateSmooth(1, selectItemX, inventory.selectedSlot * 20);

        // Центруем хотбар по низу экрана
        setX((mc.getWindow().getScaledWidth() - getWidth()) / 2);
        setY(mc.getWindow().getScaledHeight() - 27);

        // Фон хотбара
        blur.render(ShapeProperties.create(matrix, getX() - 0.5F, getY() - 0.5F, getWidth() + 1, 23F)
                    .quality(Hud.getInstance().getBlurQuality())
                    .round(4f)
                    .softness(1f)
                    .thickness(2f)
                    .outlineColor(0xFFE0E0E0)
                    .color(0xE8FFFFFF)
                    .build());
            blur.render(ShapeProperties.create(matrix, getX() + selectItemX + 1, getY() + 1, 20, 20)
                    .quality(Hud.getInstance().getBlurQuality())
                    .round(4.5F)
                    .softness(1f)
                    .thickness(2f)
                    .outlineColor(0x40000000)
                    .color(0x50000000)
                    .build());

        // Сетка слотов (вертикальные разделители)
        drawHotbarGrid(matrix);

        // Основные 9 слотов хотбара; номера — одним glyph-batch (раньше 9×9 drawString)
        for (int i = 0; i < 9; i++) {
            float slotX = getX() + i * 20 + 2;
            float slotY = getY() + 2;
            drawStack(context, inventory.main.get(i), slotX, slotY, false);
            SLOT_NUM_X[i] = slotX + 1f;
            SLOT_NUM_Y[i] = slotY + 2.5f;
        }
        drawSlotNumbers(matrix);

        // Оффхенд — отдельный квадратик слева/справа
        if (!offHand.isEmpty()) {
            int offX = getX() + (mc.player.getMainArm().equals(Arm.RIGHT) ? -28 : 198);
            drawStack(context, offHand, offX, getY() + 2, true);
        }

        if (!mc.player.isSpectator() && !mc.player.isCreative()) {
            drawExperienceBar(matrix);
        }
        drawOverlayInfo(matrix);
    }

    private void drawHotbarGrid(MatrixStack matrix) {
        float x = getX();
        float y = getY() + 1.5f;
        float h = 20f;
        // Вертикальные линии между 9 ячейками
        for (int i = 1; i < 9; i++) {
            Render2D.drawQuad(x + i * 20f, y, 0.75f, h, GRID_COLOR);
        }
        // Горизонтальные линии сверху/снизу внутри бара — лёгкая сетка
        Render2D.drawQuad(x + 1f, y, getWidth() - 2f, 0.5f, GRID_COLOR);
        Render2D.drawQuad(x + 1f, y + h - 0.5f, getWidth() - 2f, 0.5f, GRID_COLOR);
    }

    private void drawSlotNumbers(MatrixStack matrix) {
        // 4-side outline + fill for all digits → 1 flush (was 81 separate drawString/drawGlyphs)
        slotFont.drawOutlinedBatch(matrix, SLOT_NUMBER, SLOT_OUTLINE, 0.75f, SLOT_LABELS, SLOT_NUM_X, SLOT_NUM_Y, 9);
    }

    private void drawExperienceBar(MatrixStack matrix) {
        if (experienceFont == null) {
            experienceFont = Fonts.getSize(16);
        }
        // Не assert: он в продакшене выключен. HUD рисуется и в момент, когда
        // игрока ещё нет или он уже выгружен — тогда experienceLevel не из чего взять.
        if (mc.player == null) return;
        experienceFont.drawCenteredString(matrix, String.valueOf(mc.player.experienceLevel),
                mc.getWindow().getScaledWidth() / 2F, getY() - 9.5F, ColorAssist.GREEN);
    }

    private void drawOverlayInfo(MatrixStack matrix) {
        float scaledWidth = (float) mc.getWindow().getScaledWidth() / 2;
        float heightStart = mc.getWindow().getScaledHeight() - 75;
        float paddingX = 4;
        float paddingY = 3;
        if (overlayFont == null) {
            overlayFont = Fonts.getSize(14, Fonts.Type.DEFAULT);
        }

        // Подпись текущего предмета в руке (приватные поля InGameHud — через аксессор-миксин)
        com.loadercode.tabvisuals.mixin.InGameHudAccessor hudAcc = (com.loadercode.tabvisuals.mixin.InGameHudAccessor) mc.inGameHud;
        if (hudAcc.getHeldItemTooltipFade() > 0 && hudAcc.getCurrentStack() != null) {
            float alpha = ((float) hudAcc.getHeldItemTooltipFade() * 256.0F / 10.0F) / 255;

            Text text = hudAcc.getCurrentStack().getName();
            float width = overlayFont.getStringWidth(text);
            int x = (int) (scaledWidth - width / 2);

            Calculate.setAlpha(alpha, () -> {
                float tw = width + paddingX * 2;
                float th = overlayFont.getStringHeight(text) / 2.15F + paddingY * 2;
                blur.render(ShapeProperties.create(matrix, x - paddingX, heightStart - paddingY, tw, th)
                        .round(2.5F).quality(Hud.getInstance().getBlurQuality())
                        .color(ColorAssist.getRect(0.7F))
                        .build());
                overlayFont.drawText(matrix, text, x, heightStart + 2.5F);
            });
        }

        // Overlay-сообщение (типа "Вы вошли на сервер" и т.п.)
        if (hudAcc.getOverlayRemaining() > 0 && hudAcc.getOverlayMessage() != null && !hudAcc.getOverlayMessage().getString().isEmpty()) {
            float alpha = ((float) hudAcc.getOverlayRemaining() * 256.0F / 10.0F) / 255;

            Text text = hudAcc.getOverlayMessage();
            float width = overlayFont.getStringWidth(text);
            int x = (int) (scaledWidth - width / 2);

            Calculate.setAlpha(alpha, () -> {
                float tw = width + paddingX * 2;
                float th = overlayFont.getStringHeight(text) / 2.15F + paddingY * 2;
                blur.render(ShapeProperties.create(matrix, x - paddingX, heightStart - paddingY - 17, tw, th)
                            .round(2.5F).quality(Hud.getInstance().getBlurQuality())
                        .color(ColorAssist.getRect(0.7F))
                        .build());
                overlayFont.drawText(matrix, text, x, heightStart - 14.5F);
            });
        }
    }

    private void drawStack(DrawContext context, ItemStack stack, float x, float y, boolean offHand) {
        if (stack.isEmpty()) return;

        if (offHand) {
            blur.render(ShapeProperties.create(context.getMatrices(), x - 2.5F, y - 2.5F, 23, 23)
                        .round(3f).quality(Hud.getInstance().getBlurQuality())
                        .thickness(2f)
                        .softness(1f)
                        .outlineColor(ColorAssist.getOutline())
                        .color(ColorAssist.getRect(0.7F))
                        .build());
        }

        Render2D.defaultDrawStack(context, stack, x, y, false, true, 1f);
    }
}

