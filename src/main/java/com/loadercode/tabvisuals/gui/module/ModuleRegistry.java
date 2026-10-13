// language: Java, file: ModuleRegistry.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.module;

import com.loadercode.tabvisuals.gui.Category;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.ColorSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
import com.loadercode.tabvisuals.gui.setting.KeySetting;
import com.loadercode.tabvisuals.gui.setting.ModeSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Real module list — every entry drives actual rendering or the title-screen
 * media flow; nothing here is a placeholder. Categories without modules are
 * hidden from the GUI tabs automatically.
 */
public final class ModuleRegistry {

    public static final List<ModuleStub> ALL = new ArrayList<>();

    static {
        // --- Render: HUD panels, toasts and the in-chat position editor ---
        // Full LoxeDLC Hud feature: theme system (Темная/Светлая/Кастомная
        // with 14 editable colour slots), blur/glass pipeline and the whole
        // element set (Staff List included, as a draggable).
        ALL.add(new ModuleStub("hud", "HUD",
                "LoxeDLC-style elements, theme and the in-chat position editor", Category.RENDER, true)
                .with(new BooleanSetting("Watermark", true),
                        new BooleanSetting("Inventory", true),
                        new BooleanSetting("HotKeys", true),
                        new BooleanSetting("Potions", true),
                        new BooleanSetting("Target Hud", true),
                        new BooleanSetting("Binds", true),
                        new BooleanSetting("Cool Downs", true),
                        new BooleanSetting("Notifications", true),
                        new BooleanSetting("Player Info", true),
                        new BooleanSetting("Scoreboard", false),
                        new BooleanSetting("Media Player", true),
                        new BooleanSetting("Armor", true),
                        new BooleanSetting("HotBar", false),
                        new BooleanSetting("Saturation", true),
                        new BooleanSetting("Totem Counter", true),
                        new BooleanSetting("Keystrokes", true),
                        new BooleanSetting("EPRender", false),
                        new BooleanSetting("Dynamic Island", true),
                        new BooleanSetting("Staff list", true),
                        new BooleanSetting("Staff: показывать всех", false),
                        new BooleanSetting("Staff: привилегии", true),
                        new DoubleSetting("Staff: максимум строк", 12, 4, 40, 1, ""),
                        new BooleanSetting("Interactive editor", true),
                        new BooleanSetting("Magnetic snapping", true),
                        // --- theme system (LoxeDLC Hud) ---
                        new ModeSetting("Тема", 0, "Темная", "Светлая", "Кастомная"),
                        new ColorSetting("Цвет клиента", 0xFF22D3EE)
                                .presets(0xFF22D3EE, 0xFF6C9AFD, 0xFF8C7FFF, 0xFFFFA576, 0xFFFF7B7B),
                        new ColorSetting("Текст темы", 0xFFFFFFFF),
                        new ColorSetting("Вторичный текст", 0xFFAFAFAF),
                        new ColorSetting("Фон HUD 1", 0x4B121314),
                        new ColorSetting("Фон HUD 2", 0x4B000205),
                        new ColorSetting("Панель 1", 0xE41A1A1F),
                        new ColorSetting("Панель 2", 0xFF18181E),
                        new ColorSetting("Цвет блюра", 0xC8000000),
                        new ColorSetting("Обводка", 0xFF373746),
                        new ColorSetting("Вкладка (выбрана)", 0x873D3D3D),
                        new ColorSetting("Вкладка (наведение)", 0x64555564),
                        new ColorSetting("Скроллбар (фон)", 0x4B232323),
                        new ColorSetting("Скроллбар (ползунок)", 0x5F656565),
                        // --- blur / glass pipeline ---
                        new BooleanSetting("Blur", true),
                        new ModeSetting("Blur quality", 2, "Low", "Medium", "High"),
                        new BooleanSetting("Glass effect", false),
                        new DoubleSetting("Прозрачность стекла", 0.5, 0.2, 0.9, 0.05, ""),
                        new BooleanSetting("Shadow", true),
                        new DoubleSetting("Panel opacity", 1.0, 0.30, 1.60, 0.05, "x")));

        // --- Misc: title-screen now playing (SMTC + lrclib lyrics + Discord) ---
        ALL.add(new ModuleStub("media", "Media",
                "Now playing on the title screen: lyrics, cover, profile", Category.MISC, true)
                .with(new BooleanSetting("Splash lyrics", true),
                        new BooleanSetting("Menu lyrics", true),
                        new BooleanSetting("Cover glow", true),
                        new BooleanSetting("Animated lyrics", true),
                        new BooleanSetting("Music cover", true)));

        // --- Render: LoxeDLC camera features (hold-key modules) ---
        ALL.add(new ModuleStub("zoom", "Zoom",
                "Smooth camera zoom while the key is held; scroll adjusts the zoom level", Category.RENDER, false)
                .with(new KeySetting("Зум", GLFW.GLFW_KEY_C)));
        ALL.add(new ModuleStub("freelook", "FreeLook",
                "Look around with the mouse without turning the player (third-person camera)", Category.RENDER, false)
                .with(new KeySetting("Свободный обзор", GLFW.GLFW_KEY_V)));
    }

    public static List<ModuleStub> byCategory(Category category) {
        List<ModuleStub> out = new ArrayList<>();
        for (ModuleStub m : ALL) {
            if (m.category() == category) {
                out.add(m);
            }
        }
        return out;
    }

    public static ModuleStub byId(String id) {
        for (ModuleStub m : ALL) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }
}
