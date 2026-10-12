// language: Java, file: HotkeyManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Polls module binds on the client tick. Rising edge (press, not hold)
 * toggles the module and pushes a toast. State resets while any screen is
 * open so a key held while closing a menu does not fire on release.
 */
public final class HotkeyManager {

    private static final Map<String, Boolean> PREV_DOWN = new HashMap<>();

    private HotkeyManager() {
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null) {
            PREV_DOWN.clear();
            return;
        }
        long window = mc.getWindow().getHandle();
        for (ModuleStub m : ModuleRegistry.ALL) {
            int key = m.bind();
            if (key <= 0) {
                continue;
            }
            boolean down = InputUtil.isKeyPressed(window, key);
            Boolean prev = PREV_DOWN.put(m.id(), down);
            if (down && prev != null && !prev) {
                m.toggle();
                NotificationManager.moduleToggle(m.name(), m.enabled());
            }
        }
    }
}
