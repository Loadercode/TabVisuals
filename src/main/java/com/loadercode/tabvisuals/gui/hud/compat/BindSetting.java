// language: Java, file: BindSetting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

/** LoxeDLC BindSetting stand-in: a display name plus a GLFW key. */
public final class BindSetting {

    private final String name;
    private final int key;

    public BindSetting(String name, int key) {
        this.name = name;
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public int getKey() {
        return key;
    }

    public boolean isVisible() {
        return true;
    }
}
