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
            this.direction = direction;
            timerUtil.setTime(System.currentTimeMillis() - (duration - Math.min(duration, timerUtil.getTime())));
        }
    }

    protected boolean correctOutput() {
        return false;
    }

    public double getOutput() {
        if (direction == Direction.FORWARDS) {
            if (isDone()) return endPoint;
            return getEquation(timerUtil.getTime() / (double) duration) * endPoint;
        } else {
            if (isDone()) return 0;
            if (correctOutput()) {
                double revTime = Math.min(duration, Math.max(0, duration - timerUtil.getTime()));
                return getEquation(revTime / (double) duration) * endPoint;
            } else {
                return (1 - getEquation(timerUtil.getTime() / (double) duration)) * endPoint;
            }
        }
    }

    protected abstract double getEquation(double x);
}