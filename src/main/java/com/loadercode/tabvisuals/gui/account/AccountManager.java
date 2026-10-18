// language: Java, file: AccountManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loadercode.tabvisuals.TabVisualsClient;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Offline nickname profiles (alt manager): JSON-backed CRUD plus runtime
 * session swap used by the "Войти" action of the account modal.
 * Everything here is offline — a skin fetch failing never blocks a login.
 */
public final class AccountManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir()
            .resolve("config").resolve("tabvisuals").resolve("accounts.json");

    private static final int MIN_NICK = 3;
    private static final int MAX_NICK = 16;
    /** Cap matching the title-screen row rect pools (64) — a 65th profile
     *  would otherwise index past the pool and crash the account modal. */
    public static final int MAX_ACCOUNTS = 64;
    /** Precompiled: clean() runs on every keystroke of the name field. */
    private static final Pattern DIRTY = Pattern.compile("[^A-Za-z0-9_]");
    /** Windows device names — a profile called CON can never become a file name. */
    private static final Set<String> RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    /** Serialized file shape. Public fields for Gson. */
    public static final class Data {
        public List<String> accounts = new ArrayList<>();
        public String current;
    }

    private static Data data;

    /** Launcher session captured before the first swap, so delete() can undo it. */
    private static Session original_session;
    private static CompletableFuture<?> original_profile_future;
    /** Set once the capture ran — later applies must not record an alt as "original". */
    private static boolean original_captured;

    private AccountManager() {
    }

    /** Lazy load, guarded: two threads racing here could parse accounts.json twice
     *  (and the loser's Data would overwrite the winner's writes). */
    private static synchronized Data data() {
        if (data == null) {
            Data loaded = null;
            if (Files.exists(FILE)) {
                try {
                    loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                } catch (IOException | RuntimeException e) {
                    TabVisualsClient.LOGGER.error("Accounts load failed", e);
                    quarantine();
                }
            }
            data = loaded != null ? loaded : new Data();
            normalize(data);
        }
        return data;
    }

    /** Drops null/blank/duplicate rows and oversized nicks from a loaded file. */
    private static void normalize(Data d) {
        List<String> src = d.accounts;
        List<String> out = new ArrayList<>(src == null ? 0 : src.size());
        Set<String> seen = new HashSet<>();
        if (src != null) {
            for (String raw : src) {
                if (raw == null) {
                    continue;
                }
                String s = raw.trim();
                if (s.length() > MAX_NICK) {
                    s = s.substring(0, MAX_NICK);
                }
                if (s.isEmpty() || !seen.add(s.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                out.add(s);
            }
        }
        d.accounts = out;
        if (d.current != null) {
            d.current = d.current.trim();
            if (d.current.isEmpty()) {
                d.current = null;
            }
        }
    }

    /** Moves an unreadable file aside — the next save must not clobber it silently. */
    private static void quarantine() {
        try {
            Path bad = FILE.resolveSibling(FILE.getFileName() + ".bak");
            Files.move(FILE, bad, StandardCopyOption.REPLACE_EXISTING);
            TabVisualsClient.LOGGER.warn("Unreadable accounts.json kept as {}", bad.getFileName());
        } catch (IOException ignored) {
            // best effort — the load error is already logged
        }
    }

    /** tmp + rename: a crash mid-write must not leave a truncated list behind. */
    private static void save() {
        Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(tmp, GSON.toJson(data()), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("Accounts save failed", e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // a leftover tmp is harmless: the next save truncates it
            }
        }
    }

    public static List<String> names() {
        return data().accounts;
    }

    /** Nickname shown on the profile plate — logged-in alt or the real session user. */
    public static String current() {
        String c = data().current;
        if (c != null && !c.isEmpty()) {
            return c;
        }
        Session s = MinecraftClient.getInstance().getSession();
        String user = s == null ? null : s.getUsername();
        return user == null ? "" : user;
    }

    /** 3-16 chars, latin/digits/underscore. */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = DIRTY.matcher(raw.trim()).replaceAll("");
        return s.length() >= MIN_NICK ? s.substring(0, Math.min(s.length(), MAX_NICK)) : "";
    }

    /** Reserved Windows device names are rejected — they can never be persisted as files. */
    private static boolean is_reserved(String name) {
        return RESERVED.contains(name.toUpperCase(Locale.ROOT));
    }

    public static boolean create(String raw) {
        String name = clean(raw);
        if (name.isEmpty() || is_reserved(name) || contains(name)) {
            return false;
        }
        if (data().accounts.size() >= MAX_ACCOUNTS) {
            return false;
        }
        data().accounts.add(name);
        save();
        return true;
    }

    public static void delete(int index) {
        Data d = data();
        if (index < 0 || index >= d.accounts.size()) {
            return;
        }
        String removed = d.accounts.remove(index);
        if (removed.equalsIgnoreCase(d.current)) {   // create/rename keep the stored case
            d.current = null;
            // the account no longer exists: hand the launcher session back
            restore_original(MinecraftClient.getInstance());
        }
        save();
    }

    public static boolean rename(int index, String raw) {
        Data d = data();
        String name = clean(raw);
        if (index < 0 || index >= d.accounts.size() || name.isEmpty() || is_reserved(name)) {
            return false;
        }
        for (int i = 0; i < d.accounts.size(); i++) {
            if (i != index && d.accounts.get(i).equalsIgnoreCase(name)) {
                return false;
            }
        }
        if (d.current != null && d.current.equalsIgnoreCase(d.accounts.get(index))) {
            d.current = name;
        }
        d.accounts.set(index, name);
        save();
        return true;
    }

    private static boolean contains(String name) {
        for (String s : data().accounts) {
            if (s.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Cached MinecraftClient.session field — located by type, so the
     *  intermediary/production name never matters. */
    private static Field session_field;

    private static Field session_field() throws NoSuchFieldException {
        if (session_field == null) {
            for (Field f : MinecraftClient.class.getDeclaredFields()) {
                if (Session.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    session_field = f;
                    break;
                }
            }
            if (session_field == null) {
                throw new NoSuchFieldException("Session field not found in MinecraftClient");
            }
        }
        return session_field;
    }

    /** Builds an offline Session and swaps it into MinecraftClient. */
    public static void apply(String raw) {
        String name = clean(raw);
        if (name.isEmpty()) {
            return;
        }
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            Session session = new Session(name, uuid, "0",
                    Optional.empty(), Optional.empty(), Session.AccountType.LEGACY);
            MinecraftClient mc = MinecraftClient.getInstance();
            Field sf = session_field();
            remember_original(mc, sf);
            // non-static final field: setAccessible(true) grants write access (JDK spec)
            sf.set(mc, session);
            refresh_game_profile(mc, name, uuid);
            data().current = name;
            save();
            TabVisualsClient.LOGGER.info("Offline session applied: {} ({})", name, uuid);
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.error("Offline login failed for " + name, t);
        }
    }

    /** One-shot capture of the launcher session — only the first apply may record it. */
    private static void remember_original(MinecraftClient mc, Field sf) {
        if (original_captured) {
            return;
        }
        original_captured = true;
        try {
            original_session = (Session) sf.get(mc);
        } catch (Throwable t) {
            original_session = null;
        }
        try {
            Field pf = profile_future_field();
            Object v = pf == null ? null : pf.get(mc);
            original_profile_future = v instanceof CompletableFuture<?> fut ? fut : null;
        } catch (Throwable t) {
            original_profile_future = null;
        }
    }

    /** Puts the launcher session (and its cached profile future) back. */
    private static void restore_original(MinecraftClient mc) {
        if (!original_captured) {
            return;
        }
        original_captured = false;
        try {
            if (original_session != null) {
                session_field().set(mc, original_session);
            }
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.warn("Session restore failed: {}", t.toString());
        }
        try {
            Field pf = profile_future_field();
            if (pf != null && original_profile_future != null) {
                pf.set(mc, original_profile_future);
            }
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.warn("gameProfile restore failed: {}", t.toString());
        }
        original_session = null;
        original_profile_future = null;
    }

    /**
     * MinecraftClient caches the resolved profile in gameProfileFuture, which
     * MinecraftClient.getGameProfile() joins — after a session swap it would
     * keep returning the previous account (skin, tab, telemetry identity).
     * Best-effort: the login packet reads the Session directly, so a failed
     * refresh degrades to a stale display profile, not a broken login.
     */
    private static void refresh_game_profile(MinecraftClient mc, String name, UUID uuid) {
        try {
            Field pf = profile_future_field();
            if (pf != null) {
                pf.set(mc, CompletableFuture.completedFuture(
                        new ProfileResult(new GameProfile(uuid, name))));
            }
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.warn("gameProfile refresh failed: {}", t.toString());
        }
    }

    private static Field profile_future_field;
    private static boolean profile_future_field_failed;

    /** CompletableFuture&lt;ProfileResult&gt; field of MinecraftClient, or null. */
    private static Field profile_future_field() {
        Field f = profile_future_field;
        if (f == null && !profile_future_field_failed) {
            for (Field declared : MinecraftClient.class.getDeclaredFields()) {
                if (declared.getType() != CompletableFuture.class) {
                    continue;
                }
                Type generic = declared.getGenericType();
                if (generic instanceof ParameterizedType pt
                        && pt.getActualTypeArguments()[0] == ProfileResult.class) {
                    declared.setAccessible(true);
                    profile_future_field = f = declared;
                    break;
                }
            }
            if (f == null) {
                profile_future_field_failed = true;
            }
        }
        return f;
    }
}
