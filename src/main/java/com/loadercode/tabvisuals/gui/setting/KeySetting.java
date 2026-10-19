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
    private final int def;

    public KeySetting(String name, int defaultKey) {
        super(name);
        this.key = defaultKey;
        this.def = defaultKey;
    }

    public int get() {
        return key;
    }

    @Override
    public void resetToDefault() {
        this.key = def;
    }

    public void set(int key) {
        // диапазон GLFW: mouse buttons 0..7 и keys 0..GLFW_KEY_LAST; всё
        // остальное из старого конфига → unbound, а не «KEY99999» в HUD
        this.key = (key == GLFW.GLFW_KEY_UNKNOWN
                || (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_KEY_LAST))
                ? key : GLFW.GLFW_KEY_UNKNOWN;
    }

    /** Label for the GUI row and the HotKeys HUD; em-dash when unbound. */
    public String label() {
        return key <= 0 ? "—" : keyLabel(key);
    }

    /** Human-readable GLFW key label — the single source of truth for bind
     *  naming (ClickGUI chips, HotKeys/Binds HUD rows all delegate here). */
    public static String keyLabel(int key) {
        if (key <= 0) {
            return "—";
        }
        // GLFW mouse buttons occupy 0..7
        if (key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            return "MOUSE " + (key + 1);
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
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
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
