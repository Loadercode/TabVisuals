/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.Easy;

/**
 * Мягкий эластичный выход: затухающее касание цели вместо резких качаний.
 * Одно колебание за длительность, пик — примерно +12 % перелёта,
 * нормировка в знаменателе гарантирует ровно endPoint в конце.
 */
public class EaseOutElastic extends EasyAnimService {
    /** Затухание хвоста: больше — спокойнее, меньше — живее. */
    private static final double DEFAULT_DAMPING = 5.0;
    /** 2π радиан = одно мягкое колебание за одну длительность. */
    private static final double FREQUENCY = 2.0 * Math.PI;

    private final double damping;

    public EaseOutElastic(int ms, double endPoint) {
        this(ms, endPoint, Direction.FORWARDS);
    }

    public EaseOutElastic(int ms, double endPoint, Direction direction) {
        this(ms, endPoint, DEFAULT_DAMPING, direction);
    }

    public EaseOutElastic(int ms, double endPoint, double damping, Direction direction) {
        super(ms, endPoint, direction);
        this.damping = Math.max(1.0, damping);
    }

    @Override
    protected boolean correctOutput() {
        // Перелёт есть, поэтому BACKWARDS идём обратным воспроизведением кривой,
        // а не зеркалом: зеркало на старте уходило бы ниже нуля (отрицательная альфа/сдвиг).
        return true;
    }

    @Override
    protected double getEquation(double x) {
        double norm = 1.0 - Math.exp(-damping) * Math.cos(FREQUENCY);
        return (1.0 - Math.exp(-damping * x) * Math.cos(FREQUENCY * x)) / norm;
    }
}
