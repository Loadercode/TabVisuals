/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

/**
 * Выход пяточной степени: резвый старт, длинный незаметный хвост.
 * Пад-аргумент тут — прошедшее время в мс, как у остальных Animation.
 */
public class OutQuint extends Animation {

    @Override
    public double calculation(double value) {
        double x = ms <= 0 ? 1.0 : Math.min(1.0, value / ms);
        double u = 1.0 - x;
        double u2 = u * u;
        return 1.0 - u2 * u2 * u;
    }
}
