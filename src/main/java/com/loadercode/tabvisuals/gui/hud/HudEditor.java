// language: Java, file: HudEditor.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.shape.implement.Blur;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-chat HUD position editor (reference mechanics from the HUD source):
 * left-drag an element with an offset, magnetic snapping to the edges and
 * centre with violet guide lines, a hover outline with an X/Y badge, and a
 * right-click menu ("Сбросить позицию" / "Закрыть"). Active only while the
 * chat screen is open and the HUD module's "Interactive editor" is on.
 *
 * <p>The overlay itself follows the LoxeDLC look: a translucent dark scrim
 * (fade + blur ramp), a subtle grid, a rounded outline box with a name label
 * over every element, hover highlight and a lift/scale pop when a box is
 * grabbed. All of that motion is FPS-independent exponential easing
 * ({@code 1 - exp(-dt * k)}, see {@link #ease_exp}) written for this file.
 *
 * <p>Opening is drawn from {@link #overlay} (called by HudRenderer only while
 * chat is open); the closing tail is drawn from a private HUD callback,
 * because HudRenderer stops calling the editor the instant chat closes.
 *
 * <p>Mouse buttons are polled with GLFW (as the reference does), so no screen
 * mixin is needed and the chat field keeps its own clicks. Positions live in
 * {@link #POS} and are persisted through {@link ConfigManager}.
 */
public final class HudEditor {

    public static final String HOTKEYS = "hotkeys";
    public static final String POTIONS = "potions";
    /** Toast stack: Y offset only, X stays locked to the crosshair column. */
    public static final String TOASTS = "toasts";

    private static final float SNAP = 7f;
    /** Chat input band — never start a drag there so the field stays clickable. */
    private static final float CHAT_BAND = 14f;
    private static final float MENU_W = 118f;
    private static final float MENU_H = 54f;

    // reference palette (hud source: violet guides/badges over near-black)
    private static final int GUIDE_C = 0x8C826EFF;   // 130,110,255 @140
    private static final int HOVER_C = 0xA0716EFF;   // 145,110,255 @160
    private static final int BADGE_BG = 0xD712121A;  // (18,18,26) @215
    private static final int BADGE_BORDER = 0x99716EFF;
    private static final int MENU_TEXT = 0xFFD2D7E1;
    private static final int MENU_TITLE = 0xFFF0F2F8;
    private static final int ROW_HOVER = 0x14FFFFFF;

    // easing speeds (k in 1 - exp(-dt*k)); larger = snappier
    private static final float K_OPEN = 14f;    // ~95% за 0.21 с
    private static final float K_CLOSE = 20f;   // закрытие чуть резче открытия
    private static final float K_HOVER = 18f;   // подсветка бокса
    private static final float K_GUIDE = 16f;   // проявление направляющих
    private static final float K_MENU = 22f;    // всплывающее меню
    /** Шаг сетки в scaled-пикселях. */
    private static final float GRID = 16f;
    /** Ниже этой альфы скрим/сетка не рисуются вообще (не видно — не тратим draw). */
    private static final float MIN_VISIBLE = 0.05f;
    private static final float LABEL_H = 13f;
    private static final float BADGE_H = 14f;

    private static final FontRenderer F_BADGE = Fonts.getSize(10, Fonts.Type.SEMI);
    private static final FontRenderer F_MENU = Fonts.getSize(11, Fonts.Type.REGULAR);
    private static final FontRenderer F_MENU_SEMI = Fonts.getSize(11, Fonts.Type.SEMI);

    /** Saved absolute positions: element id → {x, y} (toasts: {yOffset}). */
    private static final Map<String, float[]> POS = new LinkedHashMap<>();
    /** LoxeDLC default coordinates, captured before the first saved move. */
    private static final Map<String, float[]> DEFAULTS = new LinkedHashMap<>();
    /** Bounds registered by the renderer for the current frame. */
    private static final Map<String, float[]> BOUNDS = new LinkedHashMap<>();
    /** Bounds of the previous frame — hit-testing runs before the render pass. */
    private static final Map<String, float[]> LAST = new LinkedHashMap<>();
    /** Per-element hover highlight 0..1 (exponential ease, see ease_exp). */
    private static final Map<String, float[]> HOVER = new HashMap<>();

    private record Guide(boolean vertical, float pos) {
    }

    private static final List<Guide> GUIDES = new ArrayList<>();

    private static String dragging;
    private static String hovered;
    private static String menuFor;
    /** Title of the open menu — kept so the label survives the closing fade. */
    private static String menuLabel;
    private static boolean wasActive;
    private static float grabX, grabY;
    private static float menuX, menuY;
    private static float menuGrabX, menuGrabY;
    private static boolean menuDrag;
    private static boolean lastLeft, lastRight;
    private static float mouseX, mouseY;

    /** Общий прогресс оверлея 0..1 (fade + scale + blur ramp). */
    private static float open;
    /** Проявленность направляющих 0..1. */
    private static float guideAlpha;
    /** Проявленность меню 0..1. */
    private static float menuAlpha;
    /** Наносекунды предыдущего кадра редактора — источник dt. */
    private static long frameNano;
    private static boolean tailRequested;
    private static boolean tailInstalled;

    private HudEditor() {
    }

    // ------------------------------------------------------------- data API

    public static Map<String, float[]> positions() {
        return new LinkedHashMap<>(POS);
    }

    public static void loadPositions(Map<String, float[]> in) {
        POS.clear();
        NotificationManager.yOffset = 0f;
        if (in == null) {
            return;
        }
        for (Map.Entry<String, float[]> e : in.entrySet()) {
            String k = e.getKey();
            // ids from the pre-port editor → current element ids
            if (HOTKEYS.equals(k)) {
                k = "Hot Keys";
            } else if (POTIONS.equals(k)) {
                k = "Potions";
            }
            float[] v = e.getValue();
            if (v == null || v.length == 0 || !isElement(k)) {
                continue;
            }
            boolean finite = true;
            for (float f : v) {
                if (!Float.isFinite(f)) {
                    finite = false;
                    break;
                }
            }
            if (!finite) {
                continue;
            }
            float[] copy = v.length >= 2 ? new float[]{v[0], v[1]} : new float[]{v[0]};
            POS.put(k, copy);
            if (k.equals(TOASTS)) {
                NotificationManager.yOffset = copy[0];
            }
        }
    }

    private static boolean isElement(String k) {
        return k != null && !k.isBlank();
    }

    /** Renderer reports the on-screen box of an element for this frame. */
    static void bind(String id, float x, float y, float w, float h) {
        BOUNDS.put(id, new float[]{x, y, w, h});
    }

    /** Saved position (top-left) or null → the element uses the auto layout. */
    static float[] placed(String id) {
        return POS.get(id);
    }

    /**
     * Saved positions → draggable coordinates, run before the draw loop every
     * frame (so persistence also applies outside the editor). The first
     * sighting of an element captures its constructor position as the default.
     */
    static void pushPositions(float sw, float sh) {
        for (AbstractDraggable d : Hud.getRepository().draggable()) {
            String n = d.getName();
            float[] p = POS.get(n);
            if (p == null) {
                if (!DEFAULTS.containsKey(n)) {
                    DEFAULTS.put(n, new float[]{d.getX(), d.getY()});
                }
                continue;
            }
            float maxX = Math.max(2f, sw - Math.max(8f, d.getWidth()));
            float maxY = Math.max(2f, sh - Math.max(8f, d.getHeight()));
            d.setX((int) clamp(p[0], 2f, maxX));
            d.setY((int) clamp(p[1], 2f, maxY));
        }
    }

    /** A drawn element reports its box; saved entries follow the live coords. */
    static void syncDraggable(AbstractDraggable d) {
        String n = d.getName();
        BOUNDS.put(n, new float[]{d.getX(), d.getY(), d.getWidth(), d.getHeight()});
        if (POS.containsKey(n)) {
            POS.put(n, new float[]{d.getX(), d.getY()});
        }
    }

    // ------------------------------------------------------------ frame flow

    /** True while the chat screen is open and the editor feature is enabled. */
    static boolean shouldRun(MinecraftClient mc) {
        return mc.currentScreen instanceof ChatScreen && HudRenderer.editorEnabled();
    }

    static void beginFrame() {
        LAST.clear();
        LAST.putAll(BOUNDS);
        BOUNDS.clear();
        GUIDES.clear();
        hovered = null;
    }

    /** Rising edge of the editor opening: remember the real button state so a
     * click held in the world before chat opened is not treated as a press. */
    static void setActive(boolean active) {
        request_tail_hook();
        if (active && !wasActive) {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            lastLeft = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            lastRight = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        }
        wasActive = active;
    }

    /**
     * Хвост закрытия рисуется собственным HUD-колбэком. Ставим его из тика,
     * а не сразу: setActive() вызывается прямо во время диспатча HUD-события,
     * и менять список слушателей внутри его же итерации небезопасно. Первый
     * тик гарантированно наступает раньше первого чата, так что колбэк всегда
     * успевает зарегистрироваться — и всегда ПОСЛЕ колбэка HudRenderer,
     * поэтому наш слой ложится поверх элементов.
     */
    private static void request_tail_hook() {
        if (tailRequested) {
            return;
        }
        tailRequested = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (tailInstalled) {
                return;
            }
            tailInstalled = true;
            HudRenderCallback.EVENT.register((ctx, tick) -> on_hud_render(ctx));
        });
    }

    /**
     * Closing tail: HudRenderer stops calling {@link #overlay} the moment the
     * chat screen goes away, so the fade-out lives here. Skipped while the
     * editor is active (that frame already drew through overlay()).
     */
    private static void on_hud_render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (shouldRun(mc)) {
            return; // активный кадр рисует overlay()
        }
        if (mc.player == null || mc.options.hudHidden || !HudRenderer.hudEnabled() || open <= 0f) {
            open = 0f;
            return;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        try {
            // та же высота слоя, что у HUD-элементов (HudRenderer рисует их в z=400)
            m.translate(0.0, 0.0, 400.0);
            draw_editor(m, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), false);
        } finally {
            m.pop();
        }
    }

    static void tick(MinecraftClient mc, float sw, float sh) {
        double scale = mc.getWindow().getScaleFactor();
        mouseX = (float) (mc.mouse.getX() / scale);
        mouseY = (float) (mc.mouse.getY() / scale);
        long handle = mc.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean leftEdge = left && !lastLeft;
        boolean rightEdge = right && !lastRight;
        lastLeft = left;
        lastRight = right;

        if (menuFor != null) {
            tickMenu(mouseX, mouseY, left, leftEdge, sw, sh);
            clampAll(sw, sh);
            return;
        }

        if (leftEdge && mouseY < sh - CHAT_BAND) {
            String hit = hitTest(mouseX, mouseY);
            if (hit != null) {
                float[] b = LAST.get(hit);
                if (b != null) {
                    dragging = hit;
                    grabX = mouseX - b[0];
                    grabY = mouseY - b[1];
                }
            }
        }

        if (dragging != null) {
            if (left) {
                updateDrag(mouseX, mouseY, sw, sh);
            } else {
                dragging = null;
                GUIDES.clear();
                ConfigManager.autosave(); // persist the new position
            }
        } else if (rightEdge) {
            String hit = hitTest(mouseX, mouseY);
            if (hit != null) {
                menuFor = hit;
                menuLabel = labelOf(hit);
                menuDrag = false;
                menuX = clamp(mouseX, 2, sw - MENU_W - 2);
                menuY = clamp(mouseY, 2, sh - MENU_H - 2);
            }
        }

        hovered = dragging != null || left ? null : hitTest(mouseX, mouseY);
        clampAll(sw, sh);
    }

    private static void tickMenu(float mx, float my, boolean left, boolean leftEdge, float sw, float sh) {
        if (menuDrag) {
            if (left) {
                menuX = clamp(mx - menuGrabX, 2, sw - MENU_W - 2);
                menuY = clamp(my - menuGrabY, 2, sh - MENU_H - 2);
            } else {
                menuDrag = false;
            }
            return;
        }
        if (!leftEdge) {
            return;
        }
        if (!UiRender.inside(mx, my, menuX, menuY, MENU_W, MENU_H)) {
            menuFor = null;            // click outside closes
            return;
        }
        if (UiRender.inside(mx, my, menuX + 4, menuY + 20, MENU_W - 8, 14)) {
            POS.remove(menuFor);
            if (menuFor.equals(TOASTS)) {
                NotificationManager.yOffset = 0f;
            } else {
                AbstractDraggable d = Hud.getRepository().get(menuFor);
                float[] def = DEFAULTS.get(menuFor);
                if (d != null && def != null) {
                    d.setX((int) def[0]);
                    d.setY((int) def[1]);
                }
            }
            ConfigManager.autosave();
            menuFor = null;
        } else if (UiRender.inside(mx, my, menuX + 4, menuY + 36, MENU_W - 8, 14)) {
            menuFor = null;
        } else if (my < menuY + 16) {
            menuDrag = true;           // drag area = header
            menuGrabX = mx - menuX;
            menuGrabY = my - menuY;
        }
    }

    private static void updateDrag(float mx, float my, float sw, float sh) {
        float[] b = LAST.get(dragging);
        if (b == null) {
            dragging = null;
            GUIDES.clear();
            return;
        }
        float w = b[2], h = b[3];
        GUIDES.clear();
        boolean magnetic = HudRenderer.setting("Magnetic snapping", true);

        if (dragging.equals(TOASTS)) {
            float top = my - grabY;
            if (magnetic) {
                for (float a : new float[]{8f, sh / 2f - h / 2f, sh - 8f - h}) {
                    if (Math.abs(top - a) < SNAP) {
                        top = a;
                        addGuideY(a, h, sh);
                        break;
                    }
                }
            }
            float baseY = sh / 2f + NotificationManager.START_OFFSET;
            float offset = clamp(top, 4f, sh - h - 4f) - baseY;
            float[] saved = {offset};
            POS.put(TOASTS, saved);
            NotificationManager.yOffset = offset;
            return;
        }

        float nx = mx - grabX;
        float ny = my - grabY;
        if (magnetic) {
            for (float a : new float[]{8f, sw / 2f - w / 2f, sw - 8f - w}) {
                if (Math.abs(nx - a) < SNAP) {
                    nx = a;
                    addGuideX(a, w, sw);
                    break;
                }
            }
            for (float a : new float[]{8f, sh / 2f - h / 2f, sh - 8f - h}) {
                if (Math.abs(ny - a) < SNAP) {
                    ny = a;
                    addGuideY(a, h, sh);
                    break;
                }
            }
        }
        nx = clamp(nx, 2f, Math.max(2f, sw - w - 2f));
        ny = clamp(ny, 2f, Math.max(2f, sh - h - 2f));
        POS.put(dragging, new float[]{nx, ny});
    }

    private static void addGuideX(float anchor, float w, float sw) {
        float line = Math.abs(anchor - 8f) < 0.01f ? 8f
                : Math.abs(anchor - (sw - 8f - w)) < 0.01f ? sw - 8f : sw / 2f;
        GUIDES.add(new Guide(true, line));
    }

    private static void addGuideY(float anchor, float h, float sh) {
        float line = Math.abs(anchor - 8f) < 0.01f ? 8f
                : Math.abs(anchor - (sh - 8f - h)) < 0.01f ? sh - 8f : sh / 2f;
        GUIDES.add(new Guide(false, line));
    }

    /** Keep every saved element inside the screen (resolution changes). */
    private static void clampAll(float sw, float sh) {
        for (Map.Entry<String, float[]> e : POS.entrySet()) {
            float[] v = e.getValue();
            if (e.getKey().equals(TOASTS)) {
                float baseY = sh / 2f + NotificationManager.START_OFFSET;
                v[0] = clamp(v[0], 4f - baseY, sh - NotificationManager.TOAST_H - 4f - baseY);
                continue;
            }
            float[] b = LAST.get(e.getKey());
            if (b != null) {
                v[0] = clamp(v[0], 2f, Math.max(2f, sw - b[2] - 2f));
                v[1] = clamp(v[1], 2f, Math.max(2f, sh - b[3] - 2f));
            }
        }
    }

    private static String hitTest(float mx, float my) {
        for (Map.Entry<String, float[]> e : LAST.entrySet()) {
            float[] b = e.getValue();
            if (UiRender.inside(mx, my, b[0], b[1], b[2], b[3])) {
                return e.getKey();
            }
        }
        return null;
    }

    // -------------------------------------------------------------- overlay

    /** One editor frame while chat is open (called by HudRenderer). */
    static void overlay(MatrixStack m, float sw, float sh) {
        draw_editor(m, sw, sh, true);
    }

    /**
     * Скрим + сетка + боксы + направляющие + меню. {@code interactive} = false
     * на хвосте закрытия: там hover/drag/guides/gaps уже не имеют смысла и
     * вся геометрия просто растворяется.
     */
    private static void draw_editor(MatrixStack m, float sw, float sh, boolean interactive) {
        float dt = frame_dt();
        open = ease_exp(open, interactive ? 1f : 0f, dt, interactive ? K_OPEN : K_CLOSE);
        if (open <= 0.005f) {
            open = 0f;
            return;
        }

        m.push();
        try {
            m.translate(0.0, 0.0, 10.0);   // свой слой поверх элементов HUD (z=400)
            draw_scrim(m, sw, sh);

            // контент проявляется чуть «крупнее» и оседает на место
            float scale = 0.975f + 0.025f * open;
            m.push();
            try {
                m.translate(sw / 2.0, sh / 2.0, 0.0);
                m.scale(scale, scale, 1f);
                m.translate(-sw / 2.0, -sh / 2.0, 0.0);

                draw_grid(m, sw, sh);
                draw_boxes(m, sh, interactive, dt);
                draw_guides(m, sw, sh, dt);
                draw_menu(m, interactive, dt);
            } finally {
                m.pop();
            }
        } finally {
            m.pop();
        }
    }

    /**
     * Тёмный скрим LoxeDLC ({@link HudTheme#scrimColor()}) с fade-ramp'ом;
     * при включённом Blur фон ещё и размывается — альфа цвета формы
     * переключает смесь «резкий кадр ↔ размытый», поэтому blur проявляется
     * синхронно с затемнением (это и есть blur ramp).
     */
    private static void draw_scrim(MatrixStack m, float sw, float sh) {
        if (open < MIN_VISIBLE) {
            return;
        }
        int scrim = HudTheme.multAlpha(HudTheme.scrimColor(), open);
        if (!HudRenderer.blurEnabled()) {
            UiRender.rect(m, 0, 0, sw, sh, 0, scrim);
            return;
        }
        int quality = Math.max(6, HudRenderer.blurSamples());
        // Скриму нужен обычный blur: стеклянный шейдер анимирован (блики по Time)
        // и на полноэкранный заливке выглядит мерцанием. Статус возвращается
        // сразу после вызова, внутри того же кадра других draw-вызовов нет.
        boolean glass = Blur.glassMode;
        Blur.glassMode = false;
        try {
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, 0, 0, sw, sh)
                    .round(0f)
                    .quality(quality)
                    .color(scrim, scrim, scrim, scrim)
                    .build(), quality);
        } finally {
            Blur.glassMode = glass;
        }
    }

    /** Едва заметная сетка: тот же фиолетовый акцент, что у направляющих. */
    private static void draw_grid(MatrixStack m, float sw, float sh) {
        int c = HudTheme.multAlpha(GUIDE_C, 0.085f * open);
        if (((c >>> 24) & 0xFF) == 0) {
            return;
        }
        for (float x = GRID; x < sw - 0.5f; x += GRID) {
            UiRender.rect(m, x, 0, 1, sh, 0, c);
        }
        for (float y = GRID; y < sh - 0.5f; y += GRID) {
            UiRender.rect(m, 0, y, sw, 1, 0, c);
        }
    }

    /** Rounded outline box + name label per element; hover/lift on top. */
    private static void draw_boxes(MatrixStack m, float sh, boolean interactive, float dt) {
        HOVER.keySet().retainAll(BOUNDS.keySet());
        for (Map.Entry<String, float[]> e : BOUNDS.entrySet()) {
            String id = e.getKey();
            float[] b = e.getValue();
            if (b[2] < 1f || b[3] < 1f) {
                continue;
            }
            boolean on = interactive && (id.equals(dragging) || id.equals(hovered));
            float[] h = HOVER.computeIfAbsent(id, k -> new float[1]);
            h[0] = ease_exp(h[0], on ? 1f : 0f, dt, K_HOVER);

            AbstractDraggable d = find_draggable(id);
            float lift = d == null ? 0f : d.ease_lift(interactive && id.equals(dragging), dt);
            draw_box(m, b, id, h[0], lift, sh);
        }
    }

    private static AbstractDraggable find_draggable(String id) {
        for (AbstractDraggable d : Hud.getRepository().draggable()) {
            if (d.getName().equalsIgnoreCase(id)) {
                return d;
            }
        }
        return null;
    }

    private static void draw_box(MatrixStack m, float[] b, String id, float hover, float lift, float sh) {
        float x = b[0], y = b[1], w = b[2], h = b[3];
        // «подъём» при схватывании: бокс растёт вокруг центра и приподнимается,
        // под ним проступает тень — это и есть snap/lift-анимация
        float s = 1f + 0.05f * lift;
        float bw = w * s;
        float bh = h * s;
        float bx = x + (w - bw) / 2f;
        float by = y + (h - bh) / 2f - 1.5f * lift;

        if (lift > 0.01f) {
            UiRender.rect(m, bx + 2f, by + 3f, bw, bh, HudTheme.PANEL_ROUND,
                    UiRender.mulAlpha(0xFF000000, 0.40f * lift * open));
        }
        if (hover > 0.01f) {
            UiRender.rect(m, bx, by, bw, bh, HudTheme.PANEL_ROUND,
                    UiRender.mulAlpha(HOVER_C, 0.12f * hover * open));
        }
        UiRender.outline(m, bx, by, bw, bh, HudTheme.PANEL_ROUND,
                UiRender.mulAlpha(HOVER_C, (0.32f + 0.68f * hover) * open), 1f);

        draw_label(m, id, bx, by, hover, lift);
        if (hover > 0.02f) {
            draw_coord(m, x, y, w, h, hover * open, sh);
        }
    }

    /** Name tag над боксом (внутрь — если сверху нет места). */
    private static void draw_label(MatrixStack m, String id, float bx, float by, float hover, float lift) {
        String text = labelOf(id);
        if (text == null || text.isEmpty()) {
            return;
        }
        float lw = F_BADGE.getStringWidth(text) + 10f;
        float ly = by - LABEL_H - 3f - 2f * lift;
        if (ly < 2f) {
            ly = by + 3f;
        }
        float a = open * (0.55f + 0.45f * hover);
        UiRender.rect(m, bx, ly, lw, LABEL_H, 3.5f, UiRender.mulAlpha(BADGE_BG, a));
        UiRender.outline(m, bx, ly, lw, LABEL_H, 3.5f, UiRender.mulAlpha(BADGE_BORDER, a), 1f);
        // x фиксирован: getStringWidth для коротких строк завышает ширину
        UiRender.text(F_BADGE, m, text, bx + 5f,
                ly + (LABEL_H - F_BADGE.getStringHeight(text)) / 2f + 1f,
                HudTheme.multAlpha(HudTheme.text(), a * 0.95f));
    }

    /** X/Y badge под боксом — для подсвеченного или схваченного элемента. */
    private static void draw_coord(MatrixStack m, float x, float y, float w, float h, float alpha, float sh) {
        String label = "X: " + (int) Math.round(x) + "   Y: " + (int) Math.round(y);
        float bw = F_BADGE.getStringWidth(label) + 10f;
        float by = y + h + 4f;
        if (by + BADGE_H > sh - 2f) {
            by = y - BADGE_H - 4f;
        }
        if (by < 2f) {
            by = Math.max(2f, y + h - BADGE_H);
        }
        UiRender.rect(m, x, by, bw, BADGE_H, 3.5f, UiRender.mulAlpha(BADGE_BG, alpha));
        UiRender.outline(m, x, by, bw, BADGE_H, 3.5f, UiRender.mulAlpha(BADGE_BORDER, alpha), 1f);
        UiRender.text(F_BADGE, m, label, x + 5f,
                by + (BADGE_H - F_BADGE.getStringHeight(label)) / 2f + 1f,
                HudTheme.multAlpha(0xFFF5F5F8, alpha));
    }

    private static void draw_guides(MatrixStack m, float sw, float sh, float dt) {
        guideAlpha = ease_exp(guideAlpha, GUIDES.isEmpty() ? 0f : 1f, dt, K_GUIDE);
        float a = open * guideAlpha;
        if (a <= 0.01f) {
            return;
        }
        int c = UiRender.mulAlpha(GUIDE_C, a);
        for (Guide g : GUIDES) {
            if (g.vertical()) {
                UiRender.rect(m, g.pos() - 0.5f, 0, 1, sh, 0, c);
            } else {
                UiRender.rect(m, 0, g.pos() - 0.5f, sw, 1, 0, c);
            }
        }
    }

    private static void draw_menu(MatrixStack m, boolean interactive, float dt) {
        menuAlpha = ease_exp(menuAlpha, interactive && menuFor != null ? 1f : 0f, dt, K_MENU);
        if (menuAlpha <= 0.01f || menuLabel == null) {
            return;
        }
        float a = open * menuAlpha;
        float s = 0.92f + 0.08f * menuAlpha;
        float cx = menuX + MENU_W / 2f;
        float cy = menuY + MENU_H / 2f;
        m.push();
        try {
            m.translate(cx, cy, 0.0);
            m.scale(s, s, 1f);
            m.translate(-cx, -cy, 0.0);

            UiRender.rect(m, menuX, menuY, MENU_W, MENU_H, 6f, UiRender.mulAlpha(BADGE_BG, a));
            UiRender.outline(m, menuX, menuY, MENU_W, MENU_H, 6f, UiRender.mulAlpha(BADGE_BORDER, a), 1f);

            String title = menuLabel;
            UiRender.text(F_MENU_SEMI, m, title, menuX + 8f,
                    menuY + 4f + (12f - F_MENU_SEMI.getStringHeight(title)) / 2f + 1f,
                    UiRender.mulAlpha(MENU_TITLE, a));

            draw_menu_row(m, menuY + 20f, "Сбросить позицию", a, interactive);
            draw_menu_row(m, menuY + 36f, "Закрыть", a, interactive);
        } finally {
            m.pop();
        }
    }

    private static void draw_menu_row(MatrixStack m, float ry, String label, float alpha, boolean interactive) {
        boolean hov = interactive
                && UiRender.inside(mouseX, mouseY, menuX + 4, ry, MENU_W - 8, 14);
        if (hov) {
            UiRender.rect(m, menuX + 4, ry, MENU_W - 8, 14, 3f, ROW_HOVER);
        }
        UiRender.text(F_MENU, m, label, menuX + 8f,
                ry + (14f - F_MENU.getStringHeight(label)) / 2f + 1f,
                UiRender.mulAlpha(MENU_TEXT, alpha));
    }

    private static String labelOf(String id) {
        if (id == null) {
            return null;
        }
        return switch (id) {
            case HOTKEYS -> "HotKeys";
            case POTIONS -> "Active Potions";
            case TOASTS -> "Notifications";
            default -> id;
        };
    }

    // ------------------------------------------------------------ easing/dt

    /**
     * FPS-independent easing: the value glides to {@code target} with
     * {@code 1 - exp(-dt * k)} — same wall-clock speed at 30 and 300 FPS,
     * no overshoot, no per-frame constants. Snaps once it is visually there
     * so the final scale/alpha is exact (boxes must align with elements).
     */
    private static float ease_exp(float current, float target, float dt, float k) {
        float step = 1f - (float) Math.exp(-clamp(dt, 0f, 0.05f) * k);
        float next = current + (target - current) * step;
        return Math.abs(target - next) < 0.0015f ? target : next;
    }

    /** Seconds since the previous overlay frame (0.016 on the first frame). */
    private static float frame_dt() {
        long now = System.nanoTime();
        long prev = frameNano;
        frameNano = now;
        return prev == 0L ? 0.016f : (now - prev) / 1_000_000_000f;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }
}
