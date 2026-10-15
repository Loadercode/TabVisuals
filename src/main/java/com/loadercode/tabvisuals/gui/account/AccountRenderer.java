// language: Java, file: AccountRenderer.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.UiRender;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.telemetry.TelemetrySender;
import net.minecraft.client.session.telemetry.WorldSession;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-body 3D player skin preview for the account manager: the whole model
 * (head to feet) rendered via the vanilla inventory entity pass, rotatable by
 * mouse drag (drag spins yaw / tilts the model, release eases back to a
 * neutral angle). The skin follows a nickname — the session profile by
 * default, any typed/hovered nick resolved through the Mojang session servers
 * (profile → textures property → textures.minecraft.net); resolved profiles
 * are cached in memory and on disk, and a nick without a skin falls back to
 * the default steve/alex model.
 */
public final class AccountRenderer {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Path CACHE_FILE = FabricLoader.getInstance().getGameDir()
            .resolve("config").resolve("tabvisuals").resolve("skin_cache.json");

    /** Neutral angle: the model faces the viewer (vanilla inventory pose). */
    private static final float NEUTRAL_YAW = 180f;
    private static final float MAX_TILT = 28f;

    // ------------------------------------------------- profile resolution

    /** Resolved profiles of this session: lowercase nick → profile. */
    private static final Map<String, GameProfile> PROFILE_CACHE = new ConcurrentHashMap<>();
    /** Nicks a fetch is already running for (no duplicate threads). */
    private static final Set<String> RESOLVING = ConcurrentHashMap.newKeySet();
    /** Epoch ms of the last failed fetch per nick — drives the retry backoff. */
    private static final Map<String, Long> RESOLVE_FAILED = new ConcurrentHashMap<>();
    /** One shared client — a new HttpClient per fetch leaks selector threads. */
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** Retry a failed nick resolve after this long (the nick stays selected). */
    private static final long RESOLVE_RETRY_MS = 15_000L;
    /** Disk cache, lazily loaded once: lowercase nick → json entry. */
    private static volatile JsonObject diskCache;

    // ------------------------------------------------------ runtime state

    /** Volatile: resolve worker threads read it to decide the pending swap. */
    private volatile String nick = "";
    private GameProfile profile;
    /** Freshly resolved profile waiting for the render thread to swap it in. */
    private volatile GameProfile pendingProfile;
    private final Map<String, OtherClientPlayerEntity> entities = new HashMap<>();
    /** Profiles whose entity creation threw once — never retried (no log spam). */
    private final Set<String> entityFailed = ConcurrentHashMap.newKeySet();
    /** Sticky: the fake world failed to build — the preview stays off. */
    private static volatile boolean previewWorldFailed;
    /** Log the preview render failure once, not once per frame. */
    private boolean renderErrorLogged;

    // rotation / animation state
    private float yaw = NEUTRAL_YAW;
    private float tilt;
    private boolean dragging;
    private double lastDragX, lastDragY;
    /** Crossfade 0..1 — fades the model in after a nick switch. */
    private float switchT = 1f;

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
        switchT = 0f;
        if (n.isEmpty()) {
            profile = null;
            pendingProfile = null;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Session session = mc.getSession();
        if (session != null && session.getUsername().equalsIgnoreCase(n)) {
            // the real session account — its profile already carries the textures
            profile = sessionProfile(mc);
            pendingProfile = null;
            return;
        }
        String key = n.toLowerCase(Locale.ROOT);
        GameProfile cached = PROFILE_CACHE.get(key);
        if (cached == null) {
            // profiles persisted by a previous session — instant, no refetch
            GameProfile disk = fromDisk(key);
            if (disk != null) {
                cached = disk;
                PROFILE_CACHE.put(key, disk);
            }
        }
        if (cached != null) {
            profile = cached;
            pendingProfile = null;
            return;
        }
        // unknown nick — show the offline (default-skin) model right away and
        // resolve the Mojang profile in the background
        profile = offlineProfile(n);
        pendingProfile = null;
        Long failedAt = RESOLVE_FAILED.get(key);
        // no attempt yet, or the last one failed long enough ago to retry
        if (failedAt == null || System.currentTimeMillis() - failedAt >= RESOLVE_RETRY_MS) {
            startResolve(n, key);
        }
    }

    /** Spawns one background resolve for the nick (deduped via RESOLVING). */
    private void startResolve(String name, String key) {
        if (!RESOLVING.add(key)) {
            return;
        }
        Thread t = new Thread(() -> resolveRemote(name), "tabvisuals-skin");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Re-requests a nick whose fetch failed (offline launch, rate limit): the
     * offline model stays until a retry succeeds. Called per frame; the backoff
     * + RESOLVING gate keep it to at most one request per RESOLVE_RETRY_MS.
     */
    private void retryResolve() {
        String n = nick;
        if (n.isEmpty() || PROFILE_CACHE.containsKey(n.toLowerCase(Locale.ROOT))) {
            return;
        }
        Session s = MinecraftClient.getInstance().getSession();
        if (s != null && s.getUsername().equalsIgnoreCase(n)) {
            return;   // the session account renders from its own profile
        }
        String key = n.toLowerCase(Locale.ROOT);
        Long failedAt = RESOLVE_FAILED.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt >= RESOLVE_RETRY_MS) {
            RESOLVE_FAILED.remove(key, failedAt);
            startResolve(n, key);
        }
    }

    /** Offline-steve profile for a nick — the degradation path. */
    private static GameProfile offlineProfile(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, name);
    }

    /** MinecraftClient.gameProfileFuture, located once by its generic type. */
    private static Field profileFutureField;
    private static boolean profileFutureFieldFailed;

    /**
     * Non-blocking stand-in for MinecraftClient.getGameProfile(): that one
     * joins gameProfileFuture, which runs an uncached sessionserver fetch —
     * on a dead network the render thread freezes, and a failed fetch makes
     * join() throw. Uses the resolved value only when it is already done and
     * still belongs to the current session (AccountManager.apply swaps the
     * session, the stale future keeps the old account); otherwise builds a
     * plain uuid+name profile from the session — same fallback as vanilla.
     */
    private static GameProfile sessionProfile(MinecraftClient mc) {
        Session s = mc.getSession();
        if (s == null) {
            return null;
        }
        UUID uuid = s.getUuidOrNull();
        try {
            Field f = profileFutureField();
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
    private static Field profileFutureField() {
        Field f = profileFutureField;
        if (f == null && !profileFutureFieldFailed) {
            for (Field declared : MinecraftClient.class.getDeclaredFields()) {
                if (declared.getType() != CompletableFuture.class) {
                    continue;
                }
                Type generic = declared.getGenericType();
                if (generic instanceof ParameterizedType pt
                        && pt.getActualTypeArguments()[0] == ProfileResult.class) {
                    declared.setAccessible(true);
                    profileFutureField = f = declared;
                    break;
                }
            }
            if (f == null) {
                profileFutureFieldFailed = true;
            }
        }
        return f;
    }

    /**
     * api.mojang.com name→uuid → sessionserver profile (textures property) →
     * memory + disk cache. Runs on a worker thread; the render thread picks
     * the result up through {@link #pendingProfile}.
     */
    private void resolveRemote(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        try {
            String uuidHex = fetchJson(HTTP,
                    "https://api.mojang.com/users/profiles/minecraft/" + name, "id");
            if (uuidHex == null || uuidHex.length() != 32) {
                RESOLVE_FAILED.put(key, System.currentTimeMillis());
                return;
            }
            JsonObject sessionJson = fetchJsonObj(HTTP,
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidHex + "?unsigned=false");
            if (sessionJson == null) {
                RESOLVE_FAILED.put(key, System.currentTimeMillis());
                return;
            }
            String realName = sessionJson.has("name") && sessionJson.get("name").isJsonPrimitive()
                    ? sessionJson.get("name").getAsString() : name;
            GameProfile p = new GameProfile(uuidFromHex(uuidHex), realName);
            String texValue = null, texSignature = null;
            if (sessionJson.has("properties") && sessionJson.get("properties").isJsonArray()) {
                for (JsonElement el : sessionJson.getAsJsonArray("properties")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject prop = el.getAsJsonObject();
                    if (prop.has("name") && "textures".equals(prop.get("name").getAsString())) {
                        texValue = prop.has("value") ? prop.get("value").getAsString() : null;
                        texSignature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                    }
                }
            }
            if (texValue != null) {
                p.getProperties().put("textures", new Property("textures", texValue, texSignature));
            }
            PROFILE_CACHE.put(key, p);
            RESOLVE_FAILED.remove(key);
            storeDisk(name, uuidHex, realName, texValue, texSignature);
            if (name.equalsIgnoreCase(nick)) {
                pendingProfile = p;
            }
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.warn("Skin resolve failed for {}: {}", name, t.toString());
            RESOLVE_FAILED.put(key, System.currentTimeMillis());
        } finally {
            RESOLVING.remove(key);   // a failed fetch must not pin the nick offline forever
        }
    }

    private static String fetchJson(HttpClient http, String url, String field) {
        JsonObject obj = fetchJsonObj(http, url);
        return obj != null && obj.has(field) && obj.get(field).isJsonPrimitive()
                ? obj.get(field).getAsString() : null;
    }

    private static JsonObject fetchJsonObj(HttpClient http, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return null;
            }
            JsonElement el = GSON.fromJson(resp.body(), JsonElement.class);
            return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Hex uuid (32 chars) → dashed UUID. */
    private static UUID uuidFromHex(String hex) {
        String d = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
        return UUID.fromString(d);
    }

    // --------------------------------------------------------- disk cache

    private static JsonObject diskCache() {
        JsonObject root = diskCache;
        if (root == null) {
            synchronized (AccountRenderer.class) {
                if (diskCache == null) {
                    JsonObject loaded = null;
                    if (Files.exists(CACHE_FILE)) {
                        try {
                            loaded = GSON.fromJson(Files.readString(CACHE_FILE, StandardCharsets.UTF_8), JsonObject.class);
                        } catch (Throwable ignored) {
                            // corrupt cache — rebuild from scratch
                        }
                    }
                    diskCache = loaded != null ? loaded : new JsonObject();
                    // a hand-edited/corrupt "entries" node must not throw on
                    // every later read (getAsJsonObject is type-checked)
                    if (!(diskCache.get("entries") instanceof JsonObject)) {
                        diskCache.add("entries", new JsonObject());
                    }
                }
                root = diskCache;
            }
        }
        return root;
    }

    /** Reads a cached profile for the nick, if the session stored one. */
    static GameProfile fromDisk(String name) {
        try {
            // the tree is shared with resolve workers — read under the class lock
            synchronized (AccountRenderer.class) {
                JsonElement entry = diskCache().getAsJsonObject("entries")
                        .get(name.toLowerCase(Locale.ROOT));
                if (entry == null || !entry.isJsonObject()) return null;
                JsonObject o = entry.getAsJsonObject();
                if (!o.has("uuid")) return null;
                GameProfile p = new GameProfile(uuidFromHex(o.get("uuid").getAsString()),
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

    private static void storeDisk(String name, String uuidHex, String realName, String tex, String sig) {
        try {
            // read-modify-write of the shared tree must stay atomic vs fromDisk
            synchronized (AccountRenderer.class) {
                JsonObject cache = diskCache();
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", uuidHex);
                entry.addProperty("name", realName);
                if (tex != null) entry.addProperty("textures", tex);
                if (sig != null) entry.addProperty("signature", sig);
                cache.getAsJsonObject("entries").add(name.toLowerCase(Locale.ROOT), entry);
                saveDisk(cache);
            }
        } catch (Throwable ignored) {
            // cache write is best-effort
        }
    }

    private static synchronized void saveDisk(JsonObject cache) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Files.writeString(CACHE_FILE, GSON.toJson(cache), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // cache write is best-effort
        }
    }

    // ------------------------------------------------------ fake world

    /** Standalone world/handler so entities render on the title screen. */
    private static volatile ClientWorld previewWorld;

    private static ClientWorld previewWorld() {
        ClientWorld w = previewWorld;
        if (w != null) {
            return w;
        }
        if (previewWorldFailed) {
            return null;
        }
        synchronized (AccountRenderer.class) {
            if (previewWorld != null) {
                return previewWorld;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            GameProfile session = sessionProfile(mc);
            try {
                ClientConnection conn = new ClientConnection(NetworkSide.CLIENTBOUND);
                ClientConnectionState state = new ClientConnectionState(
                        session,
                        new WorldSession(TelemetrySender.NOOP, false, Duration.ofHours(1), "TabVisuals"),
                        DynamicRegistryManager.of(Registries.REGISTRIES),
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
                previewWorld = new ClientWorld(handler,
                        new ClientWorld.Properties(Difficulty.NORMAL, false, false),
                        World.OVERWORLD, RegistryEntry.of(overworld),
                        10, 0, mc.worldRenderer, false, 0L, 16777216);
                return previewWorld;
            } catch (Throwable t) {
                // sticky: a retry per frame would rebuild-and-throw forever
                previewWorldFailed = true;
                TabVisualsClient.LOGGER.error("Preview world creation failed", t);
                return null;
            }
        }
    }

    // --------------------------------------------------------- entity

    /**
     * Player entity for the profile, cached per profile. The overrides keep
     * the title screen safe: no tab list to query (the vanilla path NPEs on a
     * null network handler) and the skin comes from the profile's textures
     * property through the vanilla provider (async fetch + disk cache).
     */
    private OtherClientPlayerEntity entityFor(GameProfile p) {
        if (p == null || p.getId() == null) {
            return null;
        }
        String key = p.getId().toString();
        OtherClientPlayerEntity cached = entities.get(key);
        if (cached != null) {
            return cached;
        }
        if (entityFailed.contains(key) || previewWorldFailed) {
            return null;
        }
        ClientWorld world = previewWorld();
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
            // sticky per profile: computeIfAbsent-style retry would log every frame
            entityFailed.add(key);
            TabVisualsClient.LOGGER.error("Preview entity creation failed", t);
            return null;
        }
    }

    // -------------------------------------------------------- rotation

    public void beginDrag(double mouseX, double mouseY) {
        dragging = true;
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    public void endDrag() {
        dragging = false;
    }

    /** Drag deltas: horizontal spins the body, vertical tilts the model. */
    public void drag(double mouseX, double mouseY) {
        if (!dragging || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) {
            return;
        }
        float dx = (float) (mouseX - lastDragX);
        float dy = (float) (mouseY - lastDragY);
        lastDragX = mouseX;
        lastDragY = mouseY;
        yaw -= dx * 0.9f;
        tilt = clamp(tilt - dy * 0.45f, -MAX_TILT, MAX_TILT);
    }

    /** FPS-independent easing; called once per frame from the screen. */
    private void update(float dt) {
        if (!Float.isFinite(yaw)) {
            yaw = NEUTRAL_YAW;   // one bad drag must not poison the matrix forever
        }
        if (!Float.isFinite(tilt)) {
            tilt = 0f;
        }
        if (!Float.isFinite(dt) || dt < 0f) {
            dt = 0f;
        }
        if (switchT < 1f) {
            switchT = Math.min(1f, switchT + dt * 3.2f);
        }
        if (dragging) {
            return;
        }
        // ease back to the nearest neutral angle (front-facing)
        float target = NEUTRAL_YAW + 360f * Math.round((yaw - NEUTRAL_YAW) / 360f);
        yaw += (target - yaw) * (1f - (float) Math.exp(-dt * 9f));
        tilt += (0f - tilt) * (1f - (float) Math.exp(-dt * 11f));
    }

    // ---------------------------------------------------------- render

    /**
     * Draws the full body inside the given box (the caller owns the panel).
     * The model is centered, aspect never squashed (uniform scale from the
     * box), and a soft shadow disc grounds the feet.
     */
    public void render(DrawContext ctx, MatrixStack m, float x, float y, float w, float h, float dt) {
        update(dt);
        // swap in a profile resolved on a worker thread (render thread only!)
        GameProfile fresh = pendingProfile;
        if (fresh != null && fresh.getName() != null && fresh.getName().equalsIgnoreCase(nick)) {
            profile = fresh;
            pendingProfile = null;
        }
        retryResolve();
        OtherClientPlayerEntity entity = entityFor(profile);
        if (entity == null) {
            return;
        }

        float cx = x + w / 2f;
        float cy = y + h / 2f;
        // uniform scale: full 1.8-block body fits the box, never wider than it
        float size = Math.min(h * 0.52f, w * 0.75f);
        float feetY = cy + 0.9f * size;

        // ground shadow under the feet (the vanilla pass draws no shadows)
        float shadowW = size * 0.62f;
        UiRender.rect(m, cx - shadowW / 2f, feetY - 2f, shadowW, 4.5f,
                2.2f, fade(0x583B2A66));

        float prevBody = entity.bodyYaw;
        float prevYaw = entity.getYaw();
        float prevPitch = entity.getPitch();
        float prevHead = entity.headYaw;
        float prevPrevHead = entity.prevHeadYaw;
        entity.bodyYaw = yaw;
        entity.setYaw(yaw);
        entity.headYaw = yaw;
        entity.prevHeadYaw = yaw;
        entity.setPitch(-tilt); // model tilt is carried by the quaternion

        Quaternionf rotZ = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf rotX = new Quaternionf().rotateX((float) Math.toRadians(tilt));
        Quaternionf combined = new Quaternionf(rotZ).mul(rotX);
        float scale = size / entity.getScale();
        Vector3f offset = new Vector3f(0f, entity.getHeight() / 2f, 0f);
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        Quaternionf prevRot = new Quaternionf(dispatcher.getRotation());
        try {
            InventoryScreen.drawEntity(ctx, cx, cy, scale, offset, combined, rotX, entity);
        } catch (Throwable t) {
            // vanilla drawEntity sets the global dispatcher rotation and never
            // restores it — keep the log quiet after the first failure
            if (!renderErrorLogged) {
                renderErrorLogged = true;
                TabVisualsClient.LOGGER.error("Preview entity render failed", t);
            }
        }
        dispatcher.setRotation(prevRot);
        entity.bodyYaw = prevBody;
        entity.setYaw(prevYaw);
        entity.setPitch(prevPitch);
        entity.headYaw = prevHead;
        entity.prevHeadYaw = prevPrevHead;

        // crossfade veil while the skin swaps after a nick change
        if (switchT < 0.999f) {
            UiRender.rect(m, x, y, w, h, 8, fadeVeil());
        }
    }

    /** Panel-colored veil alpha for the nick-switch crossfade. */
    private int fadeVeil() {
        int a = (int) ((1f - switchT) * 255f) & 0xFF;
        return (a << 24) | 0x12142E;
    }

    private int fade(int argb) {
        int a = (int) (((argb >>> 24) & 0xFF) * switchT) & 0xFF;
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }

}
