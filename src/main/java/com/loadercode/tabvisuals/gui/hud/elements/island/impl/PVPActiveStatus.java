/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.util.Network;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

/**
 * Вечный PVP (босбар «PVP режим: Активен») — без таймера.
 */
public class PVPActiveStatus extends IslandStatus implements QuickImports {

    private static final int PVP_COLOR = 0xFFB91C1C;

    @Override
    public boolean canShow() {
        return Network.isHasPvpActive();
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        String text = "Вы в PVP режиме";

        var font7 = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        float boxW = 8f;   // маленький индикатор без таймера
        width = 10f + boxW + font7.getStringWidth(text);  // 4 слева + 2 зазор + 4 справа после текста
        height = 17f;
        float boxH = 12f;
        float boxX = x + 4;
        float boxY = y + 2f;
        float textY = boxY + boxH + 2f - font7.getStringHeight("A") / 2f;

        rectangle.render(ShapeProperties.create(context.getMatrices(), boxX, boxY, boxW, boxH).round(3f).color(PVP_COLOR | 0xFF000000).build());
        font7.drawString(context.getMatrices(), text, boxX + boxW + 2, textY, ColorAssist.getText());
    }

    @Override
    public int getColor() {
        return 0xE0201010;
    }
}
