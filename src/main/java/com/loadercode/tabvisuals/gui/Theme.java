// language: Java, file: Theme.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.render.color.ColorAssist;

/**
 * Immutable palette for the click GUI. All colors are ARGB ints.
 * Built-in themes only for now; the active id is persisted via ConfigManager.
 */
public record Theme(String id, String name,
                    int background, int panel, int card, int cardHover,
                    int accent, int text, int textSecondary, int outline, int switchOff) {

    public static final Theme[] ALL = {
            new Theme("midnight", "Midnight",
                    ColorAssist.pack(8, 8, 12, 215),
                    ColorAssist.pack(19, 19, 25, 255),
                    ColorAssist.pack(27, 27, 35, 255),
                    ColorAssist.pack(34, 34, 44, 255),
                    ColorAssist.pack(255, 46, 136, 255),
                    ColorAssist.pack(255, 255, 255, 255),
                    ColorAssist.pack(158, 158, 170, 255),
                    ColorAssist.pack(46, 46, 60, 255),
                    ColorAssist.pack(60, 60, 76, 255)),
            new Theme("crimson", "Crimson",
                    ColorAssist.pack(10, 7, 8, 215),
                    ColorAssist.pack(24, 16, 18, 255),
                    ColorAssist.pack(33, 22, 24, 255),
                    ColorAssist.pack(42, 28, 31, 255),
                    ColorAssist.pack(255, 64, 80, 255),
                    ColorAssist.pack(255, 255, 255, 255),
                    ColorAssist.pack(170, 150, 152, 255),
                    ColorAssist.pack(64, 42, 46, 255),
                    ColorAssist.pack(78, 50, 55, 255)),
            new Theme("ocean", "Ocean",
                    ColorAssist.pack(6, 10, 14, 215),
                    ColorAssist.pack(14, 20, 27, 255),
                    ColorAssist.pack(20, 28, 37, 255),
                    ColorAssist.pack(26, 36, 47, 255),
                    ColorAssist.pack(64, 190, 255, 255),
                    ColorAssist.pack(255, 255, 255, 255),
                    ColorAssist.pack(150, 168, 180, 255),
                    ColorAssist.pack(40, 56, 70, 255),
                    ColorAssist.pack(50, 70, 88, 255)),
            new Theme("toxic", "Toxic",
                    ColorAssist.pack(8, 12, 8, 215),
                    ColorAssist.pack(17, 23, 17, 255),
                    ColorAssist.pack(24, 32, 24, 255),
                    ColorAssist.pack(31, 42, 31, 255),
                    ColorAssist.pack(140, 255, 90, 255),
                    ColorAssist.pack(255, 255, 255, 255),
                    ColorAssist.pack(156, 176, 150, 255),
                    ColorAssist.pack(48, 64, 46, 255),
                    ColorAssist.pack(60, 82, 56, 255)),
            new Theme("paper", "Paper",
                    ColorAssist.pack(232, 232, 238, 210),
                    ColorAssist.pack(250, 250, 252, 255),
                    ColorAssist.pack(255, 255, 255, 255),
                    ColorAssist.pack(242, 242, 247, 255),
                    ColorAssist.pack(230, 55, 130, 255),
                    ColorAssist.pack(30, 30, 36, 255),
                    ColorAssist.pack(115, 115, 128, 255),
                    ColorAssist.pack(222, 222, 230, 255),
                    ColorAssist.pack(202, 202, 212, 255))
    };

    private static volatile Theme active = ALL[0];

    public static Theme get() {
        return active;
    }

    public static void set(Theme theme) {
        active = theme != null ? theme : ALL[0];
    }

    public static Theme byId(String id) {
        for (Theme t : ALL) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return ALL[0];
    }
}
