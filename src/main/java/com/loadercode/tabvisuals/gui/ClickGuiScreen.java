// language: Java, file: ClickGuiScreen.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.hud.HudRenderer;
import com.loadercode.tabvisuals.gui.hud.NotificationManager;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
import com.loadercode.tabvisuals.gui.setting.KeySetting;
import com.loadercode.tabvisuals.gui.setting.ModeSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;
import com.loadercode.tabvisuals.gui.title.LogoManager;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.shape.implement.Blur;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
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
    private static final float DROP_ROW_H = 14f;

    public ClickGuiScreen() {
        super(Text.literal("TabVisuals"));
    }

    // --- view state ---
    private Category activeCategory = Category.RENDER;
    private boolean settingsView = false;
    private final Map<Category, Float> scrollByCat = new EnumMap<>(Category.class);
    private String search = "";
    private boolean searchFocused = false;

    /** Smooth toggle knobs: module id → eased 0..1 state. */
    private final Map<String, Float> toggleAnim = new HashMap<>();

    // --- open/close animation (LoxeDLC ease-back feel, ~325ms open) ---
    private final long openStart = System.currentTimeMillis();
    private boolean closing;
    private long closeStart;
    private boolean exited;
    /** Eased open progress of the current frame — gates input while settling. */
    private float animP;
    /** Settings overlay entry, eased 0..1. */
    private float settingsAnim;

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
    private float cfgScroll, cfgListH;
    private int cfgTotal;
    private float cfgSaveX, cfgSaveY, cfgSaveW = 70, cfgSaveH = 20;

    // --- interaction state ---
    private DoubleSetting dragging;
    private float dragTrackX, dragTrackW;
    private ModeSetting dropdown;
    private KeySetting capturing;
    private float dropX, dropY, dropW;

    // --- custom theme color picker (loxeDLC-style HSV popup) ---
    private boolean pickerOpen;
    private String pickerSlot = Theme.CUSTOM_SLOTS[0];
    private float pHue, pSat, pVal;
    /** 0 = none, 1 = saturation/value square, 2 = hue bar. */
    private int pickerDrag;
    private float pvX, pvY, pvW = 186, pvH = 92;
    private float hueX, hueY, hueW = 186, hueH = 10;
    private float pickX, pickY, pickW = 214, pickH;
    private final List<SwatchHit> swatchHits = new ArrayList<>();

    private record SwatchHit(String slot, float x, float y, float w, float h) {
    }

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
        long now = System.currentTimeMillis();
        if (closing) {
            if (now - closeStart >= 220L) {
                if (!exited) {
                    exited = true;
                    MinecraftClient.getInstance().setScreen(null);
                }
                return;
            }
        }

        MatrixStack m = context.getMatrices();
        Theme t = Theme.get();
        tabs.clear();
        cfgRows.clear();
        themeRows.clear();

        float openP = closing ? 1f - (now - closeStart) / 220f : (now - openStart) / 325f;
        openP = clamp01(openP);
        animP = openP;
        float e = easeOutBack(openP);

        // full-frame dim grows in with the window
        UiRender.rect(m, 0, 0, width, height, 0, UiRender.mulAlpha(0xFF000000, 0.55f * openP));

        m.push();
        float scx = width / 2f, scy = height / 2f;
        float s = 0.92f + 0.08f * e;
        m.translate(scx, scy, 0f);
        m.scale(s, s, 1f);
        m.translate(-scx, -scy, 0f);

        float pw = Math.min(width - 40f, 960f);
        float ph = Math.min(height - 40f, 600f);
        float px = (width - pw) / 2f;
        float py = (height - ph) / 2f;

        // LoxeDLC-style glass window: blurred game behind + dark theme panel
        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, px, py, pw, ph)
                    .round(12)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, px, py, pw, ph, 12, UiRender.mulAlpha(t.panel(), openP));
        UiRender.outline(m, px, py, pw, ph, 12, UiRender.mulAlpha(t.outline(), openP), 1f);

        renderTopBar(m, mouseX, mouseY, px, py, pw, openP);

        float pad = 14f;
        float cx = px + pad;
        float cw = pw - pad * 2;
        float contentTop = py + 48;
        float contentBottom = py + ph - pad;

        if (activeCategory == Category.CONFIGS) {
            renderConfigs(m, mouseX, mouseY, cx, contentTop, cw, contentBottom);
        } else {
            renderModules(m, mouseX, mouseY, cx, contentTop, cw, contentBottom);
        }

        if (dropdown != null) {
            renderDropdown(m, mouseX, mouseY, cx, contentTop, cw, contentBottom);
        }
        if (settingsView || settingsAnim > 0.01f) {
            renderSettings(m, mouseX, mouseY);
        }

        m.pop();

        // toasts drawn above the overlay; animation is driven here only when
        // the in-game HUD pass is not running (no world → no HUD frame)
        HudRenderer.renderToasts(MinecraftClient.getInstance(), m, width, height, MinecraftClient.getInstance().player == null);

        FontRenderer.flushDeferred();
    }

    /** Ease-out-back — the LoxeDLC window pop (slight overshoot, no bounce). */
    private static float easeOutBack(float p) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float x = clamp01(p) - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    /** Eased 0..1 switch state shared by module and setting toggles. */
    private float toggleEase(String key, boolean on) {
        float target = on ? 1f : 0f;
        float k = toggleAnim.getOrDefault(key, target);
        if (k != target) {
            k += (target - k) * 0.35f;
            if (Math.abs(target - k) < 0.02f) {
                k = target;
            }
            toggleAnim.put(key, k);
        }
        return k;
    }

    /** LoxeDLC-style switch: hairline track + a slightly larger outlined knob. */
    private void drawToggle(MatrixStack m, float x, float y, float w, float h, float k, Theme t) {
        int track = UiRender.mix(UiRender.withAlpha(t.text(), 26), UiRender.withAlpha(t.accent(), 120), k);
        UiRender.rect(m, x, y, w, h, h / 2f, track);
        UiRender.outline(m, x, y, w, h, h / 2f,
                UiRender.mix(UiRender.withAlpha(t.outline(), 150), UiRender.withAlpha(t.accent(), 190), k), 1f);
        float knob = h + 2f;
        float kx = x - 1f + k * (w - knob + 2f);
        int knobFill = UiRender.mix(t.panel(), t.accent(), k);
        int knobLine = UiRender.mix(UiRender.withAlpha(t.outline(), 200), UiRender.withAlpha(t.accent(), 255), k);
        UiRender.rect(m, kx, y - 1f, knob, knob, knob / 2f, knobFill);
        UiRender.outline(m, kx, y - 1f, knob, knob, knob / 2f, knobLine, 1.2f);
    }

    /** Categories that hold modules, plus the Configs tab — empty ones are hidden. */
    private List<Category> visibleCategories() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) {
            if (c == Category.CONFIGS || !ModuleRegistry.byCategory(c).isEmpty()) {
                out.add(c);
            }
        }
        if (out.isEmpty()) {
            out.add(Category.CONFIGS);
        }
        return out;
    }

    private void renderTopBar(MatrixStack m, int mouseX, int mouseY, float px, float py, float pw, float openA) {
        Theme t = Theme.get();

        // logo block + bind label — brand image when the asset exists
        float lx = px + 12, ly = py + 7;
        Identifier logo = LogoManager.texture();
        float markW;
        if (logo != null) {
            float lh = 24f;
            markW = lh * LogoManager.aspect();
            int col = ((int) (Math.max(0f, Math.min(1f, openA)) * 255f) << 24) | 0x00FFFFFF;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Render2D.drawTexturedQuad(m, logo, lx, lx + markW, ly + 2f, ly + 2f + lh, 0f, 1f, 0f, 1f, col);
            RenderSystem.disableBlend();
        } else {
            markW = 28f;
            UiRender.rect(m, lx, ly, 28, 28, 7, t.accent());
            F_CARD.drawCenteredString(m, "T", lx + 14, ly + (28 - F_CARD.getStringHeight("T")) / 2f + 1, 0xFFFFFFFF);
        }
        F_CARD.drawString(m, "TabVisuals", lx + markW + 10, ly + 4, t.text());
        F_SMALL.drawString(m, "[" + TabVisualsClient.guiKeyLabel() + "]", lx + markW + 10, ly + 19, t.textSecondary());

        // centered category tabs — only categories that actually hold modules
        float tabH = 26, tabY = py + 8;
        List<Category> cats = visibleCategories();
        if (!cats.contains(activeCategory)) {
            activeCategory = cats.get(0);
        }
        float[] ws = new float[cats.size()];
        float total = 0;
        for (int i = 0; i < cats.size(); i++) {
            ws[i] = 10 + 16 + 5 + F_TAB.getStringWidth(cats.get(i).displayName()) + 12;
            total += ws[i] + 4;
        }
        total -= 4;
        float tx = px + pw / 2f - total / 2f;
        for (int i = 0; i < cats.size(); i++) {
            Category c = cats.get(i);
            float w = ws[i];
            boolean isActive = c == activeCategory;
            boolean hover = UiRender.inside(mouseX, mouseY, tx, tabY, w, tabH);
            if (isActive) {
                UiRender.rect(m, tx, tabY, w, tabH, tabH / 2f, t.accent());
            } else if (hover) {
                UiRender.rect(m, tx, tabY, w, tabH, tabH / 2f, t.cardHover());
            }
            // icon: circle with the category letter centred in it
            float iy = tabY + 5;
            String iconS = String.valueOf(c.icon());
            if (isActive) {
                UiRender.rect(m, tx + 10, iy, 16, 16, 8, UiRender.withAlpha(0xFFFFFFFF, 70));
            } else {
                UiRender.outline(m, tx + 10, iy, 16, 16, 8, t.outline(), 1);
            }
            int iconC = isActive ? 0xFFFFFFFF : (hover ? t.text() : t.textSecondary());
            F_SMALL_SEMI.drawString(m, iconS, tx + 10 + (16 - F_SMALL_SEMI.getStringWidth(iconS)) / 2f,
                    iy + (16 - F_SMALL_SEMI.getStringHeight(iconS)) / 2f + 1, iconC);
            int fg = isActive ? 0xFFFFFFFF : (hover ? t.text() : t.textSecondary());
            F_TAB.drawString(m, c.displayName(), tx + 31,
                    tabY + (tabH - F_TAB.getStringHeight(c.displayName())) / 2f + 1, fg);
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

        // search box: hairline field + magnifier glyph
        searchW = 150;
        searchH = 18;
        searchX = cx + cw - searchW;
        searchY = top + 5;
        UiRender.rect(m, searchX, searchY, searchW, searchH, 5, t.card());
        UiRender.outline(m, searchX, searchY, searchW, searchH, 5,
                searchFocused ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
        boolean emptySearch = search.isEmpty() && !searchFocused;
        int icCol = emptySearch ? t.textSecondary() : t.text();
        float icx = searchX + 8, icy = searchY + searchH / 2f;
        UiRender.outline(m, icx, icy - 3.5f, 7, 7, 3.5f, icCol, 1.2f);
        UiRender.rect(m, icx + 5.6f, icy + 2.4f, 3.4f, 1.3f, 0.65f, icCol);
        F_SMALL.drawString(m, emptySearch ? "Поиск..." : search + (searchFocused ? "|" : ""),
                searchX + 22, searchY + (searchH - F_SMALL.getStringHeight("По")) / 2f + 1,
                emptySearch ? t.textSecondary() : t.text());

        // badges right-aligned before the search box
        String activeLabel = activeCount + " Активно";
        String totalLabel = all.size() + " Всего";
        float b1w = F_SMALL.getStringWidth(activeLabel) + 16;
        float b2w = F_SMALL.getStringWidth(totalLabel) + 16;
        float badgeX = searchX - 10 - b1w - 6 - b2w;
        UiRender.rect(m, badgeX, top + 5, b1w, 18, 9, UiRender.withAlpha(t.accent(), 70));
        F_SMALL.drawString(m, activeLabel, badgeX + 8, top + 5 + (18 - F_SMALL.getStringHeight(activeLabel)) / 2f + 1, t.accent());
        UiRender.rect(m, badgeX + b1w + 6, top + 5, b2w, 18, 9, t.card());
        F_SMALL.drawString(m, totalLabel, badgeX + b1w + 14, top + 5 + (18 - F_SMALL.getStringHeight(totalLabel)) / 2f + 1, t.textSecondary());

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

        // clip rect must stay in screen space: taking the matrix AFTER the
        // -scroll translate shifted it up by `scroll`, leaking cards over the header
        Matrix4f clipMatrix = m.peek().getPositionMatrix();
        m.push();
        m.translate(0, -scroll, 0);
        scissor.push(clipMatrix, cx, gridTop, cw, viewportH);
        for (CardBounds cb : layout) {
            drawCard(m, mouseX, mouseY, cb, scroll);
        }
        scissor.pop();
        m.pop();

        // LoxeDLC-style scrollbar: 4px track + rounded thumb on the right
        if (contentHeight > viewportH && viewportH > 10f) {
            float barX = cx + cw - 3f;
            float thumbH = Math.max(18f, viewportH * viewportH / contentHeight);
            float thumbY = gridTop + (viewportH - thumbH) * (scroll / Math.max(1f, maxScroll));
            UiRender.rect(m, barX, gridTop, 3f, viewportH, 1.5f, UiRender.withAlpha(t.text(), 20));
            UiRender.rect(m, barX, thumbY, 3f, thumbH, 1.5f, UiRender.withAlpha(t.text(), 70));
        }
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
        float hv = toggleEase("h:" + mod.id(), hover);
        UiRender.rect(m, x, y, w, cb.h, 6, UiRender.mix(t.card(), t.cardHover(), hv));

        // header row: name left, favourite dot + switch right, one baseline
        float togW = 26, togH = 13;
        float togX = x + w - 10 - togW;
        float rowY = y + 9;
        float nameH = F_CARD.getStringHeight(mod.name());
        F_CARD.drawString(m, mod.name(), x + 10, rowY + (togH - nameH) / 2f + 1, t.text());

        // favourite dot
        if (mod.favorite()) {
            UiRender.rect(m, togX - 12, rowY + togH / 2f - 3, 6, 6, 3, t.accent());
        } else {
            UiRender.outline(m, togX - 12, rowY + togH / 2f - 3, 6, 6, 3, t.outline(), 1);
        }

        // toggle switch (eased knob + color, re-targets while the GUI is open)
        drawToggle(m, togX, rowY, togW, togH, toggleEase("m:" + mod.id(), mod.enabled()), t);

        // description
        F_SMALL.drawStringWithScroll(m, mod.description(), x + 10, y + 27, w - 20, t.textSecondary());

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

    /** Setting rows: label left, control right, everything on one centre line. */
    private void drawSettingRow(MatrixStack m, int mouseX, int mouseY, SettingBounds sb, float scroll) {
        Theme t = Theme.get();
        float x = sb.x + 10;
        float y = sb.y;
        float cy = y + (ROW_H - 11) / 2f;
        Setting s = sb.setting;

        if (s instanceof BooleanSetting b) {
            F_SMALL.drawString(m, b.name(), x, cy + 1, t.text());
            float tw = 22, th = 11;
            drawToggle(m, sb.x + sb.w - 10 - tw, y + (ROW_H - th) / 2f, tw, th,
                    toggleEase("s:" + System.identityHashCode(b), b.get()), t);
        } else if (s instanceof DoubleSetting d) {
            F_SMALL.drawString(m, d.name(), x, cy + 1, t.text());
            String val = d.format();
            F_SMALL_SEMI.drawString(m, val, sb.x + sb.w - 10 - F_SMALL_SEMI.getStringWidth(val),
                    cy + 1, t.accent());
            float ty = y + ROW_H - 5;
            UiRender.rect(m, x, ty, sb.w - 20, 3, 1.5f, UiRender.withAlpha(t.text(), 30));
            float fill = (sb.w - 20 - 8) * d.fraction();
            UiRender.rect(m, x, ty, fill + 4, 3, 1.5f, t.accent());
            UiRender.rect(m, x + fill, ty - 2.5f, 8, 8, 4, t.accent());
            UiRender.outline(m, x + fill, ty - 2.5f, 8, 8, 4, UiRender.withAlpha(0xFFFFFFFF, 170), 1.2f);
        } else if (s instanceof ModeSetting md) {
            F_SMALL.drawString(m, md.name(), x, cy + 1, t.text());
            String val = md.get();
            float fw = 66, fh = 14;
            float px2 = sb.x + sb.w - 10 - fw, py2 = y + (ROW_H - fh) / 2f;
            boolean hov = UiRender.inside(mouseX, mouseY, px2, py2, fw, fh);
            UiRender.rect(m, px2, py2, fw, fh, 3, hov ? t.cardHover() : UiRender.withAlpha(t.card(), 210));
            UiRender.outline(m, px2, py2, fw, fh, 3,
                    dropdown == md ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
            F_SMALL.drawStringWithScroll(m, val, px2 + 7,
                    py2 + (fh - F_SMALL.getStringHeight(val)) / 2f + 1, fw - 22, t.text());
            // chevron: three stacked bars, centred in the field's right side
            float chx = px2 + fw - 12, chy = py2 + fh / 2f - 1.2f;
            UiRender.rect(m, chx, chy, 7, 1.2f, 0.6f, t.textSecondary());
            UiRender.rect(m, chx + 1, chy + 1.3f, 5, 1.2f, 0.6f, t.textSecondary());
            UiRender.rect(m, chx + 2, chy + 2.6f, 3, 1.2f, 0.6f, t.textSecondary());
        } else if (s instanceof KeySetting k) {
            F_SMALL.drawString(m, k.name(), x, cy + 1, t.text());
            String val = capturing == k ? "..." : k.label();
            float fw = 66, fh = 14;
            float px2 = sb.x + sb.w - 10 - fw, py2 = y + (ROW_H - fh) / 2f;
            boolean hov = UiRender.inside(mouseX, mouseY, px2, py2, fw, fh);
            UiRender.rect(m, px2, py2, fw, fh, 3, hov ? t.cardHover() : UiRender.withAlpha(t.card(), 210));
            UiRender.outline(m, px2, py2, fw, fh, 3,
                    capturing == k ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
            F_SMALL.drawStringWithScroll(m, val, px2 + 7,
                    py2 + (fh - F_SMALL.getStringHeight(val)) / 2f + 1, fw - 14, t.text());
        }
    }

    private void renderDropdown(MatrixStack m, int mouseX, int mouseY, float cx, float top, float cw, float bottom) {
        Theme t = Theme.get();
        String[] opts = dropdown.options();
        float totalH = opts.length * DROP_ROW_H + 6;
        Matrix4f matrix4f = m.peek().getPositionMatrix();
        scissor.push(matrix4f, cx, top, cw, bottom - top);
        UiRender.rect(m, dropX, dropY, dropW, totalH, 5, t.panel());
        UiRender.outline(m, dropX, dropY, dropW, totalH, 5, UiRender.withAlpha(t.outline(), 155), 1);
        float oy = dropY + 3;
        for (int i = 0; i < opts.length; i++) {
            boolean sel = i == dropdown.index();
            boolean hov = UiRender.inside(mouseX, mouseY, dropX + 3, oy, dropW - 6, DROP_ROW_H);
            if (sel || hov) {
                UiRender.rect(m, dropX + 3, oy, dropW - 6, DROP_ROW_H, 3,
                        UiRender.withAlpha(t.accent(), sel ? 55 : 26));
            }
            F_SMALL.drawString(m, opts[i], dropX + 9,
                    oy + (DROP_ROW_H - F_SMALL.getStringHeight(opts[i])) / 2f + 1,
                    sel ? t.accent() : t.text());
            oy += DROP_ROW_H;
        }
        scissor.pop();
    }

    private void renderConfigs(MatrixStack m, int mouseX, int mouseY, float cx, float top, float cw, float bottom) {
        Theme t = Theme.get();
        F_TITLE.drawString(m, "Конфиги", cx, top, t.text());
        F_SMALL.drawString(m, "Сохранённые модули, настройки и тема", cx, top + 22, t.textSecondary());

        // name field
        cfgFieldX = cx;
        cfgFieldY = top + 40;
        UiRender.rect(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, 5, t.card());
        UiRender.outline(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, 5,
                configNameFocused ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
        boolean emptyName = configName.isEmpty() && !configNameFocused;
        F_SMALL.drawString(m, emptyName ? "имя конфига..." : configName + (configNameFocused ? "|" : ""),
                cfgFieldX + 8, cfgFieldY + (cfgFieldH - F_SMALL.getStringHeight("имя")) / 2f + 1,
                emptyName ? t.textSecondary() : t.text());

        // save button
        cfgSaveX = cfgFieldX + cfgFieldW + 8;
        cfgSaveY = cfgFieldY;
        boolean saveHover = UiRender.inside(mouseX, mouseY, cfgSaveX, cfgSaveY, cfgSaveW, cfgSaveH);
        UiRender.rect(m, cfgSaveX, cfgSaveY, cfgSaveW, cfgSaveH, 5, saveHover ? UiRender.mulAlpha(t.accent(), 0.85f) : t.accent());
        String saveLabel = "Сохранить";
        float saveTw = F_SMALL_SEMI.getStringWidth(saveLabel);
        F_SMALL_SEMI.drawString(m, saveLabel, cfgSaveX + (cfgSaveW - saveTw) / 2f,
                cfgSaveY + (cfgSaveH - F_SMALL_SEMI.getStringHeight(saveLabel)) / 2f + 1, 0xFFFFFFFF);

        // saved configs list — clipped to the content area, wheel-scrollable
        List<String> names = ConfigManager.listConfigs();
        cfgTotal = names.size();
        float ly = top + 74;
        float listH = Math.max(0, bottom - ly);
        cfgListH = listH;
        float maxScroll = Math.max(0, cfgTotal * 32f - listH + 6f);
        cfgScroll = Math.min(Math.max(0, cfgScroll), maxScroll);

        if (names.isEmpty()) {
            F_SMALL.drawString(m, "Пока нет конфигов — введите имя и нажмите «Сохранить»", cx, ly, t.textSecondary());
            return;
        }

        float rowW = Math.min(cw, 520);
        float lw = 64, dw = 70;
        Matrix4f clipMatrix = m.peek().getPositionMatrix();
        m.push();
        m.translate(0, -cfgScroll, 0);
        scissor.push(clipMatrix, cx, ly, cw, listH);
        float rowY = ly;
        for (String name : names) {
            float screenY = rowY - cfgScroll; // hit-testing happens in screen space
            UiRender.rect(m, cx, rowY, rowW, 26, 6, t.card());
            F_BODY.drawString(m, name, cx + 10,
                    rowY + (26 - F_BODY.getStringHeight(name)) / 2f + 1, t.text());
            float loadX = cx + rowW - 12 - dw - 6 - lw;
            float delX = cx + rowW - 12 - dw;
            float btnY = rowY + 4;
            boolean lh = UiRender.inside(mouseX, mouseY, loadX, screenY + 4, lw, 18);
            boolean dh = UiRender.inside(mouseX, mouseY, delX, screenY + 4, dw, 18);
            UiRender.rect(m, loadX, btnY, lw, 18, 5, lh ? UiRender.mulAlpha(t.accent(), 0.85f) : t.accent());
            UiRender.rect(m, delX, btnY, dw, 18, 5, dh ? t.cardHover() : UiRender.withAlpha(t.text(), 14));
            String loadLabel = "Загрузить";
            float loadTw = F_SMALL_SEMI.getStringWidth(loadLabel);
            F_SMALL_SEMI.drawString(m, loadLabel, loadX + (lw - loadTw) / 2f,
                    btnY + (18 - F_SMALL_SEMI.getStringHeight(loadLabel)) / 2f + 1, 0xFFFFFFFF);
            String delLabel = "Удалить";
            float delTw = F_SMALL_SEMI.getStringWidth(delLabel);
            F_SMALL_SEMI.drawString(m, delLabel, delX + (dw - delTw) / 2f,
                    btnY + (18 - F_SMALL_SEMI.getStringHeight(delLabel)) / 2f + 1, t.text());
            // only rows whose band intersects the clip stay clickable
            if (screenY + 26 > ly && screenY < ly + listH) {
                cfgRows.add(new CfgRow(name, cx, screenY, rowW, 26, loadX, delX, screenY + 4, lw, dw));
            }
            rowY += 32;
        }
        scissor.pop();
        m.pop();
    }

    private void renderSettings(MatrixStack m, int mouseX, int mouseY) {
        Theme t = Theme.get();
        // eased overlay entry (backdrop + slight scale of the panel)
        settingsAnim += ((settingsView ? 1f : 0f) - settingsAnim) * 0.25f;
        if (settingsAnim < 0.01f) {
            settingsAnim = 0f;
            return;
        }
        float a = settingsAnim;
        float e = easeOutBack(a);

        UiRender.rect(m, 0, 0, width, height, 0, UiRender.withAlpha(0xFF000000, (int) (140 * a)));
        swatchHits.clear();

        List<Theme> themes = Theme.all();
        float pw = 340;
        float rowH = 40;
        float ph = 58 + themes.size() * rowH;
        float px = (width - pw) / 2f;
        float py = (height - ph) / 2f;

        float s = 0.94f + 0.06f * e;
        m.push();
        float ocx = width / 2f, ocy = height / 2f;
        m.translate(ocx, ocy, 0f);
        m.scale(s, s, 1f);
        m.translate(-ocx, -ocy, 0f);

        UiRender.rect(m, px, py, pw, ph, 10, UiRender.mulAlpha(t.panel(), a));
        UiRender.outline(m, px, py, pw, ph, 10, UiRender.mulAlpha(t.outline(), a), 1);
        F_CARD.drawString(m, "Темы", px + 14, py + 12, t.text());
        F_SMALL.drawString(m, "Применяется сразу — кликните свотч Custom, чтобы перекрасить",
                px + 14, py + 28, t.textSecondary());

        float ry = py + 48;
        for (Theme th : themes) {
            boolean isActive = th.id().equals(t.id());
            boolean hov = UiRender.inside(mouseX, mouseY, px + 12, ry, pw - 24, 32);
            UiRender.rect(m, px + 12, ry, pw - 24, 32, 6,
                    isActive ? UiRender.withAlpha(th.accent(), 45) : (hov ? t.cardHover() : t.card()));
            if (isActive) {
                UiRender.outline(m, px + 12, ry, pw - 24, 32, 6, t.accent(), 1);
            }
            F_BODY.drawString(m, th.name(), px + 22, ry + (32 - F_BODY.getStringHeight(th.name())) / 2f + 1, t.text());

            if (th.id().equals("custom")) {
                // five editable slots of the Custom palette
                int i = 0;
                for (String slot : Theme.CUSTOM_SLOTS) {
                    float sx = px + pw - 12 - 140 + i * 17;
                    int c = Theme.customSlot(slot);
                    UiRender.rect(m, sx, ry + 10, 12, 12, 6, c);
                    UiRender.outline(m, sx, ry + 10, 12, 12, 6,
                            pickerOpen && pickerSlot.equals(slot) ? t.accent() : t.outline(), 1);
                    swatchHits.add(new SwatchHit(slot, sx, ry + 10, 12, 12));
                    i++;
                }
            } else {
                float sx = px + pw - 12 - 64;
                UiRender.rect(m, sx, ry + 10, 12, 12, 6, th.panel());
                UiRender.outline(m, sx, ry + 10, 12, 12, 6, t.outline(), 1);
                UiRender.rect(m, sx + 18, ry + 10, 12, 12, 6, th.accent());
                UiRender.rect(m, sx + 36, ry + 10, 12, 12, 6, th.card());
                UiRender.outline(m, sx + 36, ry + 10, 12, 12, 6, t.outline(), 1);
            }
            themeRows.add(new ThemeRow(th, px + 12, ry, pw - 24, 32));
            ry += rowH;
        }
        m.pop();

        if (pickerOpen) {
            renderPicker(m, mouseX, mouseY);
        }
    }

    /** HSV popup for one Custom slot: swatch tabs, SV square, hue bar, hex, Done. */
    private void renderPicker(MatrixStack m, int mouseX, int mouseY) {
        Theme t = Theme.get();
        UiRender.rect(m, 0, 0, width, height, 0, UiRender.withAlpha(0xFF000000, 90));

        pickW = 214;
        pickH = 208;
        pickX = (width - pickW) / 2f;
        pickY = (height - pickH) / 2f;
        UiRender.rect(m, pickX, pickY, pickW, pickH, 10, t.panel());
        UiRender.outline(m, pickX, pickY, pickW, pickH, 10, t.outline(), 1);

        F_CARD.drawString(m, "Цвет слота", pickX + 10, pickY + 9, t.text());

        // slot swatch tabs
        float sy = pickY + 30;
        for (int i = 0; i < Theme.CUSTOM_SLOTS.length; i++) {
            String slot = Theme.CUSTOM_SLOTS[i];
            float sx = pickX + 10 + i * 31;
            boolean sel = slot.equals(pickerSlot);
            UiRender.rect(m, sx, sy, 26, 18, 4, Theme.customSlot(slot));
            UiRender.outline(m, sx, sy, 26, 18, 4, sel ? t.accent() : t.outline(), sel ? 1.5f : 1);
            swatchHits.add(new SwatchHit(slot, sx, sy, 26, 18));
        }
        int slotIdx = 0;
        for (int i = 0; i < Theme.CUSTOM_SLOTS.length; i++) {
            if (Theme.CUSTOM_SLOTS[i].equals(pickerSlot)) {
                slotIdx = i;
            }
        }
        F_SMALL.drawString(m, Theme.CUSTOM_SLOT_NAMES[slotIdx], pickX + 10 + 5 * 31 - 5, sy + 4, t.accent());

        // saturation/value square
        pvX = pickX + 10;
        pvY = pickY + 56;
        pvW = pickW - 20;
        pvH = 92;
        int hueRgb = hsv(pHue, 1f, 1f);
        UiRender.rect(m, pvX, pvY, pvW, pvH, 5, hueRgb);
        UiRender.gradient(m, pvX, pvY, pvW, pvH, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF);
        UiRender.gradient(m, pvX, pvY, pvW, pvH, 0x00000000, 0xFF000000, 0xFF000000, 0x00000000);
        UiRender.outline(m, pvX, pvY, pvW, pvH, 5, t.outline(), 1);
        float mkx = pvX + pSat * pvW;
        float mky = pvY + (1f - pVal) * pvH;
        UiRender.outline(m, mkx - 5, mky - 5, 10, 10, 5, 0x80000000, 3f);
        UiRender.outline(m, mkx - 4, mky - 4, 8, 8, 4, 0xFFFFFFFF, 1.5f);

        // hue bar
        hueX = pickX + 10;
        hueY = pickY + 156;
        hueW = pickW - 20;
        hueH = 10;
        int slices = 36;
        float sw = hueW / slices;
        for (int i = 0; i < slices; i++) {
            UiRender.rect(m, hueX + i * sw, hueY, sw + 0.5f, hueH, 0, hsv(360f * i / slices, 1f, 1f));
        }
        UiRender.outline(m, hueX, hueY, hueW, hueH, 3, t.outline(), 1);
        float hx = hueX + (pHue / 360f) * hueW;
        UiRender.outline(m, hx - 5, hueY - 2, 10, hueH + 4, 4, 0x80000000, 3f);
        UiRender.outline(m, hx - 4, hueY - 1, 8, hueH + 2, 3.5f, 0xFFFFFFFF, 1.5f);

        // hex + Done
        float fy = pickY + 176;
        int color = Theme.customSlot(pickerSlot);
        UiRender.rect(m, pickX + 10, fy + 2, 16, 16, 4, color);
        UiRender.outline(m, pickX + 10, fy + 2, 16, 16, 4, t.outline(), 1);
        String hex = String.format("#%06X", color & 0xFFFFFF);
        F_SMALL.drawString(m, hex, pickX + 32, fy + 5, t.textSecondary());

        float btnW = 64, btnX = pickX + pickW - 10 - btnW;
        boolean doneHov = UiRender.inside(mouseX, mouseY, btnX, fy, btnW, 20);
        UiRender.rect(m, btnX, fy, btnW, 20, 5, doneHov ? UiRender.mulAlpha(t.accent(), 0.85f) : t.accent());
        String done = "Готово";
        F_SMALL_SEMI.drawString(m, done, btnX + (btnW - F_SMALL_SEMI.getStringWidth(done)) / 2f,
                fy + (20 - F_SMALL_SEMI.getStringHeight(done)) / 2f + 1, 0xFFFFFFFF);
    }

    /** Applies the SV / hue cursor to the currently selected Custom slot. */
    private void pickerPick(double mx, double my) {
        if (pickerDrag == 1) {
            pSat = clamp01((float) ((mx - pvX) / pvW));
            pVal = clamp01(1f - (float) ((my - pvY) / pvH));
        } else if (pickerDrag == 2) {
            pHue = clamp01((float) ((mx - hueX) / hueW)) * 360f;
        } else {
            return;
        }
        Theme.setCustom(pickerSlot, hsv(pHue, pSat, pVal));
    }

    private void openPicker(String slot) {
        pickerSlot = slot;
        pickerOpen = true;
        pickerDrag = 0;
        if (!Theme.get().id().equals("custom")) {
            Theme.set(Theme.byId("custom")); // editing colours implies the Custom theme
        }
        int c = Theme.customSlot(slot);
        float[] hsv = rgbToHsv(c);
        pHue = hsv[0];
        pSat = hsv[1];
        pVal = hsv[2];
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    /** HSV → ARGB (h 0..360, s/v 0..1), fully opaque. */
    private static int hsv(float h, float s, float v) {
        float c = v * s;
        float hp = ((h % 360f) + 360f) % 360f / 60f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r = 0, g = 0, b = 0;
        if (hp < 1) {
            r = c; g = x;
        } else if (hp < 2) {
            r = x; g = c;
        } else if (hp < 3) {
            g = c; b = x;
        } else if (hp < 4) {
            g = x; b = c;
        } else if (hp < 5) {
            r = x; b = c;
        } else {
            r = c; b = x;
        }
        float m = v - c;
        int rr = (int) ((r + m) * 255f);
        int gg = (int) ((g + m) * 255f);
        int bb = (int) ((b + m) * 255f);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    /** ARGB → {hue, sat, val}. */
    private static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0f;
        if (d > 1e-6f) {
            if (max == r) {
                h = 60f * (((g - b) / d) % 6f);
            } else if (max == g) {
                h = 60f * ((b - r) / d + 2f);
            } else {
                h = 60f * ((r - g) / d + 4f);
            }
            if (h < 0) {
                h += 360f;
            }
        }
        float s = max <= 1e-6f ? 0f : d / max;
        return new float[]{h, s, max};
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        // no input while the window is still animating in or out
        if (closing || animP < 0.95f) {
            return true;
        }

        if (settingsView) {
            if (UiRender.inside(mouseX, mouseY, gearX, gearY, gearSize, gearSize)) {
                settingsView = false;
                pickerOpen = false;
                pickerDrag = 0;
                return true;
            }
            if (pickerOpen) {
                if (UiRender.inside(mouseX, mouseY, pvX, pvY, pvW, pvH)) {
                    pickerDrag = 1;
                    pickerPick(mouseX, mouseY);
                    return true;
                }
                if (UiRender.inside(mouseX, mouseY, hueX, hueY, hueW, hueH)) {
                    pickerDrag = 2;
                    pickerPick(mouseX, mouseY);
                    return true;
                }
                for (SwatchHit s : swatchHits) {
                    if (UiRender.inside(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
                        openPicker(s.slot());
                        return true;
                    }
                }
                float doneX = pickX + pickW - 10 - 64;
                if (UiRender.inside(mouseX, mouseY, doneX, pickY + 176, 64, 20)
                        || !UiRender.inside(mouseX, mouseY, pickX, pickY, pickW, pickH)) {
                    pickerOpen = false;
                    pickerDrag = 0;
                }
                return true;
            }
            for (SwatchHit s : swatchHits) {
                if (UiRender.inside(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
                    openPicker(s.slot());
                    return true;
                }
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
            // fully clipped cards are invisible — they must stay unclickable
            if (y + cb.h <= gridTop || y >= gridTop + viewportH) {
                continue;
            }
            if (!UiRender.inside(mouseX, mouseY, cb.x, y, cb.w, cb.h)) {
                continue;
            }
            // a partially clipped card is only clickable inside the visible band
            if (mouseY < gridTop || mouseY >= gridTop + viewportH) {
                continue;
            }
            ModuleStub mod = cb.module;

            if (UiRender.inside(mouseX, mouseY, cb.x + cb.w - 48, y + 12.5f, 6, 6)) {
                mod.setFavorite(!mod.favorite());
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, cb.x + cb.w - 36, y + 9, 26, 13)) {
                mod.toggle();
                NotificationManager.moduleToggle(mod.name(), mod.enabled());
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
                        dragTrackX = sb.x + 10;
                        dragTrackW = sb.w - 20;
                        d.set(d.fromFraction((float) ((mouseX - dragTrackX) / dragTrackW)));
                    }
                    return true;
                }
                if (s instanceof ModeSetting md) {
                    float px2 = sb.x + sb.w - 76, py2 = sb.y + 4;
                    if (UiRender.inside(mouseX, mouseY, px2, py2, 66, 14)) {
                        openDropdown(md, px2 - 6, py2 + 17, 66 + 12);
                        return true;
                    }
                }
                if (s instanceof KeySetting k) {
                    float px2 = sb.x + sb.w - 76, py2 = sb.y + 4;
                    if (UiRender.inside(mouseX, mouseY, px2, py2, 66, 14)) {
                        capturing = k;
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
            if (ConfigManager.saveConfig(configName)) {
                NotificationManager.configEvent(configName, "saved");
            }
            configName = "";
            return true;
        }
        for (CfgRow r : cfgRows) {
            if (UiRender.inside(mouseX, mouseY, r.loadX, r.btnY, r.btnW, r.btnH)) {
                if (ConfigManager.loadConfig(r.name)) {
                    NotificationManager.configEvent(r.name, "loaded");
                }
                return true;
            }
            if (UiRender.inside(mouseX, mouseY, r.delX, r.btnY, r.btnW, r.btnH)) {
                if (ConfigManager.deleteConfig(r.name)) {
                    NotificationManager.configEvent(r.name, "deleted");
                }
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
        if (pickerDrag != 0) {
            pickerPick(mouseX, mouseY);
            return true;
        }
        if (dragging != null) {
            dragging.set(dragging.fromFraction((float) ((mouseX - dragTrackX) / dragTrackW)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pickerDrag = 0;
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsView) {
            return false;
        }
        // an open dropdown is anchored to a card that is about to move — dismiss it
        dropdown = null;
        if (activeCategory == Category.CONFIGS) {
            float maxScroll = Math.max(0, cfgTotal * 32f - cfgListH + 6f);
            cfgScroll = Math.max(0, Math.min(maxScroll, cfgScroll - (float) verticalAmount * 24));
            return true;
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
        if (capturing != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturing = null;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                capturing.set(-1);
                capturing = null;
            } else {
                capturing.set(keyCode);
                capturing = null;
            }
            return true;
        }
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
                    if (ConfigManager.saveConfig(configName)) {
                        NotificationManager.configEvent(configName, "saved");
                    }
                    configName = "";
                    configNameFocused = false;
                }
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && settingsView) {
            if (pickerOpen) {
                pickerOpen = false;
                pickerDrag = 0;
            } else {
                settingsView = false;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        ConfigManager.autosave();
    }

    /**
     * ESC starts the close animation; the screen actually unregisters once it
     * has played out (see render). LoxeDLC-style: no instant disappear.
     */
    @Override
    public void close() {
        if (!closing) {
            closing = true;
            capturing = null;
            closeStart = System.currentTimeMillis();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
