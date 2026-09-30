package ru.daniel.soviethifi.audio;

/** Pure math shared by the mixer and regression tests. */
public final class AudioMath {
    private AudioMath() {}
    public static float distanceGain(double distance, double radius) {
        if (radius <= 0 || distance >= radius) return 0;
        distance = Math.max(0, distance);
        double x = Math.max(0, distance) / radius;
        return (float)((1 - x * x) * (1 - x * x) / (1 + distance * 0.12));
    }
    public static float wallGain(double obstruction) { return (float)Math.exp(-Math.max(0, obstruction) * 0.38); }
    public static float cutoff(double obstruction, float tape) {
        return (float)Math.max(450, (18000 - tape * 7000) * Math.exp(-Math.max(0, obstruction) * 0.55));
    }
    public static float lowPassAlpha(float hz, int sampleRate) { return 1 - (float)Math.exp(-2 * Math.PI * hz / sampleRate); }
    public static long frameAt(long serverTick, long startTick, int sampleRate) { return Math.max(0, serverTick - startTick) * sampleRate / 20; }
    public static float softClip(float sample) { return (float)Math.tanh(sample); }
    public static float mixHeadroom(float leftBudget, float rightBudget) {
        return 0.9f / Math.max(1, Math.max(leftBudget, rightBudget));
    }
}
