// language: Java, file: ModeSetting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.setting;

/** Dropdown row. Click opens the option list; value is an index into options. */
public final class ModeSetting extends Setting {
    private final String[] options;
    private int index;

    public ModeSetting(String name, int index, String... options) {
        super(name);
        this.options = options != null && options.length > 0 ? options : new String[]{"-"};
        this.index = Math.max(0, Math.min(this.options.length - 1, index));
    }

    public String get() {
        return options[index];
    }

    public int index() {
        return index;
    }

    public void setIndex(int i) {
        this.index = Math.max(0, Math.min(options.length - 1, i));
    }

    /** Sets by option name (old configs stored names); unknown → first option. */
    public void set_by_name(String name) {
        if (name != null) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equalsIgnoreCase(name)) {
                    this.index = i;
                    return;
                }
            }
        }
        this.index = 0;
    }

    public String[] options() {
        return options;
    }
}
