/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

public class OutBack extends Animation {

    @Override
    public double calculation(double value) {
        // x держим строго в [0..1]: при ms == 0 тут был бы Infinity/NaN,
        // а при отрицательном/отмотанном времени куб Back взрывался до x14+
        double x = ms <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, value / ms));

        double c1 = 1.70158;
        double c3 = c1 + 1;

        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }
}
