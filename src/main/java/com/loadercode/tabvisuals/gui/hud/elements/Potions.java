/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntityStatusEffectS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Formatting;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.common.animation.Animation;
import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.common.animation.implement.Decelerate;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class Potions extends AbstractDraggable {
    private final List<Potion> list = new ArrayList<>();
    private static final RegistryEntry[] NEGATIVE_EFFECTS = new RegistryEntry[] {
            StatusEffects.POISON, StatusEffects.WITHER, StatusEffects.NAUSEA, StatusEffects.BLINDNESS,
            StatusEffects.HUNGER, StatusEffects.SLOWNESS, StatusEffects.MINING_FATIGUE, StatusEffects.INSTANT_DAMAGE,
            StatusEffects.WEAKNESS, StatusEffects.LEVITATION, StatusEffects.UNLUCK, StatusEffects.BAD_OMEN
    };
    private static List<RegistryEntry<StatusEffect>> previewEffects;
    private FontRenderer fontCached;
    private FontRenderer iconCached;
    private FontRenderer fontPotionCache;
    private FontRenderer itemsCached;
    private long lastEffectChange = 0;
    private RegistryEntry<StatusEffect> currentRandomEffect = StatusEffects.SPEED;
    private int activeEffectCount = 0;
    /** Ids we already have rows for (the LoxeDLC packet flow, via polling). */
    private final Set<RegistryEntry<StatusEffect>> known = new HashSet<>();
    private Object lastPlayer;
    /** Время появления превью-строки (0 — превью не показывается). */
    private long preview_born_ms;

    public Potions() {
        super("Potions", 200, 40, 80, 23, true);
    }

    @Override
    public boolean visible() {
        return !list.isEmpty() || HudHelper.isChat(mc.currentScreen);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            list.clear();
            known.clear();
            activeEffectCount = 0;
            return;
        }
        if (lastPlayer != mc.player) {
            list.clear();
            known.clear();
            lastPlayer = mc.player;
        }

        // Vanilla updates the live effect list itself; the LoxeDLC packet flow
        // is reproduced by diffing presence each tick.
        Set<RegistryEntry<StatusEffect>> activeNow = new HashSet<>();
        for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
            RegistryEntry<StatusEffect> id = inst.getEffectType();
            activeNow.add(id);
            if (known.add(id)) {
                markEffectForRemoval(id);
                list.add(new Potion(new StatusEffectInstance(inst), new Decelerate().setMs(150).setValue(1.0F),
                        System.currentTimeMillis()));
            }
        }
        for (RegistryEntry<StatusEffect> id : known) {
            if (!activeNow.contains(id)) {
                markEffectForRemoval(id);
            }
        }
        known.retainAll(activeNow);

        list.removeIf(p -> p.anim.isFinished(Direction.BACKWARDS));
        activeEffectCount = 0;
        for (Potion potion : list) {
            potion.effect.update(mc.player, null);
            if (!potion.anim.isFinished(Direction.BACKWARDS)) {
                activeEffectCount++;
            }
        }
        if (list.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastEffectChange >= 1000) {
                List<RegistryEntry<StatusEffect>> effects = getPreviewEffects();
                if (!effects.isEmpty()) {
                    currentRandomEffect = effects.get(ThreadLocalRandom.current().nextInt(effects.size()));
                    lastEffectChange = currentTime;
                }
            }
        }
    }

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        final long now = System.currentTimeMillis();
        if(fontCached == null){
            fontCached = Fonts.getSize(13,Fonts.Type.DEFAULT);
            fontPotionCache = Fonts.getSize(13,Fonts.Type.DEFAULT);
            iconCached = Fonts.getSize(17,Fonts.Type.ICONS);
            itemsCached = Fonts.getSize(12,Fonts.Type.DEFAULT);

        }

        String effectCountText = String.valueOf(activeEffectCount);

        int[] gc = ColorAssist.getRectangleGradientColors();
        int blurQuality = com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality();
        float totalH = getHeight();

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
                    .round(0,4,0,4).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY() + 16.5F, getWidth(), getHeight() - 17)
                    .round(0,4,0,4)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                    .color(gc[0], gc[1], gc[2], gc[3])
                .build());

        // Фиксированная колонка справа: подпись и счётчик не смещаются при смене разрядов
        final float rightEdge = HudHelper.hud_px(getX() + getWidth() - 8f);
        itemsCached.drawString(matrix, "Active:",
                HudHelper.hud_text_right(itemsCached, "Active:", rightEdge - 22f), getY() + 7, ColorAssist.getText());
        itemsCached.drawString(matrix, effectCountText,
                HudHelper.hud_text_right(itemsCached, effectCountText, rightEdge), getY() + 7, ColorAssist.getText());

        rectangle.render(ShapeProperties.create(matrix, getX() + 18, getY() + 5, 1f, 6)
                .color(ColorAssist.getText(0.5F)).round(0F).build());

        iconCached.drawString(matrix, "C", getX() + HudHelper.hud_px(5f), getY() + HudHelper.hud_px(6.5f), ColorAssist.getText());
        fontCached.drawString(matrix, getName(), getX() + HudHelper.hud_px(22f), getY() + HudHelper.hud_px(6.5f), ColorAssist.getText());

        float centerX = getX() + getWidth() / 2.0F;
        float offset = 23;
        float maxWidth = 95;

        if (list.isEmpty() && HudHelper.isChat(mc.currentScreen)) {
            if (preview_born_ms == 0) preview_born_ms = now;
            final float rowStagger = stagger_progress(now, preview_born_ms, 0);
            final float rowSlide = (1f - rowStagger) * -12f;
            float centerY = HudHelper.hud_px(getY() + offset);
            String name = "Example effect";
            String duration = "**:**";
            int textColor = ColorAssist.getText();
            int textAlpha = (int) (255 * rowStagger);
            int colorWithAlpha = ColorAssist.rgba((textColor >> 16) & 255, (textColor >> 8) & 255, textColor & 255, textAlpha);
            int color = ColorAssist.multAlpha(ColorAssist.getText(), rowStagger);
            int colorWithAlphaRectangle = ColorAssist.rgba((textColor >> 16) & 205, (textColor >> 8) & 205, textColor & 205, textAlpha - 125);
            matrix.push();
            matrix.translate(rowSlide, 0f, 0f);
            Calculate.scale(matrix, centerX, centerY, 1, 1, () -> {
                Render2D.drawSprite(matrix, mc.getStatusEffectSpriteManager().getSprite(currentRandomEffect), getX() + 3.5F, Math.round(centerY) - 2, 8, 8, colorWithAlpha);
                rectangle.render(ShapeProperties.create(matrix, getX() + 14, centerY - 1, 1F, 7).color(colorWithAlphaRectangle).build());
                fontPotionCache.drawString(matrix, name, getX() + HudHelper.hud_px(18f), centerY + 1, colorWithAlpha);
                fontPotionCache.drawString(matrix, duration, HudHelper.hud_text_right(fontPotionCache, duration, HudHelper.hud_px(getX() + getWidth() - 8f)), centerY + 1, color);
            });
            matrix.pop();
            float width = (float) Math.ceil(fontPotionCache.getStringWidth(name + duration)) + 30;
            maxWidth = Math.max(width, maxWidth);
            offset += 11 * rowStagger;
        } else {
            preview_born_ms = 0;
            for (int rowIndex = 0; rowIndex < list.size(); rowIndex++) {
                Potion potion = list.get(rowIndex);
                StatusEffectInstance effect = potion.effect;
                final float animation = potion.anim.getOutput().floatValue();
                final float rowStagger = stagger_progress(now, potion.bornMs(), rowIndex);
                final float rowSlide = (1f - rowStagger) * -12f;
                float centerY = HudHelper.hud_px(getY() + offset);
                int amplifier = effect.getAmplifier();
                String name = com.loadercode.tabvisuals.gui.hud.HudRenderer.effectName(com.loadercode.tabvisuals.gui.hud.HudRenderer.effectKey(effect.getTranslationKey()));
                String duration = getDuration(effect);
                String lvl = amplifier > 0 ? Formatting.RED + " " + (amplifier + 1) + Formatting.RESET : "";
                boolean isBadEffect = isBadEffect(effect.getEffectType());
                int textColor = isBadEffect ? ColorAssist.rgba(255, 85, 75, 255) : ColorAssist.getText();
                int textAlpha = 255;
                if (effect.getDuration() <= 200 && effect.getDuration() > 0) {
                    double output = 0.5 + 0.5 * Math.cos(2 * Math.PI * (System.currentTimeMillis() % 700) / 700.0);
                    textAlpha = (int) (100 + (155 * output));
                } else if (effect.getDuration() == 0) {
                    textAlpha = 0;
                }
                textAlpha = (int) (textAlpha * rowStagger);
                int colorWithAlpha = isBadEffect ? ColorAssist.rgba(255, 85, 75, textAlpha) : ColorAssist.rgba((textColor >> 16) & 255, (textColor >> 8) & 255, textColor & 255, textAlpha);
                int color = ColorAssist.multAlpha(ColorAssist.getText(), rowStagger);
                int colorWithAlphaRectangle = isBadEffect ? ColorAssist.rgba(255, 85, 75, textAlpha - 125) : ColorAssist.rgba((textColor >> 16) & 205, (textColor >> 8) & 205, textColor & 205, textAlpha - 125);
                matrix.push();
                matrix.translate(rowSlide, 0f, 0f);
                Calculate.scale(matrix, centerX, centerY, 1, animation, () -> {
                    Render2D.drawSprite(matrix, mc.getStatusEffectSpriteManager().getSprite(effect.getEffectType()), getX() + 3.5F, Math.round(centerY) - 2, 8, 8, colorWithAlpha);
                    rectangle.render(ShapeProperties.create(matrix, getX() + 14, centerY - 1, 1F, 7).color(colorWithAlphaRectangle).build());
                    fontPotionCache.drawString(matrix, name, getX() + HudHelper.hud_px(18f), centerY + 1, colorWithAlpha);
                    if (amplifier > 0) {
                        String level = " " + (amplifier + 1);
                        fontPotionCache.drawString(matrix, level, HudHelper.hud_px(getX() + 18f + fontPotionCache.getStringWidth(name)), centerY + 1, colorWithAlpha);
                    }
                    fontPotionCache.drawString(matrix, duration, HudHelper.hud_text_right(fontPotionCache, duration, HudHelper.hud_px(getX() + getWidth() - 8f)), centerY + 1, color);
                });
                matrix.pop();
                float width = (float) Math.ceil(fontPotionCache.getStringWidth(name + lvl + duration)) + 30;
                maxWidth = Math.max(width, maxWidth);
                offset += 11 * animation * rowStagger;
            }
        }
        setWidth((int) Math.ceil(maxWidth));
        setHeight((int) Math.ceil(offset));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(1f, value);
    }

    private static float ease_out_cubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /**
     * Прогресс въезда строки: задержка по индексу + сдвиг слева направо.
     * Считается от wall clock, поэтому не зависит от FPS.
     */
    private static float stagger_progress(long now, long bornMs, int index) {
        long delay = Math.min(index, 5) * 45L;
        return ease_out_cubic(clamp01((now - bornMs - delay) / 220f));
    }

    private String getDuration(StatusEffectInstance pe) {
        int duration = pe.getDuration();
        int minutes = duration / 1200;
        if (pe.isInfinite() || minutes > 60) {
            return "**:**";
        }

        int seconds = (duration % 1200) / 20;
        return seconds < 10
                ? minutes + ":0" + seconds
                : minutes + ":" + seconds;
    }

    private void markEffectForRemoval(RegistryEntry<StatusEffect> effectType) {
        for (Potion potion : list) {
            if (potion.effect.getEffectType().equals(effectType)) {
                potion.anim.setDirection(Direction.BACKWARDS);
            }
        }
    }

    private boolean isBadEffect(RegistryEntry<StatusEffect> effect) {
        for (RegistryEntry negativeEffect : NEGATIVE_EFFECTS) {
            if (effect == negativeEffect) {
                return true;
            }
        }
        return false;
    }

    private static List<RegistryEntry<StatusEffect>> getPreviewEffects() {
        if (previewEffects == null) {
            previewEffects = new ArrayList<>();
            for (Identifier id : Registries.STATUS_EFFECT.getIds()) {
                Registries.STATUS_EFFECT.getEntry(id).ifPresent(previewEffects::add);
            }
        }
        return previewEffects;
    }

    private record Potion(StatusEffectInstance effect, Animation anim, long bornMs) {}
}
