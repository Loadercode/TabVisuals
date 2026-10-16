/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import lombok.Getter;
import com.loadercode.tabvisuals.TabVisualsClient;
import com.loadercode.tabvisuals.gui.Theme;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.HudRenderer;
import com.loadercode.tabvisuals.gui.hud.compat.Instance;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.media.MediaBridge;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.render.math.time.StopWatch;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MediaPlayer extends AbstractDraggable {
    /** Daemon workers: a leaked pool must never keep the JVM alive on exit. */
    private final ExecutorService controlExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tabvisuals-media-control");
        t.setDaemon(true);
        return t;
    });
    /** Пустой снапшот до первого опроса (MediaBridge.Snapshot.NONE package-private — не используем). */
    private static final MediaBridge.Snapshot EMPTY = new MediaBridge.Snapshot(false, "", "", "", "", false, 0, 0, false, 0);
    /** Последний опрос моста: позиция уже экстраполирована по wall-clock внутри MediaBridge. */
    private volatile MediaBridge.Snapshot media = EMPTY;
    /** Мьютекс файлового канала: MusicStatus и этот класс пишут из разных пулов. */
    private static final Object CMD_LOCK = new Object();
    private final StopWatch lastMedia = new StopWatch();
    private float widthDuration;
    private FontRenderer bigFont;
    private FontRenderer miniFont;
    /** Размер иконок транспорта — тот же, что в зонах хитбоксов. */
    private static final float TRANSPORT_ICON_SIZE = 17f;

    private static final float SPOTIFY_ARTWORK_CROP_BOTTOM = 0.20f;
    @Getter
    private volatile float artworkCropBottom = 0f;

    // ------------------------------------------------------------- transport

    /** Зоны кнопок: предыдущая, play/pause, следующая. */
    private static final int ZONE_PREV = 0, ZONE_PLAY = 1, ZONE_NEXT = 2;
    private static final int ZONE_COUNT = 3;
    /** Полоса кнопок: под строкой «время — иконки — время», ровно до полосы прогресса. */
    private static final float ZONE_TOP = 21.5f, ZONE_PAD = 2f;
    /** Скорости сглаживания hover (14) и press (24) — см. ease_zones. */
    private static final float HOVER_K = 14f, PRESS_K = 24f;

    private final float[] hover_t = new float[ZONE_COUNT];
    private final float[] press_t = new float[ZONE_COUNT];
    /** Переиспользуемый буфер хитбоксов — на кадр не должно быть аллокаций. */
    private final float[] zone_buf = new float[ZONE_COUNT * 4];
    /** Зона, зажатая в данный момент; -1 — ничего не нажато. */
    private int pressed_zone = -1;
    private long pressed_ns;
    /** nanoTime предыдущего кадра — из него считается dt для сглаживания. */
    private long last_frame_ns;

    public MediaPlayer() {
        super("Media Player", 10, 120, 100, 40, true);
    }

    public static MediaPlayer getInstance() {
        return Instance.getDraggable(MediaPlayer.class);
    }

    /** Живой снапшот моста: позиция экстраполируется по wall-clock внутри MediaBridge. */
    public MediaBridge.Snapshot snapshot() {
        return MediaBridge.current();
    }

    public void shutdown() {
        controlExecutor.shutdownNow();
        MediaBridge.stop();
    }

    @Override
    public boolean visible() {
        return !lastMedia.finished(2000) || HudHelper.isChat(mc.currentScreen) || (media.active() && media.playing());
    }

    @Override
    public void tick() {
        Hud hud = Hud.getInstance();
        if (!hud.isState() || (!hud.interfaceSettings.isSelected("Media Player") && !hud.interfaceSettings.isSelected("Dynamic Island")) || mc.player == null) {
            return;
        }
        if (mc.player.age % 10 != 0) return;

        MediaBridge.Snapshot snap = MediaBridge.current();
        media = snap;
        if (snap.active()) {
            lastMedia.reset();
            artworkCropBottom = isSpotify(snap) ? SPOTIFY_ARTWORK_CROP_BOTTOM : 0f;
        }
    }

    private static boolean isSpotify(MediaBridge.Snapshot s) {
        String app = s.app();
        return app != null && !app.isEmpty() && app.toLowerCase(Locale.ROOT).startsWith("spotify");
    }

    private void ensureFonts() {
        if (bigFont == null) {
            bigFont = Fonts.getSize(14, Fonts.Type.DEFAULT);
            miniFont = Fonts.getSize(11, Fonts.Type.DEFAULT);
        }
    }

    /**
     * Команда плееру уходит через файл — внешний скрипт-мост читает его вместо
     * прямого control-канала. Потокобезопасно, вызывается из controlExecutor.
     */
    public static void sendCommand(String command) {
        String cmd = command == null ? "" : command.trim();
        if (cmd.isEmpty() || cmd.indexOf('\n') >= 0 || cmd.indexOf('\r') >= 0) return;
        synchronized (CMD_LOCK) {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("tabvisuals");
            Path target = dir.resolve("media_cmd.txt");
            Path tmp = dir.resolve("media_cmd.txt.tmp");
            for (int attempt = 0; attempt < 4; attempt++) {
                try {
                    Files.createDirectories(dir);
                    // tmp + rename: скрипт-мост читает файл по таймеру и не должен
                    // увидеть недописанную команду
                    Files.writeString(tmp, cmd + "\n" + System.currentTimeMillis());
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (Throwable t) {
                    // цель может быть открыта мостом (FileShare.Read) — rename
                    // падает с access denied; короткие ретраи вместо потери клика
                    try {
                        Thread.sleep(25L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            try {
                // иначе недописанный tmp останется в папке навсегда
                Files.deleteIfExists(tmp);
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------------------------------------------------- click routing

    /**
     * Глобальный клик/отпускание мыши — вызывается из MouseMixin на HEAD
     * onMouseButton, то есть раньше, чем событие уйдёт в Screen. Поэтому
     * кнопки работают и с открытым меню/инвентарём.
     *
     * <p>Возвращает true только при попадании в кнопку: тогда событие
     * поглощается и игра/экран его не видят. Клики мимо кнопок возвращают
     * false и обрабатываются как обычно — меню и геймплей не страдают.
     */
    public static boolean on_global_mouse_button(long window, int button, int action) {
        // mixin-хук на рендере: любое падение внутри должен гасить здесь,
        // а не ронять игру
        try {
            return route_mouse_button(window, button, action);
        } catch (Throwable t) {
            TabVisualsClient.LOGGER.debug("media click routing failed", t);
            return false;
        }
    }

    private static boolean route_mouse_button(long window, int button, int action) {
        if (action == 0) {
            // отпускание мыши: просто убираем нажатие, ничего не поглощаем
            MediaPlayer mp = getInstance();
            if (mp != null) mp.pressed_zone = -1;
            return false;
        }
        if (action != 1 || button != 0) return false;
        if (mc.getWindow() == null || window != mc.getWindow().getHandle()) return false;
        if (mc.player == null || mc.options.hudHidden) return false;
        // без открытого экрана курсор захвачен (getX/getY = центр окна) —
        // клик-атака не должен попадать в кнопки, наведение тоже бессмысленно
        if (mc.currentScreen == null) return false;
        // интерактивный редактор HUD живёт на собственных опросах мыши —
        // пока он открыт, клик внутри чата принадлежит ему
        if (HudHelper.isChat(mc.currentScreen) && HudRenderer.elementOn("Interactive editor")) return false;

        MediaPlayer mp = getInstance();
        if (mp == null || !mp.media.active()) return false;
        Hud hud = Hud.getInstance();
        // canDraw включает visible() — невидимый элемент не должен ловить клики
        if (hud == null || !mp.canDraw(hud, mp)) return false;

        float[] zones = mp.zone_rects();
        double scale = mc.getWindow().getScaleFactor();
        double mx = mc.mouse.getX() / scale;
        double my = mc.mouse.getY() / scale;
        for (int i = 0; i < ZONE_COUNT; i++) {
            if (UiRender.inside(mx, my, zones[4 * i], zones[4 * i + 1], zones[4 * i + 2], zones[4 * i + 3])) {
                mp.press_zone(i);
                return true;
            }
        }
        return false;
    }

    /** Отправка команды + фиксация нажатия для press-анимации. */
    private void press_zone(int zone) {
        pressed_zone = zone;
        pressed_ns = System.nanoTime();
        String command = switch (zone) {
            case ZONE_PREV -> "prev";
            case ZONE_NEXT -> "next";
            default -> "playPause";
        };
        try {
            controlExecutor.execute(() -> sendCommand(command));
        } catch (RejectedExecutionException e) {
            // пул уже остановлен (shutdown) — клик не должен падать
        }
    }

    /** Центр полосы иконок — та же целочисленная формула, что и в отрисовке. */
    private float base_x() {
        int sizeArtwork = Math.max(24, getHeight() - 8);
        return (float) (getX() + (getWidth() + sizeArtwork + 4 - 4) / 2);
    }

    /**
     * Хитбоксы трёх зон, построенные по тем же якорям, по которым рисуются
     * иконки (base_x() - 4 / + 4.5 / + 14): граница зоны — середина между
     * соседними иконками. Один расчёт и на отрисовку, и на клик, иначе клик
     * не попадает в нарисованную кнопку.
     */
    private float[] zone_rects() {
        ensureFonts();
        float[] out = zone_buf;
        float baseX = base_x();
        float prevW = Icons.width(Icons.Glyph.SKIP_PREV, TRANSPORT_ICON_SIZE);
        float playW = Math.max(Icons.width(Icons.Glyph.PLAY, TRANSPORT_ICON_SIZE),
                Icons.width(Icons.Glyph.PAUSE, TRANSPORT_ICON_SIZE));
        float nextW = Icons.width(Icons.Glyph.SKIP_NEXT, TRANSPORT_ICON_SIZE);
        float p0 = baseX - 4f, p1 = baseX + 4.5f, p2 = baseX + 14f;
        float left = p0 - ZONE_PAD, right = p2 + nextW + ZONE_PAD;
        float b1 = (p0 + prevW + p1) / 2f;
        float b2 = (p1 + playW + p2) / 2f;
        if (!(prevW > 0f) || !(playW > 0f) || !(nextW > 0f) || b1 <= left || b2 <= b1 || right <= b2) {
            // метрики ещё не посчитаны — делим полосу на три равные части
            b1 = left + (right - left) / 3f;
            b2 = left + (right - left) * 2f / 3f;
        }
        // низ полосы — ровно верх полосы прогресса (getHeight() - 8)
        float top = getY() + ZONE_TOP;
        float bottom = Math.max(top + 6f, getY() + getHeight() - 8f);
        float h = bottom - top;
        out[0] = left; out[1] = top; out[2] = b1 - left; out[3] = h;
        out[4] = b1; out[5] = top; out[6] = b2 - b1; out[7] = h;
        out[8] = b2; out[9] = top; out[10] = right - b2; out[11] = h;
        return out;
    }

    /**
     * Сглаживание hover/press по {@code 1 - exp(-dt * k)}: на 30 и на 300 FPS
     * анимация занимает одно и то же wall-clock время.
     */
    private void ease_zones(float[] zones, float dt) {
        float kHover = 1f - (float) Math.exp(-dt * HOVER_K);
        float kPress = 1f - (float) Math.exp(-dt * PRESS_K);
        if (pressed_zone >= 0 && System.nanoTime() - pressed_ns > 2_000_000_000L) {
            pressed_zone = -1; // отпускание потерялось (alt-tab) — кнопка не должна залипнуть
        }
        // без экрана курсор захвачен — mx/my всегда центр окна, hover выключаем
        boolean cursorFree = mc.currentScreen != null;
        double scale = mc.getWindow().getScaleFactor();
        double mx = mc.mouse.getX() / scale;
        double my = mc.mouse.getY() / scale;
        for (int i = 0; i < ZONE_COUNT; i++) {
            float hoverTarget = cursorFree && media.active()
                    && UiRender.inside(mx, my, zones[4 * i], zones[4 * i + 1], zones[4 * i + 2], zones[4 * i + 3])
                    ? 1f : 0f;
            float pressTarget = pressed_zone == i ? 1f : 0f;
            hover_t[i] = approach(hover_t[i], hoverTarget, kHover);
            press_t[i] = approach(press_t[i], pressTarget, kPress);
        }
    }

    /** Приближение к цели со ступом, равным шагу экспоненты; снап на точности. */
    private static float approach(float current, float target, float step) {
        float next = current + (target - current) * step;
        return Math.abs(target - next) < 0.0015f ? target : next;
    }

    /** Кнопки-плашки под иконками: подсветка при нажатии, уходящий press-scale. */
    private void draw_zones(MatrixStack m, float[] zones, int accent) {
        for (int i = 0; i < ZONE_COUNT; i++) {
            float zx = zones[4 * i], zy = zones[4 * i + 1], zw = zones[4 * i + 2], zh = zones[4 * i + 3];
            float h = hover_t[i];
            if (h > 0.01f) {
                UiRender.glow(m, zx + zw / 2f, zy + zh / 2f, Math.max(zw, zh) * 0.62f, accent, 0.28f * h, 1f);
            }
            int bg = UiRender.mix(ColorAssist.getRect(0.5f), UiRender.withAlpha(accent, 140), UiRender.clamp01(h));
            if (i == ZONE_PLAY) {
                // play/pause — основное действие, у него акцентная рамка
                UiRender.accentButton(m, null, zx, zy, zw, zh, 3f, null, h, press_t[i], bg, 0);
            } else {
                UiRender.button(m, null, zx, zy, zw, zh, 3f, null, h, press_t[i], bg, 0);
            }
        }
    }

    /** Иконки транспорта поверх плашек: центр каждой иконки = центр её зоны,
     *  поэтому press-scale отжимает ровно нарисованную кнопку, а клик по зоне
     *  (MouseMixin → press_zone) попадает в тот же хитбокс. */
    private void draw_transport(MatrixStack m, float[] zones, MediaBridge.Snapshot snap) {
        for (int i = 0; i < ZONE_COUNT; i++) {
            // без аллокации массива строк/координат на каждый кадр
            Icons.Glyph glyph = i == ZONE_PREV ? Icons.Glyph.SKIP_PREV
                    : i == ZONE_NEXT ? Icons.Glyph.SKIP_NEXT
                    // кнопка показывает действие: играет — PAUSE (вектор), стоит — PLAY
                    : (snap.playing() ? Icons.Glyph.PAUSE : Icons.Glyph.PLAY);
            float scale = UiRender.pressScale(press_t[i]);
            float cx = zones[4 * i] + zones[4 * i + 2] / 2f;
            float cy = zones[4 * i + 1] + zones[4 * i + 3] / 2f;
            int color = UiRender.mix(ColorAssist.getText(), 0xFFFFFFFF, 0.35f * hover_t[i]);
            m.push();
            m.translate(cx, cy, 0f);
            m.scale(scale, scale, 1f);
            m.translate(-cx, -cy, 0f);
            // PAUSE векторный (два бара) — у него своя центровка, у глифов — baseline-коррекция
            if (glyph == Icons.Glyph.PAUSE) {
                Icons.drawCentered(m, glyph, cx, cy, TRANSPORT_ICON_SIZE, color);
            } else {
                UiRender.iconCentered(m, glyph, cx, cy, TRANSPORT_ICON_SIZE, color);
            }
            m.pop();
        }
    }

    // ----------------------------------------------------------------- cover

    /**
     * Обложка без растяжения: квадрат назначения заполняется квадратным
     * куском источника по центру (crop-fill). Водяной знак Spotify снизу
     * остаётся обрезкой по UV, а не растягиванием — как было раньше.
     */
    private void draw_cover(MatrixStack m, Identifier tex, int srcW, int srcH, float x, float y, float size) {
        float usableH = srcH * (1f - artworkCropBottom);
        if (usableH < 1f) usableH = srcH;
        int side = Math.max(1, Math.round(Math.min(srcW, usableH)));
        int u = Math.max(0, Math.min(srcW - side, Math.round((srcW - side) / 2f)));
        int v = Math.max(0, Math.round((usableH - side) / 2f));
        if (v + side > srcH) v = srcH - side;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        Render2D.drawTexture(m, tex, x, x + size, y, y + size, 0f, side, side, u, v, srcW, srcH, 0xFFFFFFFF);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /**
     * Заглушка вместо обложки: фиолетово-белый градиент от акцента темы,
     * мягкое радиальное свечение и буква трека. Рисуется только скруглёнными
     * фигурами, поэтому углы остаются аккуратными.
     */
    private void draw_cover_placeholder(MatrixStack m, float x, float y, float size, MediaBridge.Snapshot snap) {
        int accent = Theme.get().accent();
        int cTL = accent;
        int cBL = UiRender.lighten(accent, 0.18f);
        int cTR = UiRender.lighten(accent, 0.45f);
        int cBR = UiRender.lighten(accent, 0.85f); // почти белый
        // порядок цветов ShapeProperties: bl, tl, br, tr (см. UiRender.gradient)
        rectangle.render(ShapeProperties.create(m, x, y, size, size)
                .round(3f)
                .color(cBL, cTL, cBR, cTR)
                .build());
        UiRender.glow(m, x + size / 2f, y + size / 2f, size * 0.55f, 0xFFFFFF, 0.30f, 1f);

        String letter = cover_letter(snap);
        if (!letter.isEmpty()) {
            float glyphH = 0.5f * bigFont.getStringHeight(letter);
            bigFont.drawString(m, letter,
                    x + (size - bigFont.getStringWidth(letter)) / 2f,
                    y + (size - glyphH) / 2f + 2f,
                    UiRender.withAlpha(0xFFFFFFFF, 235));
        }
    }

    /** Кэш первой буквы: пересчёт только при смене строки снапшота. */
    private String letter_source_cache;
    private String letter_cache = "";

    /** Первая буква названия трека (или приложения), если она есть. */
    private String cover_letter(MediaBridge.Snapshot snap) {
        String source = snap.title() != null && !snap.title().isBlank() ? snap.title() : snap.app();
        if (source == null) return "";
        // идентичность строк: снапшоты переиспользуют title/app, пока трек не сменился
        if (source == letter_source_cache) return letter_cache;
        String letter = "";
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (!Character.isWhitespace(c)) {
                letter = String.valueOf(Character.toUpperCase(c));
                break;
            }
        }
        letter_source_cache = source;
        letter_cache = letter;
        return letter;
    }

    // ------------------------------------------------------------------ draw

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        ScissorAssist scissor = ScissorAssist.INSTANCE;
        ensureFonts();

        long now = System.nanoTime();
        float dt = last_frame_ns == 0L ? 0.016f : (now - last_frame_ns) / 1_000_000_000f;
        last_frame_ns = now;
        dt = MathHelper.clamp(dt, 0.0001f, 0.05f);

        int sizeArtwork = Math.max(24, getHeight() - 8);
        int maxDurationWidth = getWidth() - (sizeArtwork + 12) + 15;

        // один снапшот на кадр: время/прогресс/заголовок всегда из одной точки
        MediaBridge.Snapshot snap = MediaBridge.current();
        long rawDuration = snap.endMs();
        // Позицию мост уже экстраполирует по wall-clock — берём как есть.
        long rawPosition = snap.posMs();
        // Снапшот всегда хранит миллисекунды — делим всегда. Раньше делили только
        // при > 10000, из-за чего треки короче 10 с показывали время вроде «139:50».
        rawDuration /= 1000L;
        rawPosition /= 1000L;
        int duration = (int) rawDuration;
        int position = MathHelper.clamp((int) rawPosition, 0, duration);

        String timeDuration = StringHelper.getDuration(duration);
        String timePosition = StringHelper.getDuration(position);
        widthDuration = MathHelper.clamp(Calculate.interpolateSmooth(1, widthDuration, duration > 0 ? (float) position / duration * maxDurationWidth : 0), 1, maxDurationWidth);

        int[] gradientColors = ColorAssist.getRectangleGradientColors();
        blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth() + 15, getHeight())
                    .round(5f).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth() + 15, getHeight())
                    .round(5f)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getRectangleOutlineColor())
                .color(gradientColors[0], gradientColors[1], gradientColors[2], gradientColors[3])
                .build());

        scissor.push(matrix.peek().getPositionMatrix(), getX() + sizeArtwork + 8, getY(), getWidth() - sizeArtwork - 10 + 15, getHeight());
        bigFont.drawStringWithScroll(matrix, snap.title(), getX() + sizeArtwork + 8, getY() + 7, 56, ColorAssist.getText());
        miniFont.drawStringWithScroll(matrix, snap.artist(), getX() + sizeArtwork + 8, getY() + 15.5F, 56, ColorAssist.getText(0.75F));
        scissor.pop();

        float ax = getX() + 4;
        float ay = getY() + 4;
        // Обложку тянем из MusicManager (регистрируется один раз на трек); нет
        // текстуры — рисуем фиолетовую заглушку вместо пустого прямоугольника.
        Identifier artTex = MusicManager.coverTexture();
        int artW = MusicManager.coverSize();
        int artH = MusicManager.coverHeight();
        if (artTex != null && artW > 0 && artH > 0) {
            rectangle.render(ShapeProperties.create(matrix, ax, ay, (float) sizeArtwork, (float) sizeArtwork).round(3).color(ColorAssist.getRect(1)).build());
            draw_cover(matrix, artTex, artW, artH, ax, ay, sizeArtwork);
        } else {
            draw_cover_placeholder(matrix, ax, ay, sizeArtwork, snap);
        }

        // кнопки рисуем ДО подписей времени, чтобы текст оставался читаемым
        float[] zones = zone_rects();
        ease_zones(zones, dt);
        draw_zones(matrix, zones, Theme.get().accent());

        miniFont.drawString(matrix, timePosition, getX() + 8 + sizeArtwork, getY() + 27, ColorAssist.getText());
        miniFont.drawString(matrix, timeDuration, getX() + getWidth() - 4 - miniFont.getStringWidth(timeDuration) + 15, getY() + 27, ColorAssist.getText());

        rectangle.render(ShapeProperties.create(matrix, getX() + 8 + sizeArtwork, getY() + getHeight() - 8f, maxDurationWidth, 3.5f).round(0.85F).color(ColorAssist.getRectDarker(0.75F)).build());
        rectangle.render(ShapeProperties.create(matrix, getX() + 8 + sizeArtwork, getY() + getHeight() - 8f, widthDuration, 3.5f).softness(4).round(1).color(ColorAssist.roundClientColor(0.2F)).build());
        rectangle.render(ShapeProperties.create(matrix, getX() + 8 + sizeArtwork, getY() + getHeight() - 8f, widthDuration, 3.5f).round(0.85F).color(ColorAssist.roundClientColor(1)).build());

        // иконки последними — они интерактивные и всегда сверху
        draw_transport(matrix, zones, snap);
    }
}
