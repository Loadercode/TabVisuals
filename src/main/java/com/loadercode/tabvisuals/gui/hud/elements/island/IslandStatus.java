/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements.island;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;

@Getter
public abstract class IslandStatus {

    protected float width = 36f;   // дефолтная ширина статуса
    protected float height = 12f;  // дефолтная высота статуса

    public abstract boolean canShow();
    public abstract void draw(DrawContext context, float x, float y, float islandWidth, float islandHeight, float alpha, float slideAnim);

    public void updateSize() {}

    public int getColor() {
        return 0xE0181818;
    }

    /**
     * Если true — ванильный боссбар можно скрыть: содержимое показано в Dynamic Island.
     * Если false — боссбар не распознан как наш сценарий (например, только логотип в островке):
     * боссбар остаётся видимым, островок смещается ниже под него.
     */
    public boolean hidesVanillaBossBar() {
        return true;
    }

    /**
     * Обработка клика по области островка.
     * По умолчанию статусы игнорируют клики.
     *
     * @param mouseX       координата мыши X (в экранных координатах)
     * @param mouseY       координата мыши Y (в экранных координатах)
     * @param islandX      X островка
     * @param islandY      Y островка
     * @param islandWidth  ширина островка
     * @param islandHeight высота островка
     */
    public void click(double mouseX, double mouseY, float islandX, float islandY, float islandWidth, float islandHeight) {
        // no-op by default
    }

    /**
     * Клик произошёл вне островка.
     * Можно использовать для закрытия внутренних окон (например, управления музыкой).
     */
    public void onOutsideClick() {
        // no-op by default
    }
}
