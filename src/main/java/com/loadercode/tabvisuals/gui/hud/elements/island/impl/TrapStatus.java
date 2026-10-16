/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island.impl;

import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.gui.UiRender;
import com.loadercode.tabvisuals.gui.icon.Icons;
import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import com.loadercode.tabvisuals.gui.hud.elements.island.IslandStatus;
import com.loadercode.tabvisuals.gui.hud.compat.FTHelper;
import com.loadercode.tabvisuals.util.Network;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

public class TrapStatus extends IslandStatus implements QuickImports {

    private static final int TRAP_COLOR = 0xFFB91C1C;  // красный фон окна времени
    private static final float TIME_BOX_PAD = 3f;
    private static final float ICON_SIZE = 11f;
    private static final float ICON_GAP = 3f;

    @Override
    public boolean canShow() {
        // Instance.get возвращает null, пока модуль не зарегистрирован (см. ModuleProvider.get) —
        // без проверки статус ронял бы отрисовку Dynamic Island; EventStatus ту же проверку делает.
        FTHelper ft = FTHelper.getInstance();
        return ft != null && Network.isFunTime() && ft.isTrapActive();
    }

    @Override
    public void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim) {
        FTHelper ft = FTHelper.getInstance();
        if (ft == null) return;
        int sec = ft.getTrapSecondsRemaining();
        String timeStr = sec + "с";
        String text = "Трапка";

        var font7 = Fonts.getSize(18, Fonts.Type.NOTVISUALS_MEDIUM);
        float timeW = mc.textRenderer.getWidth(timeStr);
        float boxW = TIME_BOX_PAD * 2 + timeW;
        // 4 слева + иконка + зазор + окно времени + 2 зазор + текст + 4 справа
        width = 4f + ICON_SIZE + ICON_GAP + boxW + 2f + font7.getStringWidth(text) + 4f;
        height = 17f;
        float boxH = 12f;
        float iconX = x + 4;
        float boxX = iconX + ICON_SIZE + ICON_GAP;
        float boxY = y + 2f;
        float timeY = boxY + (boxH - mc.textRenderer.fontHeight) / 2f + 1f;
        float textY = HudHelper.hud_text_mid_y(font7, text, y, height);

        int textColor = UiRender.mulAlpha(ColorAssist.getText(), alpha);
        // щит слева от таймера — та же baseline-коррекция, что у строк HUD
        Icons.draw(context.getMatrices(), Icons.SHIELD, iconX,
                UiRender.iconY(Icons.SHIELD, ICON_SIZE, y + height / 2f), ICON_SIZE, textColor);
        rectangle.render(ShapeProperties.create(context.getMatrices(), boxX, boxY, boxW, boxH).round(3f).color(UiRender.mulAlpha(TRAP_COLOR, alpha)).build());
        context.drawText(mc.textRenderer, timeStr, (int) (boxX + TIME_BOX_PAD), (int) timeY, textColor, false);
        font7.drawStringSnapped(context.getMatrices(), text, boxX + boxW + 2, textY, textColor);
    }

}
