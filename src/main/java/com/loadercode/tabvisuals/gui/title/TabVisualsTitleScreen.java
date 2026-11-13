// language: Java, file: TabVisualsTitleScreen.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.gui.account.AccountManager;
import com.loadercode.tabvisuals.gui.account.AccountRenderer;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.gui.title.section.AccountsSection;
import com.loadercode.tabvisuals.gui.title.section.HomeSection;
import com.loadercode.tabvisuals.gui.title.section.MenuSection;
import com.loadercode.tabvisuals.gui.title.section.SettingsSection;
import com.loadercode.tabvisuals.media.DiscordBridge;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

import static com.loadercode.tabvisuals.gui.title.MenuKit.*;

/**
 * TabVisuals title flow.
 *
 * <p>Splash: night backdrop with drifting snow, the now-playing chip
 * top-left, profile row top-right, the logo + letter-spaced wordmark in the
 * centre, scrolling lyrics, a pulsing "press any key" hint and socials.
 *
 * <p>Any key / click plays a shared-element transition: the logo and the
 * wordmark fly to the top-left mark of the menu while the splash chrome
 * sinks away and the menu chrome staggers in. The menu is a left nav column
 * (a spring-driven indicator bar follows the active item) and a content
 * area that hosts {@link MenuSection} pages with cross-slide transitions.
 *
 * <p>Everything is laid out on the {@link MenuKit} canvas, so the layout is
 * identical at every resolution and GUI scale; all animation is
 * delta-time based and survives frame hitches without jumping.
 */
public final class TabVisualsTitleScreen extends Screen {

    // ---- fonts
    private static final MenuFont F_WORD = new MenuFont(13, Fonts.Type.DEFAULT);
    private static final MenuFont F_HINT = new MenuFont(11, Fonts.Type.DEFAULT);
    private static final MenuFont F_COPY = new MenuFont(9.5f, Fonts.Type.REGULAR);
    private static final MenuFont F_SMALL = new MenuFont(11.5f, Fonts.Type.DEFAULT);
    private static final MenuFont F_VER = new MenuFont(9.5f, Fonts.Type.DEFAULT);
    private static final MenuFont F_NAV = new MenuFont(21, Fonts.Type.DEFAULT);
    private static final MenuFont F_TITLE = new MenuFont(31, Fonts.Type.SEMI);
    private static final MenuFont F_SUBT = new MenuFont(13, Fonts.Type.REGULAR);
    private static final MenuFont F_KEY = new MenuFont(9, Fonts.Type.SEMI);
    private static final MenuFont F_FALLBACK_LOGO = new MenuFont(64, Fonts.Type.BOLD);

    private static final String WORDMARK = spaced("TABVISUALS");
    private static final String HINT = spaced("НАЖМИТЕ ЛЮБУЮ КЛАВИШУ");
    private static final String COPY = "© 2026 TabVisuals";
    private static final String URL_TELEGRAM = "https://t.me/tabvisuals";
    private static final String URL_GITHUB = "https://github.com/Loadercode/TabVisuals";
    private static final Identifier TG_ICON = Identifier.of("tabvisuals", "textures/menu/icon_telegram.png");
    private static final Identifier GH_ICON = Identifier.of("tabvisuals", "textures/menu/icon_github.png");
    private static final String[] SOC_LABELS = {"@tabvisuals", "Loadercode"};
    private static final Identifier[] SOC_ICONS = {TG_ICON, GH_ICON};
    private static final String[] SOC_URLS = {URL_TELEGRAM, URL_GITHUB};

    // TV-ligature crop of logo.png (u/v fractions)
    private static final float LG_U0 = 0.1691f, LG_V0 = 0.2735f, LG_U1 = 0.8357f, LG_V1 = 0.5718f;

    // ---- nav
    private static final String[] NAV = {"Главная", "Одиночный мир", "Мультиплеер", "Аккаунты", "Настройки", "Выход"};
    private static final Icons.Glyph[] NAV_ICONS = {Icons.DASHBOARD, Icons.CUBE, Icons.LINK, Icons.USER,
            Icons.SLIDERS, Icons.LOGOUT};
    private static final int NAV_HOME = 0, NAV_SP = 1, NAV_MP = 2, NAV_ACC = 3, NAV_SET = 4, NAV_QUIT = 5;

    private static final long TRANS_MS = 760L;
    private static final long PAGE_MS = 380L;

    // ---- snow: deterministic flakes (no RNG per frame)
    private static final int SNOW_N = 120;
    private static final float[] SN_X = new float[SNOW_N], SN_Y = new float[SNOW_N], SN_SPEED = new float[SNOW_N],
            SN_SIZE = new float[SNOW_N], SN_PH = new float[SNOW_N], SN_DEPTH = new float[SNOW_N];

    static {
        for (int i = 0; i < SNOW_N; i++) {
            SN_X[i] = (float) ((i * 0.618033988749895 + 0.13) % 1.0);
            SN_Y[i] = (float) ((i * 0.7548776662466927 + 0.37) % 1.0);
            float d = (float) ((i * 0.5698402909980532) % 1.0);
            SN_DEPTH[i] = d;
            SN_SIZE[i] = 0.55f + 1.45f * d * d;
            SN_SPEED[i] = 7f + 20f * d;
            SN_PH[i] = i * 1.731f;
        }
    }

    // ---- state
    private enum Mode {SPLASH, TO_MENU, MENU, TO_SPLASH}

    private Mode mode = Mode.SPLASH;
    private long transAt;
    private long splashAt = System.currentTimeMillis();
    private long menuAt;
    private final long openedAt = System.currentTimeMillis();
    private long lastNanos;

    private final MenuFrame frame = new MenuFrame();
    private final NowPlaying player = new NowPlaying();
    private final MenuSection[] sections = new MenuSection[6];
    private int section = NAV_HOME;
    private int prevSection = -1;
    private long sectionAt;

    private final Anim[] navAnim = new Anim[NAV.length];
    private final float[] navY = new float[NAV.length];
    private float navX, navW, navH;
    private final Spring navBar = new Spring(20f);
    private int keyFocus = -1;
    private final Anim[] socAnim = {new Anim(), new Anim()};
    private final float[] socX = new float[2], socW = new float[2];
    private float socY, socH = -1f;
    private final Anim escAnim = new Anim();
    private float escX, escY, escW, escH = -1f;

    private float parallaxX, parallaxY;
    private float miniPlayerT;
    private String version;
    private String nickCache = null, nickShown = "";

    public TabVisualsTitleScreen() {
        super(Text.literal("TabVisuals"));
        for (int i = 0; i < navAnim.length; i++) {
            navAnim[i] = new Anim();
        }
        sections[NAV_HOME] = new HomeSection(this, player);
        sections[NAV_ACC] = new AccountsSection();
        sections[NAV_SET] = new SettingsSection(this);
        LogoManager.texture();   // kick off the async load before the first frame
        version = FabricLoader.getInstance().getModContainer("tabvisuals")
                .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString()).orElse("v1.0");
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Menu progress 0 (splash) .. 1 (menu), eased. */
    private float menuT(long now) {
        return switch (mode) {
            case SPLASH -> 0f;
            case MENU -> 1f;
            case TO_MENU -> easeInOutCubic((now - transAt) / (float) TRANS_MS);
            case TO_SPLASH -> 1f - easeInOutCubic((now - transAt) / (float) (TRANS_MS * 0.85f));
        };
    }

    // ============================================================ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 60f : Math.min(1f / 30f, (nanos - lastNanos) / 1_000_000_000f);
        lastNanos = nanos;

        if (mode == Mode.TO_MENU && now - transAt >= TRANS_MS) {
            mode = Mode.MENU;
        } else if (mode == Mode.TO_SPLASH && now - transAt >= TRANS_MS * 0.85f) {
            mode = Mode.SPLASH;
        }

        MatrixStack m = ctx.getMatrices();
        // backdrop in GUI units, straight to the framebuffer
        ctx.draw();
        MenuBackground.render(m, width, height, 1f, now);

        float s = guiPerUnit();
        m.push();
        m.scale(s, s, 1f);
        MenuFrame f = frame;
        f.ctx = ctx;
        f.m = m;
        f.w = canvasW();
        f.h = canvasH();
        f.dt = dt;
        f.now = now;
        f.time = (now - openedAt) / 1000f;
        boolean focused = windowFocused();
        boolean busy = mode == Mode.TO_MENU || mode == Mode.TO_SPLASH;
        f.interactive = focused && !busy;
        f.mx = focused ? mouseX / s : -9999f;
        f.my = focused ? mouseY / s : -9999f;

        // a press can be lost if a click opened another screen before release
        if (MenuKit.pressed != null && client != null && client.getWindow() != null
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            MenuKit.pressed = null;
        }

        player.update(f);
        float t = menuT(now);

        // eased parallax from the cursor (snow + cover drift)
        float tx = focused ? (f.mx / f.w - 0.5f) : 0f, ty = focused ? (f.my / f.h - 0.5f) : 0f;
        parallaxX = approach(parallaxX, tx, 3f, dt);
        parallaxY = approach(parallaxY, ty, 3f, dt);

        renderCoverBackdrop(f, t);
        renderSnow(f, 1f - 0.35f * t);

        boolean modal = sections[section] != null && sections[section].modalOpen();
        if (modal) {
            f.interactive = false;
        }
        if (t < 0.999f) {
            renderSplash(f, 1f - t, t);
        }
        if (t > 0.001f) {
            renderMenu(f, t, now);
        }
        renderLogo(f, t, now);
        // profile row belongs to the splash (the menu header owns that corner)
        renderProfileRow(f, 1f - t, now);

        // dialogs of the active page get the mouse back
        f.interactive = focused && !busy;
        if (sections[section] != null && t > 0.5f) {
            sections[section].renderOverlay(f, t);
        }
        m.pop();
    }

    private static boolean windowFocused() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.getWindow() != null
                && GLFW.glfwGetWindowAttrib(mc.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    // ---------------------------------------------------------- backdrop

    /** The album cover as a dim, slowly breathing wash behind everything. */
    private void renderCoverBackdrop(MenuFrame f, float t) {
        if (player.shown <= 0.01f || !NowPlaying.setting("Cover glow")) {
            return;
        }
        Identifier cov = MusicManager.coverTexture();
        if (cov == null) {
            return;
        }
        float a = player.shown * (0.20f - 0.06f * t);
        float breathe = 1.06f + 0.03f * (float) Math.sin(f.time * 0.12f);
        float sz = Math.max(f.w, f.h) * breathe;
        float x = (f.w - sz) / 2f - parallaxX * 18f;
        float y = (f.h - sz) / 2f - parallaxY * 12f;
        cover(f.m, cov, MusicManager.coverSize(), MusicManager.coverHeight(), x, y, sz, 0f, alpha(0xFFFFFFFF, a));
        // keep the borders night-dark so the chrome reads
        gradient(f.m, 0f, 0f, f.w, f.h * 0.22f, 0, alpha(0xC0040310, player.shown), 0, 0,
                alpha(0xC0040310, player.shown));
        gradient(f.m, 0f, f.h * 0.70f, f.w, f.h * 0.30f, 0, 0, alpha(0xD0040310, player.shown),
                alpha(0xD0040310, player.shown), 0);
    }

    private void renderSnow(MenuFrame f, float alpha) {
        if (!beginGlow()) {
            return;
        }
        float t = f.time;
        float span = f.h + 20f;
        try {
        for (int i = 0; i < SNOW_N; i++) {
            float d = SN_DEPTH[i];
            float y = (SN_Y[i] * span + t * SN_SPEED[i]) % span - 10f;
            float x = SN_X[i] * f.w + (float) Math.sin(t * (0.35f + d * 0.4f) + SN_PH[i]) * (6f + 10f * d)
                    - parallaxX * (6f + 26f * d);
            y -= parallaxY * (3f + 10f * d);
            float r = SN_SIZE[i] * 2.3f;
            float a = alpha * (0.22f + 0.5f * d);
            // flakes fade near the top and bottom edges instead of popping
            float edge = Math.min(1f, Math.min((y + 10f) / 40f, (f.h + 10f - y) / 40f));
            if (edge <= 0f) {
                continue;
            }
            addGlow(f.m, x, y, r, r, alpha(0xFFEDE9FF, a * edge));
        }
        } finally {
            endGlow();
        }
    }

    // ------------------------------------------------------------ splash

    private void renderSplash(MenuFrame f, float sa, float t) {
        MatrixStack m = f.m;
        long now = f.now;
        float sink = t * 18f;   // splash chrome sinks while it fades

        player.drawCompact(f, 31f, 26f - t * 28f, sa * sa * enter(now, splashAt, 120L, 520L));

        // lyrics, low and centred
        float la = sa * enter(now, splashAt, 260L, 560L);
        player.drawLyrics(f, f.w / 2f, f.h * 0.79f + sink, f.w * 0.62f, la);

        // pulsing hint
        float ha = sa * enter(now, splashAt, 420L, 560L);
        float pulse = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(f.time * 2.6f));
        F_HINT.drawCentered(m, HINT, f.w / 2f, f.h * 0.905f + sink, alpha(TEXT_3, ha * pulse));

        F_COPY.drawCentered(m, COPY, f.w / 2f, f.h * 0.962f + sink, alpha(TEXT_4, sa * enter(now, splashAt, 520L, 500L)));
        renderSocials(f, sa * enter(now, splashAt, 480L, 520L), sink);
    }

    private void renderSocials(MenuFrame f, float a, float dy) {
        MatrixStack m = f.m;
        socH = -1f;
        if (a <= 0.01f) {
            return;
        }
        float y = f.h * 0.928f + dy;
        float x = f.w - 31f;
        for (int i = SOC_LABELS.length - 1; i >= 0; i--) {
            float w = 12f + 6f + F_SMALL.width(SOC_LABELS[i]);
            x -= w;
            socX[i] = x;
            socW[i] = w;
            Anim st = socAnim[i];
            st.update(f, x - 4f, y - 9f, w + 8f, 18f);
            int col = alpha(mix(TEXT_3, TEXT, st.hover), a);
            RenderSystem.enableBlend();
            texture(m, SOC_ICONS[i], x, y - 6f, 12f, 12f, 0f, 0f, 0f, 1f, 1f, col, true);
            F_SMALL.draw(m, SOC_LABELS[i], x + 18f, y, col);
            rect(m, x + 18f, y + 7f, (w - 18f) * st.hover, 0.8f, 0.4f, alpha(ACCENT, a * st.hover));
            x -= 22f;
        }
        socY = y - 9f;
        socH = a > 0.9f ? 18f : -1f;
    }

    // --------------------------------------------------------- shared logo

    /**
     * The logo is one element in both states: centred and big on the
     * splash, the small top-left mark in the menu. Its rect and the
     * wordmark's position/scale are interpolated along the transition.
     */
    private void renderLogo(MenuFrame f, float t, long now) {
        MatrixStack m = f.m;
        float aspect = logoAspect();
        float entry = enter(now, splashAt, 0L, 620L);
        float bob = (float) Math.sin(f.time * 1.1f) * 1.4f * (1f - t);

        float bigH = 64f, smallH = 17f;
        float h = lerp(bigH, smallH, t);
        float w = h * aspect;
        float bigCx = f.w / 2f, bigCy = f.h * 0.43f + bob + (1f - entry) * 10f;
        float smallX = 31f, smallCy = 35f;
        float cx = lerp(bigCx, smallX + smallH * aspect / 2f, t);
        float cy = lerp(bigCy, smallCy, t);
        float x = cx - w / 2f, y = cy - h / 2f;

        // breathing aura behind the big logo
        float breath = 0.8f + 0.2f * (float) Math.sin(f.time * 1.3f);
        glow(m, cx, cy, w * 0.95f * (1f - 0.6f * t), h * 1.5f * (1f - 0.5f * t),
                alpha(ACCENT_DEEP, 0.30f * breath * entry * (1f - 0.7f * t)));

        Identifier logo = LogoManager.textureFor(h / (LG_V1 - LG_V0) * unitPx());
        if (logo != null) {
            texture(m, logo, x, y, w, h, 0f, LG_U0, LG_V0, LG_U1, LG_V1, alpha(0xFFFFFFFF, entry), true);
        } else if (LogoManager.failed()) {
            pushScale(m, cx, cy, h / bigH);
            F_FALLBACK_LOGO.drawCentered(m, "TV", cx, cy, alpha(TEXT, entry));
            m.pop();
        }

        // wordmark: under the logo (centred) → right of the mark (left aligned)
        float wordScale = lerp(1f, 11f / 13f, t);
        float wordW = F_WORD.width(WORDMARK) * wordScale;
        float wx = lerp(bigCx - wordW / 2f, smallX + smallH * aspect + 12f, t);
        float wy = lerp(bigCy + bigH / 2f + 20f, smallCy, t);
        float wa = enter(now, splashAt, 160L, 620L);
        m.push();
        m.translate(wx, wy, 0f);
        m.scale(wordScale, wordScale, 1f);
        F_WORD.draw(m, WORDMARK, 0f, 0f, alpha(mix(TEXT_2, TEXT_3, t), wa));
        m.pop();
    }

    private static float logoAspect() {
        int tw = LogoManager.width(), th = LogoManager.height();
        if (tw <= 0 || th <= 0) {
            return 2.2347f;
        }
        return (LG_U1 - LG_U0) * tw / ((LG_V1 - LG_V0) * th);
    }

    // --------------------------------------------------------- profile row

    private void renderProfileRow(MenuFrame f, float alpha, long now) {
        MatrixStack m = f.m;
        float a = alpha * enter(now, openedAt, 200L, 600L);
        if (a <= 0.01f) {
            return;
        }
        float av = 15f;
        float right = f.w - 31f;
        float cy = 35f;
        float ax = right - av;

        Identifier avatar = DiscordBridge.avatar();
        String nick = AccountManager.current();
        if (!nick.equals(nickCache)) {
            nickCache = nick;
            nickShown = nick;
        }
        if (avatar != null) {
            texture(m, avatar, ax, cy - av / 2f, av, av, av / 2f, 0f, 0f, 1f, 1f, alpha(0xFFFFFFFF, a), true);
        } else if (!nickShown.isEmpty()) {
            skinFace(m, AccountRenderer.skinFor(nickShown), ax, cy - av / 2f, av, av / 2f, alpha(0xFFFFFFFF, a));
        } else {
            rect(m, ax, cy - av / 2f, av, av, av / 2f, alpha(ACCENT, a));
        }
        outline(m, ax, cy - av / 2f, av, av, av / 2f, alpha(0x30FFFFFF, a));

        String name = MusicManager.discordNick().isEmpty() ? nickShown : MusicManager.discordNick();
        float x = ax - 9f;
        F_SMALL.drawRight(m, name, x, cy, alpha(TEXT, a));
        x -= F_SMALL.width(name) + 12f;
        float vw = F_VER.width(version) + 12f;
        x -= vw;
        rect(m, x, cy - 7f, vw, 14f, 4f, alpha(0x0CFFFFFF, a));
        outline(m, x, cy - 7f, vw, 14f, 4f, alpha(LINE, a));
        F_VER.drawCentered(m, version, x + vw / 2f, cy, alpha(TEXT_3, a));
    }

    // -------------------------------------------------------------- menu

    private float contentX(MenuFrame f) {
        return Math.max(184f, f.w * 0.176f);
    }

    private void renderMenu(MenuFrame f, float t, long now) {
        MatrixStack m = f.m;
        float ma = t;
        // stagger is driven by the menu entry time, so it replays on every entry
        renderNav(f, ma, now);

        float cx = contentX(f);
        float right = f.w - 31f;
        float cw = right - cx;
        float top = 80f;
        float bottom = f.h - 36f;

        // header + hairline
        float p = clamp01((now - sectionAt) / (float) PAGE_MS);
        float pe = easeOutCubic(p);
        float headA = ma * enter(now, menuAt, 140L, 520L);
        MenuSection cur = sections[section];
        MenuSection prev = prevSection >= 0 ? sections[prevSection] : null;
        if (prev != null && pe < 1f) {
            float oa = headA * (1f - clamp01(p * 2.2f));
            F_TITLE.draw(m, prev.title(), cx - pe * 10f, 36f, alpha(TEXT, oa));
            F_SUBT.draw(m, prev.subtitle(), cx - pe * 10f, 56f, alpha(TEXT_3, oa));
            boolean wasInteractive = f.interactive;
            f.interactive = false;
            prev.renderHeader(f, right, 42f, oa);
            f.interactive = wasInteractive;
        }
        if (cur != null) {
            float na = headA * pe;
            float slide = (1f - pe) * 12f;
            F_TITLE.draw(m, F_TITLE.fit(cur.title(), cw * 0.6f), cx + slide, 36f, alpha(TEXT, na));
            F_SUBT.draw(m, F_SUBT.fit(cur.subtitle(), cw * 0.6f), cx + slide, 56f, alpha(TEXT_3, na));
            cur.renderHeader(f, right, 42f, na);
        }
        float lineW = cw * enter(now, menuAt, 200L, 700L);
        rect(m, cx, 68f, lineW, 0.6f, 0.3f, alpha(LINE, headA));

        // pages: the old one slides out left, the new one in from the right
        float pageA = ma * enter(now, menuAt, 220L, 520L);
        if (prev != null && p < 1f) {
            float oa = pageA * (1f - clamp01(p * 2.2f));
            if (oa > 0.01f) {
                boolean wasInteractive = f.interactive;
                f.interactive = false;
                m.push();
                m.translate(-pe * 14f, 0f, 0f);
                prev.render(f, cx, top, cw, bottom - top, oa);
                m.pop();
                f.interactive = wasInteractive;
            }
        } else if (prev != null) {
            prevSection = -1;
        }
        if (cur != null) {
            // hit rects are recorded from the drawn rect, so input is held until the slide settles
            boolean wasInteractive = f.interactive;
            if (p < 0.85f) {
                f.interactive = false;
            }
            float slide = (1f - pe) * 18f;
            m.push();
            m.translate(slide, 0f, 0f);
            cur.render(f, cx, top, cw, bottom - top, pageA * pe);
            m.pop();
            f.interactive = wasInteractive;
        }

        // mini player under the nav on pages other than home
        miniPlayerT = approach(miniPlayerT, section != NAV_HOME ? 1f : 0f, 8f, f.dt);
        if (miniPlayerT > 0.01f) {
            player.drawCompact(f, 31f, f.h - 92f + (1f - miniPlayerT) * 8f, ma * miniPlayerT);
        }

        renderEsc(f, ma * enter(now, menuAt, 400L, 500L));
    }

    private void renderNav(MenuFrame f, float ma, long now) {
        MatrixStack m = f.m;
        navX = 31f;
        navH = 24f;
        float y0 = f.h * 0.40f;
        float iconX = navX + 6f;
        float maxW = 0f;
        for (String s : NAV) {
            maxW = Math.max(maxW, F_NAV.width(s));
        }
        navW = 22f + maxW + 8f;
        float activeY = -1f;
        for (int i = 0; i < NAV.length; i++) {
            float y = y0 + i * 26f + (i == NAV_QUIT ? 14f : 0f);
            float e = enter(now, menuAt, 60L + i * 45L, 480L);
            float a = ma * e;
            float slide = (1f - e) * -10f;
            navY[i] = y - navH / 2f;
            Anim st = navAnim[i];
            st.update(f, navX + slide, navY[i], navW, navH, keyFocus == i);
            boolean active = i == section;
            if (active) {
                activeY = y;
            }
            float hx = st.hover * 3f;
            pushScale(m, navX + navW / 2f, y, st.scale());
            // soft glass pill under the hovered item
            if (st.hover > 0.01f && !active) {
                rect(m, navX - 4f + slide, y - navH / 2f + 1f, navW + 6f, navH - 2f, (navH - 2f) / 2f,
                        alpha(0x0FFFFFFF, a * st.hover));
            }
            int col = active ? TEXT : mix(TEXT_3, TEXT, st.hover);
            if (i == NAV_QUIT) {
                col = mix(TEXT_4, DANGER, st.hover);
            }
            icon(m, NAV_ICONS[i], iconX + slide + hx, y, 15f,
                    alpha(active ? ACCENT : mix(col, ACCENT, st.hover * 0.6f), a));
            F_NAV.draw(m, NAV[i], navX + 22f + slide + hx, y, alpha(col, a));
            m.pop();
        }
        // spring indicator bar: glides between items with a little momentum
        if (activeY >= 0f) {
            float by = navBar.update(activeY, f.dt);
            float stretch = Math.min(6f, Math.abs(navBar.velocity) * 0.02f);
            float bh = 14f + stretch;
            rect(m, 19f, by - bh / 2f, 2.5f, bh, 1.25f, alpha(ACCENT, ma));
            glow(m, 20f, by, 7f, 16f, alpha(ACCENT_DEEP, 0.55f * ma));
        }
    }

    private void renderEsc(MenuFrame f, float a) {
        MatrixStack m = f.m;
        escH = -1f;
        if (a <= 0.01f) {
            return;
        }
        float x = 31f, cy = f.h - 34f;
        String label = section == NAV_HOME ? "к заставке" : "назад";
        float kw = F_KEY.width("Esc") + 10f;
        escX = x - 3f;
        escY = cy - 9f;
        escW = kw + 10f + F_SMALL.width(label) + 6f;
        escH = 18f;
        escAnim.update(f, escX, escY, escW, escH);
        rect(m, x, cy - 6.5f, kw, 13f, 3.5f, alpha(mix(0x10FFFFFF, 0x22FFFFFF, escAnim.hover), a));
        outline(m, x, cy - 6.5f, kw, 13f, 3.5f, alpha(LINE, a));
        F_KEY.drawCentered(m, "Esc", x + kw / 2f, cy, alpha(TEXT_2, a));
        F_SMALL.draw(m, label, x + kw + 8f - escAnim.hover * -2f, cy, alpha(mix(TEXT_4, TEXT_2, escAnim.hover), a));
    }

    // ========================================================= navigation

    private void enterMenu() {
        if (mode != Mode.SPLASH) {
            return;
        }
        long now = System.currentTimeMillis();
        mode = Mode.TO_MENU;
        transAt = now;
        menuAt = now;
        navBar.reset();
        showSection(NAV_HOME, true);
    }

    private void backToSplash() {
        if (mode != Mode.MENU) {
            return;
        }
        mode = Mode.TO_SPLASH;
        transAt = System.currentTimeMillis();
        if (sections[section] != null) {
            sections[section].onHide();
        }
        keyFocus = -1;
    }

    private void showSection(int idx, boolean instant) {
        if (sections[idx] == null) {
            return;
        }
        if (idx == section && !instant) {
            return;
        }
        if (sections[section] != null) {
            sections[section].onHide();
        }
        prevSection = instant ? -1 : section;
        section = idx;
        sectionAt = System.currentTimeMillis() - (instant ? PAGE_MS : 0L);
        sections[idx].onShow();
    }

    private void activateNav(int i) {
        if (System.currentTimeMillis() - sectionAt < PAGE_MS / 2) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        MenuKit.pressed = navAnim[i];
        switch (i) {
            case NAV_SP -> mc.setScreen(new SelectWorldScreen(this));
            case NAV_MP -> mc.setScreen(new MultiplayerScreen(this));
            case NAV_QUIT -> mc.scheduleStop();
            default -> showSection(i, false);
        }
    }

    private void goBack() {
        if (section != NAV_HOME) {
            showSection(NAV_HOME, false);
        } else {
            backToSplash();
        }
    }

    // ============================================================== input

    private float cx(double v) {
        return (float) (v / guiPerUnit());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float mx = cx(mouseX), my = cx(mouseY);
        if (mode == Mode.TO_MENU || mode == Mode.TO_SPLASH) {
            return true;
        }
        if (mode == Mode.SPLASH) {
            if (button == 0 && socH > 0f) {
                for (int i = 0; i < SOC_URLS.length; i++) {
                    if (mx >= socX[i] - 4f && mx <= socX[i] + socW[i] + 4f && my >= socY && my <= socY + socH) {
                        MenuKit.pressed = socAnim[i];
                        openUrl(SOC_URLS[i]);
                        return true;
                    }
                }
            }
            enterMenu();
            return true;
        }
        MenuSection cur = sections[section];
        if (cur != null && cur.modalOpen()) {
            return cur.mouseClicked(mx, my, button) || true;
        }
        if (button == 0) {
            for (int i = 0; i < NAV.length; i++) {
                if (mx >= navX && mx <= navX + navW && my >= navY[i] && my <= navY[i] + navH) {
                    keyFocus = -1;
                    activateNav(i);
                    return true;
                }
            }
            if (escH > 0f && mx >= escX && mx <= escX + escW && my >= escY && my <= escY + escH) {
                MenuKit.pressed = escAnim;
                goBack();
                return true;
            }
        }
        if (cur != null && System.currentTimeMillis() - sectionAt > PAGE_MS * 0.85f) {
            cur.mouseClicked(mx, my, button);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        MenuKit.pressed = null;
        if (mode == Mode.MENU && sections[section] != null) {
            sections[section].mouseReleased(cx(mouseX), cx(mouseY), button);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (mode == Mode.MENU && sections[section] != null) {
            sections[section].mouseDragged(cx(mouseX), cx(mouseY), button);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (mode == Mode.MENU && sections[section] != null) {
            sections[section].mouseScrolled(cx(mouseX), cx(mouseY), (float) v);
        }
        return true;
    }

    /** Key whose press changed the mode — its auto-repeat is ignored until release. */
    private int swallowKey = Integer.MIN_VALUE;

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == swallowKey) {
            swallowKey = Integer.MIN_VALUE;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == swallowKey || mode == Mode.TO_MENU || mode == Mode.TO_SPLASH) {
            return true;
        }
        if (mode == Mode.SPLASH) {
            // modifier taps (alt-tab, screenshots) should not leave the splash
            switch (key) {
                case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_KEY_LEFT_CONTROL,
                        GLFW.GLFW_KEY_RIGHT_CONTROL, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
                        GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER, GLFW.GLFW_KEY_F2, GLFW.GLFW_KEY_F3,
                        GLFW.GLFW_KEY_PRINT_SCREEN, GLFW.GLFW_KEY_UNKNOWN -> {
                }
                default -> {
                    swallowKey = key;
                    enterMenu();
                }
            }
            return true;
        }
        MenuSection cur = sections[section];
        if (cur != null && (cur.modalOpen() || cur.keyPressed(key, scan, mods))) {
            if (cur.modalOpen()) {
                cur.keyPressed(key, scan, mods);
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (section == NAV_HOME) {
                    swallowKey = key;
                }
                goBack();
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> keyFocus = keyFocus <= 0 ? NAV.length - 1 : keyFocus - 1;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_TAB ->
                    keyFocus = keyFocus < 0 || keyFocus >= NAV.length - 1 ? 0 : keyFocus + 1;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (keyFocus >= 0) {
                    activateNav(keyFocus);
                }
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (mode == Mode.MENU && sections[section] != null) {
            sections[section].charTyped(c, mods);
        }
        return true;
    }

    @Override
    public void removed() {
        MenuKit.pressed = null;
    }

    private static void openUrl(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Throwable ignored) {
        }
    }
}
