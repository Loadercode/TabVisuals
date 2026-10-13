// language: Java, file: MusicManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.music;

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

    private static final Path COVER_PATH =
            Path.of(System.getProperty("java.io.tmpdir", "."), "tabvisuals_cover.jpg");
    private static final Identifier COVER_ID = Identifier.of("tabvisuals", "cover");

    private static volatile LrcClient.Lyrics lyrics = LrcClient.Lyrics.EMPTY;
    private static volatile long lyricsForTrack;

    /** Registered cover texture + its width (square) once the file decodes. */
    private static volatile Identifier coverTex;
    private static volatile int coverSize;
    private static volatile long coverForTrack;

    static {
        MediaBridge.start();
        DiscordBridge.start();
        MediaBridge.addListener(s -> {
            if (s.active() && s.endMs() > 0) {
                LrcClient.request(s.title(), s.artist(), s.endMs(), result -> lyrics = result);
            } else {
                lyrics = LrcClient.Lyrics.EMPTY;
            }
        });
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

    /** True when lrclib returned timed lines for the current track. */
    public static boolean hasLyrics() {
        syncCache();
        return lyrics.hasSync();
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
        LrcClient.Line[] around = LrcClient.around(lyrics, track().posMs());
        LrcClient.Line line = around[idx];
        return line == null ? "" : line.text();
    }

    /** Replaces the cached lyrics when the track rolled over without an event. */
    private static void syncCache() {
        MediaBridge.Snapshot s = track();
        if (!s.active()) {
            if (lyrics != LrcClient.Lyrics.EMPTY) lyrics = LrcClient.Lyrics.EMPTY;
            return;
        }
        long id = s.trackId();
        if (lyricsForTrack == id) return;
        lyricsForTrack = id;
        LrcClient.request(s.title(), s.artist(), s.endMs(), result -> lyrics = result);
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

    /** Decodes the captured cover file once per track, off-thread, registers it on the client thread. */
    private static void syncCover() {
        MediaBridge.Snapshot s = track();
        if (s.trackId() == 0 || coverForTrack == s.trackId()) {
            return;
        }
        coverForTrack = s.trackId();
        if (!s.active() || !s.cover()) {
            // a new track without artwork — drop the previous cover instead of
            // showing the last track's picture
            coverTex = null;
            coverSize = 0;
            return;
        }
        Thread t = new Thread(() -> {
            // the bridge writes the file right when the session appears — retry a
            // few times and accept JPEG thumbnails too (NativeImage is PNG-only)
            for (int attempt = 0; attempt < 6; attempt++) {
                try {
                    NativeImage img = readCover();
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.execute(() -> {
                        mc.getTextureManager().registerTexture(COVER_ID, new NativeImageBackedTexture(img));
                        coverTex = COVER_ID;
                        coverSize = img.getWidth();
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
            coverTex = null;
            coverSize = 0;
        }, "tabvisuals-cover");
        t.setDaemon(true);
        t.start();
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
            for (int y = 0; y < bi.getHeight(); y++) {
                for (int x = 0; x < bi.getWidth(); x++) {
                    img.setColorArgb(x, y, bi.getRGB(x, y));
                }
            }
            return img;
        }
    }
}
