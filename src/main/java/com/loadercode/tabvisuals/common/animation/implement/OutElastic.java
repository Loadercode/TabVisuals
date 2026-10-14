/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

/**
 * Мягкий эластичный выход: затухающее касание цели, пик — около +12 % перелёта,
 * нормировка гарантирует ровно 1 в конце.
 */
public class OutElastic extends Animation {
    /** Затухание хвоста: больше — спокойнее, меньше — живее. */
    private static final double DAMPING = 5.0;
    /** 2π радиан = одно мягкое колебание за одну длительность. */
    private static final double FREQUENCY = 2.0 * Math.PI;

    @Override
    public double calculation(double value) {
        double x = ms <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, value / ms));
        double norm = 1.0 - Math.exp(-DAMPING) * Math.cos(FREQUENCY);
        return (1.0 - Math.exp(-DAMPING * x) * Math.cos(FREQUENCY * x)) / norm;
    }
}
