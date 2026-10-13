// language: Java, file: Draggable.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.draggable;

import net.minecraft.client.gui.DrawContext;

/** LoxeDLC Draggable — events stripped, positions owned by our HUD editor. */
public interface Draggable {
    boolean visible();

    void tick();

    void render(DrawContext context, int mouseX, int mouseY, float delta);

    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseReleased(double mouseX, double mouseY, int button);
}
