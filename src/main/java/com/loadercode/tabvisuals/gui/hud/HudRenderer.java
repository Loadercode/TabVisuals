// language: Java, file: HudRenderer.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.module.ModuleRegistry;
import com.loadercode.tabvisuals.gui.module.ModuleStub;
import com.loadercode.tabvisuals.gui.setting.BooleanSetting;
import com.loadercode.tabvisuals.gui.setting.DoubleSetting;
import com.loadercode.tabvisuals.gui.setting.ModeSetting;
import com.loadercode.tabvisuals.gui.setting.Setting;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.shape.implement.Blur;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.gui.hud.draggable.Draggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.util.Network;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ConcurrentModificationException;

/**
 * HUD frame driver — a faithful port of the LoxeDLC HUD feature: the whole
 * element set drawn on the shared render stack with the LoxeDLC theme engine
 * ({@link HudTheme}), the blur/glass pipeline and the in-chat position editor
 * ({@link HudEditor}). Panel metrics
 * follow the reference exactly: 15.5px header plate + body plate, round 4,
 * 0.1 thickness, theme gradient + hairline outline.
 */
public final class HudRenderer {

    // ------------------------------------------------------------- element ids
    public static final String WATERMARK = "watermark";
    public static final String INVENTORY = "inventory";
    public static final String HOTKEYS = "hotkeys";
    public static final String POTIONS = "potions";
    public static final String TARGETHUD = "targethud";
    public static final String BINDS = "binds";
    public static final String COOLDOWNS = "cooldowns";
    public static final String SCOREBOARD = "scoreboard";
    public static final String MEDIAPLAYER = "mediaplayer";
    public static final String ARMOR = "armor";
    public static final String TOTEM = "totem";
    public static final String KEYSTROKES = "keystrokes";
    public static final String EPR = "epr";
    public static final String TOASTS = "toasts";
    /** Anchored elements — follow the screen, not draggable. */
    public static final String HOTBAR = "hotbar";
    public static final String ISLAND = "island";
    public static final String PLAYERINFO = "playerinfo";
    public static final String SATURATION = "saturation";

    /** Draggable element ids → editor labels (setting name of the element). */
    public static final Map<String, String> ELEMENTS = new LinkedHashMap<>();

    static {
        ELEMENTS.put(WATERMARK, "Watermark");
        ELEMENTS.put(INVENTORY, "Inventory");
        ELEMENTS.put(HOTKEYS, "HotKeys");
        ELEMENTS.put(POTIONS, "Potions");
        ELEMENTS.put(TARGETHUD, "Target Hud");
        ELEMENTS.put(BINDS, "Binds");
        ELEMENTS.put(COOLDOWNS, "Cool Downs");
        ELEMENTS.put(TOASTS, "Notifications");
        ELEMENTS.put(SCOREBOARD, "Scoreboard");
        ELEMENTS.put(MEDIAPLAYER, "Media Player");
        ELEMENTS.put(ARMOR, "Armor");
        ELEMENTS.put(TOTEM, "Totem Counter");
        ELEMENTS.put(KEYSTROKES, "Keystrokes");
        ELEMENTS.put(EPR, "EPRender");
    }

    // layout ------------------------------------------------------------------
    private static final float PILL_H = 15.5f;
    private static final float BODY_TOP = 16.4f;
    private static final float ROW_H = 11f;
    private static final float BODY_PAD = 6f;
    private static final float RADIUS = 4f;
    private static final float PAD = 6f;

    // fonts (LoxeDLC sizes; the stack renders size/2pt) ------------------------
    public static final FontRenderer F_TITLE_13 = Fonts.getSize(13, Fonts.Type.DEFAULT);
    public static final FontRenderer F_TITLE_14 = Fonts.getSize(14, Fonts.Type.DEFAULT);
    public static final FontRenderer F_ROW_13 = Fonts.getSize(13, Fonts.Type.DEFAULT);
    public static final FontRenderer F_12 = Fonts.getSize(12, Fonts.Type.DEFAULT);
    public static final FontRenderer F_12_BOLD = Fonts.getSize(12, Fonts.Type.BOLD);
    public static final FontRenderer F_12_SEMI = Fonts.getSize(12, Fonts.Type.SEMI);
    public static final FontRenderer F_11 = Fonts.getSize(11, Fonts.Type.DEFAULT);
    public static final FontRenderer F_11_SEMI = Fonts.getSize(11, Fonts.Type.SEMI);
    public static final FontRenderer F_11_BOLD = Fonts.getSize(11, Fonts.Type.BOLD);
    public static final FontRenderer F_10 = Fonts.getSize(10, Fonts.Type.DEFAULT);
    public static final FontRenderer F_10_BOLD = Fonts.getSize(10, Fonts.Type.BOLD);
    public static final FontRenderer F_15 = Fonts.getSize(15, Fonts.Type.DEFAULT);
    public static final FontRenderer F_16 = Fonts.getSize(16, Fonts.Type.DEFAULT);
    public static final FontRenderer F_18_REG = Fonts.getSize(18, Fonts.Type.REGULAR);
    public static final FontRenderer F_ICON_17 = Fonts.getSize(17, Fonts.Type.ICONS);
    public static final FontRenderer F_ICON_21 = Fonts.getSize(21, Fonts.Type.ICONS);
    public static final FontRenderer F_GI_14 = Fonts.getSize(14, Fonts.Type.GUIICONS);
    public static final FontRenderer F_GI_18 = Fonts.getSize(18, Fonts.Type.GUIICONS);
    public static final FontRenderer F_GI_20 = Fonts.getSize(20, Fonts.Type.GUIICONS);
    public static final FontRenderer F_GI_23 = Fonts.getSize(23, Fonts.Type.GUIICONS);
    public static final FontRenderer F_CAT_16 = Fonts.getSize(16, Fonts.Type.ICONSCATEGORY);
    public static final FontRenderer F_ISLAND = Fonts.getSize(16, Fonts.Type.NOTVISUALS_MEDIUM);
    public static final FontRenderer F_ISLAND_BIG = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);

    private HudRenderer() {
    }

    /** Wire into the Fabric HUD event + per-tick element/service ticks. */
    public static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            Network.tick();
            for (Draggable d : Hud.getRepository().draggable()) {
                try {
                    d.tick();
                } catch (Throwable ignored) {
                    // element tick failures must not kill the client tick
                }
            }
        });
    }

    // ------------------------------------------------------------- settings

    /** HUD module master switch; missing module → on (safe default). */
    static boolean hudEnabled() {
        ModuleStub m = ModuleRegistry.byId("hud");
        return m == null || m.enabled();
    }

    /** Boolean setting of the HUD module by name. */
    static boolean setting(String name, boolean def) {
        ModuleStub m = ModuleRegistry.byId("hud");
        if (m == null) {
            return def;
        }
        for (Setting s : m.settings()) {
            if (s.name().equals(name)) {
                return !(s instanceof BooleanSetting b) || b.get();
            }
        }
        return def;
    }

    /** Double (slider) setting of the HUD module by name. */
    static float settingNum(String name, float def) {
        ModuleStub m = ModuleRegistry.byId("hud");
        if (m == null) {
            return def;
        }
        for (Setting s : m.settings()) {
            if (s.name().equals(name) && s instanceof DoubleSetting d) {
                return (float) d.get();
            }
        }
        return def;
    }

    /** Mode (dropdown) setting of the HUD module by name. */
    static String settingMode(String name, String def) {
        ModuleStub m = ModuleRegistry.byId("hud");
        if (m == null) {
            return def;
        }
        for (Setting s : m.settings()) {
            if (s.name().equals(name) && s instanceof ModeSetting md) {
                return md.get();
            }
        }
        return def;
    }

    /** Element toggle by its label (the same string as in the GUI). */
    public static boolean elementOn(String label) {
        return hudEnabled() && setting(label, true);
    }

    /** In-chat editor allowed: module on + its "Interactive editor" setting. */
    static boolean editorEnabled() {
        return hudEnabled() && setting("Interactive editor", true);
    }

    /** True while the blur/glass pipeline is active for this frame. */
    private static volatile boolean blurActive;

    /** Blur pipeline switch, also read by the ClickGUI for its glass window. */
    public static boolean blurEnabled() {
        return setting("Blur", true);
    }

    /** Blur sample count from the "Blur quality" dropdown. */
    public static int blurSamples() {
        return switch (settingMode("Blur quality", "High")) {
            case "Low" -> 16;
            case "Medium" -> 32;
            default -> 64;
        };
    }

    // --------------------------------------------------------------- frame

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // sync BEFORE any early return: InGameHudMixin.hide() reads Hud.state
        // every frame, and a stale state=true with the module off would keep
        // vanilla cancelled while nothing draws (blank HUD until restart)
        Hud hud = Hud.getInstance();
        hud.syncFromSettings();
        if (mc.player == null || mc.options.hudHidden || !hudEnabled()) {
            return;
        }
        MatrixStack m = ctx.getMatrices();
        float w = mc.getWindow().getScaledWidth();
        float h = mc.getWindow().getScaledHeight();

        ColorAssist.flushFrameCache();
        HudTheme.flushFrameCache();

        // LoxeDLC-style blur pipeline: copy the frame once, panels sample it
        Blur.hudBlurEnabled = setting("Blur", true);
        Blur.glassMode = setting("Glass effect", false);
        blurActive = Blur.hudBlurEnabled || Blur.glassMode;
        Blur.hudContext = blurActive;
        if (blurActive) {
            QuickImports.blur.setup();
        }

        boolean editor = HudEditor.shouldRun(mc);
        HudEditor.setActive(editor);
        HudEditor.beginFrame();
        if (editor) {
            HudEditor.tick(mc, w, h);
        }
        HudEditor.pushPositions(w, h);

        // LoxeDLC frame loop: every registered element, scaled by its
        // start/stop animation, drawn on top of vanilla at z=400.
        m.push();
        try {
            m.translate(0.0F, 0.0F, 400.0F);
            for (Draggable draggable : Hud.getRepository().draggable()) {
                if (!(draggable instanceof AbstractDraggable ad)) {
                    continue;
                }
                try {
                    if (ad.canDraw(hud, ad)) {
                        ad.startAnimation();
                    } else {
                        ad.stopAnimation();
                    }
                    if (ad.isCloseAnimationFinished()) {
                        continue;
                    }
                    if (ad.isDragging()) {
                        ad.validPosition();
                    }
                    float scale = (float) ad.getScaleAnimation().output();
                    m.push();
                    try {
                        if (scale >= 0.999f) {
                            ad.drawDraggable(ctx);
                        } else {
                            Calculate.setAlpha(scale, () -> ad.drawDraggable(ctx));
                        }
                    } finally {
                        m.pop();
                    }
                    HudEditor.syncDraggable(ad);
                } catch (ConcurrentModificationException ignored) {
                } catch (Throwable t) {
                    ad.stopAnimation();
                    TabVisualsClient.LOGGER.debug("HUD element {} failed", ad.getName(), t);
                }
            }
        } finally {
            m.pop();
        }
        if (setting("Notifications", true)) {
            renderToasts(mc, m, w, h, true);
            if (editor && NotificationManager.queue().isEmpty()) {
                drawToastPreview(m, w, h);
            }
        }

        if (editor) {
            HudEditor.overlay(m, w, h);
        }
        Blur.hudContext = false;
        FontRenderer.flushDeferred();
    }

    // ------------------------------------------------------ placement / editor

    /**
     * Top-left corner of an element: the saved editor position when the
     * element was dragged, otherwise the LoxeDLC default; clamped on-screen.
     * Registers the final bounds with the editor either way.
     */
    public static float[] place(String id, float w, float totalH, float screenW, float screenH) {
        float[] saved = HudEditor.placed(id);
        float x;
        float y;
        if (saved != null) {
            x = clamp(saved[0], 2f, Math.max(2f, screenW - w - 2f));
            y = clamp(saved[1], 2f, Math.max(2f, screenH - totalH - 2f));
        } else {
            float[] def = defaultPos(id, screenW, screenH);
            x = clamp(def[0], 2f, Math.max(2f, screenW - w - 2f));
            y = clamp(def[1], 2f, Math.max(2f, screenH - totalH - 2f));
        }
        HudEditor.bind(id, x, y, w, totalH);
        return new float[]{x, y};
    }

    /** LoxeDLC default positions per element (scaled down when the screen is small). */
    private static float[] defaultPos(String id, float sw, float sh) {
        return switch (id) {
            case WATERMARK -> new float[]{10, 10};
            case COOLDOWNS -> new float[]{10, 40};
            case POTIONS -> new float[]{Math.min(200, sw * 0.45f), 40};
            case HOTKEYS -> new float[]{Math.min(300, sw * 0.65f), 40};
            case TARGETHUD -> new float[]{10, 80};
            case SCOREBOARD -> new float[]{10, 100};
            case MEDIAPLAYER -> new float[]{10, 120};
            case ARMOR -> new float[]{10, 150};
            case KEYSTROKES -> new float[]{10, 160};
            case BINDS -> new float[]{10, Math.min(180, sh - 70)};
            case EPR -> new float[]{20, Math.min(200, sh - 120)};
            case INVENTORY -> new float[]{sw - 140, 40};
            case TOTEM -> new float[]{sw - 60, sh - 60};
            default -> new float[]{10, 30};
        };
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }

    // ----------------------------------------------------------------- panel

    /**
     * LoxeDLC panel: soft drop shadow + optional blurred glass + the theme's
     * 4-corner gradient + a 0.1 hairline outline (ColorAssist gradient).
     */
    public static void panel(MatrixStack m, float x, float y, float w, float h, float round) {
        panel(m, x, y, w, h, round, 1f);
    }

    public static void panel(MatrixStack m, float x, float y, float w, float h, float round, float alpha) {
        plate(m, x, y, w, h, new Vector4f(round), alpha, true);
    }

    /** Same but with per-corner radii (LoxeDLC header/body split). */
    public static void plate(MatrixStack m, float x, float y, float w, float h,
                             Vector4f round, float alpha, boolean outline) {
        if (setting("Shadow", true) && !Blur.glassMode) {
            UiRender.rect(m, x, y + 1f, w, h, round.x, HudTheme.multAlpha(0x96000000, alpha));
        }
        int[] g = HudTheme.gradient();
        int c1 = HudTheme.multAlpha(g[0], alpha);
        int c2 = HudTheme.multAlpha(g[1], alpha);
        int c3 = HudTheme.multAlpha(g[2], alpha);
        int c4 = HudTheme.multAlpha(g[3], alpha);
        if (blurActive) {
            QuickImports.blur.renderSmooth(ShapeProperties.create(m, x, y, w, h)
                    .round(round)
                    .quality(blurSamples())
                    .color(c1, c2, c3, c4)
                    .build(), blurSamples());
        }
        rectangle_render(m, x, y, w, h, round, outline, c1, c2, c3, c4);
    }

    private static void rectangle_render(MatrixStack m, float x, float y, float w, float h,
                                         Vector4f round, boolean outline, int c1, int c2, int c3, int c4) {
        var props = ShapeProperties.create(m, x, y, w, h)
                .round(round)
                .color(c1, c2, c3, c4);
        if (outline) {
            props.thickness(0.1f).outlineColor(HudTheme.outline());
        }
        QuickImports.rectangle.render(props.build());
    }

    /** Header plate (rounded top corners only) + body plate, LoxeDLC split. */
    public static void headerPlate(MatrixStack m, float x, float y, float w, float alpha) {
        plate(m, x, y, w, PILL_H, new Vector4f(RADIUS, 0f, RADIUS, 0f), alpha, true);
    }

    public static void bodyPlate(MatrixStack m, float x, float y, float w, float h, float alpha) {
        plate(m, x, y, w, h, new Vector4f(0f, RADIUS, 0f, RADIUS), alpha, true);
    }

    /** "Title / Active: N" header content; returns nothing, pure draw. */
    public static void drawHeader(MatrixStack m, float x, float y, String title, String counter,
                                  FontRenderer iconFont, String iconGlyph) {
        int text = HudTheme.text();
        // divider like the reference: 0.5×6 hairline at x+18
        QuickImports.rectangle.render(ShapeProperties.create(m, x + 18, y + 5, 0.5f, 6)
                .color(HudTheme.text(0.5f)).build());
        if (iconFont != null && iconGlyph != null) {
            iconFont.drawString(m, iconGlyph, x + 4.5f, y + 5.5f, text);
        }
        F_TITLE_13.drawString(m, title, x + 22, y + 4f, text);
        if (counter != null) {
            FontRenderer f12 = F_12;
            float cw = f12.getStringWidth(counter);
            f12.drawString(m, counter, x + getWidth(m) - cw - 4f, y + 4.5f, HudTheme.text2());
        }
    }

    /** Width bookkeeping for headers — set by the caller before drawHeader. */
    private static float headerWidth;

    private static float getWidth(MatrixStack m) {
        return headerWidth;
    }

    public static void setHeaderWidth(float w) {
        headerWidth = w;
    }

    // --------------------------------------------------------------- toasts

    /**
     * Toast stack, centered under the crosshair (LoxeDLC Notifications: a
     * 1px accent progress bar on top of each card). Shared with ClickGuiScreen:
     * the screen draws them on top of its overlay; {@code drive} is true only
     * where the HUD frame is not already advancing the animation.
     */
    public static void renderToasts(MinecraftClient mc, MatrixStack m, float screenW, float screenH, boolean drive) {
        if (!setting("Notifications", true)) {
            return;
        }
        if (drive) {
            NotificationManager.update(screenH);
        }
        List<NotificationManager.Toast> queue = NotificationManager.queue();
        if (queue.isEmpty()) {
            return;
        }

        FontRenderer f = Fonts.getSize(11, Fonts.Type.REGULAR);
        FontRenderer fb = Fonts.getSize(11, Fonts.Type.SEMI);
        float textH = f.getStringHeight("Aa");
        boolean boundsBound = false;
        long now = System.nanoTime();
        for (NotificationManager.Toast t : queue) {
            float pw = f.getStringWidth(t.prefix);
            float nw = fb.getStringWidth(t.name);
            float mw = f.getStringWidth(t.middle);
            float sw2 = fb.getStringWidth(t.status);
            float textW = pw + nw + mw + sw2;
            float cardW = textW + 22f;
            float cardH = NotificationManager.TOAST_H;

            float alpha = NotificationManager.clamp01(t.enterEased) * (1f - t.exitP);
            if (t.animY < 0f || alpha <= 0.02f) {
                continue;
            }
            float scale = t.expired()
                    ? 0.92f + 0.08f * (1f - t.exitP)
                    : 0.92f + 0.08f * NotificationManager.clamp01(t.enterEased);
            float yOffset = (1f - t.enterEased) * 13f - 9f * t.exitP;

            float dw = cardW * scale;
            float dh = cardH * scale;
            float x = screenW / 2f - dw / 2f;
            float y = t.animY + yOffset + (cardH - dh) / 2f;

            panel(m, x, y, dw, dh, 4f, alpha);

            // progress bar on the top edge: shrinks with the remaining lifetime
            float life = NotificationManager.ENTER_MS + NotificationManager.HOLD_MS + NotificationManager.EXIT_MS;
            float age = (now - t.bornAt) / 1_000_000f;
            float prog = Math.max(0f, 1f - age / life);
            if (prog > 0.01f) {
                QuickImports.rectangle.render(ShapeProperties.create(m, x + 2, y + 0.05f, (dw - 4) * prog + 4, 1)
                        .round(0.75f, 0f, 0.75f, 0f)
                        .color(HudTheme.fade(0))
                        .build());
            }

            float tx = x + (dw - textW) / 2f;
            float ty = y + (dh - textH) / 2f;
            f.drawString(m, t.prefix, tx, ty, HudTheme.multAlpha(HudTheme.text2(), alpha));
            fb.drawString(m, t.name, tx + pw, ty, HudTheme.multAlpha(HudTheme.text(), alpha));
            f.drawString(m, t.middle, tx + pw + nw, ty, HudTheme.multAlpha(HudTheme.text2(), alpha));
            fb.drawString(m, t.status, tx + pw + nw + mw, ty, HudTheme.multAlpha(t.statusColor, alpha));

            // the topmost visible card is the editor's drag target for toasts
            if (!boundsBound) {
                HudEditor.bind(TOASTS, x, y, dw, dh);
                boundsBound = true;
            }
        }
    }

    /** Editor-only preview so the toast slot is visible and draggable when the queue is empty. */
    private static void drawToastPreview(MatrixStack m, float screenW, float screenH) {
        FontRenderer f = Fonts.getSize(11, Fonts.Type.REGULAR);
        FontRenderer fb = Fonts.getSize(11, Fonts.Type.SEMI);
        String prefix = "Module ", name = "HUD", middle = " was ", status = "enabled";
        float textW = f.getStringWidth(prefix) + fb.getStringWidth(name)
                + f.getStringWidth(middle) + fb.getStringWidth(status);
        float cardW = textW + 22f;
        float cardH = NotificationManager.TOAST_H;
        float x = screenW / 2f - cardW / 2f;
        float y = screenH / 2f + NotificationManager.START_OFFSET + NotificationManager.yOffset;

        panel(m, x, y, cardW, cardH, 4f);
        QuickImports.rectangle.render(ShapeProperties.create(m, x + 2, y + 0.05f, cardW - 4, 1)
                .round(0.75f, 0f, 0.75f, 0f)
                .color(HudTheme.fade(0))
                .build());
        float tx = x + (cardW - textW) / 2f;
        float ty = y + (cardH - f.getStringHeight("Aa")) / 2f;
        f.drawString(m, prefix, tx, ty, HudTheme.text2());
        fb.drawString(m, name, tx + f.getStringWidth(prefix), ty, HudTheme.text());
        f.drawString(m, middle, tx + f.getStringWidth(prefix) + fb.getStringWidth(name), ty, HudTheme.text2());
        fb.drawString(m, status, tx + f.getStringWidth(prefix) + fb.getStringWidth(name)
                + f.getStringWidth(middle), ty, 0xFF2ED573);
        HudEditor.bind(TOASTS, x, y, cardW, cardH);
    }

    // ------------------------------------------------------- potion naming

    /** "effect.minecraft.speed" → "speed". */
    public static String effectKey(String translationKey) {
        String key = translationKey;
        if (key.startsWith("effect.minecraft.")) {
            key = key.substring("effect.minecraft.".length());
        } else if (key.startsWith("effect.")) {
            int lastDot = key.lastIndexOf('.');
            key = lastDot >= 0 && lastDot < key.length() - 1 ? key.substring(lastDot + 1) : key;
        }
        return key.toLowerCase(Locale.ROOT);
    }

    /** Display name of a potion effect by its translation-key tail. */
    public static String effectName(String key) {
        return switch (key) {
            case "speed" -> "Speed";
            case "strength" -> "Strength";
            case "fire_resistance" -> "Fire Resistance";
            case "regeneration" -> "Regeneration";
            case "invisibility" -> "Invisibility";
            case "jump_boost" -> "Jump Boost";
            case "haste" -> "Haste";
            case "resistance" -> "Resistance";
            case "water_breathing" -> "Water Breathing";
            case "absorption" -> "Absorption";
            case "health_boost" -> "Health Boost";
            case "slow_falling" -> "Slow Falling";
            case "slowness" -> "Slowness";
            case "weakness" -> "Weakness";
            case "poison" -> "Poison";
            case "wither" -> "Wither";
            case "glowing" -> "Glowing";
            case "blindness" -> "Blindness";
            case "night_vision" -> "Night Vision";
            case "dolphins_grace" -> "Dolphin's Grace";
            case "conduit_power" -> "Conduit Power";
            case "levitation" -> "Levitation";
            case "hunger" -> "Hunger";
            case "mining_fatigue" -> "Mining Fatigue";
            case "nausea" -> "Nausea";
            case "luck" -> "Luck";
            case "bad_luck" -> "Bad Luck";
            case "hero_of_the_village" -> "Hero of the Village";
            case "bad_omen" -> "Bad Omen";
            case "raid_omen" -> "Raid Omen";
            case "trial_omen" -> "Trial Omen";
            case "wind_charged" -> "Wind Charged";
            case "weaving" -> "Weaving";
            case "oozing" -> "Oozing";
            case "infested" -> "Infested";
            default -> capitalizeWords(key);
        };
    }

    private static String capitalizeWords(String key) {
        String[] parts = key.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p, 1, p.length());
        }
        return sb.toString();
    }
}
