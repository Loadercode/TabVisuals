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
 *
 * <p>Animation model: every easing is FPS-independent — a value approaches its
 * target with {@code 1 - exp(-frameDt * k)} (see {@link #approach}). The panel
 * fades + slightly scales up on open; rail tabs slide an accent indicator and
 * ease their icon colour; module cards stagger in (slide up + fade) on a
 * category switch, ease their hover tone and sink under a held click; the
 * settings popup slides/grows in and mirrors that on close; pills, slider
 * fills and dropdowns ease their state.
 */
public final class ClickGuiScreen extends Screen {

    // --- fonts (LoxeDLC sizes) ---
    private static final FontRenderer F_TITLE = Fonts.getSize(15, Fonts.Type.DEFAULT);
    private static final FontRenderer F_HEAD = Fonts.getSize(15, Fonts.Type.SEMI);
    private static final FontRenderer F_BODY = Fonts.getSize(12, Fonts.Type.REGULAR);
    private static final FontRenderer F_SEMI = Fonts.getSize(12, Fonts.Type.SEMI);
    private static final FontRenderer F_BOLD = Fonts.getSize(12, Fonts.Type.BOLD);
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
    /** Panel scale floor of the open animation — a slight scale-up, not a zoom. */
    private static final float OPEN_MIN = 0.88f;
    private static final float RAIL_W = 42.5f;
    private static final float RAIL_GAP = 12f;
    private static final float TAB = 20f;
    private static final float TAB_SP = 12f;
    private static final float GRID_X = 12f;
    private static final float GRID_TOP = 35f;
    private static final float CARD_W = 182f;
    private static final float CARD_GAP = 9f;
    private static final float DROP_ROW_H = 12f;

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
    private boolean closing;
    private boolean exited;
    /**
     * Eased open progress 0..1: drives the whole-panel fade + slight scale-up
     * and gates input while the window is still settling. A fresh screen starts
     * at 0, so every open replays the entrance.
     */
    private float openT;
    /** Frame delta (seconds) for FPS-independent easing (1 - exp(-dt * k)). */
    private long lastFrameMs;
    private float frameDt = 1f / 60f;
    /** Theme overlay (gear) state. */
    private boolean settingsView;
    private float settingsAnim;
    /** Entrance ease of the module-settings popup (0..1). */
    private float popupAnim = 1f;
    /** The popup plays the same ease in reverse before it disappears. */
    private boolean popupClosing;
    /** Seconds since the last category switch — staggers the card entrance. */
    private float gridT;
    private Category lastCat;
    /** Global fade of the open animation (window, rail, header). */
    private float openFade = 1f;

    /** Smooth toggle knobs: module/setting id → eased 0..1 state. */
    private final Map<String, Float> toggleAnim = new HashMap<>();
    /** Eased hover state per module card. */
    private final Map<String, Float> hoverAnim = new HashMap<>();
    /** Eased fill fraction of a slider row, keyed by setting id. */
    private final Map<String, Float> sliderAnim = new HashMap<>();
    /** Eased selection per rail tab (drives the icon colour). */
    private final Map<Category, Float> tabSelAnim = new EnumMap<>(Category.class);
    /** Y of the sliding rail indicator — eases between tab positions. */
    private float tabIndY = -1f;
    /** Search field focus glow 0..1. */
    private float searchFocusAnim;
    /** Pop-in ease of the mode dropdown. */
    private float dropAnim;
    /** Module id under the held LMB + its press depth 0..1. */
    private String pressedId;
    /** Last pressed card — keeps the release sink easing out on the right card. */
    private String lastPressedId;
    private float pressAnim;

    // --- layout cache (from the last render pass) ---
    private final List<TabRect> tabs = new ArrayList<>();
    private final List<CardBounds> layout = new ArrayList<>();
    private final List<ThemeRow> themeRows = new ArrayList<>();
    private final List<CfgRow> cfgRows = new ArrayList<>();
    private final ScissorAssist scissor = new ScissorAssist();
    private float contentHeight;
    private float viewportH;
    /** Eased counterpart of the current category scroll (draw + hit-test). */
    private float smoothScroll;
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
    private float cfgScroll, cfgSmooth, cfgListH;
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
        if (lastCat != activeCategory) {
            lastCat = activeCategory;
            gridT = 0f;
            // the entrance carries the motion — don't also slide the whole grid
            smoothScroll = scrollByCat.getOrDefault(activeCategory, 0f);
            cfgSmooth = cfgScroll;
        }
        gridT += frameDt;
        pressAnim = approach(pressAnim, pressedId != null ? 1f : 0f, 26f);
        // dt-eased open/close: the whole panel fades and slightly scales up/down
        openT = approach(openT, closing ? 0f : 1f, closing ? 15f : 11f);
        if (closing && openT < 0.02f) {
            if (!exited) {
                exited = true;
                MinecraftClient.getInstance().setScreen(null);
            }
            return;
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

        openFade = clamp01(openT);
        float sc = openScale();
        tmx = (mouseX - (winX + WIN_W / 2f)) / sc + (winX + WIN_W / 2f);
        tmy = (mouseY - (winY + WIN_H / 2f)) / sc + (winY + WIN_H / 2f);

        // full-frame dim grows in with the window (LoxeDLC: alpha 100 of 255)
        UiRender.rect(m, 0, 0, width, height, 0, UiRender.mulAlpha(0xFF000000, 0.39f * openFade));

        m.push();
        float scx = winX + WIN_W / 2f, scy = winY + WIN_H / 2f;
        m.translate(scx, scy, 0f);
        m.scale(sc, sc, 1f);
        m.translate(-scx, -scy, 0f);

        renderWindow(m, t, openFade);
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
        int headCol = UiRender.mulAlpha(t.text(), openA);
        if (activeCategory != Category.CONFIGS) {
            F_CAT.drawString(m, String.valueOf(activeCategory.icon()), winX + GRID_X, winY + 10, headCol);
        } else {
            F_CAT.drawString(m, "H", winX + GRID_X, winY + 10, headCol);
        }
        F_TITLE.drawString(m, "• " + activeCategory.displayName(), winX + GRID_X + 10, winY + 12, headCol);
        UiRender.rect(m, winX + GRID_X, winY + 28, WIN_W - GRID_X, 0.5f, 0,
                UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 140), openA));

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
        UiRender.rect(m, railX, railY, RAIL_W, railH, 8, UiRender.mulAlpha(t.panel(), 0.98f * openFade));
        UiRender.outline(m, railX, railY, RAIL_W, railH, 8, UiRender.mulAlpha(t.outline(), openFade), 1f);

        // logo box at the top of the rail — brand logo cropped to the TV mark
        float logoBoxX = railX + (RAIL_W - TAB) / 2f;
        float logoBoxY = railY + 10;
        UiRender.rect(m, logoBoxX, logoBoxY, TAB, TAB, 5, UiRender.mulAlpha(t.card(), openFade));
        UiRender.outline(m, logoBoxX, logoBoxY, TAB, TAB, 5, UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 160), openFade), 1f);
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

        // category tabs: a single accent indicator slides between them, and each
        // tab's icon colour eases toward the accent once it becomes active
        tabs.clear();
        float tabX = railX + (RAIL_W - TAB) / 2f;
        float firstTabY = railY + 40;
        int activeIdx = Math.max(0, cats.indexOf(activeCategory));
        float activeTabY = firstTabY + activeIdx * (TAB + TAB_SP);
        if (tabIndY < 0f) {
            tabIndY = activeTabY;
        }
        tabIndY = approach(tabIndY, activeTabY, 15f);
        UiRender.rect(m, tabX, tabIndY, TAB, TAB, 5, UiRender.mulAlpha(t.accent(), 70f / 255f * openFade));
        UiRender.outline(m, tabX, tabIndY, TAB, TAB, 5, UiRender.mulAlpha(t.accent(), 150f / 255f * openFade), 1f);

        float ty = firstTabY;
        for (Category c : cats) {
            float tx = tabX;
            boolean isActive = c == activeCategory;
            boolean hover = UiRender.inside(tmx, tmy, tx, ty, TAB, TAB);
            if (!isActive && hover) {
                UiRender.rect(m, tx, ty, TAB, TAB, 4, UiRender.mulAlpha(t.cardHover(), openFade));
            }
            float sel = approach(tabSelAnim.getOrDefault(c, 0f), isActive ? 1f : 0f, 13f);
            tabSelAnim.put(c, sel);
            int col = UiRender.mix(UiRender.mix(t.textSecondary(), t.text(), hover ? 1f : 0f), t.accent(), sel);
            String glyph = String.valueOf(c.icon());
            F_CAT.drawCenteredString(m, glyph, tx + TAB / 2.0,
                    centerBoxY(F_CAT, glyph, ty, TAB), UiRender.mulAlpha(col, openFade));
            tabs.add(new TabRect(c, tx, ty, TAB, TAB));
            ty += TAB + TAB_SP;
        }

        // gear at the bottom of the rail — the theme overlay
        gearX = railX + (RAIL_W - gearSize) / 2f;
        gearY = railY + railH - 26;
        boolean gearHover = UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize);
        int gearBg = settingsView ? UiRender.mulAlpha(t.accent(), 0.85f) : (gearHover ? t.cardHover() : t.card());
        UiRender.rect(m, gearX, gearY, gearSize, gearSize, 5, UiRender.mulAlpha(gearBg, openFade));
        int glyph = settingsView ? 0xFFFFFFFF : t.textSecondary();
        UiRender.outline(m, gearX + 5, gearY + 5, 10, 10, 5, UiRender.mulAlpha(glyph, openFade), 1.3f);
        UiRender.rect(m, gearX + 8, gearY + 8, 4, 4, 2, UiRender.mulAlpha(glyph, openFade));
    }

    /** Two-column card grid (LoxeDLC CategoryComponent): top-anchored, scrolls down. */
    private void renderModules(MatrixStack m) {
        Theme t = Theme.get();

        // search field — RENDER/MISC only, like LoxeDLC; the outline eases in on focus
        if (activeCategory == Category.RENDER || activeCategory == Category.MISC) {
            searchFocusAnim = approach(searchFocusAnim, searchFocused ? 1f : 0f, 14f);
            searchX = winX + WIN_W - 16 - searchW;
            searchY = winY + 7.5f;
            float sa = openFade;
            UiRender.rect(m, searchX, searchY, searchW, searchH, 3, UiRender.mulAlpha(t.card(), sa));
            int searchStroke = UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(), searchFocusAnim);
            UiRender.outline(m, searchX, searchY, searchW, searchH, 3, UiRender.mulAlpha(searchStroke, sa), 1);
            boolean empty = search.isEmpty() && !searchFocused;
            F_SEARCH.drawString(m, "U", searchX + searchW - 13,
                    centerBoxY(F_SEARCH, "U", searchY, searchH),
                    UiRender.mulAlpha(empty ? t.textSecondary() : t.text(), sa));
            UiRender.rect(m, searchX + searchW - 19, searchY + 3.5f, 0.5f, 8, 0,
                    UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 160), sa));
            F_TINY.drawString(m, empty ? "Поиск" : search + (searchFocused ? "|" : ""),
                    searchX + 6, centerBoxY(F_TINY, "Aa", searchY, searchH),
                    UiRender.mulAlpha(empty ? t.textSecondary() : t.text(), sa));
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
        // eased scroll: drawing and hit-testing share the same smoothed value
        smoothScroll = approach(smoothScroll, scroll, 13f);

        Matrix4f clipMatrix = m.peek().getPositionMatrix();
        scissor.push(clipMatrix, winX + GRID_X - 4, winY + GRID_TOP, WIN_W - GRID_X - 6, viewportH);
        for (int ci = 0; ci < layout.size(); ci++) {
            CardBounds cb = layout.get(ci);
            // staggered entrance after a category switch / on open: each card
            // trails the previous one, sliding up and fading in
            float p = clamp01((gridT - ci * 0.035f) / 0.26f);
            float ce = 1f - (1f - p) * (1f - p) * (1f - p);
            float enter = (1f - ce) * 10f;
            m.push();
            m.translate(0, enter, 0);
            drawCard(m, cb, smoothScroll, ce);
            m.pop();
        }
        scissor.pop();

        // scrollbar on the right edge
        if (contentHeight > viewportH && viewportH > 10f) {
            float barX = winX + WIN_W - 8f;
            float thumbH = Math.max(18f, viewportH * viewportH / contentHeight);
            float thumbY = winY + GRID_TOP + (viewportH - thumbH) * (smoothScroll / Math.max(1f, maxScroll));
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
        int lines = Math.max(1, wrapDesc(mod.description(), CARD_W - 30).size());
        return 47 + (lines - 1) * 8;
    }

    private void drawCard(MatrixStack m, CardBounds cb, float scroll, float alpha) {
        Theme t = Theme.get();
        ModuleStub mod = cb.module;
        float x = cb.x;
        float y = cb.y - scroll;
        float w = cb.w;
        float h = cb.h;
        String id = mod.id();

        float k = toggleEase("m:" + id, mod.enabled());
        float hoverK = approach(hoverAnim.getOrDefault(id, 0f),
                UiRender.inside(tmx, tmy, x, y, w, h) ? 1f : 0f, 16f);
        hoverAnim.put(id, hoverK);
        float pressK = (id.equals(pressedId) || id.equals(lastPressedId)) ? pressAnim : 0f;

        // hover lifts the card toward the hover tone; a held click sinks it a hair
        m.push();
        if (pressK > 0.001f) {
            float sink = 1f - 0.015f * pressK;
            float cx = x + w / 2f, cy = y + h / 2f;
            m.translate(cx, cy, 0f);
            m.scale(sink, sink, 1f);
            m.translate(-cx, -cy, 0f);
        }

        int fill = UiRender.mix(t.card(), t.cardHover(), hoverK);
        fill = UiRender.mix(fill, UiRender.mix(fill, t.accent(), 0.14f), k);
        UiRender.rect(m, x, y, w, h, 5, UiRender.mulAlpha(fill, alpha));
        float glow = Math.max(hoverK, k * 0.7f);
        int stroke = UiRender.mix(UiRender.withAlpha(t.outline(), 110), UiRender.withAlpha(t.accent(), 150), glow);
        UiRender.outline(m, x, y, w, h, 5, UiRender.mulAlpha(stroke, alpha), 1f);

        // name: bullet in the glyph gutter, text on the description's left
        // column (x+15) — name and description read as one aligned block
        int nameCol = UiRender.mulAlpha(UiRender.mix(UiRender.withAlpha(t.text(), 170), t.text(), k), alpha);
        F_TITLE.drawString(m, "•", x + 6.5f, y + 6, nameCol);
        F_TITLE.drawString(m, mod.name(), x + 15, y + 6, nameCol);

        // description: first line tagged with the info glyph, continuations aligned
        // to the same text column so the block reads straight
        List<String> lines = wrapDesc(mod.description(), w - 30);
        int descCol = UiRender.mulAlpha(UiRender.withAlpha(t.textSecondary(), 186), alpha);
        int glyphCol = UiRender.mulAlpha(t.textSecondary(), alpha);
        float dy = y + 19;
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                F_ICON_C.drawString(m, "C", x + 6.5f, dy, glyphCol);
            }
            F_BODY.drawString(m, lines.get(i), x + 15, dy, descCol);
            dy += 8;
        }

        // separator above the bottom row
        float sepY = y + h - 20;
        UiRender.rect(m, x, sepY, w, 0.5f, 0, UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 120), alpha));

        // bottom row: settings glyphs left, bind chip, toggle pill right
        float rowY = y + h - 16;
        F_ICON_A.drawString(m, "A", x + 7, rowY - 4, glyphCol);
        if (!mod.settings().isEmpty()) {
            F_ICON_B.drawString(m, "B", x + 20, rowY - 3, glyphCol);
        }

        // toggle pill (LoxeDLC StatusRender): 16x8 track, the accent fill grows
        // in with the eased state, 9x9 knob slides after it
        float togX = x + w - 24;
        float togY = rowY + 2;
        UiRender.rect(m, togX, togY, 16, 8, 4, UiRender.mulAlpha(UiRender.withAlpha(t.text(), 40), alpha));
        UiRender.rect(m, togX, togY, Math.max(3f, 16f * k), 8, 4, UiRender.mulAlpha(t.accent(), alpha));
        float knobX = togX - 0.5f + k * 8;
        UiRender.rect(m, knobX, togY - 0.5f, 9, 9, 4.5f, UiRender.mulAlpha(t.card(), alpha));
        UiRender.outline(m, knobX, togY - 0.5f, 9, 9, 4.5f, UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 220), alpha), 1.2f);

        // bind chip: key label, or the chain glyph when unbound
        String bindName = capturingMod == mod ? "..." : keyLabel(mod.bind());
        float chipW = Math.max(12f, F_BODY.getStringWidth(bindName)) + 8;
        float chipX = x + w - 32 - chipW;
        UiRender.rect(m, chipX, rowY + 1, chipW, 10, 3, UiRender.mulAlpha(UiRender.withAlpha(t.text(), 80), alpha));
        if (mod.bind() < 0 && capturingMod != mod) {
            F_ICON_B.drawString(m, "G", chipX + chipW / 2f - 5, rowY - 1, glyphCol);
        } else {
            F_BODY.drawString(m, bindName, chipX + 4, rowY + 2, glyphCol);
        }
        m.pop();
    }

    /** Right-click settings popup (LoxeDLC ModuleSettingsWindow). */
    private void renderPopup(MatrixStack m, Theme t) {
        // ease the entrance (slide-in + grow) and the mirrored exit
        popupAnim = approach(popupAnim, popupClosing ? 0f : 1f, 20f);
        if (popupClosing && popupAnim < 0.02f) {
            popupModule = null;
            popupClosing = false;
            dropdown = null;
            capturing = null;
            return;
        }
        float e = easeOutBack(popupAnim);
        float slide = (1f - e) * 14f;
        float a = clamp01(popupAnim);

        popRows.clear();
        List<Setting> settings = popupModule.settings();
        float rowsH = 0f;
        for (Setting s : settings) {
            rowsH += rowHeight(s);
        }
        popH = 24 + rowsH + 10;

        float anchorX = popX;
        float anchorY = popY + popH / 2f;
        m.push();
        m.translate(anchorX + slide, anchorY, 0f);
        float psc = 0.93f + 0.07f * e;
        m.scale(psc, psc, 1f);
        m.translate(-anchorX, -anchorY, 0f);

        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f * a);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, popX, popY, popW, popH)
                    .round(8)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, popX, popY, popW, popH, 8, UiRender.mulAlpha(t.panel(), 0.98f * a));
        UiRender.outline(m, popX, popY, popW, popH, 8, UiRender.mulAlpha(t.outline(), a), 1f);

        // header: "<Module> Settings" + close glyph, hairline underneath (y+22)
        F_HEAD.drawString(m, popupModule.name() + " Settings", popX + 10, popY + 8,
                UiRender.mulAlpha(t.text(), a));
        F_ICONS.drawString(m, "K", popX + popW - 15, popY + 7,
                UiRender.mulAlpha(t.textSecondary(), 0.6f * a));
        UiRender.rect(m, popX + 10, popY + 22, popW - 20, 0.5f, 0,
                UiRender.mulAlpha(t.text(), 0.2f * a));

        float ry = popY + 24;
        for (Setting s : settings) {
            float rh = rowHeight(s);
            popRows.add(new SettingBounds(s, popX, ry, popW, rh));
            drawPopupRow(m, t, s, popX, ry, rh);
            ry += rh;
        }
        m.pop();
    }

    /** LoxeDLC row heights: checkboxes are compact, the rest sit on a 22px line. */
    private static float rowHeight(Setting s) {
        return s instanceof BooleanSetting ? 15f : 22f;
    }

    private void drawPopupRow(MatrixStack m, Theme t, Setting s, float x, float y, float h) {
        float a = clamp01(popupAnim);
        int textCol = UiRender.mulAlpha(t.text(), a);
        int dimCol = UiRender.mulAlpha(t.textSecondary(), 0.3f * a);
        if (s instanceof BooleanSetting b) {
            F_ICON_C.drawString(m, "K", x + 6, centerBoxY(F_ICON_C, "K", y, h), dimCol);
            F_BODY.drawString(m, b.name(), x + 20, centerBoxY(F_BODY, b.name(), y, h), textCol);
            float k = toggleEase("s:" + System.identityHashCode(b), b.get());
            float togX = x + popW - 26;
            float togY = y + h / 2f - 4;
            UiRender.rect(m, togX, togY, 16, 8, 4, UiRender.mulAlpha(UiRender.withAlpha(t.text(), 40), a));
            UiRender.rect(m, togX, togY, Math.max(3f, 16f * k), 8, 4, UiRender.mulAlpha(t.accent(), a));
            float knobX = togX - 0.5f + k * 8;
            UiRender.rect(m, knobX, togY - 0.5f, 9, 9, 4.5f, UiRender.mulAlpha(t.card(), a));
            UiRender.outline(m, knobX, togY - 0.5f, 9, 9, 4.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 220), a), 1.2f);
        } else if (s instanceof DoubleSetting d) {
            F_ICON_C.drawString(m, "H", x + 6, centerBoxY(F_ICON_C, "H", y, h), dimCol);
            F_BODY.drawString(m, d.name(), x + 20, centerBoxY(F_BODY, d.name(), y, h), textCol);
            String val = d.format();
            F_BOLD.drawString(m, val, x + popW - 9 - F_BOLD.getStringWidth(val), y + 3,
                    UiRender.mulAlpha(t.accent(), a));
            // slider: 65px track bottom-right, value above it (LoxeDLC slider row)
            float trackW = 65f;
            float trackX = x + popW - 9 - trackW;
            float trackY = y + 15;
            String key = "sd:" + System.identityHashCode(d);
            float frac = approach(sliderAnim.getOrDefault(key, d.fraction()), d.fraction(), 18f);
            sliderAnim.put(key, frac);
            UiRender.rect(m, trackX, trackY, trackW, 3, 1.5f, UiRender.mulAlpha(UiRender.withAlpha(t.text(), 30), a));
            UiRender.rect(m, trackX, trackY, Math.max(3f, trackW * frac), 3, 1.5f, UiRender.mulAlpha(t.accent(), a));
            float thX = trackX + trackW * frac - 3.5f;
            UiRender.rect(m, thX, trackY - 2f, 7, 7, 3.5f, UiRender.mulAlpha(t.accent(), a));
            UiRender.outline(m, thX, trackY - 2f, 7, 7, 3.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(0xFFFFFFFF, 170), a), 1.1f);
        } else if (s instanceof ModeSetting md) {
            F_ICON_C.drawString(m, "J", x + 6, centerBoxY(F_ICON_C, "J", y, h), dimCol);
            F_BODY.drawString(m, md.name(), x + 20, centerBoxY(F_BODY, md.name(), y, h), textCol);
            drawSelectField(m, t, x + popW - 75, y + (h - 14) / 2f, 66,
                    md.get(), dropdown == md, a);
        } else if (s instanceof KeySetting k2) {
            F_ICON_C.drawString(m, "L", x + 6, centerBoxY(F_ICON_C, "L", y, h), dimCol);
            F_BODY.drawString(m, "Бинд", x + 20, centerBoxY(F_BODY, "Бинд", y, h), textCol);
            drawSelectField(m, t, x + popW - 75, y + (h - 14) / 2f, 66,
                    capturing == k2 ? "..." : k2.label(), capturing == k2, a);
        }
    }

    /** 66x14 rounded select field with a chevron (LoxeDLC SelectedButton). */
    private void drawSelectField(MatrixStack m, Theme t, float fx, float fy, float fw, String value,
                                 boolean active, float a) {
        boolean hov = UiRender.inside(tmx, tmy, fx, fy, fw, 14);
        int bg = UiRender.mix(hov ? t.cardHover() : t.card(), t.accent(), active ? 0.18f : 0f);
        UiRender.rect(m, fx, fy, fw, 14, 3, UiRender.mulAlpha(bg, a));
        UiRender.outline(m, fx, fy, fw, 14, 3,
                UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(), active ? 1f : 0f), 1);
        F_BODY.drawStringWithScroll(m, value, fx + 5, centerBoxY(F_BODY, value, fy, 14), fw - 16,
                UiRender.mulAlpha(t.text(), a));
        float chx = fx + fw - 10, chy = fy + 6;
        int chev = UiRender.mulAlpha(t.textSecondary(), a);
        UiRender.rect(m, chx, chy, 5, 1f, 0.5f, chev);
        UiRender.rect(m, chx + 1, chy + 1.2f, 3, 1f, 0.5f, chev);
    }

    /** Mode dropdown list anchored under the select field (LoxeDLC SelectComponent). */
    private void renderDropdown(MatrixStack m, Theme t) {
        String[] opts = dropdown.options();
        float totalH = opts.length * DROP_ROW_H + 6;
        dropAnim = approach(dropAnim, 1f, 22f);
        float de = 0.9f + 0.1f * easeOutBack(dropAnim);
        float a = clamp01(dropAnim);
        float dcx = dropX + dropW / 2f;
        m.push();
        m.translate(dcx, dropY, 0f);
        m.scale(de, de, 1f);
        m.translate(-dcx, -dropY, 0f);

        UiRender.rect(m, dropX, dropY, dropW, totalH, 3, UiRender.mulAlpha(t.panel(), a));
        UiRender.outline(m, dropX, dropY, dropW, totalH, 3,
                UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 155), a), 1);
        float oy = dropY + 3;
        for (int i = 0; i < opts.length; i++) {
            boolean sel = i == dropdown.index();
            boolean hov = UiRender.inside(tmx, tmy, dropX + 3, oy, dropW - 6, DROP_ROW_H);
            if (sel || hov) {
                UiRender.rect(m, dropX + 3, oy, dropW - 6, DROP_ROW_H, 3,
                        UiRender.mulAlpha(UiRender.withAlpha(t.accent(), sel ? 55 : 26), a));
            }
            F_TINY.drawString(m, opts[i], dropX + 8,
                    centerBoxY(F_TINY, opts[i], oy, DROP_ROW_H),
                    UiRender.mulAlpha(sel ? t.accent() : t.text(), a));
            oy += DROP_ROW_H;
        }
        m.pop();
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
        cfgSmooth = approach(cfgSmooth, cfgScroll, 13f);
        m.push();
        m.translate(0, -cfgSmooth, 0);
        scissor.push(clipMatrix, winX + GRID_X - 4, listTop, WIN_W - GRID_X - 4, listH);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int col = i % 2;
            float cx2 = winX + GRID_X + col * (CARD_W + CARD_GAP);
            float cy2 = listTop + (i / 2) * rowStep;
            float screenY = cy2 - cfgSmooth;
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
                cfgRows.add(new CfgRow(name, cx2, screenY, CARD_W, 40, loadX, delX, btnY - cfgSmooth));
            }
        }
        scissor.pop();
        m.pop();

        if (maxScroll > 0) {
            float barX = winX + WIN_W - 8f;
            float thumbH = Math.max(18f, listH * listH / (cfgTotal / 2f * rowStep));
            float thumbY = listTop + (listH - thumbH) * (cfgSmooth / Math.max(1f, maxScroll));
            UiRender.rect(m, barX, listTop, 3f, listH, 1.5f, UiRender.withAlpha(t.text(), 20));
            UiRender.rect(m, barX, thumbY, 3f, thumbH, 1.5f, UiRender.withAlpha(t.text(), 70));
        }
    }

    /** Theme overlay behind the gear — kept from the previous GUI. */
    private void renderSettings(MatrixStack m, int mouseX, int mouseY) {
        Theme t = Theme.get();
        settingsAnim = approach(settingsAnim, settingsView ? 1f : 0f, 18f);
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

    /** Eased approach: cur → target with an FPS-independent 1 - exp(-dt * k). */
    private float approach(float cur, float target, float k) {
        float next = cur + (target - cur) * (float) (1.0 - Math.exp(-frameDt * k));
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    /** Current panel scale of the open animation (mouse transform matches it). */
    private float openScale() {
        return OPEN_MIN + (1f - OPEN_MIN) * easeOutBack(openT);
    }

    /** Eased 0..1 toggle state shared by module and setting pills. */
    private float toggleEase(String key, boolean on) {
        float target = on ? 1f : 0f;
        float k = toggleAnim.getOrDefault(key, target);
        if (k != target) {
            k = approach(k, target, 22f);
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
        if (button == 2 && openT > 0.6f
                && tmx >= winX - 20 && tmx <= winX + WIN_W + 20 && tmy >= winY && tmy <= winY + WIN_H) {
            dragging = true;
            dragOffX = tmx - winX;
            dragOffY = tmy - winY;
            return true;
        }
        // right-click on a card opens its settings popup (LoxeDLC ModuleSettingsWindow)
        if (button == 1) {
            if (closing || openT < 0.9f || settingsView) {
                return true;
            }
            if (dropdown != null) {
                dropdown = null;
                return true;
            }
            if (activeCategory == Category.CONFIGS) {
                closePopup();
                return true;
            }
            CardBounds hit = hitCard();
            if (hit != null) {
                if (popupModule == hit.module) {
                    if (popupClosing) {
                        popupClosing = false; // reverse the exit — reopen in place
                    } else {
                        closePopup();
                    }
                } else {
                    openPopup(hit.module);
                }
                return true;
            }
            closePopup();
            return true;
        }
        if (button != 0) {
            return false;
        }
        // no input while the window is still animating in or out
        if (closing || openT < 0.9f) {
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
        if (popupModule != null && !popupClosing) {
            if (UiRender.inside(tmx, tmy, popX + popW - 19, popY + 4, 16, 16)) {
                closePopup();
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
                    // LoxeDLC slider: the 65px track is the drag zone
                    float trackX = sb.x + sb.w - 9 - 65;
                    if (UiRender.inside(tmx, tmy, trackX, sb.y + 11, 65, 10)) {
                        dragging2 = d;
                        dragTrackX = trackX;
                        dragTrackW = 65;
                        d.set(d.fromFraction((float) ((tmx - dragTrackX) / dragTrackW)));
                        return true;
                    }
                }
                if (s instanceof ModeSetting md) {
                    float fx = sb.x + sb.w - 75;
                    float fy = sb.y + (sb.h - 14) / 2f;
                    if (UiRender.inside(tmx, tmy, fx, fy, 66, 14)) {
                        openDropdown(md, fx, fy + 17, 66);
                        return true;
                    }
                }
                if (s instanceof KeySetting k2) {
                    float fx = sb.x + sb.w - 75;
                    float fy = sb.y + (sb.h - 14) / 2f;
                    if (UiRender.inside(tmx, tmy, fx, fy, 66, 14)) {
                        capturing = k2;
                        return true;
                    }
                }
            }
            // click outside the popup closes it
            if (!UiRender.inside(tmx, tmy, popX, popY, popW, popH)) {
                closePopup();
                return true;
            }
        }

        // module cards — pill / bind chip / toggle
        CardBounds hit = hitCard();
        if (hit != null) {
            ModuleStub mod = hit.module;
            pressedId = mod.id(); // press sink animation, cleared on release
            lastPressedId = pressedId;
            float y = hit.y - smoothScroll;
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
            closePopup();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Card under the transformed mouse inside the visible grid band, or null. */
    private CardBounds hitCard() {
        float scroll = smoothScroll;
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

    /** Requests the popup close; it slides/grows out before it is dropped. */
    private void closePopup() {
        if (popupModule == null || popupClosing) {
            return;
        }
        popupClosing = true;
        dropdown = null;
        capturing = null;
    }

    /** Opens the settings popup for a module, right of the window (clamped). */
    private void openPopup(ModuleStub mod) {
        popupModule = mod;
        popupAnim = 0f;
        popupClosing = false;
        dropdown = null;
        capturing = null;
        popW = 160;
        float rowsH = 0f;
        for (Setting s : mod.settings()) {
            rowsH += rowHeight(s);
        }
        popH = 24 + rowsH + 10;
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
        if (button == 0) {
            pressedId = null; // release the card press sink
        }
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
        float osc = openScale();
        double tx = (mouseX - (winX + WIN_W / 2f)) / osc + (winX + WIN_W / 2f);
        double ty = (mouseY - (winY + WIN_H / 2f)) / osc + (winY + WIN_H / 2f);
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
        dropAnim = 0f;
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
        // a settings row / module bind chip is waiting for a key
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
                closePopup();
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
            settingsView = false;
            pickerOpen = false;
            dropdown = null;
            popupModule = null; // no point animating the popup out with the panel
            popupClosing = false;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
