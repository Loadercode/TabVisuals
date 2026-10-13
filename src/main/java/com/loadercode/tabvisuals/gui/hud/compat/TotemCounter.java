// language: Java, file: TotemCounter.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

/** TotemCounter module stand-in — the element toggle is our gate. */
public final class TotemCounter {

    private static final TotemCounter INSTANCE = new TotemCounter();

    private TotemCounter() {
    }

    public static TotemCounter getInstance() {
        return INSTANCE;
    }

    public boolean isState() {
        return true;
    }
}
