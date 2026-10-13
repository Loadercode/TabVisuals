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

import java.awt.Color;
import java.util.Objects;
import java.util.function.Supplier;

public class Watermark extends AbstractDraggable {
    private int fpsCount = 0;
    private static final Supplier<MsdfFont> ICONS_FONT_1 = Suppliers.memoize(() -> MsdfFont.builder().atlas("clienticon1").data("clienticon1").build());
    private FontRenderer default12;
    private FontRenderer default13;
    private FontRenderer bold12;
    private FontRenderer iconNew17;
    private String cachedServerIp = "";
    private long lastServerIpUpdate;


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
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();
        String fps = String.valueOf(fpsCount);
        String serverIp = getServerIpCached();
        String title = "TabVisuals";
        if (default12 == null) {
            default12 = Fonts.getSize(12, Fonts.Type.DEFAULT);
            default13 = Fonts.getSize(13, Fonts.Type.DEFAULT);
            bold12 = Fonts.getSize(12, Fonts.Type.BOLD);
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
        float finalHeight = getHeight() + 4;

        setWidth((int) finalWidth);
        com.loadercode.tabvisuals.render.geometry.Render2D.renderElementShadow(matrix, getX(), getY(), finalWidth, finalHeight);


        blur.render(ShapeProperties.create(matrix, getX(), getY(), finalWidth, finalHeight)
                .round(5f).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                .color(ColorAssist.getBlurColor())
                .build());

        int[] gc = ColorAssist.getRectangleGradientColors();
        rectangle.render(ShapeProperties.create(matrix, getX(), getY(), finalWidth, finalHeight)
                .round(5f)
                .thickness(0.1f)
                .outlineColor(ColorAssist.getRectangleOutlineColor())
                .color(gc[0], gc[1], gc[2], gc[3])
                .build());

        // Отрисовка логотипа по центру по высоте бара
        int logoSizeInt = (int) iconWidth;
        float logoX = getX() + 5f;                          // небольшой отступ слева
        float logoY = getY() + (finalHeight - iconWidth) / 2f; // вертикальный центр

        // dj's client logo (LogoManager); text-only bar while the asset is absent
        Identifier logo = com.loadercode.tabvisuals.gui.title.LogoManager.texture();
        if (logo != null) {
            int logoTexW = Math.max(1, com.loadercode.tabvisuals.gui.title.LogoManager.width());
            int logoTexH = Math.max(1, com.loadercode.tabvisuals.gui.title.LogoManager.height());
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            Render2D.drawTexture(matrix, logo, (int) logoX, (int) logoY,
                    iconWidth, 11f,
                    0, 0, logoTexW, logoTexH,
                    logoTexW, logoTexH,
                    0xFFFFFFFF);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        float currentX = getX() + iconWidth + 7;

        rectangle.render(ShapeProperties.create(matrix, currentX, getY() + 6.5f, 0.5F, getHeight() - 8)
                .round(0)
                .color(ColorAssist.getText(0.5F))
                .build());

        currentX += 4;

        default13.drawString(matrix, title, currentX + 2.5f, getY() + 9f, ColorAssist.getText());

        currentX += titleWidth + 9;

        rectangle.render(ShapeProperties.create(matrix, currentX, getY() + 6.5f, 0.5F, getHeight() - 8)
                .round(0f)
                .color(ColorAssist.getText(0.5F))
                .build());

        currentX += 5;

        default13.drawString(matrix, fps, currentX, getY() + 9f, ColorAssist.getText());

        currentX += fpsWidth + 2;

        iconNew17.drawString(matrix, "w", currentX, getY() + 9f, 0xFFFFFFFF);

        currentX += 10;

        rectangle.render(ShapeProperties.create(matrix, currentX, getY() + 6.5f, 0.5F, getHeight() - 8)
                .round(0)
                .color(ColorAssist.getText(0.5F))
                .build());

        currentX += 5;

        default13.drawString(matrix, serverIp, currentX, getY() + 9f, ColorAssist.getText());

        currentX += serverIpWidth;

        Builder.text()
                .font(ICONS_FONT_1.get())
                .text("D")
                .size(11)
                .color(ColorAssist.getText())
                .build()
                .render(matrix4f, currentX + 2.5f, getY() + 2f);
//
//        currentX += 5;
//        assert mc.player != null;
//        int ping = Objects.requireNonNull(Objects.requireNonNull(mc.getNetworkHandler()).getPlayerListEntry(mc.player.getUuid())).getLatency();
//
//        default13.drawString(matrix, String.valueOf(ping), currentX, getY(), ColorAssist.getText());
//
//


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
