// language: Java, file: StaffManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Collects staff members from the tab list. A player counts as staff when
 * the (colour-stripped) name matches {@link #STAFF_NAMES} exactly or the
 * display name starts with one of {@link #STAFF_PREFIXES}. Vanished staff
 * are entries currently in spectator mode. Edit the two sets per server.
 */
public final class StaffManager {

    /** Exact nicknames (lowercase) always treated as staff. */
    private static final Set<String> STAFF_NAMES = Set.of(
            "mrdomer", "sendersa", "loshok224");

    /** Display-name prefixes (lowercase) that mark staff, matched from start. */
    private static final List<String> STAFF_PREFIXES = List.of(
            "[staff]", "[admin]", "[mod]", "[мод]", "[админ]", "[глава]");

    /** name as shown, vanished = spectator right now. */
    public record Entry(String name, boolean vanished) {
    }

    private StaffManager() {
    }

    public static List<Entry> collect(MinecraftClient mc) {
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        for (PlayerListEntry e : net.getPlayerList()) {
            Text display = e.getDisplayName();
            String raw = display != null ? display.getString() : e.getProfile().getName();
            String clean = raw.replaceAll("§.", "").trim();
            String lower = clean.toLowerCase(Locale.ROOT);

            String name = null;
            if (STAFF_NAMES.contains(lower)) {
                name = clean;
            } else {
                for (String prefix : STAFF_PREFIXES) {
                    if (lower.startsWith(prefix)) {
                        name = clean.substring(prefix.length()).trim();
                        break;
                    }
                }
            }
            if (name == null || name.isEmpty()) {
                continue;
            }
            boolean vanished = e.getGameMode() == GameMode.SPECTATOR;
            out.add(new Entry(name, vanished));
        }
        // visible staff first, vanished to the bottom, then alphabetically
        out.sort(Comparator.comparing(Entry::vanished).thenComparing(Entry::name));
        return out;
    }
}
