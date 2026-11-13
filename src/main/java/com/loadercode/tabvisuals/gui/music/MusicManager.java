// language: Java, file: MusicManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.music;

import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.media.DiscordBridge;
import com.loadercode.tabvisuals.media.LrcClient;
import com.loadercode.tabvisuals.media.MediaBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Facade over the real media stack: SMTC (system transport) for the current
 * track, lrclib for synchronized lyrics, Discord IPC for the profile row,
 * and the track cover captured by the SMTC bridge. Accessors return empty
 * strings / null when no source is available so screens can simply hide the
 * whole block.
 */
public final class MusicManager {

    /** Must match the script's $env:TEMP — launchers often override java.io.tmpdir. */
    private static final Path COVER_PATH = Path.of(coverDir(), "tabvisuals_cover.jpg");
    private static final Identifier COVER_ID = Identifier.of("tabvisuals", "cover");

    /** Lyrics tagged with their track: a late result can never show under another song. */
    private record TrackLyrics(long trackId, LrcClient.Lyrics lyrics) {
    }

    private static final TrackLyrics NO_LYRICS = new TrackLyrics(0, LrcClient.Lyrics.EMPTY);

    private static volatile TrackLyrics lyrics = NO_LYRICS;
    /** Track whose lyrics were last requested (bridge listener + render thread). */
    private static volatile long lyricsForTrack;

    /** Registered cover texture + its width in pixels once the file decodes. */
    private static volatile Identifier coverTex;
    private static volatile int coverSize;
    /** Высота той же текстуры — нужна для crop-fill обложки без растяжения. */
    private static volatile int coverHeight;
    /** Read from the worker thread too — must be volatile for the stale-guard. */
    private static volatile long coverForTrack;
    /** One fetch round per track; stays false until a late cover event arrives. */
    private static boolean coverFetchDone;
    /** Last registered GL texture, closed on replacement (client thread only). */
    private static NativeImageBackedTexture prevCoverTex;

    static {
        MediaBridge.start();
        DiscordBridge.start();
        // prefetch on the bridge thread; ticks for an already requested track are no-ops
        MediaBridge.addListener(MusicManager::requestLyrics);
    }

    /** Explicit bootstrap hook — forces the static init at client startup. */
    public static void init() {
    }

    private MusicManager() {
    }

    private static MediaBridge.Snapshot track() {
        return MediaBridge.current();
    }

    /** True when a media session reports a track — gates every lyrics block. */
    public static boolean hasTrack() {
        return track().active();
    }

    public static String trackTitle() {
        return track().title();
    }

    public static String artist() {
        return track().artist();
    }

    /** Playback progress 0..1 for the splash ring; 0 when nothing plays. */
    public static float progress() {
        MediaBridge.Snapshot s = track();
        if (!s.active() || s.endMs() <= 0) return 0f;
        return Math.min(1f, Math.max(0f, s.posMs() / (float) s.endMs()));
    }

    public static boolean isPlaying() {
        return track().playing();
    }

    /** Album of the current track, "" when unknown. */
    public static String album() {
        return track().album();
    }

    /** SMTC source app id (e.g. "Spotify.exe"), "" when nothing plays. */
    public static String sourceApp() {
        return track().app();
    }

    /** Interpolated playback position in ms (0 when idle). */
    public static long positionMs() {
        return track().posMs();
    }

    /** Track length in ms; 0 when the source doesn't report one. */
    public static long durationMs() {
        return track().endMs();
    }

    /** Full interpolated snapshot — read once per frame and use its fields together. */
    public static MediaBridge.Snapshot snapshot() {
        return track();
    }

    /** False on non-Windows: the SMTC bridge never runs, every accessor stays empty. */
    public static boolean mediaSupported() {
        return MediaBridge.isSupported();
    }

    private static final java.util.Set<String> COMMANDS =
            java.util.Set.of("playPause", "play", "pause", "stop", "next", "prev");
    private static final java.util.concurrent.ExecutorService CONTROL =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "tabvisuals-media-control");
                t.setDaemon(true);
                return t;
            });

    /**
     * Fire-and-forget transport command, safe on the render thread (file IO
     * runs on a daemon worker). Accepts playPause, play, pause, stop, next,
     * prev; returns false for anything else or when SMTC is unavailable.
     */
    public static boolean control(String command) {
        if (!MediaBridge.isSupported() || command == null || !COMMANDS.contains(command)) return false;
        try {
            CONTROL.execute(() -> com.loadercode.tabvisuals.gui.hud.elements.MediaPlayer.sendCommand(command));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return false;
        }
    }

    /** True when lrclib returned timed lines for the current track. */
    public static boolean hasLyrics() {
        syncCache();
        return lyricsFor(track()).hasSync();
    }

    /** Previous synchronized lyric line — small dim text above the current one in the menu. */
    public static String prevLine() {
        return lineAt(0);
    }

    /** Current synchronized lyric line — large text on the splash, right block in the menu. */
    public static String currentLine() {
        return lineAt(1);
    }

    /** Next line — small dim text under the current one. */
    public static String nextLine() {
        return lineAt(2);
    }

    private static String lineAt(int idx) {
        syncCache();
        MediaBridge.Snapshot s = track();
        LrcClient.Line[] around = LrcClient.around(lyricsFor(s), s.posMs());
        LrcClient.Line line = around[idx];
        return line == null ? "" : line.text();
    }

    /** Lyrics of exactly this track, EMPTY while they're loading or belong to another track. */
    private static LrcClient.Lyrics lyricsFor(MediaBridge.Snapshot s) {
        TrackLyrics l = lyrics;
        return s.active() && l.trackId() == s.trackId() ? l.lyrics() : LrcClient.Lyrics.EMPTY;
    }

    /** Requests lyrics when the track rolled over without an event. */
    private static void syncCache() {
        requestLyrics(track());
    }

    private static void requestLyrics(MediaBridge.Snapshot s) {
        if (!s.active()) {
            lyrics = NO_LYRICS;
            return;
        }
        // без длительности lrclib не найдёт совпадение, а ключ зафиксировался бы
        // навсегда — ждём тика, на котором endMs станет известен
        if (s.endMs() <= 0) return;
        long id = s.trackId();
        if (lyricsForTrack == id) return;
        lyricsForTrack = id;
        LrcClient.request(s.title(), s.artist(), s.endMs(), result -> {
            // tagged with the id: lyricsFor() ignores it if the track moved on
            if (result != null) lyrics = new TrackLyrics(id, result);
        });
    }

    // --- Discord profile row -------------------------------------------------

    public static boolean discordReady() {
        return DiscordBridge.isReady();
    }

    public static String discordNick() {
        return DiscordBridge.nick();
    }

    public static net.minecraft.util.Identifier discordAvatar() {
        return DiscordBridge.avatar();
    }

    // --- album cover --------------------------------------------------------

    /**
     * Square cover texture of the current track, or null when the bridge has
     * no capture (most apps don't publish one) or it hasn't decoded yet.
     */
    public static net.minecraft.util.Identifier coverTexture() {
        syncCover();
        return coverTex;
    }

    /** Width of the cover texture in pixels — UV maths for the full draw. */
    public static int coverSize() {
        return coverSize;
    }

    /** Высота текстуры обложки в пикселях — вторая половина UV-геометрии. */
    public static int coverHeight() {
        return coverHeight;
    }

    /** Decodes the captured cover file once per track, off-thread, registers it on the client thread. */
    private static void syncCover() {
        MediaBridge.Snapshot s = track();
        if (s.trackId() == 0) {
            // сессия закончилась — старая обложка не должна висеть в памяти
            if (coverTex != null || prevCoverTex != null) dropCover();
            return;
        }
        if (coverForTrack != s.trackId()) {
            coverForTrack = s.trackId();
            coverFetchDone = false;
            if (!s.active() || !s.cover()) {
                // a new track without artwork — drop the previous cover instead of
                // showing the last track's picture
                dropCover();
            }
        }
        // no artwork flagged yet: wait for the bridge's late "cover" event
        if (coverFetchDone || !s.active() || !s.cover()) return;
        coverFetchDone = true;
        long id = s.trackId();
        Thread t = new Thread(() -> {
            // the bridge writes the file right when the session appears — retry a
            // few times and accept JPEG thumbnails too (NativeImage is PNG-only)
            for (int attempt = 0; attempt < 6; attempt++) {
                try {
                    NativeImage img = readCover();
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.execute(() -> {
                        if (coverForTrack != id) {
                            // track rolled over while this one was decoding
                            img.close();
                            return;
                        }
                        NativeImageBackedTexture tex = null;
                        try {
                            tex = new NativeImageBackedTexture(img);
                            // TextureManager не закрывает вытесненную текстуру —
                            // снимаем старую сами, иначе NativeImage течёт по трекам
                            if (prevCoverTex != null) {
                                mc.getTextureManager().destroyTexture(COVER_ID);
                                prevCoverTex.close();
                            }
                            prevCoverTex = tex;
                            mc.getTextureManager().registerTexture(COVER_ID, tex);
                        } catch (Throwable t2) {
                            // upload упал — картинка не должна утекать
                            if (tex != null) tex.close(); else img.close();
                            if (prevCoverTex == tex) prevCoverTex = null;
                            TabVisualsClient.LOGGER.debug("cover register failed", t2);
                            return;
                        }
                        // размеры — до публикации id: читатель не увидит новую
                        // текстуру со старыми/нулевыми габаритами
                        coverSize = img.getWidth();
                        coverHeight = img.getHeight();
                        coverTex = COVER_ID;
                    });
                    return;
                } catch (IOException | RuntimeException e) {
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (coverForTrack == id) {
                // No cover landed after all retries — free the old texture too,
                // otherwise prevCoverTex (and its off-heap NativeImage) stays
                // pinned until the next successful load. This runs on the cover
                // worker; TextureManager must be touched on the client thread,
                // so hop over via mc.execute (same as the register path above).
                // re-check on the client thread: a newer track may have
                // registered its cover meanwhile and must not be dropped
                MinecraftClient.getInstance().execute(() -> {
                    if (coverForTrack == id) dropCover();
                });
            }
        }, "tabvisuals-cover");
        t.setDaemon(true);
        t.start();
    }

    private static String coverDir() {
        String temp = System.getenv("TEMP");
        if (temp == null || temp.isBlank()) temp = System.getenv("TMP");
        if (temp == null || temp.isBlank()) temp = System.getProperty("java.io.tmpdir", ".");
        return temp;
    }

    /** Client thread: frees the registered cover texture when nothing draws it. */
    private static void dropCover() {
        if (prevCoverTex != null) {
            // destroyTexture убирает запись из карты TextureManager, close —
            // освобождает NativeImage (двойной glDeleteTextures GL игнорирует)
            MinecraftClient.getInstance().getTextureManager().destroyTexture(COVER_ID);
            prevCoverTex.close();
            prevCoverTex = null;
        }
        coverTex = null;
        coverSize = 0;
        coverHeight = 0;
    }

    /**
     * Decodes the cover file: NativeImage first (PNG), ImageIO fallback that
     * converts JPEG/WebP thumbnails to ARGB pixels — SMTC apps publish both.
     */
    private static NativeImage readCover() throws IOException {
        byte[] bytes = Files.readAllBytes(COVER_PATH);
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException pngFail) {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi == null) {
                throw new IOException("unsupported cover format");
            }
            NativeImage img = new NativeImage(bi.getWidth(), bi.getHeight(), false);
            try {
                for (int y = 0; y < bi.getHeight(); y++) {
                    for (int x = 0; x < bi.getWidth(); x++) {
                        img.setColorArgb(x, y, bi.getRGB(x, y));
                    }
                }
            } catch (RuntimeException e) {
                img.close(); // нативная память не утекает при сбое конверсии
                throw e;
            }
            return img;
        }
    }
}
