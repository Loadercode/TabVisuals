// language: Java, file: GameRendererMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.feature.FreeLook;
import com.loadercode.tabvisuals.feature.Zoom;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LoxeDLC GameRendererMixin, two hooks: the FOV read out of the options is
 * rewritten by {@link Zoom} while it is easing toward the zoomed value, and
 * the scissor stack is reset at the start of every frame (a swallowed
 * exception inside a scissored HUD element would otherwise leave the whole
 * render clipped until restart).
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    /** LoxeDLC loxedlc$resetScissor: clean scissor leaks once per frame. */
    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void tabvisuals$resetScissor(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ScissorAssist.INSTANCE.endFrame();
    }

    // getFov has exactly one Integer.intValue (the options FOV read at bytecode
    // offset 36); the second intValue in GameRenderer lives in render(). Pin the
    // ordinal so a future MC update adding another Integer read can't silently
    // rebind this hook to the wrong value.
    @ModifyExpressionValue(method = "getFov",
            at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I", ordinal = 0, remap = false))
    private int tabvisuals$zoomFov(int original) {
        // second per-frame driver: restores the perspective when a screen
        // opens while FreeLook's key is held (updateMouse stops running there)
        FreeLook.tickFrame();
        return Zoom.onFov(original);
    }
}
