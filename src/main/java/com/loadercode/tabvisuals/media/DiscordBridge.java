// language: Java, file: DiscordBridge.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadercode.tabvisuals.TabVisualsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Discord local RPC (IPC over named pipes): performs the handshake, reads the
 * READY payload and exposes the signed-in user's display name and a circular
 * avatar texture for the title screen profile row. Connection-only — no
 * activity is published.
 */
public final class DiscordBridge {

    private static final String CLIENT_ID = "180984871685062656";
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_PING = 2;
    private static final int OP_PONG = 3;
    private static final int OP_CLOSE = 4;
    private static final int OP_HELLO = 5;

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile boolean ready;
    private static volatile String nick = "";
    private static volatile String globalName = "";
    private static volatile Identifier avatar;
    private static volatile String avatarUrl = "";

    private DiscordBridge() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread t = new Thread(DiscordBridge::run, "tabvisuals-discord");
        t.setDaemon(true);
        t.start();
    }

    public static boolean isReady() {
        return ready;
    }

    /** Prefer global name, then username — empty when Discord is not connected. */
    public static String nick() {
        String g = globalName;
        return g != null && !g.isBlank() ? g : nick;
    }

    public static Identifier avatar() {
        return avatar;
    }

    private static void run() {
        while (!Thread.currentThread().isInterrupted()) {
            boolean connected = false;
            for (int i = 0; i < 10 && !connected; i++) {
                try (RandomAccessFile pipe = openPipe(i)) {
                    connected = session(pipe);
                } catch (IOException e) {
                    // pipe not present / Discord gone — try next slot
                }
            }
            if (ready) TabVisualsClient.LOGGER.info("Discord IPC disconnected");
            ready = false;
            try {
                Thread.sleep(connected ? 3000 : 15000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static RandomAccessFile openPipe(int index) throws IOException {
        return new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + index, "rw");
    }

    /** Runs one IPC session until the pipe closes. Returns true on clean close. */
    private static boolean session(RandomAccessFile pipe) throws IOException {
        writeFrame(pipe, OP_HANDSHAKE,
                ("{\"v\":1,\"client_id\":\"" + CLIENT_ID + "\"}").getBytes(StandardCharsets.UTF_8));

        while (true) {
            int[] header = readHeader(pipe);
            if (header == null) return false;
            int op = header[0], len = header[1];
            if (len < 0 || len > 1 << 20) return false;
            byte[] payload = new byte[len];
            pipe.readFully(payload);
            if (op == OP_HELLO) {
                continue; // handshake response comes as a frame
            }
            if (op == OP_PING) {
                writeFrame(pipe, OP_PONG, payload);
                continue;
            }
            if (op == OP_CLOSE) return true;
            if (op != OP_FRAME) continue;
            JsonObject msg = parse(payload);
            if (msg == null) continue;
            String evt = msg.has("evt") && msg.get("evt").isJsonPrimitive() ? msg.get("evt").getAsString() : "";
            if ("READY".equals(evt)) {
                handleReady(msg);
                // READY is only the first event — keep the pipe alive and stay in the
                // read loop so we don't churn reconnects (and re-download the avatar)
                continue;
            }
            if ("ERROR".equals(evt)) {
                TabVisualsClient.LOGGER.debug("Discord IPC error: {}", msg);
                return false;
            }
        }
    }

    private static void handleReady(JsonObject msg) {
        try {
            // READY is a DISPATCH event: {"cmd":"DISPATCH","data":{...,"user":{...}},"evt":"READY"}
            JsonObject data = msg.has("payload") ? msg.getAsJsonObject("payload").getAsJsonObject("data")
                                                 : msg.getAsJsonObject("data");
            JsonObject user = data.getAsJsonObject("user");
            nick = str(user, "username");
            globalName = str(user, "global_name");
            String hash = str(user, "avatar");
            String id = str(user, "id");
            String url = hash.isEmpty() ? ""
                    : "https://cdn.discordapp.com/avatars/" + id + "/" + hash + ".png?size=128";
            avatarUrl = url;
            ready = true;
            TabVisualsClient.LOGGER.info("Discord IPC ready: {} ({})", nick(), id);
            if (!url.isBlank()) downloadAvatar(url);
        } catch (Exception e) {
            TabVisualsClient.LOGGER.debug("READY parse failed: {}", e.toString());
        }
    }

    private static void downloadAvatar(String url) {
        Thread t = new Thread(() -> {
            try {
                byte[] png = httpGet(url);
                if (png == null || png.length == 0) return;
                NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
                roundMask(img);
                Identifier id = Identifier.of("tabvisuals", "discord_avatar");
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().getTextureManager()
                            .registerTexture(id, new NativeImageBackedTexture(img));
                    avatar = id;
                });
            } catch (Exception e) {
                TabVisualsClient.LOGGER.debug("avatar download failed: {}", e.toString());
            }
        }, "tabvisuals-discord-avatar");
        t.setDaemon(true);
        t.start();
    }

    /** Punches a transparent circular hole: everything outside the circle gets alpha 0. */
    private static void roundMask(NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int argb = img.getColorArgb(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (d >= r) alpha = 0;
                else if (d > r - 1.5f) alpha = (int) (alpha * (r - d) / 1.5f);
                img.setColorArgb(x, y, (alpha << 24) | (argb & 0xFFFFFF));
            }
        }
    }

    private static byte[] httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "TabVisuals/1.0");
        try {
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                in.transferTo(bos);
                return bos.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void writeFrame(RandomAccessFile pipe, int op, byte[] payload) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op);
        buf.putInt(payload.length);
        buf.put(payload);
        pipe.write(buf.array());
        pipe.getChannel().force(false);
    }

    /** Reads the 8-byte header; returns {opcode, length} or null on EOF. */
    private static int[] readHeader(RandomAccessFile pipe) {
        try {
            byte[] head = new byte[8];
            pipe.readFully(head);
            ByteBuffer buf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
            return new int[]{buf.getInt(), buf.getInt()};
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject parse(byte[] payload) {
        try {
            JsonObject o = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.isJsonObject() ? o : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
