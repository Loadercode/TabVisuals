// language: Java, file: ConnectOverlay.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import static com.loadercode.tabvisuals.gui.title.MenuKit.*;

/**
 * Replacement look of the vanilla "connecting" screen: the server card, a
 * four-step progress (Поиск → Соединение → Вход → Мир) driven by the vanilla
 * status text, an elapsed timer and rotating tips. Drawn by
 * ConnectScreenMixin; state is static because there is one connect at a time.
 */
public final class ConnectOverlay {

    private static final MenuFont F_KICKER = new MenuFont(11, Fonts.Type.SEMI);
    private static final MenuFont F_TITLE = new MenuFont(34, Fonts.Type.SEMI);
    private static final MenuFont F_NAME = new MenuFont(16, Fonts.Type.SEMI);
    private static final MenuFont F_ADDR = new MenuFont(12, Fonts.Type.REGULAR);
    private static final MenuFont F_LETTER = new MenuFont(20, Fonts.Type.SEMI);
    private static final MenuFont F_STEP = new MenuFont(12, Fonts.Type.DEFAULT);
    private static final MenuFont F_STATUS = new MenuFont(14, Fonts.Type.DEFAULT);
    private static final MenuFont F_SMALL = new MenuFont(11.5f, Fonts.Type.DEFAULT);
    private static final MenuFont F_KEY = new MenuFont(9, Fonts.Type.SEMI);
    private static final MenuFont F_WORD = new MenuFont(11, Fonts.Type.DEFAULT);
    private static final MenuFont F_CHECK = new MenuFont(13, Fonts.Type.BOLD);

    private static final String KICKER = spaced("ПОДКЛЮЧЕНИЕ");
    private static final String WORDMARK = spaced("TABVISUALS");
    private static final String[] STEPS = {"Поиск", "Соединение", "Вход", "Мир"};
    private static final String[] STATUS = {"Ищу сервер…", "Устанавливаю соединение…", "Вход на сервер…",
            "Загружаю мир…"};
    private static final String[] TIPS = {
            "Клавиша F5 переключает вид от первого и третьего лица.",
            "Правый Shift открывает меню модулей TabVisuals.",
            "Музыка из любого плеера отображается в главном меню.",
            "F3 + H показывает подробные подсказки предметов.",
            "Двойной клик по профилю в «Аккаунтах» сразу входит в него.",
            "Ctrl + колесо в чате меняет его масштаб в настройках чата."};
    private static final float LG_U0 = 0.1691f, LG_V0 = 0.2735f, LG_U1 = 0.8357f, LG_V1 = 0.5718f;

    private static String name = "Сервер Minecraft";
    private static String address = "";
    private static String letter = "C";
    private static int tileColor = 0xFF3E8E85;
    private static long startedAt = System.currentTimeMillis();
    private static long lastNanos;
    private static float stageAnim;
    private static int tipIndex;
    private static final MenuFrame FRAME = new MenuFrame();
    private static final Anim ESC = new Anim();
    private static float escX, escY, escW, escH = -1f;

    private ConnectOverlay() {
    }

    /** Called from ConnectScreen.connect — remembers what we are connecting to. */
    public static void begin(ServerAddress addr, ServerInfo info) {
        String n = info != null && info.name != null && !info.name.isBlank() ? info.name : "Сервер Minecraft";
        name = n;
        address = addr == null ? "" : addr.getPort() == 25565 ? addr.getAddress() : addr.getAddress() + ":" + addr.getPort();
        letter = n.substring(0, 1).toUpperCase();
        int[] palette = {0xFF3E8E85, 0xFF6D4AE0, 0xFFB4507A, 0xFF3B6FD4, 0xFFC0812F, 0xFF4F9A54};
        tileColor = palette[Math.floorMod(address.hashCode(), palette.length)];
        startedAt = System.currentTimeMillis();
        stageAnim = 0f;
        lastNanos = 0L;
        tipIndex = (int) (startedAt / 1000L % TIPS.length);
    }

    /** Stage 0..3 from the vanilla status key. */
    private static int stage(Text status, long elapsed) {
        String key = "";
        if (status != null && status.getContent() instanceof TranslatableTextContent t) {
            key = t.getKey();
        }
        if (key.contains("joining")) {
            return 3;
        }
        if (key.contains("authoriz") || key.contains("encrypt") || key.contains("negotiat")) {
            return 2;
        }
        // resolving happens before the first status update; show it briefly
        return elapsed < 450L ? 0 : 1;
    }

    /** True when the click hit "Esc отмена". */
    public static boolean escHit(double mouseX, double mouseY) {
        float s = guiPerUnit();
        float mx = (float) (mouseX / s), my = (float) (mouseY / s);
        return escH > 0f && mx >= escX && mx <= escX + escW && my >= escY && my <= escY + escH;
    }

    public static void render(DrawContext ctx, int width, int height, int mouseX, int mouseY, Text status) {
        long now = System.currentTimeMillis();
        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 60f : Math.min(1f / 30f, (nanos - lastNanos) / 1_000_000_000f);
        lastNanos = nanos;
        long elapsed = now - startedAt;

        MatrixStack m = ctx.getMatrices();
        ctx.draw();
        MenuBackground.render(m, width, height, 1f, now);

        float s = guiPerUnit();
        m.push();
        m.scale(s, s, 1f);
        MenuFrame f = FRAME;
        f.ctx = ctx;
        f.m = m;
        f.w = canvasW();
        f.h = canvasH();
        f.dt = dt;
        f.now = now;
        f.time = elapsed / 1000f;
        f.interactive = true;
        f.mx = mouseX / s;
        f.my = mouseY / s;

        float in = easeOutCubic(elapsed / 500f);
        float cx = f.w / 2f, cy = f.h / 2f;
        glow(m, cx, cy - 20f, f.w * 0.42f, f.h * 0.42f, alpha(0xFF5B3FD0, 0.30f * in));

        // top-left mark
        Identifier logo = LogoManager.textureFor(17f / (LG_V1 - LG_V0) * unitPx());
        float aspect = 2.2347f;
        if (logo != null) {
            texture(m, logo, 31f, 35f - 8.5f, 17f * aspect, 17f, 0f, LG_U0, LG_V0, LG_U1, LG_V1,
                    alpha(0xFFFFFFFF, in), true);
        }
        F_WORD.draw(m, WORDMARK, 31f + 17f * aspect + 12f, 35f, alpha(TEXT_3, in));

        float rise = (1f - in) * 10f;
        F_KICKER.drawCentered(m, KICKER, cx, cy - 92f + rise, alpha(ACCENT, in));
        F_TITLE.drawCentered(m, "Подключение к серверу", cx, cy - 68f + rise, alpha(TEXT, in));

        // server card
        float cardIn = enter(now, startedAt, 90L, 520L);
        String addrLine = address + "  ·  " + SharedConstants.getGameVersion().getName();
        float textW = Math.max(F_NAME.width(name), F_ADDR.width(addrLine));
        float cw = Math.min(f.w - 60f, 26f + 12f + textW + 26f);
        float ch = 38f;
        float cardX = cx - cw / 2f, cardY = cy - 38f + (1f - cardIn) * 10f;
        card(m, cardX, cardY, cw, ch, 10f, alpha(0x12FFFFFF, cardIn), alpha(LINE, cardIn));
        rect(m, cardX + 6f, cardY + 6f, 26f, 26f, 7f, alpha(tileColor, cardIn));
        F_LETTER.drawCentered(m, letter, cardX + 19f, cardY + 19f, alpha(TEXT, cardIn));
        F_NAME.draw(m, F_NAME.fit(name, cw - 52f), cardX + 40f, cardY + 13f, alpha(TEXT, cardIn));
        F_ADDR.draw(m, F_ADDR.fit(addrLine, cw - 52f), cardX + 40f, cardY + 26f, alpha(TEXT_3, cardIn));

        // stepper
        int st = stage(status, elapsed);
        stageAnim = approach(stageAnim, st, 7f, dt);
        float stepsIn = enter(now, startedAt, 180L, 520L);
        float sx0 = cx - 120f, gap = 80f, sy = cy + 26f;
        rect(m, sx0, sy - 0.75f, gap * 3f, 1.5f, 0.75f, alpha(0x22FFFFFF, stepsIn));
        rect(m, sx0, sy - 0.75f, gap * Math.min(3f, stageAnim), 1.5f, 0.75f, alpha(ACCENT, stepsIn));
        for (int i = 0; i < 4; i++) {
            float x = sx0 + i * gap;
            float done = clamp01(stageAnim - i);           // 1 = this step passed
            float active = 1f - Math.min(1f, Math.abs(stageAnim - i));   // 1 = current step
            if (active > 0.01f) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(f.time * 4f);
                glow(m, x, sy, 14f, 14f, alpha(ACCENT_DEEP, (0.35f + 0.25f * pulse) * active * stepsIn));
                outline(m, x - 9f, sy - 9f, 18f, 18f, 9f, alpha(ACCENT, active * stepsIn * 0.7f));
            }
            int fill = mix(0xFF0B0A14, ACCENT_SOFT, done);
            rect(m, x - 7f, sy - 7f, 14f, 14f, 7f, alpha(fill, stepsIn));
            if (done < 0.99f) {
                outline(m, x - 7f, sy - 7f, 14f, 14f, 7f, alpha(0x30FFFFFF, stepsIn * (1f - done)));
            }
            if (done > 0.05f) {
                drawCheck(m, x, sy, alpha(0xFF2A1F55, stepsIn * done));
            }
            int lc = mix(TEXT_4, TEXT, Math.max(active, done * 0.6f));
            F_STEP.drawCentered(m, STEPS[i], x, sy + 18f, alpha(lc, stepsIn));
        }

        // status line + timer
        float statIn = enter(now, startedAt, 260L, 520L);
        String line = STATUS[st];
        String timer = time(elapsed);
        float lw = F_STATUS.width(line) + 10f + F_SMALL.width(timer);
        float lx = cx - lw / 2f + 6f;
        float ly = cy + 68f;
        float pulse = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(f.time * 5f));
        rect(m, lx - 12f, ly - 2.5f, 5f, 5f, 2.5f, alpha(ACCENT, statIn * pulse));
        F_STATUS.draw(m, line, lx, ly, alpha(TEXT, statIn));
        F_SMALL.draw(m, timer, lx + F_STATUS.width(line) + 10f, ly, alpha(TEXT_3, statIn));

        // rotating tip: crossfade every 6 s
        long tipT = elapsed % 6000L;
        int tip = (int) ((tipIndex + elapsed / 6000L) % TIPS.length);
        float tipA = Math.min(1f, Math.min(tipT / 400f, (6000L - tipT) / 400f)) * enter(now, startedAt, 400L, 600L);
        float tipW = F_KEY.width("СОВЕТ") + 10f + F_SMALL.width(TIPS[tip]);
        float tx = cx - tipW / 2f;
        F_KEY.draw(m, "СОВЕТ", tx, f.h - 34f, alpha(TEXT_2, tipA));
        F_SMALL.draw(m, TIPS[tip], tx + F_KEY.width("СОВЕТ") + 10f, f.h - 34f, alpha(TEXT_3, tipA));

        // Esc / cancel
        float ex = 31f, ecy = f.h - 34f;
        float kw = F_KEY.width("Esc") + 10f;
        escX = ex - 3f;
        escY = ecy - 9f;
        escW = kw + 14f + F_SMALL.width("отмена");
        escH = 18f;
        ESC.update(f, escX, escY, escW, escH);
        rect(m, ex, ecy - 6.5f, kw, 13f, 3.5f, alpha(mix(0x10FFFFFF, 0x22FFFFFF, ESC.hover), in));
        outline(m, ex, ecy - 6.5f, kw, 13f, 3.5f, alpha(LINE, in));
        F_KEY.drawCentered(m, "Esc", ex + kw / 2f, ecy, alpha(TEXT_2, in));
        F_SMALL.draw(m, "отмена", ex + kw + 8f, ecy, alpha(mix(TEXT_4, TEXT_2, ESC.hover), in));

        m.pop();
    }

    /** Tick mark — a glyph: the rounded-rect shader is axis-aligned, so rotated bars would not draw. */
    private static void drawCheck(MatrixStack m, float cx, float cy, int argb) {
        F_CHECK.drawCentered(m, "✓", cx, cy, argb);
    }
}
