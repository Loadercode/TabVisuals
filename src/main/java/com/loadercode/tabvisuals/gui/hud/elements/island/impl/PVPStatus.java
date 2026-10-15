/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.util.Network;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

public class PVPStatus extends IslandStatus implements QuickImports {

    private static final int PVP_COLOR = 0xFFB91C1C;
    private static final float TIME_BOX_PAD = 3f;  // отступ цифр внутри маленького окна таймера

    @Override
    public boolean canShow() {
        return Network.isHasPvpCountdown();
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        int sec = Network.getPvpCountdownSeconds();
        String timeStr = sec + "с";
        String text = "Вы в PVP режиме";

        var font7 = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        float timeW = mc.textRenderer.getWidth(timeStr);
        float boxW = TIME_BOX_PAD * 2 + timeW;
        width = 10f + boxW + font7.getStringWidth(text);  // 4 слева + 2 зазор + 4 справа после текста
        height = 17f;
        float boxH = 12f;
        float boxX = x + 4;
        float boxY = y + 2f;
        float timeY = boxY + (boxH - mc.textRenderer.fontHeight) / 2f + 1f;
        float textY = HudHelper.hud_text_mid_y(font7, text, y, height);

        int textColor = UiRender.mulAlpha(ColorAssist.getText(), alpha);
        rectangle.render(ShapeProperties.create(context.getMatrices(), boxX, boxY, boxW, boxH).round(3f).color(UiRender.mulAlpha(PVP_COLOR, alpha)).build());
        context.drawText(mc.textRenderer, timeStr, (int) (boxX + TIME_BOX_PAD), (int) timeY, textColor, false);
        font7.drawStringSnapped(context.getMatrices(), text, boxX + boxW + 2, textY, textColor);
    }

    @Override
    public int getColor() {
        return 0xE0201010;
    }
}
