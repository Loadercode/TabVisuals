// language: Java, file: KeySetting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.setting;

import org.lwjgl.glfw.GLFW;

/**
 * Hold-key setting for feature modules (LoxeDLC BindSetting role): a GLFW key
 * code with a readable label; -1 = unbound. The ClickGUI captures the next
 * key press when the row is clicked.
 */
public class KeySetting extends Setting {

    /** GLFW key code; GLFW_KEY_UNKNOWN (-1) = unbound. */
    private int key;

    public KeySetting(String name, int defaultKey) {
        super(name);
        this.key = defaultKey;
    }

    public int get() {
        return key;
    }

    public void set(int key) {
        this.key = key;
    }

    /** Label for the GUI row and the HotKeys HUD; em-dash when unbound. */
    public String label() {
        return key <= 0 ? "—" : keyLabel(key);
    }

    /** Human-readable GLFW key label; mirrors the ModuleStub list. */
    public static String keyLabel(int key) {
        if (key <= 0) {
            return "—";
        }
        return switch (key) {
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
            case GLFW.GLFW_KEY_KP_ENTER -> "NUMENTER";
            case GLFW.GLFW_KEY_BACKSPACE -> "BKSP";
            default -> {
                if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                    yield String.valueOf((char) ('A' + key - GLFW.GLFW_KEY_A));
                }
                if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
                    yield String.valueOf((char) ('0' + key - GLFW.GLFW_KEY_0));
                }
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
                    yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                }
                yield "KEY" + key;
            }
        };
    }
}
