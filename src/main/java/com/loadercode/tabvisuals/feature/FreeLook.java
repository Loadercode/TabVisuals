// language: Java, file: FreeLook.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.feature;

import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.KeySetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * LoxeDLC FreeLook, ported 1:1 onto polling instead of the event bus: while
 * the bound key is held the camera detaches into a third-person back view
 * with its own yaw/pitch fed by mouse deltas (the player's look direction is
 * left untouched); releasing the key restores the original perspective.
 * Hooks: Mouse#updateMouse (frame + look deltas), Camera#update (rotation).
 */
public final class FreeLook {

    private static Perspective saved;
    private static boolean angleSet;
    private static float yaw;
    private static float pitch;

    private FreeLook() {
    }

    public static ModuleStub module() {
        return ModuleRegistry.byId("freelook");
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
     * Per-frame state machine (Mouse#updateMouse HEAD): while held — remember
     * the original perspective and force the free-look third person; on
     * release — restore. Runs even when the module is toggled off with the
     * key still held, so the view can never get stuck (LoxeDLC deactivate()).
     */
    public static void tickFrame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            // disconnect/world change with the key held — no perspective to restore to
            saved = null;
            angleSet = false;
            return;
        }
        if (held()) {
            if (saved == null) {
                saved = mc.options.getPerspective();
            }
            if (!angleSet) {
                yaw = mc.player.getYaw();
                pitch = mc.player.getPitch();
                angleSet = true;
            }
            if (mc.options.getPerspective().isFirstPerson()) {
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
        } else if (saved != null) {
            mc.options.setPerspective(saved);
            saved = null;
            angleSet = false;
        }
    }

    /**
     * Look-delta sink (Mouse#updateMouse changeLookDirection wrap): while the
     * key is held the deltas steer the detached camera and never reach the
     * player. Returns true when vanilla rotation must be skipped.
     */
    public static boolean onLookDelta(double dx, double dy) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!held() || mc.player == null) {
            return false;
        }
        if (!angleSet) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            angleSet = true;
        }
        // LoxeDLC: cursor deltas scaled by 0.15 into the detached angle
        yaw += (float) dx * 0.15F;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15F, -90F, 90F);
        return true;
    }

    /** Detached camera angle is live and must be applied by Camera#update. */
    public static boolean cameraActive() {
        return held() && angleSet;
    }

    public static float cameraYaw() {
        return yaw;
    }

    public static float cameraPitch() {
        return pitch;
    }
}
