// language: Java, file: NowPlaying.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;
import com.loadercode.tabvisuals.media.MediaBridge;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;


import static com.loadercode.tabvisuals.gui.title.MenuKit.*;

/**
 * "Now playing" state + drawing shared by the splash and the home page:
 * eased visibility, a track-change entry, a smoothly interpolated progress,
 * an equalizer that settles flat on pause, and the scrolling lyric pair
 * (the next line glides up into the current slot, the old one leaves upward).
 * One instance per screen; {@link #update} once per frame before drawing.
 */
public final class NowPlaying {

    private static final MenuFont F_TITLE = new MenuFont(15, Fonts.Type.SEMI);
    private static final MenuFont F_SUB = new MenuFont(12, Fonts.Type.REGULAR);
    private static final MenuFont F_LYRIC = new MenuFont(27, Fonts.Type.SEMI);
    private static final MenuFont F_LYRIC_NEXT = new MenuFont(17, Fonts.Type.DEFAULT);
    private static final MenuFont F_CARD_TITLE = new MenuFont(26, Fonts.Type.SEMI);
    private static final MenuFont F_CARD_SUB = new MenuFont(15, Fonts.Type.DEFAULT);
    private static final MenuFont F_TIME = new MenuFont(11, Fonts.Type.DEFAULT);
    private static final MenuFont F_TAG = new MenuFont(10, Fonts.Type.SEMI);

    /** Visibility of the whole block (no session → fades out instead of popping). */
    public float shown;
    private float playingT;
    private float progress;
    private long trackId = -1;
    private long trackAt;

    // lyric scroller: the line that left, the line now current, and when it changed
    private String lyricCur = "";
    private String lyricOld = "";
    private String lyricNext = "";
    private long lyricAt;

    // cached labels so the strings are not rebuilt per frame
    private String title = "", artist = "", album = "", app = "";
    private long posMs, endMs;
    private String timePos = "0:00", timeEnd = "0:00";
    private long timePosSec = -1, timeEndSec = -1;

    private final Anim prevBtn = new Anim(), playBtn = new Anim(), nextBtn = new Anim();
    private float ppSwap;
    private float hitPrevX, hitPrevY, hitPlayX, hitPlayY, hitNextX, hitNextY, hitR = -1f;

    /** Media module master switch + its settings. */
    public static boolean enabled() {
        ModuleStub m = ModuleRegistry.byId("media");
        return m == null || m.enabled();
    }

    public static boolean setting(String name) {
        if (!enabled()) {
            return false;
        }
        ModuleStub m = ModuleRegistry.byId("media");
        if (m == null) {
            return true;
        }
        for (Setting s : m.settings()) {
            if (s.name().equals(name)) {
                return !(s instanceof BooleanSetting b) || b.get();
            }
        }
        return true;
    }

    public void update(MenuFrame f) {
        boolean active = enabled() && MusicManager.hasTrack();
        shown = approach(shown, active ? 1f : 0f, 7f, f.dt);
        if (!active) {
            return;
        }
        MediaBridge.Snapshot s = MediaBridge.current();
        if (s.trackId() != trackId) {
            trackId = s.trackId();
            trackAt = f.now;
            title = s.title() == null ? "" : s.title();
            artist = s.artist() == null ? "" : s.artist();
            album = s.album() == null ? "" : s.album();
            app = s.app() == null ? "" : s.app();
            progress = 0f;
            lyricCur = lyricOld = lyricNext = "";
        }
        posMs = s.posMs();
        endMs = s.endMs();
        playingT = approach(playingT, s.playing() ? 1f : 0f, 6f, f.dt);
        ppSwap = approach(ppSwap, s.playing() ? 1f : 0f, 14f, f.dt);
        float target = endMs > 0 ? clamp01(posMs / (float) endMs) : 0f;
        // seek/new track jumps; normal play glides between the 100ms bridge steps
        progress = Math.abs(target - progress) > 0.05f ? target : approach(progress, target, 10f, f.dt);

        long ps = posMs / 1000L, es = endMs / 1000L;
        if (ps != timePosSec) {
            timePosSec = ps;
            timePos = time(posMs);
        }
        if (es != timeEndSec) {
            timeEndSec = es;
            timeEnd = time(endMs);
        }

        String cur = MusicManager.hasLyrics() ? MusicManager.currentLine() : "";
        if (cur == null) {
            cur = "";
        }
        if (!cur.equals(lyricCur)) {
            lyricOld = lyricCur;
            lyricCur = cur;
            lyricAt = f.now;
        }
        String next = MusicManager.hasLyrics() ? MusicManager.nextLine() : "";
        lyricNext = next == null ? "" : next;
    }

    public boolean hasLyrics() {
        return !lyricCur.isEmpty() || !lyricOld.isEmpty();
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    private float trackEnter(long now) {
        return easeOutCubic((now - trackAt) / 420f);
    }

    // ------------------------------------------------------------ compact

    /** Splash top-left: cover, title, artist, equalizer. Returns the drawn width. */
    public float drawCompact(MenuFrame f, float x, float y, float alpha) {
        float a = alpha * shown;
        if (a <= 0.01f) {
            return 0f;
        }
        MatrixStack m = f.m;
        float te = trackEnter(f.now);
        float size = 28f;
        float slide = (1f - te) * 4f;
        drawCoverOrTile(m, x, y, size, 5f, a * te);

        float tx = x + size + 9f;
        float maxW = 220f;
        F_TITLE.draw(m, F_TITLE.fit(title, maxW), tx, y + 9f + slide, alpha(TEXT, a * te));
        String sub = artist.isEmpty() ? app : artist;
        F_SUB.draw(m, F_SUB.fit(sub, maxW - 26f), tx, y + 20.5f + slide, alpha(TEXT_3, a * te));
        float subW = Math.min(F_SUB.width(sub), maxW - 26f);
        drawEq(m, tx + subW + 7f, y + 23.5f, f.time, a * te);
        return size + 9f + Math.max(F_TITLE.width(title), subW + 20f);
    }

    private void drawCoverOrTile(MatrixStack m, float x, float y, float size, float r, float a) {
        Identifier cov = setting("Music cover") ? MusicManager.coverTexture() : null;
        if (cov != null) {
            glow(m, x + size / 2f, y + size / 2f, size * 0.9f, size * 0.9f, alpha(ACCENT_DEEP, 0.25f * a));
            cover(m, cov, MusicManager.coverSize(), MusicManager.coverHeight(), x, y, size, r, alpha(0xFFFFFFFF, a));
            outline(m, x, y, size, size, r, alpha(0x22FFFFFF, a));
        } else {
            // no artwork: a violet tile with the track's first letter
            tile(m, x, y, size, size, r, a);
            String l = title.isEmpty() ? "♪" : title.substring(0, Math.min(1, title.length())).toUpperCase();
            MenuFont font = size > 60 ? F_CARD_TITLE : F_TITLE;
            font.drawCentered(m, l, x + size / 2f, y + size / 2f, alpha(TEXT, a));
        }
    }

    /** Three bouncing bars; flat and dim while paused. */
    private void drawEq(MatrixStack m, float x, float baseY, float t, float a) {
        float bw = 2f, gap = 1.6f, maxH = 7f;
        for (int i = 0; i < 3; i++) {
            float wave = 0.5f + 0.5f * (float) Math.sin(t * (7.1f + i * 2.3f) + i * 1.9f);
            float wave2 = 0.5f + 0.5f * (float) Math.sin(t * (4.3f + i * 1.1f) + i * 0.7f);
            float hgt = lerp(1.6f, maxH * (0.35f + 0.65f * (wave * 0.6f + wave2 * 0.4f)), playingT);
            rect(m, x + i * (bw + gap), baseY - hgt, bw, hgt, 1f, alpha(mix(TEXT_3, ACCENT, playingT), a));
        }
    }

    // ------------------------------------------------------------- lyrics

    /**
     * Current line big + next line dim, centred on (cx, cy). On a line
     * change the next line grows into the current slot while the old one
     * drifts up and fades. Without synced lyrics: "title — artist".
     */
    public void drawLyrics(MenuFrame f, float cx, float cy, float maxW, float alpha) {
        float a = alpha * shown;
        if (a <= 0.01f || !setting("Menu lyrics")) {
            return;
        }
        MatrixStack m = f.m;
        if (!hasLyrics()) {
            float te = trackEnter(f.now);
            String line = artist.isEmpty() ? title : title + "  —  " + artist;
            F_LYRIC_NEXT.drawCentered(m, F_LYRIC_NEXT.fit(line, maxW), cx, cy + (1f - te) * 6f, alpha(TEXT_2, a * te));
            return;
        }
        float gap = 19f;
        boolean animate = setting("Animated lyrics");
        float t = animate ? easeOutCubic((f.now - lyricAt) / 520f) : 1f;

        // the old line rises out of the current slot
        if (t < 1f && !lyricOld.isEmpty()) {
            F_LYRIC.drawCentered(m, F_LYRIC.fit(lyricOld, maxW), cx, cy - t * 14f, alpha(TEXT, a * (1f - t) * (1f - t)));
        }
        // the current line arrives from the next slot, growing from the small size
        if (!lyricCur.isEmpty()) {
            float s = lerp(17f / 27f, 1f, t);
            float y = lerp(cy + gap, cy, t);
            pushScale(m, cx, y, s);
            F_LYRIC.drawCentered(m, F_LYRIC.fit(lyricCur, maxW / s), cx, y, alpha(mix(TEXT_3, TEXT, t), a));
            m.pop();
        }
        // the next line fades in under it
        if (!lyricNext.isEmpty()) {
            F_LYRIC_NEXT.drawCentered(m, F_LYRIC_NEXT.fit(lyricNext, maxW), cx, cy + gap + (1f - t) * 8f,
                    alpha(TEXT_3, a * t));
        }
    }

    // --------------------------------------------------------------- card

    /** Home page player: big cover, labels, progress, transport. */
    public void drawCard(MenuFrame f, float x, float y, float w, float h, float alpha) {
        MatrixStack m = f.m;
        float a = alpha;
        float cs = h - 28f;
        float te = trackEnter(f.now);
        float vis = shown;

        card(m, x, y, w, h, 12f, alpha(CARD, a), alpha(LINE, a));
        if (vis < 0.999f) {
            // empty state underneath — crossfades with the live player
            float ea = a * (1f - vis);
            float cx = x + w / 2f, cy = y + h / 2f;
            glow(m, cx, cy - 8f, 60f, 40f, alpha(ACCENT_DEEP, 0.18f * ea));
            icon(m, Icons.PLAY, cx, cy - 14f, 30f, alpha(TEXT_3, ea));
            F_CARD_SUB.drawCentered(m, "Сейчас ничего не играет", cx, cy + 8f, alpha(TEXT_2, ea));
            F_SUB.drawCentered(m, "Включите музыку в любом плеере — трек и текст появятся здесь", cx, cy + 22f,
                    alpha(TEXT_4, ea));
        }
        float la = a * vis;
        if (la <= 0.01f) {
            hitR = -1f;
            return;
        }
        float cx0 = x + 14f, cy0 = y + 14f;
        drawCoverOrTile(m, cx0, cy0, cs, 10f, la * te);

        float tx = cx0 + cs + 16f;
        float tw = x + w - 16f - tx;
        float slide = (1f - te) * 6f;
        String src = app.isEmpty() ? "СЕЙЧАС ИГРАЕТ" : "СЕЙЧАС ИГРАЕТ  ·  " + app.toUpperCase();
        F_TAG.draw(m, F_TAG.fit(src, tw), tx, cy0 + 7f, alpha(ACCENT, la));
        F_CARD_TITLE.draw(m, F_CARD_TITLE.fit(title, tw), tx, cy0 + 26f + slide, alpha(TEXT, la * te));
        String sub = album.isEmpty() || album.equals(title) ? artist : artist + "  ·  " + album;
        F_CARD_SUB.draw(m, F_CARD_SUB.fit(sub, tw), tx, cy0 + 44f + slide, alpha(TEXT_2, la * te));

        // progress
        float barY = cy0 + cs - 30f;
        rect(m, tx, barY, tw, 3f, 1.5f, alpha(0x22FFFFFF, la));
        float pw = Math.max(3f, tw * progress);
        rect(m, tx, barY, pw, 3f, 1.5f, alpha(ACCENT, la));
        glow(m, tx + pw, barY + 1.5f, 7f, 7f, alpha(ACCENT, 0.55f * la * playingT));
        rect(m, tx + pw - 2.5f, barY - 1f, 5f, 5f, 2.5f, alpha(TEXT, la));
        F_TIME.draw(m, timePos, tx, barY + 11f, alpha(TEXT_3, la));
        F_TIME.drawRight(m, timeEnd, tx + tw, barY + 11f, alpha(TEXT_3, la));

        // transport, centred under the bar
        float by = cy0 + cs - 6f;
        float mid = tx + tw / 2f;
        hitR = 11f;
        hitPrevX = mid - 30f;
        hitPlayX = mid;
        hitNextX = mid + 30f;
        hitPrevY = hitPlayY = hitNextY = by;
        transport(f, prevBtn, Icons.SKIP_PREV, hitPrevX, by, 9f, la, false);
        transport(f, playBtn, null, hitPlayX, by, 11f, la, true);
        transport(f, nextBtn, Icons.SKIP_NEXT, hitNextX, by, 9f, la, false);
        drawEq(m, tx + tw - 10f, cy0 + 9f, f.time, la);
    }

    private void transport(MenuFrame f, Anim st, Icons.Glyph glyph, float cx, float cy, float r, float a, boolean main) {
        MatrixStack m = f.m;
        st.update(f, cx - r, cy - r, r * 2f, r * 2f);
        pushScale(m, cx, cy, st.scale() * (1f + 0.06f * st.hover));
        if (main) {
            glow(m, cx, cy, r * 2.2f, r * 2.2f, alpha(ACCENT_DEEP, (0.25f + 0.25f * st.hover) * a));
            rect(m, cx - r, cy - r, r * 2f, r * 2f, r, alpha(mix(0xFFEDE9FE, 0xFFFFFFFF, st.hover), a));
            // play ↔ pause crossfade with a little turn of scale
            float p = ppSwap;
            pushScale(m, cx, cy, 0.85f + 0.15f * Math.abs(p * 2f - 1f));
            icon(m, Icons.PLAY, cx + 0.8f, cy, 15f, alpha(0xFF1A1530, a * (1f - p)));
            int pc = alpha(0xFF1A1530, a * p);
            rect(m, cx - 3.4f, cy - 4.5f, 2.4f, 9f, 1.2f, pc);
            rect(m, cx + 1.0f, cy - 4.5f, 2.4f, 9f, 1.2f, pc);
            m.pop();
        } else {
            rect(m, cx - r, cy - r, r * 2f, r * 2f, r, alpha(0x18FFFFFF, a * st.hover));
            icon(m, glyph, cx, cy, 14f, alpha(mix(TEXT_2, TEXT, st.hover), a));
        }
        m.pop();
    }

    /** Transport clicks on the home card. */
    public boolean click(float mx, float my) {
        if (hitR <= 0f || shown < 0.5f) {
            return false;
        }
        if (within(mx, my, hitPlayX, hitPlayY)) {
            MenuKit.pressed = playBtn;
            send("playPause");
            return true;
        }
        if (within(mx, my, hitPrevX, hitPrevY)) {
            MenuKit.pressed = prevBtn;
            send("prev");
            return true;
        }
        if (within(mx, my, hitNextX, hitNextY)) {
            MenuKit.pressed = nextBtn;
            send("next");
            return true;
        }
        return false;
    }

    private boolean within(float mx, float my, float cx, float cy) {
        float dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= hitR * hitR * 1.4f;
    }

    /** Media keys are file IO in the bridge — MusicManager.control runs them off the render thread. */
    public static void send(String cmd) {
        MusicManager.control(cmd);
    }

    /** Drops the cached hit circle (page hidden). */
    public void clearHits() {
        hitR = -1f;
    }
}
