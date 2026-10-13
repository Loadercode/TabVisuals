// language: Java, file: Hud.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import com.loadercode.tabvisuals.gui.hud.compat.SelectSetting;
import com.loadercode.tabvisuals.gui.hud.draggable.DraggableRepository;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;
import com.loadercode.tabvisuals.render.color.ColorAssist;

/**
 * Settings facade for the ported LoxeDLC HUD elements: their code reads
 * {@code Hud.getInstance().*}, values are pulled from our ModuleRegistry
 * (the "HUD" module) and pushed into {@link ColorAssist} each frame.
 */
public final class Hud {

    private static final Hud INSTANCE = new Hud();
    private static volatile DraggableRepository repository;

    /** Master switch of the HUD module (synced every frame by the driver). */
    public volatile boolean state = true;
    /** Per-element toggles; names match our labels ignoring case and spaces. */
    public final InterfaceSettings interfaceSettings = new InterfaceSettings();
    /** Armor layout: «Горизонтально» / «Вертикально» (right-click menu in Armor). */
    public final SelectSetting armorOrientation =
            new SelectSetting("Горизонтально", "Горизонтально", "Вертикально");

    /** EPRender module stand-in settings (LoxeDLC defaults; element-owned UX). */
    public final com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Slider epRenderSize =
            new com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Slider(40f);
    public final com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Bool epRenderShadow =
            new com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Bool(false);
    public final com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Slider epRenderYaw =
            new com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Slider(0f);
    public final com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Slider epRenderPitch =
            new com.loadercode.tabvisuals.gui.hud.compat.KeystrokesOverlay.Slider(0f);

    /** Staff List module stand-in settings (LoxeDLC Hud feature). */
    public volatile boolean staffShowOffline;
    public volatile boolean staffShowPrivileges = true;
    public volatile int staffMaxRows = 12;

    private volatile int blurQuality = 64;
    private volatile boolean drawHudBlur = true;

    private Hud() {
    }

    public static Hud getInstance() {
        return INSTANCE;
    }

    /** Lazily built element repository (LoxeDLC DraggableRepository port). */
    public static DraggableRepository getRepository() {
        DraggableRepository r = repository;
        if (r == null) {
            synchronized (Hud.class) {
                r = repository;
                if (r == null) {
                    r = new DraggableRepository();
                    r.setup();
                    repository = r;
                }
            }
        }
        return r;
    }

    public boolean isState() {
        return state;
    }

    public int getBlurQuality() {
        return blurQuality;
    }

    /** True when the blur/glass pipeline should sample behind panels. */
    public boolean shouldDrawHudBlur() {
        return drawHudBlur;
    }

    /** Called by the HUD driver once per frame before elements draw. */
    public void syncFromSettings() {
        state = HudRenderer.hudEnabled();
        boolean blur = HudRenderer.blurEnabled();
        boolean glass = HudRenderer.setting("Glass effect", false);
        drawHudBlur = blur || glass;
        blurQuality = drawHudBlur ? HudRenderer.blurSamples() : 6;

        // Staff List settings (LoxeDLC Hud staff* ports)
        staffShowOffline = HudRenderer.setting("Staff: показывать всех", false);
        staffShowPrivileges = HudRenderer.setting("Staff: привилегии", true);
        staffMaxRows = (int) HudRenderer.settingNum("Staff: максимум строк", 12);

        // Theme bridge: the full theme (dark/light/custom + accents) lives in
        // HudTheme; the LoxeDLC elements draw through ColorAssist — feed it.
        ColorAssist.lightTheme = HudTheme.isLight();
        ColorAssist.glassMode = glass;
        ColorAssist.glassOpacity = clamp01(HudTheme.glassOpacity());
        ColorAssist.hudBlurMode = blur;
        ColorAssist.setThemeOverride(HudTheme.isCustom(),
                HudTheme.text(), HudTheme.text2(), HudTheme.blurColor(),
                HudTheme.rectColor(), HudTheme.rectOutline(), HudTheme.gradient(),
                HudTheme.outline(), HudTheme.rect1(), HudTheme.rectDarker(1f),
                HudTheme.clientColor());
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1.6f);
    }

    /** LoxeDLC MultiSelectSetting replacement: normalized name lookup. */
    public static final class InterfaceSettings {
        public boolean isSelected(String elementName) {
            String key = normalize(elementName);
            ModuleStub m = ModuleRegistry.byId("hud");
            if (m != null) {
                for (Setting s : m.settings()) {
                    if (s instanceof BooleanSetting b && normalize(s.name()).equals(key)) {
                        return b.get();
                    }
                }
            }
            return true;
        }

        /** "Hot Keys" / "HotKeys" / "hotkeys" → "hotkeys". */
        private static String normalize(String s) {
            if (s == null) {
                return "";
            }
            return s.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
        }
    }
}
