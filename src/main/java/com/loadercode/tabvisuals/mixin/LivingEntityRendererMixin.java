// language: Java, file: LivingEntityRendererMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.TabVisualsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * hasLabel (LivingEntityRenderer:64) безусловно зовёт
 * client.player.getScoreboardTeam(), а на титульном экране (до входа в мир)
 * client.player == null → NPE внутри рендера 3D-превью скина в модалке
 * «Аккаунты». Хук возвращает false (без ника) при null client.player — до
 * team-логики, поэтому не трогает рендер самой модели и не мешает живому
 * игроку в мире. Класс generic, поэтому remap = false (имена методов Yarn
 * уже захардкожены ниже).
 */
@Mixin(value = LivingEntityRenderer.class, remap = false)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void tabvisuals$noLabelWithoutClientPlayer(CallbackInfoReturnable<Boolean> cir) {
        if (MinecraftClient.getInstance().player == null) {
            cir.setReturnValue(false);
        }
    }
}
