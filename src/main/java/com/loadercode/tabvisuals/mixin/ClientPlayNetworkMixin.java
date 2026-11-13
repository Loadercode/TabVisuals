// language: Java, file: ClientPlayNetworkMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.gui.hud.elements.CoolDowns;
import com.loadercode.tabvisuals.util.Network;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CooldownUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two packet feeds LoxeDLC took from its event bus: server TPS
 * (WorldTimeUpdateS2CPacket) and vanilla item cooldowns for the CoolDowns
 * element (CooldownUpdateS2CPacket).
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkMixin {

    @Inject(method = "onWorldTimeUpdate(Lnet/minecraft/network/packet/s2c/play/WorldTimeUpdateS2CPacket;)V", at = @At("HEAD"))
    private void onWorldTimeUpdateHook(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        if (!onMainThread()) return;
        Network.onTimeUpdate();
    }

    @Inject(method = "onCooldownUpdate(Lnet/minecraft/network/packet/s2c/play/CooldownUpdateS2CPacket;)V", at = @At("HEAD"))
    private void onCooldownUpdateHook(CooldownUpdateS2CPacket packet, CallbackInfo ci) {
        if (!onMainThread()) return;
        CoolDowns.getInstance().onCooldownUpdate(packet.cooldownGroup(), packet.cooldown());
    }

    /** New world: drop the previous server's TPS/countdown state. */
    @Inject(method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V", at = @At("HEAD"))
    private void onGameJoinHook(GameJoinS2CPacket packet, CallbackInfo ci) {
        if (!onMainThread()) return;
        Network.reset();
    }

    /**
     * HEAD runs twice per packet: first on the Netty thread (where
     * forceMainThread throws to re-queue the packet), then on the client
     * thread. Only the second pass counts — the first one halved every TPS
     * interval (TPS flickered to 20) and touched HUD state off-thread.
     */
    private static boolean onMainThread() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.isOnThread();
    }
}
