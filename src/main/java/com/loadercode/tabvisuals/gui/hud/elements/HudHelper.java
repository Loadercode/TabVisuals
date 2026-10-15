// language: Java, file: HudHelper.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Slim PlayerInteractionHelper: only what the ported HUD elements call —
 * chat detection, null checks, health reads and key naming.
 */
public final class HudHelper implements QuickImports {

    /** Vanilla display cap for sanity (LoxeDLC reference). */
    private static final float MAX_DISPLAYED_HEALTH = 1000f;

    private HudHelper() {
    }

    public static boolean isChat(Screen screen) {
        return screen instanceof net.minecraft.client.gui.screen.ChatScreen;
    }

    public static boolean nullCheck() {
        return mc.player == null || mc.world == null;
    }

    /** §-codes stripped (ColorAssist returns null for empty input). */
    public static String getCleanName(String name) {
        String cleaned = ColorAssist.removeFormatting(name);
        return cleaned == null ? "" : cleaned;
    }

    /** Text overload — display names arrive as Text from ItemStack / overlay. */
    public static String getCleanName(net.minecraft.text.Text text) {
        return getCleanName(text == null ? "" : text.getString());
    }

    public static float getHealth(LivingEntity entity) {
        float hp = entity.getHealth() + entity.getAbsorptionAmount();
        return MathHelper.clamp(hp, 0f, MAX_DISPLAYED_HEALTH);
    }

    public static float getAbsorption(LivingEntity entity) {
        return Math.max(0f, entity.getAbsorptionAmount());
    }

    public static String getHealthString(LivingEntity entity) {
        return getHealthString(getHealth(entity));
    }

    public static String getHealthString(float hp) {
        // Locale.ROOT: в ru-локали "%.1f" даёт «0,0», а старый replace(".0","")
        // превращал «0.0» в пустую строку — HP 0 не отображался вовсе.
        String s = String.format(java.util.Locale.ROOT, "%.1f", hp);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static net.minecraft.client.util.InputUtil.Type getKeyType(int key) {
        return key < 8 ? net.minecraft.client.util.InputUtil.Type.MOUSE
                : net.minecraft.client.util.InputUtil.Type.KEYSYM;
    }

    public static boolean isKey(int keyCode) {
        if (keyCode == -1) {
            return false;
        }
        if (keyCode < 8) {
            return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
    }

    public static PlayerEntity self() {
        return mc.player;
    }

    // ------------------------------------------------------------------
    //  ADDITIVE: wall-clock animation helpers (EPRender, TotemCounterHud).
    //  Время берётся из System.nanoTime(), поэтому анимации не зависят от FPS.
    // ------------------------------------------------------------------

    /** Доля прошедшего времени 0..1 от startNs; результат всегда в [0..1]. */
    public static float hud_anim_progress(long startNs, long nowNs, float durationSeconds) {
        if (durationSeconds <= 0f) {
            return 1f;
        }
        return hud_anim_clamp01((nowNs - startNs) / 1_000_000_000f / durationSeconds);
    }

    public static float hud_anim_clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    /** ease-out cubic: резкий старт, мягкое завершение без толчка. */
    public static float hud_anim_cubic_out(float t) {
        float inv = 1f - hud_anim_clamp01(t);
        return 1f - inv * inv * inv;
    }

    /** ease-out back: «пружина» — выходит за 1 и возвращается. overshoot — сила. */
    public static float hud_anim_back_out(float t, float overshoot) {
        float x = hud_anim_clamp01(t) - 1f;
        return 1f + (overshoot + 1f) * x * x * x + overshoot * x * x;
    }

    /** Экспоненциальное сглаживание к цели: speed — затухание в 1/сек, dt — секунды. */
    public static float hud_anim_approach(float current, float target, float speed, float dt) {
        if (!(dt > 0f)) {
            return current;
        }
        return target + (current - target) * (float) Math.exp(-speed * Math.min(dt, 0.1f));
    }

    // ------------------------------------------------------------------
    //  ADDITIVE: text/line pixel helpers. FontRenderer.getStringWidth завышает
    //  ширину (ширины глифов округляются вверх при генерации атласа), а
    //  drawCenteredString усекает x до int — оба источника дробных/смещённых
    //  позиций и мылящегося текста.
    // ------------------------------------------------------------------

    /** Привязка к пиксельной сетке 0.5 — дробные координаты дают мылящиеся глифы. */
    public static float hud_px(float value) {
        return Math.round(value * 2f) / 2f;
    }

    /** Экранная высота строки: getStringHeight возвращает глиф-пространство (в 2 раза больше экранного). */
    public static float hud_text_height(FontRenderer font, String text) {
        return font.getStringHeight(text) / 2f;
    }

    /** y отрисовки, чтобы верх строки совпал с screenTop (drawString смещает глифы на -3). */
    public static float hud_text_top(float screenTop) {
        return screenTop + 3f;
    }

    /** y, центрирующий строку по высоте бокса [boxTop; boxTop + boxH]. */
    public static float hud_text_mid_y(FontRenderer font, String text, float boxTop, float boxH) {
        return hud_text_top(boxTop + (boxH - hud_text_height(font, text)) / 2f);
    }

    /** x текста, прижатого правым краем к rightEdge (снимает дробность измерений). */
    public static float hud_text_right(FontRenderer font, String text, float rightEdge) {
        return hud_px(rightEdge - font.getStringWidth(text));
    }

    /** x центрированного текста: округление вместо усечения в drawCenteredString. */
    public static float hud_text_center_x(FontRenderer font, String text, float centerX) {
        return hud_px(centerX - font.getStringWidth(text) / 2f);
    }
}
