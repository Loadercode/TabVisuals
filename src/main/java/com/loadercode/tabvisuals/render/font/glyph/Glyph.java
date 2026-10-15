/*© 2026 LoxeDLC. All rights reserved. */
package com.loadercode.tabvisuals.render.font.glyph;

/**
 * u/v — позиция глифа в атласе, width/height — квад в целых текселях
 * (в него умещается отрисованный «чернильный» прямоугольник).
 *
 * <p>advance — точный шаг пера в текселях по дробным метрикам шрифта.
 * Квад берётся из целочисленных метрик Java2D (они округляют advance вверх,
 * например 'T' в suisseintl даёт 10 вместо 7.7), поэтому width и advance
 * намеренно разные: разметка и getStringWidth используют advance,
 * а сам квад остаётся прежним — чтобы не подрезать чернила глифа.</p>
 */
public record Glyph(int u, int v, int width, int height, float advance, char value, GlyphMap owner) {
}
