// language: Java, file: ScreenBackgroundMixin.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import com.loadercode.tabvisuals.gui.ClickGuiScreen;
import com.loadercode.tabvisuals.gui.title.MenuBackground;
import com.loadercode.tabvisuals.gui.title.TabVisualsTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the violet-white TabVisuals backdrop behind vanilla menu screens while
 * no world is loaded. Screen#renderBackground (DrawContext, int, int, float)
 * is the single vanilla backdrop path every menu screen funnels through (it
 * draws panorama + menu blur + dirt darkening), so cancelling it and drawing
 * MenuBackground instead swaps the whole backdrop — no per-screen mixins.
 * Screens that override renderBackground with their own look (PopupScreen
 * confirmations, MessageScreen, FatalErrorScreen…) are left untouched: they
 * never reach the vanilla path we cancel, so they keep their vanilla look.
 * Our own screens draw their own look and are skipped. Runs menu-time only
 * (mc.world == null); in-game pause/chat/inventory keep vanilla behaviour.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {

    /** Backdrop fade-in from black when it starts being used again (ms). */
    @Unique
    private static final long TABVISUALS$FADE_MS = 250L;
    /** Screen the backdrop was last drawn for — fade restarts on change from idle. */
    @Unique
    private static Screen TABVISUALS$lastScreen;
    /** Fade start of the current backdrop run; -1 = idle (our screen / in-game). */
    @Unique
    private static long TABVISUALS$fadeStart = -1L;
    /** Single-entry class cache for the future gui-package screen name check. */
    @Unique
    private static Class<?> TABVISUALS$checkedClass;
    @Unique
    private static boolean TABVISUALS$checkedOurs;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At("HEAD"), cancellable = true)
    private void tabvisuals$replaceMenuBackdrop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null || tabvisuals$isOurs(self)) {
            // in-game or one of our screens — vanilla look, backdrop idle
            TABVISUALS$lastScreen = null;
            TABVISUALS$fadeStart = -1L;
            return;
        }

        long now = System.currentTimeMillis();
        if (self != TABVISUALS$lastScreen) {
            if (TABVISUALS$fadeStart < 0L) {
                TABVISUALS$fadeStart = now; // backdrop was idle — fade it in
            }
            // between two vanilla menus the backdrop never stopped being used:
            // no re-dip, the run's fade stays at full alpha
            TABVISUALS$lastScreen = self;
        }
        float t = Math.min(1f, (now - TABVISUALS$fadeStart) / (float) TABVISUALS$FADE_MS);
        float alpha = t * t * (3f - 2f * t); // smoothstep — the switch is not a hard cut

        // flush geometry batched into the context before us, then our quad
        // goes straight to the framebuffer (bypasses the context queue)
        context.draw();
        MenuBackground.render(context.getMatrices(), self.width, self.height, alpha, now);
        ci.cancel(); // panorama + menu blur + dirt darkening are gone
    }

    /** Our title screen, the ClickGUI and any future screen under gui. */
    @Unique
    private static boolean tabvisuals$isOurs(Screen screen) {
        if (screen instanceof TabVisualsTitleScreen || screen instanceof ClickGuiScreen) {
            return true;
        }
        // package check cached per class — no per-frame string work
        Class<?> cls = screen.getClass();
        if (cls != TABVISUALS$checkedClass) {
            TABVISUALS$checkedClass = cls;
            TABVISUALS$checkedOurs = cls.getName().startsWith("com.loadercode.tabvisuals.gui.");
        }
        return TABVISUALS$checkedOurs;
    }
}
