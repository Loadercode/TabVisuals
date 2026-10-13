// language: Java, file: ColorSetting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.setting;

/**
 * ARGB colour setting (LoxeDLC ColorSetting): the value plus optional
 * preset swatches shown in the picker. Persisted as a plain int.
 */
public final class ColorSetting extends Setting {

    private final int def;
    private int value;
    private int[] presets = {};

    public ColorSetting(String name, int def) {
        super(name);
        this.def = def;
        this.value = def;
    }

    public ColorSetting presets(int... colors) {
        this.presets = colors != null ? colors : new int[0];
        return this;
    }

    public int get() {
        return value;
    }

    public void set(int argb) {
        this.value = argb;
    }

    public void reset() {
        this.value = def;
    }

    public int[] presetList() {
        return presets;
    }
}
