/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.util.Network;
import com.loadercode.tabvisuals.render.math.calc.Calculate;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.gui.hud.compat.NameProtect;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

import java.awt.*;
import java.util.Objects;

public class PlayerInfo extends AbstractDraggable {
    public PlayerInfo() {
        super("Player Info", 0, 0, 60, 0, false);
    }

    @Override
    public void drawDraggable(DrawContext context) {
        int offset = HudHelper.isChat(mc.currentScreen) ? -28 : -15;
        setY(mc.getWindow().getScaledHeight() + offset);

        // Соседние элементы (Saturation, Armor) игрока проверывают — здесь
        // requireNonNull бросал NPE, которую выше ловил per-draggable catch:
        // элемент мигал и пропадал из кадра, а в лог падал stacktrace.
        if (mc.player == null) return;
        BlockPos blockPos = mc.player.getBlockPos();
        FontRenderer font = Fonts.getSize(14, Fonts.Type.DEFAULT);

        boolean hideCoords = NameProtect.getInstance() != null && NameProtect.getInstance().isHideCoords();

        double bpsValue = mc.player.getVelocity().horizontalLength() * 20.0;
        String bps = "Bps: " + Calculate.round(bpsValue, 0.25F);
        String tps = "Tps: " + Calculate.round(Network.TPS, 0.1F);
        String xyz = hideCoords ? "Xyz: 0, 0, 0" : "Xyz: " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ();
        String text = xyz + " • " + tps + " • " + bps;

        float width = font.getStringWidth(text) + 10;
        setWidth((int) width);
        setHeight(14);

        Hud hud = Hud.getInstance();
        if (hud != null && hud.shouldDrawHudBlur()) {
            blur.render(ShapeProperties.create(context.getMatrices(), getX(), getY() + 1, width + 4, 14)
                    .round(4)
                    .quality(hud.getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
        }

        font.drawGradientString(context.getMatrices(), text, getX() + 3, getY() + 6.5F, ColorAssist.getText(0.9f), ColorAssist.getText());
    }
}
