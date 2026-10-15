/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

public class InOutCirc extends Animation {

    @Override
    public double calculation(double value) {
        // x в [0..1]: за пределами (ms == 0, отмотанный таймер) sqrt уходил
        // в NaN и тянул за собой весь вывод анимации
        double x = ms <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, value / ms));

        return x < 0.5
                ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) / 2
                : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) / 2;
    }
}
