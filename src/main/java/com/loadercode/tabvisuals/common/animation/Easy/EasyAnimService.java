/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.common.animation.Easy;

import lombok.Getter;
import lombok.Setter;
import com.loadercode.tabvisuals.render.math.time.TimerUtil;

public abstract class EasyAnimService {
    public TimerUtil timerUtil = new TimerUtil();
    @Setter
    protected int duration;
    @Setter
    @Getter
    protected double endPoint;
    @Getter
    protected Direction direction;

    public EasyAnimService(int ms, double endPoint) {
        this.duration = ms;
        this.endPoint = endPoint;
        this.direction = Direction.FORWARDS;
    }

    public EasyAnimService(int ms, double endPoint, Direction direction) {
        this.duration = ms;
        this.endPoint = endPoint;
        this.direction = direction;
    }

    public boolean finished(Direction direction) {
        return isDone() && this.direction.equals(direction);
    }

    public double getLinearOutput() {
        if (duration <= 0) {
            return 0.0;
        }
        return 1 - ((timerUtil.getTime() / (double) duration) * endPoint);
    }

    public void reset() {
        timerUtil.resetCounter();
    }

    public boolean isDone() {
        return timerUtil.hasTimeElapsed(duration);
    }

    public void changeDirection() {
        setDirection(direction.opposite());
    }

    public void setDirection(Direction direction) {
        if (this.direction != direction) {
            boolean mid = duration > 0 && !isDone();
            double shown = mid ? fraction(this.direction, timerUtil.getTime()) : 0;
            this.direction = direction;
            long now = System.currentTimeMillis();
            if (!mid || correctOutput()) {
                // reverse playback (correctOutput) is continuous with the mirror formula
                timerUtil.setTime(now - (duration - Math.min(duration, Math.max(0L, timerUtil.getTime()))));
                return;
            }
            // mirrored curves: bisect the elapsed time that shows the same value,
            // the mirror formula jumped for asymmetric easings (Expo/Quint/Spring)
            boolean rising = direction == Direction.FORWARDS;
            double lo = 0, hi = duration;
            for (int i = 0; i < 24; i++) {
                double m = (lo + hi) * 0.5;
                double f = fraction(direction, m);
                if (rising ? f < shown : f > shown) lo = m;
                else hi = m;
            }
            timerUtil.setTime(now - Math.round((lo + hi) * 0.5));
        }
    }

    /** Normalized output (0..1 of endPoint) of a direction after {@code elapsed} ms. */
    private double fraction(Direction dir, double elapsed) {
        double x = Math.max(0, Math.min(1, elapsed / duration));
        double e;
        if (dir == Direction.FORWARDS) e = getEquation(x);
        else e = correctOutput() ? getEquation(1 - x) : 1 - getEquation(x);
        return Double.isFinite(e) ? e : (dir == Direction.FORWARDS ? 1 : 0);
    }

    protected boolean correctOutput() {
        return false;
    }

    public double getOutput() {
        // duration == 0: деление на ноль дало бы NaN/Infinity в обеих ветках
        if (duration <= 0) {
            return direction == Direction.FORWARDS ? endPoint : 0;
        }
        double out;
        if (direction == Direction.FORWARDS) {
            if (isDone()) return endPoint;
            out = getEquation(Math.max(0, timerUtil.getTime()) / (double) duration) * endPoint;
            return Double.isFinite(out) ? out : endPoint;
        } else {
            if (isDone()) return 0;
            if (correctOutput()) {
                double revTime = Math.min(duration, Math.max(0, duration - timerUtil.getTime()));
                out = getEquation(revTime / (double) duration) * endPoint;
            } else {
                out = (1 - getEquation(Math.max(0, timerUtil.getTime()) / (double) duration)) * endPoint;
            }
            return Double.isFinite(out) ? out : 0;
        }
    }

    protected abstract double getEquation(double x);
}