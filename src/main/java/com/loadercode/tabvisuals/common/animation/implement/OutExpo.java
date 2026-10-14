/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

/**
 * Экспоненциальный выход: почти мгновенный разгон и мягкое доведение до цели.
 */
public class OutExpo extends Animation {

    @Override
    public double calculation(double value) {
        if (ms <= 0) return 1.0;
        double x = Math.min(1.0, value / ms);
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        return 1.0 - Math.pow(2.0, -10.0 * x);
    }
}
