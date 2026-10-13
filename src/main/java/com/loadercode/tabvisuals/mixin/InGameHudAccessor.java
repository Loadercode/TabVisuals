// language: Java, file: InGameHudAccessor.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the vanilla overlay state the ported HotBar element redraws. */
@Mixin(InGameHud.class)
public interface InGameHudAccessor {

    @Accessor
    int getHeldItemTooltipFade();

    @Accessor
    ItemStack getCurrentStack();

    @Accessor
    int getOverlayRemaining();

    @Accessor
    Text getOverlayMessage();
}
