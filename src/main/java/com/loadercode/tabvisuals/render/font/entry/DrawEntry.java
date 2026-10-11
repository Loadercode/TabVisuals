/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.font.entry;

import com.loadercode.tabvisuals.render.font.glyph.Glyph;

/**
 * Один отрисованный глиф.
 *
 * <p>Был record'ом: на каждую видимую букву создавался новый объект, а кадр с
 * HUD, чатом и боссбаром даёт сотни глифов — десятки тысяч мелких аллокаций в
 * секунду, которые идут просто в мусор. Теперь это обычный изменяемый класс:
 * {@code FontRenderer} держит пул и переиспользует экземпляры между кадрами,
 * то есть присваивание вместо аллокации.</p>
 *
 * <p>Имена аксессоров оставлены прежними ({@code atX()} / {@code atY()} /
 * {@code color()} / {@code toDraw()}), чтобы весь код отрисовки остался
 * нетронутым и картинка гарантированно не изменилась.</p>
 */
public final class DrawEntry {
    private float atX;
    private float atY;
    private int color;
    private Glyph toDraw;

    public DrawEntry() {
    }

    public DrawEntry(float atX, float atY, int color, Glyph toDraw) {
        reset(atX, atY, color, toDraw);
    }

    /** Переиспользование из пула: одно присваивание на каждый аргумент. */
    public DrawEntry reset(float atX, float atY, int color, Glyph toDraw) {
        this.atX = atX;
        this.atY = atY;
        this.color = color;
        this.toDraw = toDraw;
        return this;
    }

    public float atX() {
        return atX;
    }

    public float atY() {
        return atY;
    }

    public int color() {
        return color;
    }

    public Glyph toDraw() {
        return toDraw;
    }
}
