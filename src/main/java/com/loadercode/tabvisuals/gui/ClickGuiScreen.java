// language: Java, file: ClickGuiScreen.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.hud.HudRenderer;
import com.loadercode.tabvisuals.gui.hud.NotificationManager;
import com.loadercode.tabvisuals.gui.icon.Icons;
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
 * (draggable by its header with LMB or anywhere with MMB, ease-back open),
 * a left-docked icon rail with the client logo and category tabs, a header
 * with the category glyph + name, a search field for RENDER/MISC, a two-column
 * module card grid (name / description / settings glyphs / toggle pill /
 * bind chip; right-click opens the settings popup), a compact Configs tab and
 * the theme picker behind the rail gear.
 *
 * <p>Window position is persisted through ConfigManager (guiX/guiY) and clamped
 * on load, so a resolution change never loses the window off-screen; while it
 * is being dragged a faint MOVE_H glyph rides the cursor.
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

    // --- fonts (LoxeDLC sizes) — icon fonts are routed through Icons, not these ---
    private static final FontRenderer F_TITLE = Fonts.getSize(15, Fonts.Type.DEFAULT);
    private static final FontRenderer F_HEAD = Fonts.getSize(15, Fonts.Type.SEMI);
    private static final FontRenderer F_BODY = Fonts.getSize(12, Fonts.Type.REGULAR);
    private static final FontRenderer F_SEMI = Fonts.getSize(12, Fonts.Type.SEMI);
    private static final FontRenderer F_BOLD = Fonts.getSize(12, Fonts.Type.BOLD);
    private static final FontRenderer F_TINY = Fonts.getSize(11, Fonts.Type.REGULAR);

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

    // --- shared radii: one recipe per surface size (windows/cards 10, small fields 4, pills h/2) ---
    private static final float WIN_R = 10f;
    private static final float CARD_R = 10f;
    private static final float FIELD_R = 4f;

    // --- header / drag zones ---
    /** Header band height — the top bar with the title; drag handle of the window. */
    private static final float HEAD_H = 28f;
    /** Slice of the header that must stay on screen (px) — the window can never be lost. */
    private static final float GRAB_MIN = 40f;

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
    /** Search field text with the caret — rebuilt only when the query/focus changes. */
    private String searchText = "";
    private String searchTextQuery = "\0";
    private boolean searchTextFocus;

    // --- window position, kept as screen percents like LoxeDLC ---
    private float winX, winY;
    private float offXP = 0.5f, offYP = 0.5f;
    private int lastW, lastH;
    private boolean dragging;
    /** Button that owns the current window drag (0 = header LMB, 2 = MMB anywhere). */
    private int dragButton = -1;
    private double dragOffX, dragOffY;
    /** Eased alpha of the MOVE_H cursor hint (full while dragging, faint on header hover). */
    private float dragHint;
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
    /** Wrapped card descriptions per module id — descriptions never change at runtime. */
    private final Map<String, List<String>> descCache = new HashMap<>();
    /** Card height per module id, derived from the cached wrap. */
    private final Map<String, Float> cardHeightCache = new HashMap<>();
    private final List<ThemeRow> themeRows = new ArrayList<>();
    private final List<CfgRow> cfgRows = new ArrayList<>();
    private final ScissorAssist scissor = new ScissorAssist();
    private float contentHeight;
    private float viewportH;
    /** Eased counterpart of the current category scroll (draw + hit-test). */
    private float smoothScroll;
    private float searchX, searchY, searchW = 80, searchH = 15;
    private float gearX, gearY, gearSize = 20;

    /** Visible categories — the module registry is static, so the list is built once. */
    private List<Category> visibleCats;
    /** Filtered module list + the (tab, query) it was built for — no rebuild per frame. */
    private final List<ModuleStub> filteredMods = new ArrayList<>();
    private Category filteredCat;
    private String filteredQ = null;
    /** Hot-path string caches: bind-chip width, key labels, slider read-outs. */
    private final Map<String, Float> chipWidthCache = new HashMap<>();
    private final Map<Integer, String> keyLabelCache = new HashMap<>();
    private final Map<String, String> valueLabelCache = new HashMap<>();
    private final Map<String, Float> valueLabelFor = new HashMap<>();
    /** Popup title, truncated once in openPopup instead of every frame. */
    private String popTitle = "";
    /** Picker hex label cache — rebuilt only when the edited colour actually moves. */
    private String hexCache = "";
    private int hexCacheColor;
    private boolean hexCacheValid;
    /** Config name field focus glow 0..1 (the search field's recipe). */
    private float cfgFocusAnim;

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
    /** Config file names cached in-tab — the disk is read on switch/save/delete, not per frame. */
    private final List<String> cfgNames = new ArrayList<>();

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
    /** Config card row hover — keyed by name, pruned with the button anims. */
    private static final String CFG_ROW = "cfgrow:";

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
            refreshConfigList(); // re-read the disk once per tab switch, not per frame
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

        // window position tracks the screen size by percent (LoxeDLC); the
        // first placement comes from the persisted guiX/guiY, clamped so a
        // smaller resolution (or GUI scale) never starts the window off-screen
        int cw = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int ch = MinecraftClient.getInstance().getWindow().getScaledHeight();
        if (lastW != cw || lastH != ch) {
            if (lastW != 0 && lastH != 0) {
                winX = cw * offXP - WIN_W / 2f;
                winY = ch * offYP - WIN_H / 2f;
            } else {
                float[] saved = ConfigManager.guiPosition();
                if (saved != null) {
                    winX = saved[0];
                    winY = saved[1];
                } else {
                    winX = cw / 2f - WIN_W / 2f;
                    winY = ch / 2f - WIN_H / 2f;
                }
                offXP = (winX + WIN_W / 2f) / cw;
                offYP = (winY + WIN_H / 2f) / ch;
            }
            lastW = cw;
            lastH = ch;
            clampWindowPos();
        }

        openFade = clamp01(openT);
        float sc = openScale();
        // same transform the input handlers apply — one source of truth for draw + hit-test
        updateTransformedMouse(mouseX, mouseY);

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

        // drag cursor hint: a faint MOVE_H rides the pointer while it hovers the
        // draggable header, and holds steady while the window is actually carried
        float hintTarget = 0f;
        if (!closing && openT > 0.9f && !settingsView && settingsAnim <= 0.01f
                && !pickerOpen && pickerAnim <= 0.01f) {
            if (dragging) {
                hintTarget = 0.85f;
            } else if (headerHover()) {
                hintTarget = 0.45f;
            }
        }
        dragHint = approach(dragHint, hintTarget, dragging ? 22f : 12f);
        if (dragHint > 0.01f) {
            UiRender.dragHint(m, mouseX, mouseY, 13,
                    UiRender.mulAlpha(UiRender.mix(t.text(), t.accent(), 0.45f), dragHint * openFade));
        }

        // toasts drawn above the overlay; animation is driven here only when
        // the in-game HUD pass is not running (no world → no HUD frame)
        HudRenderer.renderToasts(MinecraftClient.getInstance(), m, width, height, MinecraftClient.getInstance().player == null);

        FontRenderer.flushDeferred();
    }

    /** Main window: drop shadow + blur + glass panel + hairline + header (LoxeDLC BackgroundComponent). */
    private void renderWindow(MatrixStack m, Theme t, float openA) {
        // soft shadow first, so the blur pass and the panel paint over it
        UiRender.shadow(m, winX, winY, WIN_W, WIN_H, WIN_R, 0.42f * openA);
        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, winX, winY, WIN_W, WIN_H)
                    .round(WIN_R)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, winX, winY, WIN_W, WIN_H, WIN_R, UiRender.mulAlpha(t.panel(), openA));
        UiRender.outline(m, winX, winY, WIN_W, WIN_H, WIN_R, UiRender.mulAlpha(t.outline(), openA), 1f);

        // header: category glyph in the 13px gutter, title on the shared text column;
        // both are centred on the title's own optical centre so they read as one line
        int headCol = UiRender.mulAlpha(t.text(), openA);
        String headTitle = activeCategory.displayName();
        float titleY = winY + 12;
        float titleMid = titleY - 3f + F_TITLE.getStringHeight(headTitle) * 0.25f;
        UiRender.iconCentered(m, catGlyph(activeCategory), winX + GRID_X + 6.5f, titleMid, 18, headCol);
        F_TITLE.drawString(m, headTitle, winX + GRID_X + 15, titleY, headCol);

        // grip: three hairlines in the header's optical centre — the quiet
        // "this window is draggable" affordance; brightens under the cursor
        boolean gripHover = headerHover();
        int gripCol = UiRender.mulAlpha(t.text(), (gripHover ? 90f : 46f) / 255f * openA);
        float gripCx = winX + WIN_W / 2f;
        UiRender.hairline(m, gripCx - 5f, titleMid - 2.5f, 10f, gripCol);
        UiRender.hairline(m, gripCx - 5f, titleMid, 10f, gripCol);
        UiRender.hairline(m, gripCx - 5f, titleMid + 2.5f, 10f, gripCol);

        UiRender.hairline(m, winX + GRID_X, winY + HEAD_H, WIN_W - GRID_X,
                UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 140), openA));

        renderRail(m, t);
    }

    /**
     * Category tab glyph, routed through Icons. The per-tab visual stays
     * LoxeDLC-faithful: PLAYER keeps the person glyph ('D' → CAT_TARGET) and
     * MISC keeps the box glyph ('E' → CAT_WORLD), exactly as Category.icon().
     */
    private static Icons.Glyph catGlyph(Category c) {
        return switch (c) {
            case COMBAT -> Icons.CAT_COMBAT;
            case PLAYER -> Icons.CAT_TARGET;
            case MOVEMENT -> Icons.CAT_MOVEMENT;
            case RENDER -> Icons.CAT_VISUAL;
            case MISC -> Icons.CAT_WORLD;
            case CONFIGS -> Icons.CAT_CONFIGS;
        };
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

        UiRender.shadow(m, railX, railY, RAIL_W, railH, WIN_R, 0.32f * openFade);
        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, railX, railY, RAIL_W, railH)
                    .round(WIN_R)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, railX, railY, RAIL_W, railH, WIN_R, UiRender.mulAlpha(t.panel(), 0.98f * openFade));
        UiRender.outline(m, railX, railY, RAIL_W, railH, WIN_R, UiRender.mulAlpha(t.outline(), openFade), 1f);

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
                UiRender.rect(m, tx, ty, TAB, TAB, 5, UiRender.mulAlpha(t.cardHover(), openFade));
            }
            float sel = approach(tabSelAnim.getOrDefault(c, 0f), isActive ? 1f : 0f, 13f);
            tabSelAnim.put(c, sel);
            int col = UiRender.mix(UiRender.mix(t.textSecondary(), t.text(), hover ? 1f : 0f), t.accent(), sel);
            UiRender.iconCentered(m, catGlyph(c), tx + TAB / 2f, ty + TAB / 2f, 18,
                    UiRender.mulAlpha(col, openFade));
            tabs.add(new TabRect(c, tx, ty, TAB, TAB));
            ty += TAB + TAB_SP;
        }

        // gear at the bottom of the rail — the theme overlay
        gearX = railX + (RAIL_W - gearSize) / 2f;
        gearY = railY + railH - 26;
        float ghv = easeHover("rail:gear", UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize));
        float gpr = easePress("rail:gear", "rail:gear".equals(pressedBtn));
        pushScale(m, gearX + gearSize / 2f, gearY + gearSize / 2f, UiRender.pressScale(gpr));
        int gearBg = settingsView ? UiRender.mulAlpha(t.accent(), 0.85f)
                : UiRender.mix(t.card(), t.cardHover(), Math.max(ghv, 0.6f * gpr));
        UiRender.rect(m, gearX, gearY, gearSize, gearSize, 5, UiRender.mulAlpha(gearBg, openFade));
        int gearCol = settingsView ? 0xFFFFFFFF : UiRender.mix(t.textSecondary(), t.text(), ghv);
        UiRender.iconCentered(m, Icons.GEAR, gearX + gearSize / 2f, gearY + gearSize / 2f, 12,
                UiRender.mulAlpha(gearCol, openFade));
        m.pop();
    }

    /** Two-column card grid (LoxeDLC CategoryComponent): top-anchored, scrolls down. */
    private void renderModules(MatrixStack m) {
        Theme t = Theme.get();

        // search field — RENDER/MISC only, like LoxeDLC; the outline eases in on focus
        if (searchVisible()) {
            searchFocusAnim = approach(searchFocusAnim, searchFocused ? 1f : 0f, 14f);
            searchX = winX + WIN_W - 16 - searchW;
            searchY = winY + 7.5f;
            float sa = openFade;
            UiRender.rect(m, searchX, searchY, searchW, searchH, FIELD_R, UiRender.mulAlpha(t.card(), sa));
            int searchStroke = UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(), searchFocusAnim);
            UiRender.outline(m, searchX, searchY, searchW, searchH, FIELD_R, UiRender.mulAlpha(searchStroke, sa), 1);
            boolean empty = search.isEmpty() && !searchFocused;
            // the lens eases to the accent while the field holds the keyboard
            int lensCol = UiRender.mix(UiRender.mix(t.textSecondary(), t.text(), empty ? 0f : 1f),
                    t.accent(), searchFocusAnim);
            UiRender.iconCentered(m, Icons.SEARCH, searchX + searchW - 9.5f, searchY + searchH / 2f, 13,
                    UiRender.mulAlpha(lensCol, sa));
            UiRender.rect(m, searchX + searchW - 19, searchY + 3.5f, 0.5f, 8, 0,
                    UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 160), sa));
            // the caret string is rebuilt only when the query or the focus flips
            if (!search.equals(searchTextQuery) || searchFocused != searchTextFocus) {
                searchTextQuery = search;
                searchTextFocus = searchFocused;
                // обрезка под поле: справа ~26px заняты разделителем и линзой, каретка после обрезки
                float maxW = searchW - 26f - (searchFocused ? F_TINY.getStringWidth("|") : 0f);
                searchText = truncate(F_TINY, search, maxW) + (searchFocused ? "|" : "");
            }
            F_TINY.drawString(m, empty ? "Поиск" : searchText,
                    searchX + 6, centerBoxY(F_TINY, "Aa", searchY, searchH),
                    UiRender.mulAlpha(empty ? t.textSecondary() : t.text(), sa));
        } else {
            search = "";
            searchFocused = false; // no field on this tab — don't keep capturing keys
        }

        // filtered modules: by category, or by name across all when searching —
        // the result is cached and only rebuilt when the tab or the query changes
        String q = search.toLowerCase(Locale.ROOT);
        List<ModuleStub> mods = filteredMods;
        if (activeCategory != filteredCat || !q.equals(filteredQ)) {
            filteredCat = activeCategory;
            filteredQ = q;
            mods.clear();
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
        contentHeight = Math.max(0f, Math.max(colY[0], colY[1]) - CARD_GAP);
        viewportH = WIN_H - GRID_TOP - 6;
        if (capturingMod != null) {
            boolean stillVisible = false;
            for (CardBounds cb : layout) {
                if (cb.module == capturingMod) {
                    stillVisible = true;
                    break;
                }
            }
            if (!stillVisible) {
                capturingMod = null; // its card left the grid (tab switch / filter) — stop capturing invisibly
            }
        }

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
            // trails the previous one, sliding up and fading in. hitCard()
            // applies the same offset, so clicks land on what is on screen.
            m.push();
            m.translate(0, entranceOffset(ci), 0);
            drawCard(m, cb, smoothScroll, entranceProgress(ci));
            m.pop();
        }
        scissor.pop();

        if (mods.isEmpty()) {
            // centred empty state: icon over the hint, in the middle of the grid band
            boolean noQuery = search.isEmpty();
            String hint = noQuery ? "Нет модулей в этой категории"
                    : "Ничего не найдено: " + truncate(F_BODY, search, 130);
            float emptyCx = winX + GRID_X + (WIN_W - GRID_X - 6) / 2f;
            float emptyMidY = winY + GRID_TOP + viewportH / 2f - 8f;
            UiRender.iconCentered(m, noQuery ? Icons.DASHBOARD : Icons.SEARCH, emptyCx, emptyMidY, 18,
                    UiRender.mulAlpha(t.textSecondary(), 0.7f * openFade));
            FontRenderer.drawCentered(F_BODY, m, hint, emptyCx,
                    centerBoxY(F_BODY, hint, emptyMidY + 12f, 14f),
                    UiRender.mulAlpha(t.textSecondary(), 0.95f * openFade));
        }

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
        String[] words = desc.split(" ");
        for (String word : words) {
            String try2 = cur.isEmpty() ? word : cur + " " + word;
            if (F_BODY.getStringWidth(try2) > maxWidth && !cur.isEmpty()) {
                if (lines.size() == 2) {
                    // Third (last) line is already as full as it gets and this
                    // word still doesn't fit — the description is cut off. Put
                    // an ellipsis on the last visible line and drop the rest.
                    String base = cur.toString().stripTrailing();
                    lines.add(base + "…");
                    return lines;
                }
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(try2);
            }
        }
        if (!cur.isEmpty() && lines.size() < 3) {
            lines.add(cur.toString());
        }
        return lines;
    }

    /** LoxeDLC module card: name, wrapped description, separator, glyphs, pill, bind. */
    private float cardHeight(ModuleStub mod) {
        Float cached = cardHeightCache.get(mod.id());
        if (cached != null) {
            return cached;
        }
        int lines = Math.max(1, wrapDesc(mod.description(), CARD_W - 30).size());
        float h = 47 + (lines - 1) * 8;
        cardHeightCache.put(mod.id(), h);
        return h;
    }

    /** Wrapped description of a card; cached because the text is immutable. */
    private List<String> descLines(ModuleStub mod) {
        List<String> cached = descCache.get(mod.id());
        if (cached == null) {
            cached = List.copyOf(wrapDesc(mod.description(), CARD_W - 30));
            descCache.put(mod.id(), cached);
        }
        return cached;
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
        UiRender.rect(m, x, y, w, h, CARD_R, UiRender.mulAlpha(fill, alpha));
        float glow = Math.max(hoverK, k * 0.7f);
        int stroke = UiRender.mix(UiRender.withAlpha(t.outline(), 110), UiRender.withAlpha(t.accent(), 150), glow);
        UiRender.outline(m, x, y, w, h, CARD_R, UiRender.mulAlpha(stroke, alpha), 1f);

        // name: bullet in the glyph gutter, text on the description's left
        // column (x+15) — name and description read as one aligned block
        int nameCol = UiRender.mulAlpha(UiRender.mix(UiRender.withAlpha(t.text(), 170), t.text(), k), alpha);
        F_TITLE.drawString(m, "•", x + 10f - F_TITLE.getStringWidth("•") / 2f, y + 6, nameCol);
        F_TITLE.drawString(m, truncate(F_TITLE, mod.name(), w - 15 - 8), x + 15, y + 6, nameCol);

        // description: first line tagged with the info glyph, continuations aligned
        // to the same text column so the block reads straight
        List<String> lines = descLines(mod);
        int descCol = UiRender.mulAlpha(UiRender.withAlpha(t.textSecondary(), 186), alpha);
        int glyphCol = UiRender.mulAlpha(t.textSecondary(), alpha);
        float dy = y + 19;
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                iconOnLine(m, Icons.INFO, F_BODY, 11, x + 10f, dy, glyphCol);
            }
            F_BODY.drawString(m, lines.get(i), x + 15, dy, descCol);
            dy += 8;
        }

        // separator above the bottom row
        float sepY = y + h - 20;
        UiRender.hairline(m, x, sepY, w, UiRender.mulAlpha(UiRender.withAlpha(t.outline(), 120), alpha));

        // bottom row: settings glyphs left, bind chip, toggle pill right —
        // glyphs are centered on the same band the chip and pill sit in
        float rowY = y + h - 16;
        float bandMidY = y + h - 10;
        UiRender.iconCentered(m, Icons.SLIDERS, x + 10.5f, bandMidY, 16, glyphCol);
        if (!mod.settings().isEmpty()) {
            UiRender.iconCentered(m, Icons.GEAR, x + 24f, bandMidY, 12, glyphCol);
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

        // bind chip: key label, or the chain glyph when unbound; it eases its
        // hover and breathes in accent while it waits for a key
        String bindName = capturingMod == mod ? "..." : keyLabel(mod.bind());
        float chipW = chipWidth(bindName);
        float chipX = x + w - 32 - chipW;
        boolean chipCapturing = capturingMod == mod;
        float chipHover = easeHover("chip:" + id, UiRender.inside(tmx, tmy, chipX, rowY + 1, chipW, 10));
        float capPulse = chipCapturing ? 0.6f + 0.4f * (float) Math.sin(animTime * 6.0) : 1f;
        int chipBg = UiRender.mix(UiRender.withAlpha(t.text(), 80), UiRender.withAlpha(t.accent(), 120),
                Math.max(chipHover, chipCapturing ? 0.55f * capPulse : 0f));
        UiRender.rect(m, chipX, rowY + 1, chipW, 10, 5, UiRender.mulAlpha(chipBg, alpha));
        if (mod.bind() < 0 && !chipCapturing) {
            UiRender.iconCentered(m, Icons.LINK, chipX + chipW / 2f, rowY + 6f, 10, glyphCol);
        } else {
            F_BODY.drawString(m, bindName, chipX + 4, centerBoxY(F_BODY, bindName, rowY + 1, 10),
                    chipCapturing ? UiRender.mulAlpha(t.accent(), capPulse * alpha) : glyphCol);
        }
        m.pop();
    }

    /** Right-click settings popup (LoxeDLC ModuleSettingsWindow). */
    private void renderPopup(MatrixStack m, Theme t) {
        // ease the entrance (slide-in + grow) and the mirrored exit
        popupAnim = approach(popupAnim, popupClosing ? 0f : 1f, 18f);
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
        float slide = (1f - e) * 18f;
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
        float psc = 0.90f + 0.10f * e;
        m.scale(psc, psc, 1f);
        m.translate(-anchorX, -anchorY, 0f);

        // soft shadow rides the same slide/scale as the panel
        UiRender.shadow(m, popX, popY, popW, popPanelH, WIN_R, 0.42f * a);
        if (HudRenderer.blurEnabled()) {
            QuickImports.blur.setup();
            int tint = UiRender.mulAlpha(t.background(), 0.55f * a);
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, popX, popY, popW, popPanelH)
                    .round(WIN_R)
                    .quality(32)
                    .color(tint, tint, tint, tint)
                    .build(), 32);
        }
        UiRender.rect(m, popX, popY, popW, popPanelH, WIN_R, UiRender.mulAlpha(t.panel(), 0.98f * a));
        UiRender.outline(m, popX, popY, popW, popPanelH, WIN_R, UiRender.mulAlpha(t.outline(), a), 1f);

        // header: "<Module> Settings" + close button, hairline underneath (y+22)
        float popTitleY = popY + 8;
        F_HEAD.drawString(m, popTitle, popX + 10, popTitleY, UiRender.mulAlpha(t.text(), a));
        // close: a real 16x16 hover box with the X-in-circle glyph, sunk on press
        float closeX = popX + popW - 19;
        float closeY = popY + 4;
        float chv = easeHover("pop:close", UiRender.inside(tmx, tmy, closeX, closeY, 16, 16) && !popupClosing);
        float cpr = easePress("pop:close", "pop:close".equals(pressedBtn));
        pushScale(m, closeX + 8f, closeY + 8f, UiRender.pressScale(cpr));
        if (chv > 0.01f) {
            UiRender.rect(m, closeX, closeY, 16, 16, FIELD_R, UiRender.mulAlpha(t.text(), 0.08f * chv * a));
        }
        UiRender.iconCentered(m, Icons.CLOSE, closeX + 8f, closeY + 8f, 11,
                UiRender.mulAlpha(UiRender.mix(t.textSecondary(), t.text(), chv), 0.85f * a));
        // header hairline rides the same slide/scale as the panel it belongs to
        UiRender.hairline(m, popX + 10, popY + 22, popW - 20,
                UiRender.mulAlpha(t.text(), 0.2f * a));
        m.pop();

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
            UiRender.iconCentered(m, Icons.BOX, x + ROW_GLYPH_X + 3.5f, y + base / 2f, 11, dimCol);
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
            UiRender.iconCentered(m, Icons.SLIDERS, x + ROW_GLYPH_X + 3.5f, y + base / 2f, 11, dimCol);
            String nm = truncate(F_BODY, d.name(), w - ROW_TEXT_X - TRACK_W - 16);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);

            // the read-out counts up/down with the fill instead of snapping;
            // the formatted label is cached per row and only rebuilt when it moves
            float shown = approach(valueAnim.getOrDefault(row.key, (float) d.get()), (float) d.get(), 16f);
            valueAnim.put(row.key, shown);
            Float forVal = valueLabelFor.get(row.key);
            String val = forVal != null && forVal.floatValue() == shown
                    ? valueLabelCache.get(row.key) : null;
            if (val == null) {
                val = formatValue(d, shown);
                valueLabelCache.put(row.key, val);
                valueLabelFor.put(row.key, shown);
            }
            FontRenderer.drawRight(F_BOLD, m, val, right, centerBoxY(F_BOLD, val, y, base),
                    UiRender.mulAlpha(t.accent(), a));

            // fill and knob lag the drag (k≈18); hovering the track lifts the
            // knob and dragging adds the accent glow on top of that
            float frac = approach(sliderAnim.getOrDefault(row.key, d.fraction()), d.fraction(), 18f);
            sliderAnim.put(row.key, frac);
            float trackX = right - TRACK_W;
            float trackY = y + 15;
            float trkHv = easeHover(row.key + ":trk",
                    UiRender.inside(tmx, tmy, trackX - 3, trackY - 5, TRACK_W + 6, 13) && row.alpha > 0.5f);
            UiRender.rect(m, trackX, trackY, TRACK_W, 3, 1.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(t.text(), 30), a));
            UiRender.rect(m, trackX, trackY, Math.max(3f, TRACK_W * frac), 3, 1.5f,
                    UiRender.mulAlpha(t.accent(), a));
            float thX = trackX + TRACK_W * frac - 3.5f;
            float thY = trackY - 2f;
            if (dragging2 == d) {
                UiRender.glow(m, thX + 3.5f, thY + 3.5f, 11f, t.accent() & 0xFFFFFF, 0.30f, a);
            }
            int knobCol = UiRender.lighten(t.accent(), 0.16f * Math.max(trkHv, dragging2 == d ? 1f : 0f));
            UiRender.rect(m, thX, thY, 7, 7, 3.5f, UiRender.mulAlpha(knobCol, a));
            UiRender.outline(m, thX, thY, 7, 7, 3.5f,
                    UiRender.mulAlpha(UiRender.withAlpha(0xFFFFFFFF, 170), a), 1.1f);
        } else if (s instanceof ModeSetting md) {
            UiRender.iconCentered(m, Icons.ELLIPSIS, x + ROW_GLYPH_X + 3.5f, y + base / 2f, 11, dimCol);
            String nm = truncate(F_BODY, md.name(), w - ROW_TEXT_X - FIELD_W - 16);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);
            drawSelectField(m, t, right - FIELD_W, y + (ROW_H - 14) / 2f, FIELD_W,
                    md.get(), dropdown == md, a, row.key);
        } else if (s instanceof KeySetting k2) {
            UiRender.iconCentered(m, Icons.KEYBOARD, x + ROW_GLYPH_X + 3.5f, y + base / 2f, 11, dimCol);
            String nm = truncate(F_BODY, k2.name(), w - ROW_TEXT_X - FIELD_W - 16);
            F_BODY.drawString(m, nm, x + ROW_TEXT_X, centerBoxY(F_BODY, nm, y, base), textCol);
            drawBindField(m, t, right - FIELD_W, y + (ROW_H - 14) / 2f, FIELD_W, k2, a, row.key);
        } else if (s instanceof ColorSetting cs) {
            // colour row: the BRUSH glyph in the gutter is tinted with the live
            // colour (readable even at 11px, unlike a bare dot); swatch on the right
            UiRender.iconCentered(m, Icons.BRUSH, x + ROW_GLYPH_X + 3.5f, y + base / 2f, 11,
                    UiRender.mulAlpha(UiRender.lighten(cs.get(), 0.22f), a));
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
        UiRender.rect(m, fx, fy, fw, 14, FIELD_R, UiRender.mulAlpha(bg, a));
        int stroke = UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(), cap ? pulse : 0f);
        UiRender.outline(m, fx, fy, fw, 14, FIELD_R, UiRender.mulAlpha(stroke, a), cap ? 1.3f : 1f);
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
        UiRender.rect(m, fx, fy, fw, 14, FIELD_R, UiRender.mulAlpha(bg, a));
        // hover lifts the hairline toward the accent a quarter of the way
        UiRender.outline(m, fx, fy, fw, 14, FIELD_R, UiRender.mulAlpha(
                UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(),
                        Math.max(active ? 1f : 0f, 0.35f * hv)), a), 1);
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
        UiRender.shadow(m, dropX, dropY, dropW, listH, WIN_R, 0.25f * a);
        UiRender.rect(m, dropX, dropY, dropW, listH, WIN_R, UiRender.mulAlpha(t.panel(), a));
        UiRender.outline(m, dropX, dropY, dropW, listH, WIN_R,
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
        cfgFocusAnim = approach(cfgFocusAnim, configNameFocused ? 1f : 0f, 14f);
        UiRender.rect(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, FIELD_R, t.card());
        UiRender.outline(m, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH, FIELD_R,
                UiRender.mix(UiRender.withAlpha(t.outline(), 155), t.accent(), cfgFocusAnim), 1);
        boolean empty = configName.isEmpty() && !configNameFocused;
        F_TINY.drawString(m, empty ? "имя конфига..." : configName + (configNameFocused ? "|" : ""),
                cfgFieldX + 6, centerBoxY(F_TINY, "Aa", cfgFieldY, cfgFieldH),
                empty ? t.textSecondary() : t.text());

        // save button (LoxeDLC: floppy glyph + label) — shared accent recipe
        float saveW = 52;
        float saveX = winX + WIN_W - 16 - saveW;
        boolean saveHover = UiRender.inside(tmx, tmy, saveX, cfgFieldY, saveW, cfgFieldH);
        drawButton(m, F_TINY, CFG_SAVE, saveX, cfgFieldY, saveW, cfgFieldH, FIELD_R, "Save",
                saveHover, t.accent(), 0xFFFFFFFF, true);
        // the icon rides the same sink drawButton just eased
        pushScale(m, saveX + saveW / 2f, cfgFieldY + cfgFieldH / 2f,
                UiRender.pressScale(btnPressAnim.getOrDefault(CFG_SAVE, 0f)));
        UiRender.iconCentered(m, Icons.SAVE, saveX + 11, cfgFieldY + cfgFieldH / 2f, 11, 0xFFFFFFFF);
        m.pop();

        // config cards, two per row — the list is the cached snapshot
        // (refreshed on tab switch and after every save/load/delete)
        List<String> names = cfgNames;
        cfgTotal = names.size();
        float listTop = winY + 58;
        float listH = Math.max(0, WIN_H - 58 - 6);
        cfgListH = listH;
        float rowStep = 48;
        float maxScroll = Math.max(0, (float) Math.ceil(cfgTotal / 2.0) * rowStep - listH);
        cfgScroll = Math.min(Math.max(0, cfgScroll), maxScroll);

        if (names.isEmpty()) {
            // centred empty state: folder icon over the hint
            String hint = "Пока нет конфигов";
            float emptyCx = winX + GRID_X + (WIN_W - GRID_X - 6) / 2f;
            float emptyMidY = listTop + Math.max(0f, listH) / 2f - 8f;
            UiRender.iconCentered(m, Icons.FOLDER, emptyCx, emptyMidY, 18,
                    UiRender.mulAlpha(t.textSecondary(), 0.7f));
            FontRenderer.drawCentered(F_BODY, m, hint, emptyCx,
                    centerBoxY(F_BODY, hint, emptyMidY + 12f, 14f), UiRender.mulAlpha(t.textSecondary(), 0.95f));
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
            float rowHover = easeHover("cfgrow:" + name, UiRender.inside(tmx, tmy, cx2, screenY, CARD_W, 40));
            UiRender.rect(m, cx2, cy2, CARD_W, 40, CARD_R, UiRender.mix(t.card(), t.cardHover(), rowHover));
            UiRender.outline(m, cx2, cy2, CARD_W, 40, CARD_R, UiRender.withAlpha(t.outline(), 110), 1f);
            // folder marks the row as a file; the name keeps the shared text column
            UiRender.iconCentered(m, Icons.FOLDER, cx2 + 14, cy2 + 10, 12,
                    UiRender.mix(t.accent(), t.text(), rowHover * 0.4f));
            String cfgLabel = truncate(F_SEMI, name, CARD_W - 32);
            F_SEMI.drawString(m, cfgLabel, cx2 + 24, centerBoxY(F_SEMI, cfgLabel, cy2, 20), t.text());

            float btnY = cy2 + 22;
            // load (play) + delete (trash) icon buttons — body from drawButton,
            // the glyph rides the same eased press sink on top
            float loadX = cx2 + CARD_W - 38;
            float delX = cx2 + CARD_W - 20;
            boolean lh = UiRender.inside(tmx, tmy, loadX, btnY, 16, 12);
            boolean dh = UiRender.inside(tmx, tmy, delX, btnY, 16, 12);
            int loadFg = UiRender.mix(t.textSecondary(), t.accent(), easeHover(CFG_LOAD + name, lh));
            int delFg = UiRender.mix(t.textSecondary(), 0xFFFF6B81, easeHover(CFG_DEL + name, dh));
            drawButton(m, null, CFG_LOAD + name, loadX, btnY, 16, 12, FIELD_R, "", lh,
                    UiRender.withAlpha(t.text(), 30), loadFg, false);
            pushScale(m, loadX + 8, btnY + 6, UiRender.pressScale(btnPressAnim.getOrDefault(CFG_LOAD + name, 0f)));
            UiRender.iconCentered(m, Icons.PLAY, loadX + 8, btnY + 6, 9, loadFg);
            m.pop();
            drawButton(m, null, CFG_DEL + name, delX, btnY, 16, 12, FIELD_R, "", dh,
                    UiRender.withAlpha(t.text(), 30), delFg, false);
            pushScale(m, delX + 8, btnY + 6, UiRender.pressScale(btnPressAnim.getOrDefault(CFG_DEL + name, 0f)));
            UiRender.iconCentered(m, Icons.TRASH, delX + 8, btnY + 6, 10, delFg);
            m.pop();
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

        UiRender.shadow(m, px, py, pw, ph, WIN_R, 0.4f * a);
        UiRender.rect(m, px, py, pw, ph, WIN_R, UiRender.mulAlpha(t.panel(), a));
        UiRender.outline(m, px, py, pw, ph, WIN_R, UiRender.mulAlpha(t.outline(), a), 1);
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

            // hover lifts the tile a hair, press sinks it; the active theme keeps an accent ring
            pushScale(m, tx + tw / 2f, ry + tileH / 2f, UiRender.pressScale(pr) * (1f + 0.018f * hv));
            int fill = UiRender.mix(t.card(), t.cardHover(), hv);
            fill = UiRender.mix(fill, UiRender.mix(fill, th.accent(), 0.16f), sel);
            UiRender.rect(m, tx, ry, tw, tileH, CARD_R, UiRender.mulAlpha(fill, a));
            int stroke = UiRender.mix(UiRender.withAlpha(t.outline(), 110), t.accent(),
                    Math.max(sel, hv * 0.45f));
            UiRender.outline(m, tx, ry, tw, tileH, CARD_R, UiRender.mulAlpha(stroke, a), 1f + 0.5f * sel);

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
                drawButton(m, F_TINY, "theme:ren", ax, ay, REN_W, ACTION_H, FIELD_R, "Переим.",
                        UiRender.inside(mouseX, mouseY, ax, ay, REN_W, ACTION_H),
                        t.cardHover(), t.text(), false);
                float bx = tx + DELETE_X;
                drawButton(m, F_TINY, "theme:del", bx, ay, DEL_W, ACTION_H, FIELD_R, "Сброс",
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
        // the rename owns the keyboard — drop any other input owner
        capturing = null;
        capturingMod = null;
        searchFocused = false;
        configNameFocused = false;
    }

    private void commitRename() {
        if (!renameActive) {
            return;
        }
        String clean = renameBuf.trim();
        if (!clean.isEmpty()) {
            customName = clean.length() > 18 ? clean.substring(0, 18) : clean;
        } else {
            // a cleared field rejects the rename — the old name stays, but the
            // commit must not look like it was swallowed
            NotificationManager.push("Theme ", customName, " rename ", "failed", 0xFFFF6B81);
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

        UiRender.shadow(m, pickX, pickY, pickW, pickH, WIN_R, 0.45f * a);
        UiRender.rect(m, pickX, pickY, pickW, pickH, WIN_R, UiRender.mulAlpha(t.panel(), 0.99f * a));
        UiRender.outline(m, pickX, pickY, pickW, pickH, WIN_R, UiRender.mulAlpha(t.outline(), a), 1);

        // title in the label column, current value right-aligned in the value column
        // (both centred on the title's optical centre, so the line reads level)
        boolean slotMode = pickerColor == null;
        int color = currentPickColor();
        String title = slotMode ? "Цвет слота" : pickerColor.name();
        title = truncate(F_TITLE, title, pickW - 96);
        float pickTitleY = pickY + 9;
        float titleMid = pickTitleY - 3f + F_TITLE.getStringHeight(title) * 0.25f;
        F_TITLE.drawString(m, title, pickX + 10, pickTitleY, UiRender.mulAlpha(t.text(), a));
        String rightCol = slotMode ? slotDisplayName() : hexLabel(color);
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
        String hex = hexLabel(color);
        F_TINY.drawString(m, hex, pickX + 32, centerBoxY(F_TINY, hex, fy + 2, 16),
                UiRender.mulAlpha(t.textSecondary(), a));

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

    /** "#RRGGBB" of the edited colour — cached, re-formatted on change, not per frame. */
    private String hexLabel(int color) {
        if (!hexCacheValid || hexCacheColor != color) {
            hexCacheColor = color;
            hexCacheValid = true;
            hexCache = String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        }
        return hexCache;
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

    /**
     * Transforms a raw screen mouse into the window's open-scale space.
     * Called from render AND from every input handler, so a click that lands
     * between two frames is hit-tested against exactly what was drawn.
     */
    private void updateTransformedMouse(double mouseX, double mouseY) {
        float sc = openScale();
        float cx = winX + WIN_W / 2f, cy = winY + WIN_H / 2f;
        tmx = (mouseX - cx) / sc + cx;
        tmy = (mouseY - cy) / sc + cy;
    }

    /** The header search field only exists on RENDER/MISC (LoxeDLC) — the one test for draw, hint and clicks. */
    private boolean searchVisible() {
        return activeCategory == Category.RENDER || activeCategory == Category.MISC;
    }

    /**
     * True when the transformed cursor sits on the draggable header band, minus
     * the search field (its clicks belong to the field). The single source of
     * truth for the grip hover, the MOVE_H hint and the LMB drag hit rect —
     * the drawn header and the drag zone are literally the same test.
     */
    private boolean headerHover() {
        return UiRender.inside(tmx, tmy, winX, winY, WIN_W, HEAD_H)
                && !(searchVisible() && UiRender.inside(tmx, tmy, searchX, searchY, searchW, searchH));
    }

    /** Keeps a grabbable {@link #GRAB_MIN} slice of the window on screen — the header can never be lost. */
    private void clampWindowPos() {
        winX = Math.max(-(WIN_W - GRAB_MIN), Math.min(width - GRAB_MIN, winX));
        winY = Math.max(-(WIN_H - GRAB_MIN), Math.min(height - GRAB_MIN, winY));
    }

    /**
     * Ends the current window drag (LMB header or MMB anywhere): refreshes the
     * resolution-tracking percents and persists the position through the
     * throttled autosave. Every release/Esc/close path funnels here, and it is
     * a no-op without an active drag, so a drag can never outlive its gesture.
     */
    private void endDrag() {
        if (!dragging) {
            return;
        }
        dragging = false;
        dragButton = -1;
        int cw = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int ch = MinecraftClient.getInstance().getWindow().getScaledHeight();
        offXP = (winX + WIN_W / 2f) / cw;
        offYP = (winY + WIN_H / 2f) / ch;
        ConfigManager.rememberGuiPosition(winX, winY); // dirty-flag autosave: writes at most once per interval
    }

    /** 0..1 entrance progress of the card at index (staggered after a tab switch). */
    private float entranceProgress(int index) {
        float p = clamp01((gridT - index * 0.035f) / 0.26f);
        return 1f - (1f - p) * (1f - p) * (1f - p);
    }

    /** Slide-up offset (px) paired with the entrance progress. */
    private float entranceOffset(int index) {
        return (1f - entranceProgress(index)) * 10f;
    }

    /** Width of a card's bind chip — shared by the draw and the hit test, cached per label. */
    private float chipWidth(String bindName) {
        Float cached = chipWidthCache.get(bindName);
        if (cached != null) {
            return cached;
        }
        float w = Math.max(12f, F_BODY.getStringWidth(bindName)) + 8;
        if (chipWidthCache.size() > 256) {
            chipWidthCache.clear(); // free-form key names must not grow the cache forever
        }
        chipWidthCache.put(bindName, w);
        return w;
    }

    /** Re-reads the config list from disk and drops anims of buttons that vanished with it. */
    private void refreshConfigList() {
        cfgNames.clear();
        cfgNames.addAll(ConfigManager.listConfigs());
        pruneButtonAnims();
    }

    /**
     * Config buttons are keyed by config name — deleting configs would grow
     * btnHoverAnim/btnPressAnim forever, so stale keys are dropped on refresh.
     */
    private void pruneButtonAnims() {
        btnHoverAnim.keySet().removeIf(this::staleCfgAnim);
        btnPressAnim.keySet().removeIf(this::staleCfgAnim);
    }

    private boolean staleCfgAnim(String key) {
        if (key.startsWith(CFG_LOAD)) {
            return !cfgNames.contains(key.substring(CFG_LOAD.length()));
        }
        if (key.startsWith(CFG_DEL)) {
            return !cfgNames.contains(key.substring(CFG_DEL.length()));
        }
        return key.startsWith(CFG_ROW) && !cfgNames.contains(key.substring(CFG_ROW.length()));
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

    /** Glyph centred on cx, sitting on the same optical line as text drawn at drawY with font f. */
    private static void iconOnLine(MatrixStack m, Icons.Glyph glyph, FontRenderer font, float size,
                                   float cx, float drawY, int argb) {
        Icons.draw(m, glyph, cx - Icons.width(glyph, size) / 2f,
                UiRender.iconLineY(font, glyph, size, drawY), size, argb);
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

    /** Categories that hold modules, plus the Configs tab — empty ones are hidden. Built once (the registry is static). */
    private List<Category> visibleCategories() {
        if (visibleCats == null) {
            List<Category> out = new ArrayList<>();
            for (Category c : Category.values()) {
                if (c == Category.CONFIGS || !ModuleRegistry.byCategory(c).isEmpty()) {
                    out.add(c);
                }
            }
            if (out.isEmpty()) {
                out.add(Category.CONFIGS);
            }
            visibleCats = out;
        }
        return visibleCats;
    }

    /** Key name for the bind chip; delegates to {@link KeySetting#keyLabel}
     *  (cached — the chip is drawn per card per frame). F-keys/arrows used to
     *  fall through to "K290" here because glfwGetKeyName returns null for them. */
    private String keyLabel(int key) {
        if (key < 0) {
            return "";
        }
        String cached = keyLabelCache.get(key);
        if (cached == null) {
            cached = com.loadercode.tabvisuals.gui.setting.KeySetting.keyLabel(key);
            if (keyLabelCache.size() > 256) {
                keyLabelCache.clear(); // bounded by keycodes anyway; this is a hard stop
            }
            keyLabelCache.put(key, cached);
        }
        return cached;
    }

    /** Ease-out-back — the LoxeDLC window pop (slight overshoot, no bounce). */
    private static float easeOutBack(float p) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float x = clamp01(p) - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private static float clamp01(float v) {
        // NaN-safe like UiRender.clamp01: NaN slips both comparisons and would
        // poison the alpha/scale/transform it feeds
        if (Float.isNaN(v)) {
            return 0f;
        }
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
        // hit-test against the geometry of THIS event, not of the last frame
        updateTransformedMouse(mouseX, mouseY);
        // MMB drag of the window, LoxeDLC-style — same settle gate as LMB and the
        // same modal gate as the MOVE_H hint, so a grab never starts against the
        // still-scaled entrance geometry or from under the theme panel / picker
        if (button == 2 && !dragging && !closing && openT > 0.9f
                && !settingsView && settingsAnim <= 0.01f && !pickerOpen && pickerAnim <= 0.01f
                && tmx >= winX - 20 && tmx <= winX + WIN_W + 20 && tmy >= winY && tmy <= winY + WIN_H) {
            dragging = true;
            dragButton = 2;
            dragOffX = tmx - winX;
            dragOffY = tmy - winY;
            closePopup(); // the popup is anchored to the window and would be left behind
            return true;
        }
        // right-click on a card opens its settings popup (LoxeDLC ModuleSettingsWindow)
        if (button == 1) {
            // the picker is a modal — right-clicks must not leak to the cards behind it
            if (closing || openT < 0.9f || settingsView || pickerOpen || pickerAnim > 0.01f) {
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

        // gate on the ease-out too: clicks must not fall through to the grid
        // while the overlay is still fading over it
        if (settingsView || settingsAnim > 0.01f) {
            if (UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize)) {
                pressedBtn = "rail:gear";
                commitRename(); // closing via the gear must not lose the typed name
                settingsView = false;
                return true;
            }
            for (SwatchHit s : swatchHits) {
                if (s.slot() != null && UiRender.inside(mouseX, mouseY, s.x(), s.y(), s.w(), s.h())) {
                    commitRename(); // otherwise the rename keeps eating key input under the picker
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

        // a held drag owns every further button — a second press must not retarget it
        if (dragging) {
            return true;
        }

        // drag the window by its header with the LEFT button (MMB still works
        // anywhere); the search field keeps its clicks, and a grab drops the
        // popup — it is anchored to the window and would be left behind
        if (headerHover()) {
            dragging = true;
            dragButton = 0;
            dragOffX = tmx - winX;
            dragOffY = tmy - winY;
            closePopup();
            return true;
        }

        if (dropdown != null && dropRow != null) {
            String[] opts = dropRow.options();
            // the list is drawn inside the popup's clip — a click on the part
            // clipped away by the viewport must not pick an invisible option
            boolean inViewport = tmy >= popViewTop && tmy <= popViewTop + popViewH;
            if (inViewport && UiRender.inside(tmx, tmy, dropX, dropY, dropW, Math.max(4f, dropPushH))) {
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
                    capturing = null;
                    capturingMod = null;
                    closePopup(); // the popup was anchored to the previous tab's grid
                }
                return true;
            }
        }

        // gear → theme overlay
        if (UiRender.inside(tmx, tmy, gearX, gearY, gearSize, gearSize)) {
            pressedBtn = "rail:gear";
            settingsView = !settingsView;
            return true;
        }

        if (activeCategory == Category.CONFIGS) {
            return clickConfigs(mouseX, mouseY);
        }

        // search field (RENDER/MISC)
        if (searchVisible()) {
            if (UiRender.inside(tmx, tmy, searchX, searchY, searchW, searchH)) {
                searchFocused = true;
                configNameFocused = false;
                // one key event must not feed both a capture and a text field
                capturing = null;
                capturingMod = null;
                return true;
            }
            searchFocused = false;
        }

        // settings popup rows first — they sit above the grid
        if (popupModule != null && !popupClosing) {
            // same 16x16 box the close button draws in renderPopup
            if (UiRender.inside(tmx, tmy, popX + popW - 19, popY + 4, 16, 16)) {
                pressedBtn = "pop:close";
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
                        searchFocused = false; // the captured key must not also type into a field
                        configNameFocused = false;
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
            // click outside the popup closes it; a click inside its body but
            // not on a control is swallowed — it must never reach the grid behind
            if (!UiRender.inside(tmx, tmy, popX, popY, popW, popPanelH)) {
                closePopup();
            }
            return true;
        }

        // module cards — pill / bind chip / toggle
        CardBounds hit = hitCard();
        if (hit != null) {
            ModuleStub mod = hit.module;
            pressedId = mod.id(); // press sink animation, cleared on release
            lastPressedId = pressedId;
            // same Y the card is drawn at this frame (scroll + entrance slide)
            float y = hit.y - smoothScroll + entranceOffset(layout.indexOf(hit));
            // toggle pill on the right of the bottom row
            if (UiRender.inside(tmx, tmy, hit.x + hit.w - 26, y + hit.h - 16, 20, 12)) {
                mod.toggle();
                NotificationManager.moduleToggle(mod.name(), mod.enabled());
                return true;
            }
            // bind chip → capture a key (same label/width the card draws right now)
            String bindName = capturingMod == mod ? "..." : keyLabel(mod.bind());
            float chipW = chipWidth(bindName);
            float chipX = hit.x + hit.w - 32 - chipW;
            if (UiRender.inside(tmx, tmy, chipX, y + hit.h - 16, chipW, 12)) {
                capturingMod = mod;
                searchFocused = false; // the captured key must not also type into a field
                configNameFocused = false;
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
        for (int ci = 0; ci < layout.size(); ci++) {
            CardBounds cb = layout.get(ci);
            // the drawn card carries the entrance slide — hit-test the same rect
            float y = cb.y - scroll + entranceOffset(ci);
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
        popTitle = truncate(F_HEAD, mod.name() + " Settings", popW - 42); // immutable per module — not worth re-measuring per frame
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

    /** Ends the window drag on the release of the button that owns it — even outside the window. */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == dragButton) {
            endDrag();
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
        updateTransformedMouse(mouseX, mouseY);
        // the owning button drives the move — LMB header drags and MMB drags
        // share one path; the position follows the cursor with no easing (no lag)
        if (dragging && button == dragButton) {
            winX = (float) (tmx - dragOffX);
            winY = (float) (tmy - dragOffY);
            clampWindowPos();
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
        if (closing) {
            return true;
        }
        // gate on the fade-outs too — the wheel must not scroll the grid
        // underneath a still-visible overlay or picker
        if (settingsView || settingsAnim > 0.01f || pickerOpen || pickerAnim > 0.01f) {
            return true;
        }
        updateTransformedMouse(mouseX, mouseY);
        double tx = tmx;
        double ty = tmy;
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

    /**
     * One toast per config action, keyed to the real result: a failed
     * save/load/delete must not look like a dead click. The caller passes the
     * config the click actually touched (captured before any buffer or list is
     * cleared), so a toast can never describe a different config than the one
     * the action ran on.
     */
    private static void notifyConfig(String name, String okVerb, boolean ok) {
        String label = name == null ? "" : name.trim();
        if (label.isEmpty()) {
            label = "..."; // a rejected empty name still owes the user an answer
        }
        if (ok) {
            NotificationManager.configEvent(label, okVerb);
            return;
        }
        String action = switch (okVerb) {
            case "saved" -> "save";
            case "loaded" -> "load";
            default -> "delete";
        };
        NotificationManager.push("Config ", label, " failed to ", action, 0xFFFF6B81);
    }

    private boolean clickConfigs(double mouseX, double mouseY) {
        if (UiRender.inside(tmx, tmy, cfgFieldX, cfgFieldY, cfgFieldW, cfgFieldH)) {
            configNameFocused = true;
            searchFocused = false;
            capturing = null;
            capturingMod = null;
            return true;
        }
        configNameFocused = false;
        float saveW = 52;
        float saveX = winX + WIN_W - 16 - saveW;
        if (UiRender.inside(tmx, tmy, saveX, cfgFieldY, saveW, cfgFieldH)) {
            pressedBtn = CFG_SAVE;
            String name = configName; // the toast names the config this click saved
            notifyConfig(name, "saved", ConfigManager.saveConfig(name));
            configName = "";
            refreshConfigList(); // a new file may have appeared on disk
            return true;
        }
        // buttons of rows clipped by the list viewport are not clickable
        float listTop = winY + 58;
        for (CfgRow r : cfgRows) {
            boolean inView = r.btnY >= listTop && r.btnY + 12 <= listTop + cfgListH;
            if (!inView) {
                continue;
            }
            if (UiRender.inside(tmx, tmy, r.loadX, r.btnY, 16, 12)) {
                pressedBtn = CFG_LOAD + r.name;
                notifyConfig(r.name, "loaded", ConfigManager.loadConfig(r.name));
                return true;
            }
            if (UiRender.inside(tmx, tmy, r.delX, r.btnY, 16, 12)) {
                pressedBtn = CFG_DEL + r.name;
                notifyConfig(r.name, "deleted", ConfigManager.deleteConfig(r.name));
                refreshConfigList(); // the row is gone — refresh before the next frame's hit list
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
        if (capturing != null || capturingMod != null) {
            return true; // the key belongs to the bind capture, not to a text field
        }
        if (Character.isISOControl(chr)) {
            return false;
        }
        if (searchFocused && activeCategory != Category.CONFIGS && search.length() < 24) {
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
                    String name = configName;
                    notifyConfig(name, "saved", ConfigManager.saveConfig(name));
                    configName = "";
                    configNameFocused = false;
                    refreshConfigList(); // the new file must appear in the list
                }
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (dragging) {
                endDrag(); // Esc must never leave the window glued to the cursor
                return true;
            }
            if (pickerOpen) {
                closePicker();
                return true;
            }
            if (settingsView) {
                settingsView = false;
                return true;
            }
            if (dropdown != null) {
                dropdown = null; // first Esc closes the list, second closes the popup
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
        // the screen can be swapped mid-interaction (disconnect, F11…) —
        // release every held input state before the autosave; endDrag also
        // persists the window position the interrupted drag had reached
        endDrag();
        dragging2 = null;
        pickerDrag = 0;
        pressedId = null;
        pressedBtn = null;
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
            // no held input may outlive the screen (stuck press / drag / capture);
            // endDrag persists wherever the drag had reached
            endDrag();
            dragging2 = null;
            pickerDrag = 0;
            pressedId = null;
            pressedBtn = null;
            searchFocused = false;
            configNameFocused = false;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
