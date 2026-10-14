/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.Easy;

/**
 * Критически затухающая «пружина» в виде функции времени:
 * старт с нулевой скорости, без перерегулирования, нормировка даёт ровно 1 в конце.
 * Чем больше k, тем жёстче пружина и тем быстрее оседание.
 */
public class EaseSpring extends EasyAnimService {
    /** Жёсткость по умолчанию: ~70 % пути за половину длительности. */
    private static final double DEFAULT_K = 7.0;

    private final double k;

    public EaseSpring(int ms, double endPoint) {
        this(ms, endPoint, DEFAULT_K, Direction.FORWARDS);
    }

    public EaseSpring(int ms, double endPoint, Direction direction) {
        this(ms, endPoint, DEFAULT_K, direction);
    }

    public EaseSpring(int ms, double endPoint, double k, Direction direction) {
        super(ms, endPoint, direction);
        this.k = Math.max(0.5, k);
    }

    @Override
    protected double getEquation(double x) {
        double norm = 1.0 - (1.0 + k) * Math.exp(-k);
        return (1.0 - (1.0 + k * x) * Math.exp(-k * x)) / norm;
    }
}
