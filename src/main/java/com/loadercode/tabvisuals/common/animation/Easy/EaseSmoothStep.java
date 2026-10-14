/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.Easy;

/**
 * Классический smoothstep (3t² − 2t³) с настройкой резкости:
 * sharpness > 1 — вялый старт, < 1 — смелый выход из нуля.
 */
public class EaseSmoothStep extends EasyAnimService {
    private static final double DEFAULT_SHARPNESS = 1.0;

    private final double sharpness;

    public EaseSmoothStep(int ms, double endPoint) {
        this(ms, endPoint, DEFAULT_SHARPNESS, Direction.FORWARDS);
    }

    public EaseSmoothStep(int ms, double endPoint, Direction direction) {
        this(ms, endPoint, DEFAULT_SHARPNESS, direction);
    }

    public EaseSmoothStep(int ms, double endPoint, double sharpness, Direction direction) {
        super(ms, endPoint, direction);
        this.sharpness = Math.max(0.1, sharpness);
    }

    @Override
    protected double getEquation(double x) {
        double t = Math.min(1.0, Math.max(0.0, x));
        double smooth = t * t * (3.0 - 2.0 * t);
        return sharpness == DEFAULT_SHARPNESS ? smooth : Math.pow(smooth, sharpness);
    }
}
