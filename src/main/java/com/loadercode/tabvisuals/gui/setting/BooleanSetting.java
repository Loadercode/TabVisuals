// language: Java, file: BooleanSetting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.setting;

/** Checkbox row. */
public final class BooleanSetting extends Setting {
    private final boolean def;
    private boolean value;

    public BooleanSetting(String name, boolean value) {
        super(name);
        this.def = value;
        this.value = value;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean v) {
        this.value = v;
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public void resetToDefault() {
        this.value = def;
    }
}
