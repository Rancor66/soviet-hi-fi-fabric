package ru.daniel.soviethifi.audio;
public record PcmTrack(short[] samples, int sampleRate) {
    public int frames() { return samples.length / 2; }
    public double seconds() { return frames() / (double)sampleRate; }
    public float sample(double frame, int channel) {
        int a = (int)frame;
        if (a < 0 || a >= frames() - 1) return 0;
        float mix = (float)(frame - a);
        return (samples[a * 2 + channel] * (1 - mix) + samples[(a + 1) * 2 + channel] * mix) / 32768f;
    }
}
