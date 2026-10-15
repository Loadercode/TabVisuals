/* (c) 2026 LoxeDLC. All rights reserved. */
package com.loadercode.tabvisuals.gui.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay;
import com.loadercode.tabvisuals.gui.hud.compat.Instance;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class KeystrokesOverlayHud extends AbstractDraggable {
    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();
    private boolean leftDown;
    private boolean rightDown;

    /** Сглаженное состояние нажатия клавиши (0..1) по её подписи. */
    private final Map<String, KeyPressState> press_states = new HashMap<>();

    /** Амплитуда «ухода вниз» крышки при полном нажатии. */
    private static final float PRESS_SHIFT = 1.4f;
    /** Экспоненциальное сглаживание: нажатие резкое, отпускание мягкое. */
    private static final float PRESS_RISE_TAU = 0.045f;
    private static final float PRESS_FALL_TAU = 0.085f;

    private static final class KeyPressState {
        float press;
        long last_nanos;

        KeyPressState(long now_nanos) {
            last_nanos = now_nanos;
        }
    }

    public KeystrokesOverlayHud() {
        super("Keystrokes", 10, 160, 84, 96, true);
    }

    public static KeystrokesOverlayHud getInstance() {
        return Instance.getDraggable(KeystrokesOverlayHud.class);
    }

    @Override
    public boolean visible() {
        KeystrokesOverlay overlay = Instance.get(KeystrokesOverlay.class);
        Hud hud = Hud.getInstance();
        return overlay != null
                && overlay.isState()
                && hud != null
                && hud.isState()
                && hud.interfaceSettings.isSelected("Keystrokes");
    }

    @Override
    public void tick() {
        long handle = mc.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();

        if (left && !leftDown) leftClicks.addLast(now);
        if (right && !rightDown) rightClicks.addLast(now);
        leftDown = left;
        rightDown = right;

        trimClicks(leftClicks, now);
        trimClicks(rightClicks, now);
    }

    @Override
    public void drawDraggable(DrawContext context) {
        KeystrokesOverlay overlay = Instance.get(KeystrokesOverlay.class);
        if (overlay == null || !overlay.isState()) return;

        MatrixStack matrix = context.getMatrices();
        FontRenderer font = Fonts.getSize(Math.max(11, overlay.getKeySize().getInt() / 2), Fonts.Type.BOLD);
        FontRenderer small = Fonts.getSize(Math.max(9, overlay.getKeySize().getInt() / 3), Fonts.Type.SEMI);
        float key = overlay.getKeySize().getValue();
        float gap = Math.max(3.0f, key * 0.12f);
        float x = getX();
        float y = getY();
        float width = key * 3.0f + gap * 2.0f;
        float rowY = y;

        drawKey(matrix, font, "W", x + key + gap, rowY, key, key, isPressed(mc.options.forwardKey), overlay);
        rowY += key + gap;
        drawKey(matrix, font, "A", x, rowY, key, key, isPressed(mc.options.leftKey), overlay);
        drawKey(matrix, font, "S", x + key + gap, rowY, key, key, isPressed(mc.options.backKey), overlay);
        drawKey(matrix, font, "D", x + (key + gap) * 2.0f, rowY, key, key, isPressed(mc.options.rightKey), overlay);
        rowY += key + gap;

        if (overlay.getShowMouse().isValue()) {
            float mouseWidth = (width - gap) / 2.0f;
            drawMouseKey(matrix, font, small, "LMB", x, rowY, mouseWidth, key, leftDown, leftClicks.size(), overlay);
            drawMouseKey(matrix, font, small, "RMB", x + mouseWidth + gap, rowY, mouseWidth, key, rightDown, rightClicks.size(), overlay);
            rowY += key + gap;
        }

        if (overlay.getShowSpace().isValue()) {
            drawKey(matrix, font, "SPACE", x, rowY, width, key * 0.72f, isPressed(mc.options.jumpKey), overlay);
            rowY += key * 0.72f + gap;
        }

        boolean showSneak = overlay.getShowSneak().isValue();
        boolean showSprint = overlay.getShowSprint().isValue();
        if (showSneak || showSprint) {
            float modifierWidth = showSneak && showSprint ? (width - gap) / 2.0f : width;
            float currentX = x;
            if (showSneak) {
                drawKey(matrix, font, "SHIFT", currentX, rowY, modifierWidth, key * 0.72f, isPressed(mc.options.sneakKey), overlay);
                currentX += modifierWidth + gap;
            }
            if (showSprint) {
                drawKey(matrix, font, "CTRL", currentX, rowY, modifierWidth, key * 0.72f, isPressed(mc.options.sprintKey), overlay);
            }
            rowY += key * 0.72f + gap;
        }

        setWidth((int) Math.ceil(width));
        setHeight((int) Math.ceil(rowY - y - gap));
    }

    private boolean isPressed(KeyBinding binding) {
        // isPressed() читает поле pressed, которое vanilla обновляет по ТЕКУЩЕЙ
        // привязке (KeyBinding.setKeyPressed / updatePressedStates).
        if (binding.isPressed()) {
            return true;
        }

        // Физический опрос — по текущей клавише бинда, а не по getDefaultKey():
        // иначе после ребинда старая дефолтная клавиша продолжала подсвечиваться.
        InputUtil.Key bound;
        try {
            bound = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        } catch (RuntimeException e) {
            return false;
        }
        if (bound == null
                || bound.getCategory() != InputUtil.Type.KEYSYM
                || bound.getCode() == InputUtil.UNKNOWN_KEY.getCode()) {
            return false;
        }
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), bound.getCode());
    }

    private void drawMouseKey(MatrixStack matrix, FontRenderer font, FontRenderer small, String label, float x, float y,
                              float width, float height, boolean pressed, int cps, KeystrokesOverlay overlay) {
        float press = drawKey(matrix, font, label, x, y, width, height, pressed, overlay);
        if (overlay.getShowCps().isValue()) {
            // CPS-счётчик едет вместе с крышкой при нажатии; x центрируем округлением
            String cpsText = cps + " CPS";
            float cpsX = HudHelper.hud_text_center_x(small, cpsText, HudHelper.hud_px(x + width / 2.0f));
            float cpsY = HudHelper.hud_px(y + height - 8.0f) + press * PRESS_SHIFT;
            small.drawString(matrix, cpsText, cpsX, cpsY, getTextColor(overlay));
        }
    }

    /**
     * Ставит цель 0/1 и двигает сглаженное значение экспоненциально от прошедшего
     * времени (dt из nanoTime), поэтому скорость не зависит от FPS. Возвращает press 0..1.
     */
    private float smooth_press(String label, boolean pressed) {
        long now = System.nanoTime();
        KeyPressState state = press_states.computeIfAbsent(label, k -> new KeyPressState(now));
        float target = pressed ? 1f : 0f;
        float dt = Math.min(0.1f, (now - state.last_nanos) / 1_000_000_000f);
        state.last_nanos = now;
        float tau = pressed ? PRESS_RISE_TAU : PRESS_FALL_TAU;
        state.press += (target - state.press) * (1f - (float) Math.exp(-dt / tau));
        if (Math.abs(target - state.press) < 0.002f) {
            state.press = target;
        }
        return state.press;
    }

    private float drawKey(MatrixStack matrix, FontRenderer font, String label, float x, float y, float width, float height,
                         boolean pressed, KeystrokesOverlay overlay) {
        float press = smooth_press(label, pressed);
        float round = getRound(overlay);
        float opacity = overlay.getOpacity().getValue();
        // Фон плавно перетекает из обычного в «нажатый», рамка — приглушается
        int background = ColorAssist.multAlpha(ColorAssist.lerp(press,
                overlay.getBackgroundColor().getColor(), overlay.getPressedColor().getColor()), opacity);
        int outline = ColorAssist.multAlpha(ColorAssist.lerp(press,
                overlay.getBorderColor().getColor(), overlay.getPressedColor().getColor()), opacity * 0.75f);
        float border = overlay.getBorderThickness().getValue();

        // Базовый бокс снапим по 0.5-сетке (дробные настройки размера дают мыльные края),
        // а pressShift намеренно остаётся дробным — крышка едет плавно
        float bx = HudHelper.hud_px(x);
        float by = HudHelper.hud_px(y);
        float bw = HudHelper.hud_px(width);
        float bh = HudHelper.hud_px(height);

        Hud hud = Hud.getInstance();
        if (hud != null && hud.shouldDrawHudBlur()) {
            blur.render(ShapeProperties.create(matrix, bx, by, bw, bh)
                    .round(round)
                    .quality(hud.getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
        }

        // Крышка press-down: уходит вниз на PRESS_SHIFT и слегка сжимается к центру
        float pressShift = press * PRESS_SHIFT;
        float pressScale = 1f - press * 0.05f;
        float centerX = bx + bw / 2.0f;
        float centerY = by + bh / 2.0f + pressShift;

        matrix.push();
        matrix.translate(centerX, centerY, 0);
        matrix.scale(pressScale, pressScale, 1);
        matrix.translate(-centerX, -centerY, 0);

        if (border > 0.0f) {
            rectangle.render(ShapeProperties.create(matrix, bx - border, by - border + pressShift, bw + border * 2.0f, bh + border * 2.0f)
                    .round(round + border)
                    .color(outline)
                    .build());
        }
        rectangle.render(ShapeProperties.create(matrix, bx, by + pressShift, bw, bh)
                .round(round)
                .color(background)
                .build());

        // Текст рисуется внутри того же matrix, что и крышка, — сам «едет» с ней;
        // центр — округлением, drawCenteredString усекал x до int
        font.drawString(matrix, label,
                HudHelper.hud_text_center_x(font, label, centerX),
                HudHelper.hud_text_mid_y(font, label, by + pressShift, bh),
                getTextColor(overlay));

        matrix.pop();
        return press;
    }

    private float getRound(KeystrokesOverlay overlay) {
        if (overlay.getStyle().isSelected("Pixel")) return 0.0f;
        if (overlay.getStyle().isSelected("Classic")) return 2.0f;
        return 5.0f;
    }

    private int getTextColor(KeystrokesOverlay overlay) {
        return overlay.getTextColor().getColor();
    }

    private void trimClicks(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.removeFirst();
        }
    }
}
