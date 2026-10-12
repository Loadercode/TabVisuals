// language: Java, file: ConfigManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.Theme;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
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
                } else {
                    continue;
                }
                ms.settings.put(s.name(), v);
            }
            state.modules.put(m.id(), ms);
        }
        return state;
    }

    public static void apply(GuiState state) {
        if (state == null) {
            return;
        }
        if (state.themeId != null) {
            Theme.set(Theme.byId(state.themeId));
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

    public static void saveConfig(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return;
        }
        write(CONFIGS_DIR.resolve(clean + ".json"), snapshot());
    }

    public static void loadConfig(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return;
        }
        Path file = CONFIGS_DIR.resolve(clean + ".json");
        if (Files.exists(file)) {
            apply(read(file));
        }
    }

    public static void deleteConfig(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return;
        }
        try {
            Files.deleteIfExists(CONFIGS_DIR.resolve(clean + ".json"));
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("Config delete failed", e);
        }
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.trim().replaceAll("[^a-zA-Z0-9_\\- ]", "");
    }

    private static void write(Path file, GuiState state) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("Config save failed: {}", file, e);
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
