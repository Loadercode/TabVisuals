// language: Java, file: InGameHudMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.gui.hud.Hud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla HUD pieces our LoxeDLC elements replace — same guards as
 * the LoxeDLC InGameHudMixin: cancel only while the HUD module and the
 * matching interface element are on.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void renderHotbarHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hide("HotBar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void renderStatusEffectOverlayHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hide("Potions")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void renderScoreboardSidebarHook(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (hide("Scoreboard")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void renderOverlayMessageHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hide("HotBar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void renderExperienceLevelHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (hide("HotBar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void renderExperienceBarHook(DrawContext context, int x, CallbackInfo ci) {
        if (hide("HotBar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void renderHeldItemTooltipHook(DrawContext context, CallbackInfo ci) {
        if (hide("HotBar")) {
            ci.cancel();
        }
    }

    private static boolean hide(String element) {
        Hud hud = Hud.getInstance();
        return hud != null && hud.isState() && hud.interfaceSettings.isSelected(element);
    }
}
