/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

/**
 * Критически затухающая «пружина» как функция времени: старт с нулевой скоростью,
 * без перерегулирования; нормировка (1 − (1 + k)e^(−k)) даёт ровно 1 в конце.
 */
public class Spring extends Animation {
    /** Жёсткость: больше — быстрее оседание. */
    private static final double K = 7.0;

    @Override
    public double calculation(double value) {
        if (ms <= 0) return 1.0;
        double x = Math.min(1.0, Math.max(0.0, value / ms));
        double norm = 1.0 - (1.0 + K) * Math.exp(-K);
        return (1.0 - (1.0 + K * x) * Math.exp(-K * x)) / norm;
    }
}
