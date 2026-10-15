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
            this.direction = direction;
            adjustTimer();
        }
    }

    public boolean isDirection(Direction direction) {
        return this.direction == direction;
    }

    private void adjustTimer() {
        counter.setTime(
                System.currentTimeMillis() - ((long) ms - Math.min(ms, counter.getTime()))
        );
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
        return (1 - calculation(counter.getTime())) * value;
    }

    public Double getOutput() {
        return output();
    }

    protected double endValue() {
        return isDone()
                ? value
                : calculation(counter.getTime()) * value;
    }
}