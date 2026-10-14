// language: Java, file: Theme.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.render.color.ColorAssist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable palette for the GUI. All colors are ARGB ints. On top of the
 * built-in themes there is a "Custom" theme with five editable colour slots
 * (background / panel / card / accent / text); the remaining slots derive
 * from them so any combination stays coherent. Switching themes cross-fades
 * over ~340ms — {@link #get()} returns the interpolated palette mid-switch.
 * The active id and the custom colours are persisted via ConfigManager.
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
                    ColorAssist.pack(34, 211, 238, 255),
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

    /** Editable slots of the Custom theme, in picker order. */
    public static final String[] CUSTOM_SLOTS = {"background", "panel", "card", "accent", "text"};
    public static final String[] CUSTOM_SLOT_NAMES = {"Background", "Panel", "Card", "Accent", "Text"};

    /** Custom palette: slot → ARGB. Persisted; defaults follow the teal accent. */
    private static final Map<String, Integer> CUSTOM = new LinkedHashMap<>();

    static {
        CUSTOM.put("background", 0xFF0A1216);
        CUSTOM.put("panel", 0xFF0F1A20);
        CUSTOM.put("card", 0xFF16242C);
        CUSTOM.put("accent", 0xFF8B5CF6);
        CUSTOM.put("text", 0xFFF0F7FA);
    }

    private static final long ANIM_MS = 340L;

    private static volatile Theme active = ALL[0];
    private static volatile Theme customTheme = buildCustom();
    private static Theme animFrom;
    private static long animStart;
    private static Theme animCached;
    private static long animCachedAt = -1L;

    /** Active palette — the interpolated one while a switch is animating. */
    public static Theme get() {
        Theme from = animFrom;
        if (from == null) {
            return active;
        }
        long now = System.currentTimeMillis();
        if (now == animCachedAt && animCached != null) {
            return animCached;
        }
        float p = (now - animStart) / (float) ANIM_MS;
        if (p >= 1f) {
            animFrom = null;
            animCached = active;
            animCachedAt = now;
            return active;
        }
        float e = p * p * (3f - 2f * p); // smoothstep
        Theme lerped = lerp(from, active, e, active.id());
        animCached = lerped;
        animCachedAt = now;
        return lerped;
    }

    /** Starts a cross-fade to the given palette. */
    public static void set(Theme theme) {
        Theme next = theme != null ? theme : ALL[0];
        if (next == active) {
            return; // same palette — nothing to animate
        }
        animFrom = get();
        active = next;
        animStart = System.currentTimeMillis();
        animCached = null;
        animCachedAt = -1L;
    }

    public static Theme byId(String id) {
        if ("custom".equals(id)) {
            return customTheme;
        }
        for (Theme t : ALL) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return ALL[0];
    }

    /** Built-ins plus the current Custom palette. */
    public static List<Theme> all() {
        List<Theme> out = new ArrayList<>(ALL.length + 1);
        for (Theme t : ALL) {
            out.add(t);
        }
        out.add(customTheme);
        return out;
    }

    // ------------------------------------------------------- custom palette

    /** Snapshot of the editable slots for persistence. */
    public static Map<String, Integer> customColors() {
        return new LinkedHashMap<>(CUSTOM);
    }

    /** Loads persisted custom colours (missing slots keep their defaults). */
    public static void loadCustom(Map<String, Integer> in) {
        if (in == null) {
            return;
        }
        for (Map.Entry<String, Integer> e : in.entrySet()) {
            if (isValidSlot(e.getKey()) && e.getValue() != null) {
                CUSTOM.put(e.getKey(), e.getValue());
            }
        }
        refreshCustom();
    }

    /** Applies an edited slot live; the Custom palette follows immediately. */
    public static void setCustom(String slot, int argb) {
        if (!isValidSlot(slot)) {
            return;
        }
        CUSTOM.put(slot, argb);
        refreshCustom();
    }

    /** Current value of an editable slot (never null). */
    public static int customSlot(String slot) {
        Integer v = CUSTOM.get(slot);
        return v != null ? v : 0xFFFFFFFF;
    }

    private static boolean isValidSlot(String slot) {
        for (String s : CUSTOM_SLOTS) {
            if (s.equals(slot)) {
                return true;
            }
        }
        return false;
    }

    /** Rebuilds the Custom palette; live while the theme is already active. */
    private static void refreshCustom() {
        customTheme = buildCustom();
        if ("custom".equals(active.id())) {
            active = customTheme; // instant follow — the picker drags continuously
        }
    }

    /** Derives the secondary slots from the five editable ones. */
    private static Theme buildCustom() {
        int bg = CUSTOM.getOrDefault("background", 0xFF0A1216);
        int panel = CUSTOM.getOrDefault("panel", 0xFF0F1A20);
        int card = CUSTOM.getOrDefault("card", 0xFF16242C);
        int accent = CUSTOM.getOrDefault("accent", 0xFF8B5CF6);
        int text = CUSTOM.getOrDefault("text", 0xFFF0F7FA);
        return new Theme("custom", "Custom",
                bg,
                panel,
                card,
                mixRgb(card, 0xFFFFFF, 0.09f),
                accent,
                text,
                mixRgb(text, panel, 0.45f),
                withHairlineAlpha(mixRgb(panel, 0xFFFFFF, 0.16f)),
                mixRgb(card, 0x000000, 0.34f));
    }

    private static int withHairlineAlpha(int rgb) {
        return rgb | (42 << 24); // hairline outline @ ~16%
    }

    // ------------------------------------------------------ accent gradient

    /**
     * 4-corner gradient of the active accent, in the corner order of
     * {@link UiRender#gradient}: tl, bl, br, tr. Shades are the accent lifted
     * toward white (same derivation as the outline slot) — no new palette
     * entries, alpha 255.
     */
    public static int[] accentGradient() {
        return accentGradient(255);
    }

    /** Same with an explicit alpha for every corner (0..255). */
    public static int[] accentGradient(int alpha) {
        int a = get().accent();
        int lo = mixRgb(a, 0xFFFFFF, 0.18f);
        int hi = mixRgb(a, 0xFFFFFF, 0.36f);
        return new int[]{withAlphaRgb(a, alpha), withAlphaRgb(lo, alpha),
                withAlphaRgb(hi, alpha), withAlphaRgb(lo, alpha)};
    }

    private static int withAlphaRgb(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    // ------------------------------------------------------------- lerping

    private static Theme lerp(Theme a, Theme b, float k, String id) {
        return new Theme(id, b.name(),
                lerpColor(a.background(), b.background(), k),
                lerpColor(a.panel(), b.panel(), k),
                lerpColor(a.card(), b.card(), k),
                lerpColor(a.cardHover(), b.cardHover(), k),
                lerpColor(a.accent(), b.accent(), k),
                lerpColor(a.text(), b.text(), k),
                lerpColor(a.textSecondary(), b.textSecondary(), k),
                lerpColor(a.outline(), b.outline(), k),
                lerpColor(a.switchOff(), b.switchOff(), k));
    }

    private static int lerpColor(int a, int b, float k) {
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int outA = (int) (aa + (ba - aa) * k);
        int outR = (int) (ar + (br - ar) * k);
        int outG = (int) (ag + (bg - ag) * k);
        int outB = (int) (ab + (bb - ab) * k);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int mixRgb(int a, int b, float k) {
        int ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * k);
        int g = (int) (ag + (bg - ag) * k);
        int bl = (int) (ab + (bb - ab) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
