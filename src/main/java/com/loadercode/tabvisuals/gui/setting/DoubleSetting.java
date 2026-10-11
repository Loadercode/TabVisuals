// language: Java, file: DoubleSetting.java, target: Minecraft 1.21.4 Fabric, JDK 21
package com.loadercode.tabvisuals.gui.setting;

/** Slider row with step snapping. */
public final class DoubleSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;
    private double value;

    public DoubleSetting(String name, double value, double min, double max, double step, String suffix) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step > 0 ? step : 0.1;
        this.suffix = suffix != null ? suffix : "";
        this.value = clamp(value);
    }

    public double get() {
        return value;
    }

    public void set(double v) {
        this.value = clamp(v);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public String suffix() {
        return suffix;
    }

    /** Normalized 0..1 position of the current value on the track. */
    public float fraction() {
        return (float) ((value - min) / (max - min));
    }

    /** Value for a normalized 0..1 track position, snapped to step. */
    public double fromFraction(float f) {
        double raw = min + (max - min) * Math.max(0f, Math.min(1f, f));
        double snapped = Math.round(raw / step) * step;
        return clamp(snapped);
    }

    public String format() {
        double v = value;
        String out;
        if (step >= 1) {
            out = String.valueOf((long) v);
        } else if (step >= 0.1) {
            out = String.format(java.util.Locale.ROOT, "%.1f", v);
        } else {
            out = String.format(java.util.Locale.ROOT, "%.2f", v);
        }
        return out + suffix;
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }
}
