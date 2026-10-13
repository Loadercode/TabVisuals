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
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
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
 * Click GUI in the LoxeDLC MenuScreen layout: a floating 400x250 window
 * (MMB-draggable, ease-back open), a left-docked icon rail with the client
 * logo and category tabs, a header with the category glyph + name, a search
 * field for RENDER/MISC, a two-column module card grid (name / description /
 * settings glyphs / toggle pill / bind chip; right-click opens the settings
 * popup), a compact Configs tab and the theme picker behind the rail gear.
 */
public final class ClickGuiScreen extends Screen {

    // --- fonts (LoxeDLC sizes) ---
    private static final FontRenderer F_TITLE = Fonts.getSize(15, Fonts.Type.DEFAULT);
    private static final FontRenderer F_BODY = Fonts.getSize(12, Fonts.Type.REGULAR);
    private static final FontRenderer F_SEMI = Fonts.getSize(12, Fonts.Type.SEMI);
    private static final FontRenderer F_TINY = Fonts.getSize(11, Fonts.Type.REGULAR);
    private static final FontRenderer F_ICON_A = Fonts.getSize(18, Fonts.Type.GUIICONS);
    private static final FontRenderer F_ICON_B = Fonts.getSize(16, Fonts.Type.GUIICONS);
    private static final FontRenderer F_ICON_C = Fonts.getSize(14, Fonts.Type.GUIICONS);
    private static final FontRenderer F_ICONS = Fonts.getSize(17, Fonts.Type.ICONS);
    private static final FontRenderer F_CAT = Fonts.getSize(18, Fonts.Type.ICONSCATEGORY);
    private static final FontRenderer F_SEARCH = Fonts.getSize(15, Fonts.Type.ICONS);

    // --- window geometry (LoxeDLC MenuScreen) ---
    private static final float WIN_W = 400f;
    private static final float WIN_H = 250f;
    private static final float RAIL_W = 42.5f;
    private static final float RAIL_GAP = 12f;
    private static final float TAB = 20f;
    private static final float TAB_SP = 12f;
    private static final float GRID_X = 12f;
    private static final float GRID_TOP = 35f;
    private static final float CARD_W = 182f;
    private static final float CARD_GAP = 9f;
    private static final float ROW_H = 20f;
    private static final float DROP_ROW_H = 14f;

    public ClickGuiScreen() {
        super(Text.literal("TabVisuals"));
    }

    // --- view state ---
    private Category activeCategory = Category.RENDER;
    private final Map<Category, Float> scrollByCat = new EnumMap<>(Category.class);
    private String search = "";
    private boolean searchFocused = false;

    // --- window position, kept as screen percents like LoxeDLC ---
    private float winX, winY;
    private float offXP = 0.5f, offYP = 0.5f;
    private int lastW, lastH;
    private boolean dragging;
    private double dragOffX, dragOffY;
    /** Mouse transformed through the open-scale — matches the drawn geometry. */
    private double tmx, tmy;

    // --- animation state ---
    private final long openStart = System.currentTimeMillis();
    private boolean closing;
    private long closeStart;
    private boolean exited;
    /** Eased open progress of the current frame — gates input while settling. */
    private float animP;
    /** Frame delta (seconds) for FPS-independent easing of toggles/settings. */
    private long lastFrameMs;
    private float frameDt = 1f / 60f;
    /** Theme overlay (gear) state. */
    private boolean settingsView;
    private float settingsAnim;

    /** Smooth toggle knobs: module/setting id → eased 0..1 state. */
    private final Map<String, Float> toggleAnim = new HashMap<>();

    // --- layout cache (from the last render pass) ---
    private final List<TabRect> tabs = new ArrayList<>();
    private final List<CardBounds> layout = new ArrayList<>();
    private final List<ThemeRow> themeRows = new ArrayList<>();
    private final List<CfgRow> cfgRows = new ArrayList<>();
    private final ScissorAssist scissor = new ScissorAssist();
    private float contentHeight;
    private float viewportH;
    private float searchX, searchY, searchW = 80, searchH = 15;
    private float gearX, gearY, gearSize = 20;

    // --- settings popup (LoxeDLC ModuleSettingsWindow) ---
    private ModuleStub popupModule;
    private float popX, popY, popW = 160, popH;
    private final List<SettingBounds> popRows = new ArrayList<>();

    // --- configs tab state ---
    private String configName = "";
    private boolean configNameFocused = false;
    private float cfgFieldX, cfgFieldY, cfgFieldW = 176, cfgFieldH = 15;
    private float cfgScroll, cfgListH;
    private int cfgTotal;

    // --- interaction state ---
    private DoubleSetting dragging2;
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
        final float x, y, w, h;

        CardBounds(ModuleStub module, float x, float y, float w, float h) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private record ThemeRow(Theme theme, float x, float y, float w, float h) {
    }

    private record CfgRow(String name, float x, float y, float w, float h,
                          float loadX, float delX, float btnY) {
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (lastFrameMs != 0) {
            frameDt = Math.min(0.1f, (now - lastFrameMs) / 1000f);
        }
        lastFrameMs = now;
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

        // window position tracks the screen size by percent (LoxeDLC)
        int cw = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int ch = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (lastW != cw || lastH != ch) {
            if (lastW != 0 && lastH != 0) {
                winX = cw * offXP - WIN_W / 2f;
                winY = ch * offYP - WIN_H / 2f;
            } else {
                winX = cw / 2f - WIN_W / 2f;
                winY = ch / 2f - WIN_H / 2f;
                offXP = (winX + WIN_W / 2f) / cw;
                offYP = (winY + WIN_H / 2f) / ch;
            }
            lastW = cw;
            lastH = ch;
        }

        float openP = closing ? 1f - (now - closeStart) / 220f : (now - openStart) / 325f;
        openP = clamp01(openP);
        animP = openP;
        float e = easeOutBack(openP);
        // geometry scales with (0.5 + 0.5*e) — the mouse is transformed to match
        float sc = 0.5f + 0.5f * e;
        tmx = (mouseX - (winX + WIN_W / 2f)) / sc + (winX + WIN_W / 2f);
        tmy = (mouseY - (winY + WIN_H / 2f)) / sc + (winY + WIN_H / 2f);

        // full-frame dim grows in with the window
        UiRender.rect(m, 0, 0, width, height, 0, UiRender.mulAlpha(0xFF000000, 0.4f * openP));

        m.push();
        float scx = winX + WIN_W / 2f, scy = winY + WIN_H / 2f;
        m.translate(scx, scy, 0f);
        m.scale(sc, sc, 1f);
        m.translate(-scx, -scy, 0f);

        renderWindow(m, t, openP);
        if (activeCategory == Category.CONFIGS) {
            renderConfigs(m);
        } else {
            renderModules(m);
        }
        if (popupModule != null) {
            renderPopup(m, t);
        }
        if (dropdown != null) {
            renderDropdown(m, t);
        }

        m.pop();

        if (settingsView || settingsAnim > 0.01f) {
            renderSettings(m, mouseX, mouseY);
        }

        // toasts drawn above the overlay; animation is driven here only when
        // the in-game HUD pass is not running (no world → no HUD frame)
        HudRenderer.renderToasts(MinecraftClient.getInstance(), m, width, height, MinecraftClient.getInstance().player == null);

        FontRenderer.flushDeferred();
    }

    /** Main window: blur + glass panel + hairline + header (LoxeDLC BackgroundComponent). */
    private void renderWindow(MatrixStack m, Theme t, float openA) {
        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, winX, winY, WIN_W, WIN_H)
                    .round(8)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, winX, winY, WIN_W, WIN_H, 8, UiRender.mulAlpha(t.panel(), openA));
        UiRender.outline(m, winX, winY, WIN_W, WIN_H, 8, UiRender.mulAlpha(t.outline(), openA), 1f);

        // header: category glyph + "• Name", hairline under it
        if (activeCategory != Category.CONFIGS) {
            F_CAT.drawString(m, String.valueOf(activeCategory.icon()), winX + GRID_X, winY + 10, t.text());
        } else {
            F_CAT.drawString(m, "H", winX + GRID_X, winY + 10, t.text());
        }
        F_TITLE.drawString(m, "• " + activeCategory.displayName(), winX + GRID_X + 14, winY + 12, t.text());
        UiRender.rect(m, winX + GRID_X, winY + 28, WIN_W - GRID_X, 0.5f, 0, UiRender.withAlpha(t.outline(), 140));

        renderRail(m, t);
    }

    /** Left-docked icon rail: logo box, category tabs, gear (LoxeDLC CategoryContainer). */
    private void renderRail(MatrixStack m, Theme t) {
        List<Category> cats = visibleCategories();
        if (!cats.contains(activeCategory)) {
            activeCategory = cats.get(0);
        }
        float railH = 50 + cats.size() * TAB + Math.max(0, cats.size() - 1) * TAB_SP + 30;
        float railX = winX - RAIL_W - RAIL_GAP;
        float railY = winY + (WIN_H - railH) / 2f;

        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, railX, railY, RAIL_W, railH)
                    .round(8)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, railX, railY, RAIL_W, railH, 8, UiRender.mulAlpha(t.panel(), 0.98f));
        UiRender.outline(m, railX, railY, RAIL_W, railH, 8, t.outline(), 1f);

        // logo box at the top of the rail — brand logo cropped to the TV mark
        float logoBoxX = railX + (RAIL_W - TAB) / 2f;
        float logoBoxY = railY + 10;
        UiRender.rect(m, logoBoxX, logoBoxY, TAB, TAB, 5, t.card());
        UiRender.outline(m, logoBoxX, logoBoxY, TAB, TAB, 5, UiRender.withAlpha(t.outline(), 160), 1f);
        Identifier logo = LogoManager.texture();
        if (logo != null) {
            // crop to the TV ligature of the keyed logo (1254²: x212..1048, y343..717) —
            // the wordmark turns to mush at this size, so only the mark is shown here
            float ih = TAB * 0.447f;
            float iy = logoBoxY + (TAB - ih) / 2f;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            com.loadercode.tabvisuals.render.geometry.Render2D.drawTexture(m, logo,
                    Math.round(logoBoxX), Math.round(iy), TAB, ih,
                    212, 343, 836, 374, 1254, 1254, 0xFFFFFFFF);
            RenderSystem.disableBlend();
        } else {
            F_TITLE.drawCenteredString(m, "T", logoBoxX + TAB / 2.0,
                    centerBoxY(F_TITLE, "T", logoBoxY, TAB), t.accent());
        }

        // category tabs
        tabs.clear();
        float ty = railY + 40;
        for (Category c : cats) {
            float tx = railX + (RAIL_W - TAB) / 2f;
            boolean isActive = c == activeCategory;
            boolean hover = UiRender.inside(tmx, tmy, tx, ty, TAB, TAB);
            if (!isActive && hover) {
                UiRender.rect(m, tx, ty, TAB, TAB, 4, t.cardHover());
            }
            if (isActive) {
                UiRender.rect(m, tx, ty, TAB, TAB, 5, UiRender.withAlpha(t.accent(), 55));
                UiRender.outline(m, tx, ty, TAB, TAB, 5, UiRender.withAlpha(t.accent(), 140), 1f);
            }
            int col = isActive ? t.accent() : (hover ? t.text() : t.textSecondary());
            String glyph = String.valueOf(c.icon());
            F_CAT.drawCenteredString(m, glyph, tx + TAB / 2.0,
                    centerBoxY(F_CAT, glyph, ty, TAB), col);
            tabs.add(new TabRect(c, tx, ty, TAB, TAB));
            ty += TAB + TAB_SP;
        }

        // gear at the bottom of the rail — the theme overlay
        gearX = railX + (RAIL_W - gearSize) / 2f;
        gearY = railY + railH - 26;
        boolean gearHover = UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize);
        int gearBg = settingsView ? UiRender.mulAlpha(t.accent(), 0.85f) : (gearHover ? t.cardHover() : t.card());
        UiRender.rect(m, gearX, gearY, gearSize, gearSize, 5, gearBg);
        int glyph = settingsView ? 0xFFFFFFFF : t.textSecondary();
        UiRender.outline(m, gearX + 5, gearY + 5, 10, 10, 5, glyph, 1.3f);
        UiRender.rect(m, gearX + 8, gearY + 8, 4, 4, 2, glyph);
    }

    /** Two-column card grid (LoxeDLC CategoryComponent): top-anchored, scrolls down. */
    private void renderModules(MatrixStack m) {
        Theme t = Theme.get();

        // search field — RENDER/MISC only, like LoxeDLC
        if (activeCategory == Category.RENDER || activeCategory == Category.MISC) {
            searchX = winX + WIN_W - 16 - searchW;
            searchY = winY + 7.5f;
            UiRender.rect(m, searchX, searchY, searchW, searchH, 3, t.card());
            UiRender.outline(m, searchX, searchY, searchW, searchH, 3,
                    searchFocused ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
            boolean empty = search.isEmpty() && !searchFocused;
            F_SEARCH.drawString(m, "U", searchX + searchW - 13,
                    centerBoxY(F_SEARCH, "U", searchY, searchH),
                    empty ? t.textSecondary() : t.text());
            UiRender.rect(m, searchX + searchW - 19, searchY + 3.5f, 0.5f, 8, 0, UiRender.withAlpha(t.outline(), 160));
            F_TINY.drawString(m, empty ? "Поиск" : search + (searchFocused ? "|" : ""),
                    searchX + 6, centerBoxY(F_TINY, "Aa", searchY, searchH),
                    empty ? t.textSecondary() : t.text());
        } else {
            search = "";
        }

        // filtered modules: by category, or by name across all when searching
        String q = search.toLowerCase(Locale.ROOT);
        List<ModuleStub> mods = new ArrayList<>();
        for (Category c : Category.values()) {
            if (c == Category.CONFIGS) {
                continue;
            }
            for (ModuleStub s : ModuleRegistry.byCategory(c)) {
                if (q.isEmpty() ? c == activeCategory : s.name().toLowerCase(Locale.ROOT).contains(q)) {
                    mods.add(s);
                }
            }
        }

        // layout: two columns, strict alternation, each card sized to its text
        layout.clear();
        float[] colY = new float[2];
        for (int i = 0; i < mods.size(); i++) {
            ModuleStub s = mods.get(i);
            int col = i % 2;
            float h = cardHeight(s);
            float x = winX + GRID_X + col * (CARD_W + CARD_GAP);
            float y = winY + GRID_TOP + colY[col];
            layout.add(new CardBounds(s, x, y, CARD_W, h));
            colY[col] += h + CARD_GAP;
        }
        contentHeight = Math.max(colY[0], colY[1]) - CARD_GAP;
        viewportH = WIN_H - GRID_TOP - 6;

        float maxScroll = Math.max(0, contentHeight - viewportH);
        float scroll = Math.min(scrollByCat.getOrDefault(activeCategory, 0f), maxScroll);
        scrollByCat.put(activeCategory, scroll);

        Matrix4f clipMatrix = m.peek().getPositionMatrix();
        m.push();
        m.translate(0, -scroll, 0);
        scissor.push(clipMatrix, winX + GRID_X - 4, winY + GRID_TOP, WIN_W - GRID_X - 6, viewportH);
        for (CardBounds cb : layout) {
            drawCard(m, cb, scroll);
        }
        scissor.pop();
        m.pop();

        // scrollbar on the right edge
        if (contentHeight > viewportH && viewportH > 10f) {
            float barX = winX + WIN_W - 8f;
            float thumbH = Math.max(18f, viewportH * viewportH / contentHeight);
            float thumbY = winY + GRID_TOP + (viewportH - thumbH) * (scroll / Math.max(1f, maxScroll));
            UiRender.rect(m, barX, winY + GRID_TOP, 3f, viewportH, 1.5f, UiRender.withAlpha(t.text(), 20));
            UiRender.rect(m, barX, thumbY, 3f, thumbH, 1.5f, UiRender.withAlpha(t.text(), 70));
        }
    }

    /** Wrapped description lines, max 3, ellipsis on overflow. */
    private List<String> wrapDesc(String desc, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : desc.split(" ")) {
            String try2 = cur.isEmpty() ? word : cur + " " + word;
            if (F_BODY.getStringWidth(try2) > maxWidth && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(try2);
            }
            if (lines.size() == 3) {
                break;
            }
        }
        if (lines.size() < 3 && !cur.isEmpty()) {
            lines.add(cur.toString());
        }
        return lines;
    }

    /** LoxeDLC module card: name, wrapped description, separator, glyphs, pill, bind. */
    private float cardHeight(ModuleStub mod) {
        int lines = Math.max(1, wrapDesc(mod.description(), CARD_W - 25).size());
        return 47 + (lines - 1) * 8;
    }

    private void drawCard(MatrixStack m, CardBounds cb, float scroll) {
        Theme t = Theme.get();
        ModuleStub mod = cb.module;
        float x = cb.x;
        float y = cb.y - scroll;
        float w = cb.w;
        float h = cb.h;

        float k = toggleEase("m:" + mod.id(), mod.enabled());
        int fill = UiRender.mix(t.card(), UiRender.mix(t.card(), t.accent(), 0.12f), k);
        UiRender.rect(m, x, y, w, h, 5, fill);
        UiRender.outline(m, x, y, w, h, 5, UiRender.withAlpha(t.outline(), 110), 1f);

        // name: "• Module"
        String name = "• " + mod.name();
        int nameCol = UiRender.mix(UiRender.withAlpha(t.text(), 170), t.text(), k);
        F_TITLE.drawString(m, name, x + 11, y + 6, nameCol);

        // description: first line tagged with the info glyph
        List<String> lines = wrapDesc(mod.description(), w - 25);
        int descCol = UiRender.withAlpha(t.textSecondary(), 186);
        float dy = y + 19;
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                F_ICON_C.drawString(m, "C", x + 6.5f, dy, t.textSecondary());
                F_BODY.drawString(m, lines.get(i), x + 15, dy, descCol);
            } else {
                F_BODY.drawString(m, lines.get(i), x + 7, dy, descCol);
            }
            dy += 8;
        }

        // separator above the bottom row
        float sepY = y + h - 20;
        UiRender.rect(m, x, sepY, w, 0.5f, 0, UiRender.withAlpha(t.outline(), 120));

        // bottom row: settings glyphs left, bind chip, toggle pill right
        float rowY = y + h - 16;
        F_ICON_A.drawString(m, "A", x + 7, rowY - 4, t.textSecondary());
        if (!mod.settings().isEmpty()) {
            F_ICON_B.drawString(m, "B", x + 20, rowY - 3, t.textSecondary());
        }

        // toggle pill (LoxeDLC StatusRender): 16x8 track, accent fill by anim, 9x9 knob
        float togX = x + w - 24;
        float togY = rowY + 2;
        UiRender.rect(m, togX, togY, 16, 8, 4, UiRender.withAlpha(t.text(), 40));
        UiRender.rect(m, togX, togY, 16, 8, 4, UiRender.mulAlpha(t.accent(), k));
        float knobX = togX - 0.5f + k * 8;
        UiRender.rect(m, knobX, togY - 0.5f, 9, 9, 4.5f, t.card());
        UiRender.outline(m, knobX, togY - 0.5f, 9, 9, 4.5f, UiRender.withAlpha(t.outline(), 220), 1.2f);

        // bind chip: key label, or the chain glyph when unbound
        String bindName = capturingMod == mod ? "..." : keyLabel(mod.bind());
        float chipW = Math.max(12f, F_BODY.getStringWidth(bindName)) + 8;
        float chipX = x + w - 32 - chipW;
        UiRender.rect(m, chipX, rowY + 1, chipW, 10, 3, UiRender.withAlpha(t.text(), 80));
        if (mod.bind() < 0 && capturingMod != mod) {
            F_ICON_B.drawString(m, "G", chipX + chipW / 2f - 5, rowY - 1, t.textSecondary());
        } else {
            F_BODY.drawString(m, bindName, chipX + 4, rowY + 2, t.textSecondary());
        }
    }

    /** Right-click settings popup (LoxeDLC ModuleSettingsWindow). */
    private void renderPopup(MatrixStack m, Theme t) {
        popRows.clear();
        List<Setting> settings = popupModule.settings();
        popH = 26 + settings.size() * ROW_H + 10;

        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, popX, popY, popW, popH)
                    .round(6)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, popX, popY, popW, popH, 6, UiRender.mulAlpha(t.panel(), 0.98f));
        UiRender.outline(m, popX, popY, popW, popH, 6, t.outline(), 1f);

        // header: "• Module" + close glyph
        F_SEMI.drawString(m, "• " + popupModule.name(), popX + 8, popY + 8, t.text());
        F_ICONS.drawString(m, "K", popX + popW - 16, popY + 7, t.textSecondary());

        float ry = popY + 26;
        for (Setting s : settings) {
            popRows.add(new SettingBounds(s, popX, ry, popW, ROW_H));
            drawPopupRow(m, t, s, popX, ry);
            ry += ROW_H;
        }
    }

    private void drawPopupRow(MatrixStack m, Theme t, Setting s, float x, float y) {
        if (s instanceof BooleanSetting b) {
            F_ICON_C.drawString(m, "K", x + 8, y + 3, t.textSecondary());
            F_BODY.drawString(m, b.name(), x + 26, y + 4, t.text());
            float k = toggleEase("s:" + System.identityHashCode(b), b.get());
            float togX = x + popW - 24;
            UiRender.rect(m, togX, y + 6, 16, 8, 4, UiRender.withAlpha(t.text(), 40));
            UiRender.rect(m, togX, y + 6, 16, 8, 4, UiRender.mulAlpha(t.accent(), k));
            float knobX = togX - 0.5f + k * 8;
            UiRender.rect(m, knobX, y + 5.5f, 9, 9, 4.5f, t.card());
            UiRender.outline(m, knobX, y + 5.5f, 9, 9, 4.5f, UiRender.withAlpha(t.outline(), 220), 1.2f);
        } else if (s instanceof DoubleSetting d) {
            F_ICON_C.drawString(m, "H", x + 8, y + 1, t.textSecondary());
            F_BODY.drawString(m, d.name(), x + 26, y + 2, t.text());
            String val = d.format();
            F_SEMI.drawString(m, val, x + popW - 8 - F_SEMI.getStringWidth(val), y + 2, t.accent());
            // slider track under the row
            UiRender.rect(m, x + 26, y + 15, popW - 34, 2, 1, UiRender.withAlpha(t.text(), 30));
            float fill = (popW - 34 - 6) * d.fraction();
            UiRender.rect(m, x + 26, y + 15, fill + 3, 2, 1, t.accent());
            UiRender.rect(m, x + 26 + fill, y + 12.5f, 7, 7, 3.5f, t.accent());
            UiRender.outline(m, x + 26 + fill, y + 12.5f, 7, 7, 3.5f, UiRender.withAlpha(0xFFFFFFFF, 170), 1.1f);
        } else if (s instanceof ModeSetting md) {
            F_ICON_C.drawString(m, "J", x + 8, y + 3, t.textSecondary());
            F_BODY.drawString(m, md.name(), x + 26, y + 4, t.text());
            String val = md.get();
            float fw = 60;
            float px2 = x + popW - 8 - fw;
            boolean hov = UiRender.inside(tmx, tmy, px2, y + 3, fw, 14);
            UiRender.rect(m, px2, y + 3, fw, 14, 3, hov ? t.cardHover() : UiRender.withAlpha(t.card(), 210));
            UiRender.outline(m, px2, y + 3, fw, 14, 3,
                    dropdown == md ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
            F_BODY.drawStringWithScroll(m, val, px2 + 5, y + 5.5f, fw - 16, t.text());
            float chx = px2 + fw - 10, chy = y + 9;
            UiRender.rect(m, chx, chy, 5, 1f, 0.5f, t.textSecondary());
            UiRender.rect(m, chx + 1, chy + 1.2f, 3, 1f, 0.5f, t.textSecondary());
        } else if (s instanceof KeySetting k2) {
            F_ICON_C.drawString(m, "L", x + 8, y + 3, t.textSecondary());
            F_BODY.drawString(m, "Бинд", x + 26, y + 4, t.text());
            String val = capturing == k2 ? "..." : k2.label();
            float fw = 60;
            float px2 = x + popW - 8 - fw;
            boolean hov = UiRender.inside(tmx, tmy, px2, y + 3, fw, 14);
            UiRender.rect(m, px2, y + 3, fw, 14, 3, hov ? t.cardHover() : UiRender.withAlpha(t.card(), 210));
            UiRender.outline(m, px2, y + 3, fw, 14, 3,
                    capturing == k2 ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
            F_BODY.drawStringWithScroll(m, val, px2 + 5, y + 5.5f, fw - 10, t.text());
        }
    }

    /** Mode dropdown list anchored under the select field. */
    private void renderDropdown(MatrixStack m, Theme t) {
        String[] opts = dropdown.options();
        float totalH = opts.length * DROP_ROW_H + 6;
        UiRender.rect(m, dropX, dropY, dropW, totalH, 4, t.panel());
        UiRender.outline(m, dropX, dropY, dropW, totalH, 4, UiRender.withAlpha(t.outline(), 155), 1);
        float oy = dropY + 3;
        for (int i = 0; i < opts.length; i++) {
            boolean sel = i == dropdown.index();
            boolean hov = UiRender.inside(tmx, tmy, dropX + 3, oy, dropW - 6, DROP_ROW_H);
            if (sel || hov) {
                UiRender.rect(m, dropX + 3, oy, dropW - 6, DROP_ROW_H, 3,
                        UiRender.withAlpha(t.accent(), sel ? 55 : 26));
            }
            F_TINY.drawString(m, opts[i], dropX + 8,
                    centerBoxY(F_TINY, opts[i], oy, DROP_ROW_H),
                    sel ? t.accent() : t.text());
            oy += DROP_ROW_H;
        }
    }

    /** Compact configs tab: name field + Save button, two-column config cards. */
    private void renderConfigs(MatrixStack m) {
        Theme t = Theme.get();

        cfgFieldX = winX + GRID_X;
        cfgFieldY = winY + 36;
        UiRender.rect(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, 3, t.card());
        UiRender.outline(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, 3,
                configNameFocused ? t.accent() : UiRender.withAlpha(t.outline(), 155), 1);
        boolean empty = configName.isEmpty() && !configNameFocused;
        F_TINY.drawString(m, empty ? "имя конфига..." : configName + (configNameFocused ? "|" : ""),
                cfgFieldX + 6, centerBoxY(F_TINY, "Aa", cfgFieldY, cfgFieldH),
                empty ? t.textSecondary() : t.text());

        // save button (LoxeDLC: floppy glyph + label)
        float saveW = 52;
        float saveX = winX + WIN_W - 16 - saveW;
        boolean saveHover = UiRender.inside(tmx, tmy, saveX, cfgFieldY, saveW, cfgFieldH);
        UiRender.rect(m, saveX, cfgFieldY, saveW, cfgFieldH, 3, saveHover ? UiRender.mulAlpha(t.accent(), 0.85f) : t.accent());
        F_ICON_B.drawString(m, "M", saveX + 5, cfgFieldY + 1, 0xFFFFFFFF);
        F_TINY.drawString(m, "Save", saveX + 18, centerBoxY(F_TINY, "Save", cfgFieldY, cfgFieldH), 0xFFFFFFFF);

        // config cards, two per row
        List<String> names = ConfigManager.listConfigs();
        cfgTotal = names.size();
        float listTop = winY + 58;
        float listH = Math.max(0, WIN_H - 58 - 6);
        cfgListH = listH;
        float rowStep = 48;
        float maxScroll = Math.max(0, (float) Math.ceil(cfgTotal / 2.0) * rowStep - listH);
        cfgScroll = Math.min(Math.max(0, cfgScroll), maxScroll);

        if (names.isEmpty()) {
            F_BODY.drawString(m, "Пока нет конфигов", winX + GRID_X, listTop + 8, t.textSecondary());
            return;
        }

        Matrix4f clipMatrix = m.peek().getPositionMatrix();
        m.push();
        m.translate(0, -cfgScroll, 0);
        scissor.push(clipMatrix, winX + GRID_X - 4, listTop, WIN_W - GRID_X - 4, listH);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int col = i % 2;
            float cx2 = winX + GRID_X + col * (CARD_W + CARD_GAP);
            float cy2 = listTop + (i / 2) * rowStep;
            float screenY = cy2 - cfgScroll;
            UiRender.rect(m, cx2, cy2, CARD_W, 40, 5, t.card());
            UiRender.outline(m, cx2, cy2, CARD_W, 40, 5, UiRender.withAlpha(t.outline(), 110), 1f);
            F_SEMI.drawString(m, name, cx2 + 8, cy2 + 7, t.text());

            float btnY = cy2 + 22;
            // load (play glyph) + delete (trash glyph) icon buttons
            float loadX = cx2 + CARD_W - 38;
            float delX = cx2 + CARD_W - 20;
            boolean lh = UiRender.inside(tmx, tmy, loadX, btnY, 16, 12);
            boolean dh = UiRender.inside(tmx, tmy, delX, btnY, 16, 12);
            UiRender.rect(m, loadX, btnY, 16, 12, 3, lh ? UiRender.withAlpha(t.accent(), 90) : UiRender.withAlpha(t.text(), 30));
            UiRender.rect(m, delX, btnY, 16, 12, 3, dh ? UiRender.withAlpha(0xFFFF6B81, 90) : UiRender.withAlpha(t.text(), 30));
            F_ICONS.drawString(m, "H", loadX + 4, btnY - 2, lh ? t.accent() : t.textSecondary());
            F_ICON_C.drawString(m, "O", delX + 3, btnY - 1, dh ? 0xFFFF6B81 : t.textSecondary());
            if (screenY + 40 > listTop && screenY < listTop + listH) {
                cfgRows.add(new CfgRow(name, cx2, screenY, CARD_W, 40, loadX, delX, btnY - cfgScroll));
            }
        }
        scissor.pop();
        m.pop();

        if (maxScroll > 0) {
            float barX = winX + WIN_W - 8f;
            float thumbH = Math.max(18f, listH * listH / (cfgTotal / 2f * rowStep));
            float thumbY = listTop + (listH - thumbH) * (cfgScroll / Math.max(1f, maxScroll));
            UiRender.rect(m, barX, listTop, 3f, listH, 1.5f, UiRender.withAlpha(t.text(), 20));
            UiRender.rect(m, barX, thumbY, 3f, thumbH, 1.5f, UiRender.withAlpha(t.text(), 70));
        }
    }

    /** Theme overlay behind the gear — kept from the previous GUI. */
    private void renderSettings(MatrixStack m, int mouseX, int mouseY) {
        Theme t = Theme.get();
        settingsAnim += ((settingsView ? 1f : 0f) - settingsAnim) * (float) (1.0 - Math.exp(-frameDt * 18.0));
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
        F_TITLE.drawString(m, "Темы", px + 14, py + 12, t.text());
        F_TINY.drawString(m, "Применяется сразу — кликните свотч Custom, чтобы перекрасить",
                px + 14, py + 30, t.textSecondary());

        float ry = py + 48;
        for (Theme th : themes) {
            boolean isActive = th.id().equals(t.id());
            boolean hov = UiRender.inside(mouseX, mouseY, px + 12, ry, pw - 24, 32);
            UiRender.rect(m, px + 12, ry, pw - 24, 32, 6,
                    isActive ? UiRender.withAlpha(th.accent(), 45) : (hov ? t.cardHover() : t.card()));
            if (isActive) {
                UiRender.outline(m, px + 12, ry, pw - 24, 32, 6, t.accent(), 1);
            }
            F_BODY.drawString(m, th.name(), px + 22, centerBoxY(F_BODY, th.name(), ry, 32), t.text());

            if (th.id().equals("custom")) {
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

        F_TITLE.drawString(m, "Цвет слота", pickX + 10, pickY + 9, t.text());

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
        F_TINY.drawString(m, Theme.CUSTOM_SLOT_NAMES[slotIdx], pickX + 10 + 5 * 31 - 5, sy + 4, t.accent());

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

        float fy = pickY + 176;
        int color = Theme.customSlot(pickerSlot);
        UiRender.rect(m, pickX + 10, fy + 2, 16, 16, 4, color);
        UiRender.outline(m, pickX + 10, fy + 2, 16, 16, 4, t.outline(), 1);
        String hex = String.format("#%06X", color & 0xFFFFFF);
        F_TINY.drawString(m, hex, pickX + 32, fy + 5, t.textSecondary());

        float btnW = 64, btnX = pickX + pickW - 10 - btnW;
        boolean doneHov = UiRender.inside(mouseX, mouseY, btnX, fy, btnW, 20);
        UiRender.rect(m, btnX, fy, btnW, 20, 5, doneHov ? UiRender.mulAlpha(t.accent(), 0.85f) : t.accent());
        String done = "Готово";
        F_SEMI.drawString(m, done, btnX + (btnW - F_SEMI.getStringWidth(done)) / 2f,
                centerBoxY(F_SEMI, done, fy, 20), 0xFFFFFFFF);
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

    // ------------------------------------------------------------------ helpers

    /** Eased 0..1 toggle state shared by module and setting pills. */
    private float toggleEase(String key, boolean on) {
        float target = on ? 1f : 0f;
        float k = toggleAnim.getOrDefault(key, target);
        if (k != target) {
            k += (target - k) * (float) (1.0 - Math.exp(-frameDt * 22.0));
            if (Math.abs(target - k) < 0.02f) {
                k = target;
            }
            toggleAnim.put(key, k);
        }
        return k;
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

    /** Key name for the bind chip; module binds use GLFW keycodes. */
    private String keyLabel(int key) {
        if (key < 0) {
            return "";
        }
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) {
            return name.toUpperCase(Locale.ROOT);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            default -> "K" + key;
        };
    }

    /** Ease-out-back — the LoxeDLC window pop (slight overshoot, no bounce). */
    private static float easeOutBack(float p) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float x = clamp01(p) - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    /**
     * Draw-y that visually centres a string inside a box of height {@code boxH} whose
     * top is {@code boxTop}. The font renders at 0.5 scale with a -3 translate, so the
     * naive {@code boxTop + (boxH - h)/2} leaves text sitting high in its box.
     */
    private static float centerBoxY(FontRenderer f, String s, float boxTop, float boxH) {
        float cy = boxTop + boxH / 2f;
        return cy + 3f - f.getStringHeight(s) * 0.25f;
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
        // MMB drag of the window, LoxeDLC-style
        if (button == 2 && animP > 0.5f
                && tmx >= winX - 20 && tmx <= winX + WIN_W + 20 && tmy >= winY && tmy <= winY + WIN_H) {
            dragging = true;
            dragOffX = tmx - winX;
            dragOffY = tmy - winY;
            return true;
        }
        // right-click on a card opens its settings popup (LoxeDLC ModuleSettingsWindow)
        if (button == 1) {
            if (closing || animP < 0.95f || settingsView) {
                return true;
            }
            if (dropdown != null) {
                dropdown = null;
                return true;
            }
            if (activeCategory == Category.CONFIGS) {
                popupModule = null;
                return true;
            }
            CardBounds hit = hitCard();
            if (hit != null) {
                if (popupModule == hit.module) {
                    popupModule = null;
                    capturing = null;
                } else {
                    openPopup(hit.module);
                }
                return true;
            }
            popupModule = null;
            capturing = null;
            return true;
        }
        if (button != 0) {
            return false;
        }
        // no input while the window is still animating in or out
        if (closing || animP < 0.95f) {
            return true;
        }

        if (settingsView) {
            if (UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize)) {
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
            if (UiRender.inside(tmx, tmy, dropX, dropY, dropW, totalH)) {
                int idx = (int) ((tmy - dropY - 3) / DROP_ROW_H);
                if (idx >= 0 && idx < opts.length) {
                    dropdown.setIndex(idx);
                }
            }
            dropdown = null;
            return true;
        }

        // category tabs on the rail
        for (TabRect tr : tabs) {
            if (UiRender.inside(tmx, tmy, tr.x, tr.y, tr.w, tr.h)) {
                if (tr.category != activeCategory) {
                    activeCategory = tr.category;
                    searchFocused = false;
                    configNameFocused = false;
                    dropdown = null;
                }
                return true;
            }
        }

        // gear → theme overlay
        if (UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize)) {
            settingsView = !settingsView;
            return true;
        }

        if (activeCategory == Category.CONFIGS) {
            return clickConfigs(mouseX, mouseY);
        }

        // search field (RENDER/MISC)
        if (activeCategory == Category.RENDER || activeCategory == Category.MISC) {
            if (UiRender.inside(tmx, tmy, searchX, searchY, searchW, searchH)) {
                searchFocused = true;
                return true;
            }
            searchFocused = false;
        }

        // settings popup rows first — they sit above the grid
        if (popupModule != null) {
            if (UiRender.inside(tmx, tmy, popX + popW - 18, popY + 4, 16, 16)) {
                popupModule = null;
                dropdown = null;
                capturing = null;
                return true;
            }
            for (SettingBounds sb : popRows) {
                if (!UiRender.inside(tmx, tmy, sb.x, sb.y, sb.w, sb.h)) {
                    continue;
                }
                Setting s = sb.setting;
                if (s instanceof BooleanSetting b) {
                    b.toggle();
                    return true;
                }
                if (s instanceof DoubleSetting d) {
                    dragging2 = d;
                    dragTrackX = sb.x + 26;
                    dragTrackW = sb.w - 34;
                    d.set(d.fromFraction((float) ((tmx - dragTrackX) / dragTrackW)));
                    return true;
                }
                if (s instanceof ModeSetting md) {
                    float fw = 60;
                    float px2 = sb.x + sb.w - 8 - fw;
                    if (UiRender.inside(tmx, tmy, px2, sb.y + 3, fw, 14)) {
                        openDropdown(md, px2 - 4, sb.y + 18, fw + 8);
                        return true;
                    }
                }
                if (s instanceof KeySetting k2) {
                    float fw = 60;
                    float px2 = sb.x + sb.w - 8 - fw;
                    if (UiRender.inside(tmx, tmy, px2, sb.y + 3, fw, 14)) {
                        capturing = k2;
                        return true;
                    }
                }
            }
            // click outside the popup closes it
            if (!UiRender.inside(tmx, tmy, popX, popY, popW, popH)) {
                popupModule = null;
                dropdown = null;
                capturing = null;
                return true;
            }
        }

        // module cards — pill / bind chip / toggle
        CardBounds hit = hitCard();
        if (hit != null) {
            ModuleStub mod = hit.module;
            float y = hit.y - scrollByCat.getOrDefault(activeCategory, 0f);
            // toggle pill on the right of the bottom row
            if (UiRender.inside(tmx, tmy, hit.x + hit.w - 26, y + hit.h - 16, 20, 12)) {
                mod.toggle();
                NotificationManager.moduleToggle(mod.name(), mod.enabled());
                return true;
            }
            // bind chip → capture a key
            String bindName = keyLabel(mod.bind());
            float chipW = Math.max(12f, F_BODY.getStringWidth(bindName)) + 8;
            float chipX = hit.x + hit.w - 32 - chipW;
            if (UiRender.inside(tmx, tmy, chipX, y + hit.h - 16, chipW, 12)) {
                capturingMod = mod;
                return true;
            }
            // anywhere else on the card toggles the module
            mod.toggle();
            NotificationManager.moduleToggle(mod.name(), mod.enabled());
            return true;
        }

        // left-click on empty grid space closes a lingering popup
        if (popupModule != null) {
            popupModule = null;
            capturing = null;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Card under the transformed mouse inside the visible grid band, or null. */
    private CardBounds hitCard() {
        float scroll = scrollByCat.getOrDefault(activeCategory, 0f);
        float top = winY + GRID_TOP;
        float bottom = top + viewportH;
        for (CardBounds cb : layout) {
            float y = cb.y - scroll;
            if (y + cb.h <= top || y >= bottom) {
                continue;
            }
            if (UiRender.inside(tmx, tmy, cb.x, y, cb.w, cb.h)) {
                return cb;
            }
        }
        return null;
    }

    /** Opens the settings popup for a module, right of the window (clamped). */
    private void openPopup(ModuleStub mod) {
        popupModule = mod;
        dropdown = null;
        capturing = null;
        popW = 160;
        popH = 26 + mod.settings().size() * ROW_H + 10;
        popX = winX + WIN_W + RAIL_GAP;
        if (popX + popW > width - 8) {
            popX = winX - popW - RAIL_GAP;
        }
        if (popX < 8) {
            popX = Math.min(winX + WIN_W + RAIL_GAP, Math.max(8, width - popW - 8));
        }
        popY = Math.max(8, Math.min(winY, height - popH - 8));
    }

    /** Right-click opens the settings popup; processed before the LMB branch. */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 2) {
            dragging = false;
            int cw = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int ch = MinecraftClient.getInstance().getWindow().getScaledHeight();
            offXP = (winX + WIN_W / 2f) / cw;
            offYP = (winY + WIN_H / 2f) / ch;
            return true;
        }
        pickerDrag = 0;
        dragging2 = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 2) {
            winX = (float) (tmx - dragOffX);
            winY = (float) (tmy - dragOffY);
            return true;
        }
        if (pickerDrag != 0) {
            pickerPick(mouseX, mouseY);
            return true;
        }
        if (dragging2 != null) {
            dragging2.set(dragging2.fromFraction((float) ((tmx - dragTrackX) / dragTrackW)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsView) {
            return false;
        }
        double tx = (mouseX - (winX + WIN_W / 2f)) / (0.5f + 0.5f * animP) + (winX + WIN_W / 2f);
        double ty = (mouseY - (winY + WIN_H / 2f)) / (0.5f + 0.5f * animP) + (winY + WIN_H / 2f);
        dropdown = null;
        if (activeCategory == Category.CONFIGS) {
            float maxScroll = Math.max(0, (float) Math.ceil(cfgTotal / 2.0) * 48 - cfgListH);
            cfgScroll = Math.max(0, Math.min(maxScroll, cfgScroll - (float) (verticalAmount * 24)));
            return true;
        }
        // only inside the grid band
        if (tx < winX || tx > winX + WIN_W || ty < winY + GRID_TOP || ty > winY + WIN_H - 4) {
            return true;
        }
        float maxScroll = Math.max(0, contentHeight - viewportH);
        float cur = scrollByCat.getOrDefault(activeCategory, 0f);
        scrollByCat.put(activeCategory, Math.max(0, Math.min(maxScroll, cur + (float) verticalAmount * 24)));
        return true;
    }

    private boolean clickConfigs(double mouseX, double mouseY) {
        if (UiRender.inside(tmx, tmy, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH)) {
            configNameFocused = true;
            return true;
        }
        configNameFocused = false;
        float saveW = 52;
        float saveX = winX + WIN_W - 16 - saveW;
        if (UiRender.inside(tmx, tmy, saveX, cfgFieldY, saveW, cfgFieldH)) {
            if (ConfigManager.saveConfig(configName)) {
                NotificationManager.configEvent(configName, "saved");
            }
            configName = "";
            return true;
        }
        for (CfgRow r : cfgRows) {
            if (UiRender.inside(tmx, tmy, r.loadX, r.btnY, 16, 12)) {
                if (ConfigManager.loadConfig(r.name)) {
                    NotificationManager.configEvent(r.name, "loaded");
                }
                return true;
            }
            if (UiRender.inside(tmx, tmy, r.delX, r.btnY, 16, 12)) {
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
        dropX = x;
        dropW = w;
        dropY = y;
        dropdown = md;
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

    /** Module waiting for a key capture on its bind chip. */
    private ModuleStub capturingMod;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // right-click on a card opens its settings popup
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
        if (capturingMod != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturingMod = null;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                capturingMod.bind(-1);
                capturingMod = null;
            } else {
                capturingMod.bind(keyCode);
                capturingMod = null;
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (pickerOpen) {
                pickerOpen = false;
                pickerDrag = 0;
                return true;
            }
            if (settingsView) {
                settingsView = false;
                return true;
            }
            if (popupModule != null) {
                popupModule = null;
                dropdown = null;
                capturing = null;
                return true;
            }
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
            capturingMod = null;
            closeStart = System.currentTimeMillis();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
