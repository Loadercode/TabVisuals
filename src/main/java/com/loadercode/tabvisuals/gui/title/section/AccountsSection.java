// language: Java, file: AccountsSection.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title.section;

import com.loadercode.tabvisuals.gui.account.AccountManager;
import com.loadercode.tabvisuals.gui.account.AccountMeta;
import com.loadercode.tabvisuals.gui.account.AccountRenderer;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.title.MenuFont;
import com.loadercode.tabvisuals.gui.title.MenuFrame;
import com.loadercode.tabvisuals.gui.title.MenuKit;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.loadercode.tabvisuals.gui.title.MenuKit.*;

/**
 * "Аккаунты": offline profile manager. Left — the profile list (skin head,
 * nick, last use, favourite star, active tag); right — the selected
 * profile's card with a drag-rotatable 3D model, chips, details and
 * actions. "Добавить" / rename open a small dialog with a nick field.
 */
public final class AccountsSection implements MenuSection {

    private static final MenuFont F_NAME = new MenuFont(16, Fonts.Type.SEMI);
    private static final MenuFont F_SUB = new MenuFont(11.5f, Fonts.Type.REGULAR);
    private static final MenuFont F_TAG = new MenuFont(10, Fonts.Type.SEMI);
    private static final MenuFont F_BIG = new MenuFont(22, Fonts.Type.SEMI);
    private static final MenuFont F_KEY = new MenuFont(12, Fonts.Type.REGULAR);
    private static final MenuFont F_BTN = new MenuFont(13, Fonts.Type.SEMI);
    private static final MenuFont F_DLG_T = new MenuFont(20, Fonts.Type.SEMI);
    private static final MenuFont F_DLG = new MenuFont(12.5f, Fonts.Type.REGULAR);
    private static final MenuFont F_INPUT = new MenuFont(15, Fonts.Type.DEFAULT);
    private static final MenuFont F_EMPTY = new MenuFont(14, Fonts.Type.DEFAULT);

    private static final int MAX = AccountManager.MAX_ACCOUNTS;
    private static final float ROW_H = 40f, ROW_STEP = 44f, PANEL_W = 212f;

    private final AccountRenderer preview = new AccountRenderer();
    private final Anim[] rowAnim = new Anim[MAX];
    private final float[] rowHover = new float[MAX];
    private final boolean[] rowHit = new boolean[MAX];
    private final float[] rowY = new float[MAX];
    private float listX, listW;

    private String selected;
    private float scroll, scrollTarget, maxScroll;
    private float listTop, listBottom;
    private final Spring selBar = new Spring(22f);
    private long shownAt;

    // detail panel buttons
    private final Anim addBtn = new Anim(), useBtn = new Anim(), renBtn = new Anim(), copyBtn = new Anim(),
            favBtn = new Anim(), delBtn = new Anim();
    private float addX, addY, addW, addH = -1f;
    private final float[] btnX = new float[5], btnY = new float[5], btnW = new float[5];
    private float btnH = -1f;
    private float pvX, pvY, pvW, pvH = -1f;
    private long deleteArmedAt;
    private long copiedAt;
    private boolean hitsValid;

    // dialog
    private boolean dialog;
    private boolean dialogRename;
    private float dialogT;
    private String dlgInput = "";
    private float dlgError;
    private long dlgOpenedAt;
    private long layoutHintAt;
    private final Anim dlgOk = new Anim(), dlgCancel = new Anim();
    private float dX, dY, dW, dH, okX, cancelX, btnRowY;
    private boolean dlgHitsValid;

    public AccountsSection() {
        for (int i = 0; i < MAX; i++) {
            rowAnim[i] = new Anim();
        }
    }

    @Override
    public String title() {
        return "Аккаунты";
    }

    @Override
    public String subtitle() {
        String c = AccountManager.current();
        return c.isEmpty() ? "Менеджер профилей" : "Менеджер профилей · сейчас: " + c;
    }

    @Override
    public void onShow() {
        shownAt = System.currentTimeMillis();
        scroll = scrollTarget = 0f;
        if (selected == null || !contains(selected)) {
            selected = AccountManager.current();
        }
        selBar.reset();
        deleteArmedAt = 0L;
    }

    @Override
    public void onHide() {
        hitsValid = false;
        dialog = false;
        preview.endDrag();
    }

    @Override
    public boolean modalOpen() {
        return dialog || dialogT > 0.05f;
    }

    private static boolean contains(String nick) {
        for (String s : AccountManager.names()) {
            if (s.equalsIgnoreCase(nick)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- header

    @Override
    public void renderHeader(MenuFrame f, float rightX, float cy, float alpha) {
        MatrixStack m = f.m;
        String label = "Добавить";
        addW = F_BTN.width(label) + 34f;
        addH = 22f;
        addX = rightX - addW;
        addY = cy - addH / 2f;
        addBtn.update(f, addX, addY, addW, addH);
        pushScale(m, addX + addW / 2f, cy, addBtn.scale());
        glow(m, addX + addW / 2f, cy, addW * 0.8f, addH * 1.3f, alpha(ACCENT_DEEP, (0.18f + 0.22f * addBtn.hover) * alpha));
        rect(m, addX, addY, addW, addH, addH / 2f, alpha(mix(0xFF6E52D9, 0xFF7E63EA, addBtn.hover), alpha));
        outline(m, addX, addY, addW, addH, addH / 2f, alpha(0x33FFFFFF, alpha));
        icon(m, Icons.ADD, addX + 13f, cy, 12f, alpha(TEXT, alpha));
        F_BTN.draw(m, label, addX + 22f, cy, alpha(TEXT, alpha));
        m.pop();
    }

    // ------------------------------------------------------------- render

    @Override
    public void render(MenuFrame f, float x, float y, float w, float h, float alpha) {
        MatrixStack m = f.m;
        List<String> names = AccountManager.names();
        if (selected == null || !contains(selected)) {
            selected = names.isEmpty() ? null : AccountManager.current();
            if (selected != null && !contains(selected)) {
                selected = names.get(0);
            }
        }

        float panelW = Math.min(PANEL_W, w * 0.36f);
        listX = x;
        listW = w - panelW - 12f;
        listTop = y;
        listBottom = y + h;

        // ---- list
        int n = Math.min(names.size(), MAX);
        maxScroll = Math.max(0f, n * ROW_STEP - 4f - h);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 14f, f.dt);

        if (n == 0) {
            float e = enter(f.now, shownAt, 60L, 420L);
            float cy = y + h * 0.4f;
            icon(m, Icons.USER_OUTLINE, x + listW / 2f, cy - 14f, 30f, alpha(TEXT_3, alpha * e));
            F_EMPTY.drawCentered(m, "Профилей пока нет", x + listW / 2f, cy + 8f, alpha(TEXT_2, alpha * e));
            F_SUB.drawCentered(m, "Нажмите «Добавить», чтобы создать оффлайн-профиль", x + listW / 2f, cy + 22f,
                    alpha(TEXT_4, alpha * e));
        }

        int sx1 = (int) Math.floor(x - 4f), sy1 = (int) Math.floor(y - 2f);
        int sx2 = (int) Math.ceil(x + listW + 4f), sy2 = (int) Math.ceil(y + h);
        f.ctx.enableScissor(sx1, sy1, sx2, sy2);
        float selTargetY = -1f;
        String current = AccountManager.current();
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            float ry = y + i * ROW_STEP - scroll;
            rowHit[i] = false;
            if (ry + ROW_H < y - 2f || ry > y + h) {
                continue;
            }
            float e = enter(f.now, shownAt, 40L + Math.min(i, 10) * 40L, 420L);
            float a = alpha * e;
            float rx = x + (1f - e) * 10f;
            rowY[i] = ry;
            Anim st = rowAnim[i];
            boolean visibleWhole = ry >= y - 0.5f && ry + ROW_H <= y + h + 0.5f;
            st.update(f, rx, ry, listW, ROW_H);
            if (!visibleWhole) {
                st.hover = approach(st.hover, 0f, 16f, f.dt);
            }
            rowHit[i] = visibleWhole;
            boolean sel = name.equalsIgnoreCase(selected);
            boolean active = name.equalsIgnoreCase(current);
            if (sel) {
                selTargetY = ry + ROW_H / 2f;
            }
            rowHover[i] = st.hover;

            pushScale(m, rx + listW / 2f, ry + ROW_H / 2f, st.scale());
            int fill = sel ? 0x14FFFFFF : mix(0x00FFFFFF, 0x0CFFFFFF, st.hover);
            rect(m, rx, ry, listW, ROW_H, 9f, alpha(fill, a));
            if (sel) {
                outline(m, rx, ry, listW, ROW_H, 9f, alpha(0x12FFFFFF, a));
            }
            skinFace(m, AccountRenderer.skinFor(name), rx + 12f, ry + 7f, 26f, 5f, alpha(0xFFFFFFFF, a));
            outline(m, rx + 12f, ry + 7f, 26f, 26f, 5f, alpha(0x1AFFFFFF, a));
            F_NAME.draw(m, F_NAME.fit(name, listW - 170f), rx + 48f, ry + 14f, alpha(sel ? TEXT : mix(TEXT_2, TEXT, st.hover), a));
            F_SUB.draw(m, "Оффлайн · " + AccountMeta.ago(AccountMeta.lastUsed(name)), rx + 48f, ry + 27f,
                    alpha(TEXT_4, a));

            float tx = rx + listW - 14f;
            if (active) {
                String tag = "Активный";
                float tw = F_TAG.width(tag) + 12f;
                tx -= tw;
                rect(m, tx, ry + ROW_H / 2f - 7f, tw, 14f, 7f, alpha(0x228B5CF6, a));
                F_TAG.draw(m, tag, tx + 6f, ry + ROW_H / 2f, alpha(ACCENT, a));
                tx -= 8f;
            }
            if (AccountMeta.favorite(name)) {
                icon(m, Icons.STAR, tx - 6f, ry + ROW_H / 2f, 13f, alpha(GOLD, a));
            }
            m.pop();
        }
        f.ctx.disableScissor();

        // sliding selection bar, clipped to the list band
        if (selTargetY >= 0f) {
            float by = selBar.update(selTargetY, f.dt);
            if (by > y + 6f && by < y + h - 6f) {
                rect(m, x - 6f, by - 9f, 2.5f, 18f, 1.25f, alpha(ACCENT, alpha));
                glow(m, x - 5f, by, 6f, 14f, alpha(ACCENT_DEEP, 0.6f * alpha));
            }
        }

        // slim scrollbar
        if (maxScroll > 0f) {
            float trackH = h;
            float thumbH = Math.max(24f, trackH * (h / (n * ROW_STEP)));
            float ty = y + (trackH - thumbH) * (scroll / maxScroll);
            rect(m, x + listW + 3f, ty, 2.5f, thumbH, 1.25f, alpha(0x30FFFFFF, alpha));
        }

        // ---- detail panel
        float px = x + w - panelW;
        float e = enter(f.now, shownAt, 100L, 460L);
        renderPanel(f, px + (1f - e) * 12f, y, panelW, h, alpha * e);

        hitsValid = alpha > 0.9f && !dialog;
    }

    private void renderPanel(MenuFrame f, float x, float y, float w, float h, float a) {
        MatrixStack m = f.m;
        card(m, x, y, w, h, 12f, alpha(0x0BFFFFFF, a), alpha(LINE, a));
        btnH = -1f;
        pvH = -1f;
        if (selected == null) {
            F_SUB.drawCentered(m, "Выберите профиль", x + w / 2f, y + h / 2f, alpha(TEXT_4, a));
            return;
        }
        String nick = selected;
        boolean active = nick.equalsIgnoreCase(AccountManager.current());
        boolean fav = AccountMeta.favorite(nick);

        // 3D model on a soft pedestal
        pvX = x + 10f;
        pvY = y + 10f;
        pvW = w - 20f;
        pvH = Math.min(170f, h * 0.40f);
        if (a > 0.05f) {
            preview.setNick(nick);
            preview.render(f.ctx, m, pvX, pvY, pvW, pvH, f.dt);
            if (a < 0.999f) {
                // the model renders opaque; a veil in the panel colour fades it with the page
                rect(m, pvX - 1f, pvY - 1f, pvW + 2f, pvH + 2f, 10f, alpha(0xFF0D0B18, 1f - a));
            }
        }

        float cy = pvY + pvH + 16f;
        F_BIG.draw(m, F_BIG.fit(nick, w - 28f), x + 14f, cy, alpha(TEXT, a));

        // chips
        float chx = x + 14f, chy = cy + 16f;
        chx = chip(m, chx, chy, active ? "Активный" : "Неактивный", active ? ACCENT : TEXT_3, a);
        chx = chip(m, chx, chy, "Оффлайн", TEXT_3, a);
        if (fav) {
            chip(m, chx, chy, "Избранный", GOLD, a);
        }

        // key/value rows
        float ky = chy + 24f;
        long last = AccountMeta.lastUsed(nick);
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8)).toString();
        ky = kv(m, x, w, ky, "Тип", "Оффлайн", a);
        ky = kv(m, x, w, ky, "Последний вход", AccountMeta.ago(last), a);
        ky = kv(m, x, w, ky, "Состояние", active ? "Используется" : "Без авторизации", a);
        kv(m, x, w, ky, "UUID", uuid.substring(0, 13) + "…", a);

        // actions: primary "use" + four icon buttons
        float by = y + h - 36f;
        btnH = 26f;
        float bx = x + 12f;
        float primaryW = w - 24f - 4 * (26f + 6f);
        btnX[0] = bx;
        btnY[0] = by;
        btnW[0] = primaryW;
        useBtn.update(f, bx, by, primaryW, btnH);
        pushScale(m, bx + primaryW / 2f, by + btnH / 2f, useBtn.scale());
        if (active) {
            rect(m, bx, by, primaryW, btnH, 8f, alpha(0x10FFFFFF, a));
            icon(m, Icons.LOGIN, bx + primaryW / 2f - F_BTN.width("Используется") / 2f - 8f, by + btnH / 2f, 12f,
                    alpha(OK, a));
            F_BTN.drawCentered(m, "Используется", bx + primaryW / 2f + 6f, by + btnH / 2f, alpha(TEXT_2, a));
        } else {
            glow(m, bx + primaryW / 2f, by + btnH / 2f, primaryW * 0.6f, btnH, alpha(ACCENT_DEEP, 0.25f * useBtn.hover * a));
            rect(m, bx, by, primaryW, btnH, 8f, alpha(mix(0xFF6E52D9, 0xFF7E63EA, useBtn.hover), a));
            F_BTN.drawCentered(m, "Войти", bx + primaryW / 2f, by + btnH / 2f, alpha(TEXT, a));
        }
        m.pop();

        bx += primaryW + 6f;
        boolean copied = System.currentTimeMillis() - copiedAt < 1400L;
        boolean armed = System.currentTimeMillis() - deleteArmedAt < 3000L;
        iconBtn(f, renBtn, 1, bx, by, Icons.SLIDERS, TEXT_2, a);
        iconBtn(f, copyBtn, 2, bx + 32f, by, copied ? Icons.LOGIN : Icons.COPY, copied ? OK : TEXT_2, a);
        iconBtn(f, favBtn, 3, bx + 64f, by, Icons.STAR, fav ? GOLD : TEXT_2, a);
        iconBtn(f, delBtn, 4, bx + 96f, by, Icons.TRASH, armed ? DANGER : mix(TEXT_2, DANGER, delBtn.hover), a);
        if (armed) {
            F_SUB.drawCentered(m, "Нажмите ещё раз, чтобы удалить", x + w / 2f, by - 10f, alpha(DANGER, a));
        } else if (copied) {
            F_SUB.drawCentered(m, "Ник скопирован", x + w / 2f, by - 10f, alpha(OK, a));
        }
    }

    private void iconBtn(MenuFrame f, Anim st, int slot, float x, float y, Icons.Glyph g, int color, float a) {
        MatrixStack m = f.m;
        btnX[slot] = x;
        btnY[slot] = y;
        btnW[slot] = 26f;
        st.update(f, x, y, 26f, 26f);
        pushScale(m, x + 13f, y + 13f, st.scale());
        rect(m, x, y, 26f, 26f, 8f, alpha(mix(0x0EFFFFFF, 0x1EFFFFFF, st.hover), a));
        icon(m, g, x + 13f, y + 13f, 13f, alpha(mix(color, TEXT, st.hover * 0.3f), a));
        m.pop();
    }

    private static float chip(MatrixStack m, float x, float cy, String label, int color, float a) {
        float w = F_TAG.width(label) + 14f;
        rect(m, x, cy - 7f, w, 14f, 7f, alpha(mix(0x00FFFFFF, color, 0.14f), a));
        F_TAG.draw(m, label, x + 7f, cy, alpha(color, a));
        return x + w + 5f;
    }

    private static float kv(MatrixStack m, float x, float w, float y, String k, String v, float a) {
        F_KEY.draw(m, k, x + 14f, y, alpha(TEXT_4, a));
        F_KEY.drawRight(m, F_KEY.fit(v, w * 0.55f), x + w - 14f, y, alpha(TEXT_2, a));
        return y + 15f;
    }

    // ------------------------------------------------------------- dialog

    @Override
    public void renderOverlay(MenuFrame f, float alpha) {
        dialogT = approach(dialogT, dialog ? 1f : 0f, 14f, f.dt);
        if (dialogT <= 0.005f) {
            dlgHitsValid = false;
            return;
        }
        MatrixStack m = f.m;
        float t = easeOutCubic(dialogT);
        float a = alpha * t;
        rect(m, 0f, 0f, f.w, f.h, 0f, alpha(0x99050410, a));

        dW = 300f;
        dH = 150f;
        dX = (f.w - dW) / 2f;
        dY = (f.h - dH) / 2f + (1f - t) * 14f;
        pushScale(m, f.w / 2f, f.h / 2f, 0.95f + 0.05f * t);
        glow(m, f.w / 2f, dY + dH / 2f, dW * 0.8f, dH, alpha(ACCENT_DEEP, 0.18f * a));
        UiShadow.draw(m, dX, dY, dW, dH, a);
        rect(m, dX, dY, dW, dH, 14f, alpha(0xF5121022, a));
        outline(m, dX, dY, dW, dH, 14f, alpha(0x26FFFFFF, a));

        F_DLG_T.draw(m, dialogRename ? "Переименовать" : "Новый профиль", dX + 18f, dY + 22f, alpha(TEXT, a));
        // typing in a Cyrillic layout gets rejected silently otherwise — say why
        float hint = clamp01(1f - (f.now - layoutHintAt - 2200L) / 400f) * (layoutHintAt > 0 ? 1f : 0f);
        F_DLG.draw(m, "Оффлайн-ник: 3–16 символов, латиница, цифры и _", dX + 18f, dY + 39f,
                alpha(TEXT_3, a * (1f - hint)));
        F_DLG.draw(m, "Переключите раскладку на английскую (EN)", dX + 18f, dY + 39f, alpha(DANGER, a * hint));

        // input
        dlgError = Math.max(0f, dlgError - f.dt * 2.4f);
        float shake = dlgError > 0f ? (float) Math.sin(f.now * 0.05) * 5f * dlgError : 0f;
        float ix = dX + 18f + shake, iy = dY + 56f, iw = dW - 36f, ih = 30f;
        rect(m, ix, iy, iw, ih, 9f, alpha(0x24000000, a));
        int ring = mix(alpha(ACCENT, 0.8f), DANGER, dlgError);
        outline(m, ix, iy, iw, ih, 9f, alpha(ring, a));
        icon(m, Icons.USER, ix + 14f, iy + ih / 2f, 12f, alpha(TEXT_3, a));
        if (dlgInput.isEmpty()) {
            F_INPUT.draw(m, "Введите ник", ix + 28f, iy + ih / 2f, alpha(TEXT_4, a));
        } else {
            F_INPUT.draw(m, dlgInput, ix + 28f, iy + ih / 2f, alpha(TEXT, a));
        }
        // smooth caret blink (not a hard on/off)
        float blink = 0.5f + 0.5f * (float) Math.cos((f.now - dlgOpenedAt) * 0.006);
        float caretX = ix + 28f + F_INPUT.width(dlgInput) + 1f;
        rect(m, caretX, iy + 8f, 1.2f, ih - 16f, 0.6f, alpha(ACCENT, a * blink));
        F_TAG.drawRight(m, dlgInput.length() + "/16", ix + iw - 10f, iy + ih / 2f, alpha(TEXT_4, a));

        // buttons
        btnRowY = dY + dH - 40f;
        float bw = 100f;
        okX = dX + dW - 18f - bw;
        cancelX = okX - 8f - bw;
        dlgCancel.update(f, cancelX, btnRowY, bw, 26f);
        dlgOk.update(f, okX, btnRowY, bw, 26f);
        pushScale(m, cancelX + bw / 2f, btnRowY + 13f, dlgCancel.scale());
        rect(m, cancelX, btnRowY, bw, 26f, 8f, alpha(mix(0x10FFFFFF, 0x20FFFFFF, dlgCancel.hover), a));
        F_BTN.drawCentered(m, "Отмена", cancelX + bw / 2f, btnRowY + 13f, alpha(TEXT_2, a));
        m.pop();
        boolean valid = !AccountManager.clean(dlgInput).isEmpty();
        pushScale(m, okX + bw / 2f, btnRowY + 13f, dlgOk.scale());
        rect(m, okX, btnRowY, bw, 26f, 8f, alpha(mix(0xFF6E52D9, 0xFF7E63EA, dlgOk.hover), a * (valid ? 1f : 0.45f)));
        F_BTN.drawCentered(m, dialogRename ? "Сохранить" : "Создать", okX + bw / 2f, btnRowY + 13f,
                alpha(TEXT, a * (valid ? 1f : 0.6f)));
        m.pop();
        m.pop();
        dlgHitsValid = dialog && dialogT > 0.9f;
    }

    private void openDialog(boolean rename) {
        dialog = true;
        dialogRename = rename;
        dlgInput = rename && selected != null ? selected : "";
        dlgError = 0f;
        layoutHintAt = 0L;
        dlgOpenedAt = System.currentTimeMillis();
    }

    private void submitDialog() {
        if (dialogRename) {
            int idx = indexOf(selected);
            String old = selected;
            if (idx >= 0 && AccountManager.rename(idx, dlgInput)) {
                String now = AccountManager.names().get(idx);
                AccountMeta.rename(old, now);
                selected = now;
                dialog = false;
            } else {
                dlgError = 1f;
            }
        } else if (AccountManager.create(dlgInput)) {
            selected = AccountManager.clean(dlgInput);
            dialog = false;
            // scroll the new (last) row into view
            scrollTarget = Float.MAX_VALUE;
        } else {
            dlgError = 1f;
        }
    }

    private static int indexOf(String nick) {
        List<String> names = AccountManager.names();
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(nick)) {
                return i;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------- input

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return h > 0f && mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!dialog && dialogT > 0.05f) {
            return true;
        }
        if (dialog) {
            if (!dlgHitsValid || button != 0) {
                return true;
            }
            if (in(mx, my, okX, btnRowY, 100f, 26f)) {
                MenuKit.pressed = dlgOk;
                submitDialog();
            } else if (in(mx, my, cancelX, btnRowY, 100f, 26f)) {
                MenuKit.pressed = dlgCancel;
                dialog = false;
            } else if (!in(mx, my, dX, dY, dW, dH)) {
                dialog = false;
            }
            return true;
        }
        if (button != 0 || !hitsValid) {
            return false;
        }
        if (in(mx, my, addX, addY, addW, addH)) {
            MenuKit.pressed = addBtn;
            openDialog(false);
            return true;
        }
        if (in(mx, my, pvX, pvY, pvW, pvH)) {
            preview.beginDrag(mx, my);
            return true;
        }
        if (btnH > 0f && selected != null) {
            for (int s = 0; s < 5; s++) {
                if (in(mx, my, btnX[s], btnY[s], btnW[s], btnH)) {
                    action(s);
                    return true;
                }
            }
        }
        List<String> names = AccountManager.names();
        int n = Math.min(names.size(), MAX);
        for (int i = 0; i < n; i++) {
            if (rowHit[i] && in(mx, my, listX, rowY[i], listW, ROW_H)) {
                MenuKit.pressed = rowAnim[i];
                String name = names.get(i);
                long now = System.currentTimeMillis();
                // second click on the selected row = log in (double-click feel)
                if (name.equalsIgnoreCase(selected) && now - lastRowClick < 400L) {
                    login(name);
                }
                lastRowClick = now;
                selected = name;
                deleteArmedAt = 0L;
                return true;
            }
        }
        return false;
    }

    private long lastRowClick;

    private void action(int slot) {
        String nick = selected;
        switch (slot) {
            case 0 -> {
                MenuKit.pressed = useBtn;
                if (!nick.equalsIgnoreCase(AccountManager.current())) {
                    login(nick);
                }
            }
            case 1 -> {
                MenuKit.pressed = renBtn;
                openDialog(true);
            }
            case 2 -> {
                MenuKit.pressed = copyBtn;
                MinecraftClient.getInstance().keyboard.setClipboard(nick);
                copiedAt = System.currentTimeMillis();
            }
            case 3 -> {
                MenuKit.pressed = favBtn;
                AccountMeta.toggleFavorite(nick);
            }
            case 4 -> {
                MenuKit.pressed = delBtn;
                long now = System.currentTimeMillis();
                if (now - deleteArmedAt < 3000L) {
                    int idx = indexOf(nick);
                    if (idx >= 0) {
                        AccountManager.delete(idx);
                        AccountMeta.forget(nick);
                    }
                    selected = null;
                    deleteArmedAt = 0L;
                } else {
                    deleteArmedAt = now;
                }
            }
            default -> {
            }
        }
    }

    private static void login(String nick) {
        AccountManager.apply(nick);
        AccountMeta.touch(nick);
    }

    @Override
    public boolean mouseDragged(float mx, float my, int button) {
        if (preview.isDragging()) {
            preview.drag(mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(float mx, float my, int button) {
        if (preview.isDragging()) {
            preview.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(float mx, float my, float amount) {
        if (dialog) {
            return true;
        }
        if (in(mx, my, listX, listTop, listW, listBottom - listTop)) {
            scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget - amount * ROW_STEP));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!dialog) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> dialog = false;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> submitDialog();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!dlgInput.isEmpty()) {
                    dlgInput = (mods & GLFW.GLFW_MOD_CONTROL) != 0 ? "" : dlgInput.substring(0, dlgInput.length() - 1);
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                    String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clip != null) {
                        for (int i = 0; i < clip.length(); i++) {
                            append(clip.charAt(i));
                        }
                    }
                }
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!dialog) {
            return false;
        }
        append(c);
        return true;
    }

    private void append(char c) {
        boolean ok = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
        if (ok && dlgInput.length() < 16) {
            dlgInput += c;
        } else if (!ok && c > ' ') {
            dlgError = Math.max(dlgError, 0.5f);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) {
                layoutHintAt = System.currentTimeMillis();
            }
        }
    }

    /** Drop shadow under the dialog — layered rounded rects, drawn before the panel. */
    private static final class UiShadow {
        static void draw(MatrixStack m, float x, float y, float w, float h, float a) {
            com.loadercode.tabvisuals.gui.UiRender.shadow(m, x, y, w, h, 14f, 0.8f * a);
        }
    }
}
