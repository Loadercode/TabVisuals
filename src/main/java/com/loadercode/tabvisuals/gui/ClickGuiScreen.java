// language: Java, file: ClickGuiScreen.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
import com.loadercode.tabvisuals.gui.setting.ModeSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main click GUI: category tabs, searchable module cards (toggle / favorite /
 * slider / checkbox / dropdown), a separate Configs tab and a theme picker
 * behind the gear button. Built entirely on the ported render stack.
 */
public final class ClickGuiScreen extends Screen {

    private static final FontRenderer F_TITLE = Fonts.getSize(18, Fonts.Type.BOLD);
    private static final FontRenderer F_CARD = Fonts.getSize(14, Fonts.Type.SEMI);
    private static final FontRenderer F_TAB = Fonts.getSize(13, Fonts.Type.SEMI);
    private static final FontRenderer F_BODY = Fonts.getSize(12, Fonts.Type.REGULAR);
    private static final FontRenderer F_SMALL = Fonts.getSize(11, Fonts.Type.REGULAR);
    private static final FontRenderer F_SMALL_SEMI = Fonts.getSize(11, Fonts.Type.SEMI);

    private static final float ROW_H = 22f;
    private static final float DROP_ROW_H = 18f;

    public ClickGuiScreen() {
        super(Text.literal("TabVisuals"));
    }

    // --- view state ---
    private Category activeCategory = Category.COMBAT;
    private boolean settingsView = false;
    private final Map<Category, Float> scrollByCat = new EnumMap<>(Category.class);
    private String search = "";
    private boolean searchFocused = false;

    // --- layout cache (from the last render pass) ---
    private final List<TabRect> tabs = new ArrayList<>();
    private final List<CardBounds> layout = new ArrayList<>();
    private final List<ThemeRow> themeRows = new ArrayList<>();
    private final List<CfgRow> cfgRows = new ArrayList<>();
    private final ScissorAssist scissor = new ScissorAssist();
    private float gridTop;
    private float viewportH;
    private float contentHeight;
    private float searchX, searchY, searchW, searchH;
    private float gearX, gearY, gearSize = 28;

    // --- configs tab state ---
    private String configName = "";
    private boolean configNameFocused = false;
    private float cfgFieldX, cfgFieldY, cfgFieldW = 220, cfgFieldH = 20;
    private float cfgSaveX, cfgSaveY, cfgSaveW = 70, cfgSaveH = 20;

    // --- interaction state ---
    private DoubleSetting dragging;
    private float dragTrackX, dragTrackW;
    private ModeSetting dropdown;
    private float dropX, dropY, dropW;

    private record TabRect(Category category, float x, float y, float w, float h) {
    }

    private record SettingBounds(Setting setting, float x, float y, float w, float h) {
    }

    private static final class CardBounds {
        final ModuleStub module;
        final float x, yRel, w, h;
        final List<SettingBounds> settings = new ArrayList<>();

        CardBounds(ModuleStub module, float x, float yRel, float w, float h) {
            this.module = module;
            this.x = x;
            this.yRel = yRel;
            this.w = w;
            this.h = h;
        }
    }

    private record ThemeRow(Theme theme, float x, float y, float w, float h) {
    }

    private record CfgRow(String name, float x, float y, float w, float h,
                          float loadX, float delX, float btnY, float btnW, float btnH) {
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack m = context.getMatrices();
        Theme t = Theme.get();
        tabs.clear();
        cfgRows.clear();
        themeRows.clear();

        UiRender.rect(m, 0, 0, width, height, 0, t.background());

        float pw = Math.min(width - 40f, 960f);
        float ph = Math.min(height - 40f, 600f);
        float px = (width - pw) / 2f;
        float py = (height - ph) / 2f;

        UiRender.rect(m, px, py, pw, ph, 10, t.panel());
        renderTopBar(m, mouseX, mouseY, px, py, pw);

        float pad = 14f;
        float cx = px + pad;
        float cw = pw - pad * 2;
        float contentTop = py + 48;
        float contentBottom = py + ph - pad;

        if (activeCategory == Category.CONFIGS) {
            renderConfigs(m, mouseX, mouseY, cx, contentTop, cw);
        } else {
            renderModules(m, mouseX, mouseY, cx, contentTop, cw, contentBottom);
        }

        if (dropdown != null) {
            renderDropdown(m, mouseX, mouseY, cx, contentTop, cw, contentBottom);
        }
        if (settingsView) {
            renderSettings(m, mouseX, mouseY);
        }

        FontRenderer.flushDeferred();
    }

    private void renderTopBar(MatrixStack m, int mouseX, int mouseY, float px, float py, float pw) {
        Theme t = Theme.get();

        // logo placeholder block
        float lx = px + 12, ly = py + 7;
        UiRender.rect(m, lx, ly, 28, 28, 7, t.accent());
        F_CARD.drawString(m, "T", lx + 9, ly + 7, 0xFFFFFFFF);
        F_CARD.drawString(m, "TabVisuals", lx + 38, ly + 4, t.text());
        F_SMALL.drawString(m, "[RSHIFT]", lx + 38, ly + 19, t.textSecondary());

        // centered category tabs
        float tabH = 26, tabY = py + 8;
        Category[] cats = Category.values();
        float[] ws = new float[cats.length];
        float total = 0;
        for (int i = 0; i < cats.length; i++) {
            ws[i] = 10 + 16 + 5 + F_TAB.getStringWidth(cats[i].displayName()) + 12;
            total += ws[i] + 4;
        }
        total -= 4;
        float tx = px + pw / 2f - total / 2f;
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            float w = ws[i];
            boolean isActive = c == activeCategory;
            boolean hover = UiRender.inside(mouseX, mouseY, tx, tabY, w, tabH);
            if (isActive) {
                UiRender.rect(m, tx, tabY, w, tabH, tabH / 2f, t.accent());
            } else if (hover) {
                UiRender.rect(m, tx, tabY, w, tabH, tabH / 2f, t.cardHover());
            }
            float iy = tabY + 5;
            if (isActive) {
                UiRender.rect(m, tx + 10, iy, 16, 16, 8, UiRender.withAlpha(0xFFFFFFFF, 70));
                F_SMALL_SEMI.drawString(m, String.valueOf(c.icon()), tx + 15, iy + 4, 0xFFFFFFFF);
            } else {
                UiRender.outline(m, tx + 10, iy, 16, 16, 8, t.outline(), 1);
                F_SMALL_SEMI.drawString(m, String.valueOf(c.icon()), tx + 15, iy + 4, t.textSecondary());
            }
            int fg = isActive ? 0xFFFFFFFF : (hover ? t.text() : t.textSecondary());
            F_TAB.drawString(m, c.displayName(), tx + 31, tabY + 8, fg);
            tabs.add(new TabRect(c, tx, tabY, w, tabH));
            tx += w + 4;
        }

        // gear (settings overlay)
        gearX = px + pw - 38;
        gearY = py + 7;
        boolean gearHover = UiRender.inside(mouseX, mouseY, gearX, gearY, gearSize, gearSize);
        int gearBg = settingsView ? UiRender.mulAlpha(t.accent(), 0.85f) : (gearHover ? t.cardHover() : t.card());
        UiRender.rect(m, gearX, gearY, gearSize, gearSize, 7, gearBg);
        int glyph = settingsView ? 0xFFFFFFFF : t.text();
        UiRender.outline(m, gearX + 7, gearY + 7, 14, 14, 7, glyph, 1.5f);
        UiRender.rect(m, gearX + 11.5f, gearY + 11.5f, 5, 5, 2.5f, glyph);

        // divider
        UiRender.rect(m, px + 12, py + 43, pw - 24, 1, 0, UiRender.withAlpha(t.outline(), 140));
    }

    private void renderModules(MatrixStack m, int mouseX, int mouseY, float cx, float top, float cw, float bottom) {
        Theme t = Theme.get();

        List<ModuleStub> all = ModuleRegistry.byCategory(activeCategory);
        int activeCount = 0;
        for (ModuleStub s : all) {
            if (s.enabled()) {
                activeCount++;
            }
        }

        F_TITLE.drawString(m, activeCategory.displayName(), cx, top, t.text());

        // search box
        searchW = 150;
        searchH = 18;
        searchX = cx + cw - searchW;
        searchY = top + 5;
        UiRender.rect(m, searchX, searchY, searchW, searchH, 5, t.card());
        if (searchFocused) {
            UiRender.outline(m, searchX, searchY, searchW, searchH, 5, t.accent(), 1);
        }
        boolean emptySearch = search.isEmpty() && !searchFocused;
        F_SMALL.drawString(m, emptySearch ? "Search..." : search + (searchFocused ? "|" : ""),
                searchX + 8, searchY + 5, emptySearch ? t.textSecondary() : t.text());

        // badges right-aligned before the search box
        String activeLabel = activeCount + " Active";
        String totalLabel = all.size() + " Total";
        float b1w = F_SMALL.getStringWidth(activeLabel) + 16;
        float b2w = F_SMALL.getStringWidth(totalLabel) + 16;
        float badgeX = searchX - 10 - b1w - 6 - b2w;
        UiRender.rect(m, badgeX, top + 5, b1w, 18, 9, UiRender.withAlpha(t.accent(), 70));
        F_SMALL.drawString(m, activeLabel, badgeX + 8, top + 10, t.accent());
        UiRender.rect(m, badgeX + b1w + 6, top + 5, b2w, 18, 9, t.card());
        F_SMALL.drawString(m, totalLabel, badgeX + b1w + 14, top + 10, t.textSecondary());

        // masonry grid
        gridTop = top + 30;
        viewportH = bottom - gridTop;
        int cols = cw > 720 ? 3 : (cw > 470 ? 2 : 1);
        float gap = 8;
        float cardW = (cw - gap * (cols - 1)) / cols;

        String q = search.toLowerCase(Locale.ROOT);
        List<ModuleStub> mods = new ArrayList<>();
        for (ModuleStub s : all) {
            if (q.isEmpty()
                    || s.name().toLowerCase(Locale.ROOT).contains(q)
                    || s.description().toLowerCase(Locale.ROOT).contains(q)) {
                mods.add(s);
            }
        }

        float[] colY = new float[cols];
        layout.clear();
        for (ModuleStub s : mods) {
            float h = cardHeight(s);
            int col = 0;
            for (int i = 1; i < cols; i++) {
                if (colY[i] < colY[col]) {
                    col = i;
                }
            }
            layout.add(new CardBounds(s, cx + col * (cardW + gap), colY[col], cardW, h));
            colY[col] += h + gap;
        }
        float maxCol = 0;
        for (float v : colY) {
            maxCol = Math.max(maxCol, v);
        }
        contentHeight = Math.max(0, maxCol - gap);

        float maxScroll = Math.max(0, contentHeight - viewportH);
        float scroll = Math.min(scrollByCat.getOrDefault(activeCategory, 0f), maxScroll);
        scrollByCat.put(activeCategory, scroll);

        m.push();
        m.translate(0, -scroll, 0);
        Matrix4f matrix4f = m.peek().getPositionMatrix();
        scissor.push(matrix4f, cx, gridTop, cw, viewportH);
        for (CardBounds cb : layout) {
            drawCard(m, mouseX, mouseY, cb, scroll);
        }
        scissor.pop();
        m.pop();
    }

    private float cardHeight(ModuleStub mod) {
        return 42 + mod.settings().size() * ROW_H + 6;
    }

    private void drawCard(MatrixStack m, int mouseX, int mouseY, CardBounds cb, float scroll) {
        Theme t = Theme.get();
        ModuleStub mod = cb.module;
        float x = cb.x;
        float y = gridTop + cb.yRel - scroll;
        float w = cb.w;

        boolean hover = UiRender.inside(mouseX, mouseY, x, y, w, cb.h);
        UiRender.rect(m, x, y, w, cb.h, 6, hover ? t.cardHover() : t.card());

        F_CARD.drawString(m, mod.name(), x + 10, y + 8, t.text());

        // favorite dot
        if (mod.favorite()) {
            UiRender.rect(m, x + w - 46, y + 11, 8, 8, 4, t.accent());
        } else {
            UiRender.outline(m, x + w - 46, y + 11, 8, 8, 4, t.outline(), 1);
        }

        // toggle switch
        UiRender.rect(m, x + w - 34, y + 8, 26, 14, 7, mod.enabled() ? t.accent() : t.switchOff());
        float knobX = mod.enabled() ? x + w - 34 + 15 : x + w - 34 + 3;
        UiRender.rect(m, knobX, y + 11, 8, 8, 4, 0xFFFFFFFF);

        // description
        F_SMALL.drawStringWithScroll(m, mod.description(), x + 10, y + 26, w - 20, t.textSecondary());

        // settings rows
        float sy = y + 42;
        cb.settings.clear();
        for (Setting s : mod.settings()) {
            SettingBounds sb = new SettingBounds(s, x, sy, w, ROW_H);
            cb.settings.add(sb);
            drawSettingRow(m, mouseX, mouseY, sb, scroll);
            sy += ROW_H;
        }
    }

    private void drawSettingRow(MatrixStack m, int mouseX, int mouseY, SettingBounds sb, float scroll) {
        Theme t = Theme.get();
        float x = sb.x + 8;
        float w = sb.w - 16;
        float y = sb.y;
        Setting s = sb.setting;

        if (s instanceof BooleanSetting b) {
            F_SMALL.drawString(m, b.name(), x, y + 5, t.text());
            float bx = sb.x + sb.w - 20, by = y + 5;
            if (b.get()) {
                UiRender.rect(m, bx, by, 12, 12, 3, t.accent());
                UiRender.rect(m, bx + 3.5f, by + 3.5f, 5, 5, 1.5f, 0xFFFFFFFF);
            } else {
                UiRender.outline(m, bx, by, 12, 12, 3, t.outline(), 1);
            }
        } else if (s instanceof DoubleSetting d) {
            F_SMALL.drawString(m, d.name(), x, y, t.text());
            String val = d.format();
            F_SMALL_SEMI.drawString(m, val, sb.x + sb.w - 8 - F_SMALL_SEMI.getStringWidth(val), y, t.accent());
            float ty = y + 14;
            UiRender.rect(m, x, ty, w, 4, 2, t.switchOff());
            float fill = (w - 8) * d.fraction();
            UiRender.rect(m, x, ty, fill + 4, 4, 2, t.accent());
            UiRender.rect(m, x + fill, ty - 2, 8, 8, 4, 0xFFFFFFFF);
        } else if (s instanceof ModeSetting md) {
            F_SMALL.drawString(m, md.name(), x, y + 5, t.text());
            String val = md.get();
            float pw2 = F_SMALL.getStringWidth(val) + 16;
            float px2 = sb.x + sb.w - 8 - pw2, py2 = y + 3;
            boolean hov = UiRender.inside(mouseX, mouseY, px2, py2, pw2, 16);
            UiRender.rect(m, px2, py2, pw2, 16, 5, hov ? t.cardHover() : t.switchOff());
            if (dropdown == md) {
                UiRender.outline(m, px2, py2, pw2, 16, 5, t.accent(), 1);
            }
            F_SMALL.drawString(m, val, px2 + 8, py2 + 4, t.text());
        }
    }

    private void renderDropdown(MatrixStack m, int mouseX, int mouseY, float cx, float top, float cw, float bottom) {
        Theme t = Theme.get();
        String[] opts = dropdown.options();
        float totalH = opts.length * DROP_ROW_H + 6;
        Matrix4f matrix4f = m.peek().getPositionMatrix();
        scissor.push(matrix4f, cx, top, cw, bottom - top);
        UiRender.rect(m, dropX, dropY, dropW, totalH, 6, t.panel());
        UiRender.outline(m, dropX, dropY, dropW, totalH, 6, t.outline(), 1);
        float oy = dropY + 3;
        for (int i = 0; i < opts.length; i++) {
            boolean sel = i == dropdown.index();
            boolean hov = UiRender.inside(mouseX, mouseY, dropX + 3, oy, dropW - 6, DROP_ROW_H);
            if (sel || hov) {
                UiRender.rect(m, dropX + 3, oy, dropW - 6, DROP_ROW_H, 4, UiRender.withAlpha(t.accent(), sel ? 70 : 35));
            }
            F_SMALL.drawString(m, opts[i], dropX + 10, oy + 5, sel ? t.accent() : t.text());
            oy += DROP_ROW_H;
        }
        scissor.pop();
    }

    private void renderConfigs(MatrixStack m, int mouseX, int mouseY, float cx, float top, float cw) {
        Theme t = Theme.get();
        F_TITLE.drawString(m, "Configs", cx, top, t.text());
        F_SMALL.drawString(m, "Saved state of modules, settings and theme", cx, top + 22, t.textSecondary());

        // name field
        cfgFieldX = cx;
        cfgFieldY = top + 40;
        UiRender.rect(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, 5, t.card());
        if (configNameFocused) {
            UiRender.outline(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, 5, t.accent(), 1);
        }
        boolean emptyName = configName.isEmpty() && !configNameFocused;
        F_SMALL.drawString(m, emptyName ? "config name..." : configName + (configNameFocused ? "|" : ""),
                cfgFieldX + 8, cfgFieldY + 6, emptyName ? t.textSecondary() : t.text());

        // save button
        cfgSaveX = cfgFieldX + cfgFieldW + 8;
        cfgSaveY = cfgFieldY;
        boolean saveHover = UiRender.inside(mouseX, mouseY, cfgSaveX, cfgSaveY, cfgSaveW, cfgSaveH);
        UiRender.rect(m, cfgSaveX, cfgSaveY, cfgSaveW, cfgSaveH, 5, saveHover ? UiRender.mulAlpha(t.accent(), 0.85f) : t.accent());
        float saveTw = F_SMALL_SEMI.getStringWidth("Save");
        F_SMALL_SEMI.drawString(m, "Save", cfgSaveX + (cfgSaveW - saveTw) / 2f, cfgSaveY + 6, 0xFFFFFFFF);

        // saved configs list
        List<String> names = ConfigManager.listConfigs();
        float ly = top + 74;
        if (names.isEmpty()) {
            F_SMALL.drawString(m, "No configs yet — type a name and press Save", cx, ly, t.textSecondary());
        }
        float rowW = Math.min(cw, 520);
        float lw = 52, dw = 56;
        for (String name : names) {
            UiRender.rect(m, cx, ly, rowW, 26, 6, t.card());
            F_BODY.drawString(m, name, cx + 10, ly + 8, t.text());
            float loadX = cx + rowW - 12 - dw - 6 - lw;
            float delX = cx + rowW - 12 - dw;
            float btnY = ly + 4;
            boolean lh = UiRender.inside(mouseX, mouseY, loadX, btnY, lw, 18);
            boolean dh = UiRender.inside(mouseX, mouseY, delX, btnY, dw, 18);
            UiRender.rect(m, loadX, btnY, lw, 18, 5, lh ? UiRender.mulAlpha(t.accent(), 0.85f) : t.accent());
            UiRender.rect(m, delX, btnY, dw, 18, 5, dh ? t.cardHover() : t.switchOff());
            float loadTw = F_SMALL_SEMI.getStringWidth("Load");
            F_SMALL_SEMI.drawString(m, "Load", loadX + (lw - loadTw) / 2f, btnY + 5, 0xFFFFFFFF);
            float delTw = F_SMALL_SEMI.getStringWidth("Delete");
            F_SMALL_SEMI.drawString(m, "Delete", delX + (dw - delTw) / 2f, btnY + 5, t.text());
            cfgRows.add(new CfgRow(name, cx, ly, rowW, 26, loadX, delX, btnY, lw, dw));
            ly += 32;
        }
    }

    private void renderSettings(MatrixStack m, int mouseX, int mouseY) {
        Theme t = Theme.get();
        UiRender.rect(m, 0, 0, width, height, 0, UiRender.withAlpha(0xFF000000, 140));

        float pw = 340;
        float rowH = 40;
        float ph = 58 + Theme.ALL.length * rowH;
        float px = (width - pw) / 2f;
        float py = (height - ph) / 2f;
        UiRender.rect(m, px, py, pw, ph, 10, t.panel());
        F_CARD.drawString(m, "Themes", px + 14, py + 12, t.text());
        F_SMALL.drawString(m, "Applied instantly, saved to autosave", px + 14, py + 28, t.textSecondary());

        float ry = py + 48;
        for (Theme th : Theme.ALL) {
            boolean active = th.id().equals(t.id());
            boolean hov = UiRender.inside(mouseX, mouseY, px + 12, ry, pw - 24, 32);
            UiRender.rect(m, px + 12, ry, pw - 24, 32, 6,
                    active ? UiRender.withAlpha(th.accent(), 45) : (hov ? t.cardHover() : t.card()));
            if (active) {
                UiRender.outline(m, px + 12, ry, pw - 24, 32, 6, t.accent(), 1);
            }
            F_BODY.drawString(m, th.name(), px + 22, ry + 11, t.text());
            float sx = px + pw - 12 - 64;
            UiRender.rect(m, sx, ry + 10, 12, 12, 6, th.panel());
            UiRender.outline(m, sx, ry + 10, 12, 12, 6, t.outline(), 1);
            UiRender.rect(m, sx + 18, ry + 10, 12, 12, 6, th.accent());
            UiRender.rect(m, sx + 36, ry + 10, 12, 12, 6, th.card());
            UiRender.outline(m, sx + 36, ry + 10, 12, 12, 6, t.outline(), 1);
            themeRows.add(new ThemeRow(th, px + 12, ry, pw - 24, 32));
            ry += rowH;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        if (settingsView) {
            if (UiRender.inside(mouseX, mouseY, gearX, gearY, gearSize, gearSize)) {
                settingsView = false;
                return true;
            }
            for (ThemeRow r : themeRows) {
                if (UiRender.inside(mouseX, mouseY, r.x, r.y, r.w, r.h)) {
                    Theme.set(r.theme);
                    return true;
                }
            }
            return true;
        }

        if (dropdown != null) {
            String[] opts = dropdown.options();
            float totalH = opts.length * DROP_ROW_H + 6;
            if (UiRender.inside(mouseX, mouseY, dropX, dropY, dropW, totalH)) {
                int idx = (int) ((mouseY - dropY - 3) / DROP_ROW_H);
                if (idx >= 0 && idx < opts.length) {
                    dropdown.setIndex(idx);
                }
            }
            dropdown = null;
            return true;
        }

        for (TabRect tr : tabs) {
            if (UiRender.inside(mouseX, mouseY, tr.x, tr.y, tr.w, tr.h)) {
                if (tr.category != activeCategory) {
                    activeCategory = tr.category;
                    searchFocused = false;
                    configNameFocused = false;
                }
                return true;
            }
        }

        if (UiRender.inside(mouseX, mouseY, gearX, gearY, gearSize, gearSize)) {
            settingsView = !settingsView;
            return true;
        }

        if (activeCategory == Category.CONFIGS) {
            return clickConfigs(mouseX, mouseY);
        }

        if (UiRender.inside(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        float scroll = scrollByCat.getOrDefault(activeCategory, 0f);
        for (CardBounds cb : layout) {
            float y = gridTop + cb.yRel - scroll;
            if (!UiRender.inside(mouseX, mouseY, cb.x, y, cb.w, cb.h)) {
                continue;
            }
            ModuleStub mod = cb.module;

            if (UiRender.inside(mouseX, mouseY, cb.x + cb.w - 46, y + 11, 8, 8)) {
                mod.setFavorite(!mod.favorite());
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, cb.x + cb.w - 34, y + 8, 26, 14)) {
                mod.toggle();
                return true;
            }
            for (SettingBounds sb : cb.settings) {
                Setting s = sb.setting;
                if ((s instanceof BooleanSetting || s instanceof DoubleSetting)
                        && UiRender.inside(mouseX, mouseY, sb.x, sb.y, sb.w, sb.h)) {
                    if (s instanceof BooleanSetting b) {
                        b.toggle();
                    } else if (s instanceof DoubleSetting d) {
                        dragging = d;
                        dragTrackX = sb.x + 8;
                        dragTrackW = sb.w - 16;
                        d.set(d.fromFraction((float) ((mouseX - dragTrackX) / dragTrackW)));
                    }
                    return true;
                }
                if (s instanceof ModeSetting md) {
                    String val = md.get();
                    float pw2 = F_SMALL.getStringWidth(val) + 16;
                    float px2 = sb.x + sb.w - 8 - pw2, py2 = sb.y + 3;
                    if (UiRender.inside(mouseX, mouseY, px2, py2, pw2, 16)) {
                        openDropdown(md, px2 - 10, py2 + 19, pw2 + 20);
                        return true;
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickConfigs(double mouseX, double mouseY) {
        if (UiRender.inside(mouseX, mouseY, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH)) {
            configNameFocused = true;
            return true;
        }
        configNameFocused = false;
        if (UiRender.inside(mouseX, mouseY, cfgSaveX, cfgSaveY, cfgSaveW, cfgSaveH)) {
            ConfigManager.saveConfig(configName);
            configName = "";
            return true;
        }
        for (CfgRow r : cfgRows) {
            if (UiRender.inside(mouseX, mouseY, r.loadX, r.btnY, r.btnW, r.btnH)) {
                ConfigManager.loadConfig(r.name);
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, r.delX, r.btnY, r.btnW, r.btnH)) {
                ConfigManager.deleteConfig(r.name);
                return true;
            }
        }
        return true;
    }

    private void openDropdown(ModeSetting md, float x, float y, float w) {
        if (dropdown == md) {
            dropdown = null;
            return;
        }
        String[] opts = md.options();
        float totalH = opts.length * DROP_ROW_H + 6;
        dropX = x;
        dropW = w;
        dropY = y;
        float contentBottom = gridTop + viewportH;
        if (dropY + totalH > contentBottom && y - totalH - 2 >= gridTop - 40) {
            dropY = y - 19 - totalH;
        }
        dropdown = md;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null) {
            dragging.set(dragging.fromFraction((float) ((mouseX - dragTrackX) / dragTrackW)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsView || activeCategory == Category.CONFIGS) {
            return false;
        }
        float maxScroll = Math.max(0, contentHeight - viewportH);
        float cur = scrollByCat.getOrDefault(activeCategory, 0f);
        scrollByCat.put(activeCategory, Math.max(0, Math.min(maxScroll, cur - (float) verticalAmount * 24)));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (Character.isISOControl(chr)) {
            return false;
        }
        if (searchFocused && activeCategory != Category.CONFIGS) {
            search += chr;
            return true;
        }
        if (configNameFocused && activeCategory == Category.CONFIGS && configName.length() < 24) {
            configName += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused || configNameFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                configNameFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (searchFocused && !search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                }
                if (configNameFocused && !configName.isEmpty()) {
                    configName = configName.substring(0, configName.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (configNameFocused) {
                    ConfigManager.saveConfig(configName);
                    configName = "";
                    configNameFocused = false;
                }
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        ConfigManager.autosave();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
