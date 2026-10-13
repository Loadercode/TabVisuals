/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;

public class DefaultStatus extends IslandStatus implements QuickImports {

    @Override
    public boolean canShow() {
        return true;
    }

    @Override
    public boolean hidesVanillaBossBar() {
        return false;
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        var font = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        width = 12f + font.getStringWidth("LoxeDLC");
        height = 17f;
        float boxH = 12f;
        float boxY = y + 2f;
        float textY = boxY + boxH + 2f - font.getStringWidth("LoxeDLC") / 4f;
        font.drawString(context.getMatrices(), "LoxeDLC", x + 6, textY, ColorAssist.getText());
    }
}
