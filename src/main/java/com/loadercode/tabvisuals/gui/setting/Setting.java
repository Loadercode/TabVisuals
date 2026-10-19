// language: Java, file: Setting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.setting;

/** Base for clickable module settings. Values are plain, JSON-friendly. */
public abstract class Setting {
    private final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** Restores the constructor default (the settings popup's Reset button). */
    public void resetToDefault() {
    }
}
