// language: Java, file: MenuFrame.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.title;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Per-frame context handed to every menu part. All geometry is in CANVAS
 * units (see {@link MenuKit#unitPx()}): the matrix stack is already scaled,
 * the mouse is already converted. One instance is reused across frames.
 */
public final class MenuFrame {

    public DrawContext ctx;
    public MatrixStack m;
    /** Canvas size in units. */
    public float w, h;
    /** Mouse in canvas units; parked far off-screen while input is blocked. */
    public float mx, my;
    /** Frame delta in seconds, clamped so a hitch never teleports an animation. */
    public float dt;
    /** Wall clock (ms) — shared by every part so staggered timings line up. */
    public long now;
    /** Seconds since the screen opened; monotonic, for idle loops. */
    public float time;
    /** False while a transition or an overlay owns the mouse: no hover, no clicks. */
    public boolean interactive;

    public boolean hover(float x, float y, float bw, float bh) {
        return interactive && mx >= x && mx <= x + bw && my >= y && my <= y + bh;
    }
}
