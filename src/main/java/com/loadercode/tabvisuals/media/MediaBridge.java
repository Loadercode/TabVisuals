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
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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

    /** Wall-clock anchor for extrapolating a playing position between ticks. */
    private record Playhead(long basePosMs, long baseNano, boolean playing) {
    }

    /**
     * Snapshot + anchor as ONE reference: the render thread sees either the
     * old or the new state whole. The previous version kept snapshot, playing,
     * basePosMs and baseNano in separate volatiles, so a reader could pair a
     * fresh anchor with a stale snapshot ("torn" multi-field read).
     */
    private record State(Snapshot snap, Playhead anchor) {
    }

    private static final List<Consumer<Snapshot>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object LOCK = new Object();

    private static volatile State state = new State(Snapshot.NONE, new Playhead(0, 0, false));
    private static volatile boolean started;
    private static volatile Thread worker;
    /** The running child process — stop() must kill it, an interrupt alone does not unblock readLine(). */
    private static volatile Process process;
    private static long trackCounter;
    /**
     * Cache of the extrapolated snapshot: the position is quantized to 100 ms,
     * so the several current() calls per frame reuse one allocation instead of
     * creating a record every call.
     */
    private static volatile Snapshot extrapolated;

    /** SMTC exists only on Windows — elsewhere the bridge stays inert (never spawns a process). */
    private static final boolean SUPPORTED =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

    /** Backward drift below this is treated as tick latency, not a seek: the playhead holds instead of rewinding. */
    private static final long MAX_HOLD_BACK_MS = 1500;

    private MediaBridge() {
    }

    /** True on Windows, where the SMTC bridge can run at all. */
    public static boolean isSupported() {
        return SUPPORTED;
    }

    /** True while the PowerShell bridge process is alive. */
    public static boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
    }

    public static void start() {
        if (!SUPPORTED) return;
        synchronized (LOCK) {
            if (started) return;
            started = true;
        }
        worker = new Thread(MediaBridge::run, "tabvisuals-smtc");
        worker.setDaemon(true);
        worker.start();
    }

    public static void stop() {
        if (!SUPPORTED) return;
        Thread t = worker;
        if (t != null) t.interrupt();
        Process p = process;
        if (p != null) p.destroyForcibly();
        // stale transport command must not replay on the next launch
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("tabvisuals");
            Files.deleteIfExists(dir.resolve("media_cmd.txt"));
            Files.deleteIfExists(dir.resolve("media_cmd.txt.tmp"));
        } catch (IOException ignored) {
        }
    }

    public static Snapshot current() {
        State st = state;
        Snapshot s = st.snap();
        if (!s.active()) return s;
        long pos = extrapolate(st);
        Snapshot cached = extrapolated;
        // exact-ms reuse only: the old 100 ms quantization made progress bars
        // and lyric switches visibly step at ~10 fps
        if (cached != null && cached.trackId() == s.trackId() && cached.playing() == st.anchor().playing()
                && cached.posMs() == pos && cached.endMs() == s.endMs() && cached.cover() == s.cover()) {
            return cached;
        }
        Playhead a = st.anchor();
        Snapshot out = new Snapshot(true, s.app(), s.title(), s.artist(), s.album(), a.playing(),
                pos, s.endMs(), s.cover(), s.trackId());
        extrapolated = out;
        return out;
    }

    /** Monotonic-clock position of the given state, clamped to [0, endMs]. */
    private static long extrapolate(State st) {
        Snapshot s = st.snap();
        Playhead a = st.anchor();
        long pos = a.basePosMs();
        if (a.playing()) {
            long dt = (System.nanoTime() - a.baseNano()) / 1_000_000L;
            if (dt > 0) pos += dt;
        }
        if (s.endMs() > 0 && pos > s.endMs()) pos = s.endMs();
        return Math.max(0L, pos);
    }

    public static void addListener(Consumer<Snapshot> listener) {
        LISTENERS.add(listener);
    }

    private static void publish(Snapshot s) {
        // snapshot and its anchor go out as one atomic reference — a reader
        // can never see a new anchor with an old snapshot (or vice versa)
        state = new State(s, new Playhead(s.posMs(), System.nanoTime(), s.playing()));
        for (Consumer<Snapshot> l : LISTENERS) {
            try {
                l.accept(s);
            } catch (Throwable t) {
                TabVisualsClient.LOGGER.warn("media listener failed", t);
            }
        }
    }

    private static void run() {
        // Exponential backoff: a bridge that cannot even start (no powershell,
        // blocked execution policy) used to respawn every 5s forever, spamming
        // the log with the same ERROR. Quick successful runs reset the delay.
        int backoffMs = 5000;
        long startedAt = 0L;
        while (!Thread.currentThread().isInterrupted()) {
            startedAt = System.nanoTime();
            int exit;
            try {
                exit = spawnOnce();
            } catch (Throwable t) {
                // the worker must survive anything — STARTED blocks a restart
                TabVisualsClient.LOGGER.error("SMTC bridge crashed", t);
                exit = -1;
            }
            // a dead bridge must not leave a "playing" snapshot extrapolating
            // for the whole backoff period
            if (state.snap().active()) publish(Snapshot.NONE);
            if (Thread.currentThread().isInterrupted()) break;
            // ran for over a minute before dying → the bridge itself works,
            // the exit was a one-off; restart quickly without escalating
            if (System.nanoTime() - startedAt > 60_000_000_000L) backoffMs = 5000;
            TabVisualsClient.LOGGER.info("SMTC bridge exited ({}), restarting in {}s", exit, backoffMs / 1000);
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                break;
            }
            backoffMs = Math.min(backoffMs * 2, 300_000);   // cap at 5 min
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
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                // parent pid: the script exits when the game dies, otherwise an
                // orphaned powershell.exe would outlive a crashed game and
                // duplicate every transport command after a restart
                "-File", script.toString(), Long.toString(ProcessHandle.current().pid()));
        // stderr into stdout: an undrained error pipe fills up and blocks the
        // child forever while readLine() waits for a line that never comes
        pb.redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            TabVisualsClient.LOGGER.error("cannot start SMTC bridge", e);
            return -1;
        }
        process = proc;
        // stop() may have run between pb.start() and the assignment above —
        // it could not see this process, so honour the interrupt here
        if (Thread.currentThread().isInterrupted()) proc.destroyForcibly();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!Thread.currentThread().isInterrupted() && (line = reader.readLine()) != null) {
                // a redirected console may start the stream with a BOM, which
                // trim() does not strip — the first event would be lost
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) != '{') continue;
                try {
                    handle(JsonParser.parseString(line).getAsJsonObject());
                } catch (RuntimeException | StackOverflowError parse) {
                    TabVisualsClient.LOGGER.debug("bad bridge line: {}", line);
                }
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                TabVisualsClient.LOGGER.debug("bridge stream closed: {}", e.toString());
            }
        } finally {
            // destroy + waitFor: neither the process nor its handles may
            // outlive the worker (exitValue() would throw otherwise)
            proc.destroyForcibly();
            try {
                if (!proc.waitFor(2, TimeUnit.SECONDS)) proc.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            process = null;
        }
        return proc.isAlive() ? -1 : proc.exitValue();
    }

    private static void handle(JsonObject o) {
        String st = str(o, "state");
        switch (st) {
            case "none" -> publish(Snapshot.NONE);
            case "track" -> {
                String app = str(o, "app"), title = str(o, "title"), artist = str(o, "artist"), album = str(o, "album");
                Snapshot prev = state.snap();
                // same metadata (e.g. the bridge restarted and re-announced the
                // song) keeps its id — lyrics/cover consumers don't reset
                long id = prev.active() && prev.app().equals(app) && prev.title().equals(title)
                        && prev.artist().equals(artist) && prev.album().equals(album)
                        ? prev.trackId() : ++trackCounter;
                publish(new Snapshot(true, app, title, artist, album,
                        "Playing".equalsIgnoreCase(str(o, "status")), num(o, "posMs"), num(o, "endMs"),
                        bool(o, "cover"), id));
            }
            case "tick" -> {
                State live = state;
                Snapshot s = live.snap();
                if (!s.active()) return;
                boolean playing = "Playing".equalsIgnoreCase(str(o, "status"));
                long end = num(o, "endMs");
                long pos = num(o, "posMs");
                if (playing && live.anchor().playing()) {
                    // a reported position slightly behind our extrapolation is
                    // tick latency, not a seek: hold the playhead, never rewind
                    long cur = extrapolate(live);
                    long drift = pos - cur;
                    if (drift < 0 && drift > -MAX_HOLD_BACK_MS) pos = cur;
                }
                if (end > 0 && pos > end) pos = end;
                publish(new Snapshot(true, s.app(), s.title(), s.artist(), s.album(),
                        playing, Math.max(0L, pos), end, s.cover(), s.trackId()));
            }
            case "cover" -> {
                // artwork arrived after the track event — same track, flip the
                // flag; anchor keeps the extrapolated position, not the raw tick
                Snapshot s = current();
                if (s.active() && !s.cover()) {
                    publish(new Snapshot(true, s.app(), s.title(), s.artist(), s.album(), s.playing(),
                            s.posMs(), s.endMs(), true, s.trackId()));
                }
            }
            default -> { /* error / unknown: keep last snapshot */ }
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static long num(JsonObject o, String key) {
        try {
            if (!o.has(key) || !o.get(key).isJsonPrimitive()) return 0L;
            double d = o.get(key).getAsDouble();
            // NaN / negative / absurd values (bad SMTC timelines) degrade to 0
            return Double.isFinite(d) && d > 0 ? (long) Math.min(d, 1e12) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** Malformed "cover" (null / object) must degrade to "no artwork", not drop the whole track event. */
    private static boolean bool(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static Path extractScript() throws IOException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("tabvisuals");
        Files.createDirectories(dir);
        Path script = dir.resolve("smtc.ps1");
        byte[] existing = null;
        try {
            existing = Files.readAllBytes(script);
        } catch (IOException ignored) {
            // no installed copy yet
        }
        byte[] data;
        try (var in = MediaBridge.class.getResourceAsStream("/assets/tabvisuals/scripts/smtc.ps1")) {
            if (in == null) {
                // an already installed copy beats running without a bridge
                if (existing != null) return script;
                throw new IOException("smtc.ps1 missing from jar");
            }
            data = in.readAllBytes();
        }
        // full compare, not size-only: an updated script of the same length
        // would otherwise never reach the config dir
        if (existing != null && Arrays.equals(existing, data)) return script;
        Path tmp = dir.resolve("smtc.ps1.tmp");
        try {
            // tmp + rename: a crash mid-write must not leave a truncated .ps1
            Files.write(tmp, data);
            Files.move(tmp, script, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // the file can be locked by a running bridge — the old copy is
            // still usable, and the next launch retries the update
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            if (Files.exists(script)) {
                TabVisualsClient.LOGGER.debug("SMTC script busy, reusing the installed copy");
                return script;
            }
            throw e;
        }
        return script;
    }
}
