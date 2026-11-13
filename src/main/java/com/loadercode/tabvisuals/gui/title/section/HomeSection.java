// language: Java, file: HomeSection.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title.section;

import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.account.AccountManager;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.title.MenuFont;
import com.loadercode.tabvisuals.gui.title.MenuFrame;
import com.loadercode.tabvisuals.gui.title.MenuKit;
import com.loadercode.tabvisuals.gui.title.NowPlaying;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

import static com.loadercode.tabvisuals.gui.title.MenuKit.*;

/**
 * Home page: the big "now playing" player, a lyrics panel with the scrolling
 * line pair, and one-click "quick join" rows for the first saved servers.
 */
public final class HomeSection implements MenuSection {

    private static final MenuFont F_HEAD = new MenuFont(12, Fonts.Type.SEMI);
    private static final MenuFont F_ROW = new MenuFont(15, Fonts.Type.SEMI);
    private static final MenuFont F_ROW_SUB = new MenuFont(11.5f, Fonts.Type.REGULAR);
    private static final MenuFont F_LETTER = new MenuFont(16, Fonts.Type.BOLD);
    private static final MenuFont F_HINT = new MenuFont(13, Fonts.Type.DEFAULT);
    private static final int MAX_SERVERS = 4;

    private final Screen parent;
    private final NowPlaying player;
    private final List<ServerInfo> servers = new ArrayList<>();
    private final Anim[] rowAnim = new Anim[MAX_SERVERS + 1];
    private final float[] rowY = new float[MAX_SERVERS + 1];
    private float rowX, rowW, rowH;
    private int rowCount;
    private boolean hitsValid;
    private long shownAt;
    private String greeting = "";

    public HomeSection(Screen parent, NowPlaying player) {
        this.parent = parent;
        this.player = player;
        for (int i = 0; i < rowAnim.length; i++) {
            rowAnim[i] = new Anim();
        }
    }

    @Override
    public String title() {
        return greeting;
    }

    @Override
    public String subtitle() {
        return player.shown > 0.5f ? "Сейчас играет · " + player.title() : "Главная";
    }

    @Override
    public void onShow() {
        shownAt = System.currentTimeMillis();
        String nick = AccountManager.current();
        greeting = nick.isEmpty() ? "Добро пожаловать" : "Привет, " + nick;
        servers.clear();
        try {
            ServerList list = new ServerList(MinecraftClient.getInstance());
            list.loadFile();
            for (int i = 0; i < list.size() && servers.size() < MAX_SERVERS; i++) {
                ServerInfo s = list.get(i);
                if (s != null && s.address != null && !s.address.isBlank()) {
                    servers.add(s);
                }
            }
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.debug("server list unavailable", t);
        }
    }

    @Override
    public void onHide() {
        hitsValid = false;
        player.clearHits();
    }

    @Override
    public void render(MenuFrame f, float x, float y, float w, float h, float alpha) {
        MatrixStack m = f.m;
        float cardH = Math.min(150f, h * 0.42f);
        float e0 = enter(f.now, shownAt, 0L, 460L);
        player.drawCard(f, x, y + (1f - e0) * 10f, w, cardH, alpha * e0);

        float top = y + cardH + 12f;
        float bottomH = h - cardH - 12f;
        float gap = 12f;
        float lw = (w - gap) * 0.58f;
        float rw = w - gap - lw;

        // lyrics panel
        float e1 = enter(f.now, shownAt, 80L, 460L);
        float la = alpha * e1;
        float ly = top + (1f - e1) * 10f;
        card(m, x, ly, lw, bottomH, 12f, alpha(CARD, la), alpha(LINE, la));
        F_HEAD.draw(m, "ТЕКСТ ПЕСНИ", x + 14f, ly + 16f, alpha(TEXT_3, la));
        if (player.shown > 0.01f) {
            player.drawLyrics(f, x + lw / 2f, ly + bottomH / 2f, lw - 28f, la);
        }
        if (player.shown < 0.99f) {
            F_HINT.drawCentered(m, "Текст появится, когда заиграет трек", x + lw / 2f, ly + bottomH / 2f,
                    alpha(TEXT_4, la * (1f - player.shown)));
        }

        // quick join
        float e2 = enter(f.now, shownAt, 160L, 460L);
        float qa = alpha * e2;
        float qx = x + lw + gap;
        float qy = top + (1f - e2) * 10f;
        card(m, qx, qy, rw, bottomH, 12f, alpha(CARD, qa), alpha(LINE, qa));
        F_HEAD.draw(m, "БЫСТРЫЙ ВХОД", qx + 14f, qy + 16f, alpha(TEXT_3, qa));

        rowX = qx + 8f;
        rowW = rw - 16f;
        rowH = 34f;
        float ry = qy + 30f;
        int n = servers.size();
        rowCount = n + 1;
        for (int i = 0; i <= n; i++) {
            if (ry + rowH > qy + bottomH - 6f) {
                rowCount = i;
                break;
            }
            float re = enter(f.now, shownAt, 220L + i * 50L, 420L);
            float ra = qa * re;
            float rx = rowX + (1f - re) * 8f;
            rowY[i] = ry;
            Anim st = rowAnim[i];
            st.update(f, rx, ry, rowW, rowH);
            pushScale(m, rx + rowW / 2f, ry + rowH / 2f, st.scale());
            rect(m, rx, ry, rowW, rowH, 8f, alpha(0x14FFFFFF, ra * st.hover));
            if (i < n) {
                ServerInfo s = servers.get(i);
                int hue = tileColor(s.address);
                rect(m, rx + 6f, ry + 5f, 24f, 24f, 6f, alpha(hue, ra));
                String letter = s.name == null || s.name.isEmpty() ? "?"
                        : s.name.substring(0, 1).toUpperCase();
                F_LETTER.drawCentered(m, letter, rx + 18f, ry + 17f, alpha(TEXT, ra));
                F_ROW.draw(m, F_ROW.fit(s.name, rowW - 60f), rx + 38f, ry + 12f, alpha(TEXT, ra));
                F_ROW_SUB.draw(m, F_ROW_SUB.fit(s.address, rowW - 60f), rx + 38f, ry + 24f, alpha(TEXT_3, ra));
                icon(m, Icons.LOGIN, rx + rowW - 14f - (1f - st.hover) * 3f, ry + rowH / 2f, 14f,
                        alpha(mix(TEXT_4, ACCENT, st.hover), ra));
            } else {
                // last row: open the full server list
                outline(m, rx + 6f, ry + 5f, 24f, 24f, 6f, alpha(LINE_HOVER, ra));
                icon(m, Icons.ADD, rx + 18f, ry + 17f, 13f, alpha(TEXT_2, ra));
                F_ROW.draw(m, n == 0 ? "Добавить сервер" : "Все серверы", rx + 38f, ry + 12f, alpha(TEXT_2, ra));
                F_ROW_SUB.draw(m, "Открыть мультиплеер", rx + 38f, ry + 24f, alpha(TEXT_3, ra));
            }
            m.pop();
            ry += rowH + 4f;
        }
        hitsValid = alpha > 0.9f;
    }

    /** Stable pleasant tile colour per server address. */
    private static int tileColor(String key) {
        int[] palette = {0xFF3E8E85, 0xFF6D4AE0, 0xFFB4507A, 0xFF3B6FD4, 0xFFC0812F, 0xFF4F9A54};
        return palette[Math.floorMod(key == null ? 0 : key.hashCode(), palette.length)];
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0 || !hitsValid) {
            return false;
        }
        if (player.click(mx, my)) {
            return true;
        }
        for (int i = 0; i < rowCount; i++) {
            if (mx >= rowX && mx <= rowX + rowW && my >= rowY[i] && my <= rowY[i] + rowH) {
                MenuKit.pressed = rowAnim[i];
                MinecraftClient mc = MinecraftClient.getInstance();
                if (i < servers.size()) {
                    ServerInfo s = servers.get(i);
                    ConnectScreen.connect(new MultiplayerScreen(parent), mc, ServerAddress.parse(s.address), s,
                            false, null);
                } else {
                    mc.setScreen(new MultiplayerScreen(parent));
                }
                return true;
            }
        }
        return false;
    }
}
