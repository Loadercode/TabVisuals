// language: Java, file: ModuleRegistry.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.module;

import com.loadercode.tabvisuals.gui.Category;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
import com.loadercode.tabvisuals.gui.setting.ModeSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Dummy module list per category. Pure UI placeholders — no game logic attached. */
public final class ModuleRegistry {

    public static final List<ModuleStub> ALL = new ArrayList<>();

    static {
        // --- Combat ---
        ALL.add(new ModuleStub("killaura", "KillAura", "Attacks nearby targets automatically", Category.COMBAT, false)
                .bind(GLFW.GLFW_KEY_R)
                .with(new DoubleSetting("Range", 4.2, 1.0, 6.0, 0.1, "m"),
                        new DoubleSetting("CPS", 10, 1, 20, 1, ""),
                        new ModeSetting("Target priority", 0, "Distance", "Health", "Angle")));
        ALL.add(new ModuleStub("velocity", "Velocity", "Modifies incoming knockback", Category.COMBAT, true)
                .with(new DoubleSetting("Horizontal", 85, 0, 100, 1, "%"),
                        new DoubleSetting("Vertical", 100, 0, 100, 1, "%")));
        ALL.add(new ModuleStub("autoclicker", "AutoClicker", "Held-click automation", Category.COMBAT, false)
                .with(new DoubleSetting("Min CPS", 8, 1, 20, 1, ""),
                        new DoubleSetting("Max CPS", 12, 1, 20, 1, ""),
                        new BooleanSetting("Only weapons", true)));
        ALL.add(new ModuleStub("hitbox", "HitBox", "Expands target hitboxes", Category.COMBAT, false)
                .with(new DoubleSetting("Expand", 0.2, 0.0, 1.0, 0.05, "")));
        ALL.add(new ModuleStub("criticals", "Criticals", "Forces critical hit packets", Category.COMBAT, false)
                .with(new ModeSetting("Mode", 0, "Packet", "Jump", "MiniJump")));
        ALL.add(new ModuleStub("reach", "Reach", "Increases attack distance", Category.COMBAT, false)
                .with(new DoubleSetting("Distance", 3.5, 3.0, 6.0, 0.1, "m")));
        ALL.add(new ModuleStub("trigger", "TriggerBot", "Attacks whatever you look at", Category.COMBAT, false)
                .with(new DoubleSetting("Delay", 50, 0, 500, 10, "ms"),
                        new BooleanSetting("Players only", true)));

        // --- Player ---
        ALL.add(new ModuleStub("nofall", "NoFall", "Negates fall damage", Category.PLAYER, true)
                .with(new ModeSetting("Mode", 0, "Packet", "Spoof", "Clutch")));
        ALL.add(new ModuleStub("autotool", "AutoTool", "Picks the best tool for the block", Category.PLAYER, false)
                .with(new BooleanSetting("Swap back", true)));
        ALL.add(new ModuleStub("fastplace", "FastPlace", "Removes placement delay", Category.PLAYER, false)
                .with(new DoubleSetting("Delay", 0, 0, 4, 1, "t")));
        ALL.add(new ModuleStub("cheststealer", "ChestStealer", "Loots containers automatically", Category.PLAYER, false)
                .with(new DoubleSetting("Delay", 120, 0, 500, 10, "ms"),
                        new BooleanSetting("Auto close", true)));
        ALL.add(new ModuleStub("invmove", "InventoryMove", "Move while screens are open", Category.PLAYER, false));
        ALL.add(new ModuleStub("antiafk", "AntiAFK", "Prevents idle kicks", Category.PLAYER, false)
                .with(new ModeSetting("Style", 1, "Jump", "Rotate", "Walk")));
        ALL.add(new ModuleStub("blink", "Blink", "Queues movement packets", Category.PLAYER, false)
                .with(new DoubleSetting("Limit", 20, 1, 100, 1, "s")));

        // --- Movement ---
        ALL.add(new ModuleStub("speed", "Speed", "Movement acceleration boost", Category.MOVEMENT, false)
                .bind(GLFW.GLFW_KEY_V)
                .with(new ModeSetting("Mode", 0, "Strafe", "BHop", "OnGround"),
                        new DoubleSetting("Multiplier", 1.6, 1.0, 3.0, 0.1, "x")));
        ALL.add(new ModuleStub("flight", "Flight", "Air movement control", Category.MOVEMENT, false)
                .bind(GLFW.GLFW_KEY_F)
                .with(new ModeSetting("Mode", 0, "Creative", "Vanilla", "Glide"),
                        new DoubleSetting("Speed", 1.0, 0.2, 5.0, 0.1, "x")));
        ALL.add(new ModuleStub("longjump", "LongJump", "Extended jump distance", Category.MOVEMENT, false)
                .with(new DoubleSetting("Boost", 2.0, 1.0, 5.0, 0.1, "x")));
        ALL.add(new ModuleStub("step", "Step", "Walk up full blocks", Category.MOVEMENT, true)
                .with(new DoubleSetting("Height", 1.0, 0.5, 2.5, 0.5, "b")));
        ALL.add(new ModuleStub("sprint", "Sprint", "Keeps sprint active", Category.MOVEMENT, true)
                .with(new BooleanSetting("Omnidirectional", false)));
        ALL.add(new ModuleStub("noslow", "NoSlow", "No slowdown while using items", Category.MOVEMENT, false)
                .with(new BooleanSetting("Blocking", true),
                        new BooleanSetting("Eating", true)));
        ALL.add(new ModuleStub("parkour", "Parkour", "Auto jumps at edges", Category.MOVEMENT, false));

        // --- Render ---
        ALL.add(new ModuleStub("esp", "ESP", "Highlights entities through walls", Category.RENDER, true)
                .bind(GLFW.GLFW_KEY_H)
                .with(new ModeSetting("Style", 0, "Box", "Glow", "Corners"),
                        new BooleanSetting("Health bar", true),
                        new DoubleSetting("Distance", 64, 8, 256, 8, "m")));
        ALL.add(new ModuleStub("tracers", "Tracers", "Lines to nearby entities", Category.RENDER, false)
                .with(new DoubleSetting("Width", 1.5, 0.5, 4.0, 0.5, "")));
        ALL.add(new ModuleStub("fullbright", "FullBright", "Maximum world brightness", Category.RENDER, true)
                .bind(GLFW.GLFW_KEY_B)
                .with(new ModeSetting("Mode", 0, "Gamma", "Night vision")));
        ALL.add(new ModuleStub("nametags", "NameTags", "Custom name plates", Category.RENDER, false)
                .with(new DoubleSetting("Scale", 1.0, 0.5, 2.0, 0.1, "x"),
                        new BooleanSetting("Armor", true)));
        ALL.add(new ModuleStub("chams", "Chams", "Colored models through walls", Category.RENDER, false));
        ALL.add(new ModuleStub("itemesp", "ItemESP", "Drops glow through walls", Category.RENDER, false));
        ALL.add(new ModuleStub("animations", "Animations", "Custom swing animations", Category.RENDER, false)
                .with(new ModeSetting("Preset", 1, "Vanilla", "Compact", "Slide")));

        // --- Misc ---
        ALL.add(new ModuleStub("autogg", "AutoGG", "Sends gg after a win", Category.MISC, false)
                .with(new DoubleSetting("Delay", 800, 0, 3000, 100, "ms")));
        ALL.add(new ModuleStub("spammer", "Spammer", "Repeats chat lines", Category.MISC, false)
                .with(new DoubleSetting("Interval", 5, 1, 60, 1, "s")));
        ALL.add(new ModuleStub("antibot", "AntiBot", "Filters fake players", Category.MISC, true)
                .with(new BooleanSetting("Tab check", true)));
        ALL.add(new ModuleStub("middleclick", "MiddleClickFriend", "Friend toggle on middle click", Category.MISC, false));
        ALL.add(new ModuleStub("timer", "Timer", "Changes client tick speed", Category.MISC, false)
                .with(new DoubleSetting("Speed", 1.0, 0.5, 3.0, 0.1, "x")));
        ALL.add(new ModuleStub("notes", "Notes", "In-game scratch pad", Category.MISC, false));
        ALL.add(new ModuleStub("streamermode", "StreamerMode", "Hides nick and server info while streaming", Category.MISC, true)
                .bind(GLFW.GLFW_KEY_END));
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
