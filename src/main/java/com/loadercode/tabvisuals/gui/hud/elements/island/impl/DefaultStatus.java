/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;

public class DefaultStatus extends IslandStatus implements QuickImports {

    private static final String LABEL = "LoxeDLC";
    private static final float ICON_SIZE = 11f;
    private static final float ICON_GAP = 4f;
    private static final float EDGE_PAD = 6f;

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
        // Брендовая метка слева от названия: LOGO, а не CLOCK — часы и так стоят
        // отдельной строкой слева от островка (см. DynamicIsland), дублировать их незачем
        width = EDGE_PAD + ICON_SIZE + ICON_GAP + font.getStringWidth(LABEL) + EDGE_PAD;
        height = 17f;
        // центр строки: getStringWidth больше не завышает, старая компенсация шириной здесь не нужна
        float textY = HudHelper.hud_text_mid_y(font, LABEL, y, height);
        int color = UiRender.mulAlpha(ColorAssist.getText(), alpha);
        Icons.draw(context.getMatrices(), Icons.LOGO, x + EDGE_PAD,
                UiRender.iconY(Icons.LOGO, ICON_SIZE, y + height / 2f), ICON_SIZE, color);
        font.drawStringSnapped(context.getMatrices(), LABEL, x + EDGE_PAD + ICON_SIZE + ICON_GAP, textY, color);
    }
}
