/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.interfaces;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


public final class QuickLoggerHelper {

    public static Text getPrefix() {
        MutableText text = Text.literal("TabVisuals");
        text.setStyle(text.getStyle().withColor(Formatting.GRAY));
        text.append(" -> ");
        return text;
    }

    public static void logDirect(Text... components) {
        MutableText component = Text.literal("");
        component.append(getPrefix());
        component.append(Text.literal(" "));
        for (Text t : components) {
            component.append(t);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.inGameHud.getChatHud().addMessage(component);
    }

    public static void logDirect(String message, Formatting color) {
        String normalized = message.replace("\t", "    ");
        MutableText component = Text.literal(normalized);
        component.setStyle(component.getStyle().withColor(color));
        logDirect(component);
    }
}
