// language: Java, file: HolyWorldModuleGuard.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

import com.loadercode.tabvisuals.util.Network;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * HolyWorld guard stand-in (LoxeDLC port): server-flag detection for the HUD
 * elements that only make sense on that anarchy. Detection = the Network
 * server name or the raw address host, cached per tick so the HUD hot path
 * never re-parses.
 */
public final class HolyWorldModuleGuard {

    private static int flagsTick = Integer.MIN_VALUE;
    private static boolean holyWorldCached;

    private HolyWorldModuleGuard() {
    }

    public static boolean isHolyWorldServer() {
        int tick = currentTick();
        if (tick != flagsTick) {
            flagsTick = tick;
            holyWorldCached = computeHolyWorld();
        }
        return holyWorldCached;
    }

    public static boolean isBlockedOnCurrentServer(Object module) {
        return false;
    }

    private static boolean computeHolyWorld() {
        if ("HolyWorld".equalsIgnoreCase(Network.getServer())) {
            return true;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null
                || mc.getNetworkHandler().getServerInfo() == null) {
            return false;
        }
        return matchesHolyWorldHost(mc.getNetworkHandler().getServerInfo().address);
    }

    /** Host match: holyworld.ru, any subdomain, or "holyworld" anywhere in the host. */
    public static boolean matchesHolyWorldHost(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        String host = address.toLowerCase(Locale.ROOT).split(":")[0].trim();
        return host.equals("holyworld.ru") || host.endsWith(".holyworld.ru") || host.contains("holyworld");
    }

    /** In-world: the world time; outside: a coarse millisecond bucket (coalesces UI calls). */
    private static int currentTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.world != null) {
            return (int) mc.world.getTime();
        }
        return -(((int) (System.nanoTime() >>> 20)) | 0x40000000);
    }
}
