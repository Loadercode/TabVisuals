// language: Java, file: HudTheme.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.ColorSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
import com.loadercode.tabvisuals.gui.setting.ModeSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;

/**
 * Port of the LoxeDLC {@code ColorAssist} theme engine: one per-frame colour
 * cache built from the HUD module's theme settings (Темная / Светлая /
 * Кастомная + 14 editable slots), the glass-adjusted panel alphas and the
 * animated accent helpers. Call {@link #flushFrameCache()} once per HUD/GUI
 * frame before reading the accessors.
 */
public final class HudTheme {

    /**
     * Shared HUD rounding — LoxeDLC panel radius (round 4, 0.1 hairline),
     * used by the panels in HudRenderer and by the editor boxes here.
     */
    public static final float PANEL_ROUND = 4f;

    // built-in dark/light constants (LoxeDLC ColorAssist)
    private static final int DARK_TEXT = 0xFFFFFFFF;
    private static final int DARK_TEXT2 = 0xAFAFAFFF;
    private static final int DARK_OUTLINE_EL = 0x373746FF;   // (55,55,70,255)
    private static final int LIGHT_TEXT = 0x1E1E1EFF;
    private static final int LIGHT_TEXT2 = 0x646464FF;
    /** LoxeDLC GUI_DARK_BLUR / light counterpart — full-screen scrim @200. */
    private static final int DARK_SCRIM = 0xC8000000;
    private static final int LIGHT_SCRIM = 0xC8FFFFFF;
    /** LoxeDLC glass element outline — wins over the light/dark one. */
    private static final int GLASS_OUTLINE_EL = 0xB43C414B;   // (60,65,75,180)

    /** Per-frame cache — mirrors LoxeDLC's ensureCache(). */
    private static boolean valid;
    private static int cText = DARK_TEXT;
    private static int cText2 = DARK_TEXT2;
    private static int cBlur;
    private static int cRect;
    private static int cRectOutline;
    private static int[] cGradient = new int[4];
    private static int cRect1;
    private static int cRectDarker1;
    private static int cOutlineEl = DARK_OUTLINE_EL;

    private HudTheme() {
    }

    // ------------------------------------------------------------- settings

    private static ModuleStub hud() {
        return ModuleRegistry.byId("hud");
    }

    private static Setting find(String name) {
        ModuleStub m = hud();
        if (m == null) {
            return null;
        }
        for (Setting s : m.settings()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return null;
    }

    private static float num(String name, float def) {
        Setting s = find(name);
        return s instanceof DoubleSetting d ? (float) d.get() : def;
    }

    private static boolean bool(String name, boolean def) {
        Setting s = find(name);
        return s instanceof BooleanSetting b ? b.get() : def;
    }

    private static int color(String name, int def) {
        Setting s = find(name);
        return s instanceof ColorSetting c ? c.get() : def;
    }

    /** «Темная» / «Светлая» / «Кастомная». */
    public static String themeMode() {
        Setting s = find("Тема");
        return s instanceof ModeSetting md ? md.get() : "Темная";
    }

    public static boolean isLight() {
        return "Светлая".equals(themeMode());
    }

    public static boolean isCustom() {
        return "Кастомная".equals(themeMode());
    }

    /** Accent («Цвет клиента»). */
    public static int clientColor() {
        return color("Цвет клиента", 0xFF8B5CF6);
    }

    /** Glass opacity multiplier, 0.2..0.9 (LoxeDLC «Прозрачность стекла»). */
    public static float glassOpacity() {
        return num("Прозрачность стекла", 0.5f);
    }

    /** Global panel opacity slider (our addition; 1.0 = LoxeDLC default). */
    public static float panelOpacity() {
        return num("Panel opacity", 1.0f);
    }

    // ----------------------------------------------------------- frame cache

    public static void flushFrameCache() {
        valid = false;
    }

    private static void ensure() {
        if (valid) {
            return;
        }
        boolean glass = bool("Glass effect", false);
        boolean hudBlur = bool("Blur", true);
        boolean light = isLight();
        boolean custom = isCustom();
        float opacity = glass ? glassOpacity() : 1f;
        float global = clamp(panelOpacity(), 0.05f, 2f);

        int blurA = Math.min(glass ? (int) (100 * opacity) : (hudBlur ? 200 : 150), 255);
        int rectA = Math.min((int) ((glass ? 50 * opacity : 75) * global), 255);
        int outA = Math.min(glass ? (int) (25 * opacity) : 35, 255);

        if (custom) {
            cText = color("Текст темы", DARK_TEXT);
            cText2 = color("Вторичный текст", DARK_TEXT2);
            cBlur = multAlpha(color("Цвет блюра", 0x00000000 | blurA), opacity);
            cRect = multAlpha(color("Фон HUD 1", 0x12131400 | rectA), opacity);
            // LoxeDLC: alpha of the picked colour is multiplied by outA, not
            // replaced — a translucent custom outline keeps its own weight
            cRectOutline = multAlpha(color("Обводка", DARK_OUTLINE_EL), outA / 255f);
            int g1 = multAlpha(color("Фон HUD 1", 0x12131400 | rectA), opacity);
            int g2 = multAlpha(color("Фон HUD 2", 0x00020500 | rectA), opacity);
            cGradient = new int[]{g1, g2, g2, g1};
            cRect1 = color("Панель 1", 0x1A1A1FE4);
            cRectDarker1 = color("Панель 2", 0x18181EFF);
            cOutlineEl = color("Обводка", DARK_OUTLINE_EL);
        } else if (light) {
            cText = LIGHT_TEXT;
            cText2 = LIGHT_TEXT2;
            cBlur = pack(255, 255, 255, blurA);
            cRect = pack(255, 255, 255, rectA);
            cRectOutline = pack(224, 224, 224, outA);
            cGradient = new int[]{
                    pack(255, 255, 255, rectA), pack(245, 245, 250, rectA),
                    pack(245, 245, 250, rectA), pack(255, 255, 255, rectA)};
            cRect1 = pack(255, 255, 255, 232);
            cRectDarker1 = pack(240, 240, 240, 255);
            // LoxeDLC: glass outline outranks the light one
            cOutlineEl = glass ? GLASS_OUTLINE_EL : pack(224, 224, 224, 255);
        } else {
            cText = DARK_TEXT;
            cText2 = DARK_TEXT2;
            cBlur = pack(0, 0, 0, blurA);
            cRect = pack(18, 19, 20, rectA);
            cRectOutline = pack(18, 19, 20, outA);
            cGradient = new int[]{
                    pack(18, 19, 20, rectA), pack(0, 2, 5, rectA),
                    pack(0, 2, 5, rectA), pack(18, 19, 20, rectA)};
            cRect1 = pack(0, 0, 0, 228);
            cRectDarker1 = pack(0x18, 0x18, 0x1E, 255);
            cOutlineEl = glass ? GLASS_OUTLINE_EL : DARK_OUTLINE_EL;
        }
        valid = true;
    }

    // ------------------------------------------------------------ accessors

    public static int text() {
        ensure();
        return cText;
    }

    public static int text(float alpha) {
        return multAlpha(text(), alpha);
    }

    public static int text2() {
        ensure();
        return cText2;
    }

    /** Panel backdrop blur tint (LoxeDLC getBlurColor). */
    public static int blurColor() {
        ensure();
        return cBlur;
    }

    /** Plain panel fill (LoxeDLC getRectangleColor). */
    public static int rectColor() {
        ensure();
        return cRect;
    }

    /** Hairline outline of plain panels. */
    public static int rectOutline() {
        ensure();
        return cRectOutline;
    }

    /** 4-corner panel gradient in LoxeDLC corner order (c1..c4). */
    public static int[] gradient() {
        ensure();
        return cGradient;
    }

    /** Solid popup/card colour (LoxeDLC getRect). */
    public static int rect1() {
        ensure();
        return cRect1;
    }

    /** Darker solid colour (LoxeDLC getRectDarker). */
    public static int rectDarker(float alpha) {
        ensure();
        return multAlpha(cRectDarker1, alpha);
    }

    /** Element outline (LoxeDLC getOutlineColor). */
    public static int outline() {
        ensure();
        return cOutlineEl;
    }

    public static int outline(float alpha) {
        return multAlpha(outline(), alpha);
    }

    /**
     * Full-screen overlay scrim (LoxeDLC getClickGuiBlurColor): near-black @200
     * in the dark theme, white @200 in the light one, «Цвет блюра» in the
     * custom one. Accent colours are untouched — this is only the backdrop.
     */
    public static int scrimColor() {
        if (isCustom()) {
            return color("Цвет блюра", DARK_SCRIM);
        }
        return isLight() ? LIGHT_SCRIM : DARK_SCRIM;
    }

    public static int clientColor(float alpha) {
        return multAlpha(clientColor(), alpha);
    }

    // ------------------------------------------------------ animated accent

    /** Breathing accent between the client colour and white (LoxeDLC fade). */
    public static int fade(int indexMs) {
        long half = 800L;
        long phase = (System.currentTimeMillis() + indexMs) % (half * 2);
        float t = phase < half ? phase / (float) half : 1f - (phase - half) / (float) half;
        return mix(clientColor(), 0xFFFFFFFF, t);
    }

    /** Animated 4-corner accent for progress bars (LoxeDLC roundClientColor). */
    public static int[] roundClientColor(float alpha) {
        return new int[]{
                multAlpha(fade(270), alpha), multAlpha(fade(0), alpha),
                multAlpha(fade(180), alpha), multAlpha(fade(90), alpha)};
    }

    // -------------------------------------------------------------- helpers

    public static int multAlpha(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * clamp(factor, 0f, 1f));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int pack(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int withAlpha(int rgb, int a) {
        return (rgb & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    private static int mix(int a, int b, float k) {
        float t = clamp(k, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return pack((int) (ar + (br - ar) * t), (int) (ag + (bg - ag) * t),
                (int) (ab + (bb - ab) * t), (int) (aa + (ba - aa) * t));
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }
}
