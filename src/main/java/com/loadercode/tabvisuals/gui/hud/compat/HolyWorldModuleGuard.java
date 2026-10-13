// language: Java, file: HolyWorldModuleGuard.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

/** HolyWorld guard stand-in: no server-side module blocking on our side. */
public final class HolyWorldModuleGuard {

    private HolyWorldModuleGuard() {
    }

    public static boolean isHolyWorldServer() {
        return false;
    }

    public static boolean isBlockedOnCurrentServer(Object module) {
        return false;
    }
}
