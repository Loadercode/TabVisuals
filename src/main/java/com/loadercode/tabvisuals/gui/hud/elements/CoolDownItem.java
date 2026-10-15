/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import lombok.Getter;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.common.animation.Animation;
import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.common.animation.implement.Decelerate;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.render.math.time.StopWatch;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.gui.hud.Hud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Отдельный перетаскиваемый элемент для одного кулдауна предмета
 */
public class CoolDownItem extends AbstractDraggable {
    @Getter
    private final Item item;
    private final StopWatch time;
    private final Animation anim;
    /** Полный срок кулдауна в мс — база для полосы остатка. */
    private final float total_ms;
    private FontRenderer fontCached;
    private boolean active = true;
    /** Сглаженная полоса остатка (-1 — ещё не инициализирована). */
    private float fill = -1f;
    private long last_frame_ms;
    /** Первый кадр, когда остаток дошёл до нуля (0 — не истекал). */
    private long expire_ms;

    public CoolDownItem(Item item, StopWatch time, float x, float y) {
        super(item.getName().getString() + " CD", (int)x, (int)y, 100, 15, false);
        this.item = item;
        this.time = time;
        this.anim = new Decelerate().setMs(150).setValue(1.0F);
        this.total_ms = Math.max(1f, (float) -time.elapsedTime());
    }

    @Override
    public boolean visible() {
        return active && !anim.isFinished(Direction.BACKWARDS);
    }

    /**
     * BUGFIX: имя элемента — «&lt;Предмет&gt; CD» (например «Ender Pearl CD»),
     * такого значения нет в Hud.interfaceSettings, поэтому стандартный canDraw
     * всегда возвращал false: элемент рисовался лишь ~200 мс (пока идёт
     * Decelerate-анимация возврата) и затем исчезал. Галочку берём у
     * родительского элемента «Cool Downs».
     */
    @Override
    public boolean canDraw(Hud hud, AbstractDraggable draggable) {
        if (hud == null || !hud.isState()) return false;
        if (!hud.interfaceSettings.isSelected("Cool Downs")) return false;
        return visible();
    }

    public void setInactive() {
        anim.setDirection(Direction.BACKWARDS);
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public boolean shouldRemove() {
        return anim.isFinished(Direction.BACKWARDS);
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (fontCached == null) {
            fontCached = Fonts.getSize(13, Fonts.Type.DEFAULT);
        }

        MatrixStack matrix = context.getMatrices();
        float animation = anim.getOutput().floatValue();

        long now = System.currentTimeMillis();
        long prevFrame = last_frame_ms;
        last_frame_ms = now;
        // rawGap — для отлова возвращения после скрытия, dt — для сглаживания
        float rawGap = prevFrame == 0 ? 0f : (now - prevFrame) / 1000f;
        float dt = Math.min(0.1f, rawGap);

        long elapsedTime = time.elapsedTime();
        int timeSeconds = 0;
        if (elapsedTime >= -2147483648L && elapsedTime <= 2147483647L) {
            timeSeconds = (int) (-elapsedTime / 1000);
        } else {
            timeSeconds = elapsedTime < 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }

        // остаток кулдауна 1..0; полоса догоняет цель экспоненциально (кадронезависимо)
        float target = Math.max(0f, Math.min(1f, -elapsedTime / total_ms));
        if (fill < 0f || rawGap > 0.15f) {
            fill = target;
        } else {
            fill += (target - fill) * smooth_factor(dt, 0.08f);
        }

        // кулдаун истёк — гасим карточку до вызова setInactive() из тика
        if (expire_ms == 0 && target <= 0f) {
            expire_ms = now;
        }
        float expireP = expire_ms == 0 ? 0f : Math.min(1f, (now - expire_ms) / 350f);
        float expireFade = 1f - expireP * expireP * (3f - 2f * expireP);
        float cardAlpha = animation * expireFade;

        ItemStack displayStack = item.getDefaultStack();
        String name = displayStack.getName().getString();
        String duration = StringHelper.getDuration(timeSeconds);
        
        int textColor = ColorAssist.getText();
        int textAlpha = (int) (255 * cardAlpha);
        int colorWithAlpha = ColorAssist.rgba(
            (textColor >> 16) & 255, 
            (textColor >> 8) & 255, 
            textColor & 255, 
            textAlpha
        );

        float nameWidth = fontCached.getStringWidth(name);
        // ceil: (int)-усечение срезало правый пиксель обводки карточки
        float totalWidth = (float) Math.ceil(nameWidth + fontCached.getStringWidth(duration) + 30);
        
        setWidth((int) totalWidth);
        setHeight(15);

        int[] gc = ColorAssist.getRectangleGradientColors();
        
        Calculate.scale(matrix, getX() + getWidth() / 2, getY() + getHeight() / 2, 1, cardAlpha, () -> {
            // Фон
            blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight())
                    .round(4f)
                    .quality(5)
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight())
                    .round(4f)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                    .color(gc[0], gc[1], gc[2], gc[3])
                    .build());

            // Иконка предмета
            Render2D.drawStack(matrix, displayStack, getX() + 2, getY() + 4, false, 0.5F);
            
            // Разделитель: целые координаты + толщина 1, без полупиксельной линии
            rectangle.render(ShapeProperties.create(matrix, getX() + 14, getY() + 4, 1F, 7)
                    .color(ColorAssist.getOutline(1, 0.5F))
                    .build());
            
            // Название предмета
            fontCached.drawString(matrix, name, getX() + HudHelper.hud_px(18f), getY() + 5, colorWithAlpha);
            
            // Время: правый край фиксирован, не едет при смене разрядов
            fontCached.drawString(matrix, duration, HudHelper.hud_text_right(fontCached, duration, HudHelper.hud_px(getX() + getWidth() - 4f)), getY() + 5, colorWithAlpha);

            // Линейная полоса остатка — акцент темы, ширина сглажена на прошлом кадре
            if (fill > 0.02f) {
                rectangle.render(ShapeProperties.create(matrix, getX() + 3, getY() + getHeight() - 2.5f,
                                (getWidth() - 6f) * fill, 1.5f)
                        .round(1f)
                        .color(ColorAssist.getClientColor(0.9f))
                        .build());
            }
            // Короткая акцентная вспышка в момент истечения
            if (expireP > 0f && expireP < 1f) {
                rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), getHeight())
                        .round(4f)
                        .color(ColorAssist.getClientColor(0.35f * (1f - expireP)))
                        .build());
            }
        });
    }

    /** Кадронезависимый экспоненциальный фильтр: tau — время «догоняния» в секундах. */
    private static float smooth_factor(float dt, float tau) {
        return (float) (1.0 - Math.exp(-dt / Math.max(0.0001f, tau)));
    }

    @Override
    public void tick() {
        // Анимация обновляется автоматически в drawDraggable
    }
}
