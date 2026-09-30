package ru.daniel.soviethifi.audio;

/** Audio samples are the clock; network/wall time only steers speed gently. */
final class PlaybackCursor {
    private double position = Double.NaN;
    private double step;

    void align(double target, int sourceRate, int outputRate) {
        if (Double.isNaN(position)) position = Math.max(0, target);
        // Never jump at buffer or snapshot boundaries. Correct drift over time,
        // bounded to 0.1% so scheduler/network jitter cannot become clicks.
        double errorSeconds = (target - position) / sourceRate;
        step = sourceRate / (double) outputRate *
            (1 + Math.clamp(errorSeconds / 10, -0.001, 0.001));
    }

    double next() {
        double result = position;
        position += step;
        return result;
    }
}
