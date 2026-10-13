// language: Java, file: KeystrokesOverlay.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud.compat;

/**
 * Stand-in for the LoxeDLC KeystrokesOverlay module: the HUD reads its
 * settings, we expose the reference defaults (toggles live on the element).
 */
public final class KeystrokesOverlay {

    private static final KeystrokesOverlay INSTANCE = new KeystrokesOverlay();

    private final Slider keySize = new Slider(24f);
    private final Slider opacity = new Slider(0.82f);
    private final Slider borderThickness = new Slider(1.0f);
    private final Bool showMouse = new Bool(true);
    private final Bool showCps = new Bool(true);
    private final Bool showSpace = new Bool(true);
    private final Bool showSneak = new Bool(true);
    private final Bool showSprint = new Bool(true);
    private final SelectSetting style = new SelectSetting("Modern", "Modern", "Classic", "Pixel");
    private final ColorBox pressedColor = new ColorBox(95, 255, 135, 230);
    private final ColorBox backgroundColor = new ColorBox(15, 16, 18, 145);
    private final ColorBox textColor = new ColorBox(255, 255, 255, 240);
    private final ColorBox borderColor = new ColorBox(255, 255, 255, 45);

    private KeystrokesOverlay() {
    }

    public static KeystrokesOverlay getInstance() {
        return INSTANCE;
    }

    public boolean isState() {
        return true;
    }

    public Slider getKeySize() {
        return keySize;
    }

    public Slider getOpacity() {
        return opacity;
    }

    public Slider getBorderThickness() {
        return borderThickness;
    }

    public Bool getShowMouse() {
        return showMouse;
    }

    public Bool getShowCps() {
        return showCps;
    }

    public Bool getShowSpace() {
        return showSpace;
    }

    public Bool getShowSneak() {
        return showSneak;
    }

    public Bool getShowSprint() {
        return showSprint;
    }

    public SelectSetting getStyle() {
        return style;
    }

    public ColorBox getPressedColor() {
        return pressedColor;
    }

    public ColorBox getBackgroundColor() {
        return backgroundColor;
    }

    public ColorBox getTextColor() {
        return textColor;
    }

    public ColorBox getBorderColor() {
        return borderColor;
    }

    /** LoxeDLC slider setting: float value with an int view. */
    public static final class Slider {
        private final float value;

        public Slider(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }

        public int getInt() {
            return Math.round(value);
        }
    }

    /** LoxeDLC boolean setting. */
    public static final class Bool {
        private final boolean value;

        public Bool(boolean value) {
            this.value = value;
        }

        public boolean isValue() {
            return value;
        }
    }

    /** LoxeDLC color setting: ARGB int. */
    public static final class ColorBox {
        private final int argb;

        public ColorBox(int r, int g, int b, int a) {
            this.argb = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
        }

        public int getColor() {
            return argb;
        }
    }
}
