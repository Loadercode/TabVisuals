// language: Java, file: NameProtect.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

/** NameProtect stand-in: coordinate hiding is off. */
public final class NameProtect {

    private static final NameProtect INSTANCE = new NameProtect();

    private NameProtect() {
    }

    public static NameProtect getInstance() {
        return INSTANCE;
    }

    public boolean isHideCoords() {
        return false;
    }
}
