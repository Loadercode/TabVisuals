// language: Java, file: Category.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

/** Tabs of the click GUI. CONFIGS is a separate tab by design. */
public enum Category {
    COMBAT("Бой"),
    PLAYER("Игрок"),
    MOVEMENT("Движение"),
    RENDER("Рендер"),
    MISC("Прочее"),
    CONFIGS("Конфиги");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Single safe letter for the tab icon (no exotic glyphs, always in font coverage). */
    public char icon() {
        return displayName.charAt(0);
    }
}
