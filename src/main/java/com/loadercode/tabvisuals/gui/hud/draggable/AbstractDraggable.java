/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.draggable;

import com.loadercode.tabvisuals.gui.hud.elements.HudHelper;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import com.loadercode.tabvisuals.common.animation.Animation;
import com.loadercode.tabvisuals.common.animation.Direction;
import com.loadercode.tabvisuals.common.animation.implement.Decelerate;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.interfaces.QuickLogger;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.geometry.Render2D;
import com.loadercode.tabvisuals.gui.hud.Hud;

@Setter
@Getter
public abstract class AbstractDraggable implements Draggable, QuickImports, QuickLogger {
    private String name;
    private int x, y, width, height;
    private boolean dragging, canDrag;
    private int dragX, dragY;
    /** Позиция в долях экрана (0..1) — сохраняется при F11/смене разрешения */
    private float percentX = -1f, percentY = -1f;

    public AbstractDraggable(String name, int x, int y, int width, int height, boolean canDrag) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.canDrag = canDrag;
    }

    public final Animation scaleAnimation = new Decelerate().setValue(1).setMs(200);

    @Override
    public boolean visible() {
        return true;
    }

    @Override
    public void tick() {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!dragging) {
            dragX = 0;
            dragY = 0;
            return;
        }

        Hud hud = Hud.getInstance();
        float mouseDragX = mouseX + dragX;
        float mouseDragY = mouseY + dragY;
        int windowWidth  = mc.getWindow().getScaledWidth();
        int windowHeight = mc.getWindow().getScaledHeight();
        int radius = 3;

        this.x = (int) Math.max(0, Math.min(mouseDragX, windowWidth - width));
        this.y = (int) Math.max(0, Math.min(mouseDragY, windowHeight - height));
        updatePercent();

        for (AbstractDraggable drag : com.loadercode.tabvisuals.gui.hud.Hud.getRepository().draggable()) {
            if (!drag.canDraw(hud, drag)) continue;
            if (!drag.canDrag) continue;
            if (drag == this) continue;
            int x1 = drag.x + drag.width + radius;
            int x2 = drag.x - width - radius;
            int y1 = drag.y + drag.height + radius;
            int y2 = drag.y - height - radius;
            int y3 = drag.y;

            if (Math.abs(x1 - mouseDragX) <= radius) { drawRect(x1 - 1.5F, 0, 1, windowHeight); this.x = x1; updatePercent(); }
            if (Math.abs(x2 - mouseDragX) <= radius) { drawRect(x2 + width + 1, 0, 1, windowHeight); this.x = x2; updatePercent(); }
            if (Math.abs(y1 - mouseDragY) <= radius) { drawRect(0, y1 - 1.5F, windowWidth, 1); this.y = y1; updatePercent(); }
            if (Math.abs(y2 - mouseDragY) <= radius) { drawRect(0, y2 + height + 1, windowWidth, 1); this.y = y2; updatePercent(); }
            if (Math.abs(y3 - mouseDragY) <= radius) { drawRect(0, y3 - 1.5F, windowWidth, 1); this.y = y3; updatePercent(); }
        }

        if (Math.abs(x + (width - windowWidth) / 2) <= radius) {
            drawRect((float) windowWidth / 2 - 0.5F, 0, 1, windowHeight);
            this.x = (windowWidth - width) / 2;
            updatePercent();
        }
        if (Math.abs(y + (height - windowHeight) / 2) <= radius) {
            drawRect(0, (float) windowHeight / 2 - 0.5F, windowWidth, 1);
            this.y = (windowHeight - height) / 2;
            updatePercent();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Не даём перетаскивать элементы, выключенные в настройках HUD — иначе появляется "призрак"
        if (Hud.getInstance() == null || !canDraw(Hud.getInstance(), this)) return false;
        if (isHovered(mouseX, mouseY) && button == 0 && canDrag) {
            dragging = true;
            dragX = x - (int) mouseX;
            dragY = y - (int) mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        dragX = 0;
        dragY = 0;
        return true;
    }

    public abstract void drawDraggable(DrawContext context);

    public void drawRect(float x, float y, float width, float height) {
        Render2D.drawQuad(x, y, width, height, ColorAssist.getText(0.5F));
    }

    public void stopAnimation() {
        scaleAnimation.setDirection(Direction.BACKWARDS);
    }

    public void startAnimation() {
        scaleAnimation.setDirection(Direction.FORWARDS);
    }

    private void updatePercent() {
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        if (w > 0 && h > 0) {
            percentX = (float) Math.max(0, Math.min(x, w - width)) / w;
            percentY = (float) Math.max(0, Math.min(y, h - height)) / h;
        }
    }

    public void validPosition() {
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        if (w <= 0 || h <= 0 || width <= 0 || height <= 0) return;
        if (x + width > w) x = w - width;
        if (y + height > h) y = h - height;
        if (y < 0) y = 0;
        if (x < 0) x = 0;
        updatePercent();
    }

    /** Пересчёт x,y из percentX/percentY при смене разрешения (F11 и т.д.) */
    public void recalcPositionFromResolution(int windowWidth, int windowHeight) {
        if (windowWidth <= 0 || windowHeight <= 0) return;
        if (width <= 0 || height <= 0) return;
        if (percentX >= 0 && percentY >= 0) {
            x = (int) Math.max(0, Math.min(percentX * windowWidth, windowWidth - width));
            y = (int) Math.max(0, Math.min(percentY * windowHeight, windowHeight - height));
        } else {
            percentX = (float) Math.max(0, Math.min(x, windowWidth - width)) / windowWidth;
            percentY = (float) Math.max(0, Math.min(y, windowHeight - height)) / windowHeight;
            x = (int) (percentX * windowWidth);
            y = (int) (percentY * windowHeight);
        }
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isCloseAnimationFinished() {
        return scaleAnimation.isFinished(Direction.BACKWARDS);
    }

    public boolean canDraw(Hud hud, AbstractDraggable draggable) {
        return hud != null && hud.isState()
                && hud.interfaceSettings.isSelected(draggable.getName())
                && visible();
    }
}
