// language: Java, file: HudRenderer.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * In-game HUD subset: HotKeys / Active Potions / Staff Online panels on the
 * left, toast notifications under the crosshair. All drawn on the ported
 * render stack (UiRender + Fonts), panel metrics follow the reference style:
 * separate header pill + body card, 5px radius, dark glass fill.
 */
public final class HudRenderer {

    // layout ------------------------------------------------------------------
    private static final float X0 = 8f;
    private static final float COL_GAP = 6f;
    /** Bottom of the panel row: clears hotbar + xp/health bars in survival. */
    private static final float BOTTOM_MARGIN = 56f;
    private static final float PAD = 6f;
    private static final float PILL_H = 16f;
    private static final float PILL_GAP = 4f;
    private static final float ROW_H = 13.5f;
    private static final float BODY_PAD = 6f;
    private static final float RADIUS = 5f;
    private static final float ICON = 8.5f;
    private static final float POTION_ICON = 9f;
    private static final float DOT = 3.5f;

    // palette -----------------------------------------------------------------
    private static final int BG = 0xE6101114;        // (16,17,20) @ ~90%
    private static final int LINE = 0x16FFFFFF;      // white @ 8%
    private static final int TITLE_C = 0xFFF5F5FA;
    private static final int KEY_C = 0xFFE6E8F0;     // hotkey module name
    private static final int VALUE_C = 0xFFC8CDD7;   // hotkey key / staff tag
    private static final int ROW_C = 0xFFF5F5FA;     // potion name / timer
    private static final int STAFF_C = 0xFFF0F2F8;   // staff name
    private static final int TOAST_TEXT_C = 0xFFC8CDDA;
    private static final int GREEN = 0xFF2ED573;
    private static final int RED_DOT = 0xFFFF4757;
    private static final int VANISH_C = 0xFFFF6B81;

    // fonts -------------------------------------------------------------------
    private static final FontRenderer F_TITLE = Fonts.getSize(11, Fonts.Type.SEMI);
    private static final FontRenderer F_ROW = Fonts.getSize(10, Fonts.Type.REGULAR);
    private static final FontRenderer F_TAG = Fonts.getSize(9, Fonts.Type.SEMI);
    private static final FontRenderer F_TOAST = Fonts.getSize(11, Fonts.Type.REGULAR);
    private static final FontRenderer F_TOAST_SEMI = Fonts.getSize(11, Fonts.Type.SEMI);

    private HudRenderer() {
    }

    /** Wire into the Fabric HUD event. Call once from the client initializer. */
    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) {
            return;
        }
        MatrixStack m = ctx.getMatrices();
        float w = mc.getWindow().getScaledWidth();
        float h = mc.getWindow().getScaledHeight();

        renderToasts(mc, m, w, h, true);
        renderPanels(mc, ctx, m, w, h);
    }

    /**
     * Toast stack, centered under the crosshair. Shared with ClickGuiScreen:
     * the screen draws them on top of its overlay; {@code drive} is true only
     * where the HUD frame is not already advancing the animation.
     */
    public static void renderToasts(MinecraftClient mc, MatrixStack m, float screenW, float screenH, boolean drive) {
        if (drive) {
            NotificationManager.update(screenH);
        }
        List<NotificationManager.Toast> queue = NotificationManager.queue();
        if (queue.isEmpty()) {
            return;
        }

        float textH = F_TOAST.getStringHeight("Aa");
        for (NotificationManager.Toast t : queue) {
            float pw = F_TOAST.getStringWidth(t.prefix);
            float nw = F_TOAST_SEMI.getStringWidth(t.name);
            float mw = F_TOAST.getStringWidth(t.middle);
            float sw = F_TOAST_SEMI.getStringWidth(t.status);
            float textW = pw + nw + mw + sw;
            float cardW = textW + 24f;
            float cardH = NotificationManager.TOAST_H;

            float alpha = NotificationManager.clamp01(t.enterEased) * (1f - t.exitP);
            if (t.animY < 0f || alpha <= 0.02f) {
                continue;
            }
            float scale = t.expired()
                    ? 0.92f + 0.08f * (1f - t.exitP)
                    : 0.92f + 0.08f * NotificationManager.clamp01(t.enterEased);
            float yOffset = (1f - t.enterEased) * 13f - 9f * t.exitP;

            float sw2 = cardW * scale;
            float sh2 = cardH * scale;
            float x = screenW / 2f - sw2 / 2f;
            float y = t.animY + yOffset + (cardH - sh2) / 2f;

            UiRender.rect(m, x, y, sw2, sh2, RADIUS, UiRender.mulAlpha(BG, alpha));
            UiRender.outline(m, x + 0.5f, y + 0.5f, sw2 - 1f, sh2 - 1f, RADIUS, UiRender.mulAlpha(LINE, alpha), 1f);

            float tx = x + (sw2 - textW) / 2f;
            float ty = y + (sh2 - textH) / 2f;
            UiRender.text(F_TOAST, m, t.prefix, tx, ty, UiRender.mulAlpha(TOAST_TEXT_C, alpha));
            UiRender.text(F_TOAST_SEMI, m, t.name, tx + pw, ty, UiRender.mulAlpha(0xFFFFFFFF, alpha));
            UiRender.text(F_TOAST, m, t.middle, tx + pw + nw, ty, UiRender.mulAlpha(TOAST_TEXT_C, alpha));
            UiRender.text(F_TOAST_SEMI, m, t.status, tx + pw + nw + mw, ty, UiRender.mulAlpha(t.statusColor, alpha));
        }
    }

    // ------------------------------------------------------------------ panels

    private static void renderPanels(MinecraftClient mc, DrawContext ctx, MatrixStack m, float screenW, float screenH) {
        List<String[]> hotkeys = hotkeyRows();
        List<PotionRow> potions = potionRows(mc);
        List<StaffManager.Entry> staff = StaffManager.collect(mc);

        float x = X0;
        float bottom = screenH - BOTTOM_MARGIN;

        if (!hotkeys.isEmpty()) {
            float w = hotkeysWidth(hotkeys);
            drawHotkeys(m, x, bottom, w, hotkeys);
            x += w + COL_GAP;
        }
        if (!potions.isEmpty()) {
            float w = potionsWidth(potions);
            drawPotions(m, ctx, mc, x, bottom, w, potions);
            x += w + COL_GAP;
        }
        if (!staff.isEmpty()) {
            float w = staffWidth(staff);
            drawStaff(m, x, bottom, w, staff);
        }
    }

    private static List<String[]> hotkeyRows() {
        List<ModuleStub> bound = new ArrayList<>();
        for (ModuleStub m : ModuleRegistry.ALL) {
            if (m.enabled() && m.bind() > 0 && m.bindName() != null) {
                bound.add(m);
            }
        }
        bound.sort(Comparator.comparing(ModuleStub::name));
        List<String[]> rows = new ArrayList<>(bound.size());
        for (ModuleStub m : bound) {
            rows.add(new String[]{m.name(), m.bindName()});
        }
        return rows;
    }

    private static void drawHotkeys(MatrixStack m, float x, float bottom, float w, List<String[]> rows) {
        float bodyH = rows.size() * ROW_H + BODY_PAD;
        float totalH = PILL_H + PILL_GAP + bodyH;
        float pillY = bottom - totalH;

        drawPill(m, x, pillY, w, "HotKeys", Icon.KEYBOARD);
        float bodyY = pillY + PILL_H + PILL_GAP;
        UiRender.rect(m, x, bodyY, w, bodyH, RADIUS, BG);
        UiRender.outline(m, x + 0.5f, bodyY + 0.5f, w - 1f, bodyH - 1f, RADIUS, LINE, 1f);

        float rowY = bodyY + 3.5f;
        for (String[] r : rows) {
            float textH = F_ROW.getStringHeight(r[0]);
            float ty = rowY + (ROW_H - textH) / 2f;
            UiRender.text(F_ROW, m, r[0], x + PAD, ty, KEY_C);
            float kw = F_ROW.getStringWidth(r[1]);
            UiRender.text(F_ROW, m, r[1], x + w - PAD - kw, ty, VALUE_C);
            rowY += ROW_H;
        }
    }

    private static float hotkeysWidth(List<String[]> rows) {
        float max = 0f;
        for (String[] r : rows) {
            max = Math.max(max, F_ROW.getStringWidth(r[0]) + 10f + F_ROW.getStringWidth(r[1]));
        }
        return Math.max(96f, PAD * 2f + max);
    }

    private static void drawPotions(MatrixStack m, DrawContext ctx, MinecraftClient mc, float x, float bottom, float w, List<PotionRow> rows) {
        float bodyH = rows.size() * ROW_H + BODY_PAD;
        float totalH = PILL_H + PILL_GAP + bodyH;
        float pillY = bottom - totalH;

        drawPill(m, x, pillY, w, "Active Potions", Icon.FLASK);
        float bodyY = pillY + PILL_H + PILL_GAP;
        UiRender.rect(m, x, bodyY, w, bodyH, RADIUS, BG);
        UiRender.outline(m, x + 0.5f, bodyY + 0.5f, w - 1f, bodyH - 1f, RADIUS, LINE, 1f);

        float rowY = bodyY + 3.5f;
        for (PotionRow r : rows) {
            float iconY = rowY + (ROW_H - POTION_ICON) / 2f;
            drawEffectSprite(m, ctx, mc, r.inst, x + PAD, iconY);

            float nameX = x + PAD + POTION_ICON + 4.5f;
            float textH = F_ROW.getStringHeight(r.name);
            float ty = rowY + (ROW_H - textH) / 2f;
            UiRender.text(F_ROW, m, r.name, nameX, ty, ROW_C);
            float cursor = nameX + F_ROW.getStringWidth(r.name);
            if (!r.roman.isEmpty()) {
                float romanX = cursor + F_ROW.getStringWidth(" ");
                UiRender.text(F_ROW, m, r.roman, romanX, ty, r.chipColor);
            }
            float tw = F_ROW.getStringWidth(r.time);
            UiRender.text(F_ROW, m, r.time, x + w - PAD - tw, ty, ROW_C);
            rowY += ROW_H;
        }
    }

    private static void drawEffectSprite(MatrixStack m, DrawContext ctx, MinecraftClient mc, StatusEffectInstance inst, float x, float y) {
        try {
            Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(inst.getEffectType());
            if (sprite != null) {
                ctx.drawSpriteStretched(RenderLayer::getGuiTextured, sprite, (int) x, (int) y, (int) POTION_ICON, (int) POTION_ICON);
                return;
            }
        } catch (Throwable ignored) {
            // missing atlas entry → colour chip below
        }
        UiRender.rect(m, x, y, POTION_ICON, POTION_ICON, 2.5f, 0xFF7E8494);
    }

    private static float potionsWidth(List<PotionRow> rows) {
        float max = 0f;
        for (PotionRow r : rows) {
            float nameW = F_ROW.getStringWidth(r.name);
            if (!r.roman.isEmpty()) {
                nameW += F_ROW.getStringWidth(" ") + F_ROW.getStringWidth(r.roman);
            }
            max = Math.max(max, nameW + 18f + F_ROW.getStringWidth(r.time));
        }
        return Math.max(106f, PAD * 2f + POTION_ICON + 4.5f + max);
    }

    private static void drawStaff(MatrixStack m, float x, float bottom, float w, List<StaffManager.Entry> staff) {
        float bodyH = staff.size() * ROW_H + BODY_PAD;
        float totalH = PILL_H + PILL_GAP + bodyH;
        float pillY = bottom - totalH;

        drawPill(m, x, pillY, w, "Staff Online", Icon.PERSON);
        float bodyY = pillY + PILL_H + PILL_GAP;
        UiRender.rect(m, x, bodyY, w, bodyH, RADIUS, BG);
        UiRender.outline(m, x + 0.5f, bodyY + 0.5f, w - 1f, bodyH - 1f, RADIUS, LINE, 1f);

        float rowY = bodyY + 3.5f;
        for (StaffManager.Entry e : staff) {
            // avatar chip: reference hair palette (orange for "domer", brown otherwise)
            int hair = e.name().toLowerCase(Locale.ROOT).contains("domer") ? 0xFFE67E22 : 0xFF734426;
            float chipY = rowY + (ROW_H - POTION_ICON) / 2f;
            UiRender.rect(m, x + PAD, chipY, POTION_ICON, POTION_ICON, 2.5f, hair);
            String initial = e.name().substring(0, 1);
            float iw = F_TAG.getStringWidth(initial);
            float ih = F_TAG.getStringHeight(initial);
            UiRender.text(F_TAG, m, initial, x + PAD + (POTION_ICON - iw) / 2f, chipY + (POTION_ICON - ih) / 2f, 0xFFFFFFFF);

            float nameX = x + PAD + POTION_ICON + 4.5f;
            float textH = F_ROW.getStringHeight(e.name());
            float ty = rowY + (ROW_H - textH) / 2f;
            UiRender.text(F_ROW, m, e.name(), nameX, ty, STAFF_C);

            String tag = e.vanished() ? "[VANISH]" : "Online";
            int tagC = e.vanished() ? VANISH_C : VALUE_C;
            int dotC = e.vanished() ? RED_DOT : GREEN;
            float right = x + w - PAD;
            UiRender.rect(m, right - DOT, rowY + (ROW_H - DOT) / 2f, DOT, DOT, DOT / 2f, dotC);
            float tw = F_TAG.getStringWidth(tag);
            float th = F_TAG.getStringHeight(tag);
            UiRender.text(F_TAG, m, tag, right - DOT - 3.5f - tw, rowY + (ROW_H - th) / 2f, tagC);
            rowY += ROW_H;
        }
    }

    private static float staffWidth(List<StaffManager.Entry> staff) {
        float max = 0f;
        for (StaffManager.Entry e : staff) {
            String tag = e.vanished() ? "[VANISH]" : "Online";
            max = Math.max(max, F_ROW.getStringWidth(e.name()) + 14f + F_TAG.getStringWidth(tag) + 7f);
        }
        return Math.max(104f, PAD * 2f + POTION_ICON + 4.5f + max);
    }

    // ------------------------------------------------------------------ pill

    private enum Icon {KEYBOARD, FLASK, PERSON}

    private static void drawPill(MatrixStack m, float x, float y, float w, String title, Icon icon) {
        UiRender.rect(m, x, y, w, PILL_H, RADIUS, BG);
        UiRender.outline(m, x + 0.5f, y + 0.5f, w - 1f, PILL_H - 1f, RADIUS, LINE, 1f);

        float iconY = y + (PILL_H - ICON) / 2f;
        switch (icon) {
            case KEYBOARD -> {
                UiRender.outline(m, x + PAD, iconY + 0.5f, 9f, 6.5f, 1.5f, TITLE_C, 1f);
                for (int row = 0; row < 2; row++) {
                    for (int c = 0; c < 4; c++) {
                        UiRender.rect(m, x + PAD + 1.6f + c * 1.9f, iconY + 2.1f + row * 1.9f, 1.1f, 1.1f, 0.3f, TITLE_C);
                    }
                }
            }
            case FLASK -> {
                UiRender.rect(m, x + PAD + 3.2f, iconY, 2.6f, 2.6f, 0.5f, TITLE_C);
                UiRender.rect(m, x + PAD + 1f, iconY + 2.6f, 7f, 6.4f, 2.2f, TITLE_C);
            }
            case PERSON -> {
                UiRender.rect(m, x + PAD + 2.6f, iconY, 3.4f, 3.4f, 1.7f, TITLE_C);
                UiRender.rect(m, x + PAD + 0.75f, iconY + 4.4f, 7f, 3.6f, 1.8f, TITLE_C);
            }
        }
        float th = F_TITLE.getStringHeight(title);
        UiRender.text(F_TITLE, m, title, x + PAD + ICON + 4.5f, y + (PILL_H - th) / 2f, TITLE_C);
    }

    // ------------------------------------------------------------------ potions data

    private record PotionRow(String name, String roman, String time, int chipColor, StatusEffectInstance inst) {
    }

    private static List<PotionRow> potionRows(MinecraftClient mc) {
        List<PotionRow> out = new ArrayList<>();
        for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
            String key = effectKey(inst.getTranslationKey());
            String name = effectName(key);
            int level = inst.getAmplifier() + 1;
            String roman = level > 1 ? roman(level) : "";
            String time = inst.isInfinite() ? "∞" : formatDuration(inst.getDuration());
            out.add(new PotionRow(name, roman, time, effectColor(key), inst));
        }
        // shortest duration first — matches the reference ordering
        out.sort(Comparator.comparingInt(a -> a.inst().isInfinite() ? Integer.MAX_VALUE : a.inst().getDuration()));
        return out;
    }

    private static String effectKey(String translationKey) {
        String key = translationKey;
        if (key.startsWith("effect.minecraft.")) {
            key = key.substring("effect.minecraft.".length());
        } else if (key.startsWith("effect.")) {
            int lastDot = key.lastIndexOf('.');
            key = lastDot >= 0 && lastDot < key.length() - 1 ? key.substring(lastDot + 1) : key;
        }
        return key.toLowerCase(Locale.ROOT);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "L" + level;
        };
    }

    private static String formatDuration(int ticks) {
        int sec = Math.max(0, ticks) / 20;
        int min = sec / 60;
        int rem = sec % 60;
        return min + ":" + (rem < 10 ? "0" + rem : String.valueOf(rem));
    }

    private static String effectName(String key) {
        return switch (key) {
            case "speed" -> "Speed";
            case "strength" -> "Strength";
            case "fire_resistance" -> "Fire Resistance";
            case "regeneration" -> "Regeneration";
            case "invisibility" -> "Invisibility";
            case "jump_boost" -> "Jump Boost";
            case "haste" -> "Haste";
            case "resistance" -> "Resistance";
            case "water_breathing" -> "Water Breathing";
            case "absorption" -> "Absorption";
            case "health_boost" -> "Health Boost";
            case "slow_falling" -> "Slow Falling";
            case "slowness" -> "Slowness";
            case "weakness" -> "Weakness";
            case "poison" -> "Poison";
            case "wither" -> "Wither";
            case "glowing" -> "Glowing";
            case "blindness" -> "Blindness";
            case "night_vision" -> "Night Vision";
            case "dolphins_grace" -> "Dolphin's Grace";
            case "conduit_power" -> "Conduit Power";
            case "levitation" -> "Levitation";
            case "hunger" -> "Hunger";
            case "mining_fatigue" -> "Mining Fatigue";
            case "nausea" -> "Nausea";
            case "luck" -> "Luck";
            case "bad_luck" -> "Bad Luck";
            case "hero_of_the_village" -> "Hero of the Village";
            case "bad_omen" -> "Bad Omen";
            case "raid_omen" -> "Raid Omen";
            case "trial_omen" -> "Trial Omen";
            case "wind_charged" -> "Wind Charged";
            case "weaving" -> "Weaving";
            case "oozing" -> "Oozing";
            case "infested" -> "Infested";
            default -> capitalizeWords(key);
        };
    }

    private static String capitalizeWords(String key) {
        String[] parts = key.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p, 1, p.length());
        }
        return sb.toString();
    }

    private static int effectColor(String key) {
        return switch (key) {
            case "speed" -> 0xFF54A0FF;
            case "strength" -> 0xFFFF5A5F;
            case "fire_resistance" -> 0xFFFB923C;
            case "regeneration" -> 0xFFFF6BC1;
            case "resistance" -> 0xFF34D399;
            case "haste" -> 0xFFFBBF24;
            case "night_vision" -> 0xFF818CF8;
            case "invisibility" -> 0xFF9CA3AF;
            case "jump_boost" -> 0xFFA3E635;
            case "slowness" -> 0xFF64748B;
            case "poison" -> 0xFF4ADE80;
            case "wither" -> 0xFF374151;
            default -> 0xFF8B93A7;
        };
    }
}
