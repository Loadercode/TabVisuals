// language: Java, file: Instance.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import net.minecraft.client.MinecraftClient;

/** LoxeDLC Instance helper: draggable lookups go to our repository. */
public final class Instance {

    private Instance() {
    }

    public static <T extends AbstractDraggable> T getDraggable(Class<T> clazz) {
        return Hud.getRepository().get(clazz);
    }

    public static <T extends AbstractDraggable> T getDraggable(String name) {
        return Hud.getRepository().get(name);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> clazz) {
        if (clazz == KeystrokesOverlay.class) {
            return (T) KeystrokesOverlay.getInstance();
        }
        return null;
    }

    public static <T> T get(String module) {
        return null;
    }

    public static MinecraftClient getMinecraft() {
        return MinecraftClient.getInstance();
    }
}
