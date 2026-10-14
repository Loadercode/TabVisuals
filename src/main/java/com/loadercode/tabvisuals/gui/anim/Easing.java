/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.anim;

/**
 * Именованные функции сглаживания как лямбды: {@code Easing.CUBIC_OUT.apply(p)}.
 * Аргумент и результат — доля 0..1; внешнее время сначала прогоняют через
 * {@link UiAnim#progress(double, double)}, затем через {@link #apply(Easing, double)}.
 */
@FunctionalInterface
public interface Easing {

    double apply(double t);

    /** Линейно, без сглаживания. */
    Easing LINEAR = t -> t;

    /** Мягкий старт и мягкое окончание (smoothstep), производная на концах нулевая. */
    Easing SMOOTH = t -> {
        double x = clamp01(t);
        return x * x * (3.0 - 2.0 * x);
    };

    Easing QUAD_OUT = t -> {
        double u = 1.0 - clamp01(t);
        return 1.0 - u * u;
    };

    Easing CUBIC_IN = t -> {
        double x = clamp01(t);
        return x * x * x;
    };

    Easing CUBIC_OUT = t -> {
        double u = 1.0 - clamp01(t);
        return 1.0 - u * u * u;
    };

    Easing CUBIC_IN_OUT = t -> {
        double x = clamp01(t);
        return x < 0.5
                ? 4.0 * x * x * x
                : 1.0 - Math.pow(-2.0 * x + 2.0, 3.0) / 2.0;
    };

    Easing QUINT_OUT = t -> {
        double u = 1.0 - clamp01(t);
        double u2 = u * u;
        return 1.0 - u2 * u2 * u;
    };

    Easing EXPO_OUT = t -> {
        double x = clamp01(t);
        return x >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * x);
    };

    Easing SINE_OUT = t -> Math.sin(clamp01(t) * Math.PI * 0.5);

    Easing CIRC_OUT = t -> {
        double x = clamp01(t) - 1.0;
        return Math.sqrt(1.0 - x * x);
    };

    /**
     * Выход с лёгким перелётом (~4.5 %) на конце: константа подобрана мягче
     * канонического back-out, чтобы всплытие не читалось как отскок.
     */
    Easing BACK_OUT = t -> {
        double x = clamp01(t) - 1.0;
        double c1 = 1.10158;
        double c3 = c1 + 1.0;
        return 1.0 + c3 * x * x * x + c1 * x * x;
    };

    /** Мягкий эластичный выход: одно колебание, пик ~+12 % перелёта. */
    Easing ELASTIC_OUT = t -> {
        double x = clamp01(t);
        double norm = 1.0 - Math.exp(-5.0) * Math.cos(2.0 * Math.PI);
        return (1.0 - Math.exp(-5.0 * x) * Math.cos(2.0 * Math.PI * x)) / norm;
    };

    /** Критически затухающая пружина как функция времени: мягкий старт, без перелёта. */
    Easing SPRING_OUT = t -> {
        double x = clamp01(t);
        double k = 7.0;
        double norm = 1.0 - (1.0 + k) * Math.exp(-k);
        return (1.0 - (1.0 + k * x) * Math.exp(-k * x)) / norm;
    };

    /** Обрезать долю в 0..1. */
    static double clamp01(double t) {
        return t < 0.0 ? 0.0 : t > 1.0 ? 1.0 : t;
    }

    /** Применить сглаживание к доле 0..1 (вне диапазона обрезается). */
    static double apply(Easing easing, double t) {
        return easing.apply(clamp01(t));
    }

    /** Из OUT-функции делает IN-функцию: {@code 1 - f(1 - t)}. */
    static Easing reversed(Easing easing) {
        return t -> 1.0 - easing.apply(1.0 - clamp01(t));
    }
}
