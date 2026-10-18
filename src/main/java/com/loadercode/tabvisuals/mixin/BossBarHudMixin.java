// language: Java, file: BossBarHudMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.elements.island.DynamicIsland;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla boss bar only while the island status replaces its
 * content (PVP, teleport etc.); an unrecognized bar keeps drawing and the
 * island just shifts below it — the LoxeDLC rule.
 */
@Mixin(BossBarHud.class)
public class BossBarHudMixin {

    // Full descriptor: a bare "render" would silently bind to any overload
    // added later (and PlayerListHudMixin already spells its descriptor out).
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void renderHook(CallbackInfo ci) {
        Hud hud = Hud.getInstance();
        if (hud == null || !hud.isState() || !hud.interfaceSettings.isSelected("Dynamic Island")) {
            return;
        }
        DynamicIsland island = DynamicIsland.getInstance();
        if (island != null && island.shouldHideVanillaBossBar()) {
            ci.cancel();
        }
    }
}
