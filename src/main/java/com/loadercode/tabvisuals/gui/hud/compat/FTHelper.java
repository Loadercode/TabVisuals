// language: Java, file: FTHelper.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * FTHelper stand-in. The real module parses FunTime chat/events; for the port
 * the Binds element is fed from the vanilla hotbar instead — each non-empty
 * slot becomes an item pill bound to keys 1–9 (icon, count and cooldown
 * overlay all come from vanilla stacks).
 */
public final class FTHelper implements QuickImports {

    private static final FTHelper INSTANCE = new FTHelper();

    private FTHelper() {
    }

    public static FTHelper getInstance() {
        return INSTANCE;
    }

    /** Hotbar items 1–9 as binds; empty when there is no player. */
    public List<KeyBind> getKeyBindings() {
        if (mc.player == null) {
            return List.of();
        }
        List<KeyBind> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                out.add(new KeyBind(stack.getItem(),
                        new BindSetting("Слот " + (i + 1), GLFW.GLFW_KEY_1 + i), 0f));
            }
        }
        return out;
    }

    /** Match binds by item type only (no FunTime display-name parsing). */
    public boolean isCustomNameCheckEnabled() {
        return false;
    }

    public boolean isTrapActive() {
        return false;
    }

    /** FunTime trap countdown is not parsed — always 0 (status stays hidden). */
    public int getTrapSecondsRemaining() {
        return 0;
    }

    /** FunTime chat events are not parsed — always empty. */
    public List<ServerEvent> getServerEvents() {
        return List.of();
    }

    /** Item + key pill, mirroring the LoxeDLC record shape. */
    public record KeyBind(Item item, BindSetting setting, float distance) {
    }

    /** Chat-parsed FunTime event (kept minimal — always empty here). */
    public record ServerEvent(String name, long timeEnd) {
    }
}
