/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import com.loadercode.tabvisuals.gui.music.MusicManager;
import com.loadercode.tabvisuals.media.MediaBridge;
import net.minecraft.client.gui.DrawContext;
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
    private static final net.minecraft.util.Identifier ICON_FORWARD =
            net.minecraft.util.Identifier.of("tabvisuals", "textures/island/media_forward.png");
    private static final net.minecraft.util.Identifier ICON_BACK =
            net.minecraft.util.Identifier.of("tabvisuals", "textures/island/media_back.png");
    private static final net.minecraft.util.Identifier ICON_PAUSE =
            net.minecraft.util.Identifier.of("tabvisuals", "textures/island/media_pause.png");
    private static final net.minecraft.util.Identifier ICON_PLAY =
            net.minecraft.util.Identifier.of("tabvisuals", "textures/island/media_play.png");

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

    private void updateWaveLogic(boolean playing) {
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
            float smoothness = (i == 1) ? 0.06f : 0.14f;
            waveHeights[i] += (waveTargets[i] - waveHeights[i]) * smoothness;
        }
    }

    @Override
    public void updateSize() {
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

        updateWaveLogic(info.playing());

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

        // --- КОД ИЗ ai_studio_code (1).java ---
        // Целевые размеры для открытого (большого) плеера
        // (уменьшено в 2 раза по просьбе)
        float expandedWidth = 120f;
        float expandedHeight = 55f;

        float target = controlsVisible ? 1.0f : 0.0f;
        controlsAnim += (target - controlsAnim) * 0.15f; // Плавность открытия

        // Интерполируем размеры от базовых к расширенным
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
        int textColor = ColorAssist.getText();

        float vizX = x + width - VISUALIZER_WIDTH - VISUALIZER_RIGHT_MARGIN;
        float centerY = y + height / 2f;

        // 1) Базовое окно (переключение трека) — как раньше, только когда управление закрыто
        if (!controlsActive) {
            if (isExpanded) {
                float artworkX = x + 5;
                float artworkY = y + (height - ARTWORK_SIZE) / 2f;
                int size = (int) ARTWORK_SIZE;
                float crop = mp.getArtworkCropBottom();
                int artworkWidthExtra = (crop > 0f && crop < 1f) ? 5 : 0;
                float drawW = size + artworkWidthExtra;
                float drawX1 = artworkX - artworkWidthExtra / 2f;

                rectangle.render(ShapeProperties.create(context.getMatrices(), artworkX, artworkY, size, size).round(2).color(ColorAssist.getRect(1)).build());
                // Обложка — из MusicManager; нет текстуры — остаётся только фон.
                net.minecraft.util.Identifier artTex = MusicManager.coverTexture();
                int artSize = MusicManager.coverSize();
                if (artTex != null && artSize > 0) {
                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    if (crop > 0 && crop < 1) {
                        int regionH = (int) (artSize * (1f - crop));
                        Render2D.drawTexture(context.getMatrices(), artTex, drawX1, drawX1 + drawW, artworkY, artworkY + size, 0f, artSize, regionH, 0f, 0f, artSize, artSize, 0xFFFFFFFF);
                    } else {
                        Render2D.drawTexture(context.getMatrices(), artTex, drawX1, drawX1 + drawW, artworkY, artworkY + size, 0f, artSize, artSize, 0f, 0f, artSize, artSize, 0xFFFFFFFF);
                    }
                    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                }

                float titleX = x + 8 + ARTWORK_SIZE;
                float titleY = y + height - 4f - font.getStringHeight("A") / 2f;
                ScissorAssist scissor = ScissorAssist.INSTANCE;
                scissor.push(context.getMatrices().peek().getPositionMatrix(), titleX, y, MAX_TITLE_WIDTH, height);
                font.drawString(context.getMatrices(), lab, titleX, titleY, textColor);
                scissor.pop();
            } else {
                float titleX = x + 5;
                float fontH = font.getStringHeight("A");
                float titleY = y + Math.max(0, (islandHeight - fontH) / 2f) + 5f;
                float textAreaW = Math.min(COMPACT_TITLE_WIDTH, Math.max(0, width - 10f - VISUALIZER_GAP - VISUALIZER_WIDTH));
                ScissorAssist scissor = ScissorAssist.INSTANCE;
                scissor.push(context.getMatrices().peek().getPositionMatrix(), titleX, y, textAreaW, height);
                font.drawStringWithScroll(context.getMatrices(), lab, titleX, titleY, textAreaW, textColor);
                scissor.pop();
            }
        }

        // Визуализатор только в свёрнутом виде, когда управление закрыто
        if (!controlsActive && !isExpanded) {
            drawVisualizer(context, vizX, centerY, playing);
        }

        // 2) Окно управления — отдельная разметка, когда controlsActive
        if (controlsActive) {
            drawControlsLayout(context, x, y, title, artist, info, playing, textColor);
        }
    }

    private void drawVisualizer(DrawContext context, float x, float centerY, boolean playing) {
        int barColor = ColorAssist.multAlpha(ColorAssist.getClientColor(1), VISUALIZER_ALPHA);
        int pauseColor = ColorAssist.getText(VISUALIZER_ALPHA);
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

    // КОД ИЗ ai_studio_code (2).java
    private void drawControlsLayout(DrawContext context,
                                    float x,
                                    float y,
                                    String title,
                                    String artist,
                                    MediaBridge.Snapshot info,
                                    boolean playing,
                                    int textColor) {
        MediaPlayer mp = MediaPlayer.getInstance();
        if (mp == null) return;

        // Если анимация только началась и высоты не хватает — прячем контент, чтобы не вылезал
        if (height < 30) return;

        float padding = 6f;

        // Ограничиваем рендер контента рамками островка при анимации
        ScissorAssist scissor = ScissorAssist.INSTANCE;
        scissor.push(context.getMatrices().peek().getPositionMatrix(), x, y, width, height);

        // ================= 1. ВЕРХНИЙ БЛОК: ОБЛОЖКА И ТЕКСТ =================
        float coverSize = 24f;
        float coverX = x + padding;
        float coverY = y + padding;

        // Рендер обложки с закруглением
        rectangle.render(ShapeProperties.create(context.getMatrices(), coverX, coverY, coverSize, coverSize)
                .round(8f)
                .color(ColorAssist.getRect(1))
                .build());

        // Обложка — из MusicManager; нет текстуры — остаётся только фон.
        net.minecraft.util.Identifier artTex = MusicManager.coverTexture();
        int artTexSize = MusicManager.coverSize();
        if (artTex != null && artTexSize > 0) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            // Приближаем обложку и обрезаем края (водяной знак по периметру):
            // - 15% со всех сторон
            // - + опциональная обрезка снизу (Spotify) из MediaPlayer
            int inset = Math.max(0, Math.round(artTexSize * 0.15f));
            int bottomExtra = Math.max(0, Math.round(artTexSize * mp.getArtworkCropBottom()));
            int insetBottom = inset + bottomExtra;

            int regionW = Math.max(1, artTexSize - inset * 2);
            int regionH = Math.max(1, artTexSize - inset - insetBottom);

            Render2D.drawTexture(context.getMatrices(), artTex,
                    coverX, coverX + coverSize, coverY, coverY + coverSize,
                    0f, regionW, regionH, (float) inset, (float) inset, artTexSize, artTexSize, 0xFFFFFFFF);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }

        // Название и автор (справа от обложки)
        float textX = coverX + coverSize + 6f;
        float maxTextWidth = width - (textX - x) - padding;

        var titleFont = Fonts.getSize(14, Fonts.Type.NOTVISUALS_MEDIUM);
        var artistFont = Fonts.getSize(11, Fonts.Type.NOTVISUALS_MEDIUM);

        float titleY = coverY + 2f;
        // Минимальный зазор между названием и артистом
        float artistY = titleY + titleFont.getStringHeight("A") - 6f;

        // Обрезаем текст, чтобы не вылезал за правый край
        scissor.push(context.getMatrices().peek().getPositionMatrix(), textX, y, maxTextWidth, height);
        titleFont.drawString(context.getMatrices(), title != null ? title : "Unknown", textX, titleY, 0xFFFFFFFF);
        if (artist != null && !artist.isEmpty()) {
            artistFont.drawString(context.getMatrices(), artist, textX, artistY, 0xFFA0A0A0); // Светло-серый
        }
        scissor.pop();

        // ================= 2. СРЕДНИЙ БЛОК: ПРОГРЕСС-БАР =================
        float progressY = coverY + coverSize + 4f; // Поднимаем выше, чтобы не наезжать на кнопки
        long durationMs = info.endMs();
        long positionMs = info.posMs();

        var timeFont = Fonts.getSize(9, Fonts.Type.NOTVISUALS_MEDIUM);
        String currTimeStr = formatTime(positionMs);
        String totTimeStr = formatTime(durationMs);

        float currTimeW = timeFont.getStringWidth(currTimeStr);
        float totTimeW = timeFont.getStringWidth(totTimeStr);

        // Выравниваем время ровно по центру шкалы (одна линия)
        float barH = 3.0f;
        float textH = timeFont.getStringHeight("A");
        float timeTextY = progressY - textH / 2f + 5f; // +5f — опускаем время до уровня шкалы

        // Рисуем время (слева и справа)
        timeFont.drawString(context.getMatrices(), currTimeStr, x + padding, timeTextY, 0xFFA0A0A0);
        timeFont.drawString(context.getMatrices(), totTimeStr, x + width - padding - totTimeW, timeTextY, 0xFFA0A0A0);

        // Сама полоска (отступ времени от шкалы меньше)
        float timeBarGap = 4f;
        float barX = x + padding + currTimeW + timeBarGap;
        float barRight = x + width - padding - totTimeW - timeBarGap;
        float barW = Math.max(0, barRight - barX);

        if (barW > 0) {
            int bgBarColor = 0xFF404040; // Темно-серый
            int fgBarColor = 0xFFFFFFFF; // Белый

            // Задний фон полоски
            rectangle.render(ShapeProperties.create(context.getMatrices(), barX, progressY - barH / 2f, barW, barH)
                    .round(1.75f)
                    .color(bgBarColor)
                    .build());

            // Заполненная часть
            float ratio = durationMs > 0 ? Math.min(1f, Math.max(0f, (float) positionMs / durationMs)) : 0f;
            float fillW = barW * ratio;
            if (fillW > 1f) {
                rectangle.render(ShapeProperties.create(context.getMatrices(), barX, progressY - barH / 2f, fillW, barH)
                        .round(1.75f)
                        .color(fgBarColor)
                        .build());
            }
        }

        // ================= 3. НИЖНИЙ БЛОК: КНОПКИ (PNG ТОЛЬКО ДЛЯ ОСТРОВКА) =================
        float iconSize = 14f;
        // Центруем иконки по одной линии и гарантированно внутри окна
        float controlsY = y + height - padding - iconSize + 2f; // +2px — опускаем кнопки
        float centerX = x + width / 2f;
        float spacing = 24f; // Чуть ближе друг к другу, чтобы влезали по краям

        // Текстуры 256x256 — используем полный размер, иначе берётся только 16x16 и иконки пиксельные
        int texSize = 256;

        // Выбираем цвет иконок по теме: белые на тёмной/прозрачной, чёрные на светлой
        int iconColor = 0xFFFFFFFF;
        if (com.loadercode.tabvisuals.gui.hud.HudTheme.isLight()) {
            iconColor = 0xFF000000;
        }

        // Кнопка "предыдущий" — своя PNG (уже отзеркаленная)
        float prevX1 = centerX - spacing - iconSize / 2f;
        float prevX2 = centerX - spacing + iconSize / 2f;
        Render2D.drawTexture(context.getMatrices(), ICON_BACK,
                prevX1, prevX2, controlsY, controlsY + iconSize,
                0f, texSize, texSize, 0f, 0f, texSize, texSize, iconColor);

        // Кнопка пауза/плей по центру — если музыка остановлена, показываем иконку play
        float playX1 = centerX - iconSize / 2f;
        float playX2 = centerX + iconSize / 2f;
        net.minecraft.util.Identifier playPauseIcon = playing ? ICON_PAUSE : ICON_PLAY;
        Render2D.drawTexture(context.getMatrices(), playPauseIcon,
                playX1, playX2, controlsY, controlsY + iconSize,
                0f, texSize, texSize, 0f, 0f, texSize, texSize, iconColor);

        // Кнопка "следующий"
        float nextX1 = centerX + spacing - iconSize / 2f;
        float nextX2 = centerX + spacing + iconSize / 2f;
        Render2D.drawTexture(context.getMatrices(), ICON_FORWARD,
                nextX1, nextX2, controlsY, controlsY + iconSize,
                0f, texSize, texSize, 0f, 0f, texSize, texSize, iconColor);

        scissor.pop(); // Снимаем главный Scissor
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
                float prevX1 = centerX - spacing - iconSize / 2f;
                float prevX2 = centerX - spacing + iconSize / 2f;
                float playX1 = centerX - iconSize / 2f;
                float playX2 = centerX + iconSize / 2f;
                float nextX1 = centerX + spacing - iconSize / 2f;
                float nextX2 = centerX + spacing + iconSize / 2f;

                if (hit(mouseX, mouseY, prevX1, controlsY, prevX2 - prevX1, iconSize)) {
                    controlExecutor.execute(() -> MediaPlayer.sendCommand("prev"));
                    return;
                }
                if (hit(mouseX, mouseY, playX1, controlsY, playX2 - playX1, iconSize)) {
                    controlExecutor.execute(() -> MediaPlayer.sendCommand("playPause"));
                    return;
                }
                if (hit(mouseX, mouseY, nextX1, controlsY, nextX2 - nextX1, iconSize)) {
                    controlExecutor.execute(() -> MediaPlayer.sendCommand("next"));
                    return;
                }
            }
        }

        // Клик не по кнопке — переключаем панель управления
        controlsVisible = !controlsVisible;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void onOutsideClick() {
        controlsVisible = false;
    }

    // КОД ИЗ ai_studio_code.java
    private String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public int getColor() {
        return 0xE0150A20;
    }
}
