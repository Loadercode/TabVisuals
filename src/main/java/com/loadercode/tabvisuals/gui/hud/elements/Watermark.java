/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.google.common.base.Suppliers;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.atlasfont.msdf.MsdfFont;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.systemrender.builders.Builder;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.function.Supplier;

public class Watermark extends AbstractDraggable {
    private int fpsCount = 0;
    private static final Supplier<MsdfFont> ICONS_FONT_1 = Suppliers.memoize(() -> MsdfFont.builder().atlas("clienticon1").data("clienticon1").build());
    private FontRenderer default12;
    private FontRenderer default13;
    private FontRenderer iconNew17;
    private String cachedServerIp = "";
    private long lastServerIpUpdate;

    /** Длительность въезда: альфа набирается быстрее, сдвиг догоняет. */
    private static final float ENTRANCE_MS = 420f;
    /** Высота панели: конструктор даёт 16, панель рисуется на 4px выше/ниже — фиксированная. */
    private static final float PANEL_HEIGHT = 20f;
    /** Пауза отрисовки, после которой въезд проигрывается заново (скрытие/показ HUD). */
    private static final float ENTRANCE_GAP_MS = 350f;
    /** Сдвиг въезда по X — плашка выезжает слева. */
    private static final float ENTRANCE_SLIDE_X = -18f;

    private long entranceStartNanos;
    private long lastDrawNanos;

    public Watermark() {
        super("Watermark", 10, 10, 92, 16, true);
    }

    @Override
    public void tick() {
        fpsCount = mc.getCurrentFps();
    }

    @Override
    public void drawDraggable(DrawContext e) {
        MatrixStack matrix = e.getMatrices();

        // Въезд при первом показе и после паузы отрисовки: альфа + сдвиг слева.
        long now = System.nanoTime();
        if (lastDrawNanos == 0L || entranceStartNanos == 0L
                || (now - lastDrawNanos) / 1_000_000f > ENTRANCE_GAP_MS) {
            entranceStartNanos = now;
        }
        lastDrawNanos = now;
        float enterT = clamp01((now - entranceStartNanos) / 1_000_000f / ENTRANCE_MS);
        float alpha = 1f - (float) Math.pow(1f - enterT, 3);   // альфа: быстрый набор
        float slideX = (float) Math.pow(1f - enterT, 5) * ENTRANCE_SLIDE_X; // сдвиг: длинный хвост

        matrix.push();
        matrix.translate((double) slideX, 0.0, 0.0);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();
        String fps = String.valueOf(fpsCount);
        String serverIp = getServerIpCached();
        String title = "TabVisuals";
        if (default12 == null) {
            default12 = Fonts.getSize(12, Fonts.Type.DEFAULT);
            default13 = Fonts.getSize(13, Fonts.Type.DEFAULT);
            iconNew17 = Fonts.getSize(17, Fonts.Type.ICONSTYPENEW);
        }

        float iconWidth = 11f;
        // Замеры тем же default13, которым текст рисуется ниже (title/fps/serverIp
        // рисуются в строках 110/121/136 default13, а раньше мерялись default12/
        // bold12 — ширина фона получалась меньше контента). Сумма считается по
        // реальным шагам отрисовки (7/4/9/5/2/10/5/2.5), а не по «pointWidth * 3 + 22».
        float titleWidth = default13.getStringWidth(title);
        float fpsWidth = default13.getStringWidth(fps);
        float serverIpWidth = default13.getStringWidth(serverIp);
        float icon2Width = ICONS_FONT_1.get().getWidth("D", 11);
        float totalWidth = iconWidth + 7 + 4 + titleWidth + 9 + 5 + fpsWidth + 2 + 10 + 5
                + serverIpWidth + 2.5f + icon2Width;
        float finalWidth = totalWidth + 9;
        // getHeight()+4 росло бы на 4px каждый кадр, стоило лишь где-то вызвать
        // setHeight; заодно хитбокс редактора совпадает с нарисованной панелью
        float finalHeight = PANEL_HEIGHT;

        setWidth((int) finalWidth);
        setHeight((int) PANEL_HEIGHT);
        com.loadercode.tabvisuals.render.geometry.Render2D.renderElementShadow(matrix, getX(), getY(), finalWidth, finalHeight);


        blur.render(ShapeProperties.create(matrix, getX(), getY(), finalWidth, finalHeight)
                .round(5f).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                .color(ColorAssist.multAlpha(ColorAssist.getBlurColor(), alpha))
                .build());

        // градиент приходит общим кэшем темы — копируем в локальные, сам массив не трогаем
        int[] gc = ColorAssist.getRectangleGradientColors();
        int gc0 = ColorAssist.multAlpha(gc[0], alpha);
        int gc1 = ColorAssist.multAlpha(gc[1], alpha);
        int gc2 = ColorAssist.multAlpha(gc[2], alpha);
        int gc3 = ColorAssist.multAlpha(gc[3], alpha);
        rectangle.render(ShapeProperties.create(matrix, getX(), getY(), finalWidth, finalHeight)
                .round(5f)
                .thickness(0.1f)
                .outlineColor(ColorAssist.multAlpha(ColorAssist.getRectangleOutlineColor(), alpha))
                .color(gc0, gc1, gc2, gc3)
                .build());

        // Отрисовка логотипа по центру по высоте бара
        float logoX = getX() + 5f;                          // небольшой отступ слева
        float logoY = getY() + (finalHeight - iconWidth) / 2f; // вертикальный центр

        // dj's client logo (LogoManager); text-only bar while the asset is absent
        Identifier logo = com.loadercode.tabvisuals.gui.title.LogoManager.texture();
        if (logo != null) {
            int logoTexW = Math.max(1, com.loadercode.tabvisuals.gui.title.LogoManager.width());
            int logoTexH = Math.max(1, com.loadercode.tabvisuals.gui.title.LogoManager.height());
            // blend back on — rectangle.render left the pipeline with blend disabled
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            Render2D.drawTexture(matrix, logo, (int) logoX, (int) logoY,
                    iconWidth, 11f,
                    0, 0, logoTexW, logoTexH,
                    logoTexW, logoTexH,
                    ColorAssist.multAlpha(0xFFFFFFFF, alpha));
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }

        float currentX = getX() + iconWidth + 7;

        rectangle.render(ShapeProperties.create(matrix, currentX, getY() + 6.5f, 0.5F, getHeight() - 8)
                .round(0)
                .color(ColorAssist.getText(0.5F * alpha))
                .build());

        currentX += 4;

        default13.drawString(matrix, title, currentX + 2.5f, getY() + 9f,
                ColorAssist.multAlpha(ColorAssist.getText(), alpha));

        currentX += titleWidth + 9;

        rectangle.render(ShapeProperties.create(matrix, currentX, getY() + 6.5f, 0.5F, getHeight() - 8)
                .round(0f)
                .color(ColorAssist.getText(0.5F * alpha))
                .build());

        currentX += 5;

        default13.drawString(matrix, fps, currentX, getY() + 9f,
                ColorAssist.multAlpha(ColorAssist.getText(), alpha));

        currentX += fpsWidth + 2;

        // icon2 'n' = clock badge; 'w' was the chart glyph and rendered as an
        // odd blob at 17px (dj's photo)
        iconNew17.drawString(matrix, "n", currentX, getY() + 9f, ColorAssist.multAlpha(0xFFFFFFFF, alpha));

        currentX += 10;

        rectangle.render(ShapeProperties.create(matrix, currentX, getY() + 6.5f, 0.5F, getHeight() - 8)
                .round(0)
                .color(ColorAssist.getText(0.5F * alpha))
                .build());

        currentX += 5;

        default13.drawString(matrix, serverIp, currentX, getY() + 9f,
                ColorAssist.multAlpha(ColorAssist.getText(), alpha));

        currentX += serverIpWidth;

        Builder.text()
                .font(ICONS_FONT_1.get())
                .text("D")
                .size(11)
                .color(ColorAssist.multAlpha(ColorAssist.getText(), alpha))
                .build()
                .render(matrix4f, currentX + 2.5f, getY() + 2f);

        matrix.pop();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    private String getServerIpCached() {
        long now = System.currentTimeMillis();
        if (now - lastServerIpUpdate >= 1000L || cachedServerIp.isEmpty()) {
            cachedServerIp = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
            lastServerIpUpdate = now;
        }
        return cachedServerIp;
    }
}
