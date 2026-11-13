// language: Java, file: ConfigManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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

    /** Last ClickGUI window position (scaled px); null until the window was first placed. */
    private static Float guiX, guiY;

    /** Display name of the Custom theme (renamed from the theme panel). */
    private static String customThemeName = "Custom";
    private static final int CUSTOM_NAME_MAX = 18;

    /** Serialized GUI state. Public fields for Gson. */
    public static final class GuiState {
        public String themeId = Theme.ALL[0].id();
        public Map<String, ModuleState> modules = new HashMap<>();
        /** HUD element positions from the in-chat editor: id → {x, y} (toasts: {yOffset}). */
        public Map<String, float[]> hud = new HashMap<>();
        /** Custom theme colours: slot name → ARGB int. */
        public Map<String, Integer> customTheme = new HashMap<>();
        /** ClickGUI window position (scaled px); null = never moved — Gson omits nulls. */
        public Float guiX;
        public Float guiY;
        /** Custom theme display name; null = not present in this config. */
        public String customName;
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
        // Gson refuses NaN/Infinity: one poisoned HUD position would make every
        // later save fail, so non-finite entries are left out of the snapshot
        for (Map.Entry<String, float[]> e : HudEditor.positions().entrySet()) {
            if (e.getKey() != null && all_finite(e.getValue())) {
                state.hud.put(e.getKey(), e.getValue());
            }
        }
        state.customTheme.putAll(Theme.customColors());
        state.guiX = guiX;
        state.guiY = guiY;
        state.customName = customThemeName;
        return state;
    }

    private static boolean all_finite(float[] values) {
        if (values == null) {
            return false;
        }
        for (float v : values) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    /** Display name of the Custom theme (never blank). */
    public static String customThemeName() {
        return customThemeName;
    }

    /** Persists a renamed Custom theme; blank names are ignored. */
    public static void setCustomThemeName(String name) {
        String clean = clean_theme_name(name);
        if (clean == null || clean.equals(customThemeName)) {
            return;
        }
        customThemeName = clean;
        autosave();
    }

    private static String clean_theme_name(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.replaceAll("\\p{Cntrl}", "").trim();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() > CUSTOM_NAME_MAX ? clean.substring(0, CUSTOM_NAME_MAX).trim() : clean;
    }

    /**
     * Запоминает позицию окна ClickGUI (scaled px) и помечает автосейв грязным —
     * запись троттлится общим {@link #flush_if_due}, так что drag-шторм дёргает
     * диск не чаще раза в AUTOSAVE_MIN_INTERVAL_MS.
     */
    public static void rememberGuiPosition(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            return;
        }
        guiX = x;
        guiY = y;
        autosave();
    }

    /** Сохранённая позиция окна {x, y} или null — экран центрирует окно сам. */
    public static float[] guiPosition() {
        return guiX == null || guiY == null ? null : new float[]{guiX, guiY};
    }

    public static void apply(GuiState state) {
        apply(state, true);
    }

    /**
     * @param windowPosition false for named configs: the ClickGUI window position
     *                       is a per-install preference, a shared config must not
     *                       move the window on the next open
     */
    private static void apply(GuiState state, boolean windowPosition) {
        if (state == null) {
            return;
        }
        String themeName = clean_theme_name(state.customName);
        if (themeName != null) {
            customThemeName = themeName;
        }
        // custom colours first: the theme id below may point at the Custom slot
        if (state.customTheme != null) {
            Theme.loadCustom(state.customTheme);
        }
        // окно ClickGUI: позиция применяется при следующем открытии экрана
        // (там же зажимается под текущее разрешение — старый конфиг не должен
        // выкидывать окно за пределы меньшего экрана)
        if (windowPosition && state.guiX != null && state.guiY != null
                && Float.isFinite(state.guiX) && Float.isFinite(state.guiY)) {
            guiX = state.guiX;
            guiY = state.guiY;
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
            // via long: a hand-written unsigned 0xAARRGGBB (> Integer.MAX_VALUE)
            // must wrap to the same ARGB int, not saturate to 0x7FFFFFFF
            cs.set((int) n.longValue());
        } else if (s instanceof KeySetting ks && v instanceof Number n) {
            ks.set(n.intValue());
        }
    }

    /** Помечает состояние грязным и пишет, если интервал между записями прошёл. */
    public static synchronized void autosave() {
        // Same monitor as flush_if_due: a dirty=true landing between snapshot()
        // and the dirty=false inside the flush used to be silently dropped
        // (the change missed autosave.json until the next mutation).
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
        // read() falls back to the .bak copy, so a lost primary file still loads
        if (Files.exists(AUTOSAVE) || Files.exists(backup_of(AUTOSAVE))) {
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
                if (fn.length() <= 5 || !Files.isRegularFile(p)) {
                    continue; // a directory named "x.json" is not a config
                }
                String name = fn.substring(0, fn.length() - 5);
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        } catch (IOException | RuntimeException e) {
            // RuntimeException: DirectoryIteratorException from the stream iterator
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
        Path file = config_path(clean);
        return file != null && write(file, snapshot());
    }

    /** null instead of InvalidPathException — a hostile name must fail the action, not the game. */
    private static Path config_path(String fileBase) {
        try {
            return CONFIGS_DIR.resolve(fileBase + ".json");
        } catch (RuntimeException e) {
            TabVisualsClient.LOGGER.warn("Config name not usable as a file name: {}", fileBase);
            return null;
        }
    }

    /**
     * File of an existing config. A name exactly as listed on disk wins over
     * its sanitised form, so a hand-dropped file whose name sanitize() would
     * alter (long, trailing dot…) can still be loaded and deleted from the tab.
     */
    private static Path existing_config(String name) {
        if (name == null) {
            return null;
        }
        if (listConfigs().contains(name)) {
            return config_path(name); // came from the directory listing — a plain file name
        }
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return null;
        }
        Path file = config_path(clean);
        return file != null && Files.exists(file) ? file : null;
    }

    /** true when the file existed and parsed — callers use it to report honestly. */
    public static boolean loadConfig(String name) {
        Path file = existing_config(name);
        if (file == null) {
            return false;
        }
        GuiState state = read(file);
        if (state == null) {
            return false;
        }
        apply(state, false);
        return true;
    }

    public static boolean deleteConfig(String name) {
        Path file = existing_config(name);
        if (file == null) {
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(file);
            // the backup would otherwise linger forever next to nothing
            Files.deleteIfExists(backup_of(file));
            return deleted;
        } catch (IOException | RuntimeException e) {
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
        // control chars are as path-hostile on Windows as the punctuation set
        String s = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");
        if (s.length() > 32) {
            // never cut a surrogate pair in half — a lone surrogate is not encodable
            s = s.substring(0, Character.isHighSurrogate(s.charAt(31)) ? 31 : 32);
        }
        // trimmed AFTER the cut: the cut itself can expose a trailing dot/space
        s = s.replaceAll("^[.\\s]+|[.\\s]+$", "");
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
            } catch (IOException | RuntimeException cleanup) {
                TabVisualsClient.LOGGER.debug("config tmp cleanup failed: {}", file, cleanup);
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
        } catch (IOException | RuntimeException e) {
            // сбой бэкапа не должен прерывать сохранение — основной файл уже валиден
            TabVisualsClient.LOGGER.debug("config backup skipped: {}", file, e);
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
        // absent keys must keep the live values: no implicit default theme
        state.themeId = null;
        try {
            if (root.has("customName") && root.get("customName").isJsonPrimitive()) {
                state.customName = root.get("customName").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (root.has("themeId") && root.get("themeId").isJsonPrimitive()) {
                state.themeId = root.get("themeId").getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        read_modules(root, state);
        read_hud(root, state);
        read_custom_theme(root, state);
        read_gui_position(root, state);
        return state;
    }

    /** guiX/guiY из файла: два примитива, битое/нефинитное значение пропускается. */
    private static void read_gui_position(JsonObject root, GuiState state) {
        try {
            if (root.has("guiX") && root.has("guiY")
                    && root.get("guiX").isJsonPrimitive() && root.get("guiY").isJsonPrimitive()) {
                float x = root.get("guiX").getAsFloat();
                float y = root.get("guiY").getAsFloat();
                if (Float.isFinite(x) && Float.isFinite(y)) {
                    state.guiX = x;
                    state.guiY = y;
                }
            }
        } catch (RuntimeException ignored) {
            // битая позиция не должна сбрасывать остальной конфиг
        }
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
                state.modules.put(e.getKey(), read_module(e.getValue().getAsJsonObject()));
            } catch (RuntimeException ignored) {
                // один битый модуль не должен отменять весь конфиг
            }
        }
    }

    /**
     * Field-by-field instead of Gson reflection: one wrong-typed field
     * ("enabled": "yes", a nested object as a setting value) used to throw and
     * drop the whole module, including every setting that was still valid.
     */
    private static ModuleState read_module(JsonObject o) {
        ModuleState ms = new ModuleState();
        ms.enabled = as_bool(o.get("enabled"));
        ms.favorite = as_bool(o.get("favorite"));
        JsonElement bind = o.get("bind");
        if (bind != null && bind.isJsonPrimitive() && bind.getAsJsonPrimitive().isNumber()) {
            try {
                ms.bind = (int) bind.getAsDouble();
            } catch (RuntimeException ignored) {
            }
        }
        JsonElement settings = o.get("settings");
        if (settings == null || !settings.isJsonObject()) {
            return ms;
        }
        for (Map.Entry<String, JsonElement> e : settings.getAsJsonObject().entrySet()) {
            JsonElement v = e.getValue();
            if (v == null || !v.isJsonPrimitive()) {
                continue;
            }
            JsonPrimitive p = v.getAsJsonPrimitive();
            try {
                if (p.isBoolean()) {
                    ms.settings.put(e.getKey(), p.getAsBoolean());
                } else if (p.isNumber()) {
                    ms.settings.put(e.getKey(), p.getAsDouble());
                } else if (p.isString()) {
                    ms.settings.put(e.getKey(), p.getAsString());
                }
            } catch (RuntimeException ignored) {
                // unparsable number literal — skip this value only
            }
        }
        return ms;
    }

    /** Tolerant boolean: true/false, 0/1, "true"/"false"; anything else = absent. */
    private static Boolean as_bool(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        try {
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            if (p.isNumber()) {
                return p.getAsDouble() != 0.0;
            }
            String s = p.getAsString().trim();
            if (s.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            if (s.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
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
