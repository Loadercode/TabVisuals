// language: Java, file: CameraMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.feature.FreeLook;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LoxeDLC CameraMixin updateHook: while FreeLook holds a detached angle the
 * camera rotation is overwritten with it and pulled back by the vanilla
 * third-person clip distance — the rest of the vanilla update (rotation from
 * the player entity) is skipped.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    public abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void moveBy(float x, float y, float z);

    @Shadow
    protected abstract float clipToSpace(float desiredDistance);

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V", shift = At.Shift.AFTER),
            cancellable = true)
    private void tabvisuals$freeLook(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                     boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!FreeLook.cameraActive()) {
            return;
        }
        if (!(focusedEntity instanceof ClientPlayerEntity player) || player.isSleeping() || !thirdPerson) {
            return;
        }
        float camPitch = inverseView ? -FreeLook.cameraPitch() : FreeLook.cameraPitch();
        float camYaw = FreeLook.cameraYaw() - (inverseView ? 180 : 0);
        setRotation(camYaw, camPitch);
        moveBy(-clipToSpace(4.0F), 0.0F, 0.0F);
        ci.cancel();
    }
}
