/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import lombok.Getter;
import com.loadercode.tabvisuals.gui.hud.Hud;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPlayer extends AbstractDraggable {
    private final ExecutorService controlExecutor = Executors.newCachedThreadPool();
    /** Пустой снапшот до первого опроса (MediaBridge.Snapshot.NONE package-private — не используем). */
    private static final MediaBridge.Snapshot EMPTY = new MediaBridge.Snapshot(false, "", "", "", "", false, 0, 0, false, 0);
    /** Последний опрос моста: позиция уже экстраполирована по wall-clock внутри MediaBridge. */
    private volatile MediaBridge.Snapshot media = EMPTY;
    private final StopWatch lastMedia = new StopWatch();
    private float widthDuration;
    private FontRenderer bigFont;
    private FontRenderer miniFont;
    private FontRenderer iconFont;

    private static final float SPOTIFY_ARTWORK_CROP_BOTTOM = 0.20f;
    @Getter
    private volatile float artworkCropBottom = 0f;

    public MediaPlayer() {
        super("Media Player", 10, 120, 100, 40, true);
    }

    public static MediaPlayer getInstance() {
        return Instance.getDraggable(MediaPlayer.class);
    }

    /** Последний снапшот моста (обновляется в tick()); позиция уже экстраполирована. */
    public MediaBridge.Snapshot snapshot() {
        return media;
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
        return app != null && !app.isEmpty() && app.toLowerCase().startsWith("spotify");
    }

    private void ensureFonts() {
        if (bigFont == null) {
            bigFont = Fonts.getSize(14, Fonts.Type.DEFAULT);
            miniFont = Fonts.getSize(11, Fonts.Type.DEFAULT);
            iconFont = Fonts.getSize(17, Fonts.Type.ICONS);
        }
    }

    private boolean isButtonArea(double mouseX, double mouseY, float buttonX, float buttonSize, float buttonY, float buttonHeight) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonSize && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
    }

    /**
     * Команда плееру уходит через файл — внешний скрипт-мост читает его вместо
     * прямого control-канала. Вызывать внутри controlExecutor.
     */
    public static void sendCommand(String command) {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("tabvisuals").resolve("media_cmd.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, command + "\n" + System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            ensureFonts();
            int sw = Math.max(24, getHeight() - 8);
            // Хитбоксы считаем строго по тем же формулам, что и отрисовка иконок
            // (см. drawDraggable), иначе клик не попадал в нарисованную кнопку.
            float baseX = (float) (getX() + (getWidth() + sw + 4 - 4) / 2);
            float playPauseX = baseX + 4.5f;
            float prevX = baseX - 4;
            float nextX = baseX + 14f;
            float y = getY() + 25.5f;
            float buttonHeight = iconFont.getStringHeight("I");
            if (media.active() && isButtonArea(mouseX, mouseY, playPauseX, iconFont.getStringWidth("I"), y, buttonHeight)) {
                controlExecutor.execute(() -> sendCommand("playPause"));
                return true;
            }
            if (media.active() && isButtonArea(mouseX, mouseY, prevX, iconFont.getStringWidth("O"), y, buttonHeight)) {
                controlExecutor.execute(() -> sendCommand("prev"));
                return true;
            }
            if (media.active() && isButtonArea(mouseX, mouseY, nextX, iconFont.getStringWidth("P"), y, buttonHeight)) {
                controlExecutor.execute(() -> sendCommand("next"));
                return true;
            }
            if (isCanDrag()) return super.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        ScissorAssist scissor = ScissorAssist.INSTANCE;
        ensureFonts();

        int sizeArtwork = Math.max(24, getHeight() - 8);
        int sizePausePlay = 4;
        int maxDurationWidth = getWidth() - (sizeArtwork + 12) + 15;

        long rawDuration = media.endMs();
        // Позицию мост уже экстраполирует по wall-clock — берём как есть.
        long rawPosition = media.posMs();
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
        bigFont.drawStringWithScroll(matrix, media.title(), getX() + sizeArtwork + 8, getY() + 7, 56, ColorAssist.getText());
        miniFont.drawStringWithScroll(matrix, media.artist(), getX() + sizeArtwork + 8, getY() + 15.5F, 56, ColorAssist.getText(0.75F));
        scissor.pop();

        float ax = getX() + 4;
        float ay = getY() + 4;
        int artworkWidthExtra = (artworkCropBottom > 0f) ? 9 : 0;
        float drawW = (float) sizeArtwork + artworkWidthExtra;
        float drawX1 = ax - artworkWidthExtra / 2f;
        rectangle.render(ShapeProperties.create(matrix, ax, ay, (float) sizeArtwork, (float) sizeArtwork).round(3).color(ColorAssist.getRect(1)).build());
        // Обложку тянем из MusicManager (регистрируется один раз на трек); нет
        // текстуры — рисуем только тёмный фон (он выше по коду).
        Identifier artTex = MusicManager.coverTexture();
        int artSize = MusicManager.coverSize();
        if (artTex != null && artSize > 0) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            if (artworkCropBottom > 0 && artworkCropBottom < 1) {
                int regionH = (int) (artSize * (1f - artworkCropBottom));
                Render2D.drawTexture(matrix, artTex, drawX1, drawX1 + drawW, ay, ay + (float) sizeArtwork, 0f, artSize, regionH, 0f, 0f, artSize, artSize, 0xFFFFFFFF);
            } else {
                Render2D.drawTexture(matrix, artTex, drawX1, drawX1 + drawW, ay, ay + (float) sizeArtwork, 0f, artSize, artSize, 0f, 0f, artSize, artSize, 0xFFFFFFFF);
            }
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
        miniFont.drawString(matrix, timePosition, getX() + 8 + sizeArtwork, getY() + 27, ColorAssist.getText());
        miniFont.drawString(matrix, timeDuration, getX() + getWidth() - 4 - miniFont.getStringWidth(timeDuration) + 15, getY() + 27, ColorAssist.getText());

        rectangle.render(ShapeProperties.create(matrix, getX() + 8 + sizeArtwork, getY() + getHeight() - 8f, maxDurationWidth, 3.5f).round(0.85F).color(ColorAssist.getRectDarker(0.75F)).build());
        rectangle.render(ShapeProperties.create(matrix, getX() + 8 + sizeArtwork, getY() + getHeight() - 8f, widthDuration, 3.5f).softness(4).round(1).color(ColorAssist.roundClientColor(0.2F)).build());
        rectangle.render(ShapeProperties.create(matrix, getX() + 8 + sizeArtwork, getY() + getHeight() - 8f, widthDuration, 3.5f).round(0.85F).color(ColorAssist.roundClientColor(1)).build());

        iconFont.drawString(matrix, (media.playing() ? "I" : "H"), (float) (getX() + (getWidth() + sizeArtwork + 4 - sizePausePlay) / 2) + 4.5f, getY() + 25.5f, ColorAssist.getText());
        iconFont.drawString(matrix, "O", (float) (getX() + (getWidth() + sizeArtwork + 4 - sizePausePlay) / 2) - 4, getY() + 25.5f, ColorAssist.getText());
        iconFont.drawString(matrix, "P", (float) (getX() + (getWidth() + sizeArtwork + 4 - sizePausePlay) / 2) + 14f, getY() + 25.5f, ColorAssist.getText());
    }
}
