// language: Java, file: ConfigManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JSON persistence: autosave (loaded on startup, written on GUI close)
 * plus named configs managed from the Configs tab.
 *
 * <p>Writes are atomic (temp file + rename) and keep a .bak of the last good
 * file; parsing is tolerant entry-by-entry so a corrupt or hand-edited file
 * never crashes the game nor wipes settings that are still readable. The
 * I/O stays synchronous on the client thread — the JSON is a few KB and the
 * throttle bounds it to one write per {@link #AUTOSAVE_MIN_INTERVAL_MS};
 * a background writer would need to share the live mutable state (POS
 * arrays) with the render thread, which is the racy option here.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("config").resolve("tabvisuals");
    private static final Path CONFIGS_DIR = DIR.resolve("configs");
    private static final Path AUTOSAVE = DIR.resolve("autosave.json");

    /** Min gap between autosave writes — drag/slider storms collapse into one. */
    private static final long AUTOSAVE_MIN_INTERVAL_MS = 500L;

    /** Windows зарезервирует эти имена файлов — "con.json" недопустим. */
    private static final Pattern RESERVED_NAMES =
            Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");

    /** Dirty flag + write timestamp for the throttled autosave. */
    private static boolean dirty;
    private static long lastWriteAt;

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
        // nullable: отсутствие ключа в старом конфиге сохраняет текущее
        // значение модуля, а не сбрасывает его в false
        public Boolean enabled;
        public Boolean favorite;
        /** Module bind chip; null = not present in this config. */
        public Integer bind;
        public Map<String, Object> settings = new HashMap<>();
    }

    static {
        register_hooks();
    }

    private ConfigManager() {
    }

    private static void register_hooks() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> flush_if_due(false));
        // финальный принудительный сброс: закрытие игры и выход из мира —
        // без него позиции HUD, изменённые без открытия ClickGUI, теряются
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flush_if_due(true));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> flush_if_due(true));
    }

    public static GuiState snapshot() {
        GuiState state = new GuiState();
        state.themeId = Theme.get().id();
        for (ModuleStub m : ModuleRegistry.ALL) {
            ModuleState ms = new ModuleState();
            ms.enabled = m.enabled();
            ms.favorite = m.favorite();
            ms.bind = m.bind();
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
            Theme target = Theme.byId(state.themeId);
            // неизвестный id (тема переименована/удалена) → оставляем текущую
            // тему, а не молча сбрасываем всё на Midnight
            if (target.id().equalsIgnoreCase(state.themeId)) {
                Theme.set(target);
            }
        }
        // пустая секция hud в старом/повреждённом конфиге не должна стирать
        // текущие позиции HUD
        if (state.hud != null && !state.hud.isEmpty()) {
            HudEditor.loadPositions(state.hud);
        }
        if (state.modules == null) {
            return;
        }
        for (Map.Entry<String, ModuleState> e : state.modules.entrySet()) {
            // id из старого конфига (переименован/удалён) → пропускаем
            ModuleStub m = ModuleRegistry.byId(e.getKey());
            if (m == null || e.getValue() == null) {
                continue;
            }
            ModuleState ms = e.getValue();
            if (ms.enabled != null) {
                m.setEnabled(ms.enabled);
            }
            if (ms.favorite != null) {
                m.setFavorite(ms.favorite);
            }
            if (ms.bind != null) {
                m.bind(ms.bind);
            }
            if (ms.settings == null) {
                continue;
            }
            for (Setting s : m.settings()) {
                Object v = ms.settings.get(s.name());
                if (v == null) {
                    continue;
                }
                try {
                    apply_setting(s, v);
                } catch (RuntimeException ex) {
                    // одно битое значение не должно отменять загрузку остальных
                    TabVisualsClient.LOGGER.warn("Setting {}.{} not applied", m.id(), s.name(), ex);
                }
            }
        }
    }

    private static void apply_setting(Setting s, Object v) {
        if (s instanceof BooleanSetting b && v instanceof Boolean bv) {
            b.set(bv);
        } else if (s instanceof DoubleSetting d && v instanceof Number n) {
            d.set(n.doubleValue());
        } else if (s instanceof ModeSetting md) {
            if (v instanceof Number n) {
                md.setIndex(n.intValue());
            } else if (v instanceof String sv) {
                // старые конфиги писали имя режима; неизвестное имя → первый режим
                md.set_by_name(sv);
            }
        } else if (s instanceof ColorSetting cs && v instanceof Number n) {
            cs.set(n.intValue());
        } else if (s instanceof KeySetting ks && v instanceof Number n) {
            ks.set(n.intValue());
        }
    }

    /** Помечает состояние грязным и пишет, если интервал между записями прошёл. */
    public static void autosave() {
        dirty = true;
        flush_if_due(false);
    }

    private static synchronized void flush_if_due(boolean force) {
        if (!dirty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastWriteAt < AUTOSAVE_MIN_INTERVAL_MS) {
            return; // tick допишет хвост позже
        }
        if (write(AUTOSAVE, snapshot())) {
            dirty = false;
            lastWriteAt = now;
        }
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
                String name = fn.substring(0, fn.length() - 5);
                if (!name.isBlank()) {
                    names.add(name);
                }
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
     * "foo." as "foo"), caps length so the filename stays sane and dodges
     * Windows reserved device names (con, nul, com1…).
     */
    private static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }
        if (RESERVED_NAMES.matcher(s).matches()) {
            s = "_" + s;
        }
        return s;
    }

    private static Path backup_of(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    /** Атомарная запись: tmp → (бэкап старого) → rename. Обрезанного файла не бывает. */
    private static boolean write(Path file, GuiState state) {
        String json;
        try {
            json = GSON.toJson(state);
        } catch (RuntimeException e) {
            TabVisualsClient.LOGGER.error("Config serialize failed: {}", file, e);
            return false;
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            if (Files.exists(file)) {
                backup(file);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            // RuntimeException: InvalidPathException на зарезервированных именах Windows
            TabVisualsClient.LOGGER.error("Config save failed: {}", file, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    /** .bak обновляется только от валидного основного файла — битый JSON не затирает последний хороший бэкап. */
    private static void backup(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonParser.parseString(json);
            Files.copy(file, backup_of(file), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /** Tolerant read; falls back to the .bak copy when the primary file is unreadable. */
    private static GuiState read(Path file) {
        GuiState state = read_tolerant(file);
        if (state != null) {
            return state;
        }
        // RuntimeException: Gson throws JsonSyntaxException on malformed JSON —
        // uncaught it would crash startup (autosave) or the Configs tab
        Path bak = backup_of(file);
        if (Files.exists(bak)) {
            TabVisualsClient.LOGGER.warn("Config unreadable, trying backup: {}", file);
            return read_tolerant(bak);
        }
        return null;
    }

    /**
     * Парсит файл через JsonObject и применяет поля по одному: неизвестные
     * ключи игнорируются, битый модуль/позиция пропускаются, остальной конфиг
     * загружается. Ошибка типа внутри ModuleState (settings) снимается в
     * apply() проверками instanceof.
     */
    private static GuiState read_tolerant(Path file) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return null;
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            TabVisualsClient.LOGGER.error("Config load failed: {}", file, e);
            return null;
        }
        GuiState state = new GuiState();
        try {
            if (root.has("themeId") && root.get("themeId").isJsonPrimitive()) {
                state.themeId = root.get("themeId").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        read_modules(root, state);
        read_hud(root, state);
        read_custom_theme(root, state);
        return state;
    }

    private static void read_modules(JsonObject root, GuiState state) {
        if (!root.has("modules") || !root.get("modules").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("modules").entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            try {
                ModuleState ms = GSON.fromJson(e.getValue(), ModuleState.class);
                if (ms != null) {
                    state.modules.put(e.getKey(), ms);
                }
            } catch (RuntimeException ignored) {
                // один битый модуль не должен отменять весь конфиг
            }
        }
    }

    private static void read_hud(JsonObject root, GuiState state) {
        if (!root.has("hud") || !root.get("hud").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("hud").entrySet()) {
            if (!e.getValue().isJsonArray()) {
                continue;
            }
            try {
                JsonArray arr = e.getValue().getAsJsonArray();
                float[] f = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonPrimitive()) {
                        throw new IllegalStateException("non-number in hud position");
                    }
                    f[i] = arr.get(i).getAsFloat(); // "NaN"/строка → исключение или NaN, обе обработаны
                }
                state.hud.put(e.getKey(), f);
            } catch (RuntimeException ignored) {
                // битая позиция пропускается, остальной конфиг читается
            }
        }
    }

    private static void read_custom_theme(JsonObject root, GuiState state) {
        if (!root.has("customTheme") || !root.get("customTheme").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("customTheme").entrySet()) {
            try {
                if (!e.getValue().isJsonPrimitive()) {
                    continue;
                }
                state.customTheme.put(e.getKey(), e.getValue().getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
    }
}
