// language: Java, file: MediaBridge.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadercode.tabvisuals.TabVisualsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Windows SMTC bridge: spawns the bundled PowerShell script and exposes the
 * current media session as a volatile {@link Snapshot}. Position is
 * extrapolated between script ticks using the wall clock, so lyrics stay in
 * sync without a tick every frame.
 */
public final class MediaBridge {

    /** Immutable view of the current media session. */
    public record Snapshot(boolean active, String app, String title, String artist, String album,
                            boolean playing, long posMs, long endMs, boolean cover, long trackId) {

        static final Snapshot NONE = new Snapshot(false, "", "", "", "", false, 0, 0, false, 0);
    }

    private static final List<Consumer<Snapshot>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object LOCK = new Object();

    private static volatile Snapshot snapshot = Snapshot.NONE;
    private static volatile boolean started;
    private static Thread worker;
    private static long trackCounter;
    /** Wall-clock anchor for extrapolating a playing position between ticks. */
    private static volatile long basePosMs;
    private static volatile long baseNano;
    private static volatile boolean playing;

    private MediaBridge() {
    }

    public static void start() {
        synchronized (LOCK) {
            if (started) return;
            started = true;
        }
        worker = new Thread(MediaBridge::run, "tabvisuals-smtc");
        worker.setDaemon(true);
        worker.start();
    }

    public static void stop() {
        Thread t = worker;
        if (t != null) t.interrupt();
    }

    public static Snapshot current() {
        Snapshot s = snapshot;
        if (!s.active()) return s;
        long pos = s.posMs();
        if (playing) {
            long elapsed = (System.nanoTime() - baseNano) / 1_000_000L;
            pos = basePosMs + elapsed;
            if (s.endMs() > 0 && pos > s.endMs()) pos = s.endMs();
        }
        return new Snapshot(true, s.app(), s.title(), s.artist(), s.album(), playing,
                pos, s.endMs(), s.cover(), s.trackId());
    }

    public static void addListener(Consumer<Snapshot> listener) {
        LISTENERS.add(listener);
    }

    private static void publish(Snapshot s) {
        // anchor + playing BEFORE the volatile snapshot write: a reader that
        // sees the new snapshot is guaranteed to see the matching anchor (the
        // old order let the render thread pair a new baseNano with a stale
        // basePosMs and jump the position backwards)
        basePosMs = s.posMs();
        baseNano = System.nanoTime();
        playing = s.playing();
        snapshot = s;
        for (Consumer<Snapshot> l : LISTENERS) {
            try {
                l.accept(s);
            } catch (Throwable t) {
                TabVisualsClient.LOGGER.warn("media listener failed", t);
            }
        }
    }

    private static void run() {
        while (!Thread.currentThread().isInterrupted()) {
            int exit = spawnOnce();
            if (Thread.currentThread().isInterrupted()) break;
            TabVisualsClient.LOGGER.info("SMTC bridge exited ({}), restarting in 5s", exit);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /** Runs the bridge until the process dies. Returns the exit code. */
    private static int spawnOnce() {
        Path script;
        try {
            script = extractScript();
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("cannot extract SMTC script", e);
            return -1;
        }
        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString());
        pb.redirectErrorStream(false);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("cannot start SMTC bridge", e);
            return -1;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) != '{') continue;
                try {
                    handle(JsonParser.parseString(line).getAsJsonObject());
                } catch (Exception parse) {
                    TabVisualsClient.LOGGER.debug("bad bridge line: {}", line);
                }
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                TabVisualsClient.LOGGER.debug("bridge stream closed: {}", e.toString());
            }
        } finally {
            process.destroyForcibly();
        }
        return process.exitValue();
    }

    private static void handle(JsonObject o) {
        String state = o.has("state") ? o.get("state").getAsString() : "";
        switch (state) {
            case "none" -> publish(Snapshot.NONE);
            case "track" -> {
                trackCounter++;
                boolean playingNow = "Playing".equalsIgnoreCase(str(o, "status"));
                publish(new Snapshot(true, str(o, "app"), str(o, "title"), str(o, "artist"), str(o, "album"),
                        playingNow, num(o, "posMs"), num(o, "endMs"), o.has("cover") && o.get("cover").getAsBoolean(),
                        trackCounter));
            }
            case "tick" -> {
                Snapshot s = snapshot;
                if (!s.active()) return;
                boolean playingNow = "Playing".equalsIgnoreCase(str(o, "status"));
                publish(new Snapshot(true, s.app(), s.title(), s.artist(), s.album(), playingNow,
                        num(o, "posMs"), num(o, "endMs"), s.cover(), s.trackId()));
            }
            default -> { /* error / unknown: keep last snapshot */ }
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static long num(JsonObject o, String key) {
        try {
            return o.has(key) ? (long) o.get(key).getAsDouble() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static Path extractScript() throws IOException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("tabvisuals");
        Files.createDirectories(dir);
        Path script = dir.resolve("smtc.ps1");
        try (var in = MediaBridge.class.getResourceAsStream("/assets/tabvisuals/scripts/smtc.ps1")) {
            if (in == null) throw new IOException("smtc.ps1 missing from jar");
            byte[] data = in.readAllBytes();
            boolean same = Files.exists(script) && Files.size(script) == data.length;
            if (!same) Files.write(script, data);
        }
        return script;
    }
}
