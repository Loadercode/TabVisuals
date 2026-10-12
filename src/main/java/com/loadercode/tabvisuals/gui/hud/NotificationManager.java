// language: Java, file: NotificationManager.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.hud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toast queue for HUD notifications ("Module X was enabled" style).
 * Newest toast sits at the top slot (crosshair + {@link #START_OFFSET}),
 * older ones slide down as new arrive. Animation state lives on each toast;
 * the HUD frame drives {@link #update(float)} once per frame, screens that
 * render while no world is running pass the drive flag themselves.
 */
public final class NotificationManager {

    /** Slide/pop-in duration before the toast settles. */
    public static final float ENTER_MS = 180f;
    /** Full-opacity hold before the exit fade starts. */
    public static final float HOLD_MS = 1400f;
    /** Exit fade + float-away duration. */
    public static final float EXIT_MS = 400f;
    public static final float TOAST_H = 17f;
    public static final float TOAST_GAP = 3.5f;
    /** Offset below the crosshair for the first (topmost) toast. */
    public static final float START_OFFSET = 18f;

    private static final int MAX_QUEUE = 6;

    public static final class Toast {
        public final String prefix;
        public final String name;
        public final String middle;
        public final String status;
        public final int statusColor;
        public final long bornAt = System.nanoTime();

        /** easeOutBack of enter progress; may briefly exceed 1 (overshoot pop). */
        public float enterEased;
        /** 0..1 progress of the exit fade, starts after {@link #HOLD_MS}. */
        public float exitP;
        /** Animated slot Y; -1 until the first update assigns it. */
        public float animY = -1f;

        Toast(String prefix, String name, String middle, String status, int statusColor) {
            this.prefix = prefix;
            this.name = name;
            this.middle = middle;
            this.status = status;
            this.statusColor = statusColor;
        }

        public boolean expired() {
            return exitP > 0f;
        }
    }

    private static final List<Toast> QUEUE = new ArrayList<>();
    private static long lastNanos;

    private NotificationManager() {
    }

    /** Generic four-segment toast: prefix + name (bold) + middle + status (bold, colored). */
    public static void push(String prefix, String name, String middle, String status, int statusColor) {
        if (QUEUE.size() >= MAX_QUEUE) {
            QUEUE.remove(QUEUE.size() - 1); // drop the oldest (bottom-most)
        }
        QUEUE.add(0, new Toast(prefix, name, middle, status, statusColor));
    }

    public static void moduleToggle(String moduleName, boolean enabled) {
        push("Module ", moduleName, " was ", enabled ? "enabled" : "disabled",
                enabled ? 0xFF2ED573 : 0xFFFF6B81);
    }

    /** verb: "saved" / "loaded" (green) or "deleted" (red). */
    public static void configEvent(String configName, String verb) {
        boolean positive = !"deleted".equals(verb);
        push("Config ", configName, " was ", verb, positive ? 0xFF2ED573 : 0xFFFF6B81);
    }

    public static List<Toast> queue() {
        return QUEUE;
    }

    /** Advance animations and re-flow slot positions. Call once per frame. */
    public static void update(float screenH) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (dt > 0.25f) {
            dt = 0.25f;
        }

        float startY = screenH / 2f + START_OFFSET;
        float slotLerp = 1f - (float) Math.pow(1f - 0.22, dt * 60f);

        int index = 0;
        Iterator<Toast> it = QUEUE.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            float age = (now - t.bornAt) / 1_000_000f;
            t.enterEased = easeOutBack(clamp01(age / ENTER_MS));
            if (age > HOLD_MS) {
                t.exitP = clamp01((age - HOLD_MS) / EXIT_MS);
                if (t.exitP >= 1f) {
                    it.remove();
                    continue;
                }
            }

            float slotY = startY + index * (TOAST_H + TOAST_GAP);
            if (t.animY < 0f) {
                t.animY = slotY;
            } else {
                t.animY += (slotY - t.animY) * slotLerp;
            }
            index++;
        }
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
