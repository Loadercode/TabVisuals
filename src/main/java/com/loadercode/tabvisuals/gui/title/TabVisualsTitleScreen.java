// language: Java, file: TabVisualsTitleScreen.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.account.AccountManager;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * TabVisuals title flow per ТЗ + reference photos:
 * splash (gradient, centered logo + TABVISUALS slogan, low lyrics, pulsing
 * hint, compact profile row, inline socials) → zoom-in transition → main menu
 * (album-cover glow backdrop, top-left mark, left icon nav, right lyric block),
 * plus the alt-manager modal opened from "Аккаунты".
 */
public final class TabVisualsTitleScreen extends Screen {

    // --- palette (purple top-left → deep blue/black bottom-right) ---
    private static final int GRAD_TL = 0xFF8B4BF5;
    private static final int GRAD_BL = 0xFF2A1B4A;
    private static final int GRAD_TR = 0xFF171430;
    private static final int GRAD_BR = 0xFF0A0A14;
    private static final int ACCENT = 0xFF9B6BFF;
    private static final int ACCENT_HOVER = 0xFFB697FF;
    private static final int INK = 0xFF251840;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int NAV_IDLE = 0xFFB9BAC6;

    private static final String URL_TELEGRAM = "https://t.me/tabvisuals";
    private static final String URL_GITHUB = "https://github.com/Loadercode/TabVisuals";

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
    private static final long TRANS_MS = 450;
    private static final long MENU_IN_MS = 350;

    private enum State {SPLASH, TRANSITION, MENU}

    private State state = State.SPLASH;
    private long animStart = System.currentTimeMillis();
    private long menuStart;

    // --- modal state ---
    private boolean modal;
    /** 0 = none, 1 = new-nick field, 2 = row rename field. */
    private int focus;
    private int editIndex = -1;
    private String input = "";
    private String editText = "";
    private float listScroll;

    // --- hit caches (filled during render) ---
    private record R(float x, float y, float w, float h) {
    }

    private record NavHit(int action, R box) {
    }

    private record RowHit(int index, R box, R login, R edit, R del) {
    }

    private final List<NavHit> navHits = new ArrayList<>();
    private final List<RowHit> rowHits = new ArrayList<>();
    private R tgHit, ghHit, closeHit, inputHit, createHit;
    private float panelX, panelY, panelW, panelH;

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
        long now = System.currentTimeMillis();

        UiRender.gradient(m, 0, 0, width, height, GRAD_TL, GRAD_BL, GRAD_BR, GRAD_TR);

        switch (state) {
            case SPLASH -> renderSplash(m, mouseX, mouseY, now, 1f, 1f);
            case TRANSITION -> {
                float p = clamp01((now - animStart) / (float) TRANS_MS);
                float e = easeOut(p);
                renderSplash(m, mouseX, mouseY, now, 1f + 0.30f * e, 1f - e);
                if (p >= 1f) {
                    state = State.MENU;
                    menuStart = now;
                }
            }
            case MENU -> renderMenu(m, mouseX, mouseY, now);
        }

        if (modal) {
            renderModal(m, mouseX, mouseY, now);
        }
    }

    private void renderSplash(MatrixStack m, float mx, float my, long now, float scale, float alpha) {
        m.push();
        if (Math.abs(scale - 1f) > 0.001f) {
            float cx = width / 2f, cy = height / 2f;
            m.translate(cx, cy, 0f);
            m.scale(scale, scale, 1f);
            m.translate(-cx, -cy, 0f);
        }

        // photo 1: center logo + letterspaced slogan, no top-left mark
        F_LOGO.drawCenteredString(m, "TabVisuals", width / 2.0, height * 0.415, mulAlpha(WHITE, alpha));
        F_SLOGAN.drawCenteredString(m, spaced("TABVISUALS"), width / 2.0, height * 0.545,
                mulAlpha(0xB3FFFFFF, alpha));

        // lyrics low on the screen: big current line + small next one
        float lineY = height * 0.785f;
        F_LYRIC_BIG.drawCenteredString(m, MusicManager.currentLine(), width / 2.0, lineY,
                mulAlpha(WHITE, alpha));
        F_LYRIC_SMALL.drawCenteredString(m, MusicManager.nextLine(), width / 2.0, height * 0.845f,
                mulAlpha(0x99FFFFFF, alpha));

        // pulsing hint near the bottom edge
        float pulse = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(now / 360.0));
        F_HINT.drawCenteredString(m, spaced("НАЖМИТЕ ЛЮБУЮ КЛАВИШУ"), width / 2.0, height * 0.915,
                mulAlpha(WHITE, alpha * pulse));

        renderProfileRow(m, alpha);
        renderSocials(m, mx, my, alpha);
        F_COPY.drawCenteredString(m, "© 2026 TabVisuals", width / 2.0, height * 0.968,
                mulAlpha(0x70FFFFFF, alpha));

        m.pop();
    }

    private void renderMenu(MatrixStack m, float mx, float my, long now) {
        float p = clamp01((now - menuStart) / (float) MENU_IN_MS);
        float e = easeOut(p);

        // photo 2: blurred album-cover glow + dark readability overlay, fades in
        renderCoverGlow(m, e);

        float cx = width / 2f, cy = height / 2f;
        float s = 1.04f - 0.04f * e;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);

        renderMark(m, e);
        renderProfileRow(m, e);
        renderNav(m, mx, my, e);
        renderMenuLyrics(m, e);
        renderSocials(m, mx, my, e);
        F_COPY.drawCenteredString(m, "© 2026 TabVisuals", width / 2.0, height * 0.968,
                mulAlpha(0x70FFFFFF, e));

        m.pop();
    }

    /**
     * Stand-in for the "now playing" cover art stretched full-screen: stacked
     * translucent circles make a soft violet/blue radial glow over near-black,
     * then a dark scrim + edge gradients keep text readable (ТЗ: полупрозрачный
     * оверлей поверх обложки). Real cover textures swap in behind this later.
     */
    private void renderCoverGlow(MatrixStack m, float alpha) {
        float w = width, h = height;
        float maxD = Math.max(w, h) * 1.15f;

        UiRender.rect(m, 0, 0, w, h, 0, mulAlpha(0xFF0A0912, alpha));

        // violet core, left of center like a lit album cover.
        // many faint layers with jittered centers → smooth glow, no ring banding.
        float cvx = w * 0.40f, cvy = h * 0.44f;
        for (int i = 36; i >= 0; i--) {
            float f = i / 36f;
            float d = maxD * (0.14f + 0.86f * f);
            int a = (int) (0x03 + 0x05 * (1f - f));
            float jx = (float) Math.sin(i * 1.7) * 0.07f * maxD * f;
            float jy = (float) Math.cos(i * 2.3) * 0.05f * maxD * f;
            UiRender.rect(m, cvx - d / 2f + jx, cvy - d / 2f + jy, d, d, d / 2f,
                    mulAlpha((a << 24) | 0x6B3FE8, alpha));
        }
        // cooler blue falloff to the right
        float bnx = w * 0.70f, bny = h * 0.62f;
        for (int i = 22; i >= 0; i--) {
            float f = i / 22f;
            float d = maxD * (0.08f + 0.55f * f);
            int a = (int) (0x03 + 0x04 * (1f - f));
            float jx = (float) Math.cos(i * 2.1) * 0.06f * maxD * f;
            float jy = (float) Math.sin(i * 1.9) * 0.05f * maxD * f;
            UiRender.rect(m, bnx - d / 2f + jx, bny - d / 2f + jy, d, d, d / 2f,
                    mulAlpha((a << 24) | 0x3D55C8, alpha));
        }

        // dark overlay for readability
        UiRender.rect(m, 0, 0, w, h, 0, mulAlpha(0x66000000, alpha));

        // vignette: four edge gradients pulling the corners down
        UiRender.gradient(m, 0, 0, w, h * 0.16f, 0x50000000, 0x00000000, 0x00000000, 0x50000000);
        UiRender.gradient(m, 0, h * 0.84f, w, h * 0.16f, 0x00000000, 0x50000000, 0x50000000, 0x00000000);
        UiRender.gradient(m, 0, 0, w * 0.14f, h, 0x45000000, 0x45000000, 0x00000000, 0x00000000);
        UiRender.gradient(m, w * 0.86f, 0, w * 0.14f, h, 0x00000000, 0x00000000, 0x45000000, 0x45000000);
    }

    /** Top-left logo tile + letterspaced wordmark — main menu only (photo 2). */
    private void renderMark(MatrixStack m, float alpha) {
        float tile = Math.max(18f, width * 0.024f);
        float lx = width * 0.0325f, ly = height * 0.038f;
        UiRender.rect(m, lx, ly, tile, tile, tile * 0.3f, mulAlpha(0x1AFFFFFF, alpha));
        UiRender.outline(m, lx, ly, tile, tile, tile * 0.3f, mulAlpha(0x30FFFFFF, alpha), 1f);
        F_MARK.drawCenteredString(m, "T", lx + tile / 2.0,
                ly + (tile - F_MARK.getStringHeight("T")) / 2f + 1, mulAlpha(WHITE, alpha));
        String mark = spaced("TABVISUALS");
        F_SLOGAN.drawString(m, mark, lx + tile + width * 0.010f,
                ly + (tile - F_SLOGAN.getStringHeight(mark)) / 2f + 1, mulAlpha(WHITE, alpha));
    }

    /** Top-right compact profile row: version, status, nickname, round avatar (photo 1/2). */
    private void renderProfileRow(MatrixStack m, float alpha) {
        float av = 15;
        float right = width - width * 0.026f;
        float top = height * 0.038f;
        float cy = top + av / 2f;

        // avatar
        float ax = right - av;
        UiRender.rect(m, ax, top, av, av, av / 2f, mulAlpha(0xFF7C5CE0, alpha));
        String nick = AccountManager.current();
        String initial = nick.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(nick.charAt(0)));
        F_BTN.drawCenteredString(m, initial, ax + av / 2.0,
                top + (av - F_BTN.getStringHeight(initial)) / 2f + 1, mulAlpha(WHITE, alpha));

        // nickname (dim), right of status
        float x = ax - 6;
        x = rightText(F_TINY, m, nick, x, cy - F_TINY.getStringHeight(nick) / 2f + 1,
                mulAlpha(0xB8FFFFFF, alpha)) - 8;

        // "Protected" — the bright one
        x = rightText(F_TINY, m, "Protected", x, cy - F_TINY.getStringHeight("Protected") / 2f + 1,
                mulAlpha(WHITE, alpha)) - 8;

        // version
        rightText(F_TINY, m, "v1.0.0", x, cy - F_TINY.getStringHeight("v1.0.0") / 2f + 1,
                mulAlpha(0x8CFFFFFF, alpha));
    }

    /** Bottom-right inline socials row (photo 1/2): badge, platform, handle — clickable. */
    private void renderSocials(MatrixStack m, float mx, float my, float alpha) {
        String[] labels = {"Telegram", "GitHub"};
        String[] handles = {"@tabvisuals", "Loadercode"};
        String[] letters = {"T", "G"};

        float y = height * 0.93f;
        float rowH = 13;
        float gap = width * 0.016f;   // between the two entries
        float badge = 12;

        // measure the row, right-align it
        float total = 0;
        for (int i = 0; i < 2; i++) {
            total += badge + 5 + F_TINY.getStringWidth(labels[i]) + 5 + F_TINY.getStringWidth(handles[i]);
            if (i == 0) total += gap;
        }
        float x = width - width * 0.026f - total;

        R[] hits = new R[2];
        for (int i = 0; i < 2; i++) {
            float itemW = badge + 5 + F_TINY.getStringWidth(labels[i]) + 5 + F_TINY.getStringWidth(handles[i]);
            boolean hover = UiRender.inside(mx, my, x, y - 1, itemW, rowH);

            UiRender.rect(m, x, y, badge, badge, 3.5f, mulAlpha(hover ? 0x30FFFFFF : 0x1CFFFFFF, alpha));
            F_TINY.drawCenteredString(m, letters[i], x + badge / 2.0,
                    y + (badge - F_TINY.getStringHeight(letters[i])) / 2f + 1,
                    mulAlpha(hover ? WHITE : 0xC8FFFFFF, alpha));

            float tx = x + badge + 5;
            F_TINY.drawString(m, labels[i], tx, y + (rowH - F_TINY.getStringHeight(labels[i])) / 2f + 1,
                    mulAlpha(hover ? 0xE6FFFFFF : 0x8CFFFFFF, alpha));
            tx += F_TINY.getStringWidth(labels[i]) + 5;
            F_TINY.drawString(m, handles[i], tx, y + (rowH - F_TINY.getStringHeight(handles[i])) / 2f + 1,
                    mulAlpha(hover ? WHITE : 0xB8FFFFFF, alpha));

            hits[i] = new R(x, y - 1, itemW, rowH);
            x += itemW + gap;
        }
        tgHit = hits[0];
        ghHit = hits[1];
    }

    /** Left navigation column: bare icon+label rows, photo 2 proportions. */
    private void renderNav(MatrixStack m, float mx, float my, float alpha) {
        String[] labels = {"Одиночная игра", "Мультиплейер", "Настройки", "Аккаунты", "Выход"};
        float iconX = width * 0.0325f;
        float iconSz = Math.max(11f, width * 0.014f);
        float textX = iconX + iconSz + width * 0.007f;
        float step = height * 0.051f;
        float firstCy = height * 0.392f;
        float extraGap = height * 0.014f;   // wider break before "Выход", like the photo

        navHits.clear();
        float cy = firstCy;
        for (int i = 0; i < labels.length; i++) {
            if (i == 4) {
                cy += extraGap;
            }
            String label = labels[i];
            float textW = F_NAV.getStringWidth(label);
            float boxW = textX + textW - iconX;
            R box = new R(iconX, cy - step / 2f, boxW, step);
            boolean hover = UiRender.inside(mx, my, box.x(), box.y(), box.w(), box.h());

            int col = mulAlpha(hover ? WHITE : NAV_IDLE, alpha);
            drawNavIcon(m, i, iconX + iconSz / 2f, cy, iconSz, col);
            F_NAV.drawString(m, label, textX,
                    cy - F_NAV.getStringHeight(label) / 2f + 1, col);
            navHits.add(new NavHit(i, box));
            cy += step;
        }
    }

    private void drawNavIcon(MatrixStack m, int idx, float cx, float cy, float sz, int col) {
        float r = sz / 2f;
        float t = Math.max(1.1f, sz * 0.10f);   // line thickness
        switch (idx) {
            case 0 -> { // globe: ring reads first, meridians stay inside it
                UiRender.outline(m, cx - r, cy - r, sz, sz, r, col, Math.max(1.5f, t * 1.35f));
                float inset = Math.max(1.2f, t);
                UiRender.rect(m, cx - t / 2f, cy - r + inset, t, sz - inset * 2f, t / 2f, col);
                UiRender.rect(m, cx - r + inset, cy - t / 2f, sz - inset * 2f, t, t / 2f, col);
            }
            case 1 -> { // connection bars
                float bw = Math.max(1.6f, sz * 0.18f);
                UiRender.rect(m, cx - r, cy + r * 0.1f, bw, r * 0.8f, bw / 2f, col);
                UiRender.rect(m, cx - bw / 2f, cy - r * 0.35f, bw, r * 1.25f, bw / 2f, col);
                UiRender.rect(m, cx + r - bw, cy - r * 0.75f, bw, r * 1.65f, bw / 2f, col);
            }
            case 2 -> { // sliders
                UiRender.rect(m, cx - r, cy - r * 0.7f, sz, t, t / 2f, col);
                UiRender.rect(m, cx - r, cy - t / 2f, sz, t, t / 2f, col);
                UiRender.rect(m, cx - r, cy + r * 0.7f - t, sz, t, t / 2f, col);
                float kd = Math.max(2.6f, sz * 0.26f);
                UiRender.rect(m, cx - r * 0.55f, cy - r * 0.7f - (kd - t) / 2f, kd, kd, kd / 2f, col);
                UiRender.rect(m, cx + r * 0.15f, cy - t / 2f - (kd - t) / 2f, kd, kd, kd / 2f, col);
                UiRender.rect(m, cx - r * 0.2f, cy + r * 0.7f - t - (kd - t) / 2f, kd, kd, kd / 2f, col);
            }
            case 3 -> { // person
                float hd = sz * 0.42f;
                UiRender.rect(m, cx - hd / 2f, cy - r, hd, hd, hd / 2f, col);
                UiRender.rect(m, cx - r * 0.85f, cy + hd * 0.15f, r * 1.7f, r * 0.95f, r * 0.48f, col);
            }
            default -> { // exit arrow
                F_NAV.drawCenteredString(m, "→", cx, cy - F_NAV.getStringHeight("→") / 2f + 1, col);
            }
        }
    }

    /** Right block: prev / current / next lyric lines, right-aligned, vertically centered (photo 2). */
    private void renderMenuLyrics(MatrixStack m, float alpha) {
        float rightX = width - width * 0.0325f;
        // keep clear of the nav column
        float navTextX = width * 0.0325f + Math.max(11f, width * 0.014f) + width * 0.007f;
        float limit = navTextX + F_NAV.getStringWidth("Одиночная игра") + 20;

        rightTextClamped(F_MENU_NEXT, m, MusicManager.prevLine(), rightX, limit,
                height * 0.45f - F_MENU_NEXT.getStringHeight(MusicManager.prevLine()) / 2f + 1,
                mulAlpha(0x8CFFFFFF, alpha));
        rightTextClamped(F_MENU_LINE, m, MusicManager.currentLine(), rightX, limit,
                height * 0.503f - F_MENU_LINE.getStringHeight(MusicManager.currentLine()) / 2f + 1,
                mulAlpha(WHITE, alpha));
        rightTextClamped(F_MENU_NEXT, m, MusicManager.nextLine(), rightX, limit,
                height * 0.551f - F_MENU_NEXT.getStringHeight(MusicManager.nextLine()) / 2f + 1,
                mulAlpha(0x8CFFFFFF, alpha));
    }

    // -------------------------------------------------------------- modal

    private void renderModal(MatrixStack m, float mx, float my, long now) {
        UiRender.rect(m, 0, 0, width, height, 0, 0xB3000000);

        // fit any GUI scale: 500x380 is a max, never larger than the screen
        panelW = Math.min(500, width - 24);
        panelH = Math.min(380, height - 24);
        panelX = (width - panelW) / 2f;
        panelY = (height - panelH) / 2f;
        boolean blink = (now / 500) % 2 == 0;
        boolean tall = panelH >= 300;

        UiRender.rect(m, panelX, panelY, panelW, panelH, 14, 0xF515151D);
        UiRender.outline(m, panelX, panelY, panelW, panelH, 14, 0x1FFFFFFF, 1f);

        F_MODAL_T.drawString(m, "Аккаунты", panelX + 18, panelY + 13, WHITE);
        if (tall) {
            F_TINY.drawString(m, "Оффлайн-профили", panelX + 18, panelY + 37, 0x8CFFFFFF);
        }

        // close button
        closeHit = new R(panelX + panelW - 34, panelY + 10, 24, 24);
        if (UiRender.inside(mx, my, closeHit.x(), closeHit.y(), closeHit.w(), closeHit.h())) {
            UiRender.rect(m, closeHit.x(), closeHit.y(), closeHit.w(), closeHit.h(), 6, 0x1AFFFFFF);
        }
        F_MODAL_T.drawCenteredString(m, "×", closeHit.x() + 12,
                closeHit.y() + (24 - F_MODAL_T.getStringHeight("×")) / 2f + 1, 0xCCFFFFFF);

        // new-nick input + create button on one row
        float inputY = panelY + (tall ? 64 : 46);
        float rowH30 = 30;
        float createW = 92;
        inputHit = new R(panelX + 18, inputY, panelW - 36 - 6 - createW, rowH30);
        UiRender.rect(m, inputHit.x(), inputHit.y(), inputHit.w(), inputHit.h(), 8, 0x14FFFFFF);
        UiRender.outline(m, inputHit.x(), inputHit.y(), inputHit.w(), inputHit.h(), 8,
                focus == 1 ? ACCENT : 0x1AFFFFFF, 1f);
        String shown;
        int shownCol;
        if (input.isEmpty() && focus != 1) {
            shown = "Введите ник";
            shownCol = 0x66FFFFFF;
        } else {
            shown = input + (focus == 1 && blink ? "_" : "");
            shownCol = 0xE6FFFFFF;
        }
        F_INPUT.drawString(m, shown, inputHit.x() + 12,
                inputHit.y() + (inputHit.h() - F_INPUT.getStringHeight(shown)) / 2f + 1, shownCol);

        // create button
        createHit = new R(inputHit.x() + inputHit.w() + 6, inputY, createW, rowH30);
        boolean createHover = UiRender.inside(mx, my, createHit.x(), createHit.y(), createHit.w(), createHit.h());
        UiRender.rect(m, createHit.x(), createHit.y(), createHit.w(), createHit.h(), 8,
                createHover ? ACCENT_HOVER : ACCENT);
        F_BTN.drawCenteredString(m, "Создать", createHit.x() + createHit.w() / 2.0,
                createHit.y() + (createHit.h() - F_BTN.getStringHeight("Создать")) / 2f + 1, INK);

        // account list
        List<String> names = AccountManager.names();
        float listY = modalListTop();
        float visible = modalListVisible();
        float rowH = 30, step = 36;
        float total = names.size() * step - 6;
        float maxScroll = Math.max(0f, total - visible);
        listScroll = clamp(listScroll, 0f, maxScroll);
        rowHits.clear();

        if (names.isEmpty()) {
            F_INPUT.drawCenteredString(m, "Пока пусто — создайте первый профиль",
                    panelX + panelW / 2.0, listY + 16, 0x8CFFFFFF);
            return;
        }

        for (int i = 0; i < names.size(); i++) {
            float ry = listY + i * step - listScroll;
            if (ry > listY + visible || ry + rowH < listY) {
                continue;
            }
            String name = names.get(i);
            R row = new R(panelX + 18, ry, panelW - 36, rowH);
            boolean rowHover = UiRender.inside(mx, my, row.x(), row.y(), row.w(), row.h());
            UiRender.rect(m, row.x(), row.y(), row.w(), row.h(), 8,
                    rowHover ? 0x14FFFFFF : 0x0AFFFFFF);

            boolean editing = focus == 2 && editIndex == i;
            if (editing) {
                float fw = Math.max(70f, row.w() - 10 - 172 - 6);
                UiRender.rect(m, row.x() + 10, ry + 3, fw, 24, 6, 0x16FFFFFF);
                UiRender.outline(m, row.x() + 10, ry + 3, fw, 24, 6, ACCENT, 1f);
                String shownEdit = editText + (blink ? "_" : "");
                F_ROW.drawString(m, shownEdit, row.x() + 20,
                        ry + 3 + (24 - F_ROW.getStringHeight(shownEdit)) / 2f + 1, WHITE);
            } else {
                F_ROW.drawString(m, name, row.x() + 14,
                        ry + (rowH - F_ROW.getStringHeight(name)) / 2f + 1, 0xE6FFFFFF);
            }

            R del = new R(row.x() + row.w() - 10 - 56, ry + 3, 56, 24);
            R edit = new R(del.x() - 6 - 50, ry + 3, 50, 24);
            R login = new R(edit.x() - 6 - 54, ry + 3, 54, 24);

            // login (accent)
            boolean hov = UiRender.inside(mx, my, login.x(), login.y(), login.w(), login.h());
            UiRender.rect(m, login.x(), login.y(), login.w(), login.h(), 6, hov ? ACCENT_HOVER : ACCENT);
            F_BTN.drawCenteredString(m, "Войти", login.x() + login.w() / 2.0,
                    login.y() + (login.h() - F_BTN.getStringHeight("Войти")) / 2f + 1, INK);

            // rename / commit
            hov = UiRender.inside(mx, my, edit.x(), edit.y(), edit.w(), edit.h());
            if (editing) {
                UiRender.rect(m, edit.x(), edit.y(), edit.w(), edit.h(), 6, hov ? ACCENT_HOVER : ACCENT);
                F_BTN.drawCenteredString(m, "OK", edit.x() + edit.w() / 2.0,
                        edit.y() + (edit.h() - F_BTN.getStringHeight("OK")) / 2f + 1, INK);
            } else {
                UiRender.rect(m, edit.x(), edit.y(), edit.w(), edit.h(), 6, hov ? 0x26FFFFFF : 0x16FFFFFF);
                F_BTN.drawCenteredString(m, "Изм.", edit.x() + edit.w() / 2.0,
                        edit.y() + (edit.h() - F_BTN.getStringHeight("Изм.")) / 2f + 1, 0xDDFFFFFF);
            }

            // delete
            hov = UiRender.inside(mx, my, del.x(), del.y(), del.w(), del.h());
            UiRender.rect(m, del.x(), del.y(), del.w(), del.h(), 6, hov ? 0x2E6B3A45 : 0x1A6B3A45);
            F_BTN.drawCenteredString(m, "Удалить", del.x() + del.w() / 2.0,
                    del.y() + (del.h() - F_BTN.getStringHeight("Удалить")) / 2f + 1, 0xFFFFB4B4);

            rowHits.add(new RowHit(i, row, login, edit, del));
        }
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
            if (tgHit != null && UiRender.inside(mouseX, mouseY, tgHit.x(), tgHit.y(), tgHit.w(), tgHit.h())) {
                openUrl(URL_TELEGRAM);
                return true;
            }
            if (ghHit != null && UiRender.inside(mouseX, mouseY, ghHit.x(), ghHit.y(), ghHit.w(), ghHit.h())) {
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
        if (tgHit != null && UiRender.inside(mouseX, mouseY, tgHit.x(), tgHit.y(), tgHit.w(), tgHit.h())) {
            openUrl(URL_TELEGRAM);
            return true;
        }
        if (ghHit != null && UiRender.inside(mouseX, mouseY, ghHit.x(), ghHit.y(), ghHit.w(), ghHit.h())) {
            openUrl(URL_GITHUB);
            return true;
        }
        for (NavHit nh : navHits) {
            if (UiRender.inside(mouseX, mouseY, nh.box().x(), nh.box().y(), nh.box().w(), nh.box().h())) {
                runNav(nh.action());
                return true;
            }
        }
        return true;
    }

    private boolean clickModal(double mouseX, double mouseY) {
        if (panelH <= 0) {
            return true;
        }
        if (closeHit != null && UiRender.inside(mouseX, mouseY, closeHit.x(), closeHit.y(), closeHit.w(), closeHit.h())) {
            closeModal();
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, inputHit.x(), inputHit.y(), inputHit.w(), inputHit.h())) {
            focus = 1;
            editIndex = -1;
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, createHit.x(), createHit.y(), createHit.w(), createHit.h())) {
            submitNew();
            return true;
        }
        for (RowHit rh : rowHits) {
            if (UiRender.inside(mouseX, mouseY, rh.login().x(), rh.login().y(), rh.login().w(), rh.login().h())) {
                AccountManager.apply(AccountManager.names().get(rh.index()));
                closeModal();
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, rh.edit().x(), rh.edit().y(), rh.edit().w(), rh.edit().h())) {
                if (focus == 2 && editIndex == rh.index()) {
                    commitEdit();
                } else {
                    focus = 2;
                    editIndex = rh.index();
                    editText = AccountManager.names().get(rh.index());
                }
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, rh.del().x(), rh.del().y(), rh.del().w(), rh.del().h())) {
                AccountManager.delete(rh.index());
                if (editIndex == rh.index()) {
                    focus = 0;
                    editIndex = -1;
                } else if (editIndex > rh.index()) {
                    editIndex--;
                }
                return true;
            }
        }
        if (!UiRender.inside(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            closeModal();
        }
        return true;
    }

    private void runNav(int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (action) {
            case 0 -> mc.setScreen(new SelectWorldScreen(this));
            case 1 -> mc.setScreen(new MultiplayerScreen(this));
            case 2 -> mc.setScreen(new OptionsScreen(this, mc.options));
            case 3 -> {
                modal = true;
                focus = 1;
                input = "";
                editText = "";
                editIndex = -1;
                listScroll = 0;
            }
            case 4 -> mc.scheduleStop();
            default -> {
            }
        }
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
                closeModal();
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
            if (panelH <= 0) {
                return true;
            }
            List<String> names = AccountManager.names();
            float visible = modalListVisible();
            float total = names.size() * 36f - 6;
            float maxScroll = Math.max(0f, total - visible);
            listScroll = clamp(listScroll - (float) (verticalAmount * 36), 0f, maxScroll);
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
        }
        // SPLASH: the title screen never closes on ESC
    }

    // ----------------------------------------------------------- helpers

    private void startTransition() {
        if (state == State.SPLASH) {
            state = State.TRANSITION;
            animStart = System.currentTimeMillis();
        }
    }

    private void closeModal() {
        modal = false;
        focus = 0;
        editIndex = -1;
    }

    /** First row of the account list — header shrinks on short panels. */
    private float modalListTop() {
        return panelY + (panelH >= 300f ? 112f : 88f);
    }

    /** Visible list height, matching renderModal's bottom padding of 14. */
    private float modalListVisible() {
        return Math.max(0f, panelY + panelH - 14f - modalListTop());
    }

    private void submitNew() {
        if (AccountManager.create(input)) {
            input = "";
        }
    }

    private void commitEdit() {
        if (AccountManager.rename(editIndex, editText)) {
            focus = 0;
            editIndex = -1;
        }
    }

    private void openUrl(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Throwable ignored) {
        }
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

    private static float easeOut(float p) {
        float inv = 1f - p;
        return 1f - inv * inv * inv;
    }

    private static int mulAlpha(int argb, float factor) {
        return UiRender.mulAlpha(argb, factor);
    }
}
