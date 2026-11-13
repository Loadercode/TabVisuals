// language: Java, file: MenuSection.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title.section;

import com.loadercode.tabvisuals.gui.title.MenuFrame;

/**
 * One page of the main menu's content area (right of the nav column).
 * Everything is in canvas units. A section draws ONLY inside the rect it is
 * given and records its own hit rects from what it actually drew; the host
 * screen handles the header (title/subtitle), page transitions and input
 * routing. Input methods return true when the event was consumed.
 */
public interface MenuSection {

    String title();

    String subtitle();

    /** Called when the page becomes the active one (reset scroll, start staggers). */
    default void onShow() {
    }

    default void onHide() {
    }

    /**
     * @param alpha page fade 0..1 — multiply every colour's alpha by it
     */
    void render(MenuFrame f, float x, float y, float w, float h, float alpha);

    /** Optional header controls, right-aligned at {@code rightX}, centred on {@code cy}. */
    default void renderHeader(MenuFrame f, float rightX, float cy, float alpha) {
    }

    /** Optional full-canvas layer above everything (dialogs). */
    default void renderOverlay(MenuFrame f, float alpha) {
    }

    /** True while a dialog is open: the host then blocks its own nav input. */
    default boolean modalOpen() {
        return false;
    }

    default boolean mouseClicked(float mx, float my, int button) {
        return false;
    }

    default boolean mouseReleased(float mx, float my, int button) {
        return false;
    }

    default boolean mouseDragged(float mx, float my, int button) {
        return false;
    }

    default boolean mouseScrolled(float mx, float my, float amount) {
        return false;
    }

    default boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    default boolean charTyped(char c, int mods) {
        return false;
    }
}
