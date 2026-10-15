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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Offline nickname profiles (alt manager): JSON-backed CRUD plus runtime
 * session swap used by the "Войти" action of the account modal.
 */
public final class AccountManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getGameDir()
            .resolve("config").resolve("tabvisuals").resolve("accounts.json");

    /** Serialized file shape. Public fields for Gson. */
    public static final class Data {
        public List<String> accounts = new ArrayList<>();
        public String current;
    }

    private static Data data;

    private AccountManager() {
    }

    private static Data data() {
        if (data == null) {
            data = new Data();
            if (Files.exists(FILE)) {
                try {
                    Data loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                    if (loaded != null) {
                        data = loaded;
                        if (data.accounts == null) {
                            data.accounts = new ArrayList<>();
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    TabVisualsClient.LOGGER.error("Accounts load failed", e);
                }
            }
        }
        return data;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("Accounts save failed", e);
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
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    /** 3-16 chars, latin/digits/underscore. */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replaceAll("[^A-Za-z0-9_]", "");
        return s.length() >= 3 ? s.substring(0, Math.min(s.length(), 16)) : "";
    }

    public static boolean create(String raw) {
        String name = clean(raw);
        if (name.isEmpty() || contains(name)) {
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
        }
        save();
    }

    public static boolean rename(int index, String raw) {
        Data d = data();
        String name = clean(raw);
        if (index < 0 || index >= d.accounts.size() || name.isEmpty()) {
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
    private static Field sessionField;

    private static Field sessionField() throws NoSuchFieldException {
        if (sessionField == null) {
            for (Field f : MinecraftClient.class.getDeclaredFields()) {
                if (Session.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    sessionField = f;
                    break;
                }
            }
            if (sessionField == null) {
                throw new NoSuchFieldException("Session field not found in MinecraftClient");
            }
        }
        return sessionField;
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
            // non-static final field: setAccessible(true) grants write access (JDK spec)
            sessionField().set(mc, session);
            refreshGameProfile(mc, name, uuid);
            data().current = name;
            save();
            TabVisualsClient.LOGGER.info("Offline session applied: {} ({})", name, uuid);
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.error("Offline login failed for " + name, t);
        }
    }

    /**
     * MinecraftClient caches the resolved profile in gameProfileFuture, which
     * MinecraftClient.getGameProfile() joins — after a session swap it would
     * keep returning the previous account (skin, tab, telemetry identity).
     * Best-effort: the login packet reads the Session directly, so a failed
     * refresh degrades to a stale display profile, not a broken login.
     */
    private static void refreshGameProfile(MinecraftClient mc, String name, UUID uuid) {
        try {
            if (profileFutureField == null && !profileFutureFieldFailed) {
                for (Field f : MinecraftClient.class.getDeclaredFields()) {
                    if (f.getType() != CompletableFuture.class) {
                        continue;
                    }
                    Type generic = f.getGenericType();
                    if (generic instanceof ParameterizedType pt
                            && pt.getActualTypeArguments()[0] == ProfileResult.class) {
                        f.setAccessible(true);
                        profileFutureField = f;
                        break;
                    }
                }
                if (profileFutureField == null) {
                    profileFutureFieldFailed = true;
                }
            }
            if (profileFutureField != null) {
                profileFutureField.set(mc,
                        CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid, name))));
            }
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.warn("gameProfile refresh failed: {}", t.toString());
        }
    }

    private static Field profileFutureField;
    private static boolean profileFutureFieldFailed;
}
