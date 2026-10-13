// language: Java, file: BossBarHudAccessor.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** BossBarHud.bossBars is private in Yarn — the island statuses read it.
 *  1.21.4 stores ClientBossBar (wraps ServerBossBar); ServerBossBar here
 *  caused a ClassCastException on every iteration. */
@Mixin(BossBarHud.class)
public interface BossBarHudAccessor {

    @Accessor("bossBars")
    Map<UUID, ClientBossBar> getBossBars();
}
