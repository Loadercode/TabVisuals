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
 * TabVisuals title flow: splash (gradient, logo, lyrics, pulse hint,
 * profile plate, socials) → zoom-in transition → main menu with nav,
 * plus the alt-manager modal opened from "Аккаунт".
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
    private static final FontRenderer F_PLATE = Fonts.getSize(12, Fonts.Type.SEMI);
    private static final FontRenderer F_NAV = Fonts.getSize(15, Fonts.Type.SEMI);
    private static final FontRenderer F_TRACK = Fonts.getSize(21, Fonts.Type.SEMI);
    private static final FontRenderer F_ARTIST = Fonts.getSize(12, Fonts.Type.REGULAR);
    private static final FontRenderer F_MENU_LINE = Fonts.getSize(16, Fonts.Type.SEMI);
    private static final FontRenderer F_MENU_NEXT = Fonts.getSize(13, Fonts.Type.REGULAR);
    private static final FontRenderer F_MODAL_T = Fonts.getSize(17, Fonts.Type.SEMI);
    private static final FontRenderer F_INPUT = Fonts.getSize(13, Fonts.Type.REGULAR);
    private static final FontRenderer F_ROW = Fonts.getSize(13, Fonts.Type.SEMI);
    private static final FontRenderer F_BTN = Fonts.getSize(11, Fonts.Type.SEMI);

    // --- timings ---
    private static final long TRANS_MS = 450;
    private static final long MENU_IN_MS = 280;

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

        renderChrome(m, mx, my, alpha);

        // center logo + slogan
        F_LOGO.drawCenteredString(m, "TabVisuals", width / 2.0, height * 0.30, mulAlpha(WHITE, alpha));
        F_SLOGAN.drawCenteredString(m, spaced("PRIVATE CLIENT"), width / 2.0, height * 0.30 + 58,
                mulAlpha(0xB3FFFFFF, alpha));

        // lyrics
        float lineY = height * 0.55f;
        F_LYRIC_BIG.drawCenteredString(m, MusicManager.currentLine(), width / 2.0, lineY,
                mulAlpha(WHITE, alpha));
        F_LYRIC_SMALL.drawCenteredString(m, MusicManager.nextLine(), width / 2.0, lineY + 30,
                mulAlpha(0x99FFFFFF, alpha));

        // pulsing hint
        float pulse = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(now / 360.0));
        F_HINT.drawCenteredString(m, spaced("НАЖМИТЕ ЛЮБУЮ КЛАВИШУ"), width / 2.0, height * 0.82,
                mulAlpha(WHITE, alpha * pulse));

        m.pop();
    }

    private void renderMenu(MatrixStack m, float mx, float my, long now) {
        float p = clamp01((now - menuStart) / (float) MENU_IN_MS);
        float e = easeOut(p);

        // dark translucent overlay (album-art backdrop stand-in)
        UiRender.rect(m, 0, 0, width, height, 0, mulAlpha(0x4D000000, e));

        float cx = width / 2f, cy = height / 2f;
        float s = 1.04f - 0.04f * e;
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);

        renderChrome(m, mx, my, e);
        renderNav(m, mx, my, e);
        renderMenuLyrics(m, e);

        m.pop();
    }

    /** Logo mark, profile plate, socials, copyright — shared by splash and menu. */
    private void renderChrome(MatrixStack m, float mx, float my, float alpha) {
        // top-left mark
        float lx = 24, ly = 18, tile = 26;
        UiRender.rect(m, lx, ly, tile, tile, 7, mulAlpha(0x1AFFFFFF, alpha));
        UiRender.outline(m, lx, ly, tile, tile, 7, mulAlpha(0x30FFFFFF, alpha), 1f);
        F_MARK.drawCenteredString(m, "T", lx + tile / 2.0, ly + (tile - F_MARK.getStringHeight("T")) / 2f + 1,
                mulAlpha(WHITE, alpha));
        String mark = spaced("TABVISUALS");
        F_HINT.drawString(m, mark, lx + tile + 10,
                ly + (tile - F_HINT.getStringHeight(mark)) / 2f + 1, mulAlpha(WHITE, alpha));

        renderPlate(m, alpha);
        renderSocials(m, mx, my, alpha);

        // copyright
        F_COPY.drawCenteredString(m, "© 2026 TabVisuals", width / 2.0, height - 26,
                mulAlpha(0x70FFFFFF, alpha));
    }

    /** Top-right profile plate: avatar, nickname, version, status. */
    private void renderPlate(MatrixStack m, float alpha) {
        float pw = 216, ph = 56;
        float px = width - 24 - pw, py = 18;
        UiRender.rect(m, px, py, pw, ph, 10, mulAlpha(0xE012121A, alpha));
        UiRender.outline(m, px, py, pw, ph, 10, mulAlpha(0x1AFFFFFF, alpha), 1f);

        // avatar
        float av = 34, ax = px + 10, ay = py + (ph - av) / 2f;
        UiRender.rect(m, ax, ay, av, av, av / 2f, mulAlpha(0xFF7C5CE0, alpha));
        String nick = AccountManager.current();
        String initial = nick.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(nick.charAt(0)));
        F_NAV.drawCenteredString(m, initial, ax + av / 2.0,
                ay + (av - F_NAV.getStringHeight(initial)) / 2f + 1, mulAlpha(WHITE, alpha));

        // nickname, right-aligned
        float right = px + pw - 12;
        rightText(F_PLATE, m, nick, right, py + 13, mulAlpha(WHITE, alpha));

        // status row: version · dot · Protected, right-aligned as one line
        String ver = "v1.0.0";
        String prot = "Protected";
        float wVer = F_TINY.getStringWidth(ver);
        float wProt = F_TINY.getStringWidth(prot);
        float total = wVer + 6 + 6 + 5 + wProt;
        float sx = right - total;
        F_TINY.drawString(m, ver, sx, py + 33, mulAlpha(0x8CFFFFFF, alpha));
        UiRender.rect(m, sx + wVer + 6, py + 35, 6, 6, 3, mulAlpha(0xFF58D6A0, alpha));
        F_TINY.drawString(m, prot, sx + wVer + 6 + 6 + 5, py + 33, mulAlpha(0xB8FFFFFF, alpha));
    }

    /** Bottom-right Telegram / GitHub pills (clickable). */
    private void renderSocials(MatrixStack m, float mx, float my, float alpha) {
        String[] labels = {"Telegram", "GitHub"};
        String[] urls = {URL_TELEGRAM, URL_GITHUB};
        float h = 26, gy = height - 42;
        R[] hits = new R[2];
        float cursorX = width - 24;
        for (int i = 1; i >= 0; i--) {
            String label = labels[i];
            float textW = F_BTN.getStringWidth(label);
            float w = 8 + 14 + 7 + textW + 11;
            cursorX -= w;
            if (i == 1) {
                cursorX -= 8; // gap between pills
            }
            float x = cursorX;
            boolean hover = UiRender.inside(mx, my, x, gy, w, h);
            UiRender.rect(m, x, gy, w, h, h / 2f,
                    mulAlpha(hover ? 0x2AFFFFFF : 0x18FFFFFF, alpha));
            UiRender.outline(m, x, gy, w, h, h / 2f, mulAlpha(0x24FFFFFF, alpha), 1f);
            // letter badge
            UiRender.rect(m, x + 8, gy + 6, 14, 14, 4, mulAlpha(ACCENT, alpha));
            F_BTN.drawCenteredString(m, label.substring(0, 1), x + 8 + 7,
                    gy + 6 + (14 - F_BTN.getStringHeight("T")) / 2f + 1, mulAlpha(INK, alpha));
            F_BTN.drawString(m, label, x + 8 + 14 + 7,
                    gy + (h - F_BTN.getStringHeight(label)) / 2f + 1, mulAlpha(WHITE, alpha));
            hits[i] = new R(x, gy, w, h);
        }
        tgHit = hits[0];
        ghHit = hits[1];
    }

    /** Left navigation column: singleplayer, multiplayer, settings, account, quit. */
    private void renderNav(MatrixStack m, float mx, float my, float alpha) {
        String[] labels = {"Одиночная игра", "Сетевая игра", "Настройки", "Аккаунт", "Выход"};
        float navX = Math.max(36f, width * 0.07f);
        float itemW = 235, itemH = 38, gap = 6;
        float top = height * 0.30f;
        navHits.clear();
        for (int i = 0; i < labels.length; i++) {
            float y = top + i * (itemH + gap);
            R box = new R(navX, y, itemW, itemH);
            boolean hover = UiRender.inside(mx, my, box.x(), box.y(), box.w(), box.h());
            UiRender.rect(m, box.x(), box.y(), box.w(), box.h(), 9,
                    mulAlpha(hover ? 0x20FFFFFF : 0x10FFFFFF, alpha));
            UiRender.outline(m, box.x(), box.y(), box.w(), box.h(), 9,
                    mulAlpha(hover ? 0x30FFFFFF : 0x16FFFFFF, alpha), 1f);

            // icon chip + glyph
            float chip = 26, chx = box.x() + 8, chy = box.y() + (itemH - chip) / 2f;
            UiRender.rect(m, chx, chy, chip, chip, 8, mulAlpha(0x14FFFFFF, alpha));
            int glyphCol = mulAlpha(WHITE, alpha);
            drawNavIcon(m, i, chx + chip / 2f, chy + chip / 2f, glyphCol);

            String label = labels[i];
            F_NAV.drawString(m, label, box.x() + 48,
                    box.y() + (itemH - F_NAV.getStringHeight(label)) / 2f + 1,
                    mulAlpha(hover ? WHITE : 0xE6FFFFFF, alpha));
            navHits.add(new NavHit(i, box));
        }
    }

    private void drawNavIcon(MatrixStack m, int idx, float cx, float cy, int col) {
        switch (idx) {
            case 0 -> { // globe
                UiRender.outline(m, cx - 7, cy - 7, 14, 14, 7, col, 1.3f);
                UiRender.rect(m, cx - 0.7f, cy - 7, 1.4f, 14, 0.7f, col);
                UiRender.rect(m, cx - 7, cy - 0.7f, 14, 1.4f, 0.7f, col);
            }
            case 1 -> { // bars
                UiRender.rect(m, cx - 7, cy - 4, 2.5f, 8, 1f, col);
                UiRender.rect(m, cx - 1.5f, cy - 7, 2.5f, 14, 1f, col);
                UiRender.rect(m, cx + 4, cy - 5.5f, 2.5f, 11, 1f, col);
            }
            case 2 -> { // sliders
                UiRender.rect(m, cx - 7, cy - 5.7f, 14, 1.4f, 0.7f, col);
                UiRender.rect(m, cx - 7, cy - 0.7f, 14, 1.4f, 0.7f, col);
                UiRender.rect(m, cx - 7, cy + 4.3f, 14, 1.4f, 0.7f, col);
                UiRender.rect(m, cx - 4, cy - 6.8f, 3.6f, 3.6f, 1.8f, col);
                UiRender.rect(m, cx + 1.5f, cy - 1.8f, 3.6f, 3.6f, 1.8f, col);
                UiRender.rect(m, cx - 1, cy + 3.2f, 3.6f, 3.6f, 1.8f, col);
            }
            case 3 -> { // person
                UiRender.rect(m, cx - 3, cy - 7, 6, 6, 3, col);
                UiRender.rect(m, cx - 6, cy + 1, 12, 6, 3, col);
            }
            default -> { // exit arrow
                F_NAV.drawCenteredString(m, "→", cx, cy - F_NAV.getStringHeight("→") / 2f, col);
            }
        }
    }

    /** Right block: track + synchronized lyrics, right-aligned. */
    private void renderMenuLyrics(MatrixStack m, float alpha) {
        float rightX = width - 60;
        float limit = Math.max(340f, Math.max(36f, width * 0.07f) + 235 + 24);
        float y = height * 0.36f;

        rightTextClamped(F_TRACK, m, MusicManager.trackTitle(), rightX, limit, y, mulAlpha(WHITE, alpha));
        y += 28;
        rightTextClamped(F_ARTIST, m, MusicManager.artist(), rightX, limit, y, mulAlpha(0x99FFFFFF, alpha));
        y += 32;
        rightTextClamped(F_MENU_LINE, m, MusicManager.currentLine(), rightX, limit, y, mulAlpha(WHITE, alpha));
        y += 26;
        rightTextClamped(F_MENU_NEXT, m, MusicManager.nextLine(), rightX, limit, y, mulAlpha(0x8CFFFFFF, alpha));
    }

    // -------------------------------------------------------------- modal

    private void renderModal(MatrixStack m, float mx, float my, long now) {
        UiRender.rect(m, 0, 0, width, height, 0, 0xB3000000);

        panelW = 500;
        panelH = 380;
        panelX = (width - panelW) / 2f;
        panelY = (height - panelH) / 2f;
        boolean blink = (now / 500) % 2 == 0;

        UiRender.rect(m, panelX, panelY, panelW, panelH, 14, 0xF515151D);
        UiRender.outline(m, panelX, panelY, panelW, panelH, 14, 0x1FFFFFFF, 1f);

        F_MODAL_T.drawString(m, "Аккаунты", panelX + 20, panelY + 18, WHITE);
        F_TINY.drawString(m, "Оффлайн-профили", panelX + 20, panelY + 43, 0x8CFFFFFF);

        // close button
        closeHit = new R(panelX + panelW - 38, panelY + 14, 24, 24);
        if (UiRender.inside(mx, my, closeHit.x(), closeHit.y(), closeHit.w(), closeHit.h())) {
            UiRender.rect(m, closeHit.x(), closeHit.y(), closeHit.w(), closeHit.h(), 6, 0x1AFFFFFF);
        }
        F_MODAL_T.drawCenteredString(m, "×", closeHit.x() + 12,
                closeHit.y() + (24 - F_MODAL_T.getStringHeight("×")) / 2f + 1, 0xCCFFFFFF);

        // new-nick input
        inputHit = new R(panelX + 20, panelY + 64, 330, 34);
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
        createHit = new R(panelX + 358, panelY + 64, 100, 34);
        boolean createHover = UiRender.inside(mx, my, createHit.x(), createHit.y(), createHit.w(), createHit.h());
        UiRender.rect(m, createHit.x(), createHit.y(), createHit.w(), createHit.h(), 8,
                createHover ? ACCENT_HOVER : ACCENT);
        F_BTN.drawCenteredString(m, "Создать", createHit.x() + createHit.w() / 2.0,
                createHit.y() + (createHit.h() - F_BTN.getStringHeight("Создать")) / 2f + 1, INK);

        // account list
        List<String> names = AccountManager.names();
        float listY = panelY + 112;
        float visible = panelH - 112 - 18;
        float rowH = 38, step = 44;
        float total = names.size() * step - 6;
        float maxScroll = Math.max(0f, total - visible);
        listScroll = clamp(listScroll, 0f, maxScroll);
        rowHits.clear();

        if (names.isEmpty()) {
            F_INPUT.drawCenteredString(m, "Пока пусто — создайте первый профиль",
                    panelX + panelW / 2.0, listY + 24, 0x8CFFFFFF);
            return;
        }

        for (int i = 0; i < names.size(); i++) {
            float ry = listY + i * step - listScroll;
            if (ry > listY + visible || ry + rowH < listY) {
                continue;
            }
            String name = names.get(i);
            R row = new R(panelX + 20, ry, panelW - 40, rowH);
            boolean rowHover = UiRender.inside(mx, my, row.x(), row.y(), row.w(), row.h());
            UiRender.rect(m, row.x(), row.y(), row.w(), row.h(), 8,
                    rowHover ? 0x14FFFFFF : 0x0AFFFFFF);

            boolean editing = focus == 2 && editIndex == i;
            if (editing) {
                UiRender.rect(m, row.x() + 12, ry + 4, 240, 30, 6, 0x16FFFFFF);
                UiRender.outline(m, row.x() + 12, ry + 4, 240, 30, 6, ACCENT, 1f);
                String shownEdit = editText + (blink ? "_" : "");
                F_ROW.drawString(m, shownEdit, row.x() + 22,
                        ry + 4 + (30 - F_ROW.getStringHeight(shownEdit)) / 2f + 1, WHITE);
            } else {
                F_ROW.drawString(m, name, row.x() + 14,
                        ry + (rowH - F_ROW.getStringHeight(name)) / 2f + 1, 0xE6FFFFFF);
            }

            R del = new R(row.x() + row.w() - 12 - 58, ry + 6, 58, 26);
            R edit = new R(del.x() - 6 - 52, ry + 6, 52, 26);
            R login = new R(edit.x() - 6 - 56, ry + 6, 56, 26);

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

        // MENU
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
            float visible = panelH - 112 - 18;
            float total = names.size() * 44f - 6;
            float maxScroll = Math.max(0f, total - visible);
            listScroll = clamp(listScroll - (float) (verticalAmount * 40), 0f, maxScroll);
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
