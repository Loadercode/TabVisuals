/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.render.scissor;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.loadercode.tabvisuals.render.interfaces.QuickImports;

import java.util.Stack;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScissorAssist implements QuickImports {
    /** Shared instance for HUD elements (LoxeDLC getScissorManager()). */
    public static final ScissorAssist INSTANCE = new ScissorAssist();

    Pool<Scissor> scissorPool = new Pool<>(Scissor::new);
    Stack<Scissor> scissorStack = new Stack<>();

    Vector3f cornerA = new Vector3f();
    Vector3f cornerB = new Vector3f();

    public void push(Matrix4f matrix4f, float x, float y, float width, float height) {
        Scissor currentScissor = scissorPool.get();

        // Both corners go through the matrix (getScale() dropped mirroring),
        // into reused vectors instead of two allocations per push.
        // A negative size (overshooting collapse animation) clips everything, as before.
        Vector3f a = matrix4f.transformPosition(x, y, 0, cornerA);
        Vector3f b = matrix4f.transformPosition(x + Math.max(0f, width), y + Math.max(0f, height), 0, cornerB);

        currentScissor.set(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y));

        if (!scissorStack.isEmpty()) {
            Scissor parent = scissorStack.peek();
            currentScissor.intersect(parent);
        }

        scissorStack.push(currentScissor);
        setScissor(currentScissor);
    }

    public void pop() {
        if (!scissorStack.isEmpty()) {
            scissorPool.free(scissorStack.pop());
            if (scissorStack.isEmpty()) {
                RenderSystem.disableScissor();
            } else {
                setScissor(scissorStack.peek());
            }
        }
    }

    /**
     * Сброс в начале кадра.
     *
     * <p>Стек и состояние {@code RenderSystem} живут между кадрами, а
     * исключение между {@link #push} и {@link #pop} — например, в отрисовке
     * шрифта — оставляло бы одну область открытой навсегда: EventManager
     * прокидывает такие исключения дальше, но кадр это не останавливает,
     * дальше стек растёт на единицу каждый кадр, и в итоге обрезалось бы всё
     * рисование до перезапуска. Вызывается из старта {@code GameRenderer.render},
     * то есть до отрисовки HUD и экранов текущего кадра.</p>
     */
    public void endFrame() {
        if (scissorStack.isEmpty()) return;
        while (!scissorStack.isEmpty()) {
            scissorPool.free(scissorStack.pop());
        }
        RenderSystem.disableScissor();
    }

    private void setScissor(Scissor scissor) {
        var currentWindow = mc.getWindow();
        if (currentWindow == null) {
            return;
        }
        double scaleFactor = currentWindow.getScaleFactor();
        // Edges are rounded, not origin and size separately: rounding both
        // independently moved the far edge by a pixel for some fractional
        // positions. Framebuffer height: glScissor works in framebuffer pixels,
        // which differ from window pixels on HiDPI displays.
        int left = toPixel(scissor.x1 * scaleFactor);
        int right = toPixel(scissor.x2 * scaleFactor);
        int top = toPixel(scissor.y1 * scaleFactor);
        int bottom = toPixel(scissor.y2 * scaleFactor);

        RenderSystem.enableScissor(left, currentWindow.getFramebufferHeight() - bottom,
                Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static int toPixel(double value) {
        // NaN -> 0; the clamp keeps huge animated values from overflowing int
        return (int) Math.round(Math.max(-1.0e6, Math.min(1.0e6, value)));
    }


    /** Edges in GUI units; kept fractional so the box is rounded once, in device pixels. */
    private static class Scissor {
        public float x1, y1, x2, y2;

        public void set(float x1, float y1, float x2, float y2) {
            // Clamp the edges, not the origin: clamping x alone kept the full
            // width and pushed the far edge outwards for boxes starting off-screen.
            this.x1 = Math.max(0f, x1);
            this.y1 = Math.max(0f, y1);
            this.x2 = Math.max(this.x1, x2);
            this.y2 = Math.max(this.y1, y2);
        }

        public void intersect(Scissor parent) {
            x1 = Math.max(x1, parent.x1);
            y1 = Math.max(y1, parent.y1);
            x2 = Math.max(x1, Math.min(x2, parent.x2));
            y2 = Math.max(y1, Math.min(y2, parent.y2));
        }
    }
}
