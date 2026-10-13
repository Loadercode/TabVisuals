/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;
import com.loadercode.tabvisuals.gui.hud.compat.TotemCounter;
import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.compat.Instance;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;

public class TotemCounterHud extends AbstractDraggable {

    private static final float ICON_SIZE = 16.0f;

    public TotemCounterHud() {
        super("Totem Counter", 970, 532, 44, 20, true);
    }

    public static TotemCounterHud getInstance() {
        return Instance.getDraggable(TotemCounterHud.class);
    }

    @Override
    public boolean visible() {
        return TotemCounter.getInstance() != null && TotemCounter.getInstance().isState();
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null) return;

        int totemCount = countTotems();
        float x = getX();
        float y = getY();
        String text = String.valueOf(totemCount);
        float backgroundWidth = ICON_SIZE + 4.0f + mc.textRenderer.getWidth(text) + 6.0f;
        setWidth((int) Math.ceil(backgroundWidth));
        setHeight(20);

        Hud hud = Hud.getInstance();
        if (hud != null && hud.shouldDrawHudBlur()) {
            blur.render(ShapeProperties.create(context.getMatrices(), x - 2.0f, y - 2.0f, backgroundWidth, 20)
                    .round(4)
                    .quality(hud.getBlurQuality())
                    .color(ColorAssist.getBlurColor())
                    .build());
        }

        float textX = x + ICON_SIZE + 4.0f;
        float textY = y + (ICON_SIZE / 2.0f) - (mc.textRenderer.fontHeight / 2.0f);

        context.drawItem(Items.TOTEM_OF_UNDYING.getDefaultStack(), (int) x, (int) y);
        context.drawText(mc.textRenderer, text, (int) textX, (int) textY, -1, true);
    }

    private int countTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                count += mc.player.getInventory().getStack(i).getCount();
            }
        }
        return count;
    }
}
