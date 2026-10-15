/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;

public class DefaultStatus extends IslandStatus implements QuickImports {

    private static final String LABEL = "LoxeDLC";

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
        width = 12f + font.getStringWidth(LABEL);
        height = 17f;
        // центр строки: getStringWidth больше не завышает, старая компенсация шириной здесь не нужна
        float textY = HudHelper.hud_text_mid_y(font, LABEL, y, height);
        font.drawStringSnapped(context.getMatrices(), LABEL, x + 6, textY,
                UiRender.mulAlpha(ColorAssist.getText(), alpha));
    }
}
