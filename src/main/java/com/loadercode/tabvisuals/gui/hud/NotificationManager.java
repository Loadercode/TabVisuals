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
 *
 * <p>Card geometry mirrors LoxeDLC Notifications: a 16px card (15px content
 * + 1px progress strip on the top edge) stepping 18px down the stack.
 */
public final class NotificationManager {

    /** Slide/pop-in duration before the toast settles. */
    public static final float ENTER_MS = 400f;
    /** Full-opacity hold before the exit fade starts. */
    public static final float HOLD_MS = 1400f;
    /** Exit fade + float-away duration. */
    public static final float EXIT_MS = 400f;
    /** LoxeDLC card = getHeight()+1: 15px of content plus the 1px strip. */
    public static final float TOAST_H = 16f;
    /** LoxeDLC steps the stack by getHeight()+3 (18px) → 2px gap here. */
    public static final float TOAST_GAP = 2f;
    /** Offset below the crosshair for the first (topmost) toast — LoxeDLC anchor. */
    public static final float START_OFFSET = 100f;
    /** Extra drag offset from the in-chat HUD editor; 0 = default slot. */
    public static float yOffset = 0f;

    private static final int MAX_QUEUE = 6;
    /** Reflow spring natural frequency, rad/s — critically damped (slides, never bounces). */
    private static final float SPRING_OMEGA = 16f;
    /** Progress drain exponent: >1 keeps the strip nearly full, then finishes it off. */
    private static final float DRAIN_EXP = 1.35f;

    public static final class Toast {
        public final String prefix;
        public final String name;
        public final String middle;
        public final String status;
        public final int statusColor;
        public final long bornAt = System.nanoTime();

        /** Authored enter curve; may briefly exceed 1 (soft overshoot pop). */
        public float enterEased;
        /** 0..1 eased progress of the exit fade, starts after {@link #HOLD_MS}. */
        public float exitP;
        /** Animated slot Y; -1 until the first update assigns it. */
        public float animY = -1f;
        /** Slot Y speed (px/s) driving the reflow spring. */
        public float animV;
        /** Remaining lifetime 0..1 on the eased drain curve — the card's top strip. */
        public float progress = 1f;

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
        if (dt > 0.1f) {
            dt = 0.1f;
        }

        float startY = screenH / 2f + START_OFFSET + yOffset;
        float drainMs = ENTER_MS + HOLD_MS;

        int index = 0;
        Iterator<Toast> it = QUEUE.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            float age = (now - t.bornAt) / 1_000_000f;
            t.enterEased = enter_curve(clamp01(age / ENTER_MS));
            if (age > HOLD_MS) {
                t.exitP = smoothstep(clamp01((age - HOLD_MS) / EXIT_MS));
                if (t.exitP >= 1f) {
                    it.remove();
                    continue;
                }
            }
            // Полоса добирается до нуля ровно к началу затухания, но почти не
            // двигается в первой половине жизни (exp > 1).
            t.progress = 1f - (float) Math.pow(clamp01(age / drainMs), DRAIN_EXP);

            float slotY = startY + index * (TOAST_H + TOAST_GAP);
            if (t.animY < 0f) {
                t.animY = slotY;
                t.animV = 0f;
            } else {
                spring_step(t, slotY, dt);
            }
            index++;
        }
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    /**
     * Авторская кривая входа: быстрый разгон, лёгкий перелёт через единицу
     * и мягкое оседание. Рендер берёт её и как альфу, и как сдвиг карточки,
     * так что всплеск чуть приподнимает карточку над слотом и возвращает.
     */
    private static float enter_curve(float p) {
        if (p >= 1f) {
            return 1f;
        }
        float u = 1f - p;
        float base = 1f - u * u * u;                       // ease-out: быстрый старт
        float pop = 0.10f * (float) Math.sin(Math.PI * p) * p; // мягкий перелёт к финалу
        return base + pop;
    }

    /** Мягкое затухание: медленный старт выезда вверх, мягкое приземление. */
    private static float smoothstep(float p) {
        return p * p * (3f - 2f * p);
    }

    /**
     * Полуявный Эйлер для пружины с критическим затуханием. Длинные кадры
     * режутся на шаги ≤ 1/120 c — сглаживание не зависит от FPS и не взрывается.
     */
    private static void spring_step(Toast t, float target, float dt) {
        float rest = dt;
        while (rest > 1e-4f) {
            float h = Math.min(rest, 1f / 120f);
            rest -= h;
            float accel = SPRING_OMEGA * SPRING_OMEGA * (target - t.animY)
                    - 2f * SPRING_OMEGA * t.animV;
            t.animV += accel * h;
            t.animY += t.animV * h;
        }
    }
}
