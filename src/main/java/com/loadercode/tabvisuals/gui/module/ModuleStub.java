// language: Java, file: ModuleStub.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.module;

import com.loadercode.tabvisuals.gui.Category;
import com.loadercode.tabvisuals.gui.setting.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** UI-side module placeholder: name, description, toggle state and settings. Does nothing on its own. */
public final class ModuleStub {
    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;
    private boolean favorite;
    /** GLFW key code bound to this module; GLFW_KEY_UNKNOWN (-1) = unbound. */
    private int bind = -1;

    public ModuleStub(String id, String name, String description, Category category, boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabled;
    }

    public ModuleStub with(Setting... s) {
        for (Setting setting : s) {
            settings.add(setting);
        }
        return this;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public List<Setting> settings() {
        return settings;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
    }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    public boolean favorite() {
        return favorite;
    }

    public void setFavorite(boolean v) {
        this.favorite = v;
    }

    public int bind() {
        return bind;
    }

    public ModuleStub bind(int glfwKey) {
        this.bind = glfwKey;
        return this;
    }

    /** Human-readable key label for the HotKeys HUD; null when unbound. */
    public String bindName() {
        if (bind <= 0) {
            return null;
        }
        return switch (bind) {
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            case GLFW.GLFW_KEY_INSERT -> "INS";
            case GLFW.GLFW_KEY_DELETE -> "DEL";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE -> "BKSP";
            default -> {
                if (bind >= GLFW.GLFW_KEY_A && bind <= GLFW.GLFW_KEY_Z) {
                    yield String.valueOf((char) ('A' + bind - GLFW.GLFW_KEY_A));
                }
                if (bind >= GLFW.GLFW_KEY_0 && bind <= GLFW.GLFW_KEY_9) {
                    yield String.valueOf((char) ('0' + bind - GLFW.GLFW_KEY_0));
                }
                if (bind >= GLFW.GLFW_KEY_F1 && bind <= GLFW.GLFW_KEY_F12) {
                    yield "F" + (bind - GLFW.GLFW_KEY_F1 + 1);
                }
                yield "KEY" + bind;
            }
        };
    }
}
