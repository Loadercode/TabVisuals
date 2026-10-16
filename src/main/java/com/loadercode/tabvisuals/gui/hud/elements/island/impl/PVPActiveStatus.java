/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
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
    private static final float ICON_SIZE = 11f;
    private static final float ICON_GAP = 3f;

    @Override
    public boolean canShow() {
        return Network.isHasPvpActive();
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        String text = "Вы в PVP режиме";

        var font7 = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        float boxW = 8f;   // маленький индикатор без таймера
        // 4 слева + иконка + зазор + индикатор + 2 зазор + текст + 4 справа
        width = 4f + ICON_SIZE + ICON_GAP + boxW + 2f + font7.getStringWidth(text) + 4f;
        height = 17f;
        float boxH = 12f;
        float iconX = x + 4;
        float boxX = iconX + ICON_SIZE + ICON_GAP;
        float boxY = y + 2f;
        float textY = HudHelper.hud_text_mid_y(font7, text, y, height);

        int textColor = UiRender.mulAlpha(ColorAssist.getText(), alpha);
        Icons.draw(context.getMatrices(), Icons.SWORDS, iconX,
                UiRender.iconY(Icons.SWORDS, ICON_SIZE, y + height / 2f), ICON_SIZE, textColor);
        rectangle.render(ShapeProperties.create(context.getMatrices(), boxX, boxY, boxW, boxH).round(3f).color(UiRender.mulAlpha(PVP_COLOR, alpha)).build());
        font7.drawStringSnapped(context.getMatrices(), text, boxX + boxW + 2, textY, textColor);
    }

    @Override
    public int getColor() {
        return 0xE0201010;
    }
}
