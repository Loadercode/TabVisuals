// language: Java, file: AccountRenderer.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.telemetry.TelemetrySender;
import net.minecraft.client.session.telemetry.WorldSession;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.entity.damage.DamageScaling;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Full-body 3D player skin preview for the account manager: the whole model
 * (head to feet) rendered through the vanilla inventory entity pass, rotatable by
 * mouse drag (drag spins yaw / tilts the model, release keeps angular momentum,
 * then eases back to a neutral front-facing angle). The skin follows a nickname —
 * the session profile by default, any typed nick resolved through the Mojang
 * session servers (profile → textures property → textures.minecraft.net);
 * resolved profiles are cached in memory and on disk, a nick without a skin falls
 * back to the default steve/alex model, and a nick that cannot be fetched shows a
 * placeholder (pulsing USER glyph, NODE badge when the fetch failed) that
 * crossfades out as soon as the model is ready.
 */
public final class AccountRenderer {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Path CACHE_FILE = FabricLoader.getInstance().getGameDir()
            .resolve("config").resolve("tabvisuals").resolve("skin_cache.json");

    /** Neutral angle: the model faces the viewer (vanilla inventory pose). */
    private static final float NEUTRAL_YAW = 180f;
    private static final float MAX_TILT = 28f;
    /** Release spin cap, deg/s — one violent flick must not become a top. */
    private static final float MAX_SPIN = 1440f;
    /** Idle seconds before the model eases back to front-facing (inertia runs first). */
    private static final float SNAP_AFTER = 1.2f;
    /** Idle breathing: one slow cycle, a couple of pixels. */
    private static final float BOB_PERIOD = 8f;
    private static final float BOB_PX = 1.6f;
    private static final float SWAY_DEG = 1.5f;
    private static final float BREATH_TILT_DEG = 0.9f;
    /** A typed nick must sit still this long before it is fetched (typing skips prefixes). */
    private static final float RESOLVE_DELAY = 0.6f;

    /** Violet pedestal palette (alpha byte ignored by glow). */
    private static final int ACCENT = 0xFF8B5CF6;
    /** Veil colour: reads as the preview card while the model fades. */
    private static final int PANEL_VEIL = 0x181434;
    /** Entities past this size leave the LRU (their skins stay in the shared provider cache). */
    private static final int ENTITY_CACHE_MAX = 12;

    // ------------------------------------------------- profile resolution

    /** Resolved profiles of this session: lowercase nick → profile. */
    private static final Map<String, GameProfile> PROFILE_CACHE = new ConcurrentHashMap<>();
    /** Nicks a fetch is already running for (no duplicate threads). */
    private static final Set<String> RESOLVING = ConcurrentHashMap.newKeySet();
    /** Epoch ms of the last failed fetch per nick — drives the retry backoff. */
    private static final Map<String, Long> RESOLVE_FAILED = new ConcurrentHashMap<>();
    /** Nicks Mojang answered 404 for: settled on the default skin, never refetched. */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
    /** One shared client — a new HttpClient per fetch leaks selector threads. */
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** Retry a failed nick resolve after this long (the nick stays selected). */
    private static final long RESOLVE_RETRY_MS = 15_000L;
    /**
     * Bounded daemon pool instead of one thread per nick: a burst of typed
     * prefixes can queue work, never spawn unbounded threads, and a rejected
     * task falls into the normal failure backoff.
     */
    private static final ExecutorService RESOLVE_EXEC = new_resolve_executor();

    /** Disk cache, filled once by a daemon thread; the render thread reads it only when ready. */
    private static volatile JsonObject disk_cache;
    private static volatile boolean disk_ready;
    private static volatile boolean disk_load_started;

    // ------------------------------------------------------ runtime state

    /** Volatile: resolve workers read it to decide the pending swap. */
    private volatile String nick = "";
    private GameProfile profile;
    /** Freshly resolved profile waiting for the render thread to swap it in. */
    private volatile GameProfile pending_profile;
    /** LRU of preview entities, keyed by profile uuid — one model, many frames. */
    private final Map<String, OtherClientPlayerEntity> entities =
            new LinkedHashMap<String, OtherClientPlayerEntity>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, OtherClientPlayerEntity> eldest) {
            return size() > ENTITY_CACHE_MAX;
        }
    };
    /** Profiles whose entity creation threw once — never retried (no log spam). */
    private final Set<String> entity_failed = ConcurrentHashMap.newKeySet();
    /** Sticky: the fake world failed to build — the preview stays off. */
    private static volatile boolean preview_world_failed;
    /** Fake world/handler may only be built on this thread (set on first use). */
    private static volatile Thread preview_thread;
    /** Log the preview render failure once, not once per frame. */
    private boolean render_error_logged;

    // resolution bookkeeping (render thread only)
    /** Nicks whose disk entry was already looked up — checked exactly once per nick. */
    private final Set<String> disk_checked = ConcurrentHashMap.newKeySet();
    /** Debounce: the key the stability timer is running for, and how long it held. */
    private String stable_key = "";
    private float stable_time;

    // rotation / animation state
    private float yaw = NEUTRAL_YAW;
    private float tilt;
    /** Angular velocity left over from a drag — decays after release (inertia). */
    private float yaw_vel;
    private boolean dragging;
    private double last_drag_x, last_drag_y;
    private long last_drag_nanos;
    private float idle_time = SNAP_AFTER + 1f;
    private float anim_time;
    /** 0..1 blends for the idle bob and the at-rest breathing rotation. */
    private float idle_blend = 1f;
    private float rest_blend = 1f;
    /** Crossfade 0..1: 0 = placeholder, 1 = skin fully shown. */
    private float skin_a;

    // per-frame frame state (render thread)
    private OtherClientPlayerEntity render_entity;
    private boolean show_placeholder;
    private boolean show_error;

    // ------------------------------------------------------------ profile

    /** Nick currently shown; empty string = nothing loaded yet. */
    public String nick() {
        return nick;
    }

    public boolean isDragging() {
        return dragging;
    }

    /**
     * Loads the skin of {@code raw} (session profile when it matches, cached
     * or freshly resolved Mojang profile otherwise, offline steve meanwhile).
     * Safe to call every frame — a no-op while the nick is unchanged.
     */
    public void setNick(String raw) {
        String n = AccountManager.clean(raw);
        if (n.equalsIgnoreCase(nick)) {
            return;
        }
        nick = n;
        skin_a = 0f;   // placeholder first, the model crossfades in when ready
        if (n.isEmpty()) {
            profile = null;
            pending_profile = null;
            return;
        }
        String key = n.toLowerCase(Locale.ROOT);
        MinecraftClient mc = MinecraftClient.getInstance();
        Session session = mc.getSession();
        boolean is_session = session != null && session.getUsername().equalsIgnoreCase(n);

        GameProfile session_p = is_session ? session_profile(mc) : null;
        GameProfile cached = cache_get(key);
        if (session_p != null && has_textures(session_p)) {
            profile = session_p;                     // online session: textures already in hand
        } else if (cached != null && has_textures(cached)) {
            profile = cached;                        // resolved skin beats a plain session identity
        } else if (session_p != null) {
            profile = session_p;
        } else if (cached != null) {
            profile = cached;
        } else {
            // unknown nick — show the offline (default-skin) model right away and
            // resolve the Mojang profile in the background
            profile = offline_profile(n);
        }
        pending_profile = null;
        if (!has_textures(profile)) {
            stable_key = "";
            stable_time = 0f;   // sync_resolve restarts the fetch debounce for this key
        }
    }

    private static boolean has_textures(GameProfile p) {
        return p != null && p.getProperties().containsKey("textures");
    }

    private String nick_key() {
        String n = nick;
        return n.isEmpty() ? "" : n.toLowerCase(Locale.ROOT);
    }

    /** Cached profile for the key: memory first, disk once the loader finished. */
    private static GameProfile cache_get(String key) {
        GameProfile p = PROFILE_CACHE.get(key);
        if (p != null) {
            return p;
        }
        if (!disk_ready()) {
            return null;   // render thread never waits for the cache file
        }
        p = from_disk(key);
        if (p != null) {
            PROFILE_CACHE.put(key, p);
        }
        return p;
    }

    /**
     * Per-frame resolution step: adopts a disk entry that arrived after
     * setNick, restarts failed fetches on the backoff, and debounces the fetch
     * of a nick that is still being typed.
     */
    private void sync_resolve(float dt) {
        if (nick.isEmpty()) {
            return;
        }
        String key = nick_key();
        if (has_textures(profile) || MISSING.contains(key)) {
            return;
        }
        if (disk_ready() && disk_checked.add(key)) {
            GameProfile disk = from_disk(key);
            if (disk != null) {
                PROFILE_CACHE.put(key, disk);
                if (has_textures(disk)) {
                    profile = disk;
                    pending_profile = null;
                    return;
                }
            }
        }
        if (!disk_ready() || PROFILE_CACHE.containsKey(key) || RESOLVING.contains(key)) {
            return;   // nothing fetched before the local cache is known / while in flight
        }
        long now = System.currentTimeMillis();
        Long failed = RESOLVE_FAILED.get(key);
        if (failed != null) {
            if (now - failed < RESOLVE_RETRY_MS) {
                return;
            }
            RESOLVE_FAILED.remove(key, failed);
        }
        if (!key.equals(stable_key)) {
            stable_key = key;
            stable_time = 0f;
            return;
        }
        stable_time += dt;
        if (stable_time < RESOLVE_DELAY) {
            return;
        }
        start_resolve(nick, key);
    }

    /** Spawns one queued resolve for the nick (deduped via RESOLVING). */
    private void start_resolve(String name, String key) {
        if (!RESOLVING.add(key)) {
            return;
        }
        try {
            RESOLVE_EXEC.execute(() -> resolve_remote(name));
        } catch (RejectedExecutionException rejected) {
            // queue full: back off instead of spinning a task per frame
            RESOLVING.remove(key);
            RESOLVE_FAILED.put(key, System.currentTimeMillis());
        }
    }

    private static ExecutorService new_resolve_executor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8),
                r -> {
                    Thread t = new Thread(r, "tabvisuals-skin");
                    t.setDaemon(true);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static void mark_failed(String key) {
        RESOLVE_FAILED.put(key, System.currentTimeMillis());
    }

    private static void mark_missing(String key) {
        RESOLVE_FAILED.remove(key);
        MISSING.add(key);
    }

    /** Offline-steve profile for a nick — the degradation path. */
    private static GameProfile offline_profile(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, name);
    }

    /** MinecraftClient.gameProfileFuture, located once by its generic type. */
    private static Field profile_future_field;
    private static boolean profile_future_field_failed;

    /**
     * Non-blocking stand-in for MinecraftClient.getGameProfile(): that one
     * joins gameProfileFuture, which runs an uncached sessionserver fetch —
     * on a dead network the render thread freezes, and a failed fetch makes
     * join() throw. Uses the resolved value only when it is already done and
     * still belongs to the current session (AccountManager.apply swaps the
     * session, the stale future keeps the old account); otherwise builds a
     * plain uuid+name profile from the session — same fallback as vanilla.
     */
    private static GameProfile session_profile(MinecraftClient mc) {
        Session s = mc.getSession();
        if (s == null) {
            return null;
        }
        UUID uuid = s.getUuidOrNull();
        try {
            Field f = profile_future_field();
            if (f != null) {
                Object done = ((CompletableFuture<?>) f.get(mc)).getNow(null);
                if (done instanceof ProfileResult r && r.profile() != null
                        && uuid != null && uuid.equals(r.profile().getId())) {
                    return r.profile();
                }
            }
        } catch (Throwable ignored) {
            // reflection denied / future failed — session identity is enough
        }
        return new GameProfile(uuid, s.getUsername());
    }

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

    /** One structured GET: 0 = transport failure, else the HTTP status. */
    private record Fetch(int status, JsonObject obj) {
    }

    private static Fetch fetch_json(HttpClient http, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() != 200 || body == null || body.isBlank()) {
                return new Fetch(resp.statusCode(), null);
            }
            JsonElement el = GSON.fromJson(body, JsonElement.class);
            return new Fetch(200, el != null && el.isJsonObject() ? el.getAsJsonObject() : null);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new Fetch(0, null);
        } catch (Throwable ignored) {
            return new Fetch(0, null);   // offline / DNS / timeout
        }
    }

    /**
     * api.mojang.com name→uuid → sessionserver profile (textures property) →
     * memory + disk cache. Runs on the bounded worker pool; the render thread
     * picks the result up through {@link #pending_profile}, and only while the
     * nick is still the one this fetch was started for.
     */
    private void resolve_remote(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        try {
            Fetch uuid_resp = fetch_json(HTTP,
                    "https://api.mojang.com/users/profiles/minecraft/" + name);
            if (uuid_resp.status() == 404) {
                mark_missing(key);
                return;
            }
            if (uuid_resp.status() != 200 || uuid_resp.obj() == null) {
                mark_failed(key);
                return;
            }
            String uuid_hex = uuid_resp.obj().has("id")
                    && uuid_resp.obj().get("id").isJsonPrimitive()
                    ? uuid_resp.obj().get("id").getAsString() : null;
            if (uuid_hex == null || uuid_hex.length() != 32) {
                mark_missing(key);
                return;
            }
            Fetch prof_resp = fetch_json(HTTP,
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid_hex + "?unsigned=false");
            if (prof_resp.status() == 404) {
                mark_missing(key);
                return;
            }
            if (prof_resp.status() != 200 || prof_resp.obj() == null) {
                mark_failed(key);
                return;
            }
            JsonObject session_json = prof_resp.obj();
            String real_name = session_json.has("name") && session_json.get("name").isJsonPrimitive()
                    ? session_json.get("name").getAsString() : name;
            GameProfile p = new GameProfile(uuid_from_hex(uuid_hex), real_name);
            String tex_value = null, tex_signature = null;
            if (session_json.has("properties") && session_json.get("properties").isJsonArray()) {
                for (JsonElement el : session_json.getAsJsonArray("properties")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject prop = el.getAsJsonObject();
                    if (prop.has("name") && "textures".equals(prop.get("name").getAsString())) {
                        tex_value = prop.has("value") ? prop.get("value").getAsString() : null;
                        tex_signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                    }
                }
            }
            if (tex_value != null) {
                p.getProperties().put("textures", new Property("textures", tex_value, tex_signature));
            }
            PROFILE_CACHE.put(key, p);
            RESOLVE_FAILED.remove(key);
            store_disk(name, uuid_hex, real_name, tex_value, tex_signature);
            if (key.equals(nick_key())) {
                pending_profile = p;   // only the nick still shown may swap in
            }
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.warn("Skin resolve failed for {}: {}", name, t.toString());
            mark_failed(key);
        } finally {
            RESOLVING.remove(key);   // a failed fetch must not pin the nick offline forever
        }
    }

    /** Hex uuid (32 chars) → dashed UUID. */
    private static UUID uuid_from_hex(String hex) {
        String d = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
        return UUID.fromString(d);
    }

    // --------------------------------------------------------- disk cache

    private static void ensure_disk_load() {
        if (disk_load_started) {
            return;
        }
        synchronized (AccountRenderer.class) {
            if (disk_load_started) {
                return;
            }
            disk_load_started = true;
            Thread t = new Thread(AccountRenderer::load_disk, "tabvisuals-skin-cache");
            t.setDaemon(true);
            t.start();
        }
    }

    /** True once the cache file was read; the render thread polls this instead of blocking. */
    private static boolean disk_ready() {
        ensure_disk_load();
        return disk_ready;
    }

    private static void load_disk() {
        JsonObject root = null;
        try {
            if (Files.exists(CACHE_FILE)) {
                String text = Files.readString(CACHE_FILE, StandardCharsets.UTF_8);
                JsonElement el = GSON.fromJson(text, JsonElement.class);
                // a hand-edited/corrupt file just means "empty cache"
                if (el instanceof JsonObject o) {
                    root = o;
                }
            }
        } catch (Throwable ignored) {
            root = null;
        }
        if (root == null) {
            root = new JsonObject();
        }
        // a hand-edited/corrupt "entries" node must not throw on every later read
        if (!(root.get("entries") instanceof JsonObject)) {
            root.add("entries", new JsonObject());
        }
        synchronized (AccountRenderer.class) {
            disk_cache = root;
            disk_ready = true;   // publish last: readers that see it never wait for file IO
            AccountRenderer.class.notifyAll();
        }
    }

    /** Reads a cached profile for the nick, if the session stored one. */
    static GameProfile from_disk(String name) {
        if (!disk_ready) {
            return null;   // render thread: no read before the loader published the tree
        }
        try {
            // the tree is shared with resolve workers — read under the class lock
            synchronized (AccountRenderer.class) {
                if (disk_cache == null) {
                    return null;
                }
                JsonElement entry = disk_cache.getAsJsonObject("entries")
                        .get(name.toLowerCase(Locale.ROOT));
                if (entry == null || !entry.isJsonObject()) return null;
                JsonObject o = entry.getAsJsonObject();
                if (!o.has("uuid")) return null;
                GameProfile p = new GameProfile(uuid_from_hex(o.get("uuid").getAsString()),
                        o.has("name") ? o.get("name").getAsString() : name);
                if (o.has("textures")) {
                    p.getProperties().put("textures", new Property("textures", o.get("textures").getAsString(),
                            o.has("signature") && o.get("signature").isJsonPrimitive() ? o.get("signature").getAsString() : null));
                }
                return p;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Worker-only: wait (bounded) for the initial cache read, never the render thread. */
    private static void await_disk_load() {
        ensure_disk_load();
        if (disk_ready) {
            return;
        }
        synchronized (AccountRenderer.class) {
            long deadline = System.currentTimeMillis() + 3_000L;
            while (!disk_ready && System.currentTimeMillis() < deadline) {
                try {
                    AccountRenderer.class.wait(200L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void store_disk(String name, String uuid_hex, String real_name, String tex, String sig) {
        try {
            await_disk_load();
            String json;
            // read-modify-write of the shared tree stays atomic vs from_disk,
            // and the file write happens outside the lock (readers never wait on IO)
            synchronized (AccountRenderer.class) {
                if (disk_cache == null) {
                    return;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", uuid_hex);
                entry.addProperty("name", real_name);
                if (tex != null) entry.addProperty("textures", tex);
                if (sig != null) entry.addProperty("signature", sig);
                disk_cache.getAsJsonObject("entries").add(name.toLowerCase(Locale.ROOT), entry);
                json = GSON.toJson(disk_cache);
            }
            write_atomic(CACHE_FILE, json);
        } catch (Throwable ignored) {
            // cache write is best-effort
        }
    }

    /** tmp + rename: a crash mid-write must not leave a truncated cache behind. */
    private static void write_atomic(Path target, String json) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {
            // cache write is best-effort
        }
    }

    // ------------------------------------------------------ fake world

    /** Standalone world/handler so entities render on the title screen. */
    private static volatile ClientWorld preview_world;

    private static ClientWorld preview_world() {
        ClientWorld w = preview_world;
        if (w != null) {
            return w;
        }
        if (preview_world_failed) {
            return null;
        }
        // the fake world builds connection state objects: render thread only
        Thread here = Thread.currentThread();
        Thread owner = preview_thread;
        if (owner == null) {
            preview_thread = owner = here;
        } else if (owner != here) {
            return null;
        }
        synchronized (AccountRenderer.class) {
            if (preview_world != null) {
                return preview_world;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            GameProfile session = session_profile(mc);
            try {
                ClientConnection conn = new ClientConnection(NetworkSide.CLIENTBOUND);
                ClientConnectionState state = new ClientConnectionState(
                        session,
                        new WorldSession(TelemetrySender.NOOP, false, Duration.ofHours(1), "TabVisuals"),
                        previewRegistries(),
                        FeatureFlags.VANILLA_FEATURES,
                        "TabVisuals",
                        null, null, Map.of(), null, Map.of(),
                        ServerLinks.EMPTY);
                ClientPlayNetworkHandler handler = new ClientPlayNetworkHandler(mc, conn, state);
                DimensionType overworld = new DimensionType(
                        OptionalLong.empty(), true, false, false, true,
                        1.0, true, false, -64, 384, 384,
                        TagKey.of(RegistryKeys.BLOCK, Identifier.of("infiniburn_overworld")),
                        Identifier.of("overworld"), 0.0f,
                        new DimensionType.MonsterSettings(true, false, ConstantIntProvider.create(0), 0));
                preview_world = new ClientWorld(handler,
                        new ClientWorld.Properties(Difficulty.NORMAL, false, false),
                        World.OVERWORLD, RegistryEntry.of(overworld),
                        10, 0, mc.worldRenderer, false, 0L, 16777216);
                return preview_world;
            } catch (Throwable t) {
                // sticky: a retry per frame would rebuild-and-throw forever
                preview_world_failed = true;
                TabVisualsClient.LOGGER.error("Preview world creation failed", t);
                return null;
            }
        }
    }

    // --------------------------------------------------- preview registries

    /**
     * Ключи damage-типов, которые {@code DamageSources} тянет при ctor'е мира.
     * Значения — заглушки: preview-мир событий урона не порождает, важны только
     * присутствие ключей (иначе «Missing registry: damage_type»).
     */
    private static final List<RegistryKey<DamageType>> PREVIEW_DAMAGE_KEYS = List.of(
            DamageTypes.IN_FIRE, DamageTypes.CAMPFIRE, DamageTypes.LIGHTNING_BOLT,
            DamageTypes.ON_FIRE, DamageTypes.LAVA, DamageTypes.HOT_FLOOR,
            DamageTypes.IN_WALL, DamageTypes.CRAMMING, DamageTypes.DROWN,
            DamageTypes.STARVE, DamageTypes.CACTUS, DamageTypes.FALL,
            DamageTypes.ENDER_PEARL, DamageTypes.FLY_INTO_WALL, DamageTypes.OUT_OF_WORLD,
            DamageTypes.GENERIC, DamageTypes.MAGIC, DamageTypes.WITHER,
            DamageTypes.DRAGON_BREATH, DamageTypes.DRY_OUT, DamageTypes.SWEET_BERRY_BUSH,
            DamageTypes.FREEZE, DamageTypes.STALAGMITE, DamageTypes.OUTSIDE_BORDER,
            DamageTypes.GENERIC_KILL);

    /**
     * Менеджер реестров preview-мира. {@code ClientPlayNetworkHandler} ctor'у
     * нужны и статические реестры (ITEM — через {@code FuelRegistry.createDefault}),
     * и динамический DAMAGE_TYPE (World → DamageSources). Статические берутся
     * из общего менеджера статических реестров, DAMAGE_TYPE подменяется заглушкой:
     * preview-мир урона не порождает, а статические реестры не содержат
     * динамический DAMAGE_TYPE. Иначе создание превью падало и 3D-модель
     * мёртво показывала плейсхолдер.
     */
    private static DynamicRegistryManager.Immutable previewRegistries() {
        SimpleRegistry<DamageType> damage =
                new SimpleRegistry<>(RegistryKeys.DAMAGE_TYPE, Lifecycle.stable());
        for (RegistryKey<DamageType> key : PREVIEW_DAMAGE_KEYS) {
            damage.add(key,
                    new DamageType("generic", DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1f),
                    RegistryEntryInfo.DEFAULT);
        }
        return new PreviewRegistries(damage);
    }

    /** Статические реестры клиента (ITEM, BLOCK, ...) — общий источник для preview-менеджера. */
    private static final DynamicRegistryManager.Immutable STATIC_REGISTRIES =
            DynamicRegistryManager.of(Registries.REGISTRIES);

    /** Минимальный менеджер: DAMAGE_TYPE — заглушка, остальное — статические реестры. */
    private record PreviewRegistries(Registry<DamageType> damageTypes) implements DynamicRegistryManager.Immutable {
        @Override
        public <E> Optional<Registry<E>> getOptional(RegistryKey<? extends Registry<? extends E>> registryRef) {
            if (RegistryKeys.DAMAGE_TYPE.equals(registryRef)) {
                @SuppressWarnings("unchecked")
                Registry<E> cast = (Registry<E>) damageTypes;
                return Optional.of(cast);
            }
            // статические реестры (ITEM, BLOCK, ...) — из общего менеджера клиента
            return STATIC_REGISTRIES.getOptional(registryRef);
        }

        @Override
        public Stream<Entry<?>> streamAllRegistries() {
            return Stream.of(new Entry<>(RegistryKeys.DAMAGE_TYPE, damageTypes));
        }
    }

    // --------------------------------------------------------- entity

    /**
     * Player entity for the profile, cached per profile (LRU, render thread
     * only). The overrides keep the title screen safe: no tab list to query
     * (the vanilla path NPEs on a null network handler) and the skin comes
     * from the profile's textures property through the vanilla provider —
     * which caches textures per uuid, so evicted entities do not pile up GL
     * textures either.
     */
    private OtherClientPlayerEntity entity_for(GameProfile p) {
        if (p == null || p.getId() == null) {
            return null;
        }
        String key = p.getId().toString();
        OtherClientPlayerEntity cached = entities.get(key);
        if (cached != null) {
            return cached;
        }
        if (entity_failed.contains(key) || preview_world_failed) {
            return null;
        }
        ClientWorld world = preview_world();
        if (world == null) {
            return null;
        }
        try {
            OtherClientPlayerEntity created = new OtherClientPlayerEntity(world, p) {
                @Override
                protected PlayerListEntry getPlayerListEntry() {
                    return null; // no tab list on the title screen
                }

                @Override
                public net.minecraft.client.util.SkinTextures getSkinTextures() {
                    return MinecraftClient.getInstance().getSkinProvider()
                            .getSkinTextures(this.getGameProfile());
                }
            };
            entities.put(key, created);
            return created;
        } catch (Throwable t) {
            // sticky per profile: retry would log every frame
            entity_failed.add(key);
            TabVisualsClient.LOGGER.error("Preview entity creation failed", t);
            return null;
        }
    }

    // -------------------------------------------------------- rotation

    public void beginDrag(double mouseX, double mouseY) {
        dragging = true;
        yaw_vel = 0f;
        idle_time = 0f;
        last_drag_x = mouseX;
        last_drag_y = mouseY;
        last_drag_nanos = System.nanoTime();
    }

    public void endDrag() {
        dragging = false;
        idle_time = 0f;   // inertia first, the snap-back waits out SNAP_AFTER
    }

    /** Drag deltas: horizontal spins the body, vertical tilts the model. */
    public void drag(double mouseX, double mouseY) {
        if (!dragging || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) {
            return;
        }
        float dx = (float) (mouseX - last_drag_x);
        float dy = (float) (mouseY - last_drag_y);
        last_drag_x = mouseX;
        last_drag_y = mouseY;
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) {
            return;
        }
        long now = System.nanoTime();
        double secs = (now - last_drag_nanos) / 1.0e9;
        last_drag_nanos = now;
        float d_yaw = -dx * 0.9f;
        yaw += d_yaw;
        tilt = clamp(tilt - dy * 0.45f, -MAX_TILT, MAX_TILT);
        idle_time = 0f;
        // angular velocity for the release inertia — EMA, mouse deltas are bursty
        if (secs > 1.0e-4 && secs < 0.5) {
            float raw = (float) (d_yaw / secs);
            if (Float.isFinite(raw)) {
                yaw_vel = clamp(yaw_vel * 0.55f + raw * 0.45f, -MAX_SPIN, MAX_SPIN);
            }
        } else if (yaw_vel != 0f) {
            yaw_vel *= 0.3f;   // a pause mid-drag kills the stale momentum
        }
    }

    /** FPS-independent easing + crossfade; called once per frame from the screen. */
    private void step(float dt) {
        if (!Float.isFinite(dt) || dt < 0f) {
            dt = 0f;
        }
        if (dt > 0.25f) {
            dt = 0.25f;   // a hitch must not teleport the model
        }
        anim_time += dt;
        if (anim_time > 600f) {
            anim_time -= 600f;   // keep sin() in a precise range
        }
        // one bad drag must not poison the matrix forever
        if (!Float.isFinite(yaw)) yaw = NEUTRAL_YAW;
        if (!Float.isFinite(tilt)) tilt = 0f;
        if (!Float.isFinite(yaw_vel)) yaw_vel = 0f;

        // swap in a profile resolved on a worker thread (render thread only)
        GameProfile fresh = pending_profile;
        if (fresh != null && fresh.getId() != null && fresh.getName() != null
                && fresh.getName().equalsIgnoreCase(nick)) {
            profile = fresh;
            pending_profile = null;
        }
        sync_resolve(dt);
        render_entity = entity_for(profile);

        String key = nick_key();
        boolean settled = !nick.isEmpty() && (has_textures(profile)
                || MISSING.contains(key) || PROFILE_CACHE.containsKey(key));
        show_placeholder = !nick.isEmpty() && (render_entity == null || !settled);
        show_error = show_placeholder && RESOLVE_FAILED.containsKey(key);

        float target = show_placeholder ? 0f : 1f;
        float rate = target > skin_a ? 5.5f : 7.5f;   // in slower than out, no flicker
        skin_a += (target - skin_a) * (1f - (float) Math.exp(-dt * rate));
        if (Math.abs(target - skin_a) < 0.004f) {
            skin_a = target;
        }
        if (!Float.isFinite(skin_a)) skin_a = target;
        skin_a = clamp(skin_a, 0f, 1f);

        if (dragging) {
            idle_time = 0f;
        } else {
            idle_time += dt;
            if (yaw_vel != 0f) {
                if (idle_time < SNAP_AFTER + 1.2f) {
                    yaw += yaw_vel * dt;
                    yaw_vel *= (float) Math.exp(-dt * 4.5f);
                    if (Math.abs(yaw_vel) < 1.5f) yaw_vel = 0f;
                } else {
                    yaw_vel = 0f;
                }
            }
            if (idle_time >= SNAP_AFTER) {
                // ease back to the nearest neutral angle (front-facing)
                float target_yaw = NEUTRAL_YAW + 360f * Math.round((yaw - NEUTRAL_YAW) / 360f);
                if (Float.isFinite(target_yaw)) {
                    yaw += (target_yaw - yaw) * (1f - (float) Math.exp(-dt * 6f));
                    if (Math.abs(target_yaw - yaw) < 0.05f) yaw = target_yaw;
                }
                tilt += (0f - tilt) * (1f - (float) Math.exp(-dt * 8f));
                if (Math.abs(tilt) < 0.05f) tilt = 0f;
            }
        }
        tilt = clamp(tilt, -MAX_TILT, MAX_TILT);

        float k = 1f - (float) Math.exp(-dt * 2.5f);
        idle_blend += ((dragging ? 0f : 1f) - idle_blend) * k;
        boolean at_rest = !dragging && idle_time >= SNAP_AFTER + 0.4f
                && Math.abs(yaw - NEUTRAL_YAW) < 1.5f && Math.abs(tilt) < 1f;
        rest_blend += ((at_rest ? 1f : 0f) - rest_blend) * k;
        idle_blend = clamp(idle_blend, 0f, 1f);
        rest_blend = clamp(rest_blend, 0f, 1f);
    }

    // ---------------------------------------------------------- render

    /**
     * Draws the full body inside the given box (the caller owns the panel):
     * violet pedestal (radial glow + rounded vignette + floor shadow), the
     * model with studio lighting, and the placeholder states — everything is
     * derived from the box, no extra parameters needed.
     */
    public void render(DrawContext ctx, MatrixStack m, float x, float y, float w, float h, float dt) {
        step(dt);
        if (!(w > 0f) || !(h > 0f)) {
            return;
        }

        // half-pixel snap: the pedestal stays crisp at every GUI scale
        float cx = Math.round((x + w / 2f) * 2f) / 2f;
        float cy = Math.round((y + h / 2f) * 2f) / 2f;
        float wave = (float) (Math.PI * 2.0 / BOB_PERIOD);
        float bob = (float) Math.sin(anim_time * wave) * BOB_PX * idle_blend;

        draw_pedestal(m, x, y, w, h, cx, cy, wave);

        OtherClientPlayerEntity entity = render_entity;
        if (entity != null) {
            float size = clamp(Math.min(h * 0.52f, w * 0.75f), 6f, 512f);
            float feet_y = cy + 0.9f * size + bob;
            draw_floor_shadow(m, cx, feet_y, size, skin_a);

            // breathing: a slow sway around the neutral pose, only when settled
            float body_yaw = yaw + SWAY_DEG * (float) Math.sin(anim_time * wave + 1.1f) * rest_blend;
            float tilt_deg = clamp(tilt
                            + BREATH_TILT_DEG * (float) Math.sin(anim_time * wave + 0.4f) * rest_blend,
                    -MAX_TILT - 2f, MAX_TILT + 2f);
            draw_model(ctx, m, entity, cx, cy, size, body_yaw, tilt_deg);
        }

        // crossfade veil: the panel colour hides the model until it settles
        if (entity != null && skin_a < 0.999f) {
            UiRender.rect(m, x, y, w, h, 10,
                    UiRender.withAlpha(PANEL_VEIL, (int) ((1f - skin_a) * 245f)));
        }

        float placeholder_a = 1f - skin_a;
        if (show_placeholder && placeholder_a > 0.01f) {
            // the placeholder sits in its own halo so the pedestal never dies
            UiRender.glow(m, cx, cy, Math.min(w, h) * 0.45f, ACCENT, 0.11f, placeholder_a);
            draw_placeholder(m, x, y, w, h, cx, cy, placeholder_a);
        }
    }

    /** Radial glow + rounded vignette + hairline: the studio pedestal. */
    private void draw_pedestal(MatrixStack m, float x, float y, float w, float h,
                               float cx, float cy, float wave) {
        float breath = 1f + 0.06f * (float) Math.sin(anim_time * wave);
        float peak = (0.13f + 0.07f * skin_a) * breath;
        UiRender.glow(m, cx, cy - h * 0.08f, Math.min(w, h) * 0.62f, ACCENT, peak, 1f);

        float d = Math.min(w, h) * 0.26f;
        if (d > 1.5f) {
            int top = UiRender.withAlpha(0x07040F, 62);
            int bottom = UiRender.withAlpha(0x07040F, 96);
            UiRender.gradient(m, x, y, w, d, top, 0, 0, top);
            UiRender.gradient(m, x, y + h - d, w, d, 0, bottom, bottom, 0);
            UiRender.gradient(m, x, y, d, h, top, bottom, 0, 0);
            UiRender.gradient(m, x + w - d, y, d, h, 0, 0, bottom, top);
        }
        UiRender.outline(m, x, y, w, h, 10,
                UiRender.withAlpha(0xFFB49AFF, (int) (26f + 30f * skin_a)), 1f);
    }

    /** Soft elliptical floor shadow: three stacked ellipses, dark violet. */
    private static void draw_floor_shadow(MatrixStack m, float cx, float feet_y, float size, float alpha) {
        if (!(alpha > 0.01f) || !(size > 0f)) {
            return;
        }
        float w0 = size * 0.80f;
        float h0 = Math.max(4f, size * 0.17f);
        UiRender.rect(m, cx - w0 / 2f, feet_y - h0 / 2f, w0, h0, h0 / 2f,
                UiRender.withAlpha(0xFF171034, (int) (40f * alpha)));
        float w1 = w0 * 0.70f;
        float h1 = h0 * 0.72f;
        UiRender.rect(m, cx - w1 / 2f, feet_y - h1 / 2f, w1, h1, h1 / 2f,
                UiRender.withAlpha(0xFF100B24, (int) (72f * alpha)));
        float w2 = w0 * 0.42f;
        float h2 = h0 * 0.42f;
        UiRender.rect(m, cx - w2 / 2f, feet_y - h2 / 2f, w2, h2, h2 / 2f,
                UiRender.withAlpha(0xFF090615, (int) (110f * alpha)));
    }

    /** Pulsing USER glyph while fetching (and NODE badge when the fetch failed). */
    private void draw_placeholder(MatrixStack m, float x, float y, float w, float h,
                                  float cx, float cy, float alpha) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim_time * 2.4f);
        float size = clamp(Math.min(w, h) * 0.34f, 12f, 30f);
        int col = UiRender.withAlpha(0xFFD7CBFF, (int) (alpha * (120f + 80f * pulse)));
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(1f + 0.05f * pulse, 1f + 0.05f * pulse, 1f);
        m.translate(-cx, -cy, 0f);
        // drawCentered centres by the text box; +3/+height/4 lands the glyph on cy
        Icons.drawCentered(m, Icons.Glyph.USER_OUTLINE, cx,
                cy + 3f + Icons.height(Icons.Glyph.USER_OUTLINE, size) * 0.25f, size, col);
        m.pop();

        if (show_error) {
            float bx = x + w - 13f;
            float by = y + 13f;
            float r = 9.5f;
            UiRender.rect(m, bx - r, by - r, r * 2f, r * 2f, r,
                    UiRender.withAlpha(0x3A0B0618, (int) (alpha * 235f)));
            UiRender.outline(m, bx - r, by - r, r * 2f, r * 2f, r,
                    UiRender.withAlpha(0xFFE05B5B, (int) (alpha * (110f + 60f * pulse))), 1f);
            float ns = 11f;
            Icons.drawCentered(m, Icons.Glyph.NODE, bx,
                    by + 3f + Icons.height(Icons.Glyph.NODE, ns) * 0.25f, ns,
                    UiRender.withAlpha(0xFFFFC9C9, (int) (alpha * (150f + 70f * pulse))));
        }
    }

    /** Saves the entity pose, draws it, restores everything even on failure. */
    private void draw_model(DrawContext ctx, MatrixStack m, OtherClientPlayerEntity entity,
                            float cx, float cy, float size, float body_yaw, float tilt_deg) {
        float prev_body = entity.bodyYaw;
        float prev_yaw = entity.getYaw();
        float prev_pitch = entity.getPitch();
        float prev_head = entity.headYaw;
        float prev_prev_head = entity.prevHeadYaw;
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        Quaternionf prev_rot = null;
        try {
            Quaternionf current_rot = dispatcher.getRotation();
            prev_rot = current_rot == null ? null : new Quaternionf(current_rot);
            entity.bodyYaw = body_yaw;
            entity.setYaw(body_yaw);
            entity.headYaw = body_yaw;
            entity.prevHeadYaw = body_yaw;
            entity.setPitch(-tilt_deg); // model tilt is carried by the quaternion

            Quaternionf rot_z = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf rot_x = new Quaternionf().rotateX((float) Math.toRadians(tilt_deg));
            Quaternionf combined = new Quaternionf(rot_z).mul(rot_x);
            float entity_scale = entity.getScale();
            if (!(entity_scale > 0.01f)) {
                entity_scale = 1f;
            }
            Vector3f offset = new Vector3f(0f, entity.getHeight() / 2f, 0f);
            draw_preview_entity(ctx, m, cx, cy, size / entity_scale, offset, combined, rot_x,
                    tilt_deg, entity);
        } catch (Throwable t) {
            // a failing preview must never take the title screen down with it
            if (!render_error_logged) {
                render_error_logged = true;
                TabVisualsClient.LOGGER.error("Preview entity render failed", t);
            }
        } finally {
            if (prev_rot != null) {
                dispatcher.setRotation(prev_rot);
            }
            entity.bodyYaw = prev_body;
            entity.setYaw(prev_yaw);
            entity.setPitch(prev_pitch);
            entity.headYaw = prev_head;
            entity.prevHeadYaw = prev_prev_head;
        }
    }

    /**
     * InventoryScreen.drawEntity, transcribed 1:1 from vanilla bytecode, with
     * one change: the shader key light is rotated with the model tilt — the
     * recipe vanilla's own draggable PlayerSkinWidget uses — so the face keeps
     * its light while dragged. At rest the rotation is the identity, i.e. the
     * exact vanilla inventory lighting. Light stays MAX (the title screen
     * lightmap is the white default, any lower value would read flat grey).
     */
    private static void draw_preview_entity(DrawContext ctx, MatrixStack matrices, float cx, float cy,
                                            float size, Vector3f offset, Quaternionf rotation,
                                            Quaternionf camera_orientation, float tilt_deg,
                                            OtherClientPlayerEntity entity) {
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        matrices.push();
        try {
            matrices.translate(cx, cy, 50.0);
            matrices.scale(size, size, -size);
            matrices.translate(offset.x, offset.y, offset.z);
            matrices.multiply(rotation);
            ctx.draw();   // flush the GUI quads the pedestal was batched into
            DiffuseLighting.method_56819(new Quaternionf().rotateX((float) Math.toRadians(tilt_deg)));
            if (camera_orientation != null) {
                dispatcher.setRotation(new Quaternionf(camera_orientation).conjugate()
                        .rotateY(3.1415927f));
            }
            dispatcher.setRenderShadows(false);
            ctx.draw(vcp -> dispatcher.render(entity, 0.0, 0.0, 0.0, 1.0f,
                    ctx.getMatrices(), vcp, 15728880));
            ctx.draw();
        } finally {
            dispatcher.setRenderShadows(true);
            matrices.pop();
            DiffuseLighting.method_34742();           // vanilla entity lights back
            DiffuseLighting.enableGuiDepthLighting(); // same tail as drawEntity
        }
    }

    private static float clamp(float v, float min, float max) {
        // NaN в min: сравнения с NaN — false, и NaN уходил в glow/альфы/координаты
        if (Float.isNaN(v)) return min;
        return v < min ? min : v > max ? max : v;
    }

}
