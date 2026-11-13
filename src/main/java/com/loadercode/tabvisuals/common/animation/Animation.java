/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.loadercode.tabvisuals.render.math.time.TimerUtil;

@Setter
@Accessors(chain = true)
public class Animation implements AnimationCalculation {
    protected final TimerUtil counter = new TimerUtil();
    protected int ms;
    protected double value;
    @Getter
    protected Direction direction = Direction.FORWARDS;

    public void reset() {
        counter.resetCounter();
    }

    public boolean isDone() {
        return counter.isReached(ms);
    }

    public boolean isFinished(Direction direction) {
        return this.direction == direction && isDone();
    }

    public void setDirection(Direction direction) {
        if (this.direction != direction) {
            // progress (0..1 of value) as shown right now, before the flip
            double shown = ms > 0 && !isDone() ? fraction(this.direction, counter.getTime()) : Double.NaN;
            this.direction = direction;
            adjustTimer(shown);
        }
    }

    public boolean isDirection(Direction direction) {
        return this.direction == direction;
    }

    /**
     * Places the timer so the new direction continues from the value shown
     * at the flip. The old mirror formula (elapsed' = ms - elapsed) only
     * matched for symmetric curves; Decelerate jumped 0.75 → 0.25 mid-way.
     */
    private void adjustTimer(double shown) {
        long now = System.currentTimeMillis();
        if (Double.isNaN(shown)) {
            // finished (or ms == 0): start the new direction from its beginning
            counter.setTime(now - ((long) ms - Math.min(ms, Math.max(0L, counter.getTime()))));
            return;
        }
        // bisection over the elapsed time; FORWARDS fraction rises, BACKWARDS falls
        boolean rising = direction == Direction.FORWARDS;
        double lo = 0, hi = ms;
        for (int i = 0; i < 24; i++) {
            double mid = (lo + hi) * 0.5;
            double f = fraction(direction, mid);
            if (rising ? f < shown : f > shown) lo = mid;
            else hi = mid;
        }
        counter.setTime(now - Math.round((lo + hi) * 0.5));
    }

    /** Normalized output (0..1) of the given direction after {@code elapsed} ms. */
    private double fraction(Direction dir, double elapsed) {
        double c = calculation(Math.max(0, Math.min(ms, elapsed)));
        if (!Double.isFinite(c)) c = dir == Direction.FORWARDS ? 1 : 0;
        return dir == Direction.FORWARDS ? c : 1 - c;
    }

    /**
     * То же, что {@link #getOutput()}, но примитивом. Главный HUD-цикл зовёт
     * вывод анимации на каждый draggable каждый кадр, и упаковка в Double
     * добавляла пару десятков объектов в кадр ровно ради того, чтобы тут же
     * разобрать их обратно через floatValue().
     */
    public double output() {
        // calculation() зовём только когда результат реально используется:
        // при ms == 0 он даёт NaN (0/0), а при «отмотанном» таймере — x > 1
        if (direction == Direction.FORWARDS) {
            return endValue();
        }
        if (isDone()) {
            return 0.0;
        }
        double out = (1 - calculation(counter.getTime())) * value;
        return Double.isFinite(out) ? out : 0.0;
    }

    public Double getOutput() {
        return output();
    }

    protected double endValue() {
        if (isDone()) return value;
        double out = calculation(counter.getTime()) * value;
        return Double.isFinite(out) ? out : value;
    }
}