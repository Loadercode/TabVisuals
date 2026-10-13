// language: Java, file: SelectSetting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

/** LoxeDLC SelectSetting stand-in: a fixed list of values, one selected. */
public final class SelectSetting {

    private final String[] values;
    private String selected;

    public SelectSetting(String def, String... values) {
        this.values = values;
        this.selected = def;
    }

    public boolean isSelected(String value) {
        return selected.equals(value);
    }

    public void setSelected(String value) {
        selected = value;
    }

    public String getSelected() {
        return selected;
    }

    public String[] getValues() {
        return values;
    }
}
