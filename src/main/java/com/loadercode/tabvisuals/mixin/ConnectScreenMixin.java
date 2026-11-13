// language: Java, file: ConnectScreenMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.gui.title.ConnectOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restyles the vanilla "connecting" screen ({@link ConnectOverlay}). The
 * vanilla cancel button stays in the widget tree (hidden), so Esc / the
 * "Esc отмена" hint press it and the exact vanilla cancel path runs.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Shadow
    private Text status;

    protected ConnectScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"))
    private static void tabvisuals$begin(Screen parent, MinecraftClient client, ServerAddress address, ServerInfo info,
                                         boolean quickPlay, CookieStorage cookies, CallbackInfo ci) {
        ConnectOverlay.begin(address, info);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tabvisuals$hideButtons(CallbackInfo ci) {
        for (Element e : this.children()) {
            if (e instanceof ButtonWidget b) {
                b.visible = false;
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tabvisuals$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ConnectOverlay.render(context, this.width, this.height, mouseX, mouseY, this.status);
        ci.cancel();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            tabvisuals$cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ConnectOverlay.escHit(mouseX, mouseY)) {
            tabvisuals$cancel();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @org.spongepowered.asm.mixin.Unique
    private void tabvisuals$cancel() {
        for (Element e : this.children()) {
            if (e instanceof ButtonWidget b) {
                b.onPress();
                return;
            }
        }
    }
}
