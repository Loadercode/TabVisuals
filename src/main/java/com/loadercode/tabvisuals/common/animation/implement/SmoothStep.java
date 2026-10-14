/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.implement;

import com.loadercode.tabvisuals.common.animation.Animation;

/**
 * Smoothstep (3t² − 2t³): симметричный мягкий разгон и торможение,
 * производная на концах равна нулю — рывка на старте и на финише нет.
 */
public class SmoothStep extends Animation {

    @Override
    public double calculation(double value) {
        if (ms <= 0) return 1.0;
        double x = Math.min(1.0, Math.max(0.0, value / ms));
        return x * x * (3.0 - 2.0 * x);
    }
}
