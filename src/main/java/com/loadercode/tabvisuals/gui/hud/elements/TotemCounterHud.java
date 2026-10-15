/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import com.mojang.blaze3d.systems.RenderSystem;
import com.loadercode.tabvisuals.gui.hud.compat.TotemCounter;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.compat.Instance;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

public class TotemCounterHud extends AbstractDraggable {

    private static final float ICON_SIZE = 16.0f;
    /** Геометрия панели — числа LoxeDLC: панель на 2 левее/выше, высота 20, round 4. */
    private static final float PANEL_HEIGHT = 20.0f;
    private static final float PANEL_PAD = 2.0f;
    private static final float ENTRANCE_SECONDS = 0.30f;
    private static final float POP_SECONDS = 0.34f;
    private static final float BREAK_FADE_SECONDS = 0.36f;
    private static final float EMPTY_FADE_SECONDS = 0.45f;
    private static final float ROLL_SECONDS = 0.35f;
    private static final long FRAME_GAP_RESTART_NS = 250_000_000L;

    private long last_frame_ns;
    private long entrance_start_ns;
    private long pop_start_ns;        // 0 = поп не играет
    private boolean pop_grew;         // счётчик вырос — старт с увеличенного масштаба
    private long break_start_ns;      // тотем использован — вспышка-фейд
    private long empty_start_ns;      // счётчик упал до 0 — панель гаснет
    private long roll_start_ns;       // прокрутка числа между значениями
    private int last_count = -1;
    private int roll_from;

    public TotemCounterHud() {
        super("Totem Counter", 970, 532, 44, 20, true);
    }

    public static TotemCounterHud getInstance() {
        return Instance.getDraggable(TotemCounterHud.class);
    }

    @Override
    public boolean visible() {
        return TotemCounter.getInstance() != null && TotemCounter.getInstance().isState();
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null) return;

        int totemCount = countTotems();
        long now_ns = System.nanoTime();
        track_animations(totemCount, now_ns);

        float entrance = HudHelper.hud_anim_cubic_out(
                HudHelper.hud_anim_progress(entrance_start_ns, now_ns, ENTRANCE_SECONDS));
        float popScale = pop_scale(now_ns);
        float breakAlpha = break_alpha(now_ns);
        float emptyAlpha = empty_alpha(now_ns);
        float alpha = entrance * breakAlpha * emptyAlpha;
        int shownCount = rolling_count(now_ns, totemCount);

        float x = getX();
        float y = getY();
        String text = String.valueOf(shownCount);
        float backgroundWidth = ICON_SIZE + 4.0f + mc.textRenderer.getWidth(text) + 6.0f;
        setWidth((int) Math.ceil(backgroundWidth));
        setHeight((int) PANEL_HEIGHT);
        // Полностью погашена (последний тотем сломан) — оставляем только бокс для редактора
        if (alpha <= 0.01f) return;

        // Вход: панель въезжает снизу вместе с иконкой и текстом
        float drawY = y + (1f - entrance) * 6.0f;

        Hud hud = Hud.getInstance();
        if (hud != null && hud.shouldDrawHudBlur()) {
            // Панель по getWidth() (int-ceil), чтобы бокс редактора и blur совпадали
            blur.render(ShapeProperties.create(context.getMatrices(), x - PANEL_PAD, drawY - PANEL_PAD, getWidth(), PANEL_HEIGHT)
                    .round(4)
                    .quality(hud.getBlurQuality())
                    .color(ColorAssist.multAlpha(ColorAssist.getBlurColor(), alpha))
                    .build());
        }

        float textX = x + ICON_SIZE + 4.0f;
        float textY = drawY + (ICON_SIZE / 2.0f) - (mc.textRenderer.fontHeight / 2.0f);

        draw_totem_icon(context, x, drawY, (0.88f + 0.12f * entrance) * popScale, alpha);

        // Округление вместо (int)-усечения: дробная половина пикселя не «съедалась»
        context.drawText(mc.textRenderer, text, Math.round(textX), Math.round(textY),
                ColorAssist.multAlpha(0xFFFFFFFF, alpha), true);
    }

    /** Первый кадр/появление — без ложных анимаций; смена счётчика — запуск поп/фейдов. */
    private void track_animations(int totemCount, long now_ns) {
        if (last_frame_ns == 0L || now_ns - last_frame_ns > FRAME_GAP_RESTART_NS) {
            entrance_start_ns = now_ns;
            pop_start_ns = 0L;
            break_start_ns = 0L;
            empty_start_ns = 0L;
            roll_start_ns = 0L;
            last_count = totemCount;
            roll_from = totemCount;
        } else if (last_count >= 0 && totemCount != last_count) {
            // Текущее отображаемое число — источник прокрутки, чтобы скакать не по краю
            roll_from = rolling_count(now_ns, last_count);
            roll_start_ns = now_ns;
            pop_start_ns = now_ns;
            pop_grew = totemCount > last_count;
            if (totemCount < last_count) {
                if (totemCount == 0) {
                    // Последний тотем сломан — панель уходит в 0
                    empty_start_ns = now_ns;
                    break_start_ns = 0L;
                } else {
                    // Тотем использован — короткая вспышка-фейд
                    break_start_ns = now_ns;
                }
            } else if (empty_start_ns != 0L) {
                // Выход из гашения — въезжаем заново
                entrance_start_ns = now_ns;
                empty_start_ns = 0L;
            }
            last_count = totemCount;
        }
        last_frame_ns = now_ns;
    }

    /** Поп при смене счётчика: рост — сжатие с 1.28, падение — пружина с 0.6. */
    private float pop_scale(long now_ns) {
        if (pop_start_ns == 0L) return 1f;
        float t = HudHelper.hud_anim_progress(pop_start_ns, now_ns, POP_SECONDS);
        if (t >= 1f) {
            pop_start_ns = 0L;
            return 1f;
        }
        if (pop_grew) {
            return 1f + 0.28f * (1f - HudHelper.hud_anim_cubic_out(t));
        }
        return 0.6f + 0.4f * HudHelper.hud_anim_back_out(t, 1.7f);
    }

    /** Использование тотема: панель гаснет до 0.45 и плавно возвращается (без рывка). */
    private float break_alpha(long now_ns) {
        if (break_start_ns == 0L) return 1f;
        float t = HudHelper.hud_anim_progress(break_start_ns, now_ns, BREAK_FADE_SECONDS);
        if (t >= 1f) {
            break_start_ns = 0L;
            return 1f;
        }
        float pulse = (float) Math.sin(Math.PI * Math.min(1f, t / 0.6f));
        return 1f - 0.55f * pulse;
    }

    /** Счётчик дошёл до 0 — панель плавно исчезает и остаётся невидимой. */
    private float empty_alpha(long now_ns) {
        if (empty_start_ns == 0L) return 1f;
        return 1f - HudHelper.hud_anim_cubic_out(
                HudHelper.hud_anim_progress(empty_start_ns, now_ns, EMPTY_FADE_SECONDS));
    }

    /** Число прокручивается между старым и новым значением за 0.35 с. */
    private int rolling_count(long now_ns, int totemCount) {
        if (roll_start_ns == 0L) return totemCount;
        float t = HudHelper.hud_anim_progress(roll_start_ns, now_ns, ROLL_SECONDS);
        if (t >= 1f) {
            roll_start_ns = 0L;
            return totemCount;
        }
        float eased = HudHelper.hud_anim_cubic_out(t);
        return Math.round(roll_from + (totemCount - roll_from) * eased);
    }

    /** Иконка: масштаб (вход + поп) через матрицу, альфа — общим tint-ом. */
    private void draw_totem_icon(DrawContext context, float x, float y, float scale, float alpha) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        float centerX = x + ICON_SIZE / 2.0f;
        float centerY = y + ICON_SIZE / 2.0f;
        matrices.translate(centerX, centerY, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-centerX, -centerY, 0.0f);

        try {
            Calculate.setAlpha(alpha, () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                DiffuseLighting.enableGuiDepthLighting();
                context.drawItem(Items.TOTEM_OF_UNDYING.getDefaultStack(), (int) x, (int) y);
                DiffuseLighting.disableGuiDepthLighting();
            });
        } finally {
            matrices.pop();
        }
    }

    private int countTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                count += mc.player.getInventory().getStack(i).getCount();
            }
        }
        return count;
    }
}
