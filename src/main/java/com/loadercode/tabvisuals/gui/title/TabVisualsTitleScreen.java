// language: Java, file: TabVisualsTitleScreen.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.account.AccountManager;
import com.loadercode.tabvisuals.gui.account.AccountRenderer;
import com.loadercode.tabvisuals.gui.anim.Easing;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.media.DiscordBridge;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.List;

/**
 * TabVisuals title flow per ТЗ + reference photos:
 * splash (gradient + starfield, centered logo + TABVISUALS slogan, low
 * lyrics, pulsing hint, compact profile row, inline socials) → eased
 * zoom-in transition → main menu (violet-white gradient backdrop with an
 * aspect-correct album cover, top-left mark, left icon nav with a sliding
 * glass pill, right lyric block), plus the account manager modal with a
 * rotatable full-body 3D skin preview opened from "Аккаунты".
 */
public final class TabVisualsTitleScreen extends Screen {

    // --- palette (logo-matched: deep navy/indigo surface, violet accent) ---
    private static final int ACCENT = 0xFF8B5CF6;
    private static final int ACCENT_HOVER = 0xFFA78BFA;
    /** Accent for text/marker on the dark surface — bright enough to pop. */
    private static final int ACCENT_DEEP = 0xFF9D7BFF;
    /** Ink on accent fills (buttons, chips). */
    private static final int INK = 0xFFF4F1FF;
    /** Primary / secondary / dim text on the dark surface. */
    private static final int TEXT = 0xFFF0F1FA;
    private static final int TEXT_2 = 0xFFB9BDD6;
    private static final int TEXT_3 = 0xFF7E84A6;
    // idle должен читаться на насыщенном фиолете фона (143,98,244): старый
    // 0xFF9BA1BE давал Δlum ~30 и текст «тонул». Светлый лавандовый — контраст ~90.
    private static final int NAV_IDLE = 0xFFD7DBF0;
    /** Nav hover end — text brightens to pure white. */
    private static final int NAV_HOVER = 0xFFFFFFFF;
    /** Light glass on the dark surface. */
    private static final int GLASS_FILL = 0x18FFFFFF;
    private static final int GLASS_LINE = 0x2AFFFFFF;
    private static final int GLASS_SHEEN = 0x14FFFFFF;

    private static final String URL_TELEGRAM = "https://t.me/tabvisuals";
    private static final String URL_GITHUB = "https://github.com/Loadercode/TabVisuals";

    // --- prebuilt text (spaced() used to build a StringBuilder every frame) ---
    private static final String MARK_TXT = spaced("TABVISUALS");
    private static final String HINT_TXT = spaced("НАЖМИТЕ ЛЮБУЮ КЛАВИШУ");
    private static final String COPY_TXT = "© 2026 TabVisuals";

    // --- nav data: label + unified icon per item ---
    private static final String[] NAV_LABELS = {"Одиночная игра", "Мультиплейер", "Настройки", "Аккаунты", "Выход"};
    private static final Icons.Glyph[] NAV_GLYPHS = {
            Icons.CUBE, Icons.LINK, Icons.GEAR, Icons.USER, Icons.LOGOUT};

    // --- socials data (brand glyphs are not in the Icons set — PNG textures) ---
    private static final Identifier TG_ICON = Identifier.of("tabvisuals", "textures/menu/icon_telegram.png");
    private static final Identifier GH_ICON = Identifier.of("tabvisuals", "textures/menu/icon_github.png");
    private static final String[] SOC_LABELS = {"Telegram", "GitHub"};
    private static final String[] SOC_HANDLES = {"@tabvisuals", "Loadercode"};
    private static final Identifier[] SOC_ICONS = {TG_ICON, GH_ICON};

    // --- fonts ---
    private static final FontRenderer F_LOGO = Fonts.getSize(46, Fonts.Type.BOLD);
    private static final FontRenderer F_MARK = Fonts.getSize(14, Fonts.Type.BOLD);
    private static final FontRenderer F_SLOGAN = Fonts.getSize(11, Fonts.Type.SEMI);
    private static final FontRenderer F_LYRIC_BIG = Fonts.getSize(19, Fonts.Type.SEMI);
    private static final FontRenderer F_LYRIC_SMALL = Fonts.getSize(13, Fonts.Type.REGULAR);
    private static final FontRenderer F_HINT = Fonts.getSize(12, Fonts.Type.SEMI);
    private static final FontRenderer F_COPY = Fonts.getSize(10, Fonts.Type.REGULAR);
    private static final FontRenderer F_TINY = Fonts.getSize(10, Fonts.Type.REGULAR);
    private static final FontRenderer F_NAV = Fonts.getSize(15, Fonts.Type.SEMI);
    private static final FontRenderer F_MENU_LINE = Fonts.getSize(16, Fonts.Type.SEMI);
    private static final FontRenderer F_MENU_NEXT = Fonts.getSize(13, Fonts.Type.REGULAR);
    private static final FontRenderer F_MODAL_T = Fonts.getSize(17, Fonts.Type.SEMI);
    private static final FontRenderer F_INPUT = Fonts.getSize(13, Fonts.Type.REGULAR);
    private static final FontRenderer F_ROW = Fonts.getSize(13, Fonts.Type.SEMI);
    private static final FontRenderer F_BTN = Fonts.getSize(11, Fonts.Type.SEMI);

    // --- timings ---
    private static final long TRANS_MS = 520;
    private static final long MENU_IN_MS = 350;

    // --- starfield: deterministic positions (golden-ratio sequences), no RNG ---
    private static final int STAR_N = 18;
    private static final float[] STAR_X = new float[STAR_N];
    private static final float[] STAR_Y = new float[STAR_N];
    private static final float[] STAR_R = new float[STAR_N];
    private static final float[] STAR_PH = new float[STAR_N];

    static {
        for (int i = 0; i < STAR_N; i++) {
            STAR_X[i] = (float) ((i + 0.5) * 0.618033988749895 % 1.0);
            STAR_Y[i] = (float) ((i + 0.5) * 0.7548776662466927 % 1.0);
            STAR_R[i] = 1.2f + (i % 4) * 0.45f;
            STAR_PH[i] = i * 1.37f;
        }
    }

    private enum State {SPLASH, TRANSITION, MENU}

    private State state = State.SPLASH;
    private long animStart = System.currentTimeMillis();
    /** Stagger base for the splash entry animation (reset when returning). */
    private long splashStart = System.currentTimeMillis();
    private long menuStart;

    // --- lyric fade-in state: last drawn current line + when it changed ---
    private String lastLine = "";
    private long lineChangedAt;
    /** Per-nav-item hover/press easing, 0..1. */
    private final float[] navHover = new float[5];
    private final float[] navPress = new float[5];
    private int navPressed = -1;
    /** The single nav glass pill: eased y-centre / width / alpha. */
    private float navPillY, navPillW, navPillA;
    private boolean navPillInit;
    /** Per-social-entry hover easing, 0..1. */
    private final float[] socHover = new float[2];
    /** Frame delta (seconds) for FPS-independent animation smoothing. */
    private long lastFrameMs;
    private float frameDt = 1f / 60f;
    /** Mouse position of the current frame — button easing hit-tests. */
    private float mouseX, mouseY;

    // --- modal state ---
    private boolean modal;
    /** Modal visibility 0..1 (open/close easing) — input is dead below 0.9. */
    private float modalVis;
    private boolean modalClosing;
    /** Modal open timestamp — drives the staggered element entry. */
    private long modalStart;
    /** 0 = none, 1 = new-nick field, 2 = row rename field. */
    private int focus;
    private int editIndex = -1;
    private String editOldName = "";
    private String input = "";
    private String editText = "";
    private float listScroll, scrollTarget;
    /** Invalid "Создать" submit — decays into a shake on the input field. */
    private float inputErrorT;
    /** Failed row rename — decays into a shake on that row's edit field. */
    private float editErrorT;
    /** Row hover dwell → the preview follows the row under the mouse. */
    private String hoverCandidate, hoverNick;
    private long hoverSince;
    /** Row click pin — the preview stays on the clicked account. */
    private String pinnedNick;

    // --- smooth-button animation state (no string ids — pooled states) ---
    private static final class BtnState {
        float hover, press;
    }

    private static BtnState[] newBtnPool(int n) {
        BtnState[] a = new BtnState[n];
        for (int i = 0; i < n; i++) {
            a[i] = new BtnState();
        }
        return a;
    }

    private final BtnState closeBtn = new BtnState();
    private final BtnState createBtn = new BtnState();
    private final BtnState[] rowLoginBtn = newBtnPool(64);
    private final BtnState[] rowEditBtn = newBtnPool(64);
    private final BtnState[] rowDelBtn = newBtnPool(64);
    /** The button currently held down (cleared on release / focus loss). */
    private BtnState pressedBtn;

    /** 3D skin preview — full body, drag-rotatable, nick-driven. */
    private final AccountRenderer skinRenderer = new AccountRenderer();

    // --- hit caches (filled during render, pooled — no per-frame records) ---
    private static final class R {
        float x, y, w, h;

        R set(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }
    }

    private static R[] newRPool(int n) {
        R[] a = new R[n];
        for (int i = 0; i < n; i++) {
            a[i] = new R();
        }
        return a;
    }

    private final R[] navBox = newRPool(5);
    private final R[] socBox = newRPool(2);
    private final R[] rowBox = newRPool(64);
    private final R[] rowLogin = newRPool(64);
    private final R[] rowEdit = newRPool(64);
    private final R[] rowDel = newRPool(64);
    /** Which row boxes hold THIS frame's geometry (scrolled-out rows stay stale). */
    private final boolean[] rowRec = new boolean[64];
    private final R closeHit = new R();
    private final R inputHit = new R();
    private final R createHit = new R();
    private R previewHit;
    private float panelX, panelY, panelW, panelH;
    /** DrawContext of the current frame — used for the cover/avatar textures. */
    private DrawContext ctx;

    /** "Title — Artist" label cache — trackLabel() rebuilt it every frame. */
    private String tlTitle = " ", tlArtist = " ", tlCache = "";

    /** First-letter chip cache for the profile row. */
    private String initNick = " ", initChar = "?";

    public TabVisualsTitleScreen() {
        super(Text.literal("TabVisuals"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------- render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack m = context.getMatrices();
        this.ctx = context;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = System.currentTimeMillis();
        // clamped harder than before: a long hitch must not "teleport" every
        // eased control to its end state in one frame
        if (lastFrameMs != 0) {
            frameDt = Math.min(0.05f, (now - lastFrameMs) / 1000f);
        }
        lastFrameMs = now;

        // the cursor position freezes when the window loses focus — without
        // this every hover state sticks on the last item under the parked mouse
        if (!windowFocused()) {
            this.mouseX = -9999f;
            this.mouseY = -9999f;
        }

        // a press can be "lost" when a click opens another screen before the
        // release — poll the real button state so eased press never sticks
        if (pressedBtn != null || navPressed >= 0) {
            MinecraftClient mc0 = MinecraftClient.getInstance();
            if (mc0 == null || mc0.getWindow() == null
                    || GLFW.glfwGetMouseButton(mc0.getWindow().getHandle(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                pressedBtn = null;
                navPressed = -1;
            }
        }

        // lyric line change drives the fade/slide-in of the new line
        String curLine = mediaOn() && MusicManager.hasLyrics() ? MusicManager.currentLine() : "";
        if (curLine == null) {
            curLine = "";
        }
        if (!curLine.isEmpty() && !curLine.equals(lastLine)) {
            lastLine = curLine;
            lineChangedAt = now;
        }

        MenuBackground.render(m, width, height, 1f, now);
        renderStarfield(m, 1f, now);

        switch (state) {
            case SPLASH -> renderSplash(m, now, 1f, 1f);
            case TRANSITION -> {
                float p = clamp01((now - animStart) / (float) TRANS_MS);
                // zoom leads (expo-out: fast start, long settle), the fade
                // trails it (quad-out) — the logo never pops out early
                float zoom = (float) Easing.apply(Easing.EXPO_OUT, p);
                float fade = (float) Easing.apply(Easing.QUAD_OUT, p);
                renderSplash(m, now, 1f + 0.28f * zoom, 1f - fade);
                // soft violet bloom peaking mid-transition — a premium flash
                // that never reaches full white
                float flash = (float) Math.sin(Math.PI * Math.min(1.0, p * 1.2));
                if (flash > 0.02f) {
                    UiRender.glow(m, width / 2f, height * 0.42f,
                            Math.max(width, height) * 0.26f, 0xFFB8A6FF, 0.09f * flash, 1f);
                }
                if (p >= 1f) {
                    state = State.MENU;
                    menuStart = now;
                    navPillA = 0f;
                    navPillInit = false;
                }
            }
            case MENU -> renderMenu(m, now);
        }

        if (modal) {
            renderModal(m, now);
        }
    }

    /** Cheap focused-window poll — one JNI attr read per frame. */
    private static boolean windowFocused() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return GLFW.glfwGetWindowAttrib(mc.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    /**
     * The premium backdrop touch: 18 slow-breathing dots over the shader
     * gradient — deterministic positions, violet/white mix, the few larger
     * ones carry a faint halo. One rect each, halo layers early-out at this
     * alpha — cheap even on a GTX 770.
     */
    private void renderStarfield(MatrixStack m, float alpha, long now) {
        if (alpha <= 0.01f) {
            return;
        }
        float t = now * 0.001f;
        for (int i = 0; i < STAR_N; i++) {
            float breath = 0.5f + 0.5f * (float) Math.sin(t * (0.30f + (i % 5) * 0.05f) + STAR_PH[i]);
            float a = alpha * (0.08f + 0.34f * breath * breath);
            if (a <= 0.012f) {
                continue;
            }
            float x = STAR_X[i] * width + (float) Math.sin(t * 0.09f + STAR_PH[i]) * 6f;
            float y = (0.04f + STAR_Y[i] * 0.66f) * height
                    + (float) Math.cos(t * 0.07f + STAR_PH[i] * 1.7f) * 5f;
            float d = STAR_R[i];
            int rgb = i % 3 == 0 ? 0xFFC4B5FD : 0xFFFFFFFF;
            UiRender.rect(m, x, y, d, d, d / 2f, mulAlpha(rgb, a));
            if (d > 2.0f) {
                UiRender.glow(m, x + d / 2f, y + d / 2f, d * 5.5f, rgb, 0.10f * a, 1f);
            }
        }
    }

    private void renderSplash(MatrixStack m, long now, float scale, float alpha) {
        m.push();
        if (Math.abs(scale - 1f) > 0.001f) {
            float cx = width / 2f, cy = height / 2f;
            m.translate(cx, cy, 0f);
            m.scale(scale, scale, 1f);
            m.translate(-cx, -cy, 0f);
        }

        // staggered entry: logo → slogan → lyrics → hint (slide-up + fade)
        float aLogo = enter(now, splashStart, 0, 480);
        float aSlogan = enter(now, splashStart, 120, 480);

        // photo 1: brand logo image when the asset exists, text mark otherwise
        Identifier logo = LogoManager.texture();
        if (logo != null && ctx != null) {
            // full logo (ligature + TabVisuals wordmark), aspect-correct from
            // the REAL image size (the crop fractions are u/v of that image),
            // floating on the gradient — shown crisp, no slogan
            float lh = 90f;
            float lw = lh * fullLogoAspect();
            float ly = height * 0.335f + (1f - aLogo) * 8f;
            drawLogoFull(m, logo, width / 2f - lw / 2f, ly, lh, alpha * aLogo);
        } else {
            F_LOGO.drawCenteredString(m, "TabVisuals", width / 2.0, height * 0.415 + (1f - aLogo) * 8f,
                    mulAlpha(TEXT, alpha * aLogo));
            F_SLOGAN.drawCenteredString(m, MARK_TXT, width / 2.0, height * 0.545f + (1f - aSlogan) * 6f,
                    mulAlpha(TEXT_2, alpha * aSlogan));
        }

        // lyrics low on the screen: big current line + small next one
        // (hidden until SMTC reports a track — no placeholder text)
        float aLyric = enter(now, splashStart, 240, 520);
        if (mediaOn() && MusicManager.hasTrack() && aLyric > 0.01f) {
            float lineA = lyricAnim(now) * alpha * aLyric;
            float lineY = height * 0.785f;
            float cover = 44f;
            if (mediaSetting("Music cover")) {
                Identifier cov = MusicManager.coverTexture();
                if (cov != null && ctx != null) {
                    float cy = lineY - cover - 12f;
                    Render2D.drawTexture(ctx, cov, width / 2f - cover / 2f, cy, (int) cover);
                    UiRender.outline(m, width / 2f - cover / 2f, cy, cover, cover, 6f,
                            mulAlpha(0x40FFFFFF, alpha * aLyric), 1f);
                }
            }
            if (MusicManager.hasLyrics()) {
                F_LYRIC_BIG.drawCenteredString(m, MusicManager.currentLine(), width / 2.0,
                        lineY + (1f - lineA) * 4f, mulAlpha(TEXT, alpha * aLyric * lineA));
                F_LYRIC_SMALL.drawCenteredString(m, MusicManager.nextLine(), width / 2.0, height * 0.845f,
                        mulAlpha(TEXT_3, alpha * aLyric * lineA));
            } else {
                // no synced lyrics — title/artist only (ТЗ)
                F_LYRIC_SMALL.drawCenteredString(m, trackLabel(), width / 2.0, lineY + 4,
                        mulAlpha(TEXT_2, alpha * aLyric));
            }
        }

        // pulsing hint near the bottom edge
        float aHint = enter(now, splashStart, 380, 520);
        float pulse = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(now / 360.0));
        F_HINT.drawCenteredString(m, HINT_TXT, width / 2.0, height * 0.915,
                mulAlpha(TEXT, alpha * aHint * pulse));

        renderProfileRow(m, alpha * enter(now, splashStart, 180, 480));
        renderSocials(m, alpha * enter(now, splashStart, 440, 480));
        F_COPY.drawCenteredString(m, COPY_TXT, width / 2.0, Math.round(height * 0.968),
                mulAlpha(0x997E84A6, alpha * enter(now, splashStart, 500, 480)));

        m.pop();
    }

    /** Eased 0..1 entry factor with a per-element delay — staggered intro. */
    private static float enter(long now, long start, long delay, long dur) {
        return (float) Easing.apply(Easing.QUINT_OUT, (now - start - delay) / (float) dur);
    }

    /**
     * Eased fade/slide factor for the current lyric line: a fresh line enters
     * over ~320ms, gated by the Media module's "Animated lyrics" setting.
     */
    private float lyricAnim(long now) {
        if (!mediaSetting("Animated lyrics")) {
            return 1f;
        }
        return (float) Easing.apply(Easing.CUBIC_OUT, (now - lineChangedAt) / 320.0);
    }

    private void renderMenu(MatrixStack m, long now) {
        float p = clamp01((now - menuStart) / (float) MENU_IN_MS);
        float e = (float) Easing.apply(Easing.QUINT_OUT, p);

        // photo 2: violet-white gradient backdrop + aspect-correct album cover
        renderCoverGlow(m, e, now);

        float cx = width / 2f, cy = height / 2f;
        float s = 1.04f - 0.04f * e;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);

        // staggered chrome entry: mark → profile → nav → lyrics → socials
        renderMark(m, enter(now, menuStart, 0, 420));
        renderProfileRow(m, enter(now, menuStart, 70, 420));
        renderNav(m, now, e);
        renderMenuLyrics(m, enter(now, menuStart, 240, 460), now);
        renderSocials(m, enter(now, menuStart, 310, 460));
        F_COPY.drawCenteredString(m, COPY_TXT, width / 2.0, Math.round(height * 0.968),
                mulAlpha(0x997E84A6, enter(now, menuStart, 360, 460)));

        m.pop();
    }

    /**
     * Menu backdrop per dj's ТЗ: the album cover is drawn aspect-correct
     * (square source, uniform scale, slow breathing zoom, cropped by the
     * screen — never squashed), then a violet→white gradient wash with two
     * soft glow pools ties it to the menu palette, and dark top/bottom
     * scrims keep the white text crisp.
     */
    private void renderCoverGlow(MatrixStack m, float alpha, long now) {
        float w = width, h = height;

        // the animated shader backdrop is already drawn in render() — here the
        // real album cover fades in over it, dimmed by the readability scrim
        if (mediaSetting("Cover glow")) {
            Identifier cover = MusicManager.coverTexture();
            if (cover != null && ctx != null) {
                // square source → square quad, centred: uniform scale, so the
                // art keeps its aspect on any window shape
                float breathe = 1f + 0.03f * (float) Math.sin(now * 0.00009);
                float sz = Math.max(w, h) * breathe;
                Render2D.drawTexture(ctx, cover, (w - sz) / 2f, (h - sz) / 2f, (int) sz);
                UiRender.rect(m, 0, 0, w, h, 0, mulAlpha(0x9C060718, alpha));
            }
        }

        // violet→white diagonal light wash over the whole backdrop
        UiRender.gradient(m, 0, 0, w, h,
                mulAlpha(ACCENT, 0.16f * alpha),          // tl: violet
                mulAlpha(0xFFC4B5FD, 0.10f * alpha),      // bl: lavender
                mulAlpha(0xFFFFFFFF, 0.06f * alpha),      // br: white
                mulAlpha(0xFFFFFFFF, 0.07f * alpha));     // tr: white

        // two large soft pools — violet top-left, warm white bottom-right.
        // UiRender.glow stacks 44 layers, so the composited centre reaches
        // ~14× the peak: peaks here are tuned as EFFECTIVE strengths (the old
        // 0.13 white blew the right half to ~85% white and drowned the lyrics).
        UiRender.glow(m, w * 0.24f, h * 0.30f, Math.max(w, h) * 0.45f, ACCENT, 0.20f, alpha);
        UiRender.glow(m, w * 0.78f, h * 0.70f, Math.max(w, h) * 0.32f, 0xFFEDE9FE, 0.05f, alpha);

        // readability scrims where the chrome sits (top bar + nav/lyrics/footer)
        UiRender.gradient(m, 0, 0, w, h * 0.20f,
                mulAlpha(0xB0060718, alpha), 0x00000000, 0x00000000, mulAlpha(0xB0060718, alpha));
        UiRender.gradient(m, 0, h * 0.60f, w, h * 0.40f,
                0x00000000, mulAlpha(0xC0060718, alpha), mulAlpha(0xC0060718, alpha), 0x00000000);
    }

    /** Media module master switch — the title screen hides every now-playing block when off. */
    private static boolean mediaOn() {
        ModuleStub m = ModuleRegistry.byId("media");
        return m == null || m.enabled();
    }

    /** Boolean setting of the Media module by name. */
    private static boolean mediaSetting(String name) {
        if (!mediaOn()) {
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

    /** "Title — Artist" label, cached — only rebuilt when the track changes. */
    private String trackLabel() {
        String title = MusicManager.trackTitle();
        String artist = MusicManager.artist();
        if (title == null) {
            title = "";
        }
        if (artist == null) {
            artist = "";
        }
        if (!title.equals(tlTitle) || !artist.equals(tlArtist)) {
            tlTitle = title;
            tlArtist = artist;
            tlCache = artist.isEmpty() ? title : title.isEmpty() ? artist : title + " — " + artist;
        }
        return tlCache;
    }

    /** Top-left logo tile + letterspaced wordmark — main menu only (photo 2). */
    private void renderMark(MatrixStack m, float alpha) {
        // compact badge so the mark fits neatly in the top-left chrome
        float tile = Math.max(15f, width * 0.0165f);
        float lx = hp(width * 0.0325f), ly = hp(height * 0.038f);
        Identifier logo = LogoManager.texture();
        if (logo != null && ctx != null) {
            // subtle breathing keeps the mark feeling alive; text optically centred on it
            float breathe = 1f + 0.012f * (float) Math.sin(System.currentTimeMillis() * 0.0016);
            float lh = tile * breathe;
            float lw = lh * markAspect();
            drawLogo(m, logo, lx, ly + (tile - lh) / 2f, lh, alpha);
            F_SLOGAN.drawString(m, MARK_TXT, lx + lw + width * 0.010f,
                    centerTextY(F_SLOGAN, MARK_TXT, ly + tile / 2f), mulAlpha(TEXT, alpha));
            return;
        }
        UiRender.rect(m, lx, ly, tile, tile, tile * 0.3f, mulAlpha(0x1AFFFFFF, alpha));
        UiRender.outline(m, lx, ly, tile, tile, tile * 0.3f, mulAlpha(0x30FFFFFF, alpha), 1f);
        F_MARK.drawCenteredString(m, "T", lx + tile / 2.0,
                centerTextY(F_MARK, "T", ly + tile / 2f), mulAlpha(TEXT, alpha));
        F_SLOGAN.drawString(m, MARK_TXT, lx + tile + width * 0.010f,
                centerTextY(F_SLOGAN, MARK_TXT, ly + tile / 2f), mulAlpha(TEXT, alpha));
    }

    /** Top-right compact profile row: version, status, nickname, round avatar (photo 1/2). */
    private void renderProfileRow(MatrixStack m, float alpha) {
        float av = 15;
        float right = width - width * 0.026f;
        float top = height * 0.038f;
        float cy = top + av / 2f;

        // avatar: Discord avatar (round, alpha-masked) when IPC is up, else the
        // account initial on a tinted chip — always shown, module-independent
        float ax = right - av;
        Identifier avatar = DiscordBridge.avatar();
        if (avatar != null && ctx != null) {
            // the avatar is pre-masked to a circle (alpha) — no backing rect;
            // drawn via the vertex-color variant so the entry fade applies
            int avInt = (int) av;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Render2D.drawTexture(m, avatar, (int) ax, (int) top, av, av,
                    0, 0, avInt, avInt, avInt, avInt, mulAlpha(0xFFFFFFFF, alpha));
            RenderSystem.disableBlend();
        } else {
            UiRender.rect(m, ax, top, av, av, av / 2f, mulAlpha(ACCENT, alpha));
            String nick0 = AccountManager.current();
            if (!nick0.equals(initNick)) {
                initNick = nick0;
                initChar = nick0.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(nick0.charAt(0)));
            }
            F_BTN.drawCenteredString(m, initChar, ax + av / 2.0,
                    centerTextY(F_BTN, initChar, top + av / 2f), mulAlpha(INK, alpha));
        }

        // nickname: the Discord nick when connected, the account otherwise
        String nick = !MusicManager.discordNick().isEmpty()
                ? MusicManager.discordNick() : AccountManager.current();
        // right of the avatar; on narrow windows the left-most pieces are
        // dropped before the text can run into the top-left mark
        float markRight = width * 0.36f;
        float x = ax - 6;
        if (x - F_TINY.getStringWidth(nick) >= markRight) {
            // nickname (dim), right of status
            x = rightText(F_TINY, m, nick, x, centerTextY(F_TINY, nick, cy),
                    mulAlpha(TEXT_2, alpha)) - 8;
        }

        // "Protected" — the bright one
        if (x - F_TINY.getStringWidth("Protected") >= markRight) {
            x = rightText(F_TINY, m, "Protected", x, centerTextY(F_TINY, "Protected", cy),
                    mulAlpha(TEXT, alpha)) - 8;
        }

        // version
        if (x - F_TINY.getStringWidth("v1.0.0") >= markRight) {
            rightText(F_TINY, m, "v1.0.0", x, centerTextY(F_TINY, "v1.0.0", cy),
                    mulAlpha(TEXT_3, alpha));
        }
    }

    /** Socials row (photo 1/2): glowing glass badge, platform, handle — clickable. */
    private void renderSocials(MatrixStack m, float alpha) {
        float y = hp(height * 0.93f);
        float rowH = 13;
        float gap = width * 0.016f;   // between the two entries
        float badge = 12;

        // measure the row, right-align it; on narrow windows the handles are
        // dropped so the row never runs into the nav/lyric block
        float full = 0, shortW = 0;
        for (int i = 0; i < 2; i++) {
            full += badge + 5 + F_TINY.getStringWidth(SOC_LABELS[i]) + 5 + F_TINY.getStringWidth(SOC_HANDLES[i]);
            shortW += badge + 5 + F_TINY.getStringWidth(SOC_LABELS[i]);
            if (i == 0) {
                full += gap;
                shortW += gap;
            }
        }
        boolean showHandles = full <= width * 0.55f;
        float total = showHandles ? full : shortW;
        float x = width - width * 0.026f - total;

        float kH = 1f - (float) Math.exp(-frameDt * 14.0);
        for (int i = 0; i < 2; i++) {
            float itemW = showHandles
                    ? badge + 5 + F_TINY.getStringWidth(SOC_LABELS[i]) + 5 + F_TINY.getStringWidth(SOC_HANDLES[i])
                    : badge + 5 + F_TINY.getStringWidth(SOC_LABELS[i]);
            socBox[i].set(x, y - 1, itemW, rowH);
            boolean hover = UiRender.inside(mouseX, mouseY, socBox[i].x, socBox[i].y, socBox[i].w, socBox[i].h);
            socHover[i] += ((hover ? 1f : 0f) - socHover[i]) * kH;
            float hv = socHover[i];

            // soft violet halo behind the badge on hover — the button glow
            if (hv > 0.02f && alpha > 0.01f) {
                UiRender.glow(m, x + badge / 2f, y + badge / 2f, badge * 1.7f, ACCENT, 0.16f * hv, alpha);
            }
            UiRender.rect(m, x, y, badge, badge, badge / 2f,
                    mulAlpha(UiRender.mix(GLASS_FILL, 0x33FFFFFF, hv), alpha));
            // white glyph texture tinted to match the text colors;
            // blend back on — UiRender.rect left the pipeline with blend disabled
            float iconSz = badge - 4;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Render2D.drawTexture(m, SOC_ICONS[i], Math.round(x + 2), Math.round(y + 2), iconSz, iconSz,
                    0, 0, 128, 128, 128, 128, mulAlpha(UiRender.mix(TEXT_2, TEXT, hv), alpha));
            RenderSystem.disableBlend();

            float tx = x + badge + 5;
            F_TINY.drawString(m, SOC_LABELS[i], tx, centerTextY(F_TINY, SOC_LABELS[i], y + rowH / 2f),
                    mulAlpha(UiRender.mix(TEXT_2, TEXT, hv), alpha));
            if (showHandles) {
                tx += F_TINY.getStringWidth(SOC_LABELS[i]) + 5;
                F_TINY.drawString(m, SOC_HANDLES[i], tx, centerTextY(F_TINY, SOC_HANDLES[i], y + rowH / 2f),
                        mulAlpha(UiRender.mix(TEXT_3, TEXT_2, hv), alpha));
            }
            x += itemW + gap;
        }
    }

    /**
     * Left navigation column: unified Icons glyphs before each label and ONE
     * glass pill that slides/morphs to the hovered item; the icon lifts to
     * violet and the label to white on hover, press sinks the whole row. The
     * hit rect is exactly the drawn item rect (slide included).
     */
    private void renderNav(MatrixStack m, long now, float alpha) {
        float iconX = width * 0.0325f;
        float iconSz = Math.max(11f, width * 0.014f);
        float textX = iconX + iconSz + width * 0.007f;
        float step = height * 0.051f;
        float firstCy = height * 0.392f;
        float extraGap = height * 0.014f;   // wider break before "Выход", like the photo
        float pad = step * 0.22f;
        float kH = 1f - (float) Math.exp(-frameDt * 14.0);
        float kP = 1f - (float) Math.exp(-frameDt * 24.0);

        // pass 1: geometry + hit rects (the drawn rect, incl. entry slide)
        float cy = firstCy;
        int hovered = -1;
        for (int i = 0; i < NAV_LABELS.length; i++) {
            if (i == 4) {
                cy += extraGap;
            }
            float itemA = enter(now, menuStart, 110 + i * 55, 420);
            float slideX = (1f - itemA) * 6f;
            float slideY = (1f - itemA) * 4f;
            float boxW = textX + F_NAV.getStringWidth(NAV_LABELS[i]) - iconX;
            navBox[i].set(iconX - pad + slideX, cy - step / 2f + slideY, boxW + pad * 2f, step);
            if (UiRender.inside(mouseX, mouseY, navBox[i].x, navBox[i].y, navBox[i].w, navBox[i].h)) {
                hovered = i;
            }
            cy += step;
        }

        // pass 2: the single glass pill eases to the hovered item
        if (!navPillInit && hovered >= 0) {
            navPillY = navBox[hovered].y + step / 2f;
            navPillW = navBox[hovered].w;
            navPillInit = true;
        }
        float kS = 1f - (float) Math.exp(-frameDt * 18.0);
        float targetY = hovered >= 0 ? navBox[hovered].y + step / 2f : navPillY;
        float targetW = hovered >= 0 ? navBox[hovered].w : navPillW;
        navPillY += (targetY - navPillY) * kS;
        navPillW += (targetW - navPillW) * kS;
        navPillA += ((hovered >= 0 ? 1f : 0f) - navPillA) * kH;
        if (navPillA < 0.004f) {
            navPillA = 0f;
        }
        if (navPillA > 0.01f && alpha > 0.01f) {
            float pillH = step - pad * 1.2f;
            float pillW = navPillW * (0.88f + 0.12f * navPillA);   // grows in as it fades in
            float pillX = iconX - pad;
            float pillY = navPillY - pillH / 2f;
            float pa = alpha * navPillA;
            UiRender.rect(m, pillX, pillY, pillW, pillH, pillH / 2f, mulAlpha(0x22FFFFFF, pa));
            UiRender.outline(m, pillX, pillY, pillW, pillH, pillH / 2f, mulAlpha(GLASS_LINE, pa), 1f);
            UiRender.rect(m, pillX + 2, pillY + 1, pillW - 4, 1f, 0.5f, mulAlpha(GLASS_SHEEN, pa));
            // violet marker inside the pill's left padding
            UiRender.rect(m, pillX + 4f, navPillY - 2f, 2f, 4f, 1f, mulAlpha(ACCENT_DEEP, pa));
        }

        // pass 3: items — icon + label colour lift, press sink
        cy = firstCy;
        for (int i = 0; i < NAV_LABELS.length; i++) {
            if (i == 4) {
                cy += extraGap;
            }
            R box = navBox[i];
            float itemA = enter(now, menuStart, 110 + i * 55, 420) * alpha;
            boolean hover = UiRender.inside(mouseX, mouseY, box.x, box.y, box.w, box.h);
            float hv = navHover[i] + ((hover ? 1f : 0f) - navHover[i]) * kH;
            if (hv < 0.004f) {
                hv = 0f;
            }
            navHover[i] = hv;
            float pv = navPress[i] + ((hover && i == navPressed ? 1f : 0f) - navPress[i]) * kP;
            if (pv < 0.004f) {
                pv = 0f;
            }
            navPress[i] = pv;

            float itemCy = box.y + box.h / 2f;
            float ps = UiRender.pressScale(pv);
            m.push();
            float ccx = box.x + box.w / 2f;
            m.translate(ccx, itemCy, 0f);
            m.scale(ps, ps, 1f);
            m.translate(-ccx, -itemCy, 0f);

            // icon lifts toward violet, label toward white — two-tone, not flat
            int iconCol = mulAlpha(UiRender.mix(NAV_IDLE, ACCENT_HOVER, hv), itemA);
            int textCol = mulAlpha(UiRender.mix(NAV_IDLE, NAV_HOVER, hv), itemA);
            Icons.Glyph glyph = NAV_GLYPHS[i];
            float gx = box.x + pad;
            Icons.draw(m, glyph, gx, UiRender.iconY(glyph, iconSz, itemCy), iconSz, iconCol);
            F_NAV.drawString(m, NAV_LABELS[i], gx + iconSz + width * 0.007f,
                    centerTextY(F_NAV, NAV_LABELS[i], itemCy), textCol);
            m.pop();
        }
    }

    /**
     * Right block: album cover above the text, then prev / current / next
     * lyric lines, right-aligned, vertically centered (photo 2). The current
     * line fades + slides in whenever it changes.
     */
    private void renderMenuLyrics(MatrixStack m, float alpha, long now) {
        if (!mediaSetting("Menu lyrics") || !MusicManager.hasTrack() || alpha <= 0.01f) {
            return;
        }
        float rightX = width - width * 0.0325f;
        // keep clear of the nav column
        float navTextX = width * 0.0325f + Math.max(11f, width * 0.014f) + width * 0.007f;
        float limit = navTextX + F_NAV.getStringWidth("Одиночная игра") + 20;
        float lineA = lyricAnim(now);

        // album cover above the text block (hidden when the app published none)
        if (mediaSetting("Music cover")) {
            Identifier cov = MusicManager.coverTexture();
            if (cov != null && ctx != null) {
                float cover = 42f;
                float cy = height * 0.45f - cover - 12f;
                Render2D.drawTexture(ctx, cov, rightX - cover, cy, (int) cover);
                UiRender.outline(m, rightX - cover, cy, cover, cover, 6f, mulAlpha(0x40FFFFFF, alpha), 1f);
            }
        }

        if (!MusicManager.hasLyrics()) {
            // no synced lyrics — the track/artist label takes the current-line slot
            String label = trackLabel();
            rightTextClamped(F_MENU_LINE, m, label, rightX, limit,
                    centerTextY(F_MENU_LINE, label, height * 0.503f),
                    mulAlpha(TEXT, alpha));
            return;
        }

        rightTextClamped(F_MENU_NEXT, m, MusicManager.prevLine(), rightX, limit,
                centerTextY(F_MENU_NEXT, MusicManager.prevLine(), height * 0.45f),
                mulAlpha(TEXT_3, alpha * lineA));
        rightTextClamped(F_MENU_LINE, m, MusicManager.currentLine(), rightX, limit,
                centerTextY(F_MENU_LINE, MusicManager.currentLine(), height * 0.503f) + (1f - lineA) * 3f,
                mulAlpha(TEXT, alpha * lineA));
        rightTextClamped(F_MENU_NEXT, m, MusicManager.nextLine(), rightX, limit,
                centerTextY(F_MENU_NEXT, MusicManager.nextLine(), height * 0.551f),
                mulAlpha(TEXT_3, alpha * lineA));
    }

    // -------------------------------------------------------------- modal

    private void renderModal(MatrixStack m, long now) {
        if (ctx == null) {
            return;
        }
        // open/close easing: the panel settles in and out instead of popping
        float kV = 1f - (float) Math.exp(-frameDt * 16.0);
        modalVis += ((modalClosing ? 0f : 1f) - modalVis) * kV;
        if (modalClosing && modalVis < 0.02f) {
            modal = false;
            modalClosing = false;
            modalVis = 0f;
            return;
        }
        // visuals settle on quint-out while input stays gated on the raw value
        float ap = (float) Easing.apply(Easing.QUINT_OUT, modalVis);
        // dimming scrim over the dark menu
        UiRender.rect(m, 0, 0, width, height, 0, mulAlpha(0x66000000, ap));

        // fit any GUI scale: 580x400 is a max, never larger than the screen
        panelW = Math.min(580, width - 20);
        panelH = Math.min(400, height - 20);
        // whole-pixel origin — a .5 offset blurs the 1px hairline
        panelX = Math.round((width - panelW) / 2f);
        panelY = Math.round((height - panelH) / 2f);

        // scale/fade entry around the panel centre
        float s = 0.94f + 0.06f * ap;
        m.push();
        float pcx = width / 2f, pcy = height / 2f;
        m.translate(pcx, pcy, 0f);
        m.scale(s, s, 1f);
        m.translate(-pcx, -pcy, 0f);

        boolean blink = (now / 500) % 2 == 0;
        boolean tall = panelH >= 330;
        boolean wide = panelW >= 450;

        // dark indigo glass panel with a top hairline and a soft drop shadow
        UiRender.shadow(m, panelX, panelY, panelW, panelH, 12, 0.55f * ap);
        UiRender.rect(m, panelX, panelY, panelW, panelH, 12, 0xF212142E);
        UiRender.outline(m, panelX, panelY, panelW, panelH, 12, 0x33FFFFFF, 1f);
        UiRender.rect(m, panelX + 1, panelY + 1, panelW - 2, 1, 0.5f, 0x14FFFFFF);

        // header: title, subtitle, count pill, close button
        F_MODAL_T.drawString(m, "Аккаунты", panelX + 18, panelY + 14, TEXT);
        float headerBottom = panelY + (tall ? 58 : 44);
        if (tall) {
            F_TINY.drawString(m, "Офлайн-профили · скин по нику", panelX + 18, panelY + 37, TEXT_3);
        }
        int count = AccountManager.names().size();
        String countLabel = count + (count == 1 ? " профиль" : count < 5 ? " профиля" : " профилей");
        float countW = F_TINY.getStringWidth(countLabel) + 14;
        float countX = panelX + panelW - 34 - 10 - countW;
        UiRender.rect(m, countX, panelY + 14, countW, 16, 8, 0x18FFFFFF);
        F_TINY.drawString(m, countLabel, countX + 7, centerTextY(F_TINY, countLabel, panelY + 14 + 8f),
                TEXT_2);

        // close: icon-only button carrying the unified CLOSE glyph
        closeHit.set(panelX + panelW - 34, panelY + 10, 24, 24);
        drawIconButton(m, closeBtn, closeHit, Icons.CLOSE, 11f, 0x1AFFFFFF, TEXT_2, ap);

        // ----- left column: rotatable full-body skin preview -----
        boolean preview = tall && wide;
        float rightX = panelX + 18;
        float rightW = panelW - 36;
        if (preview) {
            float prevW = Math.min(146f, panelW * 0.30f);
            float prevH = panelY + panelH - 16 - headerBottom;
            float prevX = panelX + 18, prevY = headerBottom;
            previewHit = previewHitOrSet(prevX, prevY, prevW, prevH);
            rightX = prevX + prevW + 16;
            rightW = panelX + panelW - 18 - rightX;

            // violet-white gradient card behind the model
            boolean hovPrev = UiRender.inside(mouseX, mouseY, prevX, prevY, prevW, prevH);
            float kH = 1f - (float) Math.exp(-frameDt * 14.0);
            previewHoverT += ((hovPrev ? 1f : 0f) - previewHoverT) * kH;
            UiRender.shadow(m, prevX, prevY, prevW, prevH, 12, 0.4f * ap);
            UiRender.rect(m, prevX, prevY, prevW, prevH, 12, 0xE614102B);
            UiRender.gradient(m, prevX, prevY, prevW, prevH,
                    UiRender.withAlpha(ACCENT, (int) (34 + 22 * previewHoverT)), // tl: violet
                    0x1A2A1F4A,                                                  // bl: deep violet
                    0x2440345E,                                                  // br: lifted violet
                    UiRender.withAlpha(0xFFFFFFFF, 40));                         // tr: white
            UiRender.outline(m, prevX, prevY, prevW, prevH, 12,
                    UiRender.mix(0x33FFFFFF, 0x66FFFFFF, previewHoverT), 1f);
            // soft violet pool under the model — the renderer draws its own
            // pedestal glow, so the screen pool is kept faint to avoid over-glow
            UiRender.glow(m, prevX + prevW / 2f, prevY + prevH * 0.42f, prevW * 0.62f,
                    ACCENT, 0.06f, ap);

            // the model + the nick it belongs to
            String want = previewNick();
            skinRenderer.setNick(want);
            skinRenderer.render(ctx, m, prevX + 10, prevY + 10, prevW - 20, prevH - 44, frameDt);
            String shownNick = skinRenderer.nick().isEmpty() ? "—" : skinRenderer.nick();
            F_ROW.drawCenteredString(m, shownNick, prevX + prevW / 2.0,
                    prevY + prevH - 26, TEXT);
            F_TINY.drawCenteredString(m, skinRenderer.isDragging() ? "отпуските — вернётся"
                    : "потяните, чтобы вращать", prevX + prevW / 2.0, prevY + prevH - 14,
                    mulAlpha(TEXT_3, 0.6f + 0.4f * previewHoverT));
        } else {
            previewHit = null;
        }

        // ----- right column: input row + account list -----
        float rowH30 = 30;
        float inputY = headerBottom;
        // tiny windows: the input keeps a clickable minimum, never a negative box
        inputHit.set(rightX, inputY, Math.max(24f, rightW - 6 - rowH30), rowH30);
        createHit.set(inputHit.x + inputHit.w + 6, inputY, rowH30, rowH30);

        // invalid submit shakes the field and fades a red ring
        if (inputErrorT > 0f) {
            inputErrorT = Math.max(0f, inputErrorT - frameDt * 2.2f);
        }
        float shake = inputErrorT > 0f
                ? (float) Math.sin(now * 0.045) * 5f * inputErrorT : 0f;
        float pr = inputHit.h / 2f;   // pill field
        UiRender.rect(m, inputHit.x + shake, inputHit.y, inputHit.w, inputHit.h, pr, 0x18000000);
        float focusK = 1f - (float) Math.exp(-frameDt * 16.0);
        focusT += ((focus == 1 ? 1f : 0f) - focusT) * focusK;
        int ring = UiRender.mix(0x2AFFFFFF, ACCENT_DEEP, Math.max(focusT, inputErrorT));
        if (inputErrorT > 0.01f) {
            ring = UiRender.mix(ring, 0xFFE05B5B, inputErrorT);
        }
        UiRender.outline(m, inputHit.x + shake, inputHit.y, inputHit.w, inputHit.h, pr, ring, 1f);
        // ADD glyph at the field's left edge — accent-lifts while focused
        Icons.draw(m, Icons.ADD, inputHit.x + 10 + shake,
                UiRender.iconY(Icons.ADD, 11f, inputHit.y + inputHit.h / 2f), 11f,
                mulAlpha(UiRender.mix(TEXT_3, ACCENT_HOVER, Math.max(focusT, inputErrorT)), ap));
        String shown;
        int shownCol;
        if (input.isEmpty() && focus != 1) {
            shown = "Введите ник";
            shownCol = TEXT_3;
        } else {
            shown = input + (focus == 1 && blink ? "_" : "");
            shownCol = TEXT;
        }
        F_INPUT.drawString(m, shown, inputHit.x + 30 + shake,
                centerTextY(F_INPUT, shown, inputHit.y + inputHit.h / 2f), shownCol);

        // confirm: accent icon-only button carrying the LOGIN glyph
        drawAccentIconButton(m, createBtn, createHit, Icons.LOGIN, 13f, ACCENT, INK);

        // account list
        List<String> names = AccountManager.names();
        float listY = inputY + rowH30 + 12;
        float visible = Math.max(0f, panelY + panelH - 14 - listY);
        float rowH = 30, step = 36;
        float total = names.size() * step - 6;
        float maxScroll = Math.max(0f, total - visible);
        scrollTarget = clamp(scrollTarget, 0f, maxScroll);
        // smooth scroll: the view eases toward the wheel target (k≈12)
        float kS = 1f - (float) Math.exp(-frameDt * 12.0);
        listScroll += (scrollTarget - listScroll) * kS;
        if (Math.abs(scrollTarget - listScroll) < 0.1f) {
            listScroll = scrollTarget;
        }
        java.util.Arrays.fill(rowRec, false);

        if (names.isEmpty()) {
            F_INPUT.drawCenteredString(m, "Пока пусто — создайте первый профиль",
                    rightX + rightW / 2.0, listY + 16, TEXT_3);
            m.pop();
            return;
        }

        float listW = rightW - (maxScroll > 0f ? 8f : 0f);
        // clip rows to the list band: scrolled-out rows must neither paint
        // over the input row nor leave clickable ghosts. The GL scissor
        // ignores the matrix stack, so the modal scale s is baked in by hand
        // (rects are measured around the panel centre pcx/pcy).
        ctx.enableScissor(Math.round(pcx + (panelX - pcx) * s), Math.round(pcy + (listY - pcy) * s),
                Math.round(pcx + (panelX + panelW - pcx) * s), Math.round(pcy + (listY + visible - pcy) * s));
        boolean anyRowHover = false;
        for (int i = 0; i < names.size(); i++) {
            float ry = listY + i * step - listScroll;
            if (ry > listY + visible || ry + rowH < listY) {
                continue;
            }
            String name = names.get(i);
            // staggered row entry: fade + slide from the right
            float rowA = enter(now, modalStart, 60 + i * 35, 380) * ap;
            float rowSlide = (1f - rowA) * 6f;

            // hover is tested against last frame's lifted rect, so the hit
            // rect IS the drawn rect even mid-lift
            float rhPrev = i < rowHoverT.length ? rowHoverT[i] : 0f;
            float lift = rhPrev * 1.5f;
            R row = rowBox[i].set(rightX + rowSlide, ry - lift, listW, rowH);
            boolean rowHover = UiRender.inside(mouseX, mouseY, row.x, row.y, row.w, row.h);
            boolean isActiveRow = name.equals(AccountManager.current());

            // dwell hover: after 250ms the preview follows the hovered row
            if (rowHover) {
                anyRowHover = true;
                if (!name.equals(hoverCandidate)) {
                    hoverCandidate = name;
                    hoverSince = now;
                } else if (!name.equals(hoverNick) && now - hoverSince >= 250) {
                    hoverNick = name;
                }
            }

            // eased row hover — the bg lifts instead of snapping
            float kH = 1f - (float) Math.exp(-frameDt * 14.0);
            float rh = rhPrev + ((rowHover ? 1f : 0f) - rhPrev) * kH;
            if (i < rowHoverT.length) {
                rowHoverT[i] = rh;
            }

            UiRender.rect(m, row.x, row.y, row.w, row.h, 10,
                    mulAlpha(UiRender.mix(0x0AFFFFFF, 0x18FFFFFF, rh), rowA));
            if (isActiveRow) {
                // accent ring + bar = the current-account marker
                UiRender.outline(m, row.x, row.y, row.w, row.h, 10, mulAlpha(0x558B5CF6, rowA), 1f);
                UiRender.rect(m, row.x, row.y + 6, 2f, rowH - 12, 1f, mulAlpha(ACCENT_DEEP, rowA));
            }

            // three even buttons on the right; delete collapses to an icon
            // button that materialises with the row hover
            float delS = 24, editW = 46, loginW = 52, btnGap = 6;
            R del = rowDel[i].set(row.x + row.w - 10 - delS, row.y + 3, delS, 24);
            R edit = rowEdit[i].set(del.x - btnGap - editW, row.y + 3, editW, 24);
            R login = rowLogin[i].set(edit.x - btnGap - loginW, row.y + 3, loginW, 24);

            boolean editing = focus == 2 && editIndex == i;
            editFocusT += ((editing ? 1f : 0f) - editFocusT) * focusK;
            if (editing) {
                // a failed rename shakes THIS field (the input field is a
                // different one — its shake would point at the wrong box)
                if (editErrorT > 0f) {
                    editErrorT = Math.max(0f, editErrorT - frameDt * 2.2f);
                }
                float eshake = editErrorT > 0f
                        ? (float) Math.sin(now * 0.045) * 5f * editErrorT : 0f;
                float fw = Math.max(70f, login.x - 16 - row.x);
                UiRender.rect(m, row.x + 10 + eshake, row.y + 3, fw, 24, 12, 0x18FFFFFF);
                int ering = UiRender.mix(0x2AFFFFFF, ACCENT_DEEP, Math.max(editFocusT, editErrorT));
                if (editErrorT > 0.01f) {
                    ering = UiRender.mix(ering, 0xFFE05B5B, editErrorT);
                }
                UiRender.outline(m, row.x + 10 + eshake, row.y + 3, fw, 24, 12, ering, 1f);
                String shownEdit = editText + (blink ? "_" : "");
                F_ROW.drawString(m, shownEdit, row.x + 22 + eshake,
                        centerTextY(F_ROW, shownEdit, row.y + 3 + 12f), TEXT);
            } else {
                // row icon: solid USER for the current account, outline otherwise
                Icons.Glyph rowGlyph = isActiveRow ? Icons.USER : Icons.USER_OUTLINE;
                UiRender.iconCentered(m, rowGlyph, row.x + 22, row.y + rowH / 2f, 12f,
                        mulAlpha(isActiveRow ? ACCENT_HOVER : UiRender.mix(TEXT_2, TEXT, rh * 0.5f), rowA));
                // name + optional "Активен" tag, never overlapping the buttons
                String tag = isActiveRow ? "Активен" : null;
                float tagW = tag == null ? 0 : F_TINY.getStringWidth(tag) + 12;
                float nameMax = Math.max(4f, login.x - 10 - (row.x + 38) - tagW);
                float nameH = F_ROW.getStringHeight(name);
                String shownName = F_ROW.getStringWidth(name) > nameMax
                        ? F_ROW.trimToWidth(name, (int) nameMax) + "…" : name;
                // nameH брался по полному имени — якорь (центр строки) сохранён
                F_ROW.drawString(m, shownName, row.x + 38,
                        centerTextY(F_ROW, name, row.y + rowH / 2f), mulAlpha(TEXT, rowA));
                if (tag != null) {
                    float tagX = login.x - 10 - tagW + 4;
                    UiRender.rect(m, tagX, row.y + (rowH - 14) / 2f, tagW, 14, 7, mulAlpha(0x1A8B5CF6, rowA));
                    F_TINY.drawString(m, tag, tagX + 6,
                            centerTextY(F_TINY, tag, row.y + rowH / 2f), mulAlpha(ACCENT_DEEP, rowA));
                }
            }

            drawAccentButton(m, rowLoginBtn[i], login, "Войти", F_BTN, ACCENT, INK);
            if (editing) {
                drawAccentButton(m, rowEditBtn[i], edit, "OK", F_BTN, ACCENT, INK);
            } else {
                drawButton(m, rowEditBtn[i], edit, "Изм.", F_BTN, 0x16FFFFFF, TEXT);
            }
            // TRASH icon button materialises with the row hover, red halo on top
            if (rh > 0.02f) {
                drawIconButton(m, rowDelBtn[i], del, Icons.TRASH, 10f, 0x14FFFFFF, 0xFFFF7B7B, rh * rowA);
                if (rowDelBtn[i].hover > 0.03f) {
                    UiRender.glow(m, del.x + del.w / 2f, del.y + del.h / 2f, del.w * 1.6f, 0xFFE05B5B,
                            0.20f * rowDelBtn[i].hover, rh * rowA);
                }
            }

            // a partially clipped row keeps its looks but loses its buttons:
            // otherwise clicks would land on the part scrolled out of the band
            if (row.y + 3 >= listY && row.y + 27 <= listY + visible) {
                rowRec[i] = true;
            }
        }
        ctx.disableScissor();
        if (!anyRowHover) {
            hoverCandidate = null;
            hoverNick = null;
        }

        // slim scrollbar pill on the list's right edge
        if (maxScroll > 0f && visible > 0f) {
            float trackX = hp(panelX + panelW - 18 - 1);
            float thumbH = Math.max(18f, visible * (visible / total));
            float thumbY = listY + (visible - thumbH) * (listScroll / maxScroll);
            UiRender.rect(m, trackX, thumbY, 3f, thumbH, 1.5f, mulAlpha(0x3AFFFFFF, ap));
            // scroll affordance: chevrons fade in when there is somewhere to go
            float upA = clamp01(listScroll / 12f);
            float downA = clamp01((maxScroll - listScroll) / 12f);
            UiRender.iconCentered(m, Icons.CHEVRON_UP, rightX + rightW - 8, listY - 8, 8f,
                    mulAlpha(TEXT_3, upA * ap));
            UiRender.iconCentered(m, Icons.CHEVRON_DOWN, rightX + rightW - 8, listY + visible + 2, 8f,
                    mulAlpha(TEXT_3, downA * ap));
        }

        m.pop();
    }

    /** Tiny helper keeping the preview hit rect in the shared R pool. */
    private R previewHitOrSet(float x, float y, float w, float h) {
        if (previewHit == null) {
            previewHit = new R();
        }
        return previewHit.set(x, y, w, h);
    }

    /** Preview nick priority: typed nick > pinned row > hovered row > active. */
    private String previewNick() {
        String typed = AccountManager.clean(input);
        if (focus == 1 && typed.length() >= 3) {
            return typed;
        }
        if (pinnedNick != null) {
            return pinnedNick;
        }
        if (hoverNick != null) {
            return hoverNick;
        }
        return AccountManager.current();
    }

    /** Per-row hover easing for the list (fixed capacity — rows are small). */
    private final float[] rowHoverT = new float[64];
    private float focusT;
    /** Eased focus ring for the row rename field (0..1). */
    private float editFocusT;
    private float previewHoverT;

    /**
     * Shared smooth button: eased hover (k≈14) / press (k≈24) state, drawn
     * through the UiRender helpers so every button sinks and lifts the same
     * way. Round is always pill (h/2) per the client's button language.
     */
    private void drawButton(MatrixStack m, BtnState st, R box, String label, FontRenderer font, int bg, int fg) {
        easeButton(st, box);
        UiRender.button(m, font, box.x, box.y, box.w, box.h, box.h / 2f, label, st.hover, st.press, bg, fg);
    }

    private void drawAccentButton(MatrixStack m, BtnState st, R box, String label, FontRenderer font, int bg, int fg) {
        easeButton(st, box);
        UiRender.accentButton(m, font, box.x, box.y, box.w, box.h, box.h / 2f, label, st.hover, st.press, bg, fg);
    }

    /** Icon-only glass button: rect + hairline + centred glyph, press sinks. */
    private void drawIconButton(MatrixStack m, BtnState st, R box, Icons.Glyph glyph, float size,
                                int bg, int fg, float alpha) {
        if (alpha <= 0.01f || box.w <= 0f || box.h <= 0f) {
            return;
        }
        easeButton(st, box);
        float s = UiRender.pressScale(st.press);
        float cx = box.x + box.w / 2f, cy = box.y + box.h / 2f;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);
        int body = UiRender.lighten(bg, 0.08f * UiRender.clamp01(st.hover));
        UiRender.rect(m, box.x, box.y, box.w, box.h, box.h / 2f, mulAlpha(body, alpha));
        UiRender.outline(m, box.x, box.y, box.w, box.h, box.h / 2f,
                mulAlpha(UiRender.mix(GLASS_LINE, 0x66FFFFFF, st.hover), alpha), 1f);
        UiRender.iconCentered(m, glyph, cx, cy, size,
                mulAlpha(UiRender.lighten(fg, 0.35f * st.hover), alpha));
        m.pop();
    }

    /** Icon-only accent button (confirm actions): gradient border + glyph. */
    private void drawAccentIconButton(MatrixStack m, BtnState st, R box, Icons.Glyph glyph, float size,
                                      int bg, int fg) {
        if (box.w <= 0f || box.h <= 0f) {
            return;
        }
        easeButton(st, box);
        float s = UiRender.pressScale(st.press);
        float cx = box.x + box.w / 2f, cy = box.y + box.h / 2f;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);
        UiRender.gradientBorder(m, box.x, box.y, box.w, box.h, box.h / 2f,
                UiRender.lighten(bg, 0.08f * UiRender.clamp01(st.hover)));
        UiRender.iconCentered(m, glyph, cx, cy, size, fg);
        m.pop();
    }

    private void easeButton(BtnState st, R box) {
        float kH = 1f - (float) Math.exp(-frameDt * 14.0);
        float kP = 1f - (float) Math.exp(-frameDt * 24.0);
        boolean hov = UiRender.inside(mouseX, mouseY, box.x, box.y, box.w, box.h);
        st.hover += ((hov ? 1f : 0f) - st.hover) * kH;
        st.press += ((hov && st == pressedBtn ? 1f : 0f) - st.press) * kP;
    }

    // ------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        if (modal) {
            return clickModal(mouseX, mouseY);
        }
        if (state == State.SPLASH) {
            if (UiRender.inside(mouseX, mouseY, socBox[0].x, socBox[0].y, socBox[0].w, socBox[0].h)) {
                openUrl(URL_TELEGRAM);
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, socBox[1].x, socBox[1].y, socBox[1].w, socBox[1].h)) {
                openUrl(URL_GITHUB);
                return true;
            }
            startTransition();
            return true;
        }
        if (state == State.TRANSITION) {
            return true;
        }

        // MENU — clicks ignored while the zoom-in is still settling: chrome is
        // drawn scaled up to 4% while hits are recorded in unscaled coordinates
        if (System.currentTimeMillis() - menuStart < MENU_IN_MS) {
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, socBox[0].x, socBox[0].y, socBox[0].w, socBox[0].h)) {
            openUrl(URL_TELEGRAM);
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, socBox[1].x, socBox[1].y, socBox[1].w, socBox[1].h)) {
            openUrl(URL_GITHUB);
            return true;
        }
        for (int i = 0; i < navBox.length; i++) {
            R box = navBox[i];
            if (UiRender.inside(mouseX, mouseY, box.x, box.y, box.w, box.h)) {
                navPressed = i;
                runNav(i);
                return true;
            }
        }
        return true;
    }

    private boolean clickModal(double mouseX, double mouseY) {
        if (panelH <= 0 || modalClosing) {
            return true;
        }
        // ignore clicks while the entry animation is still settling
        if (modalVis < 0.9f) {
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, closeHit.x, closeHit.y, closeHit.w, closeHit.h)) {
            pressedBtn = closeBtn;
            closeModal();
            return true;
        }
        // the preview card captures the drag — rotate the 3D skin
        if (previewHit != null && UiRender.inside(mouseX, mouseY,
                previewHit.x, previewHit.y, previewHit.w, previewHit.h)) {
            skinRenderer.beginDrag(mouseX, mouseY);
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, inputHit.x, inputHit.y, inputHit.w, inputHit.h)) {
            focus = 1;
            editIndex = -1;
            pinnedNick = null;
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, createHit.x, createHit.y, createHit.w, createHit.h)) {
            pressedBtn = createBtn;
            submitNew();
            return true;
        }
        List<String> names = AccountManager.names();
        for (int i = 0; i < names.size() && i < rowRec.length; i++) {
            if (!rowRec[i]) {
                continue;   // scrolled out / never drawn this frame
            }
            R login = rowLogin[i], edit = rowEdit[i], del = rowDel[i], box = rowBox[i];
            if (UiRender.inside(mouseX, mouseY, login.x, login.y, login.w, login.h)) {
                pressedBtn = rowLoginBtn[i];
                AccountManager.apply(names.get(i));
                closeModal();
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, edit.x, edit.y, edit.w, edit.h)) {
                pressedBtn = rowEditBtn[i];
                if (focus == 2 && editIndex == i) {
                    commitEdit();
                } else {
                    focus = 2;
                    editIndex = i;
                    editOldName = names.get(i);
                    editText = editOldName;
                }
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, del.x, del.y, del.w, del.h)) {
                pressedBtn = rowDelBtn[i];
                String removed = names.get(i);
                AccountManager.delete(i);
                if (removed.equals(pinnedNick)) {
                    pinnedNick = null;
                }
                if (removed.equals(hoverNick)) {
                    hoverNick = null;
                    hoverCandidate = null;
                }
                if (editIndex == i) {
                    focus = 0;
                    editIndex = -1;
                } else if (editIndex > i) {
                    editIndex--;
                }
                return true;
            }
            // clicking the row body pins its nick in the preview
            if (UiRender.inside(mouseX, mouseY, box.x, box.y, box.w, box.h)) {
                pinnedNick = names.get(i);
                focus = 0;
                editIndex = -1;
                return true;
            }
        }
        if (!UiRender.inside(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            closeModal();
        } else {
            // click on empty panel space: leave the text fields
            focus = 0;
            editIndex = -1;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (modal && skinRenderer.isDragging()) {
            skinRenderer.drag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedBtn = null;
        navPressed = -1;
        if (modal) {
            skinRenderer.endDrag();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void runNav(int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (action) {
            case 0 -> mc.setScreen(new SelectWorldScreen(this));
            case 1 -> mc.setScreen(new MultiplayerScreen(this));
            case 2 -> mc.setScreen(new OptionsScreen(this, mc.options));
            case 3 -> openModal();
            case 4 -> mc.scheduleStop();
            default -> {
            }
        }
    }

    private void openModal() {
        modal = true;
        modalClosing = false;
        modalVis = 0f;
        modalStart = System.currentTimeMillis();
        focus = 1;
        input = "";
        editText = "";
        editIndex = -1;
        editOldName = "";
        listScroll = 0;
        scrollTarget = 0;
        inputErrorT = 0f;
        editErrorT = 0f;
        hoverCandidate = null;
        hoverNick = null;
        pinnedNick = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scan, int modifiers) {
        if (modal) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (focus == 1) {
                    submitNew();
                } else if (focus == 2 && editIndex >= 0) {
                    commitEdit();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus == 1 && !input.isEmpty()) {
                    input = input.substring(0, input.length() - 1);
                } else if (focus == 2 && !editText.isEmpty()) {
                    editText = editText.substring(0, editText.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (focus > 0) {
                    // first ESC leaves the field, second closes the modal
                    focus = 0;
                    editIndex = -1;
                } else {
                    closeModal();
                }
                return true;
            }
            return true;
        }
        if (state == State.SPLASH) {
            startTransition();
            return true;
        }
        return super.keyPressed(keyCode, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (modal && focus > 0) {
            boolean valid = c > 31 && c < 127
                    && (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_');
            if (valid) {
                if (focus == 1 && input.length() < 16) {
                    input += c;
                    pinnedNick = null;   // typing takes over the preview
                } else if (focus == 2 && editText.length() < 16) {
                    editText += c;
                }
            }
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (modal) {
            if (panelH <= 0 || modalClosing) {
                return true;
            }
            List<String> names = AccountManager.names();
            float listY = inputHit.y + inputHit.h + 12;
            float visible = Math.max(0f, panelY + panelH - 14 - listY);
            float total = names.size() * 36f - 6;
            float maxScroll = Math.max(0f, total - visible);
            scrollTarget = clamp(scrollTarget - (float) (verticalAmount * 36), 0f, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (modal) {
            closeModal();
        } else if (state == State.MENU) {
            state = State.SPLASH;
            animStart = System.currentTimeMillis();
            splashStart = animStart;
            navPillA = 0f;
            navPillInit = false;
        }
        // SPLASH: the title screen never closes on ESC
    }

    // ----------------------------------------------------------- helpers

    private void startTransition() {
        if (state == State.SPLASH) {
            state = State.TRANSITION;
            animStart = System.currentTimeMillis();
            navPillA = 0f;
            navPillInit = false;
        }
    }

    private void closeModal() {
        modalClosing = true;
        focus = 0;
        editIndex = -1;
        skinRenderer.endDrag();
    }

    private void submitNew() {
        if (AccountManager.create(input)) {
            input = "";
            inputErrorT = 0f;
        } else {
            inputErrorT = 1f;   // invalid or duplicate nick — shake the field
        }
    }

    private void commitEdit() {
        if (AccountManager.rename(editIndex, editText)) {
            if (pinnedNick != null && pinnedNick.equalsIgnoreCase(editOldName)) {
                pinnedNick = editText;
            }
            if (hoverNick != null && hoverNick.equalsIgnoreCase(editOldName)) {
                hoverNick = editText;
            }
            focus = 0;
            editIndex = -1;
        } else {
            editErrorT = 1f;   // shake the edit field, not the input field
        }
    }

    private void openUrl(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Throwable ignored) {
        }
    }

    // --- logo crops: u/v fractions of the keyed logo.png. The aspect is
    // computed from the REAL image size (LogoManager), so a source with any
    // proportions draws without stretching. ---

    /** TV-ligature crop — small top-left mark / GUI rail. */
    private static final float LG_U0 = 0.1691f, LG_V0 = 0.2735f, LG_U1 = 0.8357f, LG_V1 = 0.5718f;
    /** Full-content crop (ligature + TabVisuals wordmark) for the splash. */
    private static final float FL_U0 = 0.1005f, FL_V0 = 0.2759f, FL_U1 = 0.9067f, FL_V1 = 0.7671f;

    private static float cropAspect(float u0, float v0, float u1, float v1, float fallback) {
        int tw = LogoManager.width(), th = LogoManager.height();
        if (tw <= 0 || th <= 0) {
            return fallback;
        }
        return (u1 - u0) * tw / ((v1 - v0) * th);
    }

    private static float markAspect() {
        return cropAspect(LG_U0, LG_V0, LG_U1, LG_V1, 2.2465f);
    }

    private static float fullLogoAspect() {
        return cropAspect(FL_U0, FL_V0, FL_U1, FL_V1, 1.6315f);
    }

    /** Ligature-only mark (small top-left badge / GUI rail). */
    private void drawLogo(MatrixStack m, Identifier logo, float x, float y, float h, float alpha) {
        float w = h * markAspect();
        int col = ((int) (Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | 0x00FFFFFF;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Render2D.drawTexturedQuad(m, logo, x, x + w, y, y + h, LG_U0, LG_U1, LG_V0, LG_V1, col);
        RenderSystem.disableBlend();
    }

    /** Full logo (ligature + wordmark), aspect-correct, for the splash. */
    private void drawLogoFull(MatrixStack m, Identifier logo, float x, float y, float h, float alpha) {
        float w = h * fullLogoAspect();
        int col = ((int) (Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | 0x00FFFFFF;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Render2D.drawTexturedQuad(m, logo, x, x + w, y, y + h, FL_U0, FL_U1, FL_V0, FL_V1, col);
        RenderSystem.disableBlend();
    }

    /** Right-aligned text draw; returns the x it started at. */
    private float rightText(FontRenderer font, MatrixStack m, String s, float rightX, float y, int argb) {
        float x = rightX - font.getStringWidth(s);
        font.drawString(m, s, x, y, argb);
        return x;
    }

    /** Right-aligned, but never drawn left of the nav column. */
    private void rightTextClamped(FontRenderer font, MatrixStack m, String s, float rightX, float limit, float y, int argb) {
        float x = rightX - font.getStringWidth(s);
        font.drawString(m, s, Math.max(x, limit), y, argb);
    }

    /** Letter-spaced uppercase; words separated by a double gap. */
    private static String spaced(String s) {
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                sb.append("  ");
            }
            for (int i = 0; i < words[w].length(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(words[w].charAt(i));
            }
        }
        return sb.toString();
    }

    private static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }

    /** Snap a static chrome coordinate to the half-pixel grid (crisp edges). */
    private static float hp(float v) {
        return Math.round(v * 2f) / 2f;
    }

    /**
     * Draw-y that visually centres a string of height {@code h} on the line {@code cy}.
     * The font renders at 0.5 scale with a -3 translate, so a plain {@code cy - h/2}
     * sits the text too high against a centred icon/avatar; this compensates.
     */
    private static float centerTextY(FontRenderer f, String s, float cy) {
        return cy + 3f - f.getStringHeight(s) * 0.25f;
    }

    private static int mulAlpha(int argb, float factor) {
        return UiRender.mulAlpha(argb, factor);
    }
}
