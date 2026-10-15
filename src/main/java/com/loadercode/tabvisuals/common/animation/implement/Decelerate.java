/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

public class Decelerate extends Animation {

    @Override
    public double calculation(double value) {
        double x = ms <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, value / ms));
        return 1 - (x - 1) * (x - 1);
    }
}
