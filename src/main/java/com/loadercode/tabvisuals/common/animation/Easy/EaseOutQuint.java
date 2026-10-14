/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.Easy;

/**
 * Быстрый выход пяточной степени: резвый старт и длинный, незаметный хвост к цели.
 * Вместо Math.pow — цепочка одинаковых множителей: дешевле на каждом кадре.
 */
public class EaseOutQuint extends EasyAnimService {

    public EaseOutQuint(int ms, double endPoint) {
        super(ms, endPoint);
    }

    public EaseOutQuint(int ms, double endPoint, Direction direction) {
        super(ms, endPoint, direction);
    }

    @Override
    protected double getEquation(double x) {
        double u = 1.0 - x;
        double u2 = u * u;
        return 1.0 - u2 * u2 * u;
    }
}
