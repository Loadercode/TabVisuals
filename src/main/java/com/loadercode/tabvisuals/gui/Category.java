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

    /** categoryicons glyph, LoxeDLC-faithful: A=swords, D=person, B=arrows, C=image, E=box, H=clipboard. */
    public char icon() {
        return switch (this) {
            case COMBAT -> 'A';
            case PLAYER -> 'D';
            case MOVEMENT -> 'B';
            case RENDER -> 'C';
            case MISC -> 'E';
            case CONFIGS -> 'H';
        };
    }
}
