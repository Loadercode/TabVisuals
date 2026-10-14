/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.anim;

/**
 * Готовые куски «появления элемента». Всё принимает время в секундах,
 * а не в кадрах, поэтому на 30 и на 300 FPS выглядит одинаково;
 * отрицательное и переборщённое время само обрезается в 0..1.
 */
public final class UiAnim {
    /** Пауза между элементами каскада по умолчанию, сек. */
    public static final double STAGGER_STEP = 0.045;

    /** Масштаб, с которого элемент «вырастает» в 1.0. */
    public static final double SCALE_FROM = 0.92;

    private UiAnim() {
    }

    /** Прошедшее время → доля 0..1; duration <= 0 считается мгновенным. */
    public static double progress(double elapsed, double duration) {
        if (duration <= 0.0) return elapsed > 0.0 ? 1.0 : 0.0;
        double t = elapsed / duration;
        return Easing.clamp01(t);
    }

    /** Прошедшее время со сдвигом начала (stagger), но не меньше 0. */
    public static double delay(double elapsed, double delaySeconds) {
        double t = elapsed - delaySeconds;
        return t > 0.0 ? t : 0.0;
    }

    /** Альфа 0..1 за время появления: мягкий smoothstep. */
    public static double fadeIn(double elapsed, double duration) {
        return fadeIn(elapsed, duration, Easing.SMOOTH);
    }

    /** Альфа 0..1 за время появления своим сглаживанием. */
    public static double fadeIn(double elapsed, double duration, Easing easing) {
        return easing.apply(progress(elapsed, duration));
    }

    /** Смещение для slide-in: стартует с {@code distance} и приходит к 0. */
    public static double slideIn(double elapsed, double duration, double distance) {
        return slideIn(elapsed, duration, distance, Easing.CUBIC_OUT);
    }

    /** Смещение для slide-in своим сглаживанием: {@code distance} → 0. */
    public static double slideIn(double elapsed, double duration, double distance, Easing easing) {
        return distance * (1.0 - easing.apply(progress(elapsed, duration)));
    }

    /** Масштаб при появлении: от {@link #SCALE_FROM} до 1 с лёгким перелётом. */
    public static double scaleIn(double elapsed, double duration) {
        return scaleIn(elapsed, duration, SCALE_FROM);
    }

    /** Масштаб при появлении: от {@code fromScale} до 1 с лёгким перелётом. */
    public static double scaleIn(double elapsed, double duration, double fromScale) {
        double p = Easing.apply(Easing.BACK_OUT, progress(elapsed, duration));
        return fromScale + (1.0 - fromScale) * p;
    }

    /** Задержка старта, сек, для позиции {@code index} при каскадном появлении. */
    public static double stagger(int index) {
        return stagger(index, STAGGER_STEP);
    }

    /** Каскадная задержка со своим шагом, сек. */
    public static double stagger(int index, double stepSeconds) {
        if (index <= 0) return 0.0;
        return index * Math.max(0.0, stepSeconds);
    }
}
