// language: Java, file: MouseMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.feature.FreeLook;
import com.loadercode.tabvisuals.feature.Zoom;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LoxeDLC MouseMixin split into the two camera features: Zoom slows the look
 * deltas by the current zoom ratio and hijacks the hotbar scroll while held;
 * FreeLook runs its perspective state machine each frame and intercepts
 * changeLookDirection to steer the detached camera instead of the player.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow
    private double cursorDeltaX, cursorDeltaY;

    /** Zoom slowdown + FreeLook perspective machine, once per frame. */
    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void tabvisuals$frame(double timeDelta, CallbackInfo ci) {
        FreeLook.tickFrame();

        double slowdown = Zoom.mouseSlowdown();
        if (slowdown != 1.0) {
            this.cursorDeltaX *= slowdown;
            this.cursorDeltaY *= slowdown;
        }
    }

    /** FreeLook: while the key is held the deltas never reach the player. */
    @WrapWithCondition(method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"),
            require = 1, allow = 1)
    private boolean tabvisuals$freeLookRotation(ClientPlayerEntity instance, double cursorDeltaX, double cursorDeltaY) {
        return !FreeLook.onLookDelta(cursorDeltaX, cursorDeltaY);
    }

    /** Zoom: scroll adjusts the zoom level instead of the hotbar slot. */
    @Inject(method = "onMouseScroll",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getInventory()Lnet/minecraft/entity/player/PlayerInventory;"),
            cancellable = true)
    private void tabvisuals$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Zoom.onScroll(vertical)) {
            ci.cancel();
        }
    }
}
