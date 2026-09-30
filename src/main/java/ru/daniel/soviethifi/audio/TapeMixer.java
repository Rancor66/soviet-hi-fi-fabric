package ru.daniel.soviethifi.audio;

import javax.sound.sampled.*;
import java.util.*;
import java.util.function.Consumer;

/** One bounded stereo mixer. No world access from the audio thread. */
public final class TapeMixer implements AutoCloseable {
    public record Voice(String key, PcmTrack track, double frame, long measuredNanos,
                        float leftGain, float rightGain, float cutoff, float tape, int channel) {}
    private volatile List<Voice> voices = List.of();
    private final Map<String, Filter> filters = new HashMap<>();
    private volatile boolean running;
    private Thread thread;
    private SourceDataLine line;
    private final Consumer<String> error;
    public TapeMixer(Consumer<String> error) { this.error = error; }
    public void update(List<Voice> next) {
        voices = List.copyOf(next);
        if (!running && !next.isEmpty()) start();
    }
    private synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::mix, "Soviet Hi-Fi audio");
        thread.setDaemon(true);
        thread.start();
    }
    private void mix() {
        final int rate = 44100, frames = 512;
        byte[] bytes = new byte[frames * 4];
        Random noise = new Random(90);
        try {
            AudioFormat format = new AudioFormat(rate, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, frames * 4 * 4);
            line.start();
            com.mojang.logging.LogUtils.getLogger().info("Hi-Fi stereo audio output opened: {}", line.getLineInfo());
            while (running) {
                List<Voice> active = voices;
                Set<String> keys = new HashSet<>();
                for (Voice voice : active) keys.add(voice.key());
                filters.keySet().retainAll(keys);
                long now = System.nanoTime();
                double queuedSeconds = (line.getBufferSize() - line.available()) / (4.0 * rate);
                for (Voice voice : active) {
                    Filter f = filters.computeIfAbsent(voice.key(), _ -> new Filter());
                    double elapsed = Math.max(0, (now - voice.measuredNanos()) / 1e9) + queuedSeconds;
                    f.cursor.align(voice.frame() + elapsed * voice.track().sampleRate(),
                        voice.track().sampleRate(), rate);
                }
                for (int i = 0; i < frames; i++) {
                    float l = 0, r = 0, leftBudget = 0, rightBudget = 0;
                    for (Voice voice : active) {
                        Filter f = filters.computeIfAbsent(voice.key(), _ -> new Filter());
                        double pos = f.cursor.next();
                        if (pos >= voice.track().frames()) continue;
                        double seconds = pos / voice.track().sampleRate();
                        // Small zero-mean time modulation: wow and flutter, without cumulative drift.
                        pos += voice.tape() * voice.track().sampleRate() *
                            (0.0002 * Math.sin(seconds * Math.PI * 1.4) + 0.00002 * Math.sin(seconds * Math.PI * 17));
                        float sample = voice.track().sample(pos, voice.channel());
                        // Subtle coloration, without boosting quiet material into distortion.
                        float saturated = AudioMath.softClip(sample);
                        sample += (saturated - sample) * (0.15f * voice.tape());
                        // Quiet, softened hiss instead of full-band white noise.
                        f.hiss += 0.25f * ((noise.nextFloat() - 0.5f) - f.hiss);
                        sample += f.hiss * 0.0006f * voice.tape();
                        f.value += AudioMath.lowPassAlpha(voice.cutoff(), rate) * (sample - f.value);
                        f.left += (voice.leftGain() - f.left) * 0.0015f;
                        f.right += (voice.rightGain() - f.right) * 0.0015f;
                        l += f.value * f.left;
                        r += f.value * f.right;
                        leftBudget += Math.abs(f.left);
                        rightBudget += Math.abs(f.right);
                    }
                    // Linked stereo headroom; prevent hard clipping from summed speakers.
                    float busGain = AudioMath.mixHeadroom(leftBudget, rightBudget);
                    l *= busGain;
                    r *= busGain;
                    int sl = (int)(Math.max(-1, Math.min(1, l)) * 32767);
                    int sr = (int)(Math.max(-1, Math.min(1, r)) * 32767);
                    bytes[i * 4] = (byte)sl; bytes[i * 4 + 1] = (byte)(sl >> 8);
                    bytes[i * 4 + 2] = (byte)sr; bytes[i * 4 + 3] = (byte)(sr >> 8);
                }
                line.write(bytes, 0, bytes.length);
            }
        } catch (Exception e) { error.accept("Аудиовыход недоступен: " + e.getMessage()); }
        finally { if (line != null) { line.stop(); line.close(); } running = false; }
    }
    @Override public synchronized void close() {
        running = false; voices = List.of();
        if (thread != null) try { thread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        filters.clear();
    }
    private static final class Filter {
        final PlaybackCursor cursor = new PlaybackCursor();
        float value, left, right, hiss;
    }
}
