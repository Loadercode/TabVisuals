// language: Java, file: UiRender.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui;

import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import net.minecraft.client.util.math.MatrixStack;

/** Thin wrappers over the ported render stack for GUI drawing. All colors are ARGB ints. */
public final class UiRender implements QuickImports {

    private UiRender() {
    }

    public static void rect(MatrixStack m, float x, float y, float w, float h, float round, int argb) {
        rectangle.render(ShapeProperties.create(m, x, y, w, h)
                .round(round)
                .color(argb)
                .build());
    }

    /** Stroke-only rounded rect. */
    public static void outline(MatrixStack m, float x, float y, float w, float h, float round, int argb, float thickness) {
        rectangle.render(ShapeProperties.create(m, x, y, w, h)
                .round(round)
                .thickness(thickness)
                .outlineColor(argb)
                .color(0x00000000)
                .build());
    }

    public static void text(FontRenderer font, MatrixStack m, String s, double x, double y, int argb) {
        font.drawString(m, s, x, y, argb);
    }

    public static void textTruncated(FontRenderer font, MatrixStack m, String s, double x, double y, float maxWidth, int argb) {
        font.drawStringWithScroll(m, s, x, y, maxWidth, argb);
    }

    /** Centered text inside a pill; returns the full pill width. */
    public static float pill(MatrixStack m, FontRenderer font, float cx, float y, float h, float pad, String label, int bg, int fg) {
        float tw = font.getStringWidth(label);
        float w = tw + pad * 2;
        float x = cx - w / 2f;
        rect(m, x, y, w, h, h / 2f, bg);
        font.drawString(m, label, x + pad, y + (h - font.getStringHeight(label)) / 2f + 1f, fg);
        return w;
    }

    public static int withAlpha(int argb, int a) {
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int mulAlpha(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
