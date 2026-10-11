// language: Java, file: PlayerListHudMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
// *descriptor taken from yarn 1.21.4+build.8 — public render(DrawContext, int, Scoreboard, ScoreboardObjective)*
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.TabVisualsClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

	@Inject(
		method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
		at = @At("HEAD")
	)
	private void tabvisuals$onRender(DrawContext context, int maxLatency, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
		TabVisualsClient.onTabRender();
	}
}
