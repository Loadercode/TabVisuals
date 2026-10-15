// language: Java, file: ClickGuiScreen.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.hud.HudRenderer;
import com.loadercode.tabvisuals.gui.hud.NotificationManager;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.ColorSetting;
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

    // --- settings popup row metrics (LoxeDLC ModuleSettingsWindow) ---
    /** Checkbox rows are compact; every other control sits on a 22px line. */
    private static final float ROW_CHECK_H = 15f;
    private static final float ROW_H = 22f;
    /** Fixed columns of a row: glyph gutter, label column, right edge of values. */
    private static final float ROW_GLYPH_X = 6.5f;
    private static final float ROW_TEXT_X = 15f;
    private static final float ROW_RIGHT = 10f;
    /** Select field (LoxeDLC 66x14) and slider track (65px) widths. */
    private static final float FIELD_W = 66f;
    private static final float TRACK_W = 65f;
    /** Collapsible section header row height. */
    private static final float SEC_H = 16f;

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
    private final List<PopRow> popRows = new ArrayList<>();
    /** Full content height of the row stack / visible viewport of the panel. */
    private float rowsH, popViewTop, popViewH, popPanelH;
    /** Popup viewport scroll — long setting lists clip and take the wheel. */
    private float popScroll, popSmooth;
    /** Expand 0..1 of each collapsible section header (keyed per module + index). */
    private final Map<String, Float> sectionAnim = new HashMap<>();
    private final Map<String, Boolean> sectionOpen = new HashMap<>();
    /** Eased slider read-out — the number counts up/down with the fill. */
    private final Map<String, Float> valueAnim = new HashMap<>();
    /** Eased row hover tint, keyed by setting id. */
    private final Map<String, Float> rowHover = new HashMap<>();
    /** Swatch pop pulse: 1 right after its colour changed, decays back to 0. */
    private final Map<String, Float> swatchPulse = new HashMap<>();
    /** Seconds since the screen opened — drives pulses (bind capture, swatches). */
    private float animTime;

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
    /** The row the dropdown belongs to — it keeps pushing rows while it eases shut. */
    private ModeSetting dropRow;
    /** Eased height of the open dropdown: the gap the rows below are pushed into. */
    private float dropPushH;
    /** Eased hover highlight of the option list — slides between rows. */
    private float dropHoverY = -1f, dropHoverA;
    /** Generic button ease (configs / theme / picker): id → hover or press 0..1. */
    private final Map<String, Float> btnHoverAnim = new HashMap<>();
    private final Map<String, Float> btnPressAnim = new HashMap<>();
    /** Id of the generic button held down right now (release eases it back). */
    private String pressedBtn;

    // --- custom theme color picker (loxeDLC-style HSV popup) ---
    private boolean pickerOpen;
    private String pickerSlot = Theme.CUSTOM_SLOTS[0];
    /** Non-null while the picker edits a module colour row instead of a theme slot. */
    private ColorSetting pickerColor;
    private float pHue, pSat, pVal;
    /** Eased HSV read-out — follows the target smoothly when the picker is not dragged. */
    private float dHue, dSat, dVal;
    /** Pop-in ease of the picker window (0..1). */
    private float pickerAnim;
    /** 0 = none, 1 = saturation/value square, 2 = hue bar. */
    private int pickerDrag;
    private float pvX, pvY, pvW = 186, pvH = 92;
    private float hueX, hueY, hueW = 186, hueH = 10;
    private float pickX, pickY, pickW = 214, pickH;
    /** "Готово" button of the picker (hit-tested outside render too). */
    private float pickBtnX, pickBtnY, pickBtnW = 64, pickBtnH = 20;
    private final List<SwatchHit> swatchHits = new ArrayList<>();

    // --- theme overlay state (presets / rename / reset live in this screen) ---
    /** Display name of the Custom theme (renamed in-file from the theme panel). */
    private String customName = "Custom";
    private boolean renameActive;
    private String renameBuf = "";
    private float renameBlink;
    /** Eased "is the active theme" ring per theme id. */
    private final Map<String, Float> themeSelAnim = new HashMap<>();
    /** Custom palette reset (delete action): 1 → 0 while the colours tween back. */
    private boolean customResetting;
    private float customResetT;
    private final Map<String, Integer> customResetFrom = new HashMap<>();

    /** A swatch tab in the picker: a theme slot, or a preset of a module colour. */
    private record SwatchHit(String slot, ColorSetting color, int value,
                             float x, float y, float w, float h) {
    }

    private record TabRect(Category category, float x, float y, float w, float h) {
    }

    /** One laid-out popup row: a setting, or a collapsible section header. */
    private static final class PopRow {
        /** Setting behind the row; null for a section header. */
        final Setting setting;
        /** Animation key: stable per header index / per setting instance. */
        final String key;
        /** Header label; null for a normal row. */
        final String label;
        /** Settings collected under a header. */
        final int count;
        final boolean section;
        /** Top-left and width; y already carries the viewport scroll. */
        float x, y, w;
        /** Animated height — a collapsing section squeezes its children to 0. */
        float h;
        /** Fade of a child row while its section collapses (0..1). */
        float alpha;

        PopRow(Setting setting, String key, String label, int count, boolean section) {
            this.setting = setting;
            this.key = key;
            this.label = label;
            this.count = count;
            this.section = section;
        }
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

    // Configs button ids: the press sink is shared with every other button
    private static final String CFG_SAVE = "cfg:save";
    private static final String CFG_LOAD = "cfg:load:";
    private static final String CFG_DEL = "cfg:del:";

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
        animTime += frameDt;
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
            // the dropdown list is drawn inside the popup's clip, with its rows
            renderPopup(m, t);
        }

        m.pop();

        if (settingsView || settingsAnim > 0.01f) {
            renderSettings(m, mouseX, mouseY);
        }
        if (pickerOpen || pickerAnim > 0.01f) {
            renderPicker(m, mouseX, mouseY);
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

        // header: category glyph in the 13px gutter, title on the shared text column;
        // both are centred on the title's own optical centre so they read as one line
        int headCol = UiRender.mulAlpha(t.text(), openA);
        String headGlyph = activeCategory == Category.CONFIGS ? "H" : String.valueOf(activeCategory.icon());
        String headTitle = activeCategory.displayName();
        float titleY = winY + 12;
        float titleMid = titleY - 3f + F_TITLE.getStringHeight(headTitle) * 0.25f;
        FontRenderer.drawCentered(F_CAT, m, headGlyph, winX + GRID_X + 6.5f,
                centerBoxY(F_CAT, headGlyph, titleMid - 8f, 16f), headCol);
        F_TITLE.drawString(m, headTitle, winX + GRID_X + 15, titleY, headCol);
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
        F_TITLE.drawString(m, truncate(F_TITLE, mod.name(), w - 15 - 8), x + 15, y + 6, nameCol);

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

        // bottom row: settings glyphs left, bind chip, toggle pill right —
        // glyphs are centered on the same band the chip and pill sit in
        float rowY = y + h - 16;
        F_ICON_A.drawString(m, "A", x + 7, centerBoxY(F_ICON_A, "A", y + h - 20, 20), glyphCol);
        if (!mod.settings().isEmpty()) {
            F_ICON_B.drawString(m, "B", x + 20, centerBoxY(F_ICON_B, "B", y + h - 20, 20), glyphCol);
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
            F_ICON_B.drawString(m, "G", chipX + chipW / 2f - 5, centerBoxY(F_ICON_B, "G", rowY + 1, 10), glyphCol);
        } else {
            F_BODY.drawString(m, bindName, chipX + 4, centerBoxY(F_BODY, bindName, rowY + 1, 10), glyphCol);
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
            dropRow = null;
            dropPushH = 0f;
            capturing = null;
            popScroll = 0f;
            popSmooth = 0f;
            return;
        }

        // the dropdown eases its own height — the rows below are pushed into
        // that gap instead of being covered by the list
        float dropTarget = 0f;
        if (dropdown != null && dropRow != null) {
            dropTarget = dropRow.options().length * DROP_ROW_H + 6f;
        }
        dropPushH = approach(dropPushH, dropTarget, 22f);
        dropAnim = approach(dropAnim, dropdown != null ? 1f : 0f, 22f);
        if (dropdown == null && dropPushH < 0.4f && dropAnim < 0.02f) {
            dropRow = null;
            dropPushH = 0f;
            dropAnim = 0f;
            dropHoverY = -1f;
            dropHoverA = 0f;
        }

        float e = easeOutBack(popupAnim);
        float slide = (1f - e) * 14f;
        float a = clamp01(popupAnim);

        // rows first: the panel height and the scroll follow the animated stack
        layoutPopup();
        popH = 24 + rowsH + 10;
        popPanelH = Math.min(popH, Math.max(60f, height - popY - 12f));
        popViewTop = popY + 24;
        popViewH = Math.max(14f, popPanelH - 34f);
        float maxScroll = Math.max(0f, rowsH - popViewH);
        popScroll = Math.max(0f, Math.min(maxScroll, popScroll));
        popSmooth = approach(popSmooth, popScroll, 14f);

        float anchorX = popX;
        float anchorY = popY + Math.min(popH, 120f) / 2f;
        m.push();
        m.translate(anchorX + slide, anchorY, 0f);
        float psc = 0.93f + 0.07f * e;
        m.scale(psc, psc, 1f);
        m.translate(-anchorX, -anchorY, 0f);

        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f * a);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, popX, popY, popW, popPanelH)
                    .round(8)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, popX, popY, popW, popPanelH, 8, UiRender.mulAlpha(t.panel(), 0.98f * a));
        UiRender.outline(m, popX, popY, popW, popPanelH, 8, UiRender.mulAlpha(t.outline(), a), 1f);

        // header: "<Module> Settings" + close glyph, hairline underneath (y+22)
        String title = truncate(F_HEAD, popupModule.name() + " Settings", popW - 42);
        float popTitleY = popY + 8;
        float popMid = popTitleY - 3f + F_HEAD.getStringHeight(title) * 0.25f;
        F_HEAD.drawString(m, title, popX + 10, popTitleY, UiRender.mulAlpha(t.text(), a));
        F_ICONS.drawString(m, "K", popX + popW - 15,
                centerBoxY(F_ICONS, "K", popMid - 8f, 16f),
                UiRender.mulAlpha(t.textSecondary(), 0.6f * a));
        UiRender.rect(m, popX + 10, popY + 22, popW - 20, 0.5f, 0,
                UiRender.mulAlpha(t.text(), 0.2f * a));

        // rows clipped to the viewport, so a long list scrolls instead of spilling
        Matrix4f clipMatrix = m.peek().getPositionMatrix();
        scissor.push(clipMatrix, popX + 1, popViewTop, popW - 2, popViewH);
        for (PopRow r : popRows) {
            if (r.section) {
                drawSectionRow(m, t, r);
            } else {
                drawPopupRow(m, t, r);
            }
        }
        if (dropRow != null && dropPushH > 0.4f) {
            renderDropdown(m, t);
        }
        scissor.pop();

        // scrollbar on the right edge when the rows overflow the panel
        if (maxScroll > 0f) {
            float barX = popX + popW - 5f;
            float thumbH = Math.max(16f, popViewH * popViewH / Math.max(1f, rowsH));
            float thumbY = popViewTop + (popViewH - thumbH) * (popScroll / Math.max(1f, maxScroll));
            UiRender.rect(m, barX, popViewTop, 2.5f, popViewH, 1.25f, UiRender.mulAlpha(t.text(), 0.08f * a));
            UiRender.rect(m, barX, thumbY, 2.5f, thumbH, 1.25f, UiRender.mulAlpha(t.text(), 0.28f * a));
        }
        m.pop();
    }

    /**
     * Stacks the popup rows top-down from their animated heights. Section
     * headers and the open dropdown carry an eased height, so the rows below
     * are pushed smoothly instead of jumping.
     */
    private void layoutPopup() {
        popRows.clear();
        rowsH = 0f;
        if (popupModule == null) {
            return;
        }
        List<Setting> settings = popupModule.settings();
        float top = popY + 24;
        float y = top;
        int i = 0;
        while (i < settings.size()) {
            int run = sectionRun(settings, i);
            if (run > 0) {
                String key = "sec:" + popupModule.id() + ":" + i;
                boolean open = sectionOpen.getOrDefault(key, true);
                float st = approach(sectionAnim.getOrDefault(key, open ? 1f : 0f), open ? 1f : 0f, 20f);
                sectionAnim.put(key, st);
                PopRow head = new PopRow(null, key, sectionLabel(settings.get(i)), run, true);
                head.x = popX;
                head.y = y - popSmooth;
                head.w = popW;
                head.h = SEC_H;
                head.alpha = 1f;
                popRows.add(head);
                y += SEC_H;
                if (st > 0.02f) {
                    for (int j = 0; j < run; j++) {
                        y += addSettingRow(settings.get(i + j), y, st);
                    }
                }
                i += run;
            } else {
                y += addSettingRow(settings.get(i), y, 1f);
                i++;
            }
        }
        rowsH = y - top;
        popScroll = Math.min(popScroll, Math.max(0f, rowsH - popViewH));
    }

    /** Adds one setting row; its height scales with the section's expand ease. */
    private float addSettingRow(Setting s, float y, float expand) {
        float base = rowHeight(s);
        float h = base * expand;
        float ry = y - popSmooth;
        if (s == dropRow) {
            h += dropPushH;
            dropX = popX + popW - ROW_RIGHT - FIELD_W;
            dropW = FIELD_W;
            dropY = ry + base; // the list grows into the pushed gap
        }
        if (h < 0.5f) {
            return 0f;
        }
        PopRow row = new PopRow(s, "s:" + System.identityHashCode(s), null, 0, false);
        row.x = popX;
        row.y = ry;
        row.w = popW;
        row.h = h;
        row.alpha = expand;
        popRows.add(row);
        return h;
    }

    /** Length of the collapsible run starting at i (0 when no header belongs there). */
    private static int sectionRun(List<Setting> settings, int i) {
        Setting first = settings.get(i);
        if (!isSectionKind(first)) {
            return 0;
        }
        int run = 1;
        while (i + run < settings.size() && settings.get(i + run).getClass() == first.getClass()) {
            run++;
        }
        return run >= 3 ? run : 0;
    }

    /** Kinds that form a section when they appear as a run of three or more. */
    private static boolean isSectionKind(Setting s) {
        return s instanceof BooleanSetting || s instanceof ColorSetting || s instanceof DoubleSetting;
    }

    private static String sectionLabel(Setting s) {
        if (s instanceof ColorSetting) {
            return "Цвета";
        }
        if (s instanceof DoubleSetting) {
            return "Слайдеры";
        }
        return "Отображение";
    }

    /** LoxeDLC row heights: checkboxes are compact, the rest sit on a 22px line. */
    private static float rowHeight(Setting s) {
        return s instanceof BooleanSetting ? ROW_CHECK_H : ROW_H;
    }

    /** Section header: +/− marker, label, count; toggling eases the whole block open. */
    private void drawSectionRow(MatrixStack m, Theme t, PopRow r) {
        float a = clamp01(popupAnim);
        float open = sectionAnim.getOrDefault(r.key, 1f);
        float hv = easeHover("sec:" + r.key, UiRender.inside(tmx, tmy, r.x, r.y, r.w, SEC_H));
        int markCol = UiRender.mix(t.textSecondary(), t.accent(), hv);
        if (hv > 0.01f) {
            UiRender.rect(m, r.x + 4, r.y, r.w - 8, SEC_H, 3,
                    UiRender.mulAlpha(t.text(), 0.05f * hv * a));
        }
        // +/− marker: the vertical bar fades out as the section opens
        float mx = r.x + ROW_GLYPH_X;
        float my = r.y + SEC_H / 2f;
        UiRender.rect(m, mx, my - 0.5f, 8, 1f, 0.5f, UiRender.mulAlpha(markCol, a));
        UiRender.rect(m, mx + 3.5f, my - 4f, 1f, 8f, 0.5f, UiRender.mulAlpha(markCol, a * open));
        String label = truncate(F_SEMI, r.label, r.w - ROW_TEXT_X - 30);
        F_SEMI.drawString(m, label, r.x + ROW_TEXT_X, centerBoxY(F_SEMI, label, r.y, SEC_H),
                UiRender.mulAlpha(UiRender.mix(t.text(), t.accent(), hv * 0.5f), a));
        String cnt = String.valueOf(r.count);
        FontRenderer.drawRight(F_TINY, m, cnt, r.x + r.w - ROW_RIGHT,
                centerBoxY(F_TINY, cnt, r.y, SEC_H), UiRender.mulAlpha(t.textSecondary(), 0.55f * a));
    }

    private void drawPopupRow(MatrixStack m, Theme t, PopRow row) {
        Setting s = row.setting;
        float a = clamp01(popupAnim) * clamp01(row.alpha);
        if (a < 0.01f) {
            return;
        }
        float x = row.x;
        float y = row.y;
        float w = row.w;
        float base = rowHeight(s);
        float boxH = Math.min(row.h, base); // content centres on the base row, never on the pushed gap
        float right = x + w - ROW_RIGHT;

        // eased hover tint — the row lifts a hair toward the text colour
        float hv = easeHover(row.key, UiRender.inside(tmx, tmy, x, y, w, boxH));
        if (hv > 0.01f) {
            UiRender.rect(m, x + 4, y, w - 8, boxH, 3, UiRender.mulAlpha(t.text(), 0.045f * hv * a));
        }
        int textCol = UiRender.mulAlpha(t.text(), a);
        int dimCol = UiRender.mulAlpha(t.textSecondary(), 0.3f * a);

        if (s instanceof BooleanSetting b) {
            F_ICON_C.drawString(m, "K", x + ROW_GLYPH_X, centerBoxY(F_ICON_C, "K", y, base), dimCol);
            String nm = truncate(F_BODY, b.name(), w - ROW_TEXT_X - 36);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);
            float k = toggleEase(row.key, b.get());
            float togX = right - 16;
            float togY = y + base / 2f - 4;
            UiRender.rect(m, togX, togY, 16, 8, 4, UiRender.mulAlpha(UiRender.withAlpha(t.text(), 40), a));
            UiRender.rect(m, togX, togY, Math.max(3f, 16f * k), 8, 4, UiRender.mulAlpha(t.accent(), a));
            float knobX = togX - 0.5f + k * 8;
            UiRender.rect(m, knobX, togY - 0.5f, 9, 9, 4.5f, UiRender.mulAlpha(t.card(), a));
            UiRender.outline(m, knobX, togY - 0.5f, 9, 9, 4.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 220), a), 1.2f);
        } else if (s instanceof DoubleSetting d) {
            F_ICON_C.drawString(m, "H", x + ROW_GLYPH_X, centerBoxY(F_ICON_C, "H", y, base), dimCol);
            String nm = truncate(F_BODY, d.name(), w - ROW_TEXT_X - TRACK_W - 16);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);

            // the read-out counts up/down with the fill instead of snapping
            float shown = approach(valueAnim.getOrDefault(row.key, (float) d.get()), (float) d.get(), 16f);
            valueAnim.put(row.key, shown);
            String val = formatValue(d, shown);
            FontRenderer.drawRight(F_BOLD, m, val, right, centerBoxY(F_BOLD, val, y, base),
                    UiRender.mulAlpha(t.accent(), a));

            // fill and knob lag the drag (k≈18); grabbing adds a soft glow
            float frac = approach(sliderAnim.getOrDefault(row.key, d.fraction()), d.fraction(), 18f);
            sliderAnim.put(row.key, frac);
            float trackX = right - TRACK_W;
            float trackY = y + 15;
            UiRender.rect(m, trackX, trackY, TRACK_W, 3, 1.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(t.text(), 30), a));
            UiRender.rect(m, trackX, trackY, Math.max(3f, TRACK_W * frac), 3, 1.5f,
                    UiRender.mulAlpha(t.accent(), a));
            float thX = trackX + TRACK_W * frac - 3.5f;
            float thY = trackY - 2f;
            if (dragging2 == d) {
                UiRender.glow(m, thX + 3.5f, thY + 3.5f, 11f, t.accent() & 0xFFFFFF, 0.30f, a);
            }
            UiRender.rect(m, thX, thY, 7, 7, 3.5f, UiRender.mulAlpha(t.accent(), a));
            UiRender.outline(m, thX, thY, 7, 7, 3.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(0xFFFFFFFF, 170), a), 1.1f);
        } else if (s instanceof ModeSetting md) {
            F_ICON_C.drawString(m, "J", x + ROW_GLYPH_X, centerBoxY(F_ICON_C, "J", y, base), dimCol);
            String nm = truncate(F_BODY, md.name(), w - ROW_TEXT_X - FIELD_W - 16);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);
            drawSelectField(m, t, right - FIELD_W, y + (ROW_H - 14) / 2f, FIELD_W,
                    md.get(), dropdown == md, a, row.key);
        } else if (s instanceof KeySetting k2) {
            F_ICON_C.drawString(m, "L", x + ROW_GLYPH_X, centerBoxY(F_ICON_C, "L", y, base), dimCol);
            String nm = truncate(F_BODY, k2.name(), w - ROW_TEXT_X - FIELD_W - 16);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);
            drawBindField(m, t, right - FIELD_W, y + (ROW_H - 14) / 2f, FIELD_W, k2, a, row.key);
        } else if (s instanceof ColorSetting cs) {
            // colour row: current colour in the gutter, swatch on the right column
            float dotY = y + base / 2f - 3.5f;
            UiRender.rect(m, x + ROW_GLYPH_X, dotY, 7, 7, 3.5f, UiRender.mulAlpha(cs.get(), a));
            UiRender.outline(m, x + ROW_GLYPH_X, dotY, 7, 7, 3.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 200), a), 1f);
            String nm = truncate(F_BODY, cs.name(), w - ROW_TEXT_X - 30);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);

            float sw = 12f;
            float sx = right - sw;
            float sy = y + base / 2f - sw / 2f;
            float pulse = pulseEase(row.key);
            float pr = easePress(row.key, row.key.equals(pressedBtn));
            pushScale(m, sx + sw / 2f, sy + sw / 2f,
                    UiRender.pressScale(pr) * (1f + 0.20f * pulse));
            UiRender.rect(m, sx, sy, sw, sw, 4, UiRender.mulAlpha(UiRender.lighten(cs.get(), 0.12f * Math.max(hv, pulse)), a));
            int ring = UiRender.mix(UiRender.withAlpha(t.outline(), 180), t.accent(),
                    Math.max(hv, Math.max(pulse, pickerColor == cs && pickerOpen ? 1f : 0f)));
            UiRender.outline(m, sx, sy, sw, sw, 4, UiRender.mulAlpha(ring, a), 1.2f);
            m.pop();
        }
    }

    /** Key capture field: pulsing accent while it waits for the next key press. */
    private void drawBindField(MatrixStack m, Theme t, float fx, float fy, float fw,
                               KeySetting k2, float a, String id) {
        boolean cap = capturing == k2;
        boolean hov = UiRender.inside(tmx, tmy, fx, fy, fw, 14);
        float hv = easeHover(id, hov);
        float pr = easePress(id, id.equals(pressedBtn));
        // "press a key" breathes: alpha swings 0.55..1 while a capture is armed
        float pulse = cap ? 0.55f + 0.45f * (float) Math.sin(animTime * 6.0) : 1f;
        pushScale(m, fx + fw / 2f, fy + 7f, UiRender.pressScale(pr));
        int bg = UiRender.mix(UiRender.mix(t.card(), t.cardHover(), hv), t.accent(), cap ? 0.20f * pulse : 0f);
        UiRender.rect(m, fx, fy, fw, 14, 3, UiRender.mulAlpha(bg, a));
        int stroke = UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(), cap ? pulse : 0f);
        UiRender.outline(m, fx, fy, fw, 14, 3, UiRender.mulAlpha(stroke, a), cap ? 1.3f : 1f);
        String val = cap ? "Нажмите клавишу" : k2.label();
        int fg = cap ? UiRender.mulAlpha(t.accent(), pulse * a) : UiRender.mulAlpha(t.text(), a);
        F_TINY.drawStringWithScroll(m, val, fx + 5, centerBoxY(F_TINY, val, fy, 14), fw - 10, fg);
        m.pop();
    }

    /** 66x14 rounded select field with a chevron (LoxeDLC SelectedButton). */
    private void drawSelectField(MatrixStack m, Theme t, float fx, float fy, float fw, String value,
                                 boolean active, float a, String id) {
        boolean hov = UiRender.inside(tmx, tmy, fx, fy, fw, 14);
        float hv = easeHover(id, hov);
        float pr = easePress(id, id.equals(pressedBtn));
        pushScale(m, fx + fw / 2f, fy + 7f, UiRender.pressScale(pr));
        int bg = UiRender.mix(UiRender.mix(t.card(), t.cardHover(), hv), t.accent(), active ? 0.18f : 0f);
        UiRender.rect(m, fx, fy, fw, 14, 3, UiRender.mulAlpha(bg, a));
        UiRender.outline(m, fx, fy, fw, 14, 3, UiRender.mulAlpha(
                UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(), active ? 1f : 0f), a), 1);
        F_BODY.drawStringWithScroll(m, value, fx + 5, centerBoxY(F_BODY, value, fy, 14), fw - 16,
                UiRender.mulAlpha(t.text(), a));
        // chevron: two hairlines; the short one flips above when the list opens
        float chx = fx + fw - 10, chy = fy + 6;
        int chev = UiRender.mulAlpha(t.textSecondary(), a);
        UiRender.rect(m, chx, chy, 5, 1f, 0.5f, chev);
        UiRender.rect(m, chx + 1, chy + (active ? -1.2f : 1.2f), 3, 1f, 0.5f,
                UiRender.mulAlpha(t.textSecondary(), a * (active ? 0.5f : 1f)));
        m.pop();
    }

    /**
     * Option list of the open select: it grows into the pushed gap (so the rows
     * below slide down), and the hover highlight eases between the rows.
     */
    private void renderDropdown(MatrixStack m, Theme t) {
        String[] opts = dropRow.options();
        float a = clamp01(dropAnim) * clamp01(popupAnim);
        if (a < 0.01f) {
            return;
        }
        float dcx = dropX + dropW / 2f;
        m.push();
        float de = 0.94f + 0.06f * easeOutBack(dropAnim);
        m.translate(dcx, dropY, 0f);
        m.scale(de, de, 1f);
        m.translate(-dcx, -dropY, 0f);

        float listH = Math.max(4f, dropPushH);
        UiRender.rect(m, dropX, dropY, dropW, listH, 3, UiRender.mulAlpha(t.panel(), a));
        UiRender.outline(m, dropX, dropY, dropW, listH, 3,
                UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 155), a), 1);

        // hover highlight slides to the row under the cursor, fades out otherwise
        float target = -1f;
        float oy = dropY + 3;
        for (int i = 0; i < opts.length; i++) {
            if (UiRender.inside(tmx, tmy, dropX + 3, oy, dropW - 6, DROP_ROW_H)) {
                target = oy;
            }
            oy += DROP_ROW_H;
        }
        if (dropHoverY < 0f) {
            dropHoverY = dropY + 3;
        }
        dropHoverY = approach(dropHoverY, target >= 0f ? target : dropY + 3, target >= 0f ? 24f : 14f);
        dropHoverA = approach(dropHoverA, target >= 0f ? 1f : 0f, 18f);
        if (dropHoverA > 0.01f) {
            UiRender.rect(m, dropX + 3, dropHoverY, dropW - 6, DROP_ROW_H, 3,
                    UiRender.mulAlpha(UiRender.withAlpha(t.accent(), 34), dropHoverA * a));
        }

        int selIdx = dropRow.index();
        oy = dropY + 3;
        for (int i = 0; i < opts.length; i++) {
            if (oy + DROP_ROW_H > dropY + listH) {
                break; // still growing — rows below the gap are clipped away
            }
            if (i == selIdx) {
                UiRender.rect(m, dropX + 3, oy, dropW - 6, DROP_ROW_H, 3,
                        UiRender.mulAlpha(UiRender.withAlpha(t.accent(), 55), a));
            }
            String opt = truncate(F_TINY, opts[i], dropW - 16);
            F_TINY.drawString(m, opt, dropX + 8, centerBoxY(F_TINY, opt, oy, DROP_ROW_H),
                    UiRender.mulAlpha(i == selIdx ? t.accent() : t.text(), a));
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

        // save button (LoxeDLC: floppy glyph + label) — shared accent recipe
        float saveW = 52;
        float saveX = winX + WIN_W - 16 - saveW;
        boolean saveHover = UiRender.inside(tmx, tmy, saveX, cfgFieldY, saveW, cfgFieldH);
        drawButton(m, F_TINY, CFG_SAVE, saveX, cfgFieldY, saveW, cfgFieldH, 3, "Save",
                saveHover, t.accent(), 0xFFFFFFFF, true);
        // the icon rides the same sink drawButton just eased
        pushScale(m, saveX + saveW / 2f, cfgFieldY + cfgFieldH / 2f,
                UiRender.pressScale(btnPressAnim.getOrDefault(CFG_SAVE, 0f)));
        F_ICON_B.drawString(m, "M", saveX + 5, centerBoxY(F_ICON_B, "M", cfgFieldY, cfgFieldH), 0xFFFFFFFF);
        m.pop();

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
            String cfgLabel = truncate(F_SEMI, name, CARD_W - 16);
            F_SEMI.drawString(m, cfgLabel, cx2 + 8, centerBoxY(F_SEMI, cfgLabel, cy2, 20), t.text());

            float btnY = cy2 + 22;
            // load (play glyph) + delete (trash glyph) icon buttons
            float loadX = cx2 + CARD_W - 38;
            float delX = cx2 + CARD_W - 20;
            boolean lh = UiRender.inside(tmx, tmy, loadX, btnY, 16, 12);
            boolean dh = UiRender.inside(tmx, tmy, delX, btnY, 16, 12);
            int loadFg = UiRender.mix(t.textSecondary(), t.accent(), easeHover(CFG_LOAD + name, lh));
            int delFg = UiRender.mix(t.textSecondary(), 0xFFFF6B81, easeHover(CFG_DEL + name, dh));
            drawButton(m, F_ICONS, CFG_LOAD + name, loadX, btnY, 16, 12, 3, "H", lh,
                    UiRender.withAlpha(t.text(), 30), loadFg, false);
            drawButton(m, F_ICON_C, CFG_DEL + name, delX, btnY, 16, 12, 3, "O", dh,
                    UiRender.withAlpha(t.text(), 30), delFg, false);
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

    /**
     * Theme overlay behind the gear: preset tiles with eased hover/press, the
     * Custom row's editable slots, an in-place rename and a palette reset.
     */
    private void renderSettings(MatrixStack m, int mouseX, int mouseY) {
        Theme t = Theme.get();
        settingsAnim = approach(settingsAnim, settingsView ? 1f : 0f, 18f);
        tickCustomReset();
        if (settingsAnim < 0.01f) {
            settingsAnim = 0f;
            return;
        }
        float a = settingsAnim;
        float e = easeOutBack(a);
        renameBlink += frameDt;

        UiRender.rect(m, 0, 0, width, height, 0, UiRender.mulAlpha(0xFF000000, 0.55f * a));
        swatchHits.clear();

        List<Theme> themes = Theme.all();
        float pw = 340;
        float rowH = 40;
        float tileH = 32;
        float ph = 58 + themes.size() * rowH;
        float px = (width - pw) / 2f;
        float py = (height - ph) / 2f;

        m.push();
        float ocx = width / 2f, ocy = height / 2f;
        m.translate(ocx, ocy, 0f);
        float s = 0.94f + 0.06f * e;
        m.scale(s, s, 1f);
        m.translate(-ocx, -ocy, 0f);

        UiRender.shadow(m, px, py, pw, ph, 10, 0.4f * a);
        UiRender.rect(m, px, py, pw, ph, 10, UiRender.mulAlpha(t.panel(), a));
        UiRender.outline(m, px, py, pw, ph, 10, UiRender.mulAlpha(t.outline(), a), 1);
        F_TITLE.drawString(m, "Темы", px + 14, py + 12, UiRender.mulAlpha(t.text(), a));
        F_TINY.drawString(m, "Клик по свотчу Custom открывает HSV-пикер",
                px + 14, py + 30, UiRender.mulAlpha(t.textSecondary(), 0.9f * a));

        float ry = py + 48;
        for (Theme th : themes) {
            boolean custom = "custom".equals(th.id());
            String tileId = "theme:" + th.id();
            float tx = px + 12;
            float tw = pw - 24;
            boolean active = th.id().equals(t.id());
            boolean hov = UiRender.inside(mouseX, mouseY, tx, ry, tw, tileH) && !renameActive;
            float hv = easeHover(tileId, hov);
            float pr = easePress(tileId, tileId.equals(pressedBtn));
            float sel = approach(themeSelAnim.getOrDefault(th.id(), active ? 1f : 0f),
                    active ? 1f : 0f, 16f);
            themeSelAnim.put(th.id(), sel);

            pushScale(m, tx + tw / 2f, ry + tileH / 2f, UiRender.pressScale(pr));
            int fill = UiRender.mix(t.card(), t.cardHover(), hv);
            fill = UiRender.mix(fill, UiRender.mix(fill, th.accent(), 0.16f), sel);
            UiRender.rect(m, tx, ry, tw, tileH, 6, UiRender.mulAlpha(fill, a));
            int stroke = UiRender.mix(UiRender.withAlpha(t.outline(), 110), t.accent(),
                    Math.max(sel, hv * 0.45f));
            UiRender.outline(m, tx, ry, tw, tileH, 6, UiRender.mulAlpha(stroke, a), 1f + 0.4f * sel);

            // label column: the Custom name is edited in place, caret blinks
            String label = displayName(th);
            int labelCol = UiRender.mulAlpha(t.text(), a);
            if (custom && renameActive) {
                boolean caret = (renameBlink % 0.55f) < 0.34f;
                label = renameBuf + (caret ? "|" : "");
                labelCol = UiRender.mulAlpha(t.accent(), a);
            }
            label = truncate(F_BODY, label, RENAME_X - 16);
            F_BODY.drawString(m, label, tx + 10, centerBoxY(F_BODY, label, ry, tileH), labelCol);

            if (custom) {
                // fixed action column before the swatch cluster
                float ax = tx + RENAME_X;
                float ay = actionY(ry);
                drawButton(m, F_TINY, "theme:ren", ax, ay, REN_W, ACTION_H, 4, "Переим.",
                        UiRender.inside(mouseX, mouseY, ax, ay, REN_W, ACTION_H),
                        t.cardHover(), t.text(), false);
                float bx = tx + DELETE_X;
                drawButton(m, F_TINY, "theme:del", bx, ay, DEL_W, ACTION_H, 4, "Сброс",
                        UiRender.inside(mouseX, mouseY, bx, ay, DEL_W, ACTION_H),
                        UiRender.mix(t.cardHover(), 0xFFFF6B81, 0.30f), 0xFFFF8FA0, false);
            }

            // value column: the Custom slots, or a preview of the preset palette
            float clusterRight = tx + tw - 10;
            if (custom) {
                float sx = clusterRight - 76f;
                for (String slot : Theme.CUSTOM_SLOTS) {
                    int c = Theme.customSlot(slot);
                    boolean sh = UiRender.inside(mouseX, mouseY, sx, ry + 10, 12, 12) && !renameActive;
                    float pulse = pulseEase("sw:" + slot);
                    boolean picked = pickerOpen && pickerColor == null && pickerSlot.equals(slot);
                    pushScale(m, sx + 6f, ry + 16f, 1f + 0.18f * pulse);
                    UiRender.rect(m, sx, ry + 10, 12, 12, 6, UiRender.mulAlpha(UiRender.lighten(c, 0.12f * (sh ? 1f : 0f)), a));
                    int ring = UiRender.mix(UiRender.withAlpha(t.outline(), 160), t.accent(),
                            Math.max(picked ? 1f : 0f, sh ? 0.7f : 0f));
                    UiRender.outline(m, sx, ry + 10, 12, 12, 6, UiRender.mulAlpha(ring, a), 1.2f);
                    m.pop();
                    swatchHits.add(new SwatchHit(slot, null, 0, sx, ry + 10, 12, 12));
                    sx += 16f;
                }
            } else {
                int[] preview = {th.panel(), th.accent(), th.card()};
                float sx = clusterRight - 44f;
                for (int c : preview) {
                    UiRender.rect(m, sx, ry + 10, 12, 12, 6, UiRender.mulAlpha(c, a));
                    UiRender.outline(m, sx, ry + 10, 12, 12, 6, UiRender.mulAlpha(t.outline(), a), 1f);
                    sx += 16f;
                }
            }
            m.pop();

            themeRows.add(new ThemeRow(th, tx, ry, tw, tileH));
            ry += rowH;
        }
        m.pop();
    }

    // theme panel geometry: label column, action column, then the swatch cluster
    private static final float RENAME_X = 118f;
    private static final float REN_W = 46f;
    private static final float DELETE_X = 170f;
    private static final float DEL_W = 40f;
    private static final float ACTION_H = 16f;

    private static float actionY(float rowY) {
        return rowY + (32f - ACTION_H) / 2f;
    }

    private static float renameX(ThemeRow r) {
        return r.x + RENAME_X;
    }

    private static float deleteX(ThemeRow r) {
        return r.x + DELETE_X;
    }

    private static float actionY(ThemeRow r) {
        return actionY(r.y);
    }

    /** Theme name; the Custom palette can be renamed right in this screen. */
    private String displayName(Theme th) {
        return "custom".equals(th.id()) ? customName : th.name();
    }

    private void beginRename() {
        renameBuf = customName;
        renameActive = true;
        renameBlink = 0f;
    }

    private void commitRename() {
        if (!renameActive) {
            return;
        }
        String clean = renameBuf.trim();
        if (!clean.isEmpty()) {
            customName = clean.length() > 18 ? clean.substring(0, 18) : clean;
        }
        renameActive = false;
        renameBuf = "";
    }

    private void cancelRename() {
        renameActive = false;
        renameBuf = "";
    }

    /** Default Custom palette — mirrors Theme's initial CUSTOM map (kept in sync by hand). */
    private static final int[] CUSTOM_DEFAULTS = {
            0xFF0A1216, 0xFF0F1A20, 0xFF16242C, 0xFF8B5CF6, 0xFFF0F7FA
    };

    /** Resets the Custom palette: every slot tweens back to its default colour. */
    private void startCustomReset() {
        customResetFrom.clear();
        for (int i = 0; i < Theme.CUSTOM_SLOTS.length; i++) {
            customResetFrom.put(Theme.CUSTOM_SLOTS[i], Theme.customSlot(Theme.CUSTOM_SLOTS[i]));
        }
        customResetting = true;
        customResetT = 0f;
    }

    /** Steps the palette-reset tween (smoothstep over ~350ms, once per frame). */
    private void tickCustomReset() {
        if (!customResetting) {
            return;
        }
        customResetT = Math.min(1f, customResetT + frameDt / 0.35f);
        float e = customResetT * customResetT * (3f - 2f * customResetT);
        for (int i = 0; i < Theme.CUSTOM_SLOTS.length; i++) {
            String slot = Theme.CUSTOM_SLOTS[i];
            int from = customResetFrom.getOrDefault(slot, CUSTOM_DEFAULTS[i]);
            Theme.setCustom(slot, UiRender.mix(from, CUSTOM_DEFAULTS[i], e));
            swatchPulse.put("sw:" + slot, 1f); // each swatch pops as it lands
        }
        if (customResetT >= 1f) {
            customResetting = false;
            customResetFrom.clear();
        }
    }

    /** HSV popup for a Custom slot or a module colour: pop-in, eased read-out, animated swatch. */
    private void renderPicker(MatrixStack m, int mouseX, int mouseY) {
        Theme t = Theme.get();
        pickerAnim = approach(pickerAnim, pickerOpen ? 1f : 0f, 22f);
        if (!pickerOpen && pickerAnim < 0.02f) {
            pickerAnim = 0f;
            return;
        }
        float a = clamp01(pickerAnim);
        float e = easeOutBack(pickerAnim);

        // eased read-out: it trails a retarget (slot switch, palette reset) and
        // snaps to the finger while the SV square or the hue bar is dragged
        if (pickerDrag == 0) {
            dHue = approachHue(dHue, pHue, 18f);
            dSat = approach(dSat, pSat, 18f);
            dVal = approach(dVal, pVal, 18f);
        } else {
            dHue = pHue;
            dSat = pSat;
            dVal = pVal;
        }

        UiRender.rect(m, 0, 0, width, height, 0, UiRender.mulAlpha(0xFF000000, 0.55f * a));

        pickW = 214;
        pickH = 208;
        pickX = (width - pickW) / 2f;
        pickY = (height - pickH) / 2f;

        m.push();
        float ocx = pickX + pickW / 2f;
        float ocy = pickY + pickH / 2f;
        m.translate(ocx, ocy + (1f - e) * 6f, 0f);
        float psc = 0.94f + 0.06f * e;
        m.scale(psc, psc, 1f);
        m.translate(-ocx, -ocy, 0f);

        UiRender.shadow(m, pickX, pickY, pickW, pickH, 10, 0.45f * a);
        UiRender.rect(m, pickX, pickY, pickW, pickH, 10, UiRender.mulAlpha(t.panel(), 0.99f * a));
        UiRender.outline(m, pickX, pickY, pickW, pickH, 10, UiRender.mulAlpha(t.outline(), a), 1);

        // title in the label column, current value right-aligned in the value column
        // (both centred on the title's optical centre, so the line reads level)
        boolean slotMode = pickerColor == null;
        int color = currentPickColor();
        String title = slotMode ? "Цвет слота" : pickerColor.name();
        title = truncate(F_TITLE, title, pickW - 96);
        float pickTitleY = pickY + 9;
        float titleMid = pickTitleY - 3f + F_TITLE.getStringHeight(title) * 0.25f;
        F_TITLE.drawString(m, title, pickX + 10, pickTitleY, UiRender.mulAlpha(t.text(), a));
        String rightCol = slotMode ? slotDisplayName()
                : String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        FontRenderer.drawRight(F_TINY, m, rightCol, pickX + pickW - 10,
                centerBoxY(F_TINY, rightCol, titleMid - 8f, 16f),
                UiRender.mulAlpha(t.accent(), a));

        // swatch tabs: the five theme slots, or the colour's presets when it has any
        swatchHits.clear();
        float sy = pickY + 30;
        if (slotMode) {
            for (int i = 0; i < Theme.CUSTOM_SLOTS.length; i++) {
                String slot = Theme.CUSTOM_SLOTS[i];
                float sx = pickX + 10 + i * 31;
                drawSwatchTab(m, t, sx, sy, 26, 18, Theme.customSlot(slot), slot.equals(pickerSlot),
                        a, "tab:" + slot, UiRender.inside(mouseX, mouseY, sx, sy, 26, 18));
                swatchHits.add(new SwatchHit(slot, null, 0, sx, sy, 26, 18));
            }
        } else {
            int[] presets = pickerColor.presetList();
            if (presets.length > 0) {
                for (int i = 0; i < Math.min(presets.length, 6); i++) {
                    float sx = pickX + 10 + i * 31;
                    drawSwatchTab(m, t, sx, sy, 26, 18, presets[i], presets[i] == color,
                            a, "tab:" + presets[i], UiRender.inside(mouseX, mouseY, sx, sy, 26, 18));
                    swatchHits.add(new SwatchHit(null, pickerColor, presets[i], sx, sy, 26, 18));
                }
            } else {
                drawSwatchTab(m, t, pickX + 10, sy, 26, 18, color, true, a, "tab:self", false);
            }
        }

        // saturation/value square — the hue behind it comes from the eased read-out
        pvX = pickX + 10;
        pvY = pickY + 56;
        pvW = pickW - 20;
        pvH = 92;
        UiRender.rect(m, pvX, pvY, pvW, pvH, 5, hsv(dHue, 1f, 1f));
        UiRender.gradient(m, pvX, pvY, pvW, pvH, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF);
        UiRender.gradient(m, pvX, pvY, pvW, pvH, 0x00000000, 0xFF000000, 0xFF000000, 0x00000000);
        UiRender.outline(m, pvX, pvY, pvW, pvH, 5, UiRender.mulAlpha(t.outline(), a), 1);
        float mkx = pvX + dSat * pvW;
        float mky = pvY + (1f - dVal) * pvH;
        pushScale(m, mkx, mky, pickerDrag == 1 ? 1.2f : 1f);
        UiRender.outline(m, mkx - 5, mky - 5, 10, 10, 5, UiRender.mulAlpha(0x80000000, a), 3f);
        UiRender.outline(m, mkx - 4, mky - 4, 8, 8, 4, UiRender.mulAlpha(0xFFFFFFFF, a), 1.5f);
        m.pop();

        // hue bar
        hueX = pickX + 10;
        hueY = pickY + 156;
        hueW = pickW - 20;
        hueH = 10;
        int slices = 36;
        float swd = hueW / slices;
        for (int i = 0; i < slices; i++) {
            UiRender.rect(m, hueX + i * swd, hueY, swd + 0.5f, hueH, 0,
                    UiRender.mulAlpha(hsv(360f * i / slices, 1f, 1f), a));
        }
        UiRender.outline(m, hueX, hueY, hueW, hueH, 3, UiRender.mulAlpha(t.outline(), a), 1);
        float hx = hueX + (dHue / 360f) * hueW;
        pushScale(m, hx, hueY + hueH / 2f, pickerDrag == 2 ? 1.2f : 1f);
        UiRender.outline(m, hx - 5, hueY - 2, 10, hueH + 4, 4, UiRender.mulAlpha(0x80000000, a), 3f);
        UiRender.outline(m, hx - 4, hueY - 1, 8, hueH + 2, 3.5f, UiRender.mulAlpha(0xFFFFFFFF, a), 1.5f);
        m.pop();

        // current colour swatch (pops whenever the colour changes) + hex + Done
        float fy = pickY + 176;
        float pulse = pulseEase("pick:swatch");
        pushScale(m, pickX + 18, fy + 10, 1f + 0.18f * pulse);
        UiRender.rect(m, pickX + 10, fy + 2, 16, 16, 4, color);
        UiRender.outline(m, pickX + 10, fy + 2, 16, 16, 4, UiRender.mulAlpha(t.outline(), a), 1f);
        m.pop();
        String hex = String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        F_TINY.drawString(m, hex, pickX + 32, fy + 5, UiRender.mulAlpha(t.textSecondary(), a));

        pickBtnX = pickX + pickW - 10 - pickBtnW;
        pickBtnY = fy;
        drawButton(m, F_SEMI, "pick:done", pickBtnX, pickBtnY, pickBtnW, pickBtnH, 5, "Готово",
                UiRender.inside(mouseX, mouseY, pickBtnX, pickBtnY, pickBtnW, pickBtnH),
                t.accent(), 0xFFFFFFFF, true);
        m.pop();
    }

    /** One picker tab: press sinks it, hover lifts the ring toward the accent. */
    private void drawSwatchTab(MatrixStack m, Theme t, float sx, float sy, float w2, float h2,
                               int color, boolean sel, float a, String id, boolean hover) {
        float hv = easeHover(id, hover);
        float pr = easePress(id, id.equals(pressedBtn));
        pushScale(m, sx + w2 / 2f, sy + h2 / 2f, UiRender.pressScale(pr) * (1f + 0.05f * hv));
        UiRender.rect(m, sx, sy, w2, h2, 4, UiRender.mulAlpha(color, a));
        int ring = UiRender.mix(UiRender.withAlpha(t.outline(), 160), t.accent(),
                Math.max(sel ? 1f : 0f, hv));
        UiRender.outline(m, sx, sy, w2, h2, 4, UiRender.mulAlpha(ring, a), sel ? 1.5f : 1f);
        m.pop();
    }

    /** Colour the picker currently edits (theme slot, or the module row's value). */
    private int currentPickColor() {
        return pickerColor != null ? pickerColor.get() : Theme.customSlot(pickerSlot);
    }

    private String slotDisplayName() {
        for (int i = 0; i < Theme.CUSTOM_SLOTS.length; i++) {
            if (Theme.CUSTOM_SLOTS[i].equals(pickerSlot)) {
                return Theme.CUSTOM_SLOT_NAMES[i];
            }
        }
        return "";
    }

    /** Points the picker at a colour; snap also lands the eased read-out on it. */
    private void setPickerColor(int argb, boolean snap) {
        float[] h = rgbToHsv(argb);
        pHue = h[0];
        pSat = h[1];
        pVal = h[2];
        if (snap) {
            dHue = pHue;
            dSat = pSat;
            dVal = pVal;
        }
    }

    /** Applies the SV / hue cursor to whatever the picker is editing. */
    private void pickerPick(double mx, double my) {
        if (pickerDrag == 1) {
            pSat = clamp01((float) ((mx - pvX) / pvW));
            pVal = clamp01(1f - (float) ((my - pvY) / pvH));
        } else if (pickerDrag == 2) {
            pHue = clamp01((float) ((mx - hueX) / hueW)) * 360f;
        } else {
            return;
        }
        applyPicker();
    }

    /** Writes the HSV state back to the target and pops its swatch. */
    private void applyPicker() {
        int c = hsv(pHue, pSat, pVal);
        if (pickerColor != null) {
            if (pickerColor.get() != c) {
                pickerColor.set(c);
                swatchPulse.put("pick:swatch", 1f);
            }
            return;
        }
        if (Theme.customSlot(pickerSlot) != c) {
            Theme.setCustom(pickerSlot, c);
            swatchPulse.put("pick:swatch", 1f);
            swatchPulse.put("sw:" + pickerSlot, 1f);
        }
    }

    /** Opens (or retargets) the picker on a Custom slot; switching tabs eases. */
    private void openPicker(String slot) {
        boolean wasOpen = pickerOpen;
        pickerColor = null;
        pickerSlot = slot;
        pickerOpen = true;
        pickerDrag = 0;
        if (!Theme.get().id().equals("custom")) {
            Theme.set(Theme.byId("custom")); // editing colours implies the Custom theme
        }
        setPickerColor(Theme.customSlot(slot), !wasOpen);
    }

    /** Opens the picker on a module colour row. */
    private void openPicker(ColorSetting cs) {
        boolean wasOpen = pickerOpen;
        pickerColor = cs;
        pickerOpen = true;
        pickerDrag = 0;
        setPickerColor(cs.get(), !wasOpen);
    }

    /** Closes the picker; the window eases out instead of blinking away. */
    private void closePicker() {
        pickerOpen = false;
        pickerDrag = 0;
    }

    /** Hue ease on the shortest arc, so 350° → 10° never spins the wheel back. */
    private float approachHue(float cur, float target, float k) {
        float diff = target - cur;
        while (diff > 180f) {
            diff -= 360f;
        }
        while (diff < -180f) {
            diff += 360f;
        }
        float next = cur + diff * (float) (1.0 - Math.exp(-frameDt * k));
        next = ((next % 360f) + 360f) % 360f;
        return Math.abs(next - target) < 0.05f ? target : next;
    }

    // ------------------------------------------------------------------ helpers

    /** Eased approach: cur → target with an FPS-independent 1 - exp(-dt * k). */
    private float approach(float cur, float target, float k) {
        float next = cur + (target - cur) * (float) (1.0 - Math.exp(-frameDt * k));
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    // ------------------------------------------------- shared control easing

    /** Eased hover 0..1 of a control (k≈14 — a soft lift, not a snap). */
    private float easeHover(String id, boolean hover) {
        float v = approach(btnHoverAnim.getOrDefault(id, 0f), hover ? 1f : 0f, 14f);
        btnHoverAnim.put(id, v);
        return v;
    }

    /** Eased press 0..1 (k≈24) — the caller hit-tests and sets {@link #pressedBtn}. */
    private float easePress(String id, boolean pressed) {
        float v = approach(btnPressAnim.getOrDefault(id, 0f), pressed ? 1f : 0f, 24f);
        btnPressAnim.put(id, v);
        return v;
    }

    /**
     * Shared button body: hover/press are eased here, the geometry belongs to
     * {@link UiRender#button} / {@link UiRender#accentButton} so every button
     * in the client sinks and lifts identically.
     */
    private void drawButton(MatrixStack m, FontRenderer font, String id, float x, float y, float w, float h,
                            float round, String label, boolean hover, int bg, int fg, boolean accent) {
        float hv = easeHover(id, hover);
        float pr = easePress(id, id.equals(pressedBtn));
        if (accent) {
            UiRender.accentButton(m, font, x, y, w, h, round, label, hv, pr, bg, fg);
        } else {
            UiRender.button(m, font, x, y, w, h, round, label, hv, pr, bg, fg);
        }
    }

    /** Matches UiRender.button's press sink so extra content rides the same scale. */
    private static void pushScale(MatrixStack m, float cx, float cy, float scale) {
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(scale, scale, 1f);
        m.translate(-cx, -cy, 0f);
    }

    /** Decaying 1→0 pulse; set the key to 1 the moment its colour changes. */
    private float pulseEase(String key) {
        float v = approach(swatchPulse.getOrDefault(key, 0f), 0f, 5f);
        swatchPulse.put(key, v);
        return v;
    }

    /** Cuts a label to its column with a plain ellipsis instead of letting it bleed. */
    private static String truncate(FontRenderer f, String s, float maxW) {
        if (s == null || s.isEmpty() || maxW <= 4f || f.getStringWidth(s) <= maxW) {
            return s;
        }
        String tail = "...";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 0 && f.getStringWidth(sb.toString().trim() + tail) > maxW) {
            sb.setLength(sb.length() - 1);
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? tail : out + tail;
    }

    /** Slider read-out: the setting's own precision, applied to the eased value. */
    private static String formatValue(DoubleSetting d, double v) {
        String f = d.format();
        String suffix = d.suffix();
        String num = suffix.isEmpty() || !f.endsWith(suffix) ? f : f.substring(0, f.length() - suffix.length());
        int dot = num.indexOf('.');
        int dec = dot < 0 ? 0 : num.length() - dot - 1;
        return String.format(Locale.ROOT, "%." + dec + "f", v) + suffix;
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

        // the HSV picker is a modal of its own: it can open over the theme
        // panel or over the settings popup, so it is hit-tested first
        if (pickerOpen || pickerAnim > 0.01f) {
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
                    if (s.slot() != null) {
                        pressedBtn = "tab:" + s.slot();
                        openPicker(s.slot());
                    } else if (s.color() != null) {
                        pressedBtn = "tab:" + s.value();
                        setPickerColor(s.value(), false);
                        applyPicker(); // a preset snaps the target, the read-out eases after it
                    }
                    return true;
                }
            }
            if (UiRender.inside(mouseX, mouseY, pickBtnX, pickBtnY, pickBtnW, pickBtnH)
                    || !UiRender.inside(mouseX, mouseY, pickX, pickY, pickW, pickH)) {
                closePicker();
                return true;
            }
            return true;
        }

        if (settingsView) {
            if (UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize)) {
                settingsView = false;
                return true;
            }
            for (SwatchHit s : swatchHits) {
                if (s.slot() != null && UiRender.inside(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
                    openPicker(s.slot());
                    return true;
                }
            }
            for (ThemeRow r : themeRows) {
                if (!UiRender.inside(mouseX, mouseY, r.x, r.y, r.w, r.h)) {
                    continue;
                }
                if (renameActive) {
                    commitRename(); // a click anywhere in the list ends the rename
                }
                if ("custom".equals(r.theme.id())) {
                    if (UiRender.inside(mouseX, mouseY, deleteX(r), actionY(r), DEL_W, ACTION_H)) {
                        pressedBtn = "theme:del";
                        startCustomReset();
                        return true;
                    }
                    if (UiRender.inside(mouseX, mouseY, renameX(r), actionY(r), REN_W, ACTION_H)) {
                        pressedBtn = "theme:ren";
                        beginRename();
                        return true;
                    }
                }
                pressedBtn = "theme:" + r.theme.id();
                Theme.set(r.theme);
                return true;
            }
            if (renameActive) {
                commitRename();
                return true;
            }
            return true;
        }

        if (dropdown != null && dropRow != null) {
            String[] opts = dropRow.options();
            if (UiRender.inside(tmx, tmy, dropX, dropY, dropW, Math.max(4f, dropPushH))) {
                int idx = (int) ((tmy - dropY - 3) / DROP_ROW_H);
                if (idx >= 0 && idx < opts.length
                        && dropY + 3 + (idx + 1) * DROP_ROW_H <= dropY + dropPushH) {
                    dropRow.setIndex(idx);
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
            for (PopRow r : popRows) {
                // rows outside the clipped viewport are not interactive
                if (r.y + r.h < popViewTop || r.y > popViewTop + popViewH) {
                    continue;
                }
                if (!UiRender.inside(tmx, tmy, r.x, r.y, r.w, r.h)) {
                    continue;
                }
                if (r.section) {
                    sectionOpen.put(r.key, !sectionOpen.getOrDefault(r.key, true));
                    dropdown = null; // a closing list must not keep pushing rows
                    return true;
                }
                Setting s = r.setting;
                float right = r.x + r.w - ROW_RIGHT;
                if (s instanceof BooleanSetting b) {
                    b.toggle();
                    return true;
                }
                if (s instanceof DoubleSetting d) {
                    // LoxeDLC slider: the 65px track is the drag zone
                    if (UiRender.inside(tmx, tmy, right - TRACK_W, r.y + 11, TRACK_W, 11)) {
                        dragging2 = d;
                        dragTrackX = right - TRACK_W;
                        dragTrackW = TRACK_W;
                        d.set(d.fromFraction((float) ((tmx - dragTrackX) / dragTrackW)));
                        return true;
                    }
                    return true; // the rest of the row only swallows the click
                }
                if (s instanceof ModeSetting md) {
                    float fx = right - FIELD_W;
                    float fy = r.y + (ROW_H - 14) / 2f;
                    if (UiRender.inside(tmx, tmy, fx, fy, FIELD_W, 14)) {
                        pressedBtn = r.key;
                        openDropdown(md);
                        return true;
                    }
                }
                if (s instanceof KeySetting k2) {
                    float fx = right - FIELD_W;
                    float fy = r.y + (ROW_H - 14) / 2f;
                    if (UiRender.inside(tmx, tmy, fx, fy, FIELD_W, 14)) {
                        pressedBtn = r.key;
                        capturing = k2;
                        return true;
                    }
                }
                if (s instanceof ColorSetting cs) {
                    pressedBtn = r.key;
                    openPicker(cs);
                    return true;
                }
                return true; // clicked a row body with no control on it
            }
            // click outside the popup closes it
            if (!UiRender.inside(tmx, tmy, popX, popY, popW, popPanelH)) {
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
        dropRow = null;
        dropPushH = 0f;
        dropAnim = 0f;
        capturing = null;
        popScroll = 0f;
        popSmooth = 0f;
        popW = 160;
        float contentH = 0f;
        for (Setting s : mod.settings()) {
            contentH += rowHeight(s);
        }
        popH = 24 + contentH + 10;
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
            pressedBtn = null; // release the shared button press sink
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
        if (settingsView || pickerOpen) {
            return false;
        }
        float osc = openScale();
        double tx = (mouseX - (winX + WIN_W / 2f)) / osc + (winX + WIN_W / 2f);
        double ty = (mouseY - (winY + WIN_H / 2f)) / osc + (winY + WIN_H / 2f);
        dropdown = null;
        // the settings popup clips its rows — the wheel scrolls that viewport
        if (popupModule != null && !popupClosing
                && tx >= popX && tx <= popX + popW && ty >= popY && ty <= popY + popPanelH) {
            popScroll -= (float) verticalAmount * 20f;
            return true;
        }
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
            pressedBtn = CFG_SAVE;
            if (ConfigManager.saveConfig(configName)) {
                NotificationManager.configEvent(configName, "saved");
            }
            configName = "";
            return true;
        }
        for (CfgRow r : cfgRows) {
            if (UiRender.inside(tmx, tmy, r.loadX, r.btnY, 16, 12)) {
                pressedBtn = CFG_LOAD + r.name;
                if (ConfigManager.loadConfig(r.name)) {
                    NotificationManager.configEvent(r.name, "loaded");
                }
                return true;
            }
            if (UiRender.inside(tmx, tmy, r.delX, r.btnY, 16, 12)) {
                pressedBtn = CFG_DEL + r.name;
                if (ConfigManager.deleteConfig(r.name)) {
                    NotificationManager.configEvent(r.name, "deleted");
                }
                return true;
            }
        }
        return true;
    }

    /** Toggles the option list of a select; its geometry follows the row layout. */
    private void openDropdown(ModeSetting md) {
        if (dropdown == md) {
            dropdown = null;
            return;
        }
        dropdown = md;
        dropRow = md;
        dropAnim = 0f;
        dropHoverY = -1f;
        dropHoverA = 0f;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (renameActive) {
            if (!Character.isISOControl(chr) && renameBuf.length() < 18) {
                renameBuf += chr;
                renameBlink = 0f;
            }
            return true;
        }
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
        // the Custom theme is being renamed in place
        if (renameActive) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!renameBuf.isEmpty()) {
                    renameBuf = renameBuf.substring(0, renameBuf.length() - 1);
                    renameBlink = 0f;
                }
            }
            return true;
        }
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
                closePicker();
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
            cancelRename();
            closePicker();
            dropdown = null;
            dropRow = null;
            dropPushH = 0f;
            popupModule = null; // no point animating the popup out with the panel
            popupClosing = false;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
