/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

public class InOutBack extends Animation {

    @Override
    public double calculation(double value) {
        double x = value / ms;

        double c1 = 1.70158;
        double c2 = c1 * 1.525;

        return x < 0.5
                ? (Math.pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2)) / 2
                : (Math.pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2;
    }
}
