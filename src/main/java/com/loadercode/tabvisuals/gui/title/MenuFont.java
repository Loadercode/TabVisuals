// language: Java, file: MenuFont.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import net.minecraft.client.util.math.MatrixStack;

/**
 * A typeface at a fixed DESIGN size for the menu's virtual canvas.
 *
 * <p>The font stack rasterises a size-N face into N-pixel glyphs and draws
 * them at half scale, which is only 1:1 at GUI scale 2. The menu canvas has
 * its own pixel density ({@link MenuKit#unitPx()}), so this wrapper picks the
 * raster size that lands 1:1 on the physical pixels of the current window and
 * compensates with a matrix scale — text stays sharp at every resolution and
 * GUI scale instead of being stretched.
 */
public final class MenuFont {

    private final Fonts.Type type;
    private final float size;

    private float resolvedPx = -1f;
    private FontRenderer font;
    private float k = 1f;
    private float lineH;

    public MenuFont(float size, Fonts.Type type) {
        this.size = size;
        this.type = type;
    }

    private void resolve() {
        float px = MenuKit.unitPx();
        if (px == resolvedPx && font != null) {
            return;
        }
        int n = Math.max(6, Math.min(128, Math.round(size * px / 2f)));
        font = Fonts.getSize(n, type);
        k = size / n;
        lineH = font.getStringHeight("A");
        resolvedPx = px;
    }

    /** Width of {@code s} in canvas units. */
    public float width(String s) {
        if (s == null || s.isEmpty()) {
            return 0f;
        }
        resolve();
        return font.getStringWidth(s) * k;
    }

    /** Visual line height in canvas units (glyph box, half-scale applied). */
    public float height() {
        resolve();
        return lineH * 0.5f * k;
    }

    /** Draws with the text's optical centre on {@code cy}, left edge at {@code x}. */
    public void draw(MatrixStack m, String s, float x, float cy, int argb) {
        if (s == null || s.isEmpty() || (argb >>> 24) < 3) {
            return;
        }
        resolve();
        m.push();
        m.translate(x, cy, 0f);
        m.scale(k, k, 1f);
        // the renderer draws the pen 3px above y at half scale — same optical
        // centring recipe as the rest of the client
        font.drawString(m, s, 0, 3f - lineH * 0.25f, argb);
        m.pop();
    }

    public void drawCentered(MatrixStack m, String s, float cx, float cy, int argb) {
        draw(m, s, cx - width(s) / 2f, cy, argb);
    }

    public void drawRight(MatrixStack m, String s, float rightX, float cy, int argb) {
        draw(m, s, rightX - width(s), cy, argb);
    }

    /** {@code s} cut to {@code maxW} canvas units with an ellipsis; allocates only when it has to cut. */
    public String fit(String s, float maxW) {
        if (s == null || s.isEmpty() || width(s) <= maxW) {
            return s == null ? "" : s;
        }
        float room = maxW - width("…");
        if (room <= 0f) {
            return "";
        }
        String cut = font.trimToWidth(s, Math.max(1, (int) (room / k)));
        return cut.stripTrailing() + "…";
    }
}
