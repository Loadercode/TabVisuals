// language: Java, file: Zoom.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.feature;

import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.KeySetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * LoxeDLC Zoom, ported 1:1 onto polling instead of the event bus: while the
 * bound key is held the camera FOV eases toward {@code base - 20}, the scroll
 * wheel adjusts the zoom level (and never switches hotbar slots), and mouse
 * look is slowed down proportionally to the current zoom. FOV is applied by
 * the GameRenderer#getFov mixin; scroll and slowdown by the Mouse mixin.
 */
public final class Zoom {

    /** Time constant of the exponential ease: 1 - e^(-16/16.3) ≈ 0.63 per 60 fps frame. */
    private static final float SMOOTH_TAU_MS = 16.3f;

    private static float fov = 30f;
    private static float smoothFov = 30f;
    private static float lastChangedFov = 30f;
    private static boolean prevDown;
    private static long lastNanos;

    /** Last applied FOV (for the mouse slowdown); -1 = not zooming. */
    private static volatile float lastFov = -1f;

    private Zoom() {
    }

    public static ModuleStub module() {
        return ModuleRegistry.byId("zoom");
    }

    public static boolean enabled() {
        ModuleStub m = module();
        return m != null && m.enabled();
    }

    private static int bind() {
        ModuleStub m = module();
        if (m == null || m.settings().isEmpty()) {
            return -1;
        }
        return m.settings().get(0) instanceof KeySetting k ? k.get() : -1;
    }

    /** Key is held, in-world, no screen open. */
    public static boolean held() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled() || mc.player == null || mc.currentScreen != null) {
            return false;
        }
        int key = bind();
        return key > 0 && GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    /**
     * FOV override — called by the GameRenderer#getFov mixin every frame.
     * Edges of the held key are detected here (LoxeDLC used KeyEvent).
     */
    public static int onFov(int original) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float base = mc.options.getFov().getValue();

        if (!enabled()) {
            fov = smoothFov = lastChangedFov = base;
            lastFov = -1f;
            prevDown = false;
            return original;
        }

        boolean down = held();
        if (down && !prevDown) {
            fov = Math.min(lastChangedFov, base - 20f);
        } else if (!down && prevDown) {
            lastChangedFov = fov;
            fov = base;
        } else if (!down) {
            // модуль включён с самого старта (конфиг) или base сменился в
            // настройках: без этой ветки цель оставалась бы на стартовых 30°
            // и игрок жил бы в постоянном зуме до первого нажатия клавиши
            fov = base;
        }
        prevDown = down;

        // a NaN from a broken frame delta must not stick in the state forever
        if (!Float.isFinite(smoothFov)) smoothFov = base;
        smoothFov = MathHelper.lerp(frameLerp(), smoothFov, fov);
        if (Math.abs(smoothFov - fov) < 0.01f) smoothFov = fov;
        int out = Math.round(MathHelper.clamp(smoothFov, 10f, base));
        lastFov = out;
        return out;
    }

    /**
     * Scroll while zoomed adjusts the zoom level instead of the hotbar slot.
     * Returns true when the scroll must be cancelled.
     */
    public static boolean onScroll(double vertical) {
        if (!held()) {
            return false;
        }
        float base = MinecraftClient.getInstance().options.getFov().getValue();
        fov = MathHelper.clamp(fov - (float) vertical * 10f, 10f, base);
        return true;
    }

    /** Mouse-look slowdown factor matching the current zoom (LoxeDLC FovEvent math). */
    public static double mouseSlowdown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float last = lastFov;
        if (last <= 0 || mc.player == null || !enabled()) {
            return 1.0;
        }
        int base = mc.options.getFov().getValue();
        return base > 0 ? last / (double) base : 1.0;
    }

    /**
     * Per-frame lerp factor, exponential in the real frame time so the ease
     * takes the same wall time at any FPS (the old linear ms/25.6 converged
     * faster per second at high FPS). Tuned to ~0.63 at 60 fps like LoxeDLC.
     */
    private static float frameLerp() {
        long now = System.nanoTime();
        float ms = lastNanos == 0 ? 16f : (now - lastNanos) / 1_000_000f;
        lastNanos = now;
        if (!(ms > 0f)) return 0f;          // same-frame second call / clock oddity
        if (ms > 250f) return 1f;           // long stall (menu, lag): just land on target
        return 1f - (float) Math.exp(-ms / SMOOTH_TAU_MS);
    }
}
