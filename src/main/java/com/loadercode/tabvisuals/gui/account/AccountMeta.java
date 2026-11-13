// language: Java, file: AccountMeta.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loadercode.tabvisuals.TabVisualsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Menu-only metadata next to the account list: when each profile was last
 * used and which ones are starred. Kept apart from AccountManager's file so
 * an older client build reading accounts.json is unaffected. Keys are
 * lower-case nicks. Reads/writes happen on the render thread in memory; the
 * file is written by a single daemon worker (temp file + atomic move).
 */
public final class AccountMeta {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("tabvisuals").resolve("accounts_meta.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tabvisuals-account-meta");
        t.setDaemon(true);
        return t;
    });

    private static final class Data {
        Map<String, Long> lastUsed = new HashMap<>();
        Set<String> favorites = new HashSet<>();
    }

    private static Data data;

    private AccountMeta() {
    }

    private static Data data() {
        if (data == null) {
            Data d = null;
            try {
                if (Files.isRegularFile(FILE) && Files.size(FILE) < 1_000_000L) {
                    d = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                }
            } catch (Exception e) {
                TabVisualsClient.LOGGER.warn("accounts_meta.json unreadable — starting fresh", e);
            }
            if (d == null) {
                d = new Data();
            }
            if (d.lastUsed == null) {
                d.lastUsed = new HashMap<>();
            }
            if (d.favorites == null) {
                d.favorites = new HashSet<>();
            }
            d.lastUsed.values().removeIf(v -> v == null);
            d.favorites.remove(null);
            data = d;
        }
        return data;
    }

    private static String key(String nick) {
        return nick == null ? "" : nick.toLowerCase(Locale.ROOT);
    }

    /** Epoch ms of the last login with this profile, 0 = never. */
    public static long lastUsed(String nick) {
        Long v = data().lastUsed.get(key(nick));
        return v == null ? 0L : v;
    }

    public static void touch(String nick) {
        data().lastUsed.put(key(nick), System.currentTimeMillis());
        save();
    }

    public static boolean favorite(String nick) {
        return data().favorites.contains(key(nick));
    }

    public static void toggleFavorite(String nick) {
        String k = key(nick);
        if (!data().favorites.remove(k)) {
            data().favorites.add(k);
        }
        save();
    }

    /** Carries the metadata over a rename. */
    public static void rename(String from, String to) {
        Data d = data();
        String a = key(from), b = key(to);
        Long t = d.lastUsed.remove(a);
        if (t != null) {
            d.lastUsed.put(b, t);
        }
        if (d.favorites.remove(a)) {
            d.favorites.add(b);
        }
        save();
    }

    public static void forget(String nick) {
        Data d = data();
        d.lastUsed.remove(key(nick));
        d.favorites.remove(key(nick));
        save();
    }

    /** "сейчас", "5 мин назад", "вчера"… for the profile list. */
    public static String ago(long epochMs) {
        if (epochMs <= 0L) {
            return "ещё не использовался";
        }
        long s = Math.max(0L, (System.currentTimeMillis() - epochMs) / 1000L);
        if (s < 60) {
            return "сейчас";
        }
        long min = s / 60;
        if (min < 60) {
            return min + " мин назад";
        }
        long h = min / 60;
        if (h < 24) {
            return h + " ч назад";
        }
        long d = h / 24;
        if (d == 1) {
            return "вчера";
        }
        return d < 30 ? d + " дн назад" : "давно";
    }

    private static void save() {
        String json = GSON.toJson(data());
        IO.execute(() -> {
            try {
                Files.createDirectories(FILE.getParent());
                Path tmp = FILE.resolveSibling("accounts_meta.json.tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                TabVisualsClient.LOGGER.warn("accounts_meta.json write failed", e);
            }
        });
    }
}
