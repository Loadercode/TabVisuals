/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import com.loadercode.tabvisuals.gui.Theme;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.StringHelper;
import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.media.MediaBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import com.loadercode.tabvisuals.gui.hud.elements.MediaPlayer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.scissor.ScissorAssist;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

public class MusicStatus extends IslandStatus implements QuickImports {

    private static final float ARTWORK_SIZE = 16f;
    private static final float MAX_TITLE_WIDTH = 48f;
    private static final float COMPACT_TITLE_WIDTH = 80f;
    private static final long EXPANDED_DURATION_MS = 5000;
    /** Общий радиус плитки обложки: и текстура, и заглушка квадратные, крупный радиус давал бы углы. */
    private static final float COVER_ROUND = 3f;
    /** Доля периметра, срезаемая у источника (водяной знак Spotify по краям). */
    private static final float COVER_INSET_CONTROLS = 0.15f;

    private static final float VISUALIZER_WIDTH = 18f;
    private static final float VISUALIZER_GAP = 3f;
    private static final float VISUALIZER_RIGHT_MARGIN = 4f;
    private static final float BAR_W = 2.5f;
    private static final float BAR_GAP = 1.5f;
    private static final float BAR_MAX_H = 10f;
    private static final float PAUSE_BAR_W = 2f;
    private static final float PAUSE_BAR_H = 7f;
    private static final float PAUSE_BAR_GAP = 2f;
    private static final long WAVE_UPDATE_MS = 120;
    private static final float VISUALIZER_ALPHA = 0.45f;

    private String lastTrackKey = "";
    private long lastTrackChangeTime = 0;
    private boolean isExpanded = true;

    private final float[] waveHeights = new float[4];
    private final float[] waveTargets = new float[4];
    private long lastWaveUpdate = 0L;

    /** Момент предыдущего updateSize — от него считается кадровый dt (сек, ≤0.1). */
    private long lastUpdateNs = 0L;
    /** Отжатие кнопок транспорта после клика: prev / play / next. */
    private final float[] pressAnim = new float[3];
    /** Сглаженная доля прогресса: мост прыгает раз в ~250 мс, шкала должна плыть. */
    private float progressAnim = 0f;

    // Состояние окна управления музыкой (открывается по клику по островку)
    private boolean controlsVisible = false;
    private float controlsAnim = 0f;

    /** Daemon workers: a leaked pool must never keep the JVM alive on exit. */
    private static final ExecutorService controlExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tabvisuals-island-media");
        t.setDaemon(true);
        return t;
    });

    // Иконки управления (PNG) только для островка
    private static final Identifier ICON_FORWARD =
            Identifier.of("tabvisuals", "textures/island/media_forward.png");
    private static final Identifier ICON_BACK =
            Identifier.of("tabvisuals", "textures/island/media_back.png");
    private static final Identifier ICON_PAUSE =
            Identifier.of("tabvisuals", "textures/island/media_pause.png");
    private static final Identifier ICON_PLAY =
            Identifier.of("tabvisuals", "textures/island/media_play.png");

    @Override
    public boolean canShow() {
        MediaPlayer mp = MediaPlayer.getInstance();
        if (mp == null) return false;
        MediaBridge.Snapshot info = mp.snapshot();
        if (!info.active()) return false;
        return info.playing() || !info.title().isEmpty();
    }

    @Override
    public boolean hidesVanillaBossBar() {
        return false;
    }

    private static String label(MediaBridge.Snapshot info) {
        String title = info.title() != null ? info.title() : "Unknown";
        String artist = info.artist() != null ? info.artist() : "";
        if (artist.isEmpty()) return title;
        return title + " - " + artist;
    }

    private void updateWaveLogic(boolean playing, float dt) {
        if (playing) {
            if (System.currentTimeMillis() - lastWaveUpdate > WAVE_UPDATE_MS) {
                lastWaveUpdate = System.currentTimeMillis();
                waveTargets[0] = 4f + (float) (Math.random() * 6);
                waveTargets[1] = 2f + (float) (Math.random() * 10);
                waveTargets[2] = 5f + (float) (Math.random() * 4);
                waveTargets[3] = 3f + (float) (Math.random() * 5);
            }
        } else {
            for (int i = 0; i < 4; i++) waveTargets[i] = 0f;
        }
        for (int i = 0; i < 4; i++) {
            // 9 / 3.7 — те же 0.14 / 0.06 за кадр при 60 FPS, но в секундах
            float speed = (i == 1) ? 3.7f : 9f;
            waveHeights[i] = HudHelper.hud_anim_approach(waveHeights[i], waveTargets[i], speed, dt);
        }
    }

    @Override
    public void updateSize() {
        long now = System.nanoTime();
        float dt = lastUpdateNs == 0L ? 0.0166f : (now - lastUpdateNs) / 1_000_000_000f;
        lastUpdateNs = now;
        if (dt > 0.1f) dt = 0.1f;

        MediaPlayer mp = MediaPlayer.getInstance();
        if (mp == null) return;
        MediaBridge.Snapshot info = mp.snapshot();
        if (!info.active()) return;

        String title = info.title();
        String artist = info.artist();
        String trackKey = title + "|" + artist;

        if (!controlsVisible) {
            if (!trackKey.equals(lastTrackKey)) {
                lastTrackKey = trackKey;
                lastTrackChangeTime = System.currentTimeMillis();
                isExpanded = true;
            }
            long elapsed = System.currentTimeMillis() - lastTrackChangeTime;
            if (elapsed >= EXPANDED_DURATION_MS) {
                isExpanded = false;
            }
        }

        updateWaveLogic(info.playing(), dt);

        float baseWidth;
        float baseHeight;
        if (isExpanded) {
            baseWidth = 10f + ARTWORK_SIZE + MAX_TITLE_WIDTH;
            baseHeight = 22f;
        } else {
            var font = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
            String lab = label(info);
            float titleW = font.getStringWidth(lab);
            float textAreaW = Math.min(titleW, COMPACT_TITLE_WIDTH);
            baseWidth = 10f + textAreaW + VISUALIZER_GAP + VISUALIZER_WIDTH;
            baseHeight = 16f;
        }

        // Целевые размеры открытого (большого) плеера
        float expandedWidth = 120f;
        float expandedHeight = 55f;

        // 1 - exp(-dt*k): одинаковая скорость на любом FPS, dt ограничен 0.1
        float target = controlsVisible ? 1.0f : 0.0f;
        controlsAnim = HudHelper.hud_anim_approach(controlsAnim, target, 14f, dt);
        if (Math.abs(controlsAnim - target) < 0.002f) controlsAnim = target;
        for (int i = 0; i < pressAnim.length; i++) {
            pressAnim[i] = HudHelper.hud_anim_approach(pressAnim[i], 0f, 24f, dt);
        }

        long durationMs = info.endMs();
        long positionMs = info.posMs();
        float ratio = durationMs > 0 ? UiRender.clamp01(positionMs / (float) durationMs) : 0f;
        progressAnim = HudHelper.hud_anim_approach(progressAnim, ratio, 18f, dt);

        width = baseWidth + (expandedWidth - baseWidth) * controlsAnim;
        height = baseHeight + (expandedHeight - baseHeight) * controlsAnim;
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        MediaPlayer mp = MediaPlayer.getInstance();
        if (mp == null) return;

        MediaBridge.Snapshot info = mp.snapshot();
        if (!info.active()) return;

        String title = info.title() != null ? info.title() : "Unknown";
        String artist = info.artist() != null ? info.artist() : "";
        String lab = artist.isEmpty() ? title : title + " - " + artist;
        boolean playing = info.playing();
        boolean controlsActive = controlsAnim > 0.01f || controlsVisible;
        var font = Fonts.getSize(isExpanded ? 16 : 18, Fonts.Type.NOTVISUALS_MEDIUM);
        int textColor = UiRender.mulAlpha(ColorAssist.getText(), alpha);

        // визуализатор привязан к живой панели островка, а не к целевой ширине
        float vizX = x + islandWidth - VISUALIZER_WIDTH - VISUALIZER_RIGHT_MARGIN;
        float centerY = y + islandHeight / 2f;

        // 1) Базовое окно (переключение трека) — только когда управление закрыто
        if (!controlsActive) {
            if (isExpanded) {
                float artworkX = x + 5;
                float artworkY = y + (islandHeight - ARTWORK_SIZE) / 2f;
                draw_cover(context, artworkX, artworkY, ARTWORK_SIZE, 0f, title, alpha);

                float titleX = x + 8 + ARTWORK_SIZE;
                float titleY = HudHelper.hud_text_mid_y(font, lab, y, islandHeight);
                ScissorAssist scissor = ScissorAssist.INSTANCE;
                scissor.push(context.getMatrices().peek().getPositionMatrix(), titleX, y, MAX_TITLE_WIDTH, islandHeight);
                font.drawStringSnapped(context.getMatrices(), lab, titleX, titleY, textColor);
                scissor.pop();
            } else {
                float titleX = x + 5;
                float textAreaW = Math.min(COMPACT_TITLE_WIDTH, Math.max(0, width - 10f - VISUALIZER_GAP - VISUALIZER_WIDTH));
                float titleY = HudHelper.hud_text_mid_y(font, lab, y, islandHeight);
                ScissorAssist scissor = ScissorAssist.INSTANCE;
                scissor.push(context.getMatrices().peek().getPositionMatrix(), titleX, y, textAreaW, islandHeight);
                font.drawStringWithScroll(context.getMatrices(), lab, titleX, titleY, textAreaW, textColor);
                scissor.pop();
            }
        }

        // Визуализатор только в свёрнутом виде, когда управление закрыто
        if (!controlsActive && !isExpanded) {
            drawVisualizer(context, vizX, centerY, playing, alpha);
        }

        // 2) Окно управления — отдельная разметка, когда controlsActive
        if (controlsActive) {
            drawControlsLayout(context, x, y, title, artist, info, playing, alpha);
        }
    }

    private void drawVisualizer(DrawContext context, float x, float centerY, boolean playing, float alpha) {
        int barColor = UiRender.mulAlpha(ColorAssist.multAlpha(ColorAssist.getClientColor(1), VISUALIZER_ALPHA), alpha);
        int pauseColor = UiRender.mulAlpha(ColorAssist.getText(VISUALIZER_ALPHA), alpha);
        if (playing) {
            float barBaseY = centerY + 4f;
            for (int i = 0; i < 4; i++) {
                float h = Math.min(waveHeights[i], BAR_MAX_H);
                float bx = x + i * (BAR_W + BAR_GAP);
                rectangle.render(ShapeProperties.create(context.getMatrices(), bx, barBaseY - h, BAR_W, h).round(1f).color(barColor).build());
            }
        } else {
            float pX = x + (VISUALIZER_WIDTH - (PAUSE_BAR_W * 2 + PAUSE_BAR_GAP)) / 2f;
            float pY = centerY - PAUSE_BAR_H / 2f;
            rectangle.render(ShapeProperties.create(context.getMatrices(), pX, pY, PAUSE_BAR_W, PAUSE_BAR_H).round(0.5f).color(pauseColor).build());
            rectangle.render(ShapeProperties.create(context.getMatrices(), pX + PAUSE_BAR_W + PAUSE_BAR_GAP, pY, PAUSE_BAR_W, PAUSE_BAR_H).round(0.5f).color(pauseColor).build());
        }
    }

    /**
     * Плитка обложки без растяжения: квадратный кроп по центру рабочей области
     * источника (реальная coverHeight(), водяной знак по периметру + обрезка снизу),
     * либо фиолетово-белая заглушка, если текстуры ещё нет.
     *
     * @param insetRatio доля периметра источника, срезаемая до кропа
     */
    private void draw_cover(DrawContext context, float x, float y, float size, float insetRatio, String trackTitle, float fadeAlpha) {
        MatrixStack m = context.getMatrices();
        Identifier tex = MusicManager.coverTexture();
        int srcW = MusicManager.coverSize();
        int srcH = MusicManager.coverHeight();
        if (tex == null || srcW <= 0 || srcH <= 0) {
            draw_cover_placeholder(m, x, y, size, trackTitle, fadeAlpha);
            return;
        }

        MediaPlayer mp = MediaPlayer.getInstance();
        float cropBottom = mp != null ? UiRender.clamp01(mp.getArtworkCropBottom()) : 0f;

        UiRender.roundedPanel(m, x, y, size, size, COVER_ROUND,
                UiRender.mulAlpha(ColorAssist.getRect(1), fadeAlpha));

        // рабочая область источника: inset по периметру + доля снизу (Spotify-водяной знак)
        int left = Math.round(srcW * insetRatio);
        int top = Math.round(srcH * insetRatio);
        int right = srcW - left;
        int bottom = Math.round(srcH * (1f - insetRatio - cropBottom));
        if (right - left < 1) right = left + 1;
        if (bottom - top < 1) bottom = top + 1;
        // сторона квадрата = меньшая граница, позиция ровно по центру: UV квадратный, квадрат назначения квадратный — без растяжения
        int side = Math.min(right - left, bottom - top);
        int u = left + (right - left - side) / 2;
        int v = top + (bottom - top - side) / 2;

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        Render2D.drawTexture(m, tex, x, x + size, y, y + size, 0f, side, side, u, v, srcW, srcH,
                UiRender.mulAlpha(0xFFFFFFFF, fadeAlpha));
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /**
     * Заглушка вместо обложки: фиолетово-белый градиент от акцента темы
     * (0xFF8B5CF6-семейство), мягкое свечение и первая буква трека по центру.
     */
    private static void draw_cover_placeholder(MatrixStack m, float x, float y, float size, String trackTitle, float fadeAlpha) {
        int accent = Theme.get().accent();
        int tl = UiRender.mulAlpha(accent, fadeAlpha);
        int bl = UiRender.mulAlpha(UiRender.lighten(accent, 0.18f), fadeAlpha);
        int tr = UiRender.mulAlpha(UiRender.lighten(accent, 0.45f), fadeAlpha);
        int br = UiRender.mulAlpha(UiRender.lighten(accent, 0.85f), fadeAlpha); // почти белый
        UiRender.gradient(m, x, y, size, size, tl, bl, br, tr);
        UiRender.glow(m, x + size / 2f, y + size / 2f, size * 0.55f, 0xFFFFFF, 0.30f, fadeAlpha);

        String letter = track_initial(trackTitle);
        if (letter.isEmpty()) return;
        var f = Fonts.getSize(size >= 20f ? 14 : 11, Fonts.Type.NOTVISUALS_MEDIUM);
        f.drawCenteredSnapped(m, letter, x + size / 2f, HudHelper.hud_text_mid_y(f, letter, y, size),
                UiRender.mulAlpha(UiRender.withAlpha(0xFFFFFFFF, 235), fadeAlpha));
    }

    /** Первая буква названия трека — центр заглушки. */
    private static String track_initial(String title) {
        if (title == null) return "";
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (!Character.isWhitespace(c)) return String.valueOf(Character.toUpperCase(c));
        }
        return "";
    }

    private void drawControlsLayout(DrawContext context,
                                    float x,
                                    float y,
                                    String title,
                                    String artist,
                                    MediaBridge.Snapshot info,
                                    boolean playing,
                                    float fadeAlpha) {
        // Если анимация только началась и высоты не хватает — прячем контент, чтобы не вылезал
        if (height < 30) return;

        MatrixStack m = context.getMatrices();
        float padding = 6f;

        // Ограничиваем рендер контента рамками островка при анимации
        ScissorAssist scissor = ScissorAssist.INSTANCE;
        scissor.push(m.peek().getPositionMatrix(), x, y, width, height);

        // ================= 1. ВЕРХНИЙ БЛОК: ОБЛОЖКА И ТЕКСТ =================
        float coverSize = 24f;
        float coverX = x + padding;
        float coverY = y + padding;
        draw_cover(context, coverX, coverY, coverSize, COVER_INSET_CONTROLS, title, fadeAlpha);

        // Название и автор (справа от обложки), вертикально — по центру обложки
        float textX = coverX + coverSize + 6f;
        float maxTextWidth = width - (textX - x) - padding;

        var titleFont = Fonts.getSize(14, Fonts.Type.NOTVISUALS_MEDIUM);
        var artistFont = Fonts.getSize(11, Fonts.Type.NOTVISUALS_MEDIUM);
        String titleStr = title != null ? title : "Unknown";
        boolean hasArtist = artist != null && !artist.isEmpty();

        float titleH = HudHelper.hud_text_height(titleFont, titleStr);
        float artistH = hasArtist ? HudHelper.hud_text_height(artistFont, artist) : 0f;
        float lineGap = 1.5f;
        float blockTop = coverY + (coverSize - titleH - artistH - (hasArtist ? lineGap : 0f)) / 2f;
        float titleY = HudHelper.hud_text_top(blockTop);
        float artistY = HudHelper.hud_text_top(blockTop + titleH + lineGap);

        scissor.push(m.peek().getPositionMatrix(), textX, y, maxTextWidth, height);
        titleFont.drawStringSnapped(m, titleStr, textX, titleY, UiRender.mulAlpha(0xFFFFFFFF, fadeAlpha));
        if (hasArtist) {
            artistFont.drawStringSnapped(m, artist, textX, artistY, UiRender.mulAlpha(0xFFA0A0A0, fadeAlpha));
        }
        scissor.pop();

        // ================= 2. СРЕДНИЙ БЛОК: ПРОГРЕСС-БАР =================
        float progressY = coverY + coverSize + 4f;
        long durationMs = info.endMs();
        long positionMs = info.posMs();

        var timeFont = Fonts.getSize(9, Fonts.Type.NOTVISUALS_MEDIUM);
        String currTimeStr = formatTime(positionMs);
        String totTimeStr = formatTime(durationMs);

        float currTimeW = timeFont.getStringWidth(currTimeStr);
        float totTimeW = timeFont.getStringWidth(totTimeStr);

        // Оба времени на одной линии с центром шкалы; правое — drawRight по точной ширине
        float barH = 3.0f;
        float timeH = HudHelper.hud_text_height(timeFont, currTimeStr);
        float timeY = HudHelper.hud_text_mid_y(timeFont, currTimeStr, progressY - timeH / 2f, timeH);
        int timeColor = UiRender.mulAlpha(0xFFA0A0A0, fadeAlpha);
        timeFont.drawStringSnapped(m, currTimeStr, x + padding, timeY, timeColor);
        timeFont.drawRightSnapped(m, totTimeStr, x + width - padding, timeY, timeColor);

        float timeBarGap = 4f;
        float barX = x + padding + currTimeW + timeBarGap;
        float barRight = x + width - padding - totTimeW - timeBarGap;
        float barW = Math.max(0, barRight - barX);

        if (barW > 0) {
            UiRender.roundedPanel(m, barX, progressY - barH / 2f, barW, barH, 1.75f,
                    UiRender.mulAlpha(0xFF404040, fadeAlpha));
            float fillW = barW * progressAnim;
            if (fillW > 1f) {
                UiRender.roundedPanel(m, barX, progressY - barH / 2f, fillW, barH, 1.75f,
                        UiRender.mulAlpha(0xFFFFFFFF, fadeAlpha));
            }
        }

        // ================= 3. НИЖНИЙ БЛОК: КНОПКИ (PNG ТОЛЬКО ДЛЯ ОСТРОВКА) =================
        float iconSize = 14f;
        // Центруем иконки по одной линии и гарантированно внутри окна
        float controlsY = y + height - padding - iconSize + 2f;
        float centerX = x + width / 2f;
        float spacing = 24f;

        // Текстуры 256x256 — используем полный размер, иначе берётся только 16x16 и иконки пиксельные
        int texSize = 256;

        // Выбираем цвет иконок по теме: белые на тёмной/прозрачной, чёрные на светлой
        int iconColor = 0xFFFFFFFF;
        if (com.loadercode.tabvisuals.gui.hud.HudTheme.isLight()) {
            iconColor = 0xFF000000;
        }
        iconColor = UiRender.mulAlpha(iconColor, fadeAlpha);

        float iconCenterY = controlsY + iconSize / 2f;
        draw_control_icon(m, ICON_BACK, centerX - spacing, iconCenterY, iconSize, texSize, iconColor, pressAnim[0]);
        draw_control_icon(m, playing ? ICON_PAUSE : ICON_PLAY, centerX, iconCenterY, iconSize, texSize, iconColor, pressAnim[1]);
        draw_control_icon(m, ICON_FORWARD, centerX + spacing, iconCenterY, iconSize, texSize, iconColor, pressAnim[2]);

        scissor.pop(); // Снимаем главный Scissor
    }

    /** Иконка транспорта с отжатием после клика — та же схема pressScale, что у UiRender.button. */
    private static void draw_control_icon(MatrixStack m, Identifier icon, float cx, float cy,
                                          float size, int texSize, int color, float press) {
        float s = UiRender.pressScale(press);
        m.push();
        m.translate(cx, cy, 0f);
        m.scale(s, s, 1f);
        m.translate(-cx, -cy, 0f);
        Render2D.drawTexture(m, icon, cx - size / 2f, cx + size / 2f, cy - size / 2f, cy + size / 2f,
                0f, texSize, texSize, 0f, 0f, texSize, texSize, color);
        m.pop();
    }

    @Override
    public void click(double mouseX, double mouseY, float islandX, float islandY, float islandWidth, float islandHeight) {
        float padding = 6f;
        float iconSize = 14f;
        float spacing = 24f;
        float controlsY = islandY + islandHeight - padding - iconSize + 2f;
        float centerX = islandX + islandWidth / 2f;

        // Если панель управления открыта — проверяем клик по кнопкам
        if (controlsVisible) {
            MediaPlayer mp = MediaPlayer.getInstance();
            MediaBridge.Snapshot snap = mp != null ? mp.snapshot() : null;
            if (snap != null && snap.active()) {
                if (UiRender.inside(mouseX, mouseY, centerX - spacing - iconSize / 2f, controlsY, iconSize, iconSize)) {
                    pressAnim[0] = 1f;
                    controlExecutor.execute(() -> MediaPlayer.sendCommand("prev"));
                    return;
                }
                if (UiRender.inside(mouseX, mouseY, centerX - iconSize / 2f, controlsY, iconSize, iconSize)) {
                    pressAnim[1] = 1f;
                    controlExecutor.execute(() -> MediaPlayer.sendCommand("playPause"));
                    return;
                }
                if (UiRender.inside(mouseX, mouseY, centerX + spacing - iconSize / 2f, controlsY, iconSize, iconSize)) {
                    pressAnim[2] = 1f;
                    controlExecutor.execute(() -> MediaPlayer.sendCommand("next"));
                    return;
                }
            }
        }

        // Клик не по кнопке — переключаем панель управления
        controlsVisible = !controlsVisible;
    }

    @Override
    public void onOutsideClick() {
        controlsVisible = false;
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long totalSeconds = ms / 1000;
        return StringHelper.format_clock(totalSeconds / 60, totalSeconds % 60);
    }

    @Override
    public int getColor() {
        return 0xE0150A20;
    }
}
