// language: Java, file: TotemStats.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

import net.minecraft.entity.player.PlayerEntity;

/** TotemStats stand-in: pop tracking is not ported — always 0 (icon hidden). */
public final class TotemStats {

    private static final TotemStats INSTANCE = new TotemStats();

    private TotemStats() {
    }

    public static TotemStats getInstance() {
        return INSTANCE;
    }

    public boolean isState() {
        return false;
    }

    public int getPopCount(PlayerEntity player) {
        return 0;
    }
}
