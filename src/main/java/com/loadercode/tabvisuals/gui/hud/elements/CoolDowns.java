/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import lombok.Getter;
import lombok.Setter;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.registry.Registries;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.gui.hud.draggable.DraggableRepository;
import com.loadercode.tabvisuals.common.animation.Animation;
import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.common.animation.implement.Decelerate;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.gui.hud.compat.Instance;
import com.loadercode.tabvisuals.render.math.time.StopWatch;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoolDowns extends AbstractDraggable {
    public static CoolDowns getInstance() {
        return Instance.getDraggable(CoolDowns.class);
    }

    // Карта отдельных перетаскиваемых элементов для каждого предмета
    private final Map<Item, CoolDownItem> individualCooldowns = new HashMap<>();
    public final List<CoolDown> list = new ArrayList<>();
    
    @Setter
    @Getter
    private long lastItemChange = 0;
    private Object lastPlayer;
    private FontRenderer fontCached;
    private FontRenderer iconCached;
    private FontRenderer itemsCached;
    private int activeCooldownCount = 0;
    /** Кадровая развязка для въезда панели и пульса счётчика. */
    private long last_frame_ms;
    private float entrance_t = 1f;
    private int shown_count = -1;
    private long count_pulse_ms;
    private static final Item[] EXAMPLE_ITEMS = {
            Items.ENDER_EYE, Items.ENDER_PEARL, Items.SUGAR, Items.MACE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.TRIDENT, Items.CROSSBOW, Items.DRIED_KELP, Items.NETHERITE_SCRAP
    };

    public CoolDowns() {
        super("Cool Downs", 10, 40, 80, 23, true);
    }

    @Override
    public boolean visible() {
        return !list.isEmpty() || HudHelper.isChat(mc.currentScreen);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            activeCooldownCount = 0;
            return;
        }
        // Respawn/dimension change swaps the client player: drop every row.
        if (lastPlayer != mc.player) {
            lastPlayer = mc.player;
            clearAllCooldowns();
        }

        list.removeIf(c -> c.anim.isFinished(Direction.BACKWARDS));
        
        // Очистка завершенных индивидуальных кулдаунов
        individualCooldowns.values().removeIf(cd -> {
            if (cd.shouldRemove()) {
                DraggableRepository repo = com.loadercode.tabvisuals.gui.hud.Hud.getRepository();
                if (repo != null) {
                    repo.draggable().remove(cd);
                }
                return true;
            }
            return false;
        });
        int activeCount = 0;
        
        // Обновление статуса индивидуальных кулдаунов
        for (CoolDownItem cooldownItem : individualCooldowns.values()) {
            if (!mc.player.getItemCooldownManager().isCoolingDown(cooldownItem.getItem().getDefaultStack())) {
                cooldownItem.setInactive();
            }
            if (cooldownItem.isActive()) {
                activeCount++;
            }
        }
        
        for (CoolDown coolDown : list) {
            if (!mc.player.getItemCooldownManager().isCoolingDown(coolDown.item.getDefaultStack())) {
                coolDown.anim.setDirection(Direction.BACKWARDS);
            }
            // Не считаем: list создаётся 1-в-1 вместе с individualCooldowns (см.
            // packet()) — иначе каждый кулдаун учитывался бы дважды ("Active: 2").
        }
        activeCooldownCount = activeCount;
    }

    /**
     * Fed from ClientPlayNetworkMixin: a server-set cooldown group (ender
     * pearl etc.) spawns its own draggable card, exactly like the LoxeDLC
     * CooldownUpdateS2CPacket flow.
     */
    public void onCooldownUpdate(Identifier cooldownGroup, int cooldownTicks) {
        if (HudHelper.nullCheck()) return;
        Item item = Registries.ITEM.get(cooldownGroup);

        // Деактивируем существующий кулдаун этого предмета
        CoolDownItem existingItem = individualCooldowns.get(item);
        if (existingItem != null) {
            existingItem.setInactive();
        }

        for (CoolDown coolDown : list) {
            if (coolDown.item.equals(item)) {
                coolDown.anim.setDirection(Direction.BACKWARDS);
            }
        }

        if (cooldownTicks != 0) {
            // Создаем новый отдельный перетаскиваемый элемент
            float startX = getX() + 10;
            float startY = getY() + 30 + (individualCooldowns.size() * 20);

            CoolDownItem newItem = new CoolDownItem(
                item,
                new StopWatch().setMs(-cooldownTicks * 50L),
                startX,
                startY
            );

            DraggableRepository repo = com.loadercode.tabvisuals.gui.hud.Hud.getRepository();
            // put() подменяет старый объект того же предмета — его нужно убрать
            // из репозитория, иначе он остаётся сиротой: удаление по значениям
            // карты его уже не найдёт, и список draggables будет расти с каждым
            // повторным кулдауном (бросок жемчуга и т.п.).
            CoolDownItem previous = individualCooldowns.put(item, newItem);
            if (repo != null) {
                if (previous != null) repo.draggable().remove(previous);
                repo.draggable().add(newItem);
            }

            list.add(new CoolDown(item, new StopWatch().setMs(-cooldownTicks * 50L), new Decelerate().setMs(150).setValue(1.0F)));
        }
    }

    private void clearAllCooldowns() {
        list.clear();
        DraggableRepository repo = com.loadercode.tabvisuals.gui.hud.Hud.getRepository();
        if (repo != null) {
            for (CoolDownItem cd : individualCooldowns.values()) {
                repo.draggable().remove(cd);
            }
        }
        individualCooldowns.clear();
    }

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
      if(fontCached == null){
         fontCached = Fonts.getSize(13,Fonts.Type.DEFAULT);
         iconCached = Fonts.getSize(21,Fonts.Type.ICONS);
         itemsCached = Fonts.getSize(12,Fonts.Type.DEFAULT);

      }
        String cooldownCountText = String.valueOf(activeCooldownCount);
        float textWidth = itemsCached.getStringWidth(cooldownCountText);
        float boxWidth = textWidth + 6;

        // wall-clock развязка: по разрыву кадров понимаем, что элемент только что вернулся
        long now = System.currentTimeMillis();
        long prevFrame = last_frame_ms;
        last_frame_ms = now;
        float dt = prevFrame == 0 ? 0f : Math.min(0.25f, (now - prevFrame) / 1000f);
        if (prevFrame == 0 || dt > 0.15f) entrance_t = 0f;
        entrance_t = Math.min(1f, entrance_t + dt / 0.25f);
        final float enter = ease_out_cubic(entrance_t);

        if (shown_count < 0) {
            shown_count = activeCooldownCount;
            count_pulse_ms = now - 1000L;
        } else if (shown_count != activeCooldownCount) {
            shown_count = activeCooldownCount;
            count_pulse_ms = now;
        }
        final float countPulse = pulse_env(now - count_pulse_ms, 260L);

        final float countX = getX() + getWidth() - boxWidth - 2.5f;
        final float bump = 1f + 0.35f * countPulse;
        final int countColor = ColorAssist.interpolate(ColorAssist.getText(), ColorAssist.getClientColor(), countPulse);
        final float slideY = (1f - enter) * -6f;

        int[] gc = ColorAssist.getRectangleGradientColors();
        int blurQuality = com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality();
        float totalH = getHeight();

        matrix.push();
        matrix.translate(0f, slideY, 0f);
        Calculate.setAlpha(enter, () -> {
        blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), totalH)
                .round(4f)
                .quality(blurQuality)
                .color(ColorAssist.getBlurColor())
                .build());
        rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), totalH)
                .round(4f)
                .thickness(0.1f)
                .outlineColor(ColorAssist.getOutlineColor())
                .color(gc[0], gc[1], gc[2], gc[3])
                .build());
            blur.render(ShapeProperties.create(matrix, getX(), getY() + 16.5F, getWidth(), getHeight() - 17)
                    .round(4f).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY() + 16.5F, getWidth(), getHeight() - 17)
                    .round(4f)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                    .color(gc[0], gc[1], gc[2], gc[3])
                .build());

        itemsCached.drawString(matrix, "Active:", getX() + getWidth() - boxWidth - 22, getY() + 7, ColorAssist.getText());
        // счётчик короткий — центр берём от измерения того же шрифта, выравнивание x фиксированное
        Calculate.scale(matrix, countX + textWidth / 2f, getY() + 10f, bump, bump, () ->
                itemsCached.drawString(matrix, cooldownCountText, countX, getY() + 7, countColor));

        rectangle.render(ShapeProperties.create(matrix, getX() + 18, getY() + 5, 0.5f, 6)
                .color(ColorAssist.getText(0.5F)).round(0F).build());

        iconCached.drawString(matrix, "D", getX() + 4f, getY() + 5.5f, ColorAssist.getText());
        fontCached.drawString(matrix, "CoolDowns", getX() + 22, getY() + 6.5f, ColorAssist.getText());

        // Показываем инфо что каждый кулдаун теперь можно двигать отдельно
        if (HudHelper.isChat(mc.currentScreen)) {
            itemsCached.drawString(matrix, "Каждый CD можно двигать!", getX() + 5, getY() + 20, ColorAssist.getText());
        }
        });
        matrix.pop();

        setHeight(35);
    }

    private static float ease_out_cubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Огибающая «удара»: синус на отрезке [0, duration), иначе 0. */
    private static float pulse_env(long elapsedMs, long durationMs) {
        if (elapsedMs < 0 || elapsedMs >= durationMs) return 0f;
        return (float) Math.sin(Math.PI * (elapsedMs / (double) durationMs));
    }

    public record CoolDown(Item item, StopWatch time, Animation anim) {}
}

