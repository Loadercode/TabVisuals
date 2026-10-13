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

    /** LoxeDLC lerp speed: frame duration divided by 1.6. */
    private static final float SMOOTH = 16f * 1.6f;

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
        }
        prevDown = down;

        smoothFov = MathHelper.lerp(frameLerp(), smoothFov, fov);
        int out = (int) MathHelper.clamp(smoothFov + 1f, 10f, base);
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
     * Per-frame lerp factor: LoxeDLC divided the last tick duration by 1.6
     * (~0.63 at 60 fps); we measure the real frame time the same way.
     */
    private static float frameLerp() {
        long now = System.nanoTime();
        float ms = lastNanos == 0 ? 16f : (now - lastNanos) / 1_000_000f;
        lastNanos = now;
        return MathHelper.clamp(ms / SMOOTH, 0f, 1f);
    }
}
