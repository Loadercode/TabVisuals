// language: Java, file: ConfigManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.Theme;
import com.loadercode.tabvisuals.gui.hud.HudEditor;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.ColorSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
import com.loadercode.tabvisuals.gui.setting.KeySetting;
import com.loadercode.tabvisuals.gui.setting.ModeSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON persistence: autosave (loaded on startup, written on GUI close)
 * plus named configs managed from the Configs tab.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("config").resolve("tabvisuals");
    private static final Path CONFIGS_DIR = DIR.resolve("configs");
    private static final Path AUTOSAVE = DIR.resolve("autosave.json");

    /** Serialized GUI state. Public fields for Gson. */
    public static final class GuiState {
        public String themeId = Theme.ALL[0].id();
        public Map<String, ModuleState> modules = new HashMap<>();
        /** HUD element positions from the in-chat editor: id → {x, y} (toasts: {yOffset}). */
        public Map<String, float[]> hud = new HashMap<>();
        /** Custom theme colours: slot name → ARGB int. */
        public Map<String, Integer> customTheme = new HashMap<>();
    }

    public static final class ModuleState {
        public boolean enabled;
        public boolean favorite;
        public Map<String, Object> settings = new HashMap<>();
    }

    private ConfigManager() {
    }

    public static GuiState snapshot() {
        GuiState state = new GuiState();
        state.themeId = Theme.get().id();
        for (ModuleStub m : ModuleRegistry.ALL) {
            ModuleState ms = new ModuleState();
            ms.enabled = m.enabled();
            ms.favorite = m.favorite();
            for (Setting s : m.settings()) {
                Object v;
                if (s instanceof BooleanSetting b) {
                    v = b.get();
                } else if (s instanceof DoubleSetting d) {
                    v = d.get();
                } else if (s instanceof ModeSetting md) {
                    v = md.index();
                } else if (s instanceof ColorSetting cs) {
                    v = cs.get();
                } else if (s instanceof KeySetting ks) {
                    v = ks.get();
                } else {
                    continue;
                }
                ms.settings.put(s.name(), v);
            }
            state.modules.put(m.id(), ms);
        }
        state.hud.putAll(HudEditor.positions());
        state.customTheme.putAll(Theme.customColors());
        return state;
    }

    public static void apply(GuiState state) {
        if (state == null) {
            return;
        }
        // custom colours first: the theme id below may point at the Custom slot
        if (state.customTheme != null) {
            Theme.loadCustom(state.customTheme);
        }
        if (state.themeId != null) {
            Theme.set(Theme.byId(state.themeId));
        }
        if (state.hud != null) {
            HudEditor.loadPositions(state.hud);
        }
        if (state.modules == null) {
            return;
        }
        for (Map.Entry<String, ModuleState> e : state.modules.entrySet()) {
            ModuleStub m = ModuleRegistry.byId(e.getKey());
            if (m == null || e.getValue() == null) {
                continue;
            }
            ModuleState ms = e.getValue();
            m.setEnabled(ms.enabled);
            m.setFavorite(ms.favorite);
            if (ms.settings == null) {
                continue;
            }
            for (Setting s : m.settings()) {
                Object v = ms.settings.get(s.name());
                if (v == null) {
                    continue;
                }
                if (s instanceof BooleanSetting b && v instanceof Boolean bv) {
                    b.set(bv);
                } else if (s instanceof DoubleSetting d && v instanceof Number n) {
                    d.set(n.doubleValue());
                } else if (s instanceof ModeSetting md && v instanceof Number n) {
                    md.setIndex(n.intValue());
                } else if (s instanceof ColorSetting cs && v instanceof Number n) {
                    cs.set(n.intValue());
                } else if (s instanceof KeySetting ks && v instanceof Number n) {
                    ks.set(n.intValue());
                }
            }
        }
    }

    public static void autosave() {
        write(AUTOSAVE, snapshot());
    }

    public static void loadAutosave() {
        if (Files.exists(AUTOSAVE)) {
            apply(read(AUTOSAVE));
        }
    }

    public static List<String> listConfigs() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(CONFIGS_DIR)) {
            return names;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CONFIGS_DIR, "*.json")) {
            for (Path p : stream) {
                String fn = p.getFileName().toString();
                names.add(fn.substring(0, fn.length() - 5));
            }
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("Config list failed", e);
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    public static boolean saveConfig(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return false;
        }
        return write(CONFIGS_DIR.resolve(clean + ".json"), snapshot());
    }

    /** true when the file existed and parsed — callers use it to report honestly. */
    public static boolean loadConfig(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return false;
        }
        Path file = CONFIGS_DIR.resolve(clean + ".json");
        if (!Files.exists(file)) {
            return false;
        }
        GuiState state = read(file);
        if (state == null) {
            return false;
        }
        apply(state);
        return true;
    }

    public static boolean deleteConfig(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return false;
        }
        try {
            return Files.deleteIfExists(CONFIGS_DIR.resolve(clean + ".json"));
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("Config delete failed", e);
            return false;
        }
    }

    /**
     * Keeps unicode (cyrillic config names), strips only path-hostile
     * characters, trims leading/trailing dots and spaces (Windows treats
     * "foo." as "foo"), caps length so the filename stays sane.
     */
    private static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("^[.\\s]+|[.\\s]+$", "");
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private static boolean write(Path file, GuiState state) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(state), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("Config save failed: {}", file, e);
            return false;
        }
    }

    private static GuiState read(Path file) {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GuiState.class);
        } catch (IOException | RuntimeException e) {
            // RuntimeException: Gson throws JsonSyntaxException on malformed JSON —
            // uncaught it would crash startup (autosave) or the Configs tab
            TabVisualsClient.LOGGER.error("Config load failed: {}", file, e);
            return null;
        }
    }
}
