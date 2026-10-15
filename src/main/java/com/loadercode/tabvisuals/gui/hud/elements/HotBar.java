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

    /** Ширина ванильного бара — сам бар центрируется по ней, не по хитбоксу. */
    private static final float BAR_W = 182f;
    /**
     * Запас хитбокса с каждой стороны: оффхенд-слот рисуется сбоку бара
     * (getX()-28 при правой руке, getX()+198 при левой + рамка 2.5px), то есть
     * до ~30.5px левее и ~36.5px правее бара — бокс редактора должен
     * покрывать то, что нарисовано (37 ≥ 36.5, симметрия смещения не даёт
     * «уезжать» при смене основной руки).
     */
    private static final int OFFHAND_GUTTER = 37;

    private static final int SLOT_OUTLINE = 0xFF000000;
    private static final int SLOT_NUMBER = 0xFFFFFFFF;
    private static final int GRID_COLOR = 0x99000000;
    private static final float ENTRANCE_SECONDS = 0.35f;
    private static final String[] SLOT_LABELS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final float[] SLOT_NUM_X = new float[9];
    private static final float[] SLOT_NUM_Y = new float[9];

    private float selectItemX;
    /** Импульс подсветки при смене слота, 0..1 */
    private float selectPulse;
    private int lastSelectedSlot = -1;
    private FontRenderer experienceFont;
    private FontRenderer overlayFont;
    private FontRenderer slotFont;

    private long animLastNs;
    private long entranceStartNs;
    private float animDt;

    public HotBar() {
        super("HotBar", 0, 50, (int) BAR_W + 2 * OFFHAND_GUTTER, 22, false);
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

        anim_update();
        float enter = entrance_progress();

        // Плавное (FPS-независимое) движение выделенного слота + импульс при смене
        if (lastSelectedSlot >= 0 && lastSelectedSlot != inventory.selectedSlot) selectPulse = 1f;
        lastSelectedSlot = inventory.selectedSlot;
        selectItemX = approach_smooth(selectItemX, inventory.selectedSlot * 20, 16f, animDt);
        selectPulse = approach_smooth(selectPulse, 0f, 7f, animDt);

        // Центруем сам бар по низу экрана; хитбокс шире бара на OFFHAND_GUTTER
        // с каждой стороны, чтобы оффхенд-слот (рисуется вне бара) был «схватываем»
        setX((int) ((mc.getWindow().getScaledWidth() - BAR_W) / 2f) - OFFHAND_GUTTER);
        setY(mc.getWindow().getScaledHeight() - 27);
        setWidth((int) BAR_W + 2 * OFFHAND_GUTTER);
        final float barX = getX() + OFFHAND_GUTTER;

        matrix.push();
        matrix.translate(0f, (1f - enter) * 8f, 0f);

        // Фон хотбара
        blur.render(ShapeProperties.create(matrix, barX - 0.5F, getY() - 0.5F, BAR_W + 1, 23F)
                    .quality(Hud.getInstance().getBlurQuality())
                    .round(4f)
                    .softness(1f)
                    .thickness(2f)
                    .outlineColor(ColorAssist.multAlpha(0xFFE0E0E0, enter))
                    .color(ColorAssist.multAlpha(0xE8FFFFFF, enter))
                    .build());

        // Подсветка выбранного слота: растёт и ярчает на мгновение при смене слота
        float pulseGrow = 2.5f * selectPulse;
        int hlAlpha = (int) ((0x50 + 0x70 * selectPulse) * enter);
        int hlOutline = (int) ((0x40 + 0x50 * selectPulse) * enter);
            blur.render(ShapeProperties.create(matrix, barX + selectItemX + 1 - pulseGrow / 2f, getY() + 1 - pulseGrow / 2f, 20 + pulseGrow, 20 + pulseGrow)
                    .quality(Hud.getInstance().getBlurQuality())
                    .round(4.5F)
                    .softness(1f)
                    .thickness(2f)
                    .outlineColor(ColorAssist.pack(0, 0, 0, hlOutline))
                    .color(ColorAssist.pack(0, 0, 0, hlAlpha))
                    .build());

        // Сетка слотов (вертикальные разделители)
        drawHotbarGrid(matrix, enter);

        // Основные 9 слотов хотбара; номера — одним glyph-batch (раньше 9×9 drawString)
        for (int i = 0; i < 9; i++) {
            float slotX = barX + i * 20 + 2;
            float slotY = getY() + 2;
            drawStack(context, inventory.main.get(i), slotX, slotY, false);
            SLOT_NUM_X[i] = slotX + 1f;
            SLOT_NUM_Y[i] = slotY + 2.5f;
        }
        drawSlotNumbers(matrix, enter);

        // Оффхенд — отдельный квадратик слева/справа
        if (!offHand.isEmpty()) {
            int offX = (int) barX + (mc.player.getMainArm().equals(Arm.RIGHT) ? -28 : 198);
            drawStack(context, offHand, offX, getY() + 2, true);
        }

        if (!mc.player.isSpectator() && !mc.player.isCreative()) {
            drawExperienceBar(matrix, enter);
        }

        matrix.pop();

        drawOverlayInfo(matrix);
    }

    private void drawHotbarGrid(MatrixStack matrix, float fade) {
        float x = getX() + OFFHAND_GUTTER;
        float y = getY() + 1f;
        float h = 20f;
        int gridColor = ColorAssist.multAlpha(GRID_COLOR, fade);
        // Вертикальные линии между 9 ячейками: целые координаты + толщина 1
        for (int i = 1; i < 9; i++) {
            Render2D.drawQuad(x + i * 20f, y, 1f, h, gridColor);
        }
        // Горизонтальные линии сверху/снизу внутри бара — лёгкая сетка
        Render2D.drawQuad(x + 1f, y, BAR_W - 2f, 1f, gridColor);
        Render2D.drawQuad(x + 1f, getY() + 20f, BAR_W - 2f, 1f, gridColor);
    }

    private void drawSlotNumbers(MatrixStack matrix, float fade) {
        // 4-side outline + fill for all digits → 1 flush (was 81 separate drawString/drawGlyphs)
        slotFont.drawOutlinedBatch(matrix,
                ColorAssist.multAlpha(SLOT_NUMBER, fade),
                ColorAssist.multAlpha(SLOT_OUTLINE, fade),
                1f, SLOT_LABELS, SLOT_NUM_X, SLOT_NUM_Y, 9);
    }

    private void drawExperienceBar(MatrixStack matrix, float fade) {
        if (experienceFont == null) {
            experienceFont = Fonts.getSize(16);
        }
        // Не assert: он в продакшене выключен. HUD рисуется и в момент, когда
        // игрока ещё нет или он уже выгружен — тогда experienceLevel не из чего взять.
        if (mc.player == null) return;
        String levelText = String.valueOf(mc.player.experienceLevel);
        // drawCenteredString усекал x до int — центр берём через hud_text_center_x
        experienceFont.drawString(matrix, levelText,
                HudHelper.hud_text_center_x(experienceFont, levelText, mc.getWindow().getScaledWidth() / 2F),
                getY() - 9.5F, ColorAssist.multAlpha(ColorAssist.GREEN, fade));
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
            int x = Math.round((float) (scaledWidth - width / 2));

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
            int x = Math.round((float) (scaledWidth - width / 2));

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
