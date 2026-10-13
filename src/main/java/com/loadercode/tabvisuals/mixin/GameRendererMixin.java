// language: Java, file: GameRendererMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.feature.FreeLook;
import com.loadercode.tabvisuals.feature.Zoom;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * LoxeDLC GameRendererMixin FovEvent part: the FOV read out of the options is
 * rewritten by {@link Zoom} while it is easing toward the zoomed value.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @ModifyExpressionValue(method = "getFov",
            at = @At(value = "INVOKE", target = "Ljava/lang/Integer;intValue()I", remap = false))
    private int tabvisuals$zoomFov(int original) {
        // second per-frame driver: restores the perspective when a screen
        // opens while FreeLook's key is held (updateMouse stops running there)
        FreeLook.tickFrame();
        return Zoom.onFov(original);
    }
}
