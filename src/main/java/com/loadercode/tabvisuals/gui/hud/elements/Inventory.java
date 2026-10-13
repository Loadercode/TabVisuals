/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.geometry.Render2D;

import java.awt.*;

public class Inventory extends AbstractDraggable {
    private static final int SLOT_COUNT = 27;
    private static final int BODY_OUTLINE = new Color(33, 33, 33, 255).getRGB();
    private static final int BODY_COLOR_1 = new Color(18, 19, 20, 75).getRGB();
    private static final int BODY_COLOR_2 = new Color(0, 2, 5, 75).getRGB();

    private final ItemStack[] stacks = new ItemStack[SLOT_COUNT];
    private boolean hasItems;
    private long itemCount;
    private FontRenderer fontCached;
    private FontRenderer iconCached;
    private FontRenderer itemsCached;

    public Inventory() {
        super("Inventory", 385, 40, 123, 60, true);
    }

    @Override
    public boolean visible() {
        return hasItems || HudHelper.isChat(mc.currentScreen);
    }

    @Override
    public void tick() {
        if (mc.player == null) {
            hasItems = false;
            itemCount = 0;
            return;
        }

        hasItems = false;
        itemCount = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i + 9);
            stacks[i] = stack;
            if (stack.isEmpty()) continue;
            hasItems = true;
            itemCount += stack.getCount();
        }
    }

    @Override
    public void drawDraggable(DrawContext context) {
        MatrixStack matrix = context.getMatrices();
        if (fontCached == null) {
            fontCached    = Fonts.getSize(14, Fonts.Type.DEFAULT);
            iconCached    = Fonts.getSize(20, Fonts.Type.ICONS);
            itemsCached   = Fonts.getSize(12, Fonts.Type.DEFAULT);
        }

        String itemCountText = String.valueOf(itemCount);
        float textWidth = itemsCached.getStringWidth(itemCountText);
        float boxWidth = textWidth + 6;

        int[] gradientColors = ColorAssist.getRectangleGradientColors();
        blur.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), 15.5F)
                    .round(4, 0, 4, 0).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY(), getWidth(), 15.5F)
                    .round(4, 0, 4, 0)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                    .color(gradientColors[0], gradientColors[1], gradientColors[2], gradientColors[3])
                    .build());
            blur.render(ShapeProperties.create(matrix, getX(), getY() + 16.40f, getWidth(), getHeight() - 15)
                    .round(0, 4, 0, 4).quality(com.loadercode.tabvisuals.gui.hud.Hud.getInstance().getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
            rectangle.render(ShapeProperties.create(matrix, getX(), getY() + 16.40f, getWidth(), getHeight() - 15)
                    .round(0, 4, 0, 4)
                    .thickness(0.1f)
                    .outlineColor(ColorAssist.getOutlineColor())
                    .color(gradientColors[0], gradientColors[1], gradientColors[2], gradientColors[3])
                .build());

        itemsCached.drawString(matrix, "Items:", getX() + getWidth() - boxWidth - 21, getY() + 7, ColorAssist.getText());
        itemsCached.drawString(matrix, itemCountText, getX() + getWidth() - boxWidth - 2, getY() + 7, ColorAssist.getText());

        rectangle.render(ShapeProperties.create(matrix, getX() + 18, getY() + 5, 0.5f, 6).color(ColorAssist.getText(0.5F)).round(0F).build());

        iconCached.drawString(matrix, "F", getX() + 4.5f, getY() + 6, ColorAssist.getText());
        fontCached.drawString(matrix, getName(), getX() + 22, getY() + 6.5f, ColorAssist.getText());

        int offsetY = 20;
        int offsetX = 4;
        int itemsPerRow = 9;
        int itemIndex = 0;

        int separatorColor = ColorAssist.getText(0.1F);
        float gridX = getX() + 14.5f;
        float gridY = getY() + 21f;
        for (int col = 0; col < 8; col++) {
            Render2D.drawQuad(gridX + col * 13f, gridY, 0.5f, 35f, separatorColor);
        }
        for (int row = 0; row < 2; row++) {
            Render2D.drawQuad(getX() + 4.5f, getY() + 31f + row * 13f, 113f, 0.5f, separatorColor);
        }

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = stacks[i];
            float itemX = getX() + offsetX + 1;
            float itemY = getY() + offsetY + 1f;

            if (stack != null && !stack.isEmpty()) {
                Render2D.defaultDrawStack(context, stack, itemX - 1f, itemY - 1f, false, false, 0.5F);
            }

            offsetX += 13;
            itemIndex++;

            if (itemIndex % itemsPerRow == 0) {
                offsetY += 13;
                offsetX = 4;
            }
        }
    }
}
