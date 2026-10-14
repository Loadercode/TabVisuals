/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.Easy;

/**
 * Экспоненциальный выход: почти мгновенный разгон и едва различимое доведение до цели.
 * На x = 1 возвращается ровно 1, чтобы конец анимации не «дотягивался» телепортом.
 */
public class EaseOutExpo extends EasyAnimService {

    public EaseOutExpo(int ms, double endPoint) {
        super(ms, endPoint);
    }

    public EaseOutExpo(int ms, double endPoint, Direction direction) {
        super(ms, endPoint, direction);
    }

    @Override
    protected double getEquation(double x) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        return 1.0 - Math.pow(2.0, -10.0 * x);
    }
}
