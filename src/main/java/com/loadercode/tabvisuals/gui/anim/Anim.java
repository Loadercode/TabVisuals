/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.anim;

/**
 * FPS-независимый аниматор одного значения.
 *
 * Два режима:
 *  — экспоненциальный подход: за кадр значение сокращает расстояние до цели
 *    на {@code 1 - exp(-dt * speed)}, скорость падает экспоненциально;
 *  — пружина: физическое интегрирование, по умолчанию критически затухающая.
 *
 * dt задаётся в секундах и обрезается до {@link #MAX_DT}, чтобы после фриза
 * или альт-таба значение не перепрыгивало цель рывком.
 */
public final class Anim {
    /** Максимальный шаг времени, сек: длинные кадры (фриз, сворачивание) обрезаются. */
    public static final double MAX_DT = 0.05;

    /** До такого расстояния до цели значение считается установленным. */
    public static final double DEFAULT_EPSILON = 1.0E-3;

    private static final double DEFAULT_SPEED = 18.0;
    private static final double DEFAULT_STIFFNESS = 260.0;
    /** Порог скорости для точной остановки, ед/сек — зависит от единиц значения. */
    private static final double SNAP_VELOCITY = 1.0;

    private double value;
    private double target;
    private double velocity;

    private double speed = DEFAULT_SPEED;
    private double stiffness = DEFAULT_STIFFNESS;
    private double damping = 2.0 * Math.sqrt(DEFAULT_STIFFNESS);
    private boolean spring;

    private double epsilon = DEFAULT_EPSILON;
    private long lastNano = System.nanoTime();

    public Anim() {
        this(0.0);
    }

    public Anim(double start) {
        snapTo(start);
    }

    /** Вести значение к {@code target} с текущей позиции и скорости. */
    public Anim animateTo(double target) {
        this.target = target;
        return this;
    }

    /** Мгновенно поставить значение (и цель), обнулив скорость. */
    public Anim snapTo(double value) {
        this.value = value;
        this.target = value;
        this.velocity = 0.0;
        return this;
    }

    /** Продвинуть на {@code dt} секунд — когда кадр сам передаёт время. */
    public Anim update(double dt) {
        lastNano = System.nanoTime();
        return advance(dt);
    }

    /** Продвинуть по своему замеру времени с прошлого вызова — когда dt извне неоткуда взять. */
    public Anim tick() {
        long now = System.nanoTime();
        double dt = (now - lastNano) / 1.0E9;
        lastNano = now;
        return advance(dt);
    }

    private Anim advance(double dt) {
        if (!(dt > 0.0)) dt = 0.0;
        if (dt > MAX_DT) dt = MAX_DT;
        if (dt > 0.0) {
            if (spring) {
                integrateSpring(dt);
            } else {
                value += (target - value) * (1.0 - Math.exp(-dt * speed));
                velocity = (target - value) * speed;
            }
            if (Math.abs(target - value) <= epsilon && Math.abs(velocity) <= SNAP_VELOCITY) {
                value = target;
                velocity = 0.0;
            }
        }
        return this;
    }

    /**
     * Полунеявный Эйлер с разбиением на подшаги: при dt·√stiffness больше ~0.5
     * явная схема начинает раскачиваться, подшаги удерживают её на обычных кадрах.
     */
    private void integrateSpring(double dt) {
        double omega = Math.sqrt(stiffness);
        int steps = (int) Math.ceil(dt * omega * 2.0);
        if (steps < 1) steps = 1;
        else if (steps > 32) steps = 32;

        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            double accel = stiffness * (target - value) - damping * velocity;
            velocity += accel * h;
            value += velocity * h;
        }
    }

    /** Текущее значение. */
    public double get() {
        return value;
    }

    /** Текущая скорость, ед/сек: 0 в покое и после snapTo. */
    public double getVelocity() {
        return velocity;
    }

    /** Куда движемся. */
    public double getTarget() {
        return target;
    }

    /** true, когда значение вплотную к цели и скорость сброшена. */
    public boolean isDone() {
        return value == target && velocity == 0.0;
    }

    /** Пока есть что двигать — можно пропускать обновления и перерисовку. */
    public boolean isMoving() {
        return !isDone();
    }

    /** Режим мягкого подхода: k — скорость схождения, 1/сек (18 ≈ 0.2 с до цели). */
    public Anim setSpeed(double k) {
        this.speed = Math.max(1.0E-3, k);
        this.spring = false;
        return this;
    }

    /** Режим пружины с критическим затуханием: без перелёта, старт с нулевой скоростью. */
    public Anim setSpring(double stiffness) {
        double st = Math.max(1.0E-3, stiffness);
        return setSpring(st, 2.0 * Math.sqrt(st));
    }

    /**
     * Режим пружины: stiffness — жёсткость, ед/сек²; damping — затухание, 1/сек.
     * damping меньше 2√stiffness даёт лёгкий перелёт, больше — вялое оседание.
     */
    public Anim setSpring(double stiffness, double damping) {
        this.stiffness = Math.max(1.0E-3, stiffness);
        this.damping = Math.max(0.0, damping);
        this.spring = true;
        return this;
    }

    /** Порог «дошли», в единицах самого значения. */
    public Anim setEpsilon(double epsilon) {
        this.epsilon = Math.max(1.0E-9, epsilon);
        return this;
    }
}
