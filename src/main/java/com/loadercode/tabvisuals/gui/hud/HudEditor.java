// language: Java, file: HudEditor.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.config.ConfigManager;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
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
 * <p>Mouse buttons are polled with GLFW (as the reference does), so no screen
 * mixin is needed and the chat field keeps its own clicks. Positions live in
 * {@link #POS} and are persisted through {@link ConfigManager}.
 */
public final class HudEditor {

    public static final String HOTKEYS = "hotkeys";
    public static final String POTIONS = "potions";
    /** Legacy static staff panel id → the Staff List draggable name. */
    public static final String STAFF = "staff";
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

    private record Guide(boolean vertical, float pos) {
    }

    private static final List<Guide> GUIDES = new ArrayList<>();

    private static String dragging;
    private static String hovered;
    private static String menuFor;
    private static boolean wasActive;
    private static float grabX, grabY;
    private static float menuX, menuY;
    private static float menuGrabX, menuGrabY;
    private static boolean menuDrag;
    private static boolean lastLeft, lastRight;
    private static float mouseX, mouseY;

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
            } else if (STAFF.equals(k)) {
                // the staff panel became the Staff List draggable — keep the position
                k = "Staff List";
            }
            float[] v = e.getValue();
            if (v == null || v.length == 0 || !isElement(k)) {
                continue;
            }
            // cleanup: the old loader remapped "staff" to a key nothing reads
            if ("Staff list".equals(k)) {
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
        if (active && !wasActive) {
            long handle = MinecraftClient.getInstance().getWindow().getHandle();
            lastLeft = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            lastRight = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        }
        wasActive = active;
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

    static void overlay(MatrixStack m, float sw, float sh) {
        for (Guide g : GUIDES) {
            if (g.vertical()) {
                UiRender.rect(m, g.pos() - 0.5f, 0, 1, sh, 0, GUIDE_C);
            } else {
                UiRender.rect(m, 0, g.pos() - 0.5f, sw, 1, 0, GUIDE_C);
            }
        }

        String target = dragging != null ? dragging : hovered;
        if (target != null) {
            float[] b = BOUNDS.get(target);
            if (b != null) {
                UiRender.outline(m, b[0], b[1], b[2], b[3], 4.5f, HOVER_C, 1f);
                drawBadge(m, b[0], b[1], b[3]);
            }
        }

        if (menuFor != null) {
            drawMenu(m);
        }
    }

    private static void drawBadge(MatrixStack m, float x, float y, float h) {
        String label = "X: " + (int) Math.round(x) + "   Y: " + (int) Math.round(y);
        float bw = F_BADGE.getStringWidth(label) + 10f;
        float bh = 14f;
        float by = y - bh - 4f;
        if (by < 2f) {
            by = y + h + 4f;
        }
        UiRender.rect(m, x, by, bw, bh, 3.5f, BADGE_BG);
        UiRender.outline(m, x, by, bw, bh, 3.5f, BADGE_BORDER, 1f);
        UiRender.text(F_BADGE, m, label, x + 5f,
                by + (bh - F_BADGE.getStringHeight(label)) / 2f + 1f, 0xFFF5F5F8);
    }

    private static void drawMenu(MatrixStack m) {
        UiRender.rect(m, menuX, menuY, MENU_W, MENU_H, 6f, BADGE_BG);
        UiRender.outline(m, menuX, menuY, MENU_W, MENU_H, 6f, BADGE_BORDER, 1f);

        String title = labelOf(menuFor);
        UiRender.text(F_MENU_SEMI, m, title, menuX + 8f,
                menuY + 4f + (12f - F_MENU_SEMI.getStringHeight(title)) / 2f + 1f, MENU_TITLE);

        drawMenuRow(m, menuY + 20f, "Сбросить позицию");
        drawMenuRow(m, menuY + 36f, "Закрыть");
    }

    private static void drawMenuRow(MatrixStack m, float ry, String label) {
        boolean hov = UiRender.inside(mouseX, mouseY, menuX + 4, ry, MENU_W - 8, 14);
        if (hov) {
            UiRender.rect(m, menuX + 4, ry, MENU_W - 8, 14, 3f, ROW_HOVER);
        }
        UiRender.text(F_MENU, m, label, menuX + 8f,
                ry + (14f - F_MENU.getStringHeight(label)) / 2f + 1f, MENU_TEXT);
    }

    private static String labelOf(String id) {
        return switch (id) {
            case HOTKEYS -> "HotKeys";
            case POTIONS -> "Active Potions";
            case STAFF -> "Staff Online";
            case TOASTS -> "Notifications";
            default -> id;
        };
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }
}
